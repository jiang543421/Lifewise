package com.lifewise.task.repository;

import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Task 仓库接口（plan-01-task §1）。
 *
 * <p>Spring Data JPA 派生查询 + 自定义 JPQL。
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    Optional<Task> findByIdAndDeletedAtIsNull(Long id);

    @Query("""
            select t from Task t
            where t.userId = :userId
              and t.deletedAt is null
              and (:status is null or t.status = :status)
              and (:priority is null or t.priority = :priority)
            order by t.dueAt nulls last, t.createdAt desc
            """)
    Page<Task> search(@Param("userId") Long userId,
                       @Param("status") TaskStatus status,
                       @Param("priority") String priority,
                       Pageable pageable);

    @Query("""
            select count(t) from Task t
            where t.userId = :userId
              and t.deletedAt is null
              and t.status = com.lifewise.task.domain.TaskStatus.DONE
              and t.completedAt >= :since
            """)
    long countCompletedSince(@Param("userId") Long userId,
                             @Param("since") OffsetDateTime since);

    List<Task> findByUserIdAndIdInAndDeletedAtIsNull(Long userId, List<Long> ids);

    List<Task> findByUserIdAndParentIdAndDeletedAtIsNull(Long userId, Long parentId);
}