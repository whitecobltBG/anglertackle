package com.whitecobalt.anglertackle.fishing;

/*
 * ⚠ 本文件由 tools/gen-fish-layers.js 从 tools/fish-layers.json **生成**，不要手改。
 *   要改某条鱼的水层：改那个 json，然后跑
 *     node tools/gen-fish-layers.js --write
 *   tools/check-fish-layers.js 会把本文件与生成器的输出逐字节比对，手改必红。
 *
 * 依据：仓库内 fish_research/<id>.json 的 waterLayer 字段（每条带来源 URL），
 *   判定口径见 docs/42-铅锤系统策划案.md §3.3。
 */
public final class FishLayers {

    private FishLayers() {
    }

    /**
     * 水层。**顺序即位序**（{@link #bit()}），不许重排 —— 铅锤倍率按位查表。
     */
    public enum Layer {
        /** 浅层 */
        SURFACE("surface"),
        /** 中层 */
        MID("mid"),
        /** 底层 */
        BOTTOM("bottom"),
        /** 贴底 */
        NEAR_BOTTOM("near_bottom");

        private final String id;

        Layer(String id) {
            this.id = id;
        }

        /** ASCII 数据键（配置 / 存档 / 生成器都用它）。 */
        public String id() {
            return this.id;
        }

        /** 位序：{@code 1 << ordinal()}，供掩码用。 */
        public int bit() {
            return 1 << this.ordinal();
        }

        /** 语言键：{@code layer.anglertackle.<id>}。 */
        public String langKey() {
            return "layer.anglertackle." + this.id;
        }
    }

    /** 鱼 id → 水层。找不到时返回 {@code null}（调用方自己决定兜底，绝不静默当中层）。 */
    private static final java.util.Map<String, Layer> FISH = new java.util.HashMap<>();

    static {
        FISH.put("tilapia", Layer.BOTTOM);
        FISH.put("african_butterflyfish", Layer.SURFACE);
        FISH.put("crucian_carp", Layer.NEAR_BOTTOM);
        FISH.put("mirror_carp", Layer.NEAR_BOTTOM);
        FISH.put("jewel_cichlid", Layer.MID);
        FISH.put("mullet", Layer.NEAR_BOTTOM);
        FISH.put("zander", Layer.MID);
        FISH.put("atlantic_salmon", Layer.MID);
        FISH.put("bluefin_tuna", Layer.SURFACE);
        FISH.put("brown_trout", Layer.MID);
        FISH.put("arctic_char", Layer.MID);
        FISH.put("whitefish", Layer.MID);
        FISH.put("rainbow_trout", Layer.MID);
        FISH.put("burbot", Layer.NEAR_BOTTOM);
        FISH.put("taimen", Layer.BOTTOM);
        FISH.put("arctic_cod", Layer.NEAR_BOTTOM);
        FISH.put("capelin", Layer.MID);
        FISH.put("atlantic_herring", Layer.MID);
        FISH.put("wolffish", Layer.NEAR_BOTTOM);
        FISH.put("redfish", Layer.BOTTOM);
        FISH.put("sablefish", Layer.BOTTOM);
        FISH.put("atlantic_cod", Layer.BOTTOM);
        FISH.put("greenland_halibut", Layer.BOTTOM);
        FISH.put("greenland_shark", Layer.BOTTOM);
        FISH.put("sailfish", Layer.SURFACE);
        FISH.put("mahi_mahi", Layer.SURFACE);
        FISH.put("alligator_gar", Layer.NEAR_BOTTOM);
        FISH.put("giant_siamese_carp", Layer.BOTTOM);
        FISH.put("electric_eel", Layer.BOTTOM);
        FISH.put("tigerfish", Layer.MID);
        FISH.put("topmouth_culter", Layer.SURFACE);
        FISH.put("hemiculter", Layer.SURFACE);
        FISH.put("striped_bass", Layer.BOTTOM);
        FISH.put("platinum_arowana", Layer.SURFACE);
        FISH.put("oxeye_tarpon", Layer.MID);
        FISH.put("banded_loach", Layer.NEAR_BOTTOM);
        FISH.put("guppy", Layer.SURFACE);
        FISH.put("elephantnose", Layer.NEAR_BOTTOM);
        FISH.put("grass_carp", Layer.MID);
        FISH.put("black_carp", Layer.NEAR_BOTTOM);
        FISH.put("pike", Layer.MID);
        FISH.put("arapaima", Layer.SURFACE);
        FISH.put("north_african_catfish", Layer.NEAR_BOTTOM);
        FISH.put("sapphire_snakehead", Layer.BOTTOM);
        FISH.put("juvenile_shark", Layer.NEAR_BOTTOM);
        FISH.put("golden_loach", Layer.NEAR_BOTTOM);
        FISH.put("cave_blindfish", Layer.MID);
    }

    /** 查一条鱼的水层；未知 id 返回 {@code null}。 */
    public static Layer of(String fishId) {
        return FISH.get(fishId);
    }

    /** 已知鱼种数（给自检用）。 */
    public static int size() {
        return FISH.size();
    }
}
