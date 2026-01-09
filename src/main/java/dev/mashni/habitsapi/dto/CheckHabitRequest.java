package dev.mashni.habitsapi.dto;

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
