package com.whitecobalt.anglertackle.registry;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.fishing.FishChunkData;
import com.whitecobalt.anglertackle.item.BaitItem;
import com.whitecobalt.anglertackle.item.ChumItem;
import com.whitecobalt.anglertackle.item.CodexBookItem;
import com.whitecobalt.anglertackle.item.CookbookItem;
import com.whitecobalt.anglertackle.item.DishItem;
import com.whitecobalt.anglertackle.item.FishChunkItem;
import com.whitecobalt.anglertackle.item.FishItem;
import com.whitecobalt.anglertackle.item.FishKnifeItem;
import com.whitecobalt.anglertackle.item.PartStats;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.item.SeasoningItem;
import com.whitecobalt.anglertackle.item.SinkerItem;
import com.whitecobalt.anglertackle.item.TacklePartItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 物品注册。
 *
 * <p>M1 只注册"手感原型"需要的最小集合：两种木竿 + 一条鱼 + 六槽各一个基准配件。
 * 品质阶梯（铁/金/钻/合金、6 档鱼线…）在 M2 按 docs/11-配件系统详表.md 批量加。
 */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(AnglersTackle.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, AnglersTackle.MOD_ID);

    // ------------------------------------------------------------------ 钓竿
    // 五档材质 × 两种竿型 = 10 根。材质决定耐久/承重/窗口/张力相关倍率，
    // 竿型决定玩法（台钓看浮漂 / 路亚靠拟饵）。
    /** 木台钓竿：看浮漂。新手的第一根竿（可合成 / 找老柯免费领）。 */
    public static final DeferredItem<RodItem> WOOD_FLOAT_ROD = rod("wood_float_rod",
            RodItem.RodType.FLOAT, RodItem.RodSpec.WOOD);
    public static final DeferredItem<RodItem> WOOD_LURE_ROD = rod("wood_lure_rod",
            RodItem.RodType.LURE, RodItem.RodSpec.WOOD);

    /** 铁竿：耐久更高、承重 20kg，耐久损耗系数 0.85。 */
    public static final DeferredItem<RodItem> IRON_FLOAT_ROD = rod("iron_float_rod",
            RodItem.RodType.FLOAT, RodItem.RodSpec.IRON);
    public static final DeferredItem<RodItem> IRON_LURE_ROD = rod("iron_lure_rod",
            RodItem.RodType.LURE, RodItem.RodSpec.IRON);

    /** 金竿：提竿窗口最长（+0.25s），但耐久最低——技术流专用。 */
    public static final DeferredItem<RodItem> GOLD_FLOAT_ROD = rod("gold_float_rod",
            RodItem.RodType.FLOAT, RodItem.RodSpec.GOLD);
    public static final DeferredItem<RodItem> GOLD_LURE_ROD = rod("gold_lure_rod",
            RodItem.RodType.LURE, RodItem.RodSpec.GOLD);

    /** 钻石竿：承重 40kg，综合性能第一个大台阶。 */
    public static final DeferredItem<RodItem> DIAMOND_FLOAT_ROD = rod("diamond_float_rod",
            RodItem.RodType.FLOAT, RodItem.RodSpec.DIAMOND);
    public static final DeferredItem<RodItem> DIAMOND_LURE_ROD = rod("diamond_lure_rod",
            RodItem.RodType.LURE, RodItem.RodSpec.DIAMOND);

    /** 下界合金竿：承重 999kg，能钓任何鱼。 */
    public static final DeferredItem<RodItem> NETHERITE_FLOAT_ROD = rod("netherite_float_rod",
            RodItem.RodType.FLOAT, RodItem.RodSpec.NETHERITE);
    public static final DeferredItem<RodItem> NETHERITE_LURE_ROD = rod("netherite_lure_rod",
            RodItem.RodType.LURE, RodItem.RodSpec.NETHERITE);

    private static DeferredItem<RodItem> rod(String name, RodItem.RodType type, RodItem.RodSpec spec) {
        return ITEMS.register(name, () -> new RodItem(type, spec,
                new Item.Properties()
                        .stacksTo(1)
                        .durability(spec.maxDurability())
                        // 出生就带上"空装配"组件：
                        // 否则新竿的 ROD_PARTS 是 null，tooltip 会显示"未初始化"，
                        // 玩家会以为改装坏了（实际是还没装过东西）。
                        .component(ModDataComponents.ROD_PARTS.get(), ModDataComponents.RodParts.EMPTY)));
        // ⚠ 竿的"附魔亲和度"不在这里 —— 1.21.1 没有 .enchantable(...)（那是 1.21.2+ 的组件），
        //   而是在 RodItem 里重写 getEnchantmentValue()（原版鱼竿也是这么干的）。
        //   亲和度 0 的表现是"附魔台对竿什么都不出"（用户 2026-10-02 报的正是这个）。
    }

    // ------------------------------------------------------------------ ② 鱼线
    // 参数顺序：张力上限 / 张力生成 / 张力回复 / 线长倍率
    // 线长倍率 × 30 = 实际长度（米）：麻 30 / 棉 35 / 尼龙 45 / 碳氟 55 / PE 65
    // 耐久 = 还能承受多少次咬钩（每次咬钩固定 -1，见 docs/03-数值表.md §3）

    // ================================================================== ② 鱼线
    // 六档（用户定案，数值逐条指定）：
    //   档位  耐久  张力上限  张力生成  张力回复  线长
    //   白    50     1.00      1.00      1.00     30 米
    //   绿    50     1.20      0.91      1.15     35 米
    //   蓝    40     1.28      0.95      1.30     45 米
    //   紫    35     1.54      0.81      1.36     55 米
    //   金    30     1.94      0.76      1.44     65 米
    //   红    30     2.20      0.58      1.76     70 米
    //
    // ★ 蓝档及以上在原始值上做过一次**整体弱化 12%**（用户定案，
    //   口径是"每个品质张力上限回退 12%、张力生成提升 12%、张力回复降低 12%"）：
    //     上限 ×0.88、回复 ×0.88、生成 ×1.12
    //   原始值 → 调整后：
    //     蓝 1.45/0.85/1.48 → 1.28/0.95/1.30
    //     紫 1.75/0.72/1.54 → 1.54/0.81/1.36
    //     金 2.20/0.68/1.64 → 1.94/0.76/1.44
    //     红 2.50/0.52/2.00 → 2.20/0.58/1.76
    //   ⚠ 注意"生成 ×1.12"是**变差**（生成 = 张力涨得快 = 更危险），
    //     所以三项调整方向一致：都用同一个 12% 把高档线往下压一档。
    //
    // 张力上限/生成/回复在 PartComposer 里按权重合成：
    //   张力上限 = 100 × [1+(线-1)×0.60] × [1+(竿-1)×0.40]，钳制 CAP_TENSION_CEILING
    //   张力生成 = 直接取线的数值（越低越安全）
    //   线长     = 30 × 线倍率
    // 名字颜色由 TacklePartItem.quality() 的档位表决定（原版 Rarity 只有四档）。

    /**
     * 麻线：白档基准。张力上限最低、最容易断，线也不长。
     *
     * <p>用户 2026-10-01 定案微调（从全 1.00 的纯基准往上抬一点）：
     * 张力上限 1.00 → <b>1.08</b>、张力生成 1.00 → <b>0.97</b>、张力回复 1.00 → <b>1.05</b>。
     * 线长与耐久不变（×1.00 / 50）。
     * ⚠ 这三项改了以后**白档不再是"全是 1.00 的基准"** —— 但
     * {@code PartStats.NEUTRAL}（配件见底/缺失时用的中性值）**仍然保持 1.00**：
     * 它的语义是"没有加成"，不是"白档"。见 check-worn-part-stats.js。
     */
    public static final DeferredItem<TacklePartItem> HEMP_LINE = ITEMS.register("hemp_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(50, Rarity.COMMON, PartStats.line(1.08, 0.97, 1.05, 1.00, 1))));

    /** 棉线：绿档。回复略好、线略长，仍然是新手档。 */
    public static final DeferredItem<TacklePartItem> COTTON_LINE = ITEMS.register("cotton_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(50, Rarity.UNCOMMON, PartStats.line(1.20, 0.91, 1.15, 35.0 / 30.0, 2))));

    /** 尼龙线：蓝档。性价比之选，张力和线长都上了一个台阶。 */
    public static final DeferredItem<TacklePartItem> NYLON_LINE = ITEMS.register("nylon_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(40, Rarity.RARE, PartStats.line(1.28, 0.95, 1.30, 45.0 / 30.0, 3))));

    /** 碳氟线：紫档。抗磨、低延展，张力回复快，专治"线黄得快"的黏液型鱼。 */
    public static final DeferredItem<TacklePartItem> FLUORO_LINE = ITEMS.register("fluoro_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(35, Rarity.EPIC, PartStats.line(1.64, 0.78, 1.42, 60.0 / 30.0, 4))));

    /**
     * 编织 PE 线：**金档**（用户定案，原为紫）。
     *
     * <p>原版 {@code Rarity} 没有金，所以这里填 EPIC；
     * 名字颜色由 {@link com.whitecobalt.anglertackle.item.TacklePartItem#quality} 的
     * 档位表决定（末尾那个 {@code 5}），玩家看到的是**金色**。
     */
    public static final DeferredItem<TacklePartItem> PE_LINE = ITEMS.register("pe_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(30, Rarity.EPIC, PartStats.line(2.08, 0.64, 1.52, 65.0 / 30.0, 5))));

    /**
     * 钛合金记忆金属线：**红档**，全模组最强鱼线（用户定案）。
     *
     * <p>张力上限 2.36（全场最高）——比 PE 线又高一档，红线区相对更宽裕；
     * 张力生成 0.56 是全场最低，线涨得最慢。耐久只有 30，和 PE 线并列最脆。
     *
     * <p>"记忆金属"的设定落在**张力回复 1.78** 上：被拉长后回弹最快，
     * 松手时张力掉得比其他线都快。
     */
    public static final DeferredItem<TacklePartItem> TITANIUM_LINE = ITEMS.register("titanium_line",
            () -> new TacklePartItem(TacklePartItem.Slot.LINE,
                    partProps(30, Rarity.EPIC, PartStats.line(2.36, 0.56, 1.78, 70.0 / 30.0, 6))));

    // ------------------------------------------------------------------ ③ 渔轮
    // 参数顺序：不挣扎回拉 / 挣扎回拉 / 体力削减 / 压制阻尼
    //
    // 五档数值（用户定案，从上到下）：
    //   手线轮     回拉 1.00/1.00   体力削减 1.00  阻尼 1.00
    //   纺车轮     回拉 1.25/1.25   体力削减 1.25  阻尼 1.20
    //   鼓轮       回拉 1.45/1.45   体力削减 1.60  阻尼 1.45
    //   水滴轮     回拉 2.20/2.30   体力削减 1.40  阻尼 1.40
    //   大型纺车轮  回拉 1.55/1.55   体力削减 2.30  阻尼 2.50
    //
    // 设计脉络很清晰：
    //   · 鼓轮是"均衡型"——三项都中等偏上，过渡用
    //   · 水滴轮是"收线型"——回拉最高（2.2/2.3），但体力削减/阻尼都只有 1.4
    //   · 大型纺车轮是"绞盘型"——回拉反而低（1.55），靠体力削减 2.3 + 阻尼 2.5 磨死大鱼
    // 所以水滴轮适合快收小鱼，大型轮适合慢慢磨巨物，各有取舍。

    /**
     * 手线轮：白档基准，无"额外"加成——新手的第一颗轮。
     *
     * <p>用户 2026-10-01 定案微调：回拉（常态/挣扎）1.00 → <b>1.08</b>、
     * 体力削减 1.00 → <b>1.08</b>、压制阻尼 1.00 → <b>1.07</b>。
     *
     * <p>⚠ 它原来还有一个"基准倍率 1.00"的隐含身份（很多手感推算拿它当 1.0 参照）。
     * 现在四舍五入到 1.07~1.08 之后，**别再把"没装轮"当成手线轮**：
     * 没装轮走的是 {@code PartStats.NEUTRAL}，仍然是 1.00。
     */
    public static final DeferredItem<TacklePartItem> HAND_REEL = ITEMS.register("hand_reel",
            () -> new TacklePartItem(TacklePartItem.Slot.REEL,
                    partProps(0, Rarity.COMMON, PartStats.reel(1.08, 1.08, 1.08, 1.07, 1))));

    /** 纺车轮：绿档，三项同步小幅提升。 */
    public static final DeferredItem<TacklePartItem> SPINNING_REEL = ITEMS.register("spinning_reel",
            () -> new TacklePartItem(TacklePartItem.Slot.REEL,
                    partProps(0, Rarity.UNCOMMON, PartStats.reel(1.25, 1.25, 1.25, 1.20, 2))));

    /** 鼓轮：蓝档，均衡型——三项都在中等偏上，最稳的过渡轮。 */
    public static final DeferredItem<TacklePartItem> DRUM_REEL = ITEMS.register("drum_reel",
            () -> new TacklePartItem(TacklePartItem.Slot.REEL,
                    partProps(0, Rarity.RARE, PartStats.reel(1.45, 1.45, 1.60, 1.45, 3))));

    /**
     * 水滴轮：**紫档，收线型**。
     * 回拉全模组最高（1.98/1.95）——拼命收线的轮，快收小鱼/抢线首选；
     * 代价是体力削减与阻尼都只有 1.4，磨大鱼不如大型纺车轮。
     *
     * <p>用户定案：不挣扎回拉 2.20 → <b>1.90</b>、挣扎回拉 2.30 → <b>1.95</b>。
     * 下调后它仍然是回拉第一，但不再把第二名（鼓轮 1.45）甩开一倍，
     * 避免"无脑选水滴轮"。
     */
    public static final DeferredItem<TacklePartItem> BAITCAST_REEL = ITEMS.register("baitcast_reel",
            () -> new TacklePartItem(TacklePartItem.Slot.REEL,
                    partProps(0, Rarity.EPIC, PartStats.reel(1.98, 1.95, 1.40, 1.40, 4))));

    /**
     * 大型纺车轮：**金档，绞盘型**。
     * 回拉 1.64（中等），但体力削减 2.3 + 压制阻尼 2.5 全模组最高——
     * 它不靠蛮力收线，靠的是"一直磨、不给鱼回气的机会"。
     *
     * <p>⚠️ MC 1.21.1 的 {@code Rarity} 只有 COMMON/UNCOMMON/RARE/EPIC 四档，
     * 没有金和红，所以原版稀有度用 EPIC，"金档"的定位体现在数值上。
     */
    public static final DeferredItem<TacklePartItem> HEAVY_REEL = ITEMS.register("heavy_reel",
            () -> new TacklePartItem(TacklePartItem.Slot.REEL,
                    partProps(0, Rarity.EPIC, PartStats.reel(1.64, 1.64, 2.30, 2.50, 5)), 1.25));

    // ------------------------------------------------------------------ ④ 鱼钩
    // 用户定案：鱼钩只保留**唯一真正生效**的「提竿窗口」。
    // 原来的"大个体概率 / 夜间效率"两个属性没有任何实现，
    // 只在 tooltip 上显示数字 —— 已删除，不再骗玩家。
    // ④ 鱼钩【已删除 2026-10-30】：用户"取消鱼钩栏改为铅锤栏，取消鱼钩相关一切"。
    //   铅锤只影响"哪一水层的鱼更容易上钩"，**仅路亚可用**（用户定案）。
    //   倍率顺序 = [浅层, 中层, 底层, 贴底]（FishLayers.Layer 的枚举顺序）。

    /** g8 铅锤 · ガン玉 8号：浅层 ×1.30。 */
    public static final DeferredItem<SinkerItem> SINKER_G8 = ITEMS.register("sinker_g8",
            () -> new SinkerItem(partProps(0, Rarity.COMMON, PartStats.NEUTRAL), 1.30, 1.00, 1.00, 1.00));

    /** g2 铅锤 · ガン玉 2号：浅层 ×1.05、中层 ×1.20。 */
    public static final DeferredItem<SinkerItem> SINKER_G2 = ITEMS.register("sinker_g2",
            () -> new SinkerItem(partProps(0, Rarity.COMMON, PartStats.NEUTRAL), 1.05, 1.20, 1.00, 1.00));

    /** 3b 铅锤 · B 系咬铅 3B：中层 ×1.30。 */
    public static final DeferredItem<SinkerItem> SINKER_3B = ITEMS.register("sinker_3b",
            () -> new SinkerItem(partProps(0, Rarity.UNCOMMON, PartStats.NEUTRAL), 1.00, 1.30, 1.00, 1.00));

    /** 5b 铅锤 · B 系咬铅 5B：中层 ×1.05、底层 ×1.20。 */
    public static final DeferredItem<SinkerItem> SINKER_5B = ITEMS.register("sinker_5b",
            () -> new SinkerItem(partProps(0, Rarity.UNCOMMON, PartStats.NEUTRAL), 1.00, 1.05, 1.20, 1.00));

    /** 0.3号铅锤 · 中通铅：底层 ×1.10、贴底 ×1.10。 */
    public static final DeferredItem<SinkerItem> SINKER_03 = ITEMS.register("sinker_03",
            () -> new SinkerItem(partProps(0, Rarity.UNCOMMON, PartStats.NEUTRAL), 1.00, 1.00, 1.10, 1.10));

    /** 0.5号铅锤 · 中通铅：贴底 ×1.30。 */
    public static final DeferredItem<SinkerItem> SINKER_05 = ITEMS.register("sinker_05",
            () -> new SinkerItem(partProps(0, Rarity.RARE, PartStats.NEUTRAL), 1.00, 1.00, 1.00, 1.30));

    // ------------------------------------------------------------------ ⑥ 浮漂【已删除】
    // 木浮漂 / 荧光漂两个物品已移除（用户定案："去掉这个功能，目前没看到两配件作用"）。
    // 原因：浮漂的灵敏度 / 预告可见度 / 夜间可见三个属性**全都没有实现**，
    // 装上等于没装；而它还是台钓竿上唯一的可选配件槽（台钓只有饵料和浮漂两栏）。
    // 留着只会让玩家以为是自己没搞懂用法。

    // ------------------------------------------------------------------ 鱼获
    // 15 种鱼。贴图由用户提供的参考图抠图而来（马赛克格原样保留，不做降采样）。
    // 每条鱼的实际体长/重量/评级在咬钩时生成，存在 FISH_DATA 组件里，物品只是容器。

    /** 鲫鱼：白档，新手第一条鱼。 */
    public static final DeferredItem<FishItem> CRUCIAN_CARP = fish("crucian_carp");
    /** 罗非鱼：白档，耐污水的杂食鱼。 */
    public static final DeferredItem<FishItem> TILAPIA = fish("tilapia");
    /** 鲻鱼：绿档，近岸洄游。 */
    public static final DeferredItem<FishItem> MULLET = fish("mullet");
    /** 草鱼：绿档，体型大、力气沉。 */
    public static final DeferredItem<FishItem> GRASS_CARP = fish("grass_carp");
    /** 青鱼：蓝档，底层巨物，考验鱼线。 */
    public static final DeferredItem<FishItem> BLACK_CARP = fish("black_carp");
    /** 翘嘴：蓝档，中上层掠食，路亚目标鱼。 */
    public static final DeferredItem<FishItem> TOPMOUTH_CULTER = fish("topmouth_culter");
    /** 梭鲈：紫档，伏击型，手感暴烈。 */
    public static final DeferredItem<FishItem> ZANDER = fish("zander");
    /** 大西洋鲑：金红档，逆流洄游，耐力极强。**仅路亚**。 */
    public static final DeferredItem<FishItem> ATLANTIC_SALMON = fish("atlantic_salmon");
    /** 蓝鳍金枪鱼：金红档，爆发力顶尖，容易切线。 */
    public static final DeferredItem<FishItem> BLUEFIN_TUNA = fish("bluefin_tuna");
    /** 幼鲨：金红档，最高难度，只在深海出没。 */
    public static final DeferredItem<FishItem> JUVENILE_SHARK = fish("juvenile_shark");

    // ---- 新增 5 种（用户定案）----
    /** 横纹鱼（横纹南鳅）：白绿档，山溪底层小鱼，仅台钓。 */
    public static final DeferredItem<FishItem> BANDED_LOACH = fish("banded_loach");
    /** 白条鱼（䱗）：白绿档，上层群游，仅台钓，抢食快。 */
    public static final DeferredItem<FishItem> HEMICULTER = fish("hemiculter");
    /** 蓝宝石雷龙鱼：红档，底层伏击，**台钓唯一的红鱼**。 */
    public static final DeferredItem<FishItem> SAPPHIRE_SNAKEHEAD = fish("sapphire_snakehead");
    /** 铂金银龙鱼：金红档，上层掠食，仅路亚，爱跳水。 */
    public static final DeferredItem<FishItem> PLATINUM_AROWANA = fish("platinum_arowana");
    /** 大眼海鲢：红档，仅路亚，本批难度顶点（爆发猛、耐力极强）。 */
    public static final DeferredItem<FishItem> OXEYE_TARPON = fish("oxeye_tarpon");

    // ---- 第二批新增 6 种，**全部仅路亚**（用户定案：本次不增加台钓鱼）----
    /** 虎鱼：金红档，高速伏击，仅路亚。 */
    public static final DeferredItem<FishItem> TIGERFISH = fish("tigerfish");
    /** 狗鱼（白斑狗鱼）：紫金档，水草间伏击，仅路亚，冲刺猛但耐力差。 */
    public static final DeferredItem<FishItem> PIKE = fish("pike");
    /** 褐鳟：金红档，仅路亚，**粘稠手感**（摆头顶水，张力涨得快）。 */
    public static final DeferredItem<FishItem> BROWN_TROUT = fish("brown_trout");
    /** 镜鲤：金红档（红很稀有），仅路亚，耐力型磨人。 */
    public static final DeferredItem<FishItem> MIRROR_CARP = fish("mirror_carp");
    /** 条纹鲈：绿蓝紫档，仅路亚，持续对抗型。 */
    public static final DeferredItem<FishItem> STRIPED_BASS = fish("striped_bass");
    /** 巨骨舌鱼：紫金红档，仅路亚，笨重但蛮力极强、耐力最高。 */
    public static final DeferredItem<FishItem> ARAPAIMA = fish("arapaima");

    // ---- 第三批新增 5 种（用户 2026-10-01 定案：4 台钓 + 1 路亚）----
    /** 非洲蝴蝶鱼（齿蝶鱼）：紫金档，水面层小鱼，仅台钓。 */
    public static final DeferredItem<FishItem> AFRICAN_BUTTERFLYFISH = fish("african_butterflyfish");
    /** 北非胡子鲇：紫金档，泥底鲇鱼，**仅路亚**，力气大但拉不快。 */
    public static final DeferredItem<FishItem> NORTH_AFRICAN_CATFISH = fish("north_african_catfish");
    /** 红宝石鱼（斑尾伴丽鱼）：紫金档，体高侧扁的鲜红慈鲷，仅台钓。 */
    public static final DeferredItem<FishItem> JEWEL_CICHLID = fish("jewel_cichlid");
    /** 孔雀鱼：金红档，模组里最小的鱼，仅台钓。 */
    public static final DeferredItem<FishItem> GUPPY = fish("guppy");
    /** 长吻象鼻鱼（彼氏锥颌象鼻鱼）：紫金档，靠弱电在浑浊泥底找食，仅台钓。 */
    public static final DeferredItem<FishItem> ELEPHANTNOSE = fish("elephantnose");

    // ---- 第四批新增 2 种（用户 2026-10-02 定案：两种都是洞穴鱼 · 仅台钓 · 蓝紫档）----
    //
    // 为什么加这两条：洞穴水域原本只有 3 种台钓鱼（横纹鱼[0,1] / 长吻象鼻鱼[3,4] /
    // 蓝宝石雷龙鱼[5]），**中间蓝紫档是空的** ——
    //   · 玩家带秘制窝（咬钩下限抬到 2）时横纹鱼被下限挡掉；
    //   · 池子只剩两种，而两种**都是金红鱼种**（最高档 ≥4），
    //     于是"饵料把金红占比控制在 3%~10%"那条规则（hvTargetPct）因为
    //     "池子里没有非金红鱼可稀释"而整段跳过 → 金红占比 100%，
    //     玩家体感"几竿三条雷龙"。
    // 补两种蓝紫档洞穴鱼之后：池子变成 4~5 种，非金红鱼回来了，
    // 占比规则也能正常生效。
    /** 金泥鳅：蓝紫档，洞穴淤泥底层，仅台钓。 */
    public static final DeferredItem<FishItem> GOLDEN_LOACH = fish("golden_loach");
    /** 盲鱼：蓝紫档，洞穴暗河的无眼小鱼，仅台钓。 */
    public static final DeferredItem<FishItem> CAVE_BLINDFISH = fish("cave_blindfish");

    // ---- 第 1 批：冷水 5 条（用户 2026-10-08，docs/33 §9.2）----
    //
    // 前两条属于**高寒湖**（湖泊 + 高海拔/高山群系），后三条属于**冷水溪流**
    // （河流 + 低基础温度/针叶林系）—— 两条叠加修饰一次验证完。
    /** 北极红点鲑：金红档，高寒湖的耐力型招牌，通用竿。 */
    public static final DeferredItem<FishItem> ARCTIC_CHAR = fish("arctic_char");
    /** 高白鲑：绿蓝档，高寒湖中层小鱼，仅台钓（高寒湖的"非金红稀释鱼"）。 */
    public static final DeferredItem<FishItem> WHITEFISH = fish("whitefish");
    /** 虹鳟：紫金档，冷水溪流，仅路亚（与褐鳟对照：跳得凶、泄得快）。 */
    public static final DeferredItem<FishItem> RAINBOW_TROUT = fish("rainbow_trout");
    /** 江鳕：蓝紫档，冷水溪流，仅台钓 —— 唯一的淡水鳕科，下颌一根须。 */
    public static final DeferredItem<FishItem> BURBOT = fish("burbot");
    /** 哲罗鲑：金红档，冷水溪流，仅路亚 —— 世界最大的鲑科，淡水终极。 */
    public static final DeferredItem<FishItem> TAIMEN = fish("taimen");

    // ---- 第 3 批：极地 9 条（用户 2026-10-08，docs/33 §9.2）----
    // 冰鱼（icefish）用户明确**不做** ✓ 不要加回来。
    // 这一批全部只在**极地水**出现（群系常年覆冰且非高山，见 FishWaters 的 putMods）。
    /** 北极鳕：绿蓝档，冰下成群的小杂鱼，仅台钓。 */
    public static final DeferredItem<FishItem> ARCTIC_COD = fish("arctic_cod");
    /** 毛鳞鱼：绿蓝档，极地最重要的饵料鱼，仅台钓。 */
    public static final DeferredItem<FishItem> CAPELIN = fish("capelin");
    /** 大西洋鲱：蓝紫档，蓝背银身的群游小鱼（无斑无侧线无脂鳍），仅台钓。 */
    public static final DeferredItem<FishItem> ATLANTIC_HERRING = fish("atlantic_herring");
    /** 狼鱼：紫金档，碎壳专家（前犬齿后臼齿），仅台钓 —— 极地里唯一的高档台钓鱼。 */
    public static final DeferredItem<FishItem> WOLFFISH = fish("wolffish");
    /** 平鲉（红鱼）：紫金档，慢速底层、大眼短身高背，仅路亚。学名用 *Sebastes norvegicus*（见 FishSpecies 注释）。 */
    public static final DeferredItem<FishItem> REDFISH = fish("redfish");
    /** 裸盖鱼（银鳕）：紫金档，深水泥底（**是鲉形目、不是鳕形目**），仅路亚。 */
    public static final DeferredItem<FishItem> SABLEFISH = fish("sablefish");
    /** 大西洋鳕：紫金档，极地主力，一米级饱满纺锤 + 显眼长须，通用竿。 */
    public static final DeferredItem<FishItem> ATLANTIC_COD = fish("atlantic_cod");
    /** 格陵兰大比目鱼：金红档，会离底上浮捕食的比目鱼，20~200kg 的底层巨物，仅路亚。 */
    public static final DeferredItem<FishItem> GREENLAND_HALIBUT = fish("greenland_halibut");
    /** 格陵兰鲨：金红档，**极地终极（传说）** —— 巡航 0.34 m/s、体力全模组第一，80~500kg，仅路亚。 */
    public static final DeferredItem<FishItem> GREENLAND_SHARK = fish("greenland_shark");

    // ---- 第 4 批：远洋 5 条（用户 2026-10-08 逐条定档；**不含新机制**，电鳗放电已砍掉 ✓）----
    /** 雨伞旗鱼：金红档，**全模组最高拉力**（3.50），深海速度顶点，仅路亚。 */
    public static final DeferredItem<FishItem> SAILFISH = fish("sailfish");
    /** 鲯鳅：金红档，生长极快、离水褪色，深海中型掠食者，仅路亚。 */
    public static final DeferredItem<FishItem> MAHI_MAHI = fish("mahi_mahi");
    /** 鳄雀鳝：**紫金红**档，能吞气呼吸的入侵种，猛烈翻滚型，仅路亚（河湖）。 */
    public static final DeferredItem<FishItem> ALLIGATOR_GAR = fish("alligator_gar");
    /** 巨暹罗鲤：金红档，**世界最大的鲤科**（IUCN 极危），仅台钓 —— 台钓红鱼白名单已由用户点头加它。 */
    public static final DeferredItem<FishItem> GIANT_SIAMESE_CARP = fish("giant_siamese_carp");
    /** 电鳗：紫金档，长条扭动缠绕型，仅路亚（沼泽/河流）—— **不做放电机制**（用户定案）。 */
    public static final DeferredItem<FishItem> ELECTRIC_EEL = fish("electric_eel");

    /**
     * 整鱼物品的注册。
     *
     * <p>⚠ **必须带默认 FISH_DATA**（创造模式 / JEI 直接拿出来的那条鱼）：
     * 切块要读 {@code FISH_DATA} 才知道切出什么，没有数据的那条鱼**永远切不出块**
     * （2026-10-03 用户实机踩到："即使显示能做也弄不了" —— JEI 里拿的鱼块/整鱼没有数据）。
     * 真实钓鱼拿到的鱼会在 {@code FishingManager} 里被写上真实数据，覆盖这个默认值。
     */
    private static DeferredItem<FishItem> fish(String name) {
        return ITEMS.register(name, () -> new FishItem(new Item.Properties().stacksTo(16)
                .component(ModDataComponents.FISH_DATA.get(), defaultFishData(name))));
    }

    /**
     * 展示/默认用的整鱼数据：**它自己的鱼种** + 档位未知（-1）+ 1 kg / 30 cm。
     *
     * <p>只给"没有真实数据"的栈用（创造模式、JEI 里直接拿、结构里的战利品）。
     * 档位写 {@link com.whitecobalt.anglertackle.fishing.FishData#TIER_UNKNOWN}，
     * 于是切块按 {@code groupOfTier(-1)} 落到普通鱼块 —— 样本鱼只要"能切、能整条炸"就够，
     * 玩法里的真实鱼从来不缺数据。
     *
     * <p>⚠ **不许在这里查鱼种表**（`FishSpecies.byId` / `sp.tiers()` 之类）：
     * 那会触发 {@code FishSpecies.<clinit>} → {@code FightProfile.<clinit>} → 读配置，
     * 而物品注册发生在**配置加载之前**，专用服务器直接
     * {@code IllegalStateException: Cannot get config value before config is loaded}
     * （2026-10-03 起了一次专用服务器体检，第一轮就撞上，模组整个装不进去）。
     * 所以下面这些默认值全是**常数**。
     */
    private static com.whitecobalt.anglertackle.fishing.FishData defaultFishData(String species) {
        return new com.whitecobalt.anglertackle.fishing.FishData(species, 30f, 1.0f,
                com.whitecobalt.anglertackle.fishing.FishData.SizeGrade.NORMAL, 0L,
                java.util.Optional.empty(),
                com.whitecobalt.anglertackle.fishing.FishData.TIER_UNKNOWN);
    }

    /**
     * 三档鱼块的**默认鱼种 / 档位 / 体重**：创造模式给的那一块要真能做菜。
     *
     * <p>选的三个都是该档位里有代表菜的鱼（也是 docs/25 §13.5 逐鱼表里的行）：
     * 普通→鲫鱼（鱼汤）、优质→草鱼（红烧 / 鱼丸）、顶级→大西洋鲑（刺身）。
     * 真实切块拿到的鱼块会被写上"那条鱼"的数据，覆盖这里的默认值。
     *
     * <p>⚠ 同 {@link #defaultFishData}：**常数，不许在注册期查鱼种表**。
     * 这三个值是否仍与 {@code FishSpecies} 对得上（档位在它的档位表里、体重在它的规格区间里）
     * 由 {@code tools/check-cooking.js} 的 D3 逐条核对 —— 改了鱼种数据不改这里就会红。
     */
    private static final String[] SAMPLE_CHUNK_SPECIES = {"crucian_carp", "grass_carp", "atlantic_salmon"};
    private static final int[] SAMPLE_CHUNK_TIER = {1, 2, 5};
    private static final float[] SAMPLE_CHUNK_KG = {0.65f, 9.75f, 12.0f};

    /** 展示/默认用的鱼块数据。 */
    public static com.whitecobalt.anglertackle.fishing.FishChunkData sampleChunkData(int group) {
        int g = Math.max(0, Math.min(SAMPLE_CHUNK_SPECIES.length - 1, group));
        return new com.whitecobalt.anglertackle.fishing.FishChunkData(
                SAMPLE_CHUNK_SPECIES[g], SAMPLE_CHUNK_TIER[g], SAMPLE_CHUNK_KG[g]);
    }

    // ------------------------------------------------------------------ 教程书
    /** 钓鱼手册：按右键打开（内容内置在 Java 里，数据包可覆盖）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.guide.GuideBookItem> GUIDE_BOOK =
            ITEMS.register("guide_book", () -> new com.whitecobalt.anglertackle.guide.GuideBookItem(
                    new Item.Properties().stacksTo(1)));

    // ------------------------------------------------------------------ 图鉴书
    /**
     * 鱼类图鉴：按右键打开收集进度（用户定案"图鉴用一本新书看收集到哪一步"）。
     *
     * <p>和钓鱼手册分开成两本书，不是重复：手册是**怎么钓**（静态教程、随数据包更新），
     * 图鉴是**我钓到了什么**（动态的玩家数据）。合成一本要玩家自己付出代价，
     * 所以两本书都进创造页签，但生存里只能靠配方拿。
     *
     * <p>书本身**不存数据**：右键时只发一个"我要看图鉴"的请求，
     * 服务端现读玩家的 {@code PlayerFishingData} 附件再下发（见 CodexPayload）。
     * 于是卖鱼/丢鱼/送人都不影响已点亮的记录 —— 图鉴跟着玩家走，不跟着鱼走。
     */
    public static final DeferredItem<CodexBookItem> CODEX_BOOK = ITEMS.register("codex_book",
            () -> new CodexBookItem(new Item.Properties().stacksTo(1)));

    /**
     * 菜谱（2026-10-03 用户："制作菜谱，菜谱合成方式（原版任意熟鱼加书）"）。
     *
     * <p>它讲"这道菜怎么做、吃了会怎样"：材料来自 {@code DishRecipe.allowedPairs()}、
     * 效果来自 {@code DishEffects.grantsFor()}、饱食度来自 {@code FoodValues} ——
     * 三张静态表，所以这本书**不存任何数据**，也不会过期。
     *
     * <p>{@code stacksTo(1)}：与教程书/图鉴书一致（工具类物品，不该占一摞）。
     */
    public static final DeferredItem<CookbookItem> COOKBOOK = ITEMS.register("cookbook",
            () -> new CookbookItem(new Item.Properties().stacksTo(1)));

    /**
     * **邮箱**（用户 2026-10-11 点名要的邮件系统；`docs/36` §5.7 的**主入口**）。
     *
     * <p>右键打开邮箱界面。{@code stacksTo(1)}：与教程书/图鉴书/菜谱书一致（工具类物品）。
     * 真正的动作在 {@code MailboxItem.use} 里：客户端发 {@code MailActionPayload(OPEN)}，
     * 服务端回一份快照，客户端开界面（开界面必须留在客户端专用类里，理由见那个类的注释）。
     */
    /**
     * **垂纶终端**（统合界面入口；用户 2026-10-15："做一个终端物品通过右键打开"）。
     *
     * <p>右键 = 打开 {@code TerminalScreen}（委托/图鉴/邮箱/成就/称号/商店/排行榜/联赛/比赛）。
     * 走的是与 `/atl terminal` 同一条服务端载荷路径 ✓。
     *
     * <p>贴图**复用 mailbox**（用户定案：不为它新增 PNG，也就不动 `图标指纹.json` 那张
     * "用户验收过的"指纹表 —— 顺手 `--write` 等于关掉那道闸，仓库出过这个事故）。
     */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.TerminalItem> TERMINAL =
            ITEMS.register("terminal",
                    () -> new com.whitecobalt.anglertackle.item.TerminalItem(
                            new Item.Properties().stacksTo(1)));

    public static final DeferredItem<com.whitecobalt.anglertackle.item.MailboxItem> MAILBOX =
            ITEMS.register("mailbox",
                    () -> new com.whitecobalt.anglertackle.item.MailboxItem(
                            new Item.Properties().stacksTo(1)));

    // ------------------------------------------------------------------ 鱼饵（消耗品）
    // tier = 品质下限（0 白 1 绿 2 蓝 3 紫 4 金 5 红）；maxWeight 0 = 无上限
    // floatRodAllowed = 台钓竿能不能用这种饵
    /** 面包屑：白档，只能钓 1kg 以下的小鱼。 */
    public static final DeferredItem<BaitItem> BAIT_BREAD = ITEMS.register("bait_bread",
            () -> new BaitItem(0, 1.0, 0, true, 1, 1.5, new Item.Properties().stacksTo(1).durability(40)));
    /** 蚯蚓：白档通用，挖草方块可得。 */
    public static final DeferredItem<BaitItem> BAIT_WORM = ITEMS.register("bait_worm",
            () -> new BaitItem(0, 4.0, 0, true, 1, 2.0, new Item.Properties().stacksTo(1).durability(40)));
    /** 玉米粒：绿档，草食鱼加成。 */
    public static final DeferredItem<BaitItem> BAIT_CORN = ITEMS.register("bait_corn",
            () -> new BaitItem(1, 8.0, 0, true, 5, 2.2, new Item.Properties().stacksTo(1).durability(30)));
    /** 活虾：紫档，海洋稀有鱼加成。 */
    public static final DeferredItem<BaitItem> BAIT_SHRIMP = ITEMS.register("bait_shrimp",
            () -> new BaitItem(3, 0, 3.0, true, 18, 2.5, new Item.Properties().stacksTo(1).durability(22)));
    /** 发光饵：金档，洞穴/深海专用。 */
    public static final DeferredItem<BaitItem> BAIT_GLOW = ITEMS.register("bait_glow",
            () -> new BaitItem(4, 0, 5.0, true, 24, 3.0, new Item.Properties().stacksTo(1).durability(20)));
    /** 秘制香饵：红档，最强。 */
    public static final DeferredItem<BaitItem> BAIT_SECRET = ITEMS.register("bait_secret",
            () -> new BaitItem(5, 0, 10.0, true, 30, 2.8, new Item.Properties().stacksTo(1).durability(20)));

    // ------------------------------------------------------------------ 拟饵（有耐久，路亚专用）
    // 六档阶梯 **1白 2绿 3蓝 4紫 5金 6红**（和鱼线用同一套口径，见上面的鱼线段）。
    //   亮片 2绿 → 米诺/软虫 3蓝 → 深潜 4紫 → 钢珠 5金 → 镀金 6红
    //
    // ⚠ 这里**踩过两次**：
    //   ① 曾按"1绿…5红"填（整体差一档）→ 镀金显示成金色，且红档永远出不来；
    //   ② 线和饵的起点不一致（线从 1 白起、饵从 2 绿起）→ 同样填 1，
    //      麻线是白、亮片却是绿，玩家看着就是"品质乱了"。
    //   现在两边统一：**档位号相同 = 颜色相同**。
    //
    // 档位同时决定饵料下限（见 FishingSession.baitMinTier）：
    //   紫及以上（≥4）过滤绿档以下、金/红（≥5）过滤蓝档以下。

    /** 亮片拟饵：**绿档**入门（档位 2），耐久 40。 */
    public static final DeferredItem<TacklePartItem> LURE_SPOON = ITEMS.register("lure_spoon",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(40, Rarity.UNCOMMON, PartStats.lure(2))));

    /**
     * 荧光亮片：**绿档**入门（档位 2），耐久 40 —— 用户 2026-10-01 追加。
     *
     * <p>数值口径沿用"与同级别一样"：与同档的亮片拟饵**逐位相同**
     * （耐久 40 / Rarity.UNCOMMON / {@code PartStats.lure(2)}）。商店价格是
     * {@code 耐久 × 每点耐久单价}（见 tools/shop-rules.js），耐久一变价格就跟着变。
     *
     * <p>它和亮片是**互补的一对**，区别只在词条（见 tools/fish-habits.json）：
     * <ul>
     *   <li>亮片：靠亮光刺激引发反应性咬口 → 认 {@code aggressive}（性情凶猛）；</li>
     *   <li>荧光亮片：荧光材料**削弱**亮光刺激，不惊鱼 → 认 {@code timid}（胆小多疑）。</li>
     * </ul>
     * 所以它**故意不认 aggressive** —— 那与它自己的描述自相矛盾。
     */
    public static final DeferredItem<TacklePartItem> LURE_GLOWSPOON = ITEMS.register("lure_glowspoon",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(40, Rarity.UNCOMMON, PartStats.lure(2))));

    /** 米诺鱼拟饵：**蓝档**主力（档位 3），耐久 30。 */
    public static final DeferredItem<TacklePartItem> LURE_MINNOW = ITEMS.register("lure_minnow",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(30, Rarity.RARE, PartStats.lure(3))));

    /**
     * 深潜刺激米诺：**紫档**打底层（档位 4），耐久 22。
     *
     * <p>机械上也要求它是紫：{@code baitMinTier()} 里"紫饵过滤绿档以下"
     * 判的是 {@code lureTier() == 4}，填别的值那条过滤规则就走不到。
     */
    public static final DeferredItem<TacklePartItem> LURE_DEEP = ITEMS.register("lure_deep",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(22, Rarity.EPIC, PartStats.lure(4))));

    /** 钢珠声诱拟饵：**金档**（档位 5）。内置钢珠发声，专钓警觉的大个体。 */
    public static final DeferredItem<TacklePartItem> LURE_RATTLE = ITEMS.register("lure_rattle",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(20, Rarity.EPIC, PartStats.lure(5))));

    /**
     * 镀金竞技拟饵：**红档顶配**（档位 6）。竞技级做工，金红鱼也吃。
     *
     * <p>档位必须是 6 —— 填 5 会算成金档，"红档顶配显示成金色"，
     * 而且整列拟饵卡在 1~5，红档永远出不来。
     */
    public static final DeferredItem<TacklePartItem> LURE_GILDED = ITEMS.register("lure_gilded",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(20, Rarity.EPIC, PartStats.lure(6))));

    /** 软虫拟饵：**蓝档**（档位 3），靠卷尾在水里摆动，走中层。 */
    public static final DeferredItem<TacklePartItem> LURE_SOFT = ITEMS.register("lure_soft",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(26, Rarity.RARE, PartStats.lure(3))));

    // ---- 第三批 3 个拟饵（用户 2026-10-01 定案）----
    //
    // 用户口径：「具体数值与同级别一样」。所以**完全照抄同档位那一个拟饵**，
    // 一个数字都不许自己发明 —— 耐久、稀有度、档位三项都对齐：
    //
    //   蓝档（3）→ 与 lure_soft（软虫）一致：耐久 26 / Rarity.RARE
    //   紫档（4）→ 与 lure_deep（浅层刺激拟饵）一致：耐久 22 / Rarity.EPIC
    //   金档（5）→ 与 lure_rattle（钢珠声诱）一致：耐久 20 / Rarity.EPIC
    //
    // ⚠ 档位是**拟饵自己的档位**（1白 2绿 3蓝 4紫 5金 6红），
    //   与"鱼品质档位"（白0 绿1 蓝2 紫3 金4 红5）不是同一套 —— 别混。
    // ⚠ 耐久必须与同档位那个**逐位相同**：商店价格是 `耐久 × 每点耐久单价`（见
    //   tools/shop-rules.js），耐久一变价格就跟着变，"与同级别一样"就不成立了。
    //   tools/check-part-config-parity.js 会把这三个的新配置键逐项登记。

    /** 蓝—深水探索米诺：**蓝档**（档位 3），细长软虫 + 桨状尾，数值同软虫。 */
    public static final DeferredItem<TacklePartItem> LURE_DEPTHFINDER = ITEMS.register("lure_depthfinder",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(26, Rarity.RARE, PartStats.lure(3))));

    /** 紫—深水摆尾拟饵：**紫档**（档位 4），绿背银腹 + 桨状尾，数值同浅层刺激拟饵。 */
    public static final DeferredItem<TacklePartItem> LURE_PADDLETAIL = ITEMS.register("lure_paddletail",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(22, Rarity.EPIC, PartStats.lure(4))));

    /** 金—深潜巨物振动饵：**金档**（档位 5），蓝身黄头硬饵 + 两枚三本钩，数值同钢珠声诱。 */
    public static final DeferredItem<TacklePartItem> LURE_VIBRATION = ITEMS.register("lure_vibration",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(20, Rarity.EPIC, PartStats.lure(5))));

    /**
     * 测试拟饵：**仅用于调试**，上红概率 100%（用户定案）。
     *
     * <p>贴图直接复用镀金竞技拟饵（{@code lure_gilded}），不单独出图。
     * 名称里带【测试】前缀，避免误当成正式内容。
     *
     * <p>工作原理（两处，缺一不可，见 {@code FishingSession}）：
     * <ol>
     *   <li>{@code maxTier()} 放开到 5 —— 让金红鱼种能进鱼池</li>
     *   <li>{@code rollIndividual} 的下限钉成 5 —— 让**个体**也一定是红档</li>
     * </ol>
     * 只做第 ① 步的话，鱼种进来了但个体大多还是金鱼，测的还是金鱼手感。
     *
     * <p>档位填 6 → 名字显示为红色，和"红档测试品"的定位一致。
     * ⚠ 上架前应当移除，或至少确保它不在生存模式获取路径里。
     */
    public static final DeferredItem<TacklePartItem> TEST_LURE = ITEMS.register("lure_test_red",
            () -> new TacklePartItem(TacklePartItem.Slot.LURE,
                    partProps(9999, Rarity.EPIC, PartStats.lure(6))));

    // ------------------------------------------------------------------ 窝料
    /** 通用谷物窝：白档，只缩时间。 */
    public static final DeferredItem<ChumItem> CHUM_GRAIN = ITEMS.register("chum_grain",
            () -> new ChumItem(0, false, 10, 0.75, 30, new Item.Properties().stacksTo(16)));
    /** 腥味肉窝：绿档起步。 */
    public static final DeferredItem<ChumItem> CHUM_MEAT = ITEMS.register("chum_meat",
            () -> new ChumItem(1, false, 10, 0.65, 80, new Item.Properties().stacksTo(16)));
    /** 甜香蜂蜜谷物窝：蓝档起步。 */
    public static final DeferredItem<ChumItem> CHUM_FRUIT = ITEMS.register("chum_fruit",
            () -> new ChumItem(2, false, 10, 0.55, 150, new Item.Properties().stacksTo(16)));
    /** 秘制配方窝：紫档起步 + **沉底窝**（唯一能影响底层鱼的）。 */
    public static final DeferredItem<ChumItem> CHUM_SECRET = ITEMS.register("chum_secret",
            () -> new ChumItem(3, true, 10, 0.45, 300, new Item.Properties().stacksTo(16)));

    /**
     * 老柯的刷怪蛋 —— **仅用于调试**。
     *
     * <p>正式玩法里老柯随渔人小屋一起生成，不该靠刷怪蛋获得。
     * 但小屋是结构生成的，不先能单独放一只出来就没法验证 AI / 交互 / 传送 ——
     * 每次改一行都要先找半小时小屋，那种开发循环没人受得了。
     *
     * <p>⚠ 公开发布前要决定：留着（玩家也能刷，等于绕过探索）还是拿掉
     * （调试改用 {@code /summon anglertackle:fisherman}）。待办记在 docs/13。
     *
     * <h2>为什么是 {@code DeferredSpawnEggItem} 而不是原版 {@code SpawnEggItem}</h2>
     * 原版那个构造器在 NeoForge 里被标了 {@code @Deprecated}，注释原话是
     * "Use {@code net.neoforged.neoforge.common.DeferredSpawnEggItem} instead for suppliers"。
     *
     * <p>但这不是"新旧 API 二选一"的洁癖问题，而是**会不会炸**的问题，两个理由：
     * <ol>
     *   <li><b>注册顺序不保证。</b>原版构造器收的是<b>已经解析好的</b> {@code EntityType}，
     *       所以得写 {@code ModEntities.FISHERMAN.get()}。物品注册和实体注册是两个
     *       {@code DeferredRegister}、两次独立的 {@code RegisterEvent} ——
     *       谁先谁后**不能靠"我在主类里先调用了谁"来保证**。
     *       真炸起来是这样：物品先注册 → 实体还没解析 → {@code get()} 抛异常，
     *       而报错只会说"registry not present"，看不出是刷怪蛋引起的。
     *       {@code DeferredSpawnEggItem} 收的是 {@code Supplier}，把取值推迟到
     *       "真的要用这个蛋"那一刻，天然绕开顺序问题。</li>
     *   <li><b>原版构造器会往静态表里塞东西。</b>反编译看过（{@code javap -c}）：
     *       {@code super(null, ...)} 里 {@code if (type != null) BY_ID.put(type, this)}。
     *       传空是为了不污染那张表，再由子类重写 {@code getDefaultType()} 走 supplier。
     *       我们直接调原版构造器就等于把自己登记进 {@code BY_ID} ——
     *       这个副作用在物品注册失败时不会回滚，会留下一个指向半初始化状态的条目。</li>
     * </ol>
     *
     * <p>配色（底色 + 斑点）在 1.21.1 仍然是构造器参数，没有数据驱动：
     * {@code (Supplier, int, int, Properties)}，已从 {@code neoforge-21.1.252-universal.jar}
     * javap 确认（1.20.5 之前的老教程是三个参数）。
     */
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> FISHERMAN_SPAWN_EGG =
            ITEMS.register("fisherman_spawn_egg", () -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHERMAN,   // 传 supplier，不 .get()
                    0x3B4A63,   // 底色：深靛蓝（老渔夫的粗布褂子）
                    0xE8DCC0,   // 斑点：草帽/白胡的米白
                    new Item.Properties()));

    // ------------------------------------------------------------------ 鱼的刷怪蛋
    // 21 个，调试用。颜色直接取该鱼的背部色 + 花纹色（与模型贴图同源），
    // 名字是中文，tooltip 里另带一行鱼种 id —— 这样"第几条鱼不对"能直接报出来。
    //
    // ⛔ 下面整段是 tools/gen-fish-model.js 生成的，不要手改；
    //    改配色请改 tools/fish-appearance.json 后重跑生成器。
    //    上架前删除：本段 + 创造页签里的对应行 + assets 下的 *_spawn_egg.json。
    //
    // ▼ 鱼刷怪蛋字段插入点（gen-fish-model.js；锚注必须保留）
    // ==== BEGIN 鱼刷怪蛋字段（gen-fish-model.js 生成，勿手改）====
    /** 罗非鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> TILAPIA_SPAWN_EGG =
            ITEMS.register("tilapia_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("tilapia"), "tilapia", 0x6E7A4A, 0x3A3B2E,
                    new Item.Properties()));

    /** 齿蝶鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> AFRICAN_BUTTERFLYFISH_SPAWN_EGG =
            ITEMS.register("african_butterflyfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("african_butterflyfish"), "african_butterflyfish", 0x6B5F3A, 0x4A4028,
                    new Item.Properties()));

    /** 鲫鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> CRUCIAN_CARP_SPAWN_EGG =
            ITEMS.register("crucian_carp_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("crucian_carp"), "crucian_carp", 0x4A5A3C, 0xA8ADA2,
                    new Item.Properties()));

    /** 镜鲤 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> MIRROR_CARP_SPAWN_EGG =
            ITEMS.register("mirror_carp_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("mirror_carp"), "mirror_carp", 0x6B5A3C, 0x6B5A3C,
                    new Item.Properties()));

    /** 红宝石鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> JEWEL_CICHLID_SPAWN_EGG =
            ITEMS.register("jewel_cichlid_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("jewel_cichlid"), "jewel_cichlid", 0x7A6A3F, 0x1B1B22,
                    new Item.Properties()));

    /** 鲻鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> MULLET_SPAWN_EGG =
            ITEMS.register("mullet_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("mullet"), "mullet", 0x6B7A7E, 0x3A4247,
                    new Item.Properties()));

    /** 梭鲈 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ZANDER_SPAWN_EGG =
            ITEMS.register("zander_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("zander"), "zander", 0x4A5D3A, 0x3A3D2B,
                    new Item.Properties()));

    /** 大西洋鲑 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ATLANTIC_SALMON_SPAWN_EGG =
            ITEMS.register("atlantic_salmon_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("atlantic_salmon"), "atlantic_salmon", 0x3F5B54, 0x33291C,
                    new Item.Properties()));

    /** 蓝鳍金枪鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> BLUEFIN_TUNA_SPAWN_EGG =
            ITEMS.register("bluefin_tuna_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("bluefin_tuna"), "bluefin_tuna", 0x1F3F63, 0xC9D6DC,
                    new Item.Properties()));

    /** 褐鳟 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> BROWN_TROUT_SPAWN_EGG =
            ITEMS.register("brown_trout_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("brown_trout"), "brown_trout", 0x5A5136, 0x1A1A18,
                    new Item.Properties()));

    /** 虎鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> TIGERFISH_SPAWN_EGG =
            ITEMS.register("tigerfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("tigerfish"), "tigerfish", 0x6E5C3E, 0x4A3A1E,
                    new Item.Properties()));

    /** 翘嘴 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> TOPMOUTH_CULTER_SPAWN_EGG =
            ITEMS.register("topmouth_culter_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("topmouth_culter"), "topmouth_culter", 0x5A6470, 0x9AA6AC,
                    new Item.Properties()));

    /** 白条鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> HEMICULTER_SPAWN_EGG =
            ITEMS.register("hemiculter_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("hemiculter"), "hemiculter", 0x5A6B70, 0xC9D2D6,
                    new Item.Properties()));

    /** 条纹鲈 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> STRIPED_BASS_SPAWN_EGG =
            ITEMS.register("striped_bass_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("striped_bass"), "striped_bass", 0x6B7A63, 0x16191B,
                    new Item.Properties()));

    /** 铂金银龙鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> PLATINUM_AROWANA_SPAWN_EGG =
            ITEMS.register("platinum_arowana_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("platinum_arowana"), "platinum_arowana", 0xF2F0E8, 0xCFD3DA,
                    new Item.Properties()));

    /** 大眼海鲢 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> OXEYE_TARPON_SPAWN_EGG =
            ITEMS.register("oxeye_tarpon_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("oxeye_tarpon"), "oxeye_tarpon", 0x6E8C7E, 0xA9B4B9,
                    new Item.Properties()));

    /** 横纹鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> BANDED_LOACH_SPAWN_EGG =
            ITEMS.register("banded_loach_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("banded_loach"), "banded_loach", 0x7A6A4F, 0xA8823C,
                    new Item.Properties()));

    /** 孔雀鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GUPPY_SPAWN_EGG =
            ITEMS.register("guppy_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("guppy"), "guppy", 0x6B5F45, 0x1B1B22,
                    new Item.Properties()));

    /** 长吻象鼻鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ELEPHANTNOSE_SPAWN_EGG =
            ITEMS.register("elephantnose_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("elephantnose"), "elephantnose", 0x4A4038, 0x2E2822,
                    new Item.Properties()));

    /** 金泥鳅 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GOLDEN_LOACH_SPAWN_EGG =
            ITEMS.register("golden_loach_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("golden_loach"), "golden_loach", 0xE8A317, 0xE08A2B,
                    new Item.Properties()));

    /** 盲鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> CAVE_BLINDFISH_SPAWN_EGG =
            ITEMS.register("cave_blindfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("cave_blindfish"), "cave_blindfish", 0xB49E9A, 0x9E4A42,
                    new Item.Properties()));

    /** 草鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GRASS_CARP_SPAWN_EGG =
            ITEMS.register("grass_carp_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("grass_carp"), "grass_carp", 0x5A6340, 0x4A4A42,
                    new Item.Properties()));

    /** 青鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> BLACK_CARP_SPAWN_EGG =
            ITEMS.register("black_carp_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("black_carp"), "black_carp", 0x2B3338, 0x3D4A4F,
                    new Item.Properties()));

    /** 狗鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> PIKE_SPAWN_EGG =
            ITEMS.register("pike_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("pike"), "pike", 0x3F4A2A, 0xDFE7EC,
                    new Item.Properties()));

    /** 巨骨舌鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ARAPAIMA_SPAWN_EGG =
            ITEMS.register("arapaima_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("arapaima"), "arapaima", 0x4E6157, 0xC1352B,
                    new Item.Properties()));

    /** 北非胡子鲇 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> NORTH_AFRICAN_CATFISH_SPAWN_EGG =
            ITEMS.register("north_african_catfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("north_african_catfish"), "north_african_catfish", 0x3D3A33, 0x1E1C18,
                    new Item.Properties()));

    /** 蓝宝石雷龙鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> SAPPHIRE_SNAKEHEAD_SPAWN_EGG =
            ITEMS.register("sapphire_snakehead_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("sapphire_snakehead"), "sapphire_snakehead", 0x3B4650, 0xC75B23,
                    new Item.Properties()));

    /** 幼鲨 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> JUVENILE_SHARK_SPAWN_EGG =
            ITEMS.register("juvenile_shark_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("juvenile_shark"), "juvenile_shark", 0x2C5578, 0x1E2427,
                    new Item.Properties()));

    /** 北极红点鲑 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ARCTIC_CHAR_SPAWN_EGG =
            ITEMS.register("arctic_char_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("arctic_char"), "arctic_char", 0x4A4632, 0xE8A0B4,
                    new Item.Properties()));

    /** 高白鲑 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> WHITEFISH_SPAWN_EGG =
            ITEMS.register("whitefish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("whitefish"), "whitefish", 0x4A453F, 0x8A8A80,
                    new Item.Properties()));

    /** 虹鳟 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> RAINBOW_TROUT_SPAWN_EGG =
            ITEMS.register("rainbow_trout_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("rainbow_trout"), "rainbow_trout", 0x4A5D23, 0x1A1A1A,
                    new Item.Properties()));

    /** 江鳕 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> BURBOT_SPAWN_EGG =
            ITEMS.register("burbot_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("burbot"), "burbot", 0x4A4433, 0x2E2A22,
                    new Item.Properties()));

    /** 哲罗鲑 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> TAIMEN_SPAWN_EGG =
            ITEMS.register("taimen_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("taimen"), "taimen", 0x6B6A4B, 0x2B2B2B,
                    new Item.Properties()));

    /** 北极鳕 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ARCTIC_COD_SPAWN_EGG =
            ITEMS.register("arctic_cod_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("arctic_cod"), "arctic_cod", 0x6B5A3A, 0x8C9296,
                    new Item.Properties()));

    /** 毛鳞鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> CAPELIN_SPAWN_EGG =
            ITEMS.register("capelin_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("capelin"), "capelin", 0x4A5D23, 0x3A4247,
                    new Item.Properties()));

    /** 大西洋鲱 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ATLANTIC_HERRING_SPAWN_EGG =
            ITEMS.register("atlantic_herring_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("atlantic_herring"), "atlantic_herring", 0x2E4A6B, 0x9AA096,
                    new Item.Properties()));

    /** 狼鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> WOLFFISH_SPAWN_EGG =
            ITEMS.register("wolffish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("wolffish"), "wolffish", 0x6E7A6E, 0x2E2E2A,
                    new Item.Properties()));

    /** 平鲉 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> REDFISH_SPAWN_EGG =
            ITEMS.register("redfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("redfish"), "redfish", 0xD13B27, 0x2E2E2A,
                    new Item.Properties()));

    /** 裸盖鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> SABLEFISH_SPAWN_EGG =
            ITEMS.register("sablefish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("sablefish"), "sablefish", 0x4A4A4A, 0x2E2E2E,
                    new Item.Properties()));

    /** 大西洋鳕 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ATLANTIC_COD_SPAWN_EGG =
            ITEMS.register("atlantic_cod_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("atlantic_cod"), "atlantic_cod", 0x6B5A3A, 0xC0C4C8,
                    new Item.Properties()));

    /** 格陵兰大比目鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GREENLAND_HALIBUT_SPAWN_EGG =
            ITEMS.register("greenland_halibut_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("greenland_halibut"), "greenland_halibut", 0x5A4A32, 0x3A3228,
                    new Item.Properties()));

    /** 格陵兰鲨 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GREENLAND_SHARK_SPAWN_EGG =
            ITEMS.register("greenland_shark_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("greenland_shark"), "greenland_shark", 0x96907F, 0x3A3A38,
                    new Item.Properties()));

    /** 雨伞旗鱼 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> SAILFISH_SPAWN_EGG =
            ITEMS.register("sailfish_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("sailfish"), "sailfish", 0x17293D, 0x1F4E79,
                    new Item.Properties()));

    /** 鲯鳅 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> MAHI_MAHI_SPAWN_EGG =
            ITEMS.register("mahi_mahi_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("mahi_mahi"), "mahi_mahi", 0x1E8C8C, 0x7A6410,
                    new Item.Properties()));

    /** 鳄雀鳝 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ALLIGATOR_GAR_SPAWN_EGG =
            ITEMS.register("alligator_gar_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("alligator_gar"), "alligator_gar", 0x4B5320, 0xE8E4C0,
                    new Item.Properties()));

    /** 巨暹罗鲤 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> GIANT_SIAMESE_CARP_SPAWN_EGG =
            ITEMS.register("giant_siamese_carp_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("giant_siamese_carp"), "giant_siamese_carp", 0x1B2A4A, 0x1A1A1A,
                    new Item.Properties()));

    /** 电鳗 —— 调试用刷怪蛋，上架前连同创造页签那一行一起删（见 docs/12 打包清单）。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> ELECTRIC_EEL_SPAWN_EGG =
            ITEMS.register("electric_eel_spawn_egg", () -> new com.whitecobalt.anglertackle.item.FishSpawnEggItem(
                    com.whitecobalt.anglertackle.registry.ModEntities.FISHES.get("electric_eel"), "electric_eel", 0x4A3520, 0xC9C0A8,
                    new Item.Properties()));

    /** 鱼种 id → 刷怪蛋。给调试命令与检查脚本用，顺序与 FISH_IDS 一致。 */
    public static final java.util.Map<String, DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem>> FISH_SPAWN_EGGS =
            buildEggMap();

    private static java.util.Map<String, DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem>> buildEggMap() {
        java.util.Map<String, DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem>> map = new java.util.LinkedHashMap<>();
        map.put("tilapia", TILAPIA_SPAWN_EGG);
        map.put("african_butterflyfish", AFRICAN_BUTTERFLYFISH_SPAWN_EGG);
        map.put("crucian_carp", CRUCIAN_CARP_SPAWN_EGG);
        map.put("mirror_carp", MIRROR_CARP_SPAWN_EGG);
        map.put("jewel_cichlid", JEWEL_CICHLID_SPAWN_EGG);
        map.put("mullet", MULLET_SPAWN_EGG);
        map.put("zander", ZANDER_SPAWN_EGG);
        map.put("atlantic_salmon", ATLANTIC_SALMON_SPAWN_EGG);
        map.put("bluefin_tuna", BLUEFIN_TUNA_SPAWN_EGG);
        map.put("brown_trout", BROWN_TROUT_SPAWN_EGG);
        map.put("tigerfish", TIGERFISH_SPAWN_EGG);
        map.put("topmouth_culter", TOPMOUTH_CULTER_SPAWN_EGG);
        map.put("hemiculter", HEMICULTER_SPAWN_EGG);
        map.put("striped_bass", STRIPED_BASS_SPAWN_EGG);
        map.put("platinum_arowana", PLATINUM_AROWANA_SPAWN_EGG);
        map.put("oxeye_tarpon", OXEYE_TARPON_SPAWN_EGG);
        map.put("banded_loach", BANDED_LOACH_SPAWN_EGG);
        map.put("guppy", GUPPY_SPAWN_EGG);
        map.put("elephantnose", ELEPHANTNOSE_SPAWN_EGG);
        map.put("golden_loach", GOLDEN_LOACH_SPAWN_EGG);
        map.put("cave_blindfish", CAVE_BLINDFISH_SPAWN_EGG);
        map.put("grass_carp", GRASS_CARP_SPAWN_EGG);
        map.put("black_carp", BLACK_CARP_SPAWN_EGG);
        map.put("pike", PIKE_SPAWN_EGG);
        map.put("arapaima", ARAPAIMA_SPAWN_EGG);
        map.put("north_african_catfish", NORTH_AFRICAN_CATFISH_SPAWN_EGG);
        map.put("sapphire_snakehead", SAPPHIRE_SNAKEHEAD_SPAWN_EGG);
        map.put("juvenile_shark", JUVENILE_SHARK_SPAWN_EGG);
        map.put("arctic_char", ARCTIC_CHAR_SPAWN_EGG);
        map.put("whitefish", WHITEFISH_SPAWN_EGG);
        map.put("rainbow_trout", RAINBOW_TROUT_SPAWN_EGG);
        map.put("burbot", BURBOT_SPAWN_EGG);
        map.put("taimen", TAIMEN_SPAWN_EGG);
        map.put("arctic_cod", ARCTIC_COD_SPAWN_EGG);
        map.put("capelin", CAPELIN_SPAWN_EGG);
        map.put("atlantic_herring", ATLANTIC_HERRING_SPAWN_EGG);
        map.put("wolffish", WOLFFISH_SPAWN_EGG);
        map.put("redfish", REDFISH_SPAWN_EGG);
        map.put("sablefish", SABLEFISH_SPAWN_EGG);
        map.put("atlantic_cod", ATLANTIC_COD_SPAWN_EGG);
        map.put("greenland_halibut", GREENLAND_HALIBUT_SPAWN_EGG);
        map.put("greenland_shark", GREENLAND_SHARK_SPAWN_EGG);
        map.put("sailfish", SAILFISH_SPAWN_EGG);
        map.put("mahi_mahi", MAHI_MAHI_SPAWN_EGG);
        map.put("alligator_gar", ALLIGATOR_GAR_SPAWN_EGG);
        map.put("giant_siamese_carp", GIANT_SIAMESE_CARP_SPAWN_EGG);
        map.put("electric_eel", ELECTRIC_EEL_SPAWN_EGG);
        return java.util.Collections.unmodifiableMap(map);
    }
    // ==== END 鱼刷怪蛋字段 ====

    /**
     * 渔夫小屋定位线索 —— 用一次，报出最近那座小屋的坐标（docs/13 §2）。
     *
     * <p>用户 2026-10-02："清楚罗盘物品，改为「渔夫小屋定位线索」，
     * 使用8张纸与一条mod鱼合成，使用后直接告诉距离玩家最近的小屋坐标。"
     * 原 {@code tracking_compass}（常驻指向的罗盘）已整个删除 ——
     * 小屋现在的刷新率和村庄同档（spacing 34），玩家需要的是"知道在哪"，
     * 而不是背包里挂一个一直转的箭头。
     *
     * <p>没有耐久：它是一次性道具，用一次少一张（判在
     * {@link com.whitecobalt.anglertackle.item.HutClueItem#use}）。
     *
     * <p>网格资源用的是**原版**的 {@code minecraft:item/paper} 做底 ——
     * 直接引用资源路径不涉及再分发。
     */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.HutClueItem> HUT_CLUE =
            ITEMS.register("hut_clue", () -> new com.whitecobalt.anglertackle.item.HutClueItem(
                    new Item.Properties()));

    // ⚠ 「失灵的罗盘」（broken_compass）**故意还没注册**，不是漏了：
    //   策划案 §2.2 写的修理配方是「追踪罗盘 + 1 鱼币」，而鱼币**不是物品**
    //   （你定的：鱼币走独立数据面板，不做物品化），当不了合成材料。
    //   修理路径定下来之前注册它，只会得到一个"拿到手但没有任何用途"的东西，
    //   还会被 check-creative-tab.js 判成"注册了却没上架"。
    //   等修理方案定了（比如改成「失灵罗盘 + 指南针」）再一起接。

    // ------------------------------------------------------------------ 料理：调料与工具
    // 见 docs/25（鱼料理与配料方案）§3：新调料只有三样（盐 / 酱油 / 芥末），
    // 加上鱼油与鱼刀，就是**整条烹饪线上会参与配方的全部新物品**。
    // 鱼块与成品菜是后续批次（同一条链：切块 → 加热 → 调味），先不在这里注册。
    //
    // ⚠ 这 5 件必须同时具备四样东西，少一样表现都不一样（check-creative-tab.js 守着）：
    //   ① ITEMS.register  ② 创造页签 accept  ③ 中英语言键  ④ 贴图 + 模型
    //   图标由 tools/gen-kitchen-icons.js 画（可复现，不是手抠的 png）。

    /** 盐：香煎、盐烤、腌渍的基础调料。配方见 tools 里的说明（水桶 + 干海带，空桶返还）。 */
    public static final DeferredItem<SeasoningItem> SALT = ITEMS.register("salt",
            () -> new SeasoningItem("salt", net.minecraft.ChatFormatting.WHITE,
                    new Item.Properties().stacksTo(64)));

    /** 酱油：红烧 / 清蒸 / 刺身蘸料。 */
    public static final DeferredItem<SeasoningItem> SOY_SAUCE = ITEMS.register("soy_sauce",
            () -> new SeasoningItem("soy_sauce", net.minecraft.ChatFormatting.GOLD,
                    new Item.Properties().stacksTo(64)));

    /** 芥末：刺身的可选搭配（不给配方加门槛，只是"更够味"）。 */
    public static final DeferredItem<SeasoningItem> MUSTARD = ITEMS.register("mustard",
            () -> new SeasoningItem("mustard", net.minecraft.ChatFormatting.YELLOW,
                    new Item.Properties().stacksTo(64)));

    /**
     * 鱼油：取代原方案里"向日葵 + 玻璃瓶榨植物油"那套（用户 2026-10-03 定）。
     *
     * <p>榨油的配方在**鱼块那一批**：普通鱼 2 块榨 1 瓶、多脂鱼 1 块榨 1 瓶 ——
     * 因为要读鱼块里的数据（哪条鱼、肥不肥），普通 JSON 配方写不出来，
     * 得等切块的自定义配方类一起上。现在先注册物品，创造模式可拿到。
     */
    public static final DeferredItem<SeasoningItem> FISH_OIL = ITEMS.register("fish_oil",
            () -> new SeasoningItem("fish_oil", net.minecraft.ChatFormatting.YELLOW,
                    new Item.Properties().stacksTo(64)));

    // ══════════════════════════════════════════════════════════════════════════════
    // 特殊处理的产物（B①）—— 用户 2026-10-11 定案："处理得当可降一级"。
    //
    // 为什么做成**独立的材料物品**，而不是"给原来那条鱼加一个已处理标记"：
    //   加标记就要动 FISH_CHUNK / FISH_DATA 的**存档格式** ✗ —— 而这是**老功能**
    //   （老存档里躺着老鱼、老菜），用户明确要求"**别连代码都不会兼容**"。
    //   独立物品 ⇒ **存档格式零改动** ✓ 老存档完全不受影响 ✓。
    //
    // 配方是**普通 JSON 合成**（data/anglertackle/recipe/process_*.json）——
    //   因为**每条鱼本身就是独立物品**（anglertackle:greenland_shark 这种），
    //   普通 JSON 就能精确指定鱼种，不需要再写一个自定义配方类 ✓。
    //
    // 贴图：用户 2026-10-11 用 AutoMods 出的 64×64 原图，**原样进仓库、没有降采样** ——
    //   本仓库物品贴图本来就是 64×64 占绝对多数（80 张 64×64 / 25 张 128 / 13 张 16），
    //   尺寸天然对齐 ✓（所以那套"降色板 + 描边"的转换**不需要用**）。
    //   ⚠ 已纳入 `参考资料/图标指纹.json` 的守护（check-item-icons.js）—— 以后谁改了必红 ✓。
    // ══════════════════════════════════════════════════════════════════════════════

    //  ── D1（2026-10-11）：这 4 件**做成可直接食用** ─────────────────────────────
    //  用户定案 B① 是"**处理得当可降一级**"（docs/料理适配策划案.md §4）。
    //  ⚠ 我一开始想"再给它们各配一道安全菜" ✗ —— 但那是**多绕一层** ✗：
    //    现实里 hákarl 就是切块直接吃 ✓ 冷冻刺身就是生吃 ✓ 去卵鱼肉就是直接下锅 ✓
    //    ⇒ **直接可食**既贴事实 ✓ 又不用动做菜系统（那套按 FISH_CHUNK 匹配 ✓ 而这几件不是鱼块 ✗）。
    //  ⚠ 而"降一级"这件事**天然成立** ✓：它们不是 DishItem / FishChunkItem ✗
    //    ⇒ **不会走到 `FishPalatability.apply`** ✓ ⇒ 吃它们**永远没有味道惩罚** ✓✓
    //       —— 从"格陵兰鲨会凋零"到"发酵鲨肉安全"，这就是那"一级" ✓。
    //  饱食度统一按**金档菜**的水平（6 / 0.8 ✓，即 FoodValues.dishHunger(4) 那一档 ✓）。

    /** 发酵鲨肉（hákarl）：格陵兰鲨鲜肉含尿素 + TMAO、FishBase 记 "Poisonous to eat"，发酵之后才能吃。 */
    public static final DeferredItem<Item> HAKARL = ITEMS.register("hakarl",
            () -> new Item(new Item.Properties().stacksTo(64)
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(6).saturationModifier(0.8F).build())));

    /** 冷冻刺身：虹鳟这类淡水鱼的寄生虫风险靠**冷冻**解决（香港食物安全中心口径：-20℃ 7 天）。 */
    public static final DeferredItem<Item> FROZEN_SASHIMI = ITEMS.register("frozen_sashimi",
            () -> new Item(new Item.Properties().stacksTo(64)
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(6).saturationModifier(0.8F).build())));

    /** 处理过的电鳗：300~800V 的那位，**耗电**之后才安全。 */
    public static final DeferredItem<Item> SHOCKED_EEL = ITEMS.register("shocked_eel",
            () -> new Item(new Item.Properties().stacksTo(64)
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(6).saturationModifier(0.8F).build())));

    /** 去卵鳄雀鳝肉：鳄雀鳝的**卵与内脏剧毒**（省市场监管局安全提示），去干净才是安全的肉。 */
    public static final DeferredItem<Item> BONELESS_GAR = ITEMS.register("boneless_gar",
            () -> new Item(new Item.Properties().stacksTo(64)
                    .food(new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(6).saturationModifier(0.8F).build())));

    /**
     * 鱼刀：做刺身的工具（配方与耐久的说明见 {@link FishKnifeItem}）。
     *
     * <p>耐久写在物品属性里，上限与 {@link FishKnifeItem#DURABILITY} 同源，
     * 不写死第二个数字。
     */
    public static final DeferredItem<FishKnifeItem> FISH_KNIFE = ITEMS.register("fish_knife",
            () -> new FishKnifeItem(new Item.Properties()
                    .stacksTo(1)
                    .durability(FishKnifeItem.DURABILITY)));

    // ------------------------------------------------------------------ 冰钓（用户 2026-10-08 拍板，docs/33 §8）
    /** 凿冰镐：右键四种冰 → 冰洞；右键冰洞 → 碎冰。耐久上限取自 {@link IcePickItem#DURABILITY}。 */
    public static final DeferredItem<com.whitecobalt.anglertackle.item.IcePickItem> ICE_PICK =
            ITEMS.register("ice_pick", () -> new com.whitecobalt.anglertackle.item.IcePickItem(
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(com.whitecobalt.anglertackle.item.IcePickItem.DURABILITY)));

    // 冰钓的三种方块也注册成 BlockItem（创造栏/JEI 拿得到，方便测试与摆景）。
    // ⚠ 它们的掉落表是**空的**（挖掘不掉落任何东西）—— 用户定案"不允许用它刷水/刷冰"。
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ICE_HOLE =
            ITEMS.register("ice_hole", () -> new net.minecraft.world.item.BlockItem(
                    com.whitecobalt.anglertackle.registry.ModBlocks.ICE_HOLE.get(),
                    new Item.Properties()));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> BROKEN_ICE =
            ITEMS.register("broken_ice", () -> new net.minecraft.world.item.BlockItem(
                    com.whitecobalt.anglertackle.registry.ModBlocks.BROKEN_ICE.get(),
                    new Item.Properties()));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> PACK_ICE =
            ITEMS.register("pack_ice", () -> new net.minecraft.world.item.BlockItem(
                    com.whitecobalt.anglertackle.registry.ModBlocks.PACK_ICE.get(),
                    new Item.Properties()));

    // ------------------------------------------------------------------ 料理：三档鱼块（生 3 + 熟 3）
    // 见 docs/25 §2：三档材料是**鱼的品质**决定的（白绿→普通、蓝紫→优质、金红→顶级），
    // 不是鱼的种类 —— 整鱼切块时按这条映射产出对应物品，鱼种/重量进 FISH_CHUNK 组件。
    //
    // ⚠ 档位**必须进物品 id**：第 3 级的配方要按档位匹配材料（鱼汤要普通块、香煎要优质肉、
    //   刺身要顶级排），而原版配方只能按物品 id 匹配，匹配不了数据组件。
    //   反过来"鱼种"绝不能进 id：28 条鱼 × 3 档 × 生熟 = 168 个物品。

    /**
     * 生鱼块的属性。
     *
     * <p>⚠ **必须带默认 FISH_CHUNK 组件**（代表鱼种，见 {@link #sampleChunkData}）：
     * 创造模式 / JEI 里直接拿到的那一块没有数据的话，**任何菜都做不出来**——
     * 2026-10-03 用户实机踩到："即使显示能做也弄不了"。真实切块出来的鱼块
     * 会被配方写上"那条鱼"的数据，覆盖这个默认值。
     */
    private static Item.Properties chunkProps(int group) {
        return new Item.Properties().stacksTo(16)
                .component(ModDataComponents.FISH_CHUNK.get(), sampleChunkData(group));
    }

    /** 普通鱼块（白绿品质的鱼切出来的）。 */
    public static final DeferredItem<FishChunkItem> FISH_CHUNK = ITEMS.register("fish_chunk",
            () -> new FishChunkItem(FishChunkData.GROUP_COMMON, false, chunkProps(FishChunkData.GROUP_COMMON)));
    /** 优质鱼肉（蓝紫品质）。 */
    public static final DeferredItem<FishChunkItem> FINE_FISH_MEAT = ITEMS.register("fine_fish_meat",
            () -> new FishChunkItem(FishChunkData.GROUP_FINE, false, chunkProps(FishChunkData.GROUP_FINE)));
    /** 顶级鱼排（金红品质）。 */
    public static final DeferredItem<FishChunkItem> SUPREME_FISH_STEAK = ITEMS.register("supreme_fish_steak",
            () -> new FishChunkItem(FishChunkData.GROUP_SUPREME, false, chunkProps(FishChunkData.GROUP_SUPREME)));
    /** 熟鱼块 = 熔炉**清烤**的产物，可直接吃，也是所有第 3 级菜的主料。 */
    public static final DeferredItem<FishChunkItem> FISH_CHUNK_COOKED = ITEMS.register("fish_chunk_cooked",
            () -> new FishChunkItem(FishChunkData.GROUP_COMMON, true,
                    cookedChunkProps(FishChunkData.GROUP_COMMON)));
    public static final DeferredItem<FishChunkItem> FINE_FISH_MEAT_COOKED = ITEMS.register("fine_fish_meat_cooked",
            () -> new FishChunkItem(FishChunkData.GROUP_FINE, true, cookedChunkProps(FishChunkData.GROUP_FINE)));
    public static final DeferredItem<FishChunkItem> SUPREME_FISH_STEAK_COOKED = ITEMS.register("supreme_fish_steak_cooked",
            () -> new FishChunkItem(FishChunkData.GROUP_SUPREME, true,
                    cookedChunkProps(FishChunkData.GROUP_SUPREME)));

    /**
     * 熟鱼块的属性：能吃 + 默认鱼种组件 + **兜底饱食度**。
     *
     * <p>真正的数值由熔炉/烟熏炉/营火配方按**鱼的档位**写进产物栈
     * （见 {@code DataCookingRecipe.inheritChunkData} 与 {@code FoodValues}）——
     * 注册属性只能给一个固定值，这里给的是"这块物品的默认鱼种档位"对应的那一档
     * （普通→1 档、优质→2 档、顶级→5 档，见 {@code SAMPLE_CHUNK_TIER}），
     * 于是创造模式拿出来的那一块，tooltip 与数值是自洽的。
     */
    private static Item.Properties cookedChunkProps(int group) {
        return new Item.Properties().stacksTo(16)
                .component(ModDataComponents.FISH_CHUNK.get(), sampleChunkData(group))
                .food(com.whitecobalt.anglertackle.fishing.FoodValues.cooked(SAMPLE_CHUNK_TIER[
                        Math.max(0, Math.min(SAMPLE_CHUNK_TIER.length - 1, group))]));
    }

    // ------------------------------------------------------------------ 料理：成品菜（12 道"菜型"）
    // 见 docs/25 §14.3 规则①：产出物品**按菜型注册**，不按鱼 —— "哪条鱼、什么档位"进数据组件。
    // 所以这里 12 件物品对应 12 种做法，而不是 23 条鱼 × N 道菜。
    // 贴图来自用户给的参考图（`参考资料/菜品贴图/`，抠图流程见 build/subagent-out/cutout.js
    // → tools/build-dish-icons.js），尺寸 64² / 128² / 256²（按参考图原生像素画整数倍放大）。
    //
    // ⚠ 蒲烧（kabayaki）**不做**（用户 2026-10-03："蒲烧不做"）。
    // ⚠ 饱食度按**鱼的档位**走（用户 2026-10-03 定案，见 fishing/FoodValues）：
    //    做菜时由 DishRecipe 把数值写进产物栈；这里的注册值只是"没经过配方"时的兜底
    //    （创造模式 / JEI 直接拿），取中间档（2 档）那一档。

    /** 成品菜通用属性：能吃、不叠太多（熏鱼例外，见下）。 */
    private static Item.Properties dishProps() {
        return new Item.Properties()
                .stacksTo(16)
                .food(com.whitecobalt.anglertackle.fishing.FoodValues.dish(2));
    }

    /**
     * **碗装菜**的属性：在原版机制下"吃完返还碗"。
     *
     * <p>用户 2026-10-03 的要求：在森罗物语那边要用碗盛（他们的汤锅出餐靠 carrier 那一栏），
     * "我希望如此制作的物品在使用后像森罗那样返还碗"。所以三道汤走
     * {@link Item.Properties#craftRemainder}（和原版蘑菇煲/甜菜汤同款的原版机制）：
     * 吃完把空碗还回背包 —— 不需要依赖森罗，也不需要他们那个 {@code IHasContainer}。
     *
     * <p>⚠ 这是**物品本身**的性质，所以工作台做出来的同一道汤也一样返还碗
     * （同一件物品不可能"看是谁做的"分两套行为）。
     */
    private static Item.Properties soupProps() {
        return dishProps().craftRemainder(net.minecraft.world.item.Items.BOWL);
    }

    /** 刺身：生料 + 酱油 + 鱼刀（唯一走生料的菜）。 */
    public static final DeferredItem<DishItem> SASHIMI = ITEMS.register("sashimi",
            () -> new DishItem("sashimi", dishProps()));
    /** 鱼汤：熟块 + 水桶（返还空桶）。碗装 → 吃完返还碗。 */
    public static final DeferredItem<DishItem> FISH_SOUP = ITEMS.register("fish_soup",
            () -> new DishItem("fish_soup", soupProps()));
    /** 奶白鱼汤：熟块 + 水桶 + 鱼油。碗装 → 吃完返还碗。 */
    public static final DeferredItem<DishItem> CREAMY_FISH_SOUP = ITEMS.register("creamy_fish_soup",
            () -> new DishItem("creamy_fish_soup", soupProps()));
    /** 慢炖鱼：熟块 + 水桶 + 甘蔗（姜）。碗装 → 吃完返还碗。 */
    public static final DeferredItem<DishItem> FISH_STEW = ITEMS.register("fish_stew",
            () -> new DishItem("fish_stew", soupProps()));
    /** 清蒸鱼：熟块 + 水桶 + 甘蔗 + 酱油。 */
    public static final DeferredItem<DishItem> STEAMED_FISH = ITEMS.register("steamed_fish",
            () -> new DishItem("steamed_fish", dishProps()));
    /** 红烧鱼：熟块 + 酱油 + 糖。 */
    public static final DeferredItem<DishItem> BRAISED_FISH = ITEMS.register("braised_fish",
            () -> new DishItem("braised_fish", dishProps()));
    /** 香煎鱼：熟块 + 鱼油 + 盐。 */
    public static final DeferredItem<DishItem> PAN_FRIED_FISH = ITEMS.register("pan_fried_fish",
            () -> new DishItem("pan_fried_fish", dishProps()));
    /** 油炸鱼块：熟块 + 鱼油 + 小麦。 */
    public static final DeferredItem<DishItem> FRIED_FISH_CHUNK = ITEMS.register("fried_fish_chunk",
            () -> new DishItem("fried_fish_chunk", dishProps()));
    /** 鱼丸：熟块 ×2 + 小麦。 */
    public static final DeferredItem<DishItem> FISH_BALL = ITEMS.register("fish_ball",
            () -> new DishItem("fish_ball", dishProps()));
    /** 整条油炸：整鱼 + 鱼油 + 小麦（横纹鱼 / 白条鱼专用，它们切不出块）。 */
    public static final DeferredItem<DishItem> DEEP_FRIED_FISH = ITEMS.register("deep_fried_fish",
            () -> new DishItem("deep_fried_fish", dishProps()));
    /** 熏鱼：烟熏炉产出，**堆叠 64**（保存性好、适合带出门 —— docs/25 §5）。 */
    public static final DeferredItem<DishItem> SMOKED_FISH = ITEMS.register("smoked_fish",
            () -> new DishItem("smoked_fish", new Item.Properties()
                    .stacksTo(64)
                    .food(com.whitecobalt.anglertackle.fishing.FoodValues.dish(2))));
    /** 炭烤鱼：营火产出（野外现做）。 */
    public static final DeferredItem<DishItem> CHARRED_FISH = ITEMS.register("charred_fish",
            () -> new DishItem("charred_fish", dishProps()));

    // ------------------------------------------------------------------ 方块
    /**
     * 鱼架：右击把鱼摆上去，空手右击取回。
     *
     * <p>方块物品要用 {@code DeferredBlock.toStack()} 之外的标准写法：
     * 注册成 BlockItem，模型走 {@code models/item/fish_rack.json}（parent 指向方块模型）。
     */
    /** 水族箱：拼起来是一口无缝的缸，里面是水，能放鱼。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> AQUARIUM =
            ITEMS.register("aquarium", () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.AQUARIUM.get(), new Item.Properties()));

    public static final DeferredItem<net.minecraft.world.item.BlockItem> FISH_RACK =
            ITEMS.register("fish_rack", () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.FISH_RACK.get(), new Item.Properties()));

    /**
     * 放生台：右击把手里的鱼放回附近的水里。
     *
     * <p>和水族箱一样是普通 BlockItem：方块的名字键走 {@code block.*}
     * （BlockItem 自己会回退过去），模型 {@code models/item/release_platform.json}
     * 的 parent 指向方块模型，于是拿在手上是缩小过的 3D 方块。
     */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> RELEASE_PLATFORM =
            ITEMS.register("release_platform", () -> new net.minecraft.world.item.BlockItem(
                    ModBlocks.RELEASE_PLATFORM.get(), new Item.Properties()));

    // ------------------------------------------------------------------ 创造标签
    /** 木板凳（配方 砖砖砖/.棍./棍.棍）。右击坐下。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> STOOL =
            ITEMS.register("stool", () -> new net.minecraft.world.item.BlockItem(
                    com.whitecobalt.anglertackle.registry.ModBlocks.STOOL.get(),
                    new net.minecraft.world.item.Item.Properties()));

    /** 带坐垫的木板凳（皮革 + 木板凳）。坐着的回拉加成比光板凳多 1%。 */
    public static final DeferredItem<net.minecraft.world.item.BlockItem> CUSHIONED_STOOL =
            ITEMS.register("cushioned_stool", () -> new net.minecraft.world.item.BlockItem(
                    com.whitecobalt.anglertackle.registry.ModBlocks.CUSHIONED_STOOL.get(),
                    new net.minecraft.world.item.Item.Properties()));
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.anglertackle"))
                    .icon(() -> new ItemStack(WOOD_FLOAT_ROD.get()))
                    .displayItems((params, output) -> {
                        // ★ 测试拟饵放**最前面**：它是最容易找不到的东西
                        //   （贴图复用镀金的、名字又长），放最后会被淹没在一堆物品里。
                        //   上架前删除本行即可。
                        output.accept(STOOL.get());
                output.accept(CUSHIONED_STOOL.get());
                output.accept(TEST_LURE.get());
                // 垂纶终端（2026-10-15）：一个入口收齐委托/图鉴/邮箱/成就/称号/商店/排行榜/联赛/比赛。
                // 放最前面 —— 它是最该被找到的东西（与 TEST_LURE 同一条理由）。
                output.accept(TERMINAL.get());
                        // 钓竿：五档材质 × 两种竿型
                        output.accept(WOOD_FLOAT_ROD.get());
                        output.accept(WOOD_LURE_ROD.get());
                        output.accept(IRON_FLOAT_ROD.get());
                        output.accept(IRON_LURE_ROD.get());
                        output.accept(GOLD_FLOAT_ROD.get());
                        output.accept(GOLD_LURE_ROD.get());
                        output.accept(DIAMOND_FLOAT_ROD.get());
                        output.accept(DIAMOND_LURE_ROD.get());
                        output.accept(NETHERITE_FLOAT_ROD.get());
                        output.accept(NETHERITE_LURE_ROD.get());
                        // 鱼线：5 档
                        output.accept(HEMP_LINE.get());
                        output.accept(COTTON_LINE.get());
                        output.accept(NYLON_LINE.get());
                        output.accept(FLUORO_LINE.get());
                        output.accept(PE_LINE.get());
                        output.accept(TITANIUM_LINE.get());
                        // 渔轮（5 档）/ 鱼钩（浮漂已删除）
                        output.accept(HAND_REEL.get());
                        output.accept(SPINNING_REEL.get());
                        output.accept(DRUM_REEL.get());
                        output.accept(BAITCAST_REEL.get());
                        output.accept(HEAVY_REEL.get());
                        output.accept(SINKER_G8.get());
                        output.accept(SINKER_G2.get());
                        output.accept(SINKER_3B.get());
                        output.accept(SINKER_5B.get());
                        output.accept(SINKER_03.get());
                        output.accept(SINKER_05.get());
                        // 鱼获与教程书（按品质从低到高）
                        output.accept(CRUCIAN_CARP.get());
                        output.accept(TILAPIA.get());
                        output.accept(BANDED_LOACH.get());     // 白绿 · 仅台钓
                        output.accept(HEMICULTER.get());       // 白绿 · 仅台钓
                        output.accept(MULLET.get());
                        output.accept(GRASS_CARP.get());
                        output.accept(BLACK_CARP.get());
                        output.accept(TOPMOUTH_CULTER.get());
                        output.accept(ZANDER.get());
                        output.accept(SAPPHIRE_SNAKEHEAD.get());   // 红 · 仅台钓
                        output.accept(ATLANTIC_SALMON.get());
                        output.accept(PLATINUM_AROWANA.get());     // 金红 · 仅路亚
                        output.accept(BLUEFIN_TUNA.get());
                        output.accept(OXEYE_TARPON.get());         // 红 · 仅路亚
                        output.accept(JUVENILE_SHARK.get());
                        // ---- 第二批 6 种，全部仅路亚 ----
                        output.accept(STRIPED_BASS.get());         // 绿蓝紫 · 仅路亚
                        output.accept(PIKE.get());                 // 紫金 · 仅路亚
                        output.accept(ARAPAIMA.get());             // 紫金红 · 仅路亚
                        output.accept(TIGERFISH.get());            // 金红 · 仅路亚
                        output.accept(BROWN_TROUT.get());          // 金红 · 仅路亚（粘稠）
                        output.accept(MIRROR_CARP.get());          // 金红 · 仅路亚
                        // ---- 第三批 5 种（用户 2026-10-01 指定）----
                        output.accept(AFRICAN_BUTTERFLYFISH.get()); // 紫金 · 仅台钓（水面层）
                        output.accept(JEWEL_CICHLID.get());        // 紫金 · 仅台钓（体高侧扁）
                        output.accept(ELEPHANTNOSE.get());         // 紫金 · 仅台钓（底栖弱电）
                        output.accept(GOLDEN_LOACH.get());         // 蓝紫 · 仅台钓（洞穴，第四批）
                        output.accept(CAVE_BLINDFISH.get());       // 蓝紫 · 仅台钓（洞穴，第四批）
                        output.accept(NORTH_AFRICAN_CATFISH.get()); // 紫金 · 仅路亚（泥底巨物）
                        output.accept(GUPPY.get());                // 金红 · 仅台钓（最小）
                        // ---- 第 1 批：冷水 5 条（用户 2026-10-08）----
                        // 顺序与 tools/fish-list.js 一致（那个清单是创造栏展示顺序的真源）
                        output.accept(ARCTIC_CHAR.get());          // 金红 · 通用（高寒湖耐力型）
                        output.accept(WHITEFISH.get());            // 绿蓝 · 仅台钓（高寒湖中层）
                        output.accept(RAINBOW_TROUT.get());        // 紫金 · 仅路亚（冷水溪流）
                        output.accept(BURBOT.get());               // 蓝紫 · 仅台钓（冷水溪流底栖）
                        output.accept(TAIMEN.get());               // 金红 · 仅路亚（世界最大鲑科）
                        // ---- 第 3 批：极地 9 条（用户 2026-10-08）----
                        // 顺序与 tools/fish-list.js 一致（那个清单是创造栏展示顺序的真源）
                        output.accept(ARCTIC_COD.get());           // 绿蓝 · 仅台钓（冰下小杂鱼）
                        output.accept(CAPELIN.get());              // 绿蓝 · 仅台钓（饵料鱼）
                        output.accept(ATLANTIC_HERRING.get());     // 蓝紫 · 仅台钓（群游）
                        output.accept(WOLFFISH.get());             // 紫金 · 仅台钓（碎壳专家）
                        output.accept(REDFISH.get());              // 紫金 · 仅路亚（慢速底层）
                        output.accept(SABLEFISH.get());            // 紫金 · 仅路亚（深水泥底）
                        output.accept(ATLANTIC_COD.get());         // 紫金 · 通用（极地主力）
                        output.accept(GREENLAND_HALIBUT.get());    // 金红 · 仅路亚（底层巨物）
                        output.accept(GREENLAND_SHARK.get());      // 金红 · 仅路亚（极地终极）
                        // ---- 第 4 批：远洋 5 条（用户 2026-10-08）----
                        output.accept(SAILFISH.get());             // 金红 · 仅路亚（深海速度顶点）
                        output.accept(MAHI_MAHI.get());            // 金红 · 仅路亚（深海）
                        output.accept(ALLIGATOR_GAR.get());        // 紫金红 · 仅路亚（河湖入侵种）
                        output.accept(GIANT_SIAMESE_CARP.get());   // 金红 · 仅台钓（世界最大鲤科）
                        output.accept(ELECTRIC_EEL.get());         // 紫金 · 仅路亚（沼泽/河流）
                        // 老柯刷怪蛋：紧跟在教程书后面。它是调试物品，
                        // 上架前要么删掉要么挪到独立的"调试"页签。
                        output.accept(FISHERMAN_SPAWN_EGG.get());
                        // 鱼架：把钓到的鱼摆出来看（复用鱼的实体模型）
                        output.accept(FISH_RACK.get());
                        output.accept(AQUARIUM.get());
                        // 放生台：不想建缸的玩家靠它把鱼放回河里（docs/20 §3.5）
                        output.accept(RELEASE_PLATFORM.get());
                        // ---- 冰钓（用户 2026-10-08，docs/33 §8）----
                        output.accept(ICE_PICK.get());
                        output.accept(ICE_HOLE.get());
                        output.accept(BROKEN_ICE.get());
                        output.accept(PACK_ICE.get());
                        // 26 个鱼的刷怪蛋（调试用，同样是生成块；上架前整段删除）。
                        // 放在鱼获后面、紧挨着，方便一条条拿出来对着看。
                        // ▼ 鱼刷怪蛋上架插入点（gen-fish-model.js；锚注必须保留）
                        // ==== BEGIN 鱼刷怪蛋上架（gen-fish-model.js 生成，勿手改）====
                        output.accept(TILAPIA_SPAWN_EGG.get());
                        output.accept(AFRICAN_BUTTERFLYFISH_SPAWN_EGG.get());
                        output.accept(CRUCIAN_CARP_SPAWN_EGG.get());
                        output.accept(MIRROR_CARP_SPAWN_EGG.get());
                        output.accept(JEWEL_CICHLID_SPAWN_EGG.get());
                        output.accept(MULLET_SPAWN_EGG.get());
                        output.accept(ZANDER_SPAWN_EGG.get());
                        output.accept(ATLANTIC_SALMON_SPAWN_EGG.get());
                        output.accept(BLUEFIN_TUNA_SPAWN_EGG.get());
                        output.accept(BROWN_TROUT_SPAWN_EGG.get());
                        output.accept(TIGERFISH_SPAWN_EGG.get());
                        output.accept(TOPMOUTH_CULTER_SPAWN_EGG.get());
                        output.accept(HEMICULTER_SPAWN_EGG.get());
                        output.accept(STRIPED_BASS_SPAWN_EGG.get());
                        output.accept(PLATINUM_AROWANA_SPAWN_EGG.get());
                        output.accept(OXEYE_TARPON_SPAWN_EGG.get());
                        output.accept(BANDED_LOACH_SPAWN_EGG.get());
                        output.accept(GUPPY_SPAWN_EGG.get());
                        output.accept(ELEPHANTNOSE_SPAWN_EGG.get());
                        output.accept(GOLDEN_LOACH_SPAWN_EGG.get());
                        output.accept(CAVE_BLINDFISH_SPAWN_EGG.get());
                        output.accept(GRASS_CARP_SPAWN_EGG.get());
                        output.accept(BLACK_CARP_SPAWN_EGG.get());
                        output.accept(PIKE_SPAWN_EGG.get());
                        output.accept(ARAPAIMA_SPAWN_EGG.get());
                        output.accept(NORTH_AFRICAN_CATFISH_SPAWN_EGG.get());
                        output.accept(SAPPHIRE_SNAKEHEAD_SPAWN_EGG.get());
                        output.accept(JUVENILE_SHARK_SPAWN_EGG.get());
                        output.accept(ARCTIC_CHAR_SPAWN_EGG.get());
                        output.accept(WHITEFISH_SPAWN_EGG.get());
                        output.accept(RAINBOW_TROUT_SPAWN_EGG.get());
                        output.accept(BURBOT_SPAWN_EGG.get());
                        output.accept(TAIMEN_SPAWN_EGG.get());
                        output.accept(ARCTIC_COD_SPAWN_EGG.get());
                        output.accept(CAPELIN_SPAWN_EGG.get());
                        output.accept(ATLANTIC_HERRING_SPAWN_EGG.get());
                        output.accept(WOLFFISH_SPAWN_EGG.get());
                        output.accept(REDFISH_SPAWN_EGG.get());
                        output.accept(SABLEFISH_SPAWN_EGG.get());
                        output.accept(ATLANTIC_COD_SPAWN_EGG.get());
                        output.accept(GREENLAND_HALIBUT_SPAWN_EGG.get());
                        output.accept(GREENLAND_SHARK_SPAWN_EGG.get());
                        output.accept(SAILFISH_SPAWN_EGG.get());
                        output.accept(MAHI_MAHI_SPAWN_EGG.get());
                        output.accept(ALLIGATOR_GAR_SPAWN_EGG.get());
                        output.accept(GIANT_SIAMESE_CARP_SPAWN_EGG.get());
                        output.accept(ELECTRIC_EEL_SPAWN_EGG.get());
                        // ==== END 鱼刷怪蛋上架 ====
                        // 追踪罗盘：放在教程书前面 —— 它是"找不到小屋"时的第一反应
                        output.accept(HUT_CLUE.get());
                        output.accept(GUIDE_BOOK.get());
                        // 图鉴书：和手册紧挨着放，玩家一眼能看到"两本书"的区别
                        output.accept(CODEX_BOOK.get());
                        // 菜谱：第三本书，讲做菜与效果（合成 = 任意熟鱼 + 书）
                        output.accept(COOKBOOK.get());
                        // 邮箱：邮件系统的主入口（docs/36 §5.7）
                        output.accept(MAILBOX.get());
                        // 鱼饵（台钓/路亚通用）
                        output.accept(BAIT_BREAD.get());
                        output.accept(BAIT_WORM.get());
                        output.accept(BAIT_CORN.get());
                        output.accept(BAIT_SHRIMP.get());
                        output.accept(BAIT_GLOW.get());
                        output.accept(BAIT_SECRET.get());
                        // 拟饵（路亚专用，绿→蓝→紫→金→红）
                        output.accept(LURE_SPOON.get());
                        output.accept(LURE_GLOWSPOON.get());     // 绿档 · 同亮片数值（认 timid）
                        output.accept(LURE_MINNOW.get());
                        output.accept(LURE_SOFT.get());
                        output.accept(LURE_DEPTHFINDER.get());   // 蓝档 · 同软虫数值
                        output.accept(LURE_DEEP.get());
                        output.accept(LURE_PADDLETAIL.get());    // 紫档 · 同浅层刺激拟饵数值
                        output.accept(LURE_RATTLE.get());
                        output.accept(LURE_VIBRATION.get());     // 金档 · 同钢珠声诱数值
                        output.accept(LURE_GILDED.get());
                        // 窝料
                        output.accept(CHUM_GRAIN.get());
                        output.accept(CHUM_MEAT.get());
                        output.accept(CHUM_FRUIT.get());
                        output.accept(CHUM_SECRET.get());
                    })
                    .build());

    // ------------------------------------------------------------------ 料理创造页签
    /**
     * 「料理」页签：调料 / 工具 / （后续）三档鱼块与成品菜。
     *
     * <p>为什么不塞进主页面：主页面已经有 100 多件东西（10 根竿、6 条线、5 个轮、
     * 28 条鱼、6 种饵、12 个拟饵…），再往里塞烹饪线，玩家翻不到。
     * 烹饪是一条**独立的玩法线**（钓到鱼 → 切块 → 加热 → 调味），给它自己一页。
     *
     * <p>图标用鱼刀：它是这条线上第一件"必须做出来才能往下走"的东西。
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> COOKING_TAB =
            TABS.register("cooking", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.anglertackle.cooking"))
                    .icon(() -> new ItemStack(FISH_KNIFE.get()))
                    .displayItems((params, output) -> {
                        // 工具
                        output.accept(FISH_KNIFE.get());
                        // 调料（按使用频率：盐 → 酱油 → 芥末）
                        output.accept(SALT.get());
                        output.accept(SOY_SAUCE.get());
                        output.accept(MUSTARD.get());
                        // 加工食材
                        output.accept(FISH_OIL.get());
                        // 特殊处理的产物（B①，2026-10-11）—— 先让它们在创造页签里拿得到
                        output.accept(HAKARL.get());
                        output.accept(FROZEN_SASHIMI.get());
                        output.accept(SHOCKED_EEL.get());
                        output.accept(BONELESS_GAR.get());
                        // 三档鱼块：生 3 + 熟 3（熟的是熔炉清烤的产物，可直接吃）
                        output.accept(FISH_CHUNK.get());
                        output.accept(FISH_CHUNK_COOKED.get());
                        output.accept(FINE_FISH_MEAT.get());
                        output.accept(FINE_FISH_MEAT_COOKED.get());
                        output.accept(SUPREME_FISH_STEAK.get());
                        output.accept(SUPREME_FISH_STEAK_COOKED.get());
                        // 成品菜（12 道菜型；按"菜型"注册，鱼种与档位走数据组件）
                        output.accept(SASHIMI.get());
                        output.accept(FISH_SOUP.get());
                        output.accept(CREAMY_FISH_SOUP.get());
                        output.accept(FISH_STEW.get());
                        output.accept(STEAMED_FISH.get());
                        output.accept(BRAISED_FISH.get());
                        output.accept(PAN_FRIED_FISH.get());
                        output.accept(FRIED_FISH_CHUNK.get());
                        output.accept(FISH_BALL.get());
                        output.accept(DEEP_FRIED_FISH.get());
                        output.accept(SMOKED_FISH.get());
                        output.accept(CHARRED_FISH.get());
                    })
                    .build());

    private ModItems() {
    }

    /** 配件通用属性：耐久（0 表示不可损坏）+ 稀有度。 */
    private static Item.Properties partProps(int durability, Rarity rarity, PartStats stats) {
        Item.Properties props = new Item.Properties().rarity(rarity);
        if (durability > 0) {
            props = props.durability(durability);
        }
        return props.component(ModDataComponents.PART_STATS.get(), stats);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

}
