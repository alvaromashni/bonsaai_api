package dev.mashni.habitsapi.dto;

import java.util.List;

/**
 * Analytics summary for a PRO user.
 */
public record AnalyticsSummaryDTO(
    Double globalCompletionRate,
    Long totalHabitsCompleted,
    Integer currentStreak,
    Integer longestStreak,
    String bestDayOfWeek,
    List<HeatmapDataDTO> heatmap
) {
}
