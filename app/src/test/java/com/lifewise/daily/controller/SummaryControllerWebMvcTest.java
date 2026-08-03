package com.lifewise.daily.controller;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifewise.daily.config.WebMvcConfig;
import com.lifewise.daily.domain.SummaryKind;
import com.lifewise.daily.dto.AiSummaryView;
import com.lifewise.daily.service.SummaryService;
import com.lifewise.daily.service.exception.AiSummaryNotFoundException;
import com.lifewise.daily.service.exception.DailyReportNotFoundException;
import com.lifewise.daily.web.CurrentUserArgumentResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** SummaryController 2 端点契约 + 异常映射（plan-02-daily §2.4）。 */
@WebMvcTest(controllers = SummaryController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DailyGlobalExceptionHandler.class})
class SummaryControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockBean SummaryService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void trigger_returns_202() throws Exception {
        AiSummaryView view = new AiSummaryView(50L, 11L, LocalDate.of(2026, 8, 2),
                SummaryKind.DAILY, "summary text", "m", "v", "p", null,
                OffsetDateTime.now(), false);
        when(service.trigger(anyLong(), anyLong())).thenReturn(view);

        mockMvc.perform(post("/api/daily-reports/11/summary").header(HEADER, "1"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.id").value(50));
    }

    @Test
    void trigger_report_not_found_returns_404() throws Exception {
        when(service.trigger(anyLong(), anyLong()))
                .thenThrow(new DailyReportNotFoundException(11L));

        mockMvc.perform(post("/api/daily-reports/11/summary").header(HEADER, "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_returns_view() throws Exception {
        AiSummaryView view = new AiSummaryView(50L, 11L, LocalDate.of(2026, 8, 2),
                SummaryKind.DAILY, "summary", "m", "v", "p", 100,
                OffsetDateTime.now(), false);
        when(service.get(1L, 11L)).thenReturn(view);

        mockMvc.perform(get("/api/daily-reports/11/summary").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summaryText").value("summary"))
                .andExpect(jsonPath("$.data.tokensUsed").value(100));
    }

    @Test
    void get_summary_not_found_returns_404() throws Exception {
        when(service.get(anyLong(), anyLong()))
                .thenThrow(new AiSummaryNotFoundException(11L));

        mockMvc.perform(get("/api/daily-reports/11/summary").header(HEADER, "1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalid_user_header_returns_401() throws Exception {
        // v1.0 白名单：非 userId=1 一律 401（CLAUDE.md §7.3.1）。
        mockMvc.perform(post("/api/daily-reports/11/summary").header(HEADER, "2"))
                .andExpect(status().isUnauthorized());
    }
}