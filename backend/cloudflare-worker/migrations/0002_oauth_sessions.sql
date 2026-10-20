CREATE TABLE IF NOT EXISTS pairings (
    pairing_id TEXT PRIMARY KEY,
    device_secret_hash TEXT NOT NULL CHECK (length(device_secret_hash) = 64),
    confirm_code_hash TEXT NOT NULL CHECK (length(confirm_code_hash) = 64),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    device_name TEXT NOT NULL CHECK (length(device_name) BETWEEN 1 AND 48),
    client_version TEXT NOT NULL CHECK (length(client_version) BETWEEN 1 AND 32),
    protocol_version INTEGER NOT NULL CHECK (protocol_version > 0),
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'APPROVED', 'CONSUMED', 'DENIED', 'CANCELLED', 'EXPIRED')),
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    browser_cookie_hash TEXT CHECK (browser_cookie_hash IS NULL OR length(browser_cookie_hash) = 64),
    google_sub TEXT
);

CREATE INDEX IF NOT EXISTS pairings_expiry ON pairings(status, expires_at);
CREATE INDEX IF NOT EXISTS pairings_google_sub ON pairings(google_sub);

CREATE TABLE IF NOT EXISTS oauth_states (
    state_hash TEXT PRIMARY KEY CHECK (length(state_hash) = 64),
    pairing_id TEXT NOT NULL REFERENCES pairings(pairing_id) ON DELETE CASCADE,
    browser_cookie_hash TEXT NOT NULL CHECK (length(browser_cookie_hash) = 64),
    nonce_hash TEXT NOT NULL CHECK (length(nonce_hash) = 64),
    verifier_cipher TEXT NOT NULL,
    redirect_uri TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL,
    consumed_at INTEGER
);

CREATE INDEX IF NOT EXISTS oauth_states_expiry ON oauth_states(expires_at);
CREATE INDEX IF NOT EXISTS oauth_states_pairing ON oauth_states(pairing_id);

CREATE TABLE IF NOT EXISTS accounts (
    google_sub TEXT PRIMARY KEY,
    email TEXT NOT NULL,
    refresh_token_cipher TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
    session_hash TEXT PRIMARY KEY CHECK (length(session_hash) = 64),
    pairing_id TEXT NOT NULL UNIQUE REFERENCES pairings(pairing_id) ON DELETE CASCADE,
    google_sub TEXT NOT NULL REFERENCES accounts(google_sub) ON DELETE CASCADE,
    created_at INTEGER NOT NULL,
    last_seen_at INTEGER,
    revoked_at INTEGER
);

CREATE INDEX IF NOT EXISTS sessions_google_sub ON sessions(google_sub, revoked_at);

CREATE TABLE IF NOT EXISTS rate_limits (
    rate_key TEXT PRIMARY KEY CHECK (length(rate_key) = 64),
    window_start INTEGER NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0)
);
