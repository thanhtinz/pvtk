package vn.pvtk.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Entry point for the PVTK authoritative game server. */
public final class ServerMain {

    private static final Logger log = LoggerFactory.getLogger(ServerMain.class);

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromArgs(args);
        GameServer server = new GameServer(config);

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop, "pvtk-shutdown"));

        log.info("Starting Phong Vân (PVTK) server — protocol v{}",
                vn.pvtk.protocol.ProtocolConstants.PROTOCOL_VERSION);
        server.start();
        server.awaitShutdown();
    }
}
