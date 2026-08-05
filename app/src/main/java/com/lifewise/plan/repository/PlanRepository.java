package com.lifewise.plan.repository;

import com.lifewise.plan.domain.Plan;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Plan 仓库接口（plan-05-plan §3 - 6 端点）。
 *
 * <p>所有方法默认过滤 {@code deleted_at IS NULL}，并按 {@code user_id} 隔离。
 */
public interface PlanRepository extends JpaRepository<Plan, Long> {

    Optional<Plan> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    /** 仅 ACTIVE（默认列表）。 */
    @Query("""
            select p from Plan p
            where p.userId = :userId
              and p.deletedAt is null
              and p.status = com.lifewise.plan.domain.PlanStatus.ACTIVE
            order by p.startDate desc nulls last, p.id desc
            """)
    List<Plan> findActiveByUser(@Param("userId") Long userId);

    /** ACTIVE + CANCELLED（includeCancelled=true 时）。 */
    @Query("""
            select p from Plan p
            where p.userId = :userId
              and p.deletedAt is null
              and p.status in (com.lifewise.plan.domain.PlanStatus.ACTIVE,
                               com.lifewise.plan.domain.PlanStatus.CANCELLED)
            order by p.startDate desc nulls last, p.id desc
            """)
    List<Plan> findAllActiveOrCancelledByUser(@Param("userId") Long userId);

    /**
     * 长期未活动的 plan（last_activity_at 在 cutoff 之前或为 null）。
     * 用于 PlanStaleNotifyJob（plan-05-plan §5.7）。
     */
    @Query("""
            select p from Plan p
            where p.deletedAt is null
              and p.status = com.lifewise.plan.domain.PlanStatus.ACTIVE
              and (p.lastActivityAt is null or p.lastActivityAt < :cutoff)
            """)
    List<Plan> findStaleBefore(@Param("cutoff") OffsetDateTime cutoff);
}