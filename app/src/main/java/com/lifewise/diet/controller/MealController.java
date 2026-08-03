package com.lifewise.diet.controller;

import com.lifewise.diet.domain.MealType;
import com.lifewise.diet.dto.DietMessageResponse;
import com.lifewise.diet.dto.MealCreateRequest;
import com.lifewise.diet.dto.MealListItem;
import com.lifewise.diet.dto.MealView;
import com.lifewise.diet.service.MealService;
import com.lifewise.diet.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Meal REST endpoints (plan-04-diet section 4.1 /meals). */
@RestController
@RequestMapping("/api/diet/meals")
public class MealController {

    private final MealService service;

    public MealController(MealService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<MealView>> create(@CurrentUser Long userId,
                                                          @Valid @RequestBody MealCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(userId, req)));
    }

    @GetMapping("/{mealId}")
    public ApiResponse<MealView> get(@CurrentUser Long userId, @PathVariable Long mealId) {
        return ApiResponse.ok(service.getOwned(userId, mealId));
    }

    @GetMapping
    public ApiResponse<List<MealListItem>> list(@CurrentUser Long userId,
                                                @RequestParam(required = false) LocalDate from,
                                                @RequestParam(required = false) LocalDate to,
                                                @RequestParam(required = false) MealType type,
                                                @RequestParam(defaultValue = "1") int page,
                                                @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.list(userId, from, to, type, PageRequest.of(
                Math.max(page, 1) - 1, Math.max(Math.min(limit, 100), 1))));
    }

    @PutMapping("/{mealId}")
    public ApiResponse<MealView> update(@CurrentUser Long userId,
                                        @PathVariable Long mealId,
                                        @Valid @RequestBody MealCreateRequest req) {
        return ApiResponse.ok(service.update(userId, mealId, req));
    }

    @DeleteMapping("/{mealId}")
    public ApiResponse<DietMessageResponse> delete(@CurrentUser Long userId, @PathVariable Long mealId) {
        service.softDelete(userId, mealId);
        return ApiResponse.ok(DietMessageResponse.ok());
    }

    @PostMapping("/{mealId}/restore")
    public ApiResponse<MealView> restore(@CurrentUser Long userId, @PathVariable Long mealId) {
        return ApiResponse.ok(service.restore(userId, mealId));
    }
}