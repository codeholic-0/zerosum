package dev.zen.zerosum.relay.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.relay.outbox.support.PostgresTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

class OutboxAutoConfigurationIntegrationTest extends PostgresTestSupport {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .withBean(JdbcClient.class, () -> jdbc);
    }

    @Test
    void registersThePublisherAndTheValidatorByDefault() {
        runner().run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(OutboxPublisher.class)
                                        .hasSingleBean(OutboxSchemaValidator.class));
    }

    @Test
    void thePublisherWorksWithTheFallbackObjectMapper() {
        runner().run(
                        context -> {
                            OutboxPublisher publisher = context.getBean(OutboxPublisher.class);

                            transaction.executeWithoutResult(
                                    status ->
                                            publisher.publish(
                                                    new OutboxEvent(
                                                            "transfer", "t-1", "X", 1, "plain")));

                            assertThat(outboxRows()).isEqualTo(1);
                        });
    }

    @Test
    void theWholeFeatureCanBeDisabled() {
        runner().withPropertyValues("relay.outbox.enabled=false")
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .doesNotHaveBean(OutboxPublisher.class)
                                        .doesNotHaveBean(OutboxSchemaValidator.class));
    }

    @Test
    void validationCanBeSwitchedOffWhileKeepingThePublisher() {
        dropOutboxTable();

        runner().withPropertyValues("relay.outbox.validate-schema=false")
                .run(
                        context ->
                                assertThat(context)
                                        .hasNotFailed()
                                        .hasSingleBean(OutboxPublisher.class)
                                        .doesNotHaveBean(OutboxSchemaValidator.class));
    }

    @Test
    void startupFailsWithAClearMessageWhenTheTableIsMissing() {
        dropOutboxTable();

        runner().run(
                        context ->
                                assertThat(context)
                                        .hasFailed()
                                        .getFailure()
                                        .hasStackTraceContaining("'outbox' table does not exist"));
    }

    @Test
    void anApplicationDefinedPublisherWins() {
        OutboxPublisher custom = new OutboxPublisher(jdbc, JsonMapper.builder().build());

        runner().withBean(OutboxPublisher.class, () -> custom)
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(OutboxPublisher.class);
                            assertThat(context.getBean(OutboxPublisher.class)).isSameAs(custom);
                        });
    }
}
