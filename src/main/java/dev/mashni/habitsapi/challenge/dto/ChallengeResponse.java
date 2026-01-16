package dev.mashni.habitsapi.challenge.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ChallengeResponse(
    UUID id,
    String name,
    String description,
    String inviteCode,
    LocalDate startDate,
    LocalDate endDate,
    LocalDateTime createdAt,
    UUID creatorId,
    String creatorName,
    int participantCount
) {}
