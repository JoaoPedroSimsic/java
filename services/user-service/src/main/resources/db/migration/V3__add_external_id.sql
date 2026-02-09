-- Add external_id column for OAuth provider user IDs (Auth0/Keycloak)
ALTER TABLE users ADD COLUMN external_id VARCHAR(255) UNIQUE;

-- Create index for faster lookups by external_id
CREATE INDEX idx_users_external_id ON users(external_id);

-- Make password nullable since external auth providers manage passwords
-- (password was already nullable in V1, so no change needed)
