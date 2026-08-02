package com.lifewise.task.service;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import com.lifewise.task.domain.Habit;
import com.lifewise.task.domain.HabitLog;
import com.lifewise.task.domain.HabitLogSource;
import com.lifewise.task.dto.HabitCreateRequest;
import com.lifewise.task.dto.HabitLogRequest;
import com.lifewise.task.dto.HabitLogView;
import com.lifewise.task.dto.HabitView;
import com.lifewise.task.event.payload.HabitLoggedPayload;
import com.lifewise.task.repository.HabitLogRepository;
import com.lifewise.task.repository.HabitRepository;
import com.lifewise.task.service.exception.BackfillOutOfRangeException;
import com.lifewise.task.service.exception.BackfillRateLimitException;
import com.lifewise.task.service.exception.HabitNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Habit 增删改 + 打卡（plan-01-task §2.2 + §4）。 */
@Service
public class HabitService {

    private static final int BACKFILL_WINDOW_DAYS = 3;
    private static final int BACKFILL_DAILY_LIMIT = 5;

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final StreakService streakService;
    private final OutboxWriter outboxWriter;
    private final Clock clock;
    private final ZoneId zoneId;

    public HabitService(HabitRepository habitRepository,
                        HabitLogRepository habitLogRepository,
                        StreakService streakService,
                        OutboxWriter outboxWriter,
                        Clock authClock) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.streakService = streakService;
        this.outboxWriter = outboxWriter;
        this.clock = authClock;
        this.zoneId = ZoneId.of("UTC");
    }

    @Transactional(readOnly = true)
    public List<HabitView> list(long userId) {
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return habitRepository.findByUserIdAndArchivedFalseAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(h -> HabitView.of(
                        h,
                        streakService.currentStreak(h.getId(), userId, zoneId, today),
                        streakService.longestStreak(h.getId(), userId, zoneId, today)))
                .toList();
    }

    @Transactional
    public HabitView create(long userId, HabitCreateRequest req) {
        Habit habit = Habit.create(userId, req.title(), req.description(),
                req.frequency(), req.targetPerPeriod());
        habit = habitRepository.save(habit);
        return HabitView.of(habit, 0, 0);
    }

    @Transactional
    public HabitView update(long userId, long habitId, HabitCreateRequest req) {
        Habit habit = loadOwned(userId, habitId);
        habit.update(req.title(), req.description(), req.frequency(), req.targetPerPeriod());
        habit = habitRepository.save(habit);
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        return HabitView.of(
                habit,
                streakService.currentStreak(habitId, userId, zoneId, today),
                streakService.longestStreak(habitId, userId, zoneId, today));
    }

    @Transactional
    public void softDelete(long userId, long habitId) {
        Habit habit = loadOwned(userId, habitId);
        habit.softDelete();
        habitRepository.save(habit);
    }

    @Transactional
    public HabitLogView log(long userId, long habitId, HabitLogRequest req) {
        Habit habit = loadOwned(userId, habitId);
        LocalDate today = LocalDate.now(clock.withZone(zoneId));
        LocalDate date = req.localDate();
        if (date.isAfter(today) || date.isBefore(today.minusDays(BACKFILL_WINDOW_DAYS))) {
            throw new BackfillOutOfRangeException(date.toString());
        }
        HabitLogSource source = req.sourceOrDefault();
        if (source == HabitLogSource.BACKFILL) {
            long used = habitLogRepository.countBackfillInWindow(
                    habitId, userId, today.minusDays(BACKFILL_WINDOW_DAYS), today);
            if (used >= BACKFILL_DAILY_LIMIT) {
                throw new BackfillRateLimitException(habitId);
            }
        }
        if (habitLogRepository.findByHabitIdAndLocalDate(habitId, date).isPresent()) {
            throw new BackfillOutOfRangeException(date + " already logged");
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        HabitLog log = HabitLog.of(habitId, userId, date, now, source, req.note());
        log = habitLogRepository.save(log);
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.HABIT_LOGGED.eventType(),
                1,
                now,
                userId,
                "habit",
                habit.getId(),
                null,
                null,
                null,
                new HabitLoggedPayload(habit.getId(), userId, date, 1, source.name()).toMap()));
        return HabitLogView.from(log);
    }

    private Habit loadOwned(long userId, long habitId) {
        return habitRepository.findByIdAndDeletedAtIsNull(habitId)
                .filter(h -> userId == h.getUserId())
                .orElseThrow(() -> new HabitNotFoundException(habitId));
    }
}
