package com.lifewise.expense.controller;

import com.lifewise.expense.dto.StatsView;
import com.lifewise.expense.service.StatsService;
import com.lifewise.expense.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计 REST 端点（plan-03-expense §1.1 1 端点）。
 *
 * <p>显式 bean name：与 {@code com.lifewise.diet.controller.StatsController} 类名相同，
 * 默认 bean name 都会是 {@code statsController} 而冲突，导致整个 Spring 上下文无法启动。
 * 命名方式沿用各模块 {@code CurrentUserArgumentResolver} 的模块前缀惯例。
 */
@RestController("expenseStatsController")
@RequestMapping("/api/expenses/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public ApiResponse<StatsView> stats(@CurrentUser Long userId,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) String groupBy) {
        return ApiResponse.ok(statsService.stats(userId, from, to, groupBy));
    }
}