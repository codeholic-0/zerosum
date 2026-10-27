package dev.zen.zerosum.ledger.idempotency;

import dev.zen.zerosum.ledger.service.PostTransferCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;

/** SHA-256 over a transfer command; independent of JSON formatting and leg order */
public final class TransferRequestHasher {
    private TransferRequestHasher() {}

    public static String hash(PostTransferCommand command) {
        StringBuilder canonical = new StringBuilder();
        field(canonical, "currency", command.currency().getCurrencyCode());
        field(canonical, "reference", command.reference());
        field(canonical, "description", command.description());
        command.legs().stream()
                .sorted(
                        Comparator.comparing(
                                        (PostTransferCommand.Leg l) -> l.accountId().toString())
                                .thenComparing(l -> l.direction().name())
                                .thenComparingLong(l -> l.amount().amountMinor()))
                .forEach(
                        leg ->
                                field(
                                        canonical,
                                        "leg",
                                        leg.accountId()
                                                + ":"
                                                + leg.direction()
                                                + ":"
                                                + leg.amount().amountMinor()));
        return HexFormat.of()
                .formatHex(sha256(canonical.toString().getBytes(StandardCharsets.UTF_8)));
    }

    /** Length-prefixed so that values never run into each other */
    private static void field(StringBuilder sb, String name, String value) {
        sb.append(name).append('=');
        if (value == null) {
            sb.append('-');
        } else {
            sb.append(value.length()).append(':').append(value);
        }
        sb.append('\n');
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
