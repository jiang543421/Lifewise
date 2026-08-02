package com.lifewise.task.controller;

import com.lifewise.shared.integration.dto.ApiResponse;
import com.lifewise.task.dto.CreateTagRequest;
import com.lifewise.task.dto.TaskMessageResponse;
import com.lifewise.task.dto.TaskTagView;
import com.lifewise.task.dto.UpdateTagRequest;
import com.lifewise.task.service.TagService;
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

/** Tag 4 端点：list/create/rename/delete（plan-01-task §2.3）。 */
@RestController
@RequestMapping("/api/task-tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ApiResponse<List<TaskTagView>> list(@CurrentUser Long userId) {
        return ApiResponse.ok(tagService.list(userId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<TaskTagView>> create(
            @CurrentUser Long userId, @Valid @RequestBody CreateTagRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(tagService.create(userId, req.name(), req.color())));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskTagView> rename(
            @CurrentUser Long userId,
            @PathVariable long id,
            @Valid @RequestBody UpdateTagRequest req) {
        return ApiResponse.ok(tagService.rename(userId, id, req.name(), req.color()));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<TaskMessageResponse> delete(@CurrentUser Long userId, @PathVariable long id) {
        tagService.softDelete(userId, id);
        return ApiResponse.ok(TaskMessageResponse.ok());
    }
}
