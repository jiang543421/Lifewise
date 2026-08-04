package com.lifewise.daily.service;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.dto.DailyReportCreateRequest;
import com.lifewise.daily.dto.DailyReportListItem;
import com.lifewise.daily.dto.DailyReportUpdateRequest;
import com.lifewise.daily.dto.DailyReportView;
import com.lifewise.daily.dto.HighlightView;
import com.lifewise.daily.event.payload.DailyReportCreatedPayload;
import com.lifewise.daily.event.payload.DailyReportUpdatedPayload;
import com.lifewise.daily.repository.DailyReportHighlightRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.ContentTooLongException;
import com.lifewise.daily.service.exception.DailyReportNotFoundException;
import com.lifewise.daily.service.exception.DuplicateDailyReportException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 日报写操作 + 查询（plan-02-daily §2.1）。 */
@Service
public class DailyReportService {

    private final DailyReportRepository repository;
    private final DailyReportHighlightRepository highlightRepository;
    private final SummaryService summaryService;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public DailyReportService(DailyReportRepository repository,
                              DailyReportHighlightRepository highlightRepository,
                              SummaryService summaryService,
                              OutboxWriter outboxWriter,
                              Clock clock) {
        this.repository = repository;
        this.highlightRepository = highlightRepository;
        this.summaryService = summaryService;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional
    public DailyReportView create(long userId, DailyReportCreateRequest req) {
        if (req.content() != null && req.content().length() > 50000) {
            throw new ContentTooLongException(req.content().length());
        }
        repository.findByUserIdAndLocalDateAndDeletedAtIsNull(userId, req.reportDate())
                .ifPresent(existing -> {
                    throw new DuplicateDailyReportException(userId, req.reportDate());
                });
        DailyReport report = DailyReport.create(
                userId,
                req.reportDate(),
                req.timezone(),
                req.title(),
                req.content(),
                req.mood(),
                req.energyScore());
        report = repository.save(report);
        appendCreatedEvent(userId, report);
        return buildView(report);
    }

    @Transactional(readOnly = true)
    public Page<DailyReportListItem> list(long userId, Integer year, Integer month,
                                          boolean includeDraft, Pageable pageable) {
        return repository.searchByMonth(userId, year, month, includeDraft, pageable)
                .map(DailyReportListItem::from);
    }

    @Transactional(readOnly = true)
    public DailyReportView getOwned(long userId, long reportId) {
        return buildView(loadOwned(userId, reportId));
    }

    @Transactional(readOnly = true)
    public DailyReportView getByDate(long userId, LocalDate localDate) {
        DailyReport report = repository.findByUserIdAndLocalDateAndDeletedAtIsNull(userId, localDate)
                .orElseThrow(() -> new DailyReportNotFoundException(userId, localDate));
        return buildView(report);
    }

    @Transactional
    public DailyReportView update(long userId, long reportId, DailyReportUpdateRequest req) {
        DailyReport report = loadOwned(userId, reportId);
        // BR-21.d: 发布路径在 publish=true 时切到 DAILY_REPORT_PUBLISHED 事件，
        // 普通编辑仍走 changeType=edit。事件类型由 applyUpdate 写入的 draft 标志决定。
        report.applyUpdate(req.title(), req.content(), req.mood(), req.energyScore(), req.publish());
        report = repository.save(report);
        String changeType = (req.publish() != null && req.publish()) ? "publish" : "edit";
        appendUpdatedEvent(userId, report, changeType);
        return buildView(report);
    }

    @Transactional
    public void softDelete(long userId, long reportId) {
        DailyReport report = loadOwned(userId, reportId);
        report.softDelete();
        repository.save(report);
        // BR-21 联动：summary 级联软删由 SummaryService 处理
        summaryService.softDeleteAllByReport(userId, reportId);
        appendUpdatedEvent(userId, report, "softDelete");
    }

    private DailyReport loadOwned(long userId, long reportId) {
        return repository.findByIdAndDeletedAtIsNull(reportId)
                .filter(r -> userId == r.getUserId())
                .orElseThrow(() -> new DailyReportNotFoundException(reportId));
    }

    private DailyReportView buildView(DailyReport report) {
        List<HighlightView> highlights = highlightRepository
                .findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(report.getId())
                .stream().map(HighlightView::from).toList();
        return DailyReportView.from(report, highlights, summaryService.findLatestByReport(report));
    }

    private void appendCreatedEvent(long userId, DailyReport report) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.DAILY_REPORT_CREATED.eventType(),
                1, now, userId, "daily_report", report.getId(),
                null, null, null,
                new DailyReportCreatedPayload(report.getId(), userId,
                        report.getLocalDate(), report.getMood(), now).toMap()));
    }

    private void appendUpdatedEvent(long userId, DailyReport report, String changeType) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.DAILY_REPORT_UPDATED.eventType(),
                1, now, userId, "daily_report", report.getId(),
                null, null, null,
                new DailyReportUpdatedPayload(report.getId(), userId, changeType, now).toMap()));
    }

    /** 内部钩子：默认 (eventVersion=1, 空 map)。保留供未来扩展。 */
    @SuppressWarnings("unused")
    private Map<String, Object> emptyPayload() {
        return Map.of();
    }
}
