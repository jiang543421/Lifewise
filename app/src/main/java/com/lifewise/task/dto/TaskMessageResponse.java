package com.lifewise.task.dto;

public record TaskMessageResponse(String message) {
    public static TaskMessageResponse ok() {
        return new TaskMessageResponse("ok");
    }
}
