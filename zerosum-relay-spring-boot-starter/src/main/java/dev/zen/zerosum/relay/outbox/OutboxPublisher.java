package dev.zen.zerosum.relay.outbox;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/**
 * Records events in the outbox table, inside the caller's database transaction, so the event and
 * the business data commit or roll back together.
 */
public class OutboxPublisher {

    private static final String INSERT =
            """
        insert into outbox (id, aggregatetype, aggregateid, type, event_version, payload)
        values (:id, :aggregateType, :aggregateId, :type, :version, cast(:payload as jsonb))
        """;

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Writes the event and returns its id. throws IllegalTransactionStateException if there is no
     * active, writable transaction: an event written outside the caller's transaction would not be
     * atomic with its data
     */
    public UUID publish(OutboxEvent event) {
        requireWritableTransaction();

        // A serialization failure is unchecked and propagates, rolling the caller's transaction
        // back.
        String json = objectMapper.writeValueAsString(event.payload());
        UUID id = UUID.randomUUID();
        jdbc.sql(INSERT)
                .param("id", id)
                .param("aggregateType", event.aggregateType())
                .param("aggregateId", event.aggregateId())
                .param("type", event.type())
                .param("version", event.version())
                .param("payload", json)
                .update();
        return id;
    }

    private static void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    "OutboxPublisher.publish must be called inside an active transaction so the"
                            + " event commits atomically with your data");
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException(
                    "OutboxPublisher.publish cannot run in a read-only transaction");
        }
    }
}
