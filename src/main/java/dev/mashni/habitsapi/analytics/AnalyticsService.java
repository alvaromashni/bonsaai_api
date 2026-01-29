package dev.mashni.habitsapi.analytics;

import dev.mashni.habitsapi.analytics.dto.*;
import dev.mashni.habitsapi.habit.model.FrequencyType;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.model.HabitLog;
import dev.mashni.habitsapi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsResponseDTO getEnhancedAnalytics(User user) {
        // Get all logs and habits
        List<HabitLog> allLogs = analyticsRepository.findAllLogsByUserId(user.getId());
        List<Habit> activeHabits = analyticsRepository.findActiveHabitsByUserId(user.getId());

        // Define time periods
        LocalDate today = LocalDate.now();
        LocalDate weekAgoStart = today.minusDays(7);
        LocalDate twoWeeksAgoStart = today.minusDays(14);

        // Filter logs for current week and previous week
        List<HabitLog> currentWeekLogs = filterLogsByDateRange(allLogs, weekAgoStart, today);
        List<HabitLog> previousWeekLogs = filterLogsByDateRange(allLogs, twoWeeksAgoStart, weekAgoStart.minusDays(1));

        // Calculate KPIs
        KpisDTO kpis = calculateKpis(allLogs, activeHabits, currentWeekLogs, previousWeekLogs);

        // Get heatmap data for the last year
        LocalDate oneYearAgo = today.minusYears(1);
        List<Object[]> heatmapRaw = analyticsRepository.findHeatmapData(user.getId(), oneYearAgo);
        List<HeatmapDataDTO> heatmapSeries = parseHeatmapData(heatmapRaw);

        // Calculate radar series (count by day of week)
        List<RadarSeriesDTO> radarSeries = calculateRadarSeries(allLogs);

        return new AnalyticsResponseDTO(kpis, heatmapSeries, radarSeries);
    }

    @Transactional(readOnly = true)
    public AnalyticsSummaryDTO getAnalyticsSummary(User user) {
        // Get all logs and habits
        List<HabitLog> allLogs = analyticsRepository.findAllLogsByUserId(user.getId());
        List<Habit> activeHabits = analyticsRepository.findActiveHabitsByUserId(user.getId());

        // Calculate total logs
        Long totalLogs = (long) allLogs.size();

        // Calculate total expected days
        Long totalExpectedDays = calculateTotalExpectedDays(activeHabits);

        // Calculate completion rate
        Double completionRate = 0.0;
        if (totalExpectedDays != null && totalExpectedDays > 0) {
            completionRate = (totalLogs.doubleValue() / totalExpectedDays.doubleValue()) * 100.0;
            // Round to 1 decimal place
            completionRate = Math.round(completionRate * 10.0) / 10.0;
        }

        // Get best day of week
        String bestDayOfWeek = calculateBestDayOfWeek(allLogs);

        // Get heatmap data for the last year
        LocalDate oneYearAgo = LocalDate.now().minusYears(1);
        List<Object[]> heatmapRaw = analyticsRepository.findHeatmapData(user.getId(), oneYearAgo);
        List<HeatmapDataDTO> heatmap = new ArrayList<>();

        for (Object[] row : heatmapRaw) {
            LocalDate date;
            if (row[0] instanceof Date) {
                date = ((Date) row[0]).toLocalDate();
            } else if (row[0] instanceof LocalDate) {
                date = (LocalDate) row[0];
            } else {
                continue;
            }

            Integer count;
            if (row[1] instanceof Long) {
                count = ((Long) row[1]).intValue();
            } else if (row[1] instanceof Integer) {
                count = (Integer) row[1];
            } else {
                count = 0;
            }

            heatmap.add(new HeatmapDataDTO(date, count));
        }

        // Calculate streaks
        List<LocalDate> distinctDates = analyticsRepository.findDistinctCompletedDatesByUserId(user.getId());
        Integer currentStreak = calculateCurrentStreak(distinctDates);
        Integer longestStreak = calculateLongestStreak(distinctDates);

        return new AnalyticsSummaryDTO(
            completionRate,
            totalLogs,
            currentStreak,
            longestStreak,
            bestDayOfWeek,
            heatmap
        );
    }

    private Long calculateTotalExpectedDays(List<Habit> activeHabits) {
        long total = 0;
        LocalDate today = LocalDate.now();

        for (Habit habit : activeHabits) {
            LocalDate startDate = habit.getStartDate();
            if (startDate.isAfter(today)) {
                continue;
            }

            total += countExpectedDaysForHabit(habit, startDate, today);
        }

        return total;
    }

    /**
     * Count expected days for a habit between startDate and endDate.
     * Aligns with HabitService.isRequiredDay() semantics:
     * - DAILY: All days are required
     * - SPECIFIC_DAYS with targetDays: Only those days are required
     * - SPECIFIC_DAYS with empty targetDays: Treat as DAILY (all days required)
     */
    private long countExpectedDaysForHabit(Habit habit, LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            return 0;
        }

        // For DAILY habits, count all days
        if (habit.getFrequencyType() == FrequencyType.DAILY) {
            return ChronoUnit.DAYS.between(startDate, endDate) + 1;
        }

        // For SPECIFIC_DAYS habits
        if (habit.getFrequencyType() == FrequencyType.SPECIFIC_DAYS) {
            Set<DayOfWeek> targetDays = habit.getTargetDays();

            // If targetDays is null or empty, treat as DAILY (consistent with HabitService)
            if (targetDays == null || targetDays.isEmpty()) {
                return ChronoUnit.DAYS.between(startDate, endDate) + 1;
            }

            // Count only the target days in the range
            long count = 0;
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                if (targetDays.contains(current.getDayOfWeek())) {
                    count++;
                }
                current = current.plusDays(1);
            }
            return count;
        }

        // Default fallback: treat as DAILY
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private String calculateBestDayOfWeek(List<HabitLog> logs) {
        if (logs.isEmpty()) {
            return null;
        }

        // Group logs by day of week
        Map<DayOfWeek, Long> countByDay = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCompletedDate().getDayOfWeek(),
                Collectors.counting()
            ));

        // Find the day with most logs
        return countByDay.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(entry -> entry.getKey().name())
            .orElse(null);
    }

    private Integer calculateCurrentStreak(List<LocalDate> distinctDates) {
        if (distinctDates.isEmpty()) {
            return 0;
        }

        // Sort dates in descending order (most recent first)
        List<LocalDate> sortedDates = new ArrayList<>(distinctDates);
        sortedDates.sort(Collections.reverseOrder());

        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        // Check if there's activity today or yesterday
        if (!sortedDates.get(0).equals(today) && !sortedDates.get(0).equals(yesterday)) {
            return 0;
        }

        // Calculate consecutive days
        int streak = 1;
        LocalDate expectedDate = sortedDates.get(0).minusDays(1);

        for (int i = 1; i < sortedDates.size(); i++) {
            if (sortedDates.get(i).equals(expectedDate)) {
                streak++;
                expectedDate = expectedDate.minusDays(1);
            } else {
                break;
            }
        }

        return streak;
    }

    private Integer calculateLongestStreak(List<LocalDate> distinctDates) {
        if (distinctDates.isEmpty()) {
            return 0;
        }

        // Sort dates in ascending order
        List<LocalDate> sortedDates = new ArrayList<>(distinctDates);
        Collections.sort(sortedDates);

        int longestStreak = 1;
        int currentStreak = 1;

        for (int i = 1; i < sortedDates.size(); i++) {
            LocalDate current = sortedDates.get(i);
            LocalDate previous = sortedDates.get(i - 1);

            if (ChronoUnit.DAYS.between(previous, current) == 1) {
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }

        return longestStreak;
    }

    // Helper methods for enhanced analytics

    private List<HabitLog> filterLogsByDateRange(List<HabitLog> logs, LocalDate startDate, LocalDate endDate) {
        return logs.stream()
            .filter(log -> !log.getCompletedDate().isBefore(startDate) && !log.getCompletedDate().isAfter(endDate))
            .collect(Collectors.toList());
    }

    private KpisDTO calculateKpis(List<HabitLog> allLogs, List<Habit> activeHabits,
                                   List<HabitLog> currentWeekLogs, List<HabitLog> previousWeekLogs) {
        // Calculate completion rate for current week
        Long currentWeekTotal = (long) currentWeekLogs.size();
        Long currentWeekExpected = calculateExpectedDaysForPeriod(activeHabits, LocalDate.now().minusDays(7), LocalDate.now());
        Double currentCompletionRate = 0.0;
        if (currentWeekExpected > 0) {
            currentCompletionRate = (currentWeekTotal.doubleValue() / currentWeekExpected.doubleValue()) * 100.0;
            currentCompletionRate = Math.round(currentCompletionRate * 10.0) / 10.0;
        }

        // Calculate completion rate for previous week
        Long previousWeekTotal = (long) previousWeekLogs.size();
        Long previousWeekExpected = calculateExpectedDaysForPeriod(activeHabits, LocalDate.now().minusDays(14), LocalDate.now().minusDays(8));
        Double previousCompletionRate = 0.0;
        if (previousWeekExpected > 0) {
            previousCompletionRate = (previousWeekTotal.doubleValue() / previousWeekExpected.doubleValue()) * 100.0;
            previousCompletionRate = Math.round(previousCompletionRate * 10.0) / 10.0;
        }

        // Calculate trend for completion rate (percentage point difference)
        Double completionRateTrend = currentCompletionRate - previousCompletionRate;
        completionRateTrend = Math.round(completionRateTrend * 10.0) / 10.0;

        // Total habits completed (absolute difference)
        Integer totalHabitsValue = allLogs.size();
        Integer totalHabitsTrend = currentWeekLogs.size() - previousWeekLogs.size();

        // Calculate current streak
        List<LocalDate> distinctDates = allLogs.stream()
            .map(HabitLog::getCompletedDate)
            .distinct()
            .sorted(Collections.reverseOrder())
            .collect(Collectors.toList());
        Integer currentStreakValue = calculateCurrentStreak(distinctDates);

        // Calculate streak for previous week (to compare)
        List<LocalDate> previousWeekDistinctDates = filterLogsByDateRange(allLogs, LocalDate.now().minusDays(14), LocalDate.now().minusDays(8))
            .stream()
            .map(HabitLog::getCompletedDate)
            .distinct()
            .sorted(Collections.reverseOrder())
            .collect(Collectors.toList());
        Integer previousStreakValue = previousWeekDistinctDates.isEmpty() ? 0 : calculateStreakForPeriod(previousWeekDistinctDates);

        // Streak trend (absolute difference)
        Integer streakTrend = currentStreakValue - previousStreakValue;

        // Best day of week
        String bestDay = calculateBestDayOfWeek(allLogs);

        // Build KPI metrics
        KpiMetricDTO completionRate = new KpiMetricDTO(currentCompletionRate, completionRateTrend);
        KpiMetricIntegerDTO totalHabits = new KpiMetricIntegerDTO(totalHabitsValue, totalHabitsTrend);
        KpiMetricIntegerDTO currentStreak = new KpiMetricIntegerDTO(currentStreakValue, streakTrend);

        return new KpisDTO(completionRate, totalHabits, currentStreak, bestDay);
    }

    private Long calculateExpectedDaysForPeriod(List<Habit> activeHabits, LocalDate startDate, LocalDate endDate) {
        long total = 0;

        for (Habit habit : activeHabits) {
            LocalDate habitStart = habit.getStartDate();
            if (habitStart.isAfter(endDate)) {
                continue;
            }

            LocalDate periodStart = habitStart.isBefore(startDate) ? startDate : habitStart;
            total += countExpectedDaysForHabit(habit, periodStart, endDate);
        }

        return total;
    }

    private Integer calculateStreakForPeriod(List<LocalDate> distinctDates) {
        if (distinctDates.isEmpty()) {
            return 0;
        }

        int streak = 1;
        LocalDate expectedDate = distinctDates.get(0).minusDays(1);

        for (int i = 1; i < distinctDates.size(); i++) {
            if (distinctDates.get(i).equals(expectedDate)) {
                streak++;
                expectedDate = expectedDate.minusDays(1);
            } else {
                break;
            }
        }

        return streak;
    }

    private List<HeatmapDataDTO> parseHeatmapData(List<Object[]> heatmapRaw) {
        List<HeatmapDataDTO> heatmap = new ArrayList<>();

        for (Object[] row : heatmapRaw) {
            LocalDate date;
            if (row[0] instanceof Date) {
                date = ((Date) row[0]).toLocalDate();
            } else if (row[0] instanceof LocalDate) {
                date = (LocalDate) row[0];
            } else {
                continue;
            }

            Integer count;
            if (row[1] instanceof Long) {
                count = ((Long) row[1]).intValue();
            } else if (row[1] instanceof Integer) {
                count = (Integer) row[1];
            } else {
                count = 0;
            }

            heatmap.add(new HeatmapDataDTO(date, count));
        }

        return heatmap;
    }

    private List<RadarSeriesDTO> calculateRadarSeries(List<HabitLog> logs) {
        // Group logs by day of week and count
        Map<DayOfWeek, Long> countByDay = logs.stream()
            .collect(Collectors.groupingBy(
                log -> log.getCompletedDate().getDayOfWeek(),
                Collectors.counting()
            ));

        // Create radar series for all 7 days of week (including 0 for days with no logs)
        List<RadarSeriesDTO> radarSeries = new ArrayList<>();
        for (DayOfWeek day : DayOfWeek.values()) {
            Integer count = countByDay.getOrDefault(day, 0L).intValue();
            radarSeries.add(new RadarSeriesDTO(day.name(), count));
        }

        return radarSeries;
    }
}
