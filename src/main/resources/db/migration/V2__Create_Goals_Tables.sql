-- Migration V2: Create Goals feature
-- This migration creates the goals table and the many-to-many relationship with habits

-- 1. Create goals table
CREATE TABLE goals (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    deadline TIMESTAMP,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2. Create index for better query performance
CREATE INDEX idx_goals_user_id ON goals(user_id);
CREATE INDEX idx_goals_status ON goals(status);

-- 3. Create goal_habits junction table (Many-to-Many relationship)
CREATE TABLE goal_habits (
    goal_id UUID NOT NULL,
    habit_id UUID NOT NULL,
    PRIMARY KEY (goal_id, habit_id),
    CONSTRAINT fk_goal_habits_goal FOREIGN KEY (goal_id) REFERENCES goals(id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_habits_habit FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE
);

-- 4. Create indexes for junction table
CREATE INDEX idx_goal_habits_goal_id ON goal_habits(goal_id);
CREATE INDEX idx_goal_habits_habit_id ON goal_habits(habit_id);
