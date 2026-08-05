package com.lifewise.plan.controller;

import com.lifewise.plan.dto.PlanCreateRequest;
import com.lifewise.plan.dto.PlanView;
import com.lifewise.plan.service.PlanService;
import com.lifewise.plan.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Plan REST 端点（plan-05-plan §3.1 - 6 端点）。 */
@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public ApiResponse<PlanView> create(@CurrentUser Long userId,
                                        @Valid @RequestBody PlanCreateRequest req) {
        return ApiResponse.ok(planService.create(userId, req));
    }

    @GetMapping
    public ApiResponse<List<PlanView>> list(@CurrentUser Long userId,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                            @RequestParam(defaultValue = "false") boolean includeCancelled) {
        return ApiResponse.ok(planService.list(userId, from, to, includeCancelled));
    }

    @GetMapping("/{id}")
    public ApiResponse<PlanView> findById(@CurrentUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(planService.getById(userId, id));
    }

    @PutMapping("/{id}")
    public ApiResponse<PlanView> update(@CurrentUser Long userId, @PathVariable Long id,
                                        @Valid @RequestBody PlanCreateRequest req) {
        return ApiResponse.ok(planService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> softDelete(@CurrentUser Long userId, @PathVariable Long id) {
        planService.softDelete(userId, id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/abandon")
    public ApiResponse<PlanView> abandon(@CurrentUser Long userId, @PathVariable Long id) {
        return ApiResponse.ok(planService.abandon(userId, id));
    }
}