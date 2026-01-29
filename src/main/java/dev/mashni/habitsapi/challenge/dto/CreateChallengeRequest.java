package dev.mashni.habitsapi.challenge.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateChallengeRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 200, message = "Name must be between 1 and 200 characters")
    String name,

    @Size(max = 2000, message = "Description must not exceed 2000 characters")
    String description,

    @NotBlank(message = "Habit name is required")
    @Size(min = 1, max = 100, message = "Habit name must be between 1 and 100 characters")
    String habitName,

    @NotNull(message = "Start date is required")
    @FutureOrPresent(message = "Start date must be today or in the future")
    LocalDate startDate,

    LocalDate endDate
) {
    /**
     * Validates that endDate is on or after startDate (if endDate is provided).
     */
    @AssertTrue(message = "End date must be on or after start date")
    public boolean isEndDateValid() {
        if (endDate == null) {
            return true; // endDate is optional
        }
        if (startDate == null) {
            return true; // Will be caught by @NotNull on startDate
        }
        return !endDate.isBefore(startDate);
    }
}
