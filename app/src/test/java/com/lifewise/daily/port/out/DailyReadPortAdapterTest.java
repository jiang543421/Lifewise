package com.lifewise.daily.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.lifewise.daily.domain.DailyReport;
import com.lifewise.daily.domain.Mood;
import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.MoodStatsService;
import com.lifewise.shared.integration.port.snapshot.DailySnapshot;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** DailyReadPortAdapter 4 端点（plan-shared-integration §2.2）：findByDate / findInRange / averageMood / count。 */
@ExtendWith(MockitoExtension.class)
class DailyReadPortAdapterTest {

    @Mock DailyReportRepository repository;
    @Mock MoodStatsService moodStatsService;
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    DailyReadPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new DailyReadPortAdapter(repository, moodStatsService);
    }

    private static DailyReport reportOwned(long id, long userId) {
        DailyReport r = DailyReport.create(userId, LocalDate.of(2026, 8, 2), "UTC", "t",
                "short", Mood.GOOD, 4);
        r.setIdInternal(id);
        return r;
    }

    @Test
    void findByDate_returns_snapshot_when_present() {
        DailyReport r = reportOwned(11L, 7L);
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 2)))
                .thenReturn(Optional.of(r));

        Optional<DailySnapshot> snap = adapter.findByDate(7L, LocalDate.of(2026, 8, 2));

        assertThat(snap).isPresent();
        assertThat(snap.get().id()).isEqualTo(11L);
        assertThat(snap.get().mood()).isEqualTo("GOOD");
        assertThat(snap.get().summary()).isEqualTo("short");
    }

    @Test
    void findByDate_returns_empty_when_absent() {
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 3)))
                .thenReturn(Optional.empty());

        assertThat(adapter.findByDate(7L, LocalDate.of(2026, 8, 3))).isEmpty();
    }

    @Test
    void findInRange_delegates_to_mood_stats_service() {
        DailySnapshot s = new DailySnapshot(11L, 7L, LocalDate.of(2026, 8, 1), "GOOD", "x");
        when(moodStatsService.snapshotsInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).thenReturn(List.of(s));

        List<DailySnapshot> snaps = adapter.findInRange(7L,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 7));

        assertThat(snaps).hasSize(1);
        assertThat(snaps.get(0).mood()).isEqualTo("GOOD");
    }

    @Test
    void averageMoodInRange_delegates_to_mood_stats_service() {
        when(moodStatsService.averageMoodInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).thenReturn(3.7);

        assertThat(adapter.averageMoodInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).isEqualTo(3.7);
    }

    @Test
    void countReportsInRange_delegates_to_mood_stats_service() {
        when(moodStatsService.countReportsInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).thenReturn(5L);

        assertThat(adapter.countReportsInRange(7L, LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 7))).isEqualTo(5L);
    }

    @Test
    void findByDate_snippet_truncates_long_content() {
        DailyReport r = reportOwned(11L, 7L);
        r.applyUpdate(null, "x".repeat(200), null, null, null);
        when(repository.findByUserIdAndLocalDateAndDeletedAtIsNull(7L, LocalDate.of(2026, 8, 2)))
                .thenReturn(Optional.of(r));

        Optional<DailySnapshot> snap = adapter.findByDate(7L, LocalDate.of(2026, 8, 2));

        assertThat(snap.get().summary()).hasSize(121).endsWith("…");
    }
}