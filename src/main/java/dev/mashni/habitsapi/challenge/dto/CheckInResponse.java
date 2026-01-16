package dev.mashni.habitsapi.challenge.dto;

import java.time.LocalDate;

public record CheckInResponse(
    boolean checked,
    LocalDate date,
    String message
) {}
