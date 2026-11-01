package dev.zen.zerosum.relay.outbox;

import java.util.Objects;

public record OutboxEvent(
        String aggregateType, String aggregateId, String type, int version, Object payload) {

    public OutboxEvent {
        requireText(aggregateType, "aggregateType");
        requireText(aggregateId, "aggregateId");
        requireText(type, "type");
        if (version < 1) {
            throw new IllegalArgumentException("version must be at least 1: " + version);
        }
        Objects.requireNonNull(payload, "payload");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
