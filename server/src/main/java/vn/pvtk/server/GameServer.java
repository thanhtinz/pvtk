package vn.pvtk.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.server.data.GameData;
import vn.pvtk.server.handler.GameContext;
import vn.pvtk.server.handler.PacketDispatcher;
import vn.pvtk.server.net.PacketDecoder;
import vn.pvtk.server.net.PacketEncoder;
import vn.pvtk.server.net.SessionHandler;
import vn.pvtk.server.session.SessionManager;
import vn.pvtk.server.world.World;

/**
 * The authoritative game server. Owns the Netty acceptor, the world, the session
 * registry and the dispatcher. Start it with {@link #start()} and stop it with
 * {@link #stop()}.
 */
public final class GameServer {

    private static final Logger log = LoggerFactory.getLogger(GameServer.class);

    private final ServerConfig config;
    private final GameData gameData = new GameData(GameData.resolveAssetsRoot()).loadAll();
    private final SessionManager sessions = new SessionManager();
    private final World world = new World(sessions);
    private final GameContext context = new GameContext(world, sessions, gameData);
    private final PacketDispatcher dispatcher = new PacketDispatcher(context);

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public GameServer(ServerConfig config) {
        this.config = config;
    }

    public void start() throws InterruptedException {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        ServerBootstrap b = new ServerBootstrap();
        b.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .option(ChannelOption.SO_BACKLOG, 256)
                .childOption(ChannelOption.SO_KEEPALIVE, true)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline()
                                .addLast("readTimeout", new ReadTimeoutHandler(
                                        config.readTimeoutSeconds(), TimeUnit.SECONDS))
                                .addLast("decoder", new PacketDecoder())
                                .addLast("encoder", new PacketEncoder())
                                .addLast("session", new SessionHandler(sessions, dispatcher));
                    }
                });

        serverChannel = b.bind(config.host(), config.port()).sync().channel();
        log.info("PVTK game server listening on {}:{}", config.host(), config.port());
    }

    /** Blocks until the server socket is closed. */
    public void awaitShutdown() throws InterruptedException {
        if (serverChannel != null) {
            serverChannel.closeFuture().sync();
        }
    }

    public void stop() {
        log.info("Shutting down game server...");
        if (serverChannel != null) {
            serverChannel.close();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
    }

    public World world() {
        return world;
    }

    public GameData gameData() {
        return gameData;
    }

    public SessionManager sessions() {
        return sessions;
    }

    public int boundPort() {
        if (serverChannel != null && serverChannel.localAddress() instanceof java.net.InetSocketAddress addr) {
            return addr.getPort();
        }
        return config.port();
    }
}
