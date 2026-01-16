package dev.mashni.habitsapi.challenge.dto;

import java.time.LocalDate;
import java.util.UUID;

public record LeaderboardEntryResponse(
    UUID userId,
    String userName,
    String avatarUrl,
    long totalChecks,
    double completionRate,
    LocalDate lastCheckIn
) {}
