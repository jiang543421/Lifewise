package com.lifewise.daily.service;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.DailyReportHighlight;
import com.lifewise.daily.dto.HighlightRequest;
import com.lifewise.daily.dto.HighlightView;
import com.lifewise.daily.repository.DailyReportHighlightRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.HighlightLimitExceededException;
import com.lifewise.daily.service.exception.HighlightNotFoundException;
import com.lifewise.daily.service.exception.InvalidHighlightPositionException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 亮点 ≤ 3 条/日（BR-08，应用层强制）。 */
@Service
public class HighlightService {

    static final int MAX_HIGHLIGHTS_PER_DAY = 3;

    private final DailyReportRepository reportRepository;
    private final DailyReportHighlightRepository highlightRepository;

    public HighlightService(DailyReportRepository reportRepository,
                            DailyReportHighlightRepository highlightRepository) {
        this.reportRepository = reportRepository;
        this.highlightRepository = highlightRepository;
    }

    @Transactional(readOnly = true)
    public List<HighlightView> listByReport(long userId, long reportId) {
        DailyReport report = loadOwned(userId, reportId);
        return highlightRepository
                .findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(report.getId())
                .stream().map(HighlightView::from).toList();
    }

    @Transactional
    public HighlightView create(long userId, long reportId, HighlightRequest req) {
        DailyReport report = loadOwned(userId, reportId);
        long count = highlightRepository.countByDailyReportIdAndDeletedAtIsNull(report.getId());
        if (count >= MAX_HIGHLIGHTS_PER_DAY) {
            throw new HighlightLimitExceededException(report.getId());
        }
        int sortOrder = req.sortOrder() == null
                ? (int) count
                : validateSortOrder(req.sortOrder());
        DailyReportHighlight h = DailyReportHighlight.create(
                report.getId(), report.getLocalDate(),
                req.highlightType(), req.title(), req.description(),
                req.referenceType(), req.referenceId(), sortOrder);
        h = highlightRepository.save(h);
        return HighlightView.from(h);
    }

    @Transactional
    public HighlightView update(long userId, long reportId, long highlightId,
                                HighlightRequest req) {
        DailyReport report = loadOwned(userId, reportId);
        DailyReportHighlight h = highlightRepository.findByIdAndDeletedAtIsNull(highlightId)
                .filter(x -> x.getDailyReportId().equals(report.getId()))
                .orElseThrow(() -> new HighlightNotFoundException(highlightId));
        Integer sortOrder = req.sortOrder() == null ? null : validateSortOrder(req.sortOrder());
        h.applyUpdate(req.highlightType(), req.title(), req.description(),
                req.referenceType(), req.referenceId(), sortOrder);
        h = highlightRepository.save(h);
        return HighlightView.from(h);
    }

    @Transactional
    public void delete(long userId, long reportId, long highlightId) {
        DailyReport report = loadOwned(userId, reportId);
        DailyReportHighlight h = highlightRepository.findByIdAndDeletedAtIsNull(highlightId)
                .filter(x -> x.getDailyReportId().equals(report.getId()))
                .orElseThrow(() -> new HighlightNotFoundException(highlightId));
        h.softDelete();
        highlightRepository.save(h);
    }

    private DailyReport loadOwned(long userId, long reportId) {
        return reportRepository.findByIdAndDeletedAtIsNull(reportId)
                .filter(r -> userId == r.getUserId())
                .orElseThrow(() -> new com.lifewise.daily.service.exception.DailyReportNotFoundException(reportId));
    }

    private static int validateSortOrder(Integer sortOrder) {
        if (sortOrder == null || sortOrder < 0) {
            throw new InvalidHighlightPositionException(sortOrder == null ? -1 : sortOrder);
        }
        return sortOrder;
    }
}
