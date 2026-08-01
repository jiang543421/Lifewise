package com.lifewise.auth.domain;

import com.lifewise.common.BaseEntity;
import java.lang.reflect.Field;

/**
 * 测试用：通过反射为 {@link BaseEntity} 设置 id，模拟 JPA @GeneratedValue 回填。
 *
 * <p>仅 test scope；{@link BaseEntity#id} 是 private 无 setter，Hibernate 通过字段访问注入。
 */
public final class UserWithId {

    private UserWithId() {
    }

    public static void setId(BaseEntity entity, Long id) {
        try {
            Field f = BaseEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("failed to inject id for test entity", e);
        }
    }
}