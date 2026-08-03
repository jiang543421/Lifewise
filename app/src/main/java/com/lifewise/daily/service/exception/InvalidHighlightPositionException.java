package com.lifewise.daily.service.exception;

/** 亮点 sortOrder < 0 或越界。 */
public class InvalidHighlightPositionException extends DailyReportDomainException {
    public InvalidHighlightPositionException(int position) {
        super("invalid highlight sortOrder: " + position);
    }
}
