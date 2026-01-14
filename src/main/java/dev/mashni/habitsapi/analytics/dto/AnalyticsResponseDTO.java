package dev.mashni.habitsapi.analytics.dto;

import java.util.List;

/**
 * Enhanced analytics response with KPIs, trends, heatmap, and radar chart data.
 */
public record AnalyticsResponseDTO(
    KpisDTO kpis,
    List<HeatmapDataDTO> heatmapSeries,
    List<RadarSeriesDTO> radarSeries
) {
}
