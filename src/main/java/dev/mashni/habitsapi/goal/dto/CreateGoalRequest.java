package dev.mashni.habitsapi.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for creating a new goal.
 * Optionally includes a list of habit IDs to link to the goal upon creation.
 */
public record CreateGoalRequest(
    @NotBlank(message = "Goal title is required")
    @Size(min = 1, max = 200, message = "Title must be between 1 and 200 characters")
    String title,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    LocalDateTime deadline,

    Set<UUID> habitIds  // Optional list of habit IDs to link
) {
}
