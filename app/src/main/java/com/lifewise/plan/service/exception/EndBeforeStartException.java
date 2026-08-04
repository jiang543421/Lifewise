package com.lifewise.plan.service.exception;

import java.time.LocalDate;

/** BR-15：plan.endDate 在 startDate 之前或等于 startDate。 */
public class EndBeforeStartException extends RuntimeException {
    private final LocalDate startDate;
    private final LocalDate endDate;

    public EndBeforeStartException(LocalDate startDate, LocalDate endDate) {
        super("plan target_end_date (" + endDate + ") must be on or after start_date (" + startDate + ")");
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
}