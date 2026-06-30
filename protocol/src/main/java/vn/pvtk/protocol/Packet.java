package vn.pvtk.protocol;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A single application-level message exchanged between client and server.
 *
 * <p>This is a faithful, modern reimplementation of the original Phong&nbsp;V&acirc;n
 * client message class (obfuscated as {@code bc} in the J2ME jar). The on-the-wire
 * encoding is preserved exactly so that the rewritten server and clients remain
 * binary-compatible with the original protocol &mdash; see {@code docs/PROTOCOL.md}.
 *
 * <h2>Frame layout</h2>
 * <pre>
 *   +----------------+----------------+------------------------+
 *   | length  (u16)  | command (u16)  | payload (length-4 B)   |
 *   +----------------+----------------+------------------------+
 * </pre>
 * {@code length} is the size of the <em>entire</em> frame including the 4-byte
 * header, encoded big-endian. A length value of {@code 0xFFFF} (-1 as a signed
 * short) is reserved as a keep-alive / ping marker and carries no payload.
 *
 * <h2>Primitive encoding</h2>
 * <ul>
 *   <li>{@code bool}   &rarr; 1 byte (0/1)</li>
 *   <li>{@code byte}   &rarr; 1 byte</li>
 *   <li>{@code short}  &rarr; 2 bytes, big-endian</li>
 *   <li>{@code int}    &rarr; 4 bytes, big-endian</li>
 *   <li>{@code long}   &rarr; 8 bytes, big-endian</li>
 *   <li>{@code bytes}  &rarr; 3-byte big-endian length prefix, then the raw bytes</li>
 *   <li>{@code string} &rarr; 3-byte big-endian <em>char-count</em> prefix, then
 *       UTF-16BE chars (2 bytes each)</li>
 * </ul>
 *
 * <p>Instances are <strong>not</strong> thread-safe; a packet is read or written
 * by a single thread at a time, exactly as in the original client.
 */
public final class Packet {

    /** Reserved length marking a keep-alive frame (no command, no payload). */
    public static final int KEEPALIVE_LENGTH = 0xFFFF;

    /** Size of the frame header in bytes ({@code length} + {@code command}). */
    public static final int HEADER_SIZE = 4;

    /** Default initial payload capacity, matching the original 1&nbsp;KiB buffer. */
    private static final int DEFAULT_CAPACITY = 1024;

    private short command;
    private byte[] buf;
    private int pos;
    private int limit;

    /** Optional human-readable annotations (debug log / error text), as in the original. */
    private StringBuilder log;
    private StringBuilder error;

    /** Creates an empty writable packet for the given command opcode. */
    public Packet(int command) {
        this.command = (short) command;
        this.buf = new byte[DEFAULT_CAPACITY];
        this.pos = 0;
        this.limit = 0;
    }

    /** Wraps an existing payload for reading (e.g. a frame received from the network). */
    public Packet(int command, byte[] payload) {
        this.command = (short) command;
        this.buf = payload != null ? payload : new byte[0];
        this.pos = 0;
        this.limit = this.buf.length;
    }

    // ------------------------------------------------------------------
    // Command / cursor
    // ------------------------------------------------------------------

    public short command() {
        return command;
    }

    public void command(int command) {
        this.command = (short) command;
    }

    /** Rewinds the read/write cursor to the start, preserving content for re-reads. */
    public void rewind() {
        this.pos = 0;
    }

    /** Number of payload bytes currently written (write mode) / total (read mode). */
    public int length() {
        return Math.max(pos, limit);
    }

    public int remaining() {
        return limit - pos;
    }

    public boolean hasRemaining() {
        return pos < limit;
    }

    private void ensure(int extra) {
        if (pos + extra <= buf.length) {
            return;
        }
        int cap = buf.length;
        while (pos + extra > cap) {
            cap <<= 1;
        }
        buf = Arrays.copyOf(buf, cap);
    }

    private void grewWrite() {
        if (pos > limit) {
            limit = pos;
        }
    }

    // ------------------------------------------------------------------
    // Writers
    // ------------------------------------------------------------------

    public Packet putBool(boolean v) {
        ensure(1);
        buf[pos++] = (byte) (v ? 1 : 0);
        grewWrite();
        return this;
    }

    public Packet putByte(int v) {
        ensure(1);
        buf[pos++] = (byte) v;
        grewWrite();
        return this;
    }

    public Packet putShort(int v) {
        ensure(2);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
        grewWrite();
        return this;
    }

    public Packet putInt(int v) {
        ensure(4);
        buf[pos++] = (byte) (v >>> 24);
        buf[pos++] = (byte) (v >>> 16);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
        grewWrite();
        return this;
    }

    public Packet putLong(long v) {
        ensure(8);
        buf[pos++] = (byte) (v >>> 56);
        buf[pos++] = (byte) (v >>> 48);
        buf[pos++] = (byte) (v >>> 40);
        buf[pos++] = (byte) (v >>> 32);
        buf[pos++] = (byte) (v >>> 24);
        buf[pos++] = (byte) (v >>> 16);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
        grewWrite();
        return this;
    }

    /** Writes a length-prefixed byte array (3-byte big-endian length). */
    public Packet putBytes(byte[] v) {
        if (v == null) {
            return putU24(0);
        }
        putU24(v.length);
        ensure(v.length);
        System.arraycopy(v, 0, buf, pos, v.length);
        pos += v.length;
        grewWrite();
        return this;
    }

    /**
     * Writes a UTF-16BE string with a 3-byte big-endian <em>char-count</em> prefix,
     * matching the original {@code writeString} behaviour exactly.
     */
    public Packet putString(String s) {
        if (s == null) {
            s = "";
        }
        putU24(s.length());
        ensure(s.length() * 2);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            buf[pos++] = (byte) (c >>> 8);
            buf[pos++] = (byte) c;
        }
        grewWrite();
        return this;
    }

    private Packet putU24(int v) {
        ensure(3);
        buf[pos++] = (byte) (v >>> 16);
        buf[pos++] = (byte) (v >>> 8);
        buf[pos++] = (byte) v;
        grewWrite();
        return this;
    }

    // ------------------------------------------------------------------
    // Readers
    // ------------------------------------------------------------------

    public boolean getBool() {
        return buf[pos++] == 1;
    }

    public byte getByte() {
        return buf[pos++];
    }

    public int getUByte() {
        return buf[pos++] & 0xFF;
    }

    public short getShort() {
        return (short) (((buf[pos++] & 0xFF) << 8) | (buf[pos++] & 0xFF));
    }

    public int getUShort() {
        return getShort() & 0xFFFF;
    }

    public int getInt() {
        return ((buf[pos++] & 0xFF) << 24)
                | ((buf[pos++] & 0xFF) << 16)
                | ((buf[pos++] & 0xFF) << 8)
                | (buf[pos++] & 0xFF);
    }

    public long getLong() {
        long v = 0;
        for (int i = 0; i < 8; i++) {
            v = (v << 8) | (buf[pos++] & 0xFFL);
        }
        return v;
    }

    public byte[] getBytes() {
        int len = getU24();
        byte[] out = new byte[len];
        System.arraycopy(buf, pos, out, 0, len);
        pos += len;
        return out;
    }

    public String getString() {
        int n = getU24();
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            int c = ((buf[pos++] & 0xFF) << 8) | (buf[pos++] & 0xFF);
            sb.append((char) c);
        }
        return sb.toString();
    }

    private int getU24() {
        return ((buf[pos++] & 0xFF) << 16)
                | ((buf[pos++] & 0xFF) << 8)
                | (buf[pos++] & 0xFF);
    }

    // ------------------------------------------------------------------
    // Optional annotations (parity with the original message class)
    // ------------------------------------------------------------------

    public Packet appendLog(String s) {
        if (s != null) {
            (log == null ? (log = new StringBuilder()) : log).append(s);
        }
        return this;
    }

    public String logText() {
        return log == null ? "" : log.toString();
    }

    public Packet appendError(String s) {
        if (s != null) {
            (error == null ? (error = new StringBuilder()) : error).append(s);
        }
        return this;
    }

    public String errorText() {
        return error == null ? "" : error.toString();
    }

    // ------------------------------------------------------------------
    // Framing
    // ------------------------------------------------------------------

    /**
     * Serializes this packet into a complete wire frame
     * ({@code [length:u16][command:u16][payload]}).
     *
     * @throws ProtocolException if the frame would exceed the 32&nbsp;767-byte
     *         limit imposed by the signed-short length field.
     */
    public byte[] toFrame() {
        int payloadLen = length();
        int frameLen = payloadLen + HEADER_SIZE;
        if (frameLen > Short.MAX_VALUE || (command & 0xFFFF) > Short.MAX_VALUE) {
            throw new ProtocolException(
                    "Frame too large for u16 length field: len=" + frameLen + " cmd=" + command);
        }
        byte[] out = new byte[frameLen];
        out[0] = (byte) (frameLen >>> 8);
        out[1] = (byte) frameLen;
        out[2] = (byte) (command >>> 8);
        out[3] = (byte) command;
        if (payloadLen > 0) {
            System.arraycopy(buf, 0, out, HEADER_SIZE, payloadLen);
        }
        return out;
    }

    /** Returns a copy of the raw payload bytes (without the frame header). */
    public byte[] payload() {
        return Arrays.copyOf(buf, length());
    }

    /** A keep-alive frame: just the {@code 0xFFFF} length marker. */
    public static byte[] keepAliveFrame() {
        return new byte[]{(byte) 0xFF, (byte) 0xFF};
    }

    @Override
    public String toString() {
        return "Packet{cmd=" + (command & 0xFFFF)
                + " (" + Opcodes.name(command & 0xFFFF) + ")"
                + ", len=" + length() + "}";
    }

    /** UTF-8 helper kept for convenience in higher-level code (not used on the wire). */
    static byte[] utf8(String s) {
        try {
            return s.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s.getBytes(StandardCharsets.UTF_8);
        }
    }

    static byte[] drain(ByteArrayOutputStream b) {
        return b.toByteArray();
    }
}
