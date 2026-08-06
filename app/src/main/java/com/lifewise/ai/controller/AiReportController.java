package com.lifewise.ai.controller;

import com.lifewise.ai.domain.AiReport;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.dto.AiJobRequest;
import com.lifewise.ai.dto.AiReportView;
import com.lifewise.ai.repository.AiReportRepository;
import com.lifewise.ai.service.AiJobProcessor;
import com.lifewise.ai.service.AiJobService;
import com.lifewise.ai.service.AiRateLimiter;
import com.lifewise.ai.service.AiReportService;
import com.lifewise.ai.service.ConsentVerifier;
import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.PageMeta;
import com.lifewise.task.web.CurrentUser;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 报告端点（plan-06-ai §2.1）。
 *
 * <p>3 端点：
 * <ul>
 *   <li>POST /api/ai/reports/generate — 触发报告生成（consent + rate-limit + job + async）</li>
 *   <li>GET  /api/ai/reports           — 列出当前用户报告</li>
 *   <li>GET  /api/ai/reports/{id}      — 单份报告详情</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai/reports")
public class AiReportController {

    private static final Logger log = LoggerFactory.getLogger(AiReportController.class);

    private final ConsentVerifier consentVerifier;
    private final AiRateLimiter rateLimiter;
    private final AiJobService jobService;
    private final AiJobProcessor jobProcessor;
    private final AiReportService reportService;
    private final AiReportRepository reportRepository;

    public AiReportController(ConsentVerifier consentVerifier,
                              AiRateLimiter rateLimiter,
                              AiJobService jobService,
                              AiJobProcessor jobProcessor,
                              AiReportService reportService,
                              AiReportRepository reportRepository) {
        this.consentVerifier = consentVerifier;
        this.rateLimiter = rateLimiter;
        this.jobService = jobService;
        this.jobProcessor = jobProcessor;
        this.reportService = reportService;
        this.reportRepository = reportRepository;
    }

    /**
     * 触发报告生成（plan §2.1 + §6 步骤 2-5）。
     *
     * <p>流程：
     * <ol>
     *   <li>校验 userId + 同意（consent）</li>
     *   <li>三重速率限制（10/min + 60/h + 100/m global）</li>
     *   <li>创建 PENDING 作业（幂等）</li>
     *   <li>异步触发 processAsync（@Async "aiJobExecutor"）</li>
     *   <li>返回 jobId 给客户端轮询或订阅 SSE</li>
     * </ol>
     */
    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<GenerateAck>> generate(
            @CurrentUser Long userId,
            @Valid @RequestBody AiJobRequest req) {
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(new com.lifewise.shared.integration.dto.ErrorEnvelope(
                            "UNAUTHORIZED", "missing user context", null, null)));
        }
        // 1. consent
        consentVerifier.verifyOrThrow(userId);
        // 2. rate limit
        rateLimiter.acquireOrThrow(userId);

        AiJobType jobType = parseJobType(req.reportType());
        LocalDate from = req.periodFrom();
        LocalDate to = req.periodTo();

        // 3. idempotent create
        Long jobId = jobService.createJob(userId, jobType, from, to);

        // 4. async trigger
        jobProcessor.processAsync(jobId);

        log.info("AI report triggered userId={} jobId={} type={} period={}/{}",
                userId, jobId, jobType, from, to);
        return ResponseEntity.accepted()
                .body(ApiResponse.ok(new GenerateAck(jobId, jobType.name(), from, to)));
    }

    @GetMapping
    public ApiResponse<java.util.List<AiReportView>> list(
            @CurrentUser Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.min(100, Math.max(1, size)));
        Page<AiReport> p = reportRepository.findByUserIdAndOptionalKind(
                userId, null, pageable);
        java.util.List<AiReportView> items = p.getContent().stream()
                .map(AiReportView::from).toList();
        PageMeta meta = new PageMeta(p.getTotalElements(), page, size, p.hasNext());
        return ApiResponse.paged(items, meta);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AiReportView>> get(
            @CurrentUser Long userId,
            @PathVariable Long id) {
        return reportService.findById(id, userId)
                .map(r -> ResponseEntity.ok(ApiResponse.ok(AiReportView.from(r))))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(new com.lifewise.shared.integration.dto.ErrorEnvelope(
                                "NOT_FOUND", "report not found: id=" + id, null, null))));
    }

    private AiJobType parseJobType(String s) {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("reportType required");
        }
        // 前端友好名（snake_case）→ 枚举名（UPPER_SNAKE_CASE）
        String upper = s.trim().toUpperCase().replace('-', '_');
        try {
            return AiJobType.valueOf(upper);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown reportType: " + s);
        }
    }

    public record GenerateAck(Long jobId, String reportType, LocalDate periodFrom, LocalDate periodTo) {}
}