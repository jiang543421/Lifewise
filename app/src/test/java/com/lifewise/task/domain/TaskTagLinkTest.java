package com.lifewise.task.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.task.domain.TaskTagLink.Pk;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TaskTagLink 复合主键与实体行为测试（plan-01-task §7 盲区覆盖）。
 *
 * <p>{@link Pk#equals(Object)} / {@link Pk#hashCode()} 必须基于 (taskId, tagId) 双字段；任一字段为 null
 * 时仍可安全参与 HashSet，避免 NPE。{@link TaskTagLink} 工厂方法会写入 {@code createdAt} 时间戳。
 */
@DisplayName("TaskTagLink / Pk 复合主键")
class TaskTagLinkTest {

    @Test
    @DisplayName("Pk.equals：相同 (taskId, tagId) → 相等；HashSet 去重")
    void pk_equals_should_compare_both_fields_and_dedupe_in_hashset() {
        Pk a = new Pk(1L, 2L);
        Pk b = new Pk(1L, 2L);
        Pk c = new Pk(1L, 3L);
        Pk d = new Pk(2L, 2L);

        assertThat(a).isEqualTo(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(d);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());

        Set<Pk> set = new HashSet<>();
        set.add(a);
        set.add(b);
        set.add(c);
        assertThat(set).as("相同 Pk 二次添加应被 HashSet 去重").hasSize(2);
    }

    @Test
    @DisplayName("Pk.equals：null / 不同类型 → 不抛异常且返回 false")
    void pk_equals_should_handle_null_and_other_types_safely() {
        Pk p = new Pk(1L, 2L);

        assertThat(p).isNotEqualTo(null);
        assertThat(p).isNotEqualTo("1-2");
        assertThat(p.equals(p)).isTrue();
    }

    @Test
    @DisplayName("Pk.equals：含 null 字段的两个 Pk 不应抛 NPE")
    void pk_equals_should_tolerate_null_field_values() {
        Pk left = new Pk(null, 2L);
        Pk right = new Pk(1L, null);
        Pk both = new Pk(null, null);

        // equals/hashCode 必须容忍 null 字段，且语义合理
        assertThat(left).isNotEqualTo(right);
        assertThat(both.hashCode()).isEqualTo(both.hashCode());
        assertThat(left.hashCode()).isNotEqualTo(both.hashCode());
    }

    @Test
    @DisplayName("TaskTagLink 工厂方法：写入 (taskId, tagId) + createdAt 时间戳")
    void taskTagLink_factory_should_populate_id_and_timestamp() {
        OffsetDateTime before = OffsetDateTime.now().minusSeconds(1);
        TaskTagLink link = new TaskTagLink(10L, 20L);
        OffsetDateTime after = OffsetDateTime.now().plusSeconds(1);

        assertThat(link.getId()).isNotNull();
        assertThat(link.getId().getTaskId()).isEqualTo(10L);
        assertThat(link.getId().getTagId()).isEqualTo(20L);
        assertThat(link.getTaskId()).isEqualTo(10L);
        assertThat(link.getTagId()).isEqualTo(20L);
        assertThat(link.getCreatedAt())
                .as("createdAt 应在调用前后 1 秒窗口内")
                .isBetween(before, after);
    }

    @Test
    @DisplayName("TaskTagLink.Pk getter：暴露 taskId / tagId")
    void pk_getters_should_return_constructor_values() {
        Pk pk = new Pk(7L, 9L);
        assertThat(pk.getTaskId()).isEqualTo(7L);
        assertThat(pk.getTagId()).isEqualTo(9L);
    }
}