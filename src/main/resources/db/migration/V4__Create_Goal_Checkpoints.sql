-- Migration V4: Create Goal Checkpoints feature
-- This migration creates the goal_checkpoints table for tracking progress milestones

-- 1. Create goal_checkpoints table
CREATE TABLE goal_checkpoints (
    id UUID PRIMARY KEY,
    goal_id UUID NOT NULL,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    note TEXT NOT NULL,
    emoji VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goal_checkpoints_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE
);

-- 2. Create index for better query performance on goal_id
CREATE INDEX idx_goal_checkpoints_goal_id ON goal_checkpoints(goal_id);
