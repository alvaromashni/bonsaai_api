package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.goal.dto.CreateGoalRequest;
import dev.mashni.habitsapi.goal.dto.GoalDetailResponse;
import dev.mashni.habitsapi.goal.dto.GoalSummaryResponse;
import dev.mashni.habitsapi.goal.dto.UpdateGoalHabitsRequest;
import dev.mashni.habitsapi.habit.model.Habit;
import dev.mashni.habitsapi.habit.dto.HabitSummaryResponse;
import dev.mashni.habitsapi.habit.HabitRepository;
import dev.mashni.habitsapi.user.User;
import dev.mashni.habitsapi.user.UserPlan;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        var goal = goalRepository.findByIdAndUserWithHabits(goalId, user)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found or does not belong to user"));

        var habitResponses = goal.getHabits().stream()
                .map(this::mapHabitToSummary)
                .collect(Collectors.toList());

        return new GoalDetailResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getDeadline(),
                goal.getStatus(),
                habitResponses,
                goal.getCreatedAt(),
                goal.getUpdatedAt()
        );
    }

    @Transactional
    public Goal updateGoalHabits(UUID goalId, UpdateGoalHabitsRequest request, User user) {
        var goal = goalRepository.findByIdAndUserWithHabits(goalId, user)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found or does not belong to user"));

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
                .orElseThrow(() -> new IllegalArgumentException("Goal not found or does not belong to user"));

        goal.setStatus(GoalStatus.COMPLETED);
        return goalRepository.save(goal);
    }

    @Transactional
    public void deleteGoal(UUID goalId, User user) {
        var goal = goalRepository.findByIdAndUser(goalId, user)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found or does not belong to user"));

        goalRepository.delete(goal);
    }

    /**
     * Validate that FREE users don't exceed the 1 goal limit.
     * PRO users have unlimited goals.
     */
    private void validateGoalLimit(User user) {
        if (user.getUserPlan() == UserPlan.FREE) {
            long goalCount = goalRepository.countByUser(user);
            if (goalCount >= 1) {
                throw new IllegalArgumentException("Free users can only create 1 goal. Upgrade to PRO for unlimited goals.");
            }
        }
        // PRO users have no limit
    }

    /**
     * Validate that all habit IDs exist and belong to the user.
     */
    private Set<Habit> validateAndGetHabits(Set<UUID> habitIds, User user) {
        Set<Habit> habits = new HashSet<>();

        for (UUID habitId : habitIds) {
            var habit = habitRepository.findByIdAndUser(habitId, user)
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Habit with ID " + habitId + " not found or does not belong to user"
                    ));
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
}
