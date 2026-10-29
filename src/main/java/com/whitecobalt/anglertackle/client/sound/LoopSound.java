package com.whitecobalt.anglertackle.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * 可随时开/停的**循环**音（收线、放线用）。
 *
 * <h2>为什么不用 player.playSound() 反复播</h2>
 * 用户 2026-10-29："转放线时还有一段延迟才进入放线音效；短时间多次收放线会叠加多个收线音频"。
 * 根因是拿一次性音效当"状态音"：
 * <ul>
 *   <li>{@code playSound} 播的是**整段素材**（3.7 秒），停不下来 ⇒ 切到放线时旧音还在响，
 *       听感就是"延迟才切"；</li>
 *   <li>每次收线都新起一个实例 ⇒ 快速收放几次就叠成一片。</li>
 * </ul>
 * 这里改成**一个实例、循环播放、受控开关**：状态一变立刻 {@link #stopNow()}，
 * 音高/音量也能逐帧跟张力走。
 */
public final class LoopSound extends AbstractTickableSoundInstance {

    private float targetVolume;
    private float targetPitch;
    /** 淡入剩余 tick（0 = 已经淡完）。 */
    private int fadeTicks;
    /** 淡入总 tick（用来算进度）。 */
    private int fadeTotal;

    public LoopSound(SoundEvent event, float volume, float pitch) {
        super(event, SoundSource.PLAYERS, RandomSource.create());
        this.looping = true;      // 循环：素材播完接着播，直到我们叫停
        this.relative = true;     // 跟着玩家，不按坐标衰减（否则离远了就听不见）
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = volume;
        this.pitch = pitch;
        this.targetVolume = volume;
        this.targetPitch = pitch;
    }

    /**
     * 淡入：从静音在 {@code seconds} 秒内拉到目标音量。
     *
     * <p>用户 2026-10-29："给对抗收线声加一个操作淡入，比如点击左键开拉时有 0.5 秒淡入"。
     * 收线音是**循环**的，直接满音量起声会"啪"一下很硬；淡入让它像纺车轮慢慢吃上力。
     */
    public void fadeIn(float seconds) {
        this.fadeTotal = Math.max(1, Math.round(seconds * 20.0F));
        this.fadeTicks = this.fadeTotal;
        // ⚠⚠ 起点**不能是 0**：SoundEngine 在 play() 那一刻按音量决定要不要开声道，
        //   0 会被直接丢掉，之后 tick() 再怎么涨都没用 —— 表现就是"点了没声"。
        //   这里给目标音量的 2% 作为起点，听感上仍是静音，但声道能开起来。
        this.volume = Math.max(0.005F, targetVolume * 0.02F);
    }

    /** 平滑跟随目标音量/音高（张力每 tick 会抖，直接改会"唱歌"）。 */
    public void follow(float volume, float pitch) {
        this.targetVolume = volume;
        this.targetPitch = pitch;
    }

    @Override
    public void tick() {
        this.pitch += (targetPitch - this.pitch) * 0.25F;
        if (fadeTicks > 0) {
            // 淡入期间音量**由进度直接决定**，不走平滑（否则会先冲到一半再回落）
            fadeTicks--;
            float k = fadeTotal <= 0 ? 1.0F : 1.0F - (float) fadeTicks / (float) fadeTotal;
            // 同样留 2% 地板：淡入过程中任何一帧都不许回到 0（否则声道会被回收）
            this.volume = targetVolume * Math.max(0.02F, k);
            return;
        }
        this.volume += (targetVolume - this.volume) * 0.25F;
    }

    /** 立刻停（基类的 stop() 是 protected）。 */
    public void stopNow() {
        stop();
    }
}
