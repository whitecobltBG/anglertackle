package com.whitecobalt.anglertackle.entity;

import com.whitecobalt.anglertackle.fishing.ChumManager;
import com.whitecobalt.anglertackle.fishing.Tuning;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
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
 * 打窝料实体：右键抛出后像鸡蛋一样飞出去，落水后浮在水面持续冒泡。
 *
 * <p><b>为什么做成实体（原来是直接传送到水面）</b>：
 * 玩家反馈"打窝前后不知道到底有没有效果，视觉上也不直观"。
 * 直接传送到水面既没有飞行过程、水面也没有任何持久可见的标记，
 * 玩家根本不知道自己刚才那一下打到哪、影响多大。
 *
 * <p>现在这条实体负责三件事：
 * <ol>
 *   <li><b>飞行</b>：物理参数与 {@link BobberEntity} 完全一致
 *       （阻力 0.99 + 重力 0.03），所以"看着扔到哪"就是"实际落在哪"</li>
 *   <li><b>漂浮 + 下沉粒子</b>：落水后把 y 对齐水面、上下轻浮，
 *       并按 {@code ChumManager} 的节奏冒泡</li>
 *   <li><b>白圈</b>：渲染一个半径 {@link ChumManager#radius()} 的白色圆圈，
 *       圈内就是加成生效范围 —— 玩家照着这个圈抛竿即可</li>
 * </ol>
 *
 * <p>加成判定由 {@link ChumManager#findChumAt} 通过查询本实体完成，
 * 所以"看得见的圈"和"实际生效的范围"永远是同一个数，不会漂移。
 */
public class ChumEntity extends Entity {

    /** 这条窝子的品质档（0 白 … 5 红），决定加成强度。 */
    private static final EntityDataAccessor<Integer> DATA_TIER =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.INT);

    /** 是否沉底窝（沉底窝的粒子在下方扩散）。 */
    private static final EntityDataAccessor<Boolean> DATA_SINKING =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.BOOLEAN);

    /** 落水后为 true；飞行中为 false。 */
    private static final EntityDataAccessor<Boolean> DATA_LANDED =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.BOOLEAN);

    /** 上下浮动的相位（客户端渲染用）。 */
    private static final EntityDataAccessor<Float> DATA_BOB =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.FLOAT);

    /** 剩余存活 tick（客户端据此让白圈在快消失时闪烁）。 */
    private static final EntityDataAccessor<Integer> DATA_LIFE =
            SynchedEntityData.defineId(ChumEntity.class, EntityDataSerializers.INT);

    /**
     * 抛出初速（格/tick）。仰角取视线角（夹到 0°~45°），所以**抬头 45° 时射程最大**。
     *
     * <p>用户 2026-10-02："打窝料可以稍微再抛远一点。"
     * 当时它被两件事压着，一起改的：
     * <ol>
     *   <li>初速 0.75 → <b>0.85</b>（这条）；</li>
     *   <li>{@link #scanForWater} 只看"脚下 0~−2 格有没有水"，于是窝料在**离水面还有 2~3 格**
     *       时就判落水、被吸到水面上 —— 这正是"抛不远"的主因（浅角度下差得最多）。
     *       改成"自己降到水面才算"（与 BobberEntity 同一套判据）。</li>
     * </ol>
     *
     * <p>岸边站着抛的实测（build/chum-range.js 复算，最短滞空 10 tick）：
     * <pre>
     *   初速   0°     15°    30°    45°
     *   0.75   7.8   12.6   16.4   16.8     ← 只修判定
     *   0.85   8.9   15.6   20.2   20.3     ← 现在（最大 20 米，比改之前的 15.4 米远约三成）
     * </pre>
     * 守着这两个数的是 {@code tools/check-chum-throw.js}（15° 必须 ≥13 米 —— 它专门挡
     * "落水判定又变回提前落水"；45° 必须 ≥18 米 —— 它挡"初速被调回去"）。
     */
    private static final double THROW_SPEED = 0.85;

    /** 打窝加成的时长倍率（从 ChumItem 带过来）。 */
    private double timeMultiplier = 1.0;
    /**
     * 稀有度倍率（金+红占比乘它）。**恒 >= 1**。
     *
     * <p>必须和 {@link #timeMultiplier} 分开存：那个是等待时间倍率
     *（0.45~0.75，全都小于 1），曾经被误接到占比上，导致"越好的窝越不出好鱼"
     *（日志实测：倍率 x0.45 → 金红占比 1.4%）。两件事，两个字段。
     */
    private double rarityMultiplier = 1.0;

    /**
     * 判"落水"时允许窝料中心高出水面的余量（格）。
     *
     * <p>抛物线逐 tick 采样，压到水面那一下未必正好落在水面高度上，所以给一点余量。
     * 与 {@code BobberEntity.WATER_LAND_MARGIN} 同值同义，别单独调大 ——
     * 调大就等于把"必须降到水面"这条约束放松回去，射程又会缩水。
     */
    private static final double WATER_LAND_MARGIN = 0.15;

    /** 总存活 tick（用于算剩余比例）。 */
    private int totalLifeTicks = 1200;

    /** 抛投者，用于"自己的窝子"提示。 */
    @Nullable
    private UUID ownerUuid;

    /**
     * 最短滞空 tick 数。
     *
     * <p>10 tick = 0.5 秒。抛出去之后至少飞半秒才允许落水，
     * 免得站在水里/水边抛的时候第一 tick 就被判落水（白圈套脚下）。
     * 正常抛投的滞空时间是 20~30 tick，所以这个下限不会影响正常手感。
     */
/*__DECL_Tuning.chumMinAirTicks()__*/

    /**
     * 落水所在的水面高度。**这就是加成判定的唯一位置来源** ——
     * 不再单独存一个 BlockPos，避免出现"两处位置状态"。
     */
    private double waterY;

    public ChumEntity(EntityType<? extends ChumEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_TIER, 0);
        builder.define(DATA_SINKING, false);
        builder.define(DATA_LANDED, false);
        builder.define(DATA_BOB, 0.0f);
        builder.define(DATA_LIFE, 1200);
    }

    // ------------------------------------------------------------------ 初始化
    /**
     * 一条窝料的**总寿命**（tick）= 窝料自己的时长档 × 一分钟。
     *
     * <p>⚠ 必须只有这一处算式：`ChumItem` 抛之前要用它去 {@code ChumRegistry} **占名额**
     * （名额的到期时刻 = 现在 + 这个数），而实体自己也要用它当寿命。
     * 两处各写一遍的话，名额会比实体早/晚到期 —— 早了就是"窝子还在、名额已放"（上限漏 ✗），
     * 晚了就是"窝子没了、名额还占着"（玩家打不出窝 ✗）。守着它的见
     * {@code tools/check-chum.js}。
     */
    public static int lifeTicksOf(com.whitecobalt.anglertackle.item.ChumItem item) {
        return Math.max(1, item.durationMinutes() * 1200);
    }

    /**
     * 抛出去。
     *
     * @param player 抛投的玩家
     * @param item   打窝料（决定品质档、是否沉底、时长、加成倍率）
     */
    public void throwBy(ServerPlayer player, com.whitecobalt.anglertackle.item.ChumItem item) {
        this.setTier(item.tier());
        this.setSinking(item.sinking());
        this.timeMultiplier = item.timeMultiplier();
        this.rarityMultiplier = item.rarityMultiplier();
        this.ownerUuid = player.getUUID();
        this.totalLifeTicks = lifeTicksOf(item);
        this.setLifeTicks(this.totalLifeTicks);

        // 从眼睛高度、朝视线方向抛出 —— 和鸡蛋的抛投手感一致
        this.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
        float pitch = player.getXRot();   // MC 约定：向下看为正
        float yaw = player.getYRot();

        // ⚠️ 发射角必须**与抛竿的约定对齐**，不能直接用视线俯仰角。
        //
        // 抛竿的约定（用户定案，见 FishingManager.castByLook）：
        //     低头看水面 ≈ 8 米 / 平视 ≈ 16 米 / 抬头（≤45°）≈ 24 米
        //   也就是**抬头 = 远**。玩家已经养成了这个手感。
        //
        // 而固定初速的抛物线，水平射程在 45° 左右最大，直接用视线角会变成
        // "抬头 = 近"——正好相反。玩家按抛竿的手感抬头想把窝子打远，
        // 结果窝料掉在脚边（用户反馈"抬头仅呢"就是这个）。
        //
        // ⚠️ 符号（改过两次，别再改错）：发射角 = **仰角** = clamp(-pitch)。
        //    MC 的 xRot 是**向下为正**，所以：
        //      抬头 → pitch 为负 → -pitch 为正 → 发射角大 → 高抛 → 打得远 ✅
        //      低头 → pitch 为正 → -pitch 为负 → 夹到 0 → 平射 → 落在近处 ✅
        //    和抛竿的"低头 8m / 平视 16m / 抬头 24m"一致。
        double launchDeg = Math.max(0.0, Math.min(45.0, -pitch));
        double speed = THROW_SPEED;
        double rad = Math.toRadians(launchDeg);
        double dx = -Math.sin(Math.toRadians(yaw)) * Math.cos(rad) * speed;
        double dy = Math.sin(rad) * speed;
        double dz = Math.cos(Math.toRadians(yaw)) * Math.cos(rad) * speed;
        this.setDeltaMovement(dx, dy, dz);
        // 抛出时的旋转，看起来像翻滚的鸡蛋
        this.setYRot(yaw);
        this.setXRot(pitch);

        // 诊断：玩家报过"右键在脚下创建了圈，丢出去看不到实体"。
        // 这一行打出真实初速度与起点，能直接分辨是
        //   ① 初速度没设上（整条速度接近 0 → 原地落下）
        //   ② 起点已经在水里（起飞第一 tick 就判落水）
        //   ③ 弹道正常但落点很近
        // 搜"[垂纶] 打窝抛出"即可。
        com.whitecobalt.anglertackle.AnglersTackle.LOGGER.info(
                "[垂纶] 打窝抛出：起点 ({}, {}, {}) 初速 ({}, {}, {}) 视线俯仰 {}° 发射角 {}° 偏航 {}°",
                String.format("%.2f", this.getX()), String.format("%.2f", this.getY()),
                String.format("%.2f", this.getZ()),
                String.format("%.3f", dx), String.format("%.3f", dy), String.format("%.3f", dz),
                String.format("%.1f", pitch), String.format("%.1f", launchDeg),
                String.format("%.1f", yaw));
    }

    // ------------------------------------------------------------------ 每 tick
    @Override
    public void tick() {
        super.tick();

        // 寿命：到点自然消失（加成同时失效，因为判定就是查这条实体）
        if (!this.level().isClientSide()) {
            int life = getLifeTicks();
            if (life <= 0) {
                this.discard();
                return;
            }
            setLifeTicks(life - 1);
        }

        if (!isLanded()) {
            tickFlight();
        } else {
            tickFloating();
        }
    }

    /** 飞行：物理参数必须和 BobberEntity / FishingManager.simulateRange 一致。 */
    private void tickFlight() {
        Vec3 motion = this.getDeltaMovement();
        this.move(MoverType.SELF, motion);
        final double drag = 0.99, gravity = 0.03;
        this.setDeltaMovement(motion.x * drag, motion.y * drag - gravity, motion.z * drag);

        // ⚠️ 必须有一个**最短滞空时间**，而且**上升阶段不判落水**。
        //
        // 玩家反馈："右键在脚下创建了圈，需要的丢出看不到"。
        // 根因：站在水边/浅水里时，眼睛下方一两格就是水方块，
        // scanForWater() 在第一 tick 就命中，实体原地落水 → 白圈直接套在脚下。
        // 原来的 scanForWater 只看"脚下有没有水"，完全不看自己是在上升还是在下降。
        //
        // ⚠️ 这里曾经写着"浮漂（BobberEntity）没有这个问题，因为它是朝着水面抛的" ——
        //    **这个前提是错的**：抛竿的发射角也是 8°~45° 朝**前上方**（见 FishingManager.castByLook），
        //    出竿那一刻脚下往往就是水。2026-10-02 用户就报了这个："只要靠近水源或坐在船上
        //    就容易原地下漂。" 浮漂那边现在改成更准的判据（**必须自己降到水面**才算落水，
        //    见 BobberEntity.scanForWater），守着它的是 tools/check-bobber-cast.js。
        if (this.motionY() > 0 || this.tickCount < Tuning.chumMinAirTicks()) {
            // 还在上升（或被强制滞空）→ 绝不判落水，只判掉出世界
            if (this.getY() < this.level().getMinBuildHeight()) {
                this.discard();
            }
            return;
        }

        BlockPos water = scanForWater();
        if (water != null) {
            land(water);
            return;
        }
        if (this.getY() < this.level().getMinBuildHeight()) {
            this.discard();
        }
    }

    /** 当前竖直速度（下降为负）。 */
    private double motionY() {
        return this.getDeltaMovement().y;
    }

    /** 已落水：对齐水面 + 上下浮动 + 冒泡粒子。 */
    private void tickFloating() {
        double bob = Math.sin((this.tickCount + this.getId()) * 0.10) * 0.03;
        this.setBob((float) bob);

        if (!this.level().isClientSide()) {
            double targetY = this.waterY;
            if (Math.abs(this.getY() - targetY) > 1.0e-4) {
                this.move(MoverType.SELF, new Vec3(0, targetY - this.getY(), 0));
            }
        }
    }

    /**
     * 找脚下的水面。和 BobberEntity 一样**先查水**：打窝料贴水面飞的时候
     * 脚下就是水方块，先判固体的话会把它在水面上判成无效落点。
     *
     * <p>⚠️ 但"下面有水"≠"落水"。还必须满足**窝料自己已经降到水面**
     * （{@code getY() <= 水面高度 + 余量}）—— 否则它在离水面还有两三格时就判落水、
     * 被吸到水面上，射程凭空少掉一大截。用户 2026-10-02 说"打窝料可以稍微再抛远一点"，
     * 提速只是小头，**主因就是这里**（浅角度下差得最多：15° 时 9.3 米 → 15.6 米）。
     *
     * <p>扫描范围往上取一格：一 tick 最多下落约 0.7 格，可能这一 tick 正好跨过水面，
     * 此时水面方块在**当前位置之上**。往下取到 −3 是为了容错。
     */
    @Nullable
    private BlockPos scanForWater() {
        BlockPos base = this.blockPosition();
        for (int dy = 1; dy >= -3; dy--) {
            BlockPos p = base.offset(0, dy, 0);
            if (!isWater(p) || isWater(p.above())) {
                continue;                       // 只看"最上面那一格水"（顶上不是水）
            }
            double surface = p.getY() + this.level().getFluidState(p).getHeight(this.level(), p);
            if (this.getY() <= surface + WATER_LAND_MARGIN) {
                return p;
            }
        }
        return null;
    }

    private boolean isWater(BlockPos pos) {
        return this.level().getFluidState(pos).getType() == Fluids.WATER;
    }

    private void land(BlockPos waterPos) {
        this.setLanded(true);
        this.waterY = waterPos.getY() + 0.92;   // 略低于水面一格，看起来是浮着的
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(this.getX(), this.waterY, this.getZ());

        // 诊断：落点离抛出点多远。若这个数一直很小（<2 格），
        // 说明是"原地落下"，去看上一行的初速。
        com.whitecobalt.anglertackle.AnglersTackle.LOGGER.info(
                "[垂纶] 打窝落水：({}, {}, {}) 滞空 {} tick",
                String.format("%.1f", this.getX()), String.format("%.1f", this.getY()),
                String.format("%.1f", this.getZ()), this.tickCount);

        if (this.level() instanceof ServerLevel serverLevel) {
            // 入水水花
            serverLevel.sendParticles(ParticleTypes.SPLASH,
                    this.getX(), this.waterY + 0.1, this.getZ(),
                    16, 0.5, 0.2, 0.5, 0.06);
            serverLevel.sendParticles(ParticleTypes.BUBBLE,
                    this.getX(), this.waterY - 0.2, this.getZ(),
                    8, 0.4, 0.2, 0.4, 0.02);
            serverLevel.playSound(null, this.blockPosition(), SoundEvents.BUCKET_EMPTY_FISH,
                    SoundSource.NEUTRAL, 0.6f, 1.1f);
        }
    }

    // ------------------------------------------------------------------ 查询
    @Nullable
    public UUID ownerUuid() {
        return this.ownerUuid;
    }

    /**
     * 窝料**永久消失**时把全服名额还回去（用户 2026-10-11："全服至多存在 40 个窝料"）。
     *
     * <p>只有 {@code KILLED}（`/kill`、爆炸等）与 {@code DISCARDED}（我们的到期 {@code discard()}）
     * 才算"没了"。另外三种**不释放** —— 它们都还**存在**，只是不在内存里：
     * <ul>
     *   <li>{@code UNLOADED_TO_CHUNK}：区块卸载，实体存进了区块存档（一会儿还会回来）；</li>
     *   <li>{@code UNLOADED_WITH_PLAYER}：随玩家卸载，同样还在；</li>
     *   <li>{@code CHANGED_DIMENSION}：过传送门去了别的维度 —— 它还是同一条窝料。</li>
     * </ul>
     * 反过来（卸载就放名额）会变成"把窝子扔在远处、回来再打 40 个"✗，正是要防的那个刷法。
     *
     * <p>整段包 try/catch：记账失败绝不能把"实体被移除"这件事本身搞崩（那会卡住区块卸载）。
     */
    @Override
    public void remove(Entity.RemovalReason reason) {
        try {
            if (!this.level().isClientSide()
                    && this.level() instanceof ServerLevel sl
                    && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
                com.whitecobalt.anglertackle.fishing.ChumRegistry.get(sl).release(this.getUUID());
            }
        } catch (Throwable ignored) {
            // 释放失败最多让这个名额等到自然到期，不影响任何玩法
        }
        super.remove(reason);
    }

    public double timeMultiplier() {
        return this.timeMultiplier;
    }

    /** 稀有度倍率（恒 >= 1）。见字段注释：和等待时间倍率是两件事。 */
    public double rarityMultiplier() {
        return this.rarityMultiplier;
    }

    /** 是否还在加成有效期内（掉落物阶段不算）。 */
    public boolean isActive() {
        return isLanded() && getLifeTicks() > 0;
    }

    /** 剩余寿命比例 0~1，白圈快消失时用它闪烁。 */
    public float lifeRatio() {
        return Math.max(0.0f, Math.min(1.0f, getLifeTicks() / (float) Math.max(1, totalLifeTicks)));
    }

    // ------------------------------------------------------------------ 同步字段
    public int getTier() {
        return this.entityData.get(DATA_TIER);
    }

    public void setTier(int tier) {
        this.entityData.set(DATA_TIER, tier);
    }

    public boolean isSinking() {
        return this.entityData.get(DATA_SINKING);
    }

    public void setSinking(boolean sinking) {
        this.entityData.set(DATA_SINKING, sinking);
    }

    public boolean isLanded() {
        return this.entityData.get(DATA_LANDED);
    }

    public void setLanded(boolean landed) {
        this.entityData.set(DATA_LANDED, landed);
    }

    public float getBob() {
        return this.entityData.get(DATA_BOB);
    }

    public void setBob(float bob) {
        this.entityData.set(DATA_BOB, bob);
    }

    public int getLifeTicks() {
        return this.entityData.get(DATA_LIFE);
    }

    public void setLifeTicks(int ticks) {
        this.entityData.set(DATA_LIFE, Math.max(0, ticks));
    }

    // ------------------------------------------------------------------ 杂项
    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setTier(tag.getInt("ChumTier"));
        setSinking(tag.getBoolean("ChumSinking"));
        setLanded(tag.getBoolean("ChumLanded"));
        setLifeTicks(tag.getInt("ChumLife"));
        this.totalLifeTicks = Math.max(1, tag.getInt("ChumTotalLife"));
        this.timeMultiplier = tag.contains("ChumMult") ? tag.getDouble("ChumMult") : 1.0;
        // 老存档没有这个键 → 取 1.0（不加成）。**不能**拿 timeMultiplier 顶替：
        // 那正是"越好的窝越不出好鱼"那个 bug 的来源。
        this.rarityMultiplier = tag.contains("ChumRarity") ? tag.getDouble("ChumRarity") : 1.0;
        this.waterY = tag.getDouble("ChumWaterY");
        if (tag.hasUUID("ChumOwner")) {
            this.ownerUuid = tag.getUUID("ChumOwner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("ChumTier", getTier());
        tag.putBoolean("ChumSinking", isSinking());
        tag.putBoolean("ChumLanded", isLanded());
        tag.putInt("ChumLife", getLifeTicks());
        tag.putInt("ChumTotalLife", totalLifeTicks);
        tag.putDouble("ChumMult", this.timeMultiplier);
        tag.putDouble("ChumRarity", this.rarityMultiplier);
        tag.putDouble("ChumWaterY", this.waterY);
        if (this.ownerUuid != null) {
            tag.putUUID("ChumOwner", this.ownerUuid);
        }
    }
    /**
     * 窝料的**渲染距离**（2026-10-30 用户："稍微扔远点就看不到"）。
     *
     * <p>原版 {@link Entity#shouldRenderAtSqrDistance} 是按**包围盒尺寸**算的：
     * 尺寸 0.3 的实体只有 {@code (0.3 × 64)² ≈ 368} 平方格 ≈ **19 格** ——
     * 窝料是扔出去用的，19 格以外直接不画，看起来就像"消失了"。
     *
     * <p>这里改成固定 128 格（16,384 平方格）：与 {@code clientTrackingRange(12)}（192 格）
     * 一致地覆盖常见抛投距离，再远就交给服务端回收逻辑。
     */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        return distSqr < 128.0 * 128.0;
    }

}
