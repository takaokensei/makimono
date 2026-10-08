CREATE TABLE pairing_challenge (
    challenge_id       varchar(64) PRIMARY KEY,
    user_code          varchar(8) NOT NULL,
    poll_secret_hash   bytea NOT NULL CHECK (octet_length(poll_secret_hash) = 32),
    device_name        varchar(48) NOT NULL,
    client_version     varchar(32) NOT NULL,
    protocol_version   integer NOT NULL CHECK (protocol_version > 0),
    capabilities       text[] NOT NULL DEFAULT '{}',
    expires_at         timestamptz NOT NULL,
    state              varchar(16) NOT NULL CHECK (state IN (
        'PENDING', 'APPROVED', 'DENIED', 'CANCELLED', 'EXPIRED', 'CLAIMED'
    )),
    created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX pairing_challenge_pending_code_uq
    ON pairing_challenge (user_code)
    WHERE state = 'PENDING';

CREATE INDEX pairing_challenge_expiry_idx
    ON pairing_challenge (expires_at)
    WHERE state = 'PENDING';
