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
import com.lifewise.daily.dto.HighlightRequest;
import com.lifewise.daily.repository.DailyReportHighlightRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.HighlightLimitExceededException;
import com.lifewise.daily.service.exception.HighlightNotFoundException;
import com.lifewise.daily.service.exception.InvalidHighlightPositionException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HighlightServiceTest {

    @Mock DailyReportRepository reportRepository;
    @Mock DailyReportHighlightRepository highlightRepository;
    HighlightService service;

    @BeforeEach
    void setUp() {
        service = new HighlightService(reportRepository, highlightRepository);
    }

    private static DailyReport reportOwned(long id, long userId) {
        DailyReport r = DailyReport.create(userId, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", null, null);
        r.setIdInternal(id);
        return r;
    }

    @Test
    void create_appends_within_limit_and_assigns_sort_order() {
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(reportOwned(11L, 7L)));
        when(highlightRepository.countByDailyReportIdAndDeletedAtIsNull(11L)).thenReturn(1L);
        when(highlightRepository.save(any(DailyReportHighlight.class))).thenAnswer(inv -> {
            DailyReportHighlight h = inv.getArgument(0);
            h.setIdInternal(99L);
            return h;
        });
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, null);

        var view = service.create(7L, 11L, req);

        assertThat(view.id()).isEqualTo(99L);
        assertThat(view.sortOrder()).isEqualTo(1);
    }

    @Test
    void create_throws_when_count_reaches_max() {
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(reportOwned(11L, 7L)));
        when(highlightRepository.countByDailyReportIdAndDeletedAtIsNull(11L))
                .thenReturn((long) HighlightService.MAX_HIGHLIGHTS_PER_DAY);
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, null);

        assertThatThrownBy(() -> service.create(7L, 11L, req))
                .isInstanceOf(HighlightLimitExceededException.class);
        verify(highlightRepository, never()).save(any());
    }

    @Test
    void create_throws_when_sort_order_negative() {
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(reportOwned(11L, 7L)));
        when(highlightRepository.countByDailyReportIdAndDeletedAtIsNull(11L)).thenReturn(0L);
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, -1);

        assertThatThrownBy(() -> service.create(7L, 11L, req))
                .isInstanceOf(InvalidHighlightPositionException.class);
    }

    @Test
    void update_throws_when_highlight_belongs_to_other_report() {
        DailyReport other = DailyReport.create(7L, LocalDate.of(2026, 8, 3),
                "UTC", "t", "c", null, null);
        other.setIdInternal(11L);
        DailyReportHighlight h = DailyReportHighlight.create(999L, LocalDate.of(2026, 8, 3),
                HighlightType.INSIGHT, "k", "d", null, null, 0);
        h.setIdInternal(50L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(other));
        when(highlightRepository.findByIdAndDeletedAtIsNull(50L))
                .thenReturn(java.util.Optional.of(h));

        HighlightRequest req = new HighlightRequest(null, "x", null, null, null, null);

        assertThatThrownBy(() -> service.update(7L, 11L, 50L, req))
                .isInstanceOf(HighlightNotFoundException.class);
    }

    @Test
    void update_applies_fields_when_owned() {
        DailyReport r = reportOwned(11L, 7L);
        DailyReportHighlight h = DailyReportHighlight.create(11L,
                LocalDate.of(2026, 8, 2), HighlightType.INSIGHT, "k", "d", null, null, 0);
        h.setIdInternal(50L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(r));
        when(highlightRepository.findByIdAndDeletedAtIsNull(50L))
                .thenReturn(java.util.Optional.of(h));
        when(highlightRepository.save(any(DailyReportHighlight.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        HighlightRequest req = new HighlightRequest(HighlightType.HABIT, "new", null, null, null, 1);

        var view = service.update(7L, 11L, 50L, req);

        assertThat(view.title()).isEqualTo("new");
        assertThat(view.highlightType()).isEqualTo(HighlightType.HABIT);
        assertThat(view.sortOrder()).isEqualTo(1);
    }

    @Test
    void delete_soft_deletes_and_saves() {
        DailyReport r = reportOwned(11L, 7L);
        DailyReportHighlight h = DailyReportHighlight.create(11L,
                LocalDate.of(2026, 8, 2), HighlightType.INSIGHT, "k", "d", null, null, 0);
        h.setIdInternal(50L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(r));
        when(highlightRepository.findByIdAndDeletedAtIsNull(50L))
                .thenReturn(java.util.Optional.of(h));
        when(highlightRepository.save(any(DailyReportHighlight.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.delete(7L, 11L, 50L);

        assertThat(h.getDeletedAt()).isNotNull();
        verify(highlightRepository).save(h);
    }

    @Test
    void list_returns_owned_highlights() {
        DailyReport r = reportOwned(11L, 7L);
        DailyReportHighlight h = DailyReportHighlight.create(11L,
                LocalDate.of(2026, 8, 2), HighlightType.INSIGHT, "k", "d", null, null, 0);
        h.setIdInternal(99L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L))
                .thenReturn(java.util.Optional.of(r));
        when(highlightRepository.findByDailyReportIdAndDeletedAtIsNullOrderBySortOrderAsc(11L))
                .thenReturn(List.of(h));

        var views = service.listByReport(7L, 11L);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).id()).isEqualTo(99L);
    }
}