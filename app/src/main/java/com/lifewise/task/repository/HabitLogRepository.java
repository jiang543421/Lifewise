package com.lifewise.task.repository;

import com.lifewise.task.domain.HabitLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * HabitLog 仓库接口（plan-01-task §1）。
 */
public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    Optional<HabitLog> findByHabitIdAndLocalDate(Long habitId, LocalDate localDate);

    List<HabitLog> findByHabitIdAndLocalDateGreaterThanEqualOrderByLocalDateDesc(
            Long habitId, LocalDate fromDate);

    /**
     * 补卡窗口计数：同习惯、同 user、local_date ∈ [windowStart, today]，
     * 用于 BACKFILL_RATE_LIMIT（同日 ≤ 5 次）。
     */
    @Query("""
            select count(h) from HabitLog h
            where h.habitId = :habitId
              and h.userId = :userId
              and h.localDate >= :windowStart
              and h.localDate <= :today
              and h.source = com.lifewise.task.domain.HabitLogSource.BACKFILL
            """)
    long countBackfillInWindow(@Param("habitId") Long habitId,
                               @Param("userId") Long userId,
                               @Param("windowStart") LocalDate windowStart,
                               @Param("today") LocalDate today);

    List<HabitLog> findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
            Long habitId, Long userId, LocalDate fromDate, LocalDate toDate);
}