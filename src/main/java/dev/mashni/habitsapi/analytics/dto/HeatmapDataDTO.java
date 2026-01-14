package dev.mashni.habitsapi.analytics.dto;

import java.time.LocalDate;

/**
 * Represents a single day's data for the heatmap.
 */
public record HeatmapDataDTO(
    LocalDate date,
    Integer count
) {
}