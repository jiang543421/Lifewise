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
import com.lifewise.plan.domain.MilestoneStatus;
import com.lifewise.plan.dto.MilestoneRequest;
import com.lifewise.plan.dto.MilestoneView;
import com.lifewise.plan.service.MilestoneService;
import com.lifewise.plan.service.PlanService;
import com.lifewise.plan.web.CurrentUserArgumentResolver;
import com.lifewise.plan.web.PlanGlobalExceptionHandler;
import com.lifewise.plan.web.PlanWebMvcConfig;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** MilestoneController WebMvc 测试（plan-05-plan §3 - 7 个端点）。 */
@WebMvcTest(controllers = MilestoneController.class)
@Import({PlanGlobalExceptionHandler.class, CurrentUserArgumentResolver.class})
@ContextConfiguration(classes = {MilestoneController.class, PlanWebMvcConfig.class})
class MilestoneControllerWebMvcTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @MockBean MilestoneService milestoneService;
    @MockBean PlanService planService;

    private static MilestoneRequest req() {
        return new MilestoneRequest("完成第一章", "背诵500词",
                LocalDate.of(2026, 2, 1).atStartOfDay().atOffset(ZoneOffset.UTC),
                "Asia/Shanghai", 1);
    }

    private static MilestoneView sampleView(long id, MilestoneStatus status) {
        OffsetDateTime due = LocalDate.of(2026, 2, 1)
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        return new MilestoneView(id, 1L, 1L, "完成第一章", "背诵500词",
                status, due, "Asia/Shanghai", 1,
                status == MilestoneStatus.DONE
                        ? LocalDate.of(2026, 2, 5).atStartOfDay().atOffset(ZoneOffset.UTC)
                        : null);
    }

    @Test
    void POST_creates_milestone() throws Exception {
        when(milestoneService.create(eq(1L), eq(1L), any()))
            .thenReturn(sampleView(50L, MilestoneStatus.PENDING));

        mvc.perform(post("/api/plans/1/milestones")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void GET_list_returns_milestones() throws Exception {
        when(milestoneService.list(eq(1L), eq(1L)))
            .thenReturn(List.of(sampleView(50L, MilestoneStatus.PENDING)));

        mvc.perform(get("/api/plans/1/milestones").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].id").value(50));
    }

    @Test
    void PUT_updates_milestone() throws Exception {
        when(milestoneService.update(eq(1L), eq(1L), eq(50L), any()))
            .thenReturn(sampleView(50L, MilestoneStatus.PENDING));

        mvc.perform(put("/api/plans/1/milestones/50")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(req())))
            .andExpect(status().isOk());
    }

    @Test
    void POST_complete_marks_done() throws Exception {
        when(milestoneService.complete(eq(1L), eq(1L), eq(50L)))
            .thenReturn(sampleView(50L, MilestoneStatus.DONE));

        mvc.perform(post("/api/plans/1/milestones/50/complete").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("DONE"));
    }

    @Test
    void POST_reopen_returns_pending() throws Exception {
        when(milestoneService.reopen(eq(1L), eq(1L), eq(50L)))
            .thenReturn(sampleView(50L, MilestoneStatus.PENDING));

        mvc.perform(post("/api/plans/1/milestones/50/reopen").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void DELETE_soft_deletes_milestone() throws Exception {
        mvc.perform(delete("/api/plans/1/milestones/50").header("X-User-Id", "1"))
            .andExpect(status().isOk());

        verify(milestoneService, times(1)).softDelete(1L, 1L, 50L);
    }
}