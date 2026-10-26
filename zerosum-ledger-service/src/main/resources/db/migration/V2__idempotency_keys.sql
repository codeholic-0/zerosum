CREATE TABLE idempotency_keys (
    idempotency_key  TEXT        PRIMARY KEY,
    request_hash     TEXT        NOT NULL,
    transfer_id      UUID        REFERENCES transfers (id),
    response_status  INTEGER,
    response_body    JSONB,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at       TIMESTAMPTZ NOT NULL,
    -- the response columns are filled together, in the same transaction that claims the key
    CONSTRAINT idempotency_response_complete CHECK (
        (transfer_id IS NULL) = (response_status IS NULL)
        AND (transfer_id IS NULL) = (response_body IS NULL)
    )
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
