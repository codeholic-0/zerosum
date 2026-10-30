package dev.zen.zerosum.ledger.dto.response;

import dev.zen.zerosum.ledger.domain.Money;
import java.util.UUID;

public record BalanceResponse(UUID accountId, String currency, long balanceMinor) {
    public static BalanceResponse from(UUID accountId, Money balance) {
        return new BalanceResponse(
                accountId, balance.currency().getCurrencyCode(), balance.amountMinor());
    }
}
