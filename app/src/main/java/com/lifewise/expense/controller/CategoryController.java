package com.lifewise.expense.controller;

import com.lifewise.expense.dto.CategoryCreateRequest;
import com.lifewise.expense.dto.CategoryUpdateRequest;
import com.lifewise.expense.dto.CategoryView;
import com.lifewise.expense.service.CategoryService;
import com.lifewise.expense.web.CurrentUser;
import com.lifewise.shared.integration.dto.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 分类 REST 端点（plan-03-expense §1.1 5 端点）。所有响应包装在 {@link ApiResponse} 中。 */
@RestController
@RequestMapping("/api/expense-categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<CategoryView>> list(@CurrentUser Long userId) {
        return ApiResponse.ok(categoryService.list(userId));
    }

    @GetMapping("/system")
    public ApiResponse<List<CategoryView>> listSystem() {
        return ApiResponse.ok(categoryService.listSystem());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CategoryView> create(@CurrentUser Long userId,
                                @Valid @RequestBody CategoryCreateRequest req) {
        return ApiResponse.ok(categoryService.create(userId, req));
    }

    @PutMapping("/{id}")
    public ApiResponse<CategoryView> update(@CurrentUser Long userId, @PathVariable Long id,
                                @Valid @RequestBody CategoryUpdateRequest req) {
        return ApiResponse.ok(categoryService.update(userId, id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(@CurrentUser Long userId, @PathVariable Long id) {
        categoryService.softDelete(userId, id);
    }
}