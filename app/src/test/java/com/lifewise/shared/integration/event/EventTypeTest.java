package com.lifewise.shared.integration.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * EventType 枚举单测（plan-shared-integration §4 + §5.4 event_should_validate_payload_schema）。
 *
 * <p>校验：25 条事件名全部存在（外加 V33 plan-data-flyway §3.35 已锁定的 4 条 auth.*）；
 * 移除或重命名视为破坏性变更（DB CHECK 约束 data-model §6.4 BR-22）。
 */
@DisplayName("EventType 25 条事件枚举")
class EventTypeTest {

    @Test
    @DisplayName("全部 25 条业务事件名都存在（plan §4 列出）")
    void should_contain_all_25_event_types() {
        Set<String> expected = Set.of(
                "task.completed", "task.reopened", "task.created", "task.updated",
                "milestone.created", "milestone.updated", "milestone.completed", "milestone.missed",
                "habit.logged",
                "daily_report.created", "daily_report.updated", "ai.summary.generated",
                "meal.created",
                "expense.created",
                "budget.threshold",
                "plan.created",
                "ai.job.completed",
                "ai.report.feedback",
                "export.completed", "export.failed",
                "notification.requested",
                "auth.user.registered", "auth.user.logged_in",
                "auth.user.password_reset_requested", "auth.token.reuse_detected");
        assertThat(expected).hasSize(25);

        Set<String> actual = new HashSet<>();
        for (EventType t : EventType.values()) {
            actual.add(t.eventType());
        }
        assertThat(actual).containsAll(expected);
    }

    @Test
    @DisplayName("枚举值不允许重复 eventType 字符串")
    void event_type_strings_are_unique() {
        Set<String> seen = new HashSet<>();
        for (EventType t : EventType.values()) {
            assertThat(seen.add(t.eventType()))
                    .as("EventType %s 重复", t.eventType())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("eventType() 字符串为小写点分 snake_case")
    void event_type_uses_lowercase_dotted_snake() {
        for (EventType t : EventType.values()) {
            assertThat(t.eventType())
                    .as("%s 必为小写点分命名", t.eventType())
                    .matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)+");
        }
    }

    @Test
    @DisplayName("枚举数量 >= 25（forward-compat：允许 v1.1+ 增加事件）")
    void at_least_25_event_types() {
        assertThat(EventType.values().length).isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("valueOf 双向：enum.name() 与 eventType() 一一对应（DB CHECK 白名单对齐）")
    void names_and_event_types_are_paired() {
        for (EventType t : EventType.values()) {
            EventType back = EventType.valueOf(t.name());
            assertThat(back).isSameAs(t);
            assertThat(back.eventType()).isEqualTo(t.eventType());
        }
    }
}
