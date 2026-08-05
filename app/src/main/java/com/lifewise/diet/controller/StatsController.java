package com.lifewise.diet.controller;

import com.lifewise.diet.dto.StatsView;
import com.lifewise.diet.service.StatsService;
import com.lifewise.diet.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Nutrition stats REST endpoints (plan-04-diet section 4.1 /stats).
 *
 * <p>Explicit bean name: {@code com.lifewise.expense.controller.StatsController} shares this
 * class name, so both would default to {@code statsController} and clash, breaking the whole
 * Spring context. Follows the module-prefix convention already used by each module's
 * {@code CurrentUserArgumentResolver}.
 */
@RestController("dietStatsController")
@RequestMapping("/api/diet/stats")
public class StatsController {

    private final StatsService service;

    public StatsController(StatsService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<StatsView> stats(@CurrentUser Long userId,
                                        @RequestParam LocalDate from,
                                        @RequestParam LocalDate to,
                                        @RequestParam(defaultValue = "day") String granularity) {
        return ApiResponse.ok(service.stats(userId, from, to, granularity));
    }

    @GetMapping("/weekly")
    public ApiResponse<List<StatsView.WeeklyBucket>> weekly(@CurrentUser Long userId,
                                                            @RequestParam LocalDate weekStart) {
        return ApiResponse.ok(service.weekly(userId, weekStart));
    }
}