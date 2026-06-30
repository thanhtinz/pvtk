package vn.pvtk.server.handler;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.server.session.PlayerSession;

/**
 * Routes inbound packets to their registered {@link PacketHandler} by opcode.
 * Unknown opcodes are logged once and dropped, which keeps the server resilient
 * to the many original opcodes that this rewrite does not yet implement.
 */
public final class PacketDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PacketDispatcher.class);

    private final Map<Integer, PacketHandler> handlers = new HashMap<>();
    private final GameContext ctx;

    public PacketDispatcher(GameContext ctx) {
        this.ctx = ctx;
        register(Opcodes.LOGIN, new LoginHandler());
        register(Opcodes.AUTO_MOVE, new MovementHandler());
        register(Opcodes.CHAT, new ChatHandler());
        register(Opcodes.WORLD_DATA, new WorldDataHandler());
        register(Opcodes.JUMP_MAP, new JumpMapHandler());
        register(Opcodes.BAG, new InventoryHandler());
        register(Opcodes.ATTACK, new CombatHandler());

        CountryHandler country = new CountryHandler();
        register(Opcodes.COUNTRY_CREATE, country);
        register(Opcodes.COUNTRY_LIST, country);
        register(Opcodes.COUNTRY_INFO, country);
        register(Opcodes.COUNTRY_JOIN, country);
        register(Opcodes.COUNTRY_LEAVE, country);

        ShopHandler shop = new ShopHandler();
        register(Opcodes.SHOP_LIST, shop);
        register(Opcodes.SHOP_BUY, shop);
        register(Opcodes.SHOP_SELL, shop);
        register(Opcodes.SKILL_LIST, new SkillHandler());

        TeamHandler team = new TeamHandler();
        register(Opcodes.TEAM_INVITE, team);
        register(Opcodes.TEAM_LEAVE, team);

        MailHandler mail = new MailHandler();
        register(Opcodes.MAIL_SEND, mail);
        register(Opcodes.MAIL_LIST, mail);
    }

    public void register(int opcode, PacketHandler handler) {
        handlers.put(opcode, handler);
    }

    public vn.pvtk.server.world.World world() {
        return ctx.world();
    }

    public void dispatch(PlayerSession session, Packet packet) {
        session.touch();
        int op = packet.command() & 0xFFFF;
        PacketHandler handler = handlers.get(op);
        if (handler == null) {
            log.debug("No handler for opcode {} ({}) from {}",
                    op, Opcodes.name(op), session.remoteAddress());
            return;
        }
        if (!handler.allowsUnauthenticated() && !session.isAuthenticated()) {
            log.warn("Dropping {} from unauthenticated session {}", Opcodes.name(op), session.sessionId());
            return;
        }
        try {
            handler.handle(session, packet, ctx);
        } catch (Exception e) {
            log.error("Handler error for opcode {} ({})", op, Opcodes.name(op), e);
        }
    }
}
