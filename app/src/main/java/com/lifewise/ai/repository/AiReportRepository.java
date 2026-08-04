package com.lifewise.ai.repository;

import com.lifewise.ai.domain.AiReport;
import com.lifewise.ai.domain.enums.ReportKind;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiReportRepository extends JpaRepository<AiReport, Long> {

    Optional<AiReport> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    Optional<AiReport> findByJobIdAndDeletedAtIsNull(Long jobId);

    @Query("""
            SELECT r FROM AiReport r
            WHERE r.userId = :userId
              AND r.deletedAt IS NULL
              AND (:kind IS NULL OR r.reportKind = :kind)
            ORDER BY r.createdAt DESC
            """)
    Page<AiReport> findByUserIdAndOptionalKind(@Param("userId") Long userId,
                                               @Param("kind") ReportKind kind,
                                               Pageable pageable);
}