package com.lifewise.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.outbox.OutboxWriter;
import com.lifewise.task.domain.Habit;
import com.lifewise.task.domain.HabitFrequency;
import com.lifewise.task.domain.HabitLog;
import com.lifewise.task.domain.HabitLogSource;
import com.lifewise.task.dto.HabitCreateRequest;
import com.lifewise.task.dto.HabitLogRequest;
import com.lifewise.task.dto.HabitLogView;
import com.lifewise.task.dto.HabitView;
import com.lifewise.task.repository.HabitLogRepository;
import com.lifewise.task.repository.HabitRepository;
import com.lifewise.task.service.exception.BackfillOutOfRangeException;
import com.lifewise.task.service.exception.BackfillRateLimitException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HabitServiceTest {

    @Mock HabitRepository habitRepository;
    @Mock HabitLogRepository habitLogRepository;
    @Mock StreakService streakService;
    @Mock OutboxWriter outboxWriter;
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);
    HabitService service;

    @BeforeEach
    void setUp() {
        service = new HabitService(habitRepository, habitLogRepository, streakService, outboxWriter, clock);
    }

    private static Habit withId(Habit h, long id) {
        h.setIdInternal(id);
        return h;
    }

    @Test
    void log_rejects_date_in_future() {
        Habit habit = withId(Habit.create(7L, "x", null, HabitFrequency.DAILY, 1), 10L);
        when(habitRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(habit));
        HabitLogRequest req = new HabitLogRequest(LocalDate.now(clock).plusDays(1),
                HabitLogSource.NORMAL, null);
        assertThatThrownBy(() -> service.log(7L, 10L, req))
                .isInstanceOf(BackfillOutOfRangeException.class);
    }

    @Test
    void log_rejects_backfill_outside_window() {
        Habit habit = withId(Habit.create(7L, "x", null, HabitFrequency.DAILY, 1), 10L);
        when(habitRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(habit));
        HabitLogRequest req = new HabitLogRequest(LocalDate.now(clock).minusDays(10),
                HabitLogSource.BACKFILL, null);
        assertThatThrownBy(() -> service.log(7L, 10L, req))
                .isInstanceOf(BackfillOutOfRangeException.class);
    }

    @Test
    void log_rejects_backfill_rate_limited() {
        Habit habit = withId(Habit.create(7L, "x", null, HabitFrequency.DAILY, 1), 10L);
        when(habitRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.countBackfillInWindow(any(), any(), any(), any())).thenReturn(5L);
        HabitLogRequest req = new HabitLogRequest(LocalDate.now(clock).minusDays(1),
                HabitLogSource.BACKFILL, null);
        assertThatThrownBy(() -> service.log(7L, 10L, req))
                .isInstanceOf(BackfillRateLimitException.class);
    }

    @Test
    void log_persists_and_returns_view() {
        Habit habit = withId(Habit.create(7L, "x", null, HabitFrequency.DAILY, 1), 10L);
        when(habitRepository.findByIdAndDeletedAtIsNull(10L)).thenReturn(Optional.of(habit));
        when(habitLogRepository.findByHabitIdAndLocalDate(any(), any())).thenReturn(Optional.empty());
        when(habitLogRepository.save(any(HabitLog.class))).thenAnswer(inv -> {
            HabitLog log = inv.getArgument(0);
            log.setIdInternal(1L);
            return log;
        });
        HabitLogRequest req = new HabitLogRequest(LocalDate.now(clock),
                HabitLogSource.NORMAL, "ok");
        HabitLogView view = service.log(7L, 10L, req);
        assertThat(view.habitId()).isEqualTo(10L);
        assertThat(view.source()).isEqualTo(HabitLogSource.NORMAL);
    }

    @Test
    void create_returns_view_with_zero_streak() {
        when(habitRepository.save(any(Habit.class))).thenAnswer(inv -> {
            Habit h = inv.getArgument(0);
            h.setIdInternal(3L);
            return h;
        });
        HabitView view = service.create(7L, new HabitCreateRequest("x", null, HabitFrequency.DAILY, 1));
        assertThat(view.id()).isEqualTo(3L);
        assertThat(view.currentStreak()).isZero();
        assertThat(view.longestStreak()).isZero();
    }

    @Test
    void list_delegates_to_streak_service() {
        when(habitRepository.findByUserIdAndArchivedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(7L))
                .thenReturn(java.util.List.of());
        assertThat(service.list(7L)).isEmpty();
    }
}