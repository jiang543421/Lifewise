package com.lifewise.daily.service;

import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.shared.integration.port.snapshot.DailySnapshot;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 心情统计与跨模块只读快照辅助（plan-02-daily §2.5）。
 *
 * <p>对外暴露 {@link DailySnapshot}，供其他模块（AI / Export）调用。
 */
@Service
public class MoodStatsService {

    private final DailyReportRepository repository;
    private final Clock clock;

    public MoodStatsService(DailyReportRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public double averageMoodInRange(long userId, LocalDate from, LocalDate to) {
        Double avg = repository.averageEnergyScoreInRange(userId, safeRange(from), safeRange(to));
        return avg == null ? 0.0 : avg;
    }

    @Transactional(readOnly = true)
    public long countReportsInRange(long userId, LocalDate from, LocalDate to) {
        return repository.countInRange(userId, safeRange(from), safeRange(to));
    }

    /** 当前自然周均值（按 Clock 时区对齐）。 */
    @Transactional(readOnly = true)
    public double currentWeekAverage(long userId) {
        LocalDate today = LocalDate.now(clock);
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return averageMoodInRange(userId, monday, today);
    }

    @Transactional(readOnly = true)
    public List<DailySnapshot> snapshotsInRange(long userId, LocalDate from, LocalDate to) {
        return repository.findInRange(userId, safeRange(from), safeRange(to)).stream()
                .map(r -> new DailySnapshot(
                        r.getId(),
                        r.getUserId(),
                        r.getLocalDate(),
                        r.getMood() == null ? null : r.getMood().name(),
                        snippetOf(r.getContent())))
                .toList();
    }

    private static String snippetOf(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "…";
    }

    private static LocalDate safeRange(LocalDate d) {
        return d == null ? LocalDate.of(1970, 1, 1) : d;
    }
}
