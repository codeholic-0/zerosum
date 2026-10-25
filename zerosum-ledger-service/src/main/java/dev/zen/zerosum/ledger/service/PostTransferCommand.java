package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.Direction;
import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PostTransferCommand(
        String reference, String description, Currency currency, List<Leg> legs) {

    public PostTransferCommand {
        Objects.requireNonNull(currency, "currency");
        legs = List.copyOf(Objects.requireNonNull(legs, "legs"));
    }

    public record Leg(UUID accountId, Direction direction, Money amount) {
        public Leg {
            Objects.requireNonNull(accountId, "accountId");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(amount, "amount");
        }
    }

    public static Leg debit(UUID accountId, Money amount) {
        return new Leg(accountId, Direction.DEBIT, amount);
    }

    public static Leg credit(UUID accountId, Money amount) {
        return new Leg(accountId, Direction.CREDIT, amount);
    }
}
