package com.lifewise.shared.integration.port.snapshot;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TaskSnapshot 单测（plan-shared-integration §5.2 port_should_return_snapshot_not_entity）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>snapshot record 不可变（反射断言）</li>
 *   <li>accessor 返回值与构造一致</li>
 *   <li>snapshot 之间 equals / hashCode / toString 走 record 默认实现</li>
 * </ul>
 *
 * <p>其他 5 个 snapshot（PlanSnapshot / DailySnapshot / ExpenseSnapshot / MealSnapshot / AiSnapshot）
 * 结构同构，由各 Port 自行定义；本测试仅覆盖 task 作为代表性样本。
 */
@DisplayName("TaskSnapshot 跨模块只读快照")
class TaskSnapshotTest {

    @Test
    @DisplayName("record 字段访问器正常返回")
    void record_accessors() {
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 31, 10, 0, 0, 0, ZoneOffset.UTC);
        TaskSnapshot s = new TaskSnapshot(99L, 42L, "写周报", "OPEN", now, null);

        assertThat(s.id()).isEqualTo(99L);
        assertThat(s.userId()).isEqualTo(42L);
        assertThat(s.title()).isEqualTo("写周报");
        assertThat(s.status()).isEqualTo("OPEN");
        assertThat(s.createdAt()).isEqualTo(now);
        assertThat(s.completedAt()).isNull();
    }

    @Test
    @DisplayName("snapshot record 字段全部 final — 反射断言不可变")
    void record_fields_are_final() {
        for (Field f : TaskSnapshot.class.getDeclaredFields()) {
            assertThat(java.lang.reflect.Modifier.isFinal(f.getModifiers()))
                    .as("TaskSnapshot.%s 必须为 final（record 不变性）", f.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("snapshot 不暴露 setter（无 'setX' 风格方法）")
    void snapshot_has_no_setters() {
        List<String> setterNames = java.util.Arrays.stream(TaskSnapshot.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .filter(name -> name.startsWith("set") && name.length() > 3
                        && Character.isUpperCase(name.charAt(3)))
                .toList();
        assertThat(setterNames)
                .as("TaskSnapshot 不应有任何 setter 方法")
                .isEmpty();
    }

    @Test
    @DisplayName("snapshot 间 equals / hashCode / toString 走 record 默认实现")
    void snapshot_equality_by_value() {
        OffsetDateTime now = OffsetDateTime.of(2026, 7, 31, 10, 0, 0, 0, ZoneOffset.UTC);
        TaskSnapshot a = new TaskSnapshot(99L, 42L, "写周报", "OPEN", now, null);
        TaskSnapshot b = new TaskSnapshot(99L, 42L, "写周报", "OPEN", now, null);

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
        assertThat(a.toString()).contains("写周报");
    }
}
