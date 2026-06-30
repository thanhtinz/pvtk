package vn.pvtk.server.world;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** A party of players. The first member is the leader. */
public final class Team {

    private final int id;
    private volatile int leaderId;
    private final List<Integer> members = new CopyOnWriteArrayList<>();

    public Team(int id, int leaderId) {
        this.id = id;
        this.leaderId = leaderId;
        members.add(leaderId);
    }

    public int id() {
        return id;
    }

    public int leaderId() {
        return leaderId;
    }

    public List<Integer> members() {
        return members;
    }

    public void add(int playerId) {
        if (!members.contains(playerId)) {
            members.add(playerId);
        }
    }

    public void remove(int playerId) {
        members.remove(Integer.valueOf(playerId));
        if (playerId == leaderId && !members.isEmpty()) {
            leaderId = members.get(0); // promote the next member
        }
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public int size() {
        return members.size();
    }
}
