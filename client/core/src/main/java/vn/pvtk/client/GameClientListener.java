package vn.pvtk.client;

import vn.pvtk.protocol.message.Messages.ChatBroadcast;
import vn.pvtk.protocol.message.Messages.CombatEvent;
import vn.pvtk.protocol.message.Messages.CountryActionResult;
import vn.pvtk.protocol.message.Messages.CountryList;
import vn.pvtk.protocol.message.Messages.AchievementList;
import vn.pvtk.protocol.message.Messages.AchievementUnlocked;
import vn.pvtk.protocol.message.Messages.ArenaStatus;
import vn.pvtk.protocol.message.Messages.BattleUpdate;
import vn.pvtk.protocol.message.Messages.EscortStatus;
import vn.pvtk.protocol.message.Messages.FriendList;
import vn.pvtk.protocol.message.Messages.MailList;
import vn.pvtk.protocol.message.Messages.MarketList;
import vn.pvtk.protocol.message.Messages.MercList;
import vn.pvtk.protocol.message.Messages.QuestList;
import vn.pvtk.protocol.message.Messages.ShopListing;
import vn.pvtk.protocol.message.Messages.SkillList;
import vn.pvtk.protocol.message.Messages.TeamUpdate;
import vn.pvtk.protocol.message.Messages.WarStatus;

/**
 * High-level game events surfaced to the UI layer. All callbacks fire on the
 * network thread; UI code should marshal to its own render/main thread as needed
 * (libGDX: {@code Gdx.app.postRunnable}).
 */
public interface GameClientListener {

    default void onConnected() {
    }

    default void onLoginResult(boolean ok, String message) {
    }

    /** The world snapshot or an entity set changed; re-read {@link GameClient#state()}. */
    default void onWorldChanged() {
    }

    default void onEntityMoved(int entityId, int x, int y, int dir) {
    }

    default void onChat(ChatBroadcast chat) {
    }

    /** Inventory snapshot updated; re-read {@code state().inventory()}. */
    default void onInventoryChanged() {
    }

    default void onCombat(CombatEvent event) {
    }

    default void onCountryResult(int opcode, CountryActionResult result) {
    }

    default void onCountryList(CountryList list) {
    }

    default void onShopListing(ShopListing listing) {
    }

    default void onSkillList(SkillList skills) {
    }

    default void onTeamUpdate(TeamUpdate team) {
    }

    default void onMailList(MailList mails) {
    }

    default void onQuestList(QuestList quests) {
    }

    default void onAchievementList(AchievementList achievements) {
    }

    default void onAchievementUnlocked(AchievementUnlocked achievement) {
    }

    default void onMarketList(MarketList market) {
    }

    default void onMercList(MercList mercs) {
    }

    default void onFriendList(FriendList friends) {
    }

    default void onWarStatus(WarStatus war) {
    }

    default void onArenaStatus(ArenaStatus arena) {
    }

    default void onEscortStatus(EscortStatus escort) {
    }

    /** A turn-battle round (or the initial model) arrived; re-read {@code state().battle()}. */
    default void onBattleUpdate(BattleUpdate battle) {
    }

    /** Currency balances changed (gold / coin "Tiền nạp" / xu). */
    default void onCurrency(long gold, long coin, long xu) {
    }

    /** The in-game top-up package menu ("Gói nạp") arrived. */
    default void onRedeemList(vn.pvtk.protocol.message.Messages.RedeemList packages) {
    }

    default void onDisconnected(String reason) {
    }
}
