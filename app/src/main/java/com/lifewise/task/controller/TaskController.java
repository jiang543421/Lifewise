package com.lifewise.task.controller;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.shared.integration.dto.PageMeta;
import com.lifewise.task.domain.TaskPriority;
import com.lifewise.task.domain.TaskStatus;
import com.lifewise.task.dto.TaskCreateRequest;
import com.lifewise.task.dto.TaskListItem;
import com.lifewise.task.dto.TaskMessageResponse;
import com.lifewise.task.dto.TaskUpdateRequest;
import com.lifewise.task.dto.TaskView;
import com.lifewise.task.service.TaskQueryService;
import com.lifewise.task.service.TaskService;
import com.lifewise.task.web.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

/** Task 7 端点：list/get/create/update/delete/complete/reopen（plan-01-task §2.1）。 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskQueryService taskQueryService;

    public TaskController(TaskService taskService, TaskQueryService taskQueryService) {
        this.taskService = taskService;
        this.taskQueryService = taskQueryService;
    }

    @GetMapping
    public ApiResponse<java.util.List<TaskListItem>> list(
            @CurrentUser Long userId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(name = "tag_id", required = false) Long tagId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        int p = Math.max(page, 1);
        int l = Math.max(Math.min(limit, 100), 1);
        Pageable pageable = PageRequest.of(p - 1, l);
        Page<TaskListItem> result = taskQueryService.list(userId, status, priority, tagId, pageable);
        PageMeta meta = new PageMeta(result.getTotalElements(), p, l, result.hasNext());
        return ApiResponse.paged(result.getContent(), meta);
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskView> get(@CurrentUser Long userId, @PathVariable long id) {
        return ApiResponse.ok(TaskView.from(taskService.getOwned(userId, id)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskView>> create(
            @CurrentUser Long userId, @Valid @RequestBody TaskCreateRequest req) {
        TaskView view = taskService.create(userId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(view));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskView> update(
            @CurrentUser Long userId, @PathVariable long id, @Valid @RequestBody TaskUpdateRequest req) {
        return ApiResponse.ok(taskService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TaskMessageResponse> delete(@CurrentUser Long userId, @PathVariable long id) {
        taskService.softDelete(userId, id);
        return ApiResponse.ok(TaskMessageResponse.ok());
    }

    @PostMapping("/{id}/complete")
    public ApiResponse<TaskView> complete(@CurrentUser Long userId, @PathVariable long id) {
        return ApiResponse.ok(taskService.complete(userId, id));
    }

    @PostMapping("/{id}/reopen")
    public ApiResponse<TaskView> reopen(@CurrentUser Long userId, @PathVariable long id) {
        return ApiResponse.ok(taskService.reopen(userId, id));
    }
}
