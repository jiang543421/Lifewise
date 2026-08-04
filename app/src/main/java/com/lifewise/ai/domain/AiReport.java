package com.lifewise.ai.domain;

import com.lifewise.ai.domain.enums.ContentFormat;
import com.lifewise.ai.domain.enums.ReportKind;
import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * AI 报告（V8 ai_reports DDL）。
 *
 * <p>1:1 关联 {@link AiJob}（job_id FK ON DELETE CASCADE）。
 * 反馈计数由 chat_feedbacks 聚合（应用层维护）。
 */
@Entity
@Table(name = "ai_reports")
public class AiReport extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_kind", nullable = false)
    private ReportKind reportKind;

    @Column(name = "title", nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_format", nullable = false)
    private ContentFormat contentFormat = ContentFormat.MARKDOWN;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    @Column(name = "feedback_count", nullable = false)
    private Integer feedbackCount = 0;

    @Column(name = "helpful_count", nullable = false)
    private Integer helpfulCount = 0;

    protected AiReport() {
        // JPA
    }

    private AiReport(Long userId, Long jobId, ReportKind reportKind, String title,
                     ContentFormat contentFormat, String content,
                     LocalDate periodStart, LocalDate periodEnd) {
        this.userId = userId;
        this.jobId = jobId;
        this.reportKind = reportKind;
        this.title = title;
        this.contentFormat = contentFormat;
        this.content = content;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    public static AiReport create(Long userId, Long jobId, ReportKind reportKind,
                                  String title, ContentFormat contentFormat, String content,
                                  LocalDate periodStart, LocalDate periodEnd) {
        if (userId == null || jobId == null || reportKind == null) {
            throw new IllegalArgumentException("userId/jobId/reportKind required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content required");
        }
        if (content.length() > 100_000) {
            throw new IllegalArgumentException("content exceeds 100000 chars");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title exceeds 200 chars");
        }
        return new AiReport(userId, jobId, reportKind, title,
                contentFormat == null ? ContentFormat.MARKDOWN : contentFormat,
                content, periodStart, periodEnd);
    }

    public void incrementHelpful() {
        this.feedbackCount++;
        this.helpfulCount++;
    }

    public void incrementNotHelpful() {
        this.feedbackCount++;
    }

    public Long getUserId() { return userId; }
    public Long getJobId() { return jobId; }
    public ReportKind getReportKind() { return reportKind; }
    public String getTitle() { return title; }
    public ContentFormat getContentFormat() { return contentFormat; }
    public String getContent() { return content; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public Integer getFeedbackCount() { return feedbackCount; }
    public Integer getHelpfulCount() { return helpfulCount; }
    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }
}