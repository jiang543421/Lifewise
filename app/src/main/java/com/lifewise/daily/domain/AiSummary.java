package com.lifewise.daily.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifewise.common.BaseEntity;
import com.lifewise.shared.integration.port.ResourceNotFoundException;
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
 * AI 摘要实体（plan-02-daily §3 + V5 DDL ai_summaries + V21 扩展 + V25 收紧）。
 *
 * <p>BR 约束：
 * <ul>
 *   <li>BR-21 / BR-21.a：AI 写入路径仅限 summary.service 接口（应用层强制，非 DB CHECK）</li>
 *   <li>BR-21.b：{@code model_version} NOT NULL + {@code length 1~100}（V25）</li>
 *   <li>BR-21.b：{@code generated_at} NOT NULL</li>
 *   <li>BR-21.c：{@code user_edited = TRUE} 后 AI 不得自动覆盖（应用层强制）</li>
 *   <li>{@code cache_key} UNIQUE NOT NULL</li>
 * </ul>
 *
 * <p>NOTE：daily 摘要业务键为 {@code (daily_report_id, summary_kind)}；缓存键由 service 计算。
 */
@Entity
@Table(name = "ai_summaries")
public class AiSummary extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "daily_report_id")
    private Long dailyReportId;

    @Column(name = "local_date")
    private LocalDate localDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "summary_kind", nullable = false)
    private SummaryKind summaryKind;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", nullable = false, columnDefinition = "jsonb")
    private JsonNode inputSnapshot;

    @Column(name = "summary_text", nullable = false, length = 10000)
    private String summaryText;

    @Column(name = "model_name", nullable = false, length = 64)
    private String modelName;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    @Column(name = "prompt_version", nullable = false, length = 32)
    private String promptVersion;

    @Column(name = "cache_key", nullable = false, length = 200, unique = true)
    private String cacheKey;

    @Column(name = "tokens_used")
    private Integer tokensUsed;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    @Column(name = "user_edited", nullable = false)
    private boolean userEdited;

    protected AiSummary() {
        // JPA
    }

    private AiSummary(Long userId, Long dailyReportId, LocalDate localDate,
                      SummaryKind summaryKind, JsonNode inputSnapshot,
                      String summaryText, String modelName, String modelVersion,
                      String promptVersion, String cacheKey, Integer tokensUsed,
                      OffsetDateTime generatedAt) {
        this.userId = userId;
        this.dailyReportId = dailyReportId;
        this.localDate = localDate;
        this.summaryKind = summaryKind;
        this.inputSnapshot = inputSnapshot;
        this.summaryText = summaryText;
        this.modelName = modelName;
        this.modelVersion = modelVersion;
        this.promptVersion = promptVersion;
        this.cacheKey = cacheKey;
        this.tokensUsed = tokensUsed;
        this.generatedAt = generatedAt;
        this.userEdited = false;
    }

    /**
     * AI 完成时创建摘要记录（service 层入口，BR-21.a 强制）。
     *
     * @throws ResourceNotFoundException 必填字段缺失
     */
    public static AiSummary aiCreate(Long userId, Long dailyReportId, LocalDate localDate,
                                     SummaryKind summaryKind, JsonNode inputSnapshot,
                                     String summaryText, String modelName, String modelVersion,
                                     String promptVersion, String cacheKey, Integer tokensUsed,
                                     OffsetDateTime generatedAt) {
        if (userId == null || summaryText == null || summaryText.isBlank()
                || modelName == null || modelName.isBlank()
                || modelVersion == null || modelVersion.isBlank()
                || promptVersion == null || promptVersion.isBlank()
                || cacheKey == null || cacheKey.isBlank()
                || generatedAt == null || inputSnapshot == null) {
            throw new ResourceNotFoundException("ai_summary", "AI summary missing required fields");
        }
        if (modelVersion.length() > 100) {
            throw new IllegalArgumentException("modelVersion length must be <= 100");
        }
        if (summaryText.length() > 10000) {
            throw new IllegalArgumentException("summaryText length must be <= 10000");
        }
        return new AiSummary(userId, dailyReportId, localDate, summaryKind, inputSnapshot,
                summaryText, modelName, modelVersion, promptVersion, cacheKey,
                tokensUsed, generatedAt);
    }

    /**
     * 用户在 UI 上编辑摘要（BR-21.c：编辑后 AI 不得自动覆盖）。
     *
     * @param newText 用户编辑后的正文
     */
    public void userEdit(String newText) {
        if (newText == null || newText.isBlank()) {
            throw new IllegalArgumentException("summaryText must not be blank");
        }
        if (newText.length() > 10000) {
            throw new IllegalArgumentException("summaryText length must be <= 10000");
        }
        this.summaryText = newText;
        this.userEdited = true;
    }

    public Long getUserId() { return userId; }
    public Long getDailyReportId() { return dailyReportId; }
    public LocalDate getLocalDate() { return localDate; }
    public SummaryKind getSummaryKind() { return summaryKind; }
    public JsonNode getInputSnapshot() { return inputSnapshot; }
    public String getSummaryText() { return summaryText; }
    public String getModelName() { return modelName; }
    public String getModelVersion() { return modelVersion; }
    public String getPromptVersion() { return promptVersion; }
    public String getCacheKey() { return cacheKey; }
    public Integer getTokensUsed() { return tokensUsed; }
    public OffsetDateTime getGeneratedAt() { return generatedAt; }
    public boolean isUserEdited() { return userEdited; }
}
