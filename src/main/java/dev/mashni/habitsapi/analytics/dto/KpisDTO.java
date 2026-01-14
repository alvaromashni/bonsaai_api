package dev.mashni.habitsapi.analytics.dto;

/**
 * Container for all KPI metrics in the analytics dashboard.
 */
public record KpisDTO(
    KpiMetricDTO completionRate,
    KpiMetricIntegerDTO totalHabits,
    KpiMetricIntegerDTO currentStreak,
    String bestDay  // Best day of week (e.g., "MONDAY", "TUESDAY")
) {
}
