package com.lifewise.auth.repository;

import com.lifewise.auth.domain.PasswordResetToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * password_reset_tokens 仓库（V50 + B-7 closure）。
 *
 * <p>Spring Data JPA 派生方法：唯一约束 {@code token_hash} 保证
 * {@link #findByTokenHash(String)} 最多返回 1 行。
 */
@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
}