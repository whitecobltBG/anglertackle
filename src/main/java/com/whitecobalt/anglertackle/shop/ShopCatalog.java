package com.whitecobalt.anglertackle.shop;

import java.util.List;

/**
 * 商店目录（老柯卖什么、多少钱、几档解锁）。
 *
 * <p><b>本文件由 {@code tools/gen-shop-prices.js} 生成，不要手改。</b>
 * 规则在 {@code tools/shop-rules.js}，改动后重跑：
 * {@code node tools/gen-shop-prices.js --write}。
 *
 * <p>为什么生成而不是手写：40 件商品的价格/免费/兑换/限购散在手写代码里，
 * 改一次规则就要记得改好几处，而漏改的表现是"某件商品价格不对"——
 * 没人会去逐个核对 40 件。生成 + 漂移检查能保证规则和代码永远一致。
 */
public final class ShopCatalog {

    /** 商品分类（商店的"分页"就按它分）。 */
    public enum Category { ROD, LINE, REEL, SINKER, LURE, BAIT, CHUM }

    /** 获取方式。 */
    public enum Kind {
        /** 免费（麻线、手线轮）。 */
        FREE,
        /** 花鱼币买。 */
        BUY,
        /** 用鱼兑换（消耗鱼，不给钱）。 */
        EXCHANGE
    }

    /**
     * 一条商品。
     *
     * @param id            物品 id（不含命名空间）
     * @param category      分类（分页用）
     * @param tier          品质档位 1–6
     * @param kind          获取方式
     * @param price         买价（{@code BUY} 才有意义；{@code FREE} 是 0）
     * @param unlockStage   需要老柯等级到几档才能买（1 = 开局）
     * @param stockPer      每买几个售罄（0 = 不限购）
     * @param restockMinutes 售罄后等几分钟补货（0 = 不补货，即不限购）
     * @param exchange      兑换要求的描述（人类可读，判定逻辑另写）；空串 = 不是兑换
     */
    public record Entry(String id, Category category, int tier, Kind kind, int price,
                        int unlockStage, int stockPer, int restockMinutes, String exchange) {

        public boolean limited() {
            return stockPer > 0 && restockMinutes > 0;
        }
    }

    public static final List<Entry> ENTRIES = List.of(
            // ---- 竿,
            new Entry("wood_float_rod", Category.ROD, 1, Kind.BUY, 200, 1, 0, 0, ""),
            new Entry("wood_lure_rod", Category.ROD, 1, Kind.BUY, 200, 1, 0, 0, ""),
            new Entry("iron_float_rod", Category.ROD, 2, Kind.BUY, 300, 2, 0, 0, ""),
            new Entry("iron_lure_rod", Category.ROD, 2, Kind.BUY, 300, 2, 0, 0, ""),
            new Entry("gold_float_rod", Category.ROD, 3, Kind.BUY, 1500, 3, 0, 0, ""),
            new Entry("gold_lure_rod", Category.ROD, 3, Kind.BUY, 1500, 3, 0, 0, ""),
            new Entry("diamond_float_rod", Category.ROD, 4, Kind.BUY, 4500, 4, 0, 0, ""),
            new Entry("diamond_lure_rod", Category.ROD, 4, Kind.BUY, 4500, 4, 0, 0, ""),
            new Entry("netherite_float_rod", Category.ROD, 5, Kind.BUY, 15000, 5, 0, 0, ""),
            new Entry("netherite_lure_rod", Category.ROD, 5, Kind.BUY, 15000, 5, 0, 0, ""),
            // ---- 鱼线,
            new Entry("hemp_line", Category.LINE, 1, Kind.FREE, 0, 1, 0, 0, ""),
            new Entry("cotton_line", Category.LINE, 2, Kind.BUY, 200, 1, 0, 0, ""),
            new Entry("nylon_line", Category.LINE, 3, Kind.BUY, 500, 2, 0, 0, ""),
            new Entry("fluoro_line", Category.LINE, 4, Kind.BUY, 2300, 3, 0, 0, ""),
            new Entry("pe_line", Category.LINE, 5, Kind.BUY, 2700, 4, 5, 120, ""),
            new Entry("titanium_line", Category.LINE, 6, Kind.EXCHANGE, 0, 5, 0, 120, "anyRedx2"),
            // ---- 渔轮,
            new Entry("hand_reel", Category.REEL, 1, Kind.FREE, 0, 1, 0, 0, ""),
            new Entry("spinning_reel", Category.REEL, 2, Kind.BUY, 500, 1, 0, 0, ""),
            new Entry("drum_reel", Category.REEL, 3, Kind.BUY, 3750, 2, 0, 0, ""),
            new Entry("baitcast_reel", Category.REEL, 4, Kind.BUY, 20000, 3, 0, 0, ""),
            new Entry("heavy_reel", Category.REEL, 5, Kind.EXCHANGE, 0, 4, 0, 0, "atlantic_salmon+brown_trout+tigerfish@REDx1@each"),
            // ---- 铅锤,
            new Entry("sinker_g2", Category.SINKER, 1, Kind.BUY, 100, 1, 0, 0, ""),
            new Entry("sinker_g8", Category.SINKER, 1, Kind.BUY, 100, 1, 0, 0, ""),
            new Entry("sinker_03", Category.SINKER, 2, Kind.BUY, 100, 1, 0, 0, ""),
            new Entry("sinker_3b", Category.SINKER, 2, Kind.BUY, 100, 1, 0, 0, ""),
            new Entry("sinker_5b", Category.SINKER, 2, Kind.BUY, 100, 1, 0, 0, ""),
            new Entry("sinker_05", Category.SINKER, 3, Kind.BUY, 100, 2, 0, 0, ""),
            // ---- 拟饵,
            new Entry("lure_glowspoon", Category.LURE, 2, Kind.BUY, 440, 1, 0, 0, ""),
            new Entry("lure_spoon", Category.LURE, 2, Kind.BUY, 440, 1, 0, 0, ""),
            new Entry("lure_depthfinder", Category.LURE, 3, Kind.BUY, 390, 2, 0, 0, ""),
            new Entry("lure_minnow", Category.LURE, 3, Kind.BUY, 450, 2, 0, 0, ""),
            new Entry("lure_soft", Category.LURE, 3, Kind.BUY, 390, 2, 0, 0, ""),
            new Entry("lure_deep", Category.LURE, 4, Kind.BUY, 528, 3, 0, 0, ""),
            new Entry("lure_paddletail", Category.LURE, 4, Kind.BUY, 528, 3, 0, 0, ""),
            new Entry("lure_rattle", Category.LURE, 5, Kind.BUY, 580, 4, 0, 0, ""),
            new Entry("lure_vibration", Category.LURE, 5, Kind.BUY, 580, 4, 0, 0, ""),
            new Entry("lure_gilded", Category.LURE, 6, Kind.BUY, 700, 5, 0, 0, ""),
            // lure_test_red —— 调试物品，**不进商店**（调试物品 —— 不上架（发布前要删，见 docs/13 开放项40））,
            // ---- 鱼饵,
            new Entry("bait_bread", Category.BAIT, 0, Kind.BUY, 440, 1, 0, 0, ""),
            new Entry("bait_worm", Category.BAIT, 0, Kind.BUY, 440, 1, 0, 0, ""),
            new Entry("bait_corn", Category.BAIT, 1, Kind.BUY, 450, 1, 0, 0, ""),
            new Entry("bait_shrimp", Category.BAIT, 3, Kind.BUY, 528, 2, 0, 0, ""),
            new Entry("bait_glow", Category.BAIT, 4, Kind.BUY, 580, 3, 0, 0, ""),
            new Entry("bait_secret", Category.BAIT, 5, Kind.BUY, 700, 4, 0, 0, ""),
            // ---- 窝料,
            new Entry("chum_grain", Category.CHUM, 0, Kind.BUY, 40, 1, 0, 0, ""),
            new Entry("chum_meat", Category.CHUM, 1, Kind.BUY, 125, 1, 0, 0, ""),
            new Entry("chum_fruit", Category.CHUM, 2, Kind.BUY, 300, 1, 0, 0, ""),
            new Entry("chum_secret", Category.CHUM, 3, Kind.BUY, 800, 2, 0, 0, "")
    );

    /**
     * 一条「鱼 → 原版物品」的兑换（用户 2026-10-02 定案）。
     *
     * <p>与上面的 {@link Entry} 分开：那个是"卖模组配件"，这个是"用鱼换原版物品"，
     * 产出可以是原版物品（甚至附魔书），不受配件目录约束。
     *
     * @param fishId      要交的鱼种 id
     * @param minTier     最低品质档（**0 起算**：0 白 … 5 红），达到这一档及以上的该种鱼才算
     * @param fishCount   要几条
     * @param resultId    产出物品 id（可以是原版物品）
     * @param resultCount 产出几个
     * @param enchant     附魔 id（空串 = 不附魔）—— 附魔书不是普通物品，得单独带组件
     * @param enchantLevel 附魔等级（1 = 不写等级的普通附魔）
     * @param fixedSpecies 产出的**固定**鱼种（空串 = 产出不带鱼数据）。用户 2026-10-11 的 A3：
     *                     兑换"熏鱼"必须给固定组件 —— 否则 16 个各带一份不同数据 ⇒ **堆不起来** ✗
     * @param fixedTier    固定档位（0 起算；金 = 4）
     * @param fixedWeightKg 固定重量（kg）—— 它也是组件的一部分，必须固定
     */
    public record Exchange(String fishId, int minTier, int fishCount,
                           String resultId, int resultCount, String enchant, int enchantLevel,
                           String fixedSpecies, int fixedTier, float fixedWeightKg) {

        public boolean enchanted() {
            return !enchant.isEmpty();
        }

        /** 这条兑换的产出要不要写**固定鱼数据**（熏鱼那两条用得上）。 */
        public boolean fixedFishData() {
            return !fixedSpecies.isEmpty();
        }
    }

    public static final List<Exchange> EXCHANGES = List.of(
            new Exchange("golden_loach", 2, 3, "anglertackle:salt", 16, "", 1, "", 0, 0f),
            new Exchange("cave_blindfish", 2, 3, "anglertackle:soy_sauce", 8, "", 1, "", 0, 0f),
            new Exchange("atlantic_herring", 2, 4, "anglertackle:salt", 16, "", 1, "", 0, 0f),
            new Exchange("burbot", 3, 3, "anglertackle:mustard", 16, "", 1, "", 0, 0f),
            new Exchange("whitefish", 3, 3, "anglertackle:fish_oil", 8, "", 1, "", 0, 0f),
            new Exchange("arctic_cod", 2, 2, "anglertackle:fish_oil", 12, "", 1, "", 0, 0f),
            new Exchange("wolffish", 3, 2, "anglertackle:soy_sauce", 32, "", 1, "", 0, 0f),
            new Exchange("sablefish", 3, 4, "anglertackle:fish_oil", 24, "", 1, "", 0, 0f),
            new Exchange("banded_loach", 1, 4, "minecraft:bone_meal", 16, "", 1, "", 0, 0f),
            new Exchange("crucian_carp", 1, 4, "minecraft:string", 8, "", 1, "", 0, 0f),
            new Exchange("hemiculter", 1, 4, "minecraft:bread", 4, "", 1, "", 0, 0f),
            new Exchange("tilapia", 1, 4, "minecraft:leather", 8, "", 1, "", 0, 0f),
            new Exchange("grass_carp", 2, 3, "minecraft:hay_block", 8, "", 1, "", 0, 0f),
            new Exchange("mullet", 2, 3, "minecraft:kelp", 24, "", 1, "", 0, 0f),
            new Exchange("zander", 3, 3, "minecraft:copper_ingot", 16, "", 1, "", 0, 0f),
            new Exchange("striped_bass", 3, 3, "minecraft:gold_ingot", 16, "", 1, "", 0, 0f),
            new Exchange("black_carp", 3, 3, "minecraft:iron_ingot", 16, "", 1, "", 0, 0f),
            new Exchange("topmouth_culter", 3, 2, "minecraft:iron_ingot", 16, "", 1, "", 0, 0f),
            new Exchange("pike", 4, 2, "minecraft:diamond", 3, "", 1, "", 0, 0f),
            new Exchange("north_african_catfish", 4, 1, "minecraft:enchanted_book", 1, "minecraft:unbreaking", 3, "", 0, 0f),
            new Exchange("jewel_cichlid", 4, 1, "minecraft:enchanted_book", 1, "minecraft:sharpness", 5, "", 0, 0f),
            new Exchange("african_butterflyfish", 4, 1, "minecraft:enchanted_book", 1, "minecraft:protection", 4, "", 0, 0f),
            new Exchange("elephantnose", 4, 1, "minecraft:enchanted_book", 1, "minecraft:looting", 3, "", 0, 0f),
            new Exchange("mirror_carp", 4, 1, "minecraft:enchanted_book", 1, "minecraft:luck_of_the_sea", 3, "", 0, 0f),
            new Exchange("atlantic_salmon", 4, 1, "minecraft:enchanted_book", 1, "minecraft:lure", 3, "", 0, 0f),
            new Exchange("brown_trout", 4, 1, "minecraft:enchanted_book", 1, "minecraft:respiration", 3, "", 0, 0f),
            new Exchange("guppy", 4, 1, "minecraft:enchanted_book", 1, "minecraft:depth_strider", 3, "", 0, 0f),
            new Exchange("tigerfish", 4, 1, "minecraft:enchanted_book", 1, "minecraft:power", 5, "", 0, 0f),
            new Exchange("sapphire_snakehead", 5, 3, "minecraft:enchanted_book", 1, "minecraft:wind_burst", 3, "", 0, 0f),
            new Exchange("juvenile_shark", 5, 3, "minecraft:enchanted_book", 1, "minecraft:breach", 4, "", 0, 0f),
            new Exchange("bluefin_tuna", 5, 3, "minecraft:enchanted_book", 1, "minecraft:density", 5, "", 0, 0f),
            new Exchange("oxeye_tarpon", 5, 1, "minecraft:enchanted_book", 1, "minecraft:mending", 1, "", 0, 0f),
            new Exchange("north_african_catfish", 4, 2, "minecraft:enchanted_book", 1, "anglertackle:reinforced", 1, "", 0, 0f),
            new Exchange("jewel_cichlid", 4, 2, "minecraft:enchanted_book", 1, "anglertackle:rescue", 1, "", 0, 0f),
            new Exchange("african_butterflyfish", 4, 2, "minecraft:enchanted_book", 1, "anglertackle:drag", 1, "", 0, 0f),
            new Exchange("elephantnose", 4, 2, "minecraft:enchanted_book", 1, "anglertackle:load_bearing", 1, "", 0, 0f),
            new Exchange("north_african_catfish", 4, 3, "minecraft:enchanted_book", 1, "anglertackle:precise_hunt", 1, "", 0, 0f),
            new Exchange("atlantic_cod", 4, 2, "anglertackle:smoked_fish", 16, "", 1, "atlantic_cod", 4, 5f),
            new Exchange("greenland_halibut", 4, 2, "anglertackle:smoked_fish", 16, "", 1, "greenland_halibut", 4, 30f),
            new Exchange("capelin", 1, 4, "anglertackle:fish_oil", 4, "", 1, "", 0, 0f),
            new Exchange("arctic_cod", 2, 4, "minecraft:nautilus_shell", 2, "", 1, "", 0, 0f),
            new Exchange("rainbow_trout", 4, 2, "minecraft:emerald", 8, "", 1, "", 0, 0f),
            new Exchange("redfish", 4, 2, "minecraft:amethyst_shard", 12, "", 1, "", 0, 0f),
            new Exchange("electric_eel", 4, 2, "minecraft:redstone_block", 4, "", 1, "", 0, 0f),
            new Exchange("taimen", 5, 3, "minecraft:netherite_scrap", 1, "", 1, "", 0, 0f),
            new Exchange("arctic_char", 5, 3, "minecraft:diamond_block", 1, "", 1, "", 0, 0f),
            new Exchange("greenland_shark", 5, 3, "minecraft:heart_of_the_sea", 1, "", 1, "", 0, 0f),
            new Exchange("sailfish", 5, 3, "minecraft:sponge", 8, "", 1, "", 0, 0f),
            new Exchange("mahi_mahi", 5, 3, "minecraft:experience_bottle", 24, "", 1, "", 0, 0f),
            new Exchange("giant_siamese_carp", 5, 3, "minecraft:golden_apple", 8, "", 1, "", 0, 0f),
            new Exchange("arapaima", 5, 1, "minecraft:enchanted_book", 1, "minecraft:fortune", 3, "", 0, 0f),
            new Exchange("platinum_arowana", 5, 1, "minecraft:enchanted_book", 1, "minecraft:silk_touch", 1, "", 0, 0f)
    );

    /**
     * 一条**联赛商店报价**（花联赛积分换东西，`docs/39` §4）。
     *
     * <p>产物字段与 {@link Exchange} 同构：发货时拼一个 Exchange 丢给
     * {@code ShopMaterials.resultStack(...)} 即可，所以附魔书 / 固定品质鱼 / 原版物资
     * **全都走现成那条路**，不需要第二套发货代码 ✓
     *
     * @param id             稳定 id（进存档，**永不改名**；也是"每届限购"的计数键）
     * @param points         积分价
     * @param limitPerSeason 每届限购几个（0 = 不限购）
     * @param tier           档位（0 白 … 5 红；界面排序与取色用）
     * @param resultId       产物物品 id（**可带命名空间** ⇒ 原版物品也装得下）
     * @param resultCount    产物数量
     * @param enchant        附魔 id（空串 = 不附魔）
     * @param enchantLevel   附魔等级
     * @param fixedSpecies   固定鱼种（空串 = 不带鱼数据）
     * @param fixedTier      固定档位（0 起算；红 = 5）
     * @param fixedWeightKg  固定重量
     * @param titleTemplate  买到手授予的称号模板 id（空串 = 不发称号）
     */
    public record LeagueOffer(String id, int points, int limitPerSeason, int tier,
                             String resultId, int resultCount, String enchant, int enchantLevel,
                             String fixedSpecies, int fixedTier, float fixedWeightKg,
                             String titleTemplate) {

        /** 每届限购 0 = 不限购。 */
        public boolean limited() {
            return limitPerSeason > 0;
        }
    }

    public static final List<LeagueOffer> LEAGUE_OFFERS = List.of(
            new LeagueOffer("lure_deep", 20, 2, 4, "anglertackle:lure_deep", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("lure_paddletail", 20, 2, 4, "anglertackle:lure_paddletail", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("nylon_line", 70, 3, 3, "anglertackle:nylon_line", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("fluoro_line", 280, 3, 4, "anglertackle:fluoro_line", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("pe_line", 500, 2, 5, "anglertackle:pe_line", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("titanium_line_low", 750, 1, 6, "anglertackle:titanium_line", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("titanium_line_high", 1050, 1, 6, "anglertackle:titanium_line", 1, "", 1, "", 0, 0f, ""),
            new LeagueOffer("red_tigerfish", 300, 1, 5, "anglertackle:tigerfish", 1, "", 1, "tigerfish", 5, 0f, ""),
            new LeagueOffer("red_atlantic_salmon", 300, 1, 5, "anglertackle:atlantic_salmon", 1, "", 1, "atlantic_salmon", 5, 0f, ""),
            new LeagueOffer("red_brown_trout", 300, 1, 5, "anglertackle:brown_trout", 1, "", 1, "brown_trout", 5, 0f, ""),
            new LeagueOffer("ench_high_quality_lure_3", 500, 0, 4, "minecraft:enchanted_book", 1, "anglertackle:high_quality_lure", 3, "", 0, 0f, ""),
            new LeagueOffer("ench_mending", 500, 0, 4, "minecraft:enchanted_book", 1, "minecraft:mending", 1, "", 0, 0f, ""),
            new LeagueOffer("iron_ingot_32", 200, 1, 1, "minecraft:iron_ingot", 32, "", 1, "", 0, 0f, ""),
            new LeagueOffer("diamond_8", 500, 1, 3, "minecraft:diamond", 8, "", 1, "", 0, 0f, ""),
            new LeagueOffer("netherite_ingot_5", 700, 1, 5, "minecraft:netherite_ingot", 5, "", 1, "", 0, 0f, ""),
            new LeagueOffer("memorial", 500, 1, 4, "", 0, "", 1, "", 0, 0f, "league_memorial")
    );

    private ShopCatalog() {
    }
}
