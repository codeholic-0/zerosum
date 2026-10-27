package dev.zen.zerosum.ledger.api;

import dev.zen.zerosum.ledger.dto.request.TransferRequest;
import dev.zen.zerosum.ledger.dto.response.TransferResponse;
import dev.zen.zerosum.ledger.idempotency.TransferRequestHasher;
import dev.zen.zerosum.ledger.service.IdempotencyService;
import dev.zen.zerosum.ledger.service.PostTransferCommand;
import dev.zen.zerosum.ledger.service.PostedTransfer;
import dev.zen.zerosum.ledger.service.TransferService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/transfers")
@RequiredArgsConstructor
public class TransferController {

    /** Printable ASCII, no spaces, 1-255 characters. Clients should send a UUID. */
    private static final Pattern KEY_FORMAT = Pattern.compile("[\\x21-\\x7E]{1,255}");

    private static final String REPLAYED_HEADER = "Idempotent-Replayed";

    private final TransferService transferService;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> create(
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody TransferRequest request) {
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must be 1-255 printable ASCII characters without spaces");
        }

        // Input problems (400/422) are rejected here, before a key is claimed.
        PostTransferCommand command = request.toCommand();
        String requestHash = TransferRequestHasher.hash(command);

        IdempotencyService.Result result =
                idempotencyService.execute(
                        key,
                        requestHash,
                        () -> {
                            PostedTransfer posted = transferService.post(command);
                            TransferResponse response =
                                    TransferResponse.from(posted.transfer(), posted.entries());
                            return new IdempotencyService.Outcome(
                                    posted.transfer().getId(),
                                    HttpStatus.CREATED.value(),
                                    toJson(response));
                        });

        ResponseEntity.BodyBuilder builder =
                ResponseEntity.status(result.status())
                        .contentType(MediaType.APPLICATION_JSON)
                        .location(locationOf(result.body()));
        if (result.replayed()) {
            builder.header(REPLAYED_HEADER, "true");
        }
        return builder.body(result.body());
    }

    @GetMapping("/{id}")
    public TransferResponse get(@PathVariable UUID id) {
        PostedTransfer posted = transferService.get(id);
        return TransferResponse.from(posted.transfer(), posted.entries());
    }

    private String toJson(TransferResponse response) {
        return objectMapper.writeValueAsString(response);
    }

    private URI locationOf(String body) {
        TransferResponse stored = objectMapper.readValue(body, TransferResponse.class);
        return ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(stored.id())
                .toUri();
    }
}
