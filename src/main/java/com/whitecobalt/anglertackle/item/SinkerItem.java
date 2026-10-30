package com.whitecobalt.anglertackle.item;

import com.whitecobalt.anglertackle.fishing.FishLayers;

/**
 * 铅锤（2026-10-30 替换鱼钩槽，**仅路亚可用**）。
 *
 * <h2>它只做一件事</h2>
 * 按**水层**给上鱼权重乘倍率 —— 不改概率、不改个体大小、不改咬钩等待
 * （用户原话："仅权重，我打错字了"）。
 *
 * <h2>为什么效果放在物品上，而不是塞进 {@link PartStats}</h2>
 * {@code PartStats} 是六个槽位共用的**序列化** record（有 Codec 与 StreamCodec），
 * 为一个槽位的专属数据去加字段，会让所有存档格式跟着动（与 {@code roundCapBonus} 同一条理由）。
 * 铅锤的效果是"这个物品本身是什么"，放物品类上天然按物品区分，不用序列化。
 *
 * <h2>命名</h2>
 * g8 / g2 是日式 <b>ガン玉号数</b>，3b / 5b 是 <b>B 系咬铅</b>，0.3号 / 0.5号 是台钓号数铅 ——
 * tooltip 里**不写克重**：本机查不到可靠的号数-克重对照表，写"约 X g"就是编数字
 * （用户 2026-10-30 确认"这些号数克重很轻"）。
 */
public class SinkerItem extends TacklePartItem {

    /** 四层各自的倍率，下标 = {@link FishLayers.Layer#ordinal()}；1.0 = 不生效。 */
    private final double[] layerBonus;

    /**
     * @param layerBonus 长度必须是 {@link FishLayers.Layer#values()}.length
     */
    public SinkerItem(Properties properties, double... layerBonus) {
        super(Slot.SINKER, properties);
        if (layerBonus.length != FishLayers.Layer.values().length) {
            throw new IllegalArgumentException("铅锤倍率必须是 " + FishLayers.Layer.values().length
                    + " 个数（每层一个），实际 " + layerBonus.length);
        }
        for (double v : layerBonus) {
            if (!(v > 0.0) || Double.isNaN(v)) {
                throw new IllegalArgumentException("铅锤倍率必须 > 0，实际 " + v);
            }
        }
        this.layerBonus = layerBonus.clone();
    }

    /** 这一层上鱼权重的倍率（1.0 = 不影响）。 */
    public double bonus(FishLayers.Layer layer) {
        return layerBonus[layer.ordinal()];
    }

    /** 是否是"有效果"的铅锤（全 1.0 等于没装）。 */
    public boolean hasEffect() {
        for (double v : layerBonus) {
            if (v != 1.0) {
                return true;
            }
        }
        return false;
    }
}
