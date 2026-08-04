package com.lifewise.diet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifewise.diet.config.WebMvcConfig;
import com.lifewise.diet.controller.exception.DietGlobalExceptionHandler;
import com.lifewise.diet.domain.ActivityLevel;
import com.lifewise.diet.domain.Gender;
import com.lifewise.diet.dto.ProfileRequest;
import com.lifewise.diet.dto.ProfileView;
import com.lifewise.diet.service.ProfileService;
import com.lifewise.diet.web.CurrentUserArgumentResolver;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** ProfileController 3 endpoints contract (plan-04-diet section 2.4). */
@WebMvcTest(controllers = ProfileController.class)
@Import({WebMvcConfig.class, CurrentUserArgumentResolver.class,
        DietGlobalExceptionHandler.class})
class ProfileControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean ProfileService service;

    private static final String HEADER = "X-User-Id";

    @Test
    void get_returns_empty_profile_when_not_set() throws Exception {
        when(service.get(1L)).thenReturn(ProfileView.empty(1L));
        mockMvc.perform(get("/api/diet/profile").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(1));
    }

    @Test
    void put_returns_updated_profile() throws Exception {
        when(service.upsert(anyLong(), any(ProfileRequest.class)))
                .thenReturn(new ProfileView(1L, new BigDecimal("175.0"), new BigDecimal("70.0"),
                        30, Gender.MALE, ActivityLevel.MODERATE, 2000));
        ProfileRequest req = new ProfileRequest(new BigDecimal("175.0"), new BigDecimal("70.0"),
                30, Gender.MALE, ActivityLevel.MODERATE, 2000);
        mockMvc.perform(put("/api/diet/profile").header(HEADER, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyKcalTarget").value(2000));
    }

    @Test
    void recompute_target_returns_profile() throws Exception {
        when(service.recomputeTarget(1L))
                .thenReturn(new ProfileView(1L, new BigDecimal("175.0"), new BigDecimal("70.0"),
                        30, Gender.MALE, ActivityLevel.ACTIVE, 2844));
        mockMvc.perform(post("/api/diet/profile/recompute").header(HEADER, "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyKcalTarget").value(2844));
    }
}