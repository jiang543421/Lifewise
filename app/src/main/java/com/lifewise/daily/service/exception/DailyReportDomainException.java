package com.lifewise.daily.service.exception;

/** daily 模块领域异常基类（plan-02-daily §5）。 */
public class DailyReportDomainException extends RuntimeException {
    public DailyReportDomainException(String message) {
        super(message);
    }
}
