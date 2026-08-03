package com.lifewise.daily.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.daily.config.WebMvcConfig;
import com.lifewise.daily.domain.Mood;
import com.lifewise.daily.dto.DailyReportCreateRequest;
import com.lifewise.daily.dto.DailyReportListItem;
import com.lifewise.daily.dto.DailyReportUpdateRequest;
import com.lifewise.daily.dto.DailyReportView;
import com.lifewise.daily.dto.DailyMessageResponse;
import com.lifewise.daily.service.DailyReportService;
import com.lifewise.daily.service.exception.ContentTooLongException;
import com.lifewise.daily.service.exception.DailyReportNotFoundException;
import com.lifewise.daily.service.exception.DuplicateDailyReportException;
import com.lifewise.daily.web.CurrentUserArgumentResolver;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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

/** DailyReportController 6 端点契约 + 异常映射覆盖（plan-02-daily §5）。 */
@WebMvcTest(controllers = DailyReportController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DailyGlobalExceptionHandler.class})
class DailyReportControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean DailyReportService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void list_returns_paged_envelope() throws Exception {
        DailyReportListItem item = new DailyReportListItem(1L, LocalDate.of(2026, 8, 2),
                "t", Mood.GOOD, 4, true, OffsetDateTime.now());
        Page<DailyReportListItem> page = new PageImpl<>(List.of(item));
        when(service.list(anyLong(), any(), any(), eq(false), any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/daily-reports").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.meta.total").value(1));
    }

    @Test
    void get_by_date_returns_view() throws Exception {
        DailyReportView view = new DailyReportView(11L, LocalDate.of(2026, 8, 2), "UTC",
                "t", "c", Mood.GOOD, 4, true, List.of(), null, OffsetDateTime.now(),
                OffsetDateTime.now());
        when(service.getByDate(eq(7L), eq(LocalDate.of(2026, 8, 2)))).thenReturn(view);

        mockMvc.perform(get("/api/daily-reports/by-date/2026-08-02").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void get_by_date_not_found_returns_404() throws Exception {
        when(service.getByDate(eq(7L), eq(LocalDate.of(2026, 8, 2))))
                .thenThrow(new DailyReportNotFoundException(7L, LocalDate.of(2026, 8, 2)));

        mockMvc.perform(get("/api/daily-reports/by-date/2026-08-02").header(HEADER, "7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DAILY_REPORT_NOT_FOUND"));
    }

    @Test
    void get_returns_view() throws Exception {
        DailyReportView view = new DailyReportView(11L, LocalDate.of(2026, 8, 2), "UTC",
                "t", "c", null, null, true, List.of(), null, OffsetDateTime.now(),
                OffsetDateTime.now());
        when(service.getOwned(7L, 11L)).thenReturn(view);

        mockMvc.perform(get("/api/daily-reports/11").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void create_returns_201() throws Exception {
        DailyReportView view = new DailyReportView(101L, LocalDate.of(2026, 8, 2), "UTC",
                "t", "c", null, null, true, List.of(), null, OffsetDateTime.now(),
                OffsetDateTime.now());
        when(service.create(anyLong(), any(DailyReportCreateRequest.class))).thenReturn(view);
        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), "UTC", "t", "c", null, null);

        mockMvc.perform(post("/api/daily-reports").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    void create_duplicate_returns_409() throws Exception {
        when(service.create(anyLong(), any(DailyReportCreateRequest.class)))
                .thenThrow(new DuplicateDailyReportException(7L, LocalDate.of(2026, 8, 2)));
        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), null, "t", null, null, null);

        mockMvc.perform(post("/api/daily-reports").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("VERSION_CONFLICT"));
    }

    @Test
    void create_content_too_long_returns_400() throws Exception {
        when(service.create(anyLong(), any(DailyReportCreateRequest.class)))
                .thenThrow(new ContentTooLongException(50001));
        DailyReportCreateRequest req = new DailyReportCreateRequest(
                LocalDate.of(2026, 8, 2), null, "t", "x", null, null);

        mockMvc.perform(post("/api/daily-reports").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }

    @Test
    void update_returns_200() throws Exception {
        DailyReportView view = new DailyReportView(11L, LocalDate.of(2026, 8, 2), "UTC",
                "new", "c", null, null, false, List.of(), null, OffsetDateTime.now(),
                OffsetDateTime.now());
        when(service.update(anyLong(), anyLong(), any(DailyReportUpdateRequest.class)))
                .thenReturn(view);
        DailyReportUpdateRequest req = new DailyReportUpdateRequest("new", null, null, null, true);

        mockMvc.perform(put("/api/daily-reports/11").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("new"));
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/daily-reports/11").header(HEADER, "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.message").value("ok"));
    }

    @Test
    void missing_user_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/daily-reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_validation_error_returns_400_with_field_details() throws Exception {
        // title 为空白（@NotBlank），触发 MethodArgumentNotValidException
        String invalid = "{\"reportDate\":\"2026-08-02\",\"title\":\"\",\"content\":\"c\"}";
        mockMvc.perform(post("/api/daily-reports").header(HEADER, "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.error.details.errors").isArray());
    }
}