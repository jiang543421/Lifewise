package com.lifewise.task.port.out;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.repository.TaskRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskReadPortAdapterTest {

    @Mock TaskRepository taskRepository;
    TaskReadPortAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new TaskReadPortAdapter(taskRepository);
    }

    @Test
    void findById_returns_empty_when_user_mismatch() {
        Task t = Task.create(99L, "x", null, TaskPriority.NORMAL, null, null);
        t.setIdInternal(1L);
        when(taskRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        assertThat(adapter.findById(7L, 1L)).isEmpty();
    }

    @Test
    void findById_returns_snapshot_when_match() {
        Task t = Task.create(7L, "x", null, TaskPriority.NORMAL, null, null);
        t.setIdInternal(1L);
        when(taskRepository.findByIdAndDeletedAtIsNull(1L)).thenReturn(Optional.of(t));
        assertThat(adapter.findById(7L, 1L)).isPresent();
        assertThat(adapter.findById(7L, 1L).orElseThrow().userId()).isEqualTo(7L);
    }

    @Test
    void findByIds_filters_via_repository() {
        when(taskRepository.findByUserIdAndIdInAndDeletedAtIsNull(7L, List.of()))
                .thenReturn(List.of());
        assertThat(adapter.findByIds(7L, List.of())).isEmpty();
    }

    @Test
    void findByPlanId_returns_empty_until_plan_lands() {
        assertThat(adapter.findByPlanId(7L, 1L)).isEmpty();
    }

    @Test
    void countCompletedSince_delegates_to_repository() {
        Instant since = Instant.now().minus(7, ChronoUnit.DAYS);
        when(taskRepository.countCompletedSince(any(Long.class), any(OffsetDateTime.class)))
                .thenReturn(5L);
        assertThat(adapter.countCompletedSince(7L, since)).isEqualTo(5L);
    }
}