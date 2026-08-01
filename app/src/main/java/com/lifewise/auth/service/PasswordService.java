package com.lifewise.auth.service;

import com.lifewise.auth.domain.exception.WeakPasswordException;
import java.util.regex.Pattern;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 密码强度 + BCrypt 编码（plan-auth §1 + CLAUDE.md §7.3）。
 *
 * <p>v1.0 强度规则（CLAUDE.md §7.3）：
 * <ul>
 *   <li>长度 ≥ 12</li>
 *   <li>至少 1 个大写字母</li>
 *   <li>至少 1 个小写字母</li>
 *   <li>至少 1 个数字</li>
 *   <li>至少 1 个符号（{@code !@#$%^&*()_+=[\]{};':"\\|,.<>/?-}）</li>
 * </ul>
 *
 * <p>zxcvbn ≥ 3（plan-auth §1.1）作为未来扩展，本期 v1.0 范围内由上述规则替代。
 */
@Service
public class PasswordService {

    private static final Pattern UPPER = Pattern.compile("[A-Z]");
    private static final Pattern LOWER = Pattern.compile("[a-z]");
    private static final Pattern DIGIT = Pattern.compile("[0-9]");
    private static final Pattern SYMBOL = Pattern.compile("[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?~`]");

    private final BCryptPasswordEncoder encoder;

    public PasswordService(BCryptPasswordEncoder encoder) {
        this.encoder = encoder;
    }

    /**
     * 校验强度，违规则抛 {@link WeakPasswordException}。
     */
    public void assertStrong(String password) {
        if (password == null || password.length() < 12) {
            throw new WeakPasswordException("must be at least 12 characters");
        }
        if (!UPPER.matcher(password).find()) {
            throw new WeakPasswordException("must contain uppercase letter");
        }
        if (!LOWER.matcher(password).find()) {
            throw new WeakPasswordException("must contain lowercase letter");
        }
        if (!DIGIT.matcher(password).find()) {
            throw new WeakPasswordException("must contain digit");
        }
        if (!SYMBOL.matcher(password).find()) {
            throw new WeakPasswordException("must contain symbol");
        }
    }

    /** BCrypt cost=12 编码（CLAUDE.md §7.3） */
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    /** BCrypt 校验；不抛异常（错误返回 false） */
    public boolean matches(String rawPassword, String hashed) {
        return encoder.matches(rawPassword, hashed);
    }
}