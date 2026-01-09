package dev.mashni.habitsapi.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HabitDetailResponse(
    UUID id,
    String name,
    String description,
    LocalDate startDate,
    String status,
    int currentStreak,
    int bestStreak,
    int daysCompleted,
    List<LocalDate> completedDates
) {
}
