package com.whitecobalt.anglertackle.registry;

import com.whitecobalt.anglertackle.AnglersTackle;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 声音注册：路亚的五个动作音。
 *
 * <h2>素材来源（2026-10-29 用户提供）</h2>
 * 全部来自用户自己录的 5 段视频，用 ffmpeg 抽轨 → 去首尾静音 → 响度归一 → Ogg Vorbis：
 * <pre>
 *   抛竿声.mp4            → lure_cast
 *   对抗时拉线.mp4        → reel_fight
 *   鱼力竭时拉线.mp4      → reel_tired
 *   放线.mp4              → line_release
 *   拉上鱼（刚刚结束时）.mp4 → lure_land
 * </pre>
 * 之前那版是程序化合成（噪声整形 + 阻尼正弦），用户听过直接否掉：
 * "什么逆天音效……后面其他的音效完全不是我想要的"。素材型的才像真东西。
 *
 * <h2>三处必须同名</h2>
 * 注册 id、{@code assets/anglertackle/sounds.json} 的键、{@code sounds/<name>.ogg} 文件名，
 * 三者必须完全一致 —— 少一处就是"游戏里没声音但也不报错"。守着的是 {@code tools/check-sounds.js}。
 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, AnglersTackle.MOD_ID);

    /** 抛竿：竿梢甩出的那一下。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> LURE_CAST = register("lure_cast");

    /** 对抗中收线（拉得动的时候）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL_FIGHT = register("reel_fight");

    /** 鱼力竭后的收线（更轻快、线更松）。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> REEL_TIRED = register("reel_tired");

    /** 放线：不按住收线、线被鱼拽出去。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> LINE_RELEASE = register("line_release");

    /** 上鱼：拉出水的那一刻。 */
    public static final DeferredHolder<SoundEvent, SoundEvent> LURE_LAND = register("lure_land");

    private ModSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(
                        ResourceLocation.fromNamespaceAndPath(AnglersTackle.MOD_ID, name)));
    }

    public static void register(IEventBus bus) {
        SOUNDS.register(bus);
    }
}
