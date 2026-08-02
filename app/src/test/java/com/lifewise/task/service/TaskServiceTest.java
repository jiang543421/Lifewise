package com.lifewise.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.event.EventEnvelope;
import com.lifewise.shared.integration.outbox.OutboxWriter;
import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.dto.TaskCreateRequest;
import com.lifewise.task.dto.TaskUpdateRequest;
import com.lifewise.task.dto.TaskView;
import com.lifewise.task.repository.TaskRepository;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import com.lifewise.task.service.exception.TaskNotFoundException;
import com.lifewise.task.service.exception.TaskStateConflictException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskTagRepository taskTagRepository;
    @Mock TaskTagLinkRepository taskTagLinkRepository;
    @Mock OutboxWriter outboxWriter;
    Clock clock = Clock.fixed(Instant.parse("2026-08-02T00:00:00Z"), ZoneOffset.UTC);

    TaskService service;

    @BeforeEach
    void setUp() {
        service = new TaskService(taskRepository, taskTagRepository, taskTagLinkRepository,
                outboxWriter, clock);
    }

    private static Task withId(Task t, long id) {
        t.setIdInternal(id);
        return t;
    }

    @Test
    void create_persists_task_and_emits_created_event() {
        TaskCreateRequest req = new TaskCreateRequest("write", "desc", TaskPriority.HIGH,
                OffsetDateTime.now(clock), null, List.of());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> {
            Task t = inv.getArgument(0);
            t.setIdInternal(1L);
            return t;
        });

        TaskView view = service.create(7L, req);

        assertThat(view.title()).isEqualTo("write");
        assertThat(view.priority()).isEqualTo(TaskPriority.HIGH);
        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("task.created");
    }

    @Test
    void complete_emits_completed_event() {
        Task task = withId(Task.create(7L, "t", null, TaskPriority.NORMAL, null, null), 11L);
        when(taskRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskView view = service.complete(7L, 11L);

        assertThat(view.status()).isEqualTo(TaskStatus.DONE);
        assertThat(view.completedAt()).isNotNull();
        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("task.completed");
    }

    @Test
    void complete_twice_throws_state_conflict() {
        Task task = withId(Task.create(7L, "t", null, TaskPriority.NORMAL, null, null), 11L);
        task.markCompleted(OffsetDateTime.now(clock));
        when(taskRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.complete(7L, 11L))
                .isInstanceOf(TaskStateConflictException.class);
        verify(outboxWriter, never()).append(any());
    }

    @Test
    void reopen_emits_reopened_event() {
        Task task = withId(Task.create(7L, "t", null, TaskPriority.NORMAL, null, null), 11L);
        task.markCompleted(OffsetDateTime.now(clock));
        when(taskRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        TaskView view = service.reopen(7L, 11L);

        assertThat(view.status()).isEqualTo(TaskStatus.OPEN);
        assertThat(view.completedAt()).isNull();
        ArgumentCaptor<EventEnvelope> env = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(outboxWriter, times(1)).append(env.capture());
        assertThat(env.getValue().eventType()).isEqualTo("task.reopened");
    }

    @Test
    void update_emits_updated_event() {
        Task task = withId(Task.create(7L, "t", null, TaskPriority.NORMAL, null, null), 11L);
        when(taskRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        TaskUpdateRequest req = new TaskUpdateRequest("new", null, null, null, null, null);

        service.update(7L, 11L, req);

        verify(outboxWriter).append(any(EventEnvelope.class));
    }

    @Test
    void get_owned_throws_when_not_found() {
        when(taskRepository.findByIdAndDeletedAtIsNull(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getOwned(7L, 99L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void get_owned_throws_when_user_mismatch() {
        Task task = withId(Task.create(99L, "x", null, TaskPriority.NORMAL, null, null), 11L);
        when(taskRepository.findByIdAndDeletedAtIsNull(11L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.getOwned(7L, 11L))
                .isInstanceOf(TaskNotFoundException.class);
    }
}
