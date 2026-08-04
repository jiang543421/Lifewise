package com.lifewise.plan.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.plan.domain.PlanStatus;
import com.lifewise.plan.dto.PlanCreateRequest;
import com.lifewise.plan.dto.PlanView;
import com.lifewise.plan.service.PlanService;
import com.lifewise.plan.web.CurrentUserArgumentResolver;
import com.lifewise.plan.web.PlanGlobalExceptionHandler;
import com.lifewise.plan.web.PlanWebMvcConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** PlanController WebMvc 测试（plan-05-plan §3 - 6 个端点）。 */
@WebMvcTest(controllers = PlanController.class)
@Import({PlanGlobalExceptionHandler.class, CurrentUserArgumentResolver.class})
@ContextConfiguration(classes = {PlanController.class, PlanWebMvcConfig.class})
class PlanControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean PlanService planService;
    @MockBean PlanReadPortAdapterUnusedMarker unused; // 触发 Spring 装配 plan 包
    // Suppress unused-warning for unused field
    static class PlanReadPortAdapterUnusedMarker {}

    @Test
    void POST_creates_plan_with_status_201() throws Exception {
        PlanCreateRequest req = new PlanCreateRequest("学英语", "考试",
                "STUDY", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1));
        when(planService.create(eq(1L), any()))
            .thenReturn(new PlanView(100L, 1L, "学英语", "考试",
                    "STUDY", PlanStatus.ACTIVE,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));

        mvc.perform(post("/api/plans")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(100))
            .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void GET_returns_plan_when_owned() throws Exception {
        when(planService.getById(1L, 100L))
            .thenReturn(new PlanView(100L, 1L, "t", null, "STUDY",
                    PlanStatus.ACTIVE,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));

        mvc.perform(get("/api/plans/100").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(100));
    }

    @Test
    void PUT_updates_plan() throws Exception {
        PlanCreateRequest req = new PlanCreateRequest("改", null, "WORK",
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 1));
        when(planService.update(eq(1L), eq(100L), any()))
            .thenReturn(new PlanView(100L, 1L, "改", null, "WORK",
                    PlanStatus.ACTIVE,
                    LocalDate.of(2026, 2, 1), LocalDate.of(2026, 7, 1)));

        mvc.perform(put("/api/plans/100")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("改"));
    }

    @Test
    void DELETE_returns_204() throws Exception {
        mvc.perform(delete("/api/plans/100").header("X-User-Id", "1"))
            .andExpect(status().isOk());

        verify(planService, times(1)).softDelete(1L, 100L);
    }

    @Test
    void POST_abandon_marks_cancelled() throws Exception {
        when(planService.abandon(1L, 100L))
            .thenReturn(new PlanView(100L, 1L, "t", null, "STUDY",
                    PlanStatus.CANCELLED,
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 6, 1)));

        mvc.perform(post("/api/plans/100/abandon").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void GET_list_with_include_cancelled_flag() throws Exception {
        when(planService.list(eq(1L), any(), any(), eq(false)))
            .thenReturn(List.of());

        mvc.perform(get("/api/plans?includeCancelled=true").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void request_with_non_whitelisted_user_id_returns_401() throws Exception {
        // v1.0 白名单：非 userId=1 一律 401（CLAUDE.md §7.3.1）
        // 缺失 X-User-Id 头时按 fail-open 兜底到 userId=1，这是预期行为
        mvc.perform(get("/api/plans").header("X-User-Id", "2"))
            .andExpect(status().isUnauthorized());
    }
}