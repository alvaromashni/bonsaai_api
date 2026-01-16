package dev.mashni.habitsapi.challenge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record JoinChallengeRequest(
    @NotBlank(message = "Invite code is required")
    @Size(min = 6, max = 10, message = "Invite code must be between 6 and 10 characters")
    String inviteCode
) {}
