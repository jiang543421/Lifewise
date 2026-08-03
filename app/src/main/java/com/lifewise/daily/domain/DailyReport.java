package com.lifewise.daily.domain;

import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 日报实体（plan-02-daily §3 + V5 DDL daily_reports + V32 P1 is_draft）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-06 / UNIQUE(user_id, local_date) — 每日唯一</li>
 *   <li>BR-25：content length ≤ 50000（DB CHECK 强制）</li>
 *   <li>mood ∈ {GREAT, GOOD, NEUTRAL, BAD, TERRIBLE}（DB CHECK）</li>
 *   <li>energy_score ∈ [1, 5]（DB CHECK，可空）</li>
 *   <li>is_draft 默认 TRUE，发布置 FALSE</li>
 * </ul>
 *
 * <p>partition key：{@code local_date}；PRIMARY KEY 为复合 {@code (id, local_date)}。
 * MySQL/PG 分区表要求主键包含分区键。
 *
 * <p>NOTE：plan-02-daily 期望 {@code report_date}，schema 用 {@code local_date}（语义一致）。
 */
@Entity
@Table(name = "daily_reports")
public class DailyReport extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "local_date", nullable = false)
    private LocalDate localDate;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", length = 50000)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "mood")
    private Mood mood;

    @Column(name = "energy_score")
    private Integer energyScore;

    @Column(name = "is_draft", nullable = false)
    private boolean draft;

    protected DailyReport() {
        // JPA
    }

    private DailyReport(Long userId, LocalDate localDate, String timezone,
                        String title, String content, Mood mood,
                        Integer energyScore, boolean isDraft) {
        this.userId = userId;
        this.localDate = localDate;
        this.timezone = timezone;
        this.title = title;
        this.content = content;
        this.mood = mood;
        this.energyScore = energyScore;
        this.draft = isDraft;
    }

    /** 工厂方法：创建新日报（默认草稿）。 */
    public static DailyReport create(Long userId, LocalDate localDate, String timezone,
                                     String title, String content, Mood mood,
                                     Integer energyScore) {
        if (userId == null) {
            throw new IllegalArgumentException("userId must not be null");
        }
        if (localDate == null) {
            throw new IllegalArgumentException("localDate must not be null");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 200) {
            throw new IllegalArgumentException("title length must be <= 200");
        }
        if (content != null && content.length() > 50000) {
            throw new IllegalArgumentException("content length must be <= 50000");
        }
        if (energyScore != null && (energyScore < 1 || energyScore > 5)) {
            throw new IllegalArgumentException("energyScore must be in [1, 5]");
        }
        String safeTimezone = (timezone == null || timezone.isBlank()) ? "UTC" : timezone;
        if (safeTimezone.length() > 64) {
            throw new IllegalArgumentException("timezone length must be <= 64");
        }
        return new DailyReport(userId, localDate, safeTimezone, title.trim(), content,
                mood, energyScore, true);
    }

    /** 应用层更新字段（title / content / mood / energyScore / isDraft）。 */
    public void applyUpdate(String title, String content, Mood mood,
                            Integer energyScore, Boolean publish) {
        if (title != null && !title.isBlank()) {
            if (title.length() > 200) {
                throw new IllegalArgumentException("title length must be <= 200");
            }
            this.title = title.trim();
        }
        if (content != null) {
            if (content.length() > 50000) {
                throw new IllegalArgumentException("content length must be <= 50000");
            }
            this.content = content;
        }
        if (mood != null) {
            this.mood = mood;
        }
        if (energyScore != null) {
            if (energyScore < 1 || energyScore > 5) {
                throw new IllegalArgumentException("energyScore must be in [1, 5]");
            }
            this.energyScore = energyScore;
        }
        if (publish != null && publish) {
            this.draft = false;
        }
    }

    public Long getUserId() { return userId; }
    public LocalDate getLocalDate() { return localDate; }
    public String getTimezone() { return timezone; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public Mood getMood() { return mood; }
    public Integer getEnergyScore() { return energyScore; }
    public boolean isDraft() { return draft; }
}
