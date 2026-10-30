package dev.zen.zerosum.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.dto.response.AccountResponse;
import dev.zen.zerosum.ledger.dto.response.BalanceResponse;
import dev.zen.zerosum.ledger.support.AbstractApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BalanceLimitApiIntegrationTest extends AbstractApiIntegrationTest {
    private static String accountJson(String code, String type, String minBalance) {
        return """
        {"code":"%s","name":"%s","currency":"USD","type":"%s"%s}"""
                .formatted(
                        code,
                        code,
                        type,
                        minBalance == null ? "" : ",\"minBalanceMinor\":" + minBalance);
    }

    private AccountResponse create(String code, String type, String minBalance) {
        Reply reply = post("/accounts", accountJson(code, type, minBalance), null);
        assertThat(reply.status()).isEqualTo(201);
        return read(reply, AccountResponse.class);
    }

    private Reply transferBetween(UUID debit, UUID credit, long amount, String key) {
        return post(
                "/transfers",
                transfer("USD", leg(debit, "DEBIT", amount), leg(credit, "CREDIT", amount)),
                key);
    }

    @Test
    void theLimitIsAcceptedAndReturnedAndAbsentForUnconstrainedAccounts() {
        AccountResponse wallet = create("WALLET", "LIABILITY", "-500");
        AccountResponse bank = create("BANK", "ASSET", null);

        assertThat(wallet.minBalanceMinor()).isEqualTo(-500L);
        assertThat(bank.minBalanceMinor()).isNull();
        assertThat(read(get("/accounts/" + wallet.id()), AccountResponse.class).minBalanceMinor())
                .isEqualTo(-500L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPositiveLimitIsABadRequest() {
        Reply reply = post("/accounts", accountJson("BAD", "ASSET", "100"), null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:validation-failed");
        List<Map<String, String>> errors = (List<Map<String, String>>) readMap(reply).get("errors");
        assertThat(errors).extracting(e -> e.get("field")).contains("minBalanceMinor");
        assertThat(count("accounts")).isZero();
    }

    @Test
    void crossingTheLimitIsRejectedAndTheIdempotencyKeyStaysUsable() {
        AccountResponse bank = create("BANK", "ASSET", null);
        AccountResponse sales = create("SALES", "REVENUE", null);
        AccountResponse wallet = create("WALLET", "LIABILITY", "0");
        assertThat(
                        transferBetween(bank.id(), wallet.id(), 5000, UUID.randomUUID().toString())
                                .status())
                .isEqualTo(201);

        String key = UUID.randomUUID().toString();
        Reply rejected = transferBetween(wallet.id(), sales.id(), 6000, key);

        assertThat(rejected.status()).isEqualTo(422);
        assertThat(rejected.header("Content-Type")).contains("application/problem+json");
        assertThat(problemType(rejected)).isEqualTo("urn:zerosum:problem:insufficient-funds");
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("idempotency_keys")).as("the failed request left no key").isEqualTo(1);

        Reply retry = transferBetween(wallet.id(), sales.id(), 1000, key);
        assertThat(retry.status()).isEqualTo(201);
        assertThat(retry.header("Idempotent-Replayed")).isNull();
    }

    @Test
    void theBalanceEndpointReturnsTheMaintainedBalance() {
        AccountResponse bank = create("BANK", "ASSET", null);
        AccountResponse sales = create("SALES", "REVENUE", null);
        AccountResponse wallet = create("WALLET", "LIABILITY", "0");
        transferBetween(bank.id(), wallet.id(), 5000, UUID.randomUUID().toString());
        transferBetween(wallet.id(), sales.id(), 1000, UUID.randomUUID().toString());

        BalanceResponse balance =
                read(get("/accounts/" + wallet.id() + "/balance"), BalanceResponse.class);

        assertThat(balance.balanceMinor()).isEqualTo(4000);
        assertThat(balance.currency()).isEqualTo("USD");
    }
}
