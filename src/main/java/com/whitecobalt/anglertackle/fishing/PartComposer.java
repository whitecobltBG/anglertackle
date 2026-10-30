package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.item.PartStats;

/**
 * 六槽配件的属性合成（docs/11-配件系统详表.md §2 的落地）。
 *
 * <p>合成规则：
 * <pre>
 *   最终值 = 基准 × [1 + (部件倍率 - 1) × 该项权重]
 *   张力上限 = 100 × f(鱼线, 0.60) × f(鱼竿, 0.40)     钳制 ×1.60
 *   张力回复 = 1.6 × f(鱼线, 0.60) × f(鱼竿, 0.40)     钳制 ×2.20
 *   体力削减 = 1.0 × f(渔轮, 0.60) × f(鱼竿, 0.40)     钳制 ×1.80
 * </pre>
 *
 * <p>为什么用"加权 + 钳制"而不是简单相乘：让玩家能清楚知道"这一项主要由谁决定"，
 * 同时防止堆满顶配后数值失控（钳制值可在配置里改）。
 */
public final class PartComposer {

    // ---- 权重（谁主导哪项属性）----
    // ★ 全部走 Tuning 的运行时读取，不缓存成 static final。
    //   缓存的话改配置必须重启游戏，调参循环太慢；详见 Tuning 的类注释。

    // ---- 硬上限（防止数值失控）----
    // 渔轮后期档位的目标值（用户定案）：回拉 2.3、体力削减 2.5、阻尼 2.7，
    // 所以上限必须抬到这些值之上，否则高档轮会被削成一个样。
    //
    // 鱼线六档定案后张力上限基准序列是 1.00 → 2.50（红线）。
    // 加权后：1 + (2.50-1)×0.60 = **1.90**；再乘竿身 40% 权重（合金竿 1.70 → 1.28）
    // 理论上限是 2.43。原来的 1.60 会把金线(1.72)和红线(1.90)**都钳到 1.60**，
    // 两档鱼线在张力上限上完全一样 —— 等于白做了两档。抬到 3.0（= 上限 300 点）。

    // ---- 基准 ----
    /** 张力上限基准（点）。做成配置项意义不大，固定值。 */
    public static final double BASE_TENSION_CEILING = 100.0;

    private PartComposer() {
    }

    /** 把部件倍率按权重向 1.0 收敛：w=1 时全额生效，w=0 时完全不生效。 */
    private static double weighted(double partMultiplier, double weight) {
        return 1.0 + (partMultiplier - 1.0) * weight;
    }

    /** 张力上限：鱼线 60% + 鱼竿 40% 加权后钳制。 */
    public static double tensionCeiling(PartStats line, double rodMultiplier) {
        double v = BASE_TENSION_CEILING
                * weighted(line.tensionCeiling(), Tuning.wLineOnTension())
                * weighted(rodMultiplier, Tuning.wRodOnTension());
        return Math.min(v, BASE_TENSION_CEILING * Tuning.capTensionCeiling());
    }

    /** 张力回复（点/tick）：放线时由红转白的速度就取决于它。 */
    public static double tensionRecover(PartStats line, double rodMultiplier) {
        final double base = Tuning.baseRecover();
        double v = base
                * weighted(line.tensionRecover(), Tuning.wLineOnRecover())
                * weighted(rodMultiplier, Tuning.wRodOnRecover());
        return Math.min(v, base * Tuning.capTensionRecover());
    }

    /** 张力生成系数：只由鱼线决定（越小涨得越慢）。 */
    public static double tensionGen(PartStats line) {
        return line.tensionGen();
    }

    /** 体力削减：渔轮 60% + 鱼竿 40%。 */
    public static double staminaDrain(PartStats reel, double rodMultiplier) {
        double v = weighted(reel.staminaDrain(), Tuning.wReelOnDrain())
                * weighted(rodMultiplier, Tuning.wRodOnDrain());
        return Math.min(v, Tuning.capStaminaDrain());
    }

    /**
     * 不挣扎时的回拉速度。
     *
     * <p>⚠️ 这里**不乘基准值**：渔轮的数值本身就是"倍率"，
     * 最终收线速度 = {@code 0.22 × pullIdle} 格/tick（见 FishingSession）。
     * 之前误乘了基准 0.100，导致高数值轮被压到基准以下、完全拉不动线。
     */
    public static double pullIdle(PartStats reel) {
        return Math.min(reel.pullIdle(), Tuning.capPull());
    }

    /** 挣扎时的回拉速度（同样不乘基准）。 */
    public static double pullStruggle(PartStats reel) {
        return Math.min(reel.pullStruggle(), Tuning.capPull());
    }

    public static double damping(PartStats reel) {
        return Math.min(reel.damping(), Tuning.capDamping());
    }

    /**
     * 压制阻尼对「收线速率抵抗」的**抵消比例**（0 ~ 上限）—— 判定与 tooltip 共用这一个函数。
     *
     * <p>公式 {@code min(抵消上限, max(0, 阻尼 − 1) × 每点阻尼抵消)}，默认 {@code ×0.5}、
     * 上限 0.75 —— 系数 0.5 是为金轮倒推的（2.50 → 0.75 正好贴上限）。
     *
     * <p>⚠ 提示与判定必须同源：以前这个式子只写在 {@code FishSpecies.reelResist} 里，
     * tooltip 想显示就得再抄一遍 —— 那正是"改了没反应"的老路。
     *
     * @param damping **已经夹过 capDamping** 的压制阻尼（用 {@link #damping(PartStats)} 取）
     */
    public static double reelResistOffset(double damping) {
        return Math.min(Tuning.reelResistDampingCap(),
                Math.max(0.0, (damping - 1.0) * Tuning.reelResistDampingOffset()));
    }

    /**
     * 压制阻尼折算出的**单轮扣力上限加成**（0 ~ 硬顶）—— 同样由判定与 tooltip 共用。
     *
     * <p>⚠ 这个式子原来是**两份**：{@code FishingSession.dampingBonusOnCap()}（判定）
     * 与 {@code TacklePartItem} 里的内联算式（提示）。注释写着"必须逐字一致"，
     * 但逐字一致靠人记 —— 现在两边都调这里，结构上不可能分家。
     *
     * @param damping        已经夹过 capDamping 的压制阻尼
     * @param roundCapBonus  这颗渔轮的**专属倍率**（大型纺车轮 1.25，其余 1.0）
     */
    public static double dampingBonusOnCap(double damping, double roundCapBonus) {
        double excess = Math.max(0.0, damping - 1.0);
        return Math.min(Tuning.capDampingBonus(),
                excess * Tuning.dampingToCapRatio() * roundCapBonus);
    }

    /**
     * **保底体力削减**（点/秒）—— 用户 2026-10-06 定案的新属性。
     *
     * <p>定义（用户原话）："拉鱼时每秒最少削减的鱼力，**不受上限限制**。由渔轮提供，
     * 根据压制阻尼转化而来。无论渔轮压制阻尼多少，该属性最小值为 5……
     * 装备了渔轮的路亚至少每秒保底对鱼削减 5 体力。上限 20。"
     *
     * <p>公式：{@code clamp(下限, 上限, 下限 + max(0, 阻尼 − 1) × 每点阻尼转化)}
     * —— 默认 {@code 5 + (阻尼−1) × 10}，夹在 [5, 20]。
     * 这个"每点阻尼 ×10"是**倒推**出来的：金轮（大型纺车轮）压制阻尼 2.50
     * → {@code 5 + 1.5 × 10 = **20.0**}，正好贴住上限，且五个轮的档位梯度干净：
     * 手线轮 1.07 → 5.7 / 纺车轮 1.20 → 7.0 / 鼓式轮 1.45 → 9.5 /
     * 水滴轮 1.40 → 9.0 / 大型纺车轮 2.50 → 20.0。
     *
     * <p>⚠ 用 {@link #damping(PartStats)}（已按 capDamping 夹过）而不是裸字段 ——
     * 这样它与 tooltip 上显示的"压制阻尼"永远是同一个数。
     *
     * <p>⚠ 这里**不判有没有装轮子**：没装渔轮时调用方应传 null 或直接不用这个值
     * （见 {@code RodStats.reelFloorDrain}）—— "装备了渔轮的路亚才有保底"是用户定的。
     */
    public static double floorDrainPerSec(PartStats reel) {
        if (reel == null) {
            return 0.0;
        }
        double base = Tuning.reelFloorDrainBase();
        double v = base + Math.max(0.0, damping(reel) - 1.0) * Tuning.reelFloorDrainPerDamping();
        return Math.max(base, Math.min(v, Tuning.reelFloorDrainCap()));
    }

    /** 线长上限（格）：麻线 30 → 顶级 70，按倍率换算。 */
    public static double lineLength(PartStats line) {
        return 30.0 * line.lineLength();
    }

}
