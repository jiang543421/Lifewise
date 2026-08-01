package com.lifewise.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link BaseEntity} 软删除 POJO 单测（plan-data-flyway §8 soft_delete_nullable）。
 *
 * <p>{@code id / createdAt / updatedAt} 由 JPA + {@link AuditListener} 维护，本测试只覆盖
 * 与 JPA 无关的 POJO 行为：{@link BaseEntity#softDelete()} / {@link BaseEntity#restore()} /
 * {@link BaseEntity#isDeleted()}。createdAt / updatedAt 写入通过 {@link AuditListenerTest} 验证。
 *
 * <p>BaseEntity 是 6 业务模块所有 JPA 实体的 @MappedSuperclass，本测试防止业务模块继承
 * 一个未测基类。
 */
@DisplayName("BaseEntity 软删除 POJO")
class BaseEntityTest {

    private TestEntity entity;

    @BeforeEach
    void setUp() {
        entity = new TestEntity();
    }

    @Test
    @DisplayName("新实体：id / createdAt / updatedAt / deletedAt 均为 null（由 JPA + AuditListener 填充）")
    void new_entity_should_have_all_managed_fields_null() {
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreatedAt()).isNull();
        assertThat(entity.getUpdatedAt()).isNull();
        assertThat(entity.getDeletedAt()).isNull();
    }

    @Test
    @DisplayName("新实体 isDeleted() == false")
    void new_entity_should_not_be_deleted() {
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("softDelete() → deletedAt 非空 + isDeleted() == true")
    void softDelete_should_set_deleted_at_and_mark_deleted() {
        OffsetDateTime beforeCall = OffsetDateTime.now();
        entity.softDelete();
        OffsetDateTime afterCall = OffsetDateTime.now();

        assertThat(entity.getDeletedAt())
                .as("softDelete 后 deletedAt 必须非空")
                .isNotNull();
        assertThat(entity.getDeletedAt())
                .as("deletedAt 应在调用时刻 ± 1s 内")
                .isBetween(beforeCall.minusSeconds(1), afterCall.plusSeconds(1));
        assertThat(entity.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("restore() 清空 deletedAt + isDeleted() == false")
    void restore_should_clear_deleted_at_and_mark_not_deleted() {
        entity.softDelete();
        assertThat(entity.isDeleted()).isTrue();

        entity.restore();

        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("restore() 在未删除实体上幂等（仍 isDeleted() == false）")
    void restore_should_be_idempotent_on_already_clean_entity() {
        assertThat(entity.isDeleted()).isFalse();

        entity.restore();

        assertThat(entity.getDeletedAt()).isNull();
        assertThat(entity.isDeleted()).isFalse();
    }

    /**
     * 测试用具体子类 — BaseEntity 是 abstract，禁止直接 new。
     */
    static class TestEntity extends BaseEntity {
        // 仅继承，无新增字段；本测试聚焦父类 POJO 行为
    }
}