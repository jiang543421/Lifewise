package com.lifewise.daily.dto;

/** 统一的简单 ok 响应（与 task 模块 TaskMessageResponse 对齐）。 */
public record DailyMessageResponse(String message) {
    public static DailyMessageResponse ok() {
        return new DailyMessageResponse("ok");
    }
}
