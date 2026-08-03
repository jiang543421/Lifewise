package com.lifewise.daily.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.Mood;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.shared.integration.port.snapshot.DailySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MoodStatsServiceTest {

    @Mock DailyReportRepository repository;
    Clock clock = Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);
    MoodStatsService service;

    @BeforeEach
    void setUp() {
        service = new MoodStatsService(repository, clock);
    }

    @Test
    void average_mood_returns_zero_when_repository_null() {
        when(repository.averageEnergyScoreInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).thenReturn(null);

        assertThat(service.averageMoodInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).isEqualTo(0.0);
    }

    @Test
    void average_mood_returns_value_when_present() {
        when(repository.averageEnergyScoreInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).thenReturn(4.2);

        assertThat(service.averageMoodInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).isEqualTo(4.2);
    }

    @Test
    void count_reports_passes_through() {
        when(repository.countInRange(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7)))
                .thenReturn(3L);

        assertThat(service.countReportsInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).isEqualTo(3L);
    }

    @Test
    void snapshots_returns_snapshots_for_range() {
        DailyReport r1 = DailyReport.create(7L, LocalDate.of(2026, 8, 1), "UTC", "t1",
                "short", Mood.GOOD, 4);
        r1.setIdInternal(11L);
        DailyReport r2 = DailyReport.create(7L, LocalDate.of(2026, 8, 2), "UTC", "t2",
                "x".repeat(200), Mood.GREAT, 5);
        r2.setIdInternal(12L);
        when(repository.findInRange(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2)))
                .thenReturn(List.of(r1, r2));

        List<DailySnapshot> snaps = service.snapshotsInRange(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 2));

        assertThat(snaps).hasSize(2);
        assertThat(snaps.get(0).id()).isEqualTo(11L);
        assertThat(snaps.get(0).mood()).isEqualTo("GOOD");
        assertThat(snaps.get(0).summary()).isEqualTo("short");
        // 第二条 content 长度 200 > 120，应被截断为 120 字符 + "…"
        assertThat(snaps.get(1).summary()).endsWith("…");
    }

    @Test
    void snapshots_handles_null_mood_and_null_content() {
        DailyReport r = DailyReport.create(7L, LocalDate.of(2026, 8, 1), "UTC", "t", null,
                null, null);
        r.setIdInternal(11L);
        when(repository.findInRange(7L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)))
                .thenReturn(List.of(r));

        List<DailySnapshot> snaps = service.snapshotsInRange(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1));

        assertThat(snaps.get(0).mood()).isNull();
        assertThat(snaps.get(0).summary()).isNull();
    }

    @Test
    void current_week_average_aligns_to_monday_of_clock_date() {
        // 2026-08-04 是 Tuesday；Monday = 2026-08-03 (per ISO DayOfWeek)
        when(repository.averageEnergyScoreInRange(7L, LocalDate.of(2026, 8, 3),
                LocalDate.of(2026, 8, 4))).thenReturn(3.5);

        assertThat(service.currentWeekAverage(7L)).isEqualTo(3.5);
    }
}