package dev.mashni.habitsapi.goal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Request DTO for creating a new goal checkpoint.
 * Allows users to register progress milestones with date, note, and optional emoji.
 */
public record CreateCheckpointRequest(
    @NotBlank(message = "Note is required")
    @Size(max = 500, message = "Note must not exceed 500 characters")
    String note,

    @Size(max = 10, message = "Emoji must not exceed 10 characters")
    String emoji,

    @PastOrPresent(message = "Date cannot be in the future")
    LocalDate date  // Optional, defaults to today if null
) {
}
