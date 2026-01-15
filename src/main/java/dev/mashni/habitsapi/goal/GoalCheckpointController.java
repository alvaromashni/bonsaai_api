package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.goal.dto.CheckpointResponse;
import dev.mashni.habitsapi.goal.dto.CreateCheckpointRequest;
import dev.mashni.habitsapi.user.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller for managing goal checkpoints (progress milestones).
 * Provides endpoints for creating, listing, and deleting checkpoints.
 */
@RestController
@RequestMapping("/api/goals/{goalId}/checkpoints")
public class GoalCheckpointController {

    private final GoalCheckpointService checkpointService;
    private final UserService userService;

    public GoalCheckpointController(GoalCheckpointService checkpointService, UserService userService) {
        this.checkpointService = checkpointService;
        this.userService = userService;
    }

    /**
     * Create a new checkpoint for a goal.
     * Validates that the goal belongs to the authenticated user.
     */
    @PostMapping
    public ResponseEntity<CheckpointResponse> createCheckpoint(
            @PathVariable UUID goalId,
            @Valid @RequestBody CreateCheckpointRequest request,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var checkpoint = checkpointService.createCheckpoint(goalId, request, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(checkpoint);
    }

    /**
     * Get all checkpoints for a goal, ordered by date descending.
     * Validates that the goal belongs to the authenticated user.
     */
    @GetMapping
    public ResponseEntity<List<CheckpointResponse>> getCheckpoints(
            @PathVariable UUID goalId,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        var checkpoints = checkpointService.getCheckpointsByGoal(goalId, user);
        return ResponseEntity.ok(checkpoints);
    }

    /**
     * Delete a checkpoint.
     * Validates that the goal belongs to the authenticated user.
     */
    @DeleteMapping("/{checkpointId}")
    public ResponseEntity<Void> deleteCheckpoint(
            @PathVariable UUID goalId,
            @PathVariable UUID checkpointId,
            Authentication authentication) {
        var user = userService.getUserFromAuthentication(authentication);
        checkpointService.deleteCheckpoint(goalId, checkpointId, user);
        return ResponseEntity.ok().build();
    }
}
