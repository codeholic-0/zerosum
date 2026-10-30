package dev.zen.zerosum.ledger.idempotency;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IdempotencyCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupJob.class);

    private final JdbcClient jdbc;
    private final int batchSize;

    public IdempotencyCleanupJob(
            JdbcClient jdbc,
            @Value("${zerosum.idempotency.cleanup_batch_size:1000}") int batchSize) {
        this.jdbc = jdbc;
        this.batchSize = batchSize;
    }

    @Scheduled(
            initialDelayString = "${zerosum.idempotency.cleanup_initial_delay:PT1M}",
            fixedDelayString = "${zerosum.idempotency.cleanup_interval:PT1H}")
    public void scheduledPurge() {
        int purged = purgeExpired();
        if (purged > 0) {
            log.info("Purged {} expired idempotency keys", purged);
        }
    }

    /** Deletes all expired keys, one short transaction per batch. Returns how many were deleted. */
    public int purgeExpired() {
        int total = 0;
        int deleted;
        do {
            // Not @Transactional on purpose: each batch commits on its own, so locks stay short.
            // SKIP LOCKED makes it safe to run on several instances at once.
            deleted =
                    jdbc.sql(
                                    """
                        delete from idempotency_keys
                         where idempotency_key in (
                               select idempotency_key from idempotency_keys
                                where expires_at <= now()
                                order by expires_at
                                limit :batch
                                  for update skip locked)
                        """)
                            .param("batch", batchSize)
                            .update();
            total += deleted;
        } while (deleted == batchSize);
        return total;
    }
}
