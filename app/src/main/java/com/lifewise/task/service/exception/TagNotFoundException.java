package com.lifewise.task.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 标签不存在或不属于当前用户（plan-01-task §2.3）。
 */
public class TagNotFoundException extends ResourceNotFoundException {
    public TagNotFoundException(Long id) {
        super("task_tag", id);
    }
}