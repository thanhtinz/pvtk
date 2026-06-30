package vn.pvtk.server.world;

import java.util.concurrent.atomic.AtomicReference;
import vn.pvtk.protocol.message.Messages.WarStatus;

/**
 * A single active country war. One war runs at a time in this rewrite: a king
 * declares war on another country, PvP kills between the two sides score points,
 * and the first to {@link #WIN_SCORE} wins.
 */
public final class WarManager {

    public static final int WIN_SCORE = 10;

    /** Immutable war state, swapped atomically. */
    public record War(int attackerCountryId, String attackerName,
                      int defenderCountryId, String defenderName,
                      int attackerScore, int defenderScore, boolean finished, String message) {

        War score(boolean attackerScored) {
            int a = attackerScore + (attackerScored ? 1 : 0);
            int d = defenderScore + (attackerScored ? 0 : 1);
            boolean done = a >= WIN_SCORE || d >= WIN_SCORE;
            String msg = done ? ((a >= d ? attackerName : defenderName) + " chiến thắng!") : "";
            return new War(attackerCountryId, attackerName, defenderCountryId, defenderName, a, d, done, msg);
        }

        WarStatus toStatus() {
            return new WarStatus(!finished, attackerName, defenderName, attackerScore, defenderScore, message);
        }
    }

    private final AtomicReference<War> current = new AtomicReference<>();

    public boolean isActive() {
        War w = current.get();
        return w != null && !w.finished();
    }

    public War current() {
        return current.get();
    }

    public boolean declare(Country attacker, Country defender) {
        if (isActive() || attacker == null || defender == null || attacker.id() == defender.id()) {
            return false;
        }
        current.set(new War(attacker.id(), attacker.name(), defender.id(), defender.name(),
                0, 0, false, ""));
        return true;
    }

    /** Records a kill by {@code killerCountryId} if it is a participant. Returns the new state, or null. */
    public War recordKill(int killerCountryId) {
        War w = current.get();
        if (w == null || w.finished()) {
            return null;
        }
        if (killerCountryId == w.attackerCountryId()) {
            w = w.score(true);
        } else if (killerCountryId == w.defenderCountryId()) {
            w = w.score(false);
        } else {
            return null;
        }
        current.set(w);
        return w;
    }

    public WarStatus status() {
        War w = current.get();
        if (w == null) {
            return new WarStatus(false, "", "", 0, 0, "Không có chiến tranh");
        }
        return w.toStatus();
    }
}
