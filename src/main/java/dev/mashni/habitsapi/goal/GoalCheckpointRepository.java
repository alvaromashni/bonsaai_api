package dev.mashni.habitsapi.goal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalCheckpointRepository extends JpaRepository<GoalCheckpoint, UUID> {

    /**
     * Find all checkpoints for a specific goal, ordered by date descending (most recent first).
     */
    List<GoalCheckpoint> findByGoalOrderByDateDesc(Goal goal);

    /**
     * Find a checkpoint by ID and goal (for security validation).
     */
    Optional<GoalCheckpoint> findByIdAndGoal(UUID id, Goal goal);
}
