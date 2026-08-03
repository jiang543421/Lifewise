package com.lifewise.daily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.daily.domain.AiSummary;
import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.SummaryKind;
import com.lifewise.daily.repository.AiSummaryRepository;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.exception.AiSummaryNotFoundException;
import com.lifewise.daily.service.exception.DailyReportNotFoundException;
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

@ExtendWith(MockitoExtension.class)
class SummaryServiceTest {

    @Mock AiSummaryRepository summaryRepository;
    @Mock DailyReportRepository reportRepository;
    @Mock OutboxWriter outboxWriter;
    ObjectMapper objectMapper = new ObjectMapper();
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    SummaryService service;

    @BeforeEach
    void setUp() {
        service = new SummaryService(summaryRepository, reportRepository, outboxWriter,
                objectMapper, clock);
    }

    private static DailyReport reportOwned(long id, long userId) {
        DailyReport r = DailyReport.create(userId, LocalDate.of(2026, 8, 2),
                "UTC", "t", "c", null, null);
        r.setIdInternal(id);
        return r;
    }

    private static AiSummary summaryOwned(long id, long userId, long reportId, boolean userEdited) {
        AiSummary s = AiSummary.aiCreate(userId, reportId, LocalDate.of(2026, 8, 2),
                SummaryKind.DAILY, new ObjectMapper().createObjectNode(),
                "stub", "m", "v", "p", "ck:" + id, null, OffsetDateTime.now());
        s.setIdInternal(id);
        if (userEdited) {
            s.userEdit("user-edited");
        }
        return s;
    }

    @Test
    void trigger_creates_summary_and_emits_event_when_no_existing() {
        DailyReport r = reportOwned(11L, 7L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(summaryRepository.findByCacheKey(any())).thenReturn(Optional.empty());
        when(summaryRepository.save(any(AiSummary.class))).thenAnswer(inv -> {
            AiSummary s = inv.getArgument(0);
            s.setIdInternal(50L);
            return s;
        });

        var view = service.trigger(7L, 11L);

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.summaryKind()).isEqualTo(SummaryKind.DAILY);
        assertThat(view.userEdited()).isFalse();

        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("ai.summary.generated");
        assertThat(env.getValue().aggregateType()).isEqualTo("ai_summary");
        assertThat(env.getValue().aggregateId()).isEqualTo(50L);
    }

    @Test
    void trigger_returns_existing_when_already_present_and_not_user_edited() {
        DailyReport r = reportOwned(11L, 7L);
        AiSummary existing = summaryOwned(50L, 7L, 11L, false);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(summaryRepository.findByCacheKey(any())).thenReturn(Optional.of(existing));

        var view = service.trigger(7L, 11L);

        assertThat(view.id()).isEqualTo(50L);
        verify(summaryRepository, never()).save(any());
        verify(outboxWriter, never()).append(any());
    }

    @Test
    void trigger_respects_user_edited_flag_br_21c() {
        DailyReport r = reportOwned(11L, 7L);
        AiSummary edited = summaryOwned(50L, 7L, 11L, true);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(summaryRepository.findByCacheKey(any())).thenReturn(Optional.of(edited));

        var view = service.trigger(7L, 11L);

        assertThat(view.userEdited()).isTrue();
        assertThat(view.summaryText()).isEqualTo("user-edited");
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void get_throws_ai_summary_not_found_when_no_latest() {
        DailyReport r = reportOwned(11L, 7L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(summaryRepository
                .findFirstByDailyReportIdAndSummaryKindAndDeletedAtIsNullOrderByGeneratedAtDesc(
                        11L, SummaryKind.DAILY))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(7L, 11L))
                .isInstanceOf(AiSummaryNotFoundException.class);
    }

    @Test
    void get_returns_latest_when_present() {
        DailyReport r = reportOwned(11L, 7L);
        AiSummary s = summaryOwned(50L, 7L, 11L, false);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));
        when(summaryRepository
                .findFirstByDailyReportIdAndSummaryKindAndDeletedAtIsNullOrderByGeneratedAtDesc(
                        11L, SummaryKind.DAILY))
                .thenReturn(Optional.of(s));

        var view = service.get(7L, 11L);

        assertThat(view.id()).isEqualTo(50L);
        assertThat(view.summaryText()).isEqualTo("stub");
    }

    @Test
    void get_throws_when_cross_user_access() {
        DailyReport r = reportOwned(11L, 99L);
        when(reportRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.get(7L, 11L))
                .isInstanceOf(DailyReportNotFoundException.class);
    }

    @Test
    void soft_delete_all_skips_summaries_belonging_to_other_users() {
        AiSummary mine = summaryOwned(50L, 7L, 11L, false);
        AiSummary other = summaryOwned(51L, 99L, 11L, false);
        when(summaryRepository.findByDailyReportIdAndDeletedAtIsNullOrderByGeneratedAtDesc(11L))
                .thenReturn(List.of(mine, other));

        service.softDeleteAllByReport(7L, 11L);

        assertThat(mine.getDeletedAt()).isNotNull();
        assertThat(other.getDeletedAt()).isNull();
    }
}