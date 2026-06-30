package vn.pvtk.server.world;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import vn.pvtk.protocol.message.Messages.MarketListing;

/** Player-to-player consignment marketplace: sellers list items, buyers purchase. */
public final class MarketRegistry {

    /** A live listing with the data the server needs to settle a purchase. */
    public record Listing(int id, int sellerId, String sellerName,
                          int itemId, String itemName, int count, int price) { }

    private final AtomicInteger ids = new AtomicInteger(1);
    private final ConcurrentHashMap<Integer, Listing> listings = new ConcurrentHashMap<>();

    public Listing add(int sellerId, String sellerName, int itemId, String itemName, int count, int price) {
        int id = ids.getAndIncrement();
        Listing l = new Listing(id, sellerId, sellerName, itemId, itemName, count, price);
        listings.put(id, l);
        return l;
    }

    public Listing remove(int listingId) {
        return listings.remove(listingId);
    }

    public Listing get(int listingId) {
        return listings.get(listingId);
    }

    public List<MarketListing> view() {
        List<MarketListing> out = new ArrayList<>();
        for (Listing l : listings.values()) {
            out.add(new MarketListing(l.id(), l.sellerName(), l.itemId(), l.itemName(), l.count(), l.price()));
        }
        return out;
    }
}
