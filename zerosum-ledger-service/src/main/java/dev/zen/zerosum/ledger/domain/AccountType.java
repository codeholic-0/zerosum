package dev.zen.zerosum.ledger.domain;

/** Should match the CHECK constraint on accounts.type in V1__accounts_and_journal.sql. */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
