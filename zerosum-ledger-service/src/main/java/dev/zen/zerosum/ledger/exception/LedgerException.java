package dev.zen.zerosum.ledger.exception;

import dev.zen.zerosum.ledger.domain.Money;

/** Base type for all business-rule violations in the ledger * */
public abstract sealed class LedgerException extends RuntimeException
        permits LedgerException.InvalidTransfer,
                LedgerException.UnbalancedTransfer,
                LedgerException.AccountNotFound,
                LedgerException.CurrencyMismatch,
                LedgerException.InvalidInput,
                LedgerException.DuplicateAccount,
                LedgerException.IdempotencyConflict {

    protected LedgerException(String message) {
        super(message);
    }

    /** Structural problems: non-positive amount, same account on both sides. */
    public static final class InvalidTransfer extends LedgerException {
        public InvalidTransfer(String message) {
            super(message);
        }
    }

    /** Total debits differ from total credits. */
    public static final class UnbalancedTransfer extends LedgerException {
        public UnbalancedTransfer(Money debits, Money credits) {
            super("Transfer is unbalanced: debits=" + debits + ", credits=" + credits);
        }
    }

    /** One or more referenced accounts do not exist. */
    public static final class AccountNotFound extends LedgerException {
        public AccountNotFound(String message) {
            super(message);
        }
    }

    /** A leg or account currency differs from the transfer currency. */
    public static final class CurrencyMismatch extends LedgerException {
        public CurrencyMismatch(String message) {
            super(message);
        }
    }

    /** Well-formed input that is not a valid value, e.g. an unknown currency code. */
    public static final class InvalidInput extends LedgerException {
        public InvalidInput(String message) {
            super(message);
        }
    }

    /** An account with this code already exists. */
    public static final class DuplicateAccount extends LedgerException {
        public DuplicateAccount(String code) {
            super("An account with code '" + code + "' already exists");
        }
    }

    /** An idempotency key was reused with a different request. */
    public static final class IdempotencyConflict extends LedgerException {
        public IdempotencyConflict(String key) {
            super("Idempotency key '" + key + "' was already used with a different request");
        }
    }
}
