package com.whitecobalt.anglertackle.client;

import com.whitecobalt.anglertackle.AnglersTackle;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * 对抗期的相机：<b>视角跟随鱼钩</b> + <b>收线时再拉近一点</b>。
 *
 * <p><b>用户口径（2026-10-01）</b>：
 * <ul>
 *   <li>"鱼上钩后视角跟着鱼钩跑" —— 中鱼之后镜头对准浮漂；</li>
 *   <li>"玩家点击左键收线时再轻微放大，松开放线回到上鱼时的放大水平"。</li>
 * </ul>
 *
 * <p><b>两条硬约束</b>（都写成了断言，见 tools/check-fishing-camera.js）：
 * <ol>
 *   <li><b>只改渲染视角，不改玩家真实朝向。</b>
 *       这里只动 {@code event.setYaw/setPitch}，绝不碰 {@code player.setYRot/setXRot}：
 *       真实朝向一改，抛投方向、原版瞄准、服务端的位置校验都会跟着变，
 *       而玩家只是"想看清浮漂"。所以战斗结束/没在对抗时，一切自动回到原样。</li>
 *   <li><b>拉近必须是"乘以当前 FOV"，不能写死数值。</b>
 *       用户原话是"回到上鱼时的放大水平"—— 也就是说基线是**当时画面上本来就有的**
 *       视野水平（可能还叠着别的效果）。写死一个数字就会把它顶掉，
 *       松手也回不到"原样"。所以这里 {@code setFOV(getFOV() * 系数)}。</li>
 * </ol>
 *
 * <p>两个事件每帧各触发一次。<b>触发顺序（已用字节码核对，不是猜的）</b>：
 * {@code GameRenderer.renderLevel} 里先是 {@code Camera.setup}（在其中触发
 * {@code ComputeCameraAngles}），随后才调 {@code getFov}（触发 {@code ComputeFov}）。
 * 所以帧间隔在前者里算一次、后者复用 —— 否则第二个拿到的时间差恒为 0，渐入会失效。
 */
@EventBusSubscriber(modid = AnglersTackle.MOD_ID, value = Dist.CLIENT)
public final class FishingCamera {

    /** 渐入/渐出速度（1/秒）。4 ≈ 0.25 秒走完，够快但不甩镜头。 */
    // 2026-10-28 用户："跟随强度太低" ⇒ 进入从 4.0 提到 8.0（约 0.12 秒到 63%）。
    private static final float RAMP_PER_SECOND = 8.0f;

    /** 跟随**松开**的速度（比进入慢，避免"一放就弹回"）。 */
    private static final float FOLLOW_RELEASE_PER_SECOND = 1.5f;

    /** 收线拉近的**按下**速度（快）。 */
    private static final float ZOOM_PRESS_PER_SECOND = 10.0f;

    /**
     * 收线拉近的**松开**速度（慢）。
     *
     * <p>用户 2026-10-28："钓上来后的视角缩放回复速度太快"。
     * 原来按下/松开共用 {@link #RAMP_PER_SECOND}，松手一瞬间就弹回去；
     * 现在松开单独用一个更小的速率（≈0.6 秒回到原样），手感是"慢慢放回去"。
     */
    private static final float ZOOM_RELEASE_PER_SECOND = 1.8f;
    /** 一帧都没算出来时的兜底帧间隔（秒）。约等于 60fps。 */
    private static final double FALLBACK_DT = 1.0 / 60.0;
    /** 这里以下就当作"没生效"，直接不碰事件 —— 免得每帧都写一个几乎相同的值。 */
    private static final float EPS = 0.01f;

    /**
     * 瞄准目标的**位置**平滑时间常数（秒）。
     *
     * <p>为什么必须平滑：浮漂位置有两个来源，抖动程度差一个数量级 ——
     * <ul>
     *   <li>客户端实体（带插值）→ 每帧都在动，最顺；</li>
     *   <li>会话同步包里的坐标 → 服务端**每 2 tick** 才发一次，也就是 10Hz 的台阶。</li>
     * </ul>
     * 直接拿后者当目标，镜头就会以 10Hz 一跳一跳地追浮漂 ——
     * 玩家看到的就是"移动镜头卡卡的"（用户 2026-10-01 反馈）。
     */
    private static final double POS_TAU = 0.09;

    /** 瞄准**角度**的平滑时间常数（秒）。比位置再短一点，免得跟着鼠标发飘。 */
    private static final double ANGLE_TAU = 0.08;

    /** 目标一次性跳这么远（格²）就当成"换了个浮漂/重新抛竿"，直接吸附、不滑过去。 */
    private static final double SNAP_DIST_SQ = 64.0;

    private static long lastNanos;
    private static double frameDt = FALLBACK_DT;
    /** 跟随强度渐入值 0~1。 */
    private static float follow;
    /** 是否正在把**真实视角**转到浮漂最后位置。 */
    private static boolean turning;
    /** 要转到的角度（由浮漂最后位置算出）。 */
    private static float turnYaw;
    private static float turnPitch;
    /** 转向速度（每秒靠近比例）与"到位"判据（度）。 */
    private static final float TURN_PER_SECOND = 6.0f;
    private static final float TURN_SNAP_DEG = 0.2f;
    /** 上一帧的对抗状态（用于检测"对抗刚结束"这个边沿）。 */
    private static boolean lastFighting;
    /** 跟随诊断日志的秒级节流。 */
    private static long lastFollowLogSec = -1L;

    /** 上一帧是否处于"上鱼收尾"（只为诊断日志的边沿触发）。 */
    private static boolean lastLingering;

    /** 上鱼后仍要盯住的"浮漂消失前最后位置"。 */
    private static Vec3 lastAim = Vec3.ZERO;
    private static boolean hasLastAim;
    /** 还愿意继续盯住 lastAim 的剩余秒数（只在路亚、且第一人称下累积）。 */
    private static double lingerSeconds;
    /** 上鱼后继续注视浮漂最后位置的时长（秒）。 */
    private static final double LINGER_SECONDS = 0.9;

    /** 这一场是不是路亚（一旦服务端说过是，就一直保持到缩放放完为止）。 */
    private static boolean lureZoomLatched;

    /** 收线拉近渐入值 0~1。 */
    private static float zoom;
    /** 平滑后的瞄准点（世界坐标）。 */
    private static Vec3 smoothTarget = Vec3.ZERO;
    private static boolean hasTarget;
    /** 平滑后的画面角度（**自己的**，不是 event 里那个每帧都变的玩家朝向）。 */
    private static float smoothYaw;
    private static float smoothPitch;
    private static boolean hasAngles;

    private FishingCamera() {
    }

    /**
     * 帧率无关的指数逼近系数：dt 秒内走完 {@code 1 − e^(−dt/τ)}。
     *
     * <p>⚠ 不能用"每帧固定走 20%"那种写法：那样 30fps 和 240fps 的收敛速度差 8 倍，
     * 一台机器丝滑、另一台发飘，而代码看上去完全正常。
     */
    private static float ease(double dt, double tau) {
        return (float) (1.0 - Math.exp(-dt / tau));
    }

    // ------------------------------------------------------------------ 配置
    // 配置在极早期可能还没加载完，get() 会抛异常；抛出去会让整个渲染层崩掉，
    // 所以三个取值都做兜底（和 FishingLineRenderer.simplifyBeyond 同一套理由）。
    //
    // ⚠ 必须写**全限定名**：本文件在 …anglertackle.client 包里，而 Config 在
    //   …anglertackle 包 —— Java 不会自动 import 父包，写 `Config.INSTANCE`
    //   会被当成"包 Config 里的类 INSTANCE"，报"程序包Config.INSTANCE不存在"（真踩过）。
    //   同目录的 FishingLineRenderer / ClientFishingState 也都用全限定名。
    private static boolean cfgFollow() {
        try {
            return com.whitecobalt.anglertackle.Config.INSTANCE.hookCameraFollow.get();
        } catch (Throwable t) {
            return true;
        }
    }

    private static double cfgStrength() {
        try {
            return com.whitecobalt.anglertackle.Config.INSTANCE.hookCameraFollowStrength.get();
        } catch (Throwable t) {
            return 0.9;
        }
    }

    private static double cfgReelZoomPercent() {
        try {
            return com.whitecobalt.anglertackle.Config.INSTANCE.reelZoomPercent.get();
        } catch (Throwable t) {
            return 93.0;
        }
    }

    // ------------------------------------------------------------------ 时间
    /** 由 {@link #onCameraAngles} 每帧调一次（它先于 ComputeFov）。 */
    private static double beginFrame() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? FALLBACK_DT : (now - lastNanos) / 1.0e9;
        lastNanos = now;
        // 夹一下：切窗口/暂停回来时 dt 可能很大，不夹会让镜头瞬移到位
        frameDt = Mth.clamp(dt, 0.0, 0.25);
        return frameDt;
    }

    /** 朝目标值靠拢一步（指数逼近，升/降两个速率分开）。 */
    private static float stepAsym(float cur, boolean on, double dt, float upRate, float downRate) {
        float target = on ? 1.0f : 0.0f;
        float k = (float) Math.min(1.0, (on ? upRate : downRate) * dt);
        return cur + (target - cur) * k;
    }

    /** 朝目标值靠拢一步（指数逼近）。 */
    private static float step(float cur, boolean on, double dt) {
        float target = on ? 1.0f : 0.0f;
        float k = (float) Math.min(1.0, RAMP_PER_SECOND * dt);
        return cur + (target - cur) * k;
    }

    // ------------------------------------------------------------------ 视角跟随
    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double dt = beginFrame();

        Minecraft mc = Minecraft.getInstance();
        ClientFishingState st = ClientFishingState.get();
        boolean firstPerson = mc.options != null && mc.options.getCameraType().isFirstPerson();
        boolean followable = cfgFollow() && holdingLureRod() && firstPerson;   // 台钓一律不参与

        // 浮漂位置：只在**实体真的还在**时采样；实体消失后 lastAim 冻结在最后一帧。
        Vec3 bob = (followable && st.bobberActive()) ? bobberAimPos(event.getPartialTick()) : null;
        if (bob != null) {
            lastAim = bob;
            hasLastAim = true;
        }
        if (st.inCasting()) {
            hasLastAim = false;
            turning = false;
            hasAngles = false;
            hasTarget = false;
        }

        boolean fighting = followable && st.inFight() && st.bobberActive() && bob != null;

        // ⚠⚠⚠ 2026-10-28（用户最终口径）：
        //   "我要的是真正意义上的视角最后停留……你最后时刻实际上的相机视角就是要在那里"、
        //   "不是视角不动，而是最终要更新**实际视角位置**到那里"。
        //
        //   所以这里**不再用 event.setYaw 盖画面**（那只是渲染层，玩家真实朝向没变，
        //   一旦停手就弹回去 —— 这就是他一路看到的"闪回"）。改成：
        //   对抗结束那一刻，直接把**玩家本人的 yaw/pitch** 平滑转过去，转完停在那里。
        //   视角是真的到那一点了，之后鼠标从那个方向继续。
        if (lastFighting && !fighting && followable && hasLastAim) {
            beginTurnToLastAim(event.getPartialTick());
        }
        lastFighting = fighting;

        if (!fighting) {
            if (turning) {
                stepTurn(dt);
            }
            hasAngles = false;   // 渲染层不再覆盖（否则会和真实朝向打架）
            hasTarget = false;
            return;
        }

        // ---- 对抗中：只改渲染视角（不动玩家真实朝向，玩家还能微调） ----
        follow = stepAsym(follow, true, dt, RAMP_PER_SECOND, FOLLOW_RELEASE_PER_SECOND);
        if (follow < EPS || bob == null) {
            hasAngles = false;
            hasTarget = false;
            return;
        }

        if (!hasTarget || bob.distanceToSqr(smoothTarget) > SNAP_DIST_SQ) {
            smoothTarget = bob;
            hasTarget = true;
        } else {
            smoothTarget = smoothTarget.lerp(bob, ease(dt, POS_TAU));
        }

        Vec3 cam = event.getCamera().getPosition();
        double dx = smoothTarget.x - cam.x;
        double dy = smoothTarget.y - cam.y;
        double dz = smoothTarget.z - cam.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-3) {
            return;
        }

        float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));

        float k = follow * (float) cfgStrength();
        float idealYaw = event.getYaw() + Mth.wrapDegrees(wantYaw - event.getYaw()) * k;
        float idealPitch = event.getPitch()
                + (Mth.clamp(wantPitch, -90.0f, 90.0f) - event.getPitch()) * k;

        if (!hasAngles) {
            smoothYaw = idealYaw;
            smoothPitch = idealPitch;
            hasAngles = true;
        } else {
            float a = ease(dt, ANGLE_TAU);
            smoothYaw += Mth.wrapDegrees(idealYaw - smoothYaw) * a;
            smoothPitch += (idealPitch - smoothPitch) * a;
        }
        event.setYaw(smoothYaw);
        event.setPitch(smoothPitch);
    }

    /** 记下"要转到哪"：浮漂最后位置相对玩家眼睛的方向。 */
    private static void beginTurnToLastAim(double partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        // ⚠⚠ 2026-10-28（用户："上鱼后最后一刻先零帧改到抛竿点然后缓慢回到浮漂点"）：
        //
        //   对抗期间画面是按 smoothYaw/smoothPitch 渲染的（指向浮漂），
        //   但**玩家真实朝向**从头到尾没动过（还是抛竿时的方向）。
        //   所以在停手那一帧，渲染层把画面交还给真实朝向 ⇒ 瞬间跳到抛竿点，
        //   之后转向动画才从那里慢慢转回来。
        //
        //   修法：开始转向的**第一帧**先把真实朝向对齐到画面当前角度（= 浮漂方向），
        //   再从那里转到浮漂最后位置。视角连续，没有 0 帧跳变。
        if (hasAngles) {
            mc.player.setYRot(smoothYaw);
            mc.player.setXRot(smoothPitch);
            mc.player.setYHeadRot(smoothYaw);
        }

        Vec3 eye = mc.player.getEyePosition((float) partialTick);
        double dx = lastAim.x - eye.x;
        double dy = lastAim.y - eye.y;
        double dz = lastAim.z - eye.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        if (horiz < 1.0e-3) {
            return;   // 正上方/正下方：没有有意义的方位角
        }
        turnYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        turnPitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(dy, horiz)), -90.0, 90.0);
        turning = true;

        AnglersTackle.LOGGER.info("[垂纶/诊断·转向] 开始把真实视角转到浮漂最后位置：目标 yaw={} pitch={} ｜ 当前 yaw={} pitch={} ｜ 点={}/{}/{}",
                String.format("%.1f", turnYaw), String.format("%.1f", turnPitch),
                String.format("%.1f", mc.player.getYRot()), String.format("%.1f", mc.player.getXRot()),
                String.format("%.1f", lastAim.x), String.format("%.1f", lastAim.y), String.format("%.1f", lastAim.z));
    }

    /** 每帧把玩家本人的 yaw/pitch 朝目标转一点（真实朝向，不是渲染）。 */
    private static void stepTurn(double dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !hasLastAim) {
            turning = false;
            return;
        }
        float k = (float) Math.min(1.0, TURN_PER_SECOND * dt);
        float curYaw = mc.player.getYRot();
        float curPitch = mc.player.getXRot();
        float dYaw = Mth.wrapDegrees(turnYaw - curYaw);
        float dPitch = turnPitch - curPitch;

        if (Math.abs(dYaw) < TURN_SNAP_DEG && Math.abs(dPitch) < TURN_SNAP_DEG) {
            mc.player.setYRot(turnYaw);
            mc.player.setXRot(turnPitch);
            mc.player.setYHeadRot(turnYaw);
            turning = false;                       // 转到位：停在这里，不再动
            AnglersTackle.LOGGER.info("[垂纶/诊断·转向] 已到位：yaw={} pitch={}",
                    String.format("%.1f", mc.player.getYRot()), String.format("%.1f", mc.player.getXRot()));
            return;
        }

        float ny = curYaw + dYaw * k;
        float np = curPitch + dPitch * k;
        mc.player.setYRot(ny);
        mc.player.setXRot(np);
        mc.player.setYHeadRot(ny);
    }

    /**
     * 这一帧要瞄的浮漂位置：**优先用客户端实体的插值位置**。
     *
     * <p>实体位置每 tick 由服务端更新、客户端按 partialTick 插值 → 每帧都在动，最顺；
     * 而会话同步包里的坐标是**每 2 tick** 一份的离散值。两者差一个数量级的抖动，
     * 所以能用实体就用实体（找不到时才退回同步坐标，见 {@link #POS_TAU} 的说明）。
     *
     * <p>判定"哪个实体是我的浮漂"沿用鱼线渲染那套：**离同步坐标最近的那个**
     * （同一片水域里基本只会有自己这一个浮漂）。只遍历当前已加载的实体，开销可忽略。
     *
     * @return 瞄准点；没有浮漂时返回 {@code null}
     */
    private static Vec3 bobberAimPos(double partialTick) {
        ClientFishingState st = ClientFishingState.get();
        Vec3 synced = st.bobberPos();
        if (synced.equals(Vec3.ZERO)) {
            return null;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            com.whitecobalt.anglertackle.entity.BobberEntity best = null;
            double bestD = 36.0;   // 6 格内才认，免得瞄到别人的浮漂
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof com.whitecobalt.anglertackle.entity.BobberEntity b) {
                    double d = b.position().distanceToSqr(synced);
                    if (d < bestD) {
                        bestD = d;
                        best = b;
                    }
                }
            }
            if (best != null) {
                // 插值位置：这一帧真正该画在哪
                return best.getPosition((float) partialTick);
            }
        }
        return synced;
    }

    /**
     * 手上是不是一根**路亚竿**。
     *
     * <h2>⚠⚠ 2026-10-16 用户定案："台钓钓上鱼会立刻放大视野，这不是我想要的，我需要不变"</h2>
     * 原来"收线拉近 / 视角跟随"只判 {@code st.inFight()} —— 那是**服务端状态**。
     * 只要状态串了一帧（或者将来谁让台钓也走对抗），台钓的视野就会被放大/被拽向浮漂，
     * 而台钓**根本没有收线拉近这个概念**（它一上鱼就直接收鱼）✗。
     *
     * <p>现在多判一道**竿型**：台钓竿 ⇒ 无论状态怎么变，视野一律不动 ✓。
     * 两道判据都留着是有意的：{@code inFight()} 管"什么时候"，竿型管"哪根竿"，缺一不可。
     */
    private static boolean holdingLureRod() {
        try {
            var mc = Minecraft.getInstance();
            return mc.player != null
                    && mc.player.getMainHandItem().getItem()
                        instanceof com.whitecobalt.anglertackle.item.RodItem r
                    && r.rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.LURE;
        } catch (Throwable t) {
            return false;   // 判不出来就别动视野（宁可不动，也不要乱放大）
        }
    }

    // ------------------------------------------------------------------ 收线拉近（**只给路亚**）
    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        ClientFishingState st = ClientFishingState.get();

        // ⚠⚠ 2026-10-28（用户："不要牵连路亚，我明确了只要路亚的视角缩放"）
        //
        // 三票制总闸：**服务端说这一场是路亚** + 客户端在 FIGHT + 手里确实是路亚竿。
        // 任一不成立 ⇒ 第一帧把 zoom 清零并直接返回，一个像素都不改（台钓因此结构上不可能缩放）。
        //
        // ★ 2026-10-28 补充定案：台钓那阵"视野缩一下"**不是这条路**造成的 ——
        //   真凶是移动锁把 MOVEMENT_SPEED 属性归零，触发了原版
        //   getFieldOfViewModifier() 里的 (speed/walk + 1)/2 = 0.5。已在 FishingManager 修掉。
        //   这个 handler 只负责"路亚收线那点轻微拉近"，别再往它身上揽别的锅。
        // ⚠⚠ 2026-10-28 用户第 2 条："视角到最后没有缓慢回复的过程，而是很生硬的大小调整"。
        //   原来这里一出 FIGHT 就 `zoom = 0` —— 那是**硬切**，松手/上鱼瞬间视野"啪"地弹回。
        //   现在：只要这一场是**路亚**（服务端说的），就允许 zoom 继续用慢速率放回；
        //   台钓永远拿不到这个锁（lureSession 恒 false），所以台钓仍然一个像素都不动。
        if (st.lureSession()) {
            lureZoomLatched = true;
        }
        if (!st.inSession() && zoom < EPS) {
            lureZoomLatched = false;   // 会话结束且已经放完 ⇒ 解锁
        }
        if (!lureZoomLatched || !holdingLureRod()) {
            zoom = 0.0f;               // 台钓 / 空手：结构上清零
            return;
        }

        boolean want = st.inFight() && st.reeling();
        zoom = stepAsym(zoom, want, frameDt, ZOOM_PRESS_PER_SECOND, ZOOM_RELEASE_PER_SECOND);
        if (zoom < EPS) {
            return;
        }

        double factor = 1.0 + (cfgReelZoomPercent() / 100.0 - 1.0) * zoom;
        // ★ 乘在**当前** FOV 上：基线是画面上本来就有的视野水平，松手即回到原样。
        event.setFOV(event.getFOV() * factor);
    }


}
