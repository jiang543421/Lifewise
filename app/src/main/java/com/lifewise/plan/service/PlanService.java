package com.lifewise.plan.service;

import com.lifewise.plan.domain.Plan;
import com.lifewise.plan.domain.PlanStatus;
import com.lifewise.plan.dto.PlanCreateRequest;
import com.lifewise.plan.dto.PlanView;
import com.lifewise.plan.event.payload.PlanCreatedPayload;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.plan.service.exception.PlanAlreadyAbandonedException;
import com.lifewise.plan.service.exception.PlanNotFoundException;
import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plan 写服务（plan-05-plan §3.1 - 6 端点 + BR-15 / BR-30）。
 *
 * <p>职责：CRUD + outbox 同事务 + 软删时级联软删 milestones（plan-05-plan §4.5）。
 * softDelete 与 abandon 都不发 plan.* outbox 事件（v1.0 仅
 * {@code plan.created} 触发），milestone 事件由 MilestoneService 单独负责。
 */
@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final MilestoneRepository milestoneRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public PlanService(PlanRepository planRepository,
                       MilestoneRepository milestoneRepository,
                       OutboxWriter outboxWriter,
                       Clock clock) {
        this.planRepository = planRepository;
        this.milestoneRepository = milestoneRepository;
        this.outboxWriter = outboxWriter;
        this.clock = clock;
    }

    @Transactional
    public PlanView create(Long userId, PlanCreateRequest req) {
        Plan plan = Plan.create(userId, req.title(), req.description(), req.type(),
                req.startDate(), req.targetEndDate());
        Plan saved = planRepository.save(plan);

        appendPlanCreatedEvent(saved);
        return PlanView.from(saved);
    }

    @Transactional
    public PlanView update(Long userId, Long planId, PlanCreateRequest req) {
        Plan plan = loadOwnedPlan(userId, planId);
        // 已放弃（status=CANCELLED）的 plan 不可编辑（与 MilestoneService.create 行为一致）
        if (plan.isCancelled()) {
            throw new PlanAlreadyAbandonedException(planId);
        }
        plan.applyUpdate(req.title(), req.description(), req.type(),
                req.startDate(), req.targetEndDate());
        Plan saved = planRepository.save(plan);
        return PlanView.from(saved);
    }

    /**
     * 软删除 plan：同事务级联软删 milestones（plan-05-plan §4.5）。
     * 注意：plan 模块不消费 task 事件，所以 task 模块对 task 实体的清理不在本路径。
     */
    @Transactional
    public void softDelete(Long userId, Long planId) {
        Plan plan = loadOwnedPlan(userId, planId);
        milestoneRepository.softDeleteByPlanId(planId);
        plan.softDelete();
        planRepository.save(plan);
        // 不发 outbox（plan 业务事件仅 plan.created）
    }

    @Transactional
    public PlanView abandon(Long userId, Long planId) {
        Plan plan = loadOwnedPlan(userId, planId);
        plan.abandon();
        Plan saved = planRepository.save(plan);
        return PlanView.from(saved);
    }

    @Transactional(readOnly = true)
    public PlanView getById(Long userId, Long planId) {
        return PlanView.from(loadOwnedPlan(userId, planId));
    }

    /**
     * @param includeCancelled true → 含 CANCELLED（默认 false 仅 ACTIVE）
     */
    @Transactional(readOnly = true)
    public List<PlanView> list(Long userId, LocalDate from, LocalDate to,
                               boolean includeCancelled) {
        List<Plan> plans = includeCancelled
                ? planRepository.findAllActiveOrCancelledByUser(userId)
                : planRepository.findActiveByUser(userId);
        return plans.stream().map(PlanView::from).toList();
    }

    // ---------- internals ----------

    private Plan loadOwnedPlan(Long userId, Long planId) {
        return planRepository.findByIdAndUserIdAndDeletedAtIsNull(planId, userId)
                .orElseThrow(() -> new PlanNotFoundException(planId));
    }

    private void appendPlanCreatedEvent(Plan p) {
        OffsetDateTime eventAt = OffsetDateTime.now(clock);
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                EventType.PLAN_CREATED.eventType(),
                1,
                eventAt,
                p.getUserId(),
                "plan",
                p.getId(),
                null,
                null,
                null,
                new PlanCreatedPayload(
                        p.getId(),
                        p.getUserId(),
                        p.getTitle(),
                        p.getType(),
                        p.getStartDate(),
                        p.getTargetEndDate()).toMap()));
    }
}