package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.item.PartStats;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 一根钓竿**当前装配状态**下算出来的最终属性。
 *
 * <p>装配信息存在物品的 {@code ROD_PARTS} 数据组件里（六槽各自的物品 id）。
 * 每次抛竿/改装后重新计算一次——属性计算是纯函数、开销可忽略。
 *
 * @param tensionCeiling  张力上限（绝对值，基准 100，顶配约 135）
 * @param tensionRecover  张力回复速率（点/tick，基准 1.6）
 * @param tensionGen      张力生成系数（越小涨得越慢）
 * @param staminaDrain    体力削减倍率
 * @param pullIdle        不挣扎时回拉速度（格/tick）
 * @param pullStruggle    挣扎时回拉速度（格/tick）
 * @param damping         压制阻尼（抑制鱼回气）
 * @param lineLength      线长上限（格）
 * @param hookWindowBonus 提竿窗口加成（秒）
 * @param floorDrainPerSec **保底体力削减**（点/秒，来自渔轮，用户 2026-10-06 新增）
 * @param maxWeightKg     承重上限（由竿身决定）
 * @param rodSpec         竿身规格（耐久系数/窗口等）
 */
public record RodStats(
        double tensionCeiling,
        double tensionRecover,
        double tensionGen,
        double staminaDrain,
        double pullIdle,
        double pullStruggle,
        double damping,
        double lineLength,
        double hookWindowBonus,
        double floorDrainPerSec,
        double maxWeightKg,
        RodItem.RodSpec rodSpec
) {

    /** 全基准（没装任何配件时的兜底）。 */
    public static RodStats neutral(RodItem.RodSpec spec) {
        return from(spec, PartStats.NEUTRAL, PartStats.NEUTRAL, PartStats.NEUTRAL);
    }

    public static RodStats from(RodItem.RodSpec rod, PartStats line, PartStats reel, PartStats hook) {
        return from(rod, line, reel, hook, 0.0);
    }

    /**
     * 带**保底体力削减**的装配计算。
     *
     * <p>为什么把保底值单独传进来、而不是从 {@code reel} 现算：
     * 见底的配件会被 {@link #statsOf} 换成 {@link PartStats#NEUTRAL}（阻尼 1.0），
     * 而"没装渔轮"和"渔轮见底"都必须得到 **0**（用户定案："装备了渔轮的路亚才有保底"）——
     * 光看 PartStats 分不出这两种情况和"真的装了一个阻尼 1.0 的轮子"。
     * 所以由 {@link #of} 拿着**真实堆栈**算好再传进来（见 {@link #reelFloorDrain}）。
     */
    public static RodStats from(RodItem.RodSpec rod, PartStats line, PartStats reel, PartStats hook,
                                double floorDrainPerSec) {
        return new RodStats(
                PartComposer.tensionCeiling(line, rod.tensionCeiling()),
                PartComposer.tensionRecover(line, rod.tensionRecover()),
                PartComposer.tensionGen(line),
                PartComposer.staminaDrain(reel, rod.staminaDrain()),
                PartComposer.pullIdle(reel),
                PartComposer.pullStruggle(reel),
                PartComposer.damping(reel),
                PartComposer.lineLength(line),
                0.0,   // 鱼钩已移除：提竿窗口不再有配件加成（只剩 hookWindowBase）
                floorDrainPerSec,
                rod.maxWeightKg(),
                rod
        );
    }

    /**
     * 渔轮提供的**保底体力削减**（点/秒）。
     *
     * <p>没装渔轮、或渔轮已经见底 → **0**（没有保底）。
     * 见底配件与 {@link #statsOf} 的口径一致："用光了就不再提供任何加成"。
     */
    public static double reelFloorDrain(ItemStack reelStack) {
        if (reelStack == null || reelStack.isEmpty() || isWornOut(reelStack)) {
            return 0.0;
        }
        return PartComposer.floorDrainPerSec(statsOf(reelStack));
    }

    /** 从一根实际物品读出装配并计算属性。 */
    public static RodStats of(ItemStack rodStack) {
        if (!(rodStack.getItem() instanceof RodItem rodItem)) {
            return neutral(RodItem.RodSpec.WOOD);
        }
        ModDataComponents.RodParts parts = rodStack.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null) {
            return neutral(rodItem.spec());
        }
        return from(
                rodItem.spec(),
                statsOf(parts.lineStack()),
                statsOf(parts.reelStack()),
                statsOf(parts.hookStack()),
                reelFloorDrain(parts.reelStack()));
    }

    /**
     * 从**玩家实际装着的那份堆栈**还原部件属性。
     *
     * <p>⚠ 必须用真堆栈。这里以前是"拿物品 id 新建一个满耐久堆栈再读属性"，
     * 于是磨损完全不参与计算：配件耐久见底（HUD 上就是 0/20）之后加成照样生效
     * —— 用户实测反馈"即使耐久用光配件加成貌似还会生效且显示不变"。
     *
     * <p>见底的配件返回 {@link PartStats#NEUTRAL}：不再提供任何加成。
     * tooltip 和实钓读的是同一份 {@link #of}，所以显示会跟着一起掉回基准。
     */
    private static PartStats statsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || isWornOut(stack)) {
            return PartStats.NEUTRAL;
        }
        PartStats stats = stack.get(ModDataComponents.PART_STATS.get());
        return stats == null ? PartStats.NEUTRAL : stats;
    }

    /**
     * 这个配件是不是已经用光了。
     *
     * <p>⚠ 先判 {@code isDamageableItem()} 再读上限：{@code getMaxDamage()} 对
     * **没有耐久概念的物品**返回 0，直接比较会得出"0 >= 0 → 已见底"，
     * 把那些配件的加成整片抹掉。
     */
    public static boolean isWornOut(ItemStack stack) {
        return stack != null && !stack.isEmpty()
                && stack.isDamageableItem()
                && stack.getDamageValue() >= stack.getMaxDamage();
    }

    /** 换算出的"能拉多久"提示（用于 UI 显示，让玩家直观感受配件的作用）。 */
    public String describeTensionWindow() {
        // 从满张力红线回到白区所需时间（秒）——张力回复越强越短
        double drop = tensionCeiling - PartComposer.BASE_TENSION_CEILING * 0.55;
        double seconds = drop / (tensionRecover * 20.0);
        return String.format("%.2fs", Math.max(0.1, seconds));
    }
}
