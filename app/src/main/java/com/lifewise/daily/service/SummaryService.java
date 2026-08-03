package com.lifewise.daily.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.daily.domain.AiSummary;
import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.SummaryKind;
import com.lifewise.daily.dto.AiSummaryView;
import com.lifewise.daily.event.payload.AiSummaryGeneratedPayload;
import com.lifewise.daily.repository.AiSummaryRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.AiSummaryNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 日报 AI 摘要触发器（plan-02-daily §2.4 + BR-21）。
 *
 * <p>v1.0 仅落库 + 触发 outbox 事件，LLM 推断执行交由 plan-06-ai 模块消费事件后启动。
 * 直接调用本 service = AI 边界已被守护。
 */
@Service
public class SummaryService {

    static final String MODEL_NAME = "ollama:deepseek:8b";
    static final String MODEL_VERSION = "1.0.0";
    static final String PROMPT_VERSION = "daily-summary-v1";

    private final AiSummaryRepository repository;
    private final DailyReportRepository reportRepository;
    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SummaryService(AiSummaryRepository repository,
                          DailyReportRepository reportRepository,
                          OutboxWriter outboxWriter,
                          ObjectMapper objectMapper,
                          Clock clock) {
        this.repository = repository;
        this.reportRepository = reportRepository;
        this.outboxWriter = outboxWriter;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 触发摘要：同步写出 ai_summary 行（占位 summary_text）+ ai.summary.generated 事件。
     * 后续 plan-06-ai 消费事件后填写真实摘要 + 更新本行。
     */
    @Transactional
    public AiSummaryView trigger(long userId, long reportId) {
        DailyReport report = reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .filter(r -> userId == r.getUserId())
                .orElseThrow(() -> new com.lifewise.daily.service.exception.DailyReportNotFoundException(reportId));
        JsonNode inputSnapshot = snapshotOf(report);
        String cacheKey = computeCacheKey(userId, report, SummaryKind.DAILY, inputSnapshot);
        AiSummary existing = repository.findByCacheKey(cacheKey).orElse(null);
        OffsetDateTime now = OffsetDateTime.now(clock);
        AiSummary saved;
        if (existing != null) {
            // 命中 cache_key：
            //   - !user_edited：幂等返回（同一日报同一 kind 同一快照）
            //   - user_edited=true：BR-21.c 用户编辑过，AI 不得覆盖；返回原记录
            return AiSummaryView.from(existing);
        }
        AiSummary draft = AiSummary.aiCreate(
                userId, report.getId(), report.getLocalDate(),
                SummaryKind.DAILY, inputSnapshot,
                "摘要生成中…", MODEL_NAME, MODEL_VERSION,
                PROMPT_VERSION, cacheKey, null, now);
        saved = repository.save(draft);
        appendGeneratedEvent(userId, report.getId(), saved, now);
        return AiSummaryView.from(saved);
    }

    @Transactional(readOnly = true)
    public AiSummaryView findLatestByReport(DailyReport report) {
        return repository
                .findFirstByDailyReportIdAndSummaryKindAndDeletedAtIsNullOrderByGeneratedAtDesc(
                        report.getId(), SummaryKind.DAILY)
                .map(AiSummaryView::from)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public AiSummaryView get(long userId, long reportId) {
        DailyReport report = reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .filter(r -> userId == r.getUserId())
                .orElseThrow(() -> new com.lifewise.daily.service.exception.DailyReportNotFoundException(reportId));
        AiSummary summary = repository
                .findFirstByDailyReportIdAndSummaryKindAndDeletedAtIsNullOrderByGeneratedAtDesc(
                        report.getId(), SummaryKind.DAILY)
                .orElseThrow(() -> new AiSummaryNotFoundException(report.getId()));
        return AiSummaryView.from(summary);
    }

    /** BR-21：日报软删时级联软删摘要。 */
    @Transactional
    public void softDeleteAllByReport(long userId, long reportId) {
        List<AiSummary> all = repository.findByDailyReportIdAndDeletedAtIsNullOrderByGeneratedAtDesc(reportId);
        for (AiSummary s : all) {
            if (userId != s.getUserId()) {
                continue;
            }
            s.softDelete();
            repository.save(s);
        }
    }

    private JsonNode snapshotOf(DailyReport report) {
        try {
            // LinkedHashMap 保证插入顺序 → snapshot.toString() 跨 JVM/重启稳定 →
            // computeCacheKey 输出确定（避免 HashMap 迭代顺序不一致导致 cache miss）。
            java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();
            map.put("reportId", report.getId());
            map.put("reportDate", report.getLocalDate().toString());
            map.put("title", report.getTitle() == null ? "" : report.getTitle());
            map.put("mood", report.getMood() == null ? null : report.getMood().name());
            return objectMapper.readTree(objectMapper.writeValueAsString(map));
        } catch (Exception ex) {
            throw new RuntimeException("Failed to build summary input snapshot", ex);
        }
    }

    private String computeCacheKey(long userId, DailyReport report, SummaryKind kind,
                                   JsonNode snapshot) {
        // 用 | 分隔拼接，避免 "1:2:DAILY:abc" 与 "1:2DAILY:abc" 这种边界碰撞；
        // hashCode 32-bit 不去重，但 cache_key 在 DB 上有 UNIQUE 约束兜底。
        String raw = userId + "|" + report.getId() + "|" + kind.name() + "|" + snapshot.toString();
        int hash = raw.hashCode();
        return "daily:" + userId + ":" + report.getId() + ":" + kind.name() + ":" + Integer.toHexString(hash);
    }

    private void appendGeneratedEvent(long userId, long reportId, AiSummary saved,
                                      OffsetDateTime now) {
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.AI_SUMMARY_GENERATED.eventType(),
                1, now, userId, "ai_summary", saved.getId(),
                null, null, null,
                new AiSummaryGeneratedPayload(reportId, saved.getId(), userId,
                        saved.getModelName(), saved.getModelVersion(), now).toMap()));
    }

    /** 内部辅助：构造空 payload。 */
    @SuppressWarnings("unused")
    private static java.util.Map<String, Object> empty() {
        return java.util.Map.of();
    }
}
