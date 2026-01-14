package dev.mashni.habitsapi.habit.dto;

import java.time.LocalDate;

public record CheckHabitRequest(
    LocalDate date
) {
    public CheckHabitRequest {
        if (date == null) {
            date = LocalDate.now();
        }
    }
}
