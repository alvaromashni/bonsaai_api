package dev.mashni.habitsapi.habit;

import dev.mashni.habitsapi.habit.dto.*;
import dev.mashni.habitsapi.habit.model.FrequencyType;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.model.HabitLog;
import dev.mashni.habitsapi.habit.model.HabitStatus;
import dev.mashni.habitsapi.shared.exception.ResourceNotFoundException;
import dev.mashni.habitsapi.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class HabitService {

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;

    public HabitService(HabitRepository habitRepository, HabitLogRepository habitLogRepository) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
    }

    @Transactional
    public Habit createHabit(CreateHabitRequest request, User user) {
        // Validate user has not reached habit limit
        long habitCount = habitRepository.countByUser(user);
        if (habitCount >= 100) {
            throw new IllegalArgumentException("User has reached the maximum limit of 100 habits");
        }

        var habit = new Habit(
            request.name(),
            request.description(),
            request.startDate(),
            user,
            request.frequencyType(),
            request.targetDays()
        );
        return habitRepository.save(habit);
    }

    @Transactional
    public HabitLog checkHabit(UUID habitId, CheckHabitRequest request, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));

        var date = request.date() != null ? request.date() : LocalDate.now();

        // Validate date is not before habit start date
        if (date.isBefore(habit.getStartDate())) {
            throw new IllegalArgumentException("Cannot check habit before its start date");
        }

        // Validate date is not in the future
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot check habit for future dates");
        }

        var existingLog = habitLogRepository.findByHabitIdAndCompletedDate(habitId, date);

        if (existingLog.isPresent()) {
            // Toggle: if already exists, remove it (uncheck)
            habitLogRepository.delete(existingLog.get());
            return null;
        } else {
            // Create new log entry
            var log = new HabitLog(habit, date);
            return habitLogRepository.save(log);
        }
    }

    @Transactional(readOnly = true)
    public Page<HabitSummaryResponse> getAllActiveHabits(User user, Pageable pageable) {
        var habits = habitRepository.findByUserAndStatusWithLogs(user, HabitStatus.ACTIVE, pageable);
        return habits.map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public Page<HabitSummaryResponse> getAllArchivedHabits(User user, Pageable pageable) {
        var habits = habitRepository.findByUserAndStatusWithLogs(user, HabitStatus.ARCHIVED, pageable);
        return habits.map(this::mapToSummaryResponse);
    }

    @Transactional(readOnly = true)
    public HabitDetailResponse getHabitDetail(UUID habitId, User user) {
        var habit = habitRepository.findByIdAndUserWithLogs(habitId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
        var logs = habitLogRepository.findByHabitIdOrderByCompletedDateDesc(habitId);
        var completedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .sorted()
            .collect(Collectors.toList());

        var currentStreak = calculateCurrentStreak(logs, habit);
        var bestStreak = calculateBestStreak(logs, habit);

        return new HabitDetailResponse(
            habit.getId(),
            habit.getName(),
            habit.getDescription(),
            habit.getStartDate(),
            habit.getStatus().name(),
            currentStreak,
            bestStreak,
            logs.size(),
            completedDates
        );
    }

    private HabitSummaryResponse mapToSummaryResponse(Habit habit) {
        // Use logs already fetched with JOIN FETCH to avoid N+1 queries
        var logs = habit.getLogs();
        var currentStreak = calculateCurrentStreak(logs, habit);
        var bestStreak = calculateBestStreak(logs, habit);

        return new HabitSummaryResponse(
            habit.getId(),
            habit.getName(),
            habit.getDescription(),
            currentStreak,
            bestStreak,
            logs.size()
        );
    }

    @Transactional
    public Habit updateHabit(UUID habitId, UpdateHabitRequest request, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));

        habit.setName(request.name());
        habit.setDescription(request.description());

        if (request.status() != null) {
            habit.setStatus(request.status());
        }

        return habitRepository.save(habit);
    }

    @Transactional
    public void deleteHabit(UUID habitId, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
        habitRepository.delete(habit);
    }

    @Transactional
    public Habit archiveHabit(UUID habitId, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
        habit.setStatus(HabitStatus.ARCHIVED);
        return habitRepository.save(habit);
    }

    /**
     * Calculate the current streak by counting consecutive required days backward from today or yesterday.
     * For DAILY habits: every day is required.
     * For SPECIFIC_DAYS habits: only targetDays are required (Gap Forgiveness).
     * Days before the habit's start date are not counted.
     */
    private int calculateCurrentStreak(List<HabitLog> logs, Habit habit) {
        if (logs.isEmpty()) {
            return 0;
        }

        var today = LocalDate.now();
        var completedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .collect(Collectors.toSet());

        // Find the most recent required day that should have been completed
        LocalDate lastRequiredDay = findLastRequiredDay(today, habit);

        // Determine the starting point for streak calculation
        LocalDate streakStartDate;
        if (completedDates.contains(lastRequiredDay)) {
            // Last required day was completed - start from there
            streakStartDate = lastRequiredDay;
        } else {
            // Last required day was NOT completed
            // Check if the previous required day was completed (allow 1-day grace)
            LocalDate previousRequiredDay = findLastRequiredDay(lastRequiredDay.minusDays(1), habit);

            if (completedDates.contains(previousRequiredDay)) {
                // Previous required day was completed - start from there
                streakStartDate = previousRequiredDay;
            } else {
                // Neither last nor previous required day completed - no streak
                return 0;
            }
        }

        // Count backwards from the determined start date
        int streak = 0;
        var currentDate = streakStartDate;

        // Count backwards while checking required days
        while (!currentDate.isBefore(habit.getStartDate())) {
            boolean isRequired = isRequiredDay(currentDate, habit);
            boolean isCompleted = completedDates.contains(currentDate);

            if (isRequired) {
                if (isCompleted) {
                    streak++;
                } else {
                    // Required day not completed - streak breaks
                    break;
                }
            }
            // If not required, continue without incrementing (Gap Forgiveness)

            currentDate = currentDate.minusDays(1);
        }

        return streak;
    }

    /**
     * Find the most recent required day (today or in the past) based on habit's frequency.
     * For DAILY habits: returns today.
     * For SPECIFIC_DAYS habits: returns the most recent day that matches targetDays.
     */
    private LocalDate findLastRequiredDay(LocalDate from, Habit habit) {
        LocalDate currentDate = from;

        // For DAILY habits, every day is required
        if (habit.getFrequencyType() == FrequencyType.DAILY) {
            return currentDate;
        }

        // For SPECIFIC_DAYS, find the most recent required day
        while (!currentDate.isBefore(habit.getStartDate())) {
            if (isRequiredDay(currentDate, habit)) {
                return currentDate;
            }
            currentDate = currentDate.minusDays(1);
        }

        // If no required day found, return start date
        return habit.getStartDate();
    }

    /**
     * Check if a given date is a required day for the habit based on its frequency configuration.
     * - DAILY: All days are required
     * - SPECIFIC_DAYS: Only days in targetDays are required
     */
    private boolean isRequiredDay(LocalDate date, Habit habit) {
        if (habit.getFrequencyType() == FrequencyType.DAILY) {
            return true;
        } else if (habit.getFrequencyType() == FrequencyType.SPECIFIC_DAYS) {
            Set<DayOfWeek> targetDays = habit.getTargetDays();
            if (targetDays == null || targetDays.isEmpty()) {
                return true; // If no target days specified, treat as daily
            }
            return targetDays.contains(date.getDayOfWeek());
        }
        return true; // Default to daily
    }

    /**
     * Calculate the best (longest) streak in the habit's history.
     * For DAILY habits: counts consecutive days.
     * For SPECIFIC_DAYS habits: counts consecutive required days with Gap Forgiveness.
     * Only considers dates from the habit's start date onwards.
     */
    private int calculateBestStreak(List<HabitLog> logs, Habit habit) {
        if (logs.isEmpty()) {
            return 0;
        }

        var completedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .filter(date -> !date.isBefore(habit.getStartDate()))
            .collect(Collectors.toSet());

        if (completedDates.isEmpty()) {
            return 0;
        }

        int bestStreak = 0;
        int currentStreak = 0;
        LocalDate streakStartDate = null;

        // Iterate from start date to today
        LocalDate currentDate = habit.getStartDate();
        LocalDate today = LocalDate.now();

        while (!currentDate.isAfter(today)) {
            boolean isRequired = isRequiredDay(currentDate, habit);
            boolean isCompleted = completedDates.contains(currentDate);

            if (isRequired) {
                if (isCompleted) {
                    currentStreak++;
                    if (streakStartDate == null) {
                        streakStartDate = currentDate;
                    }
                    bestStreak = Math.max(bestStreak, currentStreak);
                } else {
                    // Required day not completed - streak breaks
                    currentStreak = 0;
                    streakStartDate = null;
                }
            }
            // If not required, continue without affecting streak (Gap Forgiveness)

            currentDate = currentDate.plusDays(1);
        }

        return bestStreak;
    }
}
