CREATE TYPE outbox_event_status AS ENUM ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED');

CREATE TABLE outbox_events
(
    id           UUID PRIMARY KEY    NOT NULL,
    event_type   TEXT                NOT NULL,
    routing_key  TEXT                NOT NULL,
    payload      JSONB               NOT NULL,
    status       outbox_event_status NOT NULL DEFAULT 'PENDING',
    attempts     INTEGER             NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    CONSTRAINT outbox_events_attempts_chk CHECK (attempts >= 0)
);

CREATE INDEX idx_outbox_events_status_created_at
    ON outbox_events (status, created_at);
