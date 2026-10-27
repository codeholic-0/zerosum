package dev.zen.zerosum.ledger.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "zerosum.idempotency.cleanup-initial-delay=P1D")
public abstract class AbstractApiIntegrationTest extends AbstractIntegrationTest {
    public record Reply(int status, String body, HttpHeaders headers) {
        public String header(String name) {
            return headers.firstValue(name).orElse(null);
        }
    }

    private static final HttpClient HTTP =
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    @Value("${local.server.port}")
    private int port;

    @Autowired protected ObjectMapper objectMapper;

    protected Reply post(String path, String json, String idempotencyKey) {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return send(request.build());
    }

    protected Reply get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET().build());
    }

    protected <T> T read(Reply reply, Class<T> type) {
        return objectMapper.readValue(reply.body(), type);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> readMap(Reply reply) {
        return objectMapper.readValue(reply.body(), Map.class);
    }

    protected String problemType(Reply reply) {
        return (String) readMap(reply).get("type");
    }

    protected static String leg(UUID accountId, String direction, long amountMinor) {
        return """
        {"accountId":"%s","direction":"%s","amountMinor":%d}"""
                .formatted(accountId, direction, amountMinor);
    }

    protected static String transfer(String currency, String... legs) {
        return """
        {"reference":"ref","description":"test","currency":"%s","legs":[%s]}"""
                .formatted(currency, String.join(",", legs));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private Reply send(HttpRequest request) {
        try {
            HttpResponse<String> response =
                    HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            return new Reply(response.statusCode(), response.body(), response.headers());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
