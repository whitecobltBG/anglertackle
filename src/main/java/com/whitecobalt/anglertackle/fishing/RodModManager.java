package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.item.BaitItem;
import com.whitecobalt.anglertackle.item.PartStats;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.item.TacklePartItem;
import com.whitecobalt.anglertackle.network.ModNetwork;
import com.whitecobalt.anglertackle.network.RodModActionPayload;
import com.whitecobalt.anglertackle.network.RodModPayload;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 渔具改装的服务端逻辑。
 *
 * <p><b>改装规则（按用户要求收紧）</b>：
 * <pre>
 *   台钓竿：只能换【鱼饵】
 *           —— 浮漂、鱼线、渔轮、鱼钩都是这根竿自带的，不允许拆改
 *           —— 鱼饵也限制为"台钓能用的饵"（面包屑 / 蚯蚓 / 玉米粒 / 活虾 / 发光饵 / 秘制香饵）
 *
 *   路亚竿：可以换【鱼线 / 渔轮 / 鱼钩 / 拟饵】
 *           —— 台钓的浮漂不让装（玩法不同）
 *
 *   竿身：两种竿都只读（换竿身等于换一根竿）
 * </pre>
 *
 * <p>服务端会重新校验规则，客户端改包也装不上不该装的配件。
 */
public final class RodModManager {

    /**
     * 槽位常量。
     *
     * <p>⚠ {@code SLOT_FLOAT = 5} 已废弃（浮漂槽被移除），但**编号故意留空不重用**：
     * 这些序号会写进网络包和界面状态，重排编号会让老客户端/老存档错位。
     * 留一个空洞比错位安全。
     */
    public static final int SLOT_ROD = 0;
    public static final int SLOT_LINE = 1;
    public static final int SLOT_REEL = 2;
    public static final int SLOT_SINKER = 3;
    public static final int SLOT_LURE = 4;
    /** @deprecated 浮漂槽已移除，编号保留以免错位。 */
    @Deprecated
    public static final int SLOT_FLOAT = 5;
    public static final int SLOT_BAIT = 6;

    /** 槽位序号 → 翻译键后缀。 */
    private static final String[] SLOT_KEYS =
            {"rod", "line", "reel", "sinker", "lure", "float", "bait"};

    private RodModManager() {
    }

    // =================================================================== 规则
    /** 一根竿在界面上要显示哪些槽位（顺序即显示顺序）。 */
    public static int[] slotsFor(RodItem.RodType type) {
        return type == RodItem.RodType.FLOAT
                ? new int[]{SLOT_BAIT}                              // 台钓：只有鱼饵
                : new int[]{SLOT_LINE, SLOT_REEL, SLOT_SINKER, SLOT_LURE};   // 路亚：线/轮/铅锤/拟饵
    }

    /** 槽位是否对这根竿生效。 */
    public static boolean slotApplies(RodItem.RodType type, int slot) {
        for (int s : slotsFor(type)) {
            if (s == slot) {
                return true;
            }
        }
        return false;
    }

    /** 槽位要求的配件种类；-1 表示"任意台钓可用鱼饵"，-2 表示"任意拟饵或鱼饵"。 */
    private static int requiredSlotKind(int slot) {
        return switch (slot) {
            case SLOT_LINE -> TacklePartItem.Slot.LINE.ordinal();
            case SLOT_REEL -> TacklePartItem.Slot.REEL.ordinal();
            case SLOT_SINKER -> TacklePartItem.Slot.SINKER.ordinal();
            // SLOT_FLOAT 没有对应配件种类了（浮漂已删除），落到 default
            default -> -1;
        };
    }

    // =================================================================== 打开界面
    /** 组装并发送界面数据。 */
    public static void open(ServerPlayer player, ItemStack rodStack, int highlightSlot) {
        if (!(rodStack.getItem() instanceof RodItem rodItem)) {
            AnglersTackle.LOGGER.warn("[垂纶] 打开改装界面失败：手上物品不是本模组钓竿（{}）",
                    rodStack.getItem());
            return;
        }
        ModDataComponents.RodParts parts = currentParts(rodStack);
        RodStats stats = RodStats.of(rodStack);
        int[] slots = slotsFor(rodItem.rodType());

        List<String> slotData = new ArrayList<>(slots.length);
        for (int slot : slots) {
            String installedId = getSlot(parts, slot);
            String currentName = installedId.isEmpty()
                    ? Component.translatable("gui.anglertackle.empty").getString()
                    : displayNameOf(installedId);
            List<String> candidates = listCandidates(player, rodItem.rodType(), slot);
            // slotData 格式: 显示名|该槽位说明|候选项,候选项
            slotData.add(currentName + "|" + Component.translatable(
                    "gui.anglertackle.slot." + SLOT_KEYS[slot]).getString()
                    + "|" + String.join(",", candidates));
        }

        List<String> statKeys = List.of(
                "gui.anglertackle.stat.tension_ceiling",
                "gui.anglertackle.stat.tension_recover",
                "gui.anglertackle.stat.tension_gen",
                "gui.anglertackle.stat.stamina_drain",
                "gui.anglertackle.stat.pull_idle",
                "gui.anglertackle.stat.pull_struggle",
                "gui.anglertackle.stat.damping",
                "gui.anglertackle.stat.line_length",
                "gui.anglertackle.stat.hook_window",
                "gui.anglertackle.stat.max_weight",
                "gui.anglertackle.stat.recover_time");
        List<String> statValues = List.of(
                fmt(stats.tensionCeiling(), 1),
                "x" + fmt(stats.tensionRecover(), 2),
                "x" + fmt(stats.tensionGen(), 2),
                "x" + fmt(stats.staminaDrain(), 2),
                fmt(stats.pullIdle(), 3),
                fmt(stats.pullStruggle(), 3),
                "x" + fmt(stats.damping(), 2),
                fmt(stats.lineLength(), 0),
                "+" + fmt(stats.hookWindowBonus(), 2),
                fmt(stats.maxWeightKg(), 0),
                stats.describeTensionWindow());

        ModNetwork.sendTo(player, new RodModPayload(
                rodStack.getHoverName().getString(), slotData, statKeys, statValues, (byte) 0));
        AnglersTackle.LOGGER.info("[垂纶] 改装界面已发送：竿={}（{}），可改槽位 {} 个",
                rodStack.getHoverName().getString(),
                rodItem.rodType() == RodItem.RodType.FLOAT ? "台钓" : "路亚",
                slots.length);
    }

    // =================================================================== 动作
    public static void handle(ServerPlayer player, RodModActionPayload payload) {
        AnglersTackle.LOGGER.info("[垂纶] 改装请求：action={} slot={} item={}",
                payload.action(), payload.slot(), payload.itemId());

        ItemStack rodStack = heldRodOrAny(player);
        if (rodStack == null) {
            AnglersTackle.LOGGER.warn("[垂纶] 找不到钓竿，忽略（玩家 {}）", player.getName().getString());
            player.displayClientMessage(
                    Component.translatable("message.anglertackle.no_rod_in_hand"), true);
            return;
        }

        int slot = payload.slot();
        if (!(rodStack.getItem() instanceof RodItem rodItem)) {
            return;
        }
        if (slot < 0 || slot > SLOT_BAIT) {
            return;
        }
        if (slot == SLOT_ROD) {
            player.displayClientMessage(
                    Component.translatable("message.anglertackle.rod_slot_readonly"), true);
            open(player, rodStack, slot);
            return;
        }
        // ★ 服务端强制校验：台钓竿只能改鱼饵，改包也绕不过
        if (!slotApplies(rodItem.rodType(), slot)) {
            player.displayClientMessage(Component.translatable(
                    "message.anglertackle.slot_not_applicable",
                    Component.translatable("gui.anglertackle.slot." + SLOT_KEYS[slot]),
                    Component.translatable("gui.anglertackle.rod_type."
                            + (rodItem.rodType() == RodItem.RodType.FLOAT ? "float" : "lure"))), true);
            open(player, rodStack, slot);
            return;
        }

        switch (payload.action()) {
            case RodModActionPayload.ACTION_EQUIP ->
                    equip(player, rodStack, rodItem.rodType(), slot, payload.itemId());
            case RodModActionPayload.ACTION_UNEQUIP -> unequip(player, rodStack, slot);
            default -> { /* 只刷新 */ }
        }

        open(player, rodStack, slot);
    }

    private static void equip(ServerPlayer player, ItemStack rodStack, RodItem.RodType type,
                              int slot, String wantedId) {
        ItemStack candidate = findInInventory(player, type, slot, wantedId);
        if (candidate.isEmpty()) {
            AnglersTackle.LOGGER.warn("[垂纶] 装备失败：背包里找不到 {}（槽位 {}）", wantedId, slot);
            player.displayClientMessage(
                    Component.translatable("message.anglertackle.no_such_part"), true);
            return;
        }

        // ⚠️ 顺序很重要：物品名必须在 shrink 之前取。
        // 之前是先 shrink(1) 再读 candidate.getItem()，数量归零后读到的是空气，
        // 提示消息会显示成"空气"。
        // ★ 存进竿里的必须是**背包里那一件的副本**：按 id 重造的话耐久是满的，
        //   玩家把磨损的配件拆下再装上就等于白送一次翻新（用户报的"装回去就满了"）。
        ItemStack newPart = candidate.copyWithCount(1);
        String newId = BuiltInRegistries.ITEM.getKey(candidate.getItem()).toString();
        Component newName = candidate.getHoverName();
        ModDataComponents.RodParts parts = currentParts(rodStack);

        // 旧部件退回背包（找不到位置就掉在脚下，不丢失）——退的是**竿上存着的那一份**，
        // 它带着自己用过的耐久；按 id 重造会把磨损洗掉。
        ItemStack oldPart = slotStackOf(parts, slot).copy();
        if (!oldPart.isEmpty()) {
            if (!player.getInventory().add(oldPart)) {
                player.drop(oldPart, false);
            }
        }

        candidate.shrink(1);
        setSlot(rodStack, parts, slot, newPart);

        // 双保险：显式标记背包已变更，并立刻把竿同步回客户端。
        // 数据组件是写在服务端物品栈上的，客户端那份是旧的需要刷新，
        // 否则玩家看到"背包里的改件没消失、竿也没变"。
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();

        String after = getSlot(currentParts(rodStack), slot);
        AnglersTackle.LOGGER.info("[垂纶] 装备成功：槽位 {} ← {}（竿上现在是 {}），背包剩余 {}",
                slot, newId, after, candidate.getCount());

        player.displayClientMessage(Component.translatable(
                "message.anglertackle.part_equipped",
                Component.translatable("gui.anglertackle.slot." + SLOT_KEYS[slot]),
                newName), true);
    }

    private static void unequip(ServerPlayer player, ItemStack rodStack, int slot) {
        ModDataComponents.RodParts parts = currentParts(rodStack);
        String oldId = getSlot(parts, slot);
        if (oldId == null || oldId.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.anglertackle.slot_empty"), true);
            return;
        }
        // ★ 退回竿上存着的那一份（带它自己用过的耐久），不要按 id 重造
        ItemStack oldStack = slotStackOf(parts, slot).copy();
        if (oldStack.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.anglertackle.slot_empty"), true);
            return;
        }
        if (!player.getInventory().add(oldStack)) {
            player.drop(oldStack, false);
        }
        setSlot(rodStack, parts, slot, ItemStack.EMPTY);

        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.sendAllDataToRemote();

        AnglersTackle.LOGGER.info("[垂纶] 卸下成功：槽位 {} ← {}（耐久 {}/{}，按存着的那一份退回）",
                slot, oldId,
                oldStack.getMaxDamage() - oldStack.getDamageValue(), oldStack.getMaxDamage());
        player.displayClientMessage(Component.translatable(
                "message.anglertackle.part_removed", oldStack.getHoverName()), true);
    }

    // =================================================================== 工具
    /**
     * 找一根本模组钓竿：主手 → 副手 → 背包。
     *
     * <p>不严格要求"握在手上"：玩家点开界面后可能滚轮换了手持物，
     * 那不该让界面失效。
     */
    public static ItemStack findRod(ServerPlayer player) {
        // 1) 主手（改装操作的唯一正确目标）
        if (player.getMainHandItem().getItem() instanceof RodItem) {
            return player.getMainHandItem();
        }
        // 2) 副手
        if (player.getOffhandItem().getItem() instanceof RodItem) {
            return player.getOffhandItem();
        }
        // 3) 快捷栏 0–8
        for (int i = 0; i < 9; i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof RodItem) {
                return s;
            }
        }
        // 4) 背包其余格子
        for (int i = 9; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (!s.isEmpty() && s.getItem() instanceof RodItem) {
                return s;
            }
        }
        return null;
    }

    /**
     * 拿来做改装的那根竿。
     *
     * <p><b>必须优先手持</b>：如果背包里有多根竿，随意挑一根会导致
     * 界面显示的是 A 竿、实际改的却是 B 竿——表现出来就是
     * "背包里明明有配件，却怎么都装不上"。
     */
    private static ItemStack heldRodOrAny(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof RodItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof RodItem) {
            return player.getOffhandItem();
        }
        return findRod(player);
    }

    private static ModDataComponents.RodParts currentParts(ItemStack rodStack) {
        ModDataComponents.RodParts parts = rodStack.get(ModDataComponents.ROD_PARTS.get());
        return parts == null ? ModDataComponents.RodParts.EMPTY : parts;
    }

    private static String getSlot(ModDataComponents.RodParts p, int slot) {
        return switch (slot) {
            case SLOT_LINE -> p.line();
            case SLOT_REEL -> p.reel();
            case SLOT_SINKER -> p.hook();
            case SLOT_LURE -> p.lure();
            // SLOT_FLOAT 已废（浮漂槽移除）→ 落到 default 返回空
            case SLOT_BAIT -> p.bait();
            default -> "";
        };
    }

    private static void setSlot(ItemStack rodStack, ModDataComponents.RodParts p, int slot,
                                ItemStack part) {
        // ★ 收**物品栈**而不是物品 id：按 id 造出来的堆栈耐久是满的，于是
        //   "拆下来那件磨损的配件再装回去"就被洗成全新 —— 用户 2026-10-02 报的
        //   "拆下来的配件确实有耐久，但装回去就满了"。
        //   存进竿里的一律是**传入那一件的副本**（数量固定 1）：
        //   它自己的耐久、自定义名、附魔等组件一起带走。
        ItemStack store = part == null || part.isEmpty() ? ItemStack.EMPTY : part.copyWithCount(1);
        ModDataComponents.RodParts next = switch (slot) {
            case SLOT_LINE -> p.withLine(store);
            case SLOT_REEL -> p.withReel(store);
            case SLOT_SINKER -> p.withHook(store);
            case SLOT_LURE -> p.withLure(store);
            case SLOT_BAIT -> p.withBait(store);
            default -> p;
        };
        rodStack.set(ModDataComponents.ROD_PARTS.get(), next);
    }

    /** 某个槽位上**存着**的那一件（空槽返回空栈）。与 {@link #getSlot} 同序。 */
    private static ItemStack slotStackOf(ModDataComponents.RodParts p, int slot) {
        return switch (slot) {
            case SLOT_LINE -> p.lineStack();
            case SLOT_REEL -> p.reelStack();
            case SLOT_SINKER -> p.hookStack();
            case SLOT_LURE -> p.lureStack();
            case SLOT_BAIT -> p.baitStack();
            default -> ItemStack.EMPTY;
        };
    }

    /**
     * 在背包里找符合该槽位要求的物品。
     *
     * <p>鱼饵槽（{@link #SLOT_BAIT}）接受 {@link BaitItem}（消耗品）与拟饵；
     * 其它槽位按 {@link TacklePartItem.Slot} 严格匹配。
     */
    private static ItemStack findInInventory(ServerPlayer player, RodItem.RodType type,
                                            int slot, String wantedId) {
        Item wantItem = wantedId == null || wantedId.isEmpty() ? null
                : BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(wantedId));

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (wantItem != null && stack.getItem() != wantItem) {
                continue;
            }
            if (!acceptsForSlot(stack, slot)) {
                continue;
            }
            return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * 该物品能不能装进这个槽位。
     *
     * <p><b>鱼饵槽和拟饵槽曾经接受的东西完全一样</b>（两处判断逐字相同），
     * 于是"路亚竿能装台钓饵"、"台钓竿能装拟饵"——用户报的就是前者。
     * 当时的注释还写着"路亚也能挂饵"，说明那是**有意**的，
     * 但它把台钓/路亚两套体系的区别抹掉了：玩家没有理由为了挂拟饵去换路亚竿。
     *
     * <p>现在各自只收自己的：鱼饵槽收 {@link BaitItem}，拟饵槽收 {@code Slot.LURE} 的配件。
     * ⚠ 副作用：**台钓竿也不能装拟饵了**。如果想让台钓保留"也能挂假饵"的灵活性，
     * 说一声，把拟饵加回鱼饵槽即可（反过来不行——那正是用户报的问题）。
     */
    private static boolean acceptsForSlot(ItemStack stack, int slot) {
        if (slot == SLOT_BAIT) {
            // 台钓的鱼饵槽：**只**收消耗型鱼饵
            return stack.getItem() instanceof BaitItem;
        }
        if (slot == SLOT_LURE) {
            // 路亚的拟饵槽：**只**收拟饵（不再接受台钓饵 —— 用户报的 bug）
            return stack.getItem() instanceof TacklePartItem p
                    && p.slot() == TacklePartItem.Slot.LURE;
        }
        int need = requiredSlotKind(slot);
        return stack.getItem() instanceof TacklePartItem p && p.slot().ordinal() == need;
    }

    // =================================================================== 铁砧装配
    /**
     * 把一个配件装到竿上（供铁砧流程使用，直接改传入的竿物品栈）。
     *
     * <p>和 GUI 版 {@link #equip} 的区别：这里不查背包、不扣物品——
     * 铁砧里的右槽本身就是被消耗的那一个，扣数量由铁砧逻辑负责。
     *
     * <p>★ 传的是**右槽那一件真栈**（{@code setSlot} 会取它的副本）：
     * 铁砧上装上去的配件同样要带着自己用过的耐久，不能按 id 重造。
     *
     * @return 装上了返回 true；这个配件不适合这根竿则返回 false
     */
    public static boolean applyPart(ItemStack rodStack, RodItem.RodType rodType, ItemStack part) {
        int slot = partSlotOf(part, rodType);
        if (slot < 0) {
            return false;
        }
        setSlot(rodStack, currentParts(rodStack), slot, part);
        return true;
    }

    /** 这个配件该装进哪个槽位；不适合这根竿则返回 -1。 */
    public static int partSlotOf(ItemStack part, RodItem.RodType rodType) {
        for (int slot : slotsFor(rodType)) {
            if (acceptsForSlot(part, slot)) {
                return slot;
            }
        }
        return -1;
    }

    /**
     * 这个配件会装进哪个槽位，返回槽位的语言键（用于提示文案）；不适合则返回 null。
     * 供铁砧流程给玩家反馈用。
     */
    public static String slotNameOf(ItemStack part, RodItem.RodType rodType) {
        int slot = partSlotOf(part, rodType);
        if (slot < 0 || slot >= SLOT_KEYS.length) {
            return null;
        }
        return SLOT_KEYS[slot];
    }

    /** 取出某个槽位上的配件 id（空则返回空串）。 */
    public static String partIdAt(ItemStack rodStack, int slot) {
        return getSlot(currentParts(rodStack), slot);
    }

    /**
     * 取出某个槽位上**存着的那一件**配件（空槽返回空栈）。
     *
     * <p>和 {@link #partIdAt} 的区别：这个带耐久。换件/退货一类"要把旧件还给玩家"的
     * 场合必须用它 —— 按 id 重造出来的耐久是满的，等于把磨损洗掉
     * （用户 2026-10-02："拆下来的配件确实有耐久，但装回去就满了"）。
     *
     * <p>返回的是竿上那一份本身，调用方要还货请先 {@code copy()}。
     */
    public static ItemStack partAt(ItemStack rodStack, int slot) {
        return slotStackOf(currentParts(rodStack), slot);
    }

    /** 竿上有没有装东西（有任何一件就返回 true）。 */
    public static boolean hasAnyPart(ItemStack rodStack) {
        ModDataComponents.RodParts p = currentParts(rodStack);
        return !p.line().isEmpty() || !p.reel().isEmpty() || !p.hook().isEmpty()
                || !p.lure().isEmpty() || !p.bait().isEmpty();
    }

    /**
     * 把所有配件都拆下来，返回它们的物品栈列表（用于砂轮/拆卸）。
     * 会同时清空竿上的装配记录。
     */
    public static List<ItemStack> stripAllParts(ItemStack rodStack) {
        ModDataComponents.RodParts p = currentParts(rodStack);
        List<ItemStack> out = new ArrayList<>();
        // ★ 必须返回**存下来的那几份 ItemStack**，不能按 id 重造：
        //   重造出来的耐久是满的 —— 拆下来的配件一装回去就"翻新"了。
        //   用户 2026-10-02："配件保留使用过的耐久数据，在下一次装上时不会出现耐久回满。"
        for (ItemStack part : new ItemStack[]{p.lineStack(), p.reelStack(),
                p.hookStack(), p.lureStack(), p.baitStack()}) {
            if (part == null || part.isEmpty()) {
                continue;
            }
            out.add(part.copy());   // copy：不动竿上那份记录，避免两份数据互相影响
        }
        rodStack.set(ModDataComponents.ROD_PARTS.get(), ModDataComponents.RodParts.EMPTY);
        return out;
    }

    private static List<String> listCandidates(ServerPlayer player, RodItem.RodType type, int slot) {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !acceptsForSlot(stack, slot)) {
                continue;
            }
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String displayNameOf(String itemId) {
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        if (rl == null) {
            return itemId;
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl)).getHoverName().getString();
    }

    private static String fmt(double v, int decimals) {
        return String.format("%." + decimals + "f", v);
    }

    /** 部件属性摘要（供界面显示用）。 */
    public static String describePart(PartStats stats) {
        return String.format("上限x%.2f 生成x%.2f 回复x%.2f",
                stats.tensionCeiling(), stats.tensionGen(), stats.tensionRecover());
    }
}
