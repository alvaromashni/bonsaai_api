package dev.mashni.habitsapi.dto;

import dev.mashni.habitsapi.model.GoalStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Summary response DTO for goals (used in list views).
 * Contains basic goal information without detailed habit data.
 */
public record GoalSummaryResponse(
    UUID id,
    String title,
    String description,
    LocalDateTime deadline,
    GoalStatus status,
    int habitCount,
    LocalDateTime createdAt
) {
}
