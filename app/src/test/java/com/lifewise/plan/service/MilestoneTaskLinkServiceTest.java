package com.lifewise.plan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lifewise.plan.domain.MilestoneTaskLink;
import com.lifewise.plan.repository.MilestoneTaskLinkRepository;
import com.lifewise.plan.service.exception.CrossModuleTaskNotFoundException;
import com.lifewise.shared.integration.port.TaskReadPort;
import com.lifewise.shared.integration.port.snapshot.TaskSnapshot;
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

/** MilestoneTaskLinkService 单元测试（plan-05-plan §2.3 - link 跨模块 task 引用）。 */
@ExtendWith(MockitoExtension.class)
class MilestoneTaskLinkServiceTest {

    @Mock MilestoneTaskLinkRepository linkRepository;
    @Mock TaskReadPort taskReadPort;

    private MilestoneTaskLinkService service;

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-04T00:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new MilestoneTaskLinkService(linkRepository, taskReadPort, FIXED_CLOCK);
    }

    @Test
    void link_returns_empty_when_task_ids_empty() {
        List<Long> result = service.link(7L, 50L, List.of());

        assertThat(result).isEmpty();
        verify(linkRepository, never()).save(any());
    }

    @Test
    void link_returns_empty_when_task_ids_null() {
        List<Long> result = service.link(7L, 50L, null);

        assertThat(result).isEmpty();
        verify(linkRepository, never()).save(any());
    }

    @Test
    void link_inserts_for_new_tasks() {
        when(taskReadPort.findById(7L, 100L)).thenReturn(Optional.of(task(100L)));
        when(taskReadPort.findById(7L, 101L)).thenReturn(Optional.of(task(101L)));
        when(linkRepository.existsById(any(MilestoneTaskLink.PK.class))).thenReturn(false);

        List<Long> result = service.link(7L, 50L, List.of(100L, 101L));

        assertThat(result).containsExactly(100L, 101L);
        ArgumentCaptor<MilestoneTaskLink> captor =
                ArgumentCaptor.forClass(MilestoneTaskLink.class);
        verify(linkRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(l -> {
                    assertThat(l.getMilestoneId()).isEqualTo(50L);
                    assertThat(l.getCreatedAt()).isEqualTo(OffsetDateTime.now(FIXED_CLOCK));
                });
    }

    @Test
    void link_deduplicates_input() {
        when(taskReadPort.findById(7L, 100L)).thenReturn(Optional.of(task(100L)));
        when(linkRepository.existsById(any(MilestoneTaskLink.PK.class))).thenReturn(false);

        List<Long> result = service.link(7L, 50L, List.of(100L, 100L, 100L));

        assertThat(result).containsExactly(100L);
        verify(linkRepository, times(1)).save(any(MilestoneTaskLink.class));
    }

    @Test
    void link_skips_already_linked() {
        when(linkRepository.existsById(any(MilestoneTaskLink.PK.class))).thenReturn(true);

        List<Long> result = service.link(7L, 50L, List.of(100L));

        assertThat(result).containsExactly(100L);
        verify(linkRepository, never()).save(any(MilestoneTaskLink.class));
        verify(taskReadPort, never()).findById(any(), any());
    }

    @Test
    void link_throws_when_task_not_owned() {
        when(taskReadPort.findById(7L, 999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.link(7L, 50L, List.of(999L)))
                .isInstanceOf(CrossModuleTaskNotFoundException.class);

        verify(linkRepository, never()).save(any(MilestoneTaskLink.class));
    }

    private static TaskSnapshot task(long id) {
        return new TaskSnapshot(id, 7L, "t", "OPEN", null, null);
    }
}