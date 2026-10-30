package com.whitecobalt.anglertackle.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 一个渔具配件（鱼线 / 渔轮 / 鱼钩 / 拟饵）的属性集合。
 *
 * <p>设计依据：docs/11-配件系统详表.md §2「属性合成公式」。
 * 每个属性都是**相对基准的倍率**（1.0 = 木竿/麻线基准），
 * 最终属性由 {@link com.whitecobalt.anglertackle.fishing.PartComposer} 按权重合成并钳制上限。
 *
 * <p>不可变 record + Codec：既存盘（DataComponent）也能走网络同步。
 *
 * <h2>这里曾经有一堆死字段（已删除）</h2>
 * 浮漂的 {@code sensitivity / previewVisibility / nightVisible}、
 * 鱼钩的 {@code bigFishBonus / nightBonus} 全都**只出现在 tooltip 上**，
 * 游戏逻辑一次都没读过 —— 玩家照着 tooltip 选配件，实际毫无作用。
 * 用户定案："去掉这个功能，目前没看到两配件作用"。
 * 于是**整个浮漂槽被移除**，鱼钩只保留唯一真正生效的「提竿窗口」。
 *
 * <p>防复发：{@code tools/check-dead-fields.js} 会遍历本 record 的每个字段，
 * 检查是否存在"只读来显示、没人真消费"的情况。
 *
 * @param tensionCeiling   张力上限倍率（越大越不容易断线）
 * @param tensionGen       张力生成系数（越小涨得越慢，越安全）
 * @param tensionRecover   张力回复倍率（放线时由红转白的速度）
 * @param lineLength       线长倍率（决定抛投射程上限）
 * @param pullIdle         不挣扎时回拉速度倍率
 * @param pullStruggle     挣扎时回拉速度倍率
 * @param staminaDrain     体力削减倍率（拉鱼时鱼掉体力多快）
 * @param damping          压制阻尼倍率（抑制鱼回气；高于 1 的部分另有转化，见 FishingSession）
 * @param tier             **品质档位**（1–6 = 白/绿/蓝/紫/金/红）
 *
 * <p><b>档位是 1 起，不是 0 起。</b>消费点在 {@link TacklePartItem}：
 * 只有 {@code partTier() > 0} 的配件（鱼线、拟饵）才走
 * {@code Quality.of(partTier() - 1)}；渔轮/铅锤的 0 表示"没有档位"，
 * 名字直接按原版 {@code Rarity} 上色。
 * 曾经这里（以及 ModItems 里）写成"0 白 1 绿 …"，照那个填会让整体差一档 ——
 * 镀金显示成金、红档永远出不来。{@code tools/check-part-tier-range.js} 现在会拦。
 */
public record PartStats(
        // ---- ② 鱼线 ----
        double tensionCeiling,
        double tensionGen,
        double tensionRecover,
        double lineLength,
        // ---- ③ 渔轮 ----
        double pullIdle,
        double pullStruggle,
        double staminaDrain,
        double damping,
        // ---- 通用（④ 铅锤的效果在 SinkerItem 上，不进这里）----
        int tier
) {

    /** 全 1.0 基准（木竿 / 麻线 / 手线轮 / 无铅锤），档位白。 */
    public static final PartStats NEUTRAL = new PartStats(
            1.0, 1.0, 1.0, 1.0,
            1.0, 1.0, 1.0, 1.0,
            0
    );

    public static final Codec<PartStats> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.optionalFieldOf("tension_ceiling", 1.0).forGetter(PartStats::tensionCeiling),
            Codec.DOUBLE.optionalFieldOf("tension_gen", 1.0).forGetter(PartStats::tensionGen),
            Codec.DOUBLE.optionalFieldOf("tension_recover", 1.0).forGetter(PartStats::tensionRecover),
            Codec.DOUBLE.optionalFieldOf("line_length", 1.0).forGetter(PartStats::lineLength),
            Codec.DOUBLE.optionalFieldOf("pull_idle", 1.0).forGetter(PartStats::pullIdle),
            Codec.DOUBLE.optionalFieldOf("pull_struggle", 1.0).forGetter(PartStats::pullStruggle),
            Codec.DOUBLE.optionalFieldOf("stamina_drain", 1.0).forGetter(PartStats::staminaDrain),
            Codec.DOUBLE.optionalFieldOf("damping", 1.0).forGetter(PartStats::damping),
            // 档位原先是借 sensitivity 字段存的（浮点），这里是真正的整数字段。
            // 旧存档里那个 "sensitivity" 键会被忽略（没有对应字段了），档位退回默认 0。
            Codec.INT.optionalFieldOf("tier", 0).forGetter(PartStats::tier)
    ).apply(inst, PartStats::new));

    public static final StreamCodec<FriendlyByteBuf, PartStats> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeDouble(s.tensionCeiling);
                buf.writeDouble(s.tensionGen);
                buf.writeDouble(s.tensionRecover);
                buf.writeDouble(s.lineLength);
                buf.writeDouble(s.pullIdle);
                buf.writeDouble(s.pullStruggle);
                buf.writeDouble(s.staminaDrain);
                buf.writeDouble(s.damping);
                buf.writeVarInt(s.tier);
            },
            buf -> new PartStats(
                    buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(),
                    buf.readVarInt())
    );

    /** 便捷构造：只改鱼线相关的四个值，档位默认白。 */
    public static PartStats line(double ceiling, double gen, double recover, double length) {
        return line(ceiling, gen, recover, length, 0);
    }

    /**
     * 便捷构造：鱼线四值 + **品质档位**。
     *
     * <p>为什么要显式带档位：原版 {@code Rarity} 只有四档（COMMON/UNCOMMON/RARE/EPIC），
     * 而鱼线有六档（白绿蓝紫金红）。不带档位的话，
     * <b>紫/金/红三档的线在原版稀有度上全挤在 EPIC，名字全是同一个紫色</b>，
     * 玩家在背包里分不出碳氟线和钛合金线。
     *
     * @param tier 品质档位（1–6 = 白/绿/蓝/紫/金/红）
     */
    public static PartStats line(double ceiling, double gen, double recover, double length, int tier) {
        return new PartStats(ceiling, gen, recover, length, 1, 1, 1, 1, tier);
    }

    /** 便捷构造：只改渔轮相关的四个值。 */
    public static PartStats reel(double pullIdle, double pullStruggle, double drain, double damping) {
        return reel(pullIdle, pullStruggle, drain, damping, 0);
    }

    /**
     * 带档位的渔轮（用户 2026-10-05："渔轮有品质记得不要乱"）。
     *
     * <p>以前渔轮 tier 恒为 0 = "没有档位"，所以名字颜色只能用原版 {@code Rarity} ——
     * 而 Rarity 只有四档，金/红两档的轮子会撞成同一个紫色。现在补上档位，
     * {@code TacklePartItem.quality()} 那条 {@code Quality.of(partTier() - 1)} 就自动生效。
     *
     * <p>⚠ 档位是 **1 起算**（1白 … 6红），与鱼线/拟饵同一套；0 仍然表示"没有档位"。
     */
    public static PartStats reel(double pullIdle, double pullStruggle, double drain, double damping, int tier) {
        return new PartStats(1, 1, 1, 1, pullIdle, pullStruggle, drain, damping, tier);
    }

    /**
     * 便捷构造：鱼钩。
     *
     * <p>曾经还有"大个体权重"和"夜间效率"两个参数——它们**没有任何实现**，
     * 已随浮漂槽一起删除（用户定案）。现在鱼钩只提供提竿窗口加成。
     */
    /**
     * 便捷构造：拟饵。
     *
     * <p>拟饵不参与张力/回拉计算，它决定的是"能钓多大的鱼"。
     * 档位同时决定饵料下限（能过滤掉多低级的鱼，见 FishingSession.baitMinTier）。
     *
     * @param tier 品质档位（1–6）
     */
    public static PartStats lure(int tier) {
        return new PartStats(1, 1, 1, 1, 1, 1, 1, 1, tier);
    }

    /** 配件的**品质档位**（1–6 = 白/绿/蓝/紫/金/红）。 */
    public int partTier() {
        return tier;
    }
}
