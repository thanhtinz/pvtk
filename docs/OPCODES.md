# PVTK Protocol — Opcode Reference

These opcodes (message command ids) were recovered from the original
**Phong Vân** J2ME client (`pvtk1v36maxspeed.jar`) by mapping each obfuscated
message factory back to its debug label. Direction is inferred from whether the
client *creates* (C→S request) or *processes* (S→C response) the message; many
opcodes are used in both directions.

The rewritten server currently implements the core subset
(`LOGIN`, `WORLD_DATA`, `AUTO_MOVE`, `GET_SPRITE`, `CHAT`); the rest are
documented here as the roadmap for feature parity.

### 5xxx — Account / edition / auth

| Opcode | Dir | Name |
|--------|-----|------|
| 5000 | C→S | CheckEditionMsg |
| 5001 | C→S | ModifyPlayerName |
| 5002 | C→S | SpecialCodeMsg |
| 5003 | C→S | AutoCreatePlayer |
| 5007 | S→C | DataMonsterGroupMsg |
| 5008 | C→S | CheckHttpMsg |
| 5009 | C→S | ResetPassword |
| 5010 | C→S | FindPassword |
| 5011 | C→S | CancelBind |

### 10xxx — World, character, map

| Opcode | Dir | Name |
|--------|-----|------|
| 10001 | S→C | PlayerShopRecordMsg |
| 10002 | C→S | AddPlayerMsg |
| 10003 | C→S | BattlePlan |
| 10004 | C→S | SetTeamMsg |
| 10005 | C→S | AutoCreateActor |
| 10006 | C→S | ModifyPlayerName |
| 10007 | C→S | ModifyActorName |
| 10503 | C→S | WorldDataMessage |
| 10504 | C→S | MercenaryInfoMsg |
| 10505 | S→C | MissionNPCStatus |
| 10506 | C→S | JumpMapMessage |
| 10508 | C→S | BattlePlan |
| 10510 | C→S | JumpCityMessage |
| 10511 | C→S | BrowseCityInfoMessage |
| 10512 | C→S | GetCityMoneyMsg |
| 10513 | C→S | ModifyCityNameMsg |
| 10514 | C→S | ModifyCitySignMsg |
| 10517 | C→S | SetRebornMapMsg |
| 10518 | C→S | AutoMoveMsg |
| 10519 | C→S | GetNPCData |
| 10520 | C→S | GetSpriteMessage |
| 10521 | S→C | WorldDataMsg |
| 10522 | C→S | CountryGetAllMission |
| 10523 | C→S | MailNewNotice |
| 10524 | C→S | CountryMainMenu |

### 11xxx — Mail, photo, achievements, settings

| Opcode | Dir | Name |
|--------|-----|------|
| 11003 | C→S | PlayerEnchantShopStartMsg |
| 11006 | S→C | RemoteBattleNotify |
| 11007 | C→S | MailListMsg |
| 11008 | C→S | MailDetailMsg |
| 11009 | C→S | MailSendMsg |
| 11010 | C→S | MailSendGMMsg |
| 11011 | C→S | MailAttachMsg |
| 11012 | C→S | MailBackMsg |
| 11013 | C→S | MailDeleteMsg |
| 11015 | C→S | PhotoDel |
| 11016 | C→S | PhotoUpDownMsg |
| 11017 | C→S | PhotoContentMsg |
| 11018 | C→S | PhotoLoveMsg |
| 11019 | C→S | PhotoAlbumsMsg |
| 11020 | C→S | GetAlbumsListMsg |
| 11021 | C→S | PlayerCardGenerateMsg |
| 11022 | C→S | PlayerCardViewMsg |
| 11023 | C→S | AchieveGetInfo |
| 11024 | C→S | AchieveList |
| 11025 | C→S | AchieveGainReward |
| 11026 | C→S | AchieveTitleList |
| 11027 | C→S | CountryLeaveMsg |
| 11030 | C→S | PayInfoZhiFuBao |
| 11031 | C→S | MailNewNotice |
| 11034 | C→S | MailSeeItem |
| 11038 | C→S | ModifyActorNameByItem |
| 11078 | C→S | PayDescribe |
| 11163 | S→C | SettingChangeMessage |
| 11501 | C→S | GetSuitInfoMsg |
| 11503 | C→S | CombinShop |
| 11504 | C→S | CombinItem |
| 11505 | C→S | CombinConfirm |
| 11522 | C→S | PlayerEnchantShopStartMsg |
| 11523 | C→S | PlayerEnchantShopEndMsg |
| 11525 | C→S | PlayerEnchantShopItemListMsg |
| 11526 | S→C | ListPlayerMsg |

### 12xxx — Inventory, shops, battle entry

| Opcode | Dir | Name |
|--------|-----|------|
| 12001 | C→S | PlayerBagMessage |
| 12003 | C→S | BagRefreshMsg |
| 12004 | C→S | CancelBind |
| 12005 | C→S | MercenaryBuyMsg |
| 12006 | C→S | IntegralBuy |
| 12007 | C→S | PlayerShopStartMsg |
| 12008 | C→S | PlayerShopEndMsg |
| 12010 | C→S | PlayerShopItemListMsg |
| 12011 | C→S | PlayerShopBuyMsg |
| 12012 | S→C | PlayerEnterMsg |
| 12015 | C→S | IdentifyAsk |
| 12016 | C→S | ItemInfoMsg |
| 12017 | C→S | StorageListMsg |
| 12018 | C→S | StorageOperateMsg |
| 12020 | C→S | IntegralShop |
| 12021 | C→S | IntegralBuy |
| 12024 | C→S | VIPStorageListMsg |
| 12025 | C→S | VIPStorageOperateMsg |
| 12501 | C→S | EnterLocalBattle |
| 12503 | C→S | EnterRemoteBattle |
| 12505 | C→S | BattlePlan |
| 12506 | C→S | BattleUpdate |
| 12507 | C→S | PKAskMsg |
| 12508 | C→S | FightSeeInterMsg |
| 12509 | C→S | FightSeeQuitMsg |
| 12516 | C→S | SkyArenaRefresh |

### 13xxx — Team, chat, marketplace, relations

| Opcode | Dir | Name |
|--------|-----|------|
| 13001 | C→S | MonsterBookList |
| 13002 | C→S | MonsterBookDetail |
| 13501 | C→S | InviteTeamMsg |
| 13506 | C→S | SetTeamMsg |
| 13507 | C→S | LeaveTeamMsg |
| 13509 | C→S | ChatMsg |
| 13516 | C→S | GoodsSellFind |
| 13517 | C→S | GoodsSellBuy |
| 13518 | C→S | GoodsSellSubmit |
| 13519 | C→S | GoodsSellRetrieve |
| 13520 | C→S | GoodsSellList |
| 13521 | C→S | GoodsPurchaseSubmit |
| 13522 | C→S | GoodsPurchaseCancel |
| 13523 | C→S | GoodsPurchaseGetMsg |
| 13524 | C→S | GoodsPurchaseMyList |
| 13525 | C→S | GoodsPurchaseList |
| 13526 | C→S | GoodsProvideMsg |
| 13527 | C→S | GoodsAutoProvide |
| 13528 | C→S | GoodsPurchaseTypeList |
| 13529 | C→S | RelationList |
| 13530 | C→S | RelationAdd |
| 13531 | C→S | RelationDel |
| 13532 | C→S | PlayerSee |
| 13533 | C→S | RelationFly |
| 13534 | C→S | ChatSeeItem |
| 13535 | C→S | ChatSeeMission |
| 13536 | C→S | StorageListMsg |
| 13537 | C→S | RelationDelMaster |
| 13538 | C→S | PartnerFly |
| 13539 | C→S | PartnerAdd |
| 13540 | C→S | PartnerDel |
| 13555 | C→S | MarryWishList |
| 13558 | C→S | JumpCountryMessage |

### 14xxx — Skills, quests, escort, boss, arena

| Opcode | Dir | Name |
|--------|-----|------|
| 14001 | C→S | BrowseSkillShop |
| 14002 | C→S | MercenaryBuyMsg |
| 14003 | C→S | DropSkill |
| 14004 | C→S | AutoSkillMsg |
| 14006 | C→S | DropSkillOneLevel |
| 14501 | C→S | PlayerShopStartMsg |
| 14502 | C→S | TastAcceptMsg |
| 14503 | C→S | TaskDeliverMsg |
| 14504 | C→S | TaskAbandonMsg |
| 14509 | C→S | AutoMoveMsg |
| 14510 | C→S | EscortMoveMsg |
| 14511 | C→S | GoodsSellList |
| 14512 | C→S | TaskOffLineListMsg |
| 14513 | C→S | TaskOffLineActivateMsg |
| 14514 | C→S | EscortEventMsg |
| 14515 | C→S | EscortRobList |
| 14516 | C→S | EscortRob |
| 14517 | C→S | EscortListPlayer |
| 14518 | C→S | TeamBossStart |
| 14519 | C→S | TeamBossRefresh |
| 14520 | C→S | TeamBossFight |
| 14522 | C→S | TeamBossQuit |
| 14524 | C→S | TeamBossNotFight |
| 14526 | C→S | ArenaRefresh |
| 14528 | C→S | ArenaExit |
| 14535 | C→S | NewEscortRobList |
| 14542 | C→S | CountryBossRefresh |
| 14543 | C→S | CountryBossFight |
| 14544 | C→S | CountryBossQuit |
| 14546 | C→S | CountryBossNotFight |

### 15xxx — Country (guild), war, mercenary, pet

| Opcode | Dir | Name |
|--------|-----|------|
| 15001 | C→S | CountryCreateMsg |
| 15002 | C→S | BrowseCountryInfo |
| 15003 | C→S | CountryActiveMsg |
| 15004 | C→S | CountryListMsg |
| 15006 | C→S | CountryRecruitMsg |
| 15007 | C→S | CountryTaxRate |
| 15008 | C→S | CountryEnterRate |
| 15009 | C→S | JumpCountryMessage |
| 15010 | C→S | CountryInvite |
| 15011 | C→S | CountryApply |
| 15012 | C→S | CountryMemberAppleListMsg |
| 15013 | C→S | CountryApplyDealMsg |
| 15014 | C→S | CountryDelMemMsg |
| 15015 | C→S | CountryLeaveMsg |
| 15016 | C→S | CountryMemberListMsg |
| 15018 | C→S | CountryAdjustJobMessage |
| 15019 | C→S | CountryBecomeKingMessage |
| 15020 | C→S | CountryBuildingUpgradeMsg |
| 15021 | C→S | CountryBuildingRemoveMsg |
| 15022 | C→S | CountryBookMsg |
| 15023 | C→S | CountryPeopleDonate |
| 15024 | C→S | CountryStoragePut |
| 15025 | C→S | CountryStorageDel |
| 15026 | C→S | CountryStorageList |
| 15027 | C→S | CountryStorageGet |
| 15028 | C→S | CountryGetMission |
| 15029 | C→S | CountryPublishMission |
| 15030 | C→S | CountryAssingMission |
| 15031 | C→S | CountryAssignMem |
| 15032 | C→S | CountryAssignMission |
| 15033 | C→S | CountryValidMission |
| 15034 | C→S | CountryAfficheModifyMsg |
| 15036 | C→S | CountryGetAllMission |
| 15037 | C→S | CountryMainMenu |
| 15038 | C→S | CountryExchargeData |
| 15039 | C→S | CountryWarEnter |
| 15040 | C→S | CountryWarUpdate |
| 15041 | C→S | CountryWarArmyList |
| 15042 | C→S | CountryWarOperArmy |
| 15044 | C→S | CountryWarCommandList |
| 15045 | C→S | CountryWarUseCommand |
| 15046 | C→S | WarDeclareList |
| 15047 | C→S | WarDeclare |
| 15048 | C→S | DeclareAsk |
| 15049 | C→S | WarBuildList |
| 15050 | C→S | WarBuildLevel |
| 15051 | C→S | WarAnswerInfo |
| 15052 | C→S | WarBattleList |
| 15053 | C→S | WarTextInfo |
| 15054 | C→S | WarSoldierApply |
| 15055 | C→S | SoldierList |
| 15056 | C→S | SoldierDealApply |
| 15057 | C→S | CampInfo |
| 15058 | C→S | WarUnionApplyHelp |
| 15059 | C→S | DeleteSoldier |
| 15060 | C→S | WinActionInfo |
| 15061 | C→S | WinActionDo |
| 15062 | C→S | UnionCreate |
| 15063 | C→S | WarUnionList |
| 15064 | C→S | WarUnionMy |
| 15065 | C→S | WarUnionApply |
| 15066 | C→S | WarUnionWarList |
| 15067 | C→S | WarUnionDealApply |
| 15068 | C→S | WarUnionDelMember |
| 15069 | C→S | WarUnionQuit |
| 15070 | C→S | WarUnionChange |
| 15071 | C→S | WarTopPlayer |
| 15072 | C→S | WarTopCountry |
| 15073 | C→S | WarTopCountryPlayer |
| 15076 | C→S | CreateWarDeclareList |
| 15077 | C→S | CountrySetOnlineNotify |
| 15502 | C→S | PetSeeMsg |
| 15503 | C→S | MercenaryListMsg |
| 15504 | C→S | MercenaryInfoMsg |
| 15505 | C→S | MercenaryBuyMsg |
| 15506 | S→C | UserRegisterMsg |
| 15507 | C→S | MercenaryMyInfoMsg |
| 15508 | C→S | MercenarySetStatusMsg |
| 15510 | C→S | MercenaryDelMsg |
| 15511 | C→S | PetChangeName |
| 15512 | C→S | SoldierDealApply |

### 16xxx — Item shop

| Opcode | Dir | Name |
|--------|-----|------|
| 16001 | S→C | ItemShopSell |
| 16002 | C→S | StorageOperateMsg |
| 16006 | C→S | SpecialCodeMsg |

### 17xxx — Titles

| Opcode | Dir | Name |
|--------|-----|------|
| 17001 | C→S | AchieveTitleList |
| 17002 | C→S | ModifyPlayerName |

_Total: 245 opcodes recovered from the original client._
