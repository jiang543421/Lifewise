package com.lifewise.daily.controller;

import com.lifewise.daily.dto.AiSummaryView;
import com.lifewise.daily.service.SummaryService;
import com.lifewise.daily.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Summary 2 端点（plan-02-daily §2.4）：trigger / get latest。 */
@RestController
@RequestMapping("/api/daily-reports/{reportId}/summary")
public class SummaryController {

    private final SummaryService service;

    public SummaryController(SummaryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AiSummaryView>> trigger(
            @CurrentUser Long userId, @PathVariable long reportId) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(service.trigger(userId, reportId)));
    }

    @GetMapping
    public ApiResponse<AiSummaryView> get(@CurrentUser Long userId, @PathVariable long reportId) {
        return ApiResponse.ok(service.get(userId, reportId));
    }
}
