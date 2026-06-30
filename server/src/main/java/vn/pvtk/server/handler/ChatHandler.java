package vn.pvtk.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;
import vn.pvtk.protocol.message.Messages.ChatRequest;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;

/** Routes a chat message to the appropriate audience (world / map / private). */
public final class ChatHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(ChatHandler.class);
    private static final int MAX_CHAT_LEN = 256;

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        ChatRequest req = ChatRequest.from(packet);
        Player from = session.player();

        String text = req.text();
        if (text == null || text.isBlank()) {
            return;
        }
        if (text.length() > MAX_CHAT_LEN) {
            text = text.substring(0, MAX_CHAT_LEN);
        }

        ChatBroadcast out = new ChatBroadcast(req.channel(), from.id(), from.name(), text);
        Packet packetOut = out.toPacket();

        switch (req.channel()) {
            case WORLD, SYSTEM -> ctx.world().broadcastToAll(packetOut);
            case COUNTRY -> {
                if (from.countryId() != 0) {
                    ctx.world().broadcastToCountry(from.countryId(), packetOut);
                } else {
                    session.send(packetOut); // not in a country: echo to self only
                }
            }
            case TEAM -> {
                if (from.teamId() != 0) {
                    ctx.world().broadcastToTeam(from.teamId(), packetOut);
                } else {
                    session.send(packetOut);
                }
            }
            case MAP ->
                ctx.world().broadcastToMap(ctx.world().map(from.mapId()), packetOut, -1);
            case PRIVATE -> {
                // target is the recipient's name; resolve by scanning online players
                ctx.sessions().all().stream()
                        .filter(s -> s.player() != null && s.player().name().equals(req.target()))
                        .findFirst()
                        .ifPresent(s -> {
                            s.send(packetOut);
                            session.send(packetOut); // echo to sender
                        });
            }
        }
        log.debug("[{}] {}: {}", req.channel(), from.name(), text);
    }
}
