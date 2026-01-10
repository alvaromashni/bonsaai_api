-- Migration V1: Add frequency support to habits
-- This migration adds the frequency_type column and habit_target_days table
-- to support habits with specific days of the week (e.g., gym on Mon/Wed/Fri)

-- 1. Add frequency_type column to habits table
ALTER TABLE habits ADD COLUMN IF NOT EXISTS frequency_type VARCHAR(20);

-- 2. Set default value for existing habits to avoid NULL values
-- All existing habits are treated as DAILY habits
UPDATE habits SET frequency_type = 'DAILY' WHERE frequency_type IS NULL;

-- 3. Make the column NOT NULL after setting defaults
ALTER TABLE habits ALTER COLUMN frequency_type SET NOT NULL;

-- 4. Create habit_target_days table for storing specific days of the week
-- This table is used when frequency_type = 'SPECIFIC_DAYS'
CREATE TABLE IF NOT EXISTS habit_target_days (
    habit_id UUID NOT NULL,
    day_of_week VARCHAR(20),
    CONSTRAINT fk_habit_target_days_habit FOREIGN KEY (habit_id) REFERENCES habits(id) ON DELETE CASCADE
);

-- 5. Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_habit_target_days_habit_id ON habit_target_days(habit_id);
