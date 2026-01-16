package dev.mashni.habitsapi.challenge.dto;

import java.time.LocalDate;

public record CheckInRequest(
    LocalDate date
) {}
