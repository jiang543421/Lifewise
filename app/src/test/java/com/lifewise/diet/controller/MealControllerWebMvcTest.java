package com.lifewise.diet.controller;

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
import com.lifewise.diet.config.WebMvcConfig;
import com.lifewise.diet.controller.exception.DietGlobalExceptionHandler;
import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.dto.MealCreateRequest;
import com.lifewise.diet.dto.MealItemRequest;
import com.lifewise.diet.dto.MealView;
import com.lifewise.diet.service.MealService;
import com.lifewise.diet.service.exception.MealNotFoundException;
import com.lifewise.diet.web.CurrentUserArgumentResolver;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** MealController 6 endpoints contract + exception mapping (plan-04-diet section 2.1). */
@WebMvcTest(controllers = MealController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DietGlobalExceptionHandler.class})
class MealControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean MealService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void list_returns_envelope() throws Exception {
        when(service.list(anyLong(), any(), any(), any(), any())).thenReturn(List.of());
        mockMvc.perform(get("/api/diet/meals").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void get_returns_view() throws Exception {
        when(service.getOwned(1L, 11L))
                .thenReturn(MealView.empty(11L, MealType.LUNCH, LocalDate.of(2026, 8, 3)));
        mockMvc.perform(get("/api/diet/meals/11").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(11));
    }

    @Test
    void get_not_found_returns_404() throws Exception {
        when(service.getOwned(1L, 99L))
                .thenThrow(new MealNotFoundException(99L));
        mockMvc.perform(get("/api/diet/meals/99").header(HEADER, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEAL_NOT_FOUND"));
    }

    @Test
    void create_returns_201() throws Exception {
        when(service.create(anyLong(), any(MealCreateRequest.class)))
                .thenReturn(MealView.empty(101L, MealType.LUNCH, LocalDate.of(2026, 8, 3)));
        MealCreateRequest req = new MealCreateRequest(
                MealType.LUNCH, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, new BigDecimal("100.00"), null)));

        mockMvc.perform(post("/api/diet/meals").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(101));
    }

    @Test
    void update_returns_200() throws Exception {
        when(service.update(anyLong(), eq(11L), any(MealCreateRequest.class)))
                .thenReturn(MealView.empty(11L, MealType.DINNER, LocalDate.of(2026, 8, 3)));
        MealCreateRequest req = new MealCreateRequest(
                MealType.DINNER, LocalDate.of(2026, 8, 3), "UTC", null,
                List.of(new MealItemRequest(11L, new BigDecimal("100.00"), null)));

        mockMvc.perform(put("/api/diet/meals/11").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/diet/meals/11").header(HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_user_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/diet/meals").header(HEADER, "2"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_validation_error_returns_400() throws Exception {
        // items empty triggers @NotEmpty
        String invalid = "{\"type\":\"LUNCH\",\"localDate\":\"2026-08-03\",\"timezone\":\"UTC\",\"items\":[]}";
        mockMvc.perform(post("/api/diet/meals").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}