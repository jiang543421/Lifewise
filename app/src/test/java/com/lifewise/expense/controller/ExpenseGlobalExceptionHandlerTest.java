package com.lifewise.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lifewise.expense.domain.enums.BudgetScope;
import com.lifewise.expense.domain.enums.PayMethod;
import com.lifewise.expense.dto.BudgetRequest;
import com.lifewise.expense.dto.CategoryCreateRequest;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.service.BudgetService;
import com.lifewise.expense.service.CategoryService;
import com.lifewise.expense.service.ExpenseService;
import com.lifewise.expense.service.exception.BudgetAlreadyExistsException;
import com.lifewise.expense.service.exception.BudgetNotFoundException;
import com.lifewise.expense.service.exception.CategoryHasBudgetException;
import com.lifewise.expense.service.exception.CategoryNameExistsException;
import com.lifewise.expense.service.exception.CategoryNotFoundException;
import com.lifewise.expense.service.exception.CategoryProtectedException;
import com.lifewise.expense.service.exception.ExpenseNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@link ExpenseGlobalExceptionHandler} 各分支覆盖（plan-03-expense §2.2）。
 *
 * <p>目标：把 {@code com.lifewise.expense.controller} 行覆盖从 75.9% 拉到 ≥ 85%，
 * 覆盖 {@code codeFromType} switch 的 3 个 domain 分支 + {@code MethodArgumentNotValidException} 详情路径 +
 * 5 个 envelope 完整形状（trace_id + code + message）。
 *
 * <p>完整 17 端点 / service 层契约由 {@link ExpenseWebMvcTest} 覆盖；本文件只补 controller advice 分支。
 */
@WebMvcTest(controllers = {ExpenseController.class, CategoryController.class, BudgetController.class})
@Import(ExpenseGlobalExceptionHandler.class)
class ExpenseGlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;

    @MockBean ExpenseService expenseService;
    @MockBean CategoryService categoryService;
    @MockBean BudgetService budgetService;

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    // ---------- handleNotFound：codeFromType switch 3 个 domain 分支 ----------

    @Test
    void expense_not_found_maps_404_with_EXPENSE_NOT_FOUND() throws Exception {
        when(expenseService.findById(anyLong(), eq(99L)))
            .thenThrow(new ExpenseNotFoundException(99L));

        mockMvc.perform(get("/api/expenses/99").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void budget_not_found_maps_404_with_BUDGET_NOT_FOUND() throws Exception {
        doThrow(new BudgetNotFoundException(50L))
            .when(budgetService).softDelete(anyLong(), eq(50L));

        mockMvc.perform(delete("/api/budgets/50").header("X-User-Id", "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BUDGET_NOT_FOUND"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void category_not_found_maps_404_with_CATEGORY_NOT_FOUND() throws Exception {
        when(categoryService.update(anyLong(), eq(11L), any()))
            .thenThrow(new CategoryNotFoundException(11L));

        mockMvc.perform(put("/api/expense-categories/11")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NOT_FOUND"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    // ---------- handleValidation：MethodArgumentNotValidException 详情路径 ----------

    @Test
    void validation_error_returns_400_with_field_details_envelope() throws Exception {
        // ExpenseCreateRequest.amountCents 是 @Positive(>0)；传 0 触发 @Valid 失败
        // categoryService.loadOwnedCategory 在 @Valid 之后才调用，需要 stub 防止 NPE
        when(categoryService.loadOwnedCategory(anyLong(), eq(11L)))
            .thenReturn(org.mockito.Mockito.mock(com.lifewise.expense.domain.ExpenseCategory.class));

        ExpenseCreateRequest bad = new ExpenseCreateRequest(
                11L, 0L, PayMethod.ALIPAY,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                null, "CNY");

        mockMvc.perform(post("/api/expenses")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("request validation failed"))
                .andExpect(jsonPath("$.error.trace_id").exists())
                .andExpect(jsonPath("$.error.details.errors").isArray());
    }

    // ---------- handleCategoryName / handleCategoryProtected / handleCategoryHasBudget ----------

    @Test
    void category_name_exists_maps_409_with_full_envelope() throws Exception {
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new CategoryNameExistsException("咖啡"));

        CategoryCreateRequest req = new CategoryCreateRequest("咖啡", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NAME_EXISTS"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("咖啡")))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void category_protected_maps_400_with_full_envelope() throws Exception {
        when(categoryService.update(anyLong(), eq(5L), any()))
            .thenThrow(new CategoryProtectedException(5L));

        mockMvc.perform(put("/api/expense-categories/5")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_PROTECTED"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void category_has_budget_maps_400_with_full_envelope() throws Exception {
        doThrow(new CategoryHasBudgetException(11L))
            .when(categoryService).softDelete(anyLong(), eq(11L));

        mockMvc.perform(delete("/api/expense-categories/11").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CATEGORY_HAS_BUDGET"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    // ---------- handleBudgetExists ----------

    @Test
    void budget_already_exists_maps_409_with_full_envelope() throws Exception {
        when(budgetService.create(anyLong(), any()))
            .thenThrow(new BudgetAlreadyExistsException(
                    7L, BudgetScope.CATEGORY, 11L, 2026, 8));

        BudgetRequest req = new BudgetRequest(BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true);
        mockMvc.perform(post("/api/budgets")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    // ---------- handleMissingUser 完整 envelope 断言 ----------

    @Test
    void missing_user_id_fails_open_to_user_1() throws Exception {
        // P1-4 fail-safe：missing header 降级到 userId=1。
        // 该测试覆盖 envelope handler 在 fail-open 路径下不应被触发。
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- positive-path 覆盖：剩余 controller 方法（update/restore/mute/list/listSystem） ----------

    @Test
    void expense_update_returns_200() throws Exception {
        ExpenseCreateRequest req = new ExpenseCreateRequest(
                11L, 2000L, PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                null, "CNY");
        com.lifewise.expense.dto.ExpenseView view =
                new com.lifewise.expense.dto.ExpenseView(
                        1L, 7L, 11L, 2000L, "CNY", PayMethod.CASH,
                        java.time.LocalDate.of(2026, 8, 3),
                        OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                        null);
        when(expenseService.update(anyLong(), eq(1L), any())).thenReturn(view);

        mockMvc.perform(put("/api/expenses/1")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void expense_restore_returns_200() throws Exception {
        com.lifewise.expense.dto.ExpenseView view =
                new com.lifewise.expense.dto.ExpenseView(
                        1L, 7L, 11L, 2000L, "CNY", PayMethod.CASH,
                        java.time.LocalDate.of(2026, 8, 3),
                        OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                        null);
        when(expenseService.findById(anyLong(), eq(1L))).thenReturn(view);

        mockMvc.perform(post("/api/expenses/1/restore").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void budget_mute_returns_200() throws Exception {
        com.lifewise.expense.dto.BudgetView view = new com.lifewise.expense.dto.BudgetView(
                1L, 7L, BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true, java.time.LocalDate.of(2026, 8, 20));
        when(budgetService.mute(anyLong(), eq(1L), any())).thenReturn(view);

        com.lifewise.expense.dto.BudgetMuteRequest muteReq =
                new com.lifewise.expense.dto.BudgetMuteRequest(java.time.LocalDate.of(2026, 8, 20));
        mockMvc.perform(post("/api/budgets/1/mute")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(muteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notify_muted_until").value("2026-08-20"));
    }

    @Test
    void budget_list_returns_200() throws Exception {
        when(budgetService.list(anyLong(), eq(2026), eq(8)))
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/budgets?year=2026&month=8").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void category_list_returns_200() throws Exception {
        when(categoryService.list(anyLong()))
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/expense-categories").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void category_list_system_returns_200() throws Exception {
        when(categoryService.listSystem())
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/expense-categories/system").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ---------- plan-03 review H5：IllegalArgumentException → 400 INVALID_INPUT ----------

    @Test
    void illegal_argument_throws_400_with_INVALID_INPUT_envelope() throws Exception {
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException("amount must be positive"));

        CategoryCreateRequest req = new CategoryCreateRequest("咖啡", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.message").value("amount must be positive"));
    }

    @Test
    void illegal_argument_envelope_has_trace_id() throws Exception {
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException("x"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void illegal_argument_message_passes_through_when_safe() throws Exception {
        // commit #8a-1b rename：原 illegal_argument_message_sanitized 行为是"安全字面量透传"，
        // 原名 "sanitized" 误导。SafeMessageSanitizer 对无 LEAK_PATTERNS 的 message 原样透传。
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException("amount must be positive"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        var result = mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // 安全 message 透传；同时必须不出现 SQL/堆栈关键词
        org.assertj.core.api.Assertions.assertThat(body)
                .contains("amount must be positive");
        String[] forbidden = {"INSERT", "UPDATE", "DELETE", "SELECT", "ALTER", "DROP", "duplicate key", "constraint"};
        boolean leaked = Arrays.stream(forbidden).anyMatch(body.toUpperCase()::contains);
        org.assertj.core.api.Assertions.assertThat(leaked)
                .as("IllegalArgumentException envelope must not contain SQL/constraint keywords")
                .isFalse();
    }

    // ---------- commit #8a-1b（plan-03 review MEDIUM-HIGH）：SafeMessageSanitizer 端到端覆盖 ----------

    @Test
    void envelope_sanitizes_illegal_argument_with_sql_keywords() throws Exception {
        // service 层若误传含 SQL 关键词的 message，envelope 应降级为 "request failed"
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException(
                    "could not execute statement [INSERT INTO expense_categories ...]"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("request failed"));
    }

    @Test
    void envelope_sanitizes_illegal_argument_with_constraint_phrase() throws Exception {
        // PG 错误短语（duplicate key / constraint / violates）触发降级
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException(
                    "duplicate key value violates unique constraint \"uq_expense_categories_user_name\""));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        var result = mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        // envelope 整体不应出现 SQL / PG 错误短语（断言 envelope 字段 + 整 body）
        org.assertj.core.api.Assertions.assertThat(body).contains("\"message\":\"request failed\"");
        org.assertj.core.api.Assertions.assertThat(body.toUpperCase())
                .doesNotContain("DUPLICATE KEY")
                .doesNotContain("VIOLATES")
                .doesNotContain("CONSTRAINT");
    }

    @Test
    void envelope_sanitizes_illegal_argument_with_stack_marker() throws Exception {
        // Java 堆栈 frame 前缀（\tat / Caused by:）触发降级
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new IllegalArgumentException(
                    "boom\n\tat com.lifewise.expense.service.CategoryService.create(CategoryService.java:42)"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("request failed"));
    }

    // ---------- plan-03 review H5：DataIntegrityViolationException → 409 DATA_CONFLICT ----------

    @Test
    void data_integrity_violation_throws_409_with_DATA_CONFLICT_envelope() throws Exception {
        // 模拟 service 层 catch 不到，漏网到 Spring DAO 层
        // 真实 Spring 异常 message 含 "could not execute statement [INSERT ...]" 等
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new DataIntegrityViolationException(
                    "could not execute statement [INSERT INTO expense_categories ...] "
                            + "[duplicate key value violates unique constraint \"uq_expense_categories_user_name\"]"));

        CategoryCreateRequest req = new CategoryCreateRequest("咖啡", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("DATA_CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("data conflict (duplicate or invalid reference)"));
    }

    @Test
    void data_integrity_violation_does_not_leak_sql() throws Exception {
        // 关键安全断言：SQL 关键词 / 约束名 / 表名 全部不出现于 envelope body
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new DataIntegrityViolationException(
                    "could not execute statement [INSERT INTO expense_categories ...] "
                            + "[duplicate key value violates unique constraint \"uq_x\"]"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        var result = mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        String[] forbidden = {"INSERT", "UPDATE", "DELETE", "SELECT", "ALTER", "DROP",
                              "duplicate key", "constraint", "uq_x", "expense_categories"};
        boolean leaked = Arrays.stream(forbidden).anyMatch(body.toUpperCase()::contains);
        org.assertj.core.api.Assertions.assertThat(leaked)
                .as("DataIntegrityViolationException envelope must not leak SQL/constraint/table name")
                .isFalse();
    }

    @Test
    void data_integrity_violation_envelope_has_trace_id() throws Exception {
        when(categoryService.create(anyLong(), any()))
            .thenThrow(new DataIntegrityViolationException("x"));

        CategoryCreateRequest req = new CategoryCreateRequest("x", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    // ---------- plan-03 review H5：MethodArgumentTypeMismatchException → 400 INVALID_INPUT ----------

    @Test
    void type_mismatch_throws_400_with_field_details() throws Exception {
        // /api/expenses/{id} 的 id 是 Long，传 "abc" 触发 MethodArgumentTypeMismatchException
        mockMvc.perform(get("/api/expenses/abc").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.details.errors").isArray())
                .andExpect(jsonPath("$.error.details.errors[0].field").value("id"));
    }

    @Test
    void type_mismatch_envelope_includes_param_name_and_expected_type() throws Exception {
        // 验证 field=id（参数名）+ message 提到期望类型 Long
        mockMvc.perform(get("/api/expenses/abc").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.details.errors[0].message")
                        .value(org.hamcrest.Matchers.containsString("Long")));
    }

    @Test
    void type_mismatch_envelope_has_trace_id() throws Exception {
        mockMvc.perform(get("/api/expenses/abc").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.trace_id").exists());
    }
}