package com.lifewise.ai.service.exception;

/** AI 作业不存在或无访问权限（404 CROSS_USER_ACCESS 统一返回防枚举）。 */
public class AiJobNotFoundException extends RuntimeException {
    public AiJobNotFoundException(Long id) {
        super("AI job not found: id=" + id);
    }
}