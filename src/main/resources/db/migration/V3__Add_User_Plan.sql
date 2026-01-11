-- Migration V3: Add user plan to users table
-- This migration adds the user_plan column to support FREE and PRO tiers

-- 1. Add user_plan column to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS user_plan VARCHAR(20);

-- 2. Set default value for existing users to FREE
UPDATE users SET user_plan = 'FREE' WHERE user_plan IS NULL;

-- 3. Make the column NOT NULL after setting defaults
ALTER TABLE users ALTER COLUMN user_plan SET NOT NULL;
