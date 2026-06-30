package vn.pvtk.server.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Persistent account store shared by the game server and the website. Accounts are
 * saved as JSON under {@code data/accounts.json}. Passwords are salted SHA-256.
 *
 * <p>This is the single source of truth for credentials, roles, the web wallet
 * balance and persistent player progress (gold/level/exp), so the admin panel can
 * edit a player's economy whether they are online or not.
 */
public final class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);
    private static final SecureRandom RNG = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path file;
    private final Map<String, Account> accounts = new ConcurrentHashMap<>();

    public AccountService(Path dataDir) {
        this.file = dataDir.resolve("accounts.json");
        load();
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }

    @SuppressWarnings("unchecked")
    private synchronized void load() {
        try {
            if (Files.isRegularFile(file)) {
                List<Account> list = JSON.readValue(file.toFile(),
                        JSON.getTypeFactory().constructCollectionType(List.class, Account.class));
                for (Account a : list) {
                    accounts.put(key(a.username), a);
                }
                log.info("Loaded {} accounts from {}", accounts.size(), file.toAbsolutePath());
            }
        } catch (IOException e) {
            log.warn("Failed to load accounts: {}", e.getMessage());
        }
        // Ensure a default admin exists for first-time setup.
        if (accounts.values().stream().noneMatch(Account::isAdmin)) {
            Account admin = register("admin", "admin123");
            if (admin != null) {
                admin.role = "ADMIN";
                save();
                log.info("Created default admin account: admin / admin123 (change this!)");
            }
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), new ArrayList<>(accounts.values()));
        } catch (IOException e) {
            log.warn("Failed to save accounts: {}", e.getMessage());
        }
    }

    private static String hash(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(HexFormat.of().parseHex(saltHex));
            return HexFormat.of().formatHex(md.digest(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Registers a new account via the website (enforces a minimum password length). */
    public Account register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.length() < 4) {
            return null;
        }
        return createInternal(username, password);
    }

    /** Creates an account with no length policy (used by lenient game auto-login). */
    private Account createInternal(String username, String password) {
        String k = key(username);
        if (accounts.containsKey(k)) {
            return null;
        }
        Account a = new Account();
        a.username = username.trim();
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        a.salt = HexFormat.of().formatHex(salt);
        a.passHash = hash(password == null ? "" : password, a.salt);
        a.createdAt = System.currentTimeMillis();
        accounts.put(k, a);
        save();
        return a;
    }

    public Account get(String username) {
        return accounts.get(key(username));
    }

    public boolean authenticate(String username, String password) {
        Account a = get(username);
        return a != null && !a.banned && a.passHash.equals(hash(password, a.salt));
    }

    /** For the game login: validate if the account exists, else auto-create it. */
    public Account authenticateOrCreate(String username, String password) {
        Account a = get(username);
        if (a == null) {
            return createInternal(username, password == null ? "" : password);
        }
        if (a.banned || !a.passHash.equals(hash(password, a.salt))) {
            return null;
        }
        return a;
    }

    public boolean changePassword(String username, String oldPass, String newPass) {
        Account a = get(username);
        if (a == null || newPass == null || newPass.length() < 4 || !authenticate(username, oldPass)) {
            return false;
        }
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        a.salt = HexFormat.of().formatHex(salt);
        a.passHash = hash(newPass, a.salt);
        save();
        return true;
    }

    /** Admin: force a new password without the old one. */
    public boolean resetPassword(String username, String newPass) {
        Account a = get(username);
        if (a == null || newPass == null || newPass.length() < 4) {
            return false;
        }
        byte[] salt = new byte[16];
        RNG.nextBytes(salt);
        a.salt = HexFormat.of().formatHex(salt);
        a.passHash = hash(newPass, a.salt);
        save();
        return true;
    }

    public List<Account> all() {
        return new ArrayList<>(accounts.values());
    }

    public int count() {
        return accounts.size();
    }
}
