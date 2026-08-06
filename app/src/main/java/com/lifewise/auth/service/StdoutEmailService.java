package com.lifewise.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * v1.0 Email 实现（plan-auth §5.4 + B-7 closure）。
 *
 * <p>写 SLF4J INFO 日志, docker compose logs 可见。v1.1+ 多用户接入 SMTP 时
 * 添加新 {@code @Service} + 标注 {@code @Primary}, 旧实现自动失效（参见
 * {@link ConditionalOnMissingBean}）。
 *
 * <p>v1.0 单用户场景: userId=1 永远知道密码, forgot-password 实际不会触发;
 * 此实现仅为 spec / IT 验证存在。
 */
@Service
@ConditionalOnMissingBean(name = "smtpEmailService")
public class StdoutEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(StdoutEmailService.class);

    @Override
    public void sendPasswordReset(String to, String rawToken) {
        // 故意 INFO 级别便于 docker logs 抓取；rawToken 不脱敏因为只 v1.0 本机用。
        log.info("[v1.0 email] password reset to={} token={}", to, rawToken);
    }
}