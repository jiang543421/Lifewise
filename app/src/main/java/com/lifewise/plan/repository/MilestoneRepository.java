package com.lifewise.plan.repository;

import com.lifewise.plan.domain.Milestone;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Milestone 仓库接口（plan-05-plan §3 - 7 端点 + 1 任务）。
 *
 * <p>所有方法默认过滤 {@code deleted_at IS NULL}。
 */
public interface MilestoneRepository extends JpaRepository<Milestone, Long> {

    Optional<Milestone> findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(
            Long id, Long userId, Long planId);

    List<Milestone> findAllByPlanIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(Long planId);

    /**
     * 软删 plan 时同事务级联软删所有 milestones。
     *
     * <p>用 HQL {@code offset datetime} 而非 {@code CURRENT_TIMESTAMP}：后者在 Hibernate 6
     * 下推导为 {@code java.sql.Timestamp}，赋给 {@code OffsetDateTime} 类型的 deletedAt 会在
     * 启动期校验阶段抛 SemanticException，导致整个 Spring 上下文加载失败。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Milestone m set m.deletedAt = offset datetime where m.planId = :planId")
    int softDeleteByPlanId(@Param("planId") Long planId);

    /**
     * 找出所有到期（due_at < cutoff）且状态非 DONE/CANCELLED 的里程碑
     * —— MissedMilestoneJob 用（plan-05-plan §5.6）。
     */
    @Query("""
            select m from Milestone m
            where m.deletedAt is null
              and m.dueAt is not null
              and m.dueAt < :cutoff
              and m.status in (com.lifewise.plan.domain.MilestoneStatus.PENDING,
                               com.lifewise.plan.domain.MilestoneStatus.IN_PROGRESS)
            """)
    List<Milestone> findOverduePending(@Param("cutoff") OffsetDateTime cutoff);

    /** 跨模块读端口用：找出含指定 task_id 的所有 milestone（用于正向查询）。 */
    @Query(value = """
            select m.* from milestones m
            join milestone_task_links l on l.milestone_id = m.id
            where l.task_id = :taskId
              and m.deleted_at is null
            """, nativeQuery = true)
    List<Milestone> findByTaskId(@Param("taskId") Long taskId);

    /** ProgressController 计算用：同 planId 全集（含 CANCELLED，调用方过滤）。 */
    List<Milestone> findAllByPlanIdAndDeletedAtIsNull(Long planId);
}