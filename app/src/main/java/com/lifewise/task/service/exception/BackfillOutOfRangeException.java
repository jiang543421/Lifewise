package com.lifewise.task.service.exception;

/**
 * 习惯补卡超出允许窗口 [today-3, today)（BR-05 + plan-01-task §5.2）。
 */
public class BackfillOutOfRangeException extends RuntimeException {
    public BackfillOutOfRangeException(String date) {
        super("backfill date out of range: " + date);
    }
}