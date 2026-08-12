package com.lifewise.expense.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.expense.domain.Expense;
import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.domain.enums.BudgetScope;
import com.lifewise.expense.domain.enums.PayMethod;
import com.lifewise.expense.dto.BudgetMuteRequest;
import com.lifewise.expense.dto.BudgetRequest;
import com.lifewise.expense.dto.BudgetView;
import com.lifewise.expense.dto.CategoryCreateRequest;
import com.lifewise.expense.dto.CategoryUpdateRequest;
import com.lifewise.expense.dto.CategoryView;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.dto.ExpenseUpdateRequest;
import com.lifewise.expense.dto.ExpenseView;
import com.lifewise.expense.dto.StatsView;
import com.lifewise.expense.service.BudgetService;
import com.lifewise.expense.service.CategoryService;
import com.lifewise.expense.service.ExpenseService;
import com.lifewise.expense.service.StatsService;
import com.lifewise.expense.service.exception.BudgetAlreadyExistsException;
import com.lifewise.expense.service.exception.CategoryHasBudgetException;
import com.lifewise.expense.service.exception.CategoryNameExistsException;
import com.lifewise.expense.service.exception.CategoryProtectedException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 4 controllers 的 WebMvc 集成（plan-03-expense §1.1 17 端点）。
 *
 * <p>本文件覆盖代表性场景：CRUD 成功、跨用户访问（404）、唯一性冲突、分类保护、
 * 预算重复、缺 X-User-Id（401）。完整边界由 service 层单测保证。
 */
@WebMvcTest(controllers = {ExpenseController.class, CategoryController.class,
        BudgetController.class, StatsController.class})
@Import(ExpenseGlobalExceptionHandler.class)
class ExpenseWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ExpenseService expenseService;
    @MockBean CategoryService categoryService;
    @MockBean BudgetService budgetService;
    @MockBean StatsService statsService;

    // ---------- ExpenseController ----------

    @Test
    void list_expenses_returns_200_with_views() throws Exception {
        Expense e = Expense.create(7L, 11L, LocalDate.of(2026, 8, 1), "UTC",
                1000L, "CNY", PayMethod.CASH,
                OffsetDateTime.of(2026, 8, 1, 10, 0, 0, 0, ZoneOffset.UTC), null);
        e.setIdInternal(1L);
        when(expenseService.listInRange(anyLong(), any(), any()))
            .thenReturn(List.of(ExpenseView.from(e)));

        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void list_expenses_without_user_id_fails_open_to_user_1() throws Exception {
        // P1-4 fail-safe：missing header 降级到 userId=1（nginx 故障兜底）。
        // 列表调用应穿透到 service，返回空列表 200；mock 未设值时返回 List.of()。
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ---------- C3 防御层：CurrentUserArgumentResolver 格式校验边界（task + expense 同步）----------
    // 校验链：缺失 → 非数字 → 长度超 19 → 非正数 → 401 TOKEN_INVALID envelope

    @Test
    void non_numeric_user_id_returns_401_TOKEN_INVALID() throws Exception {
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31")
                        .header("X-User-Id", "abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void user_id_exceeds_max_length_returns_401_TOKEN_INVALID() throws Exception {
        // 20 位数字（Long.MAX_VALUE 是 19 位）→ 长度超限
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31")
                        .header("X-User-Id", "12345678901234567890"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void non_positive_user_id_returns_401_TOKEN_INVALID() throws Exception {
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31")
                        .header("X-User-Id", "0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        mockMvc.perform(get("/api/expenses?from=2026-08-01&to=2026-08-31")
                        .header("X-User-Id", "-1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
    }

    @Test
    void create_expense_returns_201() throws Exception {
        ExpenseCategory cat = ExpenseCategory.createUserCategory(7L, "咖啡", null, null, null, 0);
        cat.setIdInternal(11L);
        Expense created = Expense.create(7L, 11L, LocalDate.of(2026, 8, 3), "UTC",
                3500L, "CNY", PayMethod.ALIPAY,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC), "早咖啡");
        created.setIdInternal(100L);
        when(categoryService.loadOwnedCategory(anyLong(), eq(11L))).thenReturn(cat);
        when(expenseService.create(anyLong(), any(ExpenseCreateRequest.class), eq(cat)))
            .thenReturn(ExpenseView.from(created));

        ExpenseCreateRequest req = new ExpenseCreateRequest(
                11L, 3500L, PayMethod.ALIPAY,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                "早咖啡", "CNY");
        mockMvc.perform(post("/api/expenses")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(100))
                .andExpect(jsonPath("$.data.amount_cents").value(3500));
    }

    @Test
    void create_expense_with_zero_amount_returns_400() throws Exception {
        ExpenseCreateRequest req = new ExpenseCreateRequest(
                11L, 0L, PayMethod.ALIPAY,
                OffsetDateTime.of(2026, 8, 3, 9, 30, 0, 0, ZoneOffset.UTC),
                null, "CNY");
        mockMvc.perform(post("/api/expenses")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void soft_delete_expense_returns_204() throws Exception {
        mockMvc.perform(delete("/api/expenses/1").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());
    }

    // ---------- CategoryController ----------

    @Test
    void create_category_returns_201() throws Exception {
        ExpenseCategory cat = ExpenseCategory.createUserCategory(7L, "咖啡", "☕", "#A0522D", null, 0);
        cat.setIdInternal(11L);
        when(categoryService.create(anyLong(), any(CategoryCreateRequest.class)))
            .thenReturn(CategoryView.from(cat));

        CategoryCreateRequest req = new CategoryCreateRequest("咖啡", "☕", "#A0522D", null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("咖啡"));
    }

    @Test
    void create_category_duplicate_returns_409() throws Exception {
        when(categoryService.create(anyLong(), any(CategoryCreateRequest.class)))
            .thenThrow(new CategoryNameExistsException("咖啡"));

        CategoryCreateRequest req = new CategoryCreateRequest("咖啡", null, null, null, 0);
        mockMvc.perform(post("/api/expense-categories")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NAME_EXISTS"));
    }

    @Test
    void delete_default_category_returns_400() throws Exception {
        org.mockito.Mockito.doThrow(new CategoryProtectedException(5L))
            .when(categoryService).softDelete(anyLong(), eq(5L));

        mockMvc.perform(delete("/api/expense-categories/5").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_PROTECTED"));
    }

    @Test
    void delete_category_with_budget_returns_400() throws Exception {
        org.mockito.Mockito.doThrow(new CategoryHasBudgetException(11L))
            .when(categoryService).softDelete(anyLong(), eq(11L));

        mockMvc.perform(delete("/api/expense-categories/11").header("X-User-Id", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_HAS_BUDGET"));
    }

    // ---------- BudgetController ----------

    @Test
    void create_budget_returns_201() throws Exception {
        BudgetView view = new BudgetView(50L, 7L, BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true, null);
        when(budgetService.create(anyLong(), any(BudgetRequest.class))).thenReturn(view);

        BudgetRequest req = new BudgetRequest(BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true);
        mockMvc.perform(post("/api/budgets")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(50))
                .andExpect(jsonPath("$.data.amount_cents").value(10000));
    }

    @Test
    void create_duplicate_budget_returns_409() throws Exception {
        when(budgetService.create(anyLong(), any(BudgetRequest.class)))
            .thenThrow(new BudgetAlreadyExistsException(7L, BudgetScope.CATEGORY, 11L, 2026, 8));

        BudgetRequest req = new BudgetRequest(BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true);
        mockMvc.perform(post("/api/budgets")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BUDGET_ALREADY_EXISTS"));
    }

    @Test
    void mute_budget_returns_200() throws Exception {
        BudgetView view = new BudgetView(1L, 7L, BudgetScope.CATEGORY, 11L,
                2026, 8, 10000L, "CNY", true, LocalDate.of(2026, 8, 20));
        when(budgetService.mute(anyLong(), eq(1L), any(BudgetMuteRequest.class))).thenReturn(view);

        BudgetMuteRequest req = new BudgetMuteRequest(LocalDate.of(2026, 8, 20));
        mockMvc.perform(post("/api/budgets/1/mute")
                        .header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notify_muted_until").value("2026-08-20"));
    }

    // ---------- StatsController ----------

    @Test
    void stats_returns_200() throws Exception {
        when(statsService.stats(anyLong(), any(), any(), eq("category")))
            .thenReturn(new StatsView(10000L, "CNY", List.of(), List.of()));

        mockMvc.perform(get("/api/expenses/stats?from=2026-08-01&to=2026-08-31&groupBy=category")
                        .header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total_cents").value(10000));
    }

    @Test
    void stats_without_user_id_fails_open_to_user_1() throws Exception {
        // P1-4 fail-safe：missing header 降级到 userId=1。
        mockMvc.perform(get("/api/expenses/stats?from=2026-08-01&to=2026-08-31"))
                .andExpect(status().isOk());
    }
}