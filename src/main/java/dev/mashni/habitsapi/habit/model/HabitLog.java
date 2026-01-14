package dev.mashni.habitsapi.habit.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Entity
@Table(
    name = "habit_logs",
    uniqueConstraints = @UniqueConstraint(columnNames = {"habit_id", "completed_date"})
)
public class HabitLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habit_id", nullable = false)
    @NotNull(message = "Habit is required")
    private Habit habit;

    @NotNull(message = "Completed date is required")
    @Column(name = "completed_date", nullable = false)
    private LocalDate completedDate;

    public HabitLog() {
    }

    public HabitLog(Habit habit, LocalDate completedDate) {
        this.habit = habit;
        this.completedDate = completedDate;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Habit getHabit() {
        return habit;
    }

    public void setHabit(Habit habit) {
        this.habit = habit;
    }

    public LocalDate getCompletedDate() {
        return completedDate;
    }

    public void setCompletedDate(LocalDate completedDate) {
        this.completedDate = completedDate;
    }
}
