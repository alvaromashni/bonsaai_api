package dev.mashni.habitsapi.dto;

import dev.mashni.habitsapi.model.FrequencyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

public record CreateHabitRequest(
    @NotBlank(message = "Name is required")
    @Size(min = 1, max = 100, message = "Name must be between 1 and 100 characters")
    String name,

    @Size(max = 500, message = "Description must not exceed 500 characters")
    String description,

    @NotNull(message = "Start date is required")
    @PastOrPresent(message = "Start date cannot be in the future")
    LocalDate startDate,

    FrequencyType frequencyType,

    Set<DayOfWeek> targetDays
) {
}
