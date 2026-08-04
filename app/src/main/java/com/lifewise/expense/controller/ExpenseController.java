package com.lifewise.expense.controller;

import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.dto.ExpenseCreateRequest;
import com.lifewise.expense.dto.ExpenseUpdateRequest;
import com.lifewise.expense.dto.ExpenseView;
import com.lifewise.expense.service.CategoryService;
import com.lifewise.expense.service.ExpenseService;
import com.lifewise.expense.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 消费 REST 端点（plan-03-expense §1.1 6 端点）。所有响应包装在 {@link ApiResponse} 中。 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final CategoryService categoryService;

    public ExpenseController(ExpenseService expenseService, CategoryService categoryService) {
        this.expenseService = expenseService;
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<ExpenseView>> list(@CurrentUser Long userId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(expenseService.listInRange(userId, from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<ExpenseView> findById(@CurrentUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(expenseService.findById(userId, id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ExpenseView> create(@CurrentUser Long userId, @Valid @RequestBody ExpenseCreateRequest req) {
        ExpenseCategory category = categoryService.loadOwnedCategory(userId, req.categoryId());
        return ApiResponse.ok(expenseService.create(userId, req, category));
    }

    @PutMapping("/{id}")
    public ApiResponse<ExpenseView> update(@CurrentUser Long userId, @PathVariable Long id,
                               @Valid @RequestBody ExpenseUpdateRequest req) {
        return ApiResponse.ok(expenseService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@CurrentUser Long userId, @PathVariable Long id) {
        expenseService.softDelete(userId, id);
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<ExpenseView> restore(@CurrentUser Long userId, @PathVariable Long id) {
        expenseService.restore(userId, id);
        return ApiResponse.ok(expenseService.findById(userId, id));
    }
}