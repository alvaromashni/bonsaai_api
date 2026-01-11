package dev.mashni.habitsapi.dto;

import dev.mashni.habitsapi.model.GoalStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Detailed response DTO for goals (used in detail views).
 * Contains full goal information including linked habits.
 */
public record GoalDetailResponse(
    UUID id,
    String title,
    String description,
    LocalDateTime deadline,
    GoalStatus status,
    List<HabitSummaryResponse> habits,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
}
