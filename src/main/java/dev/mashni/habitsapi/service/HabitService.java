package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.dto.*;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.model.HabitStatus;
import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.HabitLogRepository;
import dev.mashni.habitsapi.repository.HabitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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

        var habit = new Habit(request.name(), request.description(), request.startDate(), user);
        return habitRepository.save(habit);
    }

    @Transactional
    public HabitLog checkHabit(UUID habitId, CheckHabitRequest request, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new IllegalArgumentException("Habit not found or does not belong to user"));

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
        var habitOpt = habitRepository.findByIdAndUserWithLogs(habitId, user);

        if (habitOpt.isEmpty()) {
            throw new IllegalArgumentException("Habit not found or does not belong to user");
        }

        var habit = habitOpt.get();
        var logs = habitLogRepository.findByHabitIdOrderByCompletedDateDesc(habitId);
        var completedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .sorted()
            .collect(Collectors.toList());

        var currentStreak = calculateCurrentStreak(logs, habit.getStartDate());
        var bestStreak = calculateBestStreak(logs, habit.getStartDate());

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
        var currentStreak = calculateCurrentStreak(logs, habit.getStartDate());
        var bestStreak = calculateBestStreak(logs, habit.getStartDate());

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
            .orElseThrow(() -> new IllegalArgumentException("Habit not found or does not belong to user"));

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
            .orElseThrow(() -> new IllegalArgumentException("Habit not found or does not belong to user"));
        habitRepository.delete(habit);
    }

    @Transactional
    public Habit archiveHabit(UUID habitId, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new IllegalArgumentException("Habit not found or does not belong to user"));
        habit.setStatus(HabitStatus.ARCHIVED);
        return habitRepository.save(habit);
    }

    /**
     * Calculate the current streak by counting consecutive days backward from today or yesterday.
     * If today is completed, count from today. Otherwise, count from yesterday.
     * Days before the habit's start date are not counted.
     */
    private int calculateCurrentStreak(List<HabitLog> logs, LocalDate habitStartDate) {
        if (logs.isEmpty()) {
            return 0;
        }

        var today = LocalDate.now();
        var sortedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .sorted((d1, d2) -> d2.compareTo(d1)) // Sort descending (most recent first)
            .toList();

        // Start counting from today or yesterday
        var startDate = sortedDates.contains(today) ? today : today.minusDays(1);

        // If the most recent log is not today or yesterday, streak is 0
        if (!sortedDates.contains(startDate) && !sortedDates.contains(today)) {
            return 0;
        }

        int streak = 0;
        var currentDate = startDate;

        // Count backwards while dates are consecutive and not before habit start date
        while (sortedDates.contains(currentDate) && !currentDate.isBefore(habitStartDate)) {
            streak++;
            currentDate = currentDate.minusDays(1);
        }

        return streak;
    }

    /**
     * Calculate the best (longest) streak in the habit's history.
     * Only considers dates from the habit's start date onwards.
     */
    private int calculateBestStreak(List<HabitLog> logs, LocalDate habitStartDate) {
        if (logs.isEmpty()) {
            return 0;
        }

        // Filter logs to only include dates from start date onwards
        var sortedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .filter(date -> !date.isBefore(habitStartDate))
            .sorted()
            .collect(Collectors.toList());

        if (sortedDates.isEmpty()) {
            return 0;
        }

        int currentStreak = 1;
        int bestStreak = 1;

        for (int i = 1; i < sortedDates.size(); i++) {
            var previousDate = sortedDates.get(i - 1);
            var currentDate = sortedDates.get(i);

            // Check if dates are consecutive
            if (currentDate.equals(previousDate.plusDays(1))) {
                currentStreak++;
                bestStreak = Math.max(bestStreak, currentStreak);
            } else {
                currentStreak = 1;
            }
        }

        return bestStreak;
    }
}
