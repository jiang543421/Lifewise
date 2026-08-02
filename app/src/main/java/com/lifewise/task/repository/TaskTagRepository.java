package com.lifewise.task.repository;

import com.lifewise.task.domain.TaskTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * TaskTag 仓库接口（plan-01-task §1）。
 */
public interface TaskTagRepository extends JpaRepository<TaskTag, Long> {

    Optional<TaskTag> findByUserIdAndName(Long userId, String name);

    List<TaskTag> findByUserIdOrderByNameAsc(Long userId);

    List<TaskTag> findByUserId(Long userId);

    @Query("""
            select l.id.tagId from TaskTagLink l
            where l.id.taskId = :taskId
            """)
    List<Long> findTagIdsByTaskId(@Param("taskId") Long taskId);
}