package com.lifewise.ai.service;

import com.lifewise.ai.domain.AiReport;
import com.lifewise.ai.domain.enums.ContentFormat;
import com.lifewise.ai.domain.enums.ReportKind;
import com.lifewise.ai.repository.AiReportRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 报告服务（plan-06-ai §6 + §7.6）。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #saveReport} — 生成完成后 INSERT ai_reports（1:1 关联 ai_job）</li>
 *   <li>{@link #findById} — 按 id + userId 读取（ownership 校验）</li>
 * </ul>
 */
@Service
public class AiReportService {

    private final AiReportRepository repository;

    public AiReportService(AiReportRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AiReport saveReport(Long userId, Long jobId, ReportKind kind,
                               String title, String content,
                               LocalDate periodStart, LocalDate periodEnd) {
        AiReport report = AiReport.create(userId, jobId, kind, title,
                ContentFormat.MARKDOWN, content, periodStart, periodEnd);
        return repository.save(report);
    }

    @Transactional(readOnly = true)
    public Optional<AiReport> findById(Long reportId, Long userId) {
        if (reportId == null || userId == null) return Optional.empty();
        return repository.findByIdAndUserIdAndDeletedAtIsNull(reportId, userId);
    }
}