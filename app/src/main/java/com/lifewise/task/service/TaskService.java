package com.lifewise.task.service;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.event.EventType;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.domain.TaskTag;
import com.lifewise.task.domain.TaskTagLink;
import com.lifewise.task.dto.TaskCreateRequest;
import com.lifewise.task.dto.TaskUpdateRequest;
import com.lifewise.task.dto.TaskView;
import com.lifewise.task.event.payload.TaskCompletedPayload;
import com.lifewise.task.event.payload.TaskCreatedPayload;
import com.lifewise.task.event.payload.TaskReopenedPayload;
import com.lifewise.task.event.payload.TaskUpdatedPayload;
import com.lifewise.task.repository.TaskRepository;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import com.lifewise.task.service.exception.ParentUserMismatchException;
import com.lifewise.task.service.exception.TagLimitExceededException;
import com.lifewise.task.service.exception.TagNotFoundException;
import com.lifewise.task.service.exception.TaskNotFoundException;
import com.lifewise.task.service.exception.TaskStateConflictException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Task 写操作：create / update / softDelete / complete / reopen，伴随 4 个 Outbox 事件。 */
@Service
public class TaskService {

    private static final int MAX_TAGS_PER_TASK = 5;

    private final TaskRepository taskRepository;
    private final TaskTagRepository taskTagRepository;
    private final TaskTagLinkRepository taskTagLinkRepository;
    private final OutboxWriter outboxWriter;
    private final Clock clock;

    public TaskService(TaskRepository taskRepository,
                       TaskTagRepository taskTagRepository,
                       TaskTagLinkRepository taskTagLinkRepository,
                       OutboxWriter outboxWriter,
                       Clock authClock) {
        this.taskRepository = taskRepository;
        this.taskTagRepository = taskTagRepository;
        this.taskTagLinkRepository = taskTagLinkRepository;
        this.outboxWriter = outboxWriter;
        this.clock = authClock;
    }

    @Transactional
    public TaskView create(long userId, TaskCreateRequest req) {
        TaskPriority priority = req.priority() == null ? TaskPriority.NORMAL : req.priority();
        Long parentId = req.parentId();
        if (parentId != null) {
            Task parent = loadOwnedTask(userId, parentId);
            if (parent.getParentId() != null) {
                throw new TaskStateConflictException(TaskStateConflictException.Kind.ALREADY_COMPLETED, parentId);
            }
        }
        Task task = Task.create(userId, req.title(), req.description(), priority, req.dueAt(), parentId);
        task = taskRepository.save(task);
        replaceTags(task.getId(), req.tagIdsOrEmpty(), userId);
        appendEvent(EventType.TASK_CREATED, userId, task.getId(), new TaskCreatedPayload(
                task.getId(), userId, null, OffsetDateTime.now(clock)).toMap());
        return TaskView.from(task);
    }

    @Transactional
    public TaskView update(long userId, long taskId, TaskUpdateRequest req) {
        Task task = loadOwnedTask(userId, taskId);
        task.applyUpdate(req.title(), req.description(), req.priority(), req.dueAt());
        if (req.parentId() != null) {
            if (req.parentId().equals(taskId)) {
                throw new TaskStateConflictException(TaskStateConflictException.Kind.ALREADY_COMPLETED, taskId);
            }
            Task parent = loadOwnedTask(userId, req.parentId());
            if (parent.getParentId() != null) {
                throw new ParentUserMismatchException(req.parentId());
            }
            task.applyParent(req.parentId());
        }
        if (req.tagIds() != null) {
            replaceTags(taskId, req.tagIdsOrEmpty(), userId);
        }
        task = taskRepository.save(task);
        appendEvent(EventType.TASK_UPDATED, userId, taskId, new TaskUpdatedPayload(
                task.getId(), userId, "update").toMap());
        return TaskView.from(task);
    }

    @Transactional
    public void softDelete(long userId, long taskId) {
        Task task = loadOwnedTask(userId, taskId);
        taskTagLinkRepository.deleteByIdTaskId(taskId);
        task.softDelete();
        taskRepository.save(task);
    }

    @Transactional
    public TaskView complete(long userId, long taskId) {
        Task task = loadOwnedTask(userId, taskId);
        if (task.getStatus() == TaskStatus.DONE) {
            throw new TaskStateConflictException(TaskStateConflictException.Kind.ALREADY_COMPLETED, taskId);
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        task.markCompleted(now);
        task = taskRepository.save(task);
        appendEvent(EventType.TASK_COMPLETED, userId, taskId, new TaskCompletedPayload(
                task.getId(), userId, null, now).toMap());
        return TaskView.from(task);
    }

    @Transactional
    public TaskView reopen(long userId, long taskId) {
        Task task = loadOwnedTask(userId, taskId);
        if (task.getStatus() == TaskStatus.OPEN) {
            throw new TaskStateConflictException(TaskStateConflictException.Kind.ALREADY_OPEN, taskId);
        }
        OffsetDateTime previous = task.getCompletedAt();
        task.reopen();
        task = taskRepository.save(task);
        appendEvent(EventType.TASK_REOPENED, userId, taskId, new TaskReopenedPayload(
                task.getId(), userId, null, previous).toMap());
        return TaskView.from(task);
    }

    @Transactional(readOnly = true)
    public Task getOwned(long userId, long taskId) {
        return loadOwnedTask(userId, taskId);
    }

    private Task loadOwnedTask(long userId, long taskId) {
        return taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .filter(t -> userId == t.getUserId())
                .orElseThrow(() -> new TaskNotFoundException(taskId));
    }

    private void replaceTags(long taskId, List<Long> tagIds, long userId) {
        if (tagIds == null) {
            return;
        }
        Set<Long> unique = new LinkedHashSet<>(tagIds);
        if (unique.size() > MAX_TAGS_PER_TASK) {
            throw new TagLimitExceededException(taskId, unique.size());
        }
        taskTagLinkRepository.deleteByIdTaskId(taskId);
        for (Long tagId : unique) {
            TaskTag tag = taskTagRepository.findById(tagId)
                    .filter(t -> userId == t.getUserId() && !t.isDeleted())
                    .orElseThrow(() -> new TagNotFoundException(tagId));
            taskTagLinkRepository.save(new TaskTagLink(taskId, tag.getId()));
        }
    }

    private void appendEvent(EventType type, long userId, long aggregateId, Map<String, Object> payload) {
        outboxWriter.append(new EventEnvelope(
                UUID.randomUUID(),
                type.eventType(),
                1,
                OffsetDateTime.now(clock),
                userId,
                "task",
                aggregateId,
                null,
                null,
                null,
                payload));
    }
}
