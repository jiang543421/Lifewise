package com.lifewise.auth.repository;

import com.lifewise.auth.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * users 仓库（plan-auth §3.1 + V1）。
 *
 * <p>Spring Data JPA 派生方法：唯一约束 {@code users.email} 保证
 * {@link #findByEmail(String)} 最多返回 1 行。
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}