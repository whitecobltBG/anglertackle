package com.whitecobalt.anglertackle.mail;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.fishing.Tuning;
import com.whitecobalt.anglertackle.player.MailBox;
import com.whitecobalt.anglertackle.player.MailEntry;
import com.whitecobalt.anglertackle.player.PlayerData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮件系统的**服务端动作**（用户 2026-10-11 点名要的邮件箱；策划 `docs/36` M1）。
 *
 * <h2>四条纪律（都在这里落地）</h2>
 * <ol>
 *   <li><b>全部服务端读写</b>：客户端只能发"读 / 领 / 全部已读"三个动作，
 *       能不能领由这里判定（邮件在不在、领没领过、背包满不满）—— 与排行榜"服务端纯读"、
 *       玩家设置"只改自己"同一套纪律 ✓；</li>
 *   <li><b>"已读" ≠ "已领"</b>：{@link #read} 只动 read 位，{@link #claim} 才动附件；</li>
 *   <li><b>物品绝不丢</b>：背包满了就**留在邮件里**（`docs/36` §2.2）—— 先把能塞的塞进去、
 *       有一件塞不下就**把已经塞进去的回滚**并整封拒绝，绝不掉地上、绝不静默销毁 ✗；</li>
 *   <li><b>到上限拒绝投递</b>：{@code mail.maxMails} 满了就拒绝 + WARN 日志 + 告诉发信人，
 *       绝不静默丢件、也不悄悄挤掉最旧的一封 ✗。</li>
 * </ol>
 */
public final class MailService {

    private MailService() {
    }

    /** 投递结果。 */
    public enum Result {
        /** 投递成功。 */
        OK,
        /** 邮箱已满（到 {@code mail.maxMails}）—— 拒绝投递，绝不静默丢件。 */
        BOX_FULL,
        /** 正文超长（服务端侧第一道）—— 拒绝发送。 */
        BODY_TOO_LONG
    }

    /** 领取结果。 */
    public enum Claim {
        /** 领到了。 */
        OK,
        /** 没有这封邮件。 */
        NOT_FOUND,
        /** 已经领过了（幂等：第二次调用不会再发一次钱/物品）。 */
        ALREADY,
        /** 没有附件（纯通知信），没什么可领。 */
        NOTHING,
        /** 过期了，附件已作废。 */
        EXPIRED,
        /** 背包放不下 —— **物品留在邮件里**，让玩家清出格子再来。 */
        BAG_FULL
    }

    // ---------------------------------------------------------------- 派生查询
    /** 未读封数（派生值）。 */
    public static int unread(ServerPlayer player) {
        return PlayerData.getMails(player).unread();
    }

    /** 待领封数（派生值）。 */
    public static int claimable(ServerPlayer player) {
        return PlayerData.getMails(player).claimable();
    }

    /** 收件箱（按时间倒序）。 */
    public static List<MailEntry> list(ServerPlayer player) {
        return PlayerData.getMails(player).sorted();
    }

    // ---------------------------------------------------------------- 投递
    /** 发一封纯通知信（没有附件）。 */
    public static Result send(ServerPlayer to, String template, List<String> args) {
        return send(to, template, args, 0L, List.of(), "");
    }

    /**
     * 投递一封邮件。
     *
     * @param action 动作按钮 id（白名单；空 = 没有按钮）
     */
    public static Result send(ServerPlayer to, String template, List<String> args,
                              long coins, List<ItemStack> items, String action) {
        if (to == null) {
            return Result.BOX_FULL;
        }
        MailBox box = PlayerData.getMails(to);
        if (box.mails().size() >= Tuning.mailMaxMails()) {
            // ⚠ 到顶就拒绝 —— 不静默丢件、也不悄悄挤掉最旧的一封（`docs/36` §6 的断言）
            AnglersTackle.LOGGER.warn("[垂纶] 邮箱已满，拒绝投递：{} 收到 {}（上限 {} 封）",
                    to.getName().getString(), template, Tuning.mailMaxMails());
            to.displayClientMessage(Component.translatable("message.anglertackle.mail_full",
                    Tuning.mailMaxMails()).withStyle(ChatFormatting.RED), false);
            return Result.BOX_FULL;
        }
        List<ItemStack> capped = items == null ? List.of() : items.stream()
                .filter(s -> s != null && !s.isEmpty())
                .limit(MailEntry.MAX_ATTACHMENT_SLOTS)      // 界面只有 4 格
                .toList();
        boolean hasAttachment = coins > 0L || !capped.isEmpty();
        boolean hasAction = action != null && !action.isEmpty();
        MailEntry entry = new MailEntry(
                box.nextId(),
                template == null || template.isEmpty() ? "admin" : template,
                args == null ? List.of() : List.copyOf(args),
                System.currentTimeMillis(),
                false,
                // ⚠ 有动作按钮的信（如委托的 quest_accept）**初始不能算"已办完"**：
                //    动作还没执行过 ⇒ claimed=false，界面上的按钮才是可点的【接下委托】；
                //    无附件又无动作的纯通知信仍恒为已领 ✓。
                !(hasAttachment || hasAction),
                Math.max(0L, coins),
                capped,
                action == null ? "" : action);
        PlayerData.setMails(to, box.withAdded(entry));
        AnglersTackle.LOGGER.info("[垂纶] 邮件投递：{}({}) ← {}#{}（币 {} / 物品 {}）",
                to.getName().getString(), to.getUUID(), entry.template(), entry.id(),
                entry.coins(), entry.items().size());
        announce(to, entry);
        return Result.OK;
    }

    /**
     * 新信到了：**当场**告诉玩家 + 把邮箱快照推给客户端。
     *
     * <h2>⚠⚠ 为什么必须有这一步（用户 2026-10-16 报的 bug）</h2>
     * 原话："邮件在触发后（如完成成就）**不会立刻弹出**，而是在下一次登录游戏才会显示邮件寄来"。
     *
     * <p>真因：投递只写了玩家附件（服务端数据），**没有任何东西通知客户端** ——
     * 唯一的推送点是"玩家自己打开邮箱/领取"那条路（{@code MailActionPayload}）。
     * 于是界面上那封新信要等到**下次登录**（客户端重新同步）才出现，
     * 玩家完全感觉不到"奖励到账了"。这是"数据对了但玩家看不见"的典型 ✗。
     *
     * <p>两步都做，缺一不可：
     * <ol>
     *   <li><b>动作栏一行</b>（玩家不用开界面就知道有信；没有这条就只是"数据同步了"）；</li>
     *   <li><b>推一份新快照</b>（信箱界面开着时**就地**多出一行 —— 后台收信不该把界面顶掉，
     *       所以走 {@link ModNetwork#pushMailbox} 那条"只刷新不开窗"的路，
     *       而不是 {@code handleMails} 那条"没开就替你开"的路 ✗）。</li>
     * </ol>
     */
    /** 两封新信之间至少隔这么多 tick 才再弹一次**动作栏**（聊天栏那行不受限）。 */
    private static final long MAIL_TOAST_GAP_TICKS = 40L;

    /** 上一次弹动作栏的 gameTime（玩家 UUID → tick）。 */
    private static final java.util.Map<java.util.UUID, Long> mailArrivedAt = new java.util.HashMap<>();

    private static void announce(ServerPlayer to, MailEntry entry) {
        if (to == null || to.hasDisconnected()) {
            return;
        }
        // ⚠ 这一行是**可对账的证据**：用户 2026-10-16 报"邮件要等下次登录才出现"，
        //   而日志里"投递"与"提示"若不在同一毫秒级时间点，就说明通知那条路没跑。
        //   排查时直接搜「新信提示」即可（与上一行「邮件投递」并排看）。
        AnglersTackle.LOGGER.info("[垂纶] 新信提示：{}（{}）← {}#{} —— 已当场提示并推快照",
                to.getName().getString(), to.getUUID(), entry.template(), entry.id());
        Component title = Component.translatable("mail.anglertackle." + entry.template() + ".title");
        try {
            // ⚠⚠ 2026-10-16 用户复验："上了鱼也没有第一时间弹，只在后续测试时再次登录后弹出"。
            //   服务端这边**确实是当场发的**（日志里「投递」与「新信提示」同一毫秒），
            //   但只发了**动作栏**（`true`）—— 那一行会在两秒后淡出、**不进聊天记录**。
            //   玩家刚上鱼、眼睛在鱼和张力条上，错过它是必然的 ✗。
            //   ⇒ 现在**聊天栏也发一行**（false）：它会留在记录里，翻一眼就能看见，
            //     而不是"只有那两秒看得见"。
            to.displayClientMessage(Component.translatable("message.anglertackle.mail_arrived", title), false);
        } catch (Throwable t) {
            AnglersTackle.LOGGER.warn("[垂纶] 新邮件（聊天栏）提示发送失败（已忽略）：{}", t.toString());
        }
        // 动作栏那行**节流**：完成一条委托+到档会一次寄好几封，
        // 每封都弹一次动作栏等于刷屏（实测一次上鱼最多连发 3 封）。
        try {
            long now = to.serverLevel().getGameTime();
            long prev = mailArrivedAt.getOrDefault(to.getUUID(), Long.MIN_VALUE);
            if (now - prev >= MAIL_TOAST_GAP_TICKS) {
                mailArrivedAt.put(to.getUUID(), now);
                to.displayClientMessage(Component.translatable("message.anglertackle.mail_arrived", title), true);
            }
        } catch (Throwable t) {
            AnglersTackle.LOGGER.warn("[垂纶] 新邮件（动作栏）提示发送失败（已忽略）：{}", t.toString());
        }
        try {
            com.whitecobalt.anglertackle.network.ModNetwork.sendTo(to,
                    com.whitecobalt.anglertackle.mail.MailSnapshot.of(to, "", false));
        } catch (Throwable t) {
            AnglersTackle.LOGGER.warn("[垂纶] 新邮件快照推送失败（已忽略）：{}", t.toString());
        }
    }

    /**
     * 服主手动发信（`/atl mail send`）。
     *
     * <p>⚠ {@code bodyMaxChars} 在这里**拒绝**（第一道），渲染侧还会**再截断一次**（第二道）——
     * 两道都要有：只做第一道的话，改小配置后老邮件会溢出面板；只做第二道的话，服主敲进去
     * 三千字还以为发出去了 ✓。
     */
    public static Result sendAdmin(ServerPlayer to, String title, String body, long coins) {
        String t = title == null ? "" : title;
        String b = body == null ? "" : body;
        if (b.length() > Tuning.mailBodyMaxChars()) {
            return Result.BODY_TOO_LONG;
        }
        return send(to, "admin", List.of(t, b), coins, List.of(), "");
    }

    /**
     * **把"本该直接给玩家的东西"改成寄信**（`docs/36` §4.1 的 M4 改造，2026-10-12）。
     *
     * <p>四处（等级评定通知 / 图鉴里程碑 / 新手礼包 / 铁砧砂轮退件）**共用这一个入口** ——
     * 各写一遍的话，"邮箱满了怎么办"迟早每处一个答案 ✗。
     *
     * <h2>三条政策（这就是本方法存在的理由）</h2>
     * <ol>
     *   <li><b>邮箱满 ⇒ 绝不静默丢</b>：返回 {@link Result#BOX_FULL}，**由调用方决定**
     *       是推迟（不置幂等位、下次重试）还是退回原行为（掉在脚下）；
     *       本方法不替业务做选择，但**保证不吞掉失败** ✗；</li>
     *   <li><b>先投递、后置位</b>：调用方必须**先**看这里的返回值、成功了**再**写"已发"标记。
     *       反过来（先置位再发信）在满箱时会让奖励**永久拿不到**，而且不报错；</li>
     *   <li><b>物品直接进附件</b>：不做任何"转化"（不折成鱼币、不换等价物）——
     *       玩家拿到的东西与"直接塞背包"逐字相同 ✓。</li>
     * </ol>
     *
     * @param to       收件人（**在线**；离线玩家的奖励走联赛/比赛那套待投递队列）
     * @param template 模板 id（语言键 {@code mail.anglertackle.<t>.title|body|button}）
     * @param args     模板参数（**只放 id 与数字**，服务端不拼中文 ✓）
     * @param coins    附件鱼币（0 = 没有）
     * @param items    附件物品（≤ 4 格；超了会被 {@link #send} 截断，所以调用方自己数）
     * @return {@link Result#OK} = 已投递；{@link Result#BOX_FULL} = 邮箱满，**调用方必须处理**
     */
    public static Result deliver(ServerPlayer to, String template, List<String> args,
                                long coins, List<ItemStack> items) {
        return deliver(to, template, args, coins, items, "");
    }

    /**
     * 投递一封**带动作按钮**的邮件（`docs/36` §4.2；委托信 {@code quest_accept} 走这条）。
     *
     * <p>⚠ 与五参版只差一个 {@code action}。委托信是**没有附件**的动作信 ——
     * "没附件的动作必须跑得起来"这件事由 {@link #send} 的 claimed 初始值与
     * {@link #claim} 的无附件分支保证（2026-10-14 验收实测：少了这两处，
     * 按钮就是个摆设，玩家点了没反应 ✗）。
     */
    public static Result deliver(ServerPlayer to, String template, List<String> args,
                                long coins, List<ItemStack> items, String action) {
        return send(to, template, args, coins, items, action);
    }

    // ---------------------------------------------------------------- 读
    /** 标记一封邮件已读（**不领**）。返回 true = 状态真的变了。 */
    public static boolean read(ServerPlayer player, int id) {
        MailBox box = PlayerData.getMails(player);
        MailEntry m = box.byId(id);
        if (m == null || m.read()) {
            return false;
        }
        PlayerData.setMails(player, box.withReplaced(m.withRead()));
        return true;
    }

    /** 全部已读（第一版的"全部已读"按钮；**不做【一键全领】**，见 `docs/36` §8）。 */
    public static int readAll(ServerPlayer player) {
        MailBox box = PlayerData.getMails(player);
        int n = box.unread();
        if (n > 0) {
            PlayerData.setMails(player, box.withAllRead());
        }
        return n;
    }

    // ---------------------------------------------------------------- 领
    /**
     * **一键领取**：把所有"有附件且未领、未过期"的邮件一次领完。
     *
     * <p>只做循环，不重写规则：每一封都走 {@link #claim} ⇒
     * "过期作废 / 背包满整封回滚 / 鱼币同步 / 标记已领"全部与单封完全一致。
     * 碰到 {@link Claim#BAG_FULL} 就**停下**（后面的也塞不进，继续跑只会白试），
     * 已领的在前面几封里，返回计数让调用方提示"还有几封没领"。
     *
     * @return 领取结果（领了几封 / 鱼币 / 物品件数 / 是否因背包满中断）
     */
    public static ClaimAll claimAll(ServerPlayer player) {
        MailBox box = PlayerData.getMails(player);
        int mails = 0, items = 0;
        long coins = 0L;
        boolean bagFull = false;

        // 先快照 id 列表：claim() 会改写 MailBox，边遍历边改会踩到 ConcurrentModification 一类的坑
        List<Integer> ids = new ArrayList<>();
        for (MailEntry m : box.mails()) {
            if (m.hasAttachment() && !m.claimed()) {
                ids.add(m.id());
            }
        }

        for (int id : ids) {
            MailEntry before = PlayerData.getMails(player).byId(id);
            if (before == null) {
                continue;
            }
            int itemCount = before.items().size();
            long coinCount = before.coins();
            Claim r = claim(player, id);
            if (r == Claim.OK) {
                mails++;
                items += itemCount;
                coins += coinCount;
            } else if (r == Claim.BAG_FULL) {
                bagFull = true;
                break;
            }
            // NOTHING（无附件）/ ALREADY / EXPIRED / NOT_FOUND：跳过，不计数
        }
        return new ClaimAll(mails, items, coins, bagFull);
    }

    /** 一键领取的结果。 */
    public record ClaimAll(int mails, int items, long coins, boolean bagFull) {
    }


    /**
     * 领取一封邮件的附件 —— **服务端动作**，幂等。
     *
     * <p>步骤：找信 → 判过期 → 判是否已领 → 发鱼币 → 塞物品（**塞不下就整封回滚并拒绝**）→ 标记已领。
     */
    public static Claim claim(ServerPlayer player, int id) {
        MailBox box = PlayerData.getMails(player);
        MailEntry m = box.byId(id);
        if (m == null) {
            return Claim.NOT_FOUND;
        }
        if (isExpired(m)) {
            if (m.hasAttachment() || !m.claimed()) {
                PlayerData.setMails(player, box.withReplaced(m.withVoidedAttachments()));
            }
            return Claim.EXPIRED;
        }
        if (m.claimed()) {
            return Claim.ALREADY;
        }
        if (!m.hasAttachment()) {
            PlayerData.setMails(player, box.withReplaced(m.withClaimed()));
            // ⚠ 无附件但有动作的信（委托的 quest_accept）：**动作必须在这里跑** ——
            //    与有附件那条路同一顺序（先结清这封信，再执行业务）✓。
            //    少了这一行，委托按钮就是个摆设：2026-10-14 验收时实测抓到的真 bug ✗
            if (m.hasAction()) {
                MailActions.run(player, m.action());
            }
            return Claim.NOTHING;
        }

        // ---- 物品：先塞，塞不下就**把已经塞进去的拿回来**（一件都不能丢）----
        List<ItemStack> put = new ArrayList<>();
        for (ItemStack want : m.items()) {
            ItemStack copy = want.copy();
            int before = copy.getCount();
            boolean ok = player.getInventory().add(copy);
            int moved = before - copy.getCount();
            if (moved > 0) {
                ItemStack landed = want.copyWithCount(moved);
                put.add(landed);
            }
            if (!ok || copy.getCount() > 0) {
                for (ItemStack back : put) {
                    player.getInventory().removeItem(back);
                }
                return Claim.BAG_FULL;      // 邮件原样留着 ⇒ 玩家清格子后还能领 ✓
            }
        }

        // ---- 鱼币 ----
        if (m.coins() > 0L) {
            long c = m.coins();
            PlayerData.update(player, d -> d.addCoins(c));
            // ⚠ 改完鱼币**必须同步给客户端**，否则 HUD 上的数字停在旧值而实际余额是对的 ——
            //   这种不一致几乎发现不了（`tools/check-player-data.js` 专门守着这条）。
            com.whitecobalt.anglertackle.network.ModNetwork.syncCoins(player);
        }
        // ---- 标记已领（附件一起清掉）----
        MailBox now = PlayerData.getMails(player);
        MailEntry cur = now.byId(id);
        PlayerData.setMails(player, now.withReplaced((cur == null ? m : cur).withClaimed()));
        AnglersTackle.LOGGER.info("[垂纶] 邮件领取：{}({}) 领取 #{}（币 {} / 物品 {}）",
                player.getName().getString(), player.getUUID(), id, m.coins(), put.size());
        // ---- 附件领完才跑动作（`docs/36` §4.2）----
        //   ⚠ 顺序不能反：背包满时上面已经整封回滚并 return 了 ⇒ 报名也不会发生 ✓
        //   （反过来的症状是"报了名但签收礼没领到"，而且只在背包满时出现 ✗）
        if (m.hasAction()) {
            com.whitecobalt.anglertackle.mail.MailActions.run(player, m.action());
        }
        return Claim.OK;
    }

    // ---------------------------------------------------------------- 过期
    /**
     * 保管期（现实天）→ 毫秒。
     *
     * <p>⚠ 基础保管期是"普通邮件"的；**奖励类邮件另有更长的期限**（见 {@link #keepFor}）——
     * `docs/37` §6 据调研改：30 天会惩罚休假玩家（MC 生态有"14 天清数据"的实测坑），
     * 所以联赛的邀请与成绩单按 365 天保管 ✓。
     */
    public static long keepMillis() {
        return Math.max(1, Tuning.mailKeepDays()) * 24L * 60L * 60L * 1000L;
    }

    /** 这条模板的保管期（毫秒）：联赛的邀请/成绩单按"奖励类"，其余按普通。 */
    public static long keepFor(String template) {
        String t = template == null ? "" : template;
        if (t.startsWith("league_invite") || t.startsWith("league_result")) {
            return Math.max(1, Tuning.leagueRewardMailKeepDays()) * 24L * 60L * 60L * 1000L;
        }
        if (t.startsWith("league_")) {
            return Math.max(1, Tuning.leagueNoticeMailKeepDays()) * 24L * 60L * 60L * 1000L;
        }
        return keepMillis();
    }

    /** 这封信的附件过期了吗（**从 sentAt 派生**，不另存字段）。 */
    public static boolean isExpired(MailEntry m) {
        return m.sentAt() > 0L && System.currentTimeMillis() - m.sentAt() > keepFor(m.template());
    }

    /** 距过期还剩几天（负数 = 已过期）；界面用它做"快过期了"的标红。 */
    public static long daysLeft(MailEntry m) {
        if (m.sentAt() <= 0L) {
            return Tuning.mailKeepDays();
        }
        long left = m.sentAt() + keepFor(m.template()) - System.currentTimeMillis();
        return left / (24L * 60L * 60L * 1000L);
    }

    /**
     * 把过期邮件的附件作废（**正文保留**）—— 幂等，登录时与打开界面时各调一次就够。
     *
     * @return 这次作废了几封
     */
    public static int expireAttachments(ServerPlayer player) {
        MailBox box = PlayerData.getMails(player);
        int[] n = {0};
        MailBox next = box.mapAll(m -> {
            if (m.hasAttachment() && !m.claimed() && isExpired(m)) {
                n[0]++;
                return m.withVoidedAttachments();
            }
            return m;
        });
        if (n[0] > 0) {
            PlayerData.setMails(player, next);
            AnglersTackle.LOGGER.info("[垂纶] 邮件过期：{} 有 {} 封的附件已作废（正文保留）",
                    player.getName().getString(), n[0]);
        }
        return n[0];
    }
}
