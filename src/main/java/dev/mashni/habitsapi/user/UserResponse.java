package dev.mashni.habitsapi.user;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        UserPlan userPlan,
        LocalDateTime planExpiresAt
) {
}
