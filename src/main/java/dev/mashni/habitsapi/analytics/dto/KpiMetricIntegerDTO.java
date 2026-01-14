package dev.mashni.habitsapi.analytics.dto;

/**
 * Represents a KPI metric with integer value and its trend.
 */
public record KpiMetricIntegerDTO(
    Integer value,
    Integer trend  // absolute change vs previous week (not percentage for integer metrics)
) {
}
