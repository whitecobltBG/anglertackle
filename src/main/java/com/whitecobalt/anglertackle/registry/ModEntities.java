package com.whitecobalt.anglertackle.registry;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.entity.BobberEntity;
import com.whitecobalt.anglertackle.entity.ChumEntity;
import com.whitecobalt.anglertackle.entity.FishermanEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 实体注册。
 *
 * <p>浮漂的 {@code clientTrackingRange} 与 {@code updateInterval} 是
 * docs/09-原版冲突与渲染优化.md §4.1 的落地：
 * <ul>
 *   <li>{@code clientTrackingRange(10)}：原版浮漂约 4 区块（64 格），我们的鱼线最长 70 格，
 *       所以单独放宽到 10 区块。**注意**：该值大于客户端视距时以视距为准，
 *       超出部分靠"幽灵鱼线"渲染兜底。</li>
 *   <li>{@code updateInterval(1)}：每 tick 同步位置——原版默认 3 tick，
 *       画"线抖动"会跳帧。</li>
 * </ul>
 */
public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, AnglersTackle.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<BobberEntity>> BOBBER =
            ENTITIES.register("bobber", () -> EntityType.Builder
                    .<BobberEntity>of(BobberEntity::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f)
                    .clientTrackingRange(10)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("anglertackle:bobber"));

    /**
     * 打窝料实体（右键抛出，像鸡蛋一样飞出去，落水后浮着冒泡 + 画白圈）。
     *
     * <p>{@code clientTrackingRange(12)}：窝子的白圈半径是 10 格，
     * 跟踪范围必须**大于**这个半径，否则玩家站在圈边缘时实体不在客户端，
     * 圈会缺一块甚至整个不画。12 = 10 + 余量。
     *
     * <p>{@code updateInterval(1)}：飞行阶段要每 tick 同步位置，
     * 否则抛物线一跳一跳的（和浮漂同理）。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ChumEntity>> CHUM =
            ENTITIES.register("chum", () -> EntityType.Builder
                    .<ChumEntity>of(ChumEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    // 2026-10-30：12 → 16 区块（256 格）。渲染距离改到 128 格之后，
                    // 追踪范围必须 ≥ 它，否则"能画但没数据"照样看不见。
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSummon()
                    .fireImmune()
                    .build("anglertackle:chum"));

    /**
     * 渔人 · 老柯（NPC 商人，见 {@code docs/13-新手链路与NPC系统.md} §1.3）。
     *
     * <p>{@code clientTrackingRange(10)}：他站在码头上，玩家要在十几格外就能看见
     * "那边有个人"——那就是"找到小屋了"的信号，所以给得比一般生物宽。
     *
     * <p>{@code updateInterval(3)}：原版默认值。他走得很慢，每 tick 同步位置纯属
     * 浪费带宽 —— 和浮漂/窝料那种抛物线飞行完全不同（那俩是 1）。
     *
     * <p>**故意不加 {@code noSummon()}**：浮漂和窝料加了，因为它们只能由本模组
     * 的逻辑生成；老柯不一样，调试时要用刷怪蛋或 {@code /summon} 单独放一只出来，
     * 否则每改一行代码都得先去找半小时结构。
     */
    public static final DeferredHolder<EntityType<?>, EntityType<FishermanEntity>> FISHERMAN =
            ENTITIES.register("fisherman", () -> EntityType.Builder
                    .<FishermanEntity>of(FishermanEntity::new, MobCategory.CREATURE)
                    .sized(0.6f, 1.95f)      // 和村民一样大
                    .clientTrackingRange(10)
                    .updateInterval(3)
                    .build("anglertackle:fisherman"));

    // ---------------------------------------------------------------- 26 种鱼
    //
    // 全部指向**同一个** ModFish 类 —— 见那个类的注释：AbstractFish 把鱼桶交互、
    // AI、寻路、游泳、搁浅扑腾全包了，26 种鱼的差异只在贴图上。
    //
    // ⚠ 下面几个字段的**文本顺序不能改**：Java 的静态初始化按文本顺序执行，
    //   static 块用到 FISH_IDS / SIZE_OF，它们就必须写在块前面。

    /** 全部鱼种 id（和 data/anglertackle/tags/item/fishes.json 一致，检查脚本会核对）。 */
    public static final String[] FISH_IDS = {
            "crucian_carp", "tilapia", "mullet", "grass_carp", "black_carp", "topmouth_culter",
            "zander", "atlantic_salmon", "bluefin_tuna", "juvenile_shark", "banded_loach",
            "hemiculter", "sapphire_snakehead", "platinum_arowana", "oxeye_tarpon", "tigerfish",
            "pike", "brown_trout", "mirror_carp", "striped_bass", "arapaima",
            // 第三批 5 种（用户 2026-10-01 指定）：4 种仅台钓 + 1 种仅路亚
            "african_butterflyfish", "north_african_catfish", "jewel_cichlid", "guppy", "elephantnose",
            // 第四批 2 种（用户 2026-10-02 指定）：都是洞穴专属台钓鱼、蓝紫档
            "golden_loach", "cave_blindfish",
            // 第 1 批：冷水 5 条（用户 2026-10-08，docs/33 §9.2）：高寒湖 2 + 冷水溪流 3
            "arctic_char", "whitefish", "rainbow_trout", "burbot", "taimen",
            // 第 3 批：极地 9 条（用户 2026-10-08）；冰鱼用户明确**不做**，不在其中
            "arctic_cod", "capelin", "atlantic_herring", "wolffish", "redfish",
            "sablefish", "atlantic_cod", "greenland_halibut", "greenland_shark",
            // 第 4 批：远洋 5 条（用户 2026-10-08）
            "sailfish", "mahi_mahi", "alligator_gar", "giant_siamese_carp", "electric_eel",
    };

    /**
     * 按体型给碰撞箱：大鱼看得出来更大。
     *
     * <p>尺寸不再是手写的 —— 它和渲染缩放、模型长度同出一源
     * （{@link com.whitecobalt.anglertackle.fish.FishVariants}，由 tools/gen-fish-model.js
     * 从真实体长算出）。手写 SIZE_OF 时踩过一次：模型放大了但碰撞箱没跟着动，
     * 表现是"鱼比自己的判定框大一圈"，用桶捞会捞到空气。
     */
    public static final java.util.Map<String, DeferredHolder<EntityType<?>, EntityType<com.whitecobalt.anglertackle.entity.ModFish>>> FISHES =
            new java.util.LinkedHashMap<>();

    /** EntityType → 鱼种 id 的反查表（懒填充，speciesId() 每帧都会被问到）。 */
    private static final java.util.Map<EntityType<?>, String> SPECIES_OF = new java.util.IdentityHashMap<>();

    static {
        for (String id : FISH_IDS) {
            com.whitecobalt.anglertackle.fish.FishVariants.Variant v =
                    com.whitecobalt.anglertackle.fish.FishVariants.of(id);
            // WATER_AMBIENT + clientTrackingRange(4)：照抄原版四种鱼的注册写法。
            // 不写 updateInterval —— 原版也没写，用 Builder 默认的 3。
            FISHES.put(id, ENTITIES.register(id, () -> EntityType.Builder
                    .<com.whitecobalt.anglertackle.entity.ModFish>of(
                            com.whitecobalt.anglertackle.entity.ModFish::new, MobCategory.WATER_AMBIENT)
                    .sized(v.hitW(), v.hitH())
                    .clientTrackingRange(4)
                    .build("anglertackle:" + id)));
        }
    }

    /** 这条 EntityType 是哪个鱼种。查不到时退回鲫鱼（不该发生，但不该崩）。 */
    public static String speciesOf(EntityType<?> type) {
        String cached = SPECIES_OF.get(type);
        if (cached != null) {
            return cached;
        }
        for (var e : FISHES.entrySet()) {
            if (e.getValue().isBound() && e.getValue().get() == type) {
                SPECIES_OF.put(type, e.getKey());
                return e.getKey();
            }
        }
        return "crucian_carp";
    }

    private ModEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }
    /** 板凳座位。座位实体由 StoolBlock 右击时生成（见 SeatEntity.sit）。 */
    public static final DeferredHolder<EntityType<?>, EntityType<com.whitecobalt.anglertackle.entity.SeatEntity>> STOOL_SEAT =
            ENTITIES.register("stool_seat", () -> EntityType.Builder
                    .<com.whitecobalt.anglertackle.entity.SeatEntity>of(com.whitecobalt.anglertackle.entity.SeatEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.5F)
                    .clientTrackingRange(10)
                    .build("stool_seat"));

}
