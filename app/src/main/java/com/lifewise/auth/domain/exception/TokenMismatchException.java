package com.lifewise.auth.domain.exception;

import com.lifewise.shared.integration.dto.ErrorCode;

/**
 * refresh token 的 JWT claims 与 DB row 不一致（plan-auth review H2 修复）。
 *
 * <p>典型场景：攻击者拿到一个合法签名的 refresh JWT，但用另一个 token 的
 * 哈希替换，按 token_hash 查到的 row 与 claims.userId / familyId 错位。
 * 此时必须拒绝认证，且不能撤销任何 family（避免对无辜用户 DoS）。
 *
 * <p>对应 CLAUDE.md §7 「认证 / 授权」中「禁止 confused deputy」。
 */
public class TokenMismatchException extends AuthDomainException {

    public TokenMismatchException(String reason) {
        super(ErrorCode.TOKEN_INVALID, "refresh token claims mismatch: " + reason);
    }
}
