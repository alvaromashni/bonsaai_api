package dev.mashni.habitsapi.analytics;

import dev.mashni.habitsapi.habit.model.FrequencyType;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.model.HabitLog;
import dev.mashni.habitsapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsService Unit Tests")
class AnalyticsServiceTest {

    @Mock
    private AnalyticsRepository analyticsRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User("test@example.com", "Test User", "google-123");
        testUser.setId(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Expected Days Calculation Tests")
    class ExpectedDaysCalculationTests {

        @Test
        @DisplayName("DAILY habit: should count all days from start to today")
        void dailyHabit_CountsAllDays() {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);
            Habit dailyHabit = createHabit("Daily Habit", FrequencyType.DAILY, sevenDaysAgo, null);

            // 7 completed logs (one per day)
            List<HabitLog> logs = createLogsForDays(dailyHabit, sevenDaysAgo, LocalDate.now());

            setupMocks(logs, List.of(dailyHabit));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 7 days expected, 7 completed = 100%
            assertThat(summary.globalCompletionRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("SPECIFIC_DAYS with targetDays: should count only target days")
        void specificDaysWithTargetDays_CountsOnlyTargetDays() {
            LocalDate fourteenDaysAgo = LocalDate.now().minusDays(13);
            Set<DayOfWeek> targetDays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
            Habit specificDaysHabit = createHabit("MWF Habit", FrequencyType.SPECIFIC_DAYS, fourteenDaysAgo, targetDays);

            // Create logs for target days only
            List<HabitLog> logs = createLogsForTargetDays(specificDaysHabit, fourteenDaysAgo, LocalDate.now(), targetDays);

            setupMocks(logs, List.of(specificDaysHabit));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // All target days completed = 100%
            assertThat(summary.globalCompletionRate()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("SPECIFIC_DAYS with empty targetDays: should treat as DAILY (all days)")
        void specificDaysWithEmptyTargetDays_TreatedAsDaily() {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);
            // Empty target days - should be treated as DAILY
            Set<DayOfWeek> emptyTargetDays = new HashSet<>();
            Habit habitWithEmptyTargetDays = createHabit("Empty Target Days", FrequencyType.SPECIFIC_DAYS, sevenDaysAgo, emptyTargetDays);

            // Complete all 7 days
            List<HabitLog> logs = createLogsForDays(habitWithEmptyTargetDays, sevenDaysAgo, LocalDate.now());

            setupMocks(logs, List.of(habitWithEmptyTargetDays));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 7 days expected (treated as daily), 7 completed = 100%
            assertThat(summary.globalCompletionRate()).isEqualTo(100.0);
            assertThat(summary.totalHabitsCompleted()).isEqualTo(7L);
        }

        @Test
        @DisplayName("SPECIFIC_DAYS with null targetDays: should treat as DAILY (all days)")
        void specificDaysWithNullTargetDays_TreatedAsDaily() {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);
            // Null target days - should be treated as DAILY
            Habit habitWithNullTargetDays = createHabit("Null Target Days", FrequencyType.SPECIFIC_DAYS, sevenDaysAgo, null);

            // Complete all 7 days
            List<HabitLog> logs = createLogsForDays(habitWithNullTargetDays, sevenDaysAgo, LocalDate.now());

            setupMocks(logs, List.of(habitWithNullTargetDays));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 7 days expected (treated as daily), 7 completed = 100%
            assertThat(summary.globalCompletionRate()).isEqualTo(100.0);
            assertThat(summary.totalHabitsCompleted()).isEqualTo(7L);
        }

        @Test
        @DisplayName("Short interval: single day habit should count correctly")
        void singleDayHabit_CountsCorrectly() {
            LocalDate today = LocalDate.now();
            Habit singleDayHabit = createHabit("Today Habit", FrequencyType.DAILY, today, null);

            // One log for today
            HabitLog log = new HabitLog(singleDayHabit, today);
            List<HabitLog> logs = List.of(log);

            setupMocks(logs, List.of(singleDayHabit));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 1 day expected, 1 completed = 100%
            assertThat(summary.globalCompletionRate()).isEqualTo(100.0);
            assertThat(summary.totalHabitsCompleted()).isEqualTo(1L);
        }

        @Test
        @DisplayName("No logs: completion rate should be 0%")
        void noLogs_CompletionRateIsZero() {
            LocalDate sevenDaysAgo = LocalDate.now().minusDays(6);
            Habit habitWithNoLogs = createHabit("No Logs Habit", FrequencyType.DAILY, sevenDaysAgo, null);

            setupMocks(List.of(), List.of(habitWithNoLogs));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 7 days expected, 0 completed = 0%
            assertThat(summary.globalCompletionRate()).isEqualTo(0.0);
            assertThat(summary.totalHabitsCompleted()).isEqualTo(0L);
        }

        @Test
        @DisplayName("Partial completion: should calculate correct percentage")
        void partialCompletion_CorrectPercentage() {
            LocalDate tenDaysAgo = LocalDate.now().minusDays(9);
            Habit habit = createHabit("Partial Habit", FrequencyType.DAILY, tenDaysAgo, null);

            // Only 5 logs out of 10 days
            List<HabitLog> logs = createLogsForDays(habit, tenDaysAgo, tenDaysAgo.plusDays(4));

            setupMocks(logs, List.of(habit));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 10 days expected, 5 completed = 50%
            assertThat(summary.globalCompletionRate()).isEqualTo(50.0);
            assertThat(summary.totalHabitsCompleted()).isEqualTo(5L);
        }

        @Test
        @DisplayName("Future start date habit: should not count expected days")
        void futureStartDate_NoExpectedDays() {
            LocalDate tomorrow = LocalDate.now().plusDays(1);
            Habit futureHabit = createHabit("Future Habit", FrequencyType.DAILY, tomorrow, null);

            setupMocks(List.of(), List.of(futureHabit));

            var summary = analyticsService.getAnalyticsSummary(testUser);

            // 0 days expected (future start), 0 completed
            assertThat(summary.globalCompletionRate()).isEqualTo(0.0);
        }
    }

    // Helper methods

    private void setupMocks(List<HabitLog> logs, List<Habit> habits) {
        when(analyticsRepository.findAllLogsByUserId(testUser.getId())).thenReturn(logs);
        when(analyticsRepository.findActiveHabitsByUserId(testUser.getId())).thenReturn(habits);
        when(analyticsRepository.findHeatmapData(eq(testUser.getId()), any(LocalDate.class))).thenReturn(List.of());

        List<LocalDate> distinctDates = logs.stream()
                .map(HabitLog::getCompletedDate)
                .distinct()
                .sorted()
                .toList();
        when(analyticsRepository.findDistinctCompletedDatesByUserId(testUser.getId())).thenReturn(distinctDates);
    }

    private Habit createHabit(String name, FrequencyType frequencyType, LocalDate startDate, Set<DayOfWeek> targetDays) {
        Habit habit = new Habit(name, "Description", startDate, testUser, frequencyType, targetDays);
        habit.setId(UUID.randomUUID());
        return habit;
    }

    private List<HabitLog> createLogsForDays(Habit habit, LocalDate start, LocalDate end) {
        List<HabitLog> logs = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            logs.add(new HabitLog(habit, current));
            current = current.plusDays(1);
        }
        return logs;
    }

    private List<HabitLog> createLogsForTargetDays(Habit habit, LocalDate start, LocalDate end, Set<DayOfWeek> targetDays) {
        List<HabitLog> logs = new ArrayList<>();
        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (targetDays.contains(current.getDayOfWeek())) {
                logs.add(new HabitLog(habit, current));
            }
            current = current.plusDays(1);
        }
        return logs;
    }
}
