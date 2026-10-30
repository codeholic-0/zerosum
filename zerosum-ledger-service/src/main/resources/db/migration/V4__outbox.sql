CREATE TABLE outbox (
    id             UUID        PRIMARY KEY,
    aggregatetype  TEXT        NOT NULL,
    aggregateid    TEXT        NOT NULL,
    type           TEXT        NOT NULL,
    event_version  INTEGER     NOT NULL CHECK (event_version > 0),
    payload        JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_created_at ON outbox (created_at);
