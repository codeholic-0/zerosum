package dev.zen.zerosum.relay.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OutboxEventTest {
    @Test
    void acceptsAValidEvent() {
        OutboxEvent event = new OutboxEvent("transfer", "t-1", "TransferCompleted", 1, "payload");

        assertThat(event.aggregateType()).isEqualTo("transfer");
        assertThat(event.version()).isEqualTo(1);
    }

    @Test
    void rejectsBlankTextFields() {
        assertThatThrownBy(() -> new OutboxEvent(" ", "t-1", "X", 1, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateType");
        assertThatThrownBy(() -> new OutboxEvent("transfer", null, "X", 1, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");
        assertThatThrownBy(() -> new OutboxEvent("transfer", "t-1", "", 1, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void rejectsAVersionBelowOne() {
        assertThatThrownBy(() -> new OutboxEvent("transfer", "t-1", "X", 0, "p"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("version");
    }

    @Test
    void rejectsANullPayload() {
        assertThatThrownBy(() -> new OutboxEvent("transfer", "t-1", "X", 1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
