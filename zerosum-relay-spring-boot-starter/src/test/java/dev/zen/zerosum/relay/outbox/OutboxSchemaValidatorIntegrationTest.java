package dev.zen.zerosum.relay.outbox;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.zen.zerosum.relay.outbox.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;

class OutboxSchemaValidatorIntegrationTest extends PostgresTestSupport {
    private final OutboxSchemaValidator validator = new OutboxSchemaValidator(jdbc);

    @Test
    void acceptsTheExpectedSchema() {
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void acceptsExtraColumns() {
        jdbc.sql("alter table outbox add column trace_id text").update();

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsAMissingTableAndShowsTheExpectedDdl() {
        dropOutboxTable();

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist")
                .hasMessageContaining("CREATE TABLE outbox")
                .hasMessageContaining("relay.outbox.validate-schema=false");
    }

    @Test
    void rejectsAMissingColumn() {
        jdbc.sql("alter table outbox drop column payload").update();

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing column 'payload'");
    }

    @Test
    void rejectsAWrongColumnType() {
        jdbc.sql("alter table outbox alter column payload type text using payload::text").update();

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("column 'payload' is text but must be jsonb");
    }

    @Test
    void reportsEveryProblemAtOnce() {
        jdbc.sql("alter table outbox drop column payload").update();
        jdbc.sql("alter table outbox drop column event_version").update();

        assertThatThrownBy(validator::validate)
                .hasMessageContaining("missing column 'payload'")
                .hasMessageContaining("missing column 'event_version'");
    }
}
