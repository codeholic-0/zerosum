package dev.zen.zerosum.ledger.domain;

import dev.zen.zerosum.ledger.enums.AccountType;
import dev.zen.zerosum.ledger.exception.LedgerException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "accounts")
@Immutable
@Getter
public class Account implements Persistable<UUID> {

    @Id private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType type;

    @Column(name = "min_balance_minor")
    private Long minBalanceMinor;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient private boolean newEntity = true;

    protected Account() {}

    private Account(
            String code, String name, Currency currency, AccountType type, Long minBalanceMinor) {
        if (minBalanceMinor != null && minBalanceMinor > 0) {
            throw new LedgerException.InvalidInput(
                    "minBalanceMinor must be zero or negative: " + minBalanceMinor);
        }
        this.id = UUID.randomUUID();
        this.code = Objects.requireNonNull(code, "code");
        this.name = Objects.requireNonNull(name, "name");
        this.currency = Objects.requireNonNull(currency, "currency").getCurrencyCode();
        this.type = Objects.requireNonNull(type, "type");
        this.minBalanceMinor = minBalanceMinor;
        this.createdAt = Instant.now();
    }

    /** An unconstrained account: no balance limit, and its balance is never locked. */
    public static Account open(String code, String name, Currency currency, AccountType type) {
        return open(code, name, currency, type, null);
    }

    /**
     * minBalanceMinor is the lowest allowed signed balance (zero or negative), or null for none.
     */
    public static Account open(
            String code, String name, Currency currency, AccountType type, Long minBalanceMinor) {
        return new Account(code, name, currency, type, minBalanceMinor);
    }

    public Currency getCurrency() {
        return Currency.getInstance(currency);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }

    @Override
    public @Nullable UUID getId() {
        return this.id;
    }

    @Override
    public boolean isNew() {
        return this.newEntity;
    }
}
