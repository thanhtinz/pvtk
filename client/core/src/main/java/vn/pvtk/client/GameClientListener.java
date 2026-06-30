package vn.pvtk.client;

import vn.pvtk.protocol.message.Messages.ChatBroadcast;

/**
 * High-level game events surfaced to the UI layer. All callbacks fire on the
 * network thread; UI code should marshal to its own render/main thread as needed
 * (libGDX: {@code Gdx.app.postRunnable}).
 */
public interface GameClientListener {

    default void onConnected() {
    }

    default void onLoginResult(boolean ok, String message) {
    }

    /** The world snapshot or an entity set changed; re-read {@link GameClient#state()}. */
    default void onWorldChanged() {
    }

    default void onEntityMoved(int entityId, int x, int y, int dir) {
    }

    default void onChat(ChatBroadcast chat) {
    }

    default void onDisconnected(String reason) {
    }
}
