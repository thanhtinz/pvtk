package vn.pvtk.server.world;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import vn.pvtk.protocol.message.Messages.CountryInfo;

/** A guild/nation ("country" in the original game). */
public final class Country {

    private final int id;
    private final String name;
    private volatile int kingId;
    private volatile String kingName;
    private volatile int level = 1;
    private final Set<Integer> members = ConcurrentHashMap.newKeySet();

    public Country(int id, String name, int kingId, String kingName) {
        this.id = id;
        this.name = name;
        this.kingId = kingId;
        this.kingName = kingName;
        members.add(kingId);
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public int kingId() {
        return kingId;
    }

    public Set<Integer> members() {
        return members;
    }

    public void addMember(int playerId) {
        members.add(playerId);
    }

    public void removeMember(int playerId) {
        members.remove(playerId);
    }

    public boolean isEmpty() {
        return members.isEmpty();
    }

    public CountryInfo toInfo() {
        return new CountryInfo(id, name, kingName, members.size(), level);
    }
}
