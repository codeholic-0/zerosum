package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.exception.LedgerException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs a piece of work at most once per idempotency key. The key is claimed, the work runs, and the
 * response is stored, all in ONE transaction: either everything commits or nothing does.
 */
@Service
public class IdempotencyService {
    private static final int MAX_ATTEMPTS = 3;

    public record Outcome(UUID transferId, int status, String body) {}

    public record Result(int status, String body, boolean replayed) {}

    private record Stored(String requestHash, Integer status, String body) {}

    private final JdbcClient jdbc;
    private final long ttlSeconds;

    public IdempotencyService(
            JdbcClient jdbc, @Value("${zerosum.idempotency.ttl:24h}") Duration ttl) {
        this.jdbc = jdbc;
        this.ttlSeconds = ttl.toSeconds();
    }

    @Transactional
    public Result execute(String key, String requestHash, Supplier<Outcome> work) {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (claim(key, requestHash)) {
                // If the work throws, the transaction rolls back and the claim disappears with
                // it, so a failed request does not consume the key.
                Outcome outcome = work.get();
                store(key, outcome);
                return new Result(outcome.status(), outcome.body(), false);
            }
            Optional<Stored> existing = load(key);
            if (existing.isPresent()) {
                Stored stored = existing.get();
                if (!stored.requestHash().equals(requestHash)) {
                    throw new LedgerException.IdempotencyConflict(key);
                }
                if (stored.status() == null || stored.body() == null) {
                    throw new IllegalStateException(
                            "Idempotency record for key '" + key + "' has no stored response");
                }
                return new Result(stored.status(), stored.body(), true);
            }
        }
        throw new IllegalStateException("Could not claim or load idempotency key '" + key + "'");
    }

    /**
     * Claims the key. Returns false if a live (unexpired) row already exists. If another
     * transaction is claiming the same key right now, this statement waits for it to finish: after
     * a commit it sees the conflict, after a rollback it inserts. An expired row is recycled in
     * place.
     */
    private boolean claim(String key, String requestHash) {
        int rows =
                jdbc.sql(
                                """
                    insert into idempotency_keys (idempotency_key, request_hash, expires_at)
                    values (:key, :hash, now() + make_interval(secs => :ttlSeconds))
                    on conflict (idempotency_key) do update
                       set request_hash    = excluded.request_hash,
                           transfer_id     = null,
                           response_status = null,
                           response_body   = null,
                           created_at      = now(),
                           expires_at      = excluded.expires_at
                     where idempotency_keys.expires_at <= now()
                    """)
                        .param("key", key)
                        .param("hash", requestHash)
                        .param("ttlSeconds", ttlSeconds)
                        .update();
        return rows == 1;
    }

    private void store(String key, Outcome outcome) {
        jdbc.sql(
                        """
                update idempotency_keys
                   set transfer_id = :transferId,
                       response_status = :status,
                       response_body = cast(:body as jsonb)
                 where idempotency_key = :key
                """)
                .param("transferId", outcome.transferId())
                .param("status", outcome.status())
                .param("body", outcome.body())
                .param("key", key)
                .update();
    }

    private Optional<Stored> load(String key) {
        return jdbc.sql(
                        """
                select request_hash, response_status, cast(response_body as text) as body
                  from idempotency_keys
                 where idempotency_key = :key
                """)
                .param("key", key)
                .query(
                        (rs, rowNum) ->
                                new Stored(
                                        rs.getString("request_hash"),
                                        rs.getObject("response_status", Integer.class),
                                        rs.getString("body")))
                .optional();
    }
}
