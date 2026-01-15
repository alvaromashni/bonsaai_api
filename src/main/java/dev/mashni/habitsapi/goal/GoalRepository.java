package dev.mashni.habitsapi.goal;

import dev.mashni.habitsapi.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GoalRepository extends JpaRepository<Goal, UUID> {

    /**
     * Find all goals for a specific user.
     */
    List<Goal> findByUserOrderByCreatedAtDesc(User user);

    /**
     * Find a goal by ID and user (for security validation).
     */
    Optional<Goal> findByIdAndUser(UUID id, User user);

    /**
     * Find a goal by ID and user with habits eagerly loaded.
     */
    @Query("SELECT g FROM Goal g LEFT JOIN FETCH g.habits WHERE g.id = :id AND g.user = :user")
    Optional<Goal> findByIdAndUserWithHabits(@Param("id") UUID id, @Param("user") User user);

    /**
     * Find a goal by ID and user with habits and checkpoints eagerly loaded.
     */
    @Query("SELECT DISTINCT g FROM Goal g LEFT JOIN FETCH g.habits LEFT JOIN FETCH g.checkpoints WHERE g.id = :id AND g.user = :user")
    Optional<Goal> findByIdAndUserWithHabitsAndCheckpoints(@Param("id") UUID id, @Param("user") User user);

    /**
     * Count goals for a specific user (for FREE plan limit validation).
     */
    long countByUser(User user);
}
