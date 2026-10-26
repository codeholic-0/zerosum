package dev.zen.zerosum.ledger.repositories;

import dev.zen.zerosum.ledger.domain.Transfer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {}
