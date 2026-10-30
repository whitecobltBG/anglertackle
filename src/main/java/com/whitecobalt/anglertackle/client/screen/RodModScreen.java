package com.whitecobalt.anglertackle.client.screen;

import com.whitecobalt.anglertackle.network.RodModActionPayload;
import com.whitecobalt.anglertackle.network.RodModPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * 渔具改装界面。
 *
 * <p>显示哪些槽位完全由服务端决定（{@link RodModPayload}）：
 * 台钓竿只有【鱼饵】，路亚竿有 鱼线/渔轮/铅锤/拟饵。
 *
 * <p><b>所有文字都用 {@link TextWidget} 渲染</b>（与按钮同一条渲染路径），
 * 因为实测在本环境里直接 {@code drawString} 画出来的文字是糊的。
 */
public class RodModScreen extends Screen {

    private static final int PANEL_W = 420;
    private static final int PANEL_H = 216;
    private static final int ROW_H = 24;
    /** 面板宽度下限（比这还窄就没法看了）。 */
    private static final int MIN_W = 260;
    /**
     * 左栏（槽位）占面板宽度的比例：3/5。
     *
     * <p>⚠ 原来是正中分界（1/2），而"换/卸"两个按钮被摆在 x=250/292 —— 那是**右半边**，
     * 正好压在右边的属性列上（属性标签从 220 开始画）。窗口一窄还会更糟。
     * 现在左栏加宽到 3/5、按钮右对齐在左栏内，属性列只在右栏画。
     */
    private static final double SPLIT_RATIO = 0.6;

    // ---------------------------------------------------------------- 自适应（本次实际尺寸）
    private int panelW = PANEL_W;
    private int panelH = PANEL_H;
    /** 左右栏分界（面板内坐标）。 */
    private int splitX = (int) (PANEL_W * SPLIT_RATIO);
    /** 属性列里"标签 → 数值"的间距。 */
    private int statLabelW = 104;

    private RodModPayload data;
    private int left;
    private int top;
    private int expandedSlot = -1;
    private final List<Button> buttons = new ArrayList<>();
    private final List<TextWidget> texts = new ArrayList<>();

    public RodModScreen(RodModPayload data) {
        super(Component.translatable("gui.anglertackle.rod_mod"));
        this.data = data;
    }

    @Override
    protected void init() {
        // 面板必须装得进窗口（窗口是 GUI 缩放折算后的逻辑像素，见 UiLayout 的类注释）
        panelW = UiLayout.panelW(this.width, PANEL_W, MIN_W);
        panelH = UiLayout.panelH(this.height, PANEL_H, 28 + ROW_H + 30);
        splitX = Math.max(120, (int) (panelW * SPLIT_RATIO));
        // 属性列：标签占右栏宽度的一半左右，剩下的留给数值
        statLabelW = Math.max(50, (panelW - splitX - 14) / 2);
        this.left = (this.width - panelW) / 2;
        this.top = (this.height - panelH) / 2;
        com.whitecobalt.anglertackle.fishing.FishingManager.LOGGER.info(
                "[垂纶/界面] 改装界面 init：屏幕 {}x{}，面板 {}x{} 在 ({},{})，槽位 {} 个",
                this.width, this.height, panelW, panelH, this.left, this.top,
                data.slotData().size());
        rebuildAll();
    }

    /** 界面已经开着时收到新数据：原地刷新，不重新开窗（否则会闪一下）。 */
    public void acceptNewData(RodModPayload fresh) {
        this.data = fresh;
        this.expandedSlot = -1;
        rebuildAll();
    }

    private void rebuildAll() {
        for (Button b : buttons) {
            this.removeWidget(b);
        }
        buttons.clear();
        for (TextWidget w : texts) {
            this.removeWidget(w);
        }
        texts.clear();

        addText(left + 6, top + 4, data.rodName(), 0xFFF0C93B);
        addText(left + splitX + 2, top + 4,
                Component.translatable("gui.anglertackle.rod_mod").getString(), 0xFFFFD050);

        int slotCount = data.slotData().size();
        // 左栏里"换/卸"两个按钮右对齐在**左栏内**（原来摆在 250/292，压着右边的属性列）
        final int btnW = 36;
        final int removeX = splitX - 8 - btnW;
        final int swapX = removeX - btnW - 4;
        // 数值文字要给按钮让位：可用宽度 = 按钮左缘 − 数值起点
        final int valueW = Math.max(30, swapX - 6 - 90);
        for (int i = 0; i < slotCount; i++) {
            final int slotIndex = i;
            int y = top + 28 + i * ROW_H;

            String[] f = data.slotData().get(i).split("\\|", -1);
            String label = f.length > 1 ? f[1] : "";
            String value = f.length > 0 && !f[0].isEmpty()
                    ? f[0] : Component.translatable("gui.anglertackle.empty").getString();

            addText(left + 8, y, label, 0xFF8FCBF5);
            addText(left + 90, y, UiLayout.fitText(value, valueW), 0xFFFFFFFF);

            Button swap = Button.builder(Component.literal("换"), b -> {
                expandedSlot = expandedSlot == slotIndex ? -1 : slotIndex;
                rebuildAll();
            }).bounds(left + swapX, y - 3, btnW, 18).build();
            addRenderableWidget(swap);
            buttons.add(swap);

            Button remove = Button.builder(Component.literal("卸"), b -> {
                expandedSlot = -1;
                PacketDistributor.sendToServer(new RodModActionPayload(
                        RodModActionPayload.ACTION_UNEQUIP, (byte) slotIndex, ""));
            }).bounds(left + removeX, y - 3, btnW, 18).build();
            addRenderableWidget(remove);
            buttons.add(remove);
        }

        // 候选列表：按面板宽度决定一行放几个，放不下的折到下一行；
        // 实在摆不开就在末尾补一个 +N（**不静默丢候选** —— 丢了玩家就选不到那件配件）
        if (expandedSlot >= 0 && expandedSlot < slotCount) {
            String[] f = data.slotData().get(expandedSlot).split("\\|", -1);
            String[] candidates = f.length > 2 && !f[2].isEmpty() ? f[2].split(",") : new String[0];
            int listY = top + 28 + slotCount * ROW_H + 8;

            if (candidates.length == 0) {
                addText(left + 8, listY,
                        Component.translatable("gui.anglertackle.no_candidates").getString(),
                        0xFF909090);
            }
            final int targetSlot = expandedSlot;
            final int candW = 62;
            final int perRow = Math.max(1, (panelW - 16 + 4) / (candW + 4));
            // 行数也用共用公式算（UiLayout.rows），别在这儿再写一遍除法
            final int maxRows = UiLayout.rows(panelH, listY - top, 26, 22);
            final int shown = Math.min(candidates.length, perRow * maxRows);
            for (int c = 0; c < shown; c++) {
                final String id = candidates[c];
                Button pick = Button.builder(Component.literal(shortName(id)), b -> {
                    PacketDistributor.sendToServer(new RodModActionPayload(
                            RodModActionPayload.ACTION_EQUIP, (byte) targetSlot, id));
                    expandedSlot = -1;
                    rebuildAll();
                }).bounds(left + 8 + (c % perRow) * (candW + 4), listY + (c / perRow) * 22,
                        candW, 18).build();
                addRenderableWidget(pick);
                buttons.add(pick);
            }
            if (shown < candidates.length) {
                int usedRows = (shown + perRow - 1) / perRow;
                addText(left + 8, listY + usedRows * 22, "+" + (candidates.length - shown),
                        0xFF909090);
            }
        }

        // 属性面板（只在右栏画 —— 原来"换/卸"按钮正是压在它上面）
        int statX = left + splitX + 10;
        addText(statX, top + 28,
                Component.translatable("gui.anglertackle.stats").getString(), 0xFFFFD050);
        for (int i = 0; i < data.statKeys().size(); i++) {
            int rowY = top + 46 + i * 13;
            if (rowY > top + panelH - 24) {
                // 装不下的属性行**不静默丢**：末尾补一行 +N（窗口放大就能看全）
                addText(statX, top + panelH - 30,
                        "+" + (data.statKeys().size() - i), 0xFF909090);
                break;
            }
            addText(statX, rowY, UiLayout.fitText(
                            Component.translatable(data.statKeys().get(i)).getString(), statLabelW),
                    0xFFB0B0B0);
            addText(statX + statLabelW, rowY, data.statValues().get(i), 0xFFFFD050);
        }

        addText(left + 6, top + panelH - 16,
                Component.translatable("gui.anglertackle.rod_mod_hint").getString(), 0xFF909090);
    }

    private void addText(int x, int y, String text, int color) {
        int w = Math.max(8, left + panelW - 4 - x);
        TextWidget widget = new TextWidget(x, y, w, 11, UiLayout.fitText(text, w), color, true, 0, 0);
        addRenderableWidget(widget);
        texts.add(widget);
    }

    private static String shortName(String itemId) {
        String path = itemId.contains(":") ? itemId.substring(itemId.indexOf(':') + 1) : itemId;
        String s = path.replace('_', ' ');
        return s.length() > 9 ? s.substring(0, 9) : s;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);

        g.fill(left - 6, top - 6, left + panelW + 6, top + panelH + 6, 0xF0000000);
        g.fill(left - 4, top - 4, left + panelW + 4, top + panelH + 4, 0xFF1A2025);
        g.fill(left + splitX, top - 4, left + splitX + 1, top + panelH + 4, 0xFF4A5A60);

        // ⚠ 这里**不能**调 super.render()：Screen.render() 内部会再调一次
        // renderBackground()，那层全屏暗化会盖在刚刚画好的面板和文字上 ——
        // 表现就是"整个界面朦胧、白字发灰"，而 tooltip 清晰
        // （它在 renderWithTooltip 里最后画，盖不到）。
        // 4 个界面全中过这个坑，见 tools/check-screen-render.js。
        for (net.minecraft.client.gui.components.Renderable r : this.renderables) {
            r.render(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
