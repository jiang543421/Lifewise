package com.lifewise.daily.port.out;

import com.lifewise.daily.repository.DailyReportRepository;
import com.lifewise.daily.service.MoodStatsService;
import com.lifewise.shared.integration.port.DailyReadPort;
import com.lifewise.shared.integration.port.snapshot.DailySnapshot;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** daily 对外只读适配器（plan-02-daily §2.5 + plan-shared-integration §2.2）。 */
@Component
public class DailyReadPortAdapter implements DailyReadPort {

    private final DailyReportRepository repository;
    private final MoodStatsService moodStatsService;

    public DailyReadPortAdapter(DailyReportRepository repository,
                                MoodStatsService moodStatsService) {
        this.repository = repository;
        this.moodStatsService = moodStatsService;
    }

    @Override
    public Optional<DailySnapshot> findByDate(Long userId, LocalDate date) {
        return repository.findByUserIdAndLocalDateAndDeletedAtIsNull(userId, date)
                .map(r -> new DailySnapshot(
                        r.getId(), r.getUserId(), r.getLocalDate(),
                        r.getMood() == null ? null : r.getMood().name(),
                        snippet(r.getContent())));
    }

    @Override
    public List<DailySnapshot> findInRange(Long userId, LocalDate from, LocalDate to) {
        return moodStatsService.snapshotsInRange(userId, from, to);
    }

    @Override
    public Double averageMoodInRange(Long userId, LocalDate from, LocalDate to) {
        return moodStatsService.averageMoodInRange(userId, from, to);
    }

    @Override
    public long countReportsInRange(Long userId, LocalDate from, LocalDate to) {
        return moodStatsService.countReportsInRange(userId, from, to);
    }

    private static String snippet(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= 120 ? content : content.substring(0, 120) + "…";
    }
}
