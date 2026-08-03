package com.lifewise.daily.controller;

import com.lifewise.daily.dto.DailyReportCreateRequest;
import com.lifewise.daily.dto.DailyReportListItem;
import com.lifewise.daily.dto.DailyReportUpdateRequest;
import com.lifewise.daily.dto.DailyReportView;
import com.lifewise.daily.dto.DailyMessageResponse;
import com.lifewise.daily.service.DailyReportService;
import com.lifewise.daily.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.PageMeta;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DailyReport 6 端点（plan-02-daily §2.1）：list / get / by-date / create / update / softDelete。 */
@RestController
@RequestMapping("/api/daily-reports")
public class DailyReportController {

    private final DailyReportService service;

    public DailyReportController(DailyReportService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<java.util.List<DailyReportListItem>> list(
            @CurrentUser Long userId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(name = "include_draft", defaultValue = "false") boolean includeDraft,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        int p = Math.max(page, 1);
        int l = Math.max(Math.min(limit, 100), 1);
        Pageable pageable = PageRequest.of(p - 1, l);
        Page<DailyReportListItem> result = service.list(userId, year, month, includeDraft, pageable);
        PageMeta meta = new PageMeta(result.getTotalElements(), p, l, result.hasNext());
        return ApiResponse.paged(result.getContent(), meta);
    }

    @GetMapping("/by-date/{date}")
    public ApiResponse<DailyReportView> getByDate(@CurrentUser Long userId,
                                                  @PathVariable @org.springframework.format.annotation.DateTimeFormat(
                                                          iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
                                                  LocalDate date) {
        return ApiResponse.ok(service.getByDate(userId, date));
    }

    @GetMapping("/{id}")
    public ApiResponse<DailyReportView> get(@CurrentUser Long userId, @PathVariable long id) {
        return ApiResponse.ok(service.getOwned(userId, id));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DailyReportView>> create(
            @CurrentUser Long userId, @Valid @RequestBody DailyReportCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(userId, req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<DailyReportView> update(@CurrentUser Long userId,
                                               @PathVariable long id,
                                               @Valid @RequestBody DailyReportUpdateRequest req) {
        return ApiResponse.ok(service.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DailyMessageResponse> delete(@CurrentUser Long userId, @PathVariable long id) {
        service.softDelete(userId, id);
        return ApiResponse.ok(DailyMessageResponse.ok());
    }
}
