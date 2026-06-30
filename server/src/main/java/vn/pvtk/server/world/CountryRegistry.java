package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages.CountryInfo;

/** In-memory registry of all guilds/countries. */
public final class CountryRegistry {

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Country> countries = new ConcurrentHashMap<>();

    public Country create(String name, Player founder) {
        int id = ids.getAndIncrement();
        Country c = new Country(id, name, founder.id(), founder.name());
        countries.put(id, c);
        founder.countryId(id);
        return c;
    }

    public Country get(int id) {
        return countries.get(id);
    }

    public boolean nameExists(String name) {
        return countries.values().stream().anyMatch(c -> c.name().equalsIgnoreCase(name));
    }

    public boolean join(int countryId, Player player) {
        Country c = countries.get(countryId);
        if (c == null) {
            return false;
        }
        c.addMember(player.id());
        player.countryId(countryId);
        return true;
    }

    public void leave(Player player) {
        Country c = countries.get(player.countryId());
        if (c != null) {
            c.removeMember(player.id());
            if (c.isEmpty()) {
                countries.remove(c.id());
            }
        }
        player.countryId(0);
    }

    public List<CountryInfo> list() {
        List<CountryInfo> out = new ArrayList<>();
        for (Country c : countries.values()) {
            out.add(c.toInfo());
        }
        return out;
    }
}
