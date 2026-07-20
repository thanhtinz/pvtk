package vn.pvtk.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Website content persisted to {@code data/web.json}: news/events, giftcodes, webshop. */
public final class WebData {

    private static final ObjectMapper JSON = new ObjectMapper();

    public static final class ItemQty {
        public int itemId;
        public int count = 1;
        public ItemQty() { }
        public ItemQty(int itemId, int count) { this.itemId = itemId; this.count = count; }
    }

    public static final class News {
        public int id;
        public String title = "";
        public String body = "";
        public String type = "news"; // news | event
        public long date;
        public long startAt;          // event start (0 = none)
        public long endAt;            // event end / countdown target (0 = none)
    }

    /** An economy/audit record shown in player & admin history. */
    public static final class Tx {
        public long id;
        public long time;
        public String user = "";
        public String type = "";      // topup | buy | giftcode | admin_economy | admin_mail | announce
        public String detail = "";
        public long amount;           // signed
        public String currency = "";  // balance | gold | item
        public Tx() { }
        public Tx(long id, String user, String type, String detail, long amount, String currency) {
            this.id = id; this.time = 0; this.user = user; this.type = type;
            this.detail = detail; this.amount = amount; this.currency = currency;
        }
    }

    public static final class Giftcode {
        public String code = "";
        public long rewardGold;
        public long rewardBalance;
        public List<ItemQty> items = new ArrayList<>();
        public int maxUses = 1;          // total redemptions allowed (0 = unlimited)
        public int used;
        public List<String> redeemedBy = new ArrayList<>();
        public boolean enabled = true;
    }

    public static final class Product {
        public int id;
        public String name = "";
        public int itemId;
        public int count = 1;
        public long price;               // in web wallet balance
        public boolean enabled = true;
    }

    /** SePay (bank-transfer gateway) configuration, edited in the admin panel. */
    public static final class SePayConfig {
        public boolean enabled = false;
        public String bankCode = "MBBank";       // SePay bank code (e.g. MBBank, VPBank, ...)
        public String accountNumber = "";
        public String accountHolder = "";
        public String apiKey = "";               // shared secret for the webhook (Authorization: Apikey ...)
        public String prefix = "PVTK";           // transfer-content prefix; the order code follows it
        public int xuPerVnd = 0;                 // 0 => use package xu; else generic rate (xu per 1 VND)
    }

    /** A top-up package configured in the admin panel. */
    public static final class Package {
        public int id;
        public String name = "";
        public long priceVnd;                    // amount the player transfers
        public long xu;                          // Xu credited to the web wallet
        public long bonus;                       // bonus Xu
        public boolean enabled = true;
    }

    /** A pending/paid top-up order. */
    public static final class Order {
        public long id;
        public String code = "";                 // unique code embedded in the transfer content
        public String user = "";
        public int packageId;
        public long amountVnd;
        public long xu;
        public String status = "pending";        // pending | paid | expired
        public long createdAt;
        public long paidAt;
        public String ref = "";                  // SePay reference / bank txn id
    }

    /** Public site settings (contact info + download links), edited in the admin panel. */
    public static final class SiteConfig {
        public String supportEmail = "hotro@phongvan.vn";
        public String hotline = "1900 0000";
        public String facebookUrl = "";       // Fanpage
        public String groupUrl = "";           // Zalo/Facebook group
        public String guideUrl = "";           // Hướng dẫn (may be a full URL; empty => internal news page)
        public String downloadPc = "";         // PC client download link
        public String downloadAndroid = "";    // Android APK link
        public String downloadIos = "";        // iOS link
        public String downloadJava = "";       // Java client link
    }

    public static final class Root {
        public List<News> news = new ArrayList<>();
        public List<Giftcode> giftcodes = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
        public List<Tx> transactions = new ArrayList<>();
        public List<Package> packages = new ArrayList<>();
        public List<Order> orders = new ArrayList<>();
        public SePayConfig sepay = new SePayConfig();
        public SiteConfig site = new SiteConfig();
        public int newsSeq = 1;
        public int productSeq = 1;
        public int packageSeq = 1;
        public long txSeq = 1;
        public long orderSeq = 1;
    }

    private final Path file;
    private Root root = new Root();
    private final AtomicInteger lock = new AtomicInteger();

    public WebData(Path dataDir) {
        this.file = dataDir.resolve("web.json");
        load();
    }

    private synchronized void load() {
        try {
            if (Files.isRegularFile(file)) {
                root = JSON.readValue(file.toFile(), Root.class);
            } else {
                seed();
                save();
            }
        } catch (IOException e) {
            root = new Root();
            seed();
        }
    }

    private void seed() {
        News n = new News();
        n.id = root.newsSeq++;
        n.title = "Khai mở máy chủ Phong Vân!";
        n.body = "Chào mừng các hảo hán đến với thế giới Phong Vân. Đăng ký ngay để nhận quà tân thủ!";
        n.type = "news";
        n.date = System.currentTimeMillis();
        root.news.add(n);

        Product p = new Product();
        p.id = root.productSeq++;
        p.name = "Mũ Tân Thủ";
        p.itemId = 1;
        p.count = 1;
        p.price = 100;
        root.products.add(p);

        long[][] packs = {{10000, 100, 0}, {20000, 200, 20}, {50000, 500, 75}, {100000, 1000, 200}, {500000, 5000, 1500}};
        for (long[] pk : packs) {
            Package gp = new Package();
            gp.id = root.packageSeq++;
            gp.priceVnd = pk[0];
            gp.xu = pk[1];
            gp.bonus = pk[2];
            gp.name = (pk[0] / 1000) + "K → " + pk[1] + " Xu" + (pk[2] > 0 ? " (+" + pk[2] + ")" : "");
            root.packages.add(gp);
        }
    }

    public Package packageById(int id) {
        return root.packages.stream().filter(x -> x.id == id).findFirst().orElse(null);
    }

    public synchronized Order createOrder(String user, Package pk, String code) {
        Order o = new Order();
        o.id = root.orderSeq++;
        o.code = code;
        o.user = user;
        o.packageId = pk.id;
        o.amountVnd = pk.priceVnd;
        o.xu = pk.xu + pk.bonus;
        o.createdAt = System.currentTimeMillis();
        root.orders.add(0, o);
        save();
        return o;
    }

    public Order orderById(long id) {
        return root.orders.stream().filter(o -> o.id == id).findFirst().orElse(null);
    }

    /** Finds the most recent pending order whose code appears in the transfer content. */
    public Order findPendingByContent(String content) {
        if (content == null) {
            return null;
        }
        String c = content.toUpperCase().replaceAll("\\s+", "");
        for (Order o : root.orders) {
            if ("pending".equals(o.status) && !o.code.isBlank() && c.contains(o.code.toUpperCase())) {
                return o;
            }
        }
        return null;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), root);
        } catch (IOException e) {
            // best effort
        }
    }

    public Root root() {
        return root;
    }

    /** Records a transaction (newest first) and persists. */
    public synchronized void addTx(String user, String type, String detail, long amount, String currency, long nowMs) {
        Tx tx = new Tx(root.txSeq++, user, type, detail, amount, currency);
        tx.time = nowMs;
        root.transactions.add(0, tx);
        // Keep the log bounded.
        while (root.transactions.size() > 5000) {
            root.transactions.remove(root.transactions.size() - 1);
        }
        save();
    }

    public List<Tx> transactionsFor(String user, int limit) {
        List<Tx> out = new ArrayList<>();
        for (Tx t : root.transactions) {
            if (t.user.equalsIgnoreCase(user)) {
                out.add(t);
                if (out.size() >= limit) break;
            }
        }
        return out;
    }

    public List<Tx> recentTransactions(int limit) {
        return root.transactions.subList(0, Math.min(limit, root.transactions.size()));
    }
}
