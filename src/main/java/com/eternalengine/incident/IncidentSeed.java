package com.eternalengine.incident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Stable deterministic seed helper for incidents and alternative timelines.
 */
public final class IncidentSeed {

    private IncidentSeed() {
    }

    public static long stableLong(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder();
            if (parts != null) {
                for (Object part : parts) {
                    if (!builder.isEmpty()) {
                        builder.append('|');
                    }
                    builder.append(part == null ? "null" : part.toString());
                }
            }
            byte[] hash = digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (hash[i] & 0xFFL);
            }
            return value;
        } catch (Exception e) {
            return fallbackHash(parts);
        }
    }

    public static String stableHex(Object... parts) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder builder = new StringBuilder();
            if (parts != null) {
                for (Object part : parts) {
                    if (!builder.isEmpty()) {
                        builder.append('|');
                    }
                    builder.append(part == null ? "null" : part.toString());
                }
            }
            return HexFormat.of().formatHex(digest.digest(builder.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Long.toHexString(fallbackHash(parts));
        }
    }

    private static long fallbackHash(Object... parts) {
        long result = 1125899906842597L;
        if (parts != null) {
            for (Object part : parts) {
                result = 31L * result + (part == null ? 0 : part.toString().hashCode());
            }
        }
        return result;
    }
}
