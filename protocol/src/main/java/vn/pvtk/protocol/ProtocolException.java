package vn.pvtk.protocol;

/** Thrown when a frame or packet violates the wire-protocol invariants. */
public class ProtocolException extends RuntimeException {
    public ProtocolException(String message) {
        super(message);
    }

    public ProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}
