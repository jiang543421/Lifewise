package com.lifewise.auth.dto;

/** plan-auth §2.1 简单消息响应（logout / forgot-password 等）。 */
public record MessageResponse(String message) {

    public static MessageResponse ok() {
        return new MessageResponse("ok");
    }

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}