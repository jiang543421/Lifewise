package com.lifewise.task.service;

import com.lifewise.task.domain.HabitLog;
import com.lifewise.task.repository.HabitLogRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Streak 计算服务（plan-01-task §5.2）。纯函数式可测，与 user.timezone 联动。 */
@Service
public class StreakService {

    private static final int LOOKBACK_DAYS = 365;

    private final HabitLogRepository habitLogRepository;

    public StreakService(HabitLogRepository habitLogRepository) {
        this.habitLogRepository = habitLogRepository;
    }

    @Transactional(readOnly = true)
    public int currentStreak(long habitId, long userId, ZoneId zone, LocalDate today) {
        Set<LocalDate> dates = loadLogDates(habitId, userId, today);
        if (dates.isEmpty()) {
            return 0;
        }
        int streak = 0;
        LocalDate cursor = today;
        while (dates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }
        return streak;
    }

    @Transactional(readOnly = true)
    public int longestStreak(long habitId, long userId, ZoneId zone, LocalDate today) {
        Set<LocalDate> dates = loadLogDates(habitId, userId, today);
        if (dates.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = dates.stream().sorted().toList();
        int longest = 1;
        int current = 1;
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).minusDays(1).isEqual(sorted.get(i - 1))) {
                current++;
                if (current > longest) longest = current;
            } else {
                current = 1;
            }
        }
        return longest;
    }

    private Set<LocalDate> loadLogDates(long habitId, long userId, LocalDate today) {
        LocalDate from = today.minusDays(LOOKBACK_DAYS);
        List<HabitLog> logs = habitLogRepository
                .findByHabitIdAndUserIdAndLocalDateBetweenOrderByLocalDateDesc(habitId, userId, from, today);
        Set<LocalDate> dates = new HashSet<>(logs.size());
        for (HabitLog log : logs) {
            dates.add(log.getLocalDate());
        }
        return dates;
    }
}
