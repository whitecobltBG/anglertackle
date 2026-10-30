package com.whitecobalt.anglertackle.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.fishing.FishData;
import com.whitecobalt.anglertackle.item.PartStats;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 数据组件（1.20.5+ 物品不再有 NBT，鱼竿/鱼线/鱼获的属性都走组件）。
 *
 * <p>参考 docs/06-技术方案.md §2「关键 API 选型」。
 */
public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, AnglersTackle.MOD_ID);

    /** 配件属性（鱼线/渔轮/铅锤/浮漂共用）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<PartStats>> PART_STATS =
            COMPONENTS.register("part_stats", () -> DataComponentType.<PartStats>builder()
                    .persistent(PartStats.CODEC)
                    .networkSynchronized(PartStats.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    /** 钓竿的六槽装配状态（M2 用；M1 先注册占位，让竿能存住部件）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<RodParts>> ROD_PARTS =
            COMPONENTS.register("rod_parts", () -> DataComponentType.<RodParts>builder()
                    .persistent(RodParts.CODEC)
                    .networkSynchronized(RodParts.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    /** 鱼获数据：物种 / 体长 / 重量 / 尺寸评级 / 捕获时间（M3 用）。 */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<FishData>> FISH_DATA =
            COMPONENTS.register("fish_data", () -> DataComponentType.<FishData>builder()
                    .persistent(FishData.CODEC)
                    .networkSynchronized(FishData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    /**
     * 鱼块数据：物种 / 品质档位 / 切块时的重量（docs/25 §2、§14.5）。
     *
     * <p>整鱼 → 生块 → 熟块 → 菜，这份数据**一路继承**：
     * 所以"成品菜按菜型注册、一件物品一张图"才成立 —— 鱼的区分靠它，不靠物品 id。
     * 三档材料本身仍是**三个物品**（`fish_chunk` / `fine_fish_meat` /
     * `supreme_fish_steak`），因为配方只能按物品 id 匹配档位。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.whitecobalt.anglertackle.fishing.FishChunkData>> FISH_CHUNK =
            COMPONENTS.register("fish_chunk", () -> DataComponentType.<com.whitecobalt.anglertackle.fishing.FishChunkData>builder()
                    .persistent(com.whitecobalt.anglertackle.fishing.FishChunkData.CODEC)
                    .networkSynchronized(com.whitecobalt.anglertackle.fishing.FishChunkData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    // 这里曾有第三个组件 pending_parts（砂轮拆下的配件的暂存处），2026-10-02 删掉了：
    // 配件根本不需要暂存 —— 取走产出物那一刻，输入槽里那根竿还在，直接从它身上拆就行
    // （见 event/GrindstoneEvents 的类注释）。凡是要"暂存"的方案都得先回答
    // "取走时怎么把这份暂存找回来"，而两个现成的答案都是错的：
    // 用内存 Map 键 ItemStack（不按内容比相等，永远对不上）、
    // 用 OnTakeItem.getNewTopItem()（那是输入槽的残余，不是被取走的产出物）。

    private ModDataComponents() {
    }

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }

    /**
     * 钓竿装配：竿身 + 鱼线 + 渔轮 + 铅锤 + 拟饵 + 鱼饵。
     *
     * <p><b>存真实 {@link ItemStack} 而不是物品 id 字符串。</b>
     * 早先只存 id（注释原话是"部件本身不需要保存耐久"），但玩家要求
     * "钓鱼时能看到鱼竿各个配件耐久"，而 {@code partProps(durability, ...)}
     * 早就给鱼线（30~50）和拟饵（14~40）定了耐久上限 —— 只存 id 的话这份耐久
     * 根本无处安放。改成 ItemStack 后，配件耐久随竿子一起持久化、一起显示。
     *
     * <p>为了不改动散落各处的调用方，{@link #rod()} 等访问器**仍然返回物品 id 名**；
     * 需要实际堆栈时用 {@link #rodStack()} 等。旧存档里的 {@code "anglertackle:hemp_line"}
     * 字符串会被 {@link #PART_CODEC} 读成对应的新物品堆栈，老竿子不会掉配件。
     *
     * <p><b>哪些槽位能改由竿类型决定</b>（见 {@code RodModManager.slotApplies}）：
     * <pre>
     *   台钓竿：只有【鱼饵】可改（线、轮、钩都是竿身自带）
     *   路亚竿：鱼线 / 渔轮 / 铅锤 / 拟饵 可改
     * </pre>
     *
     * <p>⚠ {@code floatPart} 字段已删除（浮漂整个配件被移除，见 ModItems 的注释）。
     * 旧存档里那个 {@code "float"} 键会被 Codec 忽略——配件槽位退回空，
     * 竿子本身不受影响。
     */
    public record RodParts(
            ItemStack rodStack, ItemStack lineStack, ItemStack reelStack,
            ItemStack hookStack, ItemStack lureStack, ItemStack baitStack
    ) {
        public static final RodParts EMPTY = new RodParts(
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY,
                ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY);

        /** 物品 id 名（{@code "anglertackle:hemp_line"}），空槽返回 {@code ""}。 */
        public static String idOf(ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                return "";
            }
            return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        }

        public String rod() { return idOf(rodStack); }
        public String line() { return idOf(lineStack); }
        public String reel() { return idOf(reelStack); }
        public String hook() { return idOf(hookStack); }
        public String lure() { return idOf(lureStack); }
        public String bait() { return idOf(baitStack); }

        /** 六个槽位按固定顺序，方便遍历（显示耐久、统一扣耐久都走它）。 */
        public java.util.List<ItemStack> all() {
            return java.util.List.of(rodStack, lineStack, reelStack, hookStack, lureStack, baitStack);
        }

        /** 槽位名（与 {@link #all()} 同序），用于本地化 key。 */
        // 展示用名（语言键后缀）。⚠ 下面的 codec 字段仍叫 "hook" —— 那是老存档的键，
        // 改名会让所有已装配的竿读不出铅锤；展示名与存档名在这里是**故意分开**的。
        public static final String[] SLOT_NAMES = {"rod", "line", "reel", "sinker", "lure", "bait"};

        /** 是否有任何槽位装了东西。 */
        public boolean isEmpty() {
            return rodStack.isEmpty() && lineStack.isEmpty() && reelStack.isEmpty()
                    && hookStack.isEmpty() && lureStack.isEmpty() && baitStack.isEmpty();
        }

        public RodParts withRod(ItemStack s) { return new RodParts(s, lineStack, reelStack, hookStack, lureStack, baitStack); }
        public RodParts withLine(ItemStack s) { return new RodParts(rodStack, s, reelStack, hookStack, lureStack, baitStack); }
        public RodParts withReel(ItemStack s) { return new RodParts(rodStack, lineStack, s, hookStack, lureStack, baitStack); }
        public RodParts withHook(ItemStack s) { return new RodParts(rodStack, lineStack, reelStack, s, lureStack, baitStack); }
        public RodParts withLure(ItemStack s) { return new RodParts(rodStack, lineStack, reelStack, hookStack, s, baitStack); }
        public RodParts withBait(ItemStack s) { return new RodParts(rodStack, lineStack, reelStack, hookStack, lureStack, s); }

        /**
         * 单个槽位的编解码。
         *
         * <p>要同时吃两种输入：
         * <ul>
         *   <li><b>老存档</b>：一个物品 id 字符串，例如 {@code "anglertackle:hemp_line"}</li>
         *   <li><b>新存档</b>：完整物品堆栈 {@code {id:"...", count:1, components:{...}}}</li>
         * </ul>
         * 先试物品堆栈形态，失败再当字符串按 id 还原。编码统一写堆栈形态
         * （老格式只进不出，新存档不需要兼容它）。
         *
         * <p>用 {@code OPTIONAL_CODEC} 而不是 {@code CODEC}：后者是
         * {@code RegistryFriendlyCodec}，数据组件的持久化路径拿不到 registry ops，
         * 会在读档时抛 "Can't access registry"。原版容器组件也是用的 OPTIONAL 版本。
         */
        public static final Codec<ItemStack> PART_CODEC =
                Codec.either(ItemStack.OPTIONAL_CODEC, Codec.STRING)
                        .xmap(
                                either -> either.map(s -> s, RodParts::stackFromId),
                                stack -> stack.isEmpty()
                                        ? com.mojang.datafixers.util.Either.right("")
                                        : com.mojang.datafixers.util.Either.left(stack));

        public static final Codec<RodParts> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                PART_CODEC.optionalFieldOf("rod", ItemStack.EMPTY).forGetter(RodParts::rodStack),
                PART_CODEC.optionalFieldOf("line", ItemStack.EMPTY).forGetter(RodParts::lineStack),
                PART_CODEC.optionalFieldOf("reel", ItemStack.EMPTY).forGetter(RodParts::reelStack),
                PART_CODEC.optionalFieldOf("hook", ItemStack.EMPTY).forGetter(RodParts::hookStack),
                PART_CODEC.optionalFieldOf("lure", ItemStack.EMPTY).forGetter(RodParts::lureStack),
                PART_CODEC.optionalFieldOf("bait", ItemStack.EMPTY).forGetter(RodParts::baitStack)
        ).apply(inst, RodParts::new));

        /** 按物品 id 还原堆栈；认不出就返回空（老存档里删掉的配件不会把竿子读崩）。 */
        public static ItemStack stackFromId(String id) {
            if (id == null || id.isEmpty()) {
                return ItemStack.EMPTY;
            }
            net.minecraft.resources.ResourceLocation rl =
                    net.minecraft.resources.ResourceLocation.tryParse(id);
            if (rl == null) {
                return ItemStack.EMPTY;
            }
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            return item == null ? ItemStack.EMPTY : new ItemStack(item);
        }

        /**
         * 网络同步。**手写编码，不传整个物品堆栈**，只传"有没有装 + 物品 id + 已损耗耐久"。
         *
         * <p>为什么不用 {@code ItemStack.OPTIONAL_STREAM_CODEC}：它的类型是
         * {@code StreamCodec<RegistryFriendlyByteBuf, ItemStack>}，而数据组件的
         * {@code networkSynchronized} 和会话同步包都只有普通的 {@code FriendlyByteBuf}，
         * 类型对不上。手写还有个好处：配件没有别的组件要同步，传 id+damage 比整套堆栈省得多。
         *
         * <p>用 {@code writeResourceLocation} / {@code writeVarInt}，空槽只占 1 字节。
         */
        public static void writePart(FriendlyByteBuf buf, ItemStack stack) {
            if (stack == null || stack.isEmpty()) {
                buf.writeBoolean(false);
                return;
            }
            buf.writeBoolean(true);
            buf.writeResourceLocation(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
            buf.writeVarInt(stack.getDamageValue());
        }

        public static ItemStack readPart(FriendlyByteBuf buf) {
            if (!buf.readBoolean()) {
                return ItemStack.EMPTY;
            }
            net.minecraft.resources.ResourceLocation rl = buf.readResourceLocation();
            int dmg = buf.readVarInt();
            net.minecraft.world.item.Item item =
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
            if (item == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(item);
            stack.setDamageValue(dmg);
            return stack;
        }

        public static final StreamCodec<FriendlyByteBuf, RodParts> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    writePart(buf, p.rodStack);
                    writePart(buf, p.lineStack);
                    writePart(buf, p.reelStack);
                    writePart(buf, p.hookStack);
                    writePart(buf, p.lureStack);
                    writePart(buf, p.baitStack);
                },
                buf -> new RodParts(
                        readPart(buf), readPart(buf), readPart(buf),
                        readPart(buf), readPart(buf), readPart(buf))
        );
    }
}
