package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.goal.dto.CheckpointResponse;
import dev.mashni.habitsapi.goal.dto.CreateCheckpointRequest;
import dev.mashni.habitsapi.user.User;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GoalCheckpointService {

    private final GoalCheckpointRepository checkpointRepository;
    private final GoalRepository goalRepository;

    public GoalCheckpointService(GoalCheckpointRepository checkpointRepository, GoalRepository goalRepository) {
        this.checkpointRepository = checkpointRepository;
        this.goalRepository = goalRepository;
    }

    @Transactional
    public CheckpointResponse createCheckpoint(UUID goalId, CreateCheckpointRequest request, User currentUser) {
        Goal goal = findGoalAndValidateOwnership(goalId, currentUser);

        GoalCheckpoint checkpoint = new GoalCheckpoint(
                goal,
                request.date(),
                request.note(),
                request.emoji()
        );

        GoalCheckpoint saved = checkpointRepository.save(checkpoint);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<CheckpointResponse> getCheckpointsByGoal(UUID goalId, User currentUser) {
        Goal goal = findGoalAndValidateOwnership(goalId, currentUser);

        return checkpointRepository.findByGoalOrderByDateDesc(goal).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteCheckpoint(UUID goalId, UUID checkpointId, User currentUser) {
        Goal goal = findGoalAndValidateOwnership(goalId, currentUser);

        GoalCheckpoint checkpoint = checkpointRepository.findByIdAndGoal(checkpointId, goal)
                .orElseThrow(() -> new IllegalArgumentException("Checkpoint not found"));

        checkpointRepository.delete(checkpoint);
    }

    private Goal findGoalAndValidateOwnership(UUID goalId, User currentUser) {
        Goal goal = goalRepository.findById(goalId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));

        if (!goal.getUser().getId().equals(currentUser.getId())) {
            throw new AccessDeniedException("You don't have permission to access this goal");
        }

        return goal;
    }

    private CheckpointResponse mapToResponse(GoalCheckpoint checkpoint) {
        return new CheckpointResponse(
                checkpoint.getId(),
                checkpoint.getNote(),
                checkpoint.getEmoji(),
                checkpoint.getDate()
        );
    }
}
