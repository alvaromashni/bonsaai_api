package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.goal.dto.CreateGoalRequest;
import dev.mashni.habitsapi.goal.dto.GoalDetailResponse;
import dev.mashni.habitsapi.goal.dto.GoalSummaryResponse;
import dev.mashni.habitsapi.goal.dto.UpdateGoalHabitsRequest;
import dev.mashni.habitsapi.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing user goals.
 * Provides endpoints for creating, reading, updating, and managing goals.
 */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;
    private final UserService userService;

    public GoalController(GoalService goalService, UserService userService) {
        this.goalService = goalService;
        this.userService = userService;
    }

    /**
     * Create a new goal.
     * FREE users can create max 1 goal, PRO users have unlimited goals.
     */
    @PostMapping
    public ResponseEntity<GoalSummaryResponse> createGoal(
            @Valid @RequestBody CreateGoalRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var goal = goalService.createGoal(request, user);

        var response = new GoalSummaryResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getDeadline(),
                goal.getStatus(),
                goal.getHabits().size(),
                goal.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get all goals for the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<GoalSummaryResponse>> getAllGoals(Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var goals = goalService.getAllGoals(user);
        return ResponseEntity.ok(goals);
    }

    /**
     * Get detailed information about a specific goal including linked habits.
     */
    @GetMapping("/{id}")
    public ResponseEntity<GoalDetailResponse> getGoalDetail(
            @PathVariable UUID id,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var goal = goalService.getGoalDetail(id, user);
        return ResponseEntity.ok(goal);
    }

    /**
     * Update the habits linked to a goal.
     * This replaces the entire list of habits with the provided IDs.
     */
    @PutMapping("/{id}/habits")
    public ResponseEntity<GoalDetailResponse> updateGoalHabits(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateGoalHabitsRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        goalService.updateGoalHabits(id, request, user);
        var updatedGoal = goalService.getGoalDetail(id, user);
        return ResponseEntity.ok(updatedGoal);
    }

    /**
     * Mark a goal as completed.
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<GoalSummaryResponse> completeGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var goal = goalService.completeGoal(id, user);

        var response = new GoalSummaryResponse(
                goal.getId(),
                goal.getTitle(),
                goal.getDescription(),
                goal.getDeadline(),
                goal.getStatus(),
                goal.getHabits().size(),
                goal.getCreatedAt()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Delete a goal.
     * This does NOT delete the linked habits, only the goal itself.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable UUID id,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        goalService.deleteGoal(id, user);
        return ResponseEntity.ok().build();
    }
}
