package com.lifewise.task.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/**
 * 任务不存在或不属于当前用户（plan-01-task §2.1）。
 *
 * <p>跨用户访问与不存在统一返回 404 防枚举（业务架构 §7）。
 */
public class TaskNotFoundException extends ResourceNotFoundException {
    public TaskNotFoundException(Long id) {
        super("task", id);
    }
}