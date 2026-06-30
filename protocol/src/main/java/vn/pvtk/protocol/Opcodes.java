package vn.pvtk.protocol;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of protocol message opcodes (command ids).
 *
 * <p>These 245 opcodes were recovered from the original Phong&nbsp;V&acirc;n client
 * by mapping each obfuscated message factory back to its debug label. They are
 * grouped by leading digits into logical subsystems:
 *
 * <pre>
 *   5xxx   account / edition / auth bootstrap
 *   10xxx  world, character, map navigation
 *   11xxx  mail, photo album, achievements, settings
 *   12xxx  inventory, player shops, battle entry
 *   13xxx  team, chat, auction / marketplace, relations
 *   14xxx  skills, quests, escort, boss raids, arena
 *   15xxx  country (guild/nation), war, mercenary, pets
 *   16xxx / 17xxx  item shop, titles
 * </pre>
 *
 * The same opcode number may be used for a client&rarr;server request and the
 * matching server&rarr;client response; the direction is contextual, exactly as
 * in the original protocol.
 */
public final class Opcodes {

    private Opcodes() {
    }

    private static final Map<Integer, String> NAMES = new LinkedHashMap<>();

    private static void register(int code, String name) {
        NAMES.put(code, name);
    }

    /** Returns the symbolic name for an opcode, or {@code "UNKNOWN_<code>"}. */
    public static String name(int code) {
        String n = NAMES.get(code);
        return n != null ? n : "UNKNOWN_" + code;
    }

    public static boolean isKnown(int code) {
        return NAMES.containsKey(code);
    }

    /** An immutable view of every known opcode &rarr; name mapping. */
    public static Map<Integer, String> all() {
        return Collections.unmodifiableMap(NAMES);
    }

    // --- Frequently used opcodes referenced directly by the server/clients ---
    public static final int CHECK_EDITION       = 5000;  // client hello / version check
    public static final int AUTO_CREATE_PLAYER  = 5003;  // quick account+character create
    public static final int LOGIN               = 10003; // enter game with chosen server line
    public static final int WORLD_DATA          = 10503; // initial world snapshot
    public static final int JUMP_MAP            = 10506; // change map / zone
    public static final int AUTO_MOVE           = 10518; // movement command
    public static final int GET_SPRITE          = 10520; // request a sprite/entity's data
    public static final int CHAT                = 13509; // chat message
    public static final int RELATION_LIST       = 13529; // friends list

    // --- Subsystems implemented by this rewrite (request/response share the opcode) ---
    public static final int BAG                 = 12001; // inventory: list / equip / move
    public static final int ATTACK              = 12505; // combat: attack request (client->server)
    public static final int COMBAT_EVENT        = 12506; // combat: damage/death event (server->client)
    public static final int COUNTRY_CREATE      = 15001; // guild/nation: create
    public static final int COUNTRY_INFO        = 15002; // guild/nation: info
    public static final int COUNTRY_LIST        = 15004; // guild/nation: list
    public static final int COUNTRY_JOIN        = 15011; // guild/nation: apply/join
    public static final int COUNTRY_LEAVE       = 15015; // guild/nation: leave
    public static final int SHOP_LIST           = 12020; // NPC shop: open / listing
    public static final int SHOP_BUY            = 12021; // NPC shop: buy
    public static final int SHOP_SELL           = 16001; // NPC shop: sell
    public static final int SKILL_LIST          = 14001; // skills the player knows
    public static final int TEAM_INVITE         = 13501; // party: invite / accept
    public static final int TEAM_UPDATE         = 13506; // party: roster update
    public static final int TEAM_LEAVE          = 13507; // party: leave
    public static final int MAIL_SEND           = 11009; // mail: send
    public static final int MAIL_LIST           = 11007; // mail: mailbox list
    public static final int QUEST_LIST          = 14512; // quests: list available + active
    public static final int QUEST_ACCEPT        = 14502; // quests: accept
    public static final int QUEST_COMPLETE      = 14503; // quests: turn in
    public static final int ACHIEVE_LIST        = 11024; // achievements: list
    public static final int ACHIEVE_UNLOCK      = 11025; // achievements: unlocked push
    public static final int MARKET_LIST         = 13520; // marketplace: browse listings
    public static final int MARKET_SELL         = 13518; // marketplace: consign an item
    public static final int MARKET_BUY          = 13517; // marketplace: buy a listing
    public static final int MERC_LIST           = 15503; // mercenary/pet: hireable list
    public static final int MERC_BUY            = 15505; // mercenary/pet: hire
    public static final int RELATION_ADD        = 13530; // friends: add
    public static final int RELATION_DEL        = 13531; // friends: remove
    public static final int MAIL_CLAIM          = 11011; // mail: claim attachment
    public static final int WAR_DECLARE         = 15047; // country war: declare
    public static final int WAR_STATUS          = 15040; // country war: status / scoreboard
    public static final int ARENA_QUEUE         = 14526; // arena: join queue / status
    public static final int ARENA_STATUS        = 14528; // arena: status / result push
    public static final int ESCORT_START        = 14510; // escort: begin mission
    public static final int ESCORT_STATUS       = 14514; // escort: status / event push
    public static final int BATTLE_ENTER        = 12501; // turn battle: enter (EnterLocalBattle)
    public static final int BATTLE_PLAN         = 12503; // turn battle: submit round plan
    public static final int BATTLE_UPDATE       = 12508; // turn battle: round result / model
    public static final int CURRENCY_INFO       = 11078; // server->client: gold / coin / xu balances
    public static final int CONVERT_XU          = 11030; // client->server: convert web Xu -> in-game coin

    static {
        register(5000, "CHECK_EDITION_MSG");
        register(5001, "MODIFY_PLAYER_NAME");
        register(5002, "SPECIAL_CODE_MSG");
        register(5003, "AUTO_CREATE_PLAYER");
        register(5007, "DATA_MONSTER_GROUP_MSG");
        register(5008, "CHECK_HTTP_MSG");
        register(5009, "RESET_PASSWORD");
        register(5010, "FIND_PASSWORD");
        register(5011, "CANCEL_BIND");
        register(10001, "PLAYER_SHOP_RECORD_MSG");
        register(10002, "ADD_PLAYER_MSG");
        register(10003, "BATTLE_PLAN");
        register(10004, "SET_TEAM_MSG");
        register(10005, "AUTO_CREATE_ACTOR");
        register(10006, "MODIFY_PLAYER_NAME_2");
        register(10007, "MODIFY_ACTOR_NAME");
        register(10503, "WORLD_DATA_MESSAGE");
        register(10504, "MERCENARY_INFO_MSG");
        register(10505, "MISSION_N_P_C_STATUS");
        register(10506, "JUMP_MAP_MESSAGE");
        register(10508, "BATTLE_PLAN_2");
        register(10510, "JUMP_CITY_MESSAGE");
        register(10511, "BROWSE_CITY_INFO_MESSAGE");
        register(10512, "GET_CITY_MONEY_MSG");
        register(10513, "MODIFY_CITY_NAME_MSG");
        register(10514, "MODIFY_CITY_SIGN_MSG");
        register(10517, "SET_REBORN_MAP_MSG");
        register(10518, "AUTO_MOVE_MSG");
        register(10519, "GET_N_P_C_DATA");
        register(10520, "GET_SPRITE_MESSAGE");
        register(10521, "WORLD_DATA_MSG");
        register(10522, "COUNTRY_GET_ALL_MISSION");
        register(10523, "MAIL_NEW_NOTICE");
        register(10524, "COUNTRY_MAIN_MENU");
        register(11003, "PLAYER_ENCHANT_SHOP_START_MSG");
        register(11006, "REMOTE_BATTLE_NOTIFY");
        register(11007, "MAIL_LIST_MSG");
        register(11008, "MAIL_DETAIL_MSG");
        register(11009, "MAIL_SEND_MSG");
        register(11010, "MAIL_SEND_G_M_MSG");
        register(11011, "MAIL_ATTACH_MSG");
        register(11012, "MAIL_BACK_MSG");
        register(11013, "MAIL_DELETE_MSG");
        register(11015, "PHOTO_DEL");
        register(11016, "PHOTO_UP_DOWN_MSG");
        register(11017, "PHOTO_CONTENT_MSG");
        register(11018, "PHOTO_LOVE_MSG");
        register(11019, "PHOTO_ALBUMS_MSG");
        register(11020, "GET_ALBUMS_LIST_MSG");
        register(11021, "PLAYER_CARD_GENERATE_MSG");
        register(11022, "PLAYER_CARD_VIEW_MSG");
        register(11023, "ACHIEVE_GET_INFO");
        register(11024, "ACHIEVE_LIST");
        register(11025, "ACHIEVE_GAIN_REWARD");
        register(11026, "ACHIEVE_TITLE_LIST");
        register(11027, "COUNTRY_LEAVE_MSG");
        register(11030, "PAY_INFO_ZHI_FU_BAO");
        register(11031, "MAIL_NEW_NOTICE_2");
        register(11034, "MAIL_SEE_ITEM");
        register(11038, "MODIFY_ACTOR_NAME_BY_ITEM");
        register(11078, "PAY_DESCRIBE");
        register(11163, "SETTING_CHANGE_MESSAGE");
        register(11501, "GET_SUIT_INFO_MSG");
        register(11503, "COMBIN_SHOP");
        register(11504, "COMBIN_ITEM");
        register(11505, "COMBIN_CONFIRM");
        register(11522, "PLAYER_ENCHANT_SHOP_START_MSG_2");
        register(11523, "PLAYER_ENCHANT_SHOP_END_MSG");
        register(11525, "PLAYER_ENCHANT_SHOP_ITEM_LIST_MSG");
        register(11526, "LIST_PLAYER_MSG");
        register(12001, "PLAYER_BAG_MESSAGE");
        register(12003, "BAG_REFRESH_MSG");
        register(12004, "CANCEL_BIND_2");
        register(12005, "MERCENARY_BUY_MSG");
        register(12006, "INTEGRAL_BUY");
        register(12007, "PLAYER_SHOP_START_MSG");
        register(12008, "PLAYER_SHOP_END_MSG");
        register(12010, "PLAYER_SHOP_ITEM_LIST_MSG");
        register(12011, "PLAYER_SHOP_BUY_MSG");
        register(12012, "PLAYER_ENTER_MSG");
        register(12015, "IDENTIFY_ASK");
        register(12016, "ITEM_INFO_MSG");
        register(12017, "STORAGE_LIST_MSG");
        register(12018, "STORAGE_OPERATE_MSG");
        register(12020, "INTEGRAL_SHOP");
        register(12021, "INTEGRAL_BUY_2");
        register(12024, "V_I_P_STORAGE_LIST_MSG");
        register(12025, "V_I_P_STORAGE_OPERATE_MSG");
        register(12501, "ENTER_LOCAL_BATTLE");
        register(12503, "ENTER_REMOTE_BATTLE");
        register(12505, "BATTLE_PLAN_3");
        register(12506, "BATTLE_UPDATE");
        register(12507, "P_K_ASK_MSG");
        register(12508, "FIGHT_SEE_INTER_MSG");
        register(12509, "FIGHT_SEE_QUIT_MSG");
        register(12516, "SKY_ARENA_REFRESH");
        register(13001, "MONSTER_BOOK_LIST");
        register(13002, "MONSTER_BOOK_DETAIL");
        register(13501, "INVITE_TEAM_MSG");
        register(13506, "SET_TEAM_MSG_2");
        register(13507, "LEAVE_TEAM_MSG");
        register(13509, "CHAT_MSG");
        register(13516, "GOODS_SELL_FIND");
        register(13517, "GOODS_SELL_BUY");
        register(13518, "GOODS_SELL_SUBMIT");
        register(13519, "GOODS_SELL_RETRIEVE");
        register(13520, "GOODS_SELL_LIST");
        register(13521, "GOODS_PURCHASE_SUBMIT");
        register(13522, "GOODS_PURCHASE_CANCEL");
        register(13523, "GOODS_PURCHASE_GET_MSG");
        register(13524, "GOODS_PURCHASE_MY_LIST");
        register(13525, "GOODS_PURCHASE_LIST");
        register(13526, "GOODS_PROVIDE_MSG");
        register(13527, "GOODS_AUTO_PROVIDE");
        register(13528, "GOODS_PURCHASE_TYPE_LIST");
        register(13529, "RELATION_LIST");
        register(13530, "RELATION_ADD");
        register(13531, "RELATION_DEL");
        register(13532, "PLAYER_SEE");
        register(13533, "RELATION_FLY");
        register(13534, "CHAT_SEE_ITEM");
        register(13535, "CHAT_SEE_MISSION");
        register(13536, "STORAGE_LIST_MSG_2");
        register(13537, "RELATION_DEL_MASTER");
        register(13538, "PARTNER_FLY");
        register(13539, "PARTNER_ADD");
        register(13540, "PARTNER_DEL");
        register(13555, "MARRY_WISH_LIST");
        register(13558, "JUMP_COUNTRY_MESSAGE");
        register(14001, "BROWSE_SKILL_SHOP");
        register(14002, "MERCENARY_BUY_MSG_2");
        register(14003, "DROP_SKILL");
        register(14004, "AUTO_SKILL_MSG");
        register(14006, "DROP_SKILL_ONE_LEVEL");
        register(14501, "PLAYER_SHOP_START_MSG_2");
        register(14502, "TAST_ACCEPT_MSG");
        register(14503, "TASK_DELIVER_MSG");
        register(14504, "TASK_ABANDON_MSG");
        register(14509, "AUTO_MOVE_MSG_2");
        register(14510, "ESCORT_MOVE_MSG");
        register(14511, "GOODS_SELL_LIST_2");
        register(14512, "TASK_OFF_LINE_LIST_MSG");
        register(14513, "TASK_OFF_LINE_ACTIVATE_MSG");
        register(14514, "ESCORT_EVENT_MSG");
        register(14515, "ESCORT_ROB_LIST");
        register(14516, "ESCORT_ROB");
        register(14517, "ESCORT_LIST_PLAYER");
        register(14518, "TEAM_BOSS_START");
        register(14519, "TEAM_BOSS_REFRESH");
        register(14520, "TEAM_BOSS_FIGHT");
        register(14522, "TEAM_BOSS_QUIT");
        register(14524, "TEAM_BOSS_NOT_FIGHT");
        register(14526, "ARENA_REFRESH");
        register(14528, "ARENA_EXIT");
        register(14535, "NEW_ESCORT_ROB_LIST");
        register(14542, "COUNTRY_BOSS_REFRESH");
        register(14543, "COUNTRY_BOSS_FIGHT");
        register(14544, "COUNTRY_BOSS_QUIT");
        register(14546, "COUNTRY_BOSS_NOT_FIGHT");
        register(15001, "COUNTRY_CREATE_MSG");
        register(15002, "BROWSE_COUNTRY_INFO");
        register(15003, "COUNTRY_ACTIVE_MSG");
        register(15004, "COUNTRY_LIST_MSG");
        register(15006, "COUNTRY_RECRUIT_MSG");
        register(15007, "COUNTRY_TAX_RATE");
        register(15008, "COUNTRY_ENTER_RATE");
        register(15009, "JUMP_COUNTRY_MESSAGE_2");
        register(15010, "COUNTRY_INVITE");
        register(15011, "COUNTRY_APPLY");
        register(15012, "COUNTRY_MEMBER_APPLE_LIST_MSG");
        register(15013, "COUNTRY_APPLY_DEAL_MSG");
        register(15014, "COUNTRY_DEL_MEM_MSG");
        register(15015, "COUNTRY_LEAVE_MSG_2");
        register(15016, "COUNTRY_MEMBER_LIST_MSG");
        register(15018, "COUNTRY_ADJUST_JOB_MESSAGE");
        register(15019, "COUNTRY_BECOME_KING_MESSAGE");
        register(15020, "COUNTRY_BUILDING_UPGRADE_MSG");
        register(15021, "COUNTRY_BUILDING_REMOVE_MSG");
        register(15022, "COUNTRY_BOOK_MSG");
        register(15023, "COUNTRY_PEOPLE_DONATE");
        register(15024, "COUNTRY_STORAGE_PUT");
        register(15025, "COUNTRY_STORAGE_DEL");
        register(15026, "COUNTRY_STORAGE_LIST");
        register(15027, "COUNTRY_STORAGE_GET");
        register(15028, "COUNTRY_GET_MISSION");
        register(15029, "COUNTRY_PUBLISH_MISSION");
        register(15030, "COUNTRY_ASSING_MISSION");
        register(15031, "COUNTRY_ASSIGN_MEM");
        register(15032, "COUNTRY_ASSIGN_MISSION");
        register(15033, "COUNTRY_VALID_MISSION");
        register(15034, "COUNTRY_AFFICHE_MODIFY_MSG");
        register(15036, "COUNTRY_GET_ALL_MISSION_2");
        register(15037, "COUNTRY_MAIN_MENU_2");
        register(15038, "COUNTRY_EXCHARGE_DATA");
        register(15039, "COUNTRY_WAR_ENTER");
        register(15040, "COUNTRY_WAR_UPDATE");
        register(15041, "COUNTRY_WAR_ARMY_LIST");
        register(15042, "COUNTRY_WAR_OPER_ARMY");
        register(15044, "COUNTRY_WAR_COMMAND_LIST");
        register(15045, "COUNTRY_WAR_USE_COMMAND");
        register(15046, "WAR_DECLARE_LIST");
        register(15047, "WAR_DECLARE");
        register(15048, "DECLARE_ASK");
        register(15049, "WAR_BUILD_LIST");
        register(15050, "WAR_BUILD_LEVEL");
        register(15051, "WAR_ANSWER_INFO");
        register(15052, "WAR_BATTLE_LIST");
        register(15053, "WAR_TEXT_INFO");
        register(15054, "WAR_SOLDIER_APPLY");
        register(15055, "SOLDIER_LIST");
        register(15056, "SOLDIER_DEAL_APPLY");
        register(15057, "CAMP_INFO");
        register(15058, "WAR_UNION_APPLY_HELP");
        register(15059, "DELETE_SOLDIER");
        register(15060, "WIN_ACTION_INFO");
        register(15061, "WIN_ACTION_DO");
        register(15062, "UNION_CREATE");
        register(15063, "WAR_UNION_LIST");
        register(15064, "WAR_UNION_MY");
        register(15065, "WAR_UNION_APPLY");
        register(15066, "WAR_UNION_WAR_LIST");
        register(15067, "WAR_UNION_DEAL_APPLY");
        register(15068, "WAR_UNION_DEL_MEMBER");
        register(15069, "WAR_UNION_QUIT");
        register(15070, "WAR_UNION_CHANGE");
        register(15071, "WAR_TOP_PLAYER");
        register(15072, "WAR_TOP_COUNTRY");
        register(15073, "WAR_TOP_COUNTRY_PLAYER");
        register(15076, "CREATE_WAR_DECLARE_LIST");
        register(15077, "COUNTRY_SET_ONLINE_NOTIFY");
        register(15502, "PET_SEE_MSG");
        register(15503, "MERCENARY_LIST_MSG");
        register(15504, "MERCENARY_INFO_MSG_2");
        register(15505, "MERCENARY_BUY_MSG_3");
        register(15506, "USER_REGISTER_MSG");
        register(15507, "MERCENARY_MY_INFO_MSG");
        register(15508, "MERCENARY_SET_STATUS_MSG");
        register(15510, "MERCENARY_DEL_MSG");
        register(15511, "PET_CHANGE_NAME");
        register(15512, "SOLDIER_DEAL_APPLY_2");
        register(16001, "ITEM_SHOP_SELL");
        register(16002, "STORAGE_OPERATE_MSG_2");
        register(16006, "SPECIAL_CODE_MSG_2");
        register(17001, "ACHIEVE_TITLE_LIST_2");
        register(17002, "MODIFY_PLAYER_NAME_3");
    }
}
