package com.lifewise.plan.service;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.event.payload.MilestoneMissedPayload;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 过期里程碑扫描 Job（plan-05-plan §5.6）。
 *
 * <p>每日 03:30 运行（与 expense 月视图刷新错峰 30 分钟）。
 * 把 due_at &lt; now 且未完成 / 未取消的 milestone 标记为 MISSED，发出 milestone.missed 事件。
 *
 * <p>cron 由 {@code plan.jobs.missed-cron} 控制（默认 0 30 3 * * *）；测试可通过
 * {@code -} 关闭（如 {@code plan.jobs.missed-cron=-}）。
 */
@Component
public class MissedMilestoneJob {

    private final MilestoneRepository milestoneRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public MissedMilestoneJob(MilestoneRepository milestoneRepository,
                              OutboxWriter outboxWriter,
                              Clock clock) {
        this.milestoneRepository = milestoneRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    /** @return 本轮标记 MISSED 的里程碑数量 */
    @Scheduled(cron = "${plan.jobs.missed-cron:0 30 3 * * *}", zone = "UTC")
    @Transactional
    public int sweep() {
        OffsetDateTime cutoff = OffsetDateTime.now(clock);
        List<Milestone> overdue = milestoneRepository.findOverduePending(cutoff);
        int swept = 0;
        for (Milestone m : overdue) {
            m.markMissed();
            milestoneRepository.save(m);
            appendMissedEvent(m);
            swept++;
        }
        return swept;
    }

    private void appendMissedEvent(Milestone m) {
        Map<String, Object> payload = new MilestoneMissedPayload(
                m.getId(), m.getPlanId(), m.getUserId(),
                m.getDueAt(), m.getTimeZone()).toMap();
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.MILESTONE_MISSED.eventType(),
                1,
                OffsetDateTime.now(clock),
                m.getUserId(),
                "milestone",
                m.getId(),
                null,
                null,
                null,
                payload));
    }
}