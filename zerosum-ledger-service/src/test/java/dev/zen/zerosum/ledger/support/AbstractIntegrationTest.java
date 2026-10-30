package dev.zen.zerosum.ledger.support;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.repositories.AccountRepository;
import dev.zen.zerosum.ledger.service.AccountService;
import java.util.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(TestImages.POSTGRES);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected AccountRepository accountRepository;
    @Autowired protected AccountService accountService;

    protected Account newAccount(String code, String currency, AccountType type) {
        return accountRepository.save(
                Account.open(code, code + " account", Currency.getInstance(currency), type));
    }

    protected long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    /** An account with a lower balance bound; it gets a maintained balance row. */
    protected Account newLimitedAccount(
            String code, String currency, AccountType type, long minBalanceMinor) {
        return accountService.open(
                code, code + " account", Currency.getInstance(currency), type, minBalanceMinor);
    }

    @BeforeEach
    protected void resetDatabase() {
        jdbc.execute(
                "TRUNCATE journal_entries, transfers, accounts, idempotency_keys, account_balances"
                        + " RESTART IDENTITY");
    }
}
