package dev.zen.zerosum.ledger.domain;

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
@Table(name = "transfers")
@Immutable
@Getter
public class Transfer implements Persistable<UUID> {

    @Id private UUID id;

    private String reference;

    private String description;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Transient private boolean newEntity = true;

    protected Transfer() {}

    private Transfer(String reference, String description, Currency currency) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.description = description;
        this.currency = Objects.requireNonNull(currency, "currency").getCurrencyCode();
        this.createdAt = Instant.now();
    }

    public static Transfer create(String reference, String description, Currency currency) {
        return new Transfer(reference, description, currency);
    }

    @Override
    public @Nullable UUID getId() {
        return null;
    }

    @Override
    public boolean isNew() {
        return false;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.newEntity = false;
    }
}
