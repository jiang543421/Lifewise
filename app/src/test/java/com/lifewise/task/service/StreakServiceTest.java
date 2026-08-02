package com.lifewise.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lifewise.task.domain.HabitLog;
import com.lifewise.task.domain.HabitLogSource;
import com.lifewise.task.repository.HabitLogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreakServiceTest {

    @Mock HabitLogRepository habitLogRepository;
    StreakService service;
    ZoneId zone = ZoneId.of("UTC");

    @BeforeEach
    void setUp() {
        service = new StreakService(habitLogRepository);
    }

    private static HabitLog log(LocalDate date) {
        return HabitLog.of(1L, 7L, date, OffsetDateTime.now(), HabitLogSource.NORMAL, null);
    }

    @Test
    void current_streak_three_consecutive_days() {
        LocalDate today = LocalDate.parse("2026-08-02");
        when(habitLogRepository.findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
                any(), any(), any(), any()))
                .thenReturn(List.of(log(today), log(today.minusDays(1)), log(today.minusDays(2))));
        assertThat(service.currentStreak(1L, 7L, zone, today)).isEqualTo(3);
    }

    @Test
    void current_streak_zero_when_no_logs() {
        LocalDate today = LocalDate.parse("2026-08-02");
        when(habitLogRepository.findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
                any(), any(), any(), any())).thenReturn(Collections.emptyList());
        assertThat(service.currentStreak(1L, 7L, zone, today)).isZero();
    }

    @Test
    void current_streak_breaks_when_gap() {
        LocalDate today = LocalDate.parse("2026-08-02");
        // today + day-1 + day-3 → 连续计数为 2 (gap at day-2)
        when(habitLogRepository.findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
                any(), any(), any(), any()))
                .thenReturn(List.of(log(today), log(today.minusDays(1)), log(today.minusDays(3))));
        assertThat(service.currentStreak(1L, 7L, zone, today)).isEqualTo(2);
    }

    @Test
    void longest_streak_two_segments_picks_larger() {
        LocalDate today = LocalDate.parse("2026-08-02");
        // segment A: today, day-1, day-2 (3); segment B: day-10, day-11 (2)
        when(habitLogRepository.findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
                any(), any(), any(), any()))
                .thenReturn(List.of(
                        log(today), log(today.minusDays(1)), log(today.minusDays(2)),
                        log(today.minusDays(10)), log(today.minusDays(11))));
        assertThat(service.longestStreak(1L, 7L, zone, today)).isEqualTo(3);
    }

    @Test
    void longest_streak_zero_when_no_logs() {
        LocalDate today = LocalDate.parse("2026-08-02");
        when(habitLogRepository.findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(
                any(), any(), any(), any())).thenReturn(Collections.emptyList());
        assertThat(service.longestStreak(1L, 7L, zone, today)).isZero();
    }
}