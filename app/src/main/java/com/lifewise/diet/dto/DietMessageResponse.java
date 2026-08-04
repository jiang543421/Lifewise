package com.lifewise.diet.dto;

/** 通用成功消息响应（plan-04-diet §2.1 delete / §2.2 delete）。 */
public record DietMessageResponse(String message) {
    public static DietMessageResponse ok() {
        return new DietMessageResponse("ok");
    }
}