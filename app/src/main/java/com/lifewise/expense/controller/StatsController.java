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

/** 统计 REST 端点（plan-03-expense §1.1 1 端点）。 */
@RestController
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