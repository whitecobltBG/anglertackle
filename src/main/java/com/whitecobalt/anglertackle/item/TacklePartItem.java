package com.whitecobalt.anglertackle.item;

import com.whitecobalt.anglertackle.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * 渔具配件（鱼线 / 渔轮 / 铅锤 / 拟饵）。
 *
 * <p>和鱼获共用 {@link Quality} 与 {@link HasQuality}：
 * 名字按品质着色，tooltip 里有一行"品质：X"。
 */
public class TacklePartItem extends Item implements HasQuality {

    /**
     * 配件槽位。
     *
     * <p><b>曾经有 ⑥ 浮漂槽，已删除</b>（用户定案："去掉这个功能，目前没看到两配件作用"）。
     * 浮漂的三个属性（灵敏度 / 预告可见度 / 夜间可见）**没有任何实现**，
     * 只在 tooltip 上显示数字，玩家装上完全没作用——与其留个假配件，不如拿掉。
     * 鱼钩后来也整条删除（2026-10-30）：④ 号槽改为**铅锤**，只影响水层权重，仅路亚可用。
     */
    public enum Slot {
        /** ② 鱼线：张力上限、张力生成系数、张力回复、线长 */
        LINE,
        /** ③ 渔轮：回拉速度（常态/挣扎）、体力削减、压制阻尼 */
        REEL,
        /** ④ 铅锤：只影响"哪一水层的鱼更容易上钩"（仅路亚可用） */
        SINKER,
        /** ⑤ 拟饵：路亚专用，决定能钓什么体型与饵料下限（有耐久，可重复使用） */
        LURE
    }

    /** 配件是否属于"路亚专用"（拟饵）。 */
    public boolean isLure() {
        return slot == Slot.LURE;
    }

    private final Slot slot;

    /**
     * 「单轮扣力上限」的**配件专属倍率**。默认 1.0（不影响）。
     *
     * <p>用户定案：只有大型纺车轮（俗称"金轮"）额外 +25%。
     * 它的阻尼 2.50 本来就给出 +75% 的单轮扣力上限，再乘 1.25 → +93.75%。
     *
     * <p>为什么不塞进 {@code PartStats}：那是六个槽位共用的**序列化** record
     *（有 Codec 与 StreamCodec），为一个配件的专属特性去改它，会让所有
     * 存档格式跟着动。放物品类上则天然按物品区分，不用序列化。
     */
    private final double roundCapBonus;

    public TacklePartItem(Slot slot, Properties properties) {
        this(slot, properties, 1.0);
    }

    public TacklePartItem(Slot slot, Properties properties, double roundCapBonus) {
        super(properties);
        this.slot = slot;
        this.roundCapBonus = roundCapBonus;
    }

    /** 「单轮扣力上限」的配件专属倍率（默认 1.0）。 */
    public double roundCapBonus() {
        return roundCapBonus;
    }

    public Slot slot() {
        return slot;
    }

    /**
     * 配件品质。
     *
     * <p><b>鱼线 / 拟饵：</b>读它们自己的档位（{@code PartStats.partTier()}），
     * 映射到白/绿/蓝/紫/金/红。这两类的档位是核心属性，品质颜色就该是这个。
     * 原版 {@code Rarity} 只有四档，紫/金/红会全挤在 EPIC 变成同一个紫色，
     * 玩家在背包里根本分不出碳氟线和钛合金线——所以按档位走。
     *
     * <p><b>⚠ 档位是 1-based（白=1 … 红=6），而 {@link Quality#of(int)} 收的是 0-based。</b>
     * 必须减 1。踩过一次：直接传 {@code partTier()} 会让**所有线/饵的颜色都高一档**
     * ——碳氟线（档4=紫）显示成金、钛合金线（档6=红）越界被夹到红。
     * 玩家在游戏里一眼就看出来了。
     *
     * <p><b>其它配件：</b>品质在定义时固定，用原版稀有度换算，
     * 金/红档（目前只有大型纺车轮）单独指定。
     */
    @Override
    public Quality quality(ItemStack stack) {
        if (slot == Slot.LURE || slot == Slot.LINE) {
            PartStats stats = stack.get(ModDataComponents.PART_STATS.get());
            if (stats != null && stats.partTier() > 0) {
                return Quality.of(stats.partTier() - 1);
            }
        }
        if (stack.is(com.whitecobalt.anglertackle.registry.ModItems.HEAVY_REEL.get())) {
            return Quality.GOLD;
        }
        return Quality.fromRarity(stack.getRarity());
    }

    /** 配件名按品质着色——和鱼获走同一条路径。 */
    @Override
    public Component getName(ItemStack stack) {
        return HasQuality.applyQuality(super.getName(stack), this, stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        PartStats stats = stack.get(ModDataComponents.PART_STATS.get());
        if (stats == null) {
            lines.add(Component.translatable("tooltip.anglertackle.part.no_stats").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }

        // 品质行：和鱼获格式一致（"品质：金"），玩家学一次就够
        lines.add(qualityLine(stack));

        lines.add(Component.translatable("tooltip.anglertackle.slot." + slot.name().toLowerCase())
                .withStyle(ChatFormatting.GRAY));

        // 只有实际影响该槽位的属性才显示，避免玩家被无关数字淹没
        switch (slot) {
            case LINE -> {
                lines.add(line("tension_ceiling", stats.tensionCeiling()));
                lines.add(line("tension_gen", stats.tensionGen()));
                lines.add(line("tension_recover", stats.tensionRecover()));
                lines.add(line("line_length", stats.lineLength()));
                lines.add(durabilityLine(stack));
            }
            case REEL -> {
                lines.add(line("pull_idle", stats.pullIdle()));
                lines.add(line("pull_struggle", stats.pullStruggle()));
                lines.add(line("stamina_drain", stats.staminaDrain()));
                lines.add(line("damping", stats.damping()));
                // 压制阻尼的第二重收益：高于 1.0 的部分，按折算比例转成单轮拉线扣力上限。
                // 必须显式写出来——否则玩家只看得到它"抑制回气"，会以为高档轮子没差别。
                //
                // ⚠ 这里原来写的是 `Math.min(0.85, ... * 0.50)` —— **三个数全写死了**：
                //   ① 折算比例 0.50 = dampingToCapRatio，② 硬顶 0.85 = capDampingBonus，
                //      两个都是配置项。写死的话玩家在调参器里改了、提示却纹丝不动，
                //      会以为配置没生效（"改了没反应"是最劝退的那种 bug）。
                //   ③ 本配件专属的 roundCapBonus 也要乘：大型纺车轮（"金轮"）是 1.25，
                //      漏了就会出现**界面显示 +75%、实际判定 +93.75%** —— 手感与提示对不上。
                //
                // 公式**只有一份**：PartComposer.dampingBonusOnCap，判定侧
                // （FishingSession.dampingBonusOnCap）调的是同一个函数 ——
                // 以前两边各写一遍、靠注释"必须逐字一致"，那是"改了没反应"的老路。
                double dampingBonus = com.whitecobalt.anglertackle.fishing.PartComposer
                        .dampingBonusOnCap(stats.damping(), roundCapBonus);
                if (dampingBonus > 0.0) {
                    lines.add(line("damping_bonus", dampingBonus * 100.0));
                }
                // 收线速率抵抗的**抵消**（用户 2026-10-06 定案）：
                // 双拉属性超过阈值 1.2 的部分会被鱼按档位打折（白 5% … 红 75%），
                // 而压制阻尼能抵消掉其中一部分。不写这一行，玩家只会觉得
                // "我的水滴轮怎么没比手线轮强多少"，却看不出原因。
                // 同样调用 PartComposer 的函数，与判定同源。
                double resistOffset = com.whitecobalt.anglertackle.fishing.PartComposer
                        .reelResistOffset(com.whitecobalt.anglertackle.fishing.PartComposer.damping(stats));
                if (resistOffset > 0.0) {
                    lines.add(Component.translatable("tooltip.anglertackle.reel_resist_offset",
                            String.format("%.0f", resistOffset * 100.0)).withStyle(ChatFormatting.AQUA));
                }
                // 保底体力削减（用户 2026-10-06 定案）：压制阻尼的**第三重**收益。
                // ⚠ 这里**调用** PartComposer 的同一个函数，不重写公式 ——
                //   上面 damping_bonus 那段吃过一次亏（三个数全写死，调参器改了提示不动）。
                //   本属性是"拉鱼时每秒最少削减的鱼力、不受单轮上限限制"，
                //   一句话写在提示里，玩家才知道高档轮子的价值不止"抑制回气"。
                double floorDrain = com.whitecobalt.anglertackle.fishing.PartComposer.floorDrainPerSec(stats);
                if (floorDrain > 0.0) {
                    lines.add(Component.translatable("tooltip.anglertackle.floor_drain",
                            String.format("%.1f", floorDrain)).withStyle(ChatFormatting.AQUA));
                }
            }
            case SINKER -> {
                // 只列"真的改了的层"：全 1.0 的层写出来只会让 tooltip 变长。
                if (this instanceof com.whitecobalt.anglertackle.item.SinkerItem sinker) {
                    for (com.whitecobalt.anglertackle.fishing.FishLayers.Layer layer
                            : com.whitecobalt.anglertackle.fishing.FishLayers.Layer.values()) {
                        double mult = sinker.bonus(layer);
                        if (mult != 1.0) {
                            lines.add(Component.translatable("tooltip.anglertackle.sinker_bonus",
                                    Component.translatable(layer.langKey()),
                                    Math.round((mult - 1.0) * 100.0)));
                        }
                    }
                }
            }
            case LURE -> {
                lines.add(durabilityLine(stack));
                // ★ 专攻词条：这个拟饵认哪些鱼的习性（见 tools/fish-habits.json）。
                //
                //   为什么必须写在 tooltip 上：词条是**双向**的 —— 鱼在图鉴里显示
                //   「胆小多疑」，拟饵在这里显示「专攻：胆小多疑」，玩家才能把两边连起来。
                //   只写一边，这套机制就只剩"看攻略才会用"。
                //
                //   词条名的中英由语言文件 habit.anglertackle.<id> 提供，
                //   check-fish-habits.js 会断言每个词条 zh/en 两条 key 都在。
                int habitMask = com.whitecobalt.anglertackle.fishing.FishHabits.lureMask(itemId(stack));
                if (habitMask != 0) {
                    java.util.List<String> names = new java.util.ArrayList<>();
                    for (com.whitecobalt.anglertackle.fishing.FishHabits.Habit h
                            : com.whitecobalt.anglertackle.fishing.FishHabits.habitsOfMask(habitMask)) {
                        names.add(Component.translatable(h.langKey()).getString());
                    }
                    lines.add(Component.translatable("tooltip.anglertackle.lure_habit",
                            String.join(" / ", names)).withStyle(ChatFormatting.GOLD));
                }
                // 说明文字：一句话告诉玩家"这饵什么时候用"。
                // 文案全在语言文件里（tooltip.anglertackle.<id>.desc），
                // 缺 key 由 check-fish-habits.js 在构建期拦住，不在运行期显示原始 key。
                lines.add(Component.translatable("tooltip.anglertackle." + itemId(stack) + ".desc")
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
    }

    /** 物品的注册名（不带命名空间），用来拼语言键。 */
    private static String itemId(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
    }

    private static Component line(String key, double value) {
        return Component.translatable("tooltip.anglertackle." + key, String.format("%.2f", value))
                .withStyle(ChatFormatting.AQUA);
    }

    private static Component durabilityLine(ItemStack stack) {
        // 鱼线是消耗品：耐久 = 还能承受多少次咬钩（docs/03-数值表.md §3）
        int left = stack.getMaxDamage() - stack.getDamageValue();
        ChatFormatting color = left <= 5 ? ChatFormatting.RED
                : left <= 15 ? ChatFormatting.YELLOW
                : ChatFormatting.GREEN;
        return Component.translatable("tooltip.anglertackle.bites_left", left)
                .withStyle(color);
    }
}
