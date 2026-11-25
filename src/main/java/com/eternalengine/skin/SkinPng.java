package com.eternalengine.skin;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A painted skin, as a PNG.
 *
 * <h3>Why not write the pixels straight into the texture</h3>
 * Because the pixel layout of Minecraft's {@code NativeImage} is a thing you have to be told, and this
 * file does not have to be: a PNG's channel order is fixed by its own specification (8-bit RGBA, red
 * first). {@code NativeImage.read} decodes it, so the skin that reaches the screen is the skin that was
 * painted, with no swap to get right and no probe to trust.
 *
 * <p>And it is checkable from here: the self-test encodes a skin, writes it, and a Python beside it
 * opens the file and compares the pixels against the ones the painter put in memory. A PNG has a
 * defined meaning, so both languages agree about it by construction rather than by hope.
 *
 * <p>Written by hand - signature, IHDR, one IDAT, IEND - because the encoder is forty lines and the
 * alternative is a dependency that only exists on one side.
 */
public final class SkinPng {

    /** Vanilla skins are 64x64 in the modern format; the pool paints that. */
    public static final int SIZE = 64;

    private SkinPng() {
    }

    /** The finished file's bytes: what {@code NativeImage.read} wants, and what a viewer can open. */
    public static byte[] encode(SkinPainter.Skin skin) {
        // the raw scanlines: one filter byte (0 = none) then RGBA per pixel
        byte[] raw = new byte[SIZE * (1 + SIZE * 4)];
        int o = 0;
        for (int y = 0; y < SIZE; y++) {
            raw[o++] = 0;
            for (int x = 0; x < SIZE; x++) {
                int argb = skin.get(x, y);
                raw[o++] = (byte) (argb >>> 16);       // red
                raw[o++] = (byte) (argb >>> 8);        // green
                raw[o++] = (byte) argb;                // blue
                raw[o++] = (byte) (argb >>> 24);       // alpha
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 4 + 512);
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'}, 0, 8);
        byte[] ihdr = new byte[13];
        putInt(ihdr, 0, SIZE);
        putInt(ihdr, 4, SIZE);
        ihdr[8] = 8;                 // bit depth
        ihdr[9] = 6;                 // colour type: truecolour with alpha
        ihdr[10] = 0;                // deflate
        ihdr[11] = 0;                // adaptive filtering
        ihdr[12] = 0;                // no interlace
        chunk(out, "IHDR", ihdr);
        chunk(out, "IDAT", deflate(raw));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static void putInt(byte[] target, int at, int value) {
        target[at] = (byte) (value >>> 24);
        target[at + 1] = (byte) (value >>> 16);
        target[at + 2] = (byte) (value >>> 8);
        target[at + 3] = (byte) value;
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        byte[] header = new byte[8];
        putInt(header, 0, data.length);
        header[4] = (byte) type.charAt(0);
        header[5] = (byte) type.charAt(1);
        header[6] = (byte) type.charAt(2);
        header[7] = (byte) type.charAt(3);
        out.write(header, 0, 8);
        out.write(data, 0, data.length);
        CRC32 crc = new CRC32();
        crc.update(header, 4, 4);                       // the CRC covers the type and the data
        crc.update(data, 0, data.length);
        byte[] tail = new byte[4];
        putInt(tail, 0, (int) crc.getValue());
        out.write(tail, 0, 4);
    }

    private static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 8 + 64);
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buffer);
            out.write(buffer, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }
}
