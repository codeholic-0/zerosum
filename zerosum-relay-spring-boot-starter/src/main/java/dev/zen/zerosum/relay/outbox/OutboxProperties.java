package dev.zen.zerosum.relay.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings under {relay.outbox}. The whole feature is switched off with
 * {relay.outbox.enabled=false}, which is handled by the autoconfiguration's condition.
 * validateSchema check at startup that the outbox table exists with the expected columns
 */
@ConfigurationProperties(prefix = "relay.outbox")
public record OutboxProperties(@DefaultValue("true") boolean validateSchema) {}
