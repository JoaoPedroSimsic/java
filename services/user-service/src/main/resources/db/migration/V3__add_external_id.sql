ALTER TABLE users ADD COLUMN external_id VARCHAR(255) UNIQUE;

CREATE INDEX idx_users_external_id ON users(external_id);
