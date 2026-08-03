package com.lifewise.task.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.task.config.WebMvcConfig;
import com.lifewise.task.domain.HabitFrequency;
import com.lifewise.task.domain.HabitLogSource;
import com.lifewise.task.dto.HabitCreateRequest;
import com.lifewise.task.dto.HabitLogRequest;
import com.lifewise.task.dto.HabitLogView;
import com.lifewise.task.dto.HabitView;
import com.lifewise.task.service.HabitService;
import com.lifewise.task.service.exception.BackfillOutOfRangeException;
import com.lifewise.task.service.exception.BackfillRateLimitException;
import com.lifewise.task.web.CurrentUserArgumentResolver;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = HabitController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class, TaskGlobalExceptionHandler.class})
class HabitControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HabitService habitService;

    @Test
    void list_returns_ok() throws Exception {
        when(habitService.list(1L)).thenReturn(List.of());
        mockMvc.perform(get("/api/habits").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void create_returns_201() throws Exception {
        HabitView view = new HabitView(1L, "x", null, HabitFrequency.DAILY, 1, false, null, 0, 0);
        when(habitService.create(anyLong(), any(HabitCreateRequest.class))).thenReturn(view);
        HabitCreateRequest req = new HabitCreateRequest("x", null, HabitFrequency.DAILY, 1);
        mockMvc.perform(post("/api/habits").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/habits/1").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").exists());
    }

    @Test
    void log_backfill_oow_returns_400() throws Exception {
        when(habitService.log(anyLong(), anyLong(), any(HabitLogRequest.class)))
                .thenThrow(new BackfillOutOfRangeException("2026-01-01"));
        HabitLogRequest req = new HabitLogRequest(LocalDate.parse("2026-01-01"), HabitLogSource.NORMAL, null);
        mockMvc.perform(post("/api/habits/1/logs").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void log_returns_201_on_success() throws Exception {
        HabitLogView view = new HabitLogView(1L, 1L, LocalDate.parse("2026-08-02"),
                java.time.OffsetDateTime.now(), HabitLogSource.NORMAL, null);
        when(habitService.log(anyLong(), anyLong(), any(HabitLogRequest.class))).thenReturn(view);
        HabitLogRequest req = new HabitLogRequest(LocalDate.parse("2026-08-02"), HabitLogSource.NORMAL, null);
        mockMvc.perform(post("/api/habits/1/logs").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    void log_backfill_rate_limit_returns_429() throws Exception {
        when(habitService.log(anyLong(), anyLong(), any(HabitLogRequest.class)))
                .thenThrow(new BackfillRateLimitException(1L));
        HabitLogRequest req = new HabitLogRequest(LocalDate.parse("2026-08-02"),
                HabitLogSource.BACKFILL, null);
        mockMvc.perform(post("/api/habits/1/logs").header("X-User-Id", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
    }
}