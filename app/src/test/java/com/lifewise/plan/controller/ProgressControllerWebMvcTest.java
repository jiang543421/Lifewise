package com.lifewise.plan.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifewise.plan.dto.ProgressView;
import com.lifewise.plan.service.ProgressEvaluator;
import com.lifewise.plan.web.CurrentUserArgumentResolver;
import com.lifewise.plan.web.PlanGlobalExceptionHandler;
import com.lifewise.plan.web.PlanWebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

/** ProgressController WebMvc 测试（plan-05-plan §3 - 1 个端点）。 */
@WebMvcTest(controllers = ProgressController.class)
@Import({PlanGlobalExceptionHandler.class, CurrentUserArgumentResolver.class})
@ContextConfiguration(classes = {ProgressController.class, PlanWebMvcConfig.class})
class ProgressControllerWebMvcTest {

    @Autowired MockMvc mvc;

    @MockBean ProgressEvaluator progressEvaluator;

    @Test
    void GET_progress_returns_view() throws Exception {
        when(progressEvaluator.compute(1L, 100L))
            .thenReturn(new ProgressView(100L, 5, 10, 12, 20, 0.5, java.util.List.of()));

        mvc.perform(get("/api/plans/100/progress").header("X-User-Id", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ratio").value(0.5))
            .andExpect(jsonPath("$.data.completed_milestones").value(5));
    }

    @Test
    void GET_progress_with_non_whitelisted_user_returns_401() throws Exception {
        // v1.0 白名单：非 userId=1 一律 401（CLAUDE.md §7.3.1）
        mvc.perform(get("/api/plans/100/progress").header("X-User-Id", "2"))
            .andExpect(status().isUnauthorized());
    }
}