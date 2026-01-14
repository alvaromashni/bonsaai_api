package dev.mashni.habitsapi.habit.dto;

import java.util.UUID;

public record HabitSummaryResponse(
    UUID id,
    String name,
    String description,
    int currentStreak,
    int bestStreak,
    int daysCompleted
) {
}
