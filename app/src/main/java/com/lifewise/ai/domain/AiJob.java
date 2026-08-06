package com.lifewise.ai.domain;

import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AI 任务（V8 ai_jobs DDL + V31 status 扩展）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-21：状态机见 {@link AiJobStatus}</li>
 *   <li>BR-16：限流 10 次/分钟在应用层（AiRateLimiter），不入 schema</li>
 * </ul>
 */
@Entity
@Table(name = "ai_jobs")
public class AiJob extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private AiJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiJobStatus status = AiJobStatus.PENDING;

    /** input + output：JSONB 灵活载荷（V8）。 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input", nullable = false, columnDefinition = "jsonb")
    private String inputJson = "{}";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "output", columnDefinition = "jsonb")
    private String outputJson;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "priority", nullable = false)
    private Integer priority = 5;

    @Column(name = "scheduled_at", nullable = false)
    private OffsetDateTime scheduledAt;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "error")
    private String error;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** Period 区间（plan §3 ai_jobs 字段；period_from/period_to）。 */
    @Column(name = "period_start")
    private LocalDate periodStart;

    @Column(name = "period_end")
    private LocalDate periodEnd;

    protected AiJob() {
        // JPA
    }

    private AiJob(Long userId, AiJobType jobType, OffsetDateTime scheduledAt,
                  LocalDate periodStart, LocalDate periodEnd) {
        this.userId = userId;
        this.jobType = jobType;
        this.scheduledAt = scheduledAt;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
    }

    /** 工厂：创建 PENDING 作业（plan §6 步骤 4）。 */
    public static AiJob createPending(Long userId, AiJobType jobType,
                                      OffsetDateTime scheduledAt,
                                      LocalDate periodStart, LocalDate periodEnd) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        if (jobType == null) {
            throw new IllegalArgumentException("jobType required");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("periodStart/periodEnd required");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be >= periodStart");
        }
        return new AiJob(userId, jobType, scheduledAt, periodStart, periodEnd);
    }

    public void markRunning(OffsetDateTime when) {
        if (this.status != AiJobStatus.PENDING && this.status != AiJobStatus.PENDING_PARTIAL) {
            throw new IllegalStateException("Cannot transition to RUNNING from " + this.status);
        }
        this.status = AiJobStatus.RUNNING;
        this.startedAt = when;
    }

    public void markRunningDegraded(OffsetDateTime when) {
        if (this.status != AiJobStatus.RUNNING) {
            throw new IllegalStateException("Cannot transition to RUNNING_DEGRADED from " + this.status);
        }
        this.status = AiJobStatus.RUNNING_DEGRADED;
        if (this.startedAt == null) {
            this.startedAt = when;
        }
    }

    public void markDone(OffsetDateTime when, String modelVersion, Integer tokensUsed, String outputJson) {
        ensureTransitionable(when);
        this.status = AiJobStatus.DONE;
        this.finishedAt = when;
        this.modelVersion = modelVersion;
        this.tokensUsed = tokensUsed;
        this.outputJson = outputJson;
    }

    public void markDonePartial(OffsetDateTime when, String outputJson) {
        ensureTransitionable(when);
        this.status = AiJobStatus.DONE_PARTIAL;
        this.finishedAt = when;
        this.outputJson = outputJson;
    }

    public void markDoneNoLlm(OffsetDateTime when, String outputJson) {
        ensureTransitionable(when);
        this.status = AiJobStatus.DONE_NO_LLM;
        this.finishedAt = when;
        this.outputJson = outputJson;
    }

    public void markFailed(OffsetDateTime when, String error) {
        ensureTransitionable(when);
        this.status = AiJobStatus.FAILED;
        this.finishedAt = when;
        this.error = error;
    }

    private void ensureTransitionable(OffsetDateTime when) {
        if (this.status.isTerminal()) {
            throw new IllegalStateException("Cannot transition from terminal status " + this.status);
        }
        if (when == null) {
            throw new IllegalArgumentException("when required");
        }
    }

    public Long getUserId() { return userId; }
    public AiJobType getJobType() { return jobType; }
    public AiJobStatus getStatus() { return status; }
    public String getInputJson() { return inputJson; }
    public String getOutputJson() { return outputJson; }
    public String getReferenceType() { return referenceType; }
    public Long getReferenceId() { return referenceId; }
    public Integer getPriority() { return priority; }
    public OffsetDateTime getScheduledAt() { return scheduledAt; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public Integer getTokensUsed() { return tokensUsed; }
    public String getError() { return error; }
    public Integer getRetryCount() { return retryCount; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public boolean isOwnedBy(Long userId) { return this.userId.equals(userId); }
}