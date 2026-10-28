package com.whitecobalt.anglertackle.client;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.fishing.FishingManager;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.network.CastPayload;
import com.whitecobalt.anglertackle.network.RodModActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端事件：把键盘/鼠标状态翻译成服务端能懂的输入包。
 *
 * <p><b>按键方案（已定案）</b>：
 * <ul>
 *   <li><b>左键按住</b> = 收线；<b>松开</b> = 放线</li>
 *   <li>咬钩时 <b>左键</b> = 提竿（2 秒内不提竿，鱼逃离）</li>
 *   <li><b>A / D</b> = 控杆方向（正拉 / 反拉），不按 = 垂直（中立）</li>
 *   <li><b>潜行 + 松开右键</b> = 打开渔具改装界面</li>
 * </ul>
 *
 * <p><b>为什么没有快捷键</b>（用户 2026-10-02）：原来给改装界面绑了一个
 * <b>Z</b>，但实测按 Z 打不开（Z 是很多模组抢的键，绑了也用不上），
 * 用户定案"干脆删了这个功能"。现在只剩"潜行 + 松开右键"这一个入口 ——
 * 它不需要记快捷键，也不会和别的模组抢键。
 *
 * <p><b>左键拦截的边界</b>（docs/09-原版冲突与渲染优化.md §2 第 3 条）：
 * 只在<b>同时满足</b>"手持本模组钓竿 + 存在活跃会话 + 状态为提竿窗口或对抗中"时
 * 才吞掉左键，其他任何情况一律放行，绝不影响挖掘与攻击。
 */
@EventBusSubscriber(modid = AnglersTackle.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private static boolean hookSentThisWindow;
    private static boolean prevAttackDown;
    /** 上一 tick 玩家是否在"使用物品"（用于检测松开右键）。 */
    private static boolean wasUsingItem;
    /** 抑制期：刚发过开界面请求后，别再重复发。 */
    private static int modRequestCooldown;
    /** 蓄力诊断用：上次已打印的蓄力 tick 数，避免每 tick 刷屏。 */
    private static int chargeLogged;
    /** 对抗中是否已锁住移动（只用于"进入对抗"时提示一次）。 */
    private static boolean fishingMoveLocked;

    private ClientEvents() {
    }

    /**
     * 吞掉左键的挖掘/攻击：否则玩家一钓鱼就会把脚下的方块挖了。
     *
     * <p>用 {@code InputEvent.InteractionKeyMappingTriggered}，它发生在原版处理挖掘之前。
     */
    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        // 只处理左键；右键（use）必须原样放行，否则会吞掉抛竿
        if (!event.isAttack()) {
            return;
        }
        if (!shouldCapturePrimary()) {
            return;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    /** 是否应该把左键当作"收线/提竿"。 */
    private static boolean shouldCapturePrimary() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return false;
        }
        if (!holdingRod(mc)) {
            return false;
        }
        ClientFishingState state = ClientFishingState.get();
        // ⚠ 用 movementLocked()（FIGHT **+ 上鱼后的落地那一段**）而不是只认 FIGHT：
        //   上鱼后那 1 秒**故意**还接管着，这样"收线时按住的手"松不开也不会乱挖方块 /
        //   把角色带进水里（用户 2026-10-16 定案的功能）。
        return state.hookWindow() || state.movementLocked();
    }

    private static boolean holdingRod(Minecraft mc) {
        return mc.player != null
                && (mc.player.getMainHandItem().getItem() instanceof RodItem
                    || mc.player.getOffhandItem().getItem() instanceof RodItem);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        ClientFishingState state = ClientFishingState.get();
        state.tick();
        // 收线的声音（用户 2026-10-05）：原版音效，纯客户端表现，不走网络包
        ReelSounds.tick(state);

        boolean usingNow = mc.player.isUsingItem();
        if (modRequestCooldown > 0) {
            modRequestCooldown--;
        }

        // ---------------- 蓄力状态诊断 + 松手抛投 ----------------
        // 【核心】抛投就靠这里：检测"上一 tick 在用、这一 tick 没在用"的下降沿。
        //
        // 为什么不用服务端的 releaseUsing：实测日志证明它不会可靠触发——
        // 客户端松手时服务端收不到（原版只发了个"停止使用"标志位，
        // 弓有专门的兜底逻辑，自定义物品没有）。所以在客户端自己检测下降沿，
        // 把"我松手了 + 当时看哪"发给服务端，服务端再权威地算距离与落点。
        int remain = mc.player.getUseItemRemainingTicks();
        if (usingNow != wasUsingItem) {
            FishingManager.LOGGER.info("[垂纶/输入] 客户端使用状态变化：{} → {}（剩余 {}t，手持竿={}）",
                    wasUsingItem, usingNow, remain, holdingRod(mc));
            chargeLogged = 0;

            // 下降沿 + 手持钓竿 + 没有界面打开 = 松手抛投。
            // （加 screen 判断是为了避开"潜行松手开改装界面"那条路径：
            //   那里会主动调 stopUsingItem()，不该被当成抛投。）
            //
            // ⚠️ <b>已经有会话时绝不能再抛</b>：提前收回（右键）会让服务端调用
            // {@code stopUsingItem()}，客户端随之产生同一个下降沿，
            // 结果"收回的瞬间又抛了一杆"。会话还活着就说明这次下降沿是收回引起的，
            // 不是新的抛投意图 —— 这也是这个 bug 的根因。
            if (wasUsingItem && !usingNow && holdingRod(mc) && mc.screen == null) {
                if (ClientFishingState.get().inSession()) {
                    FishingManager.LOGGER.info("[垂纶/输入] 松手下降沿，但已有会话 → 判定为收回，不抛投");
                } else {
                    float pitch = mc.player.getXRot();
                    PacketDistributor.sendToServer(new CastPayload(pitch));
                    FishingManager.LOGGER.info("[垂纶/输入] 已上报松手：俯仰 {}°", String.format("%.1f", pitch));
                }
            }
        } else if (usingNow && holdingRod(mc)) {
            int charged = 72000 - remain;
            if (charged > 0 && charged % 20 == 0 && charged != chargeLogged) {
                chargeLogged = charged;
                FishingManager.LOGGER.info("[垂纶/输入] 客户端蓄力中：已蓄 {}t", charged);
            }
        } else {
            chargeLogged = 0;
        }

        // ---------------- 打开渔具改装界面 ----------------
        // 唯一的入口：潜行状态下"松开右键"（用 usingItem 的下降沿检测）。
        //
        // ⚠ 这里曾经还有一个"按 Z"的入口（ModKeys.OPEN_ROD_MOD）。用户 2026-10-02
        //   实测按 Z 没用 —— Z 是很多模组都在抢的键，绑了也轮不到我们，
        //   于是定案"干脆删了这个功能"。删掉的东西：
        //   ModKeys.java、这里那次 consumeClick()、两个语言键。
        //   想恢复的话要连着把这三处一起加回来，别只加回其中一处
        //   （只加回键位定义、忘了这里的检测，就是这次"按了没反应"的样子）。
        if (mc.screen == null && holdingRod(mc) && modRequestCooldown == 0) {
            boolean sneakRelease = mc.player.isShiftKeyDown() && wasUsingItem && !usingNow;
            if (sneakRelease) {
                if (usingNow) {
                    mc.player.stopUsingItem();   // 先把蓄力掐掉，否则界面会被立刻关掉
                }
                PacketDistributor.sendToServer(
                        new RodModActionPayload(RodModActionPayload.ACTION_REFRESH, (byte) 0, ""));
                modRequestCooldown = 10;
                // 给玩家一个即时反馈：即使界面因故没打开，也能知道请求发出去了
                mc.player.displayClientMessage(
                        Component.translatable("message.anglertackle.opening_rod_mod"), true);
            }
        }
        wasUsingItem = usingNow;

        // ---------------- 对抗中接管移动键 ----------------
        // 用户要求："钓鱼时移动键应该改成拉杆方向，结束后再改回来"。
        // 这里在对抗状态下把 WASD 的位移意图清空——按键本身不拦截
        // （不碰 KeyMapping，避免和别的模组打架），只是这一 tick 不产生移动。
        // 于是玩家的 A/D 就自然变成"控杆方向"（在 sendInput 里读的就是 A/D）。
        //
        // ⚠⚠ 2026-10-16 我**先把它当 bug 改错了**，用户当天纠正：这是**要的功能**，原话
        //   "上鱼后应该一秒内无法移动，不然有人收不住方向键会掉进水里"。
        //   ⇒ 判据必须是 `state.movementLocked()` = **FIGHT + 上鱼后的落地那一段**。
        //
        //   为什么它看起来像"鱼都进包了还锁着"：上鱼后服务端就停发同步包了，
        //   客户端要等 20 tick（1 秒）超时才离开 FIGHT ⇒ 那一秒**正是本功能要的**。
        //   没有它，玩家在收线动作还没结束时就恢复自由移动，按住方向键的手会把角色带走。
        //   要调这个时长就改服务端的 `landingTicks`（25~30 tick），别动这里的判据。
        if (state.movementLocked()) {
            // ⚠️ 必须在 assign 之后再清：原版这一 tick 刚根据按键算好左/前冲量，
            // 我们紧接着把它归零，玩家就真的动不了。
            // 之前只在 tick 里清一次，会被原版随后的重新赋值覆盖，所以看起来"还能走"。
            mc.player.input.leftImpulse = 0.0f;
            mc.player.input.forwardImpulse = 0.0f;
            // ⚠⚠ 2026-10-28（用户："台钓上鱼后视野会缩放"）：**只在对抗中**关冲刺。
            //   冲刺状态直接乘原版 FOV（×1.15），把它关掉视野会从 1.15 收回 1.0 ——
            //   看上去就是"视野缩了一下"。上鱼后那 1 秒只是"不许移动"（冲量已归零、
            //   服务端速度属性也压着），**完全不需要动冲刺状态**，动它就是白送一次视野跳变。
            if (state.inFight()) {
                mc.player.setSprinting(false);
            }
            // 连跳跃也拦掉，否则玩家能蹦着把鱼拽跑
            if (mc.player.input.jumping) {
                mc.player.input.jumping = false;
            }
            if (!fishingMoveLocked) {
                fishingMoveLocked = true;
                mc.player.displayClientMessage(
                        Component.translatable("message.anglertackle.fight_move_locked"), true);
            }
        } else if (fishingMoveLocked) {
            fishingMoveLocked = false;
        }

        boolean attackDown = mc.options.keyAttack.isDown();
        if (state.hookWindow()) {
            if (attackDown && !prevAttackDown && !hookSentThisWindow) {
                state.sendHook();
                hookSentThisWindow = true;
            }
        } else {
            hookSentThisWindow = false;
        }
        prevAttackDown = attackDown;

        // ---------------- 对抗输入 ----------------
        state.sendInput(mc);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFishingState.get().reset();
        // 竿尖是"上一帧手持渲染"记下来的：退出世界必须清掉，
        // 否则下一个世界的第一帧会用上一个世界/上一个位置的竿尖。
        com.whitecobalt.anglertackle.client.render.RodTipAnchor.clear();
        // 明目的指引线同理：换世界后旧坐标毫无意义（而且新世界的 gameTime 可能更小，
        // "负年龄"会让超时判断失效）。
        ClientOreSight.clear();
        hookSentThisWindow = false;
        prevAttackDown = false;
        wasUsingItem = false;
        modRequestCooldown = 0;
    }
}
