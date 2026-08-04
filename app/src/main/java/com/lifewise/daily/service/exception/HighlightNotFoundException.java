package com.lifewise.daily.service.exception;

/** 亮点不存在或不属于该用户/该日报。 */
public class HighlightNotFoundException extends DailyReportDomainException {
    public HighlightNotFoundException(long id) {
        super("highlight not found: id=" + id);
    }
}
