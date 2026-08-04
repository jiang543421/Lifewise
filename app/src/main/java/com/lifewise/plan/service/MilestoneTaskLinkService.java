package com.lifewise.plan.service;

import com.lifewise.plan.domain.MilestoneTaskLink;
import com.lifewise.plan.repository.MilestoneTaskLinkRepository;
import com.lifewise.plan.service.exception.CrossModuleTaskNotFoundException;
import com.lifewise.shared.integration.port.TaskReadPort;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Milestone ↔ Task 关联服务（plan-05-plan §2.3 + §3.2 端点 7）。
 *
 * <p>跨模块约束：plan 模块不能修改 task 实体，所以这里仅写关联表；
 * 任何要插入的 taskId 必须先经 {@link TaskReadPort#findById} 校验存在且归属当前用户。
 */
@Service
public class MilestoneTaskLinkService {

    private final MilestoneTaskLinkRepository linkRepository;
    private final TaskReadPort taskReadPort;
    private final Clock clock;

    public MilestoneTaskLinkService(MilestoneTaskLinkRepository linkRepository,
                                    TaskReadPort taskReadPort,
                                    Clock clock) {
        this.linkRepository = linkRepository;
        this.taskReadPort = taskReadPort;
        this.clock = clock;
    }

    @Transactional
    public List<Long> link(Long userId, Long milestoneId, List<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return List.of();
        }
        Set<Long> deduped = new LinkedHashSet<>(taskIds).stream().collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Long> inserted = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Long taskId : deduped) {
            if (linkRepository.existsById(new MilestoneTaskLink.PK(milestoneId, taskId))) {
                inserted.add(taskId);
                continue;
            }
            // 跨模块校验：task 必须存在且属于当前用户
            taskReadPort.findById(userId, taskId)
                    .orElseThrow(() -> new CrossModuleTaskNotFoundException(taskId));
            linkRepository.save(new MilestoneTaskLink(milestoneId, taskId, now));
            inserted.add(taskId);
        }
        return inserted;
    }

    /** 抑制未使用引用警告。 */
    @SuppressWarnings("unused")
    private static Set<Long> emptySet() {
        return new HashSet<>();
    }
}