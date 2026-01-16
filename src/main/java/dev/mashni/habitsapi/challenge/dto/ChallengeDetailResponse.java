package dev.mashni.habitsapi.challenge.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ChallengeDetailResponse(
    UUID id,
    String name,
    String description,
    String inviteCode,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime createdAt,
    UUID creatorId,
    String creatorName,
    int participantCount,
    double todayCompletionRate,
    List<LeaderboardEntryResponse> leaderboard
) {}
