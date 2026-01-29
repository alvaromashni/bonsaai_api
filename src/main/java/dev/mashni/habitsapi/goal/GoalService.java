package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.goal.dto.CheckpointResponse;
import dev.mashni.habitsapi.goal.dto.CreateGoalRequest;
import dev.mashni.habitsapi.goal.dto.GoalDetailResponse;
import dev.mashni.habitsapi.goal.dto.GoalSummaryResponse;
import dev.mashni.habitsapi.goal.dto.UpdateGoalHabitsRequest;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.dto.HabitSummaryResponse;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.shared.exception.ResourceNotFoundException;
import dev.mashni.habitsapi.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;

    public GoalService(GoalRepository goalRepository, HabitRepository habitRepository) {
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
    }

    @Transactional
    public Goal createGoal(CreateGoalRequest request, User user) {
        // Validate user plan limits
        validateGoalLimit(user);

        // Create goal
        var goal = new Goal(user, request.title(), request.description(), request.deadline());

        // Link habits if provided
        if (request.habitIds() != null && !request.habitIds().isEmpty()) {
            Set<Habit> habits = validateAndGetHabits(request.habitIds(), user);
            goal.setHabits(habits);
        }

        return goalRepository.save(goal);
    }

    @Transactional(readOnly = true)
    public List<GoalSummaryResponse> getAllGoals(User user) {
        var goals = goalRepository.findByUserOrderByCreatedAtDesc(user);
        return goals.stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public GoalDetailResponse getGoalDetail(UUID goalId, User user) {
        var goal = goalRepository.findByIdAndUserWithHabitsAndCheckpoints(goalId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        var habitResponses = goal.getHabits().stream()
                .map(this::mapHabitToSummary)
                .collect(Collectors.toList());

        var checkpointResponses = goal.getCheckpoints().stream()
                .sorted(Comparator.comparing(GoalCheckpoint::getDate).reversed())
                .map(this::mapCheckpointToResponse)
                .collect(Collectors.toList());

        return new GoalDetailResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getDeadline(),
                goal.getStatus(),
                habitResponses,
                checkpointResponses,
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }

    @Transactional
    public Goal updateGoalHabits(UUID goalId, UpdateGoalHabitsRequest request, User user) {
        var goal = goalRepository.findByIdAndUserWithHabits(goalId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        // Validate and get new habits
        Set<Habit> newHabits = validateAndGetHabits(request.habitIds(), user);

        // Replace habits
        goal.getHabits().clear();
        goal.setHabits(newHabits);

        return goalRepository.save(goal);
    }

    @Transactional
    public Goal completeGoal(UUID goalId, User user) {
        var goal = goalRepository.findByIdAndUser(goalId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        goal.setStatus(GoalStatus.COMPLETED);
        return goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(UUID goalId, User user) {
        var goal = goalRepository.findByIdAndUser(goalId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Goal", goalId));

        goalRepository.delete(goal);
    }

    /**
     * Validate that non-PRO users don't exceed the 1 goal limit.
     * Active PRO users have unlimited goals.
     * Uses isPro() to check for active PRO plan (considers expiration).
     */
    private void validateGoalLimit(User user) {
        if (!user.isPro()) {
            long goalCount = goalRepository.countByUser(user);
            if (goalCount >= 1) {
                throw new IllegalArgumentException("Free users can only create 1 goal. Upgrade to PRO for unlimited goals.");
            }
        }
        // Active PRO users have no limit
    }

    /**
     * Validate that all habit IDs exist and belong to the user.
     */
    private Set<Habit> validateAndGetHabits(Set<UUID> habitIds, User user) {
        Set<Habit> habits = new HashSet<>();

        for (UUID habitId : habitIds) {
            var habit = habitRepository.findByIdAndUser(habitId, user)
                    .orElseThrow(() -> new ResourceNotFoundException("Habit", habitId));
            habits.add(habit);
        }

        return habits;
    }

    private GoalSummaryResponse mapToSummaryResponse(Goal goal) {
        return new GoalSummaryResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getDeadline(),
                goal.getStatus(),
                goal.getHabits().size(),
                goal.getCreatedAt()
        );
    }

    private HabitSummaryResponse mapHabitToSummary(Habit habit) {
        // Simplified mapping - in a real scenario you might want to calculate streaks
        return new HabitSummaryResponse(
                habit.getId(),
                habit.getName(),
                habit.getDescription(),
                0, // currentStreak - not calculated here to avoid N+1
                0, // bestStreak - not calculated here to avoid N+1
                habit.getLogs().size()
        );
    }

    private CheckpointResponse mapCheckpointToResponse(GoalCheckpoint checkpoint) {
        return new CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getNote(),
                checkpoint.getEmoji(),
                checkpoint.getDate()
        );
    }
}
