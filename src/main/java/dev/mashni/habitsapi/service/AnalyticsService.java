package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.dto.AnalyticsSummaryDTO;
import dev.mashni.habitsapi.dto.HeatmapDataDTO;
import dev.mashni.habitsapi.model.FrequencyType;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.AnalyticsRepository;
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

            if (habit.getFrequencyType() == FrequencyType.DAILY) {
                // For daily habits, count all days from start to today
                long days = ChronoUnit.DAYS.between(startDate, today) + 1;
                total += days;
            } else if (habit.getFrequencyType() == FrequencyType.SPECIFIC_DAYS) {
                // For specific days, count only the target days
                Set<DayOfWeek> targetDays = habit.getTargetDays();
                if (targetDays != null && !targetDays.isEmpty()) {
                    LocalDate current = startDate;
                    while (!current.isAfter(today)) {
                        if (targetDays.contains(current.getDayOfWeek())) {
                            total++;
                        }
                        current = current.plusDays(1);
                    }
                }
            }
        }

        return total;
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
}
