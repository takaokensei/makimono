CREATE TABLE IF NOT EXISTS pairing_challenge (
    challenge_id TEXT PRIMARY KEY,
    user_code TEXT NOT NULL,
    poll_secret_hash TEXT NOT NULL CHECK (length(poll_secret_hash) = 64),
    device_name TEXT NOT NULL CHECK (length(device_name) BETWEEN 1 AND 48),
    client_version TEXT NOT NULL CHECK (length(client_version) BETWEEN 1 AND 32),
    protocol_version INTEGER NOT NULL CHECK (protocol_version > 0),
    capabilities_json TEXT NOT NULL,
    state TEXT NOT NULL CHECK (state IN ('PENDING', 'APPROVED', 'DENIED', 'CANCELLED', 'EXPIRED', 'CLAIMED')),
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS pairing_challenge_active_user_code
    ON pairing_challenge(user_code)
    WHERE state = 'PENDING';

CREATE INDEX IF NOT EXISTS pairing_challenge_expiry
    ON pairing_challenge(state, expires_at);
