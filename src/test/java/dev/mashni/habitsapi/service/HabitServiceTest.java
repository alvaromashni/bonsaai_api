package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.dto.CreateHabitRequest;
import dev.mashni.habitsapi.dto.HabitSummaryResponse;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.model.HabitStatus;
import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.HabitLogRepository;
import dev.mashni.habitsapi.repository.HabitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

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
        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(new ArrayList<>());

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(0);
        assertThat(habits.get(0).bestStreak()).isEqualTo(0);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(0);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(2);
        assertThat(habits.get(0).bestStreak()).isEqualTo(2);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(2);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(3);
        assertThat(habits.get(0).bestStreak()).isEqualTo(3);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(3);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(1);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(2);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(2);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(3);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(2);
        assertThat(habits.get(0).bestStreak()).isEqualTo(5);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(7);
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

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(0);
        assertThat(habits.get(0).bestStreak()).isEqualTo(2);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("Streak should count from yesterday if today not completed")
    void testCalculateStreak_YesterdayOnly_ReturnsOne() {
        // Arrange
        var yesterday = LocalDate.now().minusDays(1);

        var logs = List.of(createLog(testHabit, yesterday));

        when(habitRepository.findByUserAndStatus(testUser, HabitStatus.ACTIVE))
            .thenReturn(List.of(testHabit));
        when(habitLogRepository.findByHabitIdOrderByCompletedDateDesc(testHabit.getId()))
            .thenReturn(logs);

        // Act
        var habits = habitService.getAllActiveHabits(testUser);

        // Assert
        assertThat(habits).hasSize(1);
        assertThat(habits.get(0).currentStreak()).isEqualTo(1);
        assertThat(habits.get(0).daysCompleted()).isEqualTo(1);
    }

    private HabitLog createLog(Habit habit, LocalDate date) {
        var log = new HabitLog(habit, date);
        log.setId(System.currentTimeMillis());
        return log;
    }
}
