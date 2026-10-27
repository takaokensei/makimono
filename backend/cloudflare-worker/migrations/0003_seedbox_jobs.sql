CREATE TABLE IF NOT EXISTS seedbox_daemons (
    daemon_token_hash TEXT PRIMARY KEY CHECK (length(daemon_token_hash) = 64),
    google_sub TEXT NOT NULL REFERENCES accounts(google_sub) ON DELETE CASCADE,
    last_heartbeat_at INTEGER,
    free_disk_gb REAL,
    active_speed TEXT,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS seedbox_daemons_sub ON seedbox_daemons(google_sub);

CREATE TABLE IF NOT EXISTS seedbox_jobs (
    job_id TEXT PRIMARY KEY,
    google_sub TEXT NOT NULL REFERENCES accounts(google_sub) ON DELETE CASCADE,
    anime_query TEXT NOT NULL,
    episodes_filter TEXT NOT NULL DEFAULT 'all',
    prefer_ptbr INTEGER NOT NULL DEFAULT 1,
    magnet_uri TEXT,
    status TEXT NOT NULL CHECK (status IN ('PENDING', 'SEARCHING', 'DOWNLOADING', 'UPLOADING', 'COMPLETED', 'FAILED', 'CANCELLED')),
    progress_pct INTEGER NOT NULL DEFAULT 0 CHECK (progress_pct BETWEEN 0 AND 100),
    transfer_speed TEXT,
    drive_folder_id TEXT,
    status_detail TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS seedbox_jobs_sub ON seedbox_jobs(google_sub, status);
