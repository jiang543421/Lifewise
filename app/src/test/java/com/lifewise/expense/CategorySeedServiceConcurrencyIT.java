package com.lifewise.expense;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.expense.service.CategorySeedService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * M8 / B-2 follow-up: {@code CategorySeedService.ensureUserDefault} 并发安全（plan-03 review §M8）。
 *
 * <p><b>v1.0 修订（ledger M8 fully Closed）</b>：从 v1.0 初版的 try/catch + re-query 切换
 * 到 PostgreSQL 原生 UPSERT（{@code CategoryRepository.insertUserDefaultIfAbsent}）。
 * 该路径在 10 线程并发下被 IT 逮到 Hibernate session pollution（catch 块内重查触发
 * {@code org.hibernate.AssertionFailure} 逃出 catch）。UPSERT 路径下 9 线程 INSERT
 * 静默忽略，零 exception 抛出，所有线程通过 SELECT 拿到同一 id。
 *
 * <p>本 IT 验证三条契约：
 * <ol>
 *   <li>DB 最终只有 1 行 {@code is_user_default=TRUE} 的分类（DB 唯一性 + DB 层幂等）</li>
 *   <li>所有并发线程拿到的 id 完全一致（外部观察者视角的幂等）</li>
 *   <li>任何并发线程都未触发 {@code org.hibernate.AssertionFailure}（UPSERT 路径
 *       完全消除 Hibernate session pollution 风险）</li>
 * </ol>
 *
 * <p>第 3 条用 logback ListAppender 拦截 {@code Hibernate 内部 AssertionFailure}
 * 日志（{@code "HHH000099"} prefix）反向监测 —— 通过 = 0，反向证明 v1.0 UPSERT 修法生效。
 */
@DisplayName("CategorySeedService M8 并发安全（UPSERT 路径）")
@SpringBootTest
class CategorySeedServiceConcurrencyIT {

    private static EmbeddedPostgres PG;

    @Autowired private CategorySeedService categorySeedService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbc;

    private Long userId;

    @BeforeAll
    static void startEmbeddedPg() throws IOException, SQLException {
        PG = EmbeddedPostgres.builder().start();
        try (Connection c = DriverManager.getConnection(
                        PG.getJdbcUrl("postgres", "postgres"), "postgres", "postgres");
                Statement s = c.createStatement()) {
            try {
                s.execute("CREATE DATABASE lifewise");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) throw e;
            }
            try {
                s.execute("CREATE USER lifewise WITH PASSWORD 'lifewise'");
            } catch (SQLException e) {
                if (!e.getMessage().contains("already exists")) throw e;
            }
            s.execute("GRANT ALL PRIVILEGES ON DATABASE lifewise TO lifewise");
        }
    }

    @AfterAll
    static void stopEmbeddedPg() throws IOException {
        if (PG != null) PG.close();
    }

    @DynamicPropertySource
    static void registerPgProperties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> PG.getJdbcUrl("lifewise", "lifewise"));
        r.add("spring.datasource.username", () -> "lifewise");
        r.add("spring.datasource.password", () -> "lifewise");
        r.add("spring.flyway.enabled", () -> "true");
        r.add("outbox.scheduler.enabled", () -> "false");
    }

    @BeforeEach
    void seed() {
        userId = jdbc.queryForObject(
                "INSERT INTO users (email, password_hash, display_name, timezone)"
                        + " VALUES (?, ?, ?, ?) RETURNING id",
                Long.class,
                "u-" + UUID.randomUUID() + "@lifewise.test",
                "test-hash-1234567890",
                "test-user",
                "UTC");
    }

    @AfterEach
    void truncateState() {
        jdbc.execute("TRUNCATE TABLE outbox_events, expenses, expense_categories, budgets"
                + " RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("10 并发 ensureUserDefault → DB 1 row, id 幂等, 0 AssertionFailure")
    void ensureUserDefault_concurrent_invocation_yields_one_row_and_no_session_pollution()
            throws InterruptedException {
        // 拦截 Hibernate 内部 AssertionFailure 日志（HHH000099 prefix）反向监测
        // session pollution；UPSERT 路径下应 0 命中
        Logger hibernateLogger = (Logger) LoggerFactory.getLogger("org.hibernate");
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        hibernateLogger.addAppender(listAppender);
        try {
            int n = 10;
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(n);
            ExecutorService pool = Executors.newFixedThreadPool(n);
            List<Long> observedIds = Collections.synchronizedList(new ArrayList<>());
            AtomicReference<Throwable> firstError = new AtomicReference<>();

            for (int i = 0; i < n; i++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        // UPSERT 路径：ensureUserDefault 返回默认 category id
                        Long id = categorySeedService.ensureUserDefault(userId);
                        observedIds.add(id);
                    } catch (Throwable t) {
                        firstError.compareAndSet(null, t);
                    } finally {
                        done.countDown();
                    }
                });
            }

            start.countDown();
            assertThat(done.await(30, TimeUnit.SECONDS))
                    .as("10 个并发线程必须在 30s 内全部完成")
                    .isTrue();
            pool.shutdown();
            assertThat(firstError.get())
                    .as("并发线程中不应有异常逃出到外层（UPSERT 路径零 exception）")
                    .isNull();

            // 断言 1: DB 只有 1 行 user_default category
            long defaultCount = categoryRepository.findAll().stream()
                    .filter(c -> userId.equals(c.getUserId()))
                    .filter(ExpenseCategory::isUserDefault)
                    .filter(c -> c.getDeletedAt() == null)
                    .count();
            assertThat(defaultCount)
                    .as("DB 必须只有 1 行 user_default category for userId=%d", userId)
                    .isEqualTo(1);

            // 断言 2: 所有线程拿到的 id 完全一致（外部观察者视角的幂等）
            assertThat(observedIds).hasSize(n);
            assertThat(new HashSet<>(observedIds))
                    .as("所有并发线程必须拿同一 category id（证明幂等）")
                    .hasSize(1);

            // 断言 3: 0 次 Hibernate AssertionFailure（反向监测 session pollution）
            long assertionFailures = listAppender.list.stream()
                    .filter(e -> e.getLevel().equals(Level.ERROR))
                    .filter(e -> e.getFormattedMessage().contains("HHH000099"))
                    .count();
            assertThat(assertionFailures)
                    .as("UPSERT 路径下 0 触发 Hibernate AssertionFailure（消除 session pollution）")
                    .isEqualTo(0);
        } finally {
            hibernateLogger.detachAppender(listAppender);
            listAppender.stop();
        }
    }
}
