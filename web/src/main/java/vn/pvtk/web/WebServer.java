package vn.pvtk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.server.GameServer;
import vn.pvtk.server.account.Account;
import vn.pvtk.server.data.ItemDef;
import vn.pvtk.server.data.MonsterDef;

/**
 * Lightweight HTTP server (JDK {@code com.sun.net.httpserver}, no extra deps) that
 * powers the website and admin panel. It shares the live {@link GameServer}'s world,
 * accounts and content DB, so admin actions (send-item mail, reset boss, economy
 * edits) take effect in-game immediately. Static frontend files are served from the
 * classpath {@code /web/} resources.
 */
public final class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final SecureRandom RNG = new SecureRandom();

    private final GameServer game;
    private final WebData web;
    private final int port;
    private final Map<String, String> tokens = new ConcurrentHashMap<>(); // token -> username
    private HttpServer http;

    public WebServer(GameServer game, WebData web, int port) {
        this.game = game;
        this.web = web;
        this.port = port;
    }

    public void start() throws IOException {
        http = HttpServer.create(new InetSocketAddress(port), 0);
        http.setExecutor(Executors.newFixedThreadPool(8));
        http.createContext("/", this::route);
        http.start();
        log.info("PVTK website listening on http://localhost:{}  (admin: admin/admin123)", port);
    }

    public void stop() {
        if (http != null) {
            http.stop(0);
        }
    }

    // ------------------------------------------------------------------
    // Routing
    // ------------------------------------------------------------------

    private void route(HttpExchange ex) {
        try {
            String path = ex.getRequestURI().getPath();
            if (!path.startsWith("/api/")) {
                serveStatic(ex, path);
                return;
            }
            String m = ex.getRequestMethod();
            String p = path.substring(4); // strip "/api"
            try {
                handleApi(ex, m, p);
            } catch (HttpError e) {
                sendJson(ex, e.status, Map.of("error", e.getMessage()));
            }
        } catch (Exception e) {
            try {
                sendJson(ex, 500, Map.of("error", String.valueOf(e.getMessage())));
            } catch (IOException ignored) {
                // client gone
            }
        } finally {
            ex.close();
        }
    }

    private void handleApi(HttpExchange ex, String m, String p) throws IOException {
        // ---- auth ----
        if (p.equals("/auth/register") && m.equals("POST")) {
            Map<String, Object> b = body(ex);
            Account a = game.accounts().register(str(b, "username"), str(b, "password"));
            if (a == null) {
                throw new HttpError(400, "Tên đăng nhập đã tồn tại hoặc mật khẩu quá ngắn (>=4)");
            }
            sendJson(ex, 200, tokenResponse(a));
            return;
        }
        if (p.equals("/auth/login") && m.equals("POST")) {
            Map<String, Object> b = body(ex);
            String u = str(b, "username");
            if (!game.accounts().authenticate(u, str(b, "password"))) {
                throw new HttpError(401, "Sai tài khoản hoặc mật khẩu");
            }
            sendJson(ex, 200, tokenResponse(game.accounts().get(u)));
            return;
        }
        if (p.equals("/news") && m.equals("GET")) {
            sendJson(ex, 200, Map.of("news", web.root().news));
            return;
        }
        if (p.equals("/leaderboard") && m.equals("GET")) {
            sendJson(ex, 200, Map.of("top", leaderboard()));
            return;
        }
        if (p.equals("/shop") && m.equals("GET")) {
            sendJson(ex, 200, Map.of("products", shopView()));
            return;
        }
        if (p.equals("/items") && m.equals("GET")) {
            requireUser(ex);
            sendJson(ex, 200, Map.of("items", searchItems(query(ex, "q"), 60)));
            return;
        }
        if (p.startsWith("/items/") && p.endsWith("/icon.svg") && m.equals("GET")) {
            int iconId = Integer.parseInt(p.substring(7, p.length() - 9));
            sendSvgIcon(ex, iconId);
            return;
        }

        // ---- authenticated user ----
        if (p.equals("/me") && m.equals("GET")) {
            sendJson(ex, 200, accountView(requireUser(ex)));
            return;
        }
        if (p.equals("/me/transactions") && m.equals("GET")) {
            Account a = requireUser(ex);
            sendJson(ex, 200, Map.of("transactions", web.transactionsFor(a.username, 100)));
            return;
        }
        if (p.equals("/auth/change-password") && m.equals("POST")) {
            Account a = requireUser(ex);
            Map<String, Object> b = body(ex);
            if (!game.accounts().changePassword(a.username, str(b, "oldPassword"), str(b, "newPassword"))) {
                throw new HttpError(400, "Đổi mật khẩu thất bại (mật khẩu cũ sai hoặc mật khẩu mới quá ngắn)");
            }
            sendJson(ex, 200, Map.of("ok", true));
            return;
        }
        if (p.equals("/topup") && m.equals("POST")) {
            Account a = requireUser(ex);
            long amount = num(body(ex), "amount");
            if (amount <= 0) {
                throw new HttpError(400, "Số tiền không hợp lệ");
            }
            a.balance += amount;
            game.accounts().save();
            web.addTx(a.username, "topup", "Nạp " + amount + " 💎", amount, "balance", System.currentTimeMillis());
            sendJson(ex, 200, Map.of("balance", a.balance));
            return;
        }
        if (p.equals("/giftcode") && m.equals("POST")) {
            Account a = requireUser(ex);
            sendJson(ex, 200, redeemGiftcode(a, str(body(ex), "code")));
            return;
        }
        if (p.equals("/shop/buy") && m.equals("POST")) {
            Account a = requireUser(ex);
            sendJson(ex, 200, buyProduct(a, (int) num(body(ex), "productId")));
            return;
        }

        // ---- admin ----
        if (p.startsWith("/admin/")) {
            requireAdmin(ex);
            handleAdmin(ex, m, p.substring(6)); // strip "/admin"
            return;
        }

        throw new HttpError(404, "Not found");
    }

    private void handleAdmin(HttpExchange ex, String m, String p) throws IOException {
        switch (p) {
            case "/stats" -> {
                if (!m.equals("GET")) throw new HttpError(405, "Method");
                sendJson(ex, 200, adminStats());
            }
            case "/users" -> {
                if (!m.equals("GET")) throw new HttpError(405, "Method");
                sendJson(ex, 200, Map.of("users", usersView(query(ex, "q"))));
            }
            case "/users/economy" -> {
                Map<String, Object> b = body(ex);
                sendJson(ex, 200, adjustEconomy(str(b, "username"),
                        (int) num(b, "deltaGold"), num(b, "deltaBalance")));
            }
            case "/users/role" -> {
                Map<String, Object> b = body(ex);
                Account a = game.accounts().get(str(b, "username"));
                if (a == null) throw new HttpError(404, "User");
                a.role = "ADMIN".equalsIgnoreCase(str(b, "role")) ? "ADMIN" : "USER";
                game.accounts().save();
                sendJson(ex, 200, Map.of("ok", true, "role", a.role));
            }
            case "/users/ban" -> {
                Map<String, Object> b = body(ex);
                Account a = game.accounts().get(str(b, "username"));
                if (a == null) throw new HttpError(404, "User");
                a.banned = Boolean.parseBoolean(String.valueOf(b.get("banned")));
                game.accounts().save();
                sendJson(ex, 200, Map.of("ok", true, "banned", a.banned));
            }
            case "/users/reset-password" -> {
                Map<String, Object> b = body(ex);
                boolean ok = game.accounts().resetPassword(str(b, "username"), str(b, "newPassword"));
                sendJson(ex, 200, Map.of("ok", ok));
            }
            case "/mail" -> sendJson(ex, 200, adminSendMail(body(ex)));
            case "/reset-boss" -> sendJson(ex, 200, Map.of("respawned", game.world().resetMonsters()));
            case "/transactions" -> sendJson(ex, 200, Map.of("transactions", web.recentTransactions(200)));
            case "/online" -> sendJson(ex, 200, Map.of("players", onlineView()));
            case "/announce" -> {
                String msg = str(body(ex), "message");
                if (!msg.isBlank()) {
                    game.world().announce(msg);
                }
                sendJson(ex, 200, Map.of("ok", true, "online", game.sessions().onlineCount()));
            }
            case "/market" -> sendJson(ex, 200, Map.of("listings", game.world().market().view()));
            case "/market/remove" -> {
                boolean ok = game.world().removeMarketListing((int) num(body(ex), "listingId"));
                sendJson(ex, 200, Map.of("ok", ok));
            }
            case "/maps" -> sendJson(ex, 200, Map.of("maps", mapsView()));
            case "/monsters" -> sendJson(ex, 200, Map.of("monsters", monstersView()));
            case "/economy" -> sendJson(ex, 200, economyView());
            case "/news" -> sendJson(ex, 200, saveNews(body(ex)));
            case "/giftcodes" -> {
                if (m.equals("GET")) sendJson(ex, 200, Map.of("giftcodes", web.root().giftcodes));
                else sendJson(ex, 200, saveGiftcode(body(ex)));
            }
            case "/products" -> sendJson(ex, 200, saveProduct(body(ex)));
            default -> {
                if (p.startsWith("/news/") && m.equals("DELETE")) {
                    int id = Integer.parseInt(p.substring(6));
                    web.root().news.removeIf(n -> n.id == id);
                    web.save();
                    sendJson(ex, 200, Map.of("ok", true));
                } else if (p.startsWith("/giftcodes/") && m.equals("DELETE")) {
                    String code = p.substring(11);
                    web.root().giftcodes.removeIf(g -> g.code.equalsIgnoreCase(code));
                    web.save();
                    sendJson(ex, 200, Map.of("ok", true));
                } else if (p.startsWith("/products/") && m.equals("DELETE")) {
                    int id = Integer.parseInt(p.substring(10));
                    web.root().products.removeIf(pr -> pr.id == id);
                    web.save();
                    sendJson(ex, 200, Map.of("ok", true));
                } else {
                    throw new HttpError(404, "Admin route");
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // User operations
    // ------------------------------------------------------------------

    private Map<String, Object> redeemGiftcode(Account a, String code) {
        for (WebData.Giftcode g : web.root().giftcodes) {
            if (g.code.equalsIgnoreCase(code) && g.enabled) {
                if (g.maxUses > 0 && g.used >= g.maxUses) {
                    throw new HttpError(400, "Giftcode đã hết lượt");
                }
                if (g.redeemedBy.stream().anyMatch(u -> u.equalsIgnoreCase(a.username))) {
                    throw new HttpError(400, "Bạn đã nhận giftcode này");
                }
                g.used++;
                g.redeemedBy.add(a.username);
                if (g.rewardBalance > 0) {
                    a.balance += g.rewardBalance;
                }
                if (g.rewardGold > 0) {
                    grantGold(a, (int) g.rewardGold);
                }
                List<int[]> items = new ArrayList<>();
                for (WebData.ItemQty iq : g.items) {
                    items.add(new int[]{iq.itemId, iq.count});
                }
                if (!items.isEmpty()) {
                    game.world().sendMail("Giftcode", a.username, "Quà Giftcode",
                            "Phần thưởng từ giftcode " + g.code, 0, items);
                }
                game.accounts().save();
                web.save();
                web.addTx(a.username, "giftcode", "Nhận giftcode " + g.code,
                        g.rewardGold + g.rewardBalance, "reward", System.currentTimeMillis());
                return Map.of("ok", true, "message", "Nhận quà thành công!");
            }
        }
        throw new HttpError(404, "Giftcode không hợp lệ");
    }

    private Map<String, Object> buyProduct(Account a, int productId) {
        for (WebData.Product pr : web.root().products) {
            if (pr.id == productId && pr.enabled) {
                if (a.balance < pr.price) {
                    throw new HttpError(400, "Số dư không đủ (cần nạp thẻ)");
                }
                a.balance -= pr.price;
                game.world().sendMail("Webshop", a.username, "Vật phẩm Webshop",
                        "Cảm ơn bạn đã mua " + pr.name, 0,
                        List.of(new int[]{pr.itemId, pr.count}));
                game.accounts().save();
                web.addTx(a.username, "buy", "Mua " + pr.name, -pr.price, "balance", System.currentTimeMillis());
                return Map.of("ok", true, "balance", a.balance, "message", "Mua thành công, kiểm tra hòm thư trong game!");
            }
        }
        throw new HttpError(404, "Sản phẩm không tồn tại");
    }

    /** Adds gold to a live player if online, else to the persisted account. */
    private void grantGold(Account a, int amount) {
        if (!game.world().addGoldOnline(a.username, amount)) {
            a.gold += amount;
        }
    }

    // ------------------------------------------------------------------
    // Admin operations
    // ------------------------------------------------------------------

    private Map<String, Object> adjustEconomy(String username, int deltaGold, long deltaBalance) {
        Account a = game.accounts().get(username);
        if (a == null) {
            throw new HttpError(404, "User");
        }
        if (deltaBalance != 0) {
            a.balance = Math.max(0, a.balance + deltaBalance);
        }
        if (deltaGold != 0) {
            grantGold(a, deltaGold);
        }
        game.accounts().save();
        web.addTx(username, "admin_economy",
                "Admin chỉnh: " + (deltaGold != 0 ? deltaGold + " vàng " : "") + (deltaBalance != 0 ? deltaBalance + " 💎" : ""),
                deltaGold + deltaBalance, "mixed", System.currentTimeMillis());
        return Map.of("ok", true, "gold", a.gold, "balance", a.balance);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> adminSendMail(Map<String, Object> b) {
        String to = str(b, "toUser");
        if (game.accounts().get(to) == null && game.world().findByName(to) == null) {
            throw new HttpError(404, "Người nhận không tồn tại");
        }
        List<int[]> items = new ArrayList<>();
        Object raw = b.get("items");
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> im) {
                    int id = (int) toLong(im.get("itemId"));
                    int c = (int) Math.max(1, toLong(im.get("count")));
                    if (id > 0) {
                        items.add(new int[]{id, c});
                    }
                }
            }
        }
        int gold = (int) num(b, "gold");
        game.world().sendMail("Admin", to, str(b, "subject"), str(b, "body"), gold, items);
        web.addTx(to, "admin_mail", "Admin gửi thư (" + items.size() + " vật phẩm)", gold, "item",
                System.currentTimeMillis());
        return Map.of("ok", true, "items", items.size(), "gold", gold);
    }

    private Map<String, Object> saveNews(Map<String, Object> b) {
        WebData.News n = new WebData.News();
        Object idv = b.get("id");
        if (idv != null && toLong(idv) > 0) {
            n = web.root().news.stream().filter(x -> x.id == toLong(idv)).findFirst().orElse(n);
        }
        if (n.id == 0) {
            n.id = web.root().newsSeq++;
            web.root().news.add(0, n);
        }
        n.title = str(b, "title");
        n.body = str(b, "body");
        n.type = "event".equals(str(b, "type")) ? "event" : "news";
        n.date = System.currentTimeMillis();
        n.startAt = num(b, "startAt");
        n.endAt = num(b, "endAt");
        web.save();
        return Map.of("ok", true, "id", n.id);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> saveGiftcode(Map<String, Object> b) {
        String code = str(b, "code");
        if (code.isBlank()) {
            throw new HttpError(400, "Mã trống");
        }
        WebData.Giftcode g = web.root().giftcodes.stream()
                .filter(x -> x.code.equalsIgnoreCase(code)).findFirst().orElse(null);
        if (g == null) {
            g = new WebData.Giftcode();
            g.code = code;
            web.root().giftcodes.add(g);
        }
        g.rewardGold = num(b, "rewardGold");
        g.rewardBalance = num(b, "rewardBalance");
        g.maxUses = (int) num(b, "maxUses");
        g.enabled = b.get("enabled") == null || Boolean.parseBoolean(String.valueOf(b.get("enabled")));
        g.items = new ArrayList<>();
        if (b.get("items") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> im) {
                    g.items.add(new WebData.ItemQty((int) toLong(im.get("itemId")),
                            (int) Math.max(1, toLong(im.get("count")))));
                }
            }
        }
        web.save();
        return Map.of("ok", true, "code", g.code);
    }

    private Map<String, Object> saveProduct(Map<String, Object> b) {
        WebData.Product pr = new WebData.Product();
        Object idv = b.get("id");
        if (idv != null && toLong(idv) > 0) {
            pr = web.root().products.stream().filter(x -> x.id == toLong(idv)).findFirst().orElse(pr);
        }
        if (pr.id == 0) {
            pr.id = web.root().productSeq++;
            web.root().products.add(pr);
        }
        pr.name = str(b, "name");
        pr.itemId = (int) num(b, "itemId");
        pr.count = (int) Math.max(1, num(b, "count"));
        pr.price = num(b, "price");
        pr.enabled = b.get("enabled") == null || Boolean.parseBoolean(String.valueOf(b.get("enabled")));
        web.save();
        return Map.of("ok", true, "id", pr.id);
    }

    // ------------------------------------------------------------------
    // Views
    // ------------------------------------------------------------------

    private Map<String, Object> adminStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("online", game.sessions().onlineCount());
        m.put("accounts", game.accounts().count());
        m.put("items", game.gameData().items().size());
        m.put("monsters", game.gameData().monsterList().size());
        m.put("giftcodes", web.root().giftcodes.size());
        m.put("products", web.root().products.size());
        return m;
    }

    private List<Map<String, Object>> usersView(String q) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Account a : game.accounts().all()) {
            if (q != null && !q.isBlank() && !a.username.toLowerCase().contains(q.toLowerCase())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("username", a.username);
            m.put("role", a.role);
            m.put("banned", a.banned);
            m.put("balance", a.balance);
            m.put("gold", a.gold);
            m.put("level", a.level);
            m.put("online", game.world().findByName(a.username) != null);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> leaderboard() {
        List<Account> all = new ArrayList<>(game.accounts().all());
        all.sort((x, y) -> y.level != x.level ? Integer.compare(y.level, x.level)
                : Long.compare(y.gold, x.gold));
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < Math.min(50, all.size()); i++) {
            Account a = all.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", i + 1);
            m.put("username", a.username);
            m.put("level", a.level);
            m.put("gold", a.gold);
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> searchItems(String q, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ItemDef d : game.gameData().items().values()) {
            if (q != null && !q.isBlank()) {
                String ql = q.toLowerCase();
                if (!d.name().toLowerCase().contains(ql) && !String.valueOf(d.id()).contains(ql)) {
                    continue;
                }
            }
            out.add(itemView(d));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private Map<String, Object> itemView(ItemDef d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.id());
        m.put("name", d.name());
        m.put("type", d.type());
        m.put("price", d.price());
        m.put("icon", d.icon());
        m.put("info", d.info());
        return m;
    }

    private List<Map<String, Object>> shopView() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (WebData.Product pr : web.root().products) {
            if (!pr.enabled) {
                continue;
            }
            ItemDef d = game.gameData().item(pr.itemId);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", pr.id);
            m.put("name", pr.name);
            m.put("itemId", pr.itemId);
            m.put("count", pr.count);
            m.put("price", pr.price);
            m.put("icon", d != null ? d.icon() : 0);
            m.put("itemName", d != null ? d.name() : "?");
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> mapsView() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int id : new int[]{1, 2, 3, 4}) {
            var map = game.world().map(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", map.mapId());
            m.put("name", map.name());
            m.put("width", map.width());
            m.put("height", map.height());
            m.put("players", map.playerIds().size());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> monstersView() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (MonsterDef d : game.gameData().monsterList()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id());
            m.put("name", d.name());
            m.put("level", d.level());
            m.put("hpMax", d.hpMax());
            m.put("rewardExp", d.rewardExp());
            m.put("rewardGold", d.rewardMoney());
            out.add(m);
        }
        return out;
    }

    private List<Map<String, Object>> onlineView() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var s : game.sessions().all()) {
            if (s.player() == null) {
                continue;
            }
            var pl = s.player();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", pl.name());
            m.put("map", game.world().map(pl.mapId()).name());
            m.put("mapId", pl.mapId());
            m.put("level", pl.level());
            m.put("gold", pl.gold());
            m.put("x", pl.x());
            m.put("y", pl.y());
            out.add(m);
        }
        return out;
    }

    private Map<String, Object> economyView() {
        long gold = 0;
        long balance = 0;
        for (Account a : game.accounts().all()) {
            gold += a.gold;
            balance += a.balance;
        }
        return Map.of("totalGold", gold, "totalBalance", balance, "accounts", game.accounts().count());
    }

    private Map<String, Object> accountView(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("username", a.username);
        m.put("role", a.role);
        m.put("balance", a.balance);
        m.put("gold", a.gold);
        m.put("level", a.level);
        m.put("online", game.world().findByName(a.username) != null);
        return m;
    }

    private Map<String, Object> tokenResponse(Account a) {
        String token = HexFormat.of().formatHex(randomBytes());
        tokens.put(token, a.username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", token);
        m.put("username", a.username);
        m.put("role", a.role);
        return m;
    }

    // ------------------------------------------------------------------
    // Item icon (generated SVG so every item shows a distinct icon)
    // ------------------------------------------------------------------

    private void sendSvgIcon(HttpExchange ex, int iconId) throws IOException {
        int hue = Math.floorMod(iconId * 47, 360);
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='40' height='40'>"
                + "<rect width='40' height='40' rx='6' fill='hsl(" + hue + ",60%,45%)'/>"
                + "<rect x='3' y='3' width='34' height='34' rx='5' fill='none' stroke='white' stroke-opacity='0.5'/>"
                + "<text x='20' y='25' font-size='11' fill='white' text-anchor='middle'"
                + " font-family='monospace'>" + iconId + "</text></svg>";
        byte[] bytes = svg.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "image/svg+xml");
        ex.getResponseHeaders().set("Cache-Control", "max-age=86400");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    // ------------------------------------------------------------------
    // Static files
    // ------------------------------------------------------------------

    private void serveStatic(HttpExchange ex, String path) throws IOException {
        String rel = path.equals("/") ? "/index.html" : path;
        if (rel.contains("..")) {
            sendText(ex, 400, "bad path");
            return;
        }
        try (InputStream in = WebServer.class.getResourceAsStream("/web" + rel)) {
            if (in == null) {
                // SPA-ish fallback: unknown non-file path -> index
                if (!rel.contains(".")) {
                    serveStatic(ex, "/index.html");
                } else {
                    sendText(ex, 404, "Not found");
                }
                return;
            }
            byte[] bytes = in.readAllBytes();
            ex.getResponseHeaders().set("Content-Type", contentType(rel));
            ex.sendResponseHeaders(200, bytes.length);
            ex.getResponseBody().write(bytes);
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) return "text/html; charset=utf-8";
        if (path.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------
    // Auth helpers
    // ------------------------------------------------------------------

    private Account requireUser(HttpExchange ex) {
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        String token = auth != null && auth.startsWith("Bearer ") ? auth.substring(7) : null;
        String username = token != null ? tokens.get(token) : null;
        Account a = username != null ? game.accounts().get(username) : null;
        if (a == null) {
            throw new HttpError(401, "Cần đăng nhập");
        }
        return a;
    }

    private Account requireAdmin(HttpExchange ex) {
        Account a = requireUser(ex);
        if (!a.isAdmin()) {
            throw new HttpError(403, "Cần quyền admin");
        }
        return a;
    }

    // ------------------------------------------------------------------
    // HTTP / JSON helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private Map<String, Object> body(HttpExchange ex) throws IOException {
        byte[] data = ex.getRequestBody().readAllBytes();
        if (data.length == 0) {
            return new LinkedHashMap<>();
        }
        return JSON.readValue(data, Map.class);
    }

    private String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return null;
        }
        for (String part : q.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(key)) {
                return java.net.URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(obj);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private void sendText(HttpExchange ex, int status, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
    }

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : String.valueOf(v);
    }

    private static long num(Map<String, Object> m, String k) {
        return toLong(m.get(k));
    }

    private static long toLong(Object v) {
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return v == null ? 0 : Long.parseLong(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static byte[] randomBytes() {
        byte[] b = new byte[24];
        RNG.nextBytes(b);
        return b;
    }

    /** Internal control-flow exception carrying an HTTP status. */
    private static final class HttpError extends RuntimeException {
        final int status;
        HttpError(int status, String msg) {
            super(msg);
            this.status = status;
        }
    }
}
