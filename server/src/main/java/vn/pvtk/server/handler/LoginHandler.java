package vn.pvtk.server.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.pvtk.protocol.Packet;
import vn.pvtk.protocol.message.Messages.LoginRequest;
import vn.pvtk.protocol.message.Messages.LoginResponse;
import vn.pvtk.protocol.message.Messages.WorldSnapshot;
import vn.pvtk.server.session.PlayerSession;
import vn.pvtk.server.world.Inventory;
import vn.pvtk.server.world.MapInstance;
import vn.pvtk.server.world.Player;
import vn.pvtk.server.world.World;

/**
 * Authenticates a connection and brings the player into the world.
 *
 * <p>This rewrite uses a trivial "create-on-first-login" auth so the multiplayer
 * loop is demonstrable end-to-end; a production build would validate credentials
 * against a database here (the hook is the single {@code authenticate} method).
 */
public final class LoginHandler implements PacketHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

    @Override
    public boolean allowsUnauthenticated() {
        return true;
    }

    @Override
    public void handle(PlayerSession session, Packet packet, GameContext ctx) {
        if (session.isAuthenticated()) {
            return; // ignore duplicate logins
        }
        LoginRequest req = LoginRequest.from(packet);

        if (req.username().isBlank()) {
            session.send(new LoginResponse(false, "Tên tài khoản trống", null).toPacket());
            return;
        }
        // Validate against the shared account store (auto-creates a fresh account).
        vn.pvtk.server.account.Account account =
                ctx.accounts().authenticateOrCreate(req.username(), req.password());
        if (account == null) {
            session.send(new LoginResponse(false, "Sai mật khẩu hoặc tài khoản bị khóa", null).toPacket());
            return;
        }
        account.lastLogin = System.currentTimeMillis();
        session.account(account);

        World world = ctx.world();
        MapInstance start = world.map(1);
        Inventory inventory = new Inventory(ctx.gameData());
        // Starter gear from the real item table (novice head/armor/shoes: ids 1,2,3).
        inventory.add(1, 1);
        inventory.add(2, 1);
        inventory.add(3, 1);
        Player player = new Player(req.username(), start.mapId(), start.spawnX(), start.spawnY(), inventory);
        // Restore persistent progress (gold/level/exp/coin) from the account.
        player.applyProgress(account.gold, account.level, account.exp, account.coin);
        // Grant the first few skills from skill.txt as starters.
        ctx.gameData().skills().keySet().stream().sorted().limit(3).forEach(player::learnSkill);
        session.bindPlayer(player);
        ctx.sessions().onAuthenticated(session);

        // 1) Acknowledge login with the player's own state.
        session.send(new LoginResponse(true, "Đăng nhập thành công", player.toState()).toPacket());
        // 2) Enter the world (notifies neighbours of the spawn).
        world.enter(session);
        // 3) Send the full snapshot of everyone already visible.
        session.send(new WorldSnapshot(player.mapId(), world.visibleEntities(player)).toPacket());
        // 4) Send the initial inventory snapshot.
        session.send(new vn.pvtk.protocol.message.Messages.BagSnapshot(
                player.gold(), inventory.bagStacks(), inventory.equipmentStacks()).toPacket());
        // 5) Send the player's known skills.
        java.util.List<vn.pvtk.protocol.message.Messages.SkillEntry> skills = new java.util.ArrayList<>();
        for (int skillId : player.learnedSkills()) {
            var def = ctx.gameData().skill(skillId);
            if (def != null) {
                skills.add(new vn.pvtk.protocol.message.Messages.SkillEntry(
                        def.id(), def.level(), def.name(), def.useMp()));
            }
        }
        session.send(new vn.pvtk.protocol.message.Messages.SkillList(skills).toPacket());
        // 6) Send currency balances (gold / coin / xu).
        ConvertHandler.sendCurrency(session, account, player);

        log.info("Login OK: {} from {} on line {}", req.username(), session.remoteAddress(), req.serverLine());
    }
}
