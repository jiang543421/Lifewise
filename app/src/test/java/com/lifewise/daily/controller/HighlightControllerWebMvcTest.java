package com.lifewise.daily.controller;

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
import com.lifewise.daily.config.WebMvcConfig;
import com.lifewise.daily.domain.HighlightType;
import com.lifewise.daily.dto.HighlightRequest;
import com.lifewise.daily.dto.HighlightView;
import com.lifewise.daily.service.HighlightService;
import com.lifewise.daily.service.exception.HighlightLimitExceededException;
import com.lifewise.daily.service.exception.HighlightNotFoundException;
import com.lifewise.daily.service.exception.InvalidHighlightPositionException;
import com.lifewise.daily.web.CurrentUserArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** HighlightController 4 端点契约 + 异常映射（plan-02-daily §5）。 */
@WebMvcTest(controllers = HighlightController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DailyGlobalExceptionHandler.class})
class HighlightControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean HighlightService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void list_returns_highlights() throws Exception {
        HighlightView v = new HighlightView(99L, 11L, HighlightType.INSIGHT, "k", "d",
                null, null, 0);
        when(service.listByReport(7L, 11L)).thenReturn(List.of(v));

        mockMvc.perform(get("/api/daily-reports/11/highlights").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(99));
    }

    @Test
    void create_returns_201() throws Exception {
        HighlightView v = new HighlightView(99L, 11L, HighlightType.INSIGHT, "k", "d",
                null, null, 0);
        when(service.create(anyLong(), anyLong(), any(HighlightRequest.class))).thenReturn(v);
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, 0);

        mockMvc.perform(post("/api/daily-reports/11/highlights").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(99));
    }

    @Test
    void create_limit_exceeded_returns_409() throws Exception {
        when(service.create(anyLong(), anyLong(), any(HighlightRequest.class)))
                .thenThrow(new HighlightLimitExceededException(11L));
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, null);

        mockMvc.perform(post("/api/daily-reports/11/highlights").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @Test
    void create_invalid_position_returns_400() throws Exception {
        when(service.create(anyLong(), anyLong(), any(HighlightRequest.class)))
                .thenThrow(new InvalidHighlightPositionException(-1));
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, -1);

        mockMvc.perform(post("/api/daily-reports/11/highlights").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void update_returns_200() throws Exception {
        HighlightView v = new HighlightView(50L, 11L, HighlightType.HABIT, "x", "d",
                null, null, 1);
        when(service.update(anyLong(), anyLong(), anyLong(), any(HighlightRequest.class)))
                .thenReturn(v);
        HighlightRequest req = new HighlightRequest(HighlightType.HABIT, "x", null,
                null, null, 1);

        mockMvc.perform(put("/api/daily-reports/11/highlights/50").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("x"));
    }

    @Test
    void update_not_found_returns_404() throws Exception {
        when(service.update(anyLong(), anyLong(), anyLong(), any(HighlightRequest.class)))
                .thenThrow(new HighlightNotFoundException(50L));
        HighlightRequest req = new HighlightRequest(HighlightType.INSIGHT, "k", "d",
                null, null, 0);

        mockMvc.perform(put("/api/daily-reports/11/highlights/50").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/daily-reports/11/highlights/50").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("ok"));
    }
}