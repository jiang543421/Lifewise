package com.lifewise.plan.controller;

import com.lifewise.plan.dto.LinkTasksRequest;
import com.lifewise.plan.dto.MilestoneRequest;
import com.lifewise.plan.dto.MilestoneView;
import com.lifewise.plan.service.MilestoneService;
import com.lifewise.plan.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Milestone REST 端点（plan-05-plan §3.2 - 7 端点）。 */
@RestController
@RequestMapping("/api/plans/{planId}/milestones")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    @PostMapping
    public ApiResponse<MilestoneView> create(@CurrentUser Long userId, @PathVariable Long planId,
                                             @Valid @RequestBody MilestoneRequest req) {
        return ApiResponse.ok(milestoneService.create(userId, planId, req));
    }

    @GetMapping
    public ApiResponse<List<MilestoneView>> list(@CurrentUser Long userId, @PathVariable Long planId) {
        return ApiResponse.ok(milestoneService.list(userId, planId));
    }

    @PutMapping("/{milestoneId}")
    public ApiResponse<MilestoneView> update(@CurrentUser Long userId, @PathVariable Long planId,
                                             @PathVariable Long milestoneId,
                                             @Valid @RequestBody MilestoneRequest req) {
        return ApiResponse.ok(milestoneService.update(userId, planId, milestoneId, req));
    }

    @DeleteMapping("/{milestoneId}")
    public ApiResponse<Void> softDelete(@CurrentUser Long userId, @PathVariable Long planId,
                                        @PathVariable Long milestoneId) {
        milestoneService.softDelete(userId, planId, milestoneId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{milestoneId}/complete")
    public ApiResponse<MilestoneView> complete(@CurrentUser Long userId, @PathVariable Long planId,
                                               @PathVariable Long milestoneId) {
        return ApiResponse.ok(milestoneService.complete(userId, planId, milestoneId));
    }

    @PostMapping("/{milestoneId}/reopen")
    public ApiResponse<MilestoneView> reopen(@CurrentUser Long userId, @PathVariable Long planId,
                                             @PathVariable Long milestoneId) {
        return ApiResponse.ok(milestoneService.reopen(userId, planId, milestoneId));
    }

    @PostMapping("/{milestoneId}/tasks")
    public ApiResponse<List<Long>> linkTasks(@CurrentUser Long userId, @PathVariable Long planId,
                                             @PathVariable Long milestoneId,
                                             @Valid @RequestBody LinkTasksRequest req) {
        return ApiResponse.ok(milestoneService.linkTasks(userId, planId, milestoneId, req.taskIds()));
    }
}