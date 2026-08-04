package com.lifewise.plan.service.exception;

import com.lifewise.shared.integration.port.ResourceNotFoundException;

/** 跨模块 task 链接失败：link/touchActivity 时 task 在 task 模块中不存在或不属于当前用户。 */
public class CrossModuleTaskNotFoundException extends ResourceNotFoundException {
    public CrossModuleTaskNotFoundException(Long taskId) {
        super("task", taskId);
    }
}