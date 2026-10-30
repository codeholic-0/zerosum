package dev.zen.zerosum.relay.outbox;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@AutoConfiguration(
        afterName = "org.springframework.boot.jdbc.autoconfigure.JdbcClientAutoConfiguration")
@ConditionalOnClass(JdbcClient.class)
@ConditionalOnProperty(
        prefix = "relay.outbox",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(OutboxProperties.class)
public class OutboxAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    OutboxPublisher outboxPublisher(JdbcClient jdbc, ObjectProvider<ObjectMapper> objectMapper) {
        // Prefer the application's mapper (so payloads follow its settings); fall back to a
        // default.
        return new OutboxPublisher(
                jdbc, objectMapper.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "relay.outbox",
            name = "validate-schema",
            havingValue = "true",
            matchIfMissing = true)
    OutboxSchemaValidator outboxSchemaValidator(JdbcClient jdbc) {
        return new OutboxSchemaValidator(jdbc);
    }
}
