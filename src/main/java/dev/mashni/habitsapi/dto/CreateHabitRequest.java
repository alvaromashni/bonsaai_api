package dev.mashni.habitsapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateHabitRequest(
    @NotBlank(message = "Name is required")
    String name,

    String description,

    @NotNull(message = "Start date is required")
    LocalDate startDate
) {
}
