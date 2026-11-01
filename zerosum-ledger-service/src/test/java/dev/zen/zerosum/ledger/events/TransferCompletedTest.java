package dev.zen.zerosum.ledger.events;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.domain.Transfer;
import dev.zen.zerosum.ledger.enums.Direction;
import dev.zen.zerosum.relay.outbox.OutboxEvent;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class TransferCompletedTest {
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final Transfer transfer =
            Transfer.create("inv-1", "Customer payment", Currency.getInstance("USD"));
    private final UUID debitAccount = UUID.randomUUID();
    private final UUID creditAccount = UUID.randomUUID();
    private final List<JournalEntry> entries =
            List.of(
                    JournalEntry.of(
                            transfer.getId(), debitAccount, Direction.DEBIT, Money.of(5000, "USD")),
                    JournalEntry.of(
                            transfer.getId(),
                            creditAccount,
                            Direction.CREDIT,
                            Money.of(5000, "USD")));

    @Test
    void theJsonShapeIsTheDocumentedContract() {
        TransferCompleted event = TransferCompleted.from(transfer, entries);

        JsonNode json = MAPPER.readTree(MAPPER.writeValueAsString(event));

        assertThat(json.size()).as("top-level fields").isEqualTo(6);
        assertThat(json.get("transferId").toString()).isEqualTo("\"" + transfer.getId() + "\"");
        assertThat(json.get("reference").toString()).isEqualTo("\"inv-1\"");
        assertThat(json.get("description").toString()).isEqualTo("\"Customer payment\"");
        assertThat(json.get("currency").toString()).isEqualTo("\"USD\"");
        assertThat(json.has("createdAt")).isTrue();

        JsonNode first = json.get("entries").get(0);
        assertThat(json.get("entries").size()).isEqualTo(2);
        assertThat(first.size()).as("fields per entry").isEqualTo(3);
        assertThat(first.get("accountId").toString()).isEqualTo("\"" + debitAccount + "\"");
        assertThat(first.get("direction").toString()).isEqualTo("\"DEBIT\"");
        assertThat(first.get("amountMinor").longValue()).isEqualTo(5000L);
    }

    @Test
    void theJsonReadsBackIntoTheSameEvent() {
        TransferCompleted original = TransferCompleted.from(transfer, entries);

        TransferCompleted copy =
                MAPPER.readValue(MAPPER.writeValueAsString(original), TransferCompleted.class);

        assertThat(copy).isEqualTo(original);
    }

    @Test
    void theOutboxEnvelopeIsKeyedByTheTransferId() {
        TransferCompleted event = TransferCompleted.from(transfer, entries);

        OutboxEvent outbox = event.toOutboxEvent();

        assertThat(outbox.aggregateType()).isEqualTo("transfer");
        assertThat(outbox.aggregateId()).isEqualTo(transfer.getId().toString());
        assertThat(outbox.type()).isEqualTo("TransferCompleted");
        assertThat(outbox.version()).isEqualTo(1);
        assertThat(outbox.payload()).isSameAs(event);
    }
}
