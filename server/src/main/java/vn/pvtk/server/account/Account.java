package vn.pvtk.server.account;

/**
 * A persistent player account, shared by the game login and the website. Holds
 * credentials plus the persistent player progress (gold/level/exp) and the web
 * wallet balance (top-ups), so admin economy edits work even while a player is
 * offline.
 */
public final class Account {

    public String username = "";
    public String passHash = "";   // salted SHA-256, hex
    public String salt = "";       // hex
    public String role = "USER";   // USER | ADMIN
    public boolean banned = false;

    public long balance = 0;       // web wallet "Xu" (from SePay top-ups), spends on the webshop
    public long coin = 0;          // in-game cash "Tiền nạp" (exchanged from Xu inside the game)
    public long gold = 100;        // persistent in-game gold
    public int level = 1;
    public int exp = 0;
    public long createdAt = 0L;
    public long lastLogin = 0L;

    public Account() {
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
