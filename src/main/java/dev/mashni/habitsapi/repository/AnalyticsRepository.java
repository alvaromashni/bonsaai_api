package dev.mashni.habitsapi.repository;

import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AnalyticsRepository extends JpaRepository<HabitLog, Long> {

    /**
     * Gets all habit logs for a user.
     */
    @Query("SELECT hl FROM HabitLog hl WHERE hl.habit.user.id = :userId ORDER BY hl.completedDate")
    List<HabitLog> findAllLogsByUserId(@Param("userId") UUID userId);

    /**
     * Gets all active habits for a user.
     */
    @Query("SELECT h FROM Habit h WHERE h.user.id = :userId AND h.status = 'ACTIVE' AND h.deletedAt IS NULL")
    List<Habit> findActiveHabitsByUserId(@Param("userId") UUID userId);

    /**
     * Gets all distinct completed dates for a user's habits (for heatmap).
     */
    @Query(value = """
        SELECT hl.completed_date as date, COUNT(*) as count
        FROM habit_logs hl
        INNER JOIN habits h ON hl.habit_id = h.id
        WHERE h.user_id = :userId
        AND hl.completed_date >= :startDate
        GROUP BY hl.completed_date
        ORDER BY hl.completed_date
        """, nativeQuery = true)
    List<Object[]> findHeatmapData(@Param("userId") UUID userId, @Param("startDate") LocalDate startDate);

    /**
     * Gets all distinct completed dates for a user (for streak calculation).
     */
    @Query("SELECT DISTINCT hl.completedDate FROM HabitLog hl WHERE hl.habit.user.id = :userId ORDER BY hl.completedDate DESC")
    List<LocalDate> findDistinctCompletedDatesByUserId(@Param("userId") UUID userId);
}
