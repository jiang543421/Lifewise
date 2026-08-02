package com.lifewise.task.service;

import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.dto.TaskListItem;
import com.lifewise.task.repository.TaskRepository;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import java.util.Collections;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 任务列表 / 搜索。 */
@Service
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final TaskTagRepository taskTagRepository;
    private final TaskTagLinkRepository taskTagLinkRepository;

    public TaskQueryService(TaskRepository taskRepository,
                            TaskTagRepository taskTagRepository,
                            TaskTagLinkRepository taskTagLinkRepository) {
        this.taskRepository = taskRepository;
        this.taskTagRepository = taskTagRepository;
        this.taskTagLinkRepository = taskTagLinkRepository;
    }

    @Transactional(readOnly = true)
    public Page<TaskListItem> list(long userId, TaskStatus status, TaskPriority priority,
                                   Long tagId, Pageable pageable) {
        Page<Task> page = taskRepository.search(userId, status, priority == null ? null : priority.name(),
                pageable);
        if (tagId != null && taskTagLinkRepository.countByIdTaskId(tagId) == 0
                && taskTagRepository.findById(tagId).isEmpty()) {
            return Page.empty(pageable);
        }
        return page.map(t -> TaskListItem.of(t, tagIdsOf(t.getId())));
    }

    private List<Long> tagIdsOf(long taskId) {
        return Collections.unmodifiableList(taskTagLinkRepository.findByIdTaskId(taskId)
                .stream().map(l -> l.getTagId()).toList());
    }
}
