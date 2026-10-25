package dev.zen.zerosum.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "journal_entries")
@Immutable
@Getter
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transfer_id", nullable = false)
    private UUID transferId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Direction direction;

    @Column(name = "amount_minor", nullable = false)
    private long amountMinor;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JournalEntry() {}

    private JournalEntry(UUID transferId, UUID accountId, Direction direction, Money amount) {
        this.transferId = Objects.requireNonNull(transferId, "transferId");
        this.accountId = Objects.requireNonNull(accountId, "accountId");
        this.direction = Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(amount, "amount");
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Entry amount must be positive: " + amount);
        }
        this.amountMinor = amount.amountMinor();
        this.currency = amount.currency().getCurrencyCode();
        this.createdAt = Instant.now();
    }

    public static JournalEntry of(
            UUID transferId, UUID accountId, Direction direction, Money amount) {
        return new JournalEntry(transferId, accountId, direction, amount);
    }
}
