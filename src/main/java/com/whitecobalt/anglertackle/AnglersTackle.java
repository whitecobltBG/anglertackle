package com.whitecobalt.anglertackle;

import com.whitecobalt.anglertackle.registry.ModBlockEntities;
import com.whitecobalt.anglertackle.registry.ModBlocks;
import com.whitecobalt.anglertackle.registry.ModDataComponents;
import com.whitecobalt.anglertackle.registry.ModEntities;
import com.whitecobalt.anglertackle.registry.ModItems;
import com.whitecobalt.anglertackle.registry.ModSounds;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《垂纶》Angler's Tackle — 主类。
 *
 * <p>设计文档见仓库 {@code docs/} 目录：
 * <ul>
 *   <li>{@code 02-钓鱼小游戏机制.md} — 状态机与张力模型（核心手感）</li>
 *   <li>{@code 11-配件系统详表.md} — 六槽配件与属性合成</li>
 *   <li>{@code 06-技术方案.md} — 架构、注册表、网络包</li>
 * </ul>
 *
 * <p>本类在 M1（手感原型）阶段只负责注册与配置；玩法逻辑放在
 * {@code fishing} 包下的 {@code FishingManager} / {@code FishingSession}。
 */
@Mod(AnglersTackle.MOD_ID)
public final class AnglersTackle {

    /** 模组 ID，与 neoforge.mods.toml、资源包命名空间一致。 */
    public static final String MOD_ID = "anglertackle";

    public static final Logger LOGGER = LoggerFactory.getLogger("Angler's Tackle");

    public AnglersTackle(IEventBus modBus, ModContainer container) {
        LOGGER.info("[垂纶] Angler's Tackle 正在加载 · Minecraft 1.21.1 / NeoForge");
        LOGGER.info("[垂纶] 加载自 {}", loadedFrom());

        // 配置：分 COMMON / SERVER / CLIENT 三类，详见 docs/04-UI线框.md §9
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        // ★ 鱼种数值必须在**配置加载完成之后**装载一次。
        //
        // 踩过的坑（手感"很软"的真凶）：我只在 Tuning.reload()（游戏内 /atl config reload）
        // 里调用了 FishTuning.build()，**启动路径上一次都没调用**。
        // 于是 VALUES 一直是空 map，每条鱼都走兜底值（鲫鱼的 0.7 拉力 / 0.9 张力系数）——
        // 所有鱼手感一模一样地软，而且配置文件里怎么改都没反应。
        //
        // 为什么不能直接在这里调 build()：这一行还在 mod 构造阶段，
        // registerConfig 只是**登记**，配置真正加载晚于所有 mod 构造。
        // 此刻读配置会抛 "Cannot get config value before config is loaded."
        //（同一个坑已经踩过一次，见 tools/check-no-config-at-registration.js）。
        // 正确做法是监听配置加载事件 —— 那时读才安全。
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading e) -> {
            if (e.getConfig().getType() == ModConfig.Type.COMMON) {
                com.whitecobalt.anglertackle.fishing.FishTuning.build();
            }
        });
        // 重载（改完文件回主菜单等场景）也要跟上
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Reloading e) -> {
            if (e.getConfig().getType() == ModConfig.Type.COMMON) {
                com.whitecobalt.anglertackle.fishing.FishTuning.build();
            }
        });

        // ---- 注册表 ----
        ModDataComponents.register(modBus);
        // 菜型效果（docs/28）：11 个 MobEffect，吃菜时由 DishEffects 发放
        com.whitecobalt.anglertackle.effect.ModEffects.EFFECTS.register(modBus);
        ModItems.register(modBus);
        ModEntities.register(modBus);
        // 配方序列化器：工作台 / 随身 2×2 合成栏装配件（anglertackle:rod_assembly）
        com.whitecobalt.anglertackle.registry.ModRecipes.register(modBus);

        // 实体属性。**必须挂在 mod 事件总线上**，而且必须是 EntityAttributeCreationEvent ——
        // 漏了这一步的症状是"实体能生成，但一出现就 NaN 血/立刻倒地"，
        // 报错里完全看不出是属性没注册（属性表是空的，不是报错）。
        modBus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(ModEntities.FISHERMAN.get(), com.whitecobalt.anglertackle.entity.FishermanEntity.createAttributes().build());
            // 21 种鱼共用一份属性。AbstractFish 的 createAttributes() 是 static，
            // static 不参与继承 —— 必须在这里逐个注册。
            // 漏一条的表现是"那条鱼一出现就死"（属性表是空的，不是报错）。
            for (var holder : ModEntities.FISHES.values()) {
                e.put(holder.get(), com.whitecobalt.anglertackle.entity.ModFish.createAttributes().build());
            }
        });
        // 玩家附件（鱼币 + 图鉴）。必须在 mod 构造期注册 —— 附件类型的注册
        // 和其它 registry 一样走 DeferredRegister，晚了就拿不到。
        com.whitecobalt.anglertackle.registry.ModAttachments.register(modBus);

        // 调试命令 /atl（看竿的耐久与槽位、修竿）——挂在游戏事件总线上
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.event.RegisterCommandsEvent.class,
                event -> com.whitecobalt.anglertackle.command.AnglerCommands.register(event.getDispatcher()));

        // M2+ 逐步填入：
        ModBlocks.register(modBus);
        ModBlockEntities.register(modBus);
        // ModMenus.register(modBus);
        ModSounds.register(modBus);

        LOGGER.info("[垂纶] 注册完成：数据组件 / 物品 / 实体");
    }

    /**
     * 这个 mod 是从哪个文件加载的 —— 文件名 + 大小 + 修改时间。
     *
     * <p>为什么要专门打这一行：出过两次"对方装的是旧 jar"的事故。
     * 最典型的一次是 2026-09-30 17:11 的崩溃报告 —— 里面是
     * {@code ModItems.java:117 → Tuning.hempTensionCeiling} 的调用栈，
     * 而那个 bug 早在三小时前就修掉了。对方 mods 里的文件名是
     * {@code anglertackle-1.0.1 (1).jar}（Windows 的重名副本后缀），
     * 内容是 14:22 那一版，而发给他的却是同一个文件名下的新版本 ——
     * <b>只看文件名和 mod 版本号根本分不出是哪一次构建</b>。
     *
     * <p>所以每次启动都把自己是谁打出来。以后任何一份崩溃报告，
     * 第一行就能回答"他到底装的是哪一版"，不用再靠调用栈去反推。
     *
     * <p>用 {@code getCodeSource()} 而不是读构建时生成的资源文件：
     * 开发环境（classes 目录）和生产环境（jar）走同一条路径，
     * 不需要往 jar 里塞任何东西，也不会有"构建脚本忘了更新戳记"的可能。
     */
    private static String loadedFrom() {
        try {
            var src = AnglersTackle.class.getProtectionDomain().getCodeSource();
            if (src == null) {
                return "(未知来源)";
            }
            var p = java.nio.file.Paths.get(src.getLocation().toURI());
            if (java.nio.file.Files.isDirectory(p)) {
                return "(开发环境) " + p;
            }
            long size = java.nio.file.Files.size(p);
            String mtime = java.nio.file.Files.getLastModifiedTime(p).toString();
            // FileTime.toString() 形如 2026-09-30T16:53:10.123+08:00，取前面那段
            if (mtime.length() > 19) {
                mtime = mtime.substring(0, 19).replace('T', ' ');
            }
            return p.getFileName() + "（" + size + " 字节，" + mtime + "）";
        } catch (Throwable t) {
            // 这一行纯粹是给人看的诊断信息，绝不能因为它让游戏起不来
            return "(读取失败：" + t.getClass().getSimpleName() + ")";
        }
    }
}
