package dev.devbridge.http;

/** An error that maps to an HTTP status code. */
public class BridgeException extends RuntimeException {
    private final int status;

    public BridgeException(int status, String message) {
        super(message);
        this.status = status;
    }

    public BridgeException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static BridgeException badRequest(String msg) { return new BridgeException(400, msg); }
    public static BridgeException notFound(String msg) { return new BridgeException(404, msg); }
    public static BridgeException unavailable(String msg) { return new BridgeException(503, msg); }
}
