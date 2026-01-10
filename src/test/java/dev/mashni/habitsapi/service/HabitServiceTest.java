package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.dto.CreateHabitRequest;
import dev.mashni.habitsapi.dto.HabitSummaryResponse;
import dev.mashni.habitsapi.model.*;
import dev.mashni.habitsapi.repository.HabitLogRepository;
import dev.mashni.habitsapi.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("HabitService Unit Tests")
class HabitServiceTest {

    @Mock
    private HabitRepository habitRepository;

    @Mock
    private HabitLogRepository habitLogRepository;

    @InjectMocks
    private HabitService habitService;

    private User testUser;
    private Habit testHabit;

    @BeforeEach
    void setUp() {
        testUser = new User("test@example.com", "Test User", "google-123");
        testUser.setId(UUID.randomUUID());

        testHabit = new Habit("Read Books", "Daily reading habit", LocalDate.now().minusDays(30), testUser);
        testHabit.setId(UUID.randomUUID());
    }

    @Test
    @DisplayName("Case 1: Habit with no logs should have streak 0")
    void testCalculateStreak_NoLogs_ReturnsZero() {
        // Arrange
        testHabit.setLogs(new ArrayList<>());
        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(0);
        assertThat(habitsPage.getContent().get(0).bestStreak()).isEqualTo(0);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(0);
    }

    @Test
    @DisplayName("Case 2: Habit done today and yesterday should have streak 2")
    void testCalculateStreak_TodayAndYesterday_ReturnsTwo() {
        // Arrange
        var today = LocalDate.now();
        var yesterday = today.minusDays(1);

        var logs = Arrays.asList(
            createLog(testHabit, today),
            createLog(testHabit, yesterday)
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(2);
        assertThat(habitsPage.getContent().get(0).bestStreak()).isEqualTo(2);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("Case 3: Habit done today, yesterday, and day before yesterday should have streak 3")
    void testCalculateStreak_ThreeConsecutiveDays_ReturnsThree() {
        // Arrange
        var today = LocalDate.now();
        var yesterday = today.minusDays(1);
        var dayBeforeYesterday = today.minusDays(2);

        var logs = Arrays.asList(
            createLog(testHabit, today),
            createLog(testHabit, yesterday),
            createLog(testHabit, dayBeforeYesterday)
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(3);
        assertThat(habitsPage.getContent().get(0).bestStreak()).isEqualTo(3);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(3);
    }

    @Test
    @DisplayName("Case 4: Habit done today and day before yesterday (gap) should have streak 1")
    void testCalculateStreak_WithGap_ReturnsOne() {
        // Arrange
        var today = LocalDate.now();
        var dayBeforeYesterday = today.minusDays(2);

        var logs = Arrays.asList(
            createLog(testHabit, today),
            createLog(testHabit, dayBeforeYesterday)
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(1);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("Case 5: Habit marked for future date should not break calculation")
    void testCalculateStreak_FutureDate_DoesNotBreakCalculation() {
        // Arrange
        var today = LocalDate.now();
        var yesterday = today.minusDays(1);
        var tomorrow = today.plusDays(1);

        var logs = Arrays.asList(
            createLog(testHabit, tomorrow),
            createLog(testHabit, today),
            createLog(testHabit, yesterday)
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(2);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(3);
    }

    @Test
    @DisplayName("Best streak should be calculated correctly even when current streak is lower")
    void testCalculateStreak_BestStreakHigherThanCurrent() {
        // Arrange
        var today = LocalDate.now();

        // Old streak of 5 days, then gap, then new streak of 2 days
        var logs = Arrays.asList(
            createLog(testHabit, today),
            createLog(testHabit, today.minusDays(1)),
            // Gap here
            createLog(testHabit, today.minusDays(10)),
            createLog(testHabit, today.minusDays(11)),
            createLog(testHabit, today.minusDays(12)),
            createLog(testHabit, today.minusDays(13)),
            createLog(testHabit, today.minusDays(14))
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(2);
        assertThat(habitsPage.getContent().get(0).bestStreak()).isEqualTo(5);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(7);
    }

    @Test
    @DisplayName("Streak should be 0 when last log is more than 1 day ago")
    void testCalculateStreak_OldLogs_ReturnsZero() {
        // Arrange
        var threeDaysAgo = LocalDate.now().minusDays(3);
        var fourDaysAgo = LocalDate.now().minusDays(4);

        var logs = Arrays.asList(
            createLog(testHabit, threeDaysAgo),
            createLog(testHabit, fourDaysAgo)
        );
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(0);
        assertThat(habitsPage.getContent().get(0).bestStreak()).isEqualTo(2);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("Streak should count from yesterday if today not completed")
    void testCalculateStreak_YesterdayOnly_ReturnsOne() {
        // Arrange
        var yesterday = LocalDate.now().minusDays(1);

        var logs = List.of(createLog(testHabit, yesterday));
        testHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(testHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        assertThat(habitsPage.getContent().get(0).currentStreak()).isEqualTo(1);
        assertThat(habitsPage.getContent().get(0).daysCompleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gym Rat: Habit with SPECIFIC_DAYS (Mon/Wed/Fri) should have streak 3 even with gaps on Tue/Thu")
    void testCalculateStreak_GymRat_SpecificDaysWithGapForgiveness() {
        // Arrange: Find the most recent Friday and work backwards
        LocalDate today = LocalDate.now();
        LocalDate mostRecentFriday = getMostRecentDayOfWeek(today, DayOfWeek.FRIDAY);
        LocalDate previousWednesday = mostRecentFriday.minusDays(2); // Wednesday
        LocalDate previousMonday = mostRecentFriday.minusDays(4);    // Monday

        // Create habit with SPECIFIC_DAYS frequency (Mon, Wed, Fri)
        Set<DayOfWeek> targetDays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        Habit gymHabit = new Habit(
            "Gym",
            "Go to the gym",
            previousMonday.minusDays(7), // Start date one week before
            testUser,
            FrequencyType.SPECIFIC_DAYS,
            targetDays
        );
        gymHabit.setId(UUID.randomUUID());

        // Check-ins: Monday, Wednesday, Friday (skipping Tuesday and Thursday)
        var logs = Arrays.asList(
            createLog(gymHabit, mostRecentFriday),
            createLog(gymHabit, previousWednesday),
            createLog(gymHabit, previousMonday)
        );
        gymHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(gymHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        HabitSummaryResponse habit = habitsPage.getContent().get(0);

        // The streak should be 3 because Tuesday and Thursday are not required days
        // Gap Forgiveness: non-required days don't break the streak
        assertThat(habit.currentStreak()).isEqualTo(3);
        assertThat(habit.daysCompleted()).isEqualTo(3);
    }

    @Test
    @DisplayName("SPECIFIC_DAYS habit should break streak when required day is missed")
    void testCalculateStreak_SpecificDays_BreaksWhenRequiredDayMissed() {
        // Arrange: Find the most recent Friday and work backwards
        LocalDate today = LocalDate.now();
        LocalDate mostRecentFriday = getMostRecentDayOfWeek(today, DayOfWeek.FRIDAY);
        LocalDate previousWednesday = mostRecentFriday.minusDays(2); // Wednesday
        LocalDate previousMonday = mostRecentFriday.minusDays(4);    // Monday

        // Create habit with SPECIFIC_DAYS frequency (Mon, Wed, Fri)
        Set<DayOfWeek> targetDays = Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        Habit gymHabit = new Habit(
            "Gym",
            "Go to the gym",
            previousMonday.minusDays(7), // Start date one week before
            testUser,
            FrequencyType.SPECIFIC_DAYS,
            targetDays
        );
        gymHabit.setId(UUID.randomUUID());

        // Check-ins: Friday and Monday only (missing Wednesday - a required day!)
        var logs = Arrays.asList(
            createLog(gymHabit, mostRecentFriday),
            // Missing previousWednesday (required day)
            createLog(gymHabit, previousMonday)
        );
        gymHabit.setLogs(logs);

        Page<Habit> habitPage = new PageImpl<>(List.of(gymHabit));
        when(habitRepository.findByUserAndStatusWithLogs(any(User.class), any(HabitStatus.class), any(Pageable.class)))
            .thenReturn(habitPage);

        // Act
        var habitsPage = habitService.getAllActiveHabits(testUser, Pageable.unpaged());

        // Assert
        assertThat(habitsPage.getContent()).hasSize(1);
        HabitSummaryResponse habit = habitsPage.getContent().get(0);

        // Streak should be 1 because Wednesday (required day) was missed
        assertThat(habit.currentStreak()).isEqualTo(1);
        assertThat(habit.daysCompleted()).isEqualTo(2);
    }

    /**
     * Helper method to find the most recent occurrence of a specific day of week.
     * If today is that day, returns today. Otherwise, returns the most recent past occurrence.
     */
    private LocalDate getMostRecentDayOfWeek(LocalDate from, DayOfWeek targetDay) {
        LocalDate date = from;
        while (date.getDayOfWeek() != targetDay) {
            date = date.minusDays(1);
        }
        return date;
    }

    private HabitLog createLog(Habit habit, LocalDate date) {
        var log = new HabitLog(habit, date);
        log.setId(System.currentTimeMillis());
        return log;
    }
}
