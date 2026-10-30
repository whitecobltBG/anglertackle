package com.whitecobalt.anglertackle.network;

import com.whitecobalt.anglertackle.AnglersTackle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * 客户端 → 服务端：改装请求。
 *
 * @param action    0 = 装上（从背包取该物品装到槽位）
 *                  1 = 卸下（把槽位里的部件退回背包）
 *                  2 = 请求刷新界面数据
 * @param slot      槽位序号 0–5（竿身/鱼线/渔轮/铅锤/拟饵/浮漂）
 * @param itemId    要装的物品 id（action=0 时有效；空串表示自动挑第一个可用的）
 */
public record RodModActionPayload(byte action, byte slot, String itemId) implements CustomPacketPayload {

    public static final byte ACTION_EQUIP = 0;
    public static final byte ACTION_UNEQUIP = 1;
    public static final byte ACTION_REFRESH = 2;

    public static final CustomPacketPayload.Type<RodModActionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(AnglersTackle.MOD_ID, "rod_mod_action"));

    public static final StreamCodec<FriendlyByteBuf, RodModActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeByte(p.slot);
                buf.writeUtf(p.itemId);
            },
            buf -> new RodModActionPayload(buf.readByte(), buf.readByte(), buf.readUtf())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
