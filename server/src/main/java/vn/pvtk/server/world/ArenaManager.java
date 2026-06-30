package vn.pvtk.server.world;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * A minimal 1-v-1 arena matchmaker. Players queue; when two are waiting they are
 * paired. The actual fight uses the normal PvP combat path in {@link World}; the
 * winner is whoever lands the killing blow.
 */
public final class ArenaManager {

    private final Deque<Integer> queue = new ArrayDeque<>();

    /** Adds a player to the queue and returns the opponent id if a match is made, else 0. */
    public synchronized int enqueue(int playerId) {
        if (queue.contains(playerId)) {
            return 0;
        }
        Integer waiting = queue.poll();
        if (waiting == null) {
            queue.add(playerId);
            return 0;
        }
        if (waiting == playerId) {
            queue.add(playerId);
            return 0;
        }
        return waiting; // matched against the waiting player
    }

    public synchronized void remove(int playerId) {
        queue.remove(Integer.valueOf(playerId));
    }
}
