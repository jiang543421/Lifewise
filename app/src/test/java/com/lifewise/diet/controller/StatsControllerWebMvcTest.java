package com.lifewise.diet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lifewise.diet.config.WebMvcConfig;
import com.lifewise.diet.controller.exception.DietGlobalExceptionHandler;
import com.lifewise.diet.dto.StatsView;
import com.lifewise.diet.service.StatsService;
import com.lifewise.diet.web.CurrentUserArgumentResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/** StatsController 2 endpoints contract (plan-04-diet section 2.3). */
@WebMvcTest(controllers = StatsController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DietGlobalExceptionHandler.class})
class StatsControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @MockBean StatsService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void stats_returns_by_day() throws Exception {
        StatsView view = new StatsView(
                Map.of(LocalDate.of(2026, 8, 3), new BigDecimal("1800.00")),
                null, 2000);
        when(service.stats(anyLong(), any(), any(), any())).thenReturn(view);

        mockMvc.perform(get("/api/diet/stats?from=2026-08-01&to=2026-08-07&granularity=day")
                        .header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetKcal").value(2000));
    }

    @Test
    void weekly_returns_buckets() throws Exception {
        StatsView.WeeklyBucket bucket = new StatsView.WeeklyBucket(
                LocalDate.of(2026, 8, 3), "LUNCH", 5L,
                new BigDecimal("980.00"), new BigDecimal("42.50"),
                new BigDecimal("210.00"), new BigDecimal("6.50"));
        when(service.weekly(anyLong(), any())).thenReturn(List.of(bucket));

        mockMvc.perform(get("/api/diet/stats/weekly?weekStart=2026-08-01").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].mealType").value("LUNCH"));
    }
}