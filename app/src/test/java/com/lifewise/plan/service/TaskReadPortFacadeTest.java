package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
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
        // v1.0 单用户白名单（CLAUDE.md §7.3.1）：默认值 1L
        facade = new TaskReadPortFacade(taskReadPort, 1L);
    }

    @Test
    void findById_passes_v1_user_id_and_returns_snapshot() {
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
    void findByIds_returns_snapshots_directly() {
        List<TaskSnapshot> snapshots = List.of(task(101L), task(102L));
        when(taskReadPort.findByIds(7L, List.of(101L, 102L))).thenReturn(snapshots);

        assertThat(facade.findByIds(7L, List.of(101L, 102L)))
                .containsExactlyElementsOf(snapshots);
    }

    @Test
    void countCompletedInPlan_returns_zero_when_no_links() {
        when(taskReadPort.findByPlanId(7L, 1L)).thenReturn(List.of());

        assertThat(facade.countCompletedInPlan(7L, 1L)).isZero();
    }

    @Test
    void countCompletedInPlan_counts_only_done_status() {
        when(taskReadPort.findByPlanId(7L, 1L))
                .thenReturn(List.of(task(101L), task(102L), task(103L)));
        when(taskReadPort.findByIds(7L, List.of(101L, 102L, 103L)))
                .thenReturn(List.of(
                        new TaskSnapshot(101L, 7L, "a", "DONE", null, null),
                        new TaskSnapshot(102L, 7L, "b", "OPEN", null, null),
                        new TaskSnapshot(103L, 7L, "c", "DONE", null, null)));

        assertThat(facade.countCompletedInPlan(7L, 1L)).isEqualTo(2L);
    }

    @Test
    void custom_v1_user_id_is_respected() {
        TaskReadPortFacade customFacade = new TaskReadPortFacade(taskReadPort, 42L);
        when(taskReadPort.findById(42L, 101L)).thenReturn(Optional.of(task(101L)));

        customFacade.findById(101L);

        verify(taskReadPort).findById(42L, 101L);
    }

    private static TaskSnapshot task(long id) {
        return new TaskSnapshot(id, 7L, "t", "OPEN", null, null);
    }
}