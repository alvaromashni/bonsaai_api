package dev.mashni.habitsapi.habit;

import dev.mashni.habitsapi.habit.model.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

    Optional<HabitLog> findByHabitIdAndCompletedDate(UUID habitId, LocalDate completedDate);

    List<HabitLog> findByHabitIdOrderByCompletedDateDesc(UUID habitId);

    @Query("SELECT hl FROM HabitLog hl WHERE hl.habit.id = :habitId AND hl.completedDate >= :startDate ORDER BY hl.completedDate DESC")
    List<HabitLog> findByHabitIdAndDateRange(@Param("habitId") UUID habitId, @Param("startDate") LocalDate startDate);
}
