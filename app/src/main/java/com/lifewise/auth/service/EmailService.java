package com.lifewise.auth.service;

/**
 * Email 投递抽象（plan-auth §5.4 + B-7 closure）。
 *
 * <p>v1.0 单用户白名单下, {@link StdoutEmailService} 实现仅写 stdout（江兴旺
 * 本机登录 docker compose logs 读取）。v1.1+ 多用户切换时实现 SMTP 接入。
 *
 * <p>接口稳定: forgot-password / reset-password 等用例只依赖此接口,
 * 切换实现不影响上层调用。
 */
public interface EmailService {

    /**
     * 发送密码重置邮件。
     *
     * @param to      收件人邮箱
     * @param rawToken 原始 reset token（仅一次出现, 用于邮件链接拼接）
     */
    void sendPasswordReset(String to, String rawToken);
}