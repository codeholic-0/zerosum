package dev.zen.zerosum.ledger.enums;

/** Side of a journal entry. Balance convention: credits minus debits. */
public enum Direction {
    DEBIT(-1),
    CREDIT(1);

    private final int sign;

    Direction(int sign) {
        this.sign = sign;
    }

    /** +1 for credit, -1 for debit; multiply by the amount to get its effect on balance. */
    public int sign() {
        return sign;
    }
}
