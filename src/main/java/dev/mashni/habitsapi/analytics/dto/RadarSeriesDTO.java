package dev.mashni.habitsapi.analytics.dto;

/**
 * Represents radar chart data point for a specific day of the week.
 */
public record RadarSeriesDTO(
    String day,           // Day of week (e.g., "MONDAY", "TUESDAY")
    Integer completedCount // Number of completed habits on this day
) {
}
