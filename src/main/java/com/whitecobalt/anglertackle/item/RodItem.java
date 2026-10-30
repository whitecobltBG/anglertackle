package com.whitecobalt.anglertackle.item;

import com.whitecobalt.anglertackle.fishing.FishingManager;
import com.whitecobalt.anglertackle.fishing.Tuning;
import com.whitecobalt.anglertackle.fishing.RodStats;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import com.whitecobalt.anglertackle.fishing.FishingSession;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * 钓竿：台钓竿（浮漂）与路亚竿（拟饵）共用同一个物品类，靠 {@link RodType} 区分。
 *
 * <p>状态机在 {@link FishingSession} 里，本类只负责把玩家的输入翻译成会话动作：
 * <ul>
 *   <li>空闲时按住右键 → 开始蓄力；松手 → 抛投（{@code power} 由蓄力时间决定）</li>
 *   <li>会话中按住右键 → 收线；松开 → 停止收线（放线/僵持）</li>
 * </ul>
 *
 * <p>设计依据：docs/02-钓鱼小游戏机制.md §2（蓄力抛投）、§4.1（玩家输入）。
 */
public class RodItem extends Item implements HasQuality {

    /** 竿类型：与材质正交（每种材质都有两种竿）。 */
    public enum RodType {
        /** 台钓竿：看浮漂。吃窝料加成，咬钩窗口 +0.05s，射程 ×0.85。 */
        FLOAT("float_rod", 0.85),
        /** 路亚竿：拟饵/泳层/张力对抗。射程 ×1.00，可钓掠食鱼与红品质。 */
        LURE("lure_rod", 1.00);

        private final String id;
        private final double rangeFactor;

        RodType(String id, double rangeFactor) {
            this.id = id;
            this.rangeFactor = rangeFactor;
        }

        public String id() {
            return id;
        }

        public double rangeFactor() {
            return rangeFactor;
        }
    }

    private final RodType type;
    private final RodSpec spec;

    public RodItem(RodType type, RodSpec spec, Properties properties) {
        super(properties);
        this.type = type;
        this.spec = spec;
    }

    public RodType rodType() {
        return type;
    }

    /**
     * 附魔亲和度（1.21.1 靠重写这个方法 —— 既不是属性也不是组件）。
     *
     * <p>用户 2026-10-02："我可以在附魔书界面看到附魔，但路亚竿直接放上附魔台似乎并不会出现。"
     * 根因就是这里：{@code Item.getEnchantmentValue()} 默认返回 **0**，
     * 而附魔台的报价是按亲和度算的 —— 亲和度 0 时台子只给 1~2 级的报价，
     * 我们的附魔 min_cost 是 10~40，于是一个都刷不出来
     * （附魔书走的是另一条分支：书会被当成"任意附魔"，所以书能看到）。
     *
     * <p>数值按材质对齐原版同类材质：木 15 / 铁 14 / 金 25 / 钻 10 / 下界合金 15
     * （原版鱼竿是 1，那是给"没有材质的普通竿"的）。亲和度越高报价越高，
     * 抢救 / 拉扯（min_cost 30）才可能出现。
     */
    @Override
    public int getEnchantmentValue() {
        return switch (spec.material()) {
            case WOOD -> 15;
            case IRON -> 14;
            case GOLD -> 25;
            case DIAMOND -> 10;
            case NETHERITE -> 15;
        };
    }

    /**
     * 竿的品质：按材质档位。木 → 铁 → 金 → 钻石 → 下界合金，对应白 → 绿 → 蓝 → 紫 → 金。
     *
     * <p>这里**直接按材质判**，不再拿 {@code materialFactor} 的数值去分段比对。
     * 早先写的是「m&lt;0.55 → 红，&lt;0.78 → 紫…」，那套阈值是从默认的
     * 0.50/0.70/0.85/0.90/1.00 反推出来的；而 materialFactor 现在是**可调**的，
     * 玩家一改，竿名颜色就会跟着乱跳（金竿变绿竿那种）。
     * 品质应该由"这是什么材质"决定，跟损耗系数这种可调数值无关。
     */
    @Override
    public Quality quality(ItemStack stack) {
        return switch (spec.material()) {
            case WOOD -> Quality.WHITE;
            case IRON -> Quality.GREEN;
            case GOLD -> Quality.BLUE;
            case DIAMOND -> Quality.PURPLE;
            case NETHERITE -> Quality.GOLD;
        };
    }

    /** 竿名按品质着色——和鱼获、配件走同一条路径。 */
    @Override
    public Component getName(ItemStack stack) {
        return HasQuality.applyQuality(super.getName(stack), this, stack);
    }

    public RodSpec spec() {
        return spec;
    }

    // ------------------------------------------------------------------ 输入
    /**
     * 右键：进入"使用中"状态，松手时抛投。
     *
     * <p><b>这里永远返回 {@code consume}，任何情况下都不返回 fail。</b>
     * 这是照原版弓的做法，也是之前反复出问题的根因：
     * 弓的 {@code use()} 无条件返回 consume，所以无论什么状态，
     * 右键永远能让物品进入"使用中"，松手时原版一定会调 {@code releaseUsing}。
     * 之前这里在"已有会话"时返回 fail —— 一旦会话因为任何原因没清干净，
     * 右键就再也不会进入使用状态，表现为"按住右键毫无反应"。
     *
     * <p>具体判定全部放到 {@code releaseUsing} 和 tick 里做，
     * 输入层不做任何拒绝。
     */
    /**
     * 右键**冰洞** → 精准入洞（用户 2026-10-08 定案，docs/33 §8.3）。
     *
     * <p>为什么要有这一条：冰洞只有一格宽，靠抛物线瞄它基本靠运气（用户实测：
     * "即使准心对准冰洞方块也不会将浮漂投进去"）。洞是**已知落点**，直接放进去就行。
     *
     * <p>返回 {@code SUCCESS} 会**吃掉**这次右键，于是不会进入"蓄力"状态 ✓
     * （否则客户端那边还会照旧报一次 CastPayload，等于抛两竿）。
     * 非冰洞一律 {@code PASS}，把右键还给原来的蓄力/收线逻辑 ✓
     */
    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext ctx) {
        if (!(ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        if (!(ctx.getLevel().getBlockState(ctx.getClickedPos()).getBlock()
                instanceof com.whitecobalt.anglertackle.block.IceHoleBlock)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        FishingManager.get().castIntoIceHole(player, ctx.getClickedPos());
        return net.minecraft.world.InteractionResult.sidedSuccess(ctx.getLevel().isClientSide());
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // 显式启动，不依赖调用方对返回值的处理
        player.startUsingItem(hand);

        // 对抗中按住右键 = 收线（张力上升），松开就停。
        // 之前只在注释里写着"这一下按住 = 收线"，但代码根本没调用 setReeling，
        // 所以玩家按住右键完全没有反馈——这就是"不知道有没有用"的原因。
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            FishingManager.get().setReeling(serverPlayer, true);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 松手。**这里故意什么都不做**——抛投改由客户端上报的 {@code CastPayload} 触发。
     *
     * <p>原因是实测出来的：原版客户端松手时，服务端的 {@code releaseUsing} 不会可靠触发
     * （日志里只有客户端那一条，服务端一次都没有）。弓之所以正常是因为它有专门的服务端兜底，
     * 自定义物品没有。所以客户端在 tick 里自己检测松手的下降沿，发包给服务端。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeCharged) {
        // 松开右键 = 停止收线（在服务端生效）
        if (!level.isClientSide() && entity instanceof ServerPlayer serverPlayer) {
            FishingManager.get().setReeling(serverPlayer, false);
        }
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        // 与弓一致：给一个很大的上限，实际靠 releaseUsing 结束
        return 72000;
    }

    /** 拉起弓弦时会放慢移动——钓竿同理，蓄力时走得慢一点，手感更像"稳住"。 */
    @Override
    public net.minecraft.world.item.UseAnim getUseAnimation(ItemStack stack) {
        return net.minecraft.world.item.UseAnim.BOW;
    }

    // ------------------------------------------------------------------ 破损
    /**
     * 竿是不是彻底用坏了。
     *
     * <p>⚠️ 这里**不能**用 {@code damage >= maxDamage - 1}：
     * 那样"还剩 1 点耐久"的竿也会被判定成坏的，玩家会觉得"刚拿到的新竿就不能用"。
     * 现在只有真正扣满（{@code damage >= maxDamage}）才算坏，
     * 而且 {@code maxDamage <= 0} 视为"这根竿没有耐久概念"，永远不算坏。
     */
    public static boolean isBroken(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        return stack.getDamageValue() >= max;
    }

    /** 这根竿还剩多少耐久（调试用）。 */
    public static String durabilityInfo(ItemStack stack) {
        if (!(stack.getItem() instanceof RodItem)) {
            return "不是钓竿";
        }
        int max = stack.getMaxDamage();
        int cur = stack.getDamageValue();
        return "耐久 " + (max - cur) + "/" + max + "（damage=" + cur
                + ", isBroken=" + isBroken(stack) + "）";
    }

    // ------------------------------------------------------------------ 提示
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> lines, TooltipFlag flag) {
        // 品质行：和鱼获、配件格式一致
        lines.add(qualityLine(stack));
        lines.add(Component.translatable("tooltip.anglertackle.rod_type." + type.id())
                .withStyle(ChatFormatting.GRAY));
        // ★ 台钓竿的咬钩减时（用户定案 2026-10-02："台钓的咬钩时间单独下降 30%"）。
        //   它和**饵料那一档**是加算关系（饵的那一行在 BaitItem 上），
        //   数字一律从 BiteTiming（→ Config）现算 —— 手抄一份必然漂移。
        //   路亚竿不吃这一项，所以干脆不显示（显示 0% 只会让人以为坏了）。
        if (type == RodType.FLOAT) {
            double cut = com.whitecobalt.anglertackle.fishing.BiteTiming.rodCutPercent();
            if (cut > 0) {
                lines.add(Component.translatable("tooltip.anglertackle.rod_cut_time",
                                com.whitecobalt.anglertackle.fishing.BiteTiming.percentText(cut))
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        lines.add(Component.translatable("tooltip.anglertackle.max_weight",
                String.format("%.0f", spec.maxWeightKg())).withStyle(ChatFormatting.AQUA));
        // 档位上限：这根竿最高能出到几档品质的鱼。
        // 必须显式写出来——玩家反馈"几十杆没一条金以上"就是因为这个上限
        // 既没在 tooltip 上、又和承重脱钩，光看"最大体重"根本推不出来。
        //
        // ★ 2026-10-02 用户定案："取消台钓的档位上限机制"。
        //   所以这一行**只给路亚竿**：台钓竿写"无上限"反而像有个可以调的参数，
        //   而且玩家会拿它当"我这根竿钓不到红鱼"的依据 —— 那条规则已经不在了。
        if (type == RodType.LURE) {
            // ⚠ 档位名字要**带该档的颜色**（2026-10-03 用户："该显示颜色的文本却是白色"）：
            //   整行 AQUA 会让"金"和"白"长得一样，而这一行的全部意义就是那个字。
            //   传进去的子组件自带颜色，渲染时盖住外层的 AQUA。
            Quality q = Quality.of(spec.tier());
            lines.add(Component.translatable("tooltip.anglertackle.max_tier",
                            Component.translatable("tooltip.anglertackle.quality." + q.key())
                                    .withStyle(q.color()))
                    .withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("tooltip.anglertackle.no_tier_cap")
                    .withStyle(ChatFormatting.AQUA));
        }

        // ---- 改装结构：把各槽已装了什么列出来（用户要求"在信息栏显示改装结构"）----
        ModDataComponents.RodParts parts = stack.get(ModDataComponents.ROD_PARTS.get());
        lines.add(Component.translatable("tooltip.anglertackle.parts_header")
                .withStyle(ChatFormatting.YELLOW));

        // 台钓竿只有 鱼饵 一栏可改；路亚竿有 线/轮/钩/拟饵
        boolean isFloatRod = type == RodType.FLOAT;
        String[] names = isFloatRod
                ? new String[]{"bait"}
                : new String[]{"line", "reel", "hook", "lure"};
        String[] values = isFloatRod
                // ⚠️ 台钓读的是 bait 槽，不是 lure。
                // 之前这里写成 parts.lure()，而装进去的饵料存在 bait 字段里，
                // 结果就是"装完前后没有变化"——tooltip 永远显示（空）。
                ? new String[]{parts == null ? "" : parts.bait()}
                : new String[]{
                        parts == null ? "" : parts.line(),
                        parts == null ? "" : parts.reel(),
                        parts == null ? "" : parts.hook(),
                        parts == null ? "" : parts.lure()};

        // 每个槽位对应的**真实堆栈**：判"是否已用光"要看堆栈上的磨损，
        // 光看 id 是看不出来的（这正是加成一直生效的那个 bug 的根源）。
        ItemStack[] stacks = isFloatRod
                ? new ItemStack[]{parts == null ? ItemStack.EMPTY : parts.baitStack()}
                : new ItemStack[]{
                        parts == null ? ItemStack.EMPTY : parts.lineStack(),
                        parts == null ? ItemStack.EMPTY : parts.reelStack(),
                        parts == null ? ItemStack.EMPTY : parts.hookStack(),
                        parts == null ? ItemStack.EMPTY : parts.lureStack()};

        for (int i = 0; i < names.length; i++) {
            Component slotLabel = Component.translatable("gui.anglertackle.slot." + names[i]);
            if (values[i].isEmpty() || RodStats.isWornOut(stacks[i])) {
                // 空槽 —— 也包含"耐久已经用光的配件"。
                //
                // ⚠ 用光的配件现在会被**直接清成空槽**（见 FishingSession 扣耐久处），
                //   所以正常不会走到后半个条件；留着它是为了老存档里那些"已经扣满、
                //   但还没被清掉"的配件：显示成空槽，和它的实际作用（不提供加成）
                //   保持一致。曾经短暂地在这里显示过「（已耗尽）」—— 用户否掉了：
                //   那种状态不该存在，用光就该是空槽。
                lines.add(Component.literal("  ").append(slotLabel).append(" ")
                        .append(Component.translatable("gui.anglertackle.empty"))
                        .withStyle(ChatFormatting.DARK_GRAY));
            } else {
                lines.add(Component.literal("  ").append(slotLabel).append(" ")
                        .append(itemNameOf(values[i]))
                        .withStyle(ChatFormatting.GREEN));
            }
        }

        // ---- 合成出来的关键属性 ----
        RodStats stats = RodStats.of(stack);
        lines.add(Component.translatable("tooltip.anglertackle.stats_header")
                .withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal("  ")
                .append(Component.translatable("gui.anglertackle.stat.tension_ceiling"))
                .append(" " + String.format("%.1f", stats.tensionCeiling()))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("  ")
                .append(Component.translatable("gui.anglertackle.stat.tension_recover"))
                .append(" x" + String.format("%.2f", stats.tensionRecover()))
                .withStyle(ChatFormatting.AQUA));
        lines.add(Component.literal("  ")
                .append(Component.translatable("gui.anglertackle.stat.line_length"))
                .append(" " + String.format("%.0f 格", stats.lineLength()))
                .withStyle(ChatFormatting.AQUA));

        if (isBroken(stack)) {
            lines.add(Component.translatable("tooltip.anglertackle.broken_warning")
                    .withStyle(ChatFormatting.RED));
        }
    }

    /** 物品 id → 显示名。 */
    private static Component itemNameOf(String itemId) {
        try {
            var rl = net.minecraft.resources.ResourceLocation.parse(itemId);
            return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl))
                    .getHoverName();
        } catch (Exception e) {
            return Component.literal(itemId);
        }
    }

    /**
     * 一档竿身的静态属性（材质决定）。
     *
     * <p>注意：**竿自身的重量不影响任何东西**（已确认：竿等级不影响竿重量）；
     * 这里的 {@code maxWeightKg} 是"能钓多大的鱼"的**承重门槛**，与竿重无关。
     *
     * <p>三个"倍率"字段是**部件倍率**（1.0 = 基准），会按 40% 权重参与合成
     * （鱼线占 60%），所以最终生效值约为 `1 + (值-1)×0.4`：
     * <pre>
     *   木竿 1.00 → 生效 ×1.00
     *   铁竿 1.20 → 生效 ×1.08
     *   金竿 1.15 → 生效 ×1.06（但提竿窗口最长）
     *   钻竿 1.60 → 生效 ×1.24
     *   合金 1.70 → 生效 ×1.28
     * </pre>
     * 与 docs/11-配件系统详表.md §3 的属性表一致。
     *
     * @param maxDurability   最大耐久（木 2000 / 铁 3500 / 金 2500 / 钻 5000 / 合金 8000）
     * @param maxWeightKg     承重上限，超过此重量的鱼不会咬钩
     * @param hookWindowBonus 提竿窗口加成（秒）
     * @param tensionCeiling  张力上限部件倍率（与鱼线按 40%/60% 合成）
     * @param tensionRecover  张力回复部件倍率（与鱼线按 40%/60% 合成）
     * @param staminaDrain    体力削减部件倍率（与渔轮按 40%/60% 合成）
     * @param materialFactor  耐久损耗系数（越高级越耐用）
     * @param tier            **品质档位上限**（0 白 … 5 红）：这根竿最高能出到几档品质的鱼
     */
    /**
     * 竿身规格。
     *
     * <p><b>它不存数值，只存"是哪种材质"。</b>除耐久以外的每一项都在**取值那一刻**
     * 现查 {@link Tuning}。
     *
     * <p>为什么要这样：这五个常量是 {@code static final}，构造发生在 {@code <clinit>}，
     * 而 {@code ModItems} 的静态初始化会在**模组构造期**（配置还没加载）就碰到它们。
     * 早先的写法是「构造时把 7 个配置值读进来存成字段」，于是启动直接崩：
     * <pre>
     *   ExceptionInInitializerError: null
     *     Caused by: IllegalStateException: Cannot get config value before config is loaded.
     *       at Tuning.rodWoodMaxWeightKg(Tuning.java)
     *       at RodItem$RodSpec.&lt;clinit&gt;(RodItem.java)
     *       at ModItems.&lt;clinit&gt;(ModItems.java)
     *       at AnglersTackle.&lt;init&gt;
     * </pre>
     * 规则和配件那边一样：**注册期一律不许读配置**。配件改不动是因为数值被烘进了
     * 数据组件；竿这边没有这层限制，所以干脆改成实时读 —— 顺带让竿的承重、窗口加成、
     * 档位上限这些也变成 {@code /atl config reload} 就能生效，不用重启。
     *
     * <p>耐久是唯一的例外：它被 {@code Item.Properties#durability} 在注册那一刻烘死，
     * 改它必须重启，所以留在构造参数里当字面量。
     */
    public static final class RodSpec {

        /** 材质。竿的"身份"是材质，不是某一刻的配置快照。 */
        public enum Material {
            WOOD("木", 2000),
            IRON("铁", 3500),
            GOLD("金", 2500),
            DIAMOND("钻石", 5000),
            NETHERITE("下界合金", 8000);

            public final String cn;
            /** 注册期烘进物品的耐久上限，改不了（见类注释）。 */
            public final int maxDurability;

            Material(String cn, int maxDurability) {
                this.cn = cn;
                this.maxDurability = maxDurability;
            }
        }

        // ================================================================ 档位上限
        //
        // 用户反馈："几十杆下去没一条金以上"。根因就是这里——
        // 早先档位上限是**从承重推导**的（<10→1、<25→2、<50→3、≥50→5），
        // 而没有任何材质的承重落在 25~50 这个区间，于是：
        //   · 档位 4（金）**永远取不到**，3 直接跳到 5；
        //   · 金竿承重只有 12kg，落进 "<25 → 到蓝"，出不了紫鱼，**比铁竿还差**；
        //   · 金/红档鱼种（大西洋鲑 / 蓝鳍金枪鱼 / 幼鲨）最低档就是 4，
        //     非合金竿一律被整个过滤掉 —— 不下界合金就 100% 钓不到金红鱼。
        //
        // 现在改成**材质显式声明**，形成一条没有洞的五档阶梯：
        //   木 1 绿 → 铁 2 蓝 → 金 3 紫 → 钻 4 金 → 合金 5 红
        // 这也让每级材质**恰好对应一个新颜色**，玩家升级竿的收益看得见。
        //
        // 承重（maxWeightKg）继续作为**独立的一道闸门**（"这根竿扛不扛得住这种体型"），
        // 不再兼职决定品质档位——两件事本来就不该耦合。

        /** 木竿：白绿档。 */
        public static final RodSpec WOOD = new RodSpec(Material.WOOD);
        /** 铁竿：到蓝档。 */
        public static final RodSpec IRON = new RodSpec(Material.IRON);
        /** 金竿：到紫档。 */
        public static final RodSpec GOLD = new RodSpec(Material.GOLD);
        /** 钻竿：到金档。 */
        public static final RodSpec DIAMOND = new RodSpec(Material.DIAMOND);
        /** 合金竿：全档（含红）。 */
        public static final RodSpec NETHERITE = new RodSpec(Material.NETHERITE);

        private final Material material;

        private RodSpec(Material material) {
            this.material = material;
        }

        public Material material() {
            return material;
        }

        /** 注册期烘死的耐久上限。 */
        public int maxDurability() {
            return material.maxDurability;
        }

        // 下面每一项都是**现查**，所以 /atl config reload 之后立刻生效。

        public double maxWeightKg() {
            return switch (material) {
                case WOOD -> Tuning.rodWoodMaxWeightKg();
                case IRON -> Tuning.rodIronMaxWeightKg();
                case GOLD -> Tuning.rodGoldMaxWeightKg();
                case DIAMOND -> Tuning.rodDiamondMaxWeightKg();
                case NETHERITE -> Tuning.rodNetheriteMaxWeightKg();
            };
        }


        public double tensionCeiling() {
            return switch (material) {
                case WOOD -> Tuning.rodWoodTensionCeiling();
                case IRON -> Tuning.rodIronTensionCeiling();
                case GOLD -> Tuning.rodGoldTensionCeiling();
                case DIAMOND -> Tuning.rodDiamondTensionCeiling();
                case NETHERITE -> Tuning.rodNetheriteTensionCeiling();
            };
        }

        public double tensionRecover() {
            return switch (material) {
                case WOOD -> Tuning.rodWoodTensionRecover();
                case IRON -> Tuning.rodIronTensionRecover();
                case GOLD -> Tuning.rodGoldTensionRecover();
                case DIAMOND -> Tuning.rodDiamondTensionRecover();
                case NETHERITE -> Tuning.rodNetheriteTensionRecover();
            };
        }

        public double staminaDrain() {
            return switch (material) {
                case WOOD -> Tuning.rodWoodStaminaDrain();
                case IRON -> Tuning.rodIronStaminaDrain();
                case GOLD -> Tuning.rodGoldStaminaDrain();
                case DIAMOND -> Tuning.rodDiamondStaminaDrain();
                case NETHERITE -> Tuning.rodNetheriteStaminaDrain();
            };
        }

        public double materialFactor() {
            return switch (material) {
                case WOOD -> Tuning.rodWoodMaterialFactor();
                case IRON -> Tuning.rodIronMaterialFactor();
                case GOLD -> Tuning.rodGoldMaterialFactor();
                case DIAMOND -> Tuning.rodDiamondMaterialFactor();
                case NETHERITE -> Tuning.rodNetheriteMaterialFactor();
            };
        }

        public int tier() {
            return switch (material) {
                case WOOD -> (int) Tuning.rodWoodTier();
                case IRON -> (int) Tuning.rodIronTier();
                case GOLD -> (int) Tuning.rodGoldTier();
                case DIAMOND -> (int) Tuning.rodDiamondTier();
                case NETHERITE -> (int) Tuning.rodNetheriteTier();
            };
        }

        // 竿的身份就是材质，所以按材质比较。
        // （RodStats 是 record，它的 equals/hashCode 会用到这里，不能省。）
        @Override
        public boolean equals(Object o) {
            return o instanceof RodSpec other && other.material == material;
        }

        @Override
        public int hashCode() {
            return material.hashCode();
        }

        @Override
        public String toString() {
            return "RodSpec[" + material.cn + " 耐久=" + material.maxDurability +
                    " 承重=" + maxWeightKg() +
                    "kg 张力上限=" + tensionCeiling() + " 张力回复=" + tensionRecover() +
                    " 体力削减=" + staminaDrain() + " 损耗系数=" + materialFactor() +
                    " 档位上限=" + tier() + "]";
        }
    }
}
