package dev.zen.zerosum.ledger.events;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Transfer;
import dev.zen.zerosum.ledger.enums.Direction;
import dev.zen.zerosum.relay.outbox.OutboxEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransferCompleted(
        UUID transferId,
        String reference,
        String description,
        String currency,
        Instant createdAt,
        List<Entry> entries) {

    public static final String AGGREGATE_TYPE = "transfer";
    public static final String EVENT_TYPE = "TransferCompleted";
    public static final int VERSION = 1;

    public record Entry(UUID accountId, Direction direction, long amountMinor) {}

    public static TransferCompleted from(Transfer transfer, List<JournalEntry> entries) {
        return new TransferCompleted(
                transfer.getId(),
                transfer.getReference(),
                transfer.getDescription(),
                transfer.getCurrency().getCurrencyCode(),
                transfer.getCreatedAt(),
                entries.stream()
                        .map(
                                e ->
                                        new Entry(
                                                e.getAccountId(),
                                                e.getDirection(),
                                                e.getAmount().amountMinor()))
                        .toList());
    }

    /** The aggregate id is the transfer id, which will become the Kafka message key. */
    public OutboxEvent toOutboxEvent() {
        return new OutboxEvent(AGGREGATE_TYPE, transferId.toString(), EVENT_TYPE, VERSION, this);
    }
}
