package com.lifewise.expense.service;

import com.lifewise.expense.domain.ExpenseCategory;
import com.lifewise.expense.repository.CategoryRepository;
import com.lifewise.shared.integration.event.UserRegisteredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 首次注册预置「其他」分类（plan-03-expense §1.3 + BR-24）。
 *
 * <p>在注册事务内同步通过 Spring {@link EventListener} 触发：
 * <ul>
 *   <li>AuthService.register 提交 {@code UserRegisteredPayload} 事件</li>
 *   <li>本类监听后为该用户创建 {@code is_user_default = TRUE} 的「其他」分类</li>
 *   <li>两者共享同一事务（注册事务），保证失败回滚一致</li>
 * </ul>
 *
 * <p><b>并发安全（plan-03 review M8）</b>：{@code uq_expense_categories_user_default}
 * partial unique index 守护「每用户仅 1 个 user_default」。两并发注册若同时进入
 * 本方法，第二条 INSERT 抛 {@link DataIntegrityViolationException}，catch 后
 * 再查询（已被另一事务先创建）——自然幂等。
 *
 * <p>注：当前实现仅依赖 Spring 进程内事件。v1.1 若引入集群部署，需切换为
 * 监听 outbox {@code auth.user.registered} 事件，由共享的 OutboxWorker 串行消费。
 */
@Service
public class CategorySeedService {

    private static final Logger LOG = LoggerFactory.getLogger(CategorySeedService.class);

    private final CategoryRepository categoryRepository;

    public CategorySeedService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /**
     * 确保 userId 拥有「其他」分类（{@code is_user_default = TRUE}）。
     * 已有则跳过；无则创建。M8 并发安全：捕获 unique violation 后重新查询。
     */
    @Transactional
    public void ensureUserDefault(Long userId) {
        if (userId == null) {
            throw new IllegalArgumentException("userId required");
        }
        if (categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(userId).isPresent()) {
            return;
        }
        try {
            ExpenseCategory seeded = ExpenseCategory.createUserDefault(
                    userId,
                    "其他",
                    "📦",
                    "#9CA3AF",
                    9999);
            categoryRepository.save(seeded);
            LOG.info("[expense] user_default category seeded userId={}", userId);
        } catch (DataIntegrityViolationException ex) {
            // M8: 并发场景下另一事务已先创建，重查确认即可。
            if (categoryRepository.findFirstByUserIdAndUserDefaultTrueAndDeletedAtIsNull(userId).isPresent()) {
                LOG.info("[expense] user_default already seeded by concurrent tx userId={}", userId);
                return;
            }
            throw ex;
        }
    }

    /**
     * 监听 {@link UserRegisteredEvent}（plan-03-expense BR-24 触发点）。
     * 与 AuthService.register 共享同一事务（Spring 同步 publishEvent 在事务内完成）。
     */
    @EventListener
    public void onUserRegistered(UserRegisteredEvent event) {
        ensureUserDefault(event.userId());
    }
}
