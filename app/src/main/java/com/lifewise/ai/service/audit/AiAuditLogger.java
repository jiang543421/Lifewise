package com.lifewise.ai.service.audit;

/**
 * AI 审计日志接口（plan-06-ai §7.7；BR-19/22）。
 *
 * <p>所有 AI 流水的 4 类决策（CONSENT_CHECK / CONSENT_UPDATE / DATA_FETCH /
 * MODEL_CALL / GENERATE）必须通过本接口留痕。默认实现 {@code ChatMessageAiAuditLogger}
 * 写入 chat_messages role=SYSTEM 行，应用层只 INSERT 不 UPDATE/DELETE（DB 角色 GRANT 控制）。
 *
 * <p>调用约束：
 * <ul>
 *   <li>调用方可在同业务事务中写入，事务回滚时审计同步回滚（plan §6 步骤 2.5）</li>
 *   <li>traceId 由 AiAuditLogger 实现自动生成 MDC UUID，无需外部传入</li>
 *   <li>任何异常允许抛出，由 AiAuditLogger 实现决定是否降级为本地日志</li>
 * </ul>
 */
public interface AiAuditLogger {

    /**
     * 写入一条审计。
     *
     * @param userId  所属用户（user_profiles.user_id 必填）
     * @param decision 决策类型 + 决策结果 + 上下文（traceId / latencyMs / tokensUsed / metadata）
     */
    void log(Long userId, AiAuditDecision decision);
}
