package com.lifewise.ai.service;

import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.repository.AiJobRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 任务服务（plan-06-ai §6 + §7.6）。
 *
 * <p><b>职责（同步 CRUD + 状态机）</b>：
 * <ul>
 *   <li>{@link #createJob} — 同步创建 PENDING 作业（含幂等性检查）</li>
 *   <li>{@link #markRunning} / {@link #markDone} / {@link #markDonePartial} /
 *       {@link #markDoneNoLlm} / {@link #markFailed} — 状态机迁移（仅暴露必要接口给 Processor）</li>
 *   <li>{@link #findById} — 按 id + userId 读取（ownership 校验）</li>
 * </ul>
 *
 * <p><b>异步执行</b>在 {@code AiJobProcessor}（{@code @Async("aiJobExecutor")}）。
 * 本类不直接持有异步路径，便于单元测试同步调用。
 *
 * <p><b>幂等性</b>：同 {@code (userId, jobType, periodStart, periodEnd)} 当日
 * 若已有 PENDING / RUNNING / DONE* 作业，直接复用旧 id，避免重复生成（plan §7.6）。
 */
@Service
public class AiJobService {

    private static final Logger log = LoggerFactory.getLogger(AiJobService.class);

    private final AiJobRepository repository;
    private final Clock clock;

    public AiJobService(AiJobRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * 创建 PENDING 作业；幂等：同 (userId, jobType, period) 当日已存在 → 返回旧 id。
     */
    @Transactional
    public Long createJob(Long userId, AiJobType jobType,
                          LocalDate periodStart, LocalDate periodEnd) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId required");
        }
        if (jobType == null) {
            throw new IllegalArgumentException("jobType required");
        }
        if (periodStart == null || periodEnd == null) {
            throw new IllegalArgumentException("period required");
        }
        if (periodEnd.isBefore(periodStart)) {
            throw new IllegalArgumentException("periodEnd must be >= periodStart");
        }

        // 幂等性：当日已存在 → 复用
        List<AiJob> existing = repository.findActiveByUserTypePeriod(
                userId, jobType, periodStart, periodEnd);
        if (!existing.isEmpty()) {
            AiJob reuse = existing.get(0);
            log.info("AI job idempotent hit userId={} type={} period={}/{} reuse jobId={}",
                    userId, jobType, periodStart, periodEnd, reuse.getId());
            return reuse.getId();
        }

        AiJob job = AiJob.createPending(userId, jobType,
                OffsetDateTime.now(clock), periodStart, periodEnd);
        return repository.save(job).getId();
    }

    @Transactional
    public void markRunning(Long jobId, Long userId) {
        AiJob job = loadOrThrow(jobId, userId);
        job.markRunning(OffsetDateTime.now(clock));
        repository.save(job);
    }

    @Transactional
    public void markDone(Long jobId, Long userId,
                         String modelVersion, Integer tokensUsed, String outputJson) {
        AiJob job = loadOrThrow(jobId, userId);
        job.markDone(OffsetDateTime.now(clock), modelVersion, tokensUsed, outputJson);
        repository.save(job);
    }

    @Transactional
    public void markDonePartial(Long jobId, Long userId, String outputJson) {
        AiJob job = loadOrThrow(jobId, userId);
        job.markDonePartial(OffsetDateTime.now(clock), outputJson);
        repository.save(job);
    }

    @Transactional
    public void markDoneNoLlm(Long jobId, Long userId, String outputJson) {
        AiJob job = loadOrThrow(jobId, userId);
        job.markDoneNoLlm(OffsetDateTime.now(clock), outputJson);
        repository.save(job);
    }

    @Transactional
    public void markFailed(Long jobId, Long userId, String error) {
        AiJob job = loadOrThrow(jobId, userId);
        job.markFailed(OffsetDateTime.now(clock), error);
        repository.save(job);
    }

    @Transactional(readOnly = true)
    public Optional<AiJob> findById(Long jobId, Long userId) {
        if (jobId == null || userId == null) return Optional.empty();
        return repository.findByIdAndUserIdAndDeletedAtIsNull(jobId, userId);
    }

    @Transactional(readOnly = true)
    public AiJob loadOrThrow(Long jobId, Long userId) {
        return repository.findByIdAndUserIdAndDeletedAtIsNull(jobId, userId)
                .orElseThrow(() -> new com.lifewise.ai.service.exception.AiJobNotFoundException(
                        "job not found: id=" + jobId + " user=" + userId));
    }

    /** 仅测试用：暴露当前 Clock。 */
    Clock clock() { return clock; }
}