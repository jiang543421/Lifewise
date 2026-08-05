package com.lifewise.plan.port.out;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.repository.MilestoneRepository;
import com.lifewise.plan.repository.PlanRepository;
import com.lifewise.shared.integration.port.PlanReadPort;
import com.lifewise.shared.integration.port.snapshot.PlanSnapshot;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Plan 模块对其他模块暴露的只读端口实现（plan-shared-integration §2.2）。
 *
 * <p>对齐 {@link PlanReadPort} 接口契约：
 * <ul>
 *   <li>所有方法第一参数 userId 强制所有权</li>
 *   <li>返回 {@link PlanSnapshot} 而非 JPA entity</li>
 *   <li>v1.0 额外提供 {@link #findMilestonesByTaskId(Long)} 和 {@link #computeProgress(Long)}
 *       给 task 模块用（plan-05-plan §6）</li>
 * </ul>
 */
@Component
public class PlanReadPortAdapter implements PlanReadPort {

    private final PlanRepository planRepository;
    private final MilestoneRepository milestoneRepository;

    public PlanReadPortAdapter(PlanRepository planRepository,
                               MilestoneRepository milestoneRepository) {
        this.planRepository = planRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @Override
    public Optional<PlanSnapshot> findById(Long userId, Long planId) {
        return planRepository.findByIdAndUserIdAndDeletedAtIsNull(planId, userId)
                .map(p -> new PlanSnapshot(
                        p.getId(), p.getUserId(), p.getTitle(),
                        p.getStatus().name(),
                        p.getStartDate(), p.getTargetEndDate()));
    }

    @Override
    public List<PlanSnapshot> findActiveByUser(Long userId) {
        return planRepository.findActiveByUser(userId).stream()
                .map(p -> new PlanSnapshot(
                        p.getId(), p.getUserId(), p.getTitle(),
                        p.getStatus().name(),
                        p.getStartDate(), p.getTargetEndDate()))
                .toList();
    }

    /** task 模块在 task.completed 事件消费时调用，列出该 task 所属 milestone ID 集合。 */
    public List<Long> findMilestonesByTaskId(Long taskId) {
        return milestoneRepository.findByTaskId(taskId).stream()
                .map(Milestone::getId)
                .toList();
    }

    /** ai 模块在 plan 进度投影时调用，计算 DONE / (PENDING + DONE) 比例。 */
    public double computeProgress(Long planId) {
        List<Milestone> all = milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(planId);
        long total = all.stream()
                .filter(m -> m.getStatus() != MilestoneStatus.CANCELLED)
                .count();
        if (total == 0) {
            return 0.0;
        }
        long done = all.stream()
                .filter(m -> m.getStatus() == MilestoneStatus.DONE)
                .count();
        return (double) done / (double) total;
    }
}