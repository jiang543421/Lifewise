package com.lifewise.ai.repository;

import com.lifewise.ai.domain.AiJob;
import com.lifewise.ai.domain.enums.AiJobStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * ai_jobs 仓储（plan-06-ai §1）。
 *
 * <p>查询统一带 userId 过滤；ownership 在 service 层二次校验。
 */
public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    Optional<AiJob> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    @Query("""
            SELECT j FROM AiJob j
            WHERE j.userId = :userId
              AND j.deletedAt IS NULL
              AND (:status IS NULL OR j.status = :status)
            ORDER BY j.createdAt DESC
            """)
    Page<AiJob> findByUserIdAndOptionalStatus(@Param("userId") Long userId,
                                              @Param("status") AiJobStatus status,
                                              Pageable pageable);

    /** 幂等性查询（plan §7.6 job_should_be_idempotent）。 */
    @Query("""
            SELECT j FROM AiJob j
            WHERE j.userId = :userId
              AND j.jobType = :jobType
              AND j.periodStart = :from
              AND j.periodEnd = :to
              AND j.deletedAt IS NULL
              AND j.status IN ('PENDING','RUNNING','RUNNING_DEGRADED','DONE','DONE_PARTIAL','DONE_NO_LLM')
            ORDER BY j.createdAt DESC
            """)
    List<AiJob> findActiveByUserTypePeriod(@Param("userId") Long userId,
                                           @Param("jobType") com.lifewise.ai.domain.enums.AiJobType jobType,
                                           @Param("from") java.time.LocalDate from,
                                           @Param("to") java.time.LocalDate to);
}