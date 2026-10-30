package dev.zen.zerosum.relay.outbox.support;

import dev.zen.zerosum.relay.outbox.OutboxSchemaValidator;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public abstract class PostgresTestSupport {
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17"));

    static {
        POSTGRES.start();
    }

    protected final DataSource dataSource =
            new DriverManagerDataSource(
                    POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    protected final JdbcClient jdbc = JdbcClient.create(dataSource);
    protected final PlatformTransactionManager transactionManager =
            new DataSourceTransactionManager(dataSource);
    protected final TransactionTemplate transaction = new TransactionTemplate(transactionManager);

    @BeforeEach
    void freshOutboxTable() {
        dropOutboxTable();
        jdbc.sql(OutboxSchemaValidator.EXPECTED_DDL).update();
    }

    protected void dropOutboxTable() {
        jdbc.sql("drop table if exists outbox").update();
    }

    protected long outboxRows() {
        return jdbc.sql("select count(*) from outbox").query(Long.class).single();
    }
}
