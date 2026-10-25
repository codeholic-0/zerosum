package dev.zen.zerosum.ledger.repositories;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByTransferIdOrderByIdAsc(UUID transferId);

    /** Credits minus debits for one account, in minor units. 0 if it has no entries. */
    @Query(
            """
        select coalesce(sum(case when e.direction = dev.zen.zerosum.ledger.enums.Direction.CREDIT
                                 then e.amountMinor else -e.amountMinor end), 0L)
        from JournalEntry e
        where e.accountId = :accountId
        """)
    long balanceMinor(@Param("accountId") UUID accountId);

    /** Net of all entries per currency. Every value must be 0 in a healthy ledger. */
    @Query(
            """
        select e.currency as currency,
               sum(case when e.direction = dev.zen.zerosum.ledger.enums.Direction.CREDIT
                        then e.amountMinor else -e.amountMinor end) as total
        from JournalEntry e
        group by e.currency
        """)
    List<CurrencyTotal> totalsByCurrency();
}
