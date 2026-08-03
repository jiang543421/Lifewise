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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
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

        mockMvc.perform(get("/api/expenses/99").header("X-User-Id", "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("EXPENSE_NOT_FOUND"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    @Test
    void budget_not_found_maps_404_with_BUDGET_NOT_FOUND() throws Exception {
        doThrow(new BudgetNotFoundException(50L))
            .when(budgetService).softDelete(anyLong(), eq(50L));

        mockMvc.perform(delete("/api/budgets/50").header("X-User-Id", "7"))
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
                        .header("X-User-Id", "7")
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
                        .header("X-User-Id", "7")
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
                        .header("X-User-Id", "7")
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
                        .header("X-User-Id", "7")
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

        mockMvc.perform(delete("/api/expense-categories/11").header("X-User-Id", "7"))
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
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.error.trace_id").exists());
    }

    // ---------- handleMissingUser 完整 envelope 断言 ----------

    @Test
    void missing_user_id_maps_401_with_TOKEN_INVALID_envelope() throws Exception {
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"))
                .andExpect(jsonPath("$.error.trace_id").exists());
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
                        .header("X-User-Id", "7")
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

        mockMvc.perform(post("/api/expenses/1/restore").header("X-User-Id", "7"))
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
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(muteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notify_muted_until").value("2026-08-20"));
    }

    @Test
    void budget_list_returns_200() throws Exception {
        when(budgetService.list(anyLong(), eq(2026), eq(8)))
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/budgets?year=2026&month=8").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void category_list_returns_200() throws Exception {
        when(categoryService.list(anyLong()))
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/expense-categories").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void category_list_system_returns_200() throws Exception {
        when(categoryService.listSystem())
            .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/expense-categories/system").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}