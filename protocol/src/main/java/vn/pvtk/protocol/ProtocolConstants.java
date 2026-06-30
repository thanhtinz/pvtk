package vn.pvtk.protocol;

/** Shared transport constants for the Phong&nbsp;V&acirc;n multiplayer protocol. */
public final class ProtocolConstants {

    private ProtocolConstants() {
    }

    /** Default authoritative game-server TCP port (original client used {@code socket://host:30000}). */
    public static final int DEFAULT_GAME_PORT = 30000;

    /** Maximum size of a single frame (signed-short length field). */
    public static final int MAX_FRAME_SIZE = Short.MAX_VALUE;

    /** Protocol revision implemented by this codebase. */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * Recommended client keep-alive interval in milliseconds. The original
     * client periodically wrote a {@code 0xFFFF} length marker to keep the
     * GPRS/socket connection alive; the server treats it as a no-op ping.
     */
    public static final long KEEPALIVE_INTERVAL_MS = 25_000L;
}
