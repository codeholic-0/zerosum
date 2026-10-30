package dev.zen.zerosum.ledger.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import dev.zen.zerosum.ledger.domain.Account;
import dev.zen.zerosum.ledger.dto.response.TransferResponse;
import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.support.AbstractApiIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdempotencyIntegrationTest extends AbstractApiIntegrationTest {
    @Autowired IdempotencyCleanupJob cleanupJob;

    private Account cash;
    private Account sales;

    @BeforeEach
    void createAccounts() {
        cash = newAccount("CASH", "USD", AccountType.ASSET);
        sales = newAccount("SALES", "USD", AccountType.REVENUE);
    }

    private String body(long amount) {
        return transfer(
                "USD", leg(cash.getId(), "DEBIT", amount), leg(sales.getId(), "CREDIT", amount));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    @Test
    void aReplayReturnsTheOriginalResponseAndPostsNothing() {
        String key = newKey();

        Reply first = post("/transfers", body(5000), key);
        Reply second = post("/transfers", body(5000), key);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(second.header("Location")).isEqualTo(first.header("Location"));
        assertThat(read(second, TransferResponse.class))
                .isEqualTo(read(first, TransferResponse.class));
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("journal_entries")).isEqualTo(2);
    }

    @Test
    void theOrderOfLegsDoesNotChangeTheRequestIdentity() {
        String key = newKey();
        String debit = leg(cash.getId(), "DEBIT", 5000);
        String credit = leg(sales.getId(), "CREDIT", 5000);

        Reply first = post("/transfers", transfer("USD", debit, credit), key);
        Reply second = post("/transfers", transfer("USD", credit, debit), key);

        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(read(second, TransferResponse.class).id())
                .isEqualTo(read(first, TransferResponse.class).id());
        assertThat(count("transfers")).isEqualTo(1);
    }

    @Test
    void theSameKeyWithADifferentBodyIsAConflict() {
        String key = newKey();
        post("/transfers", body(5000), key);

        Reply conflict = post("/transfers", body(6000), key);

        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.header("Content-Type")).contains("application/problem+json");
        assertThat(problemType(conflict)).isEqualTo("urn:zerosum:problem:idempotency-key-reuse");
        assertThat(count("transfers")).isEqualTo(1);
    }

    @Test
    void differentKeysAreIndependent() {
        Reply first = post("/transfers", body(5000), newKey());
        Reply second = post("/transfers", body(5000), newKey());

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(second.header("Idempotent-Replayed")).isNull();
        assertThat(count("transfers")).isEqualTo(2);
    }

    @Test
    void aFailedRequestDoesNotConsumeTheKey() {
        String key = newKey();
        String unbalanced =
                transfer(
                        "USD",
                        leg(cash.getId(), "DEBIT", 5000),
                        leg(sales.getId(), "CREDIT", 4000));

        Reply failed = post("/transfers", unbalanced, key);
        assertThat(failed.status()).isEqualTo(422);
        assertThat(count("idempotency_keys")).isZero();

        Reply corrected = post("/transfers", body(5000), key);
        assertThat(corrected.status()).isEqualTo(201);
        assertThat(corrected.header("Idempotent-Replayed")).isNull();
        assertThat(count("transfers")).isEqualTo(1);
    }

    @Test
    void parallelDuplicatesPostExactlyOnce() throws Exception {
        int requests = 20;
        String key = newKey();
        String body = body(5000);
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);

        List<Reply> replies = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Reply>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    go.await();
                                    return post("/transfers", body, key);
                                }));
            }
            ready.await();
            go.countDown();
            for (Future<Reply> future : futures) {
                replies.add(future.get(60, TimeUnit.SECONDS));
            }
        }

        assertThat(replies).extracting(Reply::status).containsOnly(201);
        assertThat(replies.stream().filter(r -> r.header("Idempotent-Replayed") == null).count())
                .as("exactly one request did the work")
                .isEqualTo(1);
        assertThat(
                        replies.stream()
                                .map(r -> read(r, TransferResponse.class).id())
                                .distinct()
                                .count())
                .as("everyone got the same transfer")
                .isEqualTo(1);
        assertThat(count("transfers")).isEqualTo(1);
        assertThat(count("journal_entries")).isEqualTo(2);
        assertThat(count("idempotency_keys")).isEqualTo(1);
    }

    @Test
    void anExpiredKeyIsRecycledInsteadOfConflicting() {
        String key = newKey();
        post("/transfers", body(5000), key);
        expire(key);

        Reply reply = post("/transfers", body(6000), key);

        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.header("Idempotent-Replayed")).isNull();
        assertThat(count("transfers")).isEqualTo(2);
    }

    @Test
    void cleanupDeletesExpiredKeysAndKeepsLiveOnes() {
        String expiredKey = newKey();
        String liveKey = newKey();
        post("/transfers", body(5000), expiredKey);
        post("/transfers", body(6000), liveKey);
        expire(expiredKey);

        int purged = cleanupJob.purgeExpired();

        assertThat(purged).isEqualTo(1);
        assertThat(count("idempotency_keys")).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "select idempotency_key from idempotency_keys", String.class))
                .isEqualTo(liveKey);
        assertThat(count("transfers")).as("the ledger is untouched").isEqualTo(2);
    }

    private void expire(String key) {
        jdbc.update(
                "update idempotency_keys set expires_at = now() - interval '1 minute'"
                        + " where idempotency_key = ?",
                key);
    }
}
