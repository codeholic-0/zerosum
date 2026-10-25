package dev.zen.zerosum.ledger.repositories;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {
    List<JournalEntry> findByTransferIdOrderByIdAsc(UUID transferId);
}
