package dev.zen.zerosum.relay.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zen.zerosum.relay.outbox.support.PostgresTestSupport;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class OutboxPublisherIntegrationTest extends PostgresTestSupport {
    record Sample(String name, int count) {}

    /** Jackson fails on this one: the getter throws. */
    public static class Exploding {
        public String getBoom() {
            throw new IllegalStateException("boom");
        }
    }

    private final OutboxPublisher publisher =
            new OutboxPublisher(jdbc, JsonMapper.builder().build());

    private static OutboxEvent event() {
        return new OutboxEvent("transfer", "t-1", "TransferCompleted", 1, new Sample("a", 1));
    }

    @Test
    void writesOneRowWithTheExpectedColumns() {
        UUID id =
                transaction.execute(
                        status ->
                                publisher.publish(
                                        new OutboxEvent(
                                                "transfer",
                                                "t-1",
                                                "TransferCompleted",
                                                2,
                                                new Sample("abc", 7))));

        assertThat(outboxRows()).isEqualTo(1);
        Map<String, Object> row =
                jdbc.sql(
                                """
                    select id, aggregatetype, aggregateid, type, event_version,
                           payload->>'name' as name, (payload->>'count')::int as cnt,
                           created_at
                      from outbox
                    """)
                        .query()
                        .singleRow();
        assertThat(row.get("id")).isEqualTo(id);
        assertThat(row.get("aggregatetype")).isEqualTo("transfer");
        assertThat(row.get("aggregateid")).isEqualTo("t-1");
        assertThat(row.get("type")).isEqualTo("TransferCompleted");
        assertThat(row.get("event_version")).isEqualTo(2);
        assertThat(row.get("name")).isEqualTo("abc");
        assertThat(row.get("cnt")).isEqualTo(7);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void everyEventGetsItsOwnId() {
        UUID first = transaction.execute(status -> publisher.publish(event()));
        UUID second = transaction.execute(status -> publisher.publish(event()));

        assertThat(first).isNotEqualTo(second);
        assertThat(outboxRows()).isEqualTo(2);
    }

    @Test
    void failsOutsideATransactionAndWritesNothing() {
        assertThatThrownBy(() -> publisher.publish(event()))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("active transaction");

        assertThat(outboxRows()).isZero();
    }

    @Test
    void failsInAReadOnlyTransactionAndWritesNothing() {
        TransactionTemplate readOnly = new TransactionTemplate(transactionManager);
        readOnly.setReadOnly(true);

        assertThatThrownBy(
                        () -> readOnly.executeWithoutResult(status -> publisher.publish(event())))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("read-only");

        assertThat(outboxRows()).isZero();
    }

    @Test
    void aRolledBackTransactionLeavesNoEvent() {
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            publisher.publish(event());
                                            throw new IllegalStateException("business failure");
                                        }))
                .hasMessage("business failure");

        assertThat(outboxRows()).isZero();
    }

    @Test
    void aPayloadThatCannotBeSerializedFailsAndWritesNothing() {
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status ->
                                                publisher.publish(
                                                        new OutboxEvent(
                                                                "transfer",
                                                                "t-1",
                                                                "X",
                                                                1,
                                                                new Exploding()))))
                .hasStackTraceContaining("boom");

        assertThat(outboxRows()).isZero();
    }
}
