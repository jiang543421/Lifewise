package com.lifewise.daily.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifewise.daily.config.WebMvcConfig;
import com.lifewise.daily.dto.DailyReportSearchHit;
import com.lifewise.daily.service.SearchService;
import com.lifewise.daily.web.CurrentUserArgumentResolver;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;

/** SearchController 1 端点契约 + 空 query / 分页参数行为（plan-02-daily §2.3）。 */
@WebMvcTest(controllers = SearchController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DailyGlobalExceptionHandler.class})
class SearchControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockBean SearchService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void search_with_query_returns_paged_hits() throws Exception {
        DailyReportSearchHit hit = new DailyReportSearchHit(11L, LocalDate.of(2026, 8, 2),
                "<em>hello</em>", 0.9);
        Page<DailyReportSearchHit> page = new PageImpl<>(List.of(hit));
        when(service.search(anyLong(), anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/daily-reports/search?q=hello").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].reportId").value(11))
                .andExpect(jsonPath("$.data[0].reportDate").value("2026-08-02"))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void search_empty_query_returns_empty_page() throws Exception {
        Page<DailyReportSearchHit> page = Page.empty();
        when(service.search(anyLong(), anyString(), org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/daily-reports/search?q=%20%20%20").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.meta.total").value(0));
    }

    @Test
    void search_with_date_range_passes_from_to() throws Exception {
        Page<DailyReportSearchHit> page = Page.empty();
        when(service.search(anyLong(), anyString(),
                any(LocalDate.class), any(LocalDate.class), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/daily-reports/search")
                        .param("q", "hello")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")
                        .param("page", "2")
                        .param("limit", "5")
                        .header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.page").value(2));
    }

    @Test
    void search_missing_user_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/daily-reports/search?q=hello"))
                .andExpect(status().isUnauthorized());
    }
}