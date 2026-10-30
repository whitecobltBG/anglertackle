package com.whitecobalt.anglertackle.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import com.whitecobalt.anglertackle.client.ClientMails;
import com.whitecobalt.anglertackle.item.Quality;
import com.whitecobalt.anglertackle.network.MailActionPayload;
import com.whitecobalt.anglertackle.network.MailPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 邮箱界面（用户 2026-10-11 点名要的"清爽且有设计的邮件 UI"；策划 `docs/36` §5）。
 *
 * <h2>版面（设计尺寸 470×224，与既有 5 个界面同一档）</h2>
 * <pre>
 * ┌─ 邮箱 ──────────────────────────────── 未读 2/7 ─┐
 * │ ┌─ 收件箱 (200 宽) ─┐ ┌─ 详情 (244 宽) ────────┐ │
 * │ │▌标题        ●待领 │ │ 标题 / 发件人 / 时间    │ │
 * │ │  发件人     20:14 │ │ ───────────────────── │ │
 * │ │  …（每页 6 行）   │ │ 正文（自动折行）        │ │
 * │ │ [&lt;] 1/2 [&gt;]      │ │ [◎ 50] [物][物][ ][ ]   │ │
 * │ └──────────────────┘ │              [ 领取 ]   │ │
 * │  [全部已读]                            [ 完成 ] │
 * └─────────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>四条要守住的行为（都有断言/负例盯着）</h2>
 * <ol>
 *   <li><b>划过行什么都不做</b> ⚠（《死亡搁浅》的反例：划过就消感叹号 ⇒ 未读数莫名归零）；</li>
 *   <li><b>点行 = 选中 + 标为已读，但绝不自动领取</b>（`docs/36` §3 纪律 1）；</li>
 *   <li><b>空状态画两行字</b>，不画空列表（空列表和"加载失败"长得一模一样）；</li>
 *   <li>所有尺寸走 {@link UiLayout}（面板按窗口 clamp、行数现算、文字截断）——
 *       大窗口小窗口都装得下 ✓。</li>
 * </ol>
 *
 * <p>颜色**复用既有常量**（与 `CodexScreen` 逐字一致），品质色一律走 {@link Quality}；
 * 这里不新起色板 ✓。
 */
public class MailScreen extends Screen {

    // ---- 版面（设计尺寸）----
    private static final int PANEL_W = 470;
    private static final int PANEL_H = 224;
    private static final int MIN_W = 260;
    private static final int MIN_H = 160;
    private static final int LIST_W = 200;
    private static final int LIST_ROW_H = 24;
    private static final int HEADER_H = 18;
    private static final int FOOTER_H = 24;
    private static final int GAP = 10;
    private static final int SLOT = 18;

    // ---- 颜色（**全部复用既有常量**：与 CodexScreen 顶部那几个逐字相同）----
    //   ⚠ 不新起色板（`docs/36` §5.4）。这里额外用到的只有原版标准红 0xFFFF5555
    //   （"快过期/已过期"的警示色，MC 全局都在用它），其余一律取既有色号。
    private static final int C_TITLE = 0xFFFFD050;
    private static final int C_LABEL = 0xFF8FCBF5;
    private static final int C_TEXT = 0xFFE8E8E8;
    private static final int C_DIM = 0xFFB0B0B0;
    private static final int C_HINT = 0xFF909090;
    private static final int C_PANEL = 0xFF243028;
    private static final int C_SHADOW = 0xF0000000;
    private static final int C_LINE = 0xFF5A6A60;
    private static final int C_TRACK = 0xFF3A4A40;
    /** 未读窄条 = 既有标签蓝（不另起一个"未读色"）。 */
    private static final int C_UNREAD = C_LABEL;
    /** 待领标记 = 既有标题金。 */
    private static final int C_CLAIM = C_TITLE;
    /** 快过期/已过期 = 原版标准红。 */
    private static final int C_EXPIRE = 0xFFFF5555;

    private final List<Button> buttons = new ArrayList<>();
    private final List<TextWidget> texts = new ArrayList<>();

    private int panelW = PANEL_W;
    private int panelH = PANEL_H;
    private int left;
    private int top;
    private int rowsPerPage = 6;

    /** 选中的邮件 id（-1 = 没选）。 */
    private int selectedId = -1;
    private int page = 0;
    /**
     * 正文的滚动行偏移（用户 2026-10-12 实测："右侧文本区域显示不全，
     * 而且没有滚轮或翻页的设计用来查看后续内容"）。
     *
     * <p>⚠ 换一封邮件时必须**归零** —— 不然选中长信滚到底、再点一封短信，
     * 那封短信会从中间开始显示（看起来像"这封信开头没了"）。
     */
    private int bodyScroll = 0;

    public MailScreen() {
        super(Component.translatable("gui.anglertackle.mailbox"));
    }

    @Override
    protected void init() {
        // ★ 面板必须装得进窗口（同 CodexScreen：小窗口下 470 宽会出屏）
        panelW = UiLayout.panelW(this.width, PANEL_W, MIN_W);
        panelH = UiLayout.panelH(this.height, PANEL_H, MIN_H);
        rowsPerPage = UiLayout.rows(panelH, HEADER_H + 4, FOOTER_H, LIST_ROW_H);
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
        rebuild();
    }

    private MailPayload snap() {
        return ClientMails.latest();
    }

    // ------------------------------------------------------------------ 重建
    private void clearAll() {
        for (Button b : buttons) {
            this.removeWidget(b);
        }
        buttons.clear();
        for (TextWidget w : texts) {
            this.removeWidget(w);
        }
        texts.clear();
    }

    private void rebuild() {
        clearAll();
        MailPayload snap = snap();
        // 底部布局（2026-10-30 修"翻页键与已读重叠"）：
        //   [< 18] 6~24 | [全部已读 80] 28~108 | [一键领取 80] 112~192 | [> 18] 196~214 … 完成(右)
        //   ⚠ 翻页键原本和【全部已读】都从 left+6/+8 起，直接叠在一起（用户截图反馈）。
        //   这几组数是**互斥的区间**，改宽度时必须一起挪，守着它的是 check-mail-claim-all.js。
        int by = top + panelH - FOOTER_H + 3;
        Button all = Button.builder(Component.translatable("gui.anglertackle.mail_readall"),
                        b -> action(MailActionPayload.READALL, -1))
                .bounds(left + 28, by, 80, 18).build();
        all.active = snap != null && snap.unread() > 0;
        addRenderableWidget(all);
        buttons.add(all);
        // 一键领取：只有"还有可领的"才可点（避免空点）。位置放在【全部已读】右边。
        Button claimAll = Button.builder(Component.translatable("gui.anglertackle.mail_claim_all"),
                        b -> action(MailActionPayload.CLAIM_ALL, -1))
                .bounds(left + 112, by, 80, 18).build();
        claimAll.active = snap != null && snap.claimable() > 0;
        addRenderableWidget(claimAll);
        buttons.add(claimAll);
        Button done = Button.builder(Component.translatable("gui.anglertackle.mail_done"),
                        b -> this.onClose())
                .bounds(left + panelW - 6 - 60, by, 60, 18).build();
        addRenderableWidget(done);
        buttons.add(done);

        if (snap == null || snap.rows().isEmpty()) {
            return;   // 空状态/读取中：文字在 render() 里画
        }
        // 每页行数由窗口现算 ⇒ 上一页的页码要夹回来（否则放大窗口后停在空页）
        int pages = Math.max(1, (snap.rows().size() + rowsPerPage - 1) / rowsPerPage);
        page = Math.max(0, Math.min(page, pages - 1));
        if (pages > 1) {
            Button prev = Button.builder(Component.literal("<"), b -> {
                page = Math.max(0, page - 1);
                rebuild();
            }).bounds(left + 6, by, 18, 18).build();
            prev.active = page > 0;
            addRenderableWidget(prev);
            buttons.add(prev);
            Button next = Button.builder(Component.literal(">"), b -> {
                page = Math.min(pages - 1, page + 1);
                rebuild();
            }).bounds(left + 196, by, 18, 18).build();
            next.active = page < pages - 1;
            addRenderableWidget(next);
            buttons.add(next);
        }
        // 详情栏的主按钮（领取 / 已领取 / 背包已满 / 已过期）
        Row sel = selected();
        if (sel != null) {
            Button claim = Button.builder(claimLabel(sel), b -> action(MailActionPayload.CLAIM, sel.id))
                    .bounds(left + panelW - 6 - 70, top + panelH - FOOTER_H - 22, 70, 18).build();
            claim.active = sel.canClaim();
            addRenderableWidget(claim);
            buttons.add(claim);
        }
    }

    /**
     * 主按钮上的字。
     *
     * <p>⚠ 带**动作**的邮件（如联赛邀请）不是"领取"而是"签收"，而且签收完要显示成
     * "已签收 ✓"（`docs/36` §4.2 的界面细则）—— 所以三种状态的键都要按动作走：
     * {@code mail.anglertackle.<action>.button} / {@code .done}。
     * 这两把键**必须存在**（缺了按钮上就是一串原始键名）——
     * 守着它的是 {@code tools/check-league.js}（动作白名单 ↔ 语言键双向核对）。
     */
    private Component claimLabel(Row r) {
        if (r.expired) {
            return Component.translatable("gui.anglertackle.mail_expired");
        }
        if (r.claimed) {
            return Component.translatable(r.action.isEmpty()
                    ? "gui.anglertackle.mail_claimed"
                    : "mail.anglertackle." + r.action + ".done");
        }
        if (bagFull) {
            return Component.translatable("gui.anglertackle.mail_bag_full");
        }
        return Component.translatable(r.action.isEmpty()
                ? "gui.anglertackle.mail_claim"
                : "mail.anglertackle." + r.action + ".button");
    }

    private void action(int action, int id) {
        PacketDistributor.sendToServer(new MailActionPayload(action, id));
    }

    // ------------------------------------------------------------------ 数据行
    /** 一行邮件的解析结果（**只读投影**，见 `MailSnapshot` 的注释）。 */
    private static final class Row {
        int id;
        String template = "admin";
        List<String> args = List.of();
        long sentAt;
        boolean read;
        boolean claimed;
        long coins;
        List<ItemStack> items = List.of();
        String action = "";
        long daysLeft;
        boolean expired;
        String title = "";
        String sender = "";

        boolean canClaim() {
            // ⚠ 只有动作、没有附件的信（委托的 quest_accept）也必须可点：
            //    点它就是为了跑动作（接委托 + 开面板），不该要求有附件 ✓
            //    （2026-10-14 验收实测：老判据下委托按钮恒灰，玩家点了没反应 ✗）
            return !expired && !claimed && (coins > 0 || !items.isEmpty() || !action.isEmpty());
        }
    }

    private boolean bagFull;

    /** 解析快照里的每一行（字段顺序与 `MailSnapshot` 逐字对应）。 */
    private List<Row> parse() {
        MailPayload snap = snap();
        List<Row> out = new ArrayList<>();
        if (snap == null) {
            return out;
        }
        for (String line : snap.rows()) {
            String[] f = line.split("\\|", -1);
            if (f.length < 11) {
                continue;
            }
            Row r = new Row();
            try {
                r.id = Integer.parseInt(f[0]);
                r.template = f[1];
                r.args = f[2].isEmpty() ? List.of() : List.of(f[2].split("\u0001", -1));
                r.sentAt = Long.parseLong(f[3]);
                r.read = "1".equals(f[4]);
                r.claimed = "1".equals(f[5]);
                r.coins = Long.parseLong(f[6]);
                r.items = parseItems(f[7]);
                r.action = f[8];
                r.daysLeft = Long.parseLong(f[9]);
                r.expired = "1".equals(f[10]);
            } catch (NumberFormatException e) {
                continue;   // 坏行直接跳过：界面宁可少一行，也不要崩
            }
            r.title = titleOf(r);
            r.sender = senderOf(r.template);
            out.add(r);
        }
        return out;
    }

    private static List<ItemStack> parseItems(String field) {
        if (field == null || field.isEmpty()) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        for (String one : field.split("\u0002", -1)) {
            String[] p = one.split("\\*", -1);
            if (p.length < 2) {
                continue;
            }
            ResourceLocation rl = ResourceLocation.tryParse(p[0]);
            if (rl == null) {
                continue;
            }
            net.minecraft.world.item.Item item = BuiltInRegistries.ITEM.get(rl);
            if (item == null) {
                continue;
            }
            ItemStack st = new ItemStack(item);
            try {
                st.setCount(Math.max(1, Integer.parseInt(p[1])));
                if (p.length > 2) {
                    st.setDamageValue(Math.max(0, Integer.parseInt(p[2])));
                }
            } catch (NumberFormatException ignored) {
                // 数量/耐久坏了就用默认值 —— 图标还是要画出来
            }
            out.add(st);
        }
        return out;
    }

    /** 标题 = 语言文件里的模板标题（参数按顺序喂进去）——**服务端不拼中文** ✓。 */
    private static String titleOf(Row r) {
        try {
            return Component.translatable("mail.anglertackle." + r.template + ".title",
                    expandArgs(r)).getString();
        } catch (Throwable t) {
            return r.template;
        }
    }

    /**
     * 把参数里的**枚举 id / 语言键换成本地化组件**（用户 2026-10-12 截图抓到的 bug：
     * 邮件标题显示成「第 1 期**small**联赛邀请」—— 因为服务端只发 id，而参数是字符串）。
     *
     * <p>⚠ 服务端**没有语言表**，所以它只能发 `small` / `medium` / `large` 这样的 id ✓；
     * 翻译必须在客户端做。这里按**前缀**决定要不要翻（与 {@link #senderOf} 同一套做法）：
     * 认不出来就原样保留 —— 不猜、不硬编码中文（语言文件里的键就是真源）✓。
     *
     * <p>两种前缀：
     * <ol>
     *   <li>{@code league_*} 模板的第 2 个参数是联赛档位 id ⇒ 查 `league.anglertackle.tier.*`；</li>
     *   <li><b>以 {@code #} 开头</b> ⇒ 后面整段就是语言键，直接翻
     *       （委托 `docs/40` 用它发"是哪一条委托"：服务端只能发
     *       {@code #quest.anglertackle.daily_any_3.name}，中文名在语言文件里）✓。</li>
     * </ol>
     */
    private static Object[] expandArgs(Row r) {
        Object[] out = new Object[r.args.size()];
        for (int i = 0; i < out.length; i++) {
            String a = r.args.get(i);
            if (r.template.startsWith("league") && i == 1
                    && (a.equals("small") || a.equals("medium") || a.equals("large"))) {
                out[i] = Component.translatable("league.anglertackle.tier." + a);
            } else if (a.startsWith("#") && a.length() > 1) {
                out[i] = Component.translatable(a.substring(1));
            } else {
                out[i] = a;
            }
        }
        return out;
    }

    /**
     * 发件人：**由模板前缀推**（不另存字段 —— `docs/36` §2.1 的字段表里没有 sender）。
     *
     * <p>这是"界面文案"，按纪律必须走语言文件；客户端侧按前缀映射，服务端一个字都不拼 ✓。
     */
    private static String senderOf(String template) {
        String key;
        if (template.startsWith("league")) {
            key = "league";
        } else if (template.startsWith("codex")) {
            key = "codex";
        } else if (template.startsWith("quest")) {
            key = "fisherman";                  // 老柯派的活（`docs/40`）⇒ 发件人就是他 ✓
        } else if (template.startsWith("fisherman")) {
            key = "fisherman";
        } else {
            key = "admin";
        }
        return Component.translatable("mail.anglertackle.sender." + key).getString();
    }

    private Row selected() {
        for (Row r : parse()) {
            if (r.id == selectedId) {
                return r;
            }
        }
        return null;
    }

    /** 当前页的行（渲染与点击判据共用一份，避免两处算法漂移）。 */
    private List<Row> pageRows() {
        List<Row> all = parse();
        int from = Math.min(page * rowsPerPage, all.size());
        int to = Math.min(all.size(), from + rowsPerPage);
        return all.subList(from, to);
    }

    /** 列表每行在屏幕上的 y（第 i 行，0 起算）。 */
    private int rowY(int i) {
        return top + HEADER_H + 4 + i * LIST_ROW_H;
    }

    /** 把选中挪到第 i 行（并标已读 —— 与点击同一条路）。 */
    private void selectIndex(int i) {
        List<Row> page = pageRows();
        if (i < 0 || i >= page.size()) {
            return;
        }
        Row r = page.get(i);
        boolean changed = selectedId != r.id;
        selectedId = r.id;
        if (changed) {
            bodyScroll = 0;         // 换信 ⇒ 正文从第一行开始（见 bodyScroll 的注释）
        }
        if (changed && !r.read) {
            action(MailActionPayload.READ, r.id);
        }
        rebuild();
    }

    private int indexOfSelected() {
        List<Row> page = pageRows();
        for (int i = 0; i < page.size(); i++) {
            if (page.get(i).id == selectedId) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 渲染
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        // 面板 + 斜面边框（外深内亮，与既有界面一致）
        g.fill(left - 1, top - 1, left + panelW + 1, top + panelH + 1, C_SHADOW);
        g.fill(left, top, left + panelW, top + panelH, C_PANEL);
        g.fill(left, top, left + panelW, top + 1, C_LINE);
        g.fill(left, top + panelH - 1, left + panelW, top + panelH, C_LINE);
        g.fill(left, top, left + 1, top + panelH, C_LINE);
        g.fill(left + panelW - 1, top, left + panelW, top + panelH, C_LINE);

        MailPayload snap = snap();
        // 标题栏：邮箱 + 未读
        g.drawString(this.font, Component.translatable("gui.anglertackle.mailbox").getString(),
                left + 6, top + 5, C_TITLE, false);
        if (snap != null) {
            String right = Component.translatable("gui.anglertackle.mail_counter",
                    snap.unread(), snap.claimable(), snap.maxMails()).getString();
            g.drawString(this.font, right, left + panelW - 6 - this.font.width(right), top + 5,
                    snap.unread() > 0 ? C_UNREAD : C_DIM, false);
        }
        int listX = left + 6;
        int listTop = top + HEADER_H + 2;
        int detX = left + 6 + LIST_W + GAP;
        int detW = panelW - (6 + LIST_W + GAP) - 6;
        // 两块面板的底
        g.fill(listX - 1, listTop - 1, listX + LIST_W + 1,
                top + panelH - FOOTER_H, C_TRACK);
        g.fill(detX - 1, listTop - 1, detX + detW + 1, top + panelH - FOOTER_H, C_TRACK);

        List<Row> rows = parse();
        if (rows.isEmpty()) {
            // 空状态：**两行字**，不画空列表（空列表与"加载失败"长得一样）
            g.drawString(this.font,
                    Component.translatable(ClientMails.synced()
                            ? "gui.anglertackle.mail_empty" : "gui.anglertackle.mail_loading").getString(),
                    listX + 6, listTop + 8, C_DIM, false);
            g.drawString(this.font,
                    Component.translatable("gui.anglertackle.mail_empty_hint").getString(),
                    detX + 4, listTop + 8, C_HINT, false);
        } else {
            int from = page * rowsPerPage;
            int to = Math.min(rows.size(), from + rowsPerPage);
            for (int i = from; i < to; i++) {
                Row r = rows.get(i);
                // ⚠ 行的 y 必须与 rowY()/mouseClicked 用**同一个函数**：三处各算一遍迟早漂移，
                //   症状是"点第 2 行选中了第 3 行"（这种错极难一眼看出来）。
                int y = rowY(i - from) + 1;
                if (r.id == selectedId) {
                    g.fill(listX, y - 1, listX + LIST_W, y + LIST_ROW_H - 2, 0x40FFFFFF);
                }
                if (!r.read) {
                    g.fill(listX, y - 1, listX + 2, y + LIST_ROW_H - 2, C_UNREAD);   // 未读窄条
                }
                int color = r.claimed ? C_HINT : (r.read ? C_DIM : C_TEXT);
                g.drawString(this.font, UiLayout.fitText(r.title, LIST_W - 14), listX + 5, y + 2,
                        color, false);
                g.drawString(this.font, UiLayout.fitText(r.sender, LIST_W - 44), listX + 5, y + 12,
                        C_HINT, false);
                if (r.canClaim()) {
                    g.drawString(this.font,
                            Component.translatable("gui.anglertackle.mail_todo").getString(),
                            listX + LIST_W - 26, y + 12, C_CLAIM, false);
                } else if (!r.claimed && r.expired) {
                    g.drawString(this.font,
                            Component.translatable("gui.anglertackle.mail_expired").getString(),
                            listX + LIST_W - 34, y + 12, C_EXPIRE, false);
                } else if (r.canClaim() || (!r.claimed && !r.expired)) {
                    String left1 = Component.translatable("gui.anglertackle.mail_days",
                            Math.max(0, r.daysLeft)).getString();
                    g.drawString(this.font, left1, listX + LIST_W - 6 - this.font.width(left1),
                            y + 12, r.daysLeft <= 1 ? C_EXPIRE : C_HINT, false);
                }
            }
            Row sel = selected();
            if (sel != null) {
                renderDetail(g, sel, detX, listTop, detW);
            } else {
                g.drawString(this.font,
                        Component.translatable("gui.anglertackle.mail_pick").getString(),
                        detX + 4, listTop + 8, C_HINT, false);
            }
        }
        // ⚠ **不要调 super.render(...)**：它内部会再调一次 renderBackground ⇒ 那层全屏暗化
        //   盖在面板与文字上，表现是"界面朦胧、白字发灰"（用户为了这个问题说过"严查"好多次）。
        //   所以这里自己把 widgets（按钮）画一遍 ⇒ 暗化只发生一次 ✓
        //   （守着这条的是 tools/check-screen-render.js）
        for (net.minecraft.client.gui.components.Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
        // 悬浮提示放在**最后**画（盖在所有东西上面）—— 列表行与附件格都要有
        renderHoverTips(g, mouseX, mouseY);
    }

    /**
     * 悬浮提示（`docs/36` §5.3 交互细则）。
     *
     * <p>⚠ 划过**只显示提示**，绝不改已读状态（那条是《死亡搁浅》的反例）；
     * 而"点"才标已读 —— 两者分得很清楚 ✓。
     */
    private void renderHoverTips(GuiGraphics g, int mouseX, int mouseY) {
        int listX = left + 6;
        // ① 列表行
        List<Row> page = pageRows();
        for (int i = 0; i < page.size(); i++) {
            int y = rowY(i) - 1;
            if (mouseX >= listX && mouseX <= listX + LIST_W
                    && mouseY >= y && mouseY <= y + LIST_ROW_H - 2) {
                Row r = page.get(i);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.literal(r.title).withStyle(net.minecraft.ChatFormatting.YELLOW));
                tip.add(Component.literal(r.sender).withStyle(net.minecraft.ChatFormatting.GRAY));
                tip.add(Component.translatable(r.claimed ? "gui.anglertackle.mail_tip_claimed"
                        : (r.expired ? "gui.anglertackle.mail_tip_expired"
                        : (r.canClaim() ? "gui.anglertackle.mail_tip_claimable"
                        : "gui.anglertackle.mail_tip_read")))
                        .withStyle(net.minecraft.ChatFormatting.WHITE));
                if (r.canClaim() && !r.expired && r.daysLeft <= 3) {
                    tip.add(Component.translatable("gui.anglertackle.mail_tip_expiring", r.daysLeft)
                            .withStyle(net.minecraft.ChatFormatting.RED));
                }
                g.renderComponentTooltip(this.font, tip, mouseX, mouseY);
                return;
            }
        }
        // ② 附件格（鱼币格 + 物品格）：显示名字与数量
        Row sel = selected();
        if (sel == null) {
            return;
        }
        int detX = left + 6 + LIST_W + GAP;
        int detW = panelW - (6 + LIST_W + GAP) - 6;
        int sy = attachY();
        int sx = detX + 4;
        if (sel.coins > 0) {
            if (inRect(mouseX, mouseY, sx, sy)) {
                g.renderComponentTooltip(this.font, List.of(
                        Component.translatable("gui.anglertackle.mail_coins", sel.coins)
                                .withStyle(net.minecraft.ChatFormatting.GOLD)), mouseX, mouseY);
                return;
            }
            sx += SLOT + 2;
        }
        for (ItemStack st : sel.items) {
            if (inRect(mouseX, mouseY, sx, sy)) {
                g.renderTooltip(this.font, st, mouseX, mouseY);
                return;
            }
            sx += SLOT + 2;
        }
        // ③ 详情面板空白处：把"这封信怎么来的"讲清楚（标题栏与时间不重复）
        if (mouseX >= detX && mouseX <= detX + detW && mouseY >= rowY(0) && mouseY <= sy - 12) {
            g.renderComponentTooltip(this.font, List.of(
                    Component.translatable("gui.anglertackle.mail_tip_hint")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    private static boolean inRect(int mx, int my, int x, int y) {
        return mx >= x && mx <= x + SLOT && my >= y && my <= y + SLOT;
    }

    /**
     * 滚轮：**看鼠标在哪**（用户 2026-10-12 报"正文看不全又没有翻页"之后定的交互）。
     *
     * <ul>
     *   <li>鼠标在**详情栏**上 ⇒ 滚正文（长信能读到最后一行）；</li>
     *   <li>鼠标在**列表**上 ⇒ 翻页（原来的行为，不变）。</li>
     * </ul>
     * 两条都用滚轮、但作用域不同 —— 比"再加一组按钮"更顺手，也不需要玩家先点哪里。
     */
    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        MailPayload snap = snap();
        if (snap == null) {
            return super.mouseScrolled(mx, my, dx, dy);
        }
        int detX = left + 6 + LIST_W + GAP;
        int detW = panelW - (6 + LIST_W + GAP) - 6;
        if (mx >= detX && mx <= detX + detW && selected() != null) {
            Row r = selected();
            int maxScroll = Math.max(0, bodyLineCount(r) - bodyLinesMax());
            int next = bodyScroll + (dy < 0 ? 1 : -1);
            if (next >= 0 && next <= maxScroll && next != bodyScroll) {
                bodyScroll = next;
                return true;
            }
            return true;        // 已经在头/尾：吃掉事件，别让它掉到列表去翻页
        }
        int pages = Math.max(1, (snap.rows().size() + rowsPerPage - 1) / rowsPerPage);
        int next = page + (dy < 0 ? 1 : -1);
        if (next >= 0 && next < pages && next != page) {
            page = next;
            rebuild();
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    /** 正文折行后的总行数（滚轮与渲染**共用**这一份计算，两处各算一遍必然漂）。 */
    private int bodyLineCount(Row r) {
        Component body;
        try {
            body = Component.translatable("mail.anglertackle." + r.template + ".body",
                    expandArgs(r)).copy();
        } catch (Throwable t) {
            body = Component.literal(r.template);
        }
        int detW = panelW - (6 + LIST_W + GAP) - 6;
        // ⚠ 与渲染**共用同一个折行实现**（两处各折一遍必然漂 ⇒ 滚动条位置与实际内容对不上）
        return UiLayout.wrapCjk(body.getString(), Math.max(40, detW - 8)).size();
    }

    /** 正文一屏能放几行（按可用高度现算）。 */
    private int bodyLinesMax() {
        int detTop = top + HEADER_H + 2;
        int ty = detTop + 28;
        return Math.max(1, (attachY() - 12 - ty) / LINE_STEP);
    }

    /**
     * 键盘：↑/↓ 在列表里移动选择（会标已读，与点击同一条路）、Enter 领取选中的那封、
     * `A` 全部已读、Esc 关闭（父类默认行为）。
     *
     * <p>⚠ 与点击完全等价 —— 不是"另一套逻辑"（那样两条路的已读/领取行为迟早分叉）。
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int sel = indexOfSelected();
        switch (keyCode) {
            case 265 -> {   // ↑
                selectIndex(sel < 0 ? 0 : Math.max(0, sel - 1));
                return true;
            }
            case 264 -> {   // ↓
                selectIndex(sel < 0 ? 0 : Math.min(pageRows().size() - 1, sel + 1));
                return true;
            }
            case 257, 335 -> {   // Enter / 小键盘回车 = 领取选中的那封
                Row r = selected();
                if (r != null && r.canClaim()) {
                    action(MailActionPayload.CLAIM, r.id);
                    return true;
                }
                return false;
            }
            case 65 -> {    // A = 全部已读
                action(MailActionPayload.READALL, -1);
                return true;
            }
            case 266, 267 -> {   // PageUp / PageDown = 翻正文（长信不用鼠标也能读完）
                Row r = selected();
                if (r == null) {
                    return super.keyPressed(keyCode, scanCode, modifiers);
                }
                int maxScroll = Math.max(0, bodyLineCount(r) - bodyLinesMax());
                int step = keyCode == 266 ? -bodyLinesMax() : bodyLinesMax();
                bodyScroll = Math.max(0, Math.min(maxScroll, bodyScroll + step));
                return true;
            }
            case 268 -> {   // Home = 正文回顶部
                bodyScroll = 0;
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    private void renderDetail(GuiGraphics g, Row r, int x, int y, int w) {
        g.drawString(this.font, UiLayout.fitText(r.title, w - 8), x + 4, y + 3, C_TITLE, false);
        g.drawString(this.font, UiLayout.fitText(r.sender, w / 2 - 6), x + 4, y + 14, C_LABEL, false);
        String when = timeAgo(r.sentAt);
        g.drawString(this.font, when, x + w - 4 - this.font.width(when), y + 14, C_HINT, false);
        g.fill(x + 4, y + 24, x + w - 4, y + 25, C_LINE);
        // 正文：自动折行 + **可滚动**（服务端只给 template+args，这里按语言文件展开）
        Component body;
        try {
            body = Component.translatable("mail.anglertackle." + r.template + ".body",
                    expandArgs(r)).copy();
        } catch (Throwable t) {
            body = Component.literal(r.template);
        }
        int ty = y + 28;
        // ⚠ 行数**按可用高度现算**，不写死：写死 6 行时正文会被附件区拦腰截断
        //   （用户 2026-10-12 的截图："到点不管有没有打满一律" 就断在那儿）。
        int bodyBottom = attachY() - 12;
        int maxLines = Math.max(1, (bodyBottom - ty) / LINE_STEP);
        // ⚠ 用 UiLayout.wrapCjk 而不是 font.split：后者是**逐字符**填的，
        //   一行填满就断，于是"5 游戏日"这类**拉丁词 + 中文**的词组会被从中间劈开
        //   —— 用户 2026-10-12 实测："本期在 5 / 游戏日后…让人读起来戛然而止" ✗。
        java.util.List<String> lines = UiLayout.wrapCjk(body.getString(), Math.max(40, w - 8));
        int maxScroll = Math.max(0, lines.size() - maxLines);
        bodyScroll = Math.max(0, Math.min(bodyScroll, maxScroll));
        for (int i = 0; i < maxLines && i + bodyScroll < lines.size(); i++) {
            g.drawString(this.font, lines.get(i + bodyScroll), x + 4, ty, C_TEXT, false);
            ty += LINE_STEP;
        }
        // 滚动指示：**细滚动条**（用户 2026-10-12 第二次实测："文字重叠了，
        // 而且行数显示这种不重要却占空间"）。
        //
        // ⚠ 之前那版把"正文 1-10 / 10 行（滚轮 / PgUp·PgDn）"画在 bodyBottom + 1，
        //   而**"附件"两个字正好画在 attachY() - 10** ⇒ 两行重叠，谁都看不清 ✗。
        //   现在改成最省地方的形式：正文区右缘 2px 的轨道 + 滑块，不写任何字、
        //   不占正文高度、也不碰"附件"那一行 ✓。
        if (maxScroll > 0) {
            int barX = x + w - 6;
            int barTop = y + 28;
            int barH = Math.max(LINE_STEP, bodyBottom - barTop);
            g.fill(barX, barTop, barX + 2, barTop + barH, C_TRACK);
            int thumbH = Math.max(8, barH * maxLines / Math.max(1, lines.size()));
            int thumbY = barTop + (barH - thumbH) * bodyScroll / Math.max(1, maxScroll);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, C_LABEL);
        }
        // 附件区：鱼币一格 + 物品格（最多 4 格，18×18 —— 与背包格一致）
        int sy = attachY();
        g.drawString(this.font, Component.translatable("gui.anglertackle.mail_attach").getString(),
                x + 4, sy - 10, C_LABEL, false);
        int sx = x + 4;
        if (r.coins > 0) {
            g.fill(sx, sy, sx + SLOT, sy + SLOT, C_TRACK);
            g.drawString(this.font, "◎" + r.coins, sx + 1, sy + 5, C_CLAIM, false);
            sx += SLOT + 2;
        }
        int slotIdx = 0;
        for (ItemStack st : r.items) {
            if (slotIdx >= 4) {
                break;
            }
            g.fill(sx, sy, sx + SLOT, sy + SLOT, C_TRACK);
            g.renderItem(st, sx + 1, sy + 1);
            sx += SLOT + 2;
            slotIdx++;
        }
        for (int i = slotIdx + (r.coins > 0 ? 1 : 0); i < 4; i++) {
            g.fill(sx, sy, sx + SLOT, sy + SLOT, 0x30000000);
            sx += SLOT + 2;
        }
        if (r.expired) {
            g.drawString(this.font,
                    Component.translatable("gui.anglertackle.mail_expire_hint").getString(),
                    x + 4, sy + SLOT + 3, C_EXPIRE, false);
        }
    }

    /** 正文一行的高度（`LINE_STEP`）—— 抽成常量，行数公式与绘制共用同一个值。 */
    private static final int LINE_STEP = 11;

    /** 附件区的 y（正文可用高度从它往上算）。 */
    private int attachY() {
        return top + panelH - FOOTER_H - 22 - 22;
    }

    private static String timeAgo(long sentAt) {
        long mins = Math.max(0, (System.currentTimeMillis() - sentAt) / 60000L);
        if (mins < 1) {
            return Component.translatable("gui.anglertackle.mail_just_now").getString();
        }
        if (mins < 60) {
            return Component.translatable("gui.anglertackle.mail_minutes", mins).getString();
        }
        long hours = mins / 60;
        if (hours < 24) {
            return Component.translatable("gui.anglertackle.mail_hours", hours).getString();
        }
        return Component.translatable("gui.anglertackle.mail_days_ago", hours / 24).getString();
    }

    // ------------------------------------------------------------------ 输入
    /**
     * 鼠标点击：**只有点中列表行才有反应**（选中 + 标为已读，**不自动领取**）。
     *
     * <p>⚠ 划过（{@code mouseMoved}）**什么都不做** —— 那条是《死亡搁浅》的反例：
     * 划过就消感叹号 ⇒ 未读数莫名归零（`docs/36` §5.3 交互细则第一条）。
     */
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int listX = left + 6;
        List<Row> rows = pageRows();
        for (int i = 0; i < rows.size(); i++) {
            int y = rowY(i) - 1;
            if (mx >= listX && mx <= listX + LIST_W && my >= y && my <= y + LIST_ROW_H - 2) {
                selectIndex(i);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    /** 服务端推来新快照时由 handler 调用（保持选中与页码，刷新内容）。 */
    public void refresh() {
        BagFullFlag.from(ClientMails.latest(), v -> this.bagFull = v);
        rebuild();
    }

    /** 小工具：从快照的 lastError 里读"背包已满"。 */
    private static final class BagFullFlag {
        static void from(MailPayload p, java.util.function.Consumer<Boolean> out) {
            out.accept(p != null && "bag_full".equals(p.lastError()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    static {
        // 让 RenderSystem 的静态初始化早一点发生（本界面直接用 fill/drawString，
        // 不引入额外的渲染状态；这里只是保持与其它界面一致的加载顺序）
        RenderSystem.assertOnRenderThreadOrInit();
    }
}
