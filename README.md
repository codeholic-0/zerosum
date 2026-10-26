# Zerosum

A double-entry ledger service with reliable, exactly-once-in-effect event delivery.
Java 25 / Spring Boot 4 / PostgreSQL / Kafka / Debezium.

## Quick start
 - cp deploy/.env.example deploy/.env
 - docker compose -f deploy/docker-compose.yml up -d

# Run the ledger service (loads the same credentials into your shell first):

```bash
set -a; source deploy/.env; set +a
./mvnw -B -pl zerosum-ledger-service spring-boot:run
```

## Ledger core

`zerosum-ledger-service` is the double-entry core: accounts, immutable journal entries and
balanced transfers on PostgreSQL.

### Tests

Tests need Docker: Testcontainers starts a throwaway `postgres:17`.
- JaCoCo enforces at least 80% line coverage on the module.

```bash
./mvnw -B verify
```
