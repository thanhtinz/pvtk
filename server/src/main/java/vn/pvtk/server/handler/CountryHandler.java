package vn.pvtk.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.CountryActionResult;
import vn.pvtk.protocol.message.Messages.CountryCreate;
import vn.pvtk.protocol.message.Messages.CountryJoin;
import vn.pvtk.protocol.message.Messages.CountryList;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Country;
import vn.pvtk.server.world.CountryRegistry;
import vn.pvtk.server.world.Player;

/**
 * Guild/country management. One handler instance is registered for each of the
 * create/list/info/join/leave opcodes and routes on the opcode it received.
 */
public final class CountryHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(CountryHandler.class);

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        CountryRegistry reg = ctx.world().countries();
        Player p = session.player();

        switch (op) {
            case Opcodes.COUNTRY_CREATE -> {
                String name = CountryCreate.from(packet).name();
                if (name == null || name.isBlank()) {
                    reply(session, Opcodes.COUNTRY_CREATE, false, "Tên bang trống", null);
                } else if (p.countryId() != 0) {
                    reply(session, Opcodes.COUNTRY_CREATE, false, "Bạn đã ở trong một bang", null);
                } else if (reg.nameExists(name)) {
                    reply(session, Opcodes.COUNTRY_CREATE, false, "Tên bang đã tồn tại", null);
                } else {
                    Country c = reg.create(name.trim(), p);
                    reply(session, Opcodes.COUNTRY_CREATE, true, "Lập bang thành công", c);
                    log.info("{} founded country '{}' (#{})", p.name(), c.name(), c.id());
                }
            }
            case Opcodes.COUNTRY_LIST ->
                session.send(new CountryList(reg.list()).toPacket());
            case Opcodes.COUNTRY_INFO -> {
                Country c = reg.get(p.countryId());
                reply(session, Opcodes.COUNTRY_INFO, c != null,
                        c != null ? "" : "Bạn chưa có bang", c);
            }
            case Opcodes.COUNTRY_JOIN -> {
                int id = CountryJoin.from(packet).countryId();
                if (p.countryId() != 0) {
                    reply(session, Opcodes.COUNTRY_JOIN, false, "Bạn đã ở trong một bang", null);
                } else if (reg.join(id, p)) {
                    Country c = reg.get(id);
                    reply(session, Opcodes.COUNTRY_JOIN, true, "Gia nhập thành công", c);
                    log.info("{} joined country #{}", p.name(), id);
                } else {
                    reply(session, Opcodes.COUNTRY_JOIN, false, "Bang không tồn tại", null);
                }
            }
            case Opcodes.COUNTRY_LEAVE -> {
                reg.leave(p);
                reply(session, Opcodes.COUNTRY_LEAVE, true, "Đã rời bang", null);
            }
            default -> { /* not a country opcode */ }
        }
    }

    private void reply(PlayerSession s, int opcode, boolean ok, String msg, Country c) {
        s.send(new CountryActionResult(ok, msg, c != null ? c.toInfo() : null).toPacket(opcode));
    }
}
