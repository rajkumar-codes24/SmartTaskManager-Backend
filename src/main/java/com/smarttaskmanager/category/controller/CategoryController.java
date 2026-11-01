package com.smarttaskmanager.category.controller;

import com.smarttaskmanager.category.dto.CategoryResponse;
import com.smarttaskmanager.category.dto.CreateCategoryRequest;
import com.smarttaskmanager.category.dto.UpdateCategoryRequest;
import com.smarttaskmanager.category.service.CategoryService;
import com.smarttaskmanager.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.util.List;

/**
 * REST controller for managing user-specific categories.
 *
 * <p>All endpoints are protected and scoped to the authenticated user.
 * Users can only access their own categories.</p>
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Category Management", description = "CRUD operations for user categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

    private final CategoryService categoryService;

    @PostMapping
    @Operation(summary = "Create a category", description = "Creates a new category for the authenticated user. Category names must be unique per user (case-insensitive).")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully.", response));
    }

    @GetMapping
    @Operation(summary = "List user categories", description = "Returns all categories belonging to the authenticated user, ordered by name.")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getUserCategories() {
        List<CategoryResponse> categories = categoryService.getUserCategories();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully.", categories));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get category by ID", description = "Returns a single category by ID if it belongs to the authenticated user.")
    public ResponseEntity<ApiResponse<CategoryResponse>> getCategoryById(@PathVariable Long id) {
        CategoryResponse response = categoryService.getCategoryById(id);
        return ResponseEntity.ok(ApiResponse.success("Category retrieved successfully.", response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category", description = "Updates an existing category belonging to the authenticated user. Only provided fields will be updated.")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully.", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a category", description = "Deletes a category belonging to the authenticated user. Tasks associated with the category will have their category reference set to NULL.")
    public ResponseEntity<ApiResponse<Void>> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deleted successfully."));
    }
}
