package com.lifewise.plan.repository;

import com.lifewise.plan.domain.MilestoneTaskLink;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Milestone ↔ Task 链接仓库（plan-05-plan §2.3）。
 *
 * <p>v1.0 仅在 link 操作时插入；删除走 ON DELETE CASCADE（V4 DDL）。
 */
public interface MilestoneTaskLinkRepository
        extends JpaRepository<MilestoneTaskLink, MilestoneTaskLink.PK> {

    List<MilestoneTaskLink> findAllByMilestoneId(Long milestoneId);
}