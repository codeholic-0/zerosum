package dev.zen.zerosum.ledger.dto.response;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.enums.AccountType;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
        UUID id, String code, String name, String currency, AccountType type, Instant createdAt) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getCode(),
                account.getName(),
                account.getCurrency().getCurrencyCode(),
                account.getType(),
                account.getCreatedAt());
    }
}
