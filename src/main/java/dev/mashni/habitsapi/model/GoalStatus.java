package dev.mashni.habitsapi.model;

/**
 * Status of a goal.
 * - IN_PROGRESS: Goal is actively being pursued
 * - COMPLETED: Goal has been successfully achieved
 * - ABANDONED: Goal was given up or cancelled
 */
public enum GoalStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}
