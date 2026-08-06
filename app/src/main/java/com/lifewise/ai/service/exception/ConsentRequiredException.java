package com.lifewise.ai.service.exception;

/** 用户未开启 AI consent（plan-06-ai §7.1；BR-26）。 */
public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException() {
        super("AI consent required");
    }
}