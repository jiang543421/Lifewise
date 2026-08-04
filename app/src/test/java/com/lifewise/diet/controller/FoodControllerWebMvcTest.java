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
import com.lifewise.diet.dto.FoodCreateRequest;
import com.lifewise.diet.dto.FoodView;
import com.lifewise.diet.service.FoodService;
import com.lifewise.diet.service.exception.FoodNotFoundException;
import com.lifewise.diet.service.exception.FoodSystemReadOnlyException;
import com.lifewise.diet.web.CurrentUserArgumentResolver;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** FoodController 6 endpoints contract (plan-04-diet section 2.2). */
@WebMvcTest(controllers = FoodController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DietGlobalExceptionHandler.class})
class FoodControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean FoodService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void list_returns_envelope() throws Exception {
        when(service.list(anyLong(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        mockMvc.perform(get("/api/diet/foods").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void search_returns_matches() throws Exception {
        when(service.search(anyLong(), eq("tomato"))).thenReturn(List.of());
        mockMvc.perform(get("/api/diet/foods/search?q=tomato").header(HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void create_returns_201() throws Exception {
        when(service.create(anyLong(), any(FoodCreateRequest.class)))
                .thenReturn(new FoodView(1L, "Apple", List.of("苹果"), "fruit",
                        new BigDecimal("52.00"), new BigDecimal("0.30"),
                        new BigDecimal("0.20"), new BigDecimal("14.00")));
        FoodCreateRequest req = new FoodCreateRequest("Apple", List.of("苹果"), "fruit",
                new BigDecimal("52.00"), new BigDecimal("0.30"),
                new BigDecimal("0.20"), new BigDecimal("14.00"));
        mockMvc.perform(post("/api/diet/foods").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("Apple"));
    }

    @Test
    void get_returns_view() throws Exception {
        when(service.get(anyLong(), eq(1L))).thenReturn(
                new FoodView(1L, "Apple", List.of(), "fruit",
                        new BigDecimal("52.00"), new BigDecimal("0.30"),
                        new BigDecimal("0.20"), new BigDecimal("14.00")));
        mockMvc.perform(get("/api/diet/foods/1").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1));
    }

    @Test
    void update_returns_view() throws Exception {
        when(service.update(anyLong(), eq(1L), any(FoodCreateRequest.class)))
                .thenReturn(new FoodView(1L, "Apple2", List.of(), "fruit",
                        new BigDecimal("55.00"), new BigDecimal("0.30"),
                        new BigDecimal("0.20"), new BigDecimal("14.00")));
        FoodCreateRequest req = new FoodCreateRequest("Apple2", List.of(), "fruit",
                new BigDecimal("55.00"), new BigDecimal("0.30"),
                new BigDecimal("0.20"), new BigDecimal("14.00"));
        mockMvc.perform(put("/api/diet/foods/1").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    void delete_returns_200() throws Exception {
        mockMvc.perform(delete("/api/diet/foods/1").header(HEADER, "1"))
                .andExpect(status().isOk());
    }

    @Test
    void not_found_returns_404() throws Exception {
        when(service.get(anyLong(), eq(999L)))
                .thenThrow(new FoodNotFoundException(999L));
        mockMvc.perform(get("/api/diet/foods/999").header(HEADER, "1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("FOOD_NOT_FOUND"));
    }

    @Test
    void system_food_update_returns_403() throws Exception {
        when(service.update(anyLong(), eq(1L), any(FoodCreateRequest.class)))
                .thenThrow(new FoodSystemReadOnlyException(1L));
        FoodCreateRequest req = new FoodCreateRequest("X", List.of(), "fruit",
                new BigDecimal("0.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), new BigDecimal("0.00"));
        mockMvc.perform(put("/api/diet/foods/1").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalid_user_header_returns_401() throws Exception {
        mockMvc.perform(get("/api/diet/foods").header(HEADER, "2"))
                .andExpect(status().isUnauthorized());
    }

    // anyInt helper
    private static int anyInt() {
        return org.mockito.ArgumentMatchers.anyInt();
    }
}