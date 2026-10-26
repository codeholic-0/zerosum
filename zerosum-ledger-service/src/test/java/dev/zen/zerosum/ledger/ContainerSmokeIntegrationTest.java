package dev.zen.zerosum.ledger;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import dev.zen.zerosum.ledger.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

public class ContainerSmokeIntegrationTest extends AbstractIntegrationTest {

    @Test
    void flywayCreatedTheLedgerTables() {
        Integer tables =
                jdbc.queryForObject(
                        """
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('accounts', 'transfers', 'journal_entries')
                """,
                        Integer.class);
        assertThat(tables).isEqualTo(3);
    }

    @Test
    void dataIsResetBetweenTests() {
        assertThat(jdbc.queryForObject("select count(*) from accounts", Long.class)).isZero();
    }
}
