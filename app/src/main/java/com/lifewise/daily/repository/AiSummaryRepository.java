package com.lifewise.daily.repository;

import com.lifewise.daily.domain.AiSummary;
import com.lifewise.daily.domain.SummaryKind;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 摘要仓库。
 *
 * <p>br 唯一键：{@code cache_key}（service 层计算 (userId, dailyReportId, summaryKind, payloadHash)）。
 */
public interface AiSummaryRepository extends JpaRepository<AiSummary, Long> {

    Optional<AiSummary> findByCacheKey(String cacheKey);

    Optional<AiSummary> findFirstByDailyReportIdAndSummaryKindAndDeletedAtIsNullOrderByGeneratedAtDesc(
            Long dailyReportId, SummaryKind summaryKind);

    List<AiSummary> findByDailyReportIdAndDeletedAtIsNullOrderByGeneratedAtDesc(Long dailyReportId);
}
