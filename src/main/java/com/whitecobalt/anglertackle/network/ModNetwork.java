package com.whitecobalt.anglertackle.network;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.fishing.FishingManager;
import com.whitecobalt.anglertackle.fishing.FishingSession;
import com.whitecobalt.anglertackle.fishing.RodModManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 网络包注册与处理（**common 侧：只放 C→S**）。
 *
 * <p>方向约定（docs/06-技术方案.md §6）：
 * <ul>
 *   <li>C→S：{@link FishingInputPayload}（每 2 tick）、{@link HookPayload}、
 *       {@link RodModActionPayload}、{@link GuideRequestPayload}</li>
 *   <li>S→C：{@link SessionSyncPayload}（每 2 tick）、{@link RodModPayload}、{@link GuidePayload}
 *       …… 它们的注册与处理在 {@code client/ClientPayloadHandlers}（**客户端专用类**）</li>
 * </ul>
 *
 * <h2>⚠ 为什么 S→C 那一半不在这里（2026-10-03 专用服务器实测）</h2>
 * S→C 的处理函数要碰 {@code Minecraft} / 各种 Screen，写在这个 common 类里会让
 * **专用服务器加载本类时就去加载 {@code net.minecraft.client.gui.screens.Screen}**：
 * <pre>
 *   Attempted to load class net/minecraft/client/gui/screens/Screen for invalid dist DEDICATED_SERVER
 * </pre>
 * 触发点是对客户端类型做 {@code instanceof}（子类型检查必须把类加载进来）。
 * 结果整个模组装不进专用服务器，而**单人游戏完全看不出来**（客户端服务端同进程）。
 * 现在这个类的常量池里没有任何客户端类型 —— 改这里的时候也请保持这一点。
 */
@EventBusSubscriber(modid = AnglersTackle.MOD_ID)
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        // ---- 客户端 → 服务端 ----
        registrar.playToServer(FishingInputPayload.TYPE, FishingInputPayload.STREAM_CODEC,
                ModNetwork::handleInput);
        registrar.playToServer(HookPayload.TYPE, HookPayload.STREAM_CODEC,
                ModNetwork::handleHook);
        registrar.playToServer(RodModActionPayload.TYPE, RodModActionPayload.STREAM_CODEC,
                ModNetwork::handleRodMod);
        registrar.playToServer(CastPayload.TYPE, CastPayload.STREAM_CODEC,
                ModNetwork::handleCast);
        registrar.playToServer(GuideRequestPayload.TYPE, GuideRequestPayload.STREAM_CODEC,
                ModNetwork::handleGuideRequest);
        registrar.playToServer(CodexRequestPayload.TYPE, CodexRequestPayload.STREAM_CODEC,
                ModNetwork::handleCodexRequest);
        // 商店操作：买 / 兑换 / 等级评定。客户端只说"我想要什么"，
        // 价格和资格全部由服务端现查（见 shop/ShopManager）。
        registrar.playToServer(ShopActionPayload.TYPE, ShopActionPayload.STREAM_CODEC,
                ModNetwork::handleShopAction);
        // 菜谱：内容全是静态表，这一趟只为让服务端去开界面（见 CookbookItem 的类注释）
        registrar.playToServer(com.whitecobalt.anglertackle.network.RankRequestPayload.TYPE, com.whitecobalt.anglertackle.network.RankRequestPayload.STREAM_CODEC,
                ModNetwork::handleRankRequest);
        registrar.playToServer(CookbookRequestPayload.TYPE, CookbookRequestPayload.STREAM_CODEC,
                ModNetwork::handleCookbookRequest);
        // 邮件：打开 / 已读 / 领取 / 全部已读（**动作只作用于发起者自己**，包里没有 targetUuid）
        registrar.playToServer(MailActionPayload.TYPE, MailActionPayload.STREAM_CODEC,
                ModNetwork::handleMailAction);
        // 称号：打开 / 佩戴 / 摘下（同样只作用于发起者自己）
        registrar.playToServer(TitleActionPayload.TYPE, TitleActionPayload.STREAM_CODEC,
                ModNetwork::handleTitleAction);
    }

    /**
     * 称号动作（用户 2026-10-12："称号佩戴在哪，很难发现。"）。
     *
     * <p>⚠ 与邮件那条逐字同一条纪律：**只看 {@code context.player()}** ——
     * 包里没有 targetUuid，所以"替别人戴上称号"这件事在协议层就不存在。
     * 能不能戴（有没有拿到）由 {@code Titles.equip} 判，客户端说了不算。
     */
    private static void handleTitleAction(TitleActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String err = "";
        switch (payload.action()) {
            case TitleActionPayload.OPEN -> {
                // 打开/刷新：什么都不改，只回一份全表快照
            }
            case TitleActionPayload.EQUIP -> err = switch (com.whitecobalt.anglertackle.title.Titles
                    .equip(player, payload.titleId())) {
                case OK -> "";
                case UNKNOWN -> "unknown";
                case NOT_OWNED -> "not_owned";
            };
            case TitleActionPayload.UNEQUIP ->
                    com.whitecobalt.anglertackle.title.Titles.unequip(player);
            default -> {
                // ⚠ 动作是**白名单**：不认识的 id 一律忽略（客户端不能自定义动作）
                return;
            }
        }
        sendTo(player, com.whitecobalt.anglertackle.title.TitleSnapshot.of(player, err));
    }

    /** 把称号全表快照推给客户端（界面据此画）。 */
    public static void syncTitles(ServerPlayer player, String lastError) {
        sendTo(player, com.whitecobalt.anglertackle.title.TitleSnapshot.of(player, lastError));
    }

    /**
     * 把**委托面板快照**推给客户端（打开界面 / 接取之后；策划 `docs/40` §9）。
     *
     * <p>⚠ 这里**只发 id 与进度**，不发任何文案：委托名走语言键、目标与奖励由客户端读
     * {@code QuestPool}（纯表）现算 ⇒ 服务端一个中文字都不发 ✓
     * （`MailEntry` 的类注释写着这条纪律的由来：服务端没有语言表）。
     *
     * @param lastError 上一次动作的结果码（`""` = 成功），由界面翻成一句话
     */
    /**
     * 推一份**成就面板快照**（`docs/41` §7 A4）。每次 `/atl ach` 都会调一次 ——
     * 指令就是"请求"，所以不需要额外的 C2S 载荷 ✓。
     *
     * <p>每行五个字段：`id ⟂ 已点亮(1/0) ⟂ 难度档 ⟂ 鱼币 ⟂ 经验`（见 {@code AchievementPayload}）。
     * 总条数用 {@code AchievementDef.count()} 现算 —— **不许在别处再数一遍**（第二份真源迟早不一致）。
     */
    public static void syncAchievements(ServerPlayer player) {
        if (player == null) {
            return;
        }
        try {
            com.whitecobalt.anglertackle.player.AchievementData data =
                    com.whitecobalt.anglertackle.player.PlayerData.getAchievements(player);
            java.util.List<String> rows = new java.util.ArrayList<>();
            for (com.whitecobalt.anglertackle.achievement.AchievementDef.Def def
                    : com.whitecobalt.anglertackle.achievement.AchievementDef.all().values()) {
                rows.add(def.id()
                        + com.whitecobalt.anglertackle.network.AchievementPayload.FIELD_SEP
                        + (data.has(def.id()) ? "1" : "0")
                        + com.whitecobalt.anglertackle.network.AchievementPayload.FIELD_SEP + def.tier()
                        + com.whitecobalt.anglertackle.network.AchievementPayload.FIELD_SEP + def.coins()
                        + com.whitecobalt.anglertackle.network.AchievementPayload.FIELD_SEP + def.xp()
                        // 第 6 段：这条成就顺带授予的**既有称号 id**（没有就是空串）。
                        // ⚠ 连带改动（用户 2026-10-15 验收指出）：面板必须能告诉玩家"这条还给称号"，
                        //   否则玩家达成后只看到币，不知道称号从哪来 ✓。
                        + com.whitecobalt.anglertackle.network.AchievementPayload.FIELD_SEP
                        + (com.whitecobalt.anglertackle.achievement.AchievementDef.titleFor(def.id()) == null
                                ? "" : com.whitecobalt.anglertackle.achievement.AchievementDef.titleFor(def.id())));
            }
            sendTo(player, new com.whitecobalt.anglertackle.network.AchievementPayload(
                    rows, com.whitecobalt.anglertackle.achievement.AchievementDef.count()));
        } catch (Throwable t) {
            // 面板推失败不该影响任何玩法（与委托/称号同步同一条纪律：只读、可失败、不报错给玩家）
            com.whitecobalt.anglertackle.AnglersTackle.LOGGER.warn("[垂纶] 成就面板同步失败（已忽略）：{}", t.toString());
        }
    }

    public static void syncQuests(ServerPlayer player, String lastError) {
        if (player == null) {
            return;
        }
        try {
            com.whitecobalt.anglertackle.player.QuestData q =
                    com.whitecobalt.anglertackle.player.PlayerData.getQuests(player);
            long day = com.whitecobalt.anglertackle.quest.QuestService.dayOf(player.serverLevel());
            java.util.List<String> rows = new java.util.ArrayList<>(q.active().size());
            for (java.util.Map.Entry<String, com.whitecobalt.anglertackle.player.QuestData.Row> e
                    : q.active().entrySet()) {
                rows.add(e.getKey()
                        + com.whitecobalt.anglertackle.network.QuestPayload.FIELD_SEP + e.getValue().progress()
                        + com.whitecobalt.anglertackle.network.QuestPayload.FIELD_SEP
                        + (e.getValue().claimed() ? "1" : "0"));
            }
            java.util.List<Integer> tiers = new java.util.ArrayList<>();
            for (int t : com.whitecobalt.anglertackle.quest.QuestTiers.AT) {
                tiers.add(t);
            }
            sendTo(player, new com.whitecobalt.anglertackle.network.QuestPayload(
                    rows,
                    q.activity(),
                    com.whitecobalt.anglertackle.fishing.Tuning.questActivityCapWeekly(),
                    q.claimedTiers(),
                    tiers,
                    com.whitecobalt.anglertackle.quest.QuestService.dailyLeftMinutes(player.serverLevel()),
                    com.whitecobalt.anglertackle.quest.QuestService.weeklyLeftDays(day),
                    lastError == null ? "" : lastError));
        } catch (Throwable t) {
            // 委托面板是"看"的东西：出问题绝不该把接取 / 上鱼这些主流程带崩 ✓
            com.whitecobalt.anglertackle.AnglersTackle.LOGGER.warn(
                    "[垂纶] 推送委托面板失败（已忽略）：{}", t.toString());
        }
    }

    /**
     * 邮件动作（`docs/36` §5）—— **服务端判定**：能不能领由 {@code MailService} 说了算。
     *
     * <p>⚠ 这里**只看 {@code context.player()}**，包里那两个字段是"我要做什么 + 哪一封"，
     * 没有、也不会有"替谁做"（与排行榜"服务端纯读"、玩家设置"提交包不带 UUID"同一条纪律）。
     */
    private static void handleMailAction(MailActionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        String err = "";
        switch (payload.action()) {
            case MailActionPayload.OPEN -> {
                // 打开/刷新：顺手把过期邮件的附件作废（幂等）—— 界面上就不会出现"能点但领不到"
                com.whitecobalt.anglertackle.mail.MailService.expireAttachments(player);
            }
            case MailActionPayload.READ -> com.whitecobalt.anglertackle.mail.MailService
                    .read(player, payload.mailId());
            case MailActionPayload.CLAIM -> {
                var r = com.whitecobalt.anglertackle.mail.MailService.claim(player, payload.mailId());
                err = switch (r) {
                    case OK, NOTHING, ALREADY -> "";
                    case BAG_FULL -> "bag_full";
                    case EXPIRED -> "expired";
                    case NOT_FOUND -> "not_found";
                };
            }
            case MailActionPayload.READALL ->
                    com.whitecobalt.anglertackle.mail.MailService.readAll(player);
            case MailActionPayload.CLAIM_ALL -> {
                var all = com.whitecobalt.anglertackle.mail.MailService.claimAll(player);
                if (all.mails() > 0 || all.bagFull()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.anglertackle.mail_claim_all", all.mails(), all.coins(), all.items()), true);
                }
                if (all.bagFull()) {
                    err = "bag_full";
                }
            }
            default -> {
                // ⚠ 动作是**白名单**：不认识的 id 一律忽略（客户端不能自定义动作）
                return;
            }
        }
        sendTo(player, com.whitecobalt.anglertackle.mail.MailSnapshot.of(player, err));
    }

    /** 把邮箱快照推给客户端（界面据此画）。 */
    public static void syncMails(ServerPlayer player, String lastError) {
        sendTo(player, com.whitecobalt.anglertackle.mail.MailSnapshot.of(player, lastError));
    }

    /** 客户端想看菜谱 → 回一个空载包，由客户端专用类开界面。 */
    private static void handleCookbookRequest(CookbookRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            sendTo(player, CookbookPayload.INSTANCE);
        }
    }

    /**
     * 把余额推给客户端。
     *
     * <p>**所有**改变鱼币的地方都必须调它 —— 漏掉一处，表现就是"HUD 上的数字
     * 停在旧值不再动"，而实际余额是对的。这种偏差极难察觉，因为界面上那个数字
     * 看起来一直很合理。所以统一走这个函数，不要在别处直接发 CoinsPayload。
     */
    public static void syncCoins(ServerPlayer player) {
        sendTo(player, new CoinsPayload(
                com.whitecobalt.anglertackle.player.PlayerData.get(player).coins()));
    }

    /**
     * 把**联赛积分**推给客户端（`docs/39`）。
     *
     * <p>为什么要有它：积分是第二条货币线，而 HUD 与商店标题都只认客户端缓存 ——
     * 不推的话玩家钓了一堆鱼、面板上的积分纹丝不动，看起来就像"这功能没做"。
     *
     * <p>同 {@link #syncCoins}：**所有**改变积分的地方都必须调它。
     * 好在积分只有两个变动入口（`LeaguePoints.award` / `LeaguePoints.spend`），
     * 两处都调了 ⇒ 天然全覆盖，不会漏。
     */
    public static void syncLeaguePoints(ServerPlayer player) {
        sendTo(player, new LeaguePointsPayload(
                com.whitecobalt.anglertackle.player.PlayerData.get(player).leaguePoints()));
    }

    // =================================================================== C→S
    private static void handleInput(FishingInputPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // 提前收回优先处理：会话一旦被收回，后面的收线/提竿都不该再生效。
        // 走输入包而不是 RodItem.use()，是为了绕开物品使用状态的下降沿
        // （否则客户端会把同一次右键当成"松手抛投"，收回的瞬间又抛一杆）。
        if (payload.retrieveRequested()) {
            FishingSession s = FishingManager.get().sessionOf(player);
            if (s != null && s.retrieveEarly()) {
                return;
            }
        }
        boolean reeling = payload.primaryHeld();
        FishingManager.get().applyInput(player, reeling, !reeling, payload.direction());
        if (payload.hookRequested()) {
            FishingManager.get().tryHook(player);
        }
    }

    private static void handleHook(HookPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FishingManager.get().tryHook(player);
        }
    }

    private static void handleRodMod(RodModActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            RodModManager.handle(player, payload);
        }
    }

    /** 客户端上报"松手抛投"。服务端权威地决定距离与落点。 */
    private static void handleCast(CastPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            FishingManager.get().castByLook(player, payload.pitch());
            // 首抛（docs/41 §4 第 25 条）：这是**抛投的唯一入口**（客户端上报 → 服务端起竿），
            // 所以"第一次抛投"在这里判最准 —— 不漏、也不会把两次抛投算成一次 ✓。
            try {
                com.whitecobalt.anglertackle.achievement.AchievementService.unlock(player, "e_first_cast");
            } catch (Throwable ignored) {
                // 成就坏了绝不许让竿抛不出去
            }
        }
    }

    private static void handleGuideRequest(GuideRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            sendTo(player, GuidePayload.of(payload.chapter()));
        }
    }

    // =================================================================== S→C
    // ⚠ 这些包的处理在 client/ClientPayloadHandlers（客户端专用类），不在这里，
    //   原因见类注释（专用服务器会因为客户端类型而整个模组加载失败）。

    /**
     * 图鉴请求 → 现读这个玩家的记录，压成扁平行回发。
     *
     * <p>服务端权威：数据只从 {@code PlayerData} 来，客户端报什么都不影响结果。
     */
    private static void handleCodexRequest(CodexRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        sendTo(player, com.whitecobalt.anglertackle.fishing.CodexSnapshot.of(player));
    }

    private static void handleRankRequest(RankRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        // 纯读 ✓：只读榜、只拼文本、只发回去 ✓ —— 不接受任何"成绩"字段 ✓
        com.whitecobalt.anglertackle.leaderboard.Leaderboard.Board board =
                com.whitecobalt.anglertackle.leaderboard.Leaderboard.Board.HEAVIEST;
        String want = payload.boardId() == null ? "" : payload.boardId();
        for (com.whitecobalt.anglertackle.leaderboard.Leaderboard.Board b
                : com.whitecobalt.anglertackle.leaderboard.Leaderboard.Board.values()) {
            if (b.id().equalsIgnoreCase(want)) {
                board = b;
                break;
            }
        }
        com.whitecobalt.anglertackle.leaderboard.RankSnapshot.sendAll(player, board.id());
    }

    private static void handleShopAction(ShopActionPayload payload, IPayloadContext context) {
        // 服务端权威：客户端发来的只有"想要哪件"，价格/资格一律现查
        if (context.player() instanceof ServerPlayer player) {
            com.whitecobalt.anglertackle.shop.ShopManager.handle(player, payload.entryId());
        }
    }

    /** 服务端便捷发送。 */
    public static void sendTo(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    // ---------------------------------------------------------------- 头顶名牌
    /**
     * 把「在线玩家 → 已佩戴称号」**整表**广播给所有人（`docs/38` §2.1）。
     *
     * <p>三个调用点覆盖全部变化：① {@code Titles.equip/unequip}（唯一改佩戴的入口）
     * ② 登录 ③ 登出。发整表 ⇒ 漏一条也会被下一次广播纠正（自愈）。
     *
     * <p>⚠ 只收"现在仍然拥有"的称号；⚠ 任何异常都吞掉 —— 名牌不显示比"进服报错"轻得多。
     */
    public static void broadcastNameplates(net.minecraft.server.MinecraftServer server) {
        broadcastNameplates(server, null);
    }

    /**
     * 同上，但**跳过**某个玩家（登出时用）。
     *
     * <p>为什么要 skip：{@code PlayerLoggedOutEvent} 触发时那个玩家**有可能还在玩家列表里**
     * （原版是先发事件、后移除），不排除的话他的称号会一直留在别人的客户端上，
     * 直到下一次广播 —— 而"下一次广播"可能是几十分钟以后。
     */
    public static void broadcastNameplates(net.minecraft.server.MinecraftServer server,
                                           java.util.UUID skip) {
        if (server == null) {
            return;
        }
        try {
            StringBuilder sb = new StringBuilder();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (skip != null && skip.equals(p.getUUID())) {
                    continue;
                }
                com.whitecobalt.anglertackle.player.PlayerTitles t =
                        com.whitecobalt.anglertackle.title.Titles.get(p);
                String id = t.equipped();
                if (id == null || id.isEmpty() || !t.has(id)) {
                    continue;   // 没戴 / 戴的是个已经不该有的 id ⇒ 一行都不发 ✓
                }
                if (sb.length() > 0) {
                    sb.append(com.whitecobalt.anglertackle.network.TitleNameplatePayload.LINE_SEP);
                }
                sb.append(p.getUUID())
                        .append(com.whitecobalt.anglertackle.network.TitleNameplatePayload.FIELD_SEP)
                        .append(id);
            }
            PacketDistributor.sendToAllPlayers(new TitleNameplatePayload(sb.toString()));
        } catch (Throwable t) {
            // 名牌是锦上添花：绝不让它把进服/退服/佩戴这些主流程带崩
            com.whitecobalt.anglertackle.AnglersTackle.LOGGER.warn(
                    "[垂纶] 广播称号名牌失败（已忽略）：{}", t.toString());
        }
    }
}
