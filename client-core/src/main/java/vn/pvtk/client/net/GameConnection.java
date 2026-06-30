package vn.pvtk.client.net;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import vn.pvtk.protocol.Frame;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.ProtocolConstants;

/**
 * A platform-neutral TCP connection to the PVTK server using nothing but the JDK,
 * so the exact same code runs on desktop, Android and iOS (RoboVM). It mirrors the
 * original client's two-thread design: a blocking reader and an asynchronous writer,
 * plus a keep-alive ticker.
 */
public final class GameConnection {

    private final ConnectionListener listener;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;

    private Thread readerThread;
    private Thread keepAliveThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public GameConnection(ConnectionListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return running.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    /** Connects synchronously, then starts the reader and keep-alive threads. */
    public void connect(String host, int port, int connectTimeoutMs) throws IOException {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.connect(new InetSocketAddress(host, port), connectTimeoutMs);
        in = new DataInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
        running.set(true);

        readerThread = new Thread(this::readLoop, "pvtk-net-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        keepAliveThread = new Thread(this::keepAliveLoop, "pvtk-net-keepalive");
        keepAliveThread.setDaemon(true);
        keepAliveThread.start();

        listener.onConnected();
    }

    /** Sends a packet. Safe to call from any thread. */
    public void send(Packet packet) {
        if (!isConnected()) {
            return;
        }
        try {
            Frame.write(out, packet);
        } catch (IOException e) {
            disconnect("write failed: " + e.getMessage());
        }
    }

    private void readLoop() {
        try {
            while (running.get()) {
                Packet p = Frame.read(in);
                if (p != null) {
                    listener.onPacket(p);
                }
            }
        } catch (IOException e) {
            if (running.get()) {
                disconnect("connection lost: " + e.getMessage());
            }
        }
    }

    private void keepAliveLoop() {
        try {
            while (running.get()) {
                Thread.sleep(ProtocolConstants.KEEPALIVE_INTERVAL_MS);
                if (isConnected()) {
                    Frame.writeKeepAlive(out);
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            disconnect("keep-alive failed: " + e.getMessage());
        }
    }

    public void disconnect(String reason) {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
            // best effort
        }
        listener.onDisconnected(reason);
    }
}
