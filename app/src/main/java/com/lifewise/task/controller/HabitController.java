package com.lifewise.task.controller;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.task.dto.HabitCreateRequest;
import com.lifewise.task.dto.HabitLogRequest;
import com.lifewise.task.dto.HabitLogView;
import com.lifewise.task.dto.HabitView;
import com.lifewise.task.dto.TaskMessageResponse;
import com.lifewise.task.service.HabitService;
import com.lifewise.task.web.CurrentUser;
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
import org.springframework.web.bind.annotation.RestController;

/** Habit 5 端点：list/create/update/delete/log（plan-01-task §2.2）。 */
@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping
    public ApiResponse<List<HabitView>> list(@CurrentUser Long userId) {
        return ApiResponse.ok(habitService.list(userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<HabitView>> create(
            @CurrentUser Long userId, @Valid @RequestBody HabitCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(habitService.create(userId, req)));
    }

    @PutMapping("/{id}")
    public ApiResponse<HabitView> update(
            @CurrentUser Long userId, @PathVariable long id, @Valid @RequestBody HabitCreateRequest req) {
        return ApiResponse.ok(habitService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TaskMessageResponse> delete(@CurrentUser Long userId, @PathVariable long id) {
        habitService.softDelete(userId, id);
        return ApiResponse.ok(TaskMessageResponse.ok());
    }

    @PostMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<HabitLogView>> log(
            @CurrentUser Long userId, @PathVariable long id, @Valid @RequestBody HabitLogRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(habitService.log(userId, id, req)));
    }
}
