package com.lifewise.daily.service.exception;

/** 单条日报亮点超过 3 条（BR-08，应用层强制）。 */
public class HighlightLimitExceededException extends DailyReportDomainException {
    public HighlightLimitExceededException(long dailyReportId) {
        super("highlight limit exceeded (3 per report): reportId=" + dailyReportId);
    }
}
