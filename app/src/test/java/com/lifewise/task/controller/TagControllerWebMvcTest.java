package com.lifewise.task.controller;

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
import com.lifewise.task.dto.CreateTagRequest;
import com.lifewise.task.dto.TaskTagView;
import com.lifewise.task.dto.UpdateTagRequest;
import com.lifewise.task.service.TagService;
import com.lifewise.task.service.exception.DuplicateTagNameException;
import com.lifewise.task.web.CurrentUserArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TagController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class, TaskGlobalExceptionHandler.class})
class TagControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean TagService tagService;

    @Test
    void list_returns_ok() throws Exception {
        when(tagService.list(7L)).thenReturn(List.of());
        mockMvc.perform(get("/api/task-tags").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void create_returns_201() throws Exception {
        TaskTagView view = new TaskTagView(1L, "x", null);
        when(tagService.create(7L, "x", null)).thenReturn(view);
        CreateTagRequest req = new CreateTagRequest("x", null);
        mockMvc.perform(post("/api/task-tags").header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void create_duplicate_returns_409() throws Exception {
        when(tagService.create(7L, "x", null)).thenThrow(new DuplicateTagNameException("x"));
        CreateTagRequest req = new CreateTagRequest("x", null);
        mockMvc.perform(post("/api/task-tags").header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void rename_returns_200() throws Exception {
        TaskTagView view = new TaskTagView(1L, "y", null);
        when(tagService.rename(anyLong(), anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(view);
        UpdateTagRequest req = new UpdateTagRequest("y", null);
        mockMvc.perform(put("/api/task-tags/1").header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/task-tags/1").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());
    }
}