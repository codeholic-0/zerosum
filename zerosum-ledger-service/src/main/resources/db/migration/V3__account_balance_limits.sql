ALTER TABLE accounts
    ADD COLUMN min_balance_minor BIGINT,
    ADD CONSTRAINT accounts_min_balance_not_positive CHECK (min_balance_minor <= 0);

-- One row per CONSTRAINED account. The limit is copied here so the database can enforce it
-- with a plain CHECK; it never changes after the account is created.
CREATE TABLE account_balances (
    account_id        UUID   PRIMARY KEY REFERENCES accounts (id),
    balance_minor     BIGINT NOT NULL DEFAULT 0,
    min_balance_minor BIGINT NOT NULL,
    CONSTRAINT limit_not_positive CHECK (min_balance_minor <= 0),
    CONSTRAINT balance_within_limit CHECK (balance_minor >= min_balance_minor)
);
