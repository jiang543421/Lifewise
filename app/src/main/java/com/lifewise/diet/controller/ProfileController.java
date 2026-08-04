package com.lifewise.diet.controller;

import com.lifewise.diet.dto.ProfileRequest;
import com.lifewise.diet.dto.ProfileView;
import com.lifewise.diet.service.ProfileService;
import com.lifewise.diet.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** User body profile REST endpoints (plan-04-diet section 4.1 /profile). */
@RestController
@RequestMapping("/api/diet/profile")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<ProfileView> get(@CurrentUser Long userId) {
        return ApiResponse.ok(service.get(userId));
    }

    @PutMapping
    public ApiResponse<ProfileView> upsert(@CurrentUser Long userId,
                                           @Valid @RequestBody ProfileRequest req) {
        return ApiResponse.ok(service.upsert(userId, req));
    }

    @PostMapping("/recompute")
    public ApiResponse<ProfileView> recompute(@CurrentUser Long userId) {
        return ApiResponse.ok(service.recomputeTarget(userId));
    }
}