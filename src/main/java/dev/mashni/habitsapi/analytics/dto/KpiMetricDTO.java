package dev.mashni.habitsapi.analytics.dto;

/**
 * Represents a KPI metric with its current value and trend (% change vs previous week).
 */
public record KpiMetricDTO(
    Double value,
    Double trend  // % change vs previous week (positive = improvement, negative = decline)
) {
}
