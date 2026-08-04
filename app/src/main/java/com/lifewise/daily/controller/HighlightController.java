package com.lifewise.daily.controller;

import com.lifewise.daily.dto.DailyMessageResponse;
import com.lifewise.daily.dto.HighlightRequest;
import com.lifewise.daily.dto.HighlightView;
import com.lifewise.daily.service.HighlightService;
import com.lifewise.daily.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Highlight 4 端点（plan-02-daily §2.2）：list / create / update / delete。 */
@RestController
@RequestMapping("/api/daily-reports/{reportId}/highlights")
public class HighlightController {

    private final HighlightService service;

    public HighlightController(HighlightService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<HighlightView>> list(@CurrentUser Long userId, @PathVariable long reportId) {
        return ApiResponse.ok(service.listByReport(userId, reportId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HighlightView>> create(
            @CurrentUser Long userId, @PathVariable long reportId,
            @Valid @RequestBody HighlightRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(userId, reportId, req)));
    }

    @PutMapping("/{hid}")
    public ApiResponse<HighlightView> update(
            @CurrentUser Long userId,
            @PathVariable long reportId, @PathVariable long hid,
            @Valid @RequestBody HighlightRequest req) {
        return ApiResponse.ok(service.update(userId, reportId, hid, req));
    }

    @DeleteMapping("/{hid}")
    public ApiResponse<DailyMessageResponse> delete(
            @CurrentUser Long userId, @PathVariable long reportId, @PathVariable long hid) {
        service.delete(userId, reportId, hid);
        return ApiResponse.ok(DailyMessageResponse.ok());
    }
}
