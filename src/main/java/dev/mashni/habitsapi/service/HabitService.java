package dev.mashni.habitsapi.service;

import dev.mashni.habitsapi.dto.*;
import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import dev.mashni.habitsapi.model.HabitStatus;
import dev.mashni.habitsapi.model.User;
import dev.mashni.habitsapi.repository.HabitLogRepository;
import dev.mashni.habitsapi.repository.HabitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
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
        var habit = new Habit(request.name(), request.description(), request.startDate(), user);
        return habitRepository.save(habit);
    }

    @Transactional
    public HabitLog checkHabit(UUID habitId, CheckHabitRequest request, User user) {
        var habit = habitRepository.findByIdAndUser(habitId, user)
            .orElseThrow(() -> new IllegalArgumentException("Habit not found or does not belong to user"));

        var date = request.date() != null ? request.date() : LocalDate.now();
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
    public List<HabitSummaryResponse> getAllActiveHabits(User user) {
        var habits = habitRepository.findByUserAndStatus(user, HabitStatus.ACTIVE);
        return habits.stream()
            .map(this::mapToSummaryResponse)
            .collect(Collectors.toList());
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

        var currentStreak = calculateCurrentStreak(logs);
        var bestStreak = calculateBestStreak(logs);

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
        var logs = habitLogRepository.findByHabitIdOrderByCompletedDateDesc(habit.getId());
        var currentStreak = calculateCurrentStreak(logs);
        var bestStreak = calculateBestStreak(logs);

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
    public void deleteHabit(UUID habitId){
        habitRepository.deleteById(habitId);
    }

    /**
     * Calculate the current streak by counting consecutive days backward from today or yesterday.
     * If today is completed, count from today. Otherwise, count from yesterday.
     */
    private int calculateCurrentStreak(List<HabitLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }

        var today = LocalDate.now();
        var sortedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .sorted((d1, d2) -> d2.compareTo(d1)) // Sort descending (most recent first)
            .collect(Collectors.toList());

        // Start counting from today or yesterday
        var startDate = sortedDates.contains(today) ? today : today.minusDays(1);

        // If the most recent log is not today or yesterday, streak is 0
        if (!sortedDates.contains(startDate) && !sortedDates.contains(today)) {
            return 0;
        }

        int streak = 0;
        var currentDate = startDate;

        // Count backwards while dates are consecutive
        while (sortedDates.contains(currentDate)) {
            streak++;
            currentDate = currentDate.minusDays(1);
        }

        return streak;
    }

    /**
     * Calculate the best (longest) streak in the habit's history.
     */
    private int calculateBestStreak(List<HabitLog> logs) {
        if (logs.isEmpty()) {
            return 0;
        }

        var sortedDates = logs.stream()
            .map(HabitLog::getCompletedDate)
            .sorted()
            .collect(Collectors.toList());

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
