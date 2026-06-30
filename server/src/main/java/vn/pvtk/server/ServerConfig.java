package vn.pvtk.server;

import vn.pvtk.protocol.ProtocolConstants;

/** Immutable server configuration, populated from CLI args / environment. */
public record ServerConfig(String host, int port, int readTimeoutSeconds) {

    public static ServerConfig defaults() {
        return new ServerConfig("0.0.0.0", ProtocolConstants.DEFAULT_GAME_PORT, 60);
    }

    /** Builds config from {@code args} ({@code --host}, {@code --port}) and env vars. */
    public static ServerConfig fromArgs(String[] args) {
        String host = env("PVTK_HOST", "0.0.0.0");
        int port = Integer.parseInt(env("PVTK_PORT", String.valueOf(ProtocolConstants.DEFAULT_GAME_PORT)));
        int timeout = Integer.parseInt(env("PVTK_READ_TIMEOUT", "60"));

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--read-timeout" -> timeout = Integer.parseInt(args[++i]);
                default -> { }
            }
        }
        return new ServerConfig(host, port, timeout);
    }

    private static String env(String key, String def) {
        String v = System.getenv(key);
        return v != null && !v.isBlank() ? v : def;
    }
}
