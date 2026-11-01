package dev.zen.zerosum.ledger.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.domain.Money;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.enums.Direction;
import dev.zen.zerosum.ledger.exception.LedgerException;
import dev.zen.zerosum.ledger.service.PostTransferCommand;
import dev.zen.zerosum.ledger.service.PostedTransfer;
import dev.zen.zerosum.ledger.service.TransferService;
import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import dev.zen.zerosum.relay.outbox.OutboxSchemaValidator;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import tools.jackson.databind.ObjectMapper;

public class TransferEventsIntegrationTest extends AbstractIntegrationTest {
    private static final Currency USD = Currency.getInstance("USD");

    @Autowired TransferService transferService;
    @Autowired ObjectMapper objectMapper;
    @Autowired ApplicationContext context;

    private Account cash;
    private Account sales;

    @BeforeEach
    void createAccounts() {
        cash = newAccount("CASH", "USD", AccountType.ASSET);
        sales = newAccount("SALES", "USD", AccountType.REVENUE);
    }

    @Test
    void theStarterIsAutoconfiguredAndCheckedTheRealMigrationAtStartup() {
        // The context only started because OutboxSchemaValidator accepted the V4 migration.
        assertThat(context.getBeansOfType(OutboxSchemaValidator.class)).hasSize(1);
    }

    @Test
    void aCommittedTransferWritesExactlyOneEventWithTheRightEnvelopeAndPayload() {
        PostedTransfer posted = transferService.post(command(cash, sales, 5000));

        assertThat(count("outbox")).isEqualTo(1);
        Map<String, Object> row =
                jdbc.queryForMap(
                        "select aggregatetype, aggregateid, type, event_version from outbox");
        assertThat(row.get("aggregatetype")).isEqualTo("transfer");
        assertThat(row.get("aggregateid")).isEqualTo(posted.transfer().getId().toString());
        assertThat(row.get("type")).isEqualTo("TransferCompleted");
        assertThat(row.get("event_version")).isEqualTo(1);

        String json = jdbc.queryForObject("select payload::text from outbox", String.class);
        TransferCompleted event = objectMapper.readValue(json, TransferCompleted.class);
        assertThat(event.transferId()).isEqualTo(posted.transfer().getId());
        assertThat(event.currency()).isEqualTo("USD");
        assertThat(event.entries())
                .containsExactlyInAnyOrder(
                        new TransferCompleted.Entry(cash.getId(), Direction.DEBIT, 5000),
                        new TransferCompleted.Entry(sales.getId(), Direction.CREDIT, 5000));
    }

    @Test
    void eachCommittedTransferGetsItsOwnEvent() {
        for (int i = 0; i < 3; i++) {
            transferService.post(command(cash, sales, 100 + i));
        }

        assertThat(count("outbox")).isEqualTo(3);
        assertThat(
                        jdbc.queryForObject(
                                "select count(distinct aggregateid) from outbox", Long.class))
                .isEqualTo(3);
    }

    @Test
    void anUnbalancedTransferWritesNoEvent() {
        PostTransferCommand unbalanced =
                new PostTransferCommand(
                        "ref",
                        "test",
                        USD,
                        List.of(
                                PostTransferCommand.debit(cash.getId(), Money.of(5000, "USD")),
                                PostTransferCommand.credit(sales.getId(), Money.of(4000, "USD"))));

        assertThatThrownBy(() -> transferService.post(unbalanced))
                .isInstanceOf(LedgerException.UnbalancedTransfer.class);

        assertNothingWasWritten();
    }

    @Test
    void anUnknownAccountWritesNoEvent() {
        PostTransferCommand unknown =
                new PostTransferCommand(
                        "ref",
                        "test",
                        USD,
                        List.of(
                                PostTransferCommand.debit(UUID.randomUUID(), Money.of(100, "USD")),
                                PostTransferCommand.credit(sales.getId(), Money.of(100, "USD"))));

        assertThatThrownBy(() -> transferService.post(unknown))
                .isInstanceOf(LedgerException.AccountNotFound.class);

        assertNothingWasWritten();
    }

    @Test
    void aFailureAfterTheEventIsWrittenRollsTheEventBackWithTheTransfer() {
        // TransferService writes the event BEFORE it applies balance limits, so this failure
        // happens after the event row already exists in the transaction. If the ordering in
        // post() ever changes, this test stops proving that and needs a new trick.
        Account wallet = newLimitedAccount("WALLET", "USD", AccountType.LIABILITY, 0);

        assertThatThrownBy(() -> transferService.post(command(wallet, sales, 100)))
                .isInstanceOf(LedgerException.InsufficientFunds.class);

        assertNothingWasWritten();
    }

    @Test
    void aFailureWhileWritingTheEventRollsBackTheWholeTransfer() {
        jdbc.execute(
                """
            create function test_fail_outbox() returns trigger as $$
            begin
                raise exception 'simulated outbox failure';
            end;
            $$ language plpgsql
            """);
        jdbc.execute(
                """
            create trigger trg_test_fail_outbox before insert on outbox
            for each row execute function test_fail_outbox()
            """);
        try {
            assertThatThrownBy(() -> transferService.post(command(cash, sales, 100)))
                    .hasStackTraceContaining("simulated outbox failure");

            assertNothingWasWritten();
        } finally {
            jdbc.execute("drop trigger trg_test_fail_outbox on outbox");
            jdbc.execute("drop function test_fail_outbox()");
        }
    }

    private void assertNothingWasWritten() {
        assertThat(count("outbox")).isZero();
        assertThat(count("transfers")).isZero();
        assertThat(count("journal_entries")).isZero();
    }

    private static PostTransferCommand command(Account debit, Account credit, long amount) {
        return new PostTransferCommand(
                "ref",
                "test",
                USD,
                List.of(
                        PostTransferCommand.debit(debit.getId(), Money.of(amount, "USD")),
                        PostTransferCommand.credit(credit.getId(), Money.of(amount, "USD"))));
    }
}
