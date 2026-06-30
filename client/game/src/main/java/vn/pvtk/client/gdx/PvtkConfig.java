package vn.pvtk.client.gdx;

/** Launch configuration handed to {@link PvtkGame} by each platform launcher. */
public final class PvtkConfig {

    public String host = "127.0.0.1";
    public int port = 30000;
    public String username = "Guest";
    public int tileSize = 24;

    public PvtkConfig() {
    }

    public PvtkConfig(String host, int port, String username) {
        this.host = host;
        this.port = port;
        this.username = username;
    }
}
