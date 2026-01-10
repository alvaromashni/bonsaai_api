package dev.mashni.habitsapi.dto;

import dev.mashni.habitsapi.model.FrequencyType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record HabitResponse(
    UUID id,
    String name,
    String description,
    LocalDate startDate,
    String status,
    FrequencyType frequencyType,
    Set<DayOfWeek> targetDays
) {
}
