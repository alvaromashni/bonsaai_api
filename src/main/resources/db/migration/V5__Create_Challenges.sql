-- Migration V5: Create Challenges (Squads) feature
-- This migration creates the challenges table and adds the challenge_id column to habits

-- 1. Create challenges table
CREATE TABLE challenges (
    id UUID PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    creator_id UUID NOT NULL,
    invite_code VARCHAR(10) UNIQUE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_challenges_creator FOREIGN KEY (creator_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2. Create indexes for better query performance
CREATE INDEX idx_challenges_creator_id ON challenges(creator_id);
CREATE INDEX idx_challenges_invite_code ON challenges(invite_code);
CREATE INDEX idx_challenges_start_date ON challenges(start_date);

-- 3. Add challenge_id column to habits table
ALTER TABLE habits ADD COLUMN challenge_id UUID;

-- 4. Add foreign key constraint for challenge_id
ALTER TABLE habits ADD CONSTRAINT fk_habits_challenge
    FOREIGN KEY (challenge_id) REFERENCES challenges(id) ON DELETE SET NULL;

-- 5. Create index for challenge_id in habits
CREATE INDEX idx_habits_challenge_id ON habits(challenge_id);
