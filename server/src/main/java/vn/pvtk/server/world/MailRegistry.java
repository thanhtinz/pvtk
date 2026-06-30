package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages.MailEntry;

/**
 * Offline mailbox storage keyed by (lower-cased) recipient name, so mail can be
 * delivered to players who are not currently online.
 */
public final class MailRegistry {

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<String, List<MailEntry>> boxes = new ConcurrentHashMap<>();

    public void send(String fromName, String toName, String subject, String body, int gold) {
        MailEntry mail = new MailEntry(ids.getAndIncrement(), fromName, subject, body, gold, false);
        boxes.computeIfAbsent(key(toName), k -> new CopyOnWriteArrayList<>()).add(mail);
    }

    public List<MailEntry> inbox(String name) {
        return new ArrayList<>(boxes.getOrDefault(key(name), List.of()));
    }

    public int unreadCount(String name) {
        return inbox(name).size();
    }

    private static String key(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT);
    }
}
