package com.lifewise.daily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.DailyReportHighlight;
import com.lifewise.daily.domain.HighlightType;
import com.lifewise.daily.domain.Mood;
import com.lifewise.daily.dto.DailyReportCreateRequest;
import com.lifewise.daily.dto.DailyReportUpdateRequest;
import com.lifewise.daily.dto.DailyReportView;
import com.lifewise.daily.repository.DailyReportHighlightRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.ContentTooLongException;
import com.lifewise.daily.service.exception.DailyReportNotFoundException;
import com.lifewise.daily.service.exception.DuplicateDailyReportException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DailyReportServiceTest {

    @Mock DailyReportRepository repository;
    @Mock DailyReportHighlightRepository highlightRepository;
    @Mock SummaryService summaryService;
    @Mock OutboxWriter outboxWriter;
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    DailyReportService service;

    @BeforeEach
    void setUp() {
        service = new DailyReportService(repository, highlightRepository, summaryService,
                outboxWriter, clock);
    }

    private static DailyReport withId(DailyReport r, long id) {
        r.setIdInternal(id);
        return r;
    }

    @Test
    void create_persists_report_and_emits_created_event() {
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 2)))
                .thenReturn(Optional.empty());
        when(repository.save(any(DailyReport.class))).thenAnswer(inv -> {
            DailyReport r = inv.getArgument(0);
            r.setIdInternal(101L);
            return r;
        });
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(101L))
                .thenReturn(List.of());
        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), "Asia/Shanghai", "good day",
                "today was good", Mood.GOOD, 4);

        DailyReportView view = service.create(7L, req);

        assertThat(view.id()).isEqualTo(101L);
        assertThat(view.title()).isEqualTo("good day");
        assertThat(view.mood()).isEqualTo(Mood.GOOD);
        assertThat(view.isDraft()).isTrue();
        assertThat(view.highlights()).isEmpty();

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("daily_report.created");
        assertThat(env.getValue().userId()).isEqualTo(7L);
        assertThat(env.getValue().aggregateType()).isEqualTo("daily_report");
        assertThat(env.getValue().aggregateId()).isEqualTo(101L);
    }

    @Test
    void create_throws_duplicate_when_existing_for_same_date() {
        DailyReport existing = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 2),
                "UTC", "old", "c", Mood.NEUTRAL, 3), 5L);
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 2)))
                .thenReturn(Optional.of(existing));

        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), null, "x", null, null, null);

        assertThatThrownBy(() -> service.create(7L, req))
                .isInstanceOf(DuplicateDailyReportException.class);
        verify(repository, never()).save(any());
        verify(outboxWriter, never()).append(any());
    }

    @Test
    void create_throws_content_too_long_when_exceeding_limit() {
        String huge = "x".repeat(50001);
        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), null, "x", huge, null, null);

        assertThatThrownBy(() -> service.create(7L, req))
                .isInstanceOf(ContentTooLongException.class);
    }

    @Test
    void get_owned_returns_view_with_highlights_and_summary() {
        DailyReport r = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", Mood.GREAT, 5), 11L);
        DailyReportHighlight h = DailyReportHighlight.create(11L,
                LocalDate.of(2026, 8, 2), HighlightType.INSIGHT, "k", "d", null, null, 0);
        h.setIdInternal(99L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(11L))
                .thenReturn(List.of(h));
        when(summaryService.findLatestByReport(r)).thenReturn(null);

        DailyReportView view = service.getOwned(7L, 11L);

        assertThat(view.highlights()).hasSize(1);
        assertThat(view.highlights().get(0).id()).isEqualTo(99L);
        assertThat(view.summary()).isNull();
    }

    @Test
    void get_owned_throws_when_cross_user_access() {
        DailyReport r = withId(DailyReport.create(99L, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", null, null), 11L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.getOwned(7L, 11L))
                .isInstanceOf(DailyReportNotFoundException.class);
    }

    @Test
    void get_by_date_throws_when_missing() {
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 3)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByDate(7L, LocalDate.of(2026, 8, 3)))
                .isInstanceOf(DailyReportNotFoundException.class);
    }

    @Test
    void list_filters_by_user_and_passes_pageable() {
        DailyReport r = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 1),
                "UTC", "t", "c", null, null), 1L);
        Page<DailyReport> page = new PageImpl<>(List.of(r));
        when(repository.searchByMonth(7L, 2026, 8, false, PageRequest.of(0, 20)))
                .thenReturn(page);

        var items = service.list(7L, 2026, 8, false, PageRequest.of(0, 20));

        assertThat(items.getTotalElements()).isEqualTo(1);
        assertThat(items.getContent().get(0).title()).isEqualTo("t");
    }

    @Test
    void update_emits_updated_event() {
        DailyReport r = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 2),
                "UTC", "old", "c", Mood.NEUTRAL, 3), 11L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(repository.save(any(DailyReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(11L))
                .thenReturn(List.of());
        DailyReportUpdateRequest req = new DailyReportUpdateRequest("new", null, null, null, null);

        DailyReportView view = service.update(7L, 11L, req);

        assertThat(view.title()).isEqualTo("new");
        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("daily_report.updated");
        assertThat(env.getValue().payload()).containsEntry("changeType", "edit");
    }

    @Test
    void update_publish_false_keeps_draft() {
        DailyReport r = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", Mood.NEUTRAL, 3), 11L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(repository.save(any(DailyReport.class))).thenAnswer(inv -> inv.getArgument(0));
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(11L))
                .thenReturn(List.of());
        DailyReportUpdateRequest req = new DailyReportUpdateRequest(null, "edited", null, null, false);

        DailyReportView view = service.update(7L, 11L, req);

        assertThat(view.isDraft()).isTrue();
    }

    @Test
    void soft_delete_marks_deleted_and_cascades_summary() {
        DailyReport r = withId(DailyReport.create(7L, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", Mood.NEUTRAL, 3), 11L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(repository.save(any(DailyReport.class))).thenAnswer(inv -> inv.getArgument(0));

        service.softDelete(7L, 11L);

        assertThat(r.getDeletedAt()).isNotNull();
        verify(summaryService).softDeleteAllByReport(7L, 11L);
        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("daily_report.updated");
        assertThat(env.getValue().payload()).containsEntry("changeType", "softDelete");
    }

    @Test
    void soft_delete_throws_when_not_owned() {
        DailyReport r = withId(DailyReport.create(99L, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", null, null), 11L);
        when(repository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.softDelete(7L, 11L))
                .isInstanceOf(DailyReportNotFoundException.class);
    }

    @Test
    void create_offsets_event_occurred_at_from_clock() {
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 2)))
                .thenReturn(Optional.empty());
        when(repository.save(any(DailyReport.class))).thenAnswer(inv -> {
            DailyReport r = inv.getArgument(0);
            r.setIdInternal(7L);
            return r;
        });
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(7L))
                .thenReturn(List.of());

        service.create(7L, new DailyReportCreateRequest(LocalDate.of(2026, 8, 2),
                null, "t", null, null, null));

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().occurredAt())
                .isEqualTo(OffsetDateTime.now(clock));
    }
}