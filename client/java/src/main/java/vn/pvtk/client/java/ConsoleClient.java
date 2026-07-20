package vn.pvtk.client.java;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import vn.pvtk.client.GameClient;
import vn.pvtk.client.GameClientListener;
import vn.pvtk.client.model.Entity;
import vn.pvtk.protocol.message.Messages.ChatBroadcast;

/**
 * A fully runnable, dependency-free Java reference client for the PC/desktop.
 *
 * <p>It connects to the server, logs in, and offers a small interactive console
 * to move and chat &mdash; useful for manually testing the server and as the
 * canonical example of how to drive {@link GameClient} from any front-end.
 *
 * <pre>
 *   ./gradlew :client-java:run --args="--host 127.0.0.1 --port 30000 --user Alice"
 *
 *   commands:  m &lt;x&gt; &lt;y&gt;   move to tile
 *              s &lt;text&gt;     say on world chat
 *              who          list visible players
 *              quit         disconnect and exit
 * </pre>
 */
public final class ConsoleClient {

    public static void main(String[] args) throws Exception {
        String host = arg(args, "--host", "127.0.0.1");
        int port = Integer.parseInt(arg(args, "--port", "30000"));
        String user = arg(args, "--user", "Player" + (System.nanoTime() % 1000));

        // Holder lets the listener reference the client that is assigned just below.
        GameClient[] ref = new GameClient[1];
        GameClient client = new GameClient(new GameClientListener() {
            @Override public void onConnected() {
                System.out.println("[net] connected");
            }
            @Override public void onLoginResult(boolean ok, String message) {
                System.out.println("[login] " + (ok ? "OK" : "FAIL") + " - " + message);
            }
            @Override public void onWorldChanged() {
                Entity self = ref[0].state().self();
                System.out.println("[world] you=" + (self == null ? "?" : self)
                        + " | others=" + ref[0].state().others().size());
            }
            @Override public void onEntityMoved(int id, int x, int y, int dir) {
                System.out.println("[move] entity " + id + " -> (" + x + "," + y + ")");
            }
            @Override public void onChat(ChatBroadcast chat) {
                System.out.println("[" + chat.channel() + "] " + chat.fromName() + ": " + chat.text());
            }
            @Override public void onInventoryChanged() {
                var inv = ref[0].state().inventory();
                System.out.println("[bag] gold=" + inv.gold() + " items=" + inv.bag().size()
                        + " " + inv.bag().stream().map(s -> s.name() + "x" + s.count()).toList());
            }
            @Override public void onCombat(vn.pvtk.protocol.message.Messages.CombatEvent e) {
                System.out.println("[combat] " + e.attackerId() + " hit " + e.targetId()
                        + " for " + e.damage() + " (hp=" + e.targetHp() + (e.killed() ? ", KILLED" : "") + ")");
            }
            @Override public void onCountryResult(int op, vn.pvtk.protocol.message.Messages.CountryActionResult r) {
                System.out.println("[country] " + (r.ok() ? "OK" : "FAIL") + " - " + r.message()
                        + (r.country() != null ? " {" + r.country().name() + " #" + r.country().id()
                            + ", " + r.country().memberCount() + " members}" : ""));
            }
            @Override public void onCountryList(vn.pvtk.protocol.message.Messages.CountryList l) {
                System.out.println("[country list] " + l.countries().size() + " bang:");
                l.countries().forEach(c -> System.out.println("  #" + c.id() + " " + c.name()
                        + " (vua " + c.kingName() + ", " + c.memberCount() + " members)"));
            }
            @Override public void onShopListing(vn.pvtk.protocol.message.Messages.ShopListing l) {
                System.out.println("[shop #" + l.shopId() + "] " + l.entries().size() + " món:");
                l.entries().forEach(e -> System.out.println("  item " + e.itemId() + " " + e.name()
                        + " - " + e.price() + " vàng"));
            }
            @Override public void onSkillList(vn.pvtk.protocol.message.Messages.SkillList s) {
                System.out.println("[skills] " + s.skills().stream()
                        .map(x -> x.id() + ":" + x.name() + "(mp" + x.useMp() + ")").toList());
            }
            @Override public void onTeamUpdate(vn.pvtk.protocol.message.Messages.TeamUpdate t) {
                System.out.println("[team] leader=" + t.leaderId() + " members="
                        + t.members().stream().map(m -> m.name()).toList());
            }
            @Override public void onMailList(vn.pvtk.protocol.message.Messages.MailList m) {
                System.out.println("[mail] " + m.mails().size() + " thư:");
                m.mails().forEach(x -> System.out.println("  #" + x.id() + " từ " + x.fromName()
                        + ": " + x.subject() + " (" + x.gold() + " vàng)"));
            }
            @Override public void onQuestList(vn.pvtk.protocol.message.Messages.QuestList q) {
                System.out.println("[quests]");
                q.quests().forEach(x -> System.out.println("  #" + x.id() + " " + x.name()
                        + " [" + x.progress() + "/" + x.target() + "] "
                        + (new String[]{"available", "active", "done"})[x.state()]
                        + " (EXP " + x.rewardExp() + ", vàng " + x.rewardGold() + ")"));
            }
            @Override public void onAchievementList(vn.pvtk.protocol.message.Messages.AchievementList a) {
                System.out.println("[achievements]");
                a.achievements().forEach(x -> System.out.println("  " + (x.unlocked() ? "[x] " : "[ ] ")
                        + x.name() + " - " + x.desc()));
            }
            @Override public void onAchievementUnlocked(vn.pvtk.protocol.message.Messages.AchievementUnlocked a) {
                System.out.println("[ACHIEVEMENT] Mở khóa: " + a.name() + "!");
            }
            @Override public void onMarketList(vn.pvtk.protocol.message.Messages.MarketList m) {
                System.out.println("[market] " + m.listings().size() + " món rao bán:");
                m.listings().forEach(x -> System.out.println("  #" + x.listingId() + " " + x.itemName()
                        + " x" + x.count() + " - " + x.price() + " vàng (bởi " + x.sellerName() + ")"));
            }
            @Override public void onMercList(vn.pvtk.protocol.message.Messages.MercList m) {
                System.out.println("[mercenary]");
                m.mercs().forEach(x -> System.out.println("  #" + x.id() + " " + x.name()
                        + " Lv" + x.level() + " +" + x.atkBonus() + " ATK - " + x.price() + " vàng"
                        + (x.owned() ? " [đang dùng]" : "")));
            }
            @Override public void onFriendList(vn.pvtk.protocol.message.Messages.FriendList f) {
                System.out.println("[friends] " + f.friends().size() + ":");
                f.friends().forEach(x -> System.out.println("  " + x.name()
                        + " Lv" + x.level() + (x.online() ? " (online)" : " (offline)")));
            }
            @Override public void onWarStatus(vn.pvtk.protocol.message.Messages.WarStatus w) {
                System.out.println("[war] " + (w.active()
                        ? w.attacker() + " " + w.attackerScore() + " - " + w.defenderScore() + " " + w.defender()
                        : w.message()));
            }
            @Override public void onArenaStatus(vn.pvtk.protocol.message.Messages.ArenaStatus a) {
                String st = new String[]{"rảnh", "đang chờ", "đang đấu", "kết quả"}[Math.min(3, a.state())];
                System.out.println("[arena] " + st + (a.opponent().isEmpty() ? "" : " vs " + a.opponent())
                        + " (hạng " + a.rank() + ") " + a.message());
            }
            @Override public void onEscortStatus(vn.pvtk.protocol.message.Messages.EscortStatus e) {
                System.out.println("[escort] " + (e.active() ? "đang hộ tống đến " + e.destMap()
                        + " (" + e.progress() + "%)" : "") + " " + e.message());
            }
            @Override public void onCurrency(long gold, long coin, long xu) {
                System.out.println("[tiền] vàng=" + gold + "  tiền nạp(coin)=" + coin + "  xu(web)=" + xu);
            }
            @Override public void onBattleUpdate(vn.pvtk.protocol.message.Messages.BattleUpdate b) {
                System.out.println("[battle] vòng " + b.round() + " - " + b.message());
                b.actions().forEach(a -> System.out.println("    #" + a.attacker() + " -> #" + a.target()
                        + " dmg " + a.damage() + " (hp " + a.targetHp() + ")" + (a.died() ? " DIE" : "")));
                b.combatants().forEach(u -> System.out.println("    [" + u.index() + "] " + u.name()
                        + (u.side() == 0 ? " (ta)" : " (địch)") + " " + u.hp() + "/" + u.maxHp()
                        + " MP " + u.mp()));
                if (b.roundState() == 0) {
                    System.out.println("    -> dùng: plan <indexĐịch> [skillId]");
                }
            }
            @Override public void onDisconnected(String reason) {
                System.out.println("[net] disconnected: " + reason);
            }
        });
        ref[0] = client;

        System.out.printf("Connecting to %s:%d as %s...%n", host, port, user);
        client.connect(host, port);
        client.login(user, "", 0);

        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        String line;
        printHelp();
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] t = line.split("\\s+", 2);
            switch (t[0]) {
                case "m" -> {
                    String[] xy = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    if (xy.length == 2) {
                        client.move(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]), 0);
                    } else {
                        System.out.println("usage: m <x> <y>");
                    }
                }
                case "s" -> {
                    if (t.length > 1) {
                        client.say(t[1]);
                    }
                }
                case "who" -> {
                    System.out.println("you: " + client.state().self());
                    client.state().others().forEach(e ->
                            System.out.println("  - " + e + (e.isMonster() ? " [monster]" : "")));
                }
                case "jump" -> {
                    if (t.length > 1) {
                        client.jumpMap(Integer.parseInt(t[1].trim()));
                    } else {
                        System.out.println("usage: jump <mapId>  (1=town, 3=wilderness w/ monsters)");
                    }
                }
                case "bag" -> client.requestBag();
                case "eq" -> {
                    if (t.length > 1) {
                        client.equip(Integer.parseInt(t[1].trim()));
                    } else {
                        System.out.println("usage: eq <bagSlot>");
                    }
                }
                case "use" -> {
                    if (t.length > 1) {
                        client.useItem(Integer.parseInt(t[1].trim()));
                    } else {
                        System.out.println("usage: use <bagSlot>");
                    }
                }
                case "atk" -> {
                    if (t.length > 1) {
                        client.attack(Integer.parseInt(t[1].trim()));
                    } else {
                        System.out.println("usage: atk <targetId>   (see ids via 'who')");
                    }
                }
                case "country" -> handleCountry(client, t.length > 1 ? t[1] : "");
                case "shop" -> client.openShop(t.length > 1 ? Integer.parseInt(t[1].trim()) : 1);
                case "buy" -> {
                    String[] a = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    if (a.length >= 1) {
                        client.buy(Integer.parseInt(a[0]), a.length > 1 ? Integer.parseInt(a[1]) : 1);
                    } else {
                        System.out.println("usage: buy <itemId> [count]");
                    }
                }
                case "sell" -> {
                    String[] a = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    if (a.length >= 1) {
                        client.sell(Integer.parseInt(a[0]), a.length > 1 ? Integer.parseInt(a[1]) : 1);
                    } else {
                        System.out.println("usage: sell <bagSlot> [count]");
                    }
                }
                case "skills" -> client.requestSkills();
                case "party" -> {
                    if (t.length > 1) {
                        client.inviteToTeam(t[1].trim());
                    } else {
                        System.out.println("usage: party <playerName>   (leaveparty to leave)");
                    }
                }
                case "leaveparty" -> client.leaveTeam();
                case "mail" -> client.requestMail();
                case "sendmail" -> {
                    String[] a = t.length > 1 ? t[1].split("\\s+", 2) : new String[0];
                    if (a.length == 2) {
                        client.sendMail(a[0], "Thư", a[1], 0);
                    } else {
                        System.out.println("usage: sendmail <toName> <message>");
                    }
                }
                case "quests" -> client.requestQuests();
                case "accept" -> {
                    if (t.length > 1) client.acceptQuest(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: accept <questId>");
                }
                case "turnin" -> {
                    if (t.length > 1) client.completeQuest(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: turnin <questId>");
                }
                case "ach" -> client.requestAchievements();
                case "market" -> client.requestMarket();
                case "list" -> {
                    String[] a = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    if (a.length >= 2) {
                        client.marketSell(Integer.parseInt(a[0]), 1, Integer.parseInt(a[1]));
                    } else {
                        System.out.println("usage: list <bagSlot> <price>");
                    }
                }
                case "mbuy" -> {
                    if (t.length > 1) client.marketBuy(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: mbuy <listingId>");
                }
                case "merc" -> client.requestMercs();
                case "hire" -> {
                    if (t.length > 1) client.hireMerc(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: hire <mercId>");
                }
                case "friends" -> client.requestFriends();
                case "addf" -> {
                    if (t.length > 1) client.addFriend(t[1].trim());
                    else System.out.println("usage: addf <name>");
                }
                case "delf" -> {
                    if (t.length > 1) client.removeFriend(t[1].trim());
                    else System.out.println("usage: delf <name>");
                }
                case "claim" -> {
                    if (t.length > 1) client.claimMail(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: claim <mailId>");
                }
                case "war" -> client.warStatus();
                case "declare" -> {
                    if (t.length > 1) client.declareWar(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: declare <countryId>");
                }
                case "arena" -> client.arenaQueue();
                case "escort" -> client.startEscort();
                case "convert" -> {
                    if (t.length > 1) client.convertXu(Long.parseLong(t[1].trim()));
                    else System.out.println("usage: convert <số xu>   (đổi Xu web → tiền nạp trong game)");
                }
                case "battle" -> {
                    if (t.length > 1) client.enterBattle(Integer.parseInt(t[1].trim()));
                    else System.out.println("usage: battle <monsterId>  (id từ 'who' khi ở map 3)");
                }
                case "plan" -> {
                    String[] a = t.length > 1 ? t[1].split("\\s+") : new String[0];
                    var b = client.state().battle();
                    if (b == null) {
                        System.out.println("Bạn không ở trong trận.");
                    } else if (a.length >= 1) {
                        client.battlePlan(b.round(), Integer.parseInt(a[0]),
                                a.length > 1 ? Integer.parseInt(a[1]) : 0);
                    } else {
                        System.out.println("usage: plan <indexĐịch> [skillId]");
                    }
                }
                case "quit", "exit" -> {
                    client.disconnect();
                    return;
                }
                default -> printHelp();
            }
        }
    }

    private static void handleCountry(GameClient client, String rest) {
        String[] a = rest.trim().split("\\s+", 2);
        switch (a[0]) {
            case "create" -> {
                if (a.length > 1) {
                    client.createCountry(a[1]);
                } else {
                    System.out.println("usage: country create <name>");
                }
            }
            case "list" -> client.listCountries();
            case "join" -> {
                if (a.length > 1) {
                    client.joinCountry(Integer.parseInt(a[1].trim()));
                } else {
                    System.out.println("usage: country join <id>");
                }
            }
            case "leave" -> client.leaveCountry();
            case "info" -> client.countryInfo();
            default -> System.out.println("country create <name> | list | join <id> | leave | info");
        }
    }

    private static void printHelp() {
        System.out.println("commands:");
        System.out.println("  m <x> <y>            move          s <text>     say (world)");
        System.out.println("  jump <mapId>         change map     who          list entities");
        System.out.println("  bag                  inventory      eq <slot>    equip item");
        System.out.println("  use <slot>           dùng vật phẩm (thuốc hồi HP/MP)");
        System.out.println("  atk <targetId>       attack         skills       list skills");
        System.out.println("  shop [id]            open shop      buy <itemId> [n] | sell <slot> [n]");
        System.out.println("  party <name>         invite         leaveparty   leave party");
        System.out.println("  mail                 inbox          sendmail <to> <msg>");
        System.out.println("  quests | accept <id> | turnin <id>  ach (achievements)");
        System.out.println("  market | list <slot> <price> | mbuy <id>   merc | hire <id>");
        System.out.println("  friends | addf <name> | delf <name>   claim <mailId>");
        System.out.println("  war | declare <countryId>   arena (queue duel)   escort (mission)");
        System.out.println("  battle <monsterId> | plan <enemyIndex> [skillId]   (turn-based)");
        System.out.println("  convert <số xu>      đổi Xu (web) → tiền nạp trong game");
        System.out.println("  country create <name> | list | join <id> | leave | info");
        System.out.println("  quit");
    }

    private static String arg(String[] args, String key, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(key)) {
                return args[i + 1];
            }
        }
        return def;
    }
}
