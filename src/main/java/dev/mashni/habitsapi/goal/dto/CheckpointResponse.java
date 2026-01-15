package dev.mashni.habitsapi.goal.dto;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for goal checkpoints.
 * Contains checkpoint information for display in timeline/diary views.
 */
public record CheckpointResponse(
    UUID id,
    String note,
    String emoji,
    LocalDate date
) {
}
