package com.lifewise.ai.service.exception;

/** LLM 生成的 SQL 不安全（plan-06-ai §2.3 chat 双路径）。 */
public class UnsafeSqlException extends RuntimeException {
    public UnsafeSqlException(String reason) {
        super("Unsafe SQL rejected: " + reason);
    }
}