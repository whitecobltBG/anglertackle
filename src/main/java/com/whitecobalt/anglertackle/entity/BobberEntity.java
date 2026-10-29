package com.whitecobalt.anglertackle.entity;

import com.whitecobalt.anglertackle.fishing.FishingManager;
import com.whitecobalt.anglertackle.fishing.FishingSession;
import com.whitecobalt.anglertackle.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 浮漂 / 拟饵。
 *
 * <p>为什么不复用原版 {@code FishingHook}：原版那两个静态字段
 * （{@code WATER_CHECK_RANGE}、{@code MAX_DISTANCE_TO_BOBBER_SQ}）是 public static final，
 * 改它会影响所有用原版钓竿的玩家，也会和别的钓鱼模组打架。
 * 详见 docs/09-原版冲突与渲染优化.md §2。
 *
 * <p>三层距离限制的应对（同文档 §4）：
 * <ul>
 *   <li>L1 追踪范围：注册时单独设 {@code clientTrackingRange}（默认 10 区块）</li>
 *   <li>L2 远处降频：会话期间每 tick 都推进，结束后立刻交还控制权</li>
 *   <li>L3 客户端视距：渲染不依赖本实体是否存在（幽灵鱼线），见 BobberRenderer</li>
 * </ul>
 */
public class BobberEntity extends Entity {

    /** 主人 UUID（会话归属）。 */
    private static final EntityDataAccessor<String> DATA_OWNER =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.STRING);

    /** 落水状态：0=飞行中 1=已落水 2=已回收。 */
    private static final EntityDataAccessor<Byte> DATA_PHASE =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.BYTE);

    /** 浮漂在水面的上下浮动高度（供渲染用，米）。 */
    private static final EntityDataAccessor<Float> DATA_BOB =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.FLOAT);

    /** 三段预告阶段：0=无 1=探饵 2=试探 3=吞钩。 */
    private static final EntityDataAccessor<Byte> DATA_BITE_PHASE =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.BYTE);

    /** 鱼的挣扎方向：-1 左 / 0 直冲 / +1 右（同步给客户端，用于横拖动画）。 */
    private static final EntityDataAccessor<Byte> DATA_FISH_DIR =
            SynchedEntityData.defineId(BobberEntity.class, EntityDataSerializers.BYTE);

    /**
     * 判"落水"时允许漂心高出水面的余量（格）。
     *
     * <p>抛物线是逐 tick 采样的，压到水面那一刻的采样点未必正好落在水面高度上，
     * 所以给一点余量。**不能给大**：这个数变大就等于把"必须降到水面"这条约束放松回去，
     * 又会出现"出竿第一 tick 就在脚下落水"（用户 2026-10-02 报的那个）。
     */
    private static final double WATER_LAND_MARGIN = 0.15;

    /**
     * 水面锚点：抛投落水时的位置。
     *
     * <p>用来算"垂直于鱼线"的方向——漂被拖走之后不能拿漂自己的位置当基准，
     * 否则法线会跟着漂一起转，看起来就成了原地打转。
     */
    private double waterAnchorX;
    private double waterAnchorZ;

    /** 落点水深（格），用于鱼池筛选。 */
    private int waterDepth;

    /** 落点水面 Y 坐标（浮漂上下浮动的基准）。 */
    private double waterY;

    /** 是否落在流动水里（流动水咬钩等待 ×1.3）。 */
    private boolean flowingWater;

    /** 飞行是否已结束（防止重复触发落水逻辑）。 */
    private boolean landed;

    @Nullable
    private UUID ownerUuid;

    /**
     * ★ 主人 UUID 的**公开只读**入口（2026-10-10 多人适配加）：
     * 客户端要判断"这个浮漂的线该从**谁**的手上画出去" ✓。
     *
     * <p>为什么不按"就近的握竿玩家"配对 ✗：用户提出的反例 —— A 抛得远、B 只是**站得更近**
     * （哪怕 B 也握着竿 ✓），就近配对会把线画成「B 的手 → A 的浮漂」✗✗。所以必须用同步数据 ✓。
     *
     * <p>{@code DATA_OWNER} 是 {@code SynchedEntityData}（会同步到客户端 ✓），存的是主人 UUID 字符串 ✓；
     * 而服务端那个 {@code getOwnerPlayer()} 返回 {@code ServerPlayer} ✗，客户端用不了 ✓。
     */
    @Nullable
    public UUID ownerId() {
        String s = this.entityData.get(DATA_OWNER);
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public BobberEntity(EntityType<? extends BobberEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;   // 远处也不因包围盒被剔除（配合幽灵鱼线）
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_OWNER, "");
        builder.define(DATA_PHASE, (byte) 0);
        builder.define(DATA_BOB, 0.0f);
        builder.define(DATA_BITE_PHASE, (byte) 0);
        builder.define(DATA_FISH_DIR, (byte) 0);
    }

    /** 鱼的挣扎方向：-1 / 0 / +1。 */
    public int getFishDir() {
        return this.entityData.get(DATA_FISH_DIR);
    }

    /** 由会话每 tick 写入（服务端），自动同步到客户端。 */
    public void setFishDir(int dir) {
        this.entityData.set(DATA_FISH_DIR, (byte) Math.max(-1, Math.min(1, dir)));
    }

    // ------------------------------------------------------------------ 生命周期
    @Override
    public void tick() {
        super.tick();

        // ---- 飞行阶段：与 MC 标准实体物理完全一致（阻力 0.99 + 重力 0.03）----
        // ⚠️ 这里的参数必须和 FishingManager.simulateRange() 里的一模一样。
        // 抛投距离是拿那套物理反解出来的：抬头 30 格就是 30 格。
        // 如果这里改成 g=0.05 或无阻力，落点就会偏，玩家按角度瞄准会失准。
        if (!landed) {
            Vec3 motion = this.getDeltaMovement();
            this.move(MoverType.SELF, motion);
            final double drag = 0.99, gravity = 0.03;
            this.setDeltaMovement(motion.x * drag, motion.y * drag - gravity, motion.z * drag);

            // ---- 入水判定优先 ----
            // 必须先查水再查陆地：浮漂贴着水面飞的时候脚下就是水方块，
            // 如果先判"撞到固体"，会把它在水面上判成落点无效。
            BlockPos waterHit = scanForWater();
            if (waterHit != null) {
                this.onLanded(waterHit);
                return;
            }

            // ---- 掉落/撞地判定 ----
            // this.move() 对没有碰撞箱的实体会直接穿过去（这就是"漂一直掉、
            // 撞不到陆地所以也不收线"的原因），所以这里**自己做方块检测**：
            // 当前位置或下一 tick 位置若是固体方块，就判落点无效。
            if (this.getY() < this.level().getMinBuildHeight() || hitsSolidBlock(motion)) {
                this.onMiss();
                return;
            }
            return;
        }

        // ---- 已落水：水面上下浮动 ----
        // 横向移动一律由服务端的 dragByFish 用 move() 完成（它同时会更新旧坐标）。
        // 这里只在服务端把 y 对齐到水面，别再动 x/z，免得两边打架。
        if (this.getPhase() == 1) {
            double bob = Math.sin((this.tickCount + this.getId()) * 0.12) * 0.035;
            this.setBob((float) bob);
            if (!this.level().isClientSide()) {
                double targetY = this.waterSurfaceY();
                if (Math.abs(this.getY() - targetY) > 1.0e-4) {
                    this.move(MoverType.SELF, new Vec3(0, targetY - this.getY(), 0));
                }
            }
        }
    }

    /**
     * 让鱼把浮漂往左/右拖（服务端调用，每 tick 一次）。
     *
     * <p>为什么放在服务端：漂的坐标是服务端权威的，客户端 {@code setPos} 之后
     * 下一秒就会被位置同步包覆盖回去，所以"漂在水面游动"必须由服务端改。
     *
     * <p>⚠️ 必须用 {@code move()} 而不是 {@code setPos()}：
     * 原版的位置同步靠"旧坐标(xOld/yOld/zOld) → 新坐标"的插值，
     * {@code setPos} 只改当前坐标、不动旧坐标，客户端算出来的位移是 0，
     * 结果就是漂看起来纹丝不动（还会抖）。
     * {@code move(MoverType.SELF, ...)} 会同时更新旧坐标，同步才正常。
     *
     * @param dir    -1 左 / +1 右（相对"玩家→漂"这条线的法线方向）
     * @param factor 速度倍率，由鱼的手感档案决定——凶猛的鱼拖得快。
     *               最低档的鱼也要保证"肉眼看得见"，所以有下限。
     */
    public void dragByFish(net.minecraft.server.level.ServerLevel level, int dir, double factor) {
        if (!this.landed || dir == 0) {
            return;
        }
        Vec3 lateral = this.lateralVector(dir);
        if (lateral.lengthSqr() < 1.0e-9) {
            return;
        }

        // 基础速度每 tick 约 0.06 格（1.2 格/秒）。
        // 用户反馈过"低级鱼移动太保守、肉眼难辨"，所以整体比初版提了一倍多，
        // 再乘上鱼自己的 pullSpeed（0.7 ~ 2.2），凶猛鱼拖得更明显。
        double speed = 0.06 * Math.max(0.8, factor);
        double bx = this.getX();
        double bz = this.getZ();

        // ⚠️ 先移动再检查，而不是"先检查再决定动不动"。
        // 先检查时，漂一旦靠近岸边就会每 tick 都提前 return，看起来就是"完全不动"；
        // 先移动、发现落到岸上再退回来，则漂会停在岸边（正是我们想要的）。
        this.move(MoverType.SELF, new Vec3(lateral.x * speed, 0, lateral.z * speed));

        BlockPos at = BlockPos.containing(this.getX(), this.waterY - 0.1, this.getZ());
        if (!isWater(at) && !isWater(at.below())) {
            // 拖到岸上了：退回原位置，停在岸边
            this.setPos(bx, this.getY(), bz);
            return;
        }

        this.waterY = at.getY() + 0.9;
    }

    /**
     * "垂直于鱼线"的单位向量。
     *
     * <p>鱼线的水平方向 = **玩家 → 浮漂**，左/右就是这条线的水平法线。
     * 这样无论玩家朝哪抛，鱼往左跑就是把漂往玩家的左手边拖。
     *
     * <p>⚠️ 这里**必须用玩家位置**，不能用"落水锚点 → 漂"：
     * 漂落水后横向从没动过，锚点就等于漂自己的位置，那个向量恒等于 0，
     * 于是每一 tick 都提前 return —— 表现就是"漂永远不移动"。
     */
    private Vec3 lateralVector(int dir) {
        ServerPlayer owner = this.getOwnerPlayer();
        double dx, dz;
        if (owner != null) {
            dx = this.getX() - owner.getX();
            dz = this.getZ() - owner.getZ();
        } else {
            // 玩家不在了（异常情况）：退化成用落点当基准，至少不会崩
            if (this.waterAnchorX == 0 && this.waterAnchorZ == 0) {
                return Vec3.ZERO;
            }
            dx = this.getX() - this.waterAnchorX;
            dz = this.getZ() - this.waterAnchorZ;
        }

        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-4) {
            return Vec3.ZERO;
        }
        double fx = dx / len, fz = dz / len;      // 鱼线方向（水平）
        // 水平法线（右手边）
        double rx = -fz, rz = fx;
        return dir > 0 ? new Vec3(rx, 0, rz) : new Vec3(-rx, 0, -rz);
    }

    /**
     * 在实体附近搜索水面。
     *
     * <p>高速飞行时（最高约 3 格/tick）单 tick 会跨越多个方块，
     * 只检查实体脚下那一格必然漏判——那样浮漂会"穿过水面"砸到水底，
     * 或者一直找不到水面而在空中持续下落（玩家看到的就是"漂飘在天上"）。
     *
     * <p>所以这里**沿本 tick 的移动路径逐格扫描**，而不是只查终点。
     *
     * <p>⚠️ 但"下面有水"≠"落水"。还必须满足**浮漂自己已经降到水面**
     * （{@code py <= 水面高度}）——否则站在岸边、或坐在船上时，
     * 眼睛下方一两格就是水，浮漂出竿的第一 tick 就判落水，原地砸在玩家脚下。
     * 用户 2026-10-02 报的就是这个："只要靠近水源或坐在船上就容易原地下漂。"
     *
     * <p>{@code WATER_LAND_MARGIN} 是留给"漂心在水面上一点"的余量：抛物线从上方
     * 压到水面时，采样点未必正好落在水面上。
     *
     * @return 找到的水方块位置；没找到返回 {@code null}
     */
    @Nullable
    private BlockPos scanForWater() {
        Vec3 motion = this.getDeltaMovement();
        // 本 tick 的移动量（用来决定要扫多少格）
        int steps = (int) Math.ceil(Math.max(
                Math.abs(motion.x), Math.max(Math.abs(motion.y), Math.abs(motion.z))));
        steps = Math.max(1, Math.min(steps, 8));

        for (int s = 0; s <= steps; s++) {
            double t = steps == 0 ? 0 : (double) s / steps;
            // 从"上一 tick 的位置"插值到当前位置
            double px = this.getX() - motion.x * (1 - t);
            double py = this.getY() - motion.y * (1 - t);
            double pz = this.getZ() - motion.z * (1 - t);

            BlockPos base = BlockPos.containing(px, py, pz);
            // 水可能在这一格，也可能在下面几格（浮漂中心落在水面之上一点）
            for (int dy = 0; dy >= -2; dy--) {
                BlockPos p = base.offset(0, dy, 0);
                if (!isWater(p)) {
                    continue;
                }
                // ★ 只有"漂降到水面"才算落水（见方法头那段说明）
                double surface = p.getY() + this.level().getFluidState(p).getHeight(this.level(), p);
                if (py <= surface + WATER_LAND_MARGIN) {
                    return p;
                }
            }
        }
        return null;
    }

    private boolean isWater(BlockPos pos) {
        net.minecraft.world.level.material.FluidState fluid = this.level().getFluidState(pos);
        return !fluid.isEmpty() && fluid.getType() == Fluids.WATER;
    }

    /**
     * 当前位置或下一 tick 的位置有没有固体方块。
     *
     * <p>为什么自己做而不是靠 {@code this.move()} 的碰撞结果：浮漂实体的碰撞箱很小
     * 而且原版对非生物实体的碰撞结算很不可靠，实测会直接穿过地面。
     * 直接查方块状态最稳。
     */
    private boolean hitsSolidBlock(Vec3 motion) {
        BlockPos here = this.blockPosition();
        if (isSolid(here)) {
            return true;
        }
        // 下一 tick 会到哪
        Vec3 next = this.position().add(motion.x * 0.99, motion.y * 0.99 - 0.03, motion.z * 0.99);
        return isSolid(BlockPos.containing(next.x, next.y, next.z));
    }

    private boolean isSolid(BlockPos pos) {
        net.minecraft.world.level.block.state.BlockState state = this.level().getBlockState(pos);
        // 水、空气、草之类不算"撞地"
        if (state.isAir()) {
            return false;
        }
        if (!state.getFluidState().isEmpty()) {
            return false;
        }
        return state.isSolidRender(this.level(), pos);
    }

    /** 落水：记录水深与水体类型，然后通知会话进入等待咬钩。 */
    private void onLanded(BlockPos pos) {
        this.landed = true;
        this.setPhase((byte) 1);
        this.waterDepth = measureDepth(pos);
        this.flowingWater = this.level().getFluidState(pos).getOwnHeight() < 0.9f;

        // 吸附到水面。
        // 水面高度 = 水方块底面 + 流体的实际高度（满水约 0.875，再抬一点让漂"坐"在水上）。
        // 之前用 max(0.85, ...) 在小数位上会让漂沉进水里或悬在水上，
        // 现在是"水方块 y + 0.9"，稳定贴在水面之上一点点，视觉上就是浮着。
        double surface = pos.getY() + 0.9;
        this.waterY = surface;
        this.setPos(this.getX(), surface, this.getZ());
        this.setDeltaMovement(Vec3.ZERO);
        // 记录落水锚点，之后"往左/右拖"都以它为基准算方向
        this.waterAnchorX = this.getX();
        this.waterAnchorZ = this.getZ();

        // ⚠ 2026-10-29 用户："落水那一刻用原版的音效稍微高频一点就行" ⇒ 音高 1.15~1.30
        this.level().playSound(null, pos, SoundEvents.FISHING_BOBBER_SPLASH,
                SoundSource.NEUTRAL, 0.118f, 1.15f + this.random.nextFloat() * 0.15f);   // 2026-10-29：再降 20%

        if (this.level() instanceof ServerLevel serverLevel) {
            ServerPlayer owner = this.getOwnerPlayer();
            if (owner != null) {
                FishingManager.get().onBobberLanded(serverLevel, owner, this, this.waterDepth, this.flowingWater);
            } else {
                // 主人不在了，直接回收
                this.discard();
            }
        }
    }

    /** 落点无效（砸到地面/掉出世界）：不扣耐久，直接回收。 */
    private void onMiss() {
        this.landed = true;
        this.setPhase((byte) 2);
        if (this.level() instanceof ServerLevel) {
            ServerPlayer owner = this.getOwnerPlayer();
            if (owner != null) {
                FishingManager.get().onCastFailed(owner, "message.anglertackle.cast_invalid");
            }
        }
        this.discard();
    }

    /** 从落点向下统计连续水源方块数量（上限 12 格）。 */
    private int measureDepth(BlockPos from) {
        int depth = 0;
        BlockPos.MutableBlockPos cursor = from.mutable();
        for (int i = 0; i < 12; i++) {
            if (this.level().getFluidState(cursor).getType() != Fluids.WATER) {
                break;
            }
            depth++;
            cursor.move(0, -1, 0);
        }
        return depth;
    }

    private double waterSurfaceY() {
        return this.waterY > 0 ? this.waterY : Math.floor(this.getY()) + 0.9;
    }

    // ------------------------------------------------------------------ 工具
    @Nullable
    public ServerPlayer getOwnerPlayer() {
        if (this.ownerUuid == null) {
            String s = this.entityData.get(DATA_OWNER);
            if (s.isEmpty()) {
                return null;
            }
            try {
                this.ownerUuid = UUID.fromString(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        if (this.level() instanceof ServerLevel serverLevel) {
            return serverLevel.getServer().getPlayerList().getPlayer(this.ownerUuid);
        }
        return null;
    }

    public void setOwner(ServerPlayer player) {
        this.ownerUuid = player.getUUID();
        this.entityData.set(DATA_OWNER, player.getUUID().toString());
    }

    public byte getPhase() {
        return this.entityData.get(DATA_PHASE);
    }

    public void setPhase(byte phase) {
        this.entityData.set(DATA_PHASE, phase);
    }

    public float getBob() {
        return this.entityData.get(DATA_BOB);
    }

    public void setBob(float bob) {
        this.entityData.set(DATA_BOB, bob);
    }

    public byte getBitePhase() {
        return this.entityData.get(DATA_BITE_PHASE);
    }

    public void setBitePhase(byte phase) {
        this.entityData.set(DATA_BITE_PHASE, phase);
    }

    public int getWaterDepth() {
        return this.waterDepth;
    }

    public boolean isFlowingWater() {
        return this.flowingWater;
    }

    public boolean isLanded() {
        return this.landed;
    }

    /** 浮漂不可被推动（同原版浮漂）。 */
    @Override
    public boolean isPushable() {
        return false;
    }

    /** 会话结束时由管理器调用：播放收线音效并回收实体。 */
    public void reelIn() {
        this.setPhase((byte) 2);
        this.level().playSound(null, this.blockPosition(), SoundEvents.FISHING_BOBBER_RETRIEVE,
                SoundSource.NEUTRAL, 0.076f, 1.0f);   // 2026-10-29：原版收线声再降 20%   // 2026-10-29：压低原版收线声，别盖住 lure_land
        this.discard();
    }

    @Override
    public boolean isPickable() {
        return false;   // 不能被攻击/交互选中
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSq) {
        // 会话期间放宽渲染距离；浮漂本身很小，远处也要能看见
        double max = 256.0 * 256.0;
        return distanceSq < max;
    }

    // ------------------------------------------------------------------ 存盘
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.landed = tag.getBoolean("Landed");
        this.waterDepth = tag.getInt("WaterDepth");
        this.flowingWater = tag.getBoolean("Flowing");
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
            this.entityData.set(DATA_OWNER, this.ownerUuid.toString());
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("Landed", this.landed);
        tag.putInt("WaterDepth", this.waterDepth);
        tag.putBoolean("Flowing", this.flowingWater);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
    }

    @Override
    public boolean isAttackable() {
        return false;
    }
}
