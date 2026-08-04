package com.lifewise.expense.controller;

import com.lifewise.expense.dto.BudgetMuteRequest;
import com.lifewise.expense.dto.BudgetRequest;
import com.lifewise.expense.dto.BudgetView;
import com.lifewise.expense.service.BudgetService;
import com.lifewise.expense.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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

/** 预算 REST 端点（plan-03-expense §1.1 5 端点）。 */
@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;

    public BudgetController(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @GetMapping
    public ApiResponse<List<BudgetView>> list(@CurrentUser Long userId,
                                  @RequestParam int year,
                                  @RequestParam int month) {
        return ApiResponse.ok(budgetService.list(userId, year, month));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BudgetView> create(@CurrentUser Long userId,
                              @Valid @RequestBody BudgetRequest req) {
        return ApiResponse.ok(budgetService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<BudgetView> update(@CurrentUser Long userId, @PathVariable Long id,
                              @Valid @RequestBody BudgetRequest req) {
        return ApiResponse.ok(budgetService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@CurrentUser Long userId, @PathVariable Long id) {
        budgetService.softDelete(userId, id);
    }

    @PostMapping("/{id}/mute")
    public ApiResponse<BudgetView> mute(@CurrentUser Long userId, @PathVariable Long id,
                            @RequestBody BudgetMuteRequest req) {
        return ApiResponse.ok(budgetService.mute(userId, id, req));
    }
}