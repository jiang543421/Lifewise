package com.lifewise.shared.infra.security;

import com.lifewise.shared.infra.security.exception.JwtExpiredException;
import com.lifewise.shared.infra.security.exception.JwtInvalidException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HS256 JWT generator + parser. Two token types share the same secret but carry
 * a {@code typ} claim ({@code access} vs {@code refresh}) so a refresh token cannot
 * authenticate an API request.
 *
 * <p>Secret must decode to at least 32 bytes (256 bits) per RFC 7518 §3.2.
 */
public class JwtTokenProvider {

    private static final String ALG = "HmacSHA256";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKeySpec signingKey;
    private final String issuer;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Clock clock;

    public JwtTokenProvider(String base64Secret, String issuer, Duration accessTtl,
            Duration refreshTtl, Clock clock) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("HS256 secret must not be blank");
        }
        byte[] secretBytes = Base64.getDecoder().decode(base64Secret);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException(
                    "HS256 secret must decode to >= 256 bits (32 bytes), got " + secretBytes.length);
        }
        this.signingKey = new SecretKeySpec(secretBytes, ALG);
        this.issuer = requireNonBlank(issuer, "issuer");
        this.accessTtl = requirePositive(accessTtl, "accessTtl");
        this.refreshTtl = requirePositive(refreshTtl, "refreshTtl");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public String createAccessToken(Long userId, List<String> roles) {
        Instant now = clock.instant();
        Instant exp = now.plus(accessTtl);
        String jti = UUID.randomUUID().toString();
        List<String> safeRoles = roles == null ? List.of() : List.copyOf(roles);
        String payload = buildAccessClaims(now, exp, jti, userId, safeRoles);
        return sign(payload);
    }

    public AccessClaims parseAccessToken(String token) {
        Decoded decoded = decode(token, TYPE_ACCESS);
        return new AccessClaims(
                decoded.userId(),
                Collections.unmodifiableList(decoded.roles()),
                decoded.jti(),
                decoded.exp());
    }

    public String createRefreshToken(Long userId, String familyId) {
        Instant now = clock.instant();
        Instant exp = now.plus(refreshTtl);
        String jti = UUID.randomUUID().toString();
        String payload = buildRefreshClaims(now, exp, jti, userId, familyId);
        return sign(payload);
    }

    public JwtRefreshTokenService.RefreshClaims parseRefreshToken(String token) {
        Decoded decoded = decode(token, TYPE_REFRESH);
        return new JwtRefreshTokenService.RefreshClaims(
                decoded.jti(), decoded.familyId(), decoded.userId(), decoded.exp());
    }

    private String buildAccessClaims(Instant iat, Instant exp, String jti, Long userId, List<String> roles) {
        return new StringBuilder(160).append('{')
                .append("\"iss\":\"").append(issuer).append("\",")
                .append("\"sub\":\"").append(userId).append("\",")
                .append("\"uid\":").append(userId).append(',')
                .append("\"roles\":").append(toJsonArray(roles)).append(',')
                .append("\"jti\":\"").append(jti).append("\",")
                .append("\"iat\":").append(iat.getEpochSecond()).append(',')
                .append("\"exp\":").append(exp.getEpochSecond()).append(',')
                .append("\"typ\":\"").append(TYPE_ACCESS).append("\"")
                .append('}').toString();
    }

    private String buildRefreshClaims(Instant iat, Instant exp, String jti, Long userId, String familyId) {
        return new StringBuilder(160).append('{')
                .append("\"iss\":\"").append(issuer).append("\",")
                .append("\"sub\":\"").append(userId).append("\",")
                .append("\"uid\":").append(userId).append(',')
                .append("\"fid\":\"").append(familyId).append("\",")
                .append("\"jti\":\"").append(jti).append("\",")
                .append("\"iat\":").append(iat.getEpochSecond()).append(',')
                .append("\"exp\":").append(exp.getEpochSecond()).append(',')
                .append("\"typ\":\"").append(TYPE_REFRESH).append("\"")
                .append('}').toString();
    }

    private static String toJsonArray(List<String> values) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String v : values) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            first = false;
        }
        sb.append(']');
        return sb.toString();
    }

    private String sign(String payloadJson) {
        try {
            String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
            String body = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
            String signingInput = header + "." + body;
            Mac mac = Mac.getInstance(ALG);
            mac.init(signingKey);
            byte[] sig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            return signingInput + "." + base64Url(sig);
        } catch (Exception e) {
            throw new JwtInvalidException("failed to sign JWT", e);
        }
    }

    private Decoded decode(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new JwtInvalidException("token must not be blank");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new JwtInvalidException("malformed JWT: expected 3 parts");
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSig;
        byte[] providedSig;
        try {
            Mac mac = Mac.getInstance(ALG);
            mac.init(signingKey);
            expectedSig = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            providedSig = Base64.getUrlDecoder().decode(parts[2]);
        } catch (Exception e) {
            throw new JwtInvalidException("invalid JWT signature encoding", e);
        }
        if (!MessageDigest.isEqual(expectedSig, providedSig)) {
            throw new JwtInvalidException("invalid JWT signature");
        }
        String bodyJson;
        try {
            bodyJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new JwtInvalidException("malformed JWT body", e);
        }
        Decoded decoded = parseClaims(bodyJson);
        if (!expectedType.equals(decoded.typ())) {
            throw new JwtInvalidException("token type mismatch: expected " + expectedType
                    + " but got " + decoded.typ());
        }
        if (!issuer.equals(decoded.iss())) {
            throw new JwtInvalidException("token issuer mismatch: expected " + issuer
                    + " but got " + decoded.iss());
        }
        if (decoded.exp().isBefore(clock.instant())) {
            throw new JwtExpiredException("token expired at " + decoded.exp());
        }
        return decoded;
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        // Removed: replaced with MessageDigest.isEqual (JDK, constant-time per JEP 244).
        return MessageDigest.isEqual(a, b);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static Decoded parseClaims(String json) {
        try {
            String typ = extractString(json, "typ");
            String iss = extractString(json, "iss");
            String jti = extractString(json, "jti");
            String familyId = extractString(json, "fid");
            Long uid = extractLong(json, "uid");
            long iat = extractLong(json, "iat");
            long exp = extractLong(json, "exp");
            List<String> roles = extractStringArray(json, "roles");
            return new Decoded(typ, iss, jti, familyId, uid, roles,
                    Instant.ofEpochSecond(iat), Instant.ofEpochSecond(exp));
        } catch (RuntimeException e) {
            // CLAUDE.md §7.5: 不在异常 message 中泄漏 parser 内部细节（payload 片段、
            // NumberFormatException 输入字符串等）。cause 链保留 e，日志框架可拿完整 trace。
            throw new JwtInvalidException("malformed JWT claims", e);
        }
    }

    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') {
                break;
            }
            end++;
        }
        return json.substring(valueStart, end);
    }

    private static Long extractLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            return null;
        }
        int valueStart = start + marker.length();
        int end = valueStart;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == ',' || c == '}' || c == ' ') {
                break;
            }
            end++;
        }
        return Long.parseLong(json.substring(valueStart, end));
    }

    private static List<String> extractStringArray(String json, String key) {
        String marker = "\"" + key + "\":[";
        int start = json.indexOf(marker);
        if (start < 0) {
            return List.of();
        }
        int valueStart = start + marker.length();
        int end = json.indexOf(']', valueStart);
        String raw = json.substring(valueStart, end);
        if (raw.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        int i = 0;
        while (i < raw.length()) {
            int q1 = raw.indexOf('"', i);
            if (q1 < 0) {
                break;
            }
            int q2 = q1 + 1;
            while (q2 < raw.length()) {
                if (raw.charAt(q2) == '\\') {
                    q2 += 2;
                    continue;
                }
                if (raw.charAt(q2) == '"') {
                    break;
                }
                q2++;
            }
            result.add(raw.substring(q1 + 1, q2));
            i = q2 + 1;
        }
        return result;
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    /**
     * Parsed access-token claims. {@code roles} is immutable.
     */
    public record AccessClaims(Long userId, List<String> roles, String jti, Instant expiresAt) {
    }

    private record Decoded(String typ, String iss, String jti, String familyId, Long userId,
            List<String> roles, Instant iat, Instant exp) {
    }
}