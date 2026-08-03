package com.lifewise.expense;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.domain.enums.PayMethod;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.repository.BudgetRepository;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.expense.repository.ExpenseRepository;
import com.lifewise.expense.service.CategoryService;
import com.lifewise.expense.service.ExpenseService;
import com.lifewise.shared.integration.outbox.OutboxEventRepository;
import com.lifewise.shared.integration.port.ExpenseReadPort;
import com.lifewise.shared.integration.port.snapshot.ExpenseSnapshot;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Expense 模块端到端：POST 消费 → outbox 行可见 → ReadPort 可消费（plan-03 §5.6/§5.7）。
 *
 * <p>使用 zonky/embedded-postgres + Flyway 全量迁移。
 */
@DisplayName("Expense E2E + Outbox + ReadPort")
@SpringBootTest
class ExpenseE2EIT {

    private static EmbeddedPostgres PG;

    @Autowired private ExpenseService expenseService;
    @Autowired private CategoryService categoryService;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private OutboxEventRepository outboxRepository;
    @Autowired private ExpenseReadPort readPort;
    @Autowired private JdbcTemplate jdbc;

    private long userId;
    private long categoryId;

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

        ExpenseCategory cat = ExpenseCategory.createUserCategory(userId, "咖啡", null, null, null, 0);
        cat.setIdInternal(null); // 让 PG 分配
        categoryRepository.saveAndFlush(cat);
        categoryId = cat.getId();
    }

    @AfterEach
    void truncateState() {
        jdbc.execute("TRUNCATE TABLE outbox_events, expenses, expense_categories, budgets"
                + " RESTART IDENTITY CASCADE");
    }

    @Test
    @DisplayName("create expense -> expenses row + outbox row visible")
    void create_emits_outbox_and_persists_expense() {
        ExpenseCategory category = categoryService.loadOwnedCategory(userId, categoryId);
        ExpenseCreateRequest req = new ExpenseCreateRequest(
                categoryId,
                3500L,
                PayMethod.ALIPAY,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                "早咖啡",
                "CNY");

        var view = expenseService.create(userId, req, category);

        assertThat(view.id()).isNotNull();
        assertThat(view.amountCents()).isEqualTo(3500L);

        // expenses 表
        Expense persisted = expenseRepository.findById(view.id()).orElseThrow();
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getCategoryId()).isEqualTo(categoryId);

        // outbox 行
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE event_type = ? AND user_id = ?",
                Integer.class, "expense.created", userId);
        assertThat(count).isGreaterThanOrEqualTo(1);

        String aggregateType = jdbc.queryForObject(
                "SELECT aggregate_type FROM outbox_events WHERE event_type = ? AND user_id = ? ORDER BY id DESC LIMIT 1",
                String.class, "expense.created", userId);
        assertThat(aggregateType).isEqualTo("expense");
    }

    @Test
    @DisplayName("ReadPort returns cents snapshot and category aggregation")
    void readPort_returns_correct_snapshots_and_sums() {
        ExpenseCategory category = categoryService.loadOwnedCategory(userId, categoryId);
        expenseService.create(userId, new ExpenseCreateRequest(
                categoryId, 1000L, PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null, "CNY"),
                category);
        expenseService.create(userId, new ExpenseCreateRequest(
                categoryId, 2000L, PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 2, 10, 0, 0, 0, ZoneOffset.UTC), null, "CNY"),
                category);

        long total = readPort.sumInRange(userId,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(total).isEqualTo(3000L);

        List<com.lifewise.shared.integration.port.snapshot.CategoryTotal> byCat =
                readPort.sumByCategoryInRange(userId,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        assertThat(byCat)
                .filteredOn(t -> t.categoryId().equals(categoryId))
                .extracting(com.lifewise.shared.integration.port.snapshot.CategoryTotal::totalCents)
                .containsExactly(3000L);

        List<ExpenseSnapshot> snaps = readPort.findByCategory(userId, categoryId, 10);
        assertThat(snaps).hasSize(2);
        assertThat(snaps.get(0).amountCents()).isEqualTo(2000L); // occurredAt DESC
    }
}
