package com.lifewise.shared.integration.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ResourceNotFoundException 单测（plan-shared-integration §2.2 + §5.2）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code port_should_reject_cross_user_access} — 异常区分 NOT_FOUND vs CROSS_USER_ACCESS</li>
 *   <li>{@code port_should_handle_empty_optional} — 业务层显式处理 empty 时抛此异常</li>
 * </ul>
 */
@DisplayName("ResourceNotFoundException 跨模块只读异常")
class ResourceNotFoundExceptionTest {

    @Test
    @DisplayName("未声明 forUser 时构造为 NOT_FOUND")
    void not_found_by_default() {
        ResourceNotFoundException ex = new ResourceNotFoundException("task", 99L);

        assertThat(ex.resourceType()).isEqualTo("task");
        assertThat(ex.resourceId()).isEqualTo(99L);
        assertThat(ex.forUser()).isNull();
        assertThat(ex.getMessage())
                .contains("task")
                .contains("99");
    }

    @Test
    @DisplayName("declared forUser=true 时构造为 CROSS_USER_ACCESS（防止枚举攻击）")
    void cross_user_access_when_for_user_true() {
        ResourceNotFoundException ex = new ResourceNotFoundException("task", 99L, true);

        assertThat(ex.forUser()).isTrue();
        assertThat(ex.getMessage())
                .as("错误信息应包含 CROSS_USER 标签，便于审计")
                .containsIgnoringCase("cross_user");
    }

    @Test
    @DisplayName("extends RuntimeException（unchecked，business 上层就近捕获）")
    void is_unchecked_runtime_exception() {
        ResourceNotFoundException ex = new ResourceNotFoundException("task", 1L);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("三参数构造（resourceType + messageOverride + resourceId）为合法可变形态")
    void three_arg_constructor_passes_message_override() {
        ResourceNotFoundException ex = new ResourceNotFoundException(
                "task", "task 99 was deleted 30 days ago", 99L);
        assertThat(ex.resourceType()).isEqualTo("task");
        assertThat(ex.resourceId()).isEqualTo(99L);
        assertThat(ex.getMessage()).contains("task 99 was deleted 30 days ago");
    }
}
