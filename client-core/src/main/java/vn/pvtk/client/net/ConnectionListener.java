package vn.pvtk.client.net;

import vn.pvtk.protocol.Packet;

/** Low-level connection callbacks, invoked on the network reader thread. */
public interface ConnectionListener {

    void onConnected();

    void onPacket(Packet packet);

    void onDisconnected(String reason);
}
