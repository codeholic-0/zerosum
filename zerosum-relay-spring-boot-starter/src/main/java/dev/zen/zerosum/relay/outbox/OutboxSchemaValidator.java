package dev.zen.zerosum.relay.outbox;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Fails startup, with an actionable message, if the outbox table is missing or has the wrong
 * columns. It runs after ALL singletons exist, so a migration tool such as Flyway has already
 * created the table on a fresh database.
 */
public class OutboxSchemaValidator implements SmartInitializingSingleton {

    static final Map<String, String> EXPECTED_COLUMNS = new LinkedHashMap<>();

    static {
        EXPECTED_COLUMNS.put("id", "uuid");
        EXPECTED_COLUMNS.put("aggregatetype", "text");
        EXPECTED_COLUMNS.put("aggregateid", "text");
        EXPECTED_COLUMNS.put("type", "text");
        EXPECTED_COLUMNS.put("event_version", "integer");
        EXPECTED_COLUMNS.put("payload", "jsonb");
        EXPECTED_COLUMNS.put("created_at", "timestamp with time zone");
    }

    static final String EXPECTED_DDL =
            """
        CREATE TABLE outbox (
            id             UUID        PRIMARY KEY,
            aggregatetype  TEXT        NOT NULL,
            aggregateid    TEXT        NOT NULL,
            type           TEXT        NOT NULL,
            event_version  INTEGER     NOT NULL CHECK (event_version > 0),
            payload        JSONB       NOT NULL,
            created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
        );
        CREATE INDEX idx_outbox_created_at ON outbox (created_at);""";

    private final JdbcClient jdbc;

    public OutboxSchemaValidator(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void afterSingletonsInstantiated() {
        validate();
    }

    void validate() {
        Map<String, String> actual =
                jdbc
                        .sql(
                                """
                    select column_name, data_type
                      from information_schema.columns
                     where table_schema = current_schema() and table_name = 'outbox'
                    """)
                        .query((rs, rowNum) -> Map.entry(rs.getString(1), rs.getString(2)))
                        .list()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        if (actual.isEmpty()) {
            throw new IllegalStateException(
                    "The 'outbox' table does not exist. Add a migration to your service that"
                            + " creates it, for example:\n\n"
                            + EXPECTED_DDL
                            + "\n\n(or disable this check with relay.outbox.validate-schema=false)");
        }

        List<String> problems = new ArrayList<>();
        EXPECTED_COLUMNS.forEach(
                (column, type) -> {
                    String found = actual.get(column);
                    if (found == null) {
                        problems.add("missing column '" + column + "' (expected " + type + ")");
                    } else if (!found.equals(type)) {
                        problems.add(
                                "column '" + column + "' is " + found + " but must be " + type);
                    }
                });

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "The 'outbox' table does not match what the relay starter expects: "
                            + String.join("; ", problems)
                            + ".\n\nExpected:\n\n"
                            + EXPECTED_DDL);
        }
    }
}
