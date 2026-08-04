package com.lifewise.daily.service.exception;

/** 日报当前没有任何 AI 摘要。 */
public class AiSummaryNotFoundException extends DailyReportDomainException {
    public AiSummaryNotFoundException(long dailyReportId) {
        super("ai_summary not found for reportId=" + dailyReportId);
    }
}
