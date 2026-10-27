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

- API tests run against the real server over HTTP, including 20 identical requests fired at the
  same instant, which must produce exactly one transfer.

```bash
./mvnw -B verify
```
## API

Interactive docs: <http://localhost:8080/swagger-ui.html> (raw spec at `/v3/api-docs`).
Amounts are integer minor units, and every error is an RFC 9457 problem-details response.

| Method and path | Purpose |
|---|---|
| `POST /accounts` | Create an account (409 if the code exists) |
| `GET /accounts/{id}` | Read an account |
| `GET /accounts/{id}/balance` | Balance in minor units (credits minus debits) |
| `POST /transfers` | Post a transfer; requires `Idempotency-Key` |
| `GET /transfers/{id}` | Read a transfer with its entries |

### Idempotency

`POST /transfers` is safe to retry:

- Same key and same request: the original response is returned with `Idempotent-Replayed: true`,
  and nothing is posted again.
- Same key and a different request: `409`.
- Identical requests arriving at the same time: exactly one posts; the rest get the same response.
- A failed request (for example an unbalanced one) does not consume the key.
- Keys expire after 24 hours (`zerosum.idempotency.ttl`).

```bash
curl -si -X POST localhost:8080/transfers \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" \
  -d '{"reference":"inv-1","currency":"USD","legs":[
        {"accountId":"<cash-id>","direction":"DEBIT","amountMinor":5000},
        {"accountId":"<sales-id>","direction":"CREDIT","amountMinor":5000}]}'
```

### Errors

| Status | Problem types (`urn:zerosum:problem:...`) |
|---|---|
| 400 | `validation-failed`, malformed JSON, missing or malformed `Idempotency-Key` |
| 404 | `account-not-found`, `transfer-not-found` |
| 409 | `duplicate-account`, `idempotency-key-reuse` |
| 422 | `unbalanced-transfer`, `unknown-account`, `currency-mismatch`, `invalid-transfer`, `invalid-input` |
| 500 | `internal-error` (no internal details leaked) |
