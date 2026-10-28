package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.entity.BobberEntity;
import com.whitecobalt.anglertackle.item.RodItem;
import com.whitecobalt.anglertackle.network.ModNetwork;
import com.whitecobalt.anglertackle.network.SessionSyncPayload;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import com.whitecobalt.anglertackle.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 所有钓鱼会话的持有者（服务端单例）。
 *
 * <p>在 {@code ServerTickEvent.Post} 上推进所有活跃会话——没有会话时零开销。
 * 这样设计而不是挂在实体或玩家 tick 上，是为了避免和别的模组抢 tick
 * （docs/09-原版冲突与渲染优化.md §2 第 6 条）。
 */
public final class FishingManager {

    private static final FishingManager INSTANCE = new FishingManager();

    /** 日志（调试抛投/会话状态用）。 */
    public static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Angler's Tackle/Fishing");

    /** 蓄力上限：超过这个时长没松手就放弃（防止玩家挂机按住右键）。 */
    private static final long MAX_CHARGE_TICKS = 60L;

    /**
     * 估不出落差时用的兜底值（格）：岸边站着抛，浮漂起点大约在水面上方这么多。
     *
     * <p>见 {@link #estimateDrop}：射程反解必须知道"起点比落点高多少"。
     */
    private static final double FALLBACK_DROP = 1.9;
    /** 一次完整蓄力所需的 tick 数（1.6 秒）。 */
/*__DECL_Tuning.fullChargeTicks()__*/
    /** 低于这个 tick 数视为误触（点一下右键）。 */
/*__DECL_Tuning.minChargeTicks()__*/

    /** 每个玩家同时只能有一个会话（docs/02 §1）。 */
    private final Map<UUID, FishingSession> sessions = new HashMap<>();

    /** "竿已破损"提示的上次弹出时间，用于防刷屏。 */
    private final Map<UUID, Long> brokenMsgAt = new HashMap<>();

    /**
     * 对抗期间被临时归零的移动速度（玩家 UUID → 原值）。
     * 对抗结束、掉线、会话清理时都要还原，否则玩家会永久走不动。
     */
    private final Map<UUID, Double> savedMoveSpeed = new HashMap<>();

    /**
     * 移动锁期间**锁定的位置**（玩家 UUID → 触发锁那一刻的坐标）。
     *
     * <p>解锁 = 每 tick 把玩家按回这里。选"按位置"而不是"把速度属性归零"，
     * 是因为后者会通过原版的 FOV 公式把视野砍半（见锁定块里的注释）。
     */
    private final Map<UUID, net.minecraft.world.phys.Vec3> lockPositions = new HashMap<>();

    /** 还原被对抗锁住的移动速度。 */
    private void restoreMoveSpeed(ServerPlayer player) {
        Double saved = savedMoveSpeed.remove(player.getUUID());
        if (saved == null) {
            return;
        }
        var attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        if (attr != null) {
            attr.setBaseValue(saved);
        }
    }

    /**
     * **上鱼之后**还要把移动速度锁住的截止时刻（玩家 UUID → 到期 gameTime）。
     *
     * <h2>⚠⚠ 为什么需要它（2026-10-16 用户报"移动没锁一秒"）</h2>
     * "上鱼后一秒不许移动"原来只靠 {@link #savedMoveSpeed} 那张表，
     * 而它在 {@link #end} 里就被还原了 —— 而 {@code end()} 恰好就是"给鱼"那一刻 ✗。
     * 于是那一秒里服务端的速度属性**已经恢复到正常值**，只剩客户端清冲量；
     * 而客户端清冲量"压不住已经带在身上的动量与水流"（见下面 tick 里那段注释，我自己写过）。
     * ⇒ 玩家一收线就带着惯性走出去，感觉"根本没锁"。
     *
     * <p>现在把这一秒**独立记时**：上鱼时写下"锁到 gameTime + N"，
     * 每 tick 只要没到期就继续压着速度属性 —— 与"会话还在不在"彻底解耦 ✓。
     * 时长只有一个真源：{@link FishingSession#CATCH_MOVE_LOCK_TICKS}（同时发给客户端倒计时）。
     */
    private final Map<UUID, Long> moveLockUntil = new HashMap<>();

    /** ★诊断用：上一次的"压/不压"（定位完删）。 */
    private final Map<UUID, Boolean> lastLockDiag = new HashMap<>();

    /** 上鱼那一刻记下"移动还要锁到哪个 gameTime"。 */
    public void markCatchMoveLock(ServerPlayer player, int ticks) {
        if (player == null || ticks <= 0) {
            return;
        }
        long until = player.serverLevel().getGameTime() + ticks;
        moveLockUntil.merge(player.getUUID(), until, Math::max);
        LOGGER.info("[垂纶/诊断·锁] 拿锁：{} → 锁到 gameTime {}（现在 {}，共 {} tick）",
                player.getName().getString(), until, player.serverLevel().getGameTime(), ticks);
    }

    /** 这一 tick 该不该因为"上鱼后那一秒"压住移动速度。 */
    private boolean catchLockActive(ServerPlayer player) {
        Long until = moveLockUntil.get(player.getUUID());
        return until != null && player.serverLevel().getGameTime() < until;
    }

    /** 锁到点了就清掉（连带还原速度属性）。 */
    private void expireCatchMoveLock(ServerPlayer player) {
        if (!moveLockUntil.containsKey(player.getUUID()) || catchLockActive(player)) {
            return;
        }
        long now = player.serverLevel().getGameTime();
        Long until = moveLockUntil.remove(player.getUUID());
        restoreMoveSpeed(player);
        LOGGER.info("[垂纶/诊断·锁] 解锁：{}（到期 {}，现在 {}）",
                player.getName().getString(), until, now);
    }

    private FishingManager() {
    }

    public static FishingManager get() {
        return INSTANCE;
    }

    // =================================================================== 查询
    @Nullable
    public FishingSession sessionOf(ServerPlayer player) {
        return sessions.get(player.getUUID());
    }

    public boolean isFishing(ServerPlayer player) {
        return sessions.containsKey(player.getUUID());
    }

    // =================================================================== 抛投
    /**
     * 按**玩家视角俯仰**抛投（新的抛投模型，取代原来的按蓄力时长）。
     *
     * <p>玩家用抬头/低头控制距离，和真实甩竿一致：
     * <ul>
     *   <li>低头看水面（俯角 ≤ −25°）→ 近抛，约 10 格</li>
     *   <li>平视（−25° ~ +5°）→ 中抛，约 20 格</li>
     *   <li>抬头（> +5°）→ 远抛，最多约 30 格</li>
     * </ul>
     *
     * <p>发射仰角**就是玩家的视线仰角**（竿梢指向哪就飞哪），
     * 初速度用 {@link #solveSpeed} 反解，使落点正好等于目标距离。
     * 这样"抬头 45° 抛最远"这个直觉是对的，玩家不用记任何数值。
     */
    /**
     * 精准入洞：鱼竿**右键冰洞**时走这条路（用户 2026-10-08 定案，docs/33 §8.3）。
     *
     * <p>与 {@link #castByLook} 共用同一套前置检查（已有会话 / 竿已破损 / 服务端），
     * 只是**不反解初速、不发射**，直接把浮漂放到洞心水面 ✓
     */
    public void castIntoIceHole(ServerPlayer player, net.minecraft.core.BlockPos hole) {
        if (sessions.containsKey(player.getUUID())) {
            FishingSession s = sessions.get(player.getUUID());
            if (s != null && s.state().isFighting()) {
                s.setReeling(false);
            }
            return;
        }
        if (isBrokenRod(player)) {
            player.displayClientMessage(Component.translatable("message.anglertackle.rod_broken")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        FishingSession session = new FishingSession(player);
        sessions.put(player.getUUID(), session);
        session.beginCharge(level.getGameTime());
        session.castIntoIceHole(level, hole);
    }

    public void castByLook(ServerPlayer player, float pitch) {
        if (sessions.containsKey(player.getUUID())) {
            // 已经有会话：这次"松手"当作停止收线
            FishingSession s = sessions.get(player.getUUID());
            if (s != null && s.state().isFighting()) {
                s.setReeling(false);
            }
            return;
        }
        if (isBrokenRod(player)) {
            // 防刷屏：玩家连点右键时不要把同一句话弹十几次
            long now = player.level().getGameTime();
            Long last = brokenMsgAt.get(player.getUUID());
            if (last == null || now - last > 40L) {
                brokenMsgAt.put(player.getUUID(), now);
                player.displayClientMessage(
                        Component.translatable("message.anglertackle.rod_broken")
                                .withStyle(ChatFormatting.RED), true);
            }
            LOGGER.info("[垂纶] 抛投被拦：竿已破损（{}）", RodItem.durabilityInfo(player.getMainHandItem()));
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        // 视线仰角：MC 的 xRot 是俯角（向下为正），取负换成仰角
        double eyeDeg = -pitch;

        // ★ 抛投距离（用户定案，物理模型下的**约数**）：
        //     低抛（低头看水面）≈ 8 米
        //     平视              ≈ 16 米
        //     高抛（抬头，不超过 45°）≈ 24 米
        // 这里给的是"目标落点"，真实落点由 solveSpeed 反解初速度后
        // 在 MC 物理下自然得到，所以实际会在这几个值附近浮动。
        double distance = eyeDeg < -20 ? 8.0 : eyeDeg < 20 ? 16.0 : 24.0;

        // 发射仰角：直接用视线角度，但夹到 8°~45° ——
        // 用户要求"仰视不超过 45 度角"，太平会贴水面、太陡会垂直砸下来。
        double launchDeg = Math.max(8.0, Math.min(45.0, eyeDeg));
        double angle = Math.toRadians(launchDeg);
        // ★ 反解必须带上"起点比水面高多少"（见 estimateDrop）：
        //   漏掉它，浅发射角下浮漂会多飞好几米（低头 8 米打成 13.6 米）。
        double drop = estimateDrop(player, distance);
        double speed = solveSpeed(distance, angle, drop);

        FishingSession session = new FishingSession(player);
        sessions.put(player.getUUID(), session);
        session.beginCharge(level.getGameTime());
        session.castProjectile(level, speed, launchDeg);

        LOGGER.info("[垂纶] 抛投：俯仰 {}° → 目标 {} 米，仰角 {}°，落差 {} 格，初速 {}（反解）",
                String.format("%.1f", pitch), distance,
                String.format("%.1f", launchDeg), String.format("%.2f", drop),
                String.format("%.3f", speed));
    }

    /**
     * 浮漂起点比水面高出多少格 —— 射程反解的唯一未知量。
     *
     * <p><b>为什么必须算它：</b>{@code simulateRange} 原来是"起点与落点等高"（y 从 0 起），
     * 但浮漂是从**眼睛上 0.2 格**出竿、落到**水面**上的：岸边站着差约 1.9 格、
     * 坐船上约 1.6 格、站在浅水里不到 1 格。浅发射角下这点落差能多飞好几米，
     * 于是"低头 8 米"实际打到 13.6 米。用户 2026-10-02：
     * "希望低头落点平视落点抬头落点跟之前差不多"——指的就是回到设计的 8 / 16 / 24 米。
     *
     * <p>做法：沿视线的**水平方向**往前一列一列扫，每列从眼睛高度往下找
     * 第一个水面方块，用它的水面高度估落差。找不到（朝陆地抛、水面太远）
     * 就退回 {@link #FALLBACK_DROP}。
     */
    private static double estimateDrop(ServerPlayer player, double target) {
        net.minecraft.world.phys.Vec3 eye = player.getEyePosition().add(0, 0.2, 0);
        net.minecraft.world.phys.Vec3 look = player.getLookAngle();
        net.minecraft.world.phys.Vec3 flat = new net.minecraft.world.phys.Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0e-6) {
            return FALLBACK_DROP;                          // 垂直看天/看地：水平方向没有意义
        }
        flat = flat.normalize();
        ServerLevel level = player.serverLevel();
        int limit = (int) Math.min(48.0, Math.ceil(target) + 16.0);
        for (int d = 1; d <= limit; d++) {
            double cx = eye.x + flat.x * d;
            double cz = eye.z + flat.z * d;
            // 每列从眼睛高度往下扫，取**最上面**那个水面方块
            for (int dy = 2; dy >= -24; dy--) {
                net.minecraft.core.BlockPos p = net.minecraft.core.BlockPos.containing(cx, eye.y + dy, cz);
                net.minecraft.world.level.material.FluidState fluid = level.getFluidState(p);
                if (fluid.isEmpty() || fluid.getType() != net.minecraft.world.level.material.Fluids.WATER) {
                    continue;
                }
                double surface = p.getY() + fluid.getHeight(level, p);
                double drop = eye.y - surface;
                return Math.max(0.4, Math.min(4.0, drop));
            }
        }
        return FALLBACK_DROP;
    }

    /**
     * 反解初速度：在 MC 的抛物线 + 空气阻力下，让落点距离等于 {@code target}。
     *
     * <p>MC 实体每 tick 先乘以阻力 0.99，再减去重力 0.03，所以射程不能用中学公式。
     * 这里直接做一次数值模拟，二分搜索出合适的初速度——比推公式可靠，
     * 也不会因为 MC 改了物理参数而失效。
     *
     * @param drop 起点比落点（水面）高多少格 —— 见 {@link #estimateDrop}。
     *             ⚠️ 传 0 就是"等高落地"的错误模型：浅发射角下实际会多飞好几米。
     */
    private static double solveSpeed(double target, double angleRad, double drop) {
        double lo = 0.2, hi = 4.0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            double r = simulateRange(mid, angleRad, drop);
            if (r < target) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return (lo + hi) / 2;
    }

    /**
     * 模拟一次抛投的水平射程（格）。参数与 MC 实体物理保持一致。
     *
     * @param drop 起点高度（格）：起点在水面上方 drop 处，落到 y=0 为止。
     */
    private static double simulateRange(double speed, double angleRad, double drop) {
        final double drag = 0.99, gravity = 0.03;
        double vx = speed * Math.cos(angleRad);
        double vy = speed * Math.sin(angleRad);
        double x = 0, y = drop;                          // ★ 起点比落点高 drop 格
        for (int t = 0; t < 400; t++) {
            x += vx;
            y += vy;
            vx *= drag;
            vy = vy * drag - gravity;
            if (y <= 0 && t > 2) {
                break;
            }
        }
        return x;
    }

    /** 手里的竿是不是已经打坏了。 */
    private static boolean isBrokenRod(ServerPlayer player) {
        return com.whitecobalt.anglertackle.item.RodItem.isBroken(player.getMainHandItem())
                || com.whitecobalt.anglertackle.item.RodItem.isBroken(player.getOffhandItem());
    }

    /** 当前会话状态名（日志与诊断用）。 */
    public String stateName(net.minecraft.world.entity.player.Player player) {
        FishingSession s = sessions.get(player.getUUID());
        return s == null ? "无" : s.state().name();
    }

    /**
     * 对抗中的上鱼判定快照（诊断用）；不在对抗中返回 null。
     *
     * <p>给 {@code /atl rod} 用。玩家反馈"蓝条满不上鱼"这类问题时，
     * 让他敲一下命令把两个数和判定结果贴回来，比翻日志猜快得多。
     */
    public String landCheckInfo(net.minecraft.world.entity.player.Player player) {
        FishingSession s = sessions.get(player.getUUID());
        return s == null ? null : s.landCheckInfo();
    }

    // =================================================================== 回调
    /** 浮漂落水：从 CASTING 进入 WAITING。 */
    public void onBobberLanded(ServerLevel level, ServerPlayer player, BobberEntity bobber,
                               int depth, boolean flowing) {
        FishingSession session = sessions.get(player.getUUID());
        if (session == null || session.bobber() != bobber) {
            bobber.discard();
            return;
        }
        session.beginWaiting(depth, flowing);
    }

    /** 落点无效：不扣耐久，直接结束会话。 */
    public void onCastFailed(ServerPlayer player, String messageKey) {
        FishingSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.cleanup();
        }
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.YELLOW), true);
    }

    /** 断线：饵损失 + 鱼线额外 −5 + 8 秒冷却。 */
    public void onLineSnapped(FishingSession session, ServerLevel level) {
        ServerPlayer player = session.player();
        // 称号「断线专家」：这里是**断线的唯一出口**（红区超时 / 张力越界都走它）⇒
        // 计数放这一处就不会一条鱼断两次、也不会漏 ✗。
        try {
            com.whitecobalt.anglertackle.title.Titles.bump(player,
                    com.whitecobalt.anglertackle.title.Titles.K_LINE_BREAK, 1);
            com.whitecobalt.anglertackle.title.Titles.syncAuto(player);
        } catch (Throwable t) {
            LOGGER.warn("[垂纶] 断线计数失败（已忽略）：{}", t.toString());
        }
        player.displayClientMessage(
                Component.translatable("message.anglertackle.line_snapped").withStyle(ChatFormatting.RED), true);
        level.playSound(null, player.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 1.0f, 0.5f);
        // 断线额外损耗鱼线耐久（配置 durability.linePerBreak）。
        // 咬钩那一下已经扣过 durability.linePerBite，这里是"崩断"的额外惩罚。
        // 扣到 0 表示线废了——不在这里销毁物品，让玩家自己看到"耐久见底的线"再决定换。
        int extra = Math.max(0, com.whitecobalt.anglertackle.Config.INSTANCE
                .lineDurabilityPerBreak.get());
        if (extra > 0) {
            ItemStack rod = player.getMainHandItem();
            ModDataComponents.RodParts parts = rod.get(ModDataComponents.ROD_PARTS.get());
            if (rod.getItem() instanceof RodItem && parts != null && !parts.lineStack().isEmpty()) {
                ItemStack line = parts.lineStack().copy();
                if (line.getMaxDamage() > 0) {
                    int old = line.getDamageValue();
                    int now = Math.min(line.getMaxDamage(), old + extra);
                    line.setDamageValue(now);
                    rod.set(ModDataComponents.ROD_PARTS.get(), parts.withLine(line));
                    // 和咬钩扣耐久同理：主手物品的服务端改动不会自动推给客户端，
                    // 不显式同步的话玩家开背包会看到"耐久回滚"。
                    try {
                        int slot = 36 + player.getInventory().selected;
                        player.connection.send(
                                new net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket(
                                        player.inventoryMenu.containerId,
                                        player.inventoryMenu.incrementStateId(), slot, rod.copy()));
                    } catch (Exception ignored) {
                        // 同步失败不影响玩法
                    }
                    FishingManager.LOGGER.info("[垂纶] 断线：鱼线额外损耗 -{}（现 {}/{}）",
                            now - old, line.getMaxDamage() - now, line.getMaxDamage());
                }
            }
        }
        player.getCooldowns().addCooldown(ModItems.WOOD_FLOAT_ROD.get(), 160);   // 8s
        end(session);
    }

    /** 鱼逃出张力范围。 */
    public void onEscaped(FishingSession session, ServerLevel level) {
        session.player().displayClientMessage(
                Component.translatable("message.anglertackle.escaped").withStyle(ChatFormatting.YELLOW), true);
        session.player().getCooldowns().addCooldown(ModItems.WOOD_FLOAT_ROD.get(), 80);   // 4s
        end(session);
    }

    /**
     * 上鱼成功：按鱼种表 roll 出这条鱼的个体数据（体长/体重/评级）再交给玩家。
     *
     * <p>之前这里写死"永远给鲫鱼、不带数据"，所以每条鱼长得一模一样、
     * tooltip 也只有一个"暂无数据"。现在由 {@link FishSpecies#rollIndividual} 生成，
     * 同种鱼也能有无数个不同尺寸的个体（图鉴与排行榜都读这份数据）。
     */
    public void onCatch(FishingSession session) {
        ServerPlayer player = session.player();

        // 用咬钩时就定下来的那条鱼（保证"咬钩的是谁，钓上来的就是谁"）。
        // 极端情况下没有（比如别的路径直接进了收鱼），就地补抽一条兜底。
        FishSpecies species = session.hookedSpecies();
        FishData data = session.hookedData();
        if (species == null || data == null) {
            net.minecraft.util.RandomSource r = player.getRandom();
            species = FishSpecies.roll(r, session.rodKindPublic(),
                    session.maxTier(), session.baitMinTier(),
                    session.maxWeightKg(), session.castRange(),
                    // ★ 与 FishingSession 同口径：档位加成 + 金红占比目标 + 习性词条。
                    //   ⚠ 这三项必须**逐项**跟 FishingSession 那条链一致 ——
                    //   两处口径一旦分叉，兜底抽出来的鱼就和正常咬钩的不是一个分布。
                    session::baitTierBonus,
                    session.highValueBonus(session.equippedBaitTier()),
                    // speciesMinTier = 0：兜底路径没有"测试拟饵必出红"的上下文
                    0,
                    null,
                    session.equippedLureHabitMask());
            data = species.rollIndividual(r, player.level().getGameTime(),
                    player.getGameProfile().getName(),
                    session.baitMinTier());   // ★ 同 FishingSession：个体品质卡饵料下限
        }
        ItemStack reward = new ItemStack(species.item().get());
        reward.set(com.whitecobalt.anglertackle.registry.ModDataComponents.FISH_DATA.get(), data);

        if (!player.getInventory().add(reward)) {
            player.drop(reward, false);   // 背包满 → 掉在脚下，不丢失（docs/02 §8）
        }
        player.displayClientMessage(
                Component.translatable("message.anglertackle.caught",
                                reward.getHoverName(),
                                String.format("%.1f", data.lengthCm()),
                                String.format("%.2f", data.weightKg()))
                        .withStyle(ChatFormatting.GREEN), true);

        // ---- 鱼币：这里**不给钱** ----
        //
        // 用户定案："不要直接跳鱼币，而是将数据显示在鱼面板，到时候方便做 NPC 商店时卖出拿钱。"
        //
        // 经济闭环是：钓鱼 → 鱼进背包（带着品质/体长/重量/**售价**的数据面板）
        // → 拿去 NPC 商店卖掉 → 才拿到鱼币。
        //
        // ⚠ 曾经在这里直接 addCoins(售价)：那等于"钓上来就自动卖掉"，
        //   玩家永远不需要商店，背包里的鱼也失去意义 —— 而 FishItem 的售价提示
        //   正是为"挑哪条卖"服务的，先给钱就把它废掉了。
        //   想恢复成"上鱼即结算"的话改这一处即可，定价逻辑在 CoinPricing 里没动。
        //
        // 现在唯一会改余额的是 /atl coins（调试）和将来的商店。
        // 上鱼只打一行日志，方便对账：这条鱼值多少，卖的时候应该给多少。
        int tier = data.effectiveTier();

        // ---- 图鉴记账 ----
        //
        // 用户定案：图鉴用**一本新书**打开，看"收集到哪一步"。
        // 这里只负责**记**，不负责发奖 —— 发奖的幂等与去重统一放 CodexRewards，
        // 免得"记一笔"和"发一次奖"两件事散在不同地方，迟早对不上。
        //
        // ⚠ tier 必须用 data.effectiveTier()（存下来的真实档），不能从 SizeGrade 反推：
        //   金(4)和红(5)在 SizeGrade 上都落在 RECORD，反推会把金鱼记成红鱼。
        //   图鉴要显示"这条鱼我钓到过的最高品质"，记错了就再也纠正不回来。
        boolean firstCatch = !com.whitecobalt.anglertackle.player.PlayerData.get(player)
                .hasCaught(species.id());
        // ⚠ 必须先拷成 final：species / data 在上面那个"兜底补抽"分支里被**重新赋值**过，
        //   不是 effectively final，lambda 抓不了（编译期就报错，算好事）。
        final String codexId = species.id();
        final float codexKg = data.weightKg();
        final float codexCm = data.lengthCm();
        final int codexGrade = data.grade().ordinal();
        com.whitecobalt.anglertackle.player.PlayerData.update(player, d -> d.recordCatch(
                codexId, codexKg, codexCm, tier, codexGrade,
                player.level().getGameTime(), firstCatch));
        if (firstCatch) {
            FishingManager.LOGGER.info("[垂纶] 图鉴点亮：{}（第 {} 种）",
                    species.id(), com.whitecobalt.anglertackle.player.PlayerData.get(player).codexCount());
        }

        // ---- 图鉴里程碑奖励 ----
        // ⚠ 必须在**记账之后**：CodexRewards 读的就是刚写进去的完成度，
        //   顺序反了会永远晚一条鱼发奖（19 种时差一个里程碑）。
        // 幂等由 claimedMilestones 位掩码保证（见 CodexRewards 的类注释），
        // 所以这里不需要用 firstCatch 做门槛 —— 不设门槛反而让"加这个功能之前
        // 就已经点亮了几种鱼"的老存档在下次上鱼时**补发**该拿的奖励。
        com.whitecobalt.anglertackle.economy.CodexRewards.grantMilestones(player);

        // ---- 垂纶经验（用户 2026-10-02："每钓上来一条鱼获得 10 经验，
        //      白/绿/蓝/紫/金/红 额外加 2/8/16/24/32/48"）----
        //
        // ⚠ 顺序：必须在**图鉴记账之后**。虽然两者互不依赖，但"上鱼 → 记图鉴 → 给经验"
        //   是玩家心里的事件顺序，日志按这个顺序读起来才对得上。
        // ⚠ 卡在评定点（10/20/30/40）时 gainXp 一点经验都不收 —— 用户原话
        //   "不突破就卡在那一级，不积攒经验"，所以这里也**不要**提示"+0 经验"，
        //   而是提示"卡在评定"（见下）。
        {
            com.whitecobalt.anglertackle.player.FishermanProgress before =
                    com.whitecobalt.anglertackle.player.FishermanData.get(player);
            int gainedXp = com.whitecobalt.anglertackle.player.AnglerLeveling.xpForCatch(tier);
            int levelsUp = com.whitecobalt.anglertackle.player.FishermanData.addXp(player, gainedXp);
            com.whitecobalt.anglertackle.player.FishermanProgress after =
                    com.whitecobalt.anglertackle.player.FishermanData.get(player);

            if (before.atGate() || (after.atGate() && levelsUp == 0)) {
                player.displayClientMessage(Component.literal(
                        "§e垂纶等级卡在 Lv." + after.level()
                        + " §7—— 去老柯那里做【等级评定】才能继续升级（经验暂不积攒）"), false);
            } else {
                String bar = after.level() >= com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL
                        ? "§a已满级"
                        : after.xp() + "/" + com.whitecobalt.anglertackle.player.AnglerLeveling.xpToNext(after.level());
                player.displayClientMessage(Component.literal(
                        "§b+" + gainedXp + " 垂纶经验§7（Lv." + after.level() + " " + bar + "§7）"), true);
            }
            if (levelsUp > 0) {
                player.displayClientMessage(Component.literal(
                        "§6垂纶等级提升！§fLv." + after.level()
                        + "§7（商店解锁档位 " + after.stage() + "）"), false);
                player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                        SoundSource.PLAYERS, 0.6f, 1.4f);
            }
            LOGGER.info("[垂纶] 经验：{} 档={} +{}（Lv.{} {}/{}，升 {} 级）",
                    species.id(), tier, gainedXp, after.level(), after.xp(),
                    after.level() >= com.whitecobalt.anglertackle.player.AnglerLeveling.MAX_LEVEL
                            ? 0 : com.whitecobalt.anglertackle.player.AnglerLeveling.xpToNext(after.level()),
                    levelsUp);
        }

        // ---- 称号：计数 + 评估（用户 2026-10-11，"开发称号系统"）----
        //
        // 为什么计数与评估分两步：计数是"现有存档里算不出来的东西"（这一竿是在夜晚/满月/
        // 雷暴/冰洞上的？），只在这里 +1；评估是"能从现有数据算出来的东西"（图鉴/等级/巨物/纪录），
        // 统一交给 Titles.syncAuto —— 它幂等，重复调用不会有副作用 ✓。
        //
        // ⚠ 环境读的是**咬钩那一刻的快照**（session.envSnapshot()），不是现在重新取一次：
        //   一场遛鱼能横跨黄昏，重新取样会出现"白天咬的鱼算进夜钓" ✗。
        //   冰洞同理走 session.iceHoleCast()（浮漂落点判据，与 IceFishing 同一处）。
        try {
            com.whitecobalt.anglertackle.fishing.Environment envNow = session.envSnapshot();
            if (envNow != null) {
                if (envNow.timeSlot() == com.whitecobalt.anglertackle.fishing.Environment.NIGHT) {
                    com.whitecobalt.anglertackle.title.Titles.bump(player,
                            com.whitecobalt.anglertackle.title.Titles.K_NIGHT, 1);
                }
                if (envNow.weather() == com.whitecobalt.anglertackle.fishing.Environment.THUNDER
                        && tier >= 4) {
                    com.whitecobalt.anglertackle.title.Titles.bump(player,
                            com.whitecobalt.anglertackle.title.Titles.K_STORM_GOLD, 1);
                }
                if (envNow.timeSlot() == com.whitecobalt.anglertackle.fishing.Environment.NIGHT
                        && com.whitecobalt.anglertackle.fishing.Environment
                                .isFullMoon(envNow.moonPhase())) {
                    com.whitecobalt.anglertackle.title.Titles.bump(player,
                            com.whitecobalt.anglertackle.title.Titles.K_FULL_MOON, 1);
                }
            }
            if (session.iceHoleCast()) {
                com.whitecobalt.anglertackle.title.Titles.bump(player,
                        com.whitecobalt.anglertackle.title.Titles.K_ICE, 1);
            }
            if (tier >= 5) {
                com.whitecobalt.anglertackle.title.Titles.bump(player,
                        com.whitecobalt.anglertackle.title.Titles.K_RED, 1);
            }
            com.whitecobalt.anglertackle.title.Titles.syncAuto(player);
        } catch (Throwable t) {
            // 称号是锦上添花：绝不让它把上鱼结算带崩（丢鱼才是最贵的）
            LOGGER.warn("[垂纶] 称号计数失败（已忽略）：{}", t.toString());
        }

        double sizeFactor = com.whitecobalt.anglertackle.economy.CoinPricing.sizeFactor(
                data.weightKg(), species.kgMin(), species.kgMax());
        long sellValue = com.whitecobalt.anglertackle.economy.CoinPricing.priceFor(tier, sizeFactor);
        LOGGER.info("[垂纶] 入包：{} 档={} 尺寸系数={} 售价={} 币（未结算，等商店卖出）",
                species.id(), tier, String.format("%.2f", sizeFactor), sellValue);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP,
                SoundSource.PLAYERS, 0.4f, 1.8f);

        // ---- 大鱼公告（用户 2026-10-11："公告可以搞"）----
        // 挂在**这里**：售价（sellValue）刚算出来，正是"价值"的真源 ✓；
        // 而且这一处与图鉴记账、里程碑、经验是同一个收口（服务端上鱼那一帧）✓。
        // 三道闸门（价值门槛 / 玩家自己的开关 / 最小间隔）全在 BigCatchAnnounce 里，
        // 它自己吞掉所有异常 —— 公告绝不能让"上鱼结算"崩掉（那会丢鱼 ✗）。
        com.whitecobalt.anglertackle.fishing.BigCatchAnnounce.maybeAnnounce(
                player, species.item().get().getName(new ItemStack(species.item().get())),
                data.weightKg(), sellValue);

        LOGGER.info("[垂纶] 上鱼：{} 体长 {}cm 体重 {}kg 评级 {}（玩家 {}）",
                species.id(), String.format("%.1f", data.lengthCm()),
                String.format("%.2f", data.weightKg()), data.grade().key(),
                player.getName().getString());
        // ⚠⚠ 结束会话**之前**，先推一份"最终快照"。
        //
        // 这一份是用户 2026-10-16 纠正设计的关键（原话："我想要的是上鱼后给鱼、视角缩放恢复正常，
        // 一秒后允许移动"）：里面 movementLockTicks > 0 ⇒ 客户端据此
        //   ① 立刻收掉张力条/鱼力条、把镜头/收线拉近恢复正常（它们只看 FIGHT 状态）；
        //   ② 同时**继续锁住移动** movementLockTicks 个 tick（1 秒）。
        // 少了这一份，客户端只能等 20 tick 超时才离开 FIGHT ⇒ 又变成"视角迟迟不收、鱼也晚给" ✗。
        ModNetwork.sendTo(player, session.snapshot());
        end(session);
    }

    /** 主动/被动结束会话。 */
    public void abort(FishingSession session, String messageKey) {
        session.player().displayClientMessage(
                Component.translatable(messageKey).withStyle(ChatFormatting.YELLOW), true);
        end(session);
    }

    /** 由会话自己调用（例如错过提竿导致鱼逃离）。 */
    public void endSession(FishingSession session) {
        end(session);
    }

    private void end(FishingSession session) {
        restoreMoveSpeed(session.player());
        sessions.remove(session.player().getUUID());
        session.cleanup();
    }

    /** 玩家登出/死亡/换维度时清场。 */
    public void forget(ServerPlayer player) {
        // ⚠ "上鱼后那一秒"那把锁也要一起清：否则玩家带着还没到期的锁登出，
        //   再进来时 tick 里那句"未到期就一直压着速度属性"会让他一落地就走不动 ✗
        moveLockUntil.remove(player.getUUID());
        restoreMoveSpeed(player);
        FishingSession session = sessions.remove(player.getUUID());
        if (session != null) {
            session.cleanup();
        }
    }

    // =================================================================== 每 tick
    /**
     * 推进所有会话。
     *
     * <p>⚠️ <b>这里绝对不能一边遍历 sessions 一边让它被修改。</b>
     *
     * <p>之前是直接迭代 {@code sessions.entrySet()} 并在循环里 remove，
     * 但 {@code session.tick()} 内部会因为"落点无效/鱼逃离/断线"回头调用
     * {@link #endSession}/{@link #forget} 把这根会话从同一张表里删掉，
     * 于是下一次 {@code it.remove()} 就抛 {@code ConcurrentModificationException}，
     * 直接把服务器 tick 打断——玩家看到的就是"一点右键立刻被踢出游戏"。
     *
     * <p>现在改成：先快照出一份待处理的会话列表（不在遍历原表），
     * 处理完再统一清理，和"会话内部顺手删自己"彻底解耦。
     */
    public void tick(ServerLevel level) {
        // ⚠ "上鱼后那一秒"的到期检查必须在 sessions.isEmpty() **之前** ——
        //   上鱼那一刻会话就结束了（为了立刻给鱼），而这一秒还没走完 ⇒
        //   放到 early-return 后面就永远不会被清掉（速度属性会一直压着，玩家永久走不动）✗
        for (ServerPlayer p : level.players()) {
            expireCatchMoveLock(p);
        }
        if (sessions.isEmpty()) {
            return;
        }

        // 快照：遍历期间即使 sessions 被改动也不受影响
        List<Map.Entry<UUID, FishingSession>> snapshot = new ArrayList<>(sessions.entrySet());

        for (Map.Entry<UUID, FishingSession> entry : snapshot) {
            UUID id = entry.getKey();
            FishingSession session = entry.getValue();

            // 会话可能已经被别处结束了（endSession/forget），跳过
            if (sessions.get(id) != session) {
                continue;
            }

            ServerPlayer player = session.player();

            // 玩家离线/换维度/死亡 → 结束会话
            if (player.hasDisconnected() || player.isDeadOrDying() || player.level() != level) {
                restoreMoveSpeed(player);   // 别把速度锁留在身上
                session.cleanup();
                sessions.remove(id);
                continue;
            }

            session.tick(level);

            // session.tick 里可能已经结束了会话（落点无效等），这里再确认一次
            if (sessions.get(id) != session) {
                continue;
            }

            // ---- 对抗中锁住玩家移动 ----
            // 用户要求："钓鱼时移动键改成拉杆方向"。试过两种做法：
            //   ① 客户端清 leftImpulse —— 原版这一 tick 会按按键重新赋值，压不住
            //   ② 服务端 setDeltaMovement(0) —— 客户端每 tick 会把自己的移动同步上来，也压不住
            // 最终用**属性**：把移动速度临时降到 0。原版的移动计算读的就是这个属性，
            // 降到 0 之后无论客户端怎么发移动包都走不动。A/D 的按键状态不受影响，
            // 客户端照样能读到，控杆方向照常工作。
            //
            // ⚠⚠ 2026-10-16：判据从"只 FIGHT"改成 `session.state().blocksMovement()`
            //   = FIGHT **+ 上鱼后的 LANDING**。用户原话："上鱼后应该一秒内无法移动，
            //   不然有人收不住方向键会掉进水里"。
            //   为什么服务端这一半**必须**也锁（1.6.6 只改了客户端那半，用户实测仍然能动）：
            //   客户端清冲量只能压住"按键产生的位移"，压不住**已经带在身上的动量**与
            //   水流推动；属性归零是权威的，客户端怎么发移动包都走不动。
            //   两边同名同义：这里 blocksMovement() ↔ 客户端 movementLocked()。
            // ⚠⚠ 2026-10-16（用户报"移动没锁一秒"）：判据从"会话还在"扩成
            //   `blocksMovement() ‖ catchLockActive()` —— 上鱼那一刻会话就结束了
            //   （为的是"立刻给鱼、立刻收视角"），而"再锁一秒移动"必须活过会话本身 ✓。
            //   两条各自独立：前者是"对抗中"，后者是"上鱼后那一秒"。
            //   两边同名同义：这里 ↔ 客户端 movementLocked()。
            // ⚠⚠ 诊断**必须放在这句之后**：断言盯的是"这一行的原文"，
            //   在它前面塞一段就会让 `if (session.state().blocksMovement() || catchLockActive(player)) {`
            //   的正则在源码里匹配不到 —— 2026-10-16 就这么把两条负例搞成了假失败。
            if (session.state().blocksMovement() || catchLockActive(player)) {
                // ★诊断：只在"压/不压"变化时记一行（原来每 5 tick 一行，一次对抗刷 139 行）
                boolean lockingDiag = true;
                if (lockingDiag != lastLockDiag.getOrDefault(player.getUUID(), false)) {
                    LOGGER.info("[垂纶/诊断·锁] tick {}：会话态={} 会话锁={} 上鱼锁={} 离锁定点={}",
                            level.getGameTime(), session.state(), session.state().blocksMovement(),
                            catchLockActive(player),
                            String.format("%.3f", lockPositions.containsKey(player.getUUID())
                                    ? player.position().distanceTo(lockPositions.get(player.getUUID())) : -1.0));
                }
                lastLockDiag.put(player.getUUID(), lockingDiag);
                // ⚠⚠⚠ 2026-10-28 真凶修复：**绝对不许再动 MOVEMENT_SPEED 属性**。
                //
                //   原版 AbstractClientPlayer.getFieldOfViewModifier() 把移动速度算进了视野：
                //       float f2 = attributeValue(MOVEMENT_SPEED) / walkingSpeed;
                //       f *= (f2 + 1.0F) / 2.0F;
                //   属性归零 ⇒ f2 = 0 ⇒ 系数 = 0.5 ⇒ **视野 80 直接变 40**；
                //   而 GameRenderer.tickFov() 每 tick 只靠近一半 ⇒ 看起来就是"视野平滑缩下去"。
                //   这正是用户反复报的"台钓上鱼后视野缩了一下"、也是我查了四轮都没找到的东西
                //   （它跟相机、跟路亚缩放毫无关系，是**移动锁的副作用**）。
                //
                //   现在换成同样服务端权威、但不碰属性的做法：
                //     ① 每 tick 把玩家**按回**锁定的那一刻的位置（位置是权威的，客户端怎么发包都没用）；
                //     ② 速度清零，避免动量继续累积。
                net.minecraft.world.phys.Vec3 lock = lockPositions.computeIfAbsent(
                        player.getUUID(), k -> player.position());
                if (player.position().distanceToSqr(lock) > 1.0E-4) {
                    player.teleportTo(lock.x, lock.y, lock.z);
                }
                player.setDeltaMovement(0.0, 0.0, 0.0);
                // ⚠ 只在**对抗中**关冲刺：LANDING（上鱼后那 1 秒）只是锁移动，
                //   而冲刺状态会乘原版 FOV —— 在这里关掉就等于让台钓的视野跳一下。
                if (session.state() == FishingSession.State.FIGHT) {
                    player.setSprinting(false);
                }
            } else {
                if (lastLockDiag.getOrDefault(player.getUUID(), false)) {
                    LOGGER.info("[垂纶/诊断·锁] tick {}：解除压制（速度属性还原）", level.getGameTime());
                }
                lastLockDiag.put(player.getUUID(), false);
                lockPositions.remove(player.getUUID());
                restoreMoveSpeed(player);   // 兜底：把旧版本可能留在属性上的 0 还原
            }

            // 每 2 tick 同步一次状态（客户端在两次之间做插值，保证条子顺滑）
            if ((level.getGameTime() & 1L) == 0L) {
                ModNetwork.sendTo(player, session.snapshot());
            }

            // 对抗中每 10 tick 给一条动作栏提示。
            // 用户反馈"没有反馈所以无法判断有没有用"——对抗全靠张力与鱼的方向，
            // 这些东西只在 HUD 条上看不出来，必须用文字说清楚。
            if (session.state() == FishingSession.State.FIGHT
                    && level.getGameTime() % 10L == 0L) {
                player.displayClientMessage(session.fightHint(), true);
            }

            if (session.state() == FishingSession.State.IDLE) {
                session.cleanup();
                sessions.remove(id);
            }
        }
    }

    /** 会话数量（调试用）。 */
    public int activeCount() {
        return sessions.size();
    }

    /** 由输入包调用：玩家在提竿窗口内按下了提竿。 */
    public void tryHook(ServerPlayer player) {
        FishingSession session = sessions.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (session.state() == FishingSession.State.HOOK_WINDOW) {
            session.onHooked();
        }
    }

    /** 由输入包调用：每 tick 的对抗输入。 */
    public void applyInput(ServerPlayer player, boolean reeling, boolean releasing, int counterDir) {
        FishingSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.applyInput(reeling, releasing, counterDir);
        }
    }

    /**
     * 设置是否正在收线（右键按住/松开时调用）。
     *
     * <p>右键的两个时机（按下、松开）只在客户端可靠，所以服务端这边
     * 由 {@code RodItem.use/releaseUsing} 配合每 2 tick 的输入包一起决定，
     * 任一路径说"在收线"就算在收线——这样即使某一边漏了也不会失灵。
     */
    public void setReeling(ServerPlayer player, boolean reeling) {
        FishingSession session = sessions.get(player.getUUID());
        if (session != null) {
            session.setReeling(reeling);
        }
    }
}
