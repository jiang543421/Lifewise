package com.lifewise.shared.infra.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * SHA-256 hashes selected method arguments before they land in the audit
 * {@code payload} column. Plaintext secrets never touch the database.
 *
 * <p>When {@code mask == true} the hashed slot is replaced with the literal
 * {@code "***"} sentinel so reversibility is impossible even on a leaked dump.
 */
public class AuditPayloadHasher {

    private static final String MASK = "***";

    public String hash(Object[] args, int[] indices, boolean mask) {
        if (mask) {
            return MASK;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
        for (int idx : indices) {
            if (idx < 0 || idx >= args.length) {
                continue;
            }
            Object value = args[idx];
            String text = value == null ? "null" : value.toString();
            digest.update(text.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}