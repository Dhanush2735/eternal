package com.eternalengine.skin;

import com.eternalengine.EternalEngineMod;

/**
 * The one hash the skin system uses, and the reason it is written out by hand.
 *
 * <h3>Why not {@code String.hashCode()}</h3>
 * Because the skins were designed and checked in Python, and the sheets that were approved were drawn
 * from {@code hashlib.blake2b(text, digest_size=8)}. If the game used a different hash, every sheet
 * would be a picture of a world that does not exist: the pool would still be right, the faces would be
 * different. So the same function is implemented here, and {@link #verify()} proves it at start-up
 * against vectors taken from the Python.
 *
 * <p>BLAKE2b with a 64-bit digest is small enough to write out in full (RFC 7693): twelve rounds, ten
 * message words mixed per round, the standard sigma permutation. Only the parts a 64-bit digest needs
 * are here — no key, no salt, no personalisation, no tree mode.
 *
 * <p>Every draw in the skin pool goes through {@link #of(String)}: which tone, which hair, which style,
 * which garment today. A different hash is a different world, so this file is the load-bearing one.
 */
public final class SkinHashes {

    private static final long[] IV = {
            0x6A09E667F3BCC908L, 0xBB67AE8584CAA73BL, 0x3C6EF372FE94F82BL, 0xA54FF53A5F1D36F1L,
            0x510E527FADE682D1L, 0x9B05688C2B3E6C1FL, 0x1F83D9ABFB41BD6BL, 0x5BE0CD19137E2179L
    };

    private static final byte[][] SIGMA = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3},
            {11, 8, 12, 0, 5, 2, 15, 13, 10, 14, 3, 6, 7, 1, 9, 4},
            {7, 9, 3, 1, 13, 12, 11, 14, 2, 6, 5, 10, 4, 0, 15, 8},
            {9, 0, 5, 7, 2, 4, 10, 15, 14, 1, 11, 12, 6, 8, 3, 13},
            {2, 12, 6, 10, 0, 11, 8, 3, 4, 13, 7, 5, 15, 14, 1, 9},
            {12, 5, 1, 15, 14, 13, 4, 10, 0, 7, 6, 3, 9, 2, 8, 11},
            {13, 11, 7, 14, 12, 1, 3, 9, 5, 0, 15, 4, 8, 6, 2, 10},
            {6, 15, 14, 9, 11, 3, 0, 8, 12, 2, 13, 7, 1, 4, 10, 5},
            {10, 2, 8, 4, 7, 6, 1, 5, 15, 11, 9, 14, 3, 12, 13, 0},
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15},
            {14, 10, 4, 8, 9, 15, 13, 6, 1, 12, 0, 2, 11, 7, 5, 3}
    };

    private SkinHashes() {
    }

    /** BLAKE2b-64 of the UTF-8 bytes of {@code text}, read big-endian - the Python's value exactly. */
    public static long of(String text) {
        byte[] msg = text == null ? new byte[0] : text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        long[] h = IV.clone();
        h[0] ^= 0x01010000L ^ 8L;                 // digest length 8, no key

        long t0 = 0L;
        int off = 0;
        // all complete 128-byte blocks except the last: the last block is always compressed, even if
        // it is full, so the loop stops one block short
        while (msg.length - off > 128) {
            t0 += 128;
            compress(h, msg, off, 128, t0, false);
            off += 128;
        }
        int last = msg.length - off;
        t0 += last;
        compress(h, msg, off, last, t0, true);

        long out = 0L;
        for (int i = 0; i < 8; i++) {             // the first eight bytes of the state, little-endian
            out |= (h[i / 8] >>> (8 * (i % 8)) & 0xFFL) << (8 * i);
        }
        // the Python reads those eight digest bytes big-endian (int.from_bytes(digest, "big")), so the
        // assembled word is byte-reversed against its value. Verified against the vectors below; the
        // first run of the self-test caught this and printed both numbers side by side.
        return Long.reverseBytes(out);
    }

    /** The same value as an unsigned string, for logs and for keys. */
    public static String hex(String text) {
        return String.format("%016X", of(text));
    }

    /** A value in [0, bound), from the same hash. Negative-safe: floorMod, never a negative index. */
    public static int pick(String text, int bound) {
        return bound <= 0 ? 0 : (int) Math.floorMod(of(text), (long) bound);
    }

    // ------------------------------------------------------------------ the compression
    private static void compress(long[] h, byte[] msg, int off, int len, long t0, boolean last) {
        long[] m = new long[16];
        byte[] block = new byte[128];
        System.arraycopy(msg, off, block, 0, Math.max(0, Math.min(len, 128)));
        for (int i = 0; i < 16; i++) {
            long v = 0L;
            for (int j = 7; j >= 0; j--) {
                v = (v << 8) | (block[i * 8 + j] & 0xFFL);
            }
            m[i] = v;
        }
        long[] v = new long[16];
        System.arraycopy(h, 0, v, 0, 8);
        System.arraycopy(IV, 0, v, 8, 8);
        v[12] ^= t0;
        v[13] ^= 0L;                              // the high half of the counter: messages here are small
        if (last) {
            v[14] = ~v[14];
        }
        for (int r = 0; r < 12; r++) {
            byte[] s = SIGMA[r];
            g(v, 0, 4, 8, 12, m[s[0]], m[s[1]]);
            g(v, 1, 5, 9, 13, m[s[2]], m[s[3]]);
            g(v, 2, 6, 10, 14, m[s[4]], m[s[5]]);
            g(v, 3, 7, 11, 15, m[s[6]], m[s[7]]);
            g(v, 0, 5, 10, 15, m[s[8]], m[s[9]]);
            g(v, 1, 6, 11, 12, m[s[10]], m[s[11]]);
            g(v, 2, 7, 8, 13, m[s[12]], m[s[13]]);
            g(v, 3, 4, 9, 14, m[s[14]], m[s[15]]);
        }
        for (int i = 0; i < 8; i++) {
            h[i] ^= v[i] ^ v[i + 8];
        }
    }

    private static void g(long[] v, int a, int b, int c, int d, long x, long y) {
        v[a] = v[a] + v[b] + x;
        v[d] = Long.rotateRight(v[d] ^ v[a], 32);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 24);
        v[a] = v[a] + v[b] + y;
        v[d] = Long.rotateRight(v[d] ^ v[a], 16);
        v[c] = v[c] + v[d];
        v[b] = Long.rotateRight(v[b] ^ v[c], 63);
    }

    // ------------------------------------------------------------------ the proof
    /**
     * The vectors below were printed by the Python that drew the approved sheets
     * ({@code hashlib.blake2b(t.encode(), digest_size=8)} read big-endian). If this fails, the skins
     * this system paints are not the skins in the sheets, and the log says so rather than pretending.
     *
     * @return true when every vector matches
     */
    public static boolean verify() {
        String[] texts = {"", "a", "deccan", "deccan|tone", "8f2c-npc-0001|style", "348:1570|tone",
                          "india"};
        long[] want = {0xE4A6A0577479B2B4L, 0x40F89E395B66422FL, 0xAC398DEEB29065E9L,
                       0xBFB12F8C806E3351L, 0xD050BF0507F79B53L, 0xC9185347EB212672L,
                       0xA7D3639CA56C5F8CL};
        boolean ok = true;
        for (int i = 0; i < texts.length; i++) {
            long got = of(texts[i]);
            if (got != want[i]) {
                ok = false;
                EternalEngineMod.getLogger().error(
                        "[SkinHashes] BLAKE2b-64 mismatch on \"{}\": got {} want {}",
                        texts[i], String.format("%016X", got), String.format("%016X", want[i]));
            }
        }
        if (ok) {
            EternalEngineMod.getLogger().info(
                    "[SkinHashes] BLAKE2b-64 verified against {} vectors — the painted skins are the "
                    + "skins that were drawn in the sheets", texts.length);
        }
        return ok;
    }
}
