package com.whitecobalt.anglertackle.client.hud;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.client.ClientFishingState;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * 占位 HUD（M1 用，M6 换成正式贴图）。
 *
 * <p>严格按用户要求做**极简**（docs/02 §4.2）：
 * <ul>
 *   <li>蓄力条：底部一条 200×6 的条 + 绿区标记</li>
 *   <li>提竿提示：屏幕中部一个圆环 + "提竿"两个字</li>
 *   <li>张力条：260×8 细条，白/黄/红三段纯色；**不做暗角、不做震动、不做闪烁**</li>
 *   <li>鱼体力条：260×4，可关闭（这里先画，M2 接配置）</li>
 *   <li>挣扎方向：中央下方一个箭头</li>
 * </ul>
 *
 * <p>主要反馈仍是**鱼线颜色**（见 FishingLineRenderer），HUD 只是辅助。
 */
@EventBusSubscriber(modid = AnglersTackle.MOD_ID, value = Dist.CLIENT)
public final class FishingHud {

    private static boolean lastBarsVisible = false;
    private static long lastBarsSecond = -1L;

    private static final int BAR_WIDTH = 260;
    private static final int BAR_HEIGHT = 8;
    private static final int CHARGE_WIDTH = 200;

    private FishingHud() {
    }

    @SubscribeEvent
    public static void register(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.EXPERIENCE_BAR,
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(AnglersTackle.MOD_ID, "fishing_hud"),
                FishingHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }

        ClientFishingState state = ClientFishingState.get();
        if (!state.inSession()) {
            return;
        }

        float partial = delta.getGameTimeDeltaPartialTick(false);
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        if (state.charging()) {
            renderChargeBar(graphics, state, screenW, screenH);
        }

        if (state.hookWindow()) {
            renderHookPrompt(graphics, screenW, screenH, player);
        }

        // 咬钩之前（浮漂飞行 / 落水等咬钩）给一行提示：**该按哪个键**。
        // ⚠ 2026-10-16 用户定案：这个阶段原来屏幕上**什么都没有**，
        //   玩家盯着浮漂不知道自己该干什么（"鱼上钩时左键收杆"是别人告诉他的，
        //   游戏里从来没说过）。只在 hookWindow 之外显示 —— 那一刻有"！提竿！"。
        if (state.awaitingBite()) {
            renderAwaitBiteHint(graphics, screenW, screenH);
        }

        // ★诊断：状态条这一帧画没画、当时是什么状态（"一闪而过"只有它说得清）
        {
            boolean vis = state.tensionBarsVisible();
            if (vis != lastBarsVisible || (vis && (System.currentTimeMillis() / 1000L) != lastBarsSecond)) {
                com.whitecobalt.anglertackle.AnglersTackle.LOGGER.info(
                        "[垂纶/诊断·条] 张力条 {}：state={} 张力={} 会话={} 锁={}",
                        vis ? "开始画" : "停止画", state.rawStateForDebug(), state.tensionLerped(partial),
                        state.inSession(), state.movementLockTicksForDebug());
                lastBarsVisible = vis;
                lastBarsSecond = System.currentTimeMillis() / 1000L;
            }
        }
        if (state.tensionBarsVisible()) {
            renderTensionBar(graphics, state, screenW, screenH, partial);
            renderStaminaBar(graphics, state, screenW, screenH, partial);
            renderStruggleArrow(graphics, state, screenW, screenH);
            renderFatigueHint(graphics, state, screenW, screenH);
        }

        // 配件耐久：只要在钓鱼流程里就一直显示（抛投 / 等咬钩 / 对抗 / 上鱼都看得到），
        // 玩家要在"还没咬钩"时就知道线快断了，所以不能只在对抗阶段画。
        renderPartsDurability(graphics, state, screenW, screenH);

        // 环境（时段 / 天气 / 月相）：与配件耐久同理 —— 玩家要在**抛出去之前**就知道
        // 现在是什么时段天气，否则"夜里更容易上夜行鱼"这条规则他永远建立不起因果。
        renderEnvironment(graphics, state, screenW, screenH);
    }

    /**
     * 环境一行小字：如「夜晚 · 降雨 · 满月」。
     *
     * <p>见 {@code docs/24-时间天气月相策划.md}。刻意**只显示条件、不显示数字** ——
     * 玩家要建立的是"什么时候该来"，而不是"这一组占比涨了几个百分点"。
     *
     * <p>数据直接读客户端世界（自己世界的 {@code getDayTime()} / 天气 / 月相），
     * 不走网络：判定本身只在服务端做，这里显示的只是"天色"，两边天然一致。
     * 开关是 {@code environment.showCondition}（默认开）。
     */
    private static void renderEnvironment(GuiGraphics graphics, ClientFishingState state,
                                          int screenW, int screenH) {
        if (!com.whitecobalt.anglertackle.fishing.Tuning.envShowCondition()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        com.whitecobalt.anglertackle.fishing.Environment env =
                com.whitecobalt.anglertackle.fishing.Environment.now(player.level());
        if (env == null) {
            return;
        }
        String text = env.describe();
        int w = Minecraft.getInstance().font.width(text);
        // 画在底部居中偏下：张力条在中间、耐久面板在左下角，这一行放这儿不打架。
        graphics.drawString(Minecraft.getInstance().font, text,
                (screenW - w) / 2, screenH - 100, 0xFF9FB6C8, true);
    }

    /**
     * 配件耐久面板。
     *
     * <p>用户要求："钓鱼时能看到鱼竿各个配件耐久（有耐久定义的就显示）"。
     * 服务端只同步 {@code maxDamage > 0} 的配件（见 {@code FishingSession#partsDurabilitySnapshot}），
     * 所以渔轮和铅锤天然不出现，这里不用再判一次。
     *
     * <p>画在屏幕左下角，避免和居中的张力条 / 体力条打架。
     * 每行 = 槽位名 + 迷你耐久条 + "剩余/上限"数字；
     * 剩余比例低于 20% 时数字转红，低于 10% 再闪烁，提示"再拉就断"。
     */
    private static void renderPartsDurability(GuiGraphics graphics, ClientFishingState state,
                                              int screenW, int screenH) {
        int[] pd = state.partsDurability();
        if (pd == null || pd.length < 3) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        int rows = pd.length / 3;

        final int barW = 46, barH = 4, rowH = 11;
        int x = 6;
        int y = screenH - 12 - rows * rowH;

        // 底板：半透明黑，保证在亮色水面上也读得清
        int panelW = barW + 74;
        int panelH = rows * rowH + 12;
        graphics.fill(x - 3, y - 8, x - 3 + panelW, y - 8 + panelH, 0x90000000);

        String header = Component.translatable("hud.anglertackle.parts_durability").getString();
        graphics.drawString(font, header, x, y - 7, 0xFFE8E8E8, false);
        y += 4;

        for (int i = 0; i + 2 < pd.length; i += 3) {
            int slot = pd[i];
            int dmg = pd[i + 1];
            int max = Math.max(1, pd[i + 2]);
            int left = Math.max(0, max - dmg);
            float ratio = (float) left / max;

            String name = slotName(slot);
            graphics.drawString(font, name, x, y, 0xFFBFBFBF, false);

            // 迷你耐久条
            int bx = x + 34;
            graphics.fill(bx, y, bx + barW, y + barH, 0xFF1A1A1A);
            int fill = Math.round(barW * ratio);
            int color = ratio > 0.5f ? 0xFF4CE04C : ratio > 0.2f ? 0xFFE0D24C : 0xFFE04C4C;
            if (fill > 0) {
                graphics.fill(bx, y, bx + fill, y + barH, color);
            }

            String num = left + "/" + max;
            int numColor = ratio > 0.2f ? 0xFFDDDDDD : 0xFFFF5555;
            graphics.drawString(font, num, bx + barW + 4, y, numColor, false);

            y += rowH;
        }
    }

    /** 槽位序号 → 本地化名（与 {@code RodParts.SLOT_NAMES} 同序）。 */
    private static String slotName(int slot) {
        String[] names = {"rod", "line", "reel", "sinker", "lure", "bait"};
        String key = slot >= 0 && slot < names.length ? names[slot] : "rod";
        return Component.translatable("tooltip.anglertackle.slot." + key).getString();
    }

    /**
     * 疲劳期提示：反拉的最佳窗口。
     *
     * <p>刻意做得极简（用户要求"不用花里胡哨"）：鱼线本身会轻微变暗
     * （见 FishingLineRenderer），HUD 这里只给一行小字。
     */
    private static void renderFatigueHint(GuiGraphics graphics, ClientFishingState state,
                                          int screenW, int screenH) {
        if (!state.fatigued()) {
            return;
        }
        String text = Component.translatable("hud.anglertackle.fatigue").getString();
        int w = Minecraft.getInstance().font.width(text);
        graphics.drawString(Minecraft.getInstance().font, text,
                (screenW - w) / 2, screenH - 86, 0xFF8FCBF5, true);
    }

    // ---------------------------------------------------------------- 蓄力条
    /**
     * 蓄力条：按住右键时长 = 力度。
     *
     * <p>条子由**客户端本地计时**推进（服务端快照只作参考），
     * 这样即使网络抖动也不会出现"卡住/回退"。
     * 绿区 = 最佳力度，松手时落在绿区抛得最准。
     */
    private static void renderChargeBar(GuiGraphics graphics, ClientFishingState state, int screenW, int screenH) {
        float progress = state.chargeProgress();
        int x = (screenW - CHARGE_WIDTH) / 2;
        int y = screenH - 62;

        // 底槽
        graphics.fill(x - 2, y - 2, x + CHARGE_WIDTH + 2, y + 8, 0xC0000000);
        // 绿区（最佳力度 0.68~0.86）
        int gx1 = x + (int) (CHARGE_WIDTH * 0.68f);
        int gx2 = x + (int) (CHARGE_WIDTH * 0.86f);
        graphics.fill(gx1, y - 1, gx2, y + 7, 0xFF2F7A2F);
        // 过力区（>0.92 反而更散）
        int rx = x + (int) (CHARGE_WIDTH * 0.92f);
        graphics.fill(rx, y - 1, x + CHARGE_WIDTH, y + 7, 0xFF8B1A1A);

        // 已蓄力的部分
        int filled = (int) (CHARGE_WIDTH * progress);
        graphics.fill(x, y, x + filled, y + 6, 0xFFE4E4E4);
        // 当前位置竖线
        graphics.fill(x + filled, y - 3, x + filled + 1, y + 9, 0xFFFFFFFF);

        // 提示文字：蓄满后提示松手
        String hint = progress >= 1.0f
                ? Component.translatable("hud.anglertackle.release_to_cast").getString()
                : Component.translatable("hud.anglertackle.charging").getString();
        int w = Minecraft.getInstance().font.width(hint);
        graphics.drawString(Minecraft.getInstance().font, hint,
                (screenW - w) / 2, y - 14,
                progress >= 1.0f ? 0xFFFFD050 : 0xFFA8A8A8, true);
    }

    // ------------------------------------------------------------ 提竿提示
    private static void renderHookPrompt(GuiGraphics graphics, int screenW, int screenH, LocalPlayer player) {
        String text = Component.translatable("hud.anglertackle.hook_now").getString();
        int w = Minecraft.getInstance().font.width(text);
        graphics.drawString(Minecraft.getInstance().font, text,
                (screenW - w) / 2, screenH / 2 - 40, 0xFFFF5555, true);
    }

    /**
     * 等咬钩时的一行提示：「（鱼上钩时左键收杆）」。
     *
     * <p>⚠⚠ 2026-10-16 用户报了"**文本重叠**"（截图里那行字糊成一片）。两条原因：
     * <ul>
     *   <li>这行字第一版画在**屏幕中心下方**（{@code screenH / 2 + 24}）——
     *       那是准心正下方，正是**动作栏**（"！提竿！"／对抗状态行）与经验条那一片；
     *       三样东西挤在同一小段竖向空间、字号还都一样，看起来就是糊在一起 ✗；</li>
     *   <li>它还是**唯一一个没有底板**的 HUD 文字（张力条、耐久面板都有半透明黑底），
     *       压在亮水面／木板上本来就不清不楚。</li>
     * </ul>
     * ⇒ 现在挪到**左下角**（耐久面板上方，那儿是空的），并照耐久面板的做法加一条半透明黑底。
     * 判据（{@code check-fishing-camera.js}）也跟着改：不许再画在屏幕中心线上。
     */
    private static void renderAwaitBiteHint(GuiGraphics graphics, int screenW, int screenH) {
        String text = Component.translatable("hud.anglertackle.await_bite").getString();
        var font = Minecraft.getInstance().font;
        int w = font.width(text);
        int x = 6;                       // 与耐久面板同一列（左下角）
        int y = screenH - 118;           // 面板从 screenH-12-rows*11 往上长，这里是它上面的空白
        graphics.fill(x - 3, y - 4, x + w + 3, y + 10, 0x90000000);
        graphics.drawString(font, text, x, y, 0xFFCFCFCF, false);
    }

    // ------------------------------------------------------------ 张力细条
    private static void renderTensionBar(GuiGraphics graphics, ClientFishingState state,
                                         int screenW, int screenH, float partial) {
        int x = (screenW - BAR_WIDTH) / 2;
        int y = screenH - 46;

        // 三段底色：白 / 黄 / 红
        int whiteEnd = x + (int) (BAR_WIDTH * state.safeRatio());
        int yellowEnd = x + (int) (BAR_WIDTH * state.dangerRatio());

        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + BAR_HEIGHT + 1, 0xC0000000);
        graphics.fill(x, y, whiteEnd, y + BAR_HEIGHT, 0xFFE4E4E4);
        graphics.fill(whiteEnd, y, yellowEnd, y + BAR_HEIGHT, 0xFFD8A81E);
        graphics.fill(yellowEnd, y, x + BAR_WIDTH, y + BAR_HEIGHT, 0xFFC62828);

        // 当前张力位置（1px 深灰竖线，尽量不抢戏）
        float t = state.tensionLerped(partial);
        int marker = x + (int) (BAR_WIDTH * Math.min(1.0f, t));
        graphics.fill(marker, y - 3, marker + 1, y + BAR_HEIGHT + 3, 0xFF202020);

        // 红区倒计时：只在红区时出现一条 2px 深红横线
        if (t >= state.dangerRatio()) {
            int remain = (int) (BAR_WIDTH * (1.0f - state.redZoneProgress()));
            graphics.fill(x, y + BAR_HEIGHT + 2, x + remain, y + BAR_HEIGHT + 4, 0xFF8B1A1A);
        }
    }

    // ---------------------------------------------------------- 鱼体力条
    private static void renderStaminaBar(GuiGraphics graphics, ClientFishingState state,
                                         int screenW, int screenH, float partial) {
        int x = (screenW - BAR_WIDTH) / 2;
        int y = screenH - 34;
        graphics.fill(x - 1, y - 1, x + BAR_WIDTH + 1, y + 5, 0xA0000000);
        int w = (int) (BAR_WIDTH * Math.max(0.0f, 1.0f - state.staminaLerped(partial)));
        graphics.fill(x, y, x + w, y + 4, 0xFF2FA0A0);
    }

    // ---------------------------------------------------------- 挣扎方向
    private static void renderStruggleArrow(GuiGraphics graphics, ClientFishingState state,
                                            int screenW, int screenH) {
        int dir = state.struggleDir();
        String arrow = switch (dir) {
            case 1 -> "◀";   // StruggleDir.LEFT (ordinal 1, axis -1)
            case 2 -> "▶";   // StruggleDir.RIGHT (ordinal 2, axis +1)
            case 3 -> "▲";   // StruggleDir.STRAIGHT
            default -> null;
        };
        if (arrow == null) {
            return;
        }
        int color = state.sprinting() ? 0xFFFF5555 : 0xFFFFAA00;
        int w = Minecraft.getInstance().font.width(arrow);
        // ⚠ 2026-10-15 用户定案：方向箭头要**贴着准心**看 —— 原来画在底部（`screenH - 72`），
        //   玩家的眼睛得在"准心 / 张力条"之间来回跑，等看到箭头已经来不及反拉了。
        //   改法照用户原话："准心左是左箭头，右边是右箭头" ⇒ 按方向放到准心两侧，直冲放准心上方 ✓
        int cx = screenW / 2;
        int cy = screenH / 2;
        int off = 14;                       // 离准心多远：别盖住准心本身
        int ax = switch (dir) {
            case 1 -> cx - off - w;         // 向左游 ⇒ 准心左边
            case 2 -> cx + off;             // 向右游 ⇒ 准心右边
            default -> cx - w / 2;          // 直冲 ⇒ 准心正上方
        };
        int ay = dir == 3 ? cy - off - 8 : cy - 4;
        graphics.drawString(Minecraft.getInstance().font, arrow, ax, ay, color, true);
    }








}
