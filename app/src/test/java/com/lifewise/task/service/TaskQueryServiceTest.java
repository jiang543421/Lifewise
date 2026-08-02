package com.lifewise.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.lifewise.task.domain.Task;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.repository.TaskRepository;
import com.lifewise.task.repository.TaskTagLinkRepository;
import com.lifewise.task.repository.TaskTagRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

    @Mock TaskRepository taskRepository;
    @Mock TaskTagRepository taskTagRepository;
    @Mock TaskTagLinkRepository taskTagLinkRepository;
    TaskQueryService service;

    @BeforeEach
    void setUp() {
        service = new TaskQueryService(taskRepository, taskTagRepository, taskTagLinkRepository);
    }

    @Test
    void list_returns_empty_when_no_match() {
        when(taskRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        Page<?> page = service.list(7L, null, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void list_returns_items_with_tagIds() {
        Task t = Task.create(7L, "x", null, TaskPriority.NORMAL, null, null);
        t.setIdInternal(1L);
        when(taskRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(t)));
        when(taskTagLinkRepository.findByIdTaskId(1L)).thenReturn(List.of());
        Page<?> page = service.list(7L, TaskStatus.OPEN, null, null, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void list_with_tagId_returns_empty_when_tag_missing() {
        when(taskRepository.search(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of()));
        when(taskTagLinkRepository.countByIdTaskId(99L)).thenReturn(0L);
        when(taskTagRepository.findById(99L)).thenReturn(java.util.Optional.empty());
        Page<?> page = service.list(7L, null, null, 99L, PageRequest.of(0, 20));
        assertThat(page.getContent()).isEmpty();
    }
}