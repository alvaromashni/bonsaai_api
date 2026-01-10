package dev.mashni.habitsapi.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

@Entity
@Table(name = "habits")
@SQLDelete(sql = "UPDATE habits SET deleted_at = NOW() WHERE id = ?")
@Where(clause = "deleted_at IS NULL")
public class Habit {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @NotBlank(message = "Habit name is required")
    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @NotNull(message = "Start date is required")
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HabitStatus status = HabitStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", nullable = false)
    private FrequencyType frequencyType = FrequencyType.DAILY;

    @ElementCollection(targetClass = DayOfWeek.class)
    @CollectionTable(name = "habit_target_days", joinColumns = @JoinColumn(name = "habit_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private Set<DayOfWeek> targetDays = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @NotNull(message = "User is required")
    private User user;

    @OneToMany(mappedBy = "habit", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HabitLog> logs = new ArrayList<>();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public Habit() {
    }

    public Habit(String name, String description, LocalDate startDate, User user) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.user = user;
        this.status = HabitStatus.ACTIVE;
        this.frequencyType = FrequencyType.DAILY;
        this.targetDays = new HashSet<>();
    }

    public Habit(String name, String description, LocalDate startDate, User user, FrequencyType frequencyType, Set<DayOfWeek> targetDays) {
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.user = user;
        this.status = HabitStatus.ACTIVE;
        this.frequencyType = frequencyType != null ? frequencyType : FrequencyType.DAILY;
        this.targetDays = targetDays != null ? targetDays : new HashSet<>();
    }

    // Getters and Setters
    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public HabitStatus getStatus() {
        return status;
    }

    public void setStatus(HabitStatus status) {
        this.status = status;
    }

    public List<HabitLog> getLogs() {
        return logs;
    }

    public void setLogs(List<HabitLog> logs) {
        this.logs = logs;
    }

    public void addLog(HabitLog log) {
        logs.add(log);
        log.setHabit(this);
    }

    public void removeLog(HabitLog log) {
        logs.remove(log);
        log.setHabit(null);
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public FrequencyType getFrequencyType() {
        return frequencyType;
    }

    public void setFrequencyType(FrequencyType frequencyType) {
        this.frequencyType = frequencyType;
    }

    public Set<DayOfWeek> getTargetDays() {
        return targetDays;
    }

    public void setTargetDays(Set<DayOfWeek> targetDays) {
        this.targetDays = targetDays;
    }
}
