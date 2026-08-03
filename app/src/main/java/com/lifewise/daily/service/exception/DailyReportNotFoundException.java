package com.lifewise.daily.service.exception;

/** 日报不存在或不属于该用户。 */
public class DailyReportNotFoundException extends DailyReportDomainException {
    public DailyReportNotFoundException(long id) {
        super("daily_report not found: id=" + id);
    }

    public DailyReportNotFoundException(long userId, java.time.LocalDate localDate) {
        super("daily_report not found: userId=" + userId + " localDate=" + localDate);
    }
}
