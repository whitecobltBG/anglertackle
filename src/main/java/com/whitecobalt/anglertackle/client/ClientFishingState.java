package com.whitecobalt.anglertackle.client;

import com.whitecobalt.anglertackle.network.FishingInputPayload;
import com.whitecobalt.anglertackle.network.HookPayload;
import com.whitecobalt.anglertackle.network.SessionSyncPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 客户端钓鱼状态：只保存一份服务端快照，并在帧间做插值。
 *
 * <p>为什么必须插值：服务端 20 TPS 下发状态，客户端帧率远高于此。
 * 直接画会让张力条一跳一跳——"手感"就毁了（docs/06-技术方案.md §3）。
 */
public final class ClientFishingState {

    private static final ClientFishingState INSTANCE = new ClientFishingState();

    /** 与服务端 {@code FishingSession.State} 的顺序一一对应。 */
    public static final int STATE_IDLE = 0;
    public static final int STATE_CHARGING = 1;
    public static final int STATE_CASTING = 2;
    public static final int STATE_WAITING = 3;
    public static final int STATE_BITE_WARN = 4;
    public static final int STATE_HOOK_WINDOW = 5;
    public static final int STATE_FIGHT = 6;
    public static final int STATE_LANDING = 7;

    // 上一帧值（用于插值）
    private float prevTension;
    private float prevStamina;
    private float prevDistance;

    // 当前目标值（服务端下发）
    private float tension;
    private float stamina;
    private float distance;
    private float safeRatio = 0.55f;
    private float dangerRatio = 0.82f;
    private int state = STATE_IDLE;
    private int struggleDir;
    private boolean sprinting;
    private boolean fatigued;
    /** 浮漂是否已经存在（蓄力阶段为 false，此时不能画鱼线）。 */
    private boolean bobberActive;
    private int redZoneTicks;
    private int redZoneLimit = 44;
    private Vec3 bobberPos = Vec3.ZERO;

    /** 配件耐久三元组 {@code [槽位, 已损耗, 上限]}，由服务端同步（只含有耐久的配件）。 */
    private int[] partsDurability = new int[0];

    /**
     * 上一 tick 右键是否按下。
     *
     * <p>用来取"新按下"的**上升沿**发提前收回请求。用边沿而不是电平，
     * 是因为收回是"一次性动作"；用电平会每 tick 都发，虽然服务端第二次就
     * 返回 false 了，但白白多收一堆包。
     */
    private boolean prevRightDown;
    /**
     * 最近一次**发给服务端**的"左键按住"（= 收线）。
     *
     * <p>相机拉近读这个，而不是自己去读 {@code keyAttack}：
     * 画面上的"在收线"必须和机制上的"在收线"是同一个布尔，
     * 否则网络抖动或状态切换时会出现"手感上在收线、画面却没拉近"的错位。
     */
    private boolean primaryHeld;
    /** 服务端下发的蓄力进度（0~1）——客户端不自己计时，避免两端漂移。 */

    private long lastReceiveTick;
    /** 输入诊断的打印节流（每 20 tick 一行）。 */
    private int inputDebugCounter;
    /**
     * 本地蓄力计时（毫秒时间戳）。
     *
     * <p>为什么不完全依赖服务端下发的进度：服务端每 2 tick 才发一次快照，
     * 网络稍抖就会让条子看起来"卡住 / 回退"。客户端按自己的按键时长推进，
     * 服务端值只作为参考，手感才稳。
     */
    private long localChargeStartMs;
    private float localChargeProgress;
    private float serverChargeProgress;

    private ClientFishingState() {
    }

    public static ClientFishingState get() {
        return INSTANCE;
    }

    // ------------------------------------------------------------------ 接收
    /** 超承重的鱼正在烧竿（服务端判定，HUD 常驻警告用）。 */
    private boolean overCapacity = false;
    /** 竿的剩余耐久比例 0~1；<0 表示未知。 */
    private float rodDurabilityRatio = -1f;

    public boolean overCapacity() {
        return overCapacity;
    }

    public float rodDurabilityRatio() {
        return rodDurabilityRatio;
    }

    public void accept(SessionSyncPayload p) {
        this.prevTension = this.tension;
        this.prevStamina = this.stamina;
        this.prevDistance = this.distance;

        this.state = p.state();
        this.tension = p.tensionRatio();
        this.stamina = p.staminaRatio();
        this.distance = p.distanceRatio();
        this.safeRatio = p.safeRatio();
        this.dangerRatio = p.dangerRatio();
        this.struggleDir = p.struggleDir();
        this.sprinting = p.sprinting();
        this.fatigued = p.fatigued();
        this.bobberActive = p.bobberActive();
        this.redZoneTicks = p.redZoneTicks();
        this.redZoneLimit = Math.max(1, p.redZoneLimit());
        this.bobberPos = new Vec3(p.bobberX(), p.bobberY(), p.bobberZ());
        this.serverChargeProgress = p.chargeProgress();
        this.partsDurability = p.partsDurability() == null ? new int[0] : p.partsDurability();
        this.lastReceiveTick = System.currentTimeMillis() / 50L;
        // 服务端在**上鱼那一刻的最后一份快照**里给这个值 > 0 ⇒
        // 客户端据此立刻收掉视角/条子（见 inFight / tensionBarsVisible），但移动再锁这么久。
        this.movementLockTicks = Math.max(0, p.movementLockTicks());
        this.lureSession = p.lureSession();
    }

    /**
     * 配件耐久快照：每 3 个整数一组 {@code [槽位序号, 已损耗, 上限]}。
     *
     * <p>只含"有耐久定义"的配件——渔轮与鱼钩耐久为 0，服务端根本不发。
     * HUD 直接遍历这个数组即可，不用再去翻玩家背包（客户端不权威）。
     */
    public int[] partsDurability() {
        return partsDurability;
    }

    /** 超过 1 秒没收到同步 → 认为会话已结束，清掉 HUD。 */
    public void tick() {
        long now = System.currentTimeMillis() / 50L;
        // 上鱼后那"一秒不许移动"的倒计时：客户端自己走（服务端只给一次总时长）
        if (movementLockTicks > 0) {
            movementLockTicks--;
        }
        if (now - lastReceiveTick > 20) {
            if (state != STATE_IDLE) {
                state = STATE_IDLE;
                tension = 0;
                stamina = 0;
                distance = 0;
                bobberActive = false;
            }
        }
        // （原先这里递减 inputCooldown 控制输入包节流；已改为每 tick 都发，字段删除）

        // ---- 蓄力计时 ----
        // 客户端自己按时间推进，不再完全依赖服务端快照：
        // 服务端每 2 tick 才发一次，网络抖动时条子会看起来"卡住/回退"。
        if (state == STATE_CHARGING) {
            long nowMs = System.currentTimeMillis();
            if (localChargeStartMs == 0) {
                localChargeStartMs = nowMs;
            }
            // 蓄力进度：按 config 的 chargeTimeSeconds 计（默认 1.6 秒蓄满）。
            // 原来这里硬编码 1600.0f，改配置没用；现在读同一个来源。
            localChargeProgress = Math.min(1.0f,
                    (nowMs - localChargeStartMs) / (chargeSeconds() * 1000.0f));
        } else {
            localChargeStartMs = 0;
            localChargeProgress = 0;
        }
    }

    /** 兼容旧名（HUD 里用的是 chargeProgress）。 */
    public float chargeProgress() {
        return state == STATE_CHARGING ? localChargeProgress : serverChargeProgress;
    }

    /**
     * 蓄满所需秒数，来自 config（{@code cast.chargeTimeSeconds}）。
     *
     * <p>做一层取值+兜底：配置在极早期可能还没加载完，此时 {@code get()} 会抛异常。
     * 抛出去会让整个 HUD 崩掉，所以这里兜 1.6 秒（也是默认值）。
     */
    private static float chargeSeconds() {
        try {
            return (float) (double) com.whitecobalt.anglertackle.Config.INSTANCE
                    .chargeTimeSeconds.get();
        } catch (Throwable t) {
            return 1.6f;
        }
    }

    /** 服务端给的蓄力进度（调试用）。 */
    public float serverChargeProgress() {
        return serverChargeProgress;
    }

    // ------------------------------------------------------------------ 查询
    public int state() {
        return state;
    }

    public boolean inSession() {
        return state != STATE_IDLE;
    }

    public boolean charging() {
        return state == STATE_CHARGING;
    }

    /**
     * 是否在**真正的对抗**里（FIGHT）。
     *
     * <p>⚠ 只给"相机跟随 / 收线拉近"这类**跟张力有关**的表现用。
     * 别拿它判"该不该接管键盘" —— 那是 {@link #movementLocked()}，两者**故意不同**
     * （见那个方法的说明：上鱼后还要多锁一秒，是用户要的功能，不是 bug）。
     */
    public boolean inFight() {
        return state == STATE_FIGHT;
    }

    /**
     * 是否该**接管玩家的移动键 / 左键**（把 WASD 变成控杆方向）。
     *
     * <h2>⚠⚠ 三个概念必须分开（用户 2026-10-16 纠正过我一次）</h2>
     * 用户原话：<blockquote>"我想要的是**上鱼后给鱼、视角缩放恢复正常，一秒后允许移动**"</blockquote>
     * 而我当时的实现把三件事**全绑在 LANDING 这一个状态**上 ⇒ 玩家看到的是
     * "上鱼后视角仍然没正常、也不给鱼；一秒后才给鱼并恢复正常" ✗ 恰好相反。
     *
     * <table>
     *   <tr><th>事情</th><th>什么时候发生</th><th>判据</th></tr>
     *   <tr><td>给鱼（入包 / 经验 / 公告）</td><td>**上鱼那一刻**</td><td>服务端当场结算（见 FishingSession.finishCatch）</td></tr>
     *   <tr><td>视角、张力条/鱼力条收掉</td><td>**上鱼那一刻**</td><td>{@link #inFight()} / {@link #tensionBarsVisible()}（只认 FIGHT）</td></tr>
     *   <tr><td>移动</td><td>**再锁一秒**</td><td>**本方法**：FIGHT/LANDING，或服务端给的倒计时还没走完</td></tr>
     * </table>
     *
     * <p>为什么要多锁这一秒（用户上一轮的原话）："不然有人收不住方向键会掉进水里" ——
     * 收线时手按着 A/D，鱼一进包就恢复自由移动的话人会直接走进水里。
     *
     * <p>倒计时由服务端在**上鱼那一刻的最后一份快照**里给出
     * （{@code SessionSyncPayload.movementLockTicks}），客户端自己递减 ——
     * 这样"锁多久"只有一个真源（服务端的 {@code CATCH_MOVE_LOCK_TICKS}）。
     */
    public boolean movementLocked() {
        return state == STATE_FIGHT || movementLockTicks > 0;
    }

    /**
     * 上鱼之后**还剩多少 tick 不许移动**（服务端在最后一份快照里给的值）。
     *
     * <p>⚠ 与「视角/条子」**故意分开**：视角与给鱼是"立刻"，移动是"再等一秒"。
     * 别再把三件事绑到同一个状态上 —— 那正是 2026-10-16 我犯的错。
     */
    private int movementLockTicks;

    /**
     * 是否处于"上鱼后"那一段（LANDING）。
     *
     * <p>相机用它来决定"上鱼后继续盯着浮漂最后位置"这段收尾还该不该有：
     * 一旦重新抛竿（CASTING）就必须立刻结束，否则抛竿时镜头还黏在上一竿的点上，
     * 玩家会感觉"转视角发滞"（用户 2026-10-28 报的第 3 条）。
     */
    public boolean inLanding() { return this.state == STATE_LANDING; }

    /** 是否在"抛竿蓄力"（新一轮钓鱼的开始）。收尾记忆靠它清空，而不是靠"不在 LANDING"。 */
    public boolean inCasting() { return this.state == STATE_CASTING; }

    /** 服务端权威的竿型：这一场是不是路亚。 */
    private boolean lureSession;

    /** 这一场是不是路亚（服务端说了算；台钓恒为 false）。 */
    public boolean lureSession() { return this.lureSession; }

    /** ★临时诊断用（定位完就删）：原始 state 与剩余锁 tick。 */
    public int rawStateForDebug() { return this.state; }

    public int movementLockTicksForDebug() { return this.movementLockTicks; }

    /**
     * 玩家此刻是否在**收线**（左键按住）。
     *
     * <p>相机拉近用它。这是"最近一次发给服务端的那个布尔"，见 {@link #primaryHeld}。
     */
    public boolean reeling() {
        return primaryHeld;
    }

    public boolean hookWindow() {
        return state == STATE_HOOK_WINDOW;
    }

    /**
     * 浮漂已经落水、还在等鱼咬钩（WAITING）。
     *
     * <p>刻意**只认 WAITING**：CASTING 是浮漂还在空中飞的那两三 tick，
     * 那会儿玩家刚松手，屏幕上弹一行"上钩时按左键"反而比没有更吵；
     * 等浮漂落定再出现，读起来正好是"现在开始等了"。
     * 提竿窗口（HOOK_WINDOW）不算 —— 那一刻屏幕上已经有"！提竿！"。
     */
    public boolean awaitingBite() {
        return state == STATE_WAITING;
    }

    /**
     * 张力条 / 鱼力条是否还要画。
     *
     * <p>= 与 {@link #movementLocked()} **同一个区间**（FIGHT + 上鱼后的落地那一段）。
     * 刻意留成两个名字：它们的**理由**不同（一个是"别让人掉进水里"，一个是"条子别啪一下没了"），
     * 将来谁要单独调其中一个，改一处不会连带另一处。
     */
    public boolean tensionBarsVisible() {
        // ⚠ 只认 FIGHT：上鱼那一刻就收掉条子（用户 2026-10-16："上鱼后视角缩放恢复正常"）。
        //   原来这里还带着 LANDING，而 LANDING 要 1.25~1.5 秒 ⇒ 玩家看到的是
        //   "鱼都给了、条子还挂着、视角还没回来" ✗
        return state == STATE_FIGHT;
    }


    /** 插值后的张力比例（0~1）——渲染用这个，不要用原始值。 */
    public float tensionLerped(float partialTick) {
        return Mth.lerp(partialTick, prevTension, tension);
    }

    public float staminaLerped(float partialTick) {
        return Mth.lerp(partialTick, prevStamina, stamina);
    }

    public float distanceLerped(float partialTick) {
        return Mth.lerp(partialTick, prevDistance, distance);
    }

    public float safeRatio() {
        return safeRatio;
    }

    public float dangerRatio() {
        return dangerRatio;
    }

    public int struggleDir() {
        return struggleDir;
    }

    public boolean sprinting() {
        return sprinting;
    }

    /** 疲劳期：反拉的最佳窗口（docs/14-鱼的手感档案.md §1.2）。 */
    public boolean fatigued() {
        return fatigued;
    }

    /** 浮漂是否已存在——没有浮漂时不要画鱼线/落点标记。 */
    public boolean bobberActive() {
        return bobberActive;
    }

    /** 红区剩余比例 1→0；不在红区时返回 1。 */
    public float redZoneProgress() {
        if (redZoneTicks <= 0) {
            return 1.0f;
        }
        return 1.0f - Mth.clamp((float) redZoneTicks / redZoneLimit, 0.0f, 1.0f);
    }

    public Vec3 bobberPos() {
        return bobberPos;
    }

    /**
     * 深红闪烁阈值（0~1 比例），来自 config {@code tension.criticalFlash}。
     *
     * <p>与白区/黄区同理：config 里写的是"基准 100 的百分比"，
     * 这里换算成比例使用，所以换好线把上限抬高时阈值同步放大。
     */
    private static float criticalRatio() {
        try {
            return Math.min(0.999f, (float) (double) com.whitecobalt.anglertackle.Config.INSTANCE
                    .tensionCriticalFlash.get() / 100.0f);
        } catch (Throwable t) {
            return 0.98f;
        }
    }

    /**
     * 鱼线颜色（本模组最重要的视觉反馈）。
     *
     * <p>白 → 黄 → 红 → 深红闪烁，见 docs/02-钓鱼小游戏机制.md §4.2。
     */
    public int lineColor(float partialTick) {
        float t = tensionLerped(partialTick);
        // 深红闪烁阈值来自 config（tension.criticalFlash，基准 100 的百分比）
        float crit = criticalRatio();
        if (t >= crit) {
            boolean blink = ((System.currentTimeMillis() / 250L) & 1L) == 0L;
            return blink ? 0xFF5555 : 0x5C1010;
        }
        if (t >= dangerRatio) {
            float k = (t - dangerRatio) / Math.max(0.001f, 0.98f - dangerRatio);
            return lerpColor(0xE85C5C, 0xC62828, k);
        }
        if (t >= safeRatio) {
            float k = (t - safeRatio) / Math.max(0.001f, dangerRatio - safeRatio);
            return lerpColor(0xF0C93B, 0xD8A81E, k);
        }
        float k = t / Math.max(0.001f, safeRatio);
        return lerpColor(0xFFFFFF, 0xE4E4E4, k);
    }

    /** 鱼线的张力形态：0 = 松弛下垂，1 = 完全绷直。 */
    public float lineStraightness(float partialTick) {
        float t = tensionLerped(partialTick);
        if (t <= safeRatio) {
            return 0.0f;
        }
        if (t <= dangerRatio) {
            return (t - safeRatio) / Math.max(0.001f, dangerRatio - safeRatio) * 0.6f;
        }
        return 0.6f + (t - dangerRatio) / Math.max(0.001f, 0.98f - dangerRatio) * 0.4f;
    }

    private static int lerpColor(int from, int to, float k) {
        k = Mth.clamp(k, 0.0f, 1.0f);
        int r = (int) Mth.lerp(k, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(k, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(k, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------ 发送
    /**
     * 把本机输入发给服务端（每 tick 一次）。
     *
     * <p><b>按键方案（已定案）</b>：
     * <ul>
     *   <li>左键按住 = 收线；松开 = 放线</li>
     *   <li>左键在提竿窗口内新按下 = 提竿</li>
     *   <li>A / D = 控杆方向</li>
     *   <li>右键在"浮漂飞行 / 等咬钩"期间新按下 = 提前收回</li>
     * </ul>
     *
     * <p><b>右键必须每 tick 都采样，不能只在会话里采样</b>：
     * 抛投本身就是"按住右键蓄力→松手"，也就是说会话开始时右键通常**还是按着的**。
     * 如果只在 {@code inSession()} 为真时才更新 {@link #prevRightDown}，
     * 会话开始那一刻它会残留成 true，玩家接下来那次收回右键就被当成"一直按着"，
     * 上升沿被吃掉，收回请求永远发不出去。
     */
    public void sendInput(Minecraft mc) {
        // ★ 先无条件采样右键边沿（早于 inSession 判断）
        boolean rightDownNow = mc.options != null && mc.options.keyUse.isDown();
        boolean rightPressed = rightDownNow && !this.prevRightDown;
        this.prevRightDown = rightDownNow;

        if (!inSession()) {
            // 会话外必须清掉，否则"上一杆最后按着左键"会让相机拉近残留
            this.primaryHeld = false;
            return;
        }
        LocalPlayer player = mc.player;
        if (player == null) {
            this.primaryHeld = false;
            return;
        }

        boolean primaryHeld = mc.options.keyAttack.isDown();
        this.primaryHeld = primaryHeld;
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();
        int dir = left == right ? 0 : (left ? -1 : 1);
        boolean hookRequest = hookWindow() && primaryHeld;

        // 提前收回：右键**新按下**的那一 tick 置位（边沿触发，不是持续电平）。
        //
        // ⚠ 只在"浮漂飞行中 / 已落水等咬钩"这两个阶段发 —— 和服务端
        //   FishingSession.retrieveEarly() 的判定保持一致，避免玩家在对抗中
        //   一按右键就被判成收回（对抗里右键是收线）。
        boolean retrieveRequest = rightPressed
                && (state == STATE_CASTING || state == STATE_WAITING);

        // 诊断：每 20 tick 打一行"客户端实际读到的输入 + 本地状态"。
        // 玩家报"长按左键完全不动、松手才回落"——这个现象同时指向两种可能：
        //   ① 客户端没把 primaryHeld=true 发出去（按键没读到 / 状态不是 FIGHT）
        //   ② 服务端收到了但没生效
        // 这一行能直接区分：看 左键 是不是 true、state 是不是 6(FIGHT)。
        // （客户端不能引用服务端类，所以用 System.out；日志里搜 "[垂纶/输入]" 就能找到）
        if (this.inputDebugCounter++ % 20 == 0) {
            com.whitecobalt.anglertackle.AnglersTackle.LOGGER.info(
                    "[垂纶/输入] 客户端：左键={} A={} D={} → 发flags=主{}方向{} | 本地state={} inSession={}",
                    primaryHeld, left, right, primaryHeld ? 1 : 0, dir, state, inSession());
        }

        PacketDistributor.sendToServer(
                FishingInputPayload.pack(primaryHeld, hookRequest, retrieveRequest, dir));
        // ⚠ 这里是**每个 tick 都发**，故意不做节流（原先的 inputCooldown 字段已删除）。
        //   曾节流成"每 2 tick 发一次"，而服务端的"输入掉包保护"是 3 tick
        //   没收到就当成松手 —— 余量只剩 1 tick。
        //   任何一次网络抖动漏掉一个包，服务端就以为玩家松手了：
        //     表现① 张力不动（走了"中立回落"而不是"收线"分支）
        //     表现② 距离不缩（收线分支没走）
        //     表现③ HUD 还显示旧状态（客户端靠之前收到的快照）
        //   玩家报的"卡中间 + 继续拉线鱼距不回收"就是这个。
        //   这个包只有 2 个 boolean + 1 个 int，每 tick 发开销可忽略。
    }

    /** 提竿（独立包，避免和每 2 tick 的输入包时序错开）。 */
    public void sendHook() {
        PacketDistributor.sendToServer(new HookPayload());
    }

    /** 会话重置（切换世界/断线）。 */
    public void reset() {
        state = STATE_IDLE;
        tension = stamina = distance = 0;
        prevTension = prevStamina = prevDistance = 0;
        serverChargeProgress = 0;
        primaryHeld = false;
        bobberActive = false;
        bobberPos = Vec3.ZERO;
    }
}
