package vn.pvtk.server.handler;

import java.util.ArrayList;
import java.util.List;
import vn.pvtk.protocol.Opcodes;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.TeamInvite;
import vn.pvtk.protocol.message.Messages.TeamMember;
import vn.pvtk.protocol.message.Messages.TeamUpdate;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Player;
import vn.pvtk.server.world.Team;
import vn.pvtk.server.world.TeamRegistry;

/**
 * Party management. Invite immediately adds the named online player (a simplified
 * "auto-accept" flow); leave removes the caller. Every change pushes a fresh
 * roster to all members.
 */
public final class TeamHandler implements PacketHandler {

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        int op = packet.command() & 0xFFFF;
        TeamRegistry reg = ctx.world().teams();
        Player p = session.player();

        switch (op) {
            case Opcodes.TEAM_INVITE -> {
                String name = TeamInvite.from(packet).targetName();
                PlayerSession targetSession = findByName(ctx, name);
                if (targetSession == null || targetSession.player() == p) {
                    return;
                }
                Player target = targetSession.player();
                if (target.teamId() != 0) {
                    return; // already in a party
                }
                Team team = reg.ensureTeam(p);
                reg.join(team, target);
                pushRoster(ctx, team);
            }
            case Opcodes.TEAM_LEAVE -> {
                int teamId = p.teamId();
                reg.leave(p);
                session.send(new TeamUpdate(0, List.of()).toPacket()); // you now have no team
                Team team = reg.get(teamId);
                if (team != null) {
                    pushRoster(ctx, team);
                }
            }
            default -> { }
        }
    }

    private void pushRoster(GameContext ctx, Team team) {
        List<TeamMember> members = new ArrayList<>();
        for (int pid : team.members()) {
            PlayerSession s = ctx.sessions().byPlayerId(pid);
            if (s != null && s.player() != null) {
                Player m = s.player();
                members.add(new TeamMember(m.id(), m.name(), m.level(), m.hp(), m.maxHp()));
            }
        }
        Packet update = new TeamUpdate(team.leaderId(), members).toPacket();
        ctx.world().broadcastToTeam(team.id(), update);
    }

    private PlayerSession findByName(GameContext ctx, String name) {
        return ctx.sessions().all().stream()
                .filter(s -> s.player() != null && s.player().name().equalsIgnoreCase(name))
                .findFirst().orElse(null);
    }
}
