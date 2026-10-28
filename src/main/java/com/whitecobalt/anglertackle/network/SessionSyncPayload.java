package com.whitecobalt.anglertackle.network;

import com.whitecobalt.anglertackle.AnglersTackle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 服务端 → 客户端：一次钓鱼会话的完整快照（每 2 tick 一次）。
 *
 * <p>客户端只用它做**渲染与插值**，不做任何判定（服务端权威）。
 *
 * <p>字段说明：
 * <ul>
 *   <li>{@code tensionRatio} 张力 / 张力上限（0~1）</li>
 *   <li>{@code safeRatio} / {@code dangerRatio} 白区、黄区边界在条上的位置</li>
 *   <li>{@code fatigued} 是否处于疲劳期——反拉的最佳窗口</li>
 *   <li>{@code chargeProgress} 蓄力进度，客户端不自己计时，避免两端漂移</li>
 *   <li>{@code bobberX/Y/Z} 落点坐标——**即使浮漂实体没被同步到客户端**，
 *       幽灵鱼线也能靠它画出鱼线（docs/09-原版冲突与渲染优化.md §4.3）</li>
 * </ul>
 */
public record SessionSyncPayload(
        byte state,
        float tensionRatio,
        float staminaRatio,
        float distanceRatio,
        float safeRatio,
        float dangerRatio,
        byte struggleDir,
        boolean sprinting,
        boolean fatigued,
        /**
         * 浮漂是否已经存在。
         *
         * <p>蓄力阶段还没有浮漂，如果客户端照样按 {@code bobberX/Y/Z}（此时是玩家坐标）
         * 去画幽灵鱼线，就会在脚下出现一个"白框 + 竖线"。这个标志让客户端知道
         * 什么时候该画、什么时候什么都不画。
         */
        boolean bobberActive,
        short redZoneTicks,
        short redZoneLimit,
        float chargeProgress,
        double bobberX,
        double bobberY,
        double bobberZ,
        /**
         * 配件耐久：每项 {@code [槽位序号, 当前损耗, 耐久上限]} 三元组，扁平存放。
         *
         * <p>只收"有耐久定义"的配件（{@code maxDamage > 0}）——渔轮和鱼钩在
         * {@code ModItems.partProps} 里耐久是 0，它们根本不进这个列表，
         * HUD 上也不会出现（用户要求"有耐久定义的就显示"）。
         *
         * <p>用 {@code int[]} 扁平三元组而不是配一个 record 列表：槽位最多 6 个、
         * 每项都是小整数，VarInt 编码后整包才十几字节，不值得多定义一层类型。
         */
        int[] partsDurability,
        /**
         * 超承重的鱼正在烧竿。
         *
         * <p>为什么非要走同步：客户端**无法自己判断**这件事 ——
         * 它不知道咬钩的是哪条鱼、更不知道那条鱼多重。而超重警告必须在
         * 整个对抗过程中一直显示（动作栏那条消息会被后续消息顶掉，
         * 用户反馈"一拉超重提示就没"）。
         */
        boolean overCapacity,
        /** 竿的剩余耐久比例 0~1；{@code <0} 表示未知（没拿竿 / 无耐久）。 */
        float rodDurabilityRatio,
        /**
         * **上鱼后移动还要锁多少 tick**（0 = 不锁）。
         *
         * <p>⚠⚠ 这个字段是用户 2026-10-16 纠正设计之后加的，原话：
         * <blockquote>"我想要的是**上鱼后给鱼、视角缩放恢复正常，一秒后允许移动**"</blockquote>
         * 之前把三件事绑在同一个状态上（LANDING 期间：不给鱼 + 视角不收 + 锁移动），
         * 玩家看到的是"上鱼后视角仍然没正常、也不给鱼；一秒后才给鱼并恢复正常" ✗。
         *
         * <p>现在拆开：<b>给鱼与视角</b>走"上鱼那一刻发一份最终快照"（客户端见到
         * {@code movementLockTicks > 0} 就知道"鱼给了、视角该收了"），
         * <b>移动</b>由客户端拿这个 tick 数**自己倒计时**（不再依赖状态机）。
         */
        byte movementLockTicks,
        /**
         * **服务端权威**：这一场是不是路亚（{@code rodType()==LURE}）。
         *
         * <p>⚠ 2026-10-28：客户端"手拿什么竿 / 状态几"都可能被串掉，而视野缩放
         * 一旦误判就会在台钓上发生。竿型以服务端为准，客户端只照抄 —— 这样
         * **台钓场次在结构上就不可能进入缩放分支**。
         */
        boolean lureSession
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SessionSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(AnglersTackle.MOD_ID, "session_sync"));

    public static final StreamCodec<FriendlyByteBuf, SessionSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.state);
                buf.writeFloat(p.tensionRatio);
                buf.writeFloat(p.staminaRatio);
                buf.writeFloat(p.distanceRatio);
                buf.writeFloat(p.safeRatio);
                buf.writeFloat(p.dangerRatio);
                buf.writeByte(p.struggleDir);
                buf.writeBoolean(p.sprinting);
                buf.writeBoolean(p.fatigued);
                buf.writeBoolean(p.bobberActive);
                buf.writeShort(p.redZoneTicks);
                buf.writeShort(p.redZoneLimit);
                buf.writeFloat(p.chargeProgress);
                buf.writeDouble(p.bobberX);
                buf.writeDouble(p.bobberY);
                buf.writeDouble(p.bobberZ);
                // ⚠ 这两个字段的读写位置必须**完全对称**（都紧跟在 bobberZ 之后、
                //    配件耐久之前）。手写编解码错开一位不会报错，只会让后面
                //    所有字段读成别的值 —— 现象是"HUD 数字全乱"，连接却一切正常。
                buf.writeBoolean(p.overCapacity);
                buf.writeFloat(p.rodDurabilityRatio);
                // ⚠ 追加在**最后**：这个包是手写编解码，插在中间会让后面字段整体错位
                buf.writeByte(p.movementLockTicks);
                buf.writeBoolean(p.lureSession);
                // 配件耐久：先写条数，再逐条写 (槽位, 已损耗, 上限)
                int[] pd = p.partsDurability == null ? new int[0] : p.partsDurability;
                buf.writeByte(pd.length / 3);
                for (int i = 0; i + 2 < pd.length; i += 3) {
                    buf.writeByte(pd[i]);
                    buf.writeVarInt(pd[i + 1]);
                    buf.writeVarInt(pd[i + 2]);
                }
            },
            buf -> {
                byte state = buf.readByte();
                float tension = buf.readFloat();
                float stamina = buf.readFloat();
                float distance = buf.readFloat();
                float safe = buf.readFloat();
                float danger = buf.readFloat();
                byte struggleDir = buf.readByte();
                boolean sprinting = buf.readBoolean();
                boolean fatigued = buf.readBoolean();
                boolean bobberActive = buf.readBoolean();
                short rzTicks = buf.readShort();
                short rzLimit = buf.readShort();
                float charge = buf.readFloat();
                double bx = buf.readDouble();
                double by = buf.readDouble();
                double bz = buf.readDouble();
                // ⚠ 读取顺序必须和写入侧**逐字段一致**。这个包是手写编解码的，
                //    错开一位不会抛异常，只会让后面所有字段读成别的值
                //    （现象是"HUD 上的数字全乱"，但连接正常、日志也正常）。
                boolean overCap = buf.readBoolean();
                float rodRatio = buf.readFloat();
                byte moveLock = buf.readByte();
                boolean lureSession = buf.readBoolean();
                int n = buf.readByte() & 0xFF;
                int[] pd = new int[n * 3];
                for (int i = 0; i < n; i++) {
                    pd[i * 3] = buf.readByte();
                    pd[i * 3 + 1] = buf.readVarInt();
                    pd[i * 3 + 2] = buf.readVarInt();
                }
                return new SessionSyncPayload(state, tension, stamina, distance, safe, danger,
                        struggleDir, sprinting, fatigued, bobberActive, rzTicks, rzLimit,
                        charge, bx, by, bz, pd, overCap, rodRatio, moveLock, lureSession);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
