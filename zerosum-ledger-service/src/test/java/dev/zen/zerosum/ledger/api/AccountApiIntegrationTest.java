package dev.zen.zerosum.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.dto.response.AccountResponse;
import dev.zen.zerosum.ledger.dto.response.BalanceResponse;
import dev.zen.zerosum.ledger.support.AbstractApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountApiIntegrationTest extends AbstractApiIntegrationTest {
    private static final String ACCOUNT_JSON =
            """
        {"code":"%s","name":"Cash","currency":"%s","type":"ASSET"}""";

    @Test
    void createsAnAccountAndItCanBeReadBack() {
        Reply created = post("/accounts", ACCOUNT_JSON.formatted("CASH", "USD"), null);

        assertThat(created.status()).isEqualTo(201);
        AccountResponse account = read(created, AccountResponse.class);
        assertThat(account.code()).isEqualTo("CASH");
        assertThat(account.currency()).isEqualTo("USD");
        assertThat(created.header("Location")).endsWith("/accounts/" + account.id());

        Reply fetched = get("/accounts/" + account.id());
        assertThat(fetched.status()).isEqualTo(200);
        AccountResponse again = read(fetched, AccountResponse.class);
        assertThat(again.id()).isEqualTo(account.id());
        assertThat(again.code()).isEqualTo("CASH");
        assertThat(again.type()).isEqualTo(account.type());
    }

    @Test
    void aDuplicateCodeIsAConflict() {
        post("/accounts", ACCOUNT_JSON.formatted("CASH", "USD"), null);

        Reply duplicate = post("/accounts", ACCOUNT_JSON.formatted("CASH", "EUR"), null);

        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.header("Content-Type")).contains("application/problem+json");
        assertThat(problemType(duplicate)).isEqualTo("urn:zerosum:problem:duplicate-account");
        assertThat(count("accounts")).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void validationFailuresListTheOffendingFields() {
        Reply reply =
                post(
                        "/accounts",
                        """
                {"code":"","name":"Cash","currency":"usd","type":"ASSET"}""",
                        null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:validation-failed");
        List<Map<String, String>> errors = (List<Map<String, String>>) readMap(reply).get("errors");
        assertThat(errors).extracting(e -> e.get("field")).contains("code", "currency");
        assertThat(count("accounts")).isZero();
    }

    @Test
    void aWellFormedButUnknownCurrencyIsUnprocessable() {
        Reply reply = post("/accounts", ACCOUNT_JSON.formatted("WEIRD", "XYZ"), null);

        assertThat(reply.status()).isEqualTo(422);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:invalid-input");
    }

    @Test
    void malformedJsonIsABadRequest() {
        Reply reply = post("/accounts", "{not json", null);

        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.header("Content-Type")).contains("application/problem+json");
    }

    @Test
    void anUnknownAccountIsNotFound() {
        Reply reply = get("/accounts/" + UUID.randomUUID());

        assertThat(reply.status()).isEqualTo(404);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:account-not-found");
    }

    @Test
    void aNewAccountHasAZeroBalanceInItsOwnCurrency() {
        AccountResponse account =
                read(
                        post("/accounts", ACCOUNT_JSON.formatted("EUR-CASH", "EUR"), null),
                        AccountResponse.class);

        Reply reply = get("/accounts/" + account.id() + "/balance");

        assertThat(reply.status()).isEqualTo(200);
        BalanceResponse balance = read(reply, BalanceResponse.class);
        assertThat(balance.currency()).isEqualTo("EUR");
        assertThat(balance.balanceMinor()).isZero();
    }
}
