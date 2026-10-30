package dev.zen.zerosum.ledger.dto.request;

import dev.zen.zerosum.ledger.api.Currencies;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.Direction;
import dev.zen.zerosum.ledger.service.PostTransferCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record TransferRequest(
        @Size(max = 100) String reference,
        @Size(max = 500) String description,
        @NotBlank
                @Pattern(
                        regexp = "[A-Z]{3}",
                        message = "must be a 3-letter uppercase ISO 4217 code")
                String currency,
        @NotNull @Size(min = 2, max = 100) List<@Valid @NotNull LegRequest> legs) {
    public record LegRequest(
            @NotNull UUID accountId,
            @NotNull Direction direction,
            @NotNull @Positive Long amountMinor) {}

    public PostTransferCommand toCommand() {
        Currency transferCurrency = Currencies.parse(currency);
        List<PostTransferCommand.Leg> commandLegs =
                legs.stream()
                        .map(
                                leg ->
                                        new PostTransferCommand.Leg(
                                                leg.accountId(),
                                                leg.direction(),
                                                new Money(leg.amountMinor(), transferCurrency)))
                        .toList();
        return new PostTransferCommand(reference, description, transferCurrency, commandLegs);
    }
}
