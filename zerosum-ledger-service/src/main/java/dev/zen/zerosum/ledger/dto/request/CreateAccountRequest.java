package dev.zen.zerosum.ledger.dto.request;

import dev.zen.zerosum.ledger.enums.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 200) String name,
        @NotBlank
                @Pattern(
                        regexp = "[A-Z]{3}",
                        message = "must be a 3-letter uppercase ISO 4217 code")
                String currency,
        @NotNull AccountType type) {}
