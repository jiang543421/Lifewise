package com.lifewise.daily.controller;

import com.lifewise.daily.dto.DailyReportSearchHit;
import com.lifewise.daily.service.SearchService;
import com.lifewise.daily.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.PageMeta;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Search 1 端点（plan-02-daily §2.3）：全文检索 tsvector + 日期范围。 */
@RestController
@RequestMapping("/api/daily-reports/search")
public class SearchController {

    private final SearchService service;

    public SearchController(SearchService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<DailyReportSearchHit>> search(
            @CurrentUser Long userId,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        int p = Math.max(page, 1);
        int l = Math.max(Math.min(limit, 100), 1);
        Pageable pageable = PageRequest.of(p - 1, l);
        Page<DailyReportSearchHit> hits = service.search(userId, q, from, to, pageable);
        PageMeta meta = new PageMeta(hits.getTotalElements(), p, l, hits.hasNext());
        return ApiResponse.paged(hits.getContent(), meta);
    }
}
