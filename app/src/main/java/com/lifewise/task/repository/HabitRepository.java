package com.lifewise.task.repository;

import com.lifewise.task.domain.Habit;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Habit 仓库接口（plan-01-task §1）。
 */
public interface HabitRepository extends JpaRepository<Habit, Long> {

    Optional<Habit> findByIdAndDeletedAtIsNull(Long id);

    List<Habit> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);

    List<Habit> findByUserIdAndArchivedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(Long userId);
}