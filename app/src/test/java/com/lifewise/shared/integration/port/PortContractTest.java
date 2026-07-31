package com.lifewise.shared.integration.port;

import static org.assertj.core.api.Assertions.assertThat;

import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Port 接口契约单测（plan-shared-integration §5.2）。
 *
 * <p>覆盖：
 * <ul>
 *   <li>{@code port_should_expose_readonly_view} — 接口无写方法（save/update/delete/create/upsert）</li>
 *   <li>{@code port_should_return_snapshot_not_entity} — 返回类型是 snapshot record</li>
 *   <li>{@code port_should_handle_empty_optional} — 单一 ID 查询返回 Optional</li>
 *   <li>{@code port_should_reject_cross_user_access} — userId 为第一参数（强制所有权校验）</li>
 * </ul>
 *
 * <p>本测试以 TaskReadPort 为代表样本；其他 5 个 Port（Plan/Daily/Expense/Meal/Ai）以
 * 反射一并校验（确保扩展时仍遵守规则）。
 */
@DisplayName("Port 跨模块只读契约")
class PortContractTest {

    @Test
    @DisplayName("所有 *ReadPort 接口都禁止写动作（save/update/delete/create/upsert）")
    void should_expose_readonly_view() {
        List<String> forbiddenSubstrings = List.of(
                "save", "update", "delete", "create", "upsert", "insert");
        for (Class<?> iface : allPorts()) {
            for (Method m : iface.getDeclaredMethods()) {
                String n = m.getName().toLowerCase();
                for (String forbidden : forbiddenSubstrings) {
                    assertThat(n)
                            .as("%s.%s 违反只读契约（关键字 %s）",
                                    iface.getSimpleName(), m.getName(), forbidden)
                            .doesNotContain(forbidden);
                }
            }
        }
    }

    @Test
    @DisplayName("所有 *ReadPort 方法第一参数必须是 userId 所有权校验入口")
    void first_arg_must_be_userId() {
        for (Class<?> iface : allPorts()) {
            for (Method m : iface.getDeclaredMethods()) {
                Parameter[] params = m.getParameters();
                if (params.length == 0) {
                    continue;
                }
                assertThat(params[0].getType())
                        .as("%s.%s 第一参数必须是 userId 类型",
                                iface.getSimpleName(), m.getName())
                        .isEqualTo(Long.class);
                assertThat(params[0].getName())
                        .as("%s.%s 第一参数名建议为 userId",
                                iface.getSimpleName(), m.getName())
                        .isEqualToIgnoringCase("userId");
            }
        }
    }

    @Test
    @DisplayName("TaskReadPort.findById 返回 Optional<TaskSnapshot>（snapshot 而不是 JPA entity）")
    void task_read_port_returns_snapshot() throws Exception {
        Method findById = TaskReadPort.class.getMethod("findById", Long.class, Long.class);
        assertThat(findById.getReturnType()).isEqualTo(Optional.class);

        ParameterizedType optionalSnapshot = (ParameterizedType) findById.getGenericReturnType();
        assertThat(optionalSnapshot.getActualTypeArguments()[0])
                .isEqualTo(TaskSnapshot.class);
    }

    @Test
    @DisplayName("所有 *ReadPort 集合返回必须是 List<snapshot>，禁用 Set/Map 等")
    void collection_returns_must_be_list() {
        for (Class<?> iface : allPorts()) {
            for (Method m : iface.getDeclaredMethods()) {
                Class<?> raw = m.getReturnType();
                if (raw == List.class || raw == Optional.class) {
                    continue;
                }
                if (raw == long.class || raw == Long.class
                        || raw == int.class || raw == Integer.class) {
                    continue;
                }
                assertThat(raw)
                        .as("%s.%s 返回 %s 不是 List/Optional/标量；只读端口禁用其他容器",
                                iface.getSimpleName(), m.getName(), raw.getSimpleName())
                        .isIn(List.class, Optional.class, long.class, Long.class,
                                int.class, Integer.class);
            }
        }
    }

    private static List<Class<?>> allPorts() {
        return List.of(TaskReadPort.class, PlanReadPort.class, DailyReadPort.class,
                ExpenseReadPort.class, MealReadPort.class, AiReadPort.class);
    }
}
