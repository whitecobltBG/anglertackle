package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.effect.DishEffectHooks;
import com.whitecobalt.anglertackle.entity.BobberEntity;
import com.whitecobalt.anglertackle.item.PartStats;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.network.SessionSyncPayload;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * 一次钓鱼会话的状态机（服务端权威）。
 *
 * <p>实现 docs/02-钓鱼小游戏机制.md 的完整流程：
 * <pre>
 *   CHARGING → CASTING → WAITING → BITE_WARN(三段预告) → HOOK_WINDOW
 *            → FIGHT(张力对抗) → LANDING → RESULT
 * </pre>
 *
 * <p>关键设计：
 * <ul>
 *   <li>所有判定在服务端；客户端只发"意图"（收线/方向），只做渲染</li>
 *   <li>张力用固定步长推进（不依赖墙钟时间），低 TPS 下手感不变</li>
 *   <li><b>进入 HOOK_WINDOW 的瞬间扣耐久</b>——与后续成败无关（用户定义的核心规则）</li>
 * </ul>
 */
public final class FishingSession {

    /** 会话状态。 */
    public enum State {
        IDLE,
        /** 蓄力中（客户端显示蓄力条）。 */
        CHARGING,
        /** 浮漂在飞行。 */
        CASTING,
        /** 已落水，等鱼咬钩。 */
        WAITING,
        /** 三段预告（探饵 → 试探 → 吞钩）。 */
        BITE_WARN,
        /** 提竿窗口：浮漂已沉，玩家必须在此窗口内提竿。 */
        HOOK_WINDOW,
        /** 张力对抗。 */
        FIGHT,
        /** 上鱼结算动画。 */
        LANDING;

        public boolean isFighting() {
            return this == FIGHT;
        }

        /**
         * 这个阶段**该不该接管玩家的移动**（把 WASD 变成控杆方向）。
         *
         * <p>⚠⚠ 用户 2026-10-16 原话："**上鱼后应该一秒内无法移动，不然有人收不住方向键会掉进水里**"。
         * 所以判据是 {@code FIGHT} **加上**上鱼后的 {@link #LANDING}（25~30 tick ≈ 1.25~1.5 秒），
         * 不是只 FIGHT。
         *
         * <p>客户端有一份**同名同义**的判据（{@code ClientFishingState.movementLocked()}）；
         * 两边必须一起改：客户端清 WASD 冲量（本地立刻生效），服务端把移动速度属性压到 0
         * （权威，防住"客户端冲量没清干净"的滑动与水流推动）。
         * 只改一边就会出现"看起来能走 / 看起来被锁"的错位 —— 2026-10-16 的 1.6.6 只有客户端那半，
         * 用户实测仍然能动。
         */
        public boolean blocksMovement() {
            return this == FIGHT || this == LANDING;
        }

        public boolean isActive() {
            return this != IDLE;
        }
    }

    /** 鱼的挣扎方向。 */
    public enum StruggleDir {
        NONE(0), LEFT(-1), RIGHT(1), STRAIGHT(0);

        private final int axis;

        StruggleDir(int axis) {
            this.axis = axis;
        }

        /** -1 左 / +1 右 / 0 直冲。 */
        public int axis() {
            return axis;
        }
    }

    /**
     * 玩家控杆方向与鱼挣扎方向的关系（docs/14-鱼的手感档案.md §1.1）。
     *
     * <p>这不是"对/错"，而是三种取舍：正拉安全但慢、中立无倾向、反拉快但危险。
     */
    public enum LeverRelation {
        /** 正拉：顺着鱼的方向 → 体力削减少（×0.65）、张力涨得慢（×0.5）。 */
        WITH_FISH,
        /** 中立：垂直控杆 → 全部基准值，不做就不错。 */
        NEUTRAL,
        /** 反拉：逆着鱼的方向 → 体力削减多（×1.5）、张力涨得快（×1.4）。 */
        AGAINST_FISH
    }

    // ------------------------------------------------------------ 平衡常量
    /** 张力上限基准（100 = 红区刚好 100，超过即断线）。 */
/*__DECL_Tuning.tensionBaseCeiling()__*/

    /** 冲刺时的拉拽基数。 */

    /**
     * 白区 / 黄区占**张力上限**的比例（docs/02 §4.2）。
     *
     * <p><b>为什么改成比例而不是固定值：</b>
     * 原来是固定的 55 / 82 点。配上鱼线六档（张力上限 100 → 300 点）之后，
     * 一条红线的上限是 300 点，而白区仍然只有 55、黄区 82 ——
     * 等于 82~300 这一大片全是红区，**玩家一收线就超黄区，好线反而变成惩罚**。
     * 改成比例后 55/100 和 82/100 保持不变（基准竿基准线还是 55 / 82），
     * 而上限抬高时三区同步放大——好线的收益才是"安全区变宽"。
     */
    // ★ 调参值一律走 Tuning 的**运行时读取**，不再缓存成 static final。
    //
    //   原先这些是 `static final double X = Config.INSTANCE.y.get();` ——
    //   静态常量只在类加载时求值一次，所以改配置后必须重启整个游戏才生效。
    //   对手感调参来说那个循环太慢（改文件 → 重启 → 抛竿 → 观察，一次几分钟）。
    //   现在 `/atl config reload` 之后**下一条鱼就用新值**。
    //
    //   取值处直接写 Tuning.xxx()。热路径想省一点就先进局部变量：
    //     final double reelGain = Tuning.reelGain();

    /** 鱼冲刺时的拉力（暂未做成配置项，改动不频繁）。 */
    /** 抵消方向正确时的收益。 */
/*__DECL_Tuning.counterBonus()__*/
    /** 抵消方向错误时的惩罚。 */
/*__DECL_Tuning.counterPenalty()__*/

    /**
     * 压制阻尼的**溢出转化**：高于 1.0 的部分，取 50% 变成"单次拉线鱼力消耗上限"的加成。
     *
     * <p>用户定案："压制阻尼高于 1 的部分中的 50% 转化为单次拉线鱼力消耗上限叠加"。
     *
     * <p>为什么要这条：压制阻尼原本**只**抑制鱼回气（{@code regen / damping}），
     * 是个纯防守属性——你收线时它一点忙都帮不上。让它兼职"单轮能扣掉多少鱼力"，
     * 好轮子就同时有"磨得动"和"扛得住"两个收益，选轮子才有得权衡。
     *
     * <p>举例（合成阻尼 → 加成）：
     * <ul>
     *   <li>手线轮 1.00 → +0.00（无收益）</li>
     *   <li>鼓轮 1.45 → +0.225 → 单轮上限从 90% 抬到 112.5%</li>
     *   <li>大型纺车轮 2.50 → +0.75 → 单轮上限 165%</li>
     * </ul>
     *
     * <p><b>⚠ 上限看着很吓人，但实际扣力不止受它约束：</b>
     * 扣力 = {@code 0.01 × 体力上限 × 效率 × 控杆 × staminaDrain ÷ 抗性}，
     * 而 {@code staminaDrain} 合成后能到 2.6、效率最高 1.0——
     * 也就是真实扣力速度往往远低于这个上限，**限制它的是扣力速度本身**。
     * 这个加成真正起作用的场景是"低体力上限 + 高 damping"，
     * 以及避免单轮上限把好轮子卡住。仍然给一个硬顶（{@link #Tuning.capDampingBonus()}）
     * 防止以后有人把 damping 调到离谱数值时体力被一刀秒。
     */
    /**
     * 手上这根竿的渔轮槽是否带「单轮扣力上限」专属倍率。
     *
     * <p>读不到就返回 1.0（不影响）。这条路径每 tick 都会走，但不做缓存 ——
     * 玩家可以在对抗中换配件，缓存了就会出现"换了轮子没生效"的怪现象。
     */
    private double reelRoundCapBonus() {
        ItemStack rod = this.player.getMainHandItem();
        ModDataComponents.RodParts parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null) {
            return 1.0;
        }
        ItemStack reel = parts.reelStack();
        if (reel != null && reel.getItem()
                instanceof com.whitecobalt.anglertackle.item.TacklePartItem part) {
            return part.roundCapBonus();
        }
        return 1.0;
    }

    public double dampingBonusOnCap() {
        // ★ 公式**只有一份**：PartComposer.dampingBonusOnCap（tooltip 调的是同一个函数）。
        //   以前这里和 TacklePartItem 各写一遍、靠注释提醒"必须逐字一致" ——
        //   那不可靠：改一处忘一处就是"提示变了、判定没变"。
        //   ⚠ 配件专属倍率（大型纺车轮 1.25）要一起传进去，且硬顶也乘它 ——
        //     否则 0.75×1.25=0.9375 会被硬顶吃掉，等于这个特性白做。
        return PartComposer.dampingBonusOnCap(this.rodStats.damping(), reelRoundCapBonus());
    }

    /** 阻尼溢出转化率（用户定案 50%）。 */
/*__DECL_Tuning.dampingToCapRatio()__*/
    /** 阻尼转化加成的硬顶（占体力上限的比例），防止极端配置一刀秒鱼。 */
/*__DECL_Tuning.capDampingBonus()__*/

    /**
     * 单轮最多扣掉体力的比例。
     *
     * <p>低档鱼返回 1.0（= 不限制），所以装备够好就能一轮拿下；
     * 金/红档限制在 1/轮数 附近，强制跑满计划轮数。
     *
     * <p><b>⚠ 这个上限曾经把红鱼锁死。</b>
     * 原来是固定 {@code 0.9 / maxRounds}，于是"全程最多扣 90%"；
     * 而 {@code fishStamina} 每 tick 只被扣 {@code min(drain, cap - used)}，
     * **最后一次收线到不了精确的 cap**，攒不出那最后 0.01~0.04 点。
     * 轮次一旦耗尽，鱼就锁死在休息（{@code struggleTicks = 20*600}）再也不挣扎，
     * 冲刺扣力也停了，于是体力永远停在略大于 0 的位置 ——
     * 而 {@code tired} 判的是 {@code <= 0.0}，判定永远不成立。
     * 表现就是玩家看到的"蓝条满了却不上鱼"。
     *
     * <p>现在按**剩余轮数**重算：{@code (目标总扣力 - 已扣) / 剩余轮数}。
     * 这样"随装备/收线时机浮动"的那部分误差会被后面的轮次自动补回来。
     *
     * <p>目标总扣力 = {@code 体力上限 × 0.90 + 体力上限 × dampingBonusOnCap()}。
     * 阻尼加成加在**总量**而不是"每轮上限"上，效果是后面几轮的额度更宽裕，
     * 而不是第一轮就放开。
     */
    private double roundDrainCap() {
        if (this.fishTier < 4) {
            return 1.0;   // 低档：不限，装备好就一轮上
        }
        double target = this.fishStaminaMax * (Tuning.totalDrainTarget() + dampingBonusOnCap());
        // ★ 用**实际已扣掉的体力**算剩余额度，而不是"经上限允许扣掉的总量"。
        //   后者会漏掉冲刺扣力（sprint 是每 tick 绝对扣 0.10，不走上限），
        //   于是体力已经被冲刺打下去一大截、上限却还以为有额度没花完，
        //   最后几轮直接扣 0 —— 那正是"到 41 鱼力就不动了"的成因之一。
        double drained = Math.max(0.0, this.fishStaminaMax - this.fishStamina);
        double remaining = Math.max(0.0, target - drained);
        int roundsLeft = Math.max(1, this.maxRounds - this.roundsDone + 1);
        if (this.roundsExhausted) {
            // 已经没有下一轮了：把剩余额度一次放开，否则玩家永远收不动
            return 1.0;
        }
        double perRound = remaining / roundsLeft;
        // 换算成"占最大体力的比例"，与原语义保持一致
        return this.fishStaminaMax <= 0 ? 1.0 : perRound / this.fishStaminaMax;
    }
    /** 整场对抗计划扣掉的体力比例（剩下 1 - 本值 留给最后的长休息期）。 */
/*__DECL_Tuning.totalDrainTarget()__*/
    /**
     * 这条鱼"进疲劳"的体力比例（鱼种值 × 全局旋钮，夹在 1.0）。
     *
     * <p>鱼种值是 {@code FightProfile.fatigueThreshold()}（burst 0.34 / erratic 0.32 /
     * steady 0.30 / sluggish 0.28），全局旋钮 {@code fatigueThresholdScale} 再乘一层 ——
     * 用来整体提前/推后"力竭窗口"什么时候开。
     *
     * <p>为什么要这个旋钮（用户 2026-10-06："我在游戏中实际感觉力竭更难拉了"）：
     * 查他那一局的日志，31 条张力算式**全是** {@code 力竭涨速×1.00} —— 鱼一次都没进疲劳。
     * 根因是红档体力池 ×1.5 之后，同一个"34%"落在 **1.5 倍的绝对值**上
     * （450 池 → 要扣到 153 才亮【疲劳·快拉】），玩家的体感就是"它明明快没劲了却怎么都不力竭"。
     * 不想动池子厚度、只想让力竭来得早一点时，调这个旋钮（调参器热改）。
     *
     * <p>⚠ 结果夹在 1.0：比例 &gt;1 等于"一咬钩就力竭"，那是另一个效果，
     * 不该由这个旋钮偷偷实现 —— 要那个效果请直接调鱼种的 fatigueThreshold。
     */
    private double fatigueThresholdNow() {
        return Math.min(1.0, this.profile.fatigueThreshold() * Tuning.fatigueThresholdScale());
    }
    /** 近岸圈半径（格）。 */
/*__DECL_Tuning.nearShoreBase()__*/

    // ------------------------------------------------------------ 会话状态
    private final ServerPlayer player;
    private State state = State.IDLE;

    private BobberEntity bobber;
    /** 蓄力开始时的游戏刻。 */
    private long chargeStartTick;

    // 抛投参数
    private double castPower;
    private double castDistance;
    /**
     * 这一竿是不是"**冰洞精准入洞**"（用户 2026-10-08 报的 bug 引出来的）。
     *
     * <p>为什么需要这个标记：{@code castIntoIceHole} 把 {@code castDistance} 有意记作
     * {@link IceFishing#iceHoleCastDistance()}（12 米，见那里的注释），
     * 但 {@code beginWaiting} 末尾会**无条件**把它覆盖成"玩家到浮漂的真实距离" ——
     * 而冰洞就在脚边（真实距离约 6 米），于是那个意图被整个冲掉：
     * 玩家报"钓鱼在冰洞上钩后依旧显示 6 米左右"就是这么来的 ✗。
     */
    private boolean iceHoleCast;

    // 咬钩
    private int waitTicks;
    private int waitTicksTotal;
    private int bitePhaseTicks;
    private byte bitePhase;
    private int hookWindowTicks;
    private int hookWindowTotal;

    // 对抗
    private double tension;
    private int redZoneTicks;
    private int redZoneLimit;
    private double fishStamina;
    private double fishStaminaMax;
    private double distance;
    private double maxDistance;
    private StruggleDir struggleDir = StruggleDir.NONE;
    private int struggleSwitchTicks;
    private boolean sprinting;
    private int sprintTicks;
    private int sprintCooldownTicks;
    /** 疲劳期：反拉的最佳窗口（拉拽 ×fatiguedPullSpeed、张力 ×0.8、不回气）。 */
    private boolean fatigued;

    /** 咬钩时定下的鱼种与个体（提竿后钓上来的就是它）。 */
    @Nullable
    private FishSpecies hookedSpecies;
    @Nullable
    private FishData hookedData;

    /** 诊断用 tick 计数（日志节流）。 */
    private int tickCounter;

    // ------------------------------------------------------------------ 挣扎循环
    /** 鱼当前是不是在挣扎（false = 力竭休息中）。 */
    private boolean fishStruggling = true;
    /** 距离下次切换状态的 tick 数。 */
    private int struggleTicks = 20;
    /** 这条鱼的品质档（决定挣扎/休息时长与轮次）。 */
    private int fishTier = 0;
    /** 游动速度倍率（随档位上升）——影响张力、距离、漂的拖动。 */
    private double tierSpeed = 1.0;
    /** 体力抗性倍率（随档位上升）——抵消装备的收线扣力。 */
    private double tierStaminaResist = 1.0;
    /** 本轮挣扎已经扣掉多少鱼力（配合单轮上限用）。 */
    private double roundDrain = 0.0;
    /** 已经完成的挣扎轮数。 */
    private int roundsDone = 0;
    /** 这根竿这条鱼最多挣扎几轮（用户定案）。 */
    private int maxRounds = 2;
    /** 轮次是否已用尽（用尽后鱼不再挣扎，只剩收线）。 */
    private boolean roundsExhausted = false;
    /** "鱼已力竭"只播报一次。 */
    private boolean exhaustAnnounced = false;
    /** 本 tick 的收线速度（格/tick），供 /atl rod 诊断。 */
    private double lastReelSpeed = 0.0;
    /** 本 tick 鱼的拽出力（格/tick），供 /atl rod 诊断。 */
    private double lastFishOut = 0.0;
    /** 本 tick 的张力净变化（点/tick），供 /atl rod 诊断。 */
    private double lastTensionDelta = 0.0;
    /** 本 tick 鱼的拉力对张力的贡献（点/tick），供诊断。 */
    private double lastFishTension = 0.0;
    /** 张力算式的打印节流计数（每 20 tick 一行）。 */
    private int reelDebugCounter = 0;

    /**
     * 收线时，鱼的拉力最多把涨势削到的比例（用户定案）。
     *
     * <p>0.25 = 保留 25% 的原始涨势。设成 0 就等于"收线时鱼拉完全不参与"，
     * 设成 1 就回到"能被完全抵消"的老 bug（张力卡在平衡点不动）。
     */
/*__DECL_Tuning.minReelGainRatio()__*/

    /**
     * 中立/放线时，鱼的拉力最多能"减缓"多少回落（占回落量的比例）。
     *
     * <p>0.90 = 最多把回落削到十分之一，留出至少 10% 的净回落。
     * 设成 1.0 就回到老 bug：鱼的拉力恰好等于回落 → 张力净变化为 0 → 条子卡死不动。
     */
/*__DECL_Tuning.maxFishSlowdown()__*/

    // ================================================================ 张力轨迹
    // 玩家反馈"张力条跑到中间停住，任何操作都不涨不落"。
    // 光看代码推不出到底卡在哪一步，所以把对抗期间的张力轨迹记下来，
    // 结束时一次性打到日志 —— 出问题时直接看数值，不用猜。
    //
    // 每 10 tick 一条，一条对抗最多记 240 条（120 秒），够覆盖任何一次遛鱼。
    private static final int TENSION_TRACE_INTERVAL = 10;
    private static final int TENSION_TRACE_MAX = 240;
    private final java.util.List<String> tensionTrace = new java.util.ArrayList<>();
    private int tensionTraceAt = 0;
    /** 上一次记录的张力值，用来判断"是否卡住"。 */
    private double tensionTraceLast = -1.0;
    /** 连续"几乎没变"的次数（用于在日志里标出僵持段）。 */
    private int tensionStallStreak = 0;

    /**
     * 记录一条张力轨迹（每 {@link #TENSION_TRACE_INTERVAL} tick 一次）。
     *
     * <p>格式：{@code tick|张力|净变化|鱼拉|输入|阶段|疲劳|距离|收线|鱼拽}
     * —— 一行就能看出"是输入没生效、还是鱼拉抵消了回落、还是被上限钳住"。
     *
     * <p>距离/收线/鱼拽三列是用户 2026-10-06 报"力竭了也收不回来"之后补的：
     * 原来只记张力，而那个症状发生在**距离**上，日志里根本看不到"收不动"的证据。
     * 注意它们的采样点在本 tick 的距离结算**之前**（轨迹在张力段末尾记），
     * 所以比同一行的输入晚一个 tick —— 对 10 tick 一条的轨迹无所谓。
     */
    private void traceTension(ServerLevel level) {
        long now = level.getGameTime();
        if (now % TENSION_TRACE_INTERVAL != 0L) {
            return;
        }
        if (tensionTrace.size() >= TENSION_TRACE_MAX) {
            return;
        }
        // 僵持判定：|净变化| < 0.01 点/tick（每秒 0.2 点，肉眼就是不动）
        if (Math.abs(this.lastTensionDelta) < 0.01) {
            this.tensionStallStreak++;
        } else {
            this.tensionStallStreak = 0;
        }
        String input = this.reeling ? "收线" : this.releasing ? "放线" : "中立";
        tensionTrace.add(String.format(
                "%d|%.2f|%+.4f|%.4f|%s|%s|%s|距%.1f|收%.3f|拽%.3f|僵持%d",
                now, this.tension, this.lastTensionDelta,
                lastFishTension, input,
                this.fishStruggling ? "挣扎" : "休息",
                this.fatigued ? "疲劳" : "-",
                this.distance, this.lastReelSpeed, this.lastFishOut,
                this.tensionStallStreak));
        this.tensionTraceAt++;
    }

    /** 把张力轨迹打到日志（对抗结束时调用一次）。 */
    private void dumpTensionTrace(String reason) {
        if (tensionTrace.isEmpty()) {
            return;
        }
        FishingManager.LOGGER.info("[垂纶] 张力轨迹（{}，共 {} 条，格式 tick|张力|净变化/点每tick|鱼拉|输入|阶段|疲劳|距离|收线速度|鱼拽速度|僵持）",
                reason, tensionTrace.size());
        for (String line : tensionTrace) {
            FishingManager.LOGGER.info("[垂纶]   {}", line);
        }
        int maxStall = 0;
        for (String line : tensionTrace) {
            int i = line.lastIndexOf('|');
            if (i > 0) {
                try {
                    maxStall = Math.max(maxStall, Integer.parseInt(line.substring(i + 1).replace("僵持", "")));
                } catch (NumberFormatException ignored) {
                    // 轨迹是诊断用的，解析失败不该影响任何东西
                }
            }
        }
        FishingManager.LOGGER.info("[垂纶] 张力轨迹汇总：最长僵持 {} 条（= {} tick = {} 秒）",
                maxStall, maxStall * TENSION_TRACE_INTERVAL,
                String.format("%.1f", maxStall * TENSION_TRACE_INTERVAL / 20.0));
        tensionTrace.clear();
    }

    /**
     * 这条鱼最多挣扎几轮（用户定案）。
     *
     * <ul>
     *   <li><b>红档</b>：5~7 轮（随机）</li>
     *   <li><b>金档</b>：4~5 轮（随机）</li>
     *   <li><b>其余档（白/绿/蓝/紫）</b>：固定 2 轮</li>
     * </ul>
     *
     * <p>轮次用尽后鱼**再也不挣扎**，玩家只需要收线——这就是"遛翻了"的爽点。
     *
     */
    private int roundsForTier(int tier) {
        if (tier >= 5) {
            return 5 + this.player.getRandom().nextInt(3);   // 红：5~7
        }
        if (tier == 4) {
            return 4 + this.player.getRandom().nextInt(2);     // 金：4~5
        }
        return 2;                                            // 其余：固定 2 轮
    }

    private int fatigueTicks;

    /**
     * 力竭结束后的**再进入冷却**（tick）。
     *
     * <p>⚠⚠ 2026-10-29（用户："力竭后还想挣扎，结果鱼力早就超过某个阈值所以立刻改回力竭状态，
     * 听起来断断续续"）：原来的状态机是
     * <pre>
     *   if (fatigued) { if (--fatigueTicks <= 0) fatigued = false; }
     *   else if (体力仍低于阈值 && ...) { fatigued = true; }
     * </pre>
     * 两分支是 if/else-if ⇒ 退出力竭的**下一 tick** 就又能立刻重新进入（体力没回上去时必然发生）。
     * 结果：每 {@code fatigueTicks} 就闪一次"非力竭"，客户端 {@code fatigued} 跟着抖，
     * 音效在 reel_tired / reel_fight 之间反复重建 —— 听感就是一顿一顿。
     *
     * <p>这里加一个短暂的再进入冷却：退出后至少 10 tick（0.5 秒）内不许重新点亮。
     * 不改变"能拉多快"的平衡（只影响状态抖动的粒度），但能让 UI/音效稳定。
     */
    private int fatigueLockoutTicks;
    /**
     * **保底挣扎**倒计时（tick）：&gt;0 表示这条鱼这一轮还处在"保底没力竭"的窗口里
     * （用户 2026-10-06 定案，见 {@link FishSpecies#floorStruggleSeconds}）。
     *
     * <p>轮次开头把它设成"保底秒数 × 20"并**清掉疲劳标记**；倒计时归零之前疲劳不能再点亮。
     * 效果：以前"拉到一次力竭，后面每轮挣扎不到一秒就软了"，
     * 现在**每一轮都要重新把它遛软**。
     */
    private int freshStruggleTicks;
    /** 假动作标记（神经质型鱼：预告一个方向实际反方向）。 */
    private boolean fakeOutPending;
    /** 当前鱼的手感档案（M1 固定为鲫鱼；M3 从鱼种 JSON 读）。 */
    private FightProfile profile = FightProfile.CRUCIAN_CARP;

    // 输入（来自客户端，每 tick 刷新；掉包时自动视为松开）
    private boolean reeling;
    private boolean releasing;
    private int counterDir;      // -1 / 0 / +1
    private int inputStaleTicks;

    /**
     * 连续多少 tick 没收到输入就当作松手。
     *
     * <p>客户端每 tick 发一次输入包，所以正常情况这个计数一直归零。
     * 给到 5 是为了容忍偶发丢包 —— 阈值定得太紧（曾经是 3，而客户端每 2 tick 才发）
     * 会让"网络抖一下"直接变成"玩家松手了"，从而出现张力与距离同时冻住的现象。
     */
/*__DECL_Tuning.inputStaleLimit()__*/

    // 合成的配件属性（从手上那根竿的六槽装配算出来）
    private RodStats rodStats = RodStats.neutral(RodItem.RodSpec.WOOD);
    /** 是否已经读取过竿的属性（每根竿只读一次）。 */
    private boolean statsLoaded;

    /**
     * 本次咬钩实际扣掉的耐久，供提前收回时原额退回（见 {@link #refundBiteDurabilityCost()}）。
     * {@code null} = 这一步还没咬钩，没扣过。
     */
    @Nullable
    private BiteCost lastBiteCost;

    public FishingSession(ServerPlayer player) {
        this.player = player;
        // 附魔等级在**开局钉死**（理由同 water / overCapacity）：对抗中竿不会换，
        // 每 tick 现读既浪费又可能读到"竿刚断所以没附魔"的中间态。
        ItemStack rod = player.getMainHandItem();
        if (!(rod.getItem() instanceof com.whitecobalt.anglertackle.item.RodItem)) {
            rod = player.getOffhandItem();
        }
        this.enchReinforced = RodEnchants.level(rod, RodEnchants.REINFORCED);
        this.enchRescue = RodEnchants.level(rod, RodEnchants.RESCUE);
        this.enchDrag = RodEnchants.level(rod, RodEnchants.DRAG);
        this.enchPreciseHunt = RodEnchants.level(rod, RodEnchants.PRECISE_HUNT);
        this.enchHighQuality = RodEnchants.level(rod, RodEnchants.HIGH_QUALITY_LURE);
        this.enchLoadBearing = RodEnchants.level(rod, RodEnchants.LOAD_BEARING);
        this.enchRodIsLure = RodEnchants.isLure(rod);
    }

    // ---- 附魔等级（见 RodEnchants 的类注释：数值口径只在那边算）----
    private final int enchReinforced;
    private final int enchRescue;
    private final int enchDrag;
    private final int enchPreciseHunt;
    private final int enchHighQuality;
    private final int enchLoadBearing;
    private final boolean enchRodIsLure;
    /** 抢救是否已经用掉（"每场对抗一次"）。 */
    private boolean rescueUsed;

    // =================================================================== 查询
    public State state() {
        return state;
    }

    public ServerPlayer player() {
        return player;
    }

    public BobberEntity bobber() {
        return bobber;
    }

    public double tension() {
        return tension;
    }

    public double fishStamina() {
        return fishStamina;
    }

    /**
     * 鱼力是否算"耗尽"。
     *
     * <p><b>为什么不用精确的 {@code <= 0.0}：</b>
     * 收线扣力是每 tick {@code min(drain, cap - used)} 一段段累加的，
     * 受浮点累积误差影响，永远差最后零点几点扣不到恰好 0。
     * 而 HUD 的蓝条是 {@code 1 - staminaRatio}，那 0.03/380 = 0.008%
     * 四舍五入**显示成满格**——玩家看到"蓝条满了却不上鱼"就是这么来的。
     *
     * <p><b>用户定案：判"耗尽"的门槛放到最大体力的 0.5%。</b>
     * 红鱼体力 380 时就是 1.9 点。蓝条只要显示满格（四舍五入到 0%），
     * 判定就必须也认——不能出现"看着满了却不算数"。
     * 而且 {@code tickFight} 里到了这个门槛是**直接强制上鱼**，
     * <b>不再要求把线收回近岸</b>：鱼都没劲了还让玩家隔着十几米干拉，
     * 那不是难度，是折磨。
     */
    public boolean isFishTired() {
        return this.fishStamina <= Math.max(Tuning.staminaEpsilonFloor(),
                this.fishStaminaMax * Tuning.staminaEpsilonRatio());
    }

    /** 鱼力耗尽的绝对容差下限（体力很小的鱼也要能判定）。 */
/*__DECL_Tuning.staminaEpsilonFloor()__*/
    /**
     * 鱼力耗尽的相对容差（占最大体力）——**用户定案 0.5%**。
     *
     * <p>原来取 0.005（0.5%）是为了吸收浮点误差，现在这个值同时承担
     * "宽松判定"的语义：到 0.5% 就算遛翻，直接上鱼。
     */
/*__DECL_Tuning.staminaEpsilonRatio()__*/

    public double distance() {
        return distance;
    }

    /**
     * 上鱼判定的**全部输入**，一行文本（调试命令 /atl rod 打出来给玩家看）。
     *
     * <p>存在理由：玩家反馈"蓝条满不上鱼"。蓝条满 = {@code 1 - staminaRatio} = 1
     * = {@code fishStamina == 0}，而那本该让 {@code tired} 成立。
     * 光看代码推不出到底卡在哪个条件上——很可能是 {@code maxDistance}
     * 或近岸圈这两个数在实战里和设计值不一致。
     * 与其猜，不如把这两个数和判定结果直接摊出来。
     *
     * <p>判定逻辑与 {@code tickFight} 第 7 段保持**完全一致**，改那边必须同步这里。
     */
    public String landCheckInfo() {
        double nearShore = this.castDistance * 0.25 + 2.0;
        boolean tired = this.isFishTired();
        boolean pulledDry = this.distance <= 0.0;
        double threshold = Math.max(Tuning.staminaEpsilonFloor(),
                this.fishStaminaMax * Tuning.staminaEpsilonRatio());
        String verdict;
        if (pulledDry) {
            verdict = "§a可上鱼（拉干：线已收到 0）";
        } else if (tired) {
            verdict = "§a可上鱼（遛翻：鱼力 " + String.format("%.2f", this.fishStamina)
                    + " ≤ 阈值 " + String.format("%.2f", threshold) + "，不看距离）";
        } else {
            verdict = "§7还不能上鱼：鱼力 " + String.format("%.2f", this.fishStamina)
                    + "，离阈值还差 " + String.format("%.2f", this.fishStamina - threshold)
                    + "；线还剩 " + String.format("%.1f", this.distance) + " 米";
        }
        return String.format(
                "§7鱼力 §f%.2f§7/§f%.2f §8(%.2f%%)  §7阈值 §f%.2f §8(%.1f%%)  §7tired=§f%s"
                        + "  §7线 §f%.2f§7/§f%.2f米 §8(近岸圈 %.1f 已废除)"
                        + "  §7档 §f%d  §7轮 %d/%d  §7疲劳§f%s"
                        + "%n§7  阻尼 §f%.2f §7→ 单轮上限加成 §f+%.1f%%§7  总目标 §f%.0f%%"
                        + "%n§7  回拉 不挣扎§f%.2f§7/挣扎§f%.2f§7  %s  收线§f%.3f§7 vs 鱼拽§f%.3f %s"
                        + "%n  §7→ %s",
                this.fishStamina, this.fishStaminaMax,
                this.fishStaminaMax <= 0 ? 0.0 : this.fishStamina / this.fishStaminaMax * 100.0,
                threshold, Tuning.staminaEpsilonRatio() * 100.0, tired,
                this.distance, this.maxDistance, nearShore,
                this.fishTier, this.roundsDone, this.maxRounds, this.fatigued,
                this.rodStats.damping(), this.dampingBonusOnCap() * 100.0,
                (Tuning.totalDrainTarget() + this.dampingBonusOnCap()) * 100.0,
                this.rodStats.pullIdle(), this.rodStats.pullStruggle(),
                this.fishStruggling ? "§c(挣扎·用后者)" : "§a(休息·用前者)",
                this.lastReelSpeed, this.lastFishOut,
                this.lastReelSpeed > this.lastFishOut ? "§a收得动" : "§c收不动",
                verdict);
    }

    /**
     * 这根竿能承受的最大体重（kg），由竿的材质决定；0 = 不限。
     * 用于上鱼时筛选鱼种——小竿钓不到蓝鳍金枪鱼。
     */
    public double maxWeightKg() {
        ItemStack rod = this.player.getMainHandItem();
        if (rod.getItem() instanceof com.whitecobalt.anglertackle.item.RodItem rodItem) {
            return rodItem.spec().maxWeightKg();
        }
        return 0.0;
    }

    /**
     * 这次抛投最多能出到几档品质（0 白 … 5 红）。
     *
     * <p>直接读竿材质声明的档位（{@link com.whitecobalt.anglertackle.item.RodItem.RodSpec#tier()}）：
     * 木 1 → 铁 2 → 金 3 → 钻 4 → 合金 5。
     *
     * <p><b>这里原来是按承重现推的</b>（{@code <10→1、<25→2、<50→3、≥50→5}），
     * 结果档位 4 永远取不到（没有材质承重落在 25~50），
     * 金竿还因为只有 12kg 掉到"到蓝"、比铁竿还差，
     * 最终表现就是玩家反馈的"几十杆下去没一条金以上"。
     * 档位是**设计参数**，不该从承重这种平衡参数里反推。
     */
    public int maxTier() {
        // 测试拟饵：把杆的档位上限放开到 5，让金红鱼种能进池
        // （个体层还要再钉一次，见 beginFight）
        if (this.testLureEquipped()) {
            return 5;
        }
        ItemStack rod = this.player.getMainHandItem();
        if (rod.getItem() instanceof com.whitecobalt.anglertackle.item.RodItem rodItem) {
            // ★★ 台钓竿**没有品质上限**（用户 2026-10-02 定案："取消台钓的档位上限机制"）。
            //
            // 原来这里一律返回 spec().tier()（木1 铁2 金3 钻4 合金5）。
            // 后果之一就是洞穴水域那次：
            //   铁竿（上限蓝 2）+ 秘制窝（下限蓝 2），而洞穴的台钓鱼是
            //   横纹鱼[0,1]（太低）、长吻象鼻鱼[3,4] 与蓝宝石雷龙鱼[5]（太高），
            //   中间没有蓝档 → 池子空了 → 退回不限水体 → 在洞里钓出草鱼青鱼。
            //
            // 取消之后：台钓竿能钓到什么，只由**水体（鱼在哪）**与
            // **鱼饵/窝料（谁愿意咬）**决定，与竿的材质无关。
            // 材质仍然决定承重、耐久、张力与手感 —— 那才是它该管的事。
            //
            // ⚠ 路亚竿**保留**档位上限：它的材质阶梯与拟饵档位是配套的一条进度线
            //   （也是"好竿才能上大货"在那条线上唯一的表达）。
            if (rodItem.rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
                return 5;
            }
            return rodItem.spec().tier();
        }
        return 1;
    }

    /**
     * 这次抛投的距离档位（用户定案：近抛压高级鱼、高抛抬高级鱼）。
     *
     * <p>按实际落点距离判断：≤10 米算近抛、≥20 米算高抛，中间是平抛。
     * 用**实际距离**而不是玩家当时的视角，是因为"抛了多远"才是影响
     * 水深的真实因素（视角只是手段）。
     */
    public FishSpecies.CastRange castRange() {
        if (this.castDistance <= 10.0) {
            return FishSpecies.CastRange.NEAR;
        }
        if (this.castDistance >= 20.0) {
            return FishSpecies.CastRange.FAR;
        }
        return FishSpecies.CastRange.LEVEL;
    }

    /**
     * 读出**当前实际装在竿上的**饵 / 拟饵的档位（0~5），没装或读不到返回 0。
     *
     * <p><b>为什么必须查物品本身，而不是查鱼竿：</b>
     * 拟饵的档位存在**拟饵自己**的 {@code PART_STATS} 里。
     * 之前这里写的是 {@code rod.get(PART_STATS)}——读的是鱼竿自己的组件，
     * 而鱼竿从来没写过这个组件，于是永远返回 0，
     * 「金饵过滤蓝档以下」这条规则对路亚竿**完全没生效**
     * （玩家反馈："用金以上的路亚饵还是会出绿色以下的鱼"）。
     * 鱼饵（{@link com.whitecobalt.anglertackle.item.BaitItem}）走它自己的 {@code tier()}。
     *
     * <p>台钓竿看 {@code bait} 槽，路亚竿看 {@code lure} 槽。
     *
     * <p><b>返回的是 0 起算的『品质档位』（白=0 绿=1 蓝=2 紫=3 金=4 红=5）</b>——
     * 与 {@code Quality} 枚举、与鱼的档位同一套。
     *
     * <p>⚠ 这里曾经把两套档位混着返回：鱼饵本来就是 0 起算（面包屑 0 … 秘制香饵 5），
     * 而拟饵是 1 起算（亮片 2 … 镀金 6），于是同一个方法对两种竿返回两种口径。
     * 后果是**台钓整条『金红占比』链整体错一档**：活虾（紫，3）落进 default 完全没加成、
     * 发光饵（金，4）拿到紫饵的 2%、秘制香饵（红，5）拿到金饵的 4.5%，
     * 用户定的"红饵 10%"在台钓上**永远不会出现**。
     * 现在统一在这里 −1 归一，调用方只看 0 起算的一套。
     */
    public int equippedBaitTier() {
        ItemStack rod = this.player.getMainHandItem();
        var parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null) {
            return 0;
        }
        boolean floatRod = rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT;
        // ⚠ 见底的拟饵 / 鱼饵不参与。以前这里只读物品 id（parts.bait() / parts.lure()），
        //   磨损完全不看 —— 于是"耐久 0/20 的拟饵"照样把鱼种池往高档拉，
        //   玩家看到的现象就是"耐久用光了加成还在"。
        if (RodStats.isWornOut(floatRod ? parts.baitStack() : parts.lureStack())) {
            return 0;
        }
        String id = floatRod ? parts.bait() : parts.lure();
        if (id == null || id.isEmpty()) {
            return 0;
        }
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            return 0;
        }
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(rl);
        // 鱼饵：档位在物品自己的字段上
        if (item instanceof com.whitecobalt.anglertackle.item.BaitItem bait) {
            return bait.tier();
        }
        // 拟饵：档位在它自己 stack 的 PART_STATS 上（lureTier 借用了 sensitivity 字段）
        //
        // ⚠ 必须 −1：拟饵/配件是 **1 起算**（1白 2绿 … 6红），
        //   而本方法对外承诺 0 起算。这与 TacklePartItem 里
        //   `Quality.of(stats.partTier() - 1)`（决定物品名字颜色）是同一个换算 ——
        //   两处口径必须一致，否则"显示成红档的拟饵"和"按红档算加成"会对不上。
        if (item instanceof com.whitecobalt.anglertackle.item.TacklePartItem part && part.isLure()) {
            PartStats stats = new ItemStack(item).get(ModDataComponents.PART_STATS.get());
            if (stats != null && stats.partTier() > 0) {
                return stats.partTier() - 1;
            }
        }
        return 0;
    }

    /**
     * **台钓饵料**把咬钩等待压低多少（%）。竿型不是台钓 / 空槽 / 饵料耗尽 → {@code 0}。
     *
     * <p>用户定案 2026-10-02："台钓饵料对咬钩时间也进行下降，具体数值：
     * 白 5% 绿 8% 蓝 12% 紫 16% 金 18.5% 红 21%，以上提到的数值属于**加算**。"
     * 竿本身那 −30% 在 {@code Config.biteFloatRodCutPercent}，两边加算后一起用
     * （见 {@link #beginWaiting}）。
     *
     * <p>⚠ <b>必须单独问一句"槽里到底有没有饵"</b>：{@link #equippedBaitTier()} 把
     * "没装饵"和"白档饵"**都**记作 0（白档就是 0，这对其他消费点是对的），
     * 但减时不能这么算 —— 否则空槽会白拿"白饵 −5%"，
     * 表现就是"把饵拿下来反而更慢一点点"这种说不通的手感。
     *
     * <p>⚠ 这里的取槽 / 判耗尽必须与 {@link #equippedBaitTier()} **完全一致**
     * （台钓看 {@code bait} 槽）。两处一旦分叉，就会重演"用的是 bait 的加成、
     * 扣的是 lure 的耐久"那个已经踩过的坑，所以本方法刻意只做门控，
     * 档位仍旧回去问 {@code equippedBaitTier()}，不自己再数一遍刻度。
     */
    public double equippedBaitCutPercent() {
        if (rodType() != com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            return 0.0;
        }
        ModDataComponents.RodParts parts = this.player.getMainHandItem()
                .get(ModDataComponents.ROD_PARTS.get());
        if (parts == null || RodStats.isWornOut(parts.baitStack())) {
            return 0.0;
        }
        String id = parts.bait();
        if (id == null || id.isEmpty()) {
            return 0.0;
        }
        return BiteTiming.baitCutPercent(this.equippedBaitTier());
    }

    /**
     * 装备中的**拟饵物品 id**（台钓竿、没装拟饵、拟饵已耗尽 → 空串）。
     *
     * <p>习性词条只认拟饵：台钓的饵料/窝料不走词条机制（用户定案
     * "拟饵才按鱼的习性加权重"）。
     */
    public String equippedLureId() {
        ItemStack rod = this.player.getMainHandItem();
        var parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null || rodType() != com.whitecobalt.anglertackle.item.RodItem.RodType.LURE) {
            return "";
        }
        // 和 equippedBaitTier() 一样：见底的拟饵不参与（"耐久用光了加成还在"那次）
        if (RodStats.isWornOut(parts.lureStack())) {
            return "";
        }
        String id = parts.lure();
        return id == null ? "" : id;
    }

    /**
     * 装备中的拟饵**认的习性词条**位掩码（见 {@link FishHabits}）。
     *
     * <p>0 = 不按词条加权：台钓竿、没装拟饵、拟饵耐久见底、或装了不认词条的
     * 调试拟饵（{@code lure_test_red} 故意不在表里）。
     */
    public int equippedLureHabitMask() {
        return FishHabits.lureMask(equippedLureId());
    }

    /**
     * **咬钩下限**——只对**路亚拟饵**生效；台钓饵料不再提升下限（用户定案）。
     *
     * <p><b>用户最新定案：</b>"台钓饵料不提升下限，只提升上高价值鱼概率，
     * 金红总概率随饵料升级概率变动为 3% 到 10%"。
     * 所以台钓走 {@link #highValueBonus(int)}，下限恒为 0（什么鱼都能咬）。
     *
     * <p>路亚拟饵**保留**原来的下限规则（紫饵过滤绿档以下、金/红过滤蓝档以下）：
     * 拟饵的档位本来就是"能钓多高级的鱼"的载体（见 ModItems 拟饵段注释），
     * 而且用户只说了台钓饵料要改。
     *
     * <p>档位是 **0 起算的品质档**（白=0 绿=1 蓝=2 紫=3 金=4 红=5），
     * 由 {@link #equippedBaitTier()} 统一归一后传进来（它已经替拟饵做了 −1）。
     * ⚠ 这里原来写的是 {@code case 4 / case 5,6}——那是**拟饵的 1 起算档位**
     * （1白 2绿 3蓝 4紫 5金 6红）下的写法，配着 0 起算的鱼饵就会整体错一档。
     * 现在只有一套口径：紫 = 3、金 = 4、红 = 5。
     */
    public int baitMinTier() {
        // 台钓：不设下限
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            return 0;
        }
        // 路亚：拟饵档位 → 下限
        int tier = this.equippedBaitTier();
        return switch (tier) {
            case 3 -> 1;      // 紫拟饵：过滤白档（绿档起）
            case 4, 5 -> 2;   // 金/红拟饵：过滤白绿（蓝档起）
            default -> 0;     // 绿/蓝拟饵：不限
        };
    }

    /**
     * **高价值鱼目标占比**（用户定案）。
     *
     * <p>口径："台钓饵料不提升下限，只提升上高价值鱼概率，
     * 金红总概率随饵料升级概率变动为 **3% 到 10%**"。
     *
     * <p>返回的是"金+红鱼种在鱼池权重里应该占的百分比"，不是倍率。
     * 真正的权重调整在 {@link FishSpecies#buildPool} 里**精确求解**：
     * 设金红权重乘 k、其余不变，要使 {@code (k·hv)/(k·hv+other) = p}，
     * 解得 {@code k = p·other / ((1-p)·hv)}。
     *
     * <p><b>为什么不在这一层算倍率：</b>一开始我在这里按"基准占比 4%"反推倍率，
     * 结果金饵实测 5.4%、红饵 16.8%，和目标 3%/10% 都差很远——
     * 因为台钓池只含 1 种金红鱼、路亚池含 3 种，池子组成一变，
     * "基准占比"这个常数就失效了。只有拿到**真实池子**才算得准。
     *
     * @param tier 饵料/拟饵的**品质档（0 起算：白=0 绿=1 蓝=2 紫=3 金=4 红=5）**，
     *             由 {@link #equippedBaitTier()} 归一后传入
     * @return 目标占比（%）；&lt;=0 表示不调整（用基准权重）
     */
    public double highValueBonus(int tier) {
        return targetHighValuePct(tier);
    }

    /**
     * 各档饵料/拟饵的**目标"金+红"总占比**（%）。用户 2026-10-01 定案：
     * 紫饵 2% / 金饵 4.5% / 红饵 10%，绿蓝两档不调整。
     *
     * <p>⚠ 入参是 **0 起算的品质档**（白=0 … 红=5），与 {@code Quality} 枚举一致。
     * 这里连着错过两次，两次都是**两套档位混用**：
     * <ol>
     *   <li>先是写成 {@code case 4 -> 3.0; case 5 -> 10.0;} 还注着"金饵 3% / 红饵 10%" ——
     *       那是按 0 起算写的 case 配 1 起算的注释，红饵落进 default 一点加成都没有；</li>
     *   <li>改成 1 起算（4/5/6）之后**拟饵是对的，台钓鱼饵又整体错一档**
     *       （鱼饵本来就是 0 起算）—— 活虾（紫，3）没加成、秘制香饵（红，5）只拿到 4.5%。</li>
     * </ol>
     * 现在全链只有一套：{@link #equippedBaitTier()} 负责把拟饵的 1 起算档 −1 归一。
     */
    public static double targetHighValuePct(int baitTier) {
        return switch (baitTier) {
            case 3 -> 2.0;    // 紫：2%
            case 4 -> 4.5;    // 金：4.5%
            case 5 -> 10.0;   // 红：10%
            default -> -1.0;  // 白/绿/蓝：不调整
        };
    }

    /**
     * 当前是否装着**创造模式测试拟饵**。
     *
     * <p>用户定案："添加创造模式测试路亚饵，可以套用现有镀金竞技拟饵贴图，
     * 仅测试用，上红概率 100%"。
     *
     * <p>它影响两处：鱼种层把档位上限放开到 5（让金红鱼种进池），
     * 个体层直接钉成红档。两处都要，只改一处会出现
     * "鱼种进来了但 roll 出来的还是金鱼"。
     */
    public boolean testLureEquipped() {
        ItemStack rod = this.player.getMainHandItem();
        var parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null) {
            return false;
        }
        return com.whitecobalt.anglertackle.registry.ModItems.TEST_LURE.getId().toString()
                .equals(parts.lure());
    }

    /**
     * 鱼饵对特定档位的额外吸引力——**红饵/红拟饵的金红鱼加权**。
     *
     * <p>返回该档位的**权重倍率**。
     *
     * <p>档位是 **0 起算的品质档**（白=0 … 红=5）：红饵 = 红拟饵 = 5。
     * ⚠ 这里原来写的是"红饵 = 5、红拟饵 = 6"，那是鱼饵 0 起算、拟饵 1 起算混用的产物；
     * 现在 {@link #equippedBaitTier()} 已统一归一，只有一个红档 = 5。
     *
     * <p><b>注意这个方法本身不能直接写进 {@code FishSpecies}：</b>
     * 它要读玩家主手物品，而 {@code FishSpecies} 是纯数据表。
     * 调用方把它作为 {@code IntToDoubleFunction} 传进
     * {@link FishSpecies#roll} / {@link FishSpecies#buildPool} 才会生效——
     * 早先这个方法**定义了却没有任何调用点**，红饵加成一直没生效。
     *
     * @param tier **鱼的品质档（0 起算）**，即 {@code s.maxTier()}；
     *             4 = 金鱼、5 = 红鱼
     */
    public double baitTierBonus(int tier) {
        int bait = this.equippedBaitTier();
        if (!isRedTier(bait)) {
            return 1.0;   // 只有红档有额外加成
        }
        return switch (tier) {
            case 4 -> 1.0 + 0.05 * 6.0;    // 金鱼 +5%
            case 5 -> 1.0 + 0.018 * 6.0;   // 红鱼 +1.8%
            default -> 1.0;
        };
    }

    /** 是不是红档 —— 归一之后红只有 5 一个值（见 {@link #equippedBaitTier()}）。 */
    private static boolean isRedTier(int tier) {
        return tier == 5;
    }

    /**
     * 对抗中的一行动作栏提示。
     *
     * <p>为什么需要：整个对抗机制（鱼往哪边拉、你是正拉还是反拉、张力到哪一档、
     * 鱼还剩下多少体力、有没有进疲劳期）全都只存在于数值里，
     * 玩家光看 HUD 那两条是读不出来的。之前不做任何文字反馈，
     * 结果就是"我按了 A/D 但不知道有没有用"。
     *
     * <p>格式：{鱼方向} {正拉/反拉/中立} · 距离 · 鱼状态 · 鱼力 {百分比}{疲劳}
     *
     * <p>⚠ 2026-10-16 用户定案：**去掉「收线中/放线」与「张力 白/黄/红」两段**。
     * 张力条本来就在屏幕上有条子（见 {@code FishingHud.renderTensionBar}），
     * 再在动作栏里用文字重复一遍既占地方又和条子打架；
     * "收线中/放线"则是玩家自己按着的键，写出来等于念他的操作。
     * 保留的那几项都是**条子上看不出来**的：鱼往哪边游、正拉还是反拉、
     * 线还剩多少米、鱼在挣扎还是在歇、鱼力还剩几个百分点。
     */
    public net.minecraft.network.chat.Component fightHint() {
        int fishAxis = this.struggleDir.axis();
        String fishArrow = fishAxis < 0 ? "◀" : fishAxis > 0 ? "▶" : "◆";

        LeverRelation relation;
        if (fishAxis == 0 || this.counterDir == 0) {
            relation = LeverRelation.NEUTRAL;
        } else if (this.counterDir == fishAxis) {
            relation = LeverRelation.WITH_FISH;
        } else {
            relation = LeverRelation.AGAINST_FISH;
        }
        String rel = switch (relation) {
            case WITH_FISH -> "§a正拉";
            case AGAINST_FISH -> "§c反拉";
            case NEUTRAL -> "§7中立";
        };
        String relHint = switch (relation) {
            case WITH_FISH -> "§7(省力)";
            case AGAINST_FISH -> "§7(费力但快)";
            case NEUTRAL -> "§7(垂直)";
        };

        // 显示用百分比：算作"耗尽"时直接显示 0，避免出现
        // "显示 0% 但判定说没耗尽"这种自相矛盾（那正是玩家看到的诡异现象）。
        int staminaPct = this.fishStaminaMax <= 0 ? 0
                : this.isFishTired() ? 0
                : (int) Math.round(100.0 * this.fishStamina / this.fishStaminaMax);
        String fatigue = this.fatigued ? " §b【疲劳·快拉】" : "";

        // ★ 距离显示（用户要求）：**只显示"鱼离玩家多少米"**，不做任何倒计时。
        //   秒数会让玩家误以为"时间到了鱼就跑"，那是之前的设计缺陷。
        //   现在唯一的失败条件是断线（张力红线），距离只表示"线还剩多少"。
        //   超过鱼线设计长度的 80% 时标红警示——那才是鱼快要逃了。
        double lineMax = this.maxDistance;
        double toGo = Math.max(0.0, this.distance - (this.castDistance * 0.25 + 2.0));
        String distColor = this.distance >= lineMax * 0.8 ? "§c" : "§f";
        String lineHint = distColor + String.format("%.1f", this.distance) + "米"
                + " §8/ " + String.format("%.0f", lineMax) + "米";
        if (this.isFishTired()) {
            lineHint += " §e(收 " + String.format("%.1f", toGo) + "米)";
        }

        // 鱼的当前状态：只描述它在干什么，不给倒计时。
        // 鱼在挣扎 → 稳住别硬拉；鱼在歇 → 赶紧抢线；轮次用尽 → 放心收线。
        String phase;
        if (this.roundsExhausted && !this.fishStruggling) {
            phase = "§b鱼已力竭";
        } else if (this.fishStruggling) {
            phase = "§c挣扎中 §8" + Math.max(1, this.roundsDone) + "/" + this.maxRounds + "轮";
        } else {
            phase = "§b鱼在歇 §e←抢线";
        }

        return net.minecraft.network.chat.Component.literal(
                "§6鱼 " + fishArrow + "  " + rel + relHint
                        + "  §7|  " + lineHint
                        + "  §7|  " + phase
                        + "  §7|  鱼力 " + staminaPct + "%" + fatigue);
    }

    public StruggleDir struggleDir() {
        return struggleDir;
    }

    public boolean sprinting() {
        return sprinting;
    }

    public int redZoneTicks() {
        return redZoneTicks;
    }

    /**
     * 本次对抗**实际生效**的张力上限。
     *
     * <p>= 竿线合成的上限 × 韧性保护倍率（每级 +8%，**只对路亚竿生效**）。
     * 这个方法是唯一的出口：断线判定、白/黄区划分、客户端张力条读的都是它，
     * 所以倍率加在这里就"实际生效"了 —— 用户原话里特意强调了"在实际生效时"。
     */
    public double tensionCeiling() {
        return rodStats.tensionCeiling()
                * (enchRodIsLure ? 1.0 + RodEnchants.REINFORCED_PER_LEVEL * enchReinforced : 1.0);
    }

    /** 白区上限 = 张力上限 × 配置的比例（默认 55%）。 */
    public double whiteMax() {
        return this.tensionCeiling() * Tuning.whiteZoneRatio();
    }

    /** 黄区上限 = 张力上限 × 配置的比例（默认 82%）。 */
    public double yellowMax() {
        return this.tensionCeiling() * Tuning.yellowZoneRatio();
    }

    // =================================================================== 蓄力
    public void beginCharge(long gameTick) {
        this.state = State.CHARGING;
        this.chargeStartTick = gameTick;
        this.castPower = 0.0;
    }

    /** 蓄力进度 0~1（客户端也用这个值画蓄力条）。 */
    public double chargeProgress(long gameTick) {
        double seconds = (gameTick - chargeStartTick) / 20.0;
        return Math.min(1.0, seconds / 1.6);   // 1.6s 蓄满（路亚竿基准）
    }

    // =================================================================== 抛投
    /**
     * 抛投：参数化抛物线。
     *
     * <p><b>物理模型</b>（docs/02-钓鱼小游戏机制.md §2.2）：以固定仰角 32° 抛出，
     * 初速 v0 随蓄力增长，重力 g = 0.05 格/tick²。水平射程
     * {@code R = v0²·sin(2θ)/g}，在 θ=32° 时 {@code sin(64°)≈0.899}。
     *
     * <p>⚠️ 这里必须把 v0 **分解**成水平与垂直两个分量，而不是把射程当作水平速度：
     * 分成 {@code v0·cosθ} 与 {@code v0·sinθ} 后，实体在 {@code 2·v0·sinθ/g} tick 后
     * 自然落回出发高度，水平恰好前进 R 格。
     */
    /**
     * 抛投（**按视角角度**，取代原来的按蓄力力度）。
     *
     * <p>水平方向仍然是玩家看的方向；垂直方向用给定的发射仰角与初速度。
     * 距离已经由 {@code FishingManager.solveSpeed} 反解好，这里只负责把浮漂扔出去。
     *
     * @param level      服务端世界
     * @param speed      初速度（格/tick）
     * @param launchDeg  发射仰角（度，正值向上）
     */
    /**
     * 精准入洞（用户 2026-10-08 定案，docs/33 §8.3）：鱼竿右键冰洞时**不走抛物线**，
     * 直接把浮漂放到洞中心的水面上。
     *
     * <p>为什么必须单开这条路：冰洞只有**一格宽**，用抛物线去瞄一格宽的洞基本靠运气 ——
     * 用户实测原话："即使准心对准冰洞方块也不会将浮漂投进去"。
     * 洞本身就是个**已知落点**，没有理由再让玩家去算角度。
     *
     * <p>落点取水面：冰洞方块在冰那一层（它的下面是水），所以浮漂放在
     * {@code hole.y - 1 + 0.9} 附近 —— 落在**水方块**里而不是洞里，
     * 这样浮漂自己的"碰到水"判定和 {@code WaterBody.detect} 都按正常路径走 ✓
     */
    public void castIntoIceHole(ServerLevel level, net.minecraft.core.BlockPos hole) {
        this.state = State.CASTING;

        Vec3 center = new Vec3(hole.getX() + 0.5, hole.getY() - 1 + 0.9, hole.getZ() + 0.5);

        BobberEntity entity = new BobberEntity(
                com.whitecobalt.anglertackle.registry.ModEntities.BOBBER.get(), level);
        entity.setOwner(this.player);
        entity.setPos(center.x, center.y, center.z);
        entity.setDeltaMovement(0.0, -0.01, 0.0);   // 只往下压一点点，别飞出去
        entity.setYRot(this.player.getYRot());
        level.addFreshEntity(entity);

        // ★ 记作 12 米（用户 2026-10-08："冰钓固定 6 米左右容易拉，建议 12 米"）——
        //   洞就在脚边，按真实距离算会吃"抛得近→出鱼差"的惩罚 ✗，玩家会觉得洞里只有小鱼。
        //   12 米是中距离（抛投设计是 低头 8 / 平视 16 / 抬头 24），
        //   于是洞里既不吃近距离惩罚、也拿不到远投的额外好处 ✓
        this.bobber = entity;
        this.castDistance = IceFishing.iceHoleCastDistance();
        // ★ 打上标记：beginWaiting 那一行"按真实距离重算"必须跳过这一竿（见字段注释）
        this.iceHoleCast = true;

        // 抛投音只由服务端在这里发一次：
        //   台钓 → 用**路亚的抛竿声**（lure_cast）；用户 2026-10-30 允许"台钓部分音效使用路亚那边的"，
        //          目前只放开了这一条，其余（收线/力竭/放线/上鱼）台钓仍然不播。
        //   路亚 → 这里不播：客户端 ReelSounds 会播同一份素材（两边都播会糊成两层）。
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            level.playSound(null, this.player.blockPosition(),
                    com.whitecobalt.anglertackle.registry.ModSounds.LURE_CAST.get(),
                    SoundSource.NEUTRAL, 0.9f, 1.0f);
        }

        FishingManager.LOGGER.info("[垂纶] 精准入洞：浮漂直接放在洞心 ({}, {}, {})",
                hole.getX(), hole.getY() - 1, hole.getZ());
    }

    public void castProjectile(ServerLevel level, double speed, double launchDeg) {
        this.state = State.CASTING;

        final double angle = Math.toRadians(launchDeg);
        final double vHorizontal = speed * Math.cos(angle);
        final double vVertical = speed * Math.sin(angle);

        Vec3 look = this.player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-6) {
            horizontal = new Vec3(0, 0, 1);                            // 垂直向下看时的兜底方向
        }
        horizontal = horizontal.normalize();

        // 从眼睛稍微往前上方出竿，避免浮漂生在自己身体里
        Vec3 origin = this.player.getEyePosition().add(0, 0.2, 0);

        BobberEntity entity = new BobberEntity(
                com.whitecobalt.anglertackle.registry.ModEntities.BOBBER.get(), level);
        entity.setOwner(this.player);
        entity.setPos(origin.x, origin.y, origin.z);
        entity.setDeltaMovement(
                horizontal.x * vHorizontal,
                vVertical,
                horizontal.z * vHorizontal);
        entity.setYRot(this.player.getYRot());
        level.addFreshEntity(entity);

        this.bobber = entity;
        this.castDistance = 0.0;
        this.iceHoleCast = false;      // 普通抛投：距离按浮漂真实落点算（beginWaiting 会重算）

        // 抛投音只由服务端在这里发一次：
        //   台钓 → 用**路亚的抛竿声**（lure_cast）；用户 2026-10-30 允许"台钓部分音效使用路亚那边的"，
        //          目前只放开了这一条，其余（收线/力竭/放线/上鱼）台钓仍然不播。
        //   路亚 → 这里不播：客户端 ReelSounds 会播同一份素材（两边都播会糊成两层）。
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            level.playSound(null, this.player.blockPosition(),
                    com.whitecobalt.anglertackle.registry.ModSounds.LURE_CAST.get(),
                    SoundSource.NEUTRAL, 0.9f, 1.0f);
        }

        FishingManager.LOGGER.info("[垂纶] 浮漂已生成：初速 {} 仰角 {}° → vH={} vV={}",
                String.format("%.3f", speed), String.format("%.1f", launchDeg),
                String.format("%.3f", vHorizontal), String.format("%.3f", vVertical));
    }

    /**
     * 抛投（旧的按力度版本，保留给自动化测试与理论射程断言用）。
     *
     * <p>⚠️ 这里必须把 v0 **分解**成水平与垂直两个分量，而不是把射程当作水平速度：
     * 分成 {@code v0·cosθ} 与 {@code v0·sinθ} 后，实体在 {@code 2·v0·sinθ/g} tick 后
     * 自然落回出发高度，水平恰好前进 R 格。
     */
    public void cast(ServerLevel level, double power) {
        this.castPower = Math.min(1.0, Math.max(0.0, power));
        this.state = State.CASTING;

        final double angle = Math.toRadians(32.0);
        final double v0 = 1.2 + 1.6 * this.castPower;                 // 格/tick（约 24–56 格/秒）
        final double vHorizontal = v0 * Math.cos(angle);
        final double vVertical = v0 * Math.sin(angle);

        Vec3 look = this.player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0, look.z);
        if (horizontal.lengthSqr() < 1.0e-6) {
            horizontal = new Vec3(0, 0, 1);                            // 垂直向下看时的兜底方向
        }
        horizontal = horizontal.normalize();

        Vec3 origin = this.player.getEyePosition().add(0, 0.25, 0);

        BobberEntity entity = new BobberEntity(
                com.whitecobalt.anglertackle.registry.ModEntities.BOBBER.get(), level);
        entity.setOwner(this.player);
        entity.setPos(origin.x, origin.y, origin.z);
        entity.setDeltaMovement(
                horizontal.x * vHorizontal,
                vVertical,
                horizontal.z * vHorizontal);
        entity.setYRot(this.player.getYRot());
        level.addFreshEntity(entity);

        this.bobber = entity;
        this.castDistance = 0.0;
        this.iceHoleCast = false;      // 同 castProjectile：普通抛投不吃上一竿的"冰洞"状态

        // 抛投音只由服务端在这里发一次：
        //   台钓 → 用**路亚的抛竿声**（lure_cast）；用户 2026-10-30 允许"台钓部分音效使用路亚那边的"，
        //          目前只放开了这一条，其余（收线/力竭/放线/上鱼）台钓仍然不播。
        //   路亚 → 这里不播：客户端 ReelSounds 会播同一份素材（两边都播会糊成两层）。
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            level.playSound(null, this.player.blockPosition(),
                    com.whitecobalt.anglertackle.registry.ModSounds.LURE_CAST.get(),
                    SoundSource.NEUTRAL, 0.9f, 1.0f);
        }

        FishingManager.LOGGER.debug("[垂纶] 抛投 power={} v0={} vH={} vV={}",
                String.format("%.2f", this.castPower), String.format("%.2f", v0),
                String.format("%.2f", vHorizontal), String.format("%.2f", vVertical));
    }

    /** 理论射程（格）——仅用于调试与自动化测试断言。 */
    public static double theoreticalRange(double power) {
        double v0 = 1.2 + 1.6 * Math.min(1.0, Math.max(0.0, power));
        return v0 * v0 * Math.sin(Math.toRadians(64.0)) / 0.05;
    }

    // =================================================================== 咬钩
    /**
     * 冰钓判据的取样点 = **浮漂落点**（没有浮漂时退回玩家脚下）。
     *
     * <p>和 {@code /atl water} 用的是同一口径：会话中"这片水"指的就是浮漂那一格。
     */
    private net.minecraft.core.BlockPos icePos() {
        return this.bobber != null ? this.bobber.blockPosition() : this.player.blockPosition();
    }

    private ServerLevel iceLevel() {
        return this.player.level() instanceof ServerLevel sl ? sl : null;
    }

    /** 浮漂落水，开始等待咬钩。 */
    public void beginWaiting(int depth, boolean flowing) {
        this.state = State.WAITING;

        // 基础等待 = 最短 + 随机量（都来自 config）
        var cfg = com.whitecobalt.anglertackle.Config.INSTANCE;
        double waitMin = cfg.biteMinWait.get();
        double waitMax = cfg.biteMaxWait.get();

        // ★ 冰钓（用户 2026-10-08 拍板，docs/33 §8.4）：**覆冰非高山群系且没凿洞**时
        //   咬钩等待 ×openIceBiteTimeMultiplier（默认 1.5 = 延长 50%）；洞内**不变**。
        //   判据走 IceFishing 那一处 —— 爆率/线损用的是同一个函数，
        //   免得出现"咬钩时间认了、爆率没认"这种只错一半的状态。
        double iceBite = IceFishing.biteTimeMultiplier(iceLevel(), icePos());
        if (iceBite != 1.0) {
            waitMin *= iceBite;
            waitMax *= iceBite;
        }

        // ★ 打窝缩时间（用户定案 2026-09-30：没打窝 10~24 秒，打窝后 6~12 秒）
        //
        // 用窝料自己的 timeMultiplier 当倍率，而不是写死一个 6~12：
        // 四种窝料的 0.75 / 0.65 / 0.55 / 0.45 本来就是一条"越高级咬得越快"的阶梯，
        // 写死就等于把这四个数一起废掉，四种窝在手感上变得没有区别。
        // 实际区间（基准 10~24 秒）：
        //     谷物 ×0.75 →  7.5~18.0 秒
        //     腥味 ×0.65 →  6.5~15.6 秒
        //     甜香 ×0.55 →  5.5~13.2 秒   ← 中间两档正落在用户给的 6~12 附近
        //     秘制 ×0.45 →  4.5~10.8 秒
        //
        // ⚠ 窝子的判定必须在这里**再查一次**，不能复用咬钩时那次：
        //   等待是浮漂落水那一刻算的，而咬钩在几秒之后 —— 两者之间窝子可能刚过期，
        //   也可能玩家又补了一个。用错时刻的判定会让"打了窝却没缩短"变成偶发问题。
        com.whitecobalt.anglertackle.entity.ChumEntity chum = null;
        if (this.bobber != null && this.player.level() instanceof ServerLevel chumLevel) {
            chum = ChumManager.get().findChumAt(chumLevel, this.bobber.blockPosition());
        }
        double chumTime = chum != null ? chum.timeMultiplier() : 1.0;

        // ★ 水体判定：这里（浮漂刚落水）算一次并钉死。
        //   放到咬钩时再算的话，浮漂可能已被水流推动、或者玩家重抛过，
        //   同一次抛投会算出不同水体，鱼的来源就不可复现了。
        if (this.bobber != null && this.player.level() instanceof ServerLevel waterLevel) {
            this.water = WaterBody.detect(waterLevel, this.bobber.blockPosition());
            // ★ 修饰同一个方块、同一时机算（理由见上面的注释与字段声明）。
            this.waterMods = WaterBody.modifiers(waterLevel, this.bobber.blockPosition(), this.water);
            // ★ 冰洞里：**在覆冰非高山群系里**才算"冰下"（用户 2026-10-08 定案，docs/33 §8.3）。
            //
            //   ⚠ 这里必须**同时**看洞和群系 —— 只看洞的话，"把冰运到平原、挖个洞"
            //     就能钓北极鳕 ✗（用户 2026-10-08 亲自指出："如果有人运冰到非极地群系你又如何"）。
            //   洞只是**入口**：它决定"你从哪下钩"，而"这里是什么水"仍然由群系说了算 ✓
            //   （和现实一致：温带池塘里冻的那块冰不会把水变成北冰洋）。
            //   洞内那三条数值照旧生效（咬钩时间不变 / 金红 +1% / 线损 ×2）——
            //   它们是"从洞里下钩"的代价与好处，与群系无关 ✓
            if (IceFishing.inIceHole(waterLevel, this.bobber.blockPosition())
                    && WaterBody.iceFishingBiome(waterLevel, this.bobber.blockPosition())) {
                this.waterMods |= WaterBody.M_POLAR;
            }
            // ★ 环境（时段/天气/月相）与水体同一时机取一次 —— **整段会话复用**。
            //   每 tick 重算的话，跨过时段边界（比如黄昏→夜晚）时同一竿的判定会前后不一致，
            //   玩家看到的就是"浮漂还没动，鱼池已经换了"。
            this.env = Environment.now(waterLevel);
            // ★ 干旱耐受（docs/28 §3）：效果语义是"任何时候都享受下雨带来的加成"，
            //   而本 mod 在钓鱼侧的"下雨加成"就是环境里的天气维（雨天活跃的鱼组 +10 个百分点）。
            //   所以持有它时把天气钉成降雨 —— 只改喂给鱼池的这一份快照，不改采样的真实值。
            Environment realEnv = this.env;
            this.env = DishEffectHooks.applyDroughtTolerance(this.player, this.env);
            if (this.env != realEnv) {
                FishingManager.LOGGER.info("[垂纶] 干旱耐受：天气按【降雨】计入鱼池（实际 {}）",
                        Environment.weatherName(realEnv.weather()));
            }
        } else {
            this.water = null;
            this.waterMods = 0;
            this.env = null;
        }

        double base = waitMin + this.player.getRandom().nextDouble() * waitMax;

        // ★ 台钓专属减时（用户定案 2026-10-02）：竿 −30% 与饵料那一档**加算**。
        //   路亚竿不吃这一项 —— 用户只说了台钓，而且饵料槽本来就是台钓的槽。
        //   顺序：基础 → 减时 → 打窝倍率 → 流动 ×1.3，最后夹进 [floor, hardCap]。
        //   （减时放在打窝**之前**只是为了让"两个百分比先加在一起"读起来清楚，
        //     乘法的结合律保证它和放在后面完全等价。）
        boolean floatRod = rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT;
        // ★ 夜间台钓减免（环境系统里唯一动咬钩节奏的一项，见 docs/24 §4.4）。
        //   与竿/饵的减时**加算**，最后同样被 floorSeconds 与硬上限夹住。
        boolean night = this.env != null && this.env.timeSlot() == Environment.NIGHT;
        double rodCut = floatRod ? BiteTiming.rodCutPercent() : 0.0;
        double baitCut = floatRod ? this.equippedBaitCutPercent() : 0.0;
        double nightCut = BiteTiming.nightCutPercent(floatRod, night);
        double cut = BiteTiming.totalCut(floatRod, baitCut, night);
        base *= (1.0 - cut);
        base *= chumTime;
        if (flowing) {
            base *= 1.3;
        }
        // ★ 下限（用户定案："配合打窝至多做到 3 秒一条鱼"）：
        //   台钓 −51%（红饵）+ 秘制窝 ×0.45 之后，最短那一杆会算到 2.2 秒。
        //   没有下限就成了"越堆越快、没有尽头"，3 秒是用户定的地板。
        base = Math.max(base, BiteTiming.floorSeconds());
        // 硬上限：玩家扛不住长时间干等
        base = Math.min(base, cfg.biteHardCap.get());
        this.waitTicksTotal = (int) Math.round(base * 20.0);
        this.waitTicks = 0;

        FishingManager.LOGGER.info("[垂纶] 等待咬钩：{}{}{} → 实得 {} 秒（{}）｜水体={}｜环境={}",
                String.format("%.0f~%.0f", waitMin, waitMin + waitMax),
                cut > 0 ? String.format("，减时 −%.1f%%（竿 %.1f + 饵 %.1f + 夜 %.1f）",
                        cut * 100.0, rodCut, baitCut, nightCut) : "",
                chum != null ? " ×" + String.format("%.2f", chumTime) : "",
                String.format("%.1f", base),
                base <= BiteTiming.floorSeconds() + 1e-9
                        ? "已到下限秒数"
                        : (chum != null ? "窝子内，已缩短" : "无窝子加成"),
                this.water == null ? "未判定" : this.water.cn()
                        + (this.waterMods == 0 ? "" : " + " + WaterBody.describeModifiers(this.waterMods)),
                this.env == null ? "未取样" : this.env.describe());

        // ★ 冰洞精准入洞**不许**被真实距离覆盖（用户 2026-10-08 报的 bug）：
        //   "钓鱼在冰洞上钩后依旧显示 6 米左右" —— 洞就在脚边，玩家到浮漂的真实距离约 6 米，
        //   而 castIntoIceHole 有意把它记作 12 米（IceFishing.iceHoleCastDistance），
        //   为的是不吃"抛得近 → 出鱼差"的惩罚。这一行原来无条件覆盖，把那个意图整个冲掉 ✗。
        if (!this.iceHoleCast) {
            this.castDistance = this.player.position().distanceTo(this.bobber.position());
        }
    }

    /** 进入提竿窗口：★ 这一刻立即扣耐久（与后续成败无关）。 */
    public void enterHookWindow() {
        this.state = State.HOOK_WINDOW;
        this.bitePhase = 3;
        if (this.bobber != null) {
            this.bobber.setBitePhase((byte) 3);
        }

        // ★ 咬钩的瞬间就把"是什么鱼"定下来。
        // 这样提竿后钓上来的就是咬钩的那一条，而不是上鱼时另抽一次——
        // 后者会让玩家觉得"咬钩的是小鱼、拉上来变大鱼"。
        if (this.hookedSpecies == null) {
            double maxWeight = maxWeightKg();
            boolean testLure = this.testLureEquipped();
            int mt = maxTier();
            int mn = baitMinTier();
            // ⚠ 这一行**必须打在窝料抬下限之后**（原先是打在这里，见下面 roll 之前那条）。
            //   真实误导（2026-10-02）：日志里"抽取输入：…咬钩下限=0"与同一次调用的
            //   "抽取结果：…个体下限=2"打架 —— 因为窝料把 mn 从 0 抬到了 2，
            //   而这条日志还在用抬之前的值。查了两轮才定位，白花时间。

            // ★ 打窝加成（用户要求"让玩家知道圈里的浮漂吃到加成"）。
            //
            // ⚠ 这里原先**没有接**：ChumManager.findChumAt() 写好了却从来没人调用，
            //   所以打窝除了冒泡之外毫无作用 —— 玩家反馈"打窝前后不知道有没有效果"，
            //   根因就是这个（不是视觉问题，是真的没生效）。
            //
            // 加成方式：窝子在半径内 → 把"金+红"占比按窝子的 timeMultiplier 放大。
            //   水面窝 1.5 / 2.0 / 2.5，沉底窝 0.75（沉底窝是"慢"而不是"多"，见 ChumItem 注释）。
            //   只对台钓生效（路亚没有"窝子"概念，拟饵靠游动诱鱼）。
            double chumBoost = 1.0;
            if (this.bobber != null && this.player.level() instanceof ServerLevel chumLevel) {
                com.whitecobalt.anglertackle.entity.ChumEntity chum =
                        ChumManager.get().findChumAt(chumLevel, this.bobber.blockPosition());
                if (chum != null) {
                    // ★ 必须用 rarityMultiplier()，**不是** timeMultiplier()。
                    //   后者是等待时间倍率（0.45~0.75，全都 < 1），拿它乘占比
                    //   会让越好的窝越不出好鱼。日志实测过：
                    //     "打窝加成：倍率 x0.45 → 金红占比 1.4%"
                    //   —— 玩家报"打窝影响不明显"的真凶。
                    chumBoost = chum.rarityMultiplier();

                    // ★★ 窝料还决定**咬钩品质下限**（用户 2026-10-01 定的规则）：
                    //      白窝 / 绿窝 → 不限制（0）
                    //      蓝窝 → 绿以上（1）
                    //      紫窝 → 蓝以上（2）
                    //    即"档位 ≥ 2 时，下限 = 档位 - 1"。
                    //
                    //   为什么必须单独接这一条：在那之前窝子**只**放大金红占比（hvPct），
                    //   低档鱼只是被稀释、并没有被排除 —— 玩家实测反馈
                    //   "使用秘制配方饵打窝还是会出蓝鱼"，根因就在这。
                    //
                    //   ⚠ 它连**鱼种**一起过滤（buildPool 里 maxTier < minTier 的鱼种整条剔掉），
                    //     所以下限要夹在竿本身的档位上限 mt 之内，否则圈里会没鱼可咬。
                    int chumFloor = chum.getTier() >= 2 ? chum.getTier() - 1 : 0;
                    if (chumFloor > mn) {
                        mn = Math.min(chumFloor, mt);
                        FishingManager.LOGGER.info(
                                "[垂纶] 打窝下限：窝料档 {} → 咬钩下限提到 {}（钳在竿上限 {} 之内）",
                                chum.getTier(), mn, mt);
                    }
                }
            }
            // ★ 金红加成**两种竿都吃**（用户 2026-10-01 定案："我的金红加成本来就是写给路亚的"）。
            //
            //   原来这里按竿型分叉（台钓才给 hvPct、路亚恒 −1），于是路亚竿上
            //   镀金竞技（红档，700 鱼币的顶配）跟白档饵一样，金红占比毫无变化。
            //   equippedBaitTier() 本身已经按竿型取好槽位（台钓取 bait、路亚取 lure），
            //   值也统一归一成 0 起算档位，所以这里**不需要**再分叉。
            double hvPct = this.highValueBonus(this.equippedBaitTier());
            // 习性词条：拟饵认的词条命中这条鱼 → 权重 ×1.10（不叠加、不碰金红占比）
            int habitMask = this.equippedLureHabitMask();
            // hvPct < 0 表示"这种饵不调占比"（例如路亚、或没有高档饵）；
            // 此时窝子仍然要给一个可见的收益，所以用一个保底基准 3% 再乘倍率。
            if (chumBoost != 1.0) {
                hvPct = (hvPct > 0 ? hvPct : 3.0) * chumBoost;
        // 高质吸引：每级 +1 个百分点（用户："每级使得金红品质鱼上鱼概率提升1%"）。
        // ⚠ 必须夹在 0~100：加过头会让金红占比超过 100%，按权重反解金红那套会崩
        //   （tools/check-enchantments.js 会离线复算这条）。
        hvPct = Math.max(0.0, Math.min(100.0,
                hvPct + RodEnchants.highQualityExtraPercent(this.enchHighQuality)));
            }
            // ★ 冰钓：洞内 **+1 个百分点**、覆冰非高山群系（非洞）**−1 个百分点**，
            //   都是**加算**（用户定案，docs/33 §8.4）。
            //   ⚠ 两个口径上的坑：
            //     ① 配置项的单位是"比例"（0.01），而 hvPct 的单位是"百分数"（10 = 10%）
            //        → 必须 ×100 换算，否则 +1% 会变成 +0.01%（等于没加，还查不出来）；
            //     ② 只在 **hvPct > 0**（饵料确实在控占比）时才动它 —— 路亚/低档饵的 hvPct 是 0，
            //        那是"**不调**占比"的语义，+1% 会把它变成"强行 1%"，性质完全不同。
            double iceHvDelta = IceFishing.highValueDelta(iceLevel(), icePos());
            if (iceHvDelta != 0.0 && hvPct > 0) {
                hvPct = Math.max(0.0, Math.min(100.0, hvPct + iceHvDelta * 100.0));
                FishingManager.LOGGER.info("[垂纶] 冰钓：金红总占比 {}{} 个百分点 → {}%",
                        iceHvDelta > 0 ? "+" : "", String.format("%.2f", iceHvDelta * 100.0),
                        String.format("%.2f", hvPct));
            }
            FishingManager.LOGGER.info("[垂纶] 打窝加成：倍率 x{} → 金红占比 {}%",
                    String.format("%.2f", chumBoost),
                    hvPct > 0 ? String.format("%.1f", hvPct) : "不调整");

            // ★ 钓获类菜品效果（docs/28 §3）：渔运 / 爆口 / 空军。
            //   它们是**独立的一层**，不并进上面的 hvPct ——
            //   渔运加的是"占比目标"，而 hvPct < 0（路亚、低档饵不承诺占比）时
            //   要先按当前池子算出占比再加，那只能发生在 buildPool 里面；
            //   爆口/空军乘的是单条鱼的权重，位置在"解完 k、取整之前"。
            //   所以这里只把它们打包传下去，日志另打一行（上面那行 hvPct 是饵料承诺，不含渔运）。
            FishSpecies.RollBonus rollBonus = DishEffectHooks.rollBonus(this.player);
            // 铅锤（仅路亚）：把四层倍率挂进 RollBonus，由 buildPool 在"解 k 之后"统一乘。
            // 台钓不挂 —— 用户定案"铅锤仅路亚可用"，结构上不让它影响台钓。
            if (rodKind() == FishSpecies.RodKind.LURE) {
                rollBonus = rollBonus.withLayers(
                        com.whitecobalt.anglertackle.fishing.RodModManager
                                .sinkerLayerBonus(this.player.getMainHandItem()));
            }
            String bonusReport = DishEffectHooks.describeRollBonus(this.player);
            if (!bonusReport.isEmpty()) {
                FishingManager.LOGGER.info("[垂纶] 菜品效果：{}", bonusReport);
            }

            // 两个修正是**分开传**的，不能相乘：
            //   ① hvTargetPct  —— 台钓饵料把"金+红鱼种"的总占比精确拉到 3%~10%
            //                     （在 buildPool 里解 k 值，需要真实池子才算得准）
            //   ② baitTierBonus —— 红饵/红拟饵对金鱼 +5%、红鱼 +1.8%（按档位的权重倍率）
            // 精准猎获（用户："每级使得鱼饵的上鱼类型权重（不影响稀有度的那种）额外提升2%（加算）"）
        // —— 只加**词条(类型)加权**，不动金红占比（那是 hvPct，另一条路）。
        // 诊断：这条链上有四个变量（测试拟饵 / 竿档位上限 / 饵料下限 / 抽到的鱼种），
        // 光看结果推不出来 —— 把**最终生效**的取值打出来。
        // ⚠ 位置很要紧：必须在这里（窝料抬完下限、roll 之前），
        //   打在更前面就会显示抬升前的旧值（2026-10-02 被它误导了两轮排查）。
        FishingManager.LOGGER.info(
                "[垂纶] 抽取输入：测试拟饵={} 竿档位上限={} 咬钩下限={}（含打窝）竿型={} 承重={} 环境={}",
                testLure, mt, mn, rodKind(), String.format("%.0f", maxWeight),
                this.env == null ? "未取样" : this.env.debugToken());
        this.hookedSpecies = FishSpecies.roll(
                    this.player.getRandom(), rodKind(), mt, mn,
                    maxWeight, castRange(),
                    this::baitTierBonus,
                    hvPct,
                    // ★ 测试拟饵：要求鱼种**能达到**红档，把鲻鱼那类"最高只到蓝"的
                    //   鱼种整个挡在池外。没有这一步，"必出红鱼"就不成立——
                    //   因为 rollIndividual 有个兜底：鱼种够不到下限就抬到它自己的最高档，
                    //   于是鲻鱼(1~2)会被抬成蓝鱼。玩家报的"测试拟饵居然出蓝货"就是这个。
                    testLure ? 5 : 0,
                    this.water,     // ★ 按落点水体筛鱼池
                    this.waterMods, // ★ 落点命中的水域修饰（极地 / 高寒湖 / 冷水溪流）
                    habitMask,   // ★ 拟饵认的习性词条（命中鱼种权重 ×1.10）
                    // ★【精准猎获】：每级把词条加权的百分点 +2（加算，不动上面那条稀有度占比）
                    RodEnchants.habitBonusExtraPercent(this.enchPreciseHunt),
                    this.env,   // ★ 时间/天气/月相（只改抽签权重，不改等待）
                    rollBonus); // ★ 菜品效果：渔运（占比目标）/ 爆口·空军（单条鱼权重）
            if (habitMask != 0) {
                FishingManager.LOGGER.info("[垂纶] 词条加成：拟饵 {} 认【{}】→ 命中鱼种权重 ×{}",
                        this.equippedLureId(), FishHabits.describe(habitMask),
                        String.format("%.2f", (FishHabits.BONUS_PERCENT
                                + RodEnchants.habitBonusExtraPercent(this.enchPreciseHunt)) / 100.0));
            }
            // ★ 环境报告（见 docs/24）：这一竿的环境到底吃到了几个百分点。
            //   平时只进日志；开了 environment.logToChat 才打到聊天栏 ——
            //   调参期这个数字比"感觉鱼变多了"有用得多，而且它是**唯一**能验证
            //   "上界真的被遵守了"的手段（占比增量必须 ≤ 配置的 delta）。
            String envReport = FishSpecies.lastEnvReport();
            if (!envReport.isEmpty()) {
                FishingManager.LOGGER.info("[垂纶] 环境加成：{}", envReport);
                if (Tuning.envLogToChat()) {
                    this.player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§7[环境] §f" + envReport), false);
                }
            }
            // ★ 测试拟饵：把个体也钉成红档。
            //   只放开 maxTier() 是不够的——鱼种进池之后个体品质是按体重 roll 的，
            //   大多数个体仍会落在金档，测出来的还是"金鱼的手感"。
            int floor = testLure ? 5 : mn;
            this.hookedData = this.hookedSpecies.rollIndividual(
                    this.player.getRandom(), this.player.level().getGameTime(),
                    this.player.getGameProfile().getName(),
                    floor);   // ★ 个体品质也要卡饵料下限，否则金饵仍会出绿鱼
            FishingManager.LOGGER.info(
                    "[垂纶] 抽取结果：鱼种={} 档位表={} 个体下限={} 体重={} 实得档位={}",
                    this.hookedSpecies.id(), java.util.Arrays.toString(this.hookedSpecies.tiers()),
                    floor, String.format("%.2f", this.hookedData.weightKg()),
                    this.hookedSpecies.tierForWeight(this.hookedData.weightKg()));

            // ★ 咬钩这一刻判超承重，并把结果**钉死**（之后每 tick 烧竿要用）。
            //   不放在 tickFight 里现算：玩家在对抗中可以换手，
            //   现算的话把鱼挪到副手就能白嫖。
            checkOverCapacity();
        }

        // 提竿窗口 = 基础秒数（下限由 config 兜住）。
        // 2026-10-30：鱼钩已删除 ⇒ 这里不再有"配件加成"这一项。
        var hookCfg = com.whitecobalt.anglertackle.Config.INSTANCE;
        double windowSeconds = Math.max(hookCfg.hookWindowMin.get(), hookCfg.hookWindowBase.get());
        this.hookWindowTotal = (int) Math.round(windowSeconds * 20.0);
        this.hookWindowTicks = hookWindowTotal;

        // ★ 咬钩即扣耐久（用户规则）：扣鱼竿 + 扣鱼线 + 扣拟饵。
        //   记下这次扣了多少（{@link #lastBiteCost}），提前收回时按同样的数额退回去。
        applyBiteDurabilityCost();

        // ⚠ 2026-10-29 用户："咬钩用原版音效，稍微高频一点" ⇒ 音高 0.7 → 1.22
        playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.20f, 1.22f);   // 2026-10-29：原版音效再降 20%
        spawnParticles(ParticleTypes.SPLASH, 12);
    }

    /** 这根竿是台钓还是路亚。 */
    public com.whitecobalt.anglertackle.item.RodItem.RodType rodType() {
        ItemStack rod = this.player.getMainHandItem();
        if (rod.getItem() instanceof com.whitecobalt.anglertackle.item.RodItem rodItem) {
            return rodItem.rodType();
        }
        return com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT;
    }

    private FishSpecies.RodKind rodKind() {
        return rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT
                ? FishSpecies.RodKind.FLOAT
                : FishSpecies.RodKind.LURE;
    }

    /** 供 FishingManager 兜底抽鱼时使用（和 rodKind 同义，只是公开）。 */
    public FishSpecies.RodKind rodKindPublic() {
        return rodKind();
    }

    /** 咬钩的那条鱼（可能为 null，比如还没进提竿窗口）。 */
    @Nullable
    public FishSpecies hookedSpecies() {
        return this.hookedSpecies;
    }

    /** 咬钩那条鱼的个体数据（体长/体重/评级）。 */
    @Nullable
    public FishData hookedData() {
        return this.hookedData;
    }

    /**
     * 咬钩瞬间的耐久扣除——**无论最终钓上来、切线还是错过提竿都要扣**。
     *
     * <p>规则来源（用户定义）：鱼线每次咬钩固定 −1；鱼竿按稀有度基础损耗
     * × 材质系数 × Boss 倍率 × 尺寸系数。
     *
     * <p>扣的是鱼竿本体 + 配件堆栈（{@code RodParts} 里的鱼线 / 拟饵）。
     * 扣了多少记在 {@link #lastBiteCost}，供提前收回时原额退回。
     * 渔轮和铅锤在 {@code ModItems.partProps} 里耐久是 0（不设耐久上限），
     * 天然不参与扣除——"有耐久定义的就显示/就消耗"这条规则自动成立。
     *
     * <p>⚠️ <b>顺序很关键（曾经写成错的）</b>：配件耐久存在**数据组件**里，
     * 而 {@code hurtAndBreak} 会**立刻**把物品同步给客户端。
     * 早先的写法是「先 hurtAndBreak（广播出去）→ 再改组件」，
     * 于是广播带的是旧组件，服务端改了但客户端不知道。
     * 表现就是玩家反馈的："上鱼后耐久确实会消耗，但打开背包又回满了" ——
     * 打开容器时服务端把权威状态重推一遍，客户端的本地预测被覆盖回去。
     *
     * <p>现在改成：**先把组件改完，再动鱼竿本体，最后统一显式同步一次**。
     */
    // ================================================================ 超承重烧竿
    //
    // 用户定案："不用做筛选，只要上超承重鱼就清空钓上它的鱼竿的耐久，
    // 或者说如果上鱼检测是超承重鱼就飞快消耗当前鱼竿耐久，不用等钓上来。"

    /**
     * 这条鱼是不是超出了竿的承重。咬钩那一刻就判，之后不再变。
     *
     * <p>存成字段而不是每 tick 现算：承重来自手上那根竿，而玩家在对抗中
     * **可以换手**（换到别的槽位）。每 tick 现算的话，玩家把超重的鱼
     * 挪到副手就能白嫖 —— 判定必须在咬钩那一刻钉死。
     */
    private boolean overCapacity;
    private double overCapacityRatio = 1.0;

    /**
     * 落点水体 —— 咬钩时用哪一套鱼池。
     *
     * <p>在 {@link #beginWaiting} 就算好并存在这里，而不是咬钩时现算：
     * 浮漂落水后可能被水流推走、也可能玩家把竿收回来重抛；
     * 现算的话"同一次抛投在不同时刻算出不同水体"，鱼的来源就变得不可复现。
     * 落水那一刻钉死，和超承重判定是同一个道理。
     *
     * <p>{@code null} = 还没判（会话刚开始）或读不到 —— 此时不筛水体，
     * 退化成"哪里的鱼都行"。宁可给一条普通的鱼，也不要因为读不到群系就不出鱼。
     */
    private WaterBody water;
    /**
     * 落点命中的**水域修饰**掩码（极地 / 高寒湖 / 冷水溪流；0 = 普通水域）。
     *
     * <p>与 {@link #water} 在**同一时机、同一个方块**算一次并钉死 ——
     * 修饰读的是群系高度与基础温度，浮漂被水流推走之后同样会变，
     * 每 tick 现算就会让同一次抛投前后不一致（用户 2026-10-08）。
     */
    private int waterMods;

    /**
     * 环境（时段 / 天气 / 月相）—— 与 {@link #water} 同一时机取一次，**整段会话复用**。
     *
     * <p>见 {@code docs/24-时间天气月相策划.md}。取一次的三个理由：
     * <ol>
     *   <li>跨过时段边界（黄昏→夜晚）时，同一竿的鱼池判定不该变；</li>
     *   <li>抽签权重是"这一竿的输入"，和落点水体一样必须可复现；</li>
     *   <li>日志里打出来的环境要与真正生效的那次一致，否则排查时对不上。</li>
     * </ol>
     *
     * <p>{@code null} = 没取到（会话刚开始、或玩家不在服务端世界）——
     * 此时环境系统整个不参与，退化成"没有时间天气概念"，与实现之前逐字节一致。
     */
    private Environment env;

    /**
     * **咬钩那一刻**取样的环境快照（时段 / 天气 / 月相）。
     *
     * <p>为什么要有这个 getter：称号里的「夜钓客 / 雷雨钓手 / 月满垂纶」要判"**这一竿**是在什么
     * 环境下上的鱼"。上鱼结算发生在遛鱼结束那一刻，而一场遛鱼可以横跨黄昏 ——
     * 那时候再 {@code Environment.now(...)} 取一次，就会出现"这条鱼是白天咬的、却算进夜钓"✗。
     * 所以读**同一份快照**（与习性加成用的是同一个字段，天然同源 ✓）。
     *
     * <p>可能是 {@code null}（没走到取样那一步，例如兜底路径）—— 调用方自己判空。
     */
    public Environment envSnapshot() {
        return this.env;
    }

    /**
     * 这一竿是不是在**冰洞**里（浮漂落点判据，与 {@code IceFishing.inIceHole} 同一处）。
     *
     * <p>同样是给称号用的（「冰钓人」）：判据落在浮漂那一格，没有浮漂就退回玩家脚下 ——
     * 与 {@code icePos()} 逐字一致 ✓。
     */
    public boolean iceHoleCast() {
        if (!(this.player.level() instanceof ServerLevel level)) {
            return false;
        }
        return com.whitecobalt.anglertackle.fishing.IceFishing.inIceHole(level, icePos());
    }

    /**
     * 超承重时每 tick 烧掉多少耐久，按"占最大耐久的比例"算。
     *
     * <p>取 {@code 1/80}：约 **4 秒**烧完一整根新竿。
     * 为什么用比例而不是固定点数：竿的耐久从 2000（木）到 8000（合金），
     * 固定点数会让木竿瞬间没、合金竿挠痒痒 —— 而"超承重"这件事对两种竿
     * 应该一样致命。
     *
     * <p>4 秒是刻意选的：玩家看到提示后还来得及放线让鱼跑掉，
     * 但来不及犹豫太久。太短就变成"没反应过来竿没了"的随机惩罚，
     * 太长则等于没有惩罚。
     */
    private static final double OVER_CAPACITY_BURN_PER_TICK = 1.0 / 80.0;

    /**
     * 超承重的鱼**被拉上岸时**，最少要按"竿耐久的百分之多少 × (超重倍数 − 1)"补扣。
     *
     * <h2>为什么需要它（用户报的问题）</h2>
     * 烧耐久是**每 tick** 扣 {@code max × ratio / 80}，所以正常情况下超重 2 倍的鱼
     * 硬拉 2 秒就把竿赔进去 —— 这套"硬拉还是放线认输"的抉择靠的就是这个。
     *
     * <p>但**如果鱼一下就被拉上来**（钓鱼力够强，体力被秒削光 → 直接进 LANDING），
     * 全程只烧了 1~2 tick，也就是竿耐久的 1~2% —— 玩家完全无感，
     * 惩罚等于没发生。用户的原话："一下就能拉上来所以不扣耐久"。
     *
     * <p>所以上鱼时补一次差额：**按"本该烧掉多少"补齐**。
     * 0.15 的含义：超重 2 倍扣 15%、3 倍 30%、4 倍（上限）45%。
     *
     * <p><b>补扣永远不会把竿弄断</b>（最多留 1 点耐久）。鱼已经拉上来了，
     * 因为"事后补账"而变成"竿断了鱼也没了"是玩家无法预料、也无法接受的。
     * 想让它断，就把这个值调大到 0.5 以上 —— 那时补扣还是会夹在 1 点，不会断。
     */
    private static final double OVER_CAPACITY_LANDING_MIN_FRACTION = 0.15;

    /** 本场超容量**已经**烧掉的耐久，用来算上鱼时要补多少差额。 */
    private int overCapacityBurned = 0;

    /** 判定这一杆有没有超承重（在咬钩定下鱼之后调用）。 */
    private void checkOverCapacity() {
        this.overCapacity = false;
        if (this.hookedData == null) {
            return;
        }
        double cap = maxWeightKg();
        if (cap <= 0) {
            return;   // 0 = 不限
        }
        double kg = this.hookedData.weightKg();
        if (kg <= cap) {
            return;
        }
        this.overCapacity = true;
        // 超得越多烧得越快：刚好超一点给满 4 秒，超一倍只要 2 秒
        this.overCapacityRatio = Math.min(4.0, kg / cap);
        this.player.displayClientMessage(
                net.minecraft.network.chat.Component.translatable(
                                "message.anglertackle.over_capacity",
                                String.format("%.2f", kg), String.format("%.0f", cap))
                        .withStyle(net.minecraft.ChatFormatting.RED), true);
        FishingManager.LOGGER.info(
                "[垂纶] 超承重！{} {}kg > 竿承重 {}kg（超 {} 倍）—— 开始烧竿耐久，放线可保竿",
                this.hookedSpecies == null ? "?" : this.hookedSpecies.id(),
                String.format("%.2f", kg), String.format("%.0f", cap),
                String.format("%.2f", kg / cap));
    }

    /**
     * 每 tick 烧一次竿耐久。返回 {@code true} 表示**竿已经断了**，
     * 调用方应当立刻停止本 tick 的其余逻辑。
     */
    private boolean tickOverCapacityBurn() {
        if (!this.overCapacity) {
            return false;
        }
        ItemStack rod = this.player.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem)) {
            return false;   // 竿不在手上了：不再烧（也不惩罚换手，见 checkOverCapacity 的说明）
        }
        int max = rod.getMaxDamage();
        if (max <= 0) {
            return false;
        }
        int cur = rod.getDamageValue();
        if (cur >= max) {
            return true;    // 已经断了
        }

        // 超得越多，每 tick 烧得越快。
        // 承重保护（用户："每级使得路亚钓获超承重鱼时，耐久削减速度降低20%"）：
        //   每 tick 烧耐久 ×(1 − 20%/级)，只对路亚生效（台钓竿带着这个附魔没用）。
        double perTick = Math.max(1.0, max * OVER_CAPACITY_BURN_PER_TICK * this.overCapacityRatio
                * RodEnchants.overCapacityBurnMultiplier(
                        this.player.getMainHandItem(), this.enchLoadBearing));
        int now = (int) Math.min(max, cur + Math.round(perTick));
        rod.setDamageValue(now);
        this.overCapacityBurned += (now - cur);

        if (now >= max) {
            // 竿断了：鱼跑掉，会话结束。
            // 提示要说清"是因为超承重"，否则玩家只会觉得"莫名其妙竿没了"。
            this.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                                    "message.anglertackle.rod_broken_over_capacity")
                            .withStyle(net.minecraft.ChatFormatting.DARK_RED), false);
            FishingManager.LOGGER.info("[垂纶] 竿被超承重的鱼拉断，鱼跑了");
            FishingManager.get().abort(this, "message.anglertackle.escaped");
            return true;
        }
        return false;
    }

    private void applyBiteDurabilityCost() {
        ItemStack rod = this.player.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem rodItem)) {
            return;
        }
        this.lastBiteCost = null;

        // ---- 1. 先改配件组件（此时还没广播任何东西）----
        // 扣多少由 config 决定（durability.linePerBite / lurePerBite）。
        // 渔轮和铅锤在 ModItems.partProps 里耐久为 0（不设上限），天然不参与。
        var duraCfg = com.whitecobalt.anglertackle.Config.INSTANCE;
        int lineStep = Math.max(0, duraCfg.lineDurabilityPerBite.get());
        // ⚠ 这一项**同时管台钓饵和路亚拟饵**（名字里只有 lure 是历史原因，
        //   改配置键会让玩家已有的 toml 失效，所以没动）。
        int lureStep = Math.max(0, duraCfg.lureDurabilityPerBite.get());
        // 台钓看 bait 槽、路亚看 lure 槽 —— 和 equippedBaitTier() 的取法**必须一致**。
        //
        // 踩过的坑：这里原本**无条件扣 lure 槽**，于是"路亚饵会掉耐久、台钓饵永远不掉"。
        // 用户报的就是这个（"路亚饵料的耐久是可以被消耗的"、"台钓饵希望改成和路亚一样"）。
        // 更坏的是 tier 的取法是分竿型的（equippedBaitTier 里 floatRod ? bait : lure），
        // 所以台钓竿用的是 bait 槽的加成、扣的却是 lure 槽的耐久 —— 两处不一致，
        // 而表现只是"某个槽的耐久莫名其妙在掉"，很难联想到。
        boolean floatRod = rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT;
        int lineCost = 0, lureCost = 0;
        ModDataComponents.RodParts parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts != null) {
            ModDataComponents.RodParts next = parts;
            ItemStack line = parts.lineStack();
            if (lineStep > 0 && !line.isEmpty() && line.getMaxDamage() > 0
                    && line.getDamageValue() < line.getMaxDamage()) {
                ItemStack copy = line.copy();
                int old = line.getDamageValue();
                // ★ 冰钓（用户 2026-10-08 定案，docs/33 §8.4）：**洞内**鱼线磨损
                //   ×iceHoleLineWearMultiplier（默认 2 = 寿命减半）。
                //   ⚠ **只乘鱼线** —— 竿的损耗在下面"第 2 段"里，一个字节都不碰（用户明确"只加鱼线"）。
                //     用户的原话意图："有条件的玩家可以自己挖开一大片水面来避免这种损耗"。
                double iceLineMul = IceFishing.lineWearMultiplier(iceLevel(), icePos());
                int lineWear = iceLineMul == 1.0 ? lineStep
                        : Math.max(0, (int) Math.round(lineStep * iceLineMul));
                int now = Math.min(line.getMaxDamage(), old + lineWear);
                lineCost = now - old;
                // 用光 → 槽位直接清空（用户要求：不要出现"某个饵料已耗尽"这种状态，
                // 而是变成空槽，竿子自然也不再享受它的加成 —— 空槽在 RodStats 里
                // 本来就是 NEUTRAL，不需要另一套"已耗尽"分支）。
                if (now >= copy.getMaxDamage()) {
                    next = next.withLine(ItemStack.EMPTY);
                } else {
                    copy.setDamageValue(now);
                    next = next.withLine(copy);
                }
            }
            // 按竿型取**实际在使用**的那个槽 —— 另一个槽里的东西没被使用，不该掉耐久
            ItemStack lure = floatRod ? parts.baitStack() : parts.lureStack();
            if (lureStep > 0 && !lure.isEmpty() && lure.getMaxDamage() > 0
                    && lure.getDamageValue() < lure.getMaxDamage()) {
                ItemStack copy = lure.copy();
                int old = lure.getDamageValue();
                int now = Math.min(lure.getMaxDamage(), old + lureStep);
                lureCost = now - old;
                if (now >= copy.getMaxDamage()) {
                    // 同上：用光就变空槽
                    next = floatRod ? next.withBait(ItemStack.EMPTY) : next.withLure(ItemStack.EMPTY);
                } else {
                    copy.setDamageValue(now);
                    next = floatRod ? next.withBait(copy) : next.withLure(copy);
                }
            }
            if (next != parts) {
                rod.set(ModDataComponents.ROD_PARTS.get(), next);
            }
        }

        // ---- 2. 再改鱼竿本体 ----
        // 刻意不用 hurtAndBreak：它内部有"耐久扣满就碎裂"以及中途广播的逻辑，
        // 对钓鱼竿我们只想要"扣点耐久"，碎裂与否交给 RodItem.isBroken 判定。
        // 基础损耗来自 config，再乘竿的材质系数（越好的材质越耐磨）。
        int base = Math.max(0, duraCfg.rodDurabilityPerBite.get());
        double material = rodItem.spec().materialFactor();
        int rodCost = Math.max(0, (int) Math.round(base * material));
        if (rodCost > 0 && rod.getMaxDamage() > 0) {
            int old = rod.getDamageValue();
            int now = Math.min(rod.getMaxDamage(), old + rodCost);
            rod.setDamageValue(now);
            rodCost = now - old;   // 实际扣掉的（夹到上限后可能比预期少）
        } else {
            rodCost = 0;
        }

        // ---- 3. 最后统一把权威状态推给客户端 ----
        syncHeldItem();

        this.lastBiteCost = new BiteCost(rodCost, lineCost, lureCost);
        FishingManager.LOGGER.info("[垂纶] 咬钩扣耐久：竿 -{}，线 -{}，饵料 -{}（{}），竿耐久 {}/{}",
                rodCost, lineCost, lureCost, floatRod ? "台钓饵" : "拟饵",
                rod.getMaxDamage() - rod.getDamageValue(), rod.getMaxDamage());
    }

    /**
     * 把主手物品的权威状态强制同步给客户端。
     *
     * <p>为什么需要显式发：主手物品的改动在服务端**不会**自动推给客户端，
     * 客户端平时看到的是它自己的本地预测。一旦服务端的改动没通知到客户端，
     * 两边就会分叉——玩家下次打开容器（会重推权威状态）时才会"露馅"，
     * 表现为"耐久刚才明明扣了，开背包又满了"。
     *
     * <p>用 {@code ClientboundContainerSetSlotPacket} 手动补一刀，
     * 背包菜单（{@code inventoryMenu}）的第 36 号槽就是主手。
     */
    private void syncHeldItem() {
        if (!(this.player instanceof net.minecraft.server.level.ServerPlayer sp)) {
            return;
        }
        try {
            int slot = 36 + sp.getInventory().selected;
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                    sp.inventoryMenu.containerId, sp.inventoryMenu.incrementStateId(), slot,
                    sp.getMainHandItem().copy()));
            // 通知客户端背包已变（触发渲染层刷新）
            sp.inventoryMenu.broadcastChanges();
        } catch (Exception e) {
            // 同步失败不影响玩法（下次开容器仍会重推），只记日志
            FishingManager.LOGGER.warn("[垂纶] 主手物品同步失败：{}", e.toString());
        }
    }

    /** 一次咬钩实际扣掉的耐久（用于提前收回时原额退回）。 */
    private record BiteCost(int rod, int line, int lure) {
    }

    /**
     * 提前收回：把 {@link #applyBiteDurabilityCost()} 刚扣掉的耐久**原额退回**。
     *
     * <p>玩家规则：抛出后立刻右键收回，任何配件和鱼竿的耐久都不消耗。
     * 因为咬钩才扣耐久，而提前收回只在咬钩之前允许，
     * 实际上这个方法只是为了兜住"恰好同一 tick 内咬钩 + 收回"的边界。
     */
    private void refundBiteDurabilityCost() {
        BiteCost cost = this.lastBiteCost;
        this.lastBiteCost = null;
        if (cost == null) {
            return;
        }
        ItemStack rod = this.player.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem)) {
            return;
        }
        // ---- 1. 先退配件组件 ----
        ModDataComponents.RodParts parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts != null) {
            ModDataComponents.RodParts next = parts;
            if (cost.line() > 0 && !parts.lineStack().isEmpty()) {
                ItemStack copy = parts.lineStack().copy();
                copy.setDamageValue(Math.max(0, copy.getDamageValue() - cost.line()));
                next = next.withLine(copy);
            }
            // ⚠ 退耐久必须和扣耐久**对称**：扣的时候按竿型扣 bait 或 lure，
            //   退的时候也得退同一个槽。只退 lure 的话，台钓饵的耐久只增不减
            //   （扣在 bait 上、退在 lure 上，而 lure 槽通常是空的，于是什么也没退）。
            boolean floatRod = rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT;
            ItemStack bait = floatRod ? parts.baitStack() : parts.lureStack();
            if (cost.lure() > 0 && !bait.isEmpty()) {
                ItemStack copy = bait.copy();
                copy.setDamageValue(Math.max(0, copy.getDamageValue() - cost.lure()));
                next = floatRod ? next.withBait(copy) : next.withLure(copy);
            }
            if (next != parts) {
                rod.set(ModDataComponents.ROD_PARTS.get(), next);
            }
        }
        // ---- 2. 再退鱼竿本体 ----
        if (cost.rod() > 0 && rod.isDamaged()) {
            rod.setDamageValue(Math.max(0, rod.getDamageValue() - cost.rod()));
        }
        // ---- 3. 统一同步（同 applyBiteDurabilityCost，见那里的长注释）----
        syncHeldItem();

        FishingManager.LOGGER.info("[垂纶] 提前收回：退回耐久 竿 +{}，线 +{}，拟饵 +{}",
                cost.rod(), cost.line(), cost.lure());
    }

    /**
     * 提前收回：抛出后（浮漂在飞 or 已落水等咬钩）再按一次右键，把线收回来。
     *
     * <p>玩家规则：**提前收回不消耗任何耐久**（鱼竿与全部配件）。
     * 因为耐久是在咬钩那一刻才扣的（见 {@link #applyBiteDurabilityCost()}），
     * 而这个方法只在 {@link State#CASTING} / {@link State#WAITING} 生效 ——
     * 鱼已经咬钩（BITE_WARN 及以后）就不再允许提前收，只能提竿或等它跑掉。
     *
     * <p>收回本身也给一次浮漂回收音效，和"鱼跑了"用不同音高区分开
     * （鱼跑了是 0.7，提前收回是 1.9，听起来更"利落"）。
     */
    public boolean retrieveEarly() {
        if (this.state != State.CASTING && this.state != State.WAITING) {
            return false;
        }
        // 兜底：万一咬钩和收回撞在同一 tick，把刚扣的耐久退回去
        refundBiteDurabilityCost();

        this.state = State.IDLE;
        this.reeling = false;
        this.releasing = false;

        this.player.displayClientMessage(
                Component.translatable("message.anglertackle.retrieved_early")
                        .withStyle(ChatFormatting.GRAY), true);
        // 2026-10-29 用户："提前收线的声音也使用上鱼声"。
        //   路亚 ⇒ 播 lure_land（和真正上鱼同一个素材）；台钓 ⇒ 仍用原版收线声。
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.LURE) {
            playSound(com.whitecobalt.anglertackle.registry.ModSounds.LURE_LAND.get(), 0.6f, 1.0f);
        } else {
            playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6f, 1.9f);
        }
        FishingManager.LOGGER.info("[垂纶] 提前收回（不消耗耐久）");

        // ⚠ 必须走 FishingManager.endSession —— 光把 state 设成 IDLE 是不够的：
        //   会话还留在 sessions map 里，下一次 castByLook 会看到"已有会话"而拒绝抛投，
        //   表现为"收回一次之后就再也抛不出去了"。endSession 还会恢复移动速度、
        //   并在 cleanup() 里回收浮漂实体。
        FishingManager.get().endSession(this);
        return true;
    }

    /**
     * 玩家在窗口内成功提竿。
     *
     * <p><b>台钓与路亚在这里分道扬镳</b>（用户要求）：
     * <ul>
     *   <li><b>台钓竿</b>：提竿就是上鱼——不需要对抗。
     *       台钓本来就是"看漂、抓口、提竿"，多一层拉力对抗反而不像台钓。</li>
     *   <li><b>路亚竿</b>：进入张力对抗（本模组手感的核心）。
     *       路亚是逗鱼咬口，中鱼之后的遛鱼过程才是重点。</li>
     * </ul>
     */
    public void onHooked() {
        if (rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {
            // 台钓：直接进入收鱼
            this.state = State.LANDING;
            this.landingTicks = 25;
            if (this.bobber != null) {
                this.bobber.reelIn();
                this.bobber = null;
            }
            playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.6f, 1.8f);
            FishingManager.LOGGER.info("[垂纶] 台钓提竿成功，直接收鱼");
            return;
        }

        // 路亚：进入张力对抗
        this.state = State.FIGHT;
        // 用咬钩时定下的那条鱼的手感档案，而不是写死鲫鱼。
        // ★ 走 effectiveProfile()：它按鱼种 id 从**配置**现取 13 个数值并组装，
        //   所以改配置只影响之后咬钩的鱼；这里取到的是一次快照，
        //   正在遛的这条不会被中途改手感（玩家会觉得"卡了"）。
        this.profile = this.hookedSpecies != null
                ? this.hookedSpecies.effectiveProfile()
                : FightProfile.CRUCIAN_CARP;

        // ★ 这条鱼**个体**的品质档（体重越大档越高）—— 只算这一次，下面体力池、
        //   挣扎节奏、tierSpeed / tierStaminaResist 全都读它，避免两处各算一遍再打架。
        int hookedTier = this.hookedSpecies != null && this.hookedData != null
                ? this.hookedSpecies.tierForWeight(this.hookedData.weightKg())
                : 0;

        // ★ 体力池按档位修正（用户 2026-10-05：金档体力不变、红档 ×1.50）。
        //   乘在这里而不是乘进 profile.stamina() 的源头：profile 是**按鱼种**存的，
        //   而档位是这一条个体的事（同种鱼的金档与红档共用一份 profile）。
        // ★ 红档体力**基准下限**（用户 2026-10-06："有些红鱼似乎体力基准连 200 都没到"
        //   → 定案"给所有红档鱼设体力池下限：基准至少 200"）。
        //   顺序是**先抬下限、再乘倍率** —— 默认下限 200 的红档鱼实际是 300。
        //   能出红档的 11 种鱼里有三种基准不到 200：蓝宝石雷龙鱼 130（台钓唯一的红鱼）、
        //   虎鱼 120、孔雀鱼 60，而它们的游速不低，于是"红档"只剩个名头。
        double rawStamina = this.profile.stamina();
        double baseStamina = FishSpecies.redStaminaFloor(hookedTier, rawStamina);
        this.fishStaminaMax = baseStamina * FishSpecies.tierFeelStaminaScale(hookedTier);
        // ★ 把"实际体力池"打成一行日志（用户 2026-10-06 问："上次体力上限的改动生效了吗，
        //   我在调参界面看到的好像偏低"）。
        //   调参器里那条「体力上限」是**按鱼种的基准值**，档位倍率是咬钩这一刻乘上去的 ——
        //   界面上永远看不到乘完的乘积，所以这里留一行证据：
        //   实际 = 基准 × 档位倍率，一条日志就能对账，不用去猜"到底生没生效"。
        FishingManager.LOGGER.info("[垂纶] 体力池：{} = 基准 {}{} × 档位倍率 {}（档 {}，游速 ×{}）",
                String.format("%.1f", this.fishStaminaMax),
                String.format("%.1f", baseStamina),
                baseStamina > rawStamina + 1e-9
                        ? String.format("（原本 %.1f，红档下限 %.1f）", rawStamina, Tuning.tierRedStaminaMin())
                        : "",
                String.format("%.2f", FishSpecies.tierFeelStaminaScale(hookedTier)),
                hookedTier,
                String.format("%.3f", FishSpecies.tierSpeed(hookedTier)));
        this.fishStamina = fishStaminaMax;
        this.tension = 20.0;
        this.redZoneTicks = 0;
        this.redZoneLimit = computeRedZoneLimit();
        this.distance = Math.max(Tuning.nearShoreBase() + 2.0, this.castDistance);
        // ★ 线长上限 = **鱼线的设计长度**（由鱼线配件决定，见 PartComposer.lineLength）。
        //   用户定案："鱼游动超过鱼线设计长度就显示鱼逃离"。
        //   麻线 30 米、棉线 35、尼龙 45、碳氟 55、PE 65 —— 都比最远抛投（24 米）长，
        //   所以正常抛投绝不会一出去就逃；只有鱼把线拖出去超过设计长度才会。
        this.maxDistance = Math.max(this.distance + 5.0, this.rodStats.lineLength());
        this.struggleSwitchTicks = this.profile.turnIntervalTicks();
        this.sprintCooldownTicks = this.profile.sprintIntervalTicks();
        this.sprinting = false;
        this.fatigued = false;
        this.fatigueTicks = 0;
        this.fatigueLockoutTicks = 0;

        // ★ 挣扎循环的节奏由**这条鱼实际的品质档**决定（体重越大档越高），
        // 而不是鱼种的上限——同种鱼里的大个体应该更折腾。
        //   档位本身在上面组装 profile 时就算过一次（hookedTier），这里**直接复用**：
        //   体力池已经按它修正过，两处必须是同一个数。
        this.fishTier = hookedTier;
        // ★ 巨物猎手（docs/28 §3）在这里**报一次**，不在对抗循环里报：
        //   pullMult 是每 tick 重算的，日志打在那边会刷屏。
        //   档位一钉死就能判定，所以这一行就是"这次到底吃没吃到"的唯一证据。
        double giantPull = DishEffectHooks.giantPullMultiplier(this.player, this.fishTier);
        if (giantPull > 1.0) {
            FishingManager.LOGGER.info("[垂纶] 巨物猎手：{} 档鱼咬钩 → 回拉速度 ×{}",
                    this.fishTier, String.format("%.2f", giantPull));
        }
        // ★ 测试拟饵的"必出红档"**不在这里强制**。
        //
        // 早先这里无条件把 this.fishTier 钉成 5 —— 改的是**对抗档位**。
        // 但**物品档位**来自 hookedData（实际抽到的那条鱼），两者于是可以不一致。
        // 真实事故（2026-09-30）：
        //   抽取结果：鱼种=crucian_carp 档位表=[0, 1] 个体下限=5 体重=0.88 实得档位=1
        //   中鱼：    crucian_carp 档=5 挣扎 10.0s / 休息 3.0s，最多 7 轮
        // 玩家看到的是"测试饵出绿鱼，却有 7 轮挣扎"——两个症状，一个根因。
        //
        // 正确做法：让 **hookedData 自己**就是红档（咬钩时已用 floor=5 抽过，
        // 见 enterHookWindow），对抗档位**一律从数据读**。
        // 这样"物品显示什么档，就是什么档在打"，结构上不可能再打架。
        if (this.testLureEquipped() && this.fishTier < 5) {
            // 抽出来的个体够不到 5 档 —— 说明池子/鱼种过滤放进来一条不该来的鱼。
            // 不静默修正（那正是上面那个 bug 的做法），而是**留下证据**。
            FishingManager.LOGGER.warn("[垂纶] 测试拟饵没抽到红档：{} 实得档位={}（" +
                            "鱼种档位表达不到 5 档，检查鱼池的水体/档位过滤）",
                    this.hookedSpecies == null ? "未知" : this.hookedSpecies.id(), this.fishTier);
        }
        // 档位派生的两个手感系数：游动速度、体力抗性。
        // tierSpeed 里已经含**金/红档手感修正**（FishSpecies.tierFeelSpeedScale，
        // 用户 2026-10-05：金 ×0.84、红 ×1.05），这里不再另乘一次。
        // 它们会和竿/线/轮的合成属性相乘或相除（见 FishSpecies 顶部的公式说明）。
        this.tierSpeed = FishSpecies.tierSpeed(this.fishTier);
        this.tierStaminaResist = FishSpecies.tierStaminaResist(this.fishTier);
        // 轮次用尽后不再挣扎，所以这里先设为"已用尽"，
        // 等第一次进入 tickFight 时由循环逻辑把它打开并计第 1 轮。
        this.fishStruggling = false;
        this.roundsDone = 0;
        this.roundsExhausted = false;
        this.exhaustAnnounced = false;
        this.roundDrain = 0.0;    // 单轮扣力清零（roundDrainCap 改用"实际已扣体力"算额度，无需累计字段）
        this.maxRounds = roundsForTier(this.fishTier);
        this.struggleTicks = 1;   // 下一 tick 立刻进入第 1 轮挣扎

        FishingManager.LOGGER.info("[垂纶] 中鱼：{} 对抗档={} 挣扎 {}s / 休息 {}s，最多 {} 轮",
                this.hookedSpecies == null ? "未知" : this.hookedSpecies.id(),
                this.fishTier,
                String.format("%.1f", FishSpecies.struggleSeconds(this.fishTier)),
                String.format("%.1f", FishSpecies.restSeconds(this.fishTier)),
                this.maxRounds);

        playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.5f, 1.6f);
        FishingManager.LOGGER.info("[垂纶] 路亚中鱼，进入对抗：{}（{}）",
                this.hookedSpecies == null ? "未知" : this.hookedSpecies.id(),
                this.profile.archetype());

        // ★ 手感来源对账行：把这条鱼**实际生效**的数值一次列全。
        //
        // 为什么必须打这一行：玩家改了 [fishStats] 之后，"改动到底有没有进去"
        // 光靠手感判断是不可靠的（张力还叠加了线/竿/控杆/档位四层系数）。
        // 有了这行就能**直接比对**：日志里的 pullSpeed 是不是配置文件里那个数。
        // —— 排查"配置没生效"这类问题时，这是唯一的硬证据。
        FishingManager.LOGGER.info(
                "[垂纶] 手感来源：id={} 档={} 拉力={} 疲劳拉力={} 体力={} 张力系数={} "
                        + "冲刺={}s/{}s 变向={}s 假动作={} 疲劳门槛={} 档位速度×{}",
                this.hookedSpecies == null ? "未知" : this.hookedSpecies.id(),
                this.fishTier,
                String.format("%.4f", this.profile.pullSpeed()),
                String.format("%.4f", this.profile.fatiguedPullSpeed()),
                String.format("%.1f", this.profile.stamina()),
                String.format("%.4f", this.profile.tensionGain()),
                String.format("%.2f", this.profile.sprintDurationSec()),
                String.format("%.2f", this.profile.sprintIntervalSec()),
                String.format("%.2f", this.profile.turnIntervalSec()),
                String.format("%.3f", this.profile.fakeOutChance()),
                String.format("%.3f", this.profile.fatigueThreshold()),
                String.format("%.4f", this.tierSpeed));
        FishingManager.LOGGER.info(
                "[垂纶] 手感来源对照：上面这几个数应当与 config/anglertackle-common.toml 里 "
                        + "{} 前缀的项**完全一致**；不一致说明配置没读到（检查该组是否在文件里）。",
                this.hookedSpecies == null ? "?" : this.hookedSpecies.id() + "*");
    }

    /**
     * 错过提竿 → **鱼逃离**。
     *
     * <p>已按用户定案：等待 2 秒（提竿窗口）后鱼逃离，
     * **扣鱼线耐久 + 扣饵料耐久，但【不扣鱼竿耐久】**。
     */
    public void onFishEscaped() {
        this.state = State.IDLE;

        // 鱼线额外损耗（M2 接入六槽配件后写进鱼线物品）
        // TODO(M2): LineStats.damage(getLine(), 1)
        // 饵料损耗
        // TODO(M2): RecipeManager.consumeBait(player, 1.0f)

        this.player.displayClientMessage(
                Component.translatable("message.anglertackle.escaped")
                        .withStyle(ChatFormatting.YELLOW), true);
        playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.5f, 0.7f);

        if (this.bobber != null) {
            this.bobber.reelIn();
            this.bobber = null;
        }
    }

    private int computeRedZoneLimit() {
        return (int) Math.round(Math.max(0.6, Math.min(5.0, 2.2)) * 20.0);
    }

    // =================================================================== 每 tick
    public void tick(ServerLevel level) {
        // 首次 tick 时读一次手上那根竿的六槽装配，算出最终属性
        if (!statsLoaded) {
            statsLoaded = true;
            this.rodStats = RodStats.of(this.player.getMainHandItem());
        }

        // 输入掉包保护：连续这么多 tick 没收到输入就当作松开。
        //
        // ⚠ 这个阈值和客户端的发送频率是**配套**的：客户端现在每 tick 都发
        //   （见 ClientFishingState.sendInput 的注释）。曾经客户端每 2 tick 发一次、
        //   这里阈值 3 —— 余量只有 1 tick，网络一抖动服务端就误判成"松手"，
        //   表现为"张力卡住 + 继续拉线鱼距不回收"，而 HUD 还显示旧状态。
        //   现在发送每 tick 一次、容忍 5 tick，余量足够，
        //   也不会因为偶尔丢一两个包就把玩家的收线中断掉。
        if (this.inputStaleTicks++ > Tuning.inputStaleLimit()) {
            this.reeling = false;
            this.releasing = false;
            this.counterDir = 0;
        }

        switch (this.state) {
            case WAITING -> tickWaiting();
            case BITE_WARN -> tickBiteWarn();
            case HOOK_WINDOW -> tickHookWindow();
            case FIGHT -> tickFight(level);
            case LANDING -> tickLanding();
            default -> { /* IDLE / CHARGING / CASTING 无需推进 */ }
        }
    }

    private void tickWaiting() {
        if (this.bobber == null || !this.bobber.isLanded()) {
            return;
        }
        // 玩家跑太远直接收线。
        //
        // ⚠️ 这里之前写的是 distanceToSqr(...) > 9.0 —— 9.0 是**平方**距离，
        // 等于只有 3 格！浮漂正常要飞 10~30 格，所以一抛出去就立刻判定"太远"收线。
        // 表现就是"只有贴着水边极近处抛才勉强能用"。
        // 现在按实际抛投距离给 2.5 倍余量，最少也给 32 格，让玩家能正常走动。
        double allowed = Math.max(32.0, this.castDistance * 2.5);
        if (this.player.position().distanceToSqr(this.bobber.position()) > allowed * allowed) {
            FishingManager.get().abort(this, "message.anglertackle.too_far");
            return;
        }
        if (++this.waitTicks >= this.waitTicksTotal) {
            startBiteWarn();
        }
    }

    /** 三段预告：① 探饵 ② 试探 ③ 吞钩（docs/02 §3.3）。 */
    private void startBiteWarn() {
        this.state = State.BITE_WARN;
        this.bitePhase = 1;
        this.bitePhaseTicks = 0;
        if (this.bobber != null) {
            this.bobber.setBitePhase((byte) 1);
        }
        playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.15f, 1.8f);
    }

    private void tickBiteWarn() {
        this.bitePhaseTicks++;
        switch (this.bitePhase) {
            case 1 -> {
                // 探饵 0.3–1.2s
                if (this.bitePhaseTicks > 6 + this.player.getRandom().nextInt(18)) {
                    this.bitePhase = 2;
                    this.bitePhaseTicks = 0;
                    if (this.bobber != null) {
                        this.bobber.setBitePhase((byte) 2);
                    }
                    playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.3f, 1.2f);
                }
            }
            case 2 -> {
                // 试探 0.3–0.8s
                if (this.bitePhaseTicks > 6 + this.player.getRandom().nextInt(10)) {
                    enterHookWindow();
                }
            }
            default -> enterHookWindow();
        }
    }

    private void tickHookWindow() {
        if (--this.hookWindowTicks <= 0) {
            // 2 秒内没提竿 → 鱼逃离（扣鱼线 + 扣饵料，不扣鱼竿）
            onFishEscaped();
            FishingManager.get().endSession(this);
        }
    }

    /** ★ 张力对抗：本模组手感的核心（含控杆三方向 + 疲劳期）。 */
    private void tickFight(ServerLevel level) {
        // ★ 第一件事：超承重的鱼正在**烧竿**。
        //
        // 用户定案："不用做筛选，只要上超承重鱼就清空钓上它的鱼竿的耐久，
        // 或者说如果上鱼检测是超承重鱼就飞快消耗当前鱼竿耐久，不用等钓上来。"
        //
        // 所以这不是"钓上来再断竿"，而是**从咬钩那一刻就开始烧**：
        // 玩家有几秒钟反应时间 —— 放线让鱼把线拽完（= 鱼逃离，竿保住），
        // 还是硬拉到底把竿赔进去。这是这套玩法里少见的"主动认输"抉择。
        //
        // 放在最前面并 return：这一 tick 若把竿烧断了，后面的对抗逻辑
        // 不该再对一个已经断掉的竿做任何事。
        if (tickOverCapacityBurn()) {
            return;
        }

        // ================= 1. 鱼的行为（由手感档案驱动）=================
        // 1a. 左右变向（神经质型鱼间隔很短）
        if (--this.struggleSwitchTicks <= 0) {
            switch (this.profile.archetype()) {
                case ERRATIC, BURST -> {
                    // 变向频繁：优先左右，偶尔直冲
                    this.struggleDir = this.player.getRandom().nextBoolean()
                            ? StruggleDir.LEFT : StruggleDir.RIGHT;
                }
                default -> {
                    this.struggleDir = switch (this.player.getRandom().nextInt(3)) {
                        case 0 -> StruggleDir.LEFT;
                        case 1 -> StruggleDir.RIGHT;
                        default -> StruggleDir.STRAIGHT;
                    };
                }
            }
            int base = this.profile.turnIntervalTicks();
            this.struggleSwitchTicks = base / 2 + this.player.getRandom().nextInt(Math.max(2, base));
        }

        // 1b. ★ 挣扎循环（用户定案的核心手感）：
        //     鱼刚上钩就猛游一阵 → 没力气歇一会儿 → 再挣扎 → 再歇。
        //     但**轮次是有限的**（见 roundsForTier）：
        //       红档 5~7 轮 / 金档 4~5 轮 / 其余档固定 2 轮
        //     轮次用完后鱼再也不挣扎，只剩玩家收线——这就是"遛翻了"。
        if (--this.struggleTicks <= 0) {
            // ⚠️ 轮次用尽后必须**锁定在休息**。
            // 之前只是把 struggling 设成 false，但计时器归零后又把它翻回 true，
            // 于是轮数会一直涨下去（实测档2的鱼跑到了 6 轮，而上限是 2）。
            if (!this.roundsExhausted) {
                this.fishStruggling = !this.fishStruggling;
            } else {
                this.fishStruggling = false;
            }
            this.roundDrain = 0.0;   // 新一轮：重置单轮扣力上限

            if (this.fishStruggling) {
                this.roundsDone++;
                if (this.roundsDone >= this.maxRounds) {
                    // 这是最后一轮，结束后不再挣扎
                    this.roundsExhausted = true;
                }
                this.struggleTicks = (int) Math.round(
                        FishSpecies.struggleSeconds(this.fishTier) * 20.0);
                // ★ 保底挣扎（用户 2026-10-06）：这一轮的头 N 秒它**必须**是"没力竭"的。
                //   顺手把疲劳清掉 —— 否则"上一轮遛软了"会一直带到这一轮，
                //   玩家看到的就是"再进入挣扎也不超过一秒"。
                this.freshStruggleTicks = (int) Math.round(FishSpecies.floorStruggleSeconds(
                        this.fishTier, this.profile.archetype()) * 20.0);
                this.fatigued = false;
                this.fatigueTicks = 0;
                FishingManager.LOGGER.info("[垂纶] 第 {} 轮挣扎：保底 {} 秒不力竭（档 {} / 策略 {}）",
                        this.roundsDone,
                        String.format("%.1f", this.freshStruggleTicks / 20.0),
                        this.fishTier, this.profile.archetype());
                spawnParticles(ParticleTypes.SPLASH, 8);
                playSound(SoundEvents.FISHING_BOBBER_SPLASH, 0.35f, 1.4f);
                this.struggleDir = this.player.getRandom().nextBoolean()
                        ? StruggleDir.LEFT : StruggleDir.RIGHT;
                this.struggleSwitchTicks = 12 + this.player.getRandom().nextInt(20);
            } else {
                // 休息时长：力竭后给一个很长的值，等于永久休息
                this.struggleTicks = this.roundsExhausted
                        ? 20 * 600
                        : (int) Math.round(FishSpecies.restSeconds(this.fishTier) * 20.0);
                this.sprinting = false;
                if (this.roundsExhausted && !this.exhaustAnnounced) {
                    this.exhaustAnnounced = true;
                    this.struggleDir = StruggleDir.NONE;
                    playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 0.5f, 0.8f);
                    FishingManager.LOGGER.info("[垂纶] 鱼已力竭（共 {} 轮），不再挣扎", this.roundsDone);
                }
            }
        }

        // 1b-2. 挣扎期间才左右乱窜（休息时鱼基本不动，漂也停下）
        if (this.fishStruggling) {
            if (--this.struggleSwitchTicks <= 0) {
                switch (this.profile.archetype()) {
                    case ERRATIC, BURST -> {
                        this.struggleDir = this.player.getRandom().nextBoolean()
                                ? StruggleDir.LEFT : StruggleDir.RIGHT;
                    }
                    default -> {
                        this.struggleDir = switch (this.player.getRandom().nextInt(3)) {
                            case 0 -> StruggleDir.LEFT;
                            case 1 -> StruggleDir.RIGHT;
                            default -> StruggleDir.STRAIGHT;
                        };
                    }
                }
                int base = this.profile.turnIntervalTicks();
                this.struggleSwitchTicks = Math.max(6,
                        base / 2 + this.player.getRandom().nextInt(Math.max(2, base)));
            }
        } else {
            // 休息：方向归零，漂就停在原地（玩家能明确看出"它现在没劲了"）
            this.struggleDir = StruggleDir.NONE;
        }

        // 1c. 冲刺（在挣扎周期之上再叠一层短促爆发）
        //
        // ★ 力竭的鱼**不再冲刺**（用户 2026-10-06："鱼即使力竭了但依然收线回拉不过来"）。
        //   原来这里只判 `fishStruggling`，而疲劳的**续期**条件里带着 `!sprinting`
        //   （见下面 1c 疲劳段）—— 两条凑在一起就出一个恶性循环：
        //   一条力竭的鱼每隔 sprintInterval（爆发型 2.6 秒）冲一次、每次 1.6 秒，
        //   冲刺期间疲劳标记**不能续期**，于是刚点亮就熄灭，2~3 秒的窗口被切成碎片。
        //   玩家看到的是"它明明没劲了，收线还是拉不动、线还在往外走"，
        //   因为一半以上的 tick 它算"没疲劳"，拽力按满值算。
        //   物理上也说不通：一条连游都游不动的鱼不该再爆发。
        //   改完之后：体力一旦掉到疲劳阈值以下（且回气被疲劳挡住，见第 5 节），
        //   疲劳标记就再也断不了，力竭窗口是**连续的**。
        if (this.fishStruggling && !this.fatigued) {
            if (this.sprinting) {
                if (--this.sprintTicks <= 0) {
                    this.sprinting = false;
                    this.sprintCooldownTicks = this.profile.sprintIntervalTicks();
                }
            } else if (--this.sprintCooldownTicks <= 0) {
                this.sprinting = true;
                this.sprintTicks = this.profile.sprintTicks();
                if (this.profile.fakeOutChance() > 0
                        && this.player.getRandom().nextDouble() < this.profile.fakeOutChance()) {
                    // 假动作：预告一个方向，实际反方向
                    this.fakeOutPending = true;
                }
                spawnParticles(ParticleTypes.SPLASH, 8);
            }
        }

        // 1c. 疲劳期（冲刺后 + 体力低于阈值 → 反拉的最佳窗口）
        //
        // ★ 保底挣扎（用户 2026-10-06）：每进入一轮挣扎，鱼至少有 N 秒是**没力竭**的 ——
        //   这段时间内疲劳**不能点亮**（N 在轮次开头设好，见上面 1b 的挣扎分支）。
        //   用户原话："现有的疲劳体系让红金等鱼过于容易拉上来……注意我说的挣扎
        //   指的是鱼不力竭的状态。"
        if (this.freshStruggleTicks > 0) {
            this.freshStruggleTicks--;
        }
        if (this.fatigueLockoutTicks > 0) {
            this.fatigueLockoutTicks--;
        }
        if (this.fatigued) {
            if (--this.fatigueTicks <= 0) {
                this.fatigued = false;
                // 退出后 10 tick 内不许再进 —— 否则体力还在阈值下会"退出→立刻重进"，
                // 客户端看到的就是力竭状态每轮闪一下（音效跟着重建，听起来断断续续）。
                this.fatigueLockoutTicks = 10;
            }
        } else if (this.freshStruggleTicks <= 0
                && this.fatigueLockoutTicks <= 0
                && !this.sprinting
                && this.fishStamina <= this.fishStaminaMax * fatigueThresholdNow()) {
            this.fatigued = true;
            this.fatigueTicks = this.profile.fatigueTicks();
        }

        // ================= 2. 控杆三方向（用户定案的核心机制）=================
        // 正拉（顺着鱼）= 体力削减少、张力涨得慢  → 安全但磨得慢
        // 中立（垂直）  = 基准                     → 不做就不错
        // 反拉（逆着鱼）= 体力削减多、张力涨得快  → 快但危险
        int fishAxis = this.struggleDir.axis();
        LeverRelation relation;
        if (fishAxis == 0 || this.counterDir == 0) {
            relation = LeverRelation.NEUTRAL;
        } else if (this.counterDir == fishAxis) {
            relation = LeverRelation.WITH_FISH;   // 正拉
        } else {
            relation = LeverRelation.AGAINST_FISH; // 反拉
        }

        double drainMult = switch (relation) {
            case WITH_FISH -> 0.65;
            case NEUTRAL -> 1.00;
            case AGAINST_FISH -> 1.50;
        };
        double tensionMult = switch (relation) {
            case WITH_FISH -> 0.50;
            case NEUTRAL -> 1.00;
            case AGAINST_FISH -> 1.40;
        };
        // 疲劳期：更好拉、更安全（但不回气）
        if (this.fatigued) {
            tensionMult *= 0.80;
            drainMult *= 1.20;
        }

        // ================= 2.5 把鱼的方向推给浮漂，并真的把漂拖走 =================
        // 玩家看到的"漂被鱼拖着在水面游动"就靠这一段。
        //
        // ⚠️ 漂的移动必须由**服务端**驱动：漂的坐标是服务端权威的，
        // 客户端改完下一秒就被同步包覆盖回去，所以放在客户端做是白费力气。
        if (this.bobber != null) {
            // 拖漂只在"鱼在挣扎"时发生；休息时漂停在原地——
            // 玩家一眼就能看出"它现在没劲了，赶紧抢线"。
            int push = (this.fatigued || !this.fishStruggling) ? 0 : fishAxis;
            this.bobber.setFishDir(push);
            if (push != 0) {
                // 拖动速度跟鱼的手感档案挂钩：爆发/神经质的鱼拖得凶，黏液型的鱼拖得慢。
                // 这样"漂动得多快"本身就是一条关于鱼种的线索。
                this.bobber.dragByFish(level, push, this.profile.pullSpeed());
            }
            // 每 2 秒记一次，用来确认"漂到底有没有被拖动"。
            if (this.tickCounter++ % 40 == 0) {
                FishingManager.LOGGER.info(
                        "[垂纶] 拖漂：方向={} 挣扎={} 剩余={}t landed={} 漂=({}, {})",
                        push, this.fishStruggling, this.struggleTicks,
                        this.bobber.isLanded(),
                        String.format("%.2f", this.bobber.getX()),
                        String.format("%.2f", this.bobber.getZ()));
            }
        }

        // ================= 3. 张力 =================
        // 鱼线决定"涨得多快"，鱼竿+鱼线决定"落得多快"（docs/11 §2）
        // gen 直接取鱼线的 tensionGen：麻线 1.00（涨得最快）→ 钛合金记忆线 0.52（最慢）
        double gen = this.rodStats.tensionGen();
        double recoverMult = this.rodStats.tensionRecover()
                / Tuning.baseRecover();                              // 1.00 ~ 2.20
        // ★ 力竭期的张力修正（用户 2026-10-06："鱼力竭时张力涨速再乘 0.85，张力回复再乘 1.15"）。
        //   力竭 = "反拉的最佳窗口"，这段时间线应该更安全：涨得更慢（下面 gain ×0.85）、
        //   落得更快（recoverMult ×1.15）。
        //   ⚠ 是**再乘**：控杆关系那边本来就有一套力竭 ×0.80（见第 2 节），两者相乘生效，
        //     所以力竭期收线的实际涨速是 0.80 × 0.85 = 0.68（相对非力竭的同向控杆）。
        //   注意 fishTension 也被这一层覆盖（涨速是"条子涨多快"，不该分是谁贡献的）。
        double fatigueGainScale = this.fatigued ? Tuning.fatiguedTensionGainScale() : 1.0;
        if (this.fatigued) {
            recoverMult *= Tuning.fatiguedTensionRecoverScale();
        }

        double delta;
        if (this.reeling) {
            delta = Tuning.reelGain() * tensionMult * gen;
        } else if (this.releasing) {
            delta = -Tuning.releaseLoss() * recoverMult;
        } else {
            delta = -Tuning.idleLoss() * recoverMult;
        }

        // 鱼的拉力：拉拽速度越快，张力涨得越凶（高级鱼因此"断线狠"）
        // 注意 fishPull 的 pullSpeed 与 tierSpeed **都**受鱼种/档位影响，
        // 而 gen 来自鱼线——两边相乘，于是"高级鱼 + 低级线"是最危险的组合。
        double fishPull = this.sprinting ? Tuning.pullSprint() : Tuning.pullIdle();
        double fishTension = fishPull * this.profile.pullSpeed() * this.tierSpeed
                * this.profile.tensionGain() * gen;

        // ================= 3b. ⚠ 中立/放线时张力**只减不增** =================
        //
        // 玩家反馈："偶发张力条跑到中间位置停住，此时不会因为任何操作涨红或落白，
        // 很容易上鱼，掉红鱼时尤为明显。"
        //
        // 根因是原来的公式允许鱼的拉力**压过**张力回落，于是存在一个平衡点：
        //     中立时 delta = -IDLE_LOSS×回复 + 鱼拉
        // 代入幼鲨红档（基础 3.625、档位系数 1.70、张力系数 1.70）× 红线（生成 0.58）：
        //     张力 20  → 鱼拉 +0.537，回落 -0.273 → 净 +0.264（还在涨）
        //     张力 60  → 鱼拉 +0.322，回落 -0.273 → 净 +0.049（还在涨）
        //     张力 74  → 鱼拉 +0.273，回落 -0.273 → 净  0.000  ← 冻在这里
        // 74 点正好落在黄区中间，不上不下，怎么操作都推不出去。
        //
        // 修法：中立/放线时先算**净回落**，再把鱼拉当成"减缓回落"叠加，
        // 并且**限幅到不超过当前张力**——张力绝不因为鱼在拉而上升。
        // 效果：
        //   · 鱼越猛 → 回落越慢（站着不动就是慢慢被拖进黄区）
        //   · 但永远不会出现"干看着它自己涨"或"卡死不动"
        //   · 收线仍然是唯一能把张力推上去的操作（REEL_GAIN 在上面的分支里）
        // ================= 3b. 收线：鱼的拉力只**减缓**涨势，不能抵消 =================
        //
        // 玩家反馈："一直长按左键收线，那个黑色判定条最终会卡到中间。"
        //
        // 根因是"鱼的拉力"正比于**当前张力**（含 tensionGain × gen），
        // 而收线的涨幅是**常数** —— 于是解 {收线涨 + 鱼拉 = 0} 会得到一个平衡张力：
        //     幼鲨红档 + 钛合金线 + 合金竿：
        //       收线涨  = 0.9 × 1.0 × 0.58                  = +0.522 点/tick
        //       鱼拉/点 = 0.15 × 3.625 × 1.76 × 1.70 × 0.58 =  0.944 点/tick（满拉力时）
        //       → 平衡点 T ≈ 0.522 / (0.944 / 张力上限) ≈ 85（黄区）
        // 低回拉轮子平衡点更低（60 出头，正好贴着白/黄分界）——
        // 和玩家两次截图里标记线的位置一致。
        //
        // 修法：鱼的拉力**最多只能把收线的涨势削到 25%**，不能削成负数。
        //   收线永远是唯一能把张力推上去的操作，这条保证必须硬。
        //   （张力本来就会随控杆关系和鱼的挣扎自然变凶，不需要靠"抵消收线"来体现。）
        //
        // 诊断：每 20 tick 打一行完整的张力算式，卡住时直接对数值。
        // 一行里包含"输入 / 控杆倍率 / 张力生成 / 回复 / 净变化 / 结果"——
        // 能一次区分：没收到输入、被控杆关系压扁、生成倍率太低、被上限钳住。
        if (this.reelDebugCounter++ % 20 == 0) {
            FishingManager.LOGGER.info(
                    "[垂纶] 张力算式：张力={} 输入={} 控杆×{} gen={} 回复×{} 力竭涨速×{} "
                            + "delta={} 鱼拉={} 上限={} 白区={} 黄区={}",
                    String.format("%.2f", this.tension),
                    this.reeling ? "收线" : this.releasing ? "放线" : "中立",
                    String.format("%.2f", tensionMult),
                    String.format("%.2f", gen),
                    String.format("%.2f", recoverMult),
                    String.format("%.2f", fatigueGainScale),
                    String.format("%+.3f", delta),
                    String.format("%.3f", fishTension),
                    String.format("%.0f", this.rodStats.tensionCeiling()),
                    String.format("%.0f", this.whiteMax()),
                    String.format("%.0f", this.yellowMax()));
        }
        // 诊断：这一 tick 的净变化 = 结算后 − 结算前。
        // ⚠ 这个"结算前"以前取错了位置（原来叫 beforeClamp，是在下面**结算之后**才取的），
        //   于是 lastTensionDelta 永远是 0、张力轨迹里的"净变化"列恒为 +0.0000、
        //   "僵持"计数一路涨到 31 —— 那一列从来没有反映过真实变化（用户 2026-10-06 的日志里
        //   满屏 +0.0000 就是这个）。现在挪到结算之前，列才有意义。
        double beforeTension = this.tension;
        if (this.reeling) {
            // 收线：鱼拉最多把涨势削到 25%（见 Tuning.minReelGainRatio() —— 保证恒为正）
            double gain = delta + fishTension;
            double floor = delta * Tuning.minReelGainRatio();   // delta > 0，floor 也是正数
            if (gain < floor) {
                gain = floor;
            }
            gain *= fatigueGainScale;                           // ★ 力竭：涨速 ×0.85
            this.tension = this.tension + gain;
        } else {
            // 中立/放线：张力**永远在往低处走**，鱼的拉力只能减缓、不能阻止。
            //
            // ⚠ 这里不能写成 net = delta + fishTension 再夹到 ≤0 ——
            //   那样等于只挡住了"上升"，没挡住"停住"：
            //     幼鲨红档 + 钛合金线：鱼拉 0.945 > 回落 0.693
            //     → 夹紧后净变化恰好为 0，张力纹丝不动。
            //   玩家看到的就是"一直长按左键，判定条卡在中间"。
            //   实际上只要他松开左键，条子也该慢慢回落 —— 现在真的会落。
            //
            //   实现：把鱼拉的贡献限制在"回落的 90%"，留出至少 10% 的净回落。
            //   鱼越猛 → 落得越慢（站着不动会被慢慢拖进黄区），但永远在落。
            double fall = -delta;                          // delta < 0
            double cappedFish = Math.min(fishTension, fall * Tuning.maxFishSlowdown());
            double net = cappedFish - fall;
            // ★ 中立最小净回落（用户 2026-10-06："现在感觉张力下降好慢，所有时候"）。
            //
            //   上面那条"鱼拉最多把回落削到 90%"有个副作用：净回落 = 回落的 **10%**，
            //   而它是**按比例**削的 —— 所以调 idleLoss 几乎没用（回落涨多少，鱼就多削多少）。
            //   实测（用户那局 铂金银龙鱼 红档，日志原文）：
            //     鱼拉 1.650 vs 中立回落 0.51（力竭时 0.511 / 平时 0.4445）
            //     → 净回落只有 **-0.05 点/tick ≈ 1 点/秒**，从红区退回白区要 50 秒。
            //   玩家的感受就是"条子几乎不动"，而且**任何时候**都是这样。
            //
            //   这里加一条**绝对下限**：不管鱼多猛，中立时每 tick 至少回落这么多。
            //   ⚠ 只作用于**中立**：放线本来就有 -releaseLoss×回复（他那局 -2.34/tick = 47 点/秒），
            //     那一档已经很快，不该也不需要被这条下限改写。
            //   ⚠ 代价：原来"干站着不动会被慢慢拖进黄区"的压力会消失（现在中立一定能回白）。
            //     想要那份压力就把 idleFallFloor 调回 0（调参器热改）。
            if (!this.releasing) {
                net = Math.min(net, -Tuning.idleFallFloor());
            }
            this.tension = this.tension + net;
        }
        // 诊断：记下这一 tick 的**实际**净变化与鱼拉贡献
        // ⚠ 只夹下界，**不要夹上界**。
        //
        // 这里原来写的是 Math.min(110.0, ...) —— 那个 110 是按"木竿+麻线"的
        // 基准上限（100，红区到 100）拍脑袋定的常数。但张力上限现在由
        // 鱼线 × 鱼竿**合成**：麻线+木竿 100，钛合金线+合金竿 220。
        // 于是高配玩家的张力涨到 110 就被硬钳住：
        //   · 110 / 220 = 恰好在条子一半，看起来"卡中间"
        //   · 110 还不到白区上限 121 → 永远进不了黄区、永远不会断线
        // 玩家报的"一直长按左键完全不动、松手才回落"就是这个。
        //
        // 上界交给下面那句"张力 >= 合成上限 → 断线"负责，那才是真正的边界。
        this.tension = Math.max(0.0, this.tension);
        this.lastTensionDelta = this.tension - beforeTension;
        this.lastFishTension = fishTension;
        traceTension(level);

        // ================= 4. 红区计时与断线 =================
        if (this.tension > this.yellowMax()) {
            if (++this.redZoneTicks > this.redZoneLimit) {
                dumpTensionTrace("红区超时断线");
                FishingManager.get().onLineSnapped(this, level);
                return;
            }
        } else {
            this.redZoneTicks = Math.max(0, this.redZoneTicks - 2);
        }

        // 超过这根竿配出来的张力上限 → 断线（顶配约 135，木竿+麻线 100）
        // ⚠ 这里必须用**会话的** tensionCeiling()，不能用 rodStats 的原始值 ——
        //   韧性保护改的是会话上限，判定用旧值的话就成了"条子变长了但还在老位置断线"。
        if (this.tension >= this.tensionCeiling()) {
            // 抢救（用户："路亚即将因为拉鱼到达红条断线时，强制使得张力条回退到黄与白交界"）：
            //   每场对抗**只触发一次**，回落点是黄/白交界（whiteMax），不是清零 ——
            //   它是"保住这一竿"，不是"免死金牌"，回头仍然可能再断。
            if (enchRodIsLure && enchRescue > 0 && !rescueUsed) {
                rescueUsed = true;
                this.tension = this.whiteMax();
                this.redZoneTicks = 0;
                this.player.displayClientMessage(
                        net.minecraft.network.chat.Component
                                .translatable("message.anglertackle.enchant_rescue")
                                .withStyle(net.minecraft.ChatFormatting.GOLD), true);
                FishingManager.LOGGER.info("[垂纶] 抢救生效：张力回退到黄/白交界 {}",
                        String.format("%.1f", this.whiteMax()));
            } else {
                dumpTensionTrace("张力触顶断线");
                FishingManager.get().onLineSnapped(this, level);
                return;
            }
        }

        // ================= 5. 鱼体力 =================
        double efficiency = this.tension <= this.whiteMax() ? 1.0
                : this.tension <= this.yellowMax() ? 0.6 : 0.2;

        if (this.reeling) {
            // 扣力 = 基准 × 竿/线/轮的加成 ÷ 鱼的体力抗性。
            // 装备越好扣得越快，鱼档位越高越抗磨——两边对冲。
            double drain = Tuning.staminaDrainPerTick() * this.fishStaminaMax
                    * efficiency * drainMult * this.rodStats.staminaDrain()
                    / this.tierStaminaResist;
            double cap = this.fishStaminaMax * this.roundDrainCap();
            double used = this.roundDrain;
            double allowed = used < cap ? Math.min(drain, cap - used) : 0.0;
            // ★ 保底体力削减（用户 2026-10-06 定案的新属性）：
            //   "拉鱼时每秒最少削减的鱼力，**不受上限限制**。由渔轮提供，根据压制阻尼转化而来。"
            //   取**较大者**而不是相加 —— 它是"下限"，不是"额外加成"；
            //   因为它绕过了 cap，所以**单轮额度用尽的那几轮也不会变成扣 0**
            //   （那正是"到 41 鱼力就不动了"那类卡死的成因）。
            //   没装渔轮 / 轮子见底时 rodStats.floorDrainPerSec() 是 0，等于没有这条保底。
            allowed = Math.max(allowed, this.rodStats.floorDrainPerSec() / 20.0);
            this.fishStamina -= allowed;
            this.roundDrain += allowed;
        }
        if (this.sprinting) {
            this.fishStamina -= 0.10;
        } else if (!this.fatigued) {
            // 鱼回气（渔轮阻尼抑制它）
            this.fishStamina += this.profile.staminaRegen() / Math.max(0.5, this.rodStats.damping());
        }
        this.fishStamina = Math.max(0.0, Math.min(this.fishStaminaMax, this.fishStamina));

        // ================= 6. 距离（线被拽出去多少）=================
        // ★ 设计原则（用户要求）：**只有断线才是失败**。
        //   距离条不该变成隐形倒计时——原来三条路径里两条一直在涨，
        //   松线 +0.10、中立 +0.02~0.06，而收线速度常常追不上，
        //   于是"时间一到鱼必逃"，跟断不断线完全无关。这是设计错误。
        //
        // 现在的模型：
        //   · 鱼挣扎/冲刺时才往外拽（歇着的时候几乎不拽）
        //   · 玩家收线能把线拉回来，好渔轮拉得更快
        //   · 距离满了只是"线快放完了"的警告，真正的失败条件是断线
        //
        // ★ 收线速度分**两档**（用户定案，终于把 pullStruggle 接上了）：
        //     鱼在挣扎 → pullStruggle      鱼在休息 → pullIdle
        //   为什么必须分档：以前两条路都用 pullIdle，收线速度**与鱼的状态无关**，
        //   高回拉的轮子恒定压过鱼的拽出力，一路把线收到 0 触发"拉干"强制上鱼——
        //   体力条完全没参与，整场对抗被绕过。分档之后"等它歇了再抢线"才是真玩法，
        //   水滴轮(pullIdle 1.90 / pullStruggle 1.95) 和
        //   大型纺车轮(1.55/1.55) 的差异也才真正体现。
        //
        //   ⚠ 实测数据（0.055 × pullSpeed × tierSpeed = 鱼的拽出力）：
        //     挣扎时鱼拽 0.10~0.28/tick，而 0.22 × pullStruggle 1.95 = 0.429 ——
        //     仍然压得过。所以"拉干"依然偏容易，但至少不再是恒定值，
        //     且低回拉的轮子在鱼发力时会被反推出去。
        boolean strugglingNow = this.fishStruggling;
        // ★ 收线速率抵抗（用户 2026-10-06 定案）：双拉属性（渔轮回拉速度）越高，
        //   高档鱼的抵抗越强（白 5% … 红 32%），压制阻尼抵消一部分（金轮贴住 75% 上限）。
        //   ⚠ 只对**超过阈值 1.2 的那部分**打折：整条打折会在阈值处跳变，
        //     1.21 的配置打完折反而比 1.19 还慢（玩家会觉得"换了好轮子更拉不动"）。
        double pullBase = strugglingNow ? this.rodStats.pullStruggle() : this.rodStats.pullIdle();
        double reelResistNow = FishSpecies.reelResist(
                this.fishTier, this.profile.archetype(), this.rodStats.damping());
        double pullResisted = FishSpecies.resistReelSpeed(pullBase, reelResistNow);
        // 拉扯（用户："每级使得路亚的双回拉速度提升5%"）：不挣扎/挣扎两个回拉速度一起乘
        double pullMult = pullResisted
                * (this.enchRodIsLure ? 1.0 + RodEnchants.DRAG_PER_LEVEL * this.enchDrag : 1.0);
        // ★ 巨物猎手（docs/28 §3）：金档以上的鱼咬钩后，回拉速度 +20%/级。
        //   乘在 pullMult 上而不是加在 distance 结算里 —— 它是"这条轮子/这根竿拉得更快"，
        //   与【拉扯】附魔同一量纲，所以 /atl rod 的"当前回拉速度"显示也会跟着变。
        //   低于金档时这个方法原样返回 1.0，等于没有这个效果。
        pullMult *= DishEffectHooks.giantPullMultiplier(this.player, this.fishTier);
        // ★ 板凳（用户 2026-10-09）：坐在**木板凳**上收线 +1%，**带坐垫**的 +2%。
        //   与【拉扯】附魔、巨物猎手同一量纲 ⇒ 乘在 pullMult 上 ✓；
        //   /atl rod 的"当前回拉速度"显示也会跟着变 ✓。没坐板凳时是 1.0（原样）✓
        pullMult *= seatReelMultiplier();
        double fishOutNow = strugglingNow
                ? 0.055 * this.profile.pullSpeed() * this.tierSpeed
                    * (this.fatigued ? this.profile.fatiguedPullSpeed() : 1.0)
                : 0.006;

        if (this.reeling) {
            // 收线：好渔轮明显拉得回来（这是遛鱼的正反馈）
            double reelSpeed = 0.22 * pullMult;
            // 鱼的反抗：挣扎时才拽得凶，休息时几乎拽不动。
            // 乘 tierSpeed —— 高档鱼游得快，线被拽远得更快。
            double distDelta = fishOutNow - reelSpeed;
            // ★ 力竭保底（用户 2026-10-06："鱼即使力竭了但依然收线回拉不过来，收不收都一个游速"）：
            //   鱼力竭时，收线的净回收**至少** fatiguedReelMinGain 格/tick。
            //
            //   为什么必须有这条：距离结算是 `鱼拽 − 收线`，而收线速度
            //   = 0.22 × 渔轮的 pullStruggle —— 低档轮（手线轮 1.08 → 0.238）
            //   一旦磨损掉档，就会低于力竭鱼的拽力（红档幼鲨 0.184、
            //   红档大眼海鲢 0.163…），于是"力竭"只剩一个标签，线照样往外走。
            //   这跟张力那边的 minReelGainRatio（收线涨势保底 25%）是同一条设计原则：
            //   **玩家的正确操作必须永远有正反馈**，不能因为装备差就变成"看着它力竭却拉不动"。
            //
            //   只保底"力竭"这一段 —— 鱼还精神的时候照样能把线拽出去（那是玩法）。
            if (this.fatigued) {
                distDelta = Math.min(distDelta, -Tuning.fatiguedReelMinGain());
            }
            this.distance += distDelta;
        } else if (this.releasing) {
            // 主动放线（松手）：给鱼一点空间，但代价是线变长
            this.distance += strugglingNow ? 0.05 * this.tierSpeed : 0.004;
        } else {
            // 中立：不打不收。鱼在挣扎时会被拽走一点，歇着时基本不动。
            this.distance += strugglingNow
                    ? 0.012 + 0.020 * this.profile.pullSpeed() * this.tierSpeed
                    : 0.002;
        }
        // 线长是双向的：收得回来，也能被拽到负值（说明线很松）
        this.distance = Math.max(0.0, this.distance);

        // 记下当前收线速度 / 是否收得动，供 /atl rod 诊断
        this.lastReelSpeed = this.reeling ? 0.22 * pullMult : 0.0;
        this.lastFishOut = fishOutNow;

        if (this.distance >= this.maxDistance) {
            dumpTensionTrace("线被放完，鱼逃离");
            FishingManager.get().onEscaped(this, level);
            return;
        }
        // ================= 7. 上鱼判定 =================
        // **只有两条路**能拿下：
        //   A 拉干：线被收到 0（线绷到最短）→ 强制上鱼（用户定案）
        //   B 遛翻：鱼力 ≤ 最大体力的 0.5% → 直接上鱼，**不看距离**（用户定案）
        //
        // ⚠️ 这里**曾经有第三条"兜底"路**：`else if (distance <= 近岸圈) 上鱼`。
        //    那条路完全不看鱼力，只要线收到 `castDistance×0.25 + 2.0`（抛 20 米时 = 7 米）
        //    就直接把鱼判上来。结果玩家反馈"到 41 鱼力就上鱼了"——
        //    实测抛 20 米时第 204 tick（10.2 秒）就触发，**鱼力还剩 84.8%**。
        //    我原以为它"基本被 A 覆盖"，其实恰好相反：A 要 distance ≤ 0，
        //    而 C 只要 distance ≤ 7，**C 永远比 A 先触发**，等于把整场对抗掏空了。
        //    教训：判定路径里不允许有"不看主条件的兜底"，那等于取消主条件。
        //
        // 注意 `distance` 上面已经 `Math.max(0.0, ...)` 夹住，所以判 `<= 0.0` 即可。
        boolean tired = this.isFishTired();
        boolean pulledDry = this.distance <= 0.0;

        if (pulledDry) {
            dumpTensionTrace("拉干上鱼");
            com.whitecobalt.anglertackle.leaderboard.LeaderboardHooks.onLanded(this.player, this.hookedSpecies == null ? "" : this.hookedSpecies.id(), this.hookedData == null ? 0.0 : this.hookedData.weightKg(), this.hookedData == null ? 0.0 : this.hookedData.lengthCm(), this.hookedData == null ? "" : String.valueOf(this.hookedData.grade()), this.roundsDone, this.tickCounter, this.fishTier, 0);
            FishingManager.LOGGER.info("[垂纶] 拉干强制上鱼：线收到 0 格（鱼力剩 {}%）",
                    String.format("%.2f", this.fishStaminaMax <= 0 ? 0
                            : this.fishStamina / this.fishStaminaMax * 100));
            // ⚠ 2026-10-16：这里**当场给鱼**（不再等 LANDING 的 1.25~1.5 秒）——
            //   用户要的是"上鱼后立刻给鱼、视角立刻复原，只有移动再锁一秒"。
            //   `state = LANDING` 那句保留（只用来标记"本场已经上鱼了"），
            //   真正结束会话的是 finishCatch() 里的 onCatch → FishingManager.end。
            this.state = State.LANDING;
            this.landingTicks = 0;
            if (this.bobber != null) {
                this.bobber.reelIn();
                this.bobber = null;
            }
            this.catchMoveLockTicks = CATCH_MOVE_LOCK_TICKS;
            finishCatch();
        } else if (tired) {
            dumpTensionTrace("遛翻上鱼");
            com.whitecobalt.anglertackle.leaderboard.LeaderboardHooks.onLanded(this.player, this.hookedSpecies == null ? "" : this.hookedSpecies.id(), this.hookedData == null ? 0.0 : this.hookedData.weightKg(), this.hookedData == null ? 0.0 : this.hookedData.lengthCm(), this.hookedData == null ? "" : String.valueOf(this.hookedData.grade()), this.roundsDone, this.tickCounter, this.fishTier, 0);
            FishingManager.LOGGER.info("[垂纶] 遛翻上鱼：鱼力 {}%（阈值 {}%，线还在 {} 格）",
                    String.format("%.2f", this.fishStaminaMax <= 0 ? 0
                            : this.fishStamina / this.fishStaminaMax * 100),
                    String.format("%.1f", Tuning.staminaEpsilonRatio() * 100),
                    String.format("%.1f", this.distance));
            // 与"拉干上鱼"同一条纪律：当场给鱼，只把移动多锁一秒
            this.state = State.LANDING;
            this.landingTicks = 0;
            if (this.bobber != null) {
                this.bobber.reelIn();
                this.bobber = null;
            }
            this.catchMoveLockTicks = CATCH_MOVE_LOCK_TICKS;
            finishCatch();
        }
    }

    private int landingTicks;

    /**
     * 上鱼那一刻**还剩多少 tick 要继续锁住移动**（发给客户端，见 {@link #movementLockTicks()}）。
     *
     * <p>⚠⚠ 2026-10-16 用户纠正过一次设计（原话）：
     * <blockquote>"我想要的是**上鱼后给鱼、视角缩放恢复正常，一秒后允许移动**"</blockquote>
     * 之前的实现是"先等 1.25~1.5 秒（LANDING）才给鱼" —— 玩家看到的是
     * **"上鱼后视角仍然没正常，也不给鱼；一秒后才给鱼并恢复正常"** ✗ 完全反了。
     * ⇒ 现在：{@link #state} 在**上鱼那一刻**就结束会话（客户端据此立刻把视角/条子收掉、
     * 立刻拿到鱼），而移动**单独**再锁 {@link #CATCH_MOVE_LOCK_TICKS} tick（1 秒）。
     */
    public static final int CATCH_MOVE_LOCK_TICKS = 20;

    /** 上鱼后还要锁多少 tick 的移动（0 = 不锁）。客户端照这个值自己倒计时。 */
    public int movementLockTicks() {
        return this.catchMoveLockTicks;
    }

    private int catchMoveLockTicks;

    /** 真正"给鱼"的收口：补扣超重耐久 → 结算上鱼 → 把移动锁的时长交给客户端。 */
    private void finishCatch() {
        if (this.catchDone) {
            return;                 // 幂等：tickLanding 与 onHooked 两条路都可能走到这里
        }
        this.catchDone = true;
        applyOverCapacityLandingCost();
        // ⚠⚠ 2026-10-16（用户报"移动没锁一秒"）：这一秒**必须记在 FishingManager 里**，
        //   不能只靠"会话还在" —— 下面 onCatch() 会**当场结束会话**（为了立刻给鱼、立刻收视角），
        //   而 end() 会 restoreMoveSpeed()，于是服务端的速度锁在"给鱼那一刻"就松了 ✗。
        //   客户端清冲量压不住动量与水流（FishingManager 里那段注释写过），玩家就走出去了。
        FishingManager.get().markCatchMoveLock(this.player, this.catchMoveLockTicks);
        FishingManager.LOGGER.info("[垂纶] 上鱼结算：当场给鱼；移动再锁 {} tick（{} 秒，服务端也压着速度属性）",
                CATCH_MOVE_LOCK_TICKS, String.format("%.1f", CATCH_MOVE_LOCK_TICKS / 20.0));
        FishingManager.get().onCatch(this);
    }

    private boolean catchDone;

    private void tickLanding() {
        if (--this.landingTicks <= 0) {
            finishCatch();
        }
    }

    /**
     * 超承重的鱼上岸时，把它"本该烧掉、但因为上得太快而没烧够"的耐久补上。
     *
     * <p>见 {@link #OVER_CAPACITY_LANDING_MIN_FRACTION} 的说明。这里只补**差额**：
     * 玩家如果已经硬拉了很久（烧够了），就不再补 —— 不能重复计费。
     */
    private void applyOverCapacityLandingCost() {
        if (!this.overCapacity) {
            return;
        }
        ItemStack rod = this.player.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem)) {
            return;
        }
        int max = rod.getMaxDamage();
        if (max <= 0) {
            return;
        }
        // 超重倍数从 1 起算，所以 ratio=1 时不补（那本来就没超）
        double frac = OVER_CAPACITY_LANDING_MIN_FRACTION * (this.overCapacityRatio - 1.0);
        int floor = (int) Math.round(max * frac);
        int owed = floor - this.overCapacityBurned;
        if (owed <= 0) {
            return;
        }
        int cur = rod.getDamageValue();
        // ⚠ 夹在 max-1：**补扣不会把竿弄断**。鱼已经拉上来了，
        //   因为事后补账而"竿断了鱼也没了"是玩家无法预料也无法接受的。
        int now = Math.min(max - 1, cur + owed);
        rod.setDamageValue(now);
        int real = now - cur;
        if (real > 0) {
            this.player.displayClientMessage(
                    net.minecraft.network.chat.Component.translatable(
                                    "message.anglertackle.over_capacity_landed",
                                    String.format("%.2f", this.overCapacityRatio),
                                    real)
                            .withStyle(net.minecraft.ChatFormatting.RED), false);
            FishingManager.LOGGER.info("[垂纶] 超承重上鱼补扣耐久：超重 ×{}，已烧 {}，补扣 {}，竿耐久 {}/{}",
                    String.format("%.2f", this.overCapacityRatio), this.overCapacityBurned,
                    real, max - now, max);
        }
    }

    // =================================================================== 输入
    public void applyInput(boolean reeling, boolean releasing, int counterDir) {
        this.reeling = reeling;
        this.releasing = releasing;
        this.counterDir = Math.max(-1, Math.min(1, counterDir));
        this.inputStaleTicks = 0;
    }

    public void setReeling(boolean reeling) {
        this.reeling = reeling;
        this.releasing = !reeling;
        this.inputStaleTicks = 0;
    }

    // =================================================================== 同步
    /** 打包一份客户端快照（每 2 tick 发一次）。 */
    public SessionSyncPayload snapshot() {
        double ceiling = tensionCeiling();
        double safe = whiteMax();
        double danger = yellowMax();
        // 归一化成 0~1 的比例，客户端无需要知道绝对值就能画条
        float t = (float) Math.min(1.0, this.tension / Math.max(1.0, ceiling));
        float s = (float) (this.fishStaminaMax <= 0 ? 0 : this.fishStamina / this.fishStaminaMax);
        float d = (float) Math.min(1.0, this.distance / Math.max(1.0, this.maxDistance));

        Vec3 pos = this.bobber != null ? this.bobber.position() : this.player.position();

        return new SessionSyncPayload(
                (byte) this.state.ordinal(),
                t,
                s,
                d,
                (float) (safe / ceiling),
                (float) (danger / ceiling),
                (byte) this.struggleDir.ordinal(),
                this.sprinting,
                this.fatigued,
                this.bobber != null,
                (short) Math.min(Short.MAX_VALUE, this.redZoneTicks),
                (short) Math.min(Short.MAX_VALUE, this.redZoneLimit),
                (float) this.chargeProgress(this.player.level().getGameTime()),
                pos.x, pos.y, pos.z,
                this.partsDurabilitySnapshot(),
                // 超承重警告靠这两个字段常驻在 HUD 上。
                // 只发动作栏消息是不够的 —— 玩家一开始收线，别的消息就把它顶掉了
                //（用户反馈："一拉超重提示就没，不明显"）。
                this.overCapacity,
                rodDurabilityRatio(),
                // 上鱼后"移动还要锁多久"：客户端照这个值自己倒计时（见字段的说明）
                (byte) Math.max(0, this.catchMoveLockTicks),
                // 服务端权威的竿型：客户端据此决定"允许不允许缩放视野"
                rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.LURE
        );
    }

    /**
     * 手上那根竿的**剩余**耐久比例 0~1；小于 0 表示读不到（没拿竿 / 无耐久）。
     *
     * <p>超重烧竿时 HUD 要画一条"血在往下掉"的条 —— 看不见在烧，
     * 惩罚就等于不存在，玩家只会觉得竿莫名其妙没了。
     */
    private float rodDurabilityRatio() {
        ItemStack rod = this.player.getMainHandItem();
        if (!(rod.getItem() instanceof RodItem) || rod.getMaxDamage() <= 0) {
            return -1f;
        }
        return 1f - (float) rod.getDamageValue() / rod.getMaxDamage();
    }

    /**
     * 打包配件的耐久，给 HUD 显示用。
     *
     * <p>只收 {@code maxDamage > 0} 的槽位：渔轮和铅锤在 {@code ModItems.partProps}
     * 里耐久传的是 0（不设耐久上限），它们天然不进列表、也不会在 HUD 上出现。
     * 这就是用户那条"有耐久定义的就显示"。
     *
     * <p>输出扁平三元组 {@code [槽位序号, 已损耗, 上限]}，槽位序号对应
     * {@link ModDataComponents.RodParts#SLOT_NAMES}。
     */
    private int[] partsDurabilitySnapshot() {
        ItemStack rod = this.player.getMainHandItem();
        ModDataComponents.RodParts parts = rod.get(ModDataComponents.ROD_PARTS.get());
        if (parts == null) {
            return new int[0];
        }
        java.util.List<ItemStack> slots = parts.all();
        int[] buf = new int[slots.size() * 3];
        int n = 0;
        for (int i = 0; i < slots.size(); i++) {
            ItemStack s = slots.get(i);
            if (s == null || s.isEmpty()) {
                continue;
            }
            int max = s.getMaxDamage();
            if (max <= 0) {
                continue;   // 没有耐久定义（渔轮 / 铅锤）→ 不显示
            }
            buf[n++] = i;
            buf[n++] = s.getDamageValue();
            buf[n++] = max;
        }
        return java.util.Arrays.copyOf(buf, n);
    }

    // =================================================================== 工具
    private void playSound(net.minecraft.sounds.SoundEvent event, float volume, float pitch) {
        this.player.level().playSound(null, this.player.blockPosition(), event,
                SoundSource.NEUTRAL, volume, pitch);
    }

    private void spawnParticles(net.minecraft.core.particles.SimpleParticleType type, int count) {
        if (this.bobber == null || !(this.player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 p = this.bobber.position();
        level.sendParticles(type, p.x, p.y, p.z, count, 0.2, 0.1, 0.2, 0.02);
    }

    /** 清理：回收浮漂、回到 IDLE。 */
    public void cleanup() {
        if (this.bobber != null && this.bobber.isAlive()) {
            this.bobber.discard();
        }
        this.bobber = null;
        this.state = State.IDLE;
        this.reeling = false;
        this.releasing = false;
        this.counterDir = 0;
        this.tension = 0;
        this.redZoneTicks = 0;
    }

    /** 给聊天栏/动作栏的调试输出（M1 手感调参用）。 */
    public Component debugLine(long gameTick) {
        return Component.literal(String.format(
                "[%s] T=%.1f/%.0f 体力=%.1f 距离=%.1f/%.1f 挣扎=%s%s 红区=%d/%d",
                state, tension, tensionCeiling(), fishStamina, distance, maxDistance,
                struggleDir, sprinting ? " 冲刺!" : "", redZoneTicks, redZoneLimit
        )).withStyle(ChatFormatting.GRAY);
    }


    /**
     * 坐着板凳时对回拉速度的**乘数**（1.0 = 没坐，原样）。
     * 用户 2026-10-09 定案：木板凳 +1%、带坐垫再 +1%（共 +2%）✓。
     * 数值来自脚下方块自己的 {@code reelBonus()} ⇒ 想调只需改方块，不散落在钓鱼逻辑里 ✓。
     */
    private double seatReelMultiplier() {
        if (this.player == null) return 1.0;
        if (!(this.player.getVehicle() instanceof com.whitecobalt.anglertackle.entity.SeatEntity seat)) {
            return 1.0;
        }
        return 1.0 + seat.reelBonus();
    }
}
