package com.smarttaskmanager.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating a new category.
 */
public record CreateCategoryRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 50, message = "Category name must not exceed 50 characters")
        String name,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @Size(max = 7, message = "Color code must not exceed 7 characters")
        String color
) {}
