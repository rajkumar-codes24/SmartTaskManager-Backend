package com.smarttaskmanager.category.dto;

import jakarta.validation.constraints.Size;

/**
 * Request payload for updating an existing category.
 * All fields are optional; only the provided fields will be updated.
 */
public record UpdateCategoryRequest(
        @Size(max = 50, message = "Category name must not exceed 50 characters")
        String name,

        @Size(max = 255, message = "Description must not exceed 255 characters")
        String description,

        @Size(max = 7, message = "Color code must not exceed 7 characters")
        String color
) {}
