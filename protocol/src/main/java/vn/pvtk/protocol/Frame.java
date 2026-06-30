package vn.pvtk.protocol;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Stream-oriented frame reader/writer for plain {@code Socket}-based clients.
 *
 * <p>This mirrors the original client's blocking I/O threads (obfuscated as the
 * input reader {@code fk} and output writer {@code bp}) without any third-party
 * dependency, so the lightweight Java reference client can talk to the Netty
 * server using exactly the same bytes. The Netty server/clients use
 * {@code io.netty.handler.codec.LengthFieldBasedFrameDecoder}-style codecs that
 * are byte-for-byte equivalent.
 */
public final class Frame {

    private Frame() {
    }

    /**
     * Reads one frame from the stream, blocking until a full frame is available.
     *
     * @return the decoded {@link Packet}, or {@code null} for a keep-alive marker
     * @throws EOFException if the stream ends mid-frame
     */
    public static Packet read(DataInputStream in) throws IOException {
        int length = in.readShort() & 0xFFFF;
        if (length == Packet.KEEPALIVE_LENGTH) {
            return null; // keep-alive ping; nothing to dispatch
        }
        if (length < Packet.HEADER_SIZE) {
            throw new ProtocolException("Frame length " + length + " smaller than header");
        }
        int command = in.readShort() & 0xFFFF;
        byte[] payload = new byte[length - Packet.HEADER_SIZE];
        readFully(in, payload);
        return new Packet(command, payload);
    }

    /** Serializes and writes a packet as a single frame, then flushes. */
    public static void write(OutputStream out, Packet packet) throws IOException {
        byte[] frame = packet.toFrame();
        synchronized (out) {
            out.write(frame);
            out.flush();
        }
    }

    /** Sends a bare keep-alive marker. */
    public static void writeKeepAlive(OutputStream out) throws IOException {
        synchronized (out) {
            out.write(Packet.keepAliveFrame());
            out.flush();
        }
    }

    private static void readFully(DataInputStream in, byte[] dst) throws IOException {
        int off = 0;
        while (off < dst.length) {
            int n = in.read(dst, off, dst.length - off);
            if (n < 0) {
                throw new EOFException("Stream closed after " + off + "/" + dst.length + " bytes");
            }
            off += n;
        }
    }
}
