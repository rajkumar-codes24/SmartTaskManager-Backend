package com.smarttaskmanager.category.dto;

/**
 * Response DTO for a category.
 */
public record CategoryResponse(
        Long id,
        String name,
        String description,
        String color
) {}
