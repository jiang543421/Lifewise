package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TaskReadPortFacade 单元测试（plan-05-plan §2.4 - 跨模块 task 读取门面）。 */
@ExtendWith(MockitoExtension.class)
class TaskReadPortFacadeTest {

    @Mock TaskReadPort taskReadPort;

    private TaskReadPortFacade facade;

    @BeforeEach
    void setUp() {
        facade = new TaskReadPortFacade(taskReadPort);
    }

    @Test
    void findById_passes_user_id_1_and_returns_snapshot() {
        TaskSnapshot snapshot = task(101L);
        when(taskReadPort.findById(1L, 101L)).thenReturn(Optional.of(snapshot));

        Optional<TaskSnapshot> result = facade.findById(101L);

        assertThat(result).contains(snapshot);
        verify(taskReadPort, times(1)).findById(1L, 101L);
    }

    @Test
    void findById_returns_empty_when_port_empty() {
        when(taskReadPort.findById(1L, 999L)).thenReturn(Optional.empty());

        assertThat(facade.findById(999L)).isEmpty();
    }

    @Test
    void findByPlanId_projects_to_id_list() {
        when(taskReadPort.findByPlanId(1L, 7L))
                .thenReturn(List.of(task(101L), task(102L), task(103L)));

        List<Long> ids = facade.findByPlanId(7L);

        assertThat(ids).containsExactly(101L, 102L, 103L);
    }

    @Test
    void countCompletedSince_uses_epoch() {
        when(taskReadPort.countCompletedSince(eq(7L), any(Instant.class))).thenReturn(5L);

        long count = facade.countCompletedSince(7L, 1L);

        assertThat(count).isEqualTo(5L);
        verify(taskReadPort).countCompletedSince(eq(7L), any(Instant.class));
    }

    private static TaskSnapshot task(long id) {
        return new TaskSnapshot(id, 7L, "t", "OPEN", null, null);
    }
}