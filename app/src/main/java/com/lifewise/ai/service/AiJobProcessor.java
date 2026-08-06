package com.lifewise.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import com.lifewise.ai.domain.enums.AiJobType;
import com.lifewise.ai.domain.enums.ReportKind;
import com.lifewise.ai.service.audit.AiAuditDecision;
import com.lifewise.ai.service.audit.AiAuditLogger;
import com.lifewise.ai.service.exception.AiJobNotFoundException;
import com.lifewise.ai.service.exception.OllamaUnavailableException;
import com.lifewise.ai.service.ollama.GenerationResult;
import com.lifewise.ai.service.ollama.OllamaClient;
import com.lifewise.ai.service.ollama.PromptBuilder;
import com.lifewise.ai.service.ollama.PromptResult;
import com.lifewise.ai.service.scope.ScopedDataFetcher;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 任务异步编排器（plan-06-ai §6 + §7.6 + §7.6.1）。
 *
 * <p><b>三态决策</b>（X3 闭环）：
 * <ul>
 *   <li>DONE — Ollama OK + 数据齐 + consent</li>
 *   <li>DONE_PARTIAL — 源数据缺失（fetch 抛异常 → 部分数据）</li>
 *   <li>DONE_NO_LLM — Ollama 红色态 / 超时 / 不同意（生成结构化兜底报告）</li>
 * </ul>
 *
 * <p>三态均发 {@code ai.job.completed} outbox 事件，payload.final_status 区分。
 * 真正异常（DB 失败 / 程序 bug）才走 {@code FAILED}，不发 completed 事件。
 *
 * <p>设计取舍：{@link #processAsync} 只负责转线程执行；核心 pipeline 在
 * {@link #runPipeline} 同步方法里，便于单元测试。
 */
@Component
public class AiJobProcessor {

    private static final Logger log = LoggerFactory.getLogger(AiJobProcessor.class);

    /** 任务级别 scope 表 → 列白名单（与 ai-data-scopes.yml 一致；YML 由 v1.1+ 引入）。 */
    private static final Map<AiJobType, List<TableSpec>> SCOPES = Map.of(
            AiJobType.DAILY_SUMMARY, List.of(
                    new TableSpec("tasks", Set.of("id", "title", "status", "occurred_at"))),
            AiJobType.WEEKLY_SUMMARY, List.of(
                    new TableSpec("tasks", Set.of("id", "title", "status", "occurred_at"))),
            AiJobType.PLAN_REVIEW, List.of(
                    new TableSpec("plans", Set.of("id", "title", "status", "last_activity_at"))),
            AiJobType.HABIT_ANALYSIS, List.of(
                    new TableSpec("tasks", Set.of("id", "title", "status", "occurred_at"))),
            AiJobType.MEAL_ANALYSIS, List.of(
                    new TableSpec("meals", Set.of("id", "type", "occurred_at"))),
            AiJobType.EXPENSE_ANALYSIS, List.of(
                    new TableSpec("expenses", Set.of("id", "amount", "currency", "category", "occurred_at"))));

    private static final Map<AiJobType, ReportKind> KIND_MAP = Map.of(
            AiJobType.DAILY_SUMMARY, ReportKind.DAILY,
            AiJobType.WEEKLY_SUMMARY, ReportKind.WEEKLY,
            AiJobType.PLAN_REVIEW, ReportKind.PLAN,
            AiJobType.HABIT_ANALYSIS, ReportKind.HABIT,
            AiJobType.MEAL_ANALYSIS, ReportKind.MEAL,
            AiJobType.EXPENSE_ANALYSIS, ReportKind.EXPENSE,
            AiJobType.CUSTOM_PROMPT, ReportKind.CUSTOM);

    private final AiJobService jobService;
    private final AiReportService reportService;
    private final ScopedDataFetcher dataFetcher;
    private final PromptBuilder promptBuilder;
    private final OllamaClient ollamaClient;
    private final AiAuditLogger auditLogger;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AiJobProcessor(AiJobService jobService,
                          AiReportService reportService,
                          ScopedDataFetcher dataFetcher,
                          PromptBuilder promptBuilder,
                          OllamaClient ollamaClient,
                          AiAuditLogger auditLogger,
                          OutboxWriter outboxWriter,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.jobService = jobService;
        this.reportService = reportService;
        this.dataFetcher = dataFetcher;
        this.promptBuilder = promptBuilder;
        this.ollamaClient = ollamaClient;
        this.auditLogger = auditLogger;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Async("aiJobExecutor")
    public void processAsync(Long jobId) {
        try {
            runPipeline(jobId);
        } catch (Exception ex) {
            // 兜底：未捕获异常 → FAILED（防止异步任务静默失败）
            log.error("AI pipeline crashed jobId={}", jobId, ex);
            tryMarkFailedSafely(jobId, "pipeline crashed: " + ex.getMessage());
        }
    }

    @Transactional
    public void runPipeline(Long jobId) {
        AiJob job = jobService.findById(jobId, null) // ownership 在 createJob 时已校验
                .orElseThrow(() -> new AiJobNotFoundException("job not found: " + jobId));
        // 重新加载带 userId（供 ownership 二次校验）
        job = jobService.loadOrThrow(jobId, job.getUserId());
        if (job.getStatus().isTerminal()) {
            log.info("AI job already terminal, skip jobId={} status={}", jobId, job.getStatus());
            return;
        }

        String traceId = UUID.randomUUID().toString();
        Long userId = job.getUserId();
        AiJobType type = job.getJobType();

        jobService.markRunning(jobId, userId);
        audit(userId, traceId, "DATA_FETCH", "STARTED", null, null, Map.of("type", type.name()));

        // 1. 拉数据
        List<Map<String, Object>> rows;
        boolean partial = false;
        try {
            rows = fetchData(userId, type, job.getPeriodStart(), job.getPeriodEnd());
        } catch (RuntimeException ex) {
            // X3 PARTIAL 降级：fetch 失败 → DONE_PARTIAL
            log.warn("AI data fetch failed, partial fallback jobId={}: {}", jobId, ex.getMessage());
            partial = true;
            rows = List.of();
        }
        audit(userId, traceId, "DATA_FETCH", partial ? "PARTIAL" : "SUCCESS",
                null, null, Map.of("rows", rows.size()));

        // 2. 构建 prompt
        Map<String, Object> params = new HashMap<>();
        params.put("period_start", job.getPeriodStart().toString());
        params.put("period_end", job.getPeriodEnd().toString());
        PromptResult prompt = promptBuilder.build(type.name(), rows, params);
        audit(userId, traceId, "MODEL_CALL", "STARTED", null, null,
                Map.of("prompt_hash", prompt.promptHash(),
                       "token_count", prompt.tokenCount(),
                       "truncated", prompt.truncated()));

        // 3. 调用 Ollama（红色态 → DONE_NO_LLM）
        GenerationResult gen;
        try {
            gen = ollamaClient.generate(prompt.userPrompt());
        } catch (OllamaUnavailableException ex) {
            log.warn("AI Ollama unavailable, DONE_NO_LLM fallback jobId={}: {}", jobId, ex.getMessage());
            String fallback = renderFallback(type, rows, "ollama unavailable");
            jobService.markDoneNoLlm(jobId, userId, fallback);
            audit(userId, traceId, "MODEL_CALL", "FAILED", null, null,
                    Map.of("reason", ex.getMessage()));
            audit(userId, traceId, "GENERATE", "DONE_NO_LLM", null, null,
                    Map.of("report_length", fallback.length()));
            emitCompletedEvent(jobId, userId, type, AiJobStatus.DONE_NO_LLM, "deepseek:8b", 0L);
            return;
        }

        audit(userId, traceId, "MODEL_CALL", "COMPLETED",
                gen.latencyMs(), (int) gen.tokensUsed(),
                Map.of("model", "deepseek:8b"));

        // 4. 写报告
        String title = renderTitle(type, job.getPeriodStart(), job.getPeriodEnd());
        reportService.saveReport(userId, jobId, KIND_MAP.get(type),
                title, gen.content(),
                job.getPeriodStart(), job.getPeriodEnd());

        // 5. 终态 + outbox
        if (partial) {
            jobService.markDonePartial(jobId, userId, gen.content());
            audit(userId, traceId, "GENERATE", "DONE_PARTIAL", gen.latencyMs(), (int) gen.tokensUsed(),
                    Map.of("reason", "source_missing"));
            emitCompletedEvent(jobId, userId, type, AiJobStatus.DONE_PARTIAL, "deepseek:8b", gen.latencyMs());
        } else {
            jobService.markDone(jobId, userId, "deepseek:8b", (int) gen.tokensUsed(), gen.content());
            audit(userId, traceId, "GENERATE", "DONE", gen.latencyMs(), (int) gen.tokensUsed(), Map.of());
            emitCompletedEvent(jobId, userId, type, AiJobStatus.DONE, "deepseek:8b", gen.latencyMs());
        }
    }

    private List<Map<String, Object>> fetchData(Long userId, AiJobType type,
                                                java.time.LocalDate from, java.time.LocalDate to) {
        List<TableSpec> scopes = SCOPES.getOrDefault(type, List.of());
        java.util.List<Map<String, Object>> all = new java.util.ArrayList<>();
        for (TableSpec spec : scopes) {
            all.addAll(dataFetcher.fetch(userId, type, spec.tableName, spec.columns, from, to));
        }
        return all;
    }

    private String renderTitle(AiJobType type, java.time.LocalDate from, java.time.LocalDate to) {
        return type.name() + " " + from + " ~ " + to;
    }

    private String renderFallback(AiJobType type, List<Map<String, Object>> rows, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(type.name()).append("（降级报告 — Ollama 不可用）\n\n");
        sb.append("**原因**：").append(reason).append('\n');
        sb.append("**结构化数据行数**：").append(rows.size()).append('\n');
        return sb.toString();
    }

    private void audit(Long userId, String traceId, String type, String decision,
                       Long latencyMs, Integer tokensUsed, Map<String, Object> metadata) {
        try {
            auditLogger.log(userId, AiAuditDecision.builder()
                    .decisionType(type)
                    .decision(decision)
                    .traceId(traceId)
                    .latencyMs(latencyMs)
                    .tokensUsed(tokensUsed)
                    .metadata(metadata == null ? Map.of() : metadata)
                    .build());
        } catch (RuntimeException ex) {
            // 审计失败不应阻塞 pipeline（plan §6 步骤 2.5 降级策略）
            log.warn("AI audit failed userId={} type={}: {}", userId, type, ex.getMessage());
        }
    }

    private void emitCompletedEvent(Long jobId, Long userId, AiJobType type,
                                    AiJobStatus finalStatus, String modelVersion, long latencyMs) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("job_id", jobId);
            payload.put("user_id", userId);
            payload.put("report_type", type.name());
            payload.put("final_status", finalStatus.name());
            payload.put("model_version", modelVersion);
            payload.put("latency_ms", latencyMs);

            EventEnvelope env = new EventEnvelope(
                    UUID.randomUUID(),
                    "ai.job.completed",
                    1,
                    OffsetDateTime.now(clock),
                    userId,
                    "ai_job",
                    jobId,
                    null,
                    null,
                    null,
                    payload);
            outboxWriter.append(env);
        } catch (RuntimeException ex) {
            // outbox 失败仅记日志（plan §6 步骤 h 降级）
            log.warn("AI outbox emit failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    private void tryMarkFailedSafely(Long jobId, String error) {
        try {
            // 尽力失败标记（无 ownership 信息，仅用 id 查找）
            jobService.findById(jobId, null).ifPresent(j ->
                    jobService.markFailed(jobId, j.getUserId(), error));
        } catch (Exception ex) {
            log.error("AI job markFailed failed jobId={}: {}", jobId, ex.getMessage());
        }
    }

    /** 静态 scope 配置（YAML 落地前 v1.0 兜底）。 */
    record TableSpec(String tableName, Set<String> columns) {
        TableSpec {
            columns = Set.copyOf(columns);
        }
        // 列有序集合用于渲染
        java.util.LinkedHashSet<String> orderedColumns() {
            return new LinkedHashSet<>(columns);
        }
    }
}