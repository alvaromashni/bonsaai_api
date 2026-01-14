package dev.mashni.habitsapi.goal.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;
import java.util.UUID;

/**
 * Request DTO for updating the habits linked to a goal.
 * Replaces the entire list of habits with the provided IDs.
 */
public record UpdateGoalHabitsRequest(
    @NotNull(message = "Habit IDs are required")
    Set<UUID> habitIds
) {
}
