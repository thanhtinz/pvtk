package vn.pvtk.server.world;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks all active parties. */
public final class TeamRegistry {

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Team> teams = new ConcurrentHashMap<>();

    public Team get(int id) {
        return teams.get(id);
    }

    /** Ensures {@code leader} has a team, creating one if needed, and returns it. */
    public Team ensureTeam(Player leader) {
        if (leader.teamId() != 0) {
            Team existing = teams.get(leader.teamId());
            if (existing != null) {
                return existing;
            }
        }
        int id = ids.getAndIncrement();
        Team t = new Team(id, leader.id());
        teams.put(id, t);
        leader.teamId(id);
        return t;
    }

    public boolean join(Team team, Player player) {
        if (team == null) {
            return false;
        }
        team.add(player.id());
        player.teamId(team.id());
        return true;
    }

    public void leave(Player player) {
        Team t = teams.get(player.teamId());
        if (t != null) {
            t.remove(player.id());
            if (t.size() <= 1) {
                // a party of one is no party — disband.
                teams.remove(t.id());
            }
        }
        player.teamId(0);
    }
}
