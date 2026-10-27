package dev.zen.zerosum.ledger.dto.response;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Transfer;
import dev.zen.zerosum.ledger.enums.Direction;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferResponse(
        UUID id,
        String reference,
        String description,
        String currency,
        Instant createdAt,
        List<EntryResponse> entries) {
    public record EntryResponse(Long id, UUID accountId, Direction direction, long amountMinor) {}

    public static TransferResponse from(Transfer transfer, List<JournalEntry> entries) {
        return new TransferResponse(
                transfer.getId(),
                transfer.getReference(),
                transfer.getDescription(),
                transfer.getCurrency().getCurrencyCode(),
                transfer.getCreatedAt(),
                entries.stream()
                        .map(
                                e ->
                                        new EntryResponse(
                                                e.getId(),
                                                e.getAccountId(),
                                                e.getDirection(),
                                                e.getAmount().amountMinor()))
                        .toList());
    }
}
