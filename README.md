# Zerosum

A double-entry ledger service with reliable, exactly-once-in-effect event delivery.
Java 25 / Spring Boot 4 / PostgreSQL / Kafka / Debezium.

## Quick start
 - cp deploy/.env.example deploy/.env
 - docker compose -f deploy/docker-compose.yml up -d