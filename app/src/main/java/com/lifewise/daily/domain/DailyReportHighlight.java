package com.lifewise.daily.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 日报亮点实体（plan-02-daily §3 + V5 DDL daily_report_highlights）。
 *
 * <p>NOTE：schema 未提供独立的 {@code position} 列；BR-08 要求 ≤ 3 条/日，由应用层在
 * service 层强制（count per reportId < 3）。
 *
 * <p>复合 FK：{@code (daily_report_id, local_date)} → {@code daily_reports(id, local_date)}；
 * 符合分区表对 FK 的要求（CLAUDE.md §数据模型 / 分区表 FK 复合键跟随分区键）。
 */
@Entity
@Table(name = "daily_report_highlights")
public class DailyReportHighlight extends BaseEntity {

    @Column(name = "daily_report_id", nullable = false)
    private Long dailyReportId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "highlight_type", nullable = false)
    private HighlightType highlightType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 2000)
    private String description;

    @Column(name = "reference_type", length = 32)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DailyReportHighlight() {
        // JPA
    }

    private DailyReportHighlight(Long dailyReportId, LocalDate localDate,
                                 HighlightType highlightType, String title, String description,
                                 String referenceType, Long referenceId, int sortOrder) {
        this.dailyReportId = dailyReportId;
        this.localDate = localDate;
        this.highlightType = highlightType;
        this.title = title;
        this.description = description;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sortOrder = sortOrder;
    }

    /** 工厂方法：创建一条亮点（service 层校验当日 ≤ 3 条）。 */
    public static DailyReportHighlight create(Long dailyReportId, LocalDate localDate,
                                              HighlightType highlightType, String title,
                                              String description, String referenceType,
                                              Long referenceId, int sortOrder) {
        if (dailyReportId == null) {
            throw new IllegalArgumentException("dailyReportId must not be null");
        }
        if (localDate == null) {
            throw new IllegalArgumentException("localDate must not be null");
        }
        if (highlightType == null) {
            throw new IllegalArgumentException("highlightType must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title length must be <= 200");
        }
        if (description != null && description.length() > 2000) {
            throw new IllegalArgumentException("description length must be <= 2000");
        }
        if (referenceType != null && referenceType.length() > 32) {
            throw new IllegalArgumentException("referenceType length must be <= 32");
        }
        return new DailyReportHighlight(dailyReportId, localDate, highlightType,
                title.trim(), description, referenceType, referenceId, sortOrder);
    }

    /** 应用层更新：替换可变字段。 */
    public void applyUpdate(HighlightType highlightType, String title, String description,
                            String referenceType, Long referenceId, Integer sortOrder) {
        if (highlightType != null) {
            this.highlightType = highlightType;
        }
        if (title != null && !title.isBlank()) {
            if (title.length() > 200) {
                throw new IllegalArgumentException("title length must be <= 200");
            }
            this.title = title.trim();
        }
        if (description != null) {
            if (description.length() > 2000) {
                throw new IllegalArgumentException("description length must be <= 2000");
            }
            this.description = description;
        }
        if (referenceType != null) {
            if (referenceType.length() > 32) {
                throw new IllegalArgumentException("referenceType length must be <= 32");
            }
            this.referenceType = referenceType;
        }
        this.referenceId = referenceId;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public Long getDailyReportId() { return dailyReportId; }
    public LocalDate getLocalDate() { return localDate; }
    public HighlightType getHighlightType() { return highlightType; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public int getSortOrder() { return sortOrder; }
}
