package com.lifewise.plan.service;

import com.lifewise.plan.domain.Milestone;
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.dto.ProgressView;
import com.lifewise.plan.repository.MilestoneRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 进度评估器（plan-05-plan §3.3 - GET /api/plans/{id}/progress）。
 *
 * <p>计算：
 * <ul>
 *   <li>已完成里程碑数 / 总有效里程碑数（非 CANCELLED）</li>
 *   <li>通过 TaskReadPort 跨模块查询 task 完成数 / 总数</li>
 *   <li>ratio = done / total（total=0 时 ratio=0）</li>
 * </ul>
 */
@Service
public class ProgressEvaluator {

    private final MilestoneRepository milestoneRepository;
    private final TaskReadPortFacade taskReadPortFacade;
    private final Clock clock;

    public ProgressEvaluator(MilestoneRepository milestoneRepository,
                             TaskReadPortFacade taskReadPortFacade,
                             Clock clock) {
        this.milestoneRepository = milestoneRepository;
        this.taskReadPortFacade = taskReadPortFacade;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProgressView compute(Long userId, Long planId) {
        List<Milestone> all = milestoneRepository.findAllByPlanIdAndDeletedAtIsNull(planId);
        Map<MilestoneStatus, Long> grouped = all.stream()
                .collect(Collectors.groupingBy(Milestone::getStatus, Collectors.counting()));

        long total = all.stream()
                .filter(m -> m.getStatus() != MilestoneStatus.CANCELLED)
                .count();
        long completed = grouped.getOrDefault(MilestoneStatus.DONE, 0L);

        List<Long> linkedTaskIds = taskReadPortFacade.findByPlanId(planId);
        long totalTasks = linkedTaskIds.size();
        long completedTasks = linkedTaskIds.isEmpty()
                ? 0
                : taskReadPortFacade.countCompletedSince(userId, planId);

        double ratio = total == 0 ? 0.0 : (double) completed / (double) total;
        return new ProgressView(planId, completed, total, completedTasks, totalTasks,
                ratio, linkedTaskIds);
    }
}