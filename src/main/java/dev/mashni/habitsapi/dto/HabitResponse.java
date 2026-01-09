package dev.mashni.habitsapi.dto;

import java.time.LocalDate;
import java.util.UUID;

public record HabitResponse(
    UUID id,
    String name,
    String description,
    LocalDate startDate,
    String status
) {
}
