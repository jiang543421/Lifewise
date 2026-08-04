package com.lifewise.daily.service.exception;

/** content 超过 50000 字符（BR-25）。 */
public class ContentTooLongException extends DailyReportDomainException {
    public ContentTooLongException(int actual) {
        super("content too long: " + actual + " > 50000");
    }
}
