package com.lifewise.task.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 习惯不存在或不属于当前用户（plan-01-task §2.2）。
 */
public class HabitNotFoundException extends ResourceNotFoundException {
    public HabitNotFoundException(Long id) {
        super("habit", id);
    }
}