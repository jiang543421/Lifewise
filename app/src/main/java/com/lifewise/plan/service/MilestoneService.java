package com.lifewise.plan.service;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.dto.MilestoneRequest;
import com.lifewise.plan.dto.MilestoneView;
import com.lifewise.plan.event.payload.MilestoneCompletedPayload;
import com.lifewise.plan.event.payload.MilestoneCreatedPayload;
import com.lifewise.plan.event.payload.MilestoneUpdatedPayload;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.exception.MilestoneDoneReadOnlyException;
import com.lifewise.plan.service.exception.MilestoneNotFoundException;
import com.lifewise.plan.service.exception.PlanAlreadyAbandonedException;
import com.lifewise.plan.service.exception.PlanNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestone 写服务（plan-05-plan §3.2 - 7 端点 + BR-14 / BR-29）。
 *
 * <p>完整 7 端点：
 * <ol>
 *   <li>POST   /api/plans/{planId}/milestones             创建</li>
 *   <li>GET    /api/plans/{planId}/milestones             列表</li>
 *   <li>PUT    /api/plans/{planId}/milestones/{id}        更新（DONE-only-readonly）</li>
 *   <li>DELETE /api/plans/{planId}/milestones/{id}        软删</li>
 *   <li>POST   /api/plans/{planId}/milestones/{id}/complete 完成</li>
 *   <li>POST   /api/plans/{planId}/milestones/{id}/reopen   重开</li>
 *   <li>POST   /api/plans/{planId}/milestones/{id}/tasks    关联 task</li>
 * </ol>
 */
@Service
public class MilestoneService {

    private final PlanRepository planRepository;
    private final MilestoneRepository milestoneRepository;
    private final OutboxWriter outboxWriter;
    private final MilestoneTaskLinkService linkService;
    private final Clock clock;

    public MilestoneService(PlanRepository planRepository,
                            MilestoneRepository milestoneRepository,
                            OutboxWriter outboxWriter,
                            MilestoneTaskLinkService linkService,
                            Clock clock) {
        this.planRepository = planRepository;
        this.milestoneRepository = milestoneRepository;
        this.outboxWriter = outboxWriter;
        this.linkService = linkService;
        this.clock = clock;
    }

    @Transactional
    public MilestoneView create(Long userId, Long planId, MilestoneRequest req) {
        Plan plan = loadOwnedPlan(userId, planId);
        if (plan.isCancelled()) {
            throw new PlanAlreadyAbandonedException(planId);
        }
        Milestone m = Milestone.create(planId, userId, req.title(), req.description(),
                req.dueAt(), req.timeZone(), req.sortOrder());
        Milestone saved = milestoneRepository.save(m);

        appendMilestoneEvent(saved, EventType.MILESTONE_CREATED,
                new MilestoneCreatedPayload(
                        saved.getId(), saved.getPlanId(), saved.getUserId(),
                        saved.getTitle(), saved.getDueAt(), saved.getTimeZone(),
                        saved.getSortOrder()).toMap());
        return MilestoneView.from(saved);
    }

    @Transactional(readOnly = true)
    public List<MilestoneView> list(Long userId, Long planId) {
        loadOwnedPlan(userId, planId);
        return milestoneRepository
                .findAllByPlanIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(planId)
                .stream()
                .map(MilestoneView::from)
                .toList();
    }

    /**
     * BR-14：DONE 状态不可修改 title/due_at/sort_order。已 DONE 抛 MilestoneDoneReadOnlyException。
     */
    @Transactional
    public MilestoneView update(Long userId, Long planId, Long milestoneId, MilestoneRequest req) {
        Milestone m = loadOwnedMilestone(userId, planId, milestoneId);
        // 状态机检查（在 entity 层抛），entity 会自行抛出
        m.applyUpdate(req.title(), req.description(), req.dueAt(), req.timeZone(), req.sortOrder());
        Milestone saved = milestoneRepository.save(m);

        appendMilestoneEvent(saved, EventType.MILESTONE_UPDATED,
                new MilestoneUpdatedPayload(
                        saved.getId(), saved.getPlanId(), saved.getUserId(),
                        null, saved.getStatus().name(),
                        saved.getDueAt(), saved.getCompletedAt()).toMap());
        return MilestoneView.from(saved);
    }

    @Transactional
    public MilestoneView complete(Long userId, Long planId, Long milestoneId) {
        Milestone m = loadOwnedMilestone(userId, planId, milestoneId);
        OffsetDateTime now = OffsetDateTime.now(clock);
        m.complete(now);   // DONE → 抛 MilestoneAlreadyDoneException（BR-14）
        Milestone saved = milestoneRepository.save(m);

        appendMilestoneEvent(saved, EventType.MILESTONE_COMPLETED,
                new MilestoneCompletedPayload(
                        saved.getId(), saved.getPlanId(), saved.getUserId(),
                        saved.getCompletedAt()).toMap());
        return MilestoneView.from(saved);
    }

    @Transactional
    public MilestoneView reopen(Long userId, Long planId, Long milestoneId) {
        Milestone m = loadOwnedMilestone(userId, planId, milestoneId);
        m.reopen();   // 非 DONE → 抛 MilestoneNotDoneException（BR-14）
        Milestone saved = milestoneRepository.save(m);

        appendMilestoneEvent(saved, EventType.MILESTONE_UPDATED,
                new MilestoneUpdatedPayload(
                        saved.getId(), saved.getPlanId(), saved.getUserId(),
                        MilestoneStatus.DONE.name(), saved.getStatus().name(),
                        saved.getDueAt(), saved.getCompletedAt()).toMap());
        return MilestoneView.from(saved);
    }

    @Transactional
    public void softDelete(Long userId, Long planId, Long milestoneId) {
        Milestone m = loadOwnedMilestone(userId, planId, milestoneId);
        m.softDelete();
        milestoneRepository.save(m);
        // 不发 outbox：v1.0 milestone 删除不在事件集合内
    }

    /** 关联 task（plan-05-plan §3.2 端点 7）。 */
    @Transactional
    public List<Long> linkTasks(Long userId, Long planId, Long milestoneId, List<Long> taskIds) {
        Milestone m = loadOwnedMilestone(userId, planId, milestoneId);
        return linkService.link(userId, m.getId(), taskIds);
    }

    // ---------- internals ----------

    private Plan loadOwnedPlan(Long userId, Long planId) {
        return planRepository.findByIdAndUserIdAndDeletedAtIsNull(planId, userId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    private Milestone loadOwnedMilestone(Long userId, Long planId, Long milestoneId) {
        return milestoneRepository
                .findByIdAndUserIdAndPlanIdAndDeletedAtIsNull(milestoneId, userId, planId)
                .orElseThrow(() -> new MilestoneNotFoundException(milestoneId));
    }

    private void appendMilestoneEvent(Milestone m, EventType type, java.util.Map<String, Object> payload) {
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                type.eventType(),
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