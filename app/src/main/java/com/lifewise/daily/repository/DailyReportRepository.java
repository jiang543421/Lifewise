package com.lifewise.daily.repository;

import com.lifewise.daily.domain.DailyReport;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 日报仓库接口（plan-02-daily §3）。
 *
 * <p>分区表 Hibernate 仍按 IDENTITY 单列查询；{@code local_date} 作为分区键不可更新。
 * 全文检索走原生 SQL + {@code content_tsv} GIN 索引（V37 落地）。
 */
public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {

    Optional<DailyReport> findByIdAndDeletedAtIsNull(Long id);

    Optional<DailyReport> findByUserIdAndLocalDateAndDeletedAtIsNull(Long userId, LocalDate localDate);

    /**
     * 按年月切片（plan-02-daily §5.1 daily_query_should_filter_by_year_month）。
     * 不传 month 即查整年；{@code includeDraft=false} 过滤草稿。
     */
    @Query("""
            select d from DailyReport d
            where d.userId = :userId
              and d.deletedAt is null
              and (:includeDraft = true or d.draft = false)
              and (:year is null or function('year', d.localDate) = :year)
              and (:month is null or function('month', d.localDate) = :month)
            order by d.localDate desc
            """)
    Page<DailyReport> searchByMonth(@Param("userId") Long userId,
                                    @Param("year") Integer year,
                                    @Param("month") Integer month,
                                    @Param("includeDraft") boolean includeDraft,
                                    Pageable pageable);

    /**
     * tsvector 全文检索（原生 SQL，PG-only）。
     * 返回 {@code (id, local_date, snippet)} 三元组用于组装 SearchHit。
     *
     * <p>NULL 行为：{@code tsQuery} 为 NULL 时 tsquery 自动匹配空，等价于无匹配。
     * Service 层负责过滤/校验。
     */
    @Query(value = """
            SELECT id AS id, local_date AS local_date,
                   ts_headline('simple', content, plainto_tsquery('simple', :tsQuery),
                               'StartSel=<em>,StopSel=</em>,MaxFragments=1,MaxWords=20,MinWords=5')
                       AS snippet,
                   ts_rank(content_tsv, plainto_tsquery('simple', :tsQuery)) AS score
            FROM daily_reports
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND content_tsv @@ plainto_tsquery('simple', :tsQuery)
              AND (CAST(:from AS DATE) IS NULL OR local_date >= CAST(:from AS DATE))
              AND (CAST(:to AS DATE) IS NULL OR local_date <= CAST(:to AS DATE))
            ORDER BY score DESC, local_date DESC
            """,
            countQuery = """
            SELECT count(*)
            FROM daily_reports
            WHERE user_id = :userId
              AND deleted_at IS NULL
              AND content_tsv @@ plainto_tsquery('simple', :tsQuery)
              AND (CAST(:from AS DATE) IS NULL OR local_date >= CAST(:from AS DATE))
              AND (CAST(:to AS DATE) IS NULL OR local_date <= CAST(:to AS DATE))
            """,
            nativeQuery = true)
    Page<Object[]> fullTextSearch(@Param("userId") Long userId,
                                  @Param("tsQuery") String tsQuery,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to,
                                  Pageable pageable);

    @Query("""
            select d from DailyReport d
            where d.userId = :userId
              and d.deletedAt is null
              and d.localDate >= :from
              and d.localDate <= :to
            order by d.localDate asc
            """)
    List<DailyReport> findInRange(@Param("userId") Long userId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    /**
     * 心情均值（{@code energy_score} 1~5 数值列；无评分时按 0 处理）。
     * plan-02-daily 原期望按 mood 数值均值；当前 schema 没有 mood 数值列，
     * 数值化靠 {@code energy_score}，由 service 层映射到 DailySnapshot.moodScore。
     */
    @Query("""
            select coalesce(avg(cast(d.energyScore as double)), 0)
            from DailyReport d
            where d.userId = :userId
              and d.deletedAt is null
              and d.localDate >= :from
              and d.localDate <= :to
              and d.energyScore is not null
            """)
    Double averageEnergyScoreInRange(@Param("userId") Long userId,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    @Query("""
            select count(d) from DailyReport d
            where d.userId = :userId
              and d.deletedAt is null
              and d.localDate >= :from
              and d.localDate <= :to
            """)
    long countInRange(@Param("userId") Long userId,
                      @Param("from") LocalDate from,
                      @Param("to") LocalDate to);
}
