-- Migration V7: Add plan expiration tracking to users table
-- This migration adds support for time-limited PRO subscriptions

-- Add plan_expires_at column to track when PRO plan expires
ALTER TABLE users
ADD COLUMN plan_expires_at TIMESTAMP NULL;

-- Add index for optimization in scheduled downgrade job
CREATE INDEX idx_users_plan_expires_at ON users(plan_expires_at);

-- Add comment for documentation
COMMENT ON COLUMN users.plan_expires_at IS 'Timestamp when the PRO plan expires. NULL for FREE users or expired PRO users.';
