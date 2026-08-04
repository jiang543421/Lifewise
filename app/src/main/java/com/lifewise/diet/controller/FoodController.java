package com.lifewise.diet.controller;

import com.lifewise.diet.dto.DietMessageResponse;
import com.lifewise.diet.dto.FoodCreateRequest;
import com.lifewise.diet.dto.FoodView;
import com.lifewise.diet.service.FoodService;
import com.lifewise.diet.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
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

/** 食物库 REST 端点 (plan-04-diet section 4.1 /foods). */
@RestController
@RequestMapping("/api/diet/foods")
public class FoodController {

    private final FoodService service;

    public FoodController(FoodService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FoodView>> create(@CurrentUser Long userId,
                                                          @Valid @RequestBody FoodCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(userId, req)));
    }

    @GetMapping
    public ApiResponse<List<FoodView>> list(@CurrentUser Long userId,
                                            @RequestParam(required = false) String q,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(service.list(userId, q, category, page, limit));
    }

    @GetMapping("/search")
    public ApiResponse<List<FoodView>> search(@CurrentUser Long userId,
                                              @RequestParam(name = "q", required = false) String q) {
        return ApiResponse.ok(service.search(userId, q));
    }

    @GetMapping("/{foodId}")
    public ApiResponse<FoodView> get(@CurrentUser Long userId, @PathVariable Long foodId) {
        return ApiResponse.ok(service.get(userId, foodId));
    }

    @PutMapping("/{foodId}")
    public ApiResponse<FoodView> update(@CurrentUser Long userId,
                                        @PathVariable Long foodId,
                                        @Valid @RequestBody FoodCreateRequest req) {
        return ApiResponse.ok(service.update(userId, foodId, req));
    }

    @DeleteMapping("/{foodId}")
    public ApiResponse<DietMessageResponse> delete(@CurrentUser Long userId, @PathVariable Long foodId) {
        service.delete(userId, foodId);
        return ApiResponse.ok(DietMessageResponse.ok());
    }
}