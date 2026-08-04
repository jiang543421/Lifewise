package com.lifewise.diet.repository;

import com.lifewise.diet.domain.Meal;
import com.lifewise.diet.domain.MealType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 餐次仓库（plan-04-diet §3）。
 *
 * <p>分区表 Hibernate 仍按 IDENTITY 单列查询；{@code local_date} 作为分区键不可更新。
 */
public interface MealRepository extends JpaRepository<Meal, Long> {

    Optional<Meal> findByIdAndDeletedAtIsNull(Long id);

    List<Meal> findByUserIdAndLocalDateBetweenAndDeletedAtIsNullOrderByLocalDateAsc(
            Long userId, LocalDate from, LocalDate to);

    @Query("""
            select m from Meal m
            where m.userId = :userId
              and m.deletedAt is null
              and (cast(:from as date) is null or m.localDate >= :from)
              and (cast(:to as date) is null or m.localDate <= :to)
              and (cast(:type as string) is null or m.mealType = :type)
            order by m.localDate desc, m.id desc
            """)
    Page<Meal> search(@Param("userId") Long userId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to,
                      @Param("type") MealType type,
                      Pageable pageable);
}