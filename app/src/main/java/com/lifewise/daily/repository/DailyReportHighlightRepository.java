package com.lifewise.daily.repository;

import com.lifewise.daily.domain.DailyReportHighlight;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 亮点仓库：按 daily_report_id 列出 + 软删过滤。 */
public interface DailyReportHighlightRepository
        extends JpaRepository<DailyReportHighlight, Long> {

    List<DailyReportHighlight> findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(
            Long dailyReportId);

    Optional<DailyReportHighlight> findByIdAndDeletedAtIsNull(Long id);

    long countByDailyReportIdAndDeletedAtIsNull(Long dailyReportId);
}
