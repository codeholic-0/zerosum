package dev.zen.zerosum.ledger.dto.request;

import dev.zen.zerosum.ledger.enums.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public record CreateAccountRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank
                @Pattern(
                        regexp = "[A-Z]{3}",
                        message = "must be a 3-letter uppercase ISO 4217 code")
                String currency,
        @NotNull AccountType type,
        @Schema(
                        description =
                                "Lowest allowed balance in minor units (credits minus debits). Zero or"
                                        + " negative. Omit for an unconstrained account.",
                        example = "0")
                @Max(value = 0, message = "must be zero or negative")
                Long minBalanceMinor) {}
