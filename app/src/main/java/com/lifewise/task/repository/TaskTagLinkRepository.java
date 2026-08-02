package com.lifewise.task.repository;

import com.lifewise.task.domain.TaskTagLink;
import com.lifewise.task.domain.TaskTagLink.Pk;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TaskTagLink 仓库接口（plan-01-task §1）。
 */
public interface TaskTagLinkRepository extends JpaRepository<TaskTagLink, Pk> {

    void deleteByIdTaskId(Long taskId);

    long countByIdTaskId(Long taskId);

    java.util.List<TaskTagLink> findByIdTaskId(Long taskId);
}