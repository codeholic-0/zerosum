package dev.zen.zerosum.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.dto.response.BalanceResponse;
import dev.zen.zerosum.ledger.dto.response.TransferResponse;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.support.AbstractApiIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TransferApiIntegrationTest extends AbstractApiIntegrationTest {
    private Account cash;
    private Account sales;

    @BeforeEach
    void createAccounts() {
        cash = newAccount("CASH", "USD", AccountType.ASSET);
        sales = newAccount("SALES", "USD", AccountType.REVENUE);
    }

    private String validBody() {
        return transfer(
                "USD", leg(cash.getId(), "DEBIT", 5000), leg(sales.getId(), "CREDIT", 5000));
    }

    @Test
    void postsATransferAndItCanBeReadBack() {
        Reply reply = post("/transfers", validBody(), UUID.randomUUID().toString());

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.header("Idempotent-Replayed")).isNull();
        TransferResponse transfer = read(reply, TransferResponse.class);
        assertThat(reply.header("Location")).endsWith("/transfers/" + transfer.id());
        assertThat(transfer.entries()).hasSize(2);

        Reply fetched = get("/transfers/" + transfer.id());
        assertThat(fetched.status()).isEqualTo(200);
        TransferResponse again = read(fetched, TransferResponse.class);
        assertThat(again.id()).isEqualTo(transfer.id());
        assertThat(again.entries()).hasSize(2);
    }

    @Test
    void balancesReflectThePostedTransfer() {
        post("/transfers", validBody(), UUID.randomUUID().toString());

        assertThat(
                        read(get("/accounts/" + cash.getId() + "/balance"), BalanceResponse.class)
                                .balanceMinor())
                .isEqualTo(-5000);
        assertThat(
                        read(get("/accounts/" + sales.getId() + "/balance"), BalanceResponse.class)
                                .balanceMinor())
                .isEqualTo(5000);
    }

    @Test
    void anUnknownTransferIsNotFound() {
        Reply reply = get("/transfers/" + UUID.randomUUID());

        assertThat(reply.status()).isEqualTo(404);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:transfer-not-found");
    }

    // ---- 400: the request itself is malformed -------------------------------------------------

    @Test
    void aMissingIdempotencyKeyIsABadRequest() {
        assertBadRequest(post("/transfers", validBody(), null));
    }

    @Test
    void aKeyWithSpacesIsABadRequest() {
        assertBadRequest(post("/transfers", validBody(), "bad key"));
    }

    @Test
    void aKeyLongerThan255CharactersIsABadRequest() {
        assertBadRequest(post("/transfers", validBody(), "k".repeat(256)));
    }

    @Test
    void malformedJsonIsABadRequest() {
        assertBadRequest(post("/transfers", "{not json", UUID.randomUUID().toString()));
    }

    @Test
    void fewerThanTwoLegsFailsValidation() {
        Reply reply =
                post(
                        "/transfers",
                        transfer("USD", leg(cash.getId(), "DEBIT", 100)),
                        UUID.randomUUID().toString());

        assertBadRequest(reply);
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:validation-failed");
        assertThat(errorFields(reply)).contains("legs");
    }

    @Test
    void aNonPositiveAmountFailsValidation() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "USD",
                                leg(cash.getId(), "DEBIT", 0),
                                leg(sales.getId(), "CREDIT", 0)),
                        UUID.randomUUID().toString());

        assertBadRequest(reply);
        assertThat(errorFields(reply))
                .anyMatch(f -> f.startsWith("legs[") && f.endsWith("amountMinor"));
    }

    @Test
    void aLowercaseCurrencyFailsValidation() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "usd",
                                leg(cash.getId(), "DEBIT", 100),
                                leg(sales.getId(), "CREDIT", 100)),
                        UUID.randomUUID().toString());

        assertBadRequest(reply);
        assertThat(errorFields(reply)).contains("currency");
    }

    // ---- 422: well-formed, but it breaks a ledger rule -----------------------------------------

    @Test
    void anUnbalancedTransferIsUnprocessable() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "USD",
                                leg(cash.getId(), "DEBIT", 5000),
                                leg(sales.getId(), "CREDIT", 4000)),
                        UUID.randomUUID().toString());

        assertRejected(reply, 422, "unbalanced-transfer");
    }

    @Test
    void anUnknownAccountIsUnprocessable() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "USD",
                                leg(UUID.randomUUID(), "DEBIT", 100),
                                leg(sales.getId(), "CREDIT", 100)),
                        UUID.randomUUID().toString());

        assertRejected(reply, 422, "unknown-account");
    }

    @Test
    void anAccountInAnotherCurrencyIsUnprocessable() {
        Account euroCash = newAccount("EUR-CASH", "EUR", AccountType.ASSET);

        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "USD",
                                leg(euroCash.getId(), "DEBIT", 100),
                                leg(sales.getId(), "CREDIT", 100)),
                        UUID.randomUUID().toString());

        assertRejected(reply, 422, "currency-mismatch");
    }

    @Test
    void theSameAccountOnBothSidesIsUnprocessable() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "USD",
                                leg(cash.getId(), "DEBIT", 100),
                                leg(cash.getId(), "CREDIT", 100)),
                        UUID.randomUUID().toString());

        assertRejected(reply, 422, "invalid-transfer");
    }

    @Test
    void anUnknownCurrencyCodeIsUnprocessable() {
        Reply reply =
                post(
                        "/transfers",
                        transfer(
                                "XYZ",
                                leg(cash.getId(), "DEBIT", 100),
                                leg(sales.getId(), "CREDIT", 100)),
                        UUID.randomUUID().toString());

        assertRejected(reply, 422, "invalid-input");
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void assertBadRequest(Reply reply) {
        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.header("Content-Type")).contains("application/problem+json");
        assertNothingWasWritten();
    }

    private void assertRejected(Reply reply, int status, String problemSlug) {
        assertThat(reply.status()).isEqualTo(status);
        assertThat(reply.header("Content-Type")).contains("application/problem+json");
        assertThat(problemType(reply)).isEqualTo("urn:zerosum:problem:" + problemSlug);
        assertNothingWasWritten();
    }

    /** A rejected request leaves no transfer, no entries and no idempotency key behind. */
    private void assertNothingWasWritten() {
        assertThat(count("transfers")).isZero();
        assertThat(count("journal_entries")).isZero();
        assertThat(count("idempotency_keys")).isZero();
    }

    @SuppressWarnings("unchecked")
    private List<String> errorFields(Reply reply) {
        List<Map<String, String>> errors = (List<Map<String, String>>) readMap(reply).get("errors");
        return errors.stream().map(e -> e.get("field")).toList();
    }
}
