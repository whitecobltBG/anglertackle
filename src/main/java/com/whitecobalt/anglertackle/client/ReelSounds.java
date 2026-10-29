package com.whitecobalt.anglertackle.client;

import com.whitecobalt.anglertackle.client.sound.LoopSound;
import com.whitecobalt.anglertackle.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;

/**
 * 路亚的动作音：抛竿 / 收线（对抗、力竭）/ 放线 / 上鱼。
 *
 * <h2>素材来源（2026-10-29 用户给的 5 段视频）</h2>
 * <pre>
 *   抛竿声.mp4            → lure_cast
 *   对抗时拉线.mp4        → reel_fight
 *   鱼力竭时拉线.mp4      → reel_tired
 *   放线.mp4              → line_release
 *   拉上鱼（刚刚结束时）.mp4 → lure_land
 * </pre>
 *
 * <h2>⚠⚠ 2026-10-29 用户划的边界</h2>
 * "我一直只要求改力竭时收线的声音，其他的别擅作主张。"
 * ⇒ 这个类里**只有 reel_tired 相关的逻辑允许改**；抛竿 / 落水 / 咬钩 / 对抗收线 / 放线 / 上鱼
 * 一律保持既有行为，不要顺手"优化"。
 *
 * <h2>收线为什么用循环实例</h2>
 * 一次性 {@code playSound} 播的是整段素材且停不下来：切放线时旧音还拖着、快速收放会叠层。
 * 这里收线/放线各一个 {@link LoopSound}，状态一变立刻 {@code stopNow()}。
 */
public final class ReelSounds {

    private static final float FIGHT_VOLUME = 0.85F;
    private static final float TIRED_VOLUME = 0.80F;
    private static final float RELEASE_VOLUME = 0.55F;

    private static final float CAST_VOLUME = 0.90F;
    private static final float LAND_VOLUME = 0.95F;

    /** 收线音的淡入时长（秒）。 */
    private static final float REEL_FADE_IN_SECONDS = 0.3F;

    /** 力竭判定的消抖 tick：连续 N tick 才算真力竭（状态在阈值附近会抖）。 */
    private static final int TIRED_DEBOUNCE_TICKS = 10;

    /** 放线判定消抖（tick）：单帧掉输入不切放线。 */
    private static final int RELEASE_DEBOUNCE_TICKS = 3;

    /** 当前循环音（收线 / 放线各一个，互斥）。 */
    private static LoopSound reelLoop;
    private static LoopSound releaseLoop;
    /** 当前收线循环用的是哪个事件（力竭要换素材）。 */
    private static SoundEvent reelEvent;

    private static float smoothDanger;

    /** 连续处于力竭的 tick 数（用于消抖）。 */
    private static int tiredTicks;
    /** 本轮对抗是否已经进入过力竭（进入后锁定，不再切回对抗素材）。 */
    private static boolean tiredLatched;
    /** 连续"没在收线"的 tick 数（放线判定的消抖）。 */
    private static int releaseHoldTicks;

    private ReelSounds() {
    }

    /** 每 tick 调一次（{@link ClientEvents} 里）。 */
    public static void tick(ClientFishingState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            silence();
            return;
        }
        if (!state.inSession() || state.charging() || !lureGear(mc)) {
            silence();
            return;
        }
        smoothDanger += (state.dangerRatio() - smoothDanger) * 0.25F;

        if (!state.inFight()) {
            silence();
            return;
        }

        // 收线循环在对抗期间**不停**：没在收线只压低音量让位给放线（输入抖动不该打断波形）。
        tiredTicks = state.fatigued() ? tiredTicks + 1 : tiredTicks;
        if (!tiredLatched && tiredTicks >= TIRED_DEBOUNCE_TICKS) {
            tiredLatched = true;
        }

        SoundEvent want = tiredLatched ? ModSounds.REEL_TIRED.get() : ModSounds.REEL_FIGHT.get();
        float baseVolume = tiredLatched ? TIRED_VOLUME : FIGHT_VOLUME;
        float pitch = 0.98F + smoothDanger * 0.14F;

        if (reelLoop == null || reelEvent != want) {
            stopReel();
            reelEvent = want;
            reelLoop = new LoopSound(want, baseVolume, pitch);
            reelLoop.fadeIn(REEL_FADE_IN_SECONDS);
            mc.getSoundManager().play(reelLoop);
        }
        reelLoop.follow(state.reeling() ? baseVolume : baseVolume * 0.35F, pitch);

        // 放线循环：只在"确实没在收线"时叠上去（带 3 tick 消抖）。
        if (state.reeling()) {
            releaseHoldTicks = 0;
            stopRelease();
        } else {
            releaseHoldTicks++;
            if (releaseHoldTicks >= RELEASE_DEBOUNCE_TICKS && releaseLoop == null) {
                releaseLoop = new LoopSound(ModSounds.LINE_RELEASE.get(), RELEASE_VOLUME, 1.0F);
                mc.getSoundManager().play(releaseLoop);
            }
        }
    }

    /**
     * 状态跃迁时的一次性音效（在 {@code ClientFishingState.accept} 里调）。
     *
     * <p>抛竿与上鱼都是"一次"：抛竿在**进入 CASTING** 时（客户端蓄力没有 CHARGING 状态），
     * 上鱼在**进入 LANDING** 时。
     */
    public static void onStateChanged(int prev, int next) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        if (!lureGear(mc)) {
            return;   // 台钓：一声都不响
        }

        if (prev != ClientFishingState.STATE_CASTING && next == ClientFishingState.STATE_CASTING) {
            playOnce(mc, ModSounds.LURE_CAST.get(), CAST_VOLUME, 1.0F);
        }
        // 浮漂落水：服务端那一份在浮漂坐标上、音量又低，客户端再补一声（UI 通道）。
        if (prev != ClientFishingState.STATE_WAITING && next == ClientFishingState.STATE_WAITING) {
            playOnce(mc, net.minecraft.sounds.SoundEvents.FISHING_BOBBER_SPLASH, 0.185F, 1.18F);
        }
        if (next == ClientFishingState.STATE_LANDING && prev != ClientFishingState.STATE_LANDING) {
            silence();   // 上鱼瞬间把循环停掉，别和一次性音打架
            playOnce(mc, ModSounds.LURE_LAND.get(), LAND_VOLUME, 1.0F);
        }
    }

    /** 一次性音：走 SoundManager + UI 变体 ⇒ 与坐标无关、必定听得见。 */
    private static void playOnce(Minecraft mc, SoundEvent event, float volume, float pitch) {
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private static void stopReel() {
        if (reelLoop != null) {
            reelLoop.stopNow();
            reelLoop = null;
            reelEvent = null;
        }
    }

    private static void stopRelease() {
        if (releaseLoop != null) {
            releaseLoop.stopNow();
            releaseLoop = null;
        }
    }

    /** 两个循环都停（离开对抗/会话结束/切台钓）。 */
    public static void silence() {
        stopReel();
        stopRelease();
        smoothDanger = 0.0F;
        tiredTicks = 0;
        releaseHoldTicks = 0;
        tiredLatched = false;
    }

    /** 手上是不是路亚竿（服务端 lureSession 之外再兜一层）。 */
    private static boolean lureGear(Minecraft mc) {
        if (ClientFishingState.get().lureSession()) {
            return true;
        }
        return mc.player != null
                && mc.player.getMainHandItem().getItem()
                    instanceof com.whitecobalt.anglertackle.item.RodItem r
                && r.rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.LURE;
    }
}
