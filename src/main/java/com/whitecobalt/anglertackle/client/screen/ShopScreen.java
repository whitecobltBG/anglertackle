package com.whitecobalt.anglertackle.client.screen;

import com.whitecobalt.anglertackle.client.ClientCoins;
import com.whitecobalt.anglertackle.client.ClientLeaguePoints;
import com.whitecobalt.anglertackle.client.ClientShopState;
import com.whitecobalt.anglertackle.shop.ShopCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 钓鱼商店界面（老柯）。
 *
 * <h2>为什么是 {@link Screen} 而不是 {@code AbstractContainerScreen}</h2>
 * 这个商店**没有槽位** —— 不是把物品拖进去交易，而是"点一下 → 服务端扣钱发货"。
 * 用 Container 要多写一个 Menu、一套槽位同步、还要防"往槽里塞东西刷钱"，
 * 全是白费的复杂度。等以后做"把鱼卖给老柯"（那个确实要槽位）再上 Menu。
 *
 * <h2>三件事都在这一个界面里</h2>
 * 用户要求的"物品分类分页 + 等级评定 + 任务兑换"合成一个界面：
 * 顶上一排是分类（含"等级评定"），下面是该分类的商品列表。
 * 兑换不是单独一页 —— 红鱼线、金渔轮就在"鱼线""渔轮"两页里，
 * 只是它们的"价格"那一栏写的是兑换要求。少一层跳转，玩家少迷路一次。
 *
 * <h2>未解锁的商品照样显示</h2>
 * 用户要求"看得见但买不了"。点它会提示还差什么 ——
 * 藏起来的话玩家根本不知道"还要做什么才能买到"。
 */
public class ShopScreen extends Screen {

    private static final int W = 340;
    private static final int H = 210;
    private static final int ROW_H = 20;
    private static final int ROWS_PER_PAGE = 7;

    /** 分类标签行：7 个物品分类 + 1 个「等级评定」。 */
    private static final String[] TABS = {"竿", "鱼线", "渔轮", "铅锤", "拟饵", "鱼饵", "窝料", "等级评定", "卖鱼", "修杆", "兑换", "联赛"};

    /** 「等级评定」和「卖鱼」不是物品分类，它们排在 7 个分类后面。 */
    private static final int TAB_RATING = 7;
    private static final int TAB_SELL = 8;
    private static final int TAB_REPAIR = 9;
    /** 「兑换」：鱼 → 原版物品（{@code ShopCatalog.EXCHANGES}，用户 2026-10-02 加的）。 */
    private static final int TAB_EXCHANGE = 10;
    /**
     * 「联赛」：花**联赛积分**换东西（`docs/39` §4，`ShopCatalog.LEAGUE_OFFERS`）。
     *
     * <p>⚠ 它是**第五个非分类页签**，所以排在下标 11 —— 加它的时候四个老页签的下标**没动**
     * （分类那 7 个仍按 `Category.values()` 排 ✓），但 `check-shop-ui.js` 里
     * "页签数 = 分类数 + 4"那条断言要跟着改成 `+ 5` ✓。
     */
    private static final int TAB_LEAGUE = 11;

    /**
     * 卖鱼页当前显示的背包槽位（渲染时填，点击时用）。
     *
     * <p>为什么不直接按下标算：背包里大部分格子不是鱼，列表要**跳着**显示。
     * 渲染和点击必须用**同一份**列表，各自遍历一遍背包的话，
     * 一旦两次结果不同（比如中途卖掉一条），点到的就不是看见的那条 ——
     * 那种 bug 表现为"点 A 卖了 B"，很难复现。
     */
    private final java.util.List<Integer> sellSlots = new java.util.ArrayList<>();
    /** 修杆页：背包里"坏了且是竿"的槽位。渲染时填，点击时用同一份（理由同 sellSlots）。 */
    private final java.util.List<Integer> repairSlots = new java.util.ArrayList<>();

    /** 卖鱼页每页几条。 */
    private static final int SELL_PER_PAGE = 7;

    // ---- 确认框布局（渲染和点击**必须用同一组常量**，各写一遍必然会错位）----
    private static final int CFM_W = 290;
    private static final int CFM_H = 100;
    private static final int CFM_BTN_Y = 68;
    private static final int CFM_BTN_H = 18;

    // ---------------------------------------------------------------- 自适应（本次实际尺寸）
    //
    // 上面那些是**设计尺寸**（窗口够大时用的就是它）。真正画的时候一律用下面这些字段，
    // 在 init() 里按窗口现算 —— 见 UiLayout 的类注释（用户 2026-10-02 报"内容装不下"）。
    private int panelW = W;
    private int panelH = H;
    /** 商品列表每页条数 / 卖鱼·修杆列表每页条数。 */
    private int rowsPerPage = ROWS_PER_PAGE;
    private int sellPerPage = SELL_PER_PAGE;
    /** 确认框：尺寸与按钮，都随面板缩放。 */
    private int cfmW = CFM_W;
    private int cfmH = CFM_H;
    private int cfmBtnY = CFM_BTN_Y;
    /** 面板宽度下限（比这还窄就没法看了）。 */
    private static final int MIN_W = 250;

    /**
     * 待确认的卖出：槽位号（-1 = 没有待确认的）。
     *
     * <p>点材料鱼时不直接卖，先记在这儿、画一个确认框，玩家点"确定"才真的发出去。
     */
    private int pendingSellSlot = -1;
    private String pendingSellReason;

    private int left;
    private int top;
    private int tab = 0;
    private int page = 0;
    /** 鼠标悬停的行（-1 = 没有）—— 用来画高亮和物品 tooltip。 */
    private int hoverRow = -1;

    public ShopScreen() {
        super(Component.translatable("gui.anglertackle.shop"));
    }

    // ================================================================ 布局
    @Override
    protected void init() {
        // ★ 面板必须装得进窗口（窗口是 GUI 缩放折算后的逻辑像素，见 UiLayout 的类注释）
        panelW = UiLayout.panelW(width, W, MIN_W);
        panelH = UiLayout.panelH(height, H, 40 + 24 + ROW_H);
        // 列表第一行在 top+40，页脚占 24 → 能放几行由面板高度决定（原来写死 7 行）
        rowsPerPage = UiLayout.rows(panelH, 40, 24, ROW_H);
        sellPerPage = rowsPerPage;
        // 确认框不能比面板还大；按钮那一行要在框内
        cfmW = Math.min(CFM_W, panelW - 16);
        cfmH = Math.min(CFM_H, panelH - 16);
        cfmBtnY = Math.min(CFM_BTN_Y, cfmH - 26);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    /** 确认框里第 i 个按钮的 x（相对框左缘）。三个按钮等宽，随框宽缩放。 */
    private int cfmBtnX(int i) {
        int gap = 8;
        return gap + i * (cfmBtnW() + gap);
    }

    /** 确认框按钮宽度（渲染与点击共用，别各写一份）。 */
    private int cfmBtnW() {
        return Math.max(40, (cfmW - 32) / 3);
    }

    /** 当前分类下的商品。最后一个标签是「等级评定」，没有商品列表。 */
    private List<ShopCatalog.Entry> currentEntries() {
        if (tab >= ShopCatalog.Category.values().length) {
            return List.of();
        }
        ShopCatalog.Category cat = ShopCatalog.Category.values()[tab];
        return ShopCatalog.ENTRIES.stream().filter(e -> e.category() == cat).toList();
    }

    private int pageCount() {
        int n = currentEntries().size();
        return Math.max(1, (n + rowsPerPage - 1) / rowsPerPage);
    }

    // ================================================================ 绘制
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left, top, left + panelW, top + panelH, 0xC0101010);
        g.fill(left, top, left + panelW, top + 1, 0xFF808080);
        g.fill(left, top + panelH - 1, left + panelW, top + panelH, 0xFF808080);

        // ---- 标题：等级 + 经验 + 余额 ----
        g.drawString(font, "§6钓鱼商店 §8· §f老柯", left + 8, top + 7, 0xFFFFFF);
        int lvNow = ClientShopState.level();
        boolean capped = com.whitecobalt.anglertackle.player.AnglerLeveling.isGate(lvNow);
        String st = "§7Lv.§f" + lvNow
                + (lvNow >= com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL
                    ? " §a满级"
                    : capped ? " §c待评定"
                    : " §7经验 §f" + ClientShopState.xp() + "§8/"
                      + com.whitecobalt.anglertackle.player.AnglerLeveling.xpToNext(lvNow));
        g.drawString(font, st, left + 130, top + 7, 0xFFFFFF);
        String coins = "§7鱼币 §6" + ClientCoins.coins();
        int coinsX = left + panelW - 8 - font.width(coins);
        g.drawString(font, coins, coinsX, top + 7, 0xFFFFFF);
        // 联赛积分（`docs/39`，2026-10-13）：紧挨在鱼币左边。
        // 用户在「联赛」页花钱时余额必须看得见 —— 否则不知道买完还剩多少、
        // 也不知道"为什么这一条买不了"（那多半就是积分不够）。
        // ⚠ 空间不够就**不画**：宁可少显示一行，也不能和左边的等级/经验文字叠上。
        if (ClientLeaguePoints.known()) {
            String pts = "§7积分 §b" + ClientLeaguePoints.points();
            int px = coinsX - 10 - font.width(pts);
            if (px > left + 130 + font.width(st) + 6) {
                g.drawString(font, pts, px, top + 7, 0xFFFFFF);
            }
        }

        // ---- 分类标签 ----
        int tabW = (panelW - 16) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int x = left + 8 + i * tabW;
            boolean sel = i == tab;
            boolean hot = in(mouseX, mouseY, x, top + 20, tabW - 2, 14);
            g.fill(x, top + 20, x + tabW - 2, top + 34, sel ? 0xFF3A3A6A : (hot ? 0xFF2A2A3A : 0xFF1A1A22));
            int color = sel ? 0xFFD080 : 0xA0A0A0;
            g.drawString(font, TABS[i], x + (tabW - 2 - font.width(TABS[i])) / 2, top + 23, color);
        }

        if (!ClientShopState.talked()) {
            g.drawString(font, "§c先跟老柯聊聊，他才会做生意。", left + 12, top + 46, 0xFFFFFF);
        renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }

        if (tab == TAB_SELL) {
            renderSell(g, mouseX, mouseY);
            renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }
        if (tab == TAB_REPAIR) {
            renderRepair(g, mouseX, mouseY);
            renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }
        if (tab == TAB_RATING) {
            renderRating(g);
        renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }
        if (tab == TAB_EXCHANGE) {
            renderExchange(g, mouseX, mouseY);
            renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }
        if (tab == TAB_LEAGUE) {
            renderLeague(g, mouseX, mouseY);
            renderWidgetsOnly(g, mouseX, mouseY, partialTick);
            return;
        }

        // ---- 商品列表 ----
        List<ShopCatalog.Entry> list = currentEntries();
        int start = page * rowsPerPage;
        hoverRow = -1;
        for (int i = 0; i < rowsPerPage && start + i < list.size(); i++) {
            ShopCatalog.Entry e = list.get(start + i);
            int y = top + 40 + i * ROW_H;
            boolean hot = in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1);
            if (hot) {
                hoverRow = start + i;
                g.fill(left + 8, y, left + panelW - 8, y + ROW_H - 1, 0x40FFFFFF);
            }
            drawEntry(g, e, left + 10, y + 1);
        }

        // ---- 分页 ----
        if (pageCount() > 1) {
            String p = (page + 1) + " / " + pageCount();
            g.drawString(font, "§7" + p, left + panelW / 2 - font.width(p) / 2, top + panelH - 16, 0xFFFFFF);
            g.drawString(font, "§7◀", left + panelW / 2 - 50, top + panelH - 16, 0xFFFFFF);
            g.drawString(font, "§7▶", left + panelW / 2 + 40, top + panelH - 16, 0xFFFFFF);
        }
        g.drawString(font, "§8点击购买 · ESC 关闭", left + 8, top + panelH - 16, 0xFFFFFF);

        // 悬停行的物品 tooltip（鼠标悬浮查看，和物品栏里的表现一致）
        if (hoverRow >= 0 && hoverRow < list.size()) {
            g.renderTooltip(font, new ItemStack(itemOf(list.get(hoverRow))), mouseX, mouseY);
        }
        renderWidgetsOnly(g, mouseX, mouseY, partialTick);
    }

    /**
     * 只渲染 widgets —— **不要调 super.render()**。
     *
     * <p>Screen.render() 内部会再调一次 renderBackground()，那层**全屏**暗化
     * 会盖在刚刚画好的面板和文字上 —— 表现就是"整个界面朦胧、白字发灰"，
     * 而 tooltip 清晰（它在 renderWithTooltip 里最后画，盖不到）。
     * 4 个界面全中过这个坑，见 tools/check-screen-render.js。
     */
    private void renderWidgetsOnly(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        for (net.minecraft.client.gui.components.Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    /**
     * 「卖鱼」页：列出背包里所有鱼，点一下卖一条。
     *
     * <p><b>为什么不做成"拖进槽位"</b>：那需要 AbstractContainerMenu + 槽位同步 +
     * 防刷（玩家往槽里塞东西、双端不一致），是一整套东西；而这个列表能达到同样目的，
     * 而且少一次拖拽。核心诉求是"把钓到的鱼换成钱"，两种做法在这一点上等价。
     *
     * <p>价格用客户端的 {@code FishItem.sellValue} 显示 —— 它和卖出的鱼是**同一个
     * 物品栈**，所以显示多少就是卖多少。真正结算仍在服务端现算一遍（防伪造）。
     */
    /**
     * 修杆页：列出背包里**已经掉耐久**的鱼竿，标出修一次要多少币。
     *
     * <p>价格只是**显示**用的，服务端在修的那一刻会用同一套规则重算一遍
     * （见 ShopManager.repairCost）—— 改包改不动价格。
     */
    private void renderRepair(GuiGraphics g, int mouseX, int mouseY) {
        repairSlots.clear();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (com.whitecobalt.anglertackle.shop.ShopManager.repairCost(s) >= 0) {
                repairSlots.add(i);
            }
        }

        if (repairSlots.isEmpty()) {
            g.drawString(font, "§7背包里没有需要修的鱼竿。", left + 12, top + 46, 0xFFFFFF);
            g.drawString(font, "§8修一次 = 目录价的一半；兑换来的竿固定 "
                    + com.whitecobalt.anglertackle.shop.ShopManager.REPAIR_EXCHANGE_COST + " 币",
                    left + 12, top + 62, 0xFFFFFF);
            return;
        }

        int pages = Math.max(1, (repairSlots.size() + sellPerPage - 1) / sellPerPage);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * sellPerPage;
        for (int i = 0; i < sellPerPage && start + i < repairSlots.size(); i++) {
            ItemStack s = player.getInventory().getItem(repairSlots.get(start + i));
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                g.fill(left + 8, y, left + panelW - 8, y + ROW_H - 1, 0x40FFFFFF);
            }
            g.renderItem(s, left + 10, y + 1);
            g.drawString(font, s.getHoverName().getString(), left + 30, y + 5, 0xFFFFFF);
            int cost = com.whitecobalt.anglertackle.shop.ShopManager.repairCost(s);
            String right = "§6" + String.format("%,d", cost) + " §7币";
            g.drawString(font, right, left + panelW - 16 - font.width(right), y + 5, 0xFFFFFF);
        }
        g.drawString(font, "§8点一根修一根 · ESC 关闭", left + 8, top + panelH - 16, 0xFFFFFF);
        if (pages > 1) {
            String p = (page + 1) + " / " + pages;
            g.drawString(font, "§7" + p, left + panelW / 2 - font.width(p) / 2, top + panelH - 16, 0xFFFFFF);
        }
        int hov = hoverRepairRow(mouseX, mouseY);
        if (hov >= 0 && hov < repairSlots.size()) {
            g.renderTooltip(font, player.getInventory().getItem(repairSlots.get(hov)), mouseX, mouseY);
        }
    }

    private int hoverRepairRow(int mouseX, int mouseY) {
        for (int i = 0; i < sellPerPage; i++) {
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                return page * sellPerPage + i;
            }
        }
        return -1;
    }

    // ================================================================ 兑换（鱼 → 原版物品）
    //
    // 用户 2026-10-02："加一个兑换，用特定品质鱼兑换原版物品……至少给我 20 种兑换表。"
    // 表在 ShopCatalog.EXCHANGES（由 tools/shop-rules.js 生成），这里只负责画与点。
    //
    // ⚠ 界面**不做判定**：那一行灰不灰走 ShopMaterials.has(...)（和服务端扣鱼用的是
    //   同一份判据），点了之后能不能换由服务端再判一次。

    /** 一页画几条兑换（与商品列表同一套行高）。 */
    private int hoverExchangeRow(int mouseX, int mouseY) {
        for (int i = 0; i < rowsPerPage; i++) {
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                return page * rowsPerPage + i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ 联赛页（`docs/39` P4）

    /**
     * 联赛页：一行一条报价（产物图标 + 名字 + **积分价**）。
     *
     * <p>与兑换页同一个骨架（图标 + 左文右价 + 底部翻页），只是"材料"那一栏换成积分价。
     * ⚠ 客户端**只画价、不发送价** —— 价格与限购一律由服务端从目录现查（`check-shop-ui.js` 明令）✓
     */
    private void renderLeague(GuiGraphics g, int mouseX, int mouseY) {
        var all = com.whitecobalt.anglertackle.shop.ShopCatalog.LEAGUE_OFFERS;
        int pages = Math.max(1, (all.size() + rowsPerPage - 1) / rowsPerPage);
        if (page > pages - 1) {
            page = pages - 1;
        }
        int start = page * rowsPerPage;
        for (int i = 0; i < rowsPerPage && start + i < all.size(); i++) {
            var o = all.get(start + i);
            int y = top + 40 + i * ROW_H;
            boolean hot = in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1);
            if (hot) {
                g.fill(left + 8, y, left + panelW - 8, y + ROW_H - 1, 0x40FFFFFF);
            }
            ItemStack icon = leagueOfferIcon(o);
            if (!icon.isEmpty()) {
                g.renderItem(icon, left + 10, y + 1);
            }
            g.drawString(font, leagueOfferName(o), left + 30, y + 5, 0xFFFFFF);
            String price = "§e" + o.points() + " 积分";
            g.drawString(font, price, left + panelW - 12 - font.width(price), y + 5, 0xFFFFFF);
        }
        g.drawString(font, "§8点一行用联赛积分兑换 · 每届联赛刷新限购 · ESC 关闭",
                left + 8, top + panelH - 16, 0xFFFFFF);
        if (pages > 1) {
            String p = "§7◀ §f" + (page + 1) + " / " + pages + " §7▶";
            g.drawString(font, p, left + panelW / 2 - font.width(p) / 2, top + panelH - 16, 0xFFFFFF);
        }
    }

    /** 联赛页的行命中（**渲染与点击共用同一份算法**，免得点到的不是看见的那条）。 */
    private int hoverLeagueRow(int mouseX, int mouseY) {
        for (int i = 0; i < rowsPerPage; i++) {
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                return page * rowsPerPage + i;
            }
        }
        return -1;
    }

    /** 报价的产物图标（纯称号条目没有产物 ⇒ 空栈，不画图标）。 */
    private static ItemStack leagueOfferIcon(com.whitecobalt.anglertackle.shop.ShopCatalog.LeagueOffer o) {
        if (o.resultId().isEmpty()) {
            return ItemStack.EMPTY;
        }
        var rl = net.minecraft.resources.ResourceLocation.tryParse(o.resultId());
        if (rl == null) {
            return ItemStack.EMPTY;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        if (item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /**
     * 报价的显示名：有产物走产物名（客户端从注册表取 ✓），纯称号条目走称号模板名。
     *
     * <p>⚠ 称号名是**带参数的模板**（{@code S%s%s联赛纪念}）—— **必须把参数传进去**：
     * 不传的话占位符会被原样画在界面上（用户 2026-10-13 截图发现的那条 ✗）。
     * 参数（届号 + 规模）来自商店同步包（{@link ClientShopState}）。
     */
    private static String leagueOfferName(com.whitecobalt.anglertackle.shop.ShopCatalog.LeagueOffer o) {
        if (o.resultId().isEmpty() && !o.titleTemplate().isEmpty()) {
            int season = ClientShopState.leagueSeason();
            String tier = ClientShopState.leagueTier();
            if (season > 0 && !tier.isEmpty()) {
                return net.minecraft.network.chat.Component
                        .translatable("title.anglertackle." + o.titleTemplate(), season,
                                net.minecraft.network.chat.Component
                                        .translatable("league.anglertackle.tier." + tier))
                        .getString();
            }
            // 还没开过联赛 ⇒ 拿不到参数：换成**不带占位符**的说法，绝不把 %s 画出来
            return net.minecraft.network.chat.Component
                    .translatable("gui.anglertackle.league_title_pending").getString();
        }
        ItemStack st = leagueOfferIcon(o);
        return st.isEmpty() ? o.id() : st.getHoverName().getString();
    }

    private void renderExchange(GuiGraphics g, int mouseX, int mouseY) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        var all = com.whitecobalt.anglertackle.shop.ShopCatalog.EXCHANGES;
        int pages = Math.max(1, (all.size() + rowsPerPage - 1) / rowsPerPage);
        if (page > pages - 1) {
            page = pages - 1;
        }
        int start = page * rowsPerPage;
        for (int i = 0; i < rowsPerPage && start + i < all.size(); i++) {
            var x = all.get(start + i);
            int y = top + 40 + i * ROW_H;
            boolean hot = in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1);
            boolean ok = com.whitecobalt.anglertackle.shop.ShopMaterials.has(player, x);
            if (hot) {
                g.fill(left + 8, y, left + panelW - 8, y + ROW_H - 1, 0x40FFFFFF);
            }
            // 左：材料（灰 = 现在凑不齐，用 describe 的 dim 版）；右：产出
            String need = com.whitecobalt.anglertackle.shop.ShopMaterials.describe(x, !ok);
            g.drawString(font, need, left + 10, y + 5, 0xFFFFFF);
            var out = com.whitecobalt.anglertackle.shop.ShopMaterials.resultStack(player, x);
            if (!out.isEmpty()) {
                g.renderItem(out, left + panelW - 116, y + 1);
            }
            String res = (ok ? "§a" : "§8") + "→ " + out.getCount() + "× "
                    + com.whitecobalt.anglertackle.shop.ShopMaterials.resultName(player, x);
            g.drawString(font, res, left + panelW - 94, y + 5, 0xFFFFFF);
        }
        g.drawString(font, "§8点一行兑换 · 品质不够的鱼不算 · ESC 关闭", left + 8, top + panelH - 16, 0xFFFFFF);
        if (pages > 1) {
            // ⚠ 翻页提示要**画得像能点**：原来只画了一行"1 / 2"，
            //   用户 2026-10-02："我知道兑换页码能翻，但没有像是按钮那样的提示，
            //   第一眼过去让人以为没有翻页功能。" 所以照商品列表那样画金色的 ◀ ▶。
            String p = (page + 1) + " / " + pages;
            g.drawString(font, "§f" + p, left + panelW / 2 - font.width(p) / 2, top + panelH - 16, 0xFFFFFF);
            g.drawString(font, "§e◀", left + panelW / 2 - 46, top + panelH - 16, 0xFFFFFF);
            g.drawString(font, "§e▶", left + panelW / 2 + 38, top + panelH - 16, 0xFFFFFF);
        } else {
            g.drawString(font, "§8共 " + all.size() + " 条（一页放得下）",
                    left + panelW / 2 - 30, top + panelH - 16, 0xFFFFFF);
        }
        int hov = hoverExchangeRow(mouseX, mouseY);
        if (hov >= 0 && hov < all.size()) {
            var out = com.whitecobalt.anglertackle.shop.ShopMaterials.resultStack(player, all.get(hov));
            if (!out.isEmpty()) {
                g.renderTooltip(font, out, mouseX, mouseY);
            }
        }
    }

    private void renderSell(GuiGraphics g, int mouseX, int mouseY) {
        sellSlots.clear();
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem()
                    instanceof com.whitecobalt.anglertackle.item.FishItem) {
                sellSlots.add(i);
            }
        }

        if (sellSlots.isEmpty()) {
            g.drawString(font, "§7背包里没有鱼。去钓几条再来。", left + 12, top + 46, 0xFFFFFF);
            return;
        }

        int pages = Math.max(1, (sellSlots.size() + sellPerPage - 1) / sellPerPage);
        if (page >= pages) {
            page = pages - 1;
        }
        int start = page * sellPerPage;

        long total = 0;
        for (int i = 0; i < sellPerPage && start + i < sellSlots.size(); i++) {
            ItemStack s = player.getInventory().getItem(sellSlots.get(start + i));
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                g.fill(left + 8, y, left + panelW - 8, y + ROW_H - 1, 0x40FFFFFF);
            }
            g.renderItem(s, left + 10, y + 1);
            g.drawString(font, s.getHoverName().getString(), left + 30, y + 5, 0xFFFFFF);

            long price = ((com.whitecobalt.anglertackle.item.FishItem) s.getItem()).sellValue(s);
            String right = "§6" + String.format("%,d", price) + " §7×" + s.getCount();
            g.drawString(font, right, left + panelW - 16 - font.width(right), y + 5, 0xFFFFFF);
            total += price * s.getCount();
        }

        g.drawString(font, "§7这些鱼一共值 §6" + String.format("%,d", total) + " §7鱼币",
                left + 12, top + panelH - 32, 0xFFFFFF);
        g.drawString(font, "§8点一条卖一条（不会一次全卖掉） · ESC 关闭",
                left + 8, top + panelH - 16, 0xFFFFFF);
        if (pages > 1) {
            String p = (page + 1) + " / " + pages;
            g.drawString(font, "§7" + p, left + panelW / 2 - font.width(p) / 2, top + panelH - 16, 0xFFFFFF);
        }
        int hov = hoverSellRow(mouseX, mouseY);
        if (hov >= 0 && hov < sellSlots.size()) {
            g.renderTooltip(font, player.getInventory().getItem(sellSlots.get(hov)), mouseX, mouseY);
        }

        // 确认框画在最后 —— 它要盖住列表和 tooltip
        if (pendingSellSlot >= 0) {
            renderConfirm(g, mouseX, mouseY);
        }
    }

    /**
     * 「确定要卖掉吗」确认框。
     *
     * <p>只在**材料鱼**上弹（见 {@code ShopMaterials.warningFor}）：梭鲈是等级评定要交的，
     * 大西洋鲑/褐鳟/虎鱼是换金渔轮要的，红品质鱼能换红鱼线。这几样通常要钓很久，
     * 卖错一条的代价是几十分钟 —— 而卖鱼页是"点一下就卖"，手快是真会点错。
     *
     * <p>普通鱼不弹 —— 每条都问一遍会把"卖鱼"变成一种折磨，玩家最后会去勾
     * "本局不再提示"，那时连材料鱼也不弹了，反而更糟。
     */
    private void renderConfirm(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(left, top, left + panelW, top + panelH, 0xC0000000);
        int bx = left + (panelW - cfmW) / 2;
        int by = top + (panelH - cfmH) / 2;
        g.fill(bx, by, bx + cfmW, by + cfmH, 0xFF202028);
        g.fill(bx, by, bx + cfmW, by + 1, 0xFFA0A0A0);

        g.drawString(font, "§e确定要卖掉吗？", bx + 10, by + 8, 0xFFFFFF);
        g.drawString(font, "§7" + pendingSellReason, bx + 10, by + 26, 0xFFFFFF);
        g.drawString(font, "§7卖掉之后就拿不回来了。", bx + 10, by + 42, 0xFFFFFF);

        String[] labels = {"§a确定卖掉", "§f取消", "§8本局不再提示"};
        int bw = cfmBtnW();
        for (int i = 0; i < labels.length; i++) {
            int x = bx + cfmBtnX(i);
            int y = by + cfmBtnY;
            boolean hot = in(mouseX, mouseY, x, y, bw, CFM_BTN_H);
            g.fill(x, y, x + bw, y + CFM_BTN_H, hot ? 0xFF3A3A5A : 0xFF2A2A38);
            g.drawString(font, UiLayout.fitText(labels[i], bw - 4),
                    x + (bw - font.width(UiLayout.fitText(labels[i], bw - 4))) / 2, y + 5, 0xFFFFFF);
        }
    }

    /** 确认框按钮的命中区（渲染和点击共用，避免两处各写一遍坐标）。 */
    private int confirmButtonAt(int mouseX, int mouseY) {
        int bx = left + (panelW - cfmW) / 2;
        int by = top + (panelH - cfmH) / 2;
        int y = by + cfmBtnY;
        int bw = cfmBtnW();
        for (int i = 0; i < 3; i++) {
            if (in(mouseX, mouseY, bx + cfmBtnX(i), y, bw, CFM_BTN_H)) {
                return i;
            }
        }
        return -1;
    }

    /** 鼠标在第几行卖鱼条目上（-1 = 没有）。 */
    private int hoverSellRow(int mouseX, int mouseY) {
        int start = page * sellPerPage;
        for (int i = 0; i < sellPerPage && start + i < sellSlots.size(); i++) {
            int y = top + 40 + i * ROW_H;
            if (in(mouseX, mouseY, left + 8, y, panelW - 16, ROW_H - 1)) {
                return start + i;
            }
        }
        return -1;
    }

    /** 一行商品：图标 + 名字 + 价格/状态。 */
    private void drawEntry(GuiGraphics g, ShopCatalog.Entry e, int x, int y) {
        ItemStack stack = new ItemStack(itemOf(e));
        g.renderItem(stack, x, y + 1);

        boolean unlocked = ClientShopState.unlocked(e.unlockStage());
        int nameColor = unlocked ? 0xFFFFFF : 0x707070;
        g.drawString(font, stack.getHoverName().getString(), x + 20, y + 5, nameColor);

        // 价格那一栏：买价 / 免费 / 兑换
        String price;
        int priceColor;
        if (e.kind() == ShopCatalog.Kind.FREE) {
            price = "免费";
            priceColor = 0x55FFFF;
        } else if (e.kind() == ShopCatalog.Kind.BUY) {
            price = e.price() + " 币";
            priceColor = 0xFFAA00;
        } else {
            price = "兑换";
            priceColor = 0xFF55FF;
        }

        int waitMinutes = ClientShopState.waitMinutes(e.id());
        if (!unlocked) {
            price = "需等级 " + e.unlockStage();
            priceColor = 0x707070;
        } else if (waitMinutes > 0) {
            price = "售罄 " + waitMinutes + " 分钟";
            priceColor = 0xFF5555;
        }
        g.drawString(font, price, left + panelW - 16 - font.width(price), y + 5, priceColor);
    }

    /** 「等级评定」那一页：告诉玩家现在几级、还差多少经验、要交什么。 */
    private void renderRating(GuiGraphics g) {
        int lv = ClientShopState.level();
        int xp = ClientShopState.xp();
        boolean max = lv >= com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL;
        int gate = com.whitecobalt.anglertackle.player.AnglerLeveling.nextGate(lv);
        boolean atGate = com.whitecobalt.anglertackle.player.AnglerLeveling.isGate(lv);

        g.drawString(font, "§6等级评定", left + 12, top + 46, 0xFFFFFF);
        // 等级与经验条（卡在评定时不显示进度，因为那时候经验不积攒）
        String lvLine = max
                ? "§fLv.50 §a（已满级）"
                : atGate
                    ? "§fLv." + lv + " §c（卡在评定点，经验暂不积攒）"
                    : "§fLv." + lv + " §7经验 " + xp + "/"
                      + com.whitecobalt.anglertackle.player.AnglerLeveling.xpToNext(lv);
        g.drawString(font, lvLine, left + 12, top + 62, 0xFFFFFF);

        if (max || gate == 0) {
            // 41 级以上：用户口径"到 40 级突破完后不会出现评定任务"
            g.drawString(font, "§a老柯说：评定都做完了，剩下的路你自己走。", left + 12, top + 84, 0xFFFFFF);
            g.drawString(font, "§7（Lv.41 之后每级只要经验，一直钓到 Lv.50）", left + 12, top + 98, 0xFFFFFF);
            return;
        }

        g.drawString(font, "§7下一次评定在 §fLv." + gate + "§7，交付后突破到 §fLv." + (gate + 1) + "§7。",
                left + 12, top + 84, 0xFFFFFF);
        g.drawString(font, "§7评定 §c不能重复交 §7，交出去的鱼会被收走。", left + 12, top + 98, 0xFFFFFF);
        // 需求文案由**共享的那张表**生成（AnglerLeveling.GATES），界面不再自己抄一份
        g.drawString(font, "§e要求：" + requirementText(gate), left + 12, top + 116, 0xFFFFFF);

        int btnY = top + 140;
        if (!atGate) {
            g.drawString(font, "§8（等级到了 " + gate + " 才能交付）", left + 12, btnY + 5, 0xFFFFFF);
            return;
        }
        boolean hot = in(lastMouseX, lastMouseY, left + panelW / 2 - 60, btnY, 120, 18);
        g.fill(left + panelW / 2 - 60, btnY, left + panelW / 2 + 60, btnY + 18,
                hot ? 0xFF3A6A3A : 0xFF2A3A2A);
        String btn = "交付并评定";
        g.drawString(font, btn, left + panelW / 2 - font.width(btn) / 2, btnY + 5, 0xFFFFFF);
    }

    /**
     * 交付要求的文案 —— **直接读共享的那张表**（{@code AnglerLeveling.GATES}）。
     *
     * <p>⚠ 这里原本是按档位手写 {@code switch}（"case 2 → 绿品质以上梭鲈 ×1"…），
     * 和 {@code ShopManager.RATINGS} 是同一份规则的两处写法，靠检查器比对两边的字面量。
     * 2026-10-02 改成 50 级 + 每 10 级一次突破、且一级要交多条材料之后，
     * 手写 switch 立刻就成了"改一处忘一处"的坑 —— 现在客户端和服务端读的是同一张表，
     * 结构上不可能再对不上。
     */
    public static String requirementText(int gateLevel) {
        return com.whitecobalt.anglertackle.player.AnglerLeveling.describe(gateLevel);
    }

    // ================================================================ 输入
    private int lastMouseX;
    private int lastMouseY;

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        lastMouseX = (int) mouseX;
        lastMouseY = (int) mouseY;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return super.mouseClicked(mx, my, button);
        }
        int x = (int) mx;
        int y = (int) my;

        // ---- 确认框优先：它在最上层，此时点别处不该被当成点商品/点鱼 ----
        if (pendingSellSlot >= 0) {
            int b = confirmButtonAt(x, y);
            if (b == 0) {
                // 确定卖掉：这时才真的发给服务端
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.sell(pendingSellSlot));
                pendingSellSlot = -1;
                pendingSellReason = null;
            } else if (b == 1) {
                pendingSellSlot = -1;
                pendingSellReason = null;
            } else if (b == 2) {
                // 「本局不再提示」= 关掉提示**并且**把当前这条也卖了（玩家点它的意图就是"别烦我，卖"）
                ClientShopState.suppressSellWarningForever();
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.sell(pendingSellSlot));
                pendingSellSlot = -1;
                pendingSellReason = null;
            }
            // 点框外：什么都不做（不关框）。误点关掉框 = 又要点一次才卖得出去，
            // 而"框还开着"是个安全状态。
            return true;
        }

        // 分类标签
        int tabW = (panelW - 16) / TABS.length;
        for (int i = 0; i < TABS.length; i++) {
            int tx = left + 8 + i * tabW;
            if (in(x, y, tx, top + 20, tabW - 2, 14)) {
                tab = i;
                page = 0;
                return true;
            }
        }

        if (!ClientShopState.talked()) {
            return super.mouseClicked(mx, my, button);
        }

        // 兑换页：点一行换一次（和红鱼线/金渔轮那两条老兑换一致：单击即换，
        // 界面已经把"要什么、给什么"写在同一行上；能不能换由服务端再判一次）
        if (tab == TAB_EXCHANGE) {
            var all = com.whitecobalt.anglertackle.shop.ShopCatalog.EXCHANGES;
            int pages = Math.max(1, (all.size() + rowsPerPage - 1) / rowsPerPage);
            if (pages > 1 && y >= top + panelH - 20 && y <= top + panelH - 6) {
                if (x < left + panelW / 2 - 20) {
                    page = Math.max(0, page - 1);
                    return true;
                }
                if (x > left + panelW / 2 + 20) {
                    page = Math.min(pages - 1, page + 1);
                    return true;
                }
            }
            int row = hoverExchangeRow(x, y);
            if (row >= 0 && row < all.size()) {
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.exchange(row));
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // 联赛页：点一行 = 花积分买（**只发下标**；价格、余额、每届限购全由服务端现判 ✓）
        if (tab == TAB_LEAGUE) {
            var all = com.whitecobalt.anglertackle.shop.ShopCatalog.LEAGUE_OFFERS;
            int pages = Math.max(1, (all.size() + rowsPerPage - 1) / rowsPerPage);
            if (pages > 1 && y >= top + panelH - 20 && y <= top + panelH - 6) {
                if (x < left + panelW / 2 - 20) {
                    page = Math.max(0, page - 1);
                    return true;
                }
                if (x > left + panelW / 2 + 20) {
                    page = Math.min(pages - 1, page + 1);
                    return true;
                }
            }
            int row = hoverLeagueRow(x, y);
            if (row >= 0 && row < all.size()) {
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.league(row));
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // 修杆页：点一根修一根
        if (tab == TAB_REPAIR) {
            int row = hoverRepairRow(x, y);
            if (row >= 0 && row < repairSlots.size()) {
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.repair(repairSlots.get(row)));
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // 卖鱼页：点一条卖一条
        if (tab == TAB_SELL) {
            int pages = Math.max(1, (sellSlots.size() + sellPerPage - 1) / sellPerPage);
            if (pages > 1 && y >= top + panelH - 20 && y <= top + panelH - 6) {
                if (x < left + panelW / 2 - 20) {
                    page = Math.max(0, page - 1);
                    return true;
                }
                if (x > left + panelW / 2 + 20) {
                    page = Math.min(pages - 1, page + 1);
                    return true;
                }
            }
            int row = hoverSellRow(x, y);
            // ⚠ 用的是渲染时那份 sellSlots，不是重新遍历背包 ——
            //   两次遍历结果一旦不同（比如刚卖掉一条导致后面的格子前移），
            //   点到的就不是看见的那条。表现为"点 A 卖了 B"，很难复现。
            if (row >= 0 && row < sellSlots.size()) {
                int slot = sellSlots.get(row);
                var mc = net.minecraft.client.Minecraft.getInstance();
                ItemStack s = mc.player == null ? ItemStack.EMPTY
                        : mc.player.getInventory().getItem(slot);
                // 材料鱼先弹确认框（用户："可能手快点到兑换材料和评定材料"；
                // 2026-10-02 补："卖鱼时提醒玩家下一级突破任务的鱼确定要卖吗" ——
                // 下一次评定的材料会用点名的文案，见 ShopMaterials.warningFor）。
                // 普通鱼直接卖 —— 每条都问一遍会把"卖鱼"变成一种折磨。
                String why = com.whitecobalt.anglertackle.shop.ShopMaterials.warningFor(
                        s, ClientShopState.level());
                if (why != null && !ClientShopState.suppressSellWarning()) {
                    pendingSellSlot = slot;
                    pendingSellReason = why;
                    return true;
                }
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.sell(slot));
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // 等级评定的按钮（只有**正好卡在评定点**时才可点，和 service 端判定一致）
        if (tab == TAB_RATING) {
            if (com.whitecobalt.anglertackle.player.AnglerLeveling.isGate(ClientShopState.level())
                    && in(x, y, left + panelW / 2 - 60, top + 140, 120, 18)) {
                PacketDistributor.sendToServer(
                        com.whitecobalt.anglertackle.network.ShopActionPayload.rate());
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        // 翻页
        if (pageCount() > 1 && y >= top + panelH - 20 && y <= top + panelH - 6) {
            if (x < left + panelW / 2 - 20) {
                page = Math.max(0, page - 1);
                return true;
            }
            if (x > left + panelW / 2 + 20) {
                page = Math.min(pageCount() - 1, page + 1);
                return true;
            }
        }

        // 点商品
        List<ShopCatalog.Entry> list = currentEntries();
        if (hoverRow >= 0 && hoverRow < list.size()) {
            ShopCatalog.Entry e = list.get(hoverRow);
            if (!ClientShopState.unlocked(e.unlockStage())) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.displayClientMessage(
                            Component.literal("§c老柯还不肯卖给你（需要等级 " + e.unlockStage() + "）"), true);
                }
                return true;
            }
            // 只发"我想要这件"。价格、够不够钱、限购都归服务端判。
            PacketDistributor.sendToServer(
                    com.whitecobalt.anglertackle.network.ShopActionPayload.of(e.id()));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private Item itemOf(ShopCatalog.Entry e) {
        return BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("anglertackle", e.id()));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
