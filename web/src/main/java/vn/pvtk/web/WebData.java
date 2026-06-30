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

    public static final class Root {
        public List<News> news = new ArrayList<>();
        public List<Giftcode> giftcodes = new ArrayList<>();
        public List<Product> products = new ArrayList<>();
        public int newsSeq = 1;
        public int productSeq = 1;
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
}
