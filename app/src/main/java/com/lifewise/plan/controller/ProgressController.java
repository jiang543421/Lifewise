package com.lifewise.plan.controller;

import com.lifewise.plan.dto.ProgressView;
import com.lifewise.plan.service.ProgressEvaluator;
import com.lifewise.plan.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 进度 REST 端点（plan-05-plan §3.3 - 1 端点）。 */
@RestController
@RequestMapping("/api/plans/{planId}/progress")
public class ProgressController {

    private final ProgressEvaluator progressEvaluator;

    public ProgressController(ProgressEvaluator progressEvaluator) {
        this.progressEvaluator = progressEvaluator;
    }

    @GetMapping
    public ApiResponse<ProgressView> compute(@CurrentUser Long userId, @PathVariable Long planId) {
        return ApiResponse.ok(progressEvaluator.compute(userId, planId));
    }
}