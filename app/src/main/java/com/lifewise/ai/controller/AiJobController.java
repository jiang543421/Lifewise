package com.lifewise.ai.controller;

import com.lifewise.ai.dto.AiJobView;
import com.lifewise.ai.service.AiJobService;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.task.web.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 作业状态端点（plan-06-ai §2.2）。
 *
 * <p>1 端点：GET /api/ai/jobs/{id} — 轮询作业状态（PENDING / RUNNING / DONE* / FAILED）。
 * 前端可改用 SSE 订阅 ai.job.completed 推送（plan §2.6，v1.1+ 接入）。
 */
@RestController
@RequestMapping("/api/ai/jobs")
public class AiJobController {

    private final AiJobService jobService;

    public AiJobController(AiJobService jobService) {
        this.jobService = jobService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiJobView>> get(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        return jobService.findById(id, userId)
                .map(job -> ResponseEntity.ok(ApiResponse.ok(AiJobView.from(job))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(new com.lifewise.shared.integration.dto.ErrorEnvelope(
                                "NOT_FOUND", "job not found: id=" + id, null, null))));
    }
}