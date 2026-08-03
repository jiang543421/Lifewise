package com.lifewise.daily.service;

import com.lifewise.daily.dto.DailyReportSearchHit;
import com.lifewise.daily.repository.DailyReportRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 全文检索（plan-02-daily §2.3 + §5.3）——基于 tsvector + GIN 索引（V37）。
 *
 * <p>tsQuery 由 controller 校验（trim + 非空）；repository 原生查询使用
 * {@code plainto_tsquery} + {@code ts_rank} 排序。
 */
@Service
public class SearchService {

    private final DailyReportRepository repository;

    public SearchService(DailyReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<DailyReportSearchHit> search(long userId, String tsQuery, LocalDate from,
                                             LocalDate to, Pageable pageable) {
        // 安全门：trim 后空串直接返回空分页
        String normalized = tsQuery == null ? "" : tsQuery.trim();
        if (normalized.isEmpty()) {
            return Page.empty(pageable);
        }
        Page<Object[]> raw = repository.fullTextSearch(userId, normalized, from, to, pageable);
        return raw.map(row -> {
            Long id = ((Number) row[0]).longValue();
            LocalDate date = ((java.sql.Date) row[1]).toLocalDate();
            String snippet = (String) row[2];
            double score = row[3] == null ? 0.0 : ((Number) row[3]).doubleValue();
            return new DailyReportSearchHit(id, date, snippet, score);
        });
    }
}
