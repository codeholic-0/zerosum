CREATE TABLE accounts (
    id          UUID PRIMARY KEY,
    code        TEXT NOT NULL UNIQUE,
    name        TEXT NOT NULL,
    currency    CHAR(3) NOT NULL,
    type        TEXT NOT NULL CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, currency)
);

CREATE TABLE transfers (
    id          UUID PRIMARY KEY,
    reference   TEXT,
    description TEXT,
    currency    CHAR(3) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, currency)
);

CREATE TABLE journal_entries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    transfer_id   UUID NOT NULL,
    account_id    UUID NOT NULL,
    direction     TEXT NOT NULL CHECK (direction IN ('DEBIT','CREDIT')),
    amount_minor  BIGINT NOT NULL CHECK (amount_minor > 0),
    currency      CHAR(3) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (transfer_id, currency) REFERENCES transfers (id, currency),
    FOREIGN KEY (account_id, currency)  REFERENCES accounts (id, currency)
);

CREATE INDEX idx_entries_account  ON journal_entries (account_id, id);
CREATE INDEX idx_entries_transfer ON journal_entries (transfer_id);

-- Balanced check, evaluated at COMMIT so all entries of a transfer can be inserted first
CREATE FUNCTION check_transfer_balanced() RETURNS trigger AS $$
DECLARE
    diff BIGINT;
BEGIN
    SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0)
      INTO diff
      FROM journal_entries
     WHERE transfer_id = NEW.transfer_id;
    IF diff <> 0 THEN
        RAISE EXCEPTION 'transfer % is unbalanced by %', NEW.transfer_id, diff;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_transfer_balanced
    AFTER INSERT ON journal_entries
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_transfer_balanced();

-- Append-only: block UPDATE and DELETE
CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% on % is not allowed: the ledger is append-only', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_entries_immutable
    BEFORE UPDATE OR DELETE ON journal_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE TRIGGER trg_transfers_immutable
    BEFORE UPDATE OR DELETE ON transfers
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
