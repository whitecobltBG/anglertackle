package com.whitecobalt.anglertackle.shop;

import com.whitecobalt.anglertackle.player.FishermanData;
import com.whitecobalt.anglertackle.player.FishermanProgress;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * 把商店状态打到聊天栏 —— 用来在**没有界面**的时候验证规则。
 *
 * <h2>为什么值得单独写</h2>
 * 商店的规则（解锁档位 / 价格 / 免费 / 兑换 / 限购补货）全都"不报错"：
 * 判定写错了只表现为"这件东西买不了"或"价格不对"，而没人会去逐个核对 39 件商品。
 * 把它们一次性打印出来，"哪几件不该解锁却解锁了""哪件价格是 0"一眼就能看见。
 *
 * <p>界面做出来之后这个命令**依然有用** —— 玩家报"XX 买不了"时，
 * 让他跑一次 {@code /atl shop} 把输出发过来，比来回问快得多。
 */
public final class ShopDebug {

    private ShopDebug() {
    }

    /** 一页最多打这么多行，超过就提示用分类查看（聊天栏刷屏也很烦）。 */
    private static final int MAX_LINES = 30;

    public static void dump(ServerPlayer player) {
        FishermanProgress p = FishermanData.get(player);
        long now = player.level().getGameTime();

        line(player, "§6=== 钓鱼商店 · 规则核对 ===");
        line(player, "§7垂纶等级：§fLv." + p.level() + " §7（经验 " + p.xp() + "/"
                + (p.atGate() ? "§c卡在评定" : com.whitecobalt.anglertackle.player.AnglerLeveling
                        .xpToNext(p.level())) + "§7）"
                + "　§7商店档位：§f" + p.stage() + " §7/ " + FishermanProgress.MAX_STAGE);
        line(player, "§7商店：" + (p.talked() ? "§a已开启" : "§c未开启（还没跟老柯说过话）")
                + (p.atGate() ? "　§e下一次评定：Lv." + p.level() + " 交 "
                        + com.whitecobalt.anglertackle.player.AnglerLeveling.describe(p.level()) : ""));

        // 按分类分组打印：同一类的放一起，"某类整类不可买"一眼就看出来
        int shown = 0;
        for (ShopCatalog.Category cat : ShopCatalog.Category.values()) {
            List<ShopCatalog.Entry> list = ShopCatalog.ENTRIES.stream()
                    .filter(e -> e.category() == cat).toList();
            if (list.isEmpty()) {
                continue;
            }
            if (shown >= MAX_LINES) {
                line(player, "§8…（还有更多，先用 /atl shop 看完再往下）");
                break;
            }
            line(player, "§e" + catName(cat) + "§8（" + list.size() + " 件）");
            for (ShopCatalog.Entry e : list) {
                if (shown++ >= MAX_LINES) {
                    break;
                }
                line(player, "  " + describe(player, p, e, now));
            }
        }
        line(player, "§8提示：/atl shop stage <1-50> 改垂纶等级、/atl shop talk 开启商店、"
                + "/atl shop resetstock 清限购");
    }

    private static String describe(ServerPlayer player, FishermanProgress p,
                                   ShopCatalog.Entry e, long now) {
        boolean unlocked = p.unlocked(e.unlockStage());
        String head = (unlocked ? "§a✔ " : "§c✘ ") + "§f" + e.id() + " §8[" + e.tier() + "档]";

        String how;
        switch (e.kind()) {
            case FREE -> how = "§b免费";
            case BUY -> how = "§6" + e.price() + " 币";
            case EXCHANGE -> how = "§d兑换 " + e.exchange();
            default -> how = "?";
        }

        String gate = unlocked ? "" : " §8(需等级 " + e.unlockStage() + ")";

        String stock = "";
        if (e.limited()) {
            long wait = p.restockWait(e.id(), now);
            if (wait > 0) {
                stock = " §c售罄，还等 " + (wait / 1200L) + " 分钟";
            } else {
                stock = " §7限购 " + p.stockOf(e.id()).bought() + "/" + e.stockPer();
            }
        }
        return head + " §8| " + how + gate + stock;
    }

    private static String catName(ShopCatalog.Category c) {
        return switch (c) {
            case ROD -> "竿";
            case LINE -> "鱼线";
            case REEL -> "渔轮";
            case SINKER -> "铅锤";
            case LURE -> "拟饵";
            case BAIT -> "鱼饵";
            case CHUM -> "窝料";
        };
    }

    private static void line(ServerPlayer player, String s) {
        player.displayClientMessage(Component.literal(s).withStyle(ChatFormatting.RESET), false);
    }
}
