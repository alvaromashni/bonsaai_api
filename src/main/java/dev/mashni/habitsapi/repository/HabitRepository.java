package dev.mashni.habitsapi.repository;

import dev.mashni.habitsapi.model.Habit;
import dev.mashni.habitsapi.model.HabitStatus;
import dev.mashni.habitsapi.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HabitRepository extends JpaRepository<Habit, UUID> {

    List<Habit> findByStatus(HabitStatus status);

    List<Habit> findByUserAndStatus(User user, HabitStatus status);

    Optional<Habit> findByIdAndUser(UUID id, User user);

    @Query("SELECT h FROM Habit h LEFT JOIN FETCH h.logs WHERE h.id = :id")
    Habit findByIdWithLogs(@Param("id") UUID id);

    @Query("SELECT h FROM Habit h LEFT JOIN FETCH h.logs WHERE h.id = :id AND h.user = :user")
    Optional<Habit> findByIdAndUserWithLogs(@Param("id") UUID id, @Param("user") User user);

    @Query(value = "SELECT DISTINCT h FROM Habit h LEFT JOIN FETCH h.logs WHERE h.user = :user AND h.status = :status",
           countQuery = "SELECT COUNT(h) FROM Habit h WHERE h.user = :user AND h.status = :status")
    Page<Habit> findByUserAndStatusWithLogs(@Param("user") User user, @Param("status") HabitStatus status, Pageable pageable);

    long countByUser(User user);
}
