package com.lifewise.shared.integration.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * 一致性守卫：EventType enum ⇔ outbox_events.event_type CHECK（plan-03 B-3 修订）。
 *
 * <p>每次新增/重命名 outbox 事件必须同时更新：
 * <ul>
 *   <li>{@link EventType} enum（Java 端）</li>
 *   <li>最新 V*__*.sql 的 {@code CHECK(event_type IN (...))} 约束（DB 端）</li>
 * </ul>
 *
 * <p>本测试自动发现 db/migration/ 下含 {@code outbox_events_event_type_check}
 * 的最新 migration，取其 CHECK 列表与 enum 比对，杜绝手抄漂移。
 * 触发场景：
 * <ul>
 *   <li>加 enum 没改 SQL → CHECK 拒绝写入（生产 fail-fast）</li>
 *   <li>加 SQL 没改 enum → Java 端 publish 时构造 envelope 即失败</li>
 * </ul>
 *
 * <p>注意：扫描仅识别「包含 outbox_events_event_type_check 字样」的 migration，
 * 不限于最近一次；最新版以 {@code V<digits>__*.sql} 的数字版本号升序为准。
 * 不能用 {@code String.compareTo}——字典序会把 V100__x.sql 排在 V39__x.sql 前面，
 * 导致守卫比对的是旧 migration 的 CHECK 列表并虚假通过。
 */
class EventTypeSqlCheckConsistencyTest {

    private static final Pattern CHECK_IN = Pattern.compile(
            "CHECK\\s*\\(\\s*event_type\\s+IN\\s*\\((.*?)\\)\\s*\\)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private static final Pattern STRING_LITERAL = Pattern.compile("'([^']+)'");

    private static final Pattern V_MIGRATION = Pattern.compile("V(\\d+)__.*\\.sql");

    @Test
    void event_type_enum_matches_latest_migration_check() throws IOException {
        Path latestMigration = findLatestMigrationWithCheck();
        String sql = Files.readString(latestMigration, StandardCharsets.UTF_8);

        Matcher m = CHECK_IN.matcher(sql);
        assertThat(m.find())
            .as("No CHECK(event_type IN (...)) pattern found in %s", latestMigration)
            .isTrue();
        Set<String> checkTypes = parseCheckList(m.group(1));

        Set<String> enumTypes = Arrays.stream(EventType.values())
                .map(EventType::eventType)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        assertThat(enumTypes)
            .as("EventType enum must match CHECK list in %s. "
                    + "If you added an event, update BOTH the enum AND the migration. "
                    + "Missing enum: %s. Extra enum: %s.",
                    latestMigration,
                    diff(checkTypes, enumTypes),
                    diff(enumTypes, checkTypes))
            .isEqualTo(checkTypes);
    }

    /**
     * 从 CHECK IN(...) 内捕获的 raw 文本里抽取所有单引号字符串字面量。
     * 不再 split-by-','——后者会把首项与前导注释合并进同一 token，导致 startsWith('\'') 检查失败丢字。
     * 用 regex 提取所有 '...' 字面量，鲁棒地处理注释、换行、尾逗号。
     */
    private Set<String> parseCheckList(String rawList) {
        Set<String> set = new LinkedHashSet<>();
        Matcher m = STRING_LITERAL.matcher(rawList);
        while (m.find()) {
            set.add(m.group(1));
        }
        return set;
    }

    private static <T> Set<T> diff(Set<T> a, Set<T> b) {
        Set<T> result = new LinkedHashSet<>(a);
        result.removeAll(b);
        return result;
    }

    private Path findLatestMigrationWithCheck() throws IOException {
        ClassPathResource dir = new ClassPathResource("db/migration/");
        assertThat(dir.exists())
            .as("db/migration/ must exist on classpath")
            .isTrue();
        Path dirPath = dir.getFile().toPath();
        try (Stream<Path> paths = Files.list(dirPath)) {
            return paths
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("V") && name.endsWith(".sql");
                    })
                    .sorted(Comparator.comparingInt(
                            p -> extractVersion(p.getFileName().toString())))
                    .filter(EventTypeSqlCheckConsistencyTest::containsCheck)
                    .reduce((a, b) -> b)
                    .orElseThrow(() -> new AssertionError(
                            "No migration with outbox_events event_type CHECK found in db/migration/"));
        }
    }

    /**
     * 提取 {@code V<digits>__*.sql} 的数字版本号。不匹配返回 -1（被 sorted 排到最前，
     * 但 filter(containsCheck) 仍会过滤掉不相关的 V 迁移；负值在数字升序里属边界）。
     */
    private static int extractVersion(String filename) {
        Matcher m = V_MIGRATION.matcher(filename);
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static boolean containsCheck(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8)
                    .contains("outbox_events_event_type_check");
        } catch (IOException e) {
            return false;
        }
    }
}
