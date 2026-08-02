package com.lifewise.task.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.task.config.WebMvcConfig;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.dto.TaskCreateRequest;
import com.lifewise.task.dto.TaskListItem;
import com.lifewise.task.dto.TaskUpdateRequest;
import com.lifewise.task.dto.TaskView;
import com.lifewise.task.service.TaskQueryService;
import com.lifewise.task.service.TaskService;
import com.lifewise.task.service.exception.ParentUserMismatchException;
import com.lifewise.task.service.exception.TagLimitExceededException;
import com.lifewise.task.service.exception.TaskNotFoundException;
import com.lifewise.task.service.exception.TaskStateConflictException;
import com.lifewise.task.web.CurrentUserArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** 端到端验证 TaskController 7 端点的契约（状态码、信封、错误映射）。 */
@WebMvcTest(controllers = TaskController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class, TaskGlobalExceptionHandler.class})
class TaskControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TaskService taskService;
    @MockBean TaskQueryService taskQueryService;

    private static final String HEADER = "X-User-Id";

    @Test
    void list_returns_paged_envelope() throws Exception {
        TaskListItem item = new TaskListItem(1L, "t", TaskStatus.OPEN, TaskPriority.NORMAL,
                null, null, List.of());
        Page<TaskListItem> page = new PageImpl<>(List.of(item));
        when(taskQueryService.list(anyLong(), any(), any(), any(), any(Pageable.class))).thenReturn(page);
        mockMvc.perform(get("/api/tasks").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1));
    }

    @Test
    void get_returns_view() throws Exception {
        com.lifewise.task.domain.Task entity = com.lifewise.task.domain.Task.create(
                7L, "t", null, TaskPriority.NORMAL, null, null);
        entity.setIdInternal(1L);
        when(taskService.getOwned(7L, 1L)).thenReturn(entity);
        mockMvc.perform(get("/api/tasks/1").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void get_not_found_returns_404() throws Exception {
        when(taskService.getOwned(7L, 1L)).thenThrow(new TaskNotFoundException(1L));
        mockMvc.perform(get("/api/tasks/1").header(HEADER, "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void create_persists_and_returns_201() throws Exception {
        TaskView view = new TaskView(1L, "x", null, TaskStatus.OPEN, TaskPriority.HIGH,
                null, null, null);
        when(taskService.create(anyLong(), any(TaskCreateRequest.class))).thenReturn(view);
        TaskCreateRequest req = new TaskCreateRequest("x", null, TaskPriority.HIGH, null, null, List.of());
        mockMvc.perform(post("/api/tasks").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void update_returns_200() throws Exception {
        TaskView view = new TaskView(1L, "x", null, TaskStatus.OPEN, TaskPriority.HIGH,
                null, null, null);
        when(taskService.update(anyLong(), anyLong(), any(TaskUpdateRequest.class))).thenReturn(view);
        TaskUpdateRequest req = new TaskUpdateRequest("x", null, null, null, null, null);
        mockMvc.perform(put("/api/tasks/1").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/tasks/1").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());
    }

    @Test
    void complete_conflict_returns_409() throws Exception {
        when(taskService.complete(7L, 1L)).thenThrow(new TaskStateConflictException(
                TaskStateConflictException.Kind.ALREADY_COMPLETED, 1L));
        mockMvc.perform(post("/api/tasks/1/complete").header(HEADER, "7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missing_user_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isUnauthorized());
    }

    // ---- TaskGlobalExceptionHandler 映射补全（task §7 关键路径）----

    @Test
    void update_parent_user_mismatch_returns_403() throws Exception {
        when(taskService.update(anyLong(), anyLong(), any(TaskUpdateRequest.class)))
                .thenThrow(new ParentUserMismatchException(99L));
        TaskUpdateRequest req = new TaskUpdateRequest("x", null, null, null, 99L, null);
        mockMvc.perform(put("/api/tasks/1").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CROSS_USER_ACCESS"));
    }

    @Test
    void create_validation_error_returns_400_with_field_details() throws Exception {
        // title 为空白（@NotBlank），应触发 MethodArgumentNotValidException → 400 + field details
        String invalid = "{\"title\":\"\",\"priority\":\"HIGH\"}";
        mockMvc.perform(post("/api/tasks").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.details.errors").isArray());
    }

    @Test
    void reopen_already_open_returns_409() throws Exception {
        when(taskService.reopen(7L, 1L)).thenThrow(new TaskStateConflictException(
                TaskStateConflictException.Kind.ALREADY_OPEN, 1L));
        mockMvc.perform(post("/api/tasks/1/reopen").header(HEADER, "7"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TASK_INVALID_STATUS_TRANSITION"));
    }

    @Test
    void create_tag_limit_exceeded_returns_409() throws Exception {
        when(taskService.create(anyLong(), any(TaskCreateRequest.class)))
                .thenThrow(new TagLimitExceededException(1L, 6));
        // 6 个 tag id，超过 MAX_TAGS_PER_TASK=5
        java.util.List<Long> tagIds = java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L);
        TaskCreateRequest req = new TaskCreateRequest("x", null, TaskPriority.NORMAL, null, null, tagIds);
        mockMvc.perform(post("/api/tasks").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }
}