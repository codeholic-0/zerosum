package dev.zen.zerosum.ledger.service;

import dev.zen.zerosum.ledger.domain.JournalEntry;
import dev.zen.zerosum.ledger.domain.Transfer;
import java.util.List;

public record PostedTransfer(Transfer transfer, List<JournalEntry> entries) {}
