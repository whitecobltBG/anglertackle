package com.whitecobalt.anglertackle.command;

import com.mojang.brigadier.CommandDispatcher;
import com.whitecobalt.anglertackle.fishing.FishSpecies;
import com.whitecobalt.anglertackle.fishing.FishingManager;
import com.whitecobalt.anglertackle.fishing.FishingSession;
import com.whitecobalt.anglertackle.fishing.WaterBody;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.HitResult;

import java.util.List;

/**
 * 调试命令 {@code /atl}（Angler's Tackle）。
 *
 * <p>存在的理由：模组里"竿坏了/配件没装上"这类问题，之前只能靠翻日志猜。
 * 有了这个命令，玩家自己在游戏里敲一下就能看到确切数值，
 * 我也能根据他贴回来的输出直接定位，不用来回试。
 *
 * <ul>
 *   <li>{@code /atl rod}     —— 看手上这根竿的耐久、破损判定、四个槽位装了什么</li>
 *   <li>{@code /atl pool}    —— 看"这根竿 + 这个饵"实际能钓到哪些鱼（鱼池体检）</li>
 *   <li>{@code /atl repair}  —— 修好手上的竿（把耐久回满）</li>
 * </ul>
 */
public final class AnglerCommands {

    private AnglerCommands() {
    }

    /**
     * **需要 OP 的子指令白名单** —— 用户 2026-10-11 定案：
     * "本 mod 很多指令本就是不应该有权限限制的，**除非是明显影响平衡**"。
     *
     * <p>所以默认是"**人人可用**"，只有下面这四条例外，理由逐条写在旁边：
     * <ul>
     *   <li>{@code stage} —— 把垂纶等级直接设成任意值 ⇒ 顺手解锁商店档位与考核，凭空跳过全部进度；</li>
     *   <li>{@code coins} —— 看/改鱼币（含 {@code set} / {@code add}）⇒ **凭空造钱**；</li>
     *   <li>{@code resetstock} —— 清空自己的限购计数 ⇒ 限购形同虚设（限购 1 的附魔书可以反复买）；</li>
     *   <li>{@code repair} —— 把主手钓竿耐久清零 ⇒ 跳过修竿花费（那是设计里的鱼币去处）；</li>
     *   <li>{@code grant} / {@code revoke} —— 改的是**别人**的称号（显示身份）⇒ 影响他人，
     *       而且能凭空发出"服主授予"这类称号。⚠ 同一条链上的 {@code list} / {@code use}
     *       **故意不加门槛**：看别人不等于改别人，而 {@code use} 只改自己。</li>
     *   <li>{@code send} —— 服主给**任意玩家**发信并附带鱼币（`/atl mail send`）⇒
     *       凭空给钱 + 发东西，明显影响平衡。⚠ 同一组里的 {@code mail}（列自己的信）、
     *       {@code read} / {@code claim} / {@code readall} **故意不加门槛** ——
     *       它们只作用于发起者自己（`MailService.claim(player, id)` 没有 target 参数）。</li>
     *   <li>{@code start} / {@code settle} —— 联赛的服主应急口：{@code start} 会让**全服**
     *       立刻收到邀请（并结算上一期），{@code settle} 会**立刻发一轮奖励**
     *       （鱼币 + 物品 + 经验 + 称号）⇒ 与 {@code send} 同一类"改数 + 发东西"。
     *       ⚠ 而 {@code /atl league}（看本期状态与名次）与 {@code /atl league stats}
     *       **故意不加门槛**：那是看数据 ✓（AGENTS §六）。</li>
     *   <li>{@code add} —— 加垂纶经验（{@code /atl xp add [玩家] <数量>}）⇒
     *       凭空推进等级与商店档位，与 {@code stage} 同一类"明显影响平衡"。
     *       ⚠ 它同时是 {@code /atl coins add} 的名字（两条本来都要 OP）；
     *       而 {@code /atl xp}（看等级与经验进度）**不加门槛**。</li>
     *   <li>{@code stop} —— 立刻结束并结算一场比赛（发鱼币 + 发冠军称号）⇒ 与 {@code settle} 同类。
     *       ⚠ 开赛那条叶子叫 {@code start}（**已在表里**，联赛加的）⇒ {@code /atl contest start}
     *       自动继承 OP 门槛；而 {@code /atl contest}（看本场）与 {@code last}（看上一场）不加门槛。</li>
     * </ul>
     *
     * <p>反过来，{@code rank} / {@code shop} / {@code talk} / {@code rod} / {@code pool} /
     * {@code water} / {@code codex} / {@code config list} 这些**看数据、开界面、报状态**的
     * 一律不加门槛 ✓ —— 它们既不改数也不发东西，加门槛只会让玩家查不了自己的问题
     * （{@code /atl rod} 存在的全部意义就是"玩家自己敲一下把输出贴给我"）。
     *
     * <p>⚠ 这个集合是权限的**唯一真源**：{@link #lit(String)} 按它决定挂不挂 {@code hasPermission(2)}。
     * 别在某个 {@code .then(...)} 上再手写 {@code .requires(...)} —— 那样就会出现
     * "某条指令忽然要 OP"而没人知道为什么。守着这条的是
     * {@code tools/check-command-permissions.js}（往集合里加 / 从集合里删，都必须红）。
     */
    private static final java.util.Set<String> OP_ONLY = java.util.Set.of(
            "stage",
            "coins",
            "resetstock",
            "repair",
            // 2026-10-11（称号系统）：改**别人**的显示身份 = 影响他人 ⇒ 属于"明显影响平衡"。
            // ⚠ 只有 `grant` / `revoke` 在表里；`list` / `use` **不加门槛** ——
            //   用户口径："看别人不等于改别人"，而 `use` 只改自己（与设置界面同一条纪律）。
            "grant",
            "revoke",
            // 2026-10-12（邮件箱）：`send` 是**服主给任意玩家发信 + 附带鱼币**（凭空给钱 + 发东西）
            // ⇒ 明显影响平衡。⚠ 它是 `/atl mail send` 的叶子名，当前只有这一处叫 send；
            //   将来别处若也要叫 send，会一并继承 OP 门槛 —— 这是**有意**的：宁可多要一次权限，
            //   也不要让"给任意人发钱"那条悄悄变成人人可用 ✗。
            "send",
            // 2026-10-12（联赛，docs/37）：`start` / `settle` 是**服主应急口** ——
            //   它们会让全服立刻收到邀请、或立刻发一轮奖励（发钱 + 发物品 + 发经验 + 发称号）
            //   ⇒ 与 `send` 同一类"改数 + 发东西"，明显影响平衡。
            // ⚠ 而 `/atl league`（光打这一条，看本期状态与名次）**不加门槛**：那是看数据 ✓。
            "start",
            "settle",
            // 2026-10-12（经验指令，用户："增加经验的指令"）：`add` 是**加垂纶经验**的叶子名
            //   （`/atl xp add [玩家] <数量>`）⇒ 凭空推进等级与商店档位，与 `stage` 同一类。
            // ⚠ 它是 `/atl coins add` 与 `/atl xp add` **共用的名字** —— 两条本来就都要 OP，
            //   所以这样命名没有副作用；而将来别处若也要叫 add，会一并继承 OP 门槛
            //   （与 `send` 同一个取舍：宁可多要一次权限，也不让"凭空给经验"那条变成人人可用 ✗）。
            // ⚠ `/atl xp`（光打这一条，看等级与经验进度）**故意不加门槛**：那是看数据 ✓。
            "add",
            // 2026-10-12（比赛，docs/35）：`stop` = 立刻结束并结算一场比赛（发鱼币 + 发冠军称号）
            //   ⇒ 与 `start` / `settle` 同一类"改数 + 发东西"，明显影响平衡。
            // ⚠ 开赛的叶子名是 `start` —— 它**已经在表里**（上面联赛那条），所以 `/atl contest start`
            //   自动继承 OP 门槛，不需要再加一次 ✓（这正是"权限只有一个真源"的好处）。
            // ⚠ 而 `/atl contest`（看本场状态）与 `/atl contest last`（看上一场）**不加门槛** ✓。
            "stop");

    /**
     * 建一个 {@code /atl} 的子指令节点，**权限由 {@link #OP_ONLY} 决定**。
     *
     * <p>为什么要包一层：权限如果散在十几个 {@code .then(...)} 里，就没人能一眼回答
     * "到底哪些指令要 OP"；而"某条常用指令误挂 OP"的症状是**玩家说用不了**，
     * 本地单人存档还测不出来（单人房主永远是 OP ✗）。收成一处之后，
     * 加指令想清楚一次、检查器一眼看全。
     */
    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> lit(String name) {
        return Commands.literal(name).requires(src -> !OP_ONLY.contains(name) || src.hasPermission(2));
    }

    /**
     * 设置"大鱼公告"开关（`/atl announce on|off`）—— 只改发起者自己的那份玩家数据。
     *
     * <p>⚠ 这里**没有**任何"改谁"的参数：改的永远是 `ctx.getSource()` 的那个玩家 ✓
     * （与设置界面同一条纪律：客户端提交不带 UUID，见 docs/34 §4.3）。
     */
    private static int setAnnounce(CommandSourceStack src, boolean value)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.whitecobalt.anglertackle.player.PlayerData.update(player,
                d -> d.withBigCatchAnnounce(value));
        src.sendSuccess(() -> Component.translatable("message.anglertackle.announce_set",
                Component.translatable(value
                        ? "message.anglertackle.announce_on"
                        : "message.anglertackle.announce_off")), false);
        return 1;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        registerAll(dispatcher);
        registerRank(dispatcher);
        registerTitle(dispatcher);
        registerQuest(dispatcher);
        registerMail(dispatcher);
        registerLeague(dispatcher);
        registerXp(dispatcher);
        registerContest(dispatcher);
        registerAchievements(dispatcher);   // 成就（docs/41 §7 A1，2026-10-15）
        registerTerminal(dispatcher);       // 垂纶终端（统合界面，2026-10-15）
        // ★ 2026-10-10 自检（用户报"补全里没有 rank"）：把 /atl 的**真实子命令**打进日志 ✓
        //   这样"命令到底挂上没挂上"不用再靠截图与猜测 ✓ —— 启动后读 logs/latest.log 即可定性 ✓
        dispatcher.getRoot().getChildren().stream()
                .filter(node -> "atl".equals(node.getName()))
                .findFirst()
                .ifPresent(node -> com.whitecobalt.anglertackle.AnglersTackle.LOGGER.info(
                        "[垂纶] /atl 的真实子命令 = {}", node.getChildren().stream()
                                .map(com.mojang.brigadier.tree.CommandNode::getName)
                                .sorted()
                                .toList()));
    }

    /**
     * 排行榜指令（2026-10-10 M1 ✓）—— **独立注册** ✓。
     *
     * <p>为什么不再往那条几百行的链里插 ✗：插过两次，两次都因为层级/缩进踩坑 ✓
     * （第一次嵌进子命令里、第二次被"排版替换"误伤另一处 ✗）。
     * Brigadier 对**同名 root literal** 会**合并子节点** ✓ ⇒ 独立注册一样进同一棵树 ✓，
     * 而且改动局部化 ✓、编译器与日志自检都能一眼验证 ✓。
     */
    private static void registerRank(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("rank")
                        .executes(ctx -> {
                            com.whitecobalt.anglertackle.leaderboard.RankSnapshot.sendAll(
                                    ctx.getSource().getPlayerOrException(), "");
                            return 1;
                        })
                        .then(Commands.argument("board", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .executes(ctx -> {
                                    com.whitecobalt.anglertackle.leaderboard.RankSnapshot.sendAll(
                                            ctx.getSource().getPlayerOrException(),
                                            com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "board"));
                                    return 1;
                                }))));
    }

    /**
     * 成就指令（2026-10-15，策划 `docs/41` §7 A1）。
     *
     * <pre>
     *   /atl ach                     —— 看**自己**的成就进度与已点亮清单（无权限）
     *   /atl ach grant &lt;id&gt;          —— 点亮一条（**需要 OP**：这是"发东西"，
     *                                   `grant` 已在 {@link #OP_ONLY} 里，复用既有口径）
     * </pre>
     *
     * <p>与 {@code /atl rank} / {@code /atl title} 一样**独立注册**（Brigadier 合并同名 root）。
     *
     * <p>⚠ {@code ach} 自己**可执行**（光打 = 看进度）且**不在** {@link #OP_ONLY} 里
     * ⇒ {@link #lit} 给它的谓词恒真，不会把子指令一起锁掉；真正要 OP 的是 {@code grant}
     * —— 权限只由 {@link #OP_ONLY} 一处决定（守这条的是 {@code tools/check-command-permissions.js}）。
     */
    /**
     * **垂纶终端**（统合界面；`docs/40` §10 / `docs/41` §8 第 5 项）。
     *
     * <pre>
     *   /atl terminal      —— 打开终端（无权限；用户 2026-10-15："每个板块的功能都分散在不同物品上"）
     * </pre>
     *
     * <p>终端本身**只是入口聚合器**：客户端点模块 → 发一条既有 `/atl xxx` → 对应面板自己弹出来 ✓。
     * 所以这里只需要"服务端推一个空载荷让客户端开界面"，**不新增任何业务逻辑**。
     */
    private static void registerTerminal(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("terminal").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    com.whitecobalt.anglertackle.network.ModNetwork.sendTo(player,
                            new com.whitecobalt.anglertackle.network.TerminalPayload(9));
                    return 1;
                })));
    }

    private static void registerAchievements(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("ach")
                        .executes(ctx -> showAchievements(ctx.getSource()))
                        .then(lit("grant")
                                .then(Commands.argument("id",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(AnglerCommands::grantAchievement)))));
    }

    /** `/atl ach`：自己的成就进度 + 已点亮清单（名字走语言键，服务端本地化）。 */
    private static int showAchievements(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.whitecobalt.anglertackle.player.AchievementData data =
                com.whitecobalt.anglertackle.player.PlayerData.getAchievements(player);
        // A4（docs/41 §7）：顺手把面板推给客户端 —— 聊天栏那份列表保留（`/atl ach` 既能看聊天、
        // 也会弹出界面；关掉界面后聊天记录里还留着明细）。看界面**不加门槛**（ach 不在 OP_ONLY 里）。
        com.whitecobalt.anglertackle.network.ModNetwork.syncAchievements(player);
        src.sendSuccess(() -> Component.translatable("msg.anglertackle.ach_progress",
                data.count(), com.whitecobalt.anglertackle.achievement.AchievementDef.count()), false);
        for (String id : data.unlocked()) {
            src.sendSuccess(() -> Component.literal(" · ").append(Component.translatable(
                    com.whitecobalt.anglertackle.achievement.AchievementService.NAME_PREFIX + id)), false);
        }
        return 1;
    }

    /** `/atl ach grant &lt;id&gt;`：走**唯一结算点**（幂等 + 先发信成功才置位）。 */
    private static int grantAchievement(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String id = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
        boolean first = com.whitecobalt.anglertackle.achievement.AchievementService.unlock(player, id);
        ctx.getSource().sendSuccess(() -> Component.translatable(
                first ? "msg.anglertackle.ach_granted" : "msg.anglertackle.ach_not_granted", id), false);
        return first ? 1 : 0;
    }

    /**
     * 称号指令（2026-10-11，用户："开发称号系统" / "不止自己连别人也能看到"）。
     *
     * <pre>
     *   /atl title                      —— **打开称号界面**（无权限；用户 2026-10-12："称号佩戴在哪，很难发现。"）
     *   /atl title list                 —— 看**自己**的称号（无权限）
     *   /atl title list &lt;玩家&gt;         —— 看**别人**的称号与次数（无权限，用户点名要的）
     *   /atl title use &lt;id&gt;            —— 佩戴 / 摘掉（`none` = 不佩戴；无权限，只改自己）
     *   /atl title grant &lt;玩家&gt; &lt;id&gt;   —— 服主授予（**需要 OP**，走 {@link #lit}）
     *   /atl title revoke &lt;玩家&gt; &lt;id&gt;  —— 服主收回（**需要 OP**）
     * </pre>
     *
     * <p>与 {@code /atl rank} 一样**独立注册**：Brigadier 会把同名 root literal 合并 ✓，
     * 而改动局部化以后，编译器与启动日志里那行"真实子命令"都能一眼验证 ✓。
     *
     * <p>⚠ {@code title} 这一层**自己也可执行**（光打 = 开界面），所以它走 {@link #lit} ——
     * 而 {@code "title"} **不在** {@link #OP_ONLY} 里 ⇒ {@code lit} 给它的谓词恒为真
     * ⇒ 不会向下继承、把 {@code list} / {@code use} 一起锁掉 ✓
     * （权限只由 {@link #OP_ONLY} 一处决定：{@code grant} / {@code revoke} 要 OP，
     * 其余一律不要 —— 这正是 {@code tools/check-command-permissions.js} 守的那条）。
     */
    /**
     * 委托指令（2026-10-14，策划 `docs/40` §9）：
     *
     * <pre>
     *   /atl quest   —— **打开委托面板**（无权限）
     * </pre>
     *
     * <p>与 {@code /atl title} 同一条做法：这一层**自己也可执行**（光打 = 开界面），
     * 所以走 {@link #lit} —— 而 {@code "quest"} **不在** {@link #OP_ONLY} 里
     * ⇒ 谓词恒真、不要权限 ✓（权限只由 {@link #OP_ONLY} 一处决定，
     * 守着这条的是 `tools/check-command-permissions.js`）。
     *
     * <p>⚠ 这个界面**只读**：接取在邮件里（【接下委托】按钮）、领取在邮箱里 ——
     * 指令不是"另一个真源"，只是"把服务端推来的快照画出来"的第三个入口 ✓。
     */
    private static void registerQuest(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("quest")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            com.whitecobalt.anglertackle.network.ModNetwork.syncQuests(p, "");
                            return 1;
                        })));
    }

    private static void registerTitle(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("title")
                        // 光打 /atl title = 开界面（**与界面里的按钮走同一个包**：
                        // 指令只负责"请求一份快照"，状态一律由服务端回推）
                        .executes(ctx -> openTitles(ctx.getSource()))
                        .then(lit("list")
                                .executes(ctx -> showTitles(ctx.getSource(),
                                        ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> {
                                            CommandSourceStack src = ctx.getSource();
                                            String name = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(ctx, "player");
                                            // 看别人**不需要权限** ✓，但必须是本服真实存在过的玩家 ——
                                            // 否则就成了"拿别人的名字去试探他有没有上线过"✗。
                                            ServerPlayer target = src.getServer().getPlayerList()
                                                    .getPlayerByName(name);
                                            if (target == null) {
                                                src.sendFailure(Component.translatable(
                                                        "message.anglertackle.title_no_such_player", name));
                                                return 0;
                                            }
                                            return showTitles(src, target);
                                        })))
                        .then(lit("use")
                                .then(Commands.argument("id",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String id = com.mojang.brigadier.arguments.StringArgumentType
                                                    .getString(ctx, "id");
                                            return useTitle(ctx.getSource(), player, id);
                                        })))
                        .then(lit("grant")
                                .then(Commands.argument("player",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("id",
                                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> grantTitle(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "player"),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "id"),
                                                        true)))))
                        .then(lit("revoke")
                                .then(Commands.argument("player",
                                                com.mojang.brigadier.arguments.StringArgumentType.word())
                                        .then(Commands.argument("id",
                                                        com.mojang.brigadier.arguments.StringArgumentType.word())
                                                .executes(ctx -> grantTitle(ctx.getSource(),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "player"),
                                                        com.mojang.brigadier.arguments.StringArgumentType
                                                                .getString(ctx, "id"),
                                                        false)))))));
    }

    /**
     * {@code /atl title} = 打开称号界面。
     *
     * <p>⚠ 服务端在这里只做一件事：**把全表快照推给发起者**。界面由客户端那个
     * {@code ClientPayloadHandlers.handleTitles} 打开 —— 与邮箱、图鉴、排行榜、
     * 菜谱、商店**五处入口逐字同一条做法**（指令不碰任何客户端类型，
     * 否则专用服务器会因为加载 {@code Screen} 而整个模组装不上 ✗）。
     */
    private static int openTitles(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.whitecobalt.anglertackle.network.ModNetwork.syncTitles(player, "");
        return 1;
    }

    /**
     * 联赛指令（用户 2026-10-11 定案；策划 `docs/37` §5.1）。
     *
     * <pre>
     *   /atl league            —— 本期状态 + 上一期名次（**无权限**：看数据不加门槛 ✓）
     *   /atl league stats      —— 观测指标：签收人数 / 上榜人数 / 签收后未参赛率（**无权限**）
     *   /atl league start      —— 服主应急：立刻结算上一期（若未结算）并开新一期（**需要 OP**）
     *   /atl league settle     —— 服主应急：立刻强制结算本期（**需要 OP**）
     * </pre>
     *
     * <p>⚠ 参赛的唯一入口是**邮件里的【签收】按钮**（用户原话："点击邮件之中的签收按钮自动领取
     * 邮件奖励并参与联赛"）—— 这里**故意不提供 `/atl league join`**：
     * 有这条指令就等于绕开邀请，"邀请制"当场失效 ✗。
     *
     * <p>⚠ 全都在服务端跑：`start` / `settle` 直接调 {@code LeagueService}，
     * 客户端一个字段都影响不了结果 ✓。
     */
    private static void registerLeague(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                .then(lit("league")
                        .executes(ctx -> showLeague(ctx.getSource()))
                        .then(lit("stats").executes(ctx -> showLeagueStats(ctx.getSource())))
                        .then(lit("points").executes(ctx -> showLeaguePoints(ctx.getSource())))
                        .then(lit("start").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            var d = com.whitecobalt.anglertackle.league.LeagueData.get(server);
                            if (d.seasonId > 0 && !d.settled) {
                                com.whitecobalt.anglertackle.league.LeagueService.settle(server);
                            }
                            long game = server.overworld().getGameTime();
                            int season = com.whitecobalt.anglertackle.league.LeagueService.push(
                                    server, game, System.currentTimeMillis());
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.anglertackle.league_started", season), true);
                            return 1;
                        }))
                        .then(lit("settle").executes(ctx -> {
                            var server = ctx.getSource().getServer();
                            int ranked = com.whitecobalt.anglertackle.league.LeagueService.settle(server);
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.anglertackle.league_settled", ranked), true);
                            return 1;
                        }))));
    }

    /** `/atl league points`：看自己的联赛积分（**无权限**，`docs/39` §3.3）。 */
    private static int showLeaguePoints(CommandSourceStack src) {
        var player = src.getPlayer();
        if (player == null) {
            return 0;
        }
        long pts = com.whitecobalt.anglertackle.league.LeaguePoints.balance(player);
        src.sendSuccess(() -> Component.translatable(
                "message.anglertackle.league_points", pts), false);
        return 1;
    }

    /** `/atl league`：本期状态 + 上一期名次（无权限）。 */
    private static int showLeague(CommandSourceStack src) {
        var server = src.getServer();
        java.util.List<String> lines = com.whitecobalt.anglertackle.league.LeagueService
                .statusLines(server);
        for (String line : lines) {
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "league_none" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.league_none"), false);
                case "header" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.league_header", f[1],
                        Component.translatable("league.anglertackle.tier." + f[2]),
                        Component.translatable("league.anglertackle.rule." + f[3])), false);
                case "running" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.league_running",
                        com.whitecobalt.anglertackle.league.LeagueService.ticksToText(Long.parseLong(f[1])),
                        f[2], Long.parseLong(f[3]) / com.whitecobalt.anglertackle.league.League.TICKS_PER_MINUTE),
                        false);
                case "closed" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.league_closed", f[1], f[2]), false);
                case "rank" -> {
                    String[] r = f[1].split("\\|", -1);
                    src.sendSuccess(() -> Component.literal("  " + rankPrefix(r[0]) + " " + r[1]
                            + " §7" + r[2] + "（" + r[3] + " 尾）"), false);
                }
                default -> {
                    // 认不出来的行就原样打出来（不猜、不吞）
                    src.sendSuccess(() -> Component.literal("  " + line), false);
                }
            }
        }
        return 1;
    }

    private static String rankPrefix(String rank) {
        return switch (rank) {
            case "1" -> "§6①";
            case "2" -> "§7②";
            case "3" -> "§c③";
            default -> "§8" + rank + ".";
        };
    }

    /**
     * `/atl league stats`：**观测指标**（`docs/37` §4.2 第 3 条）。
     *
     * <p>为什么值得有这条：调研提醒"签收后未参赛率"正是"参与度虚高但留存差"的早期信号 ——
     * 只看"有多少人签收"会把问题盖住（签收是点一下就有钱拿，当然人人点）。
     */
    private static int showLeagueStats(CommandSourceStack src) {
        var server = src.getServer();
        var d = com.whitecobalt.anglertackle.league.LeagueData.get(server);
        int joined = d.members.size();
        int ranked = d.ranked(com.whitecobalt.anglertackle.fishing.Tuning.leagueMinCatchesToRank()).size();
        int idle = joined - ranked;
        int invited = d.invited.size();
        src.sendSuccess(() -> Component.translatable("message.anglertackle.league_stats",
                d.seasonId, invited, joined, ranked, idle,
                joined <= 0 ? "0" : String.valueOf(Math.round(100.0 * idle / joined)),
                d.pending.size()), false);
        return 1;
    }

    /**
     * 垂纶经验指令（用户 2026-10-12："增加经验的指令"）。
     *
     * <pre>
     *   /atl xp                        —— 看自己的等级与经验进度（**无权限**：看数据不加门槛 ✓）
     *   /atl xp add &lt;数量&gt;          —— 给自己加经验（**需要 OP**）
     *   /atl xp add &lt;玩家&gt; &lt;数量&gt;   —— 给别人加经验（**需要 OP**；补偿/发奖用）
     * </pre>
     *
     * <p>⚠ 与 {@code /atl stage}（把等级直接设成 N）**是两件事**：这一条走的是**和钓鱼同一条经验流水**
     * （{@link com.whitecobalt.anglertackle.player.FishermanData#addXp} ⇒ {@code FishermanProgress.gainXp}），
     * 所以升级、**卡在评定点时不收经验**、升级后继续结算剩余经验，全都与正常游玩逐字一致 ✓。
     * 直接写 level 字段能造出"正常玩不可能出现的状态"（例如跳过了评定②却拿着 21 级），
     * 而这个指令**造不出来** —— 要跳级就用 {@code /atl stage}，它本来就存在且写明是调试口。
     *
     * <p>⚠ 卡评定点（10/20/30/40）时 {@code gainXp} **一点经验都不收**：那时必须明确告诉玩家
     * "要先去老柯那里通过评定"，否则症状就是"我加了 5000 经验怎么没动" ✗
     * —— 所以回显里带上**真正收下的点数**（{@code xpAccepted}）。
     *
     * <p>⚠ 改完必须 {@code ShopManager.sync(player)}：等级与经验显示在商店界面与 HUD 上，
     * 而 {@code FishermanProgress} 这个附件**没有注册 .sync(...)**（不自动推客户端）——
     * 不推的话表现是"界面上的等级停在旧值，实际已经升了"（与鱼币那条同类的坑）。
     */
    private static void registerXp(CommandDispatcher<CommandSourceStack> dispatcher) {
        // ⚠ 用局部变量自下而上搭（Brigadier 的泛型 + 括号嵌套写到第五层以后极易数错括号，
        //   本文件在 title 那条指令上已经因为同一个原因返工过一次）。
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> amountSelf =
                Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType
                                .integer(1, 1000000))
                        .executes(ctx -> giveXp(ctx.getSource(),
                                ctx.getSource().getPlayerOrException(),
                                com.mojang.brigadier.arguments.IntegerArgumentType
                                        .getInteger(ctx, "amount")));
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> amountOther =
                Commands.argument("player", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .then(Commands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType
                                        .integer(1, 1000000))
                                .executes(ctx -> {
                                    CommandSourceStack src = ctx.getSource();
                                    String name = com.mojang.brigadier.arguments.StringArgumentType
                                            .getString(ctx, "player");
                                    ServerPlayer target = src.getServer().getPlayerList()
                                            .getPlayerByName(name);
                                    if (target == null) {
                                        src.sendFailure(Component.translatable(
                                                "message.anglertackle.title_no_such_player", name));
                                        return 0;
                                    }
                                    return giveXp(src, target,
                                            com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(ctx, "amount"));
                                }));
        com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> add =
                lit("add").then(amountSelf).then(amountOther);
        dispatcher.register(Commands.literal("atl")
                .then(lit("xp").executes(ctx -> showXp(ctx.getSource())).then(add)));
    }

    /** `/atl xp`：等级 + 经验进度（无权限）。 */
    private static int showXp(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.whitecobalt.anglertackle.player.FishermanProgress p =
                com.whitecobalt.anglertackle.player.FishermanData.get(player);
        src.sendSuccess(() -> Component.literal(describeXp(player, p)), false);
        return 1;
    }

    /**
     * 加经验（**唯一实现**：给自己和给别人走同一段代码，两条路不会分叉）。
     *
     * <p>先算出"这一次能收下多少"（纯计算，{@code gainXp} 不改状态），
     * 再调 {@code addXp} 真正写回 —— 于是"说了加多少"与"实际加了多少"永远对得上 ✓。
     */
    private static int giveXp(CommandSourceStack src, ServerPlayer target, int amount) {
        com.whitecobalt.anglertackle.player.FishermanProgress before =
                com.whitecobalt.anglertackle.player.FishermanData.get(target);
        com.whitecobalt.anglertackle.player.FishermanProgress.Gain g = before.gainXp(amount);
        int gained = com.whitecobalt.anglertackle.player.FishermanData.addXp(target, amount);
        // 等级变了 ⇒ 把状态推给客户端（`FishermanProgress` 没注册 .sync，不推就是"界面停在旧值"）
        com.whitecobalt.anglertackle.shop.ShopManager.sync(target);
        // 等级类称号（垂纶学徒 / 名仕 …）跟着等级走：与上鱼那条路一样顺手过一遍
        try {
            com.whitecobalt.anglertackle.title.Titles.syncAuto(target);
        } catch (Throwable ignored) {
            // 称号只是锦上添花
        }
        com.whitecobalt.anglertackle.player.FishermanProgress after = g.next();
        boolean refused = g.xpAccepted() < amount;
        src.sendSuccess(() -> Component.literal("§a给 §f" + target.getName().getString()
                + "§a 加了 §f" + amount + "§a 点垂纶经验"
                + (gained > 0 ? "§a（升 §f" + gained + "§a 级）" : "")
                + (refused ? "§e —— 其中 §f" + (amount - g.xpAccepted())
                        + "§e 点没有收下：卡在评定点，要先通过评定" : "")), true);
        target.displayClientMessage(Component.literal("§6垂纶经验 +" + g.xpAccepted()
                + (gained > 0 ? "§6，升到 §fLv." + after.level() + "§6！" : "")
                + (refused ? "§7（卡在评定点，多余的经验没收下）" : "")), false);
        src.sendSuccess(() -> Component.literal(describeXp(target, after)), false);
        return 1;
    }

    /** 一行"当前等级 + 经验进度"（`/atl xp` 与加完之后的回显共用一份，不会两处不一致）。 */
    private static String describeXp(ServerPlayer player,
                                     com.whitecobalt.anglertackle.player.FishermanProgress p) {
        if (p.atGate()) {
            return "§6垂纶经验：§fLv." + p.level() + "§7 —— **卡在评定点**："
                    + "去老柯那里通过评定才能继续攒经验（"
                    + com.whitecobalt.anglertackle.player.AnglerLeveling.describe(p.level()) + "）";
        }
        if (p.maxLevel()) {
            return "§6垂纶经验：§fLv." + p.level() + "§7 —— 已满级（"
                    + com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL + "）";
        }
        int need = p.xpToNext();
        return "§6垂纶经验：§fLv." + p.level() + " §7经验 §f" + p.xp() + "/" + need
                + "§7（距下一级还差 §f" + Math.max(0, need - p.xp()) + "§7）";
    }

    /**
     * 比赛指令（用户 2026-10-11 定案；策划 `docs/35` §2 / §4）。
     *
     * <pre>
     *   /atl contest                     —— 本场状态：赛制 / 剩余时间 / 我的名次与成绩 / 前三名（**无权限**）
     *   /atl contest last                —— 上一场的名次（下一次开赛才覆盖；**无权限**）
     *   /atl contest start [分钟] [赛制]  —— 开一场（**需要 OP**：影响全服玩法）
     *   /atl contest stop                —— 立刻结束并结算（**需要 OP**）
     * </pre>
     *
     * <p>⚠ **没有报名指令**：用户定案"全员自动参加"（报名 = 玩家忘了报 / 中途加入要不要算 /
     * 旁边看着的人最扫兴，一次消掉）—— 开赛后谁钓到的鱼都自动计入 ✓。
     *
     * <p>⚠ 赛制参数用**名字**（`heaviest` / `total_weight` / `high_tier`）而不是数字：
     * 这是服主手敲的指令，敲错一个数字就会开一场自己没想要的比赛 ✗。
     */
    private static void registerContest(CommandDispatcher<CommandSourceStack> dispatcher) {
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> ruleArg =
                Commands.argument("rule", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .suggests((ctx, b) -> {
                            for (com.whitecobalt.anglertackle.contest.Contest.Rule r
                                    : com.whitecobalt.anglertackle.contest.Contest.Rule.values()) {
                                b.suggest(r.id());
                            }
                            return b.buildFuture();
                        });
        com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> minuteArg =
                Commands.argument("minutes", com.mojang.brigadier.arguments.IntegerArgumentType
                                .integer(1, 1440))
                        .executes(ctx -> startContest(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes"),
                                com.whitecobalt.anglertackle.contest.Contest.defaultRule()))
                        .then(ruleArg.executes(ctx -> startContest(ctx.getSource(),
                                com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "minutes"),
                                com.whitecobalt.anglertackle.contest.Contest.Rule.byId(
                                        com.mojang.brigadier.arguments.StringArgumentType
                                                .getString(ctx, "rule")))));
        dispatcher.register(Commands.literal("atl")
                .then(lit("contest")
                        .executes(ctx -> showContest(ctx.getSource()))
                        .then(lit("last").executes(ctx -> showContestLast(ctx.getSource())))
                        .then(lit("start")
                                .executes(ctx -> startContest(ctx.getSource(),
                                        com.whitecobalt.anglertackle.contest.Contest.defaultMinutes(),
                                        com.whitecobalt.anglertackle.contest.Contest.defaultRule()))
                                .then(minuteArg))
                        .then(lit("stop").executes(ctx -> {
                            int ranked = com.whitecobalt.anglertackle.contest.ContestService
                                    .stop(ctx.getSource().getServer());
                            if (ranked < 0) {
                                ctx.getSource().sendFailure(Component.translatable(
                                        "message.anglertackle.contest_none_running"));
                                return 0;
                            }
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.anglertackle.contest_stopped", ranked), true);
                            return 1;
                        }))));
    }

    /** 开赛（`start` 的两条路共用）。 */
    private static int startContest(CommandSourceStack src, int minutes,
                                    com.whitecobalt.anglertackle.contest.Contest.Rule rule) {
        var r = com.whitecobalt.anglertackle.contest.ContestService.start(src.getServer(), minutes, rule);
        switch (r) {
            case BUSY -> {
                long left = com.whitecobalt.anglertackle.contest.ContestService
                        .remainingTicks(src.getServer());
                src.sendFailure(Component.translatable("message.anglertackle.contest_busy",
                        left / com.whitecobalt.anglertackle.contest.Contest.TICKS_PER_MINUTE));
                return 0;
            }
            case TOO_FEW -> {
                src.sendFailure(Component.translatable("message.anglertackle.contest_too_few",
                        com.whitecobalt.anglertackle.fishing.Tuning.contestMinPlayers()));
                return 0;
            }
            default -> {
                src.sendSuccess(() -> Component.translatable("message.anglertackle.contest_started_ok",
                        minutes, Component.translatable("contest.anglertackle.rule." + rule.id())), true);
                return 1;
            }
        }
    }

    /** `/atl contest`：本场状态（**无权限**）。 */
    private static int showContest(CommandSourceStack src) {
        var server = src.getServer();
        ServerPlayer me = src.getEntity() instanceof ServerPlayer p ? p : null;
        java.util.List<String> lines = com.whitecobalt.anglertackle.contest.ContestService
                .statusLines(server, me);
        for (String line : lines) {
            String[] f = line.split("\\|", -1);
            switch (f[0]) {
                case "idle" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.contest_idle", f[1]), false);
                case "running" -> src.sendSuccess(() -> Component.translatable(
                        "message.anglertackle.contest_running",
                        Component.translatable("contest.anglertackle.rule." + f[1]), f[2], f[3]), false);
                case "mine" -> {
                    if ("-1".equals(f[1])) {
                        src.sendSuccess(() -> Component.translatable(
                                "message.anglertackle.contest_no_score"), false);
                    } else {
                        src.sendSuccess(() -> Component.translatable(
                                "message.anglertackle.contest_mine", f[1], f[2], f[3]), false);
                    }
                }
                case "top" -> src.sendSuccess(() -> Component.literal("  " + rankPrefix(f[1])
                        + " " + f[2] + " §7" + f[3]), false);
                default -> src.sendSuccess(() -> Component.literal("  " + line), false);
            }
        }
        return 1;
    }

    /** `/atl contest last`：上一场名次（**无权限**）。 */
    private static int showContestLast(CommandSourceStack src) {
        java.util.List<String> lines = com.whitecobalt.anglertackle.contest.ContestService
                .lastLines(src.getServer());
        if (lines.size() == 1 && "none".equals(lines.get(0))) {
            src.sendSuccess(() -> Component.translatable("message.anglertackle.contest_no_last"), false);
            return 1;
        }
        String[] head = lines.get(0).split("\\|", -1);
        src.sendSuccess(() -> Component.translatable("message.anglertackle.contest_last_header",
                Component.translatable("contest.anglertackle.rule."
                        + com.whitecobalt.anglertackle.contest.Contest.Rule
                                .byMode(Integer.parseInt(head[1])).id()),
                head[2]), false);
        for (int i = 1; i < lines.size(); i++) {
            String[] r = lines.get(i).split("\\|", -1);
            src.sendSuccess(() -> Component.literal("  " + rankPrefix(r[0]) + " " + r[1]
                    + " §7" + r[2] + (r[4].isEmpty() || "0".equals(r[4]) ? "" : " · " + r[4] + " kg")
                    + "（" + r[5] + " 尾）"), false);
        }
        return 1;
    }

    /** 把某个玩家的称号打成聊天栏（**别人也看得到**的那一层：无权限 ✓）。 */    private static int showTitles(CommandSourceStack src, ServerPlayer target)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        com.whitecobalt.anglertackle.player.PlayerTitles t =
                com.whitecobalt.anglertackle.title.Titles.get(target);
        boolean self = src.getEntity() instanceof ServerPlayer me && me.getUUID().equals(target.getUUID());
        // ⚠ 只数**静态 40 条**里拿到了几条：生成式实例（联赛赛季称号）随赛季无限增长，
        //   混进来会让"N / 40"这个进度读数失去意义（`docs/39` §5.3⑥）
        int staticOwned = 0;
        for (String oid : t.owned()) {
            if (com.whitecobalt.anglertackle.title.TitleDefs.byId(oid) != null) {
                staticOwned++;
            }
        }
        final int shownOwned = staticOwned;
        src.sendSuccess(() -> Component.translatable(
                self ? "message.anglertackle.title_list_self" : "message.anglertackle.title_list_other",
                self ? new Object[] {shownOwned, com.whitecobalt.anglertackle.title.TitleDefs.ALL.size()}
                     : new Object[] {target.getName().getString(), shownOwned,
                                     com.whitecobalt.anglertackle.title.TitleDefs.ALL.size()}), false);
        // 逐条列：名字带品质色（与 tooltip / 图鉴同一套色号 ✓）
        for (com.whitecobalt.anglertackle.title.TitleDefs.Def def
                : com.whitecobalt.anglertackle.title.TitleDefs.ALL) {
            if (!t.has(def.id())) {
                continue;
            }
            int n = t.countOf(def.id());
            boolean wearing = def.id().equals(t.equipped());
            src.sendSuccess(() -> Component.literal("  ")
                    .append(Component.literal(wearing ? "▶ " : "· "))
                    .append(Component.translatable(com.whitecobalt.anglertackle.title.Titles
                                    .nameKey(def.id()))
                            .withStyle(com.whitecobalt.anglertackle.item.Quality.of(def.quality()).color()))
                    .append(Component.literal(def.repeatable() && n > 1 ? " ×" + n : ""))
                    .append(Component.literal(wearing ? "（佩戴中）" : ""))
                    .append(Component.literal("  §7" + Component
                            // 带参数：生成式称号的条件带占位符（否则指令里也显示成 %s）
                            .translatable(com.whitecobalt.anglertackle.title.Titles.condKey(def.id()),
                                    com.whitecobalt.anglertackle.title.Titles.nameArgs(def.id()))
                            .getString())), false);
        }
        // 生成式实例也要列（`docs/39` §5.3⑥ 那一类漏法的第二个点：不在 ALL 里就看不见）
        for (String iid : t.owned()) {
            if (com.whitecobalt.anglertackle.title.TitleDefs.byId(iid) != null
                    || !com.whitecobalt.anglertackle.title.TitleDefs.validInstance(iid)) {
                continue;
            }
            boolean wearing = iid.equals(t.equipped());
            src.sendSuccess(() -> Component.literal("  ")
                    .append(Component.literal(wearing ? "▶ " : "· "))
                    .append(Component.translatable(com.whitecobalt.anglertackle.title.Titles.nameKey(iid),
                                    com.whitecobalt.anglertackle.title.Titles.nameArgs(iid))
                            .withStyle(com.whitecobalt.anglertackle.item.Quality
                                    .of(com.whitecobalt.anglertackle.title.TitleDefs.qualityOf(iid)).color()))
                    .append(Component.literal(wearing ? "（佩戴中）" : ""))
                    .append(Component.literal("  §7" + Component
                            .translatable(com.whitecobalt.anglertackle.title.Titles.condKey(iid),
                                    com.whitecobalt.anglertackle.title.Titles.nameArgs(iid))
                            .getString())), false);
        }
        if (t.owned().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("message.anglertackle.title_none"), false);
        }
        return 1;
    }

    /**
     * 佩戴 / 摘掉（`none` 或空 = 不佩戴）。只改发起者自己 ✓。
     *
     * <p>⚠ 判定本身在 {@link com.whitecobalt.anglertackle.title.Titles#equip} ——
     * 指令与**界面按钮**走的是同一个方法（各写一遍的话迟早分叉：
     * 一边允许用没拿到的称号，另一边不允许）。
     */
    private static int useTitle(CommandSourceStack src, ServerPlayer player, String id) {
        var r = com.whitecobalt.anglertackle.title.Titles.equip(player, id);
        switch (r) {
            case NOT_OWNED -> {
                src.sendFailure(Component.translatable("message.anglertackle.title_not_owned", id));
                return 0;
            }
            case UNKNOWN -> {
                src.sendFailure(Component.translatable("message.anglertackle.title_unknown_id", id));
                return 0;
            }
            case OK -> {
                // 落到这里只有一种情况：摘掉（id 为空/none）
                if (id == null || id.isEmpty() || "none".equalsIgnoreCase(id)) {
                    src.sendSuccess(() -> Component.translatable(
                            "message.anglertackle.title_unequipped"), false);
                } else {
                    src.sendSuccess(() -> Component.translatable("message.anglertackle.title_equipped",
                            Component.translatable(com.whitecobalt.anglertackle.title.Titles
                                    .nameKey(id))), false);
                }
                return 1;
            }
            default -> {
                return 0;
            }
        }
    }

    /** 服主授予 / 收回（**需要 OP** —— 权限由 {@link #lit} 统一决定）。 */
    private static int grantTitle(CommandSourceStack src, String playerName, String id, boolean give) {
        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            src.sendFailure(Component.translatable("message.anglertackle.title_no_such_player", playerName));
            return 0;
        }
        if (com.whitecobalt.anglertackle.title.TitleDefs.byId(id) == null) {
            src.sendFailure(Component.translatable("message.anglertackle.title_unknown_id", id));
            return 0;
        }
        if (give) {
            com.whitecobalt.anglertackle.title.Titles.grant(target, id);
        } else {
            com.whitecobalt.anglertackle.player.PlayerData.updateTitles(target, t -> {
                java.util.Set<String> owned = new java.util.HashSet<>(t.owned());
                owned.remove(id);
                String eq = id.equals(t.equipped()) ? "" : t.equipped();
                return new com.whitecobalt.anglertackle.player.PlayerTitles(
                        java.util.Set.copyOf(owned), t.counts(), eq, t.progress());
            });
        }
        src.sendSuccess(() -> Component.translatable(
                give ? "message.anglertackle.title_granted" : "message.anglertackle.title_revoked",
                target.getName().getString(), id), true);
        return 1;
    }
    private static void registerAll(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atl")
                // ---- /atl shop：把商店规则在聊天栏里全打印出来 ----
                //
                // 为什么先做命令而不是界面：界面（Menu/Screen/分页）是一大块工程，
                // 而"解锁判定对不对、限购会不会卡住、价格算得对不对"这些**跟界面无关**。
                // 用命令先把规则跑通，界面就只剩"把同一份数据画出来"。
                // 而且这是唯一能在没有界面时验证规则的手段。
                .then(lit("shop")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.whitecobalt.anglertackle.shop.ShopDebug.dump(player);
                            return 1;
                        })
                        .then(lit("talk").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.whitecobalt.anglertackle.player.FishermanData.update(
                                    player, com.whitecobalt.anglertackle.player.FishermanProgress::withTalked);
                            player.displayClientMessage(
                                    Component.literal("§a已标记「跟老柯说过话」，商店开启"), false);
                            return 1;
                        }))
                        // 2026-10-02：等级改成 1–50 之后，这个调试命令改成"设垂纶等级"。
                        // 商店档位不再单独设 —— 它由等级推导（AnglerLeveling.stageFor），
                        // 能单独改档位的话就会出现"改了档位但等级对不上"的假象。
                .then(lit("stage")
                                        .then(Commands.argument("n", com.mojang.brigadier.arguments.IntegerArgumentType.integer(
                                                1, com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            int n = com.mojang.brigadier.arguments.IntegerArgumentType
                                                    .getInteger(ctx, "n");
                                            com.whitecobalt.anglertackle.player.FishermanProgress old =
                                                    com.whitecobalt.anglertackle.player.FishermanData.get(player);
                                            com.whitecobalt.anglertackle.player.FishermanData.set(player,
                                                    new com.whitecobalt.anglertackle.player.FishermanProgress(
                                                            n, 0, true, old.stock()));
                                            player.displayClientMessage(
                                                    Component.literal("§a垂纶等级设为 §fLv." + n
                                                            + "§a（商店档位 "
                                                            + com.whitecobalt.anglertackle.player.AnglerLeveling
                                                                    .stageFor(n) + "）"), false);
                                            return 1;
                                        })))
                        .then(lit("resetstock").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            com.whitecobalt.anglertackle.player.FishermanData.update(player,
                                    p -> new com.whitecobalt.anglertackle.player.FishermanProgress(
                                            p.level(), p.xp(), p.talked(), java.util.Map.of()));
                            player.displayClientMessage(Component.literal("§a限购计数已清零"), false);
                            return 1;
                        })))
                .then(lit("rod").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();
                    if (!(stack.getItem() instanceof RodItem)) {
                        player.displayClientMessage(
                                Component.literal("§c主手拿的不是垂纶的钓竿"), false);
                        return 0;
                    }

                    player.displayClientMessage(Component.literal("§6=== 垂纶调试 ==="), false);
                    player.displayClientMessage(Component.literal("§7物品：§f"
                            + stack.getHoverName().getString()), false);
                    player.displayClientMessage(Component.literal("§7" + RodItem.durabilityInfo(stack)), false);

                    ModDataComponents.RodParts parts = stack.get(ModDataComponents.ROD_PARTS.get());
                    if (parts == null) {
                        player.displayClientMessage(
                                Component.literal("§7配件组件：§c未初始化（null）"), false);
                    } else {
                        player.displayClientMessage(Component.literal("§7配件组件："), false);
                        // 每个槽位连**耐久**一起显示。
                        // 用户报过"上鱼后耐久扣了，打开背包又回满"——那是服务端/客户端不同步。
                        // 这一行打的是**服务端权威值**，用来分辨"真没存住"还是"只是没显示"。
                        player.displayClientMessage(Component.literal(
                                "§7  鱼线：§f" + partWithDurability(parts.lineStack())), false);
                        player.displayClientMessage(Component.literal(
                                "§7  渔轮：§f" + partWithDurability(parts.reelStack())), false);
                        player.displayClientMessage(Component.literal(
                                "§7  铅锤：§f" + partWithDurability(parts.hookStack())), false);
                        player.displayClientMessage(Component.literal(
                                "§7  拟饵：§f" + partWithDurability(parts.lureStack())), false);
                        player.displayClientMessage(Component.literal(
                                "§7  鱼饵：§f" + partWithDurability(parts.baitStack())), false);
                    }

                    player.displayClientMessage(Component.literal("§7会话：§f"
                            + FishingManager.get().stateName(player)), false);
                    // 上鱼判定快照：只在对抗中才有值。
                    // "蓝条满却不上鱼"这类问题全靠这一行定位。
                    for (String line : FishingManager.get().landCheckInfo(player) == null
                            ? new String[]{"§8（不在对抗中，无上鱼判定数据）"}
                            : FishingManager.get().landCheckInfo(player).split("\n")) {
                        player.displayClientMessage(Component.literal(line), false);
                    }
                    return 1;
                }))
                .then(lit("pool").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (Component line : poolReport(player)) {
                        player.displayClientMessage(line, false);
                    }
                    return 1;
                }))
                // 水体判定（含三个叠加修饰）。用户 2026-10-08 定的第 0 批验收手段：
                // "调试命令能打印'这片水判成了什么'"。
                .then(lit("water").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    for (Component line : waterReport(player)) {
                        player.displayClientMessage(line, false);
                    }
                    return 1;
                }))
                // ---------------------------------------------------------- 调参
                // 开发迭代用：改完配置文件不用重启，敲一下 reload 就生效。
                // 手感调参的循环是"改数值 → 抛竿 → 观察 → 再改"，
                // 每次都要重启游戏的话一轮几分钟，根本试不动。
                .then(lit("config")
                        .then(lit("reload").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            int n = com.whitecobalt.anglertackle.fishing.Tuning.reload();
                            if (n < 0) {
                                player.displayClientMessage(Component.literal(
                                        "§c热重载失败（见日志）。配置改动需要重启游戏才生效。"), false);
                                return 0;
                            }
                            player.displayClientMessage(Component.literal(
                                    "§a配置已热重载：应用 §f" + n + " §a项。§7下一条鱼就用新参数。"), false);
                            return 1;
                        }))
                        .then(lit("list").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            for (Component line : tuningReport(ctx.getSource())) {
                                player.displayClientMessage(line, false);
                            }
                            return 1;
                        })))
                // ---------------------------------------------------------- 鱼币
                //
                // 鱼币是**玩家数据**（附件），不是物品 —— 用户明确要求"单独一个数据面板，
                // 不要搞物品化"。所以查看/增减只能走指令或界面，背包里找不到它。
                //
                // 需要 OP：这是能凭空造钱的调试入口，普通玩家不该有。
                .then(lit("coins")
                                .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            player.displayClientMessage(Component.literal(
                                    "§6鱼币：§f" + com.whitecobalt.anglertackle.player.PlayerData
                                            .get(player).coins() + " ¤"), false);
                            return 1;
                        })
                        .then(lit("set")
                                .then(Commands.argument("amount", com.mojang.brigadier.arguments.LongArgumentType.longArg(0))
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            long n = com.mojang.brigadier.arguments.LongArgumentType
                                                    .getLong(ctx, "amount");
                                            com.whitecobalt.anglertackle.player.PlayerData.update(
                                                    player, d -> d.withCoins(n));
                                            com.whitecobalt.anglertackle.network.ModNetwork.syncCoins(player);
                                            player.displayClientMessage(Component.literal(
                                                    "§a鱼币已设为 §f" + n + " ¤"), false);
                                            return 1;
                                        })))
                        .then(lit("add")
                                .then(Commands.argument("delta", com.mojang.brigadier.arguments.LongArgumentType.longArg())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            long n = com.mojang.brigadier.arguments.LongArgumentType
                                                    .getLong(ctx, "delta");
                                            var after = com.whitecobalt.anglertackle.player.PlayerData.update(
                                                    player, d -> d.addCoins(n));
                                            com.whitecobalt.anglertackle.network.ModNetwork.syncCoins(player);
                                            player.displayClientMessage(Component.literal(
                                                    "§a鱼币 " + (n >= 0 ? "+" : "") + n + " → §f"
                                                            + after.coins() + " ¤"), false);
                                            return 1;
                                        }))))
                // ---------------------------------------------------------- 鱼类图鉴
                //
                // 先做成指令，界面（图鉴书）随后接。理由：指令不依赖网络包和 Screen，
                // 是验证"上鱼记账真的写进去了"的最短路径 —— 记错了当场就能看出来，
                // 而如果先做界面，界面空白时分不清是"没记上"还是"没画出来"。
                .then(lit("codex").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    var data = com.whitecobalt.anglertackle.player.PlayerData.get(player);
                    java.util.List<com.whitecobalt.anglertackle.fishing.FishSpecies> all =
                            com.whitecobalt.anglertackle.fishing.FishSpecies.ALL;
                    int got = data.codexCount();
                    player.displayClientMessage(Component.literal(
                            "§6=== 鱼类图鉴 ===  §7已收集 §f" + got + "§7/§f" + all.size()
                                    + "§7（完成度 §f" + (got * 100 / Math.max(1, all.size())) + "%§7）"), false);
                    player.displayClientMessage(Component.literal(
                            "§7鱼币：§f" + data.coins() + " ¤"), false);

                    // 已收集的排前面，按最高档位从高到低 —— 玩家最想看的是"我钓到过什么好的"
                    // 档位显示成中文名。数字 0~5 对玩家没有意义 ——
                    // "白绿蓝紫金红"才是他天天在鱼名上看到的词，
                    // 打数字等于让玩家自己去数颜色。
                    final String[] tierCn = {"白", "绿", "蓝", "紫", "金", "红"};
                    java.util.List<String> collected = new java.util.ArrayList<>();
                    java.util.List<String> missing = new java.util.ArrayList<>();
                    for (var sp : all) {
                        var e = data.codex().get(sp.id());
                        if (e == null) {
                            // 未收集的也显示中文名：图鉴的立场是"不隐藏"，
                            // 让玩家知道还有什么在等着（docs/04 §3）。
                            // 打 id 的话等于给了个查不到的暗号。
                            missing.add(sp.item().get().getName(
                                    new ItemStack(sp.item().get())).getString());
                            continue;
                        }
                        String color = switch (e.bestTier()) {
                            case 0 -> "§f";
                            case 1 -> "§a";
                            case 2 -> "§9";
                            case 3 -> "§5";
                            case 4 -> "§6";
                            default -> "§c";
                        };
                        // 名字取物品的语言键 —— 之前直接打 sp.id()，玩家看到的是
                        // crucian_carp 这种内部 id，完全对不上游戏里那条鱼的中文名。
                        collected.add(color + sp.item().get().getName(
                                new ItemStack(sp.item().get())).getString() + " §7×" + e.count()
                                + "  最大 §f" + String.format("%.2f", e.maxKg()) + "kg"
                                + " / " + String.format("%.1f", e.maxCm()) + "cm"
                                + "  §7最高档 " + color
                                + tierCn[Math.max(0, Math.min(5, e.bestTier()))]);
                    }
                    for (String line : collected) {
                        player.displayClientMessage(Component.literal("  " + line), false);
                    }
                    if (!missing.isEmpty()) {
                        player.displayClientMessage(Component.literal(
                                "§8尚未收集 " + missing.size() + " 种："
                                        + String.join("、", missing)), false);
                    }
                    return 1;
                }))
                // ---------------------------------------------------------- 大鱼公告开关（玩家设置）
                //
                // 用户 2026-10-11："公告可以搞" + 定案 6"必须在设置界面能自由开关，
                // 且持续生效（退出重进也保留）"。
                // ⚠ **无权限门槛**：它只改**自己**的开关，不影响任何平衡（AGENTS.md §六）。
                //   设置界面（docs/34）做完之后这条指令仍然保留 —— 它是玩家报问题时
                //   让我"一条指令就能自查"的那个口子（与 /atl rod 同一个用途）。
                .then(lit("announce")
                        .executes(ctx -> {
                            boolean on = com.whitecobalt.anglertackle.player.PlayerData
                                    .get(ctx.getSource().getPlayerOrException()).bigCatchAnnounce();
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.anglertackle.announce_status",
                                    Component.translatable(on
                                            ? "message.anglertackle.announce_on"
                                            : "message.anglertackle.announce_off"),
                                    com.whitecobalt.anglertackle.fishing.Tuning.bigCatchAnnounceValue()),
                                    false);
                            return 1;
                        })
                        .then(lit("on").executes(ctx -> setAnnounce(ctx.getSource(), true)))
                        .then(lit("off").executes(ctx -> setAnnounce(ctx.getSource(), false))))
                .then(lit("repair").executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ItemStack stack = player.getMainHandItem();
                    if (!(stack.getItem() instanceof RodItem)) {
                        player.displayClientMessage(
                                Component.literal("§c主手拿的不是垂纶的钓竿"), false);
                        return 0;
                    }
                    stack.setDamageValue(0);
                    player.displayClientMessage(Component.literal("§a已修好："
                            + RodItem.durabilityInfo(stack)), false);
                    return 1;
                })));
    }

    // ================================================================== 鱼池体检
    /**
     * 算出"手上这根竿 + 装的这个饵，现在实际能钓到哪些鱼"，整理成待发送的行。
     *
     * <p>存在的理由：过滤条件有四个（竿型、竿承重、竿品质上限、饵料下限），
     * 叠加之后玩家看到的"怎么还能钓到这种鱼"光看代码很难推。
     * 把池子直接打出来，一眼就能定位是哪条规则没生效。
     *
     * <p>注意这里读的是**主手物品**，不依赖正在进行的钓鱼会话，
     * 所以岸上站着也能查。
     */
    /**
     * 玩家"正在钓的那片水"的取样位置。
     *
     * <p>顺序（每一层都有理由）：
     * <ol>
     *   <li><b>浮漂落点</b> —— 只要在钓鱼会话里，这就是**判定真正用的那个方块**
     *       （{@code FishingSession} 里就是拿 {@code bobber.blockPosition()} 判的），
     *       照抄它才能保证"指令说的"和"实际判的"是同一片水；</li>
     *   <li><b>视线命中的水</b>（24 格内）—— 岸上站着就能查"我盯着的那片水是什么"；</li>
     *   <li><b>脚下方块</b> —— 兜底，至少不会崩。</li>
     * </ol>
     */
    public static BlockPos waterProbe(ServerPlayer player) {
        FishingSession s = FishingManager.get().sessionOf(player);
        if (s != null && s.bobber() != null) {
            return s.bobber().blockPosition();
        }
        ServerLevel level = player.serverLevel();
        var from = player.getEyePosition();
        var to = from.add(player.getViewVector(1.0F).scale(24.0));
        var hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.ANY, player));
        if (hit.getType() != HitResult.Type.MISS) {
            return hit.getBlockPos();
        }
        return player.blockPosition();
    }

    /**
     * 打印"这片水判成了什么"—— 水体本体 + 三个**叠加修饰** + 判定依据。
     *
     * <p>为什么必须把依据一起打出来：修饰只从群系标签与两个阈值来，
     * 而标签引用的群系 id 在数据包那边。玩家站在雪原湖上问"这算不算高寒湖"时，
     * 只看"高寒湖"三个字是没法自查的 —— 得同时看到群系 id、基础温度、y 值和阈值。
     */
    public static java.util.List<Component> waterReport(ServerPlayer player) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        ServerLevel level = player.serverLevel();
        BlockPos pos = waterProbe(player);
        WaterBody body = WaterBody.detect(level, pos);
        int mods = WaterBody.modifiers(level, pos, body);
        Holder<Biome> biome = level.getBiome(pos);
        boolean onBobber = FishingManager.get().sessionOf(player) != null;
        out.add(Component.literal("§6=== 水体判定 ==="));
        out.add(Component.literal("§7取样点：§f" + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + "§8（" + (onBobber ? "浮漂落点 = 判定真正用的位置" : "视线/脚下") + "）"));
        out.add(Component.literal("§7群系：§f"
                + biome.unwrapKey().map(k -> k.location().toString()).orElse("（未注册）")
                + "§7  基础温度 §f" + String.format("%.2f", biome.value().getBaseTemperature())));
        out.add(Component.literal("§7水体：§f" + body.cn() + "§7（" + body.name() + "）"));
        out.add(Component.literal("§7修饰：§f" + WaterBody.describeModifiers(mods)));
        out.add(Component.literal("§8高寒湖 = 湖泊 且 y≥"
                + com.whitecobalt.anglertackle.fishing.Tuning.alpineLakeMinY()
                + "（或群系在 #anglertackle:alpine）"));
        out.add(Component.literal("§8冷水溪流 = 河流 且 温度≤"
                + String.format("%.2f", com.whitecobalt.anglertackle.fishing.Tuning.coldWaterMaxTemp())
                + "（或群系在 #anglertackle:cold_streams）"));
        out.add(Component.literal("§8极地 = 群系在 #anglertackle:ice_fishing 且不在 #anglertackle:alpine"));
        return out;
    }

    public static java.util.List<Component> poolReport(ServerPlayer player) {
        java.util.List<Component> out = new java.util.ArrayList<>();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RodItem rodItem)) {
            out.add(Component.literal("§c主手拿的不是垂纶的钓竿"));
            return out;
        }

        boolean floatRod = rodItem.rodType() == RodItem.RodType.FLOAT;
        FishSpecies.RodKind kind = floatRod ? FishSpecies.RodKind.FLOAT : FishSpecies.RodKind.LURE;

        ModDataComponents.RodParts parts = stack.get(ModDataComponents.ROD_PARTS.get());
        String equipId = parts == null ? "" : (floatRod ? parts.bait() : parts.lure());
        if (equipId == null) {
            equipId = "";
        }

        int baitTier = baitTierOf(equipId);
        // 咬钩下限：只有**路亚拟饵**有（台钓饵料按用户定案已改为只提升高价值鱼概率）
        // 档位是 0 起算（紫=3 金=4 红=5），与 FishingSession.baitMinTier 同口径。
        int minTier = floatRod ? 0 : switch (baitTier) {
            case 3 -> 1;
            case 4, 5 -> 2;
            default -> 0;
        };
        int maxTier = maxTierOf(rodItem);
        double maxWeight = rodItem.spec().maxWeightKg();
        // 高价值鱼加成：**两种竿都吃**（用户定案"金红加成本来就是写给路亚的"）
        double hv = FishingSession.targetHighValuePct(baitTier);
        // 习性词条：只有路亚拟饵认词条
        int habitMask = floatRod ? 0
                : com.whitecobalt.anglertackle.fishing.FishHabits.lureMask(equipId);

        out.add(Component.literal("§6=== 鱼池体检 ==="));
        out.add(Component.literal("§7竿型：§f" + (floatRod ? "台钓竿" : "路亚竿")));
        out.add(Component.literal("§7装" + (floatRod ? "饵" : "拟饵") + "：§f"
                + (equipId.isEmpty() ? "（空）" : equipId)));
        out.add(Component.literal("§7饵档位：§f" + (baitTier == 0 ? "白/无" : baitTier)
                + "§7（0 起算：白0 绿1 蓝2 紫3 金4 红5）"));
        out.add(Component.literal("§7咬钩下限：§f"
                + (floatRod ? "无（台钓饵料不设下限）" : minTier + "§7（" + tierName(minTier) + "）")));
        out.add(Component.literal("§7高价值鱼目标占比（金+红）：§f"
                + (hv > 0 ? String.format("%.1f%%", hv) : "无加成（用基准权重）")));
        out.add(Component.literal("§7习性词条：§f"
                + (habitMask == 0 ? "无（台钓 / 没装拟饵 / 该拟饵不认词条）"
                                  : com.whitecobalt.anglertackle.fishing.FishHabits.describe(habitMask))
                + "§7 → 命中鱼种权重 §f×1.10"));
        out.add(Component.literal("§7竿上限：§f" + maxTier + "§7（" + tierName(maxTier)
                + "）  承重上限：§f" + String.format("%.0f", maxWeight) + " §7kg"));

        // ⚠ 这里以前传的是 null —— 也就是**不按水体筛**，于是 /atl pool 打出来的池子
        //   比实际能钓到的大一圈（湖泊里会列出海水鱼）。现在按"玩家正在钓的那片水"筛，
        //   取样点和 /atl water 用的是同一个函数，两处不会各说一套。
        WaterBody water = WaterBody.detect(player.serverLevel(), waterProbe(player));
        java.util.List<FishSpecies.PoolEntry> pool = FishSpecies.buildPool(
                kind, maxTier, minTier, maxWeight, FishSpecies.CastRange.LEVEL,
                null, hv, 0, water, habitMask);
        out.add(Component.literal("§7可出鱼种（" + pool.size() + "）："));
        if (pool.isEmpty()) {
            out.add(Component.literal("§c  空池！会走兜底给鲫鱼，说明过滤条件互相打架"));
        }
        for (FishSpecies.PoolEntry e : pool) {
            FishSpecies s = e.species();
            out.add(Component.literal("§7  " + s.id()
                    + " §8档 " + s.minTier() + "~" + s.maxTier()
                    + " §8重 " + String.format("%.1f", s.kgMin()) + "~"
                    + String.format("%.0f", s.kgMax()) + "kg"
                    + " §8权重 " + e.weight()));
            // 数值明细：这些全都能在配置文件 [fishStats] 组里改，
            // 所以列出来给玩家一个"改完立刻能核对"的地方 ——
            // 否则只能靠再钓一条上来感受，反馈太慢。
            com.whitecobalt.anglertackle.fishing.FightProfile p = s.effectiveProfile();
            out.add(Component.literal(String.format(
                    "§8    拉力 %.2f（疲劳 %.2f）  体力 %.0f  张力 %.2f  "
                            + "冲刺 %.1fs/%.1fs  变向 %.1fs  假动作 %.2f  "
                            + "疲劳门槛 %.2f/%.1fs  回气 %.3f",
                    p.pullSpeed(), p.fatiguedPullSpeed(), p.stamina(), p.tensionGain(),
                    p.sprintDurationSec(), p.sprintIntervalSec(), p.turnIntervalSec(),
                    p.fakeOutChance(), p.fatigueThreshold(), p.fatigueDurationSec(),
                    p.staminaRegen())));
        }
        return out;
    }

    /**
     * 读出饵 / 拟饵的**品质档（0 起算：白=0 … 红=5）**；读不到返回 0。
     *
     * <p>⚠ 与 {@code FishingSession.equippedBaitTier()} 必须**逐字同口径**：
     * 鱼饵本来就是 0 起算，拟饵/配件是 1 起算，所以拟饵要 −1。
     * 这个换算曾经两边不一致，导致台钓整条金红占比链错一档
     * （见 {@code FishingSession.equippedBaitTier()} 的注释）。
     */
    private static int baitTierOf(String id) {
        if (id == null || id.isEmpty()) {
            return 0;
        }
        var rl = net.minecraft.resources.ResourceLocation.tryParse(id);
        if (rl == null) {
            return 0;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (item instanceof com.whitecobalt.anglertackle.item.BaitItem bait) {
            return bait.tier();
        }
        if (item instanceof com.whitecobalt.anglertackle.item.TacklePartItem part && part.isLure()) {
            var stats = new ItemStack(item).get(ModDataComponents.PART_STATS.get());
            if (stats != null && stats.partTier() > 0) {
                return stats.partTier() - 1;
            }
        }
        return 0;
    }

    /** 竿的材质档位 → 最高能出到几档（口径与 FishingSession.maxTier 一致）。 */
    private static int maxTierOf(RodItem rodItem) {
        return rodItem.spec().tier();
    }

    private static String tierName(int tier) {
        return switch (tier) {
            case 0 -> "白";
            case 1 -> "绿";
            case 2 -> "蓝";
            case 3 -> "紫";
            case 4 -> "金";
            case 5 -> "红";
            default -> "?";
        };
    }

    private static String show(String id) {
        return id == null || id.isEmpty() ? "（空）" : id;
    }

    /**
     * 打印当前生效的调参值。
     *
     * <p>为什么要有这个命令：改了配置文件之后，光看文件不知道自己改的那项
     * 到底有没有被引擎接受（拼错键名、写错分组都会静默忽略）。
     * 这里打的是**运行时真正生效的值**，和手感一一对应。
     */
    private static List<Component> tuningReport(CommandSourceStack src) {
        List<Component> out = new java.util.ArrayList<>();
        out.add(Component.literal("§6=== 垂纶 当前调参值（运行时生效值）==="));
        out.add(Component.literal("§7张力"));
        out.add(Component.literal("§7  收线涨幅 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.reelGain())
                + "§7　放线回落 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.releaseLoss())
                + "§7　自然松弛 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.idleLoss())));
        out.add(Component.literal("§7  白区 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.whiteZoneRatio() * 100)
                + "%§7　黄区 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.yellowZoneRatio() * 100)
                + "%§7　深红闪烁 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.criticalRatio() * 100) + "%"));
        out.add(Component.literal("§7  回复基准 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.baseRecover())));
        out.add(Component.literal("§7鱼的行为"));
        out.add(Component.literal("§7  常态拉力 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.pullIdle())
                + "§7　体力消耗 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.staminaDrainPerSec()) + "/秒"));
        out.add(Component.literal("§7配件合成上限"));
        out.add(Component.literal("§7  张力上限 ×§f" + f(com.whitecobalt.anglertackle.fishing.Tuning.capTensionCeiling())
                + "§7　回复 ×§f" + f(com.whitecobalt.anglertackle.fishing.Tuning.capTensionRecover())
                + "§7　体力削减 ×§f" + f(com.whitecobalt.anglertackle.fishing.Tuning.capStaminaDrain())));
        out.add(Component.literal("§7  回拉 ×§f" + f(com.whitecobalt.anglertackle.fishing.Tuning.capPull())
                + "§7　阻尼 ×§f" + f(com.whitecobalt.anglertackle.fishing.Tuning.capDamping())));
        out.add(Component.literal("§7提竿窗口 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.hookWindowBase())
                + "§7秒（下限 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.hookWindowMin()) + "§7秒）"));
        out.add(Component.literal("§7咬钩等待 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.biteMinWait())
                + "§7+随机 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.biteMaxWait())
                + "§7秒（上限 §f" + f(com.whitecobalt.anglertackle.fishing.Tuning.biteHardCap()) + "§7秒）"));
        out.add(Component.literal("§7耐久/次　竿 §f" + com.whitecobalt.anglertackle.fishing.Tuning.rodDurabilityPerBite()
                + "§7　线 §f" + com.whitecobalt.anglertackle.fishing.Tuning.lineDurabilityPerBite()
                + "§7　拟饵 §f" + com.whitecobalt.anglertackle.fishing.Tuning.lureDurabilityPerBite()
                + "§7　断线额外 §f" + com.whitecobalt.anglertackle.fishing.Tuning.lineDurabilityPerBreak()));
        out.add(Component.literal("§7窝子半径 §f" + com.whitecobalt.anglertackle.fishing.ChumManager.radius()
                + "§7格　§7全服上限 §f" + com.whitecobalt.anglertackle.fishing.ChumRegistry.cap() + "§7条"));
        // 名额占用数：多人服上"打不出窝"最常见的原因就是满了 —— 直接报出来，
        // 省得玩家以为是手里的窝料坏了（用户 2026-10-11 定的规则要看得见）
        try {
            out.add(Component.literal("§7当前占用名额 §f"
                    + com.whitecobalt.anglertackle.fishing.ChumRegistry.get(src.getServer().overworld())
                            .count(src.getServer().overworld().getGameTime())
                    + "§7/§f" + com.whitecobalt.anglertackle.fishing.ChumRegistry.cap()
                    + "§7（到期的会自己释放）"));
        } catch (Throwable ignored) {
            // 指令的自查输出绝不能因为记账读不到就整条失败
        }
        out.add(Component.literal("§8改完配置敲 /atl config reload 即可生效，不用重启。"));
        return out;
    }

    /** 数值格式化：去掉多余小数位。 */
    private static String f(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0e-9) {
            return String.valueOf((long) Math.rint(v));
        }
        return String.format("%.3f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /**
     * 槽位内容 + 耐久，例如 {@code anglertackle:hemp_line 43/50}。
     *
     * <p>没有耐久定义的配件（渔轮、铅锤，{@code partProps} 传的耐久是 0）
     * 只显示物品名，不加数字——和 HUD 的显示规则保持一致。
     */
    private static String partWithDurability(net.minecraft.world.item.ItemStack s) {
        if (s == null || s.isEmpty()) {
            return "（空）";
        }
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(s.getItem()).toString();
        if (s.getMaxDamage() <= 0) {
            return id;
        }
        return id + " §e" + (s.getMaxDamage() - s.getDamageValue()) + "/" + s.getMaxDamage();
    }

    /**
     * 邮件指令（2026-10-12，用户 2026-10-11 点名要的邮件系统；策划 `docs/36` M1）。
     *
     * <pre>
     *   /atl mail                                    —— 列自己的邮件（无权限）
     *   /atl mail read &lt;id&gt;                          —— 标记已读（无权限，只改自己）
     *   /atl mail claim &lt;id&gt;                         —— 领取附件（无权限，只改自己）
     *   /atl mail readall                            —— 全部已读（无权限）
     *   /atl mail send &lt;玩家&gt; "标题" "正文" [鱼币]     —— 服主发信（**需要 OP**，走 {@link #lit}）
     * </pre>
     *
     * <p>⚠ 标题与正文用**带引号的字符串**（`StringArgumentType.string()`）而不是 greedyString：
     * Brigadier 里**两个 greedyString 不能相邻**（第一个会把剩下的全吃掉，第二个永远收不到参数）✗。
     *
     * <p>⚠ **没有【一键全领】**（`docs/36` §8）：奖励混在一起玩家分不清哪封给了什么，
     * 也没法"先看再领"；第一版只有"全部已读"。
     *
     * <p>⚠ {@code claim} 是**服务端动作**：能不能领由 {@code MailService} 判定
     * （在不在、领没领过、背包满不满），指令/界面/以后的动作按钮走的都是它 —— 一条路 ✓。
     */
    private static void registerMail(CommandDispatcher<CommandSourceStack> dispatcher) {
        // 逐层向上搭，而不是一路点下去 —— 五层 .then(...) 的括号数过一次错一次（这次就数错了）
        var coinsArg = Commands.argument("coins",
                        com.mojang.brigadier.arguments.LongArgumentType.longArg(0L))
                .executes(ctx -> sendMail(ctx.getSource(),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "title"),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body"),
                        com.mojang.brigadier.arguments.LongArgumentType.getLong(ctx, "coins")));
        var bodyArg = Commands.argument("body",
                        com.mojang.brigadier.arguments.StringArgumentType.string())
                .executes(ctx -> sendMail(ctx.getSource(),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "player"),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "title"),
                        com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "body"),
                        0L))
                .then(coinsArg);
        var titleArg = Commands.argument("title",
                        com.mojang.brigadier.arguments.StringArgumentType.string())
                .then(bodyArg);
        var sendArg = Commands.argument("player",
                        com.mojang.brigadier.arguments.StringArgumentType.word())
                .then(titleArg);
        var readArg = Commands.argument("id",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    int id = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id");
                    boolean changed = com.whitecobalt.anglertackle.mail.MailService.read(p, id);
                    ctx.getSource().sendSuccess(() -> Component.translatable(
                            changed ? "message.anglertackle.mail_read"
                                    : "message.anglertackle.mail_read_noop", id), false);
                    return 1;
                });
        var claimArg = Commands.argument("id",
                        com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                .executes(ctx -> claimMail(ctx.getSource(),
                        ctx.getSource().getPlayerOrException(),
                        com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "id")));

        dispatcher.register(Commands.literal("atl")
                .then(lit("mail")
                        .executes(ctx -> openMail(ctx.getSource()))
                        // 纯文本版（调试台/后台用）：UI 打不开时还能看列表
                        .then(lit("list").executes(ctx -> listMails(ctx.getSource())))
                        .then(lit("read").then(readArg))
                        .then(lit("claim").then(claimArg))
                        .then(lit("readall").executes(ctx -> {
                            int n = com.whitecobalt.anglertackle.mail.MailService
                                    .readAll(ctx.getSource().getPlayerOrException());
                            ctx.getSource().sendSuccess(() -> Component.translatable(
                                    "message.anglertackle.mail_readall", n), false);
                            return 1;
                        }))
                        .then(lit("send").then(sendArg))));
    }

    /**
     * `/atl mail`：**打开邮箱界面**（界面的另一个入口是邮箱物品与图鉴上的按钮）。
     *
     * <p>做法与菜谱书/图鉴书一致：服务端只负责"把快照推过去"，开界面留在客户端专用类里
     * （common 侧写 {@code Minecraft.getInstance()} 会把客户端类拖进专用服务器 ✗）。
     */
    private static int openMail(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        com.whitecobalt.anglertackle.mail.MailService.expireAttachments(player);
        com.whitecobalt.anglertackle.network.ModNetwork.syncMails(player, "");
        return 1;
    }

    /** 列自己的邮件：一行一封（id / 状态 / 模板 / 附件摘要）—— `/atl mail list` 的纯文本版。 */
    private static int listMails(CommandSourceStack src)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = src.getPlayerOrException();
        var box = com.whitecobalt.anglertackle.player.PlayerData.getMails(player);
        src.sendSuccess(() -> Component.translatable("message.anglertackle.mail_list_header",
                box.mails().size(), box.unread(), box.claimable(),
                com.whitecobalt.anglertackle.fishing.Tuning.mailMaxMails()), false);
        for (com.whitecobalt.anglertackle.player.MailEntry m
                : com.whitecobalt.anglertackle.mail.MailService.list(player)) {
            boolean expired = com.whitecobalt.anglertackle.mail.MailService.isExpired(m);
            String state = expired ? "§8已过期"
                    : (m.claimed() ? "§7已领取" : (m.unread() ? "§e未读·待领" : "§a已读·待领"));
            String att = m.hasAttachment()
                    ? " §7[币 " + m.coins() + " / 物 " + m.items().size() + "]"
                    : "";
            int id = m.id();
            String tpl = m.template();
            src.sendSuccess(() -> Component.literal("  §7#" + id + " " + state + " §f"
                    + Component.translatable("mail.anglertackle." + tpl + ".title").getString()
                    + att + " §8(" + com.whitecobalt.anglertackle.mail.MailService.daysLeft(m)
                    + " 天后过期)"), false);
        }
        return 1;
    }

    /** 领取（服务端判定结果翻译成人话）。 */
    private static int claimMail(CommandSourceStack src, ServerPlayer player, int id) {
        var r = com.whitecobalt.anglertackle.mail.MailService.claim(player, id);
        String key = switch (r) {
            case OK -> "message.anglertackle.mail_claimed";
            case NOT_FOUND -> "message.anglertackle.mail_not_found";
            case ALREADY -> "message.anglertackle.mail_already";
            case NOTHING -> "message.anglertackle.mail_nothing";
            case EXPIRED -> "message.anglertackle.mail_expired";
            case BAG_FULL -> "message.anglertackle.mail_bag_full";
        };
        if (r == com.whitecobalt.anglertackle.mail.MailService.Claim.OK
                || r == com.whitecobalt.anglertackle.mail.MailService.Claim.NOTHING
                || r == com.whitecobalt.anglertackle.mail.MailService.Claim.ALREADY) {
            src.sendSuccess(() -> Component.translatable(key, id), false);
            return 1;
        }
        src.sendFailure(Component.translatable(key, id));
        return 0;
    }

    /** 服主发信（**需要 OP** —— 权限由 {@link #lit} 统一决定）。 */
    private static int sendMail(CommandSourceStack src, String playerName, String title,
                                String body, long coins) {
        ServerPlayer target = src.getServer().getPlayerList().getPlayerByName(playerName);
        if (target == null) {
            src.sendFailure(Component.translatable("message.anglertackle.title_no_such_player", playerName));
            return 0;
        }
        var r = com.whitecobalt.anglertackle.mail.MailService.sendAdmin(target, title, body, coins);
        if (r == com.whitecobalt.anglertackle.mail.MailService.Result.BODY_TOO_LONG) {
            src.sendFailure(Component.translatable("message.anglertackle.mail_body_too_long",
                    com.whitecobalt.anglertackle.fishing.Tuning.mailBodyMaxChars()));
            return 0;
        }
        if (r == com.whitecobalt.anglertackle.mail.MailService.Result.BOX_FULL) {
            src.sendFailure(Component.translatable("message.anglertackle.mail_target_full",
                    target.getName().getString(),
                    com.whitecobalt.anglertackle.fishing.Tuning.mailMaxMails()));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("message.anglertackle.mail_sent",
                target.getName().getString(), coins), true);
        return 1;
    }
}
