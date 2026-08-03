package com.lifewise.daily.service.exception;

import java.time.LocalDate;

/** 同一用户同一 localDate 已有日报（BR-06 约束）。 */
public class DuplicateDailyReportException extends DailyReportDomainException {
    private final long userId;
    private final LocalDate localDate;

    public DuplicateDailyReportException(long userId, LocalDate localDate) {
        super("daily_report already exists for userId=" + userId + " localDate=" + localDate);
        this.userId = userId;
        this.localDate = localDate;
    }

    public long userId() { return userId; }
    public LocalDate localDate() { return localDate; }
}
