package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.AnglersTackle;
import com.whitecobalt.anglertackle.Config;

/**
 * 调参值的**运行时读取层**。
 *
 * <p><b>为什么需要这一层</b>：原先所有调参值都是
 * {@code private static final double REEL_GAIN = Config.INSTANCE.xxx.get();}
 * —— 静态常量只在**类加载时求值一次**。于是：
 * <ul>
 *   <li>改配置文件后必须**重启整个游戏**才生效</li>
 *   <li>调一个数值要经历"改文件 → 重启 → 抛竿 → 观察"的循环，一次几分钟</li>
 * </ul>
 * 对做手感调参来说这个循环太慢，所以改成运行时读取。
 *
 * <p><b>性能</b>：{@link com.whitecobalt.anglertackle.Config} 里的
 * {@code ModConfigSpec.DoubleValue.get()} 只是读一个已加载的字段，不是文件 IO，
 * 每 tick 调用几次的开销可以忽略（对比同一 tick 里已有的实体/粒子/网络开销）。
 * 热路径上如果确实在意，可以在方法内取一次局部变量复用。
 *
 * <p><b>热重载</b>：{@code /atl config reload} 会调 {@code Config.SPEC.reload()}，
 * NeoForge 重新读盘并更新 {@code ConfigValue}；而本类的所有 getter 都是现读现取，
 * 所以**下一条鱼就会用新值**，不用重启。
 */
public final class Tuning {

    private Tuning() {
    }

    // ================================================================ 张力
    public static double reelGain() {
        return cfg().tensionReelGain.get();
    }

    public static double releaseLoss() {
        return cfg().tensionReleaseLoss.get();
    }

    public static double idleLoss() {
        return cfg().tensionIdleLoss.get();
    }

    /** 白区上限占张力上限的比例（0~1）。config 里写的是"基准 100 的百分比"。 */
    public static double whiteZoneRatio() {
        return cfg().tensionWhiteMax.get() / 100.0;
    }

    /** 黄区上限占张力上限的比例（0~1）。 */
    public static double yellowZoneRatio() {
        return cfg().tensionYellowMax.get() / 100.0;
    }

    /** 深红闪烁阈值占张力上限的比例（0~1）。 */
    public static double criticalRatio() {
        return Math.min(0.999, cfg().tensionCriticalFlash.get() / 100.0);
    }

    public static double baseRecover() {
        return cfg().tensionBaseRecover.get();
    }

    // ============================================================ 鱼的行为
    public static double pullIdle() {
        return cfg().pullIdleBase.get();
    }

    public static double pullStruggle() {
        return cfg().pullStruggleBase.get();
    }

    /** 收线时每秒扣掉的鱼力比例（相对满体力）。 */
    public static double staminaDrainPerSec() {
        return cfg().staminaDrainBase.get();
    }

    public static double staminaDrainPerTick() {
        return staminaDrainPerSec() / 20.0;
    }

    // ============================================================ 配件合成
    public static double wLineOnTension() {
        return cfg().weightLineOnTension.get();
    }

    public static double wRodOnTension() {
        return cfg().weightRodOnTension.get();
    }

    public static double wLineOnRecover() {
        return cfg().weightLineOnRecover.get();
    }

    public static double wRodOnRecover() {
        return cfg().weightRodOnRecover.get();
    }

    public static double wReelOnDrain() {
        return cfg().weightReelOnDrain.get();
    }

    public static double wRodOnDrain() {
        return cfg().weightRodOnDrain.get();
    }

    public static double capTensionCeiling() {
        return cfg().capTensionCeiling.get();
    }

    public static double capTensionRecover() {
        return cfg().capTensionRecover.get();
    }

    public static double capStaminaDrain() {
        return cfg().capStaminaDrain.get();
    }

    public static double capPull() {
        return cfg().capPull.get();
    }

    public static double capDamping() {
        return cfg().capDamping.get();
    }

    // ================================================================ 其他
    public static double hookWindowBase() {
        return cfg().hookWindowBase.get();
    }

    public static double hookWindowMin() {
        return cfg().hookWindowMin.get();
    }

    public static double biteMinWait() {
        return cfg().biteMinWait.get();
    }

    public static double biteMaxWait() {
        return cfg().biteMaxWait.get();
    }

    public static double biteHardCap() {
        return cfg().biteHardCap.get();
    }

    /**
     * 等待下限秒数（台钓减时 + 打窝叠加后的地板）。
     *
     * <p>台钓减时那一组（floorSeconds / floatRodCutPercent / baitCut*Percent）
     * 走 {@link com.whitecobalt.anglertackle.fishing.BiteTiming} 读配置，
     * 这里只把下限再暴露一份 —— {@code BiteTiming.floorSeconds()} 是权威口径，
     * 消费点都用它（见 check-bite-time.js 的断言）。
     */
    public static double biteFloorSeconds() {
        return cfg().biteFloorSeconds.get();
    }

    // ------------------------------------------------ 时间 / 天气 / 月相
    // 见 docs/24-时间天气月相策划.md。环境只改抽签权重，不改咬钩节奏
    // （唯一例外 biteNightCutPercent，由 BiteTiming 读）。

    public static boolean environmentEnabled() {
        return cfg().environmentEnabled.get();
    }

    /** 时段组占比最多提升几个百分点（用户定案 10）。 */
    public static double envTimeDeltaPct() {
        return cfg().envTimeDeltaPct.get() / 100.0;
    }

    /** 天气组占比最多提升几个百分点（用户定案 10）。 */
    public static double envWeatherDeltaPct() {
        return cfg().envWeatherDeltaPct.get() / 100.0;
    }

    /** 月相：所有金红鱼一起分享的爆率预算（用户定案 5 个百分点）。 */
    public static double envMoonGoldRedDeltaPct() {
        return cfg().envMoonGoldRedDeltaPct.get() / 100.0;
    }

    /** 月相：所有紫鱼一起分享的爆率预算（用户定案 15 个百分点）。 */
    public static double envMoonPurpleDeltaPct() {
        return cfg().envMoonPurpleDeltaPct.get() / 100.0;
    }

    /** 月相预算分法：0 = 组内同倍率，1 = 按条数均摊。 */
    public static int envMoonShareMode() {
        return cfg().envMoonShareMode.get();
    }

    /** 安全阀：单组占比不得超过它（0~1）。 */
    public static double envShareCap() {
        return cfg().envShareCapPct.get() / 100.0;
    }

    public static boolean envShowCondition() {
        return cfg().envShowCondition.get();
    }

    public static boolean envLogToChat() {
        return cfg().envLogToChat.get();
    }

    /** 夜间台钓的咬钩等待减免百分比（0~1；0 = 关闭）。 */
    public static double biteNightCutPercent() {
        return cfg().biteNightCutPercent.get() / 100.0;
    }

    public static int lineDurabilityPerBite() {
        return cfg().lineDurabilityPerBite.get();
    }

    public static int lureDurabilityPerBite() {
        return cfg().lureDurabilityPerBite.get();
    }

    public static int rodDurabilityPerBite() {
        return cfg().rodDurabilityPerBite.get();
    }

    public static int lineDurabilityPerBreak() {
        return cfg().lineDurabilityPerBreak.get();
    }

    public static int chumParticleIntervalTicks() {
        return cfg().chumParticleIntervalTicks.get();
    }

    public static int chumParticlesPerBurst() {
        return cfg().chumParticlesPerBurst.get();
    }

    /** 窝子半径（格）。见 ChumManager.radius() —— 那边早先把它存成了 static 常量，会崩。 */
    public static double chumRadius() {
        return cfg().chumRadius.get();
    }

    /**
     * 全服同时存在的窝料上限（条）。用户 2026-10-11："**全服至多存在 40 个窝料**"。
     *
     * <p>夹到 ≥1：配置里允许填 0（"我不想要上限"的直觉写法），但 0 会让
     * <b>一条窝料都打不出去</b> —— 那不是"没上限"，是"禁用打窝"✗。
     * 所以这里把 ≤0 当成 1（要真禁用打窝，该用别的开关，而不是让这个数字兼职）。
     */
    public static int chumServerCap() {
        return Math.max(1, cfg().chumServerCap.get());
    }

    public static int ghostLineBeyondBlocks() {
        return cfg().ghostLineBeyondBlocks.get();
    }

    // ============================================================ 称号（用户 2026-10-11）
    //
    // 门槛数字全部走配置（原话："这些数字全部进配置，不写死在代码里"）。
    // ⚠ 这里**没有**"能出金的鱼种数"这类派生量 —— 那些从 FishSpecies 当场数
    //   （写死就会出现"47 种全金"那种永远拿不到的条件，见 docs/38 §4.1）。
    /** 聊天称号后缀模式：0 full_line / 1 message_only / 2 vanilla。 */
    public static int titleChatMode() {
        return cfg().titleChatMode.get();
    }

    public static int titleCodexCount5() {
        return cfg().titleCodexCount5.get();
    }

    public static int titleCodexCount10() {
        return cfg().titleCodexCount10.get();
    }

    public static int titleCodexCount24() {
        return cfg().titleCodexCount24.get();
    }

    public static int titleCodexCount40() {
        return cfg().titleCodexCount40.get();
    }

    public static int titleGoldCount10() {
        return cfg().titleGoldCount10.get();
    }

    public static double titleGiantKg5() {
        return cfg().titleGiantKg5.get();
    }

    public static double titleGiantKg20() {
        return cfg().titleGiantKg20.get();
    }

    public static double titleGiantKg50() {
        return cfg().titleGiantKg50.get();
    }

    public static double titleGiantKg100() {
        return cfg().titleGiantKg100.get();
    }

    public static int titleGarCount() {
        return cfg().titleGarCount.get();
    }

    public static int titleRedCount() {
        return cfg().titleRedCount.get();
    }

    public static int titleNightCount() {
        return cfg().titleNightCount.get();
    }

    public static int titleFullMoonCount() {
        return cfg().titleFullMoonCount.get();
    }

    public static int titleIceCount() {
        return cfg().titleIceCount.get();
    }

    public static int titleChumCount() {
        return cfg().titleChumCount.get();
    }

    public static int titleLineBreakCount() {
        return cfg().titleLineBreakCount.get();
    }

    public static int titleDishMadeCount() {
        return cfg().titleDishMadeCount.get();
    }

    public static int titleDishEatenCount() {
        return cfg().titleDishEatenCount.get();
    }

    // ============================================================ 邮件（用户 2026-10-11；docs/36 §2.4）
    /** 邮件保管期（**现实天**）—— 过期只作废附件、正文保留。 */
    public static int mailKeepDays() {
        return Math.max(1, cfg().mailKeepDays.get());
    }

    /** 邮箱条数上限 —— 到顶**拒绝投递**（绝不静默丢件）。 */
    public static int mailMaxMails() {
        return Math.max(1, cfg().mailMaxMails.get());
    }

    /** 服主手写正文的长度上限（发送侧拒绝 + 渲染侧再截断，两道都要有）。 */
    public static int mailBodyMaxChars() {
        return Math.max(20, cfg().mailBodyMaxChars.get());
    }

    /** 进世界时给一次未读邮件的动作栏提示（**只一次**）。 */
    public static boolean mailUnreadLoginNotice() {
        return cfg().mailUnreadLoginNotice.get();
    }

    // ============================================================ 委托（用户 2026-10-14 定案；docs/40）
    /** 委托系统总开关。 */
    public static boolean questEnabled() {
        return cfg().questEnabled.get();
    }

    /** 每游戏日投递几条每日委托。 */
    public static int questDailyCount() {
        return Math.max(1, cfg().questDailyCount.get());
    }

    /** 每游戏周投递几条周常委托。 */
    public static int questWeeklyCount() {
        return Math.max(1, cfg().questWeeklyCount.get());
    }

    /** 完成一个每日委托给多少活跃度。 */
    public static int questActivityPerDaily() {
        return Math.max(1, cfg().questActivityPerDaily.get());
    }

    /** 完成一个周常委托给多少活跃度。 */
    public static int questActivityPerWeekly() {
        return Math.max(1, cfg().questActivityPerWeekly.get());
    }

    /** 每周活跃度上限。 */
    public static int questActivityCapWeekly() {
        return Math.max(10, cfg().questActivityCapWeekly.get());
    }

    // ============================================================ 联赛（用户 2026-10-11 定案；docs/37）
    /** 计时口径：true = 推送间隔按**现实天**（默认）/ false = 按**游戏日**。 */
    public static boolean leagueUseRealDays() {
        return cfg().leagueUseRealDays.get();
    }

    /** 邀请资格线（默认 21 = 过了 Lv.20 评定②）。 */
    public static int leagueMinLevel() {
        return cfg().leagueMinLevel.get();
    }

    /** 未达线者也收邀请（新服防"长期无人参赛"）。 */
    public static boolean leagueAllowBelowLevel() {
        return cfg().leagueAllowBelowLevel.get();
    }

    public static int leagueSmallDays() {
        return Math.max(1, cfg().leagueSmallDays.get());
    }

    public static int leagueMediumDays() {
        return Math.max(1, cfg().leagueMediumDays.get());
    }

    public static int leagueLargeDays() {
        return Math.max(1, cfg().leagueLargeDays.get());
    }

    /** 一期硬截止（**游戏日**，用户定案 5）。 */
    public static int leaguePeriodGameDays() {
        return Math.max(1, cfg().leaguePeriodGameDays.get());
    }

    /** 每人窗口（**游戏分钟**，用户定案 30）。 */
    public static int leaguePlayerWindowMinutes() {
        return Math.max(1, cfg().leaguePlayerWindowMinutes.get());
    }

    /** 现实口径下每期的起始本地时刻（0~23）。 */
    public static int leaguePeriodBoundaryHour() {
        return Math.max(0, Math.min(23, cfg().leaguePeriodBoundaryHour.get()));
    }

    /**
     * 计分口径下标（0=单鱼最重 / 1=总重量 / 2=金红尾数 / 3=图鉴完成度）。
     *
     * <p>⚠ 与 {@code League.Rule} 的枚举顺序强绑定（{@code Rule.byMode} 按下标取），
     * 由 {@code tools/check-league.js} 双向核对。
     */
    public static int leagueRuleMode() {
        return Math.max(0, Math.min(3, cfg().leagueRuleMode.get()));
    }

    /** 有效成绩下限（至少上鱼 N 尾才进榜）。 */
    public static int leagueMinCatchesToRank() {
        return Math.max(1, cfg().leagueMinCatchesToRank.get());
    }

    public static int leagueRewardCoinsParticipant() {
        return Math.max(0, cfg().leagueRewardCoinsParticipant.get());
    }

    public static int leagueRewardCoinsTop10() {
        return Math.max(0, cfg().leagueRewardCoinsTop10.get());
    }

    public static int leagueRewardCoins1st() {
        return Math.max(0, cfg().leagueRewardCoins1st.get());
    }

    public static int leagueRewardCoins2nd() {
        return Math.max(0, cfg().leagueRewardCoins2nd.get());
    }

    public static int leagueRewardCoins3rd() {
        return Math.max(0, cfg().leagueRewardCoins3rd.get());
    }

    public static int leagueRewardXp1st() {
        return Math.max(0, cfg().leagueRewardXp1st.get());
    }

    public static int leagueRewardXp2nd() {
        return Math.max(0, cfg().leagueRewardXp2nd.get());
    }

    public static int leagueRewardXp3rd() {
        return Math.max(0, cfg().leagueRewardXp3rd.get());
    }

    public static double leagueScaleMediumCoinsXp() {
        return cfg().leagueScaleMediumCoinsXp.get();
    }

    public static double leagueScaleLargeCoinsXp() {
        return cfg().leagueScaleLargeCoinsXp.get();
    }

    public static double leagueScaleLargeVanillaItems() {
        return cfg().leagueScaleLargeVanillaItems.get();
    }

    /** 联赛奖励类邮件（邀请 / 成绩单）的保管期（现实天，据调研改：默认 365）。 */
    public static int leagueRewardMailKeepDays() {
        return Math.max(1, cfg().leagueRewardMailKeepDays.get());
    }

    /** 联赛通知类邮件的保管期（现实天）。 */
    public static int leagueNoticeMailKeepDays() {
        return Math.max(1, cfg().leagueNoticeMailKeepDays.get());
    }

    /** 登录时补发当前期邀请。 */
    public static boolean leagueInviteOnLogin() {
        return cfg().leagueInviteOnLogin.get();
    }

    // ============================================================ 比赛（用户 2026-10-11 定案；docs/35）
    /** 默认时长（游戏分钟）。 */
    public static int contestDefaultMinutes() {
        return Math.max(1, cfg().contestDefaultMinutes.get());
    }

    /** 默认赛制下标（0=最重一尾 / 1=总重量 / 2=金红尾数）—— 与 {@code Contest.Rule} 顺序绑定。 */
    public static int contestDefaultRuleMode() {
        return Math.max(0, Math.min(2, cfg().contestDefaultRuleMode.get()));
    }

    /** 低于这个在线人数就不开赛（默认 1）。 */
    public static int contestMinPlayers() {
        return Math.max(1, cfg().contestMinPlayers.get());
    }

    public static int contestRewardFirst() {
        return Math.max(0, cfg().contestRewardFirst.get());
    }

    public static int contestRewardSecond() {
        return Math.max(0, cfg().contestRewardSecond.get());
    }

    public static int contestRewardThird() {
        return Math.max(0, cfg().contestRewardThird.get());
    }

    /** 自动开赛间隔（分钟；0 = 只手动）。 */
    public static int contestAutoEveryMinutes() {
        return Math.max(0, cfg().contestAutoEveryMinutes.get());
    }

    /** 结算公告前几名（1~10）。 */
    public static int contestAnnounceTop() {
        return Math.max(1, Math.min(10, cfg().contestAnnounceTop.get()));
    }

    // ============================================================ 热重载
    /**
     * 重新从磁盘读配置，**不重启游戏**。
     *
     * <p>为什么不是一行 {@code spec.reload()}：NeoForge 1.21.1 的
     * {@code ModConfigSpec} 没有公开 reload（只有 {@code resetCaches} / {@code save}，
     * {@code ConfigTracker} 也没有公开的 reload）。所以这里自己做：
     * <ol>
     *   <li>读配置文件（NeoForge 用它自己的读写路径，见 {@link #configFile()}）</li>
     *   <li>按 {@code [分组].键} 的路径找到每个 {@code ConfigValue}，用 {@code set()} 写进内存</li>
     *   <li>{@code resetCaches(RestartType.NONE)} 让下次 {@code get()} 不读旧缓存</li>
     * </ol>
     * 之后本类所有 getter 立刻返回新值 —— 下一条鱼就用新参数。
     *
     * <p><b>失败不影响玩法</b>：任何一步出错都只记日志并返回 false，
     * 玩家重启游戏即可（也就是没有这个功能时的行为）。
     *
     * @return 成功重载的项数；0 表示没读到或没变化，-1 表示出错
     */
    public static int reload() {
        try {
            java.nio.file.Path file = configFile();
            if (file == null || !java.nio.file.Files.isRegularFile(file)) {
                AnglersTackle.LOGGER.warn("[垂纶] 热重载失败：找不到配置文件 {}", file);
                return -1;
            }
            java.util.Map<String, Object> flat = parseFlatToml(file);
            if (flat.isEmpty()) {
                AnglersTackle.LOGGER.warn("[垂纶] 热重载：配置文件解析为空 {}", file);
                return -1;
            }

            final Config c = cfg();
            int[] applied = {0};
            // ConfigValue.set 写的是内存里那份 CommentedConfig（getRaw 读的同一份）。
            // 写完必须 clearCache()，否则 get() 还返回旧缓存 ——
            // 这里逐个清而不是用 resetCaches()：后者要匹配 RestartType，
            // 一旦类型对不上就静默不清（表现就是"敲了 reload 没反应"）。

            apply(c, flat, applied);

            AnglersTackle.LOGGER.info("[垂纶] 配置热重载完成：应用 {} 项（{}）", applied[0], file.getFileName());
            return applied[0];
        } catch (Throwable t) {
            AnglersTackle.LOGGER.warn("[垂纶] 配置热重载失败：{}", t.toString());
            return -1;
        }
    }

    /** 把解析出来的扁平值逐个塞进 spec。 */
    private static void apply(Config c, java.util.Map<String, Object> flat, int[] counter) {
        // 逐项显式列出，而不是反射遍历 —— 反射在混淆/模块边界下脆弱，
        // 而且这里只有 35 项，显式写反而更清楚、更容易在编译期发现漏项。
        put(flat, "cast.chargeTimeSeconds", c.chargeTimeSeconds, counter);
        put(flat, "hookWindow.base", c.hookWindowBase, counter);
        put(flat, "hookWindow.min", c.hookWindowMin, counter);
        put(flat, "tension.reelGain", c.tensionReelGain, counter);
        put(flat, "tension.releaseLoss", c.tensionReleaseLoss, counter);
        put(flat, "tension.idleLoss", c.tensionIdleLoss, counter);
        put(flat, "tension.whiteMax", c.tensionWhiteMax, counter);
        put(flat, "tension.yellowMax", c.tensionYellowMax, counter);
        put(flat, "tension.criticalFlash", c.tensionCriticalFlash, counter);
        put(flat, "tension.baseRecover", c.tensionBaseRecover, counter);
        put(flat, "bite.minWaitSeconds", c.biteMinWait, counter);
        put(flat, "bite.maxWaitSeconds", c.biteMaxWait, counter);
        put(flat, "bite.hardCapSeconds", c.biteHardCap, counter);
        // 台钓减时（用户 2026-10-02 定案）：这 8 项是调参期最想动的一组
        // （咬钩快慢直接决定手感）。漏掉映射的表现是"调参器里改了、游戏里没反应"，
        // 而调参器自己不会报错 —— check-tuning-schema.js 就是专门抓这个的。
        put(flat, "bite.floorSeconds", c.biteFloorSeconds, counter);
        put(flat, "bite.floatRodCutPercent", c.biteFloatRodCutPercent, counter);
        put(flat, "bite.baitCutWhitePercent", c.biteBaitCutWhitePercent, counter);
        put(flat, "bite.baitCutGreenPercent", c.biteBaitCutGreenPercent, counter);
        put(flat, "bite.baitCutBluePercent", c.biteBaitCutBluePercent, counter);
        put(flat, "bite.baitCutPurplePercent", c.biteBaitCutPurplePercent, counter);
        put(flat, "bite.baitCutGoldPercent", c.biteBaitCutGoldPercent, counter);
        put(flat, "bite.baitCutRedPercent", c.biteBaitCutRedPercent, counter);
        // 时间 / 天气 / 月相（用户 2026-10-02 定案，见 docs/24）。
        // 这一组是"调环境手感"时最想动的：三个 delta 直接决定"某个时段/天气/月相
        // 到底能带来多明显的差异"。与咬钩那组同理，漏映射的表现是
        // "调参器里改了、游戏里没反应"，由 check-tuning-schema.js 抓。
        put(flat, "environment.enabled", c.environmentEnabled, counter);
        put(flat, "environment.timeDeltaPct", c.envTimeDeltaPct, counter);
        put(flat, "environment.weatherDeltaPct", c.envWeatherDeltaPct, counter);
        put(flat, "environment.moonGoldRedDeltaPct", c.envMoonGoldRedDeltaPct, counter);
        put(flat, "environment.moonPurpleDeltaPct", c.envMoonPurpleDeltaPct, counter);
        put(flat, "environment.moonShareMode", c.envMoonShareMode, counter);
        put(flat, "environment.shareCapPct", c.envShareCapPct, counter);
        put(flat, "environment.showCondition", c.envShowCondition, counter);
        put(flat, "environment.logToChat", c.envLogToChat, counter);
        put(flat, "environment.nightCutPercent", c.biteNightCutPercent, counter);
        put(flat, "parts.lineOnTension", c.weightLineOnTension, counter);
        put(flat, "parts.rodOnTension", c.weightRodOnTension, counter);
        put(flat, "parts.lineOnRecover", c.weightLineOnRecover, counter);
        put(flat, "parts.rodOnRecover", c.weightRodOnRecover, counter);
        put(flat, "parts.reelOnDrain", c.weightReelOnDrain, counter);
        put(flat, "parts.rodOnDrain", c.weightRodOnDrain, counter);
        put(flat, "parts.capTensionCeiling", c.capTensionCeiling, counter);
        put(flat, "parts.capTensionRecover", c.capTensionRecover, counter);
        put(flat, "parts.capStaminaDrain", c.capStaminaDrain, counter);
        put(flat, "parts.capPull", c.capPull, counter);
        put(flat, "parts.capDamping", c.capDamping, counter);
        put(flat, "parts.pullIdleBase", c.pullIdleBase, counter);
        put(flat, "parts.pullStruggleBase", c.pullStruggleBase, counter);
        put(flat, "parts.staminaDrainBase", c.staminaDrainBase, counter);
        put(flat, "chum.particleIntervalTicks", c.chumParticleIntervalTicks, counter);
        put(flat, "chum.particlesPerBurst", c.chumParticlesPerBurst, counter);
        put(flat, "chum.radius", c.chumRadius, counter);
        put(flat, "chum.serverCap", c.chumServerCap, counter);
        // 称号门槛（用户口径："这些数字全部进配置"）—— 少一行就是"调参器里改了、游戏里没反应"，
        // 由 check-tuning-schema.js 双向核对（schema 有而这里没有 ⇒ 红；这里多出 ⇒ 也红）。
        put(flat, "titles.chatMode", c.titleChatMode, counter);
        put(flat, "titles.codexCount5", c.titleCodexCount5, counter);
        put(flat, "titles.codexCount10", c.titleCodexCount10, counter);
        put(flat, "titles.codexCount24", c.titleCodexCount24, counter);
        put(flat, "titles.codexCount40", c.titleCodexCount40, counter);
        put(flat, "titles.goldCount10", c.titleGoldCount10, counter);
        put(flat, "titles.giantKg5", c.titleGiantKg5, counter);
        put(flat, "titles.giantKg20", c.titleGiantKg20, counter);
        put(flat, "titles.giantKg50", c.titleGiantKg50, counter);
        put(flat, "titles.giantKg100", c.titleGiantKg100, counter);
        put(flat, "titles.garCount", c.titleGarCount, counter);
        put(flat, "titles.redCount", c.titleRedCount, counter);
        put(flat, "titles.nightCount", c.titleNightCount, counter);
        put(flat, "titles.fullMoonCount", c.titleFullMoonCount, counter);
        put(flat, "titles.iceCount", c.titleIceCount, counter);
        put(flat, "titles.chumCount", c.titleChumCount, counter);
        put(flat, "titles.lineBreakCount", c.titleLineBreakCount, counter);
        put(flat, "titles.dishMadeCount", c.titleDishMadeCount, counter);
        put(flat, "titles.dishEatenCount", c.titleDishEatenCount, counter);
        // 邮件（用户 2026-10-11 点名要的邮件系统）—— 少一行就是"调参器里改了、游戏里没反应"，
        // 由 check-tuning-schema.js 双向核对。
        put(flat, "mail.keepDays", c.mailKeepDays, counter);
        put(flat, "mail.maxMails", c.mailMaxMails, counter);
        put(flat, "mail.bodyMaxChars", c.mailBodyMaxChars, counter);
        put(flat, "mail.unreadLoginNotice", c.mailUnreadLoginNotice, counter);
        // 联赛（用户 2026-10-11 定案；docs/37 §6）。⚠ 少一行就是"调参器里改了、游戏里没反应"，
        // 由 check-tuning-schema.js 双向核对（它现在也认字符串/布尔了 —— 见 parseFlatToml 的注释）。
        put(flat, "league.useRealDays", c.leagueUseRealDays, counter);
        put(flat, "league.minLevel", c.leagueMinLevel, counter);
        put(flat, "league.allowBelowLevel", c.leagueAllowBelowLevel, counter);
        put(flat, "league.smallDays", c.leagueSmallDays, counter);
        put(flat, "league.mediumDays", c.leagueMediumDays, counter);
        put(flat, "league.largeDays", c.leagueLargeDays, counter);
        put(flat, "league.periodGameDays", c.leaguePeriodGameDays, counter);
        put(flat, "league.playerWindowMinutes", c.leaguePlayerWindowMinutes, counter);
        put(flat, "league.periodBoundaryHour", c.leaguePeriodBoundaryHour, counter);
        put(flat, "league.ruleMode", c.leagueRuleMode, counter);
        put(flat, "league.minCatchesToRank", c.leagueMinCatchesToRank, counter);
        put(flat, "league.rewardCoinsParticipant", c.leagueRewardCoinsParticipant, counter);
        put(flat, "league.rewardCoinsTop10", c.leagueRewardCoinsTop10, counter);
        put(flat, "league.rewardCoins1st", c.leagueRewardCoins1st, counter);
        put(flat, "league.rewardCoins2nd", c.leagueRewardCoins2nd, counter);
        put(flat, "league.rewardCoins3rd", c.leagueRewardCoins3rd, counter);
        put(flat, "league.rewardXp1st", c.leagueRewardXp1st, counter);
        put(flat, "league.rewardXp2nd", c.leagueRewardXp2nd, counter);
        put(flat, "league.rewardXp3rd", c.leagueRewardXp3rd, counter);
        put(flat, "league.scaleMediumCoinsXp", c.leagueScaleMediumCoinsXp, counter);
        put(flat, "league.scaleLargeCoinsXp", c.leagueScaleLargeCoinsXp, counter);
        put(flat, "league.scaleLargeVanillaItems", c.leagueScaleLargeVanillaItems, counter);
        put(flat, "league.rewardMailKeepDays", c.leagueRewardMailKeepDays, counter);
        put(flat, "league.noticeMailKeepDays", c.leagueNoticeMailKeepDays, counter);
        put(flat, "league.inviteOnLogin", c.leagueInviteOnLogin, counter);
        // 比赛（用户 2026-10-11 定案；docs/35 §7）。⚠ 少一行就是"调参器里改了、游戏里没反应"。
        put(flat, "contest.defaultMinutes", c.contestDefaultMinutes, counter);
        put(flat, "contest.defaultRuleMode", c.contestDefaultRuleMode, counter);
        put(flat, "contest.minPlayers", c.contestMinPlayers, counter);
        put(flat, "contest.rewardFirst", c.contestRewardFirst, counter);
        put(flat, "contest.rewardSecond", c.contestRewardSecond, counter);
        put(flat, "contest.rewardThird", c.contestRewardThird, counter);
        put(flat, "contest.autoEveryMinutes", c.contestAutoEveryMinutes, counter);
        put(flat, "contest.announceTop", c.contestAnnounceTop, counter);
        put(flat, "durability.rodPerBite", c.rodDurabilityPerBite, counter);
        put(flat, "durability.linePerBite", c.lineDurabilityPerBite, counter);
        put(flat, "durability.lurePerBite", c.lureDurabilityPerBite, counter);
        put(flat, "durability.linePerBreak", c.lineDurabilityPerBreak, counter);
        put(flat, "render.ghostLineBeyondBlocks", c.ghostLineBeyondBlocks, counter);
        // 对抗期相机（用户 2026-10-01）：改完 /atl config reload 立刻生效，不用重启。
        // ⚠ 新增配置项必须在这里登记，否则 check-tuning-schema.js 会红
        //   （"调参器有 X，但 Tuning.reload 没映射它 —— 改了不会有反应"）。
        put(flat, "render.hookCameraFollow", c.hookCameraFollow, counter);
        put(flat, "render.hookCameraFollowStrength", c.hookCameraFollowStrength, counter);
        put(flat, "render.reelZoomPercent", c.reelZoomPercent, counter);
        // 委托（docs/40，用户 2026-10-14）：6 个键全部登记 —— 少一条就是"调参器里改了没反应"
        //   （守着这条的是 check-tuning-schema.js；闸第 4 阶段当场抓到过）。
        // ⚠ TOML 键名是 "quest.enable"（**不是 enabled**）：[environment] 已经占了 enabled，
        //   而 gen-tuning-schema.js 按扁平键名判重 ⇒ 撞车会让整条闸死在第 3 阶段。
        put(flat, "quest.enable", c.questEnabled, counter);
        putIntValue(flat, "quest.dailyCount", c.questDailyCount, counter);
        putIntValue(flat, "quest.weeklyCount", c.questWeeklyCount, counter);
        putIntValue(flat, "quest.activityPerDaily", c.questActivityPerDaily, counter);
        putIntValue(flat, "quest.activityPerWeekly", c.questActivityPerWeekly, counter);
        putIntValue(flat, "quest.activityCapWeekly", c.questActivityCapWeekly, counter);
    // @tuning-slot: partsReload
        // ==== BEGIN 配件数值热重载（生成，勿手改）====
        putIntValue(flat, "partsStats.hempDurability", c.hempDurability, counter);
        putDoubleValue(flat, "partsStats.hempTensionCeiling", c.hempTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.hempTensionGen", c.hempTensionGen, counter);
        putDoubleValue(flat, "partsStats.hempTensionRecover", c.hempTensionRecover, counter);
        putDoubleValue(flat, "partsStats.hempLineLength", c.hempLineLength, counter);
        putIntValue(flat, "partsStats.hempTier", c.hempTier, counter);
        putIntValue(flat, "partsStats.cottonDurability", c.cottonDurability, counter);
        putDoubleValue(flat, "partsStats.cottonTensionCeiling", c.cottonTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.cottonTensionGen", c.cottonTensionGen, counter);
        putDoubleValue(flat, "partsStats.cottonTensionRecover", c.cottonTensionRecover, counter);
        putDoubleValue(flat, "partsStats.cottonLineLength", c.cottonLineLength, counter);
        putIntValue(flat, "partsStats.cottonTier", c.cottonTier, counter);
        putIntValue(flat, "partsStats.nylonDurability", c.nylonDurability, counter);
        putDoubleValue(flat, "partsStats.nylonTensionCeiling", c.nylonTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.nylonTensionGen", c.nylonTensionGen, counter);
        putDoubleValue(flat, "partsStats.nylonTensionRecover", c.nylonTensionRecover, counter);
        putDoubleValue(flat, "partsStats.nylonLineLength", c.nylonLineLength, counter);
        putIntValue(flat, "partsStats.nylonTier", c.nylonTier, counter);
        putIntValue(flat, "partsStats.fluoroDurability", c.fluoroDurability, counter);
        putDoubleValue(flat, "partsStats.fluoroTensionCeiling", c.fluoroTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.fluoroTensionGen", c.fluoroTensionGen, counter);
        putDoubleValue(flat, "partsStats.fluoroTensionRecover", c.fluoroTensionRecover, counter);
        putDoubleValue(flat, "partsStats.fluoroLineLength", c.fluoroLineLength, counter);
        putIntValue(flat, "partsStats.fluoroTier", c.fluoroTier, counter);
        putIntValue(flat, "partsStats.peDurability", c.peDurability, counter);
        putDoubleValue(flat, "partsStats.peTensionCeiling", c.peTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.peTensionGen", c.peTensionGen, counter);
        putDoubleValue(flat, "partsStats.peTensionRecover", c.peTensionRecover, counter);
        putDoubleValue(flat, "partsStats.peLineLength", c.peLineLength, counter);
        putIntValue(flat, "partsStats.peTier", c.peTier, counter);
        putIntValue(flat, "partsStats.titaniumDurability", c.titaniumDurability, counter);
        putDoubleValue(flat, "partsStats.titaniumTensionCeiling", c.titaniumTensionCeiling, counter);
        putDoubleValue(flat, "partsStats.titaniumTensionGen", c.titaniumTensionGen, counter);
        putDoubleValue(flat, "partsStats.titaniumTensionRecover", c.titaniumTensionRecover, counter);
        putDoubleValue(flat, "partsStats.titaniumLineLength", c.titaniumLineLength, counter);
        putIntValue(flat, "partsStats.titaniumTier", c.titaniumTier, counter);
        putIntValue(flat, "partsStats.handDurability", c.handDurability, counter);
        putDoubleValue(flat, "partsStats.handPullIdle", c.handPullIdle, counter);
        putDoubleValue(flat, "partsStats.handPullStruggle", c.handPullStruggle, counter);
        putDoubleValue(flat, "partsStats.handStaminaDrain", c.handStaminaDrain, counter);
        putDoubleValue(flat, "partsStats.handDamping", c.handDamping, counter);
        putIntValue(flat, "partsStats.handTier", c.handTier, counter);
        putIntValue(flat, "partsStats.spinningDurability", c.spinningDurability, counter);
        putDoubleValue(flat, "partsStats.spinningPullIdle", c.spinningPullIdle, counter);
        putDoubleValue(flat, "partsStats.spinningPullStruggle", c.spinningPullStruggle, counter);
        putDoubleValue(flat, "partsStats.spinningStaminaDrain", c.spinningStaminaDrain, counter);
        putDoubleValue(flat, "partsStats.spinningDamping", c.spinningDamping, counter);
        putIntValue(flat, "partsStats.spinningTier", c.spinningTier, counter);
        putIntValue(flat, "partsStats.drumDurability", c.drumDurability, counter);
        putDoubleValue(flat, "partsStats.drumPullIdle", c.drumPullIdle, counter);
        putDoubleValue(flat, "partsStats.drumPullStruggle", c.drumPullStruggle, counter);
        putDoubleValue(flat, "partsStats.drumStaminaDrain", c.drumStaminaDrain, counter);
        putDoubleValue(flat, "partsStats.drumDamping", c.drumDamping, counter);
        putIntValue(flat, "partsStats.drumTier", c.drumTier, counter);
        putIntValue(flat, "partsStats.baitcastDurability", c.baitcastDurability, counter);
        putDoubleValue(flat, "partsStats.baitcastPullIdle", c.baitcastPullIdle, counter);
        putDoubleValue(flat, "partsStats.baitcastPullStruggle", c.baitcastPullStruggle, counter);
        putDoubleValue(flat, "partsStats.baitcastStaminaDrain", c.baitcastStaminaDrain, counter);
        putDoubleValue(flat, "partsStats.baitcastDamping", c.baitcastDamping, counter);
        putIntValue(flat, "partsStats.baitcastTier", c.baitcastTier, counter);
        putIntValue(flat, "partsStats.heavyDurability", c.heavyDurability, counter);
        putDoubleValue(flat, "partsStats.heavyPullIdle", c.heavyPullIdle, counter);
        putDoubleValue(flat, "partsStats.heavyPullStruggle", c.heavyPullStruggle, counter);
        putDoubleValue(flat, "partsStats.heavyStaminaDrain", c.heavyStaminaDrain, counter);
        putDoubleValue(flat, "partsStats.heavyDamping", c.heavyDamping, counter);
        putIntValue(flat, "partsStats.heavyTier", c.heavyTier, counter);
        putIntValue(flat, "partsStats.lureSpoonDurability", c.lureSpoonDurability, counter);
        putIntValue(flat, "partsStats.lureSpoonTier", c.lureSpoonTier, counter);
        putIntValue(flat, "partsStats.lureGlowspoonDurability", c.lureGlowspoonDurability, counter);
        putIntValue(flat, "partsStats.lureGlowspoonTier", c.lureGlowspoonTier, counter);
        putIntValue(flat, "partsStats.lureMinnowDurability", c.lureMinnowDurability, counter);
        putIntValue(flat, "partsStats.lureMinnowTier", c.lureMinnowTier, counter);
        putIntValue(flat, "partsStats.lureDeepDurability", c.lureDeepDurability, counter);
        putIntValue(flat, "partsStats.lureDeepTier", c.lureDeepTier, counter);
        putIntValue(flat, "partsStats.lureRattleDurability", c.lureRattleDurability, counter);
        putIntValue(flat, "partsStats.lureRattleTier", c.lureRattleTier, counter);
        putIntValue(flat, "partsStats.lureGildedDurability", c.lureGildedDurability, counter);
        putIntValue(flat, "partsStats.lureGildedTier", c.lureGildedTier, counter);
        putIntValue(flat, "partsStats.lureSoftDurability", c.lureSoftDurability, counter);
        putIntValue(flat, "partsStats.lureSoftTier", c.lureSoftTier, counter);
        putIntValue(flat, "partsStats.lureDepthfinderDurability", c.lureDepthfinderDurability, counter);
        putIntValue(flat, "partsStats.lureDepthfinderTier", c.lureDepthfinderTier, counter);
        putIntValue(flat, "partsStats.lurePaddletailDurability", c.lurePaddletailDurability, counter);
        putIntValue(flat, "partsStats.lurePaddletailTier", c.lurePaddletailTier, counter);
        putIntValue(flat, "partsStats.lureVibrationDurability", c.lureVibrationDurability, counter);
        putIntValue(flat, "partsStats.lureVibrationTier", c.lureVibrationTier, counter);
        putIntValue(flat, "partsStats.lureTestRedDurability", c.lureTestRedDurability, counter);
        putIntValue(flat, "partsStats.lureTestRedTier", c.lureTestRedTier, counter);
        // ==== END 配件数值热重载 ====
    // @tuning-slot: fishReload
        // ==== BEGIN 鱼种数值热重载（生成，勿手改）====
        putDoubleValue(flat, "fishStats.crucianCarpMinKg", c.crucianCarpMinKg, counter);
        putDoubleValue(flat, "fishStats.crucianCarpMaxKg", c.crucianCarpMaxKg, counter);
        putDoubleValue(flat, "fishStats.crucianCarpPullSpeed", c.crucianCarpPullSpeed, counter);
        putDoubleValue(flat, "fishStats.crucianCarpFatiguedPullSpeed", c.crucianCarpFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.crucianCarpStamina", c.crucianCarpStamina, counter);
        putDoubleValue(flat, "fishStats.crucianCarpTensionGain", c.crucianCarpTensionGain, counter);
        putDoubleValue(flat, "fishStats.crucianCarpSprintDurationSec", c.crucianCarpSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.crucianCarpSprintIntervalSec", c.crucianCarpSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.crucianCarpTurnIntervalSec", c.crucianCarpTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.crucianCarpFakeOutChance", c.crucianCarpFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.crucianCarpFatigueThreshold", c.crucianCarpFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.crucianCarpFatigueDurationSec", c.crucianCarpFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.crucianCarpStaminaRegen", c.crucianCarpStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.tilapiaMinKg", c.tilapiaMinKg, counter);
        putDoubleValue(flat, "fishStats.tilapiaMaxKg", c.tilapiaMaxKg, counter);
        putDoubleValue(flat, "fishStats.tilapiaPullSpeed", c.tilapiaPullSpeed, counter);
        putDoubleValue(flat, "fishStats.tilapiaFatiguedPullSpeed", c.tilapiaFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.tilapiaStamina", c.tilapiaStamina, counter);
        putDoubleValue(flat, "fishStats.tilapiaTensionGain", c.tilapiaTensionGain, counter);
        putDoubleValue(flat, "fishStats.tilapiaSprintDurationSec", c.tilapiaSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.tilapiaSprintIntervalSec", c.tilapiaSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.tilapiaTurnIntervalSec", c.tilapiaTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.tilapiaFakeOutChance", c.tilapiaFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.tilapiaFatigueThreshold", c.tilapiaFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.tilapiaFatigueDurationSec", c.tilapiaFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.tilapiaStaminaRegen", c.tilapiaStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.mulletMinKg", c.mulletMinKg, counter);
        putDoubleValue(flat, "fishStats.mulletMaxKg", c.mulletMaxKg, counter);
        putDoubleValue(flat, "fishStats.mulletPullSpeed", c.mulletPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mulletFatiguedPullSpeed", c.mulletFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mulletStamina", c.mulletStamina, counter);
        putDoubleValue(flat, "fishStats.mulletTensionGain", c.mulletTensionGain, counter);
        putDoubleValue(flat, "fishStats.mulletSprintDurationSec", c.mulletSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.mulletSprintIntervalSec", c.mulletSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mulletTurnIntervalSec", c.mulletTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mulletFakeOutChance", c.mulletFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.mulletFatigueThreshold", c.mulletFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.mulletFatigueDurationSec", c.mulletFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.mulletStaminaRegen", c.mulletStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.grassCarpMinKg", c.grassCarpMinKg, counter);
        putDoubleValue(flat, "fishStats.grassCarpMaxKg", c.grassCarpMaxKg, counter);
        putDoubleValue(flat, "fishStats.grassCarpPullSpeed", c.grassCarpPullSpeed, counter);
        putDoubleValue(flat, "fishStats.grassCarpFatiguedPullSpeed", c.grassCarpFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.grassCarpStamina", c.grassCarpStamina, counter);
        putDoubleValue(flat, "fishStats.grassCarpTensionGain", c.grassCarpTensionGain, counter);
        putDoubleValue(flat, "fishStats.grassCarpSprintDurationSec", c.grassCarpSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.grassCarpSprintIntervalSec", c.grassCarpSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.grassCarpTurnIntervalSec", c.grassCarpTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.grassCarpFakeOutChance", c.grassCarpFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.grassCarpFatigueThreshold", c.grassCarpFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.grassCarpFatigueDurationSec", c.grassCarpFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.grassCarpStaminaRegen", c.grassCarpStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.blackCarpMinKg", c.blackCarpMinKg, counter);
        putDoubleValue(flat, "fishStats.blackCarpMaxKg", c.blackCarpMaxKg, counter);
        putDoubleValue(flat, "fishStats.blackCarpPullSpeed", c.blackCarpPullSpeed, counter);
        putDoubleValue(flat, "fishStats.blackCarpFatiguedPullSpeed", c.blackCarpFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.blackCarpStamina", c.blackCarpStamina, counter);
        putDoubleValue(flat, "fishStats.blackCarpTensionGain", c.blackCarpTensionGain, counter);
        putDoubleValue(flat, "fishStats.blackCarpSprintDurationSec", c.blackCarpSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.blackCarpSprintIntervalSec", c.blackCarpSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.blackCarpTurnIntervalSec", c.blackCarpTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.blackCarpFakeOutChance", c.blackCarpFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.blackCarpFatigueThreshold", c.blackCarpFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.blackCarpFatigueDurationSec", c.blackCarpFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.blackCarpStaminaRegen", c.blackCarpStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.bandedLoachMinKg", c.bandedLoachMinKg, counter);
        putDoubleValue(flat, "fishStats.bandedLoachMaxKg", c.bandedLoachMaxKg, counter);
        putDoubleValue(flat, "fishStats.bandedLoachPullSpeed", c.bandedLoachPullSpeed, counter);
        putDoubleValue(flat, "fishStats.bandedLoachFatiguedPullSpeed", c.bandedLoachFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.bandedLoachStamina", c.bandedLoachStamina, counter);
        putDoubleValue(flat, "fishStats.bandedLoachTensionGain", c.bandedLoachTensionGain, counter);
        putDoubleValue(flat, "fishStats.bandedLoachSprintDurationSec", c.bandedLoachSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.bandedLoachSprintIntervalSec", c.bandedLoachSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.bandedLoachTurnIntervalSec", c.bandedLoachTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.bandedLoachFakeOutChance", c.bandedLoachFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.bandedLoachFatigueThreshold", c.bandedLoachFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.bandedLoachFatigueDurationSec", c.bandedLoachFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.bandedLoachStaminaRegen", c.bandedLoachStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.hemiculterMinKg", c.hemiculterMinKg, counter);
        putDoubleValue(flat, "fishStats.hemiculterMaxKg", c.hemiculterMaxKg, counter);
        putDoubleValue(flat, "fishStats.hemiculterPullSpeed", c.hemiculterPullSpeed, counter);
        putDoubleValue(flat, "fishStats.hemiculterFatiguedPullSpeed", c.hemiculterFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.hemiculterStamina", c.hemiculterStamina, counter);
        putDoubleValue(flat, "fishStats.hemiculterTensionGain", c.hemiculterTensionGain, counter);
        putDoubleValue(flat, "fishStats.hemiculterSprintDurationSec", c.hemiculterSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.hemiculterSprintIntervalSec", c.hemiculterSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.hemiculterTurnIntervalSec", c.hemiculterTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.hemiculterFakeOutChance", c.hemiculterFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.hemiculterFatigueThreshold", c.hemiculterFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.hemiculterFatigueDurationSec", c.hemiculterFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.hemiculterStaminaRegen", c.hemiculterStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterMinKg", c.topmouthCulterMinKg, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterMaxKg", c.topmouthCulterMaxKg, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterPullSpeed", c.topmouthCulterPullSpeed, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterFatiguedPullSpeed", c.topmouthCulterFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterStamina", c.topmouthCulterStamina, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterTensionGain", c.topmouthCulterTensionGain, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterSprintDurationSec", c.topmouthCulterSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterSprintIntervalSec", c.topmouthCulterSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterTurnIntervalSec", c.topmouthCulterTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterFakeOutChance", c.topmouthCulterFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterFatigueThreshold", c.topmouthCulterFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterFatigueDurationSec", c.topmouthCulterFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.topmouthCulterStaminaRegen", c.topmouthCulterStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.zanderMinKg", c.zanderMinKg, counter);
        putDoubleValue(flat, "fishStats.zanderMaxKg", c.zanderMaxKg, counter);
        putDoubleValue(flat, "fishStats.zanderPullSpeed", c.zanderPullSpeed, counter);
        putDoubleValue(flat, "fishStats.zanderFatiguedPullSpeed", c.zanderFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.zanderStamina", c.zanderStamina, counter);
        putDoubleValue(flat, "fishStats.zanderTensionGain", c.zanderTensionGain, counter);
        putDoubleValue(flat, "fishStats.zanderSprintDurationSec", c.zanderSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.zanderSprintIntervalSec", c.zanderSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.zanderTurnIntervalSec", c.zanderTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.zanderFakeOutChance", c.zanderFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.zanderFatigueThreshold", c.zanderFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.zanderFatigueDurationSec", c.zanderFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.zanderStaminaRegen", c.zanderStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadMinKg", c.sapphireSnakeheadMinKg, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadMaxKg", c.sapphireSnakeheadMaxKg, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadPullSpeed", c.sapphireSnakeheadPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadFatiguedPullSpeed", c.sapphireSnakeheadFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadStamina", c.sapphireSnakeheadStamina, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadTensionGain", c.sapphireSnakeheadTensionGain, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadSprintDurationSec", c.sapphireSnakeheadSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadSprintIntervalSec", c.sapphireSnakeheadSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadTurnIntervalSec", c.sapphireSnakeheadTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadFakeOutChance", c.sapphireSnakeheadFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadFatigueThreshold", c.sapphireSnakeheadFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadFatigueDurationSec", c.sapphireSnakeheadFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.sapphireSnakeheadStaminaRegen", c.sapphireSnakeheadStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaMinKg", c.platinumArowanaMinKg, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaMaxKg", c.platinumArowanaMaxKg, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaPullSpeed", c.platinumArowanaPullSpeed, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaFatiguedPullSpeed", c.platinumArowanaFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaStamina", c.platinumArowanaStamina, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaTensionGain", c.platinumArowanaTensionGain, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaSprintDurationSec", c.platinumArowanaSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaSprintIntervalSec", c.platinumArowanaSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaTurnIntervalSec", c.platinumArowanaTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaFakeOutChance", c.platinumArowanaFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaFatigueThreshold", c.platinumArowanaFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaFatigueDurationSec", c.platinumArowanaFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.platinumArowanaStaminaRegen", c.platinumArowanaStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponMinKg", c.oxeyeTarponMinKg, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponMaxKg", c.oxeyeTarponMaxKg, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponPullSpeed", c.oxeyeTarponPullSpeed, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponFatiguedPullSpeed", c.oxeyeTarponFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponStamina", c.oxeyeTarponStamina, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponTensionGain", c.oxeyeTarponTensionGain, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponSprintDurationSec", c.oxeyeTarponSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponSprintIntervalSec", c.oxeyeTarponSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponTurnIntervalSec", c.oxeyeTarponTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponFakeOutChance", c.oxeyeTarponFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponFatigueThreshold", c.oxeyeTarponFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponFatigueDurationSec", c.oxeyeTarponFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.oxeyeTarponStaminaRegen", c.oxeyeTarponStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonMinKg", c.atlanticSalmonMinKg, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonMaxKg", c.atlanticSalmonMaxKg, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonPullSpeed", c.atlanticSalmonPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonFatiguedPullSpeed", c.atlanticSalmonFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonStamina", c.atlanticSalmonStamina, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonTensionGain", c.atlanticSalmonTensionGain, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonSprintDurationSec", c.atlanticSalmonSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonSprintIntervalSec", c.atlanticSalmonSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonTurnIntervalSec", c.atlanticSalmonTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonFakeOutChance", c.atlanticSalmonFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonFatigueThreshold", c.atlanticSalmonFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonFatigueDurationSec", c.atlanticSalmonFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticSalmonStaminaRegen", c.atlanticSalmonStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaMinKg", c.bluefinTunaMinKg, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaMaxKg", c.bluefinTunaMaxKg, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaPullSpeed", c.bluefinTunaPullSpeed, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaFatiguedPullSpeed", c.bluefinTunaFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaStamina", c.bluefinTunaStamina, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaTensionGain", c.bluefinTunaTensionGain, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaSprintDurationSec", c.bluefinTunaSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaSprintIntervalSec", c.bluefinTunaSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaTurnIntervalSec", c.bluefinTunaTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaFakeOutChance", c.bluefinTunaFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaFatigueThreshold", c.bluefinTunaFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaFatigueDurationSec", c.bluefinTunaFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.bluefinTunaStaminaRegen", c.bluefinTunaStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkMinKg", c.juvenileSharkMinKg, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkMaxKg", c.juvenileSharkMaxKg, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkPullSpeed", c.juvenileSharkPullSpeed, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkFatiguedPullSpeed", c.juvenileSharkFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkStamina", c.juvenileSharkStamina, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkTensionGain", c.juvenileSharkTensionGain, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkSprintDurationSec", c.juvenileSharkSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkSprintIntervalSec", c.juvenileSharkSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkTurnIntervalSec", c.juvenileSharkTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkFakeOutChance", c.juvenileSharkFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkFatigueThreshold", c.juvenileSharkFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkFatigueDurationSec", c.juvenileSharkFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.juvenileSharkStaminaRegen", c.juvenileSharkStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.tigerfishMinKg", c.tigerfishMinKg, counter);
        putDoubleValue(flat, "fishStats.tigerfishMaxKg", c.tigerfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.tigerfishPullSpeed", c.tigerfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.tigerfishFatiguedPullSpeed", c.tigerfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.tigerfishStamina", c.tigerfishStamina, counter);
        putDoubleValue(flat, "fishStats.tigerfishTensionGain", c.tigerfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.tigerfishSprintDurationSec", c.tigerfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.tigerfishSprintIntervalSec", c.tigerfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.tigerfishTurnIntervalSec", c.tigerfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.tigerfishFakeOutChance", c.tigerfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.tigerfishFatigueThreshold", c.tigerfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.tigerfishFatigueDurationSec", c.tigerfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.tigerfishStaminaRegen", c.tigerfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.pikeMinKg", c.pikeMinKg, counter);
        putDoubleValue(flat, "fishStats.pikeMaxKg", c.pikeMaxKg, counter);
        putDoubleValue(flat, "fishStats.pikePullSpeed", c.pikePullSpeed, counter);
        putDoubleValue(flat, "fishStats.pikeFatiguedPullSpeed", c.pikeFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.pikeStamina", c.pikeStamina, counter);
        putDoubleValue(flat, "fishStats.pikeTensionGain", c.pikeTensionGain, counter);
        putDoubleValue(flat, "fishStats.pikeSprintDurationSec", c.pikeSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.pikeSprintIntervalSec", c.pikeSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.pikeTurnIntervalSec", c.pikeTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.pikeFakeOutChance", c.pikeFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.pikeFatigueThreshold", c.pikeFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.pikeFatigueDurationSec", c.pikeFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.pikeStaminaRegen", c.pikeStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.brownTroutMinKg", c.brownTroutMinKg, counter);
        putDoubleValue(flat, "fishStats.brownTroutMaxKg", c.brownTroutMaxKg, counter);
        putDoubleValue(flat, "fishStats.brownTroutPullSpeed", c.brownTroutPullSpeed, counter);
        putDoubleValue(flat, "fishStats.brownTroutFatiguedPullSpeed", c.brownTroutFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.brownTroutStamina", c.brownTroutStamina, counter);
        putDoubleValue(flat, "fishStats.brownTroutTensionGain", c.brownTroutTensionGain, counter);
        putDoubleValue(flat, "fishStats.brownTroutSprintDurationSec", c.brownTroutSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.brownTroutSprintIntervalSec", c.brownTroutSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.brownTroutTurnIntervalSec", c.brownTroutTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.brownTroutFakeOutChance", c.brownTroutFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.brownTroutFatigueThreshold", c.brownTroutFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.brownTroutFatigueDurationSec", c.brownTroutFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.brownTroutStaminaRegen", c.brownTroutStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpMinKg", c.mirrorCarpMinKg, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpMaxKg", c.mirrorCarpMaxKg, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpPullSpeed", c.mirrorCarpPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpFatiguedPullSpeed", c.mirrorCarpFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpStamina", c.mirrorCarpStamina, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpTensionGain", c.mirrorCarpTensionGain, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpSprintDurationSec", c.mirrorCarpSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpSprintIntervalSec", c.mirrorCarpSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpTurnIntervalSec", c.mirrorCarpTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpFakeOutChance", c.mirrorCarpFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpFatigueThreshold", c.mirrorCarpFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpFatigueDurationSec", c.mirrorCarpFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.mirrorCarpStaminaRegen", c.mirrorCarpStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.stripedBassMinKg", c.stripedBassMinKg, counter);
        putDoubleValue(flat, "fishStats.stripedBassMaxKg", c.stripedBassMaxKg, counter);
        putDoubleValue(flat, "fishStats.stripedBassPullSpeed", c.stripedBassPullSpeed, counter);
        putDoubleValue(flat, "fishStats.stripedBassFatiguedPullSpeed", c.stripedBassFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.stripedBassStamina", c.stripedBassStamina, counter);
        putDoubleValue(flat, "fishStats.stripedBassTensionGain", c.stripedBassTensionGain, counter);
        putDoubleValue(flat, "fishStats.stripedBassSprintDurationSec", c.stripedBassSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.stripedBassSprintIntervalSec", c.stripedBassSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.stripedBassTurnIntervalSec", c.stripedBassTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.stripedBassFakeOutChance", c.stripedBassFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.stripedBassFatigueThreshold", c.stripedBassFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.stripedBassFatigueDurationSec", c.stripedBassFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.stripedBassStaminaRegen", c.stripedBassStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.arapaimaMinKg", c.arapaimaMinKg, counter);
        putDoubleValue(flat, "fishStats.arapaimaMaxKg", c.arapaimaMaxKg, counter);
        putDoubleValue(flat, "fishStats.arapaimaPullSpeed", c.arapaimaPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arapaimaFatiguedPullSpeed", c.arapaimaFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arapaimaStamina", c.arapaimaStamina, counter);
        putDoubleValue(flat, "fishStats.arapaimaTensionGain", c.arapaimaTensionGain, counter);
        putDoubleValue(flat, "fishStats.arapaimaSprintDurationSec", c.arapaimaSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.arapaimaSprintIntervalSec", c.arapaimaSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arapaimaTurnIntervalSec", c.arapaimaTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arapaimaFakeOutChance", c.arapaimaFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.arapaimaFatigueThreshold", c.arapaimaFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.arapaimaFatigueDurationSec", c.arapaimaFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.arapaimaStaminaRegen", c.arapaimaStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishMinKg", c.africanButterflyfishMinKg, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishMaxKg", c.africanButterflyfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishPullSpeed", c.africanButterflyfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishFatiguedPullSpeed", c.africanButterflyfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishStamina", c.africanButterflyfishStamina, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishTensionGain", c.africanButterflyfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishSprintDurationSec", c.africanButterflyfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishSprintIntervalSec", c.africanButterflyfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishTurnIntervalSec", c.africanButterflyfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishFakeOutChance", c.africanButterflyfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishFatigueThreshold", c.africanButterflyfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishFatigueDurationSec", c.africanButterflyfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.africanButterflyfishStaminaRegen", c.africanButterflyfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidMinKg", c.jewelCichlidMinKg, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidMaxKg", c.jewelCichlidMaxKg, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidPullSpeed", c.jewelCichlidPullSpeed, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidFatiguedPullSpeed", c.jewelCichlidFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidStamina", c.jewelCichlidStamina, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidTensionGain", c.jewelCichlidTensionGain, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidSprintDurationSec", c.jewelCichlidSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidSprintIntervalSec", c.jewelCichlidSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidTurnIntervalSec", c.jewelCichlidTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidFakeOutChance", c.jewelCichlidFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidFatigueThreshold", c.jewelCichlidFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidFatigueDurationSec", c.jewelCichlidFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.jewelCichlidStaminaRegen", c.jewelCichlidStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.guppyMinKg", c.guppyMinKg, counter);
        putDoubleValue(flat, "fishStats.guppyMaxKg", c.guppyMaxKg, counter);
        putDoubleValue(flat, "fishStats.guppyPullSpeed", c.guppyPullSpeed, counter);
        putDoubleValue(flat, "fishStats.guppyFatiguedPullSpeed", c.guppyFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.guppyStamina", c.guppyStamina, counter);
        putDoubleValue(flat, "fishStats.guppyTensionGain", c.guppyTensionGain, counter);
        putDoubleValue(flat, "fishStats.guppySprintDurationSec", c.guppySprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.guppySprintIntervalSec", c.guppySprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.guppyTurnIntervalSec", c.guppyTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.guppyFakeOutChance", c.guppyFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.guppyFatigueThreshold", c.guppyFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.guppyFatigueDurationSec", c.guppyFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.guppyStaminaRegen", c.guppyStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.elephantnoseMinKg", c.elephantnoseMinKg, counter);
        putDoubleValue(flat, "fishStats.elephantnoseMaxKg", c.elephantnoseMaxKg, counter);
        putDoubleValue(flat, "fishStats.elephantnosePullSpeed", c.elephantnosePullSpeed, counter);
        putDoubleValue(flat, "fishStats.elephantnoseFatiguedPullSpeed", c.elephantnoseFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.elephantnoseStamina", c.elephantnoseStamina, counter);
        putDoubleValue(flat, "fishStats.elephantnoseTensionGain", c.elephantnoseTensionGain, counter);
        putDoubleValue(flat, "fishStats.elephantnoseSprintDurationSec", c.elephantnoseSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.elephantnoseSprintIntervalSec", c.elephantnoseSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.elephantnoseTurnIntervalSec", c.elephantnoseTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.elephantnoseFakeOutChance", c.elephantnoseFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.elephantnoseFatigueThreshold", c.elephantnoseFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.elephantnoseFatigueDurationSec", c.elephantnoseFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.elephantnoseStaminaRegen", c.elephantnoseStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.goldenLoachMinKg", c.goldenLoachMinKg, counter);
        putDoubleValue(flat, "fishStats.goldenLoachMaxKg", c.goldenLoachMaxKg, counter);
        putDoubleValue(flat, "fishStats.goldenLoachPullSpeed", c.goldenLoachPullSpeed, counter);
        putDoubleValue(flat, "fishStats.goldenLoachFatiguedPullSpeed", c.goldenLoachFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.goldenLoachStamina", c.goldenLoachStamina, counter);
        putDoubleValue(flat, "fishStats.goldenLoachTensionGain", c.goldenLoachTensionGain, counter);
        putDoubleValue(flat, "fishStats.goldenLoachSprintDurationSec", c.goldenLoachSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.goldenLoachSprintIntervalSec", c.goldenLoachSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.goldenLoachTurnIntervalSec", c.goldenLoachTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.goldenLoachFakeOutChance", c.goldenLoachFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.goldenLoachFatigueThreshold", c.goldenLoachFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.goldenLoachFatigueDurationSec", c.goldenLoachFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.goldenLoachStaminaRegen", c.goldenLoachStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishMinKg", c.caveBlindfishMinKg, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishMaxKg", c.caveBlindfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishPullSpeed", c.caveBlindfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishFatiguedPullSpeed", c.caveBlindfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishStamina", c.caveBlindfishStamina, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishTensionGain", c.caveBlindfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishSprintDurationSec", c.caveBlindfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishSprintIntervalSec", c.caveBlindfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishTurnIntervalSec", c.caveBlindfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishFakeOutChance", c.caveBlindfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishFatigueThreshold", c.caveBlindfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishFatigueDurationSec", c.caveBlindfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.caveBlindfishStaminaRegen", c.caveBlindfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishMinKg", c.northAfricanCatfishMinKg, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishMaxKg", c.northAfricanCatfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishPullSpeed", c.northAfricanCatfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishFatiguedPullSpeed", c.northAfricanCatfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishStamina", c.northAfricanCatfishStamina, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishTensionGain", c.northAfricanCatfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishSprintDurationSec", c.northAfricanCatfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishSprintIntervalSec", c.northAfricanCatfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishTurnIntervalSec", c.northAfricanCatfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishFakeOutChance", c.northAfricanCatfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishFatigueThreshold", c.northAfricanCatfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishFatigueDurationSec", c.northAfricanCatfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.northAfricanCatfishStaminaRegen", c.northAfricanCatfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutMinKg", c.rainbowTroutMinKg, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutMaxKg", c.rainbowTroutMaxKg, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutPullSpeed", c.rainbowTroutPullSpeed, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutFatiguedPullSpeed", c.rainbowTroutFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutStamina", c.rainbowTroutStamina, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutTensionGain", c.rainbowTroutTensionGain, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutSprintDurationSec", c.rainbowTroutSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutSprintIntervalSec", c.rainbowTroutSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutTurnIntervalSec", c.rainbowTroutTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutFakeOutChance", c.rainbowTroutFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutFatigueThreshold", c.rainbowTroutFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutFatigueDurationSec", c.rainbowTroutFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.rainbowTroutStaminaRegen", c.rainbowTroutStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.arcticCharMinKg", c.arcticCharMinKg, counter);
        putDoubleValue(flat, "fishStats.arcticCharMaxKg", c.arcticCharMaxKg, counter);
        putDoubleValue(flat, "fishStats.arcticCharPullSpeed", c.arcticCharPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arcticCharFatiguedPullSpeed", c.arcticCharFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arcticCharStamina", c.arcticCharStamina, counter);
        putDoubleValue(flat, "fishStats.arcticCharTensionGain", c.arcticCharTensionGain, counter);
        putDoubleValue(flat, "fishStats.arcticCharSprintDurationSec", c.arcticCharSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.arcticCharSprintIntervalSec", c.arcticCharSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arcticCharTurnIntervalSec", c.arcticCharTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arcticCharFakeOutChance", c.arcticCharFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.arcticCharFatigueThreshold", c.arcticCharFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.arcticCharFatigueDurationSec", c.arcticCharFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.arcticCharStaminaRegen", c.arcticCharStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.burbotMinKg", c.burbotMinKg, counter);
        putDoubleValue(flat, "fishStats.burbotMaxKg", c.burbotMaxKg, counter);
        putDoubleValue(flat, "fishStats.burbotPullSpeed", c.burbotPullSpeed, counter);
        putDoubleValue(flat, "fishStats.burbotFatiguedPullSpeed", c.burbotFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.burbotStamina", c.burbotStamina, counter);
        putDoubleValue(flat, "fishStats.burbotTensionGain", c.burbotTensionGain, counter);
        putDoubleValue(flat, "fishStats.burbotSprintDurationSec", c.burbotSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.burbotSprintIntervalSec", c.burbotSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.burbotTurnIntervalSec", c.burbotTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.burbotFakeOutChance", c.burbotFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.burbotFatigueThreshold", c.burbotFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.burbotFatigueDurationSec", c.burbotFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.burbotStaminaRegen", c.burbotStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.whitefishMinKg", c.whitefishMinKg, counter);
        putDoubleValue(flat, "fishStats.whitefishMaxKg", c.whitefishMaxKg, counter);
        putDoubleValue(flat, "fishStats.whitefishPullSpeed", c.whitefishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.whitefishFatiguedPullSpeed", c.whitefishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.whitefishStamina", c.whitefishStamina, counter);
        putDoubleValue(flat, "fishStats.whitefishTensionGain", c.whitefishTensionGain, counter);
        putDoubleValue(flat, "fishStats.whitefishSprintDurationSec", c.whitefishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.whitefishSprintIntervalSec", c.whitefishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.whitefishTurnIntervalSec", c.whitefishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.whitefishFakeOutChance", c.whitefishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.whitefishFatigueThreshold", c.whitefishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.whitefishFatigueDurationSec", c.whitefishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.whitefishStaminaRegen", c.whitefishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.taimenMinKg", c.taimenMinKg, counter);
        putDoubleValue(flat, "fishStats.taimenMaxKg", c.taimenMaxKg, counter);
        putDoubleValue(flat, "fishStats.taimenPullSpeed", c.taimenPullSpeed, counter);
        putDoubleValue(flat, "fishStats.taimenFatiguedPullSpeed", c.taimenFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.taimenStamina", c.taimenStamina, counter);
        putDoubleValue(flat, "fishStats.taimenTensionGain", c.taimenTensionGain, counter);
        putDoubleValue(flat, "fishStats.taimenSprintDurationSec", c.taimenSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.taimenSprintIntervalSec", c.taimenSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.taimenTurnIntervalSec", c.taimenTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.taimenFakeOutChance", c.taimenFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.taimenFatigueThreshold", c.taimenFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.taimenFatigueDurationSec", c.taimenFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.taimenStaminaRegen", c.taimenStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.arcticCodMinKg", c.arcticCodMinKg, counter);
        putDoubleValue(flat, "fishStats.arcticCodMaxKg", c.arcticCodMaxKg, counter);
        putDoubleValue(flat, "fishStats.arcticCodPullSpeed", c.arcticCodPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arcticCodFatiguedPullSpeed", c.arcticCodFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.arcticCodStamina", c.arcticCodStamina, counter);
        putDoubleValue(flat, "fishStats.arcticCodTensionGain", c.arcticCodTensionGain, counter);
        putDoubleValue(flat, "fishStats.arcticCodSprintDurationSec", c.arcticCodSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.arcticCodSprintIntervalSec", c.arcticCodSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arcticCodTurnIntervalSec", c.arcticCodTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.arcticCodFakeOutChance", c.arcticCodFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.arcticCodFatigueThreshold", c.arcticCodFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.arcticCodFatigueDurationSec", c.arcticCodFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.arcticCodStaminaRegen", c.arcticCodStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.capelinMinKg", c.capelinMinKg, counter);
        putDoubleValue(flat, "fishStats.capelinMaxKg", c.capelinMaxKg, counter);
        putDoubleValue(flat, "fishStats.capelinPullSpeed", c.capelinPullSpeed, counter);
        putDoubleValue(flat, "fishStats.capelinFatiguedPullSpeed", c.capelinFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.capelinStamina", c.capelinStamina, counter);
        putDoubleValue(flat, "fishStats.capelinTensionGain", c.capelinTensionGain, counter);
        putDoubleValue(flat, "fishStats.capelinSprintDurationSec", c.capelinSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.capelinSprintIntervalSec", c.capelinSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.capelinTurnIntervalSec", c.capelinTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.capelinFakeOutChance", c.capelinFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.capelinFatigueThreshold", c.capelinFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.capelinFatigueDurationSec", c.capelinFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.capelinStaminaRegen", c.capelinStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringMinKg", c.atlanticHerringMinKg, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringMaxKg", c.atlanticHerringMaxKg, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringPullSpeed", c.atlanticHerringPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringFatiguedPullSpeed", c.atlanticHerringFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringStamina", c.atlanticHerringStamina, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringTensionGain", c.atlanticHerringTensionGain, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringSprintDurationSec", c.atlanticHerringSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringSprintIntervalSec", c.atlanticHerringSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringTurnIntervalSec", c.atlanticHerringTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringFakeOutChance", c.atlanticHerringFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringFatigueThreshold", c.atlanticHerringFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringFatigueDurationSec", c.atlanticHerringFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticHerringStaminaRegen", c.atlanticHerringStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.wolffishMinKg", c.wolffishMinKg, counter);
        putDoubleValue(flat, "fishStats.wolffishMaxKg", c.wolffishMaxKg, counter);
        putDoubleValue(flat, "fishStats.wolffishPullSpeed", c.wolffishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.wolffishFatiguedPullSpeed", c.wolffishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.wolffishStamina", c.wolffishStamina, counter);
        putDoubleValue(flat, "fishStats.wolffishTensionGain", c.wolffishTensionGain, counter);
        putDoubleValue(flat, "fishStats.wolffishSprintDurationSec", c.wolffishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.wolffishSprintIntervalSec", c.wolffishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.wolffishTurnIntervalSec", c.wolffishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.wolffishFakeOutChance", c.wolffishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.wolffishFatigueThreshold", c.wolffishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.wolffishFatigueDurationSec", c.wolffishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.wolffishStaminaRegen", c.wolffishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.redfishMinKg", c.redfishMinKg, counter);
        putDoubleValue(flat, "fishStats.redfishMaxKg", c.redfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.redfishPullSpeed", c.redfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.redfishFatiguedPullSpeed", c.redfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.redfishStamina", c.redfishStamina, counter);
        putDoubleValue(flat, "fishStats.redfishTensionGain", c.redfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.redfishSprintDurationSec", c.redfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.redfishSprintIntervalSec", c.redfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.redfishTurnIntervalSec", c.redfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.redfishFakeOutChance", c.redfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.redfishFatigueThreshold", c.redfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.redfishFatigueDurationSec", c.redfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.redfishStaminaRegen", c.redfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.sablefishMinKg", c.sablefishMinKg, counter);
        putDoubleValue(flat, "fishStats.sablefishMaxKg", c.sablefishMaxKg, counter);
        putDoubleValue(flat, "fishStats.sablefishPullSpeed", c.sablefishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sablefishFatiguedPullSpeed", c.sablefishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sablefishStamina", c.sablefishStamina, counter);
        putDoubleValue(flat, "fishStats.sablefishTensionGain", c.sablefishTensionGain, counter);
        putDoubleValue(flat, "fishStats.sablefishSprintDurationSec", c.sablefishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.sablefishSprintIntervalSec", c.sablefishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sablefishTurnIntervalSec", c.sablefishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sablefishFakeOutChance", c.sablefishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.sablefishFatigueThreshold", c.sablefishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.sablefishFatigueDurationSec", c.sablefishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.sablefishStaminaRegen", c.sablefishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.atlanticCodMinKg", c.atlanticCodMinKg, counter);
        putDoubleValue(flat, "fishStats.atlanticCodMaxKg", c.atlanticCodMaxKg, counter);
        putDoubleValue(flat, "fishStats.atlanticCodPullSpeed", c.atlanticCodPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticCodFatiguedPullSpeed", c.atlanticCodFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.atlanticCodStamina", c.atlanticCodStamina, counter);
        putDoubleValue(flat, "fishStats.atlanticCodTensionGain", c.atlanticCodTensionGain, counter);
        putDoubleValue(flat, "fishStats.atlanticCodSprintDurationSec", c.atlanticCodSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticCodSprintIntervalSec", c.atlanticCodSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticCodTurnIntervalSec", c.atlanticCodTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.atlanticCodFakeOutChance", c.atlanticCodFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.atlanticCodFatigueThreshold", c.atlanticCodFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.atlanticCodFatigueDurationSec", c.atlanticCodFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.atlanticCodStaminaRegen", c.atlanticCodStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutMinKg", c.greenlandHalibutMinKg, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutMaxKg", c.greenlandHalibutMaxKg, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutPullSpeed", c.greenlandHalibutPullSpeed, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutFatiguedPullSpeed", c.greenlandHalibutFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutStamina", c.greenlandHalibutStamina, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutTensionGain", c.greenlandHalibutTensionGain, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutSprintDurationSec", c.greenlandHalibutSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutSprintIntervalSec", c.greenlandHalibutSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutTurnIntervalSec", c.greenlandHalibutTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutFakeOutChance", c.greenlandHalibutFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutFatigueThreshold", c.greenlandHalibutFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutFatigueDurationSec", c.greenlandHalibutFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.greenlandHalibutStaminaRegen", c.greenlandHalibutStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkMinKg", c.greenlandSharkMinKg, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkMaxKg", c.greenlandSharkMaxKg, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkPullSpeed", c.greenlandSharkPullSpeed, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkFatiguedPullSpeed", c.greenlandSharkFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkStamina", c.greenlandSharkStamina, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkTensionGain", c.greenlandSharkTensionGain, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkSprintDurationSec", c.greenlandSharkSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkSprintIntervalSec", c.greenlandSharkSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkTurnIntervalSec", c.greenlandSharkTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkFakeOutChance", c.greenlandSharkFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkFatigueThreshold", c.greenlandSharkFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkFatigueDurationSec", c.greenlandSharkFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.greenlandSharkStaminaRegen", c.greenlandSharkStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.sailfishMinKg", c.sailfishMinKg, counter);
        putDoubleValue(flat, "fishStats.sailfishMaxKg", c.sailfishMaxKg, counter);
        putDoubleValue(flat, "fishStats.sailfishPullSpeed", c.sailfishPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sailfishFatiguedPullSpeed", c.sailfishFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.sailfishStamina", c.sailfishStamina, counter);
        putDoubleValue(flat, "fishStats.sailfishTensionGain", c.sailfishTensionGain, counter);
        putDoubleValue(flat, "fishStats.sailfishSprintDurationSec", c.sailfishSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.sailfishSprintIntervalSec", c.sailfishSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sailfishTurnIntervalSec", c.sailfishTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.sailfishFakeOutChance", c.sailfishFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.sailfishFatigueThreshold", c.sailfishFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.sailfishFatigueDurationSec", c.sailfishFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.sailfishStaminaRegen", c.sailfishStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.mahiMahiMinKg", c.mahiMahiMinKg, counter);
        putDoubleValue(flat, "fishStats.mahiMahiMaxKg", c.mahiMahiMaxKg, counter);
        putDoubleValue(flat, "fishStats.mahiMahiPullSpeed", c.mahiMahiPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mahiMahiFatiguedPullSpeed", c.mahiMahiFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.mahiMahiStamina", c.mahiMahiStamina, counter);
        putDoubleValue(flat, "fishStats.mahiMahiTensionGain", c.mahiMahiTensionGain, counter);
        putDoubleValue(flat, "fishStats.mahiMahiSprintDurationSec", c.mahiMahiSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.mahiMahiSprintIntervalSec", c.mahiMahiSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mahiMahiTurnIntervalSec", c.mahiMahiTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.mahiMahiFakeOutChance", c.mahiMahiFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.mahiMahiFatigueThreshold", c.mahiMahiFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.mahiMahiFatigueDurationSec", c.mahiMahiFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.mahiMahiStaminaRegen", c.mahiMahiStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.alligatorGarMinKg", c.alligatorGarMinKg, counter);
        putDoubleValue(flat, "fishStats.alligatorGarMaxKg", c.alligatorGarMaxKg, counter);
        putDoubleValue(flat, "fishStats.alligatorGarPullSpeed", c.alligatorGarPullSpeed, counter);
        putDoubleValue(flat, "fishStats.alligatorGarFatiguedPullSpeed", c.alligatorGarFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.alligatorGarStamina", c.alligatorGarStamina, counter);
        putDoubleValue(flat, "fishStats.alligatorGarTensionGain", c.alligatorGarTensionGain, counter);
        putDoubleValue(flat, "fishStats.alligatorGarSprintDurationSec", c.alligatorGarSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.alligatorGarSprintIntervalSec", c.alligatorGarSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.alligatorGarTurnIntervalSec", c.alligatorGarTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.alligatorGarFakeOutChance", c.alligatorGarFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.alligatorGarFatigueThreshold", c.alligatorGarFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.alligatorGarFatigueDurationSec", c.alligatorGarFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.alligatorGarStaminaRegen", c.alligatorGarStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpMinKg", c.giantSiameseCarpMinKg, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpMaxKg", c.giantSiameseCarpMaxKg, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpPullSpeed", c.giantSiameseCarpPullSpeed, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpFatiguedPullSpeed", c.giantSiameseCarpFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpStamina", c.giantSiameseCarpStamina, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpTensionGain", c.giantSiameseCarpTensionGain, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpSprintDurationSec", c.giantSiameseCarpSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpSprintIntervalSec", c.giantSiameseCarpSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpTurnIntervalSec", c.giantSiameseCarpTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpFakeOutChance", c.giantSiameseCarpFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpFatigueThreshold", c.giantSiameseCarpFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpFatigueDurationSec", c.giantSiameseCarpFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.giantSiameseCarpStaminaRegen", c.giantSiameseCarpStaminaRegen, counter);
        putDoubleValue(flat, "fishStats.electricEelMinKg", c.electricEelMinKg, counter);
        putDoubleValue(flat, "fishStats.electricEelMaxKg", c.electricEelMaxKg, counter);
        putDoubleValue(flat, "fishStats.electricEelPullSpeed", c.electricEelPullSpeed, counter);
        putDoubleValue(flat, "fishStats.electricEelFatiguedPullSpeed", c.electricEelFatiguedPullSpeed, counter);
        putDoubleValue(flat, "fishStats.electricEelStamina", c.electricEelStamina, counter);
        putDoubleValue(flat, "fishStats.electricEelTensionGain", c.electricEelTensionGain, counter);
        putDoubleValue(flat, "fishStats.electricEelSprintDurationSec", c.electricEelSprintDurationSec, counter);
        putDoubleValue(flat, "fishStats.electricEelSprintIntervalSec", c.electricEelSprintIntervalSec, counter);
        putDoubleValue(flat, "fishStats.electricEelTurnIntervalSec", c.electricEelTurnIntervalSec, counter);
        putDoubleValue(flat, "fishStats.electricEelFakeOutChance", c.electricEelFakeOutChance, counter);
        putDoubleValue(flat, "fishStats.electricEelFatigueThreshold", c.electricEelFatigueThreshold, counter);
        putDoubleValue(flat, "fishStats.electricEelFatigueDurationSec", c.electricEelFatigueDurationSec, counter);
        putDoubleValue(flat, "fishStats.electricEelStaminaRegen", c.electricEelStaminaRegen, counter);
        // ==== END 鱼种数值热重载 ====
    // @tuning-slot: gameReload
        // ==== BEGIN 玩法数值热重载（生成，勿手改）====
        putDoubleValue(flat, "gameplay.rodWoodMaxWeightKg", c.rodWoodMaxWeightKg, counter);
        putDoubleValue(flat, "gameplay.rodWoodHookWindowBonus", c.rodWoodHookWindowBonus, counter);
        putDoubleValue(flat, "gameplay.rodWoodTensionCeiling", c.rodWoodTensionCeiling, counter);
        putDoubleValue(flat, "gameplay.rodWoodTensionRecover", c.rodWoodTensionRecover, counter);
        putDoubleValue(flat, "gameplay.rodWoodStaminaDrain", c.rodWoodStaminaDrain, counter);
        putDoubleValue(flat, "gameplay.rodWoodMaterialFactor", c.rodWoodMaterialFactor, counter);
        putIntValue(flat, "gameplay.rodWoodTier", c.rodWoodTier, counter);
        putDoubleValue(flat, "gameplay.rodIronMaxWeightKg", c.rodIronMaxWeightKg, counter);
        putDoubleValue(flat, "gameplay.rodIronHookWindowBonus", c.rodIronHookWindowBonus, counter);
        putDoubleValue(flat, "gameplay.rodIronTensionCeiling", c.rodIronTensionCeiling, counter);
        putDoubleValue(flat, "gameplay.rodIronTensionRecover", c.rodIronTensionRecover, counter);
        putDoubleValue(flat, "gameplay.rodIronStaminaDrain", c.rodIronStaminaDrain, counter);
        putDoubleValue(flat, "gameplay.rodIronMaterialFactor", c.rodIronMaterialFactor, counter);
        putIntValue(flat, "gameplay.rodIronTier", c.rodIronTier, counter);
        putDoubleValue(flat, "gameplay.rodGoldMaxWeightKg", c.rodGoldMaxWeightKg, counter);
        putDoubleValue(flat, "gameplay.rodGoldHookWindowBonus", c.rodGoldHookWindowBonus, counter);
        putDoubleValue(flat, "gameplay.rodGoldTensionCeiling", c.rodGoldTensionCeiling, counter);
        putDoubleValue(flat, "gameplay.rodGoldTensionRecover", c.rodGoldTensionRecover, counter);
        putDoubleValue(flat, "gameplay.rodGoldStaminaDrain", c.rodGoldStaminaDrain, counter);
        putDoubleValue(flat, "gameplay.rodGoldMaterialFactor", c.rodGoldMaterialFactor, counter);
        putIntValue(flat, "gameplay.rodGoldTier", c.rodGoldTier, counter);
        putDoubleValue(flat, "gameplay.rodDiamondMaxWeightKg", c.rodDiamondMaxWeightKg, counter);
        putDoubleValue(flat, "gameplay.rodDiamondHookWindowBonus", c.rodDiamondHookWindowBonus, counter);
        putDoubleValue(flat, "gameplay.rodDiamondTensionCeiling", c.rodDiamondTensionCeiling, counter);
        putDoubleValue(flat, "gameplay.rodDiamondTensionRecover", c.rodDiamondTensionRecover, counter);
        putDoubleValue(flat, "gameplay.rodDiamondStaminaDrain", c.rodDiamondStaminaDrain, counter);
        putDoubleValue(flat, "gameplay.rodDiamondMaterialFactor", c.rodDiamondMaterialFactor, counter);
        putIntValue(flat, "gameplay.rodDiamondTier", c.rodDiamondTier, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteMaxWeightKg", c.rodNetheriteMaxWeightKg, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteHookWindowBonus", c.rodNetheriteHookWindowBonus, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteTensionCeiling", c.rodNetheriteTensionCeiling, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteTensionRecover", c.rodNetheriteTensionRecover, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteStaminaDrain", c.rodNetheriteStaminaDrain, counter);
        putDoubleValue(flat, "gameplay.rodNetheriteMaterialFactor", c.rodNetheriteMaterialFactor, counter);
        putIntValue(flat, "gameplay.rodNetheriteTier", c.rodNetheriteTier, counter);
        putDoubleValue(flat, "gameplay.tensionBaseCeiling", c.tensionBaseCeiling, counter);
        putDoubleValue(flat, "gameplay.pullSprint", c.pullSprint, counter);
        putDoubleValue(flat, "gameplay.counterBonus", c.counterBonus, counter);
        putDoubleValue(flat, "gameplay.counterPenalty", c.counterPenalty, counter);
        putDoubleValue(flat, "gameplay.minReelGainRatio", c.minReelGainRatio, counter);
        putDoubleValue(flat, "gameplay.maxFishSlowdown", c.maxFishSlowdown, counter);
        putDoubleValue(flat, "gameplay.dampingToCapRatio", c.dampingToCapRatio, counter);
        putDoubleValue(flat, "gameplay.capDampingBonus", c.capDampingBonus, counter);
        putDoubleValue(flat, "gameplay.totalDrainTarget", c.totalDrainTarget, counter);
        putDoubleValue(flat, "gameplay.nearShoreBase", c.nearShoreBase, counter);
        putDoubleValue(flat, "gameplay.staminaEpsilonFloor", c.staminaEpsilonFloor, counter);
        putDoubleValue(flat, "gameplay.staminaEpsilonRatio", c.staminaEpsilonRatio, counter);
        putIntValue(flat, "gameplay.inputStaleLimit", c.inputStaleLimit, counter);
        putDoubleValue(flat, "gameplay.tierSpeedBase", c.tierSpeedBase, counter);
        putDoubleValue(flat, "gameplay.tierStaminaResistBase", c.tierStaminaResistBase, counter);
        putDoubleValue(flat, "gameplay.tierGoldSpeedScale", c.tierGoldSpeedScale, counter);
        putDoubleValue(flat, "gameplay.tierGoldStaminaScale", c.tierGoldStaminaScale, counter);
        putDoubleValue(flat, "gameplay.tierRedSpeedScale", c.tierRedSpeedScale, counter);
        putDoubleValue(flat, "gameplay.tierRedStaminaScale", c.tierRedStaminaScale, counter);
        putDoubleValue(flat, "gameplay.tierRedStaminaMin", c.tierRedStaminaMin, counter);
        putDoubleValue(flat, "gameplay.fatiguePullMultiplier", c.fatiguePullMultiplier, counter);
        putDoubleValue(flat, "gameplay.reelFloorDrainBase", c.reelFloorDrainBase, counter);
        putDoubleValue(flat, "gameplay.reelFloorDrainPerDamping", c.reelFloorDrainPerDamping, counter);
        putDoubleValue(flat, "gameplay.reelFloorDrainCap", c.reelFloorDrainCap, counter);
        putDoubleValue(flat, "gameplay.floorStruggleBase", c.floorStruggleBase, counter);
        putDoubleValue(flat, "gameplay.floorStrugglePerTier", c.floorStrugglePerTier, counter);
        putDoubleValue(flat, "gameplay.floorStruggleBonusSteady", c.floorStruggleBonusSteady, counter);
        putDoubleValue(flat, "gameplay.floorStruggleBonusSluggish", c.floorStruggleBonusSluggish, counter);
        putDoubleValue(flat, "gameplay.floorStruggleBonusBurst", c.floorStruggleBonusBurst, counter);
        putDoubleValue(flat, "gameplay.floorStruggleBonusErratic", c.floorStruggleBonusErratic, counter);
        putDoubleValue(flat, "gameplay.reelResistThreshold", c.reelResistThreshold, counter);
        putDoubleValue(flat, "gameplay.reelResistTierWhite", c.reelResistTierWhite, counter);
        putDoubleValue(flat, "gameplay.reelResistTierGreen", c.reelResistTierGreen, counter);
        putDoubleValue(flat, "gameplay.reelResistTierBlue", c.reelResistTierBlue, counter);
        putDoubleValue(flat, "gameplay.reelResistTierPurple", c.reelResistTierPurple, counter);
        putDoubleValue(flat, "gameplay.reelResistTierGold", c.reelResistTierGold, counter);
        putDoubleValue(flat, "gameplay.reelResistTierRed", c.reelResistTierRed, counter);
        putDoubleValue(flat, "gameplay.reelResistStratSteady", c.reelResistStratSteady, counter);
        putDoubleValue(flat, "gameplay.reelResistStratSluggish", c.reelResistStratSluggish, counter);
        putDoubleValue(flat, "gameplay.reelResistStratBurst", c.reelResistStratBurst, counter);
        putDoubleValue(flat, "gameplay.reelResistStratErratic", c.reelResistStratErratic, counter);
        putDoubleValue(flat, "gameplay.reelResistDampingOffset", c.reelResistDampingOffset, counter);
        putDoubleValue(flat, "gameplay.reelResistDampingCap", c.reelResistDampingCap, counter);
        putDoubleValue(flat, "gameplay.fatiguedReelMinGain", c.fatiguedReelMinGain, counter);
        putDoubleValue(flat, "gameplay.fatiguedTensionGainScale", c.fatiguedTensionGainScale, counter);
        putDoubleValue(flat, "gameplay.fatiguedTensionRecoverScale", c.fatiguedTensionRecoverScale, counter);
        putDoubleValue(flat, "gameplay.fatigueThresholdScale", c.fatigueThresholdScale, counter);
        putDoubleValue(flat, "gameplay.idleFallFloor", c.idleFallFloor, counter);
        putDoubleValue(flat, "gameplay.struggleSecondsBase", c.struggleSecondsBase, counter);
        putDoubleValue(flat, "gameplay.struggleSecondsPerTier", c.struggleSecondsPerTier, counter);
        putDoubleValue(flat, "gameplay.restSecondsBase", c.restSecondsBase, counter);
        putDoubleValue(flat, "gameplay.restSecondsPerTier", c.restSecondsPerTier, counter);
        putIntValue(flat, "gameplay.alpineLakeMinY", c.alpineLakeMinY, counter);
        putDoubleValue(flat, "gameplay.coldWaterMaxTemp", c.coldWaterMaxTemp, counter);
        putIntValue(flat, "gameplay.brokenIceToPackIceMinutes", c.brokenIceToPackIceMinutes, counter);
        putIntValue(flat, "gameplay.packIceToIceMinutes", c.packIceToIceMinutes, counter);
        putIntValue(flat, "gameplay.icePickDurabilityCost", c.icePickDurabilityCost, counter);
        putDoubleValue(flat, "gameplay.iceHoleLineWearMultiplier", c.iceHoleLineWearMultiplier, counter);
        putDoubleValue(flat, "gameplay.iceHoleHighValueBonus", c.iceHoleHighValueBonus, counter);
        putDoubleValue(flat, "gameplay.openIceBiteTimeMultiplier", c.openIceBiteTimeMultiplier, counter);
        putDoubleValue(flat, "gameplay.openIceHighValuePenalty", c.openIceHighValuePenalty, counter);
        putIntValue(flat, "gameplay.fullChargeTicks", c.fullChargeTicks, counter);
        putIntValue(flat, "gameplay.minChargeTicks", c.minChargeTicks, counter);
        putIntValue(flat, "gameplay.chumMinAirTicks", c.chumMinAirTicks, counter);
        putIntValue(flat, "gameplay.oversizeFishAction", c.oversizeFishAction, counter);
        putIntValue(flat, "gameplay.compassSearchRadius", c.compassSearchRadius, counter);
        putIntValue(flat, "gameplay.bigCatchAnnounceValue", c.bigCatchAnnounceValue, counter);
        putIntValue(flat, "gameplay.bigCatchAnnounceMinIntervalSec", c.bigCatchAnnounceMinIntervalSec, counter);
        // ==== END 玩法数值热重载 ====

    }

    /**
     * 单项：把文件里的数值写进 {@code ConfigValue} 并清掉它的缓存。
     *
     * <p>两种类型分开处理（double / int），因为 {@code ConfigValue.set} 是泛型的，
     * 直接塞 {@code Number} 会因为泛型擦除而在取值时抛 ClassCastException。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void put(java.util.Map<String, Object> flat, String path,
                            net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<?> value,
                            int[] counter) {
        Object v = flat.get(path);
        if (v == null) {
            return;
        }
        try {
            Object def = value.getDefault();
            // ⚠ 按**默认值的类型**决定怎么写回去，且类型对不上就跳过：
            //   泛型擦除下乱塞会让取值时抛 ClassCastException（这条注释原来就在，别删）。
            if (def instanceof Integer && v instanceof Number num) {
                ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) value).set(num.intValue());
            } else if (def instanceof Double && v instanceof Number num) {
                ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) value).set(num.doubleValue());
            } else if (def instanceof Boolean && v instanceof Boolean b) {
                ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) value).set(b);
            } else if (def instanceof String && v instanceof String s) {
                ((net.neoforged.neoforge.common.ModConfigSpec.ConfigValue) value).set(s);
            } else {
                return;         // 类型对不上（例如手改成了字符串）⇒ 跳过这一项
            }
            // 必须清缓存：不清的话 get() 仍返回旧值，表现为"敲了 reload 没反应"
            value.clearCache();
            counter[0]++;
        } catch (Throwable t) {
            // 单项失败（例如手改坏了格式）就跳过，不影响其它项
            AnglersTackle.LOGGER.debug("[垂纶] 热重载跳过 {}：{}", path, t.toString());
        }
    }

    /**
     * 极简 TOML 读取：认 {@code [分组]} 头，以及 {@code 键 = 数字 / true|false / "字符串"}，
     * 忽略注释与空行。够用且不引入依赖。
     *
     * <p>⚠ 2026-10-12 扩展：原先**只认数字**（"boolean/字符串本类不关心"），
     * 于是 {@code put(...)} 里那些 boolean / String 项**悄悄不生效** ——
     * 敲了 {@code /atl config reload} 看着成功、值却没变（而调参器里每一项都列着它 ✗）。
     * 联赛的 {@code clockMode} / {@code rule} 是字符串、{@code inviteOnLogin} 是布尔 ⇒ 必须补上。
     */
    private static java.util.Map<String, Object> parseFlatToml(java.nio.file.Path file)
            throws java.io.IOException {
        java.util.Map<String, Object> out = new java.util.HashMap<>();
        String group = "";
        for (String rawLine : java.nio.file.Files.readAllLines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                group = line.substring(1, line.length() - 1).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0 || group.isEmpty()) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1).trim();
            // ⚠ 去行内注释要**避开字符串里的 #**（"a#b" 是合法值）
            if (!val.startsWith("\"")) {
                int hash = val.indexOf('#');
                if (hash >= 0) {
                    val = val.substring(0, hash).trim();
                }
            }
            String path = group + "." + key;
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                out.put(path, val.substring(1, val.length() - 1));
                continue;
            }
            if ("true".equals(val) || "false".equals(val)) {
                out.put(path, Boolean.valueOf(val));
                continue;
            }
            // TOML 的行内注释与尾部 D/d 后缀（NeoForge 写 double 会带 'd'）
            if (val.endsWith("d") || val.endsWith("D")) {
                val = val.substring(0, val.length() - 1);
            }
            try {
                out.put(path, Double.valueOf(val));
            } catch (NumberFormatException ignored) {
                // 认不出来就跳过这一项（不影响别的项）
            }
        }
        return out;
    }

    /**
     * 配置文件路径。
     *
     * <p>NeoForge 把 common 配置放在 {@code <gameDir>/config/<modid>-common.toml}。
     * 用 FML 的加载器拿到真实 gameDir，而不是猜 {@code user.dir} ——
     * 开发环境和正式启动的 gameDir 不是同一个值。
     */
    private static java.nio.file.Path configFile() {
        try {
            java.nio.file.Path gameDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
            return gameDir.resolve("config").resolve(
                    AnglersTackle.MOD_ID + "-" + "common" + ".toml");
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 兜底
    /**
     * 配置句柄。
     *
     * <p>极早期（构造注册阶段）配置还没加载完，{@code get()} 会抛异常。
     * 那时如果直接崩，游戏起不来 —— 而这些值只在钓鱼时用，早期根本不需要。
     * 所以这里把异常翻译成一个明确的错误信息，便于定位而不是静默给错值。
     */


    // @tuning-slot: partsGetter
    // ==== BEGIN 配件数值 getter（生成，勿手改）====
    public static double hempTensionCeiling() { return cfg().hempTensionCeiling.get(); }
    public static double hempTensionGen() { return cfg().hempTensionGen.get(); }
    public static double hempTensionRecover() { return cfg().hempTensionRecover.get(); }
    public static double hempLineLength() { return cfg().hempLineLength.get(); }
    public static int hempTier() { return cfg().hempTier.get(); }
    public static double cottonTensionCeiling() { return cfg().cottonTensionCeiling.get(); }
    public static double cottonTensionGen() { return cfg().cottonTensionGen.get(); }
    public static double cottonTensionRecover() { return cfg().cottonTensionRecover.get(); }
    public static double cottonLineLength() { return cfg().cottonLineLength.get(); }
    public static int cottonTier() { return cfg().cottonTier.get(); }
    public static double nylonTensionCeiling() { return cfg().nylonTensionCeiling.get(); }
    public static double nylonTensionGen() { return cfg().nylonTensionGen.get(); }
    public static double nylonTensionRecover() { return cfg().nylonTensionRecover.get(); }
    public static double nylonLineLength() { return cfg().nylonLineLength.get(); }
    public static int nylonTier() { return cfg().nylonTier.get(); }
    public static double fluoroTensionCeiling() { return cfg().fluoroTensionCeiling.get(); }
    public static double fluoroTensionGen() { return cfg().fluoroTensionGen.get(); }
    public static double fluoroTensionRecover() { return cfg().fluoroTensionRecover.get(); }
    public static double fluoroLineLength() { return cfg().fluoroLineLength.get(); }
    public static int fluoroTier() { return cfg().fluoroTier.get(); }
    public static double peTensionCeiling() { return cfg().peTensionCeiling.get(); }
    public static double peTensionGen() { return cfg().peTensionGen.get(); }
    public static double peTensionRecover() { return cfg().peTensionRecover.get(); }
    public static double peLineLength() { return cfg().peLineLength.get(); }
    public static int peTier() { return cfg().peTier.get(); }
    public static double titaniumTensionCeiling() { return cfg().titaniumTensionCeiling.get(); }
    public static double titaniumTensionGen() { return cfg().titaniumTensionGen.get(); }
    public static double titaniumTensionRecover() { return cfg().titaniumTensionRecover.get(); }
    public static double titaniumLineLength() { return cfg().titaniumLineLength.get(); }
    public static int titaniumTier() { return cfg().titaniumTier.get(); }
    public static double handPullIdle() { return cfg().handPullIdle.get(); }
    public static double handPullStruggle() { return cfg().handPullStruggle.get(); }
    public static double handStaminaDrain() { return cfg().handStaminaDrain.get(); }
    public static double handDamping() { return cfg().handDamping.get(); }
    public static int handTier() { return cfg().handTier.get(); }
    public static double spinningPullIdle() { return cfg().spinningPullIdle.get(); }
    public static double spinningPullStruggle() { return cfg().spinningPullStruggle.get(); }
    public static double spinningStaminaDrain() { return cfg().spinningStaminaDrain.get(); }
    public static double spinningDamping() { return cfg().spinningDamping.get(); }
    public static int spinningTier() { return cfg().spinningTier.get(); }
    public static double drumPullIdle() { return cfg().drumPullIdle.get(); }
    public static double drumPullStruggle() { return cfg().drumPullStruggle.get(); }
    public static double drumStaminaDrain() { return cfg().drumStaminaDrain.get(); }
    public static double drumDamping() { return cfg().drumDamping.get(); }
    public static int drumTier() { return cfg().drumTier.get(); }
    public static double baitcastPullIdle() { return cfg().baitcastPullIdle.get(); }
    public static double baitcastPullStruggle() { return cfg().baitcastPullStruggle.get(); }
    public static double baitcastStaminaDrain() { return cfg().baitcastStaminaDrain.get(); }
    public static double baitcastDamping() { return cfg().baitcastDamping.get(); }
    public static int baitcastTier() { return cfg().baitcastTier.get(); }
    public static double heavyPullIdle() { return cfg().heavyPullIdle.get(); }
    public static double heavyPullStruggle() { return cfg().heavyPullStruggle.get(); }
    public static double heavyStaminaDrain() { return cfg().heavyStaminaDrain.get(); }
    public static double heavyDamping() { return cfg().heavyDamping.get(); }
    public static int heavyTier() { return cfg().heavyTier.get(); }
    public static int lureSpoonTier() { return cfg().lureSpoonTier.get(); }
    public static int lureGlowspoonTier() { return cfg().lureGlowspoonTier.get(); }
    public static int lureMinnowTier() { return cfg().lureMinnowTier.get(); }
    public static int lureDeepTier() { return cfg().lureDeepTier.get(); }
    public static int lureRattleTier() { return cfg().lureRattleTier.get(); }
    public static int lureGildedTier() { return cfg().lureGildedTier.get(); }
    public static int lureSoftTier() { return cfg().lureSoftTier.get(); }
    public static int lureDepthfinderTier() { return cfg().lureDepthfinderTier.get(); }
    public static int lurePaddletailTier() { return cfg().lurePaddletailTier.get(); }
    public static int lureVibrationTier() { return cfg().lureVibrationTier.get(); }
    public static int lureTestRedTier() { return cfg().lureTestRedTier.get(); }
    // ==== END 配件数值 getter ====
    // @tuning-slot: fishGetter
    // ==== BEGIN 鱼种数值 getter（生成，勿手改）====
    public static double crucianCarpMinKg() { return cfg().crucianCarpMinKg.get(); }
    public static double crucianCarpMaxKg() { return cfg().crucianCarpMaxKg.get(); }
    public static double crucianCarpPullSpeed() { return cfg().crucianCarpPullSpeed.get(); }
    public static double crucianCarpFatiguedPullSpeed() { return cfg().crucianCarpFatiguedPullSpeed.get(); }
    public static double crucianCarpStamina() { return cfg().crucianCarpStamina.get(); }
    public static double crucianCarpTensionGain() { return cfg().crucianCarpTensionGain.get(); }
    public static double crucianCarpSprintDurationSec() { return cfg().crucianCarpSprintDurationSec.get(); }
    public static double crucianCarpSprintIntervalSec() { return cfg().crucianCarpSprintIntervalSec.get(); }
    public static double crucianCarpTurnIntervalSec() { return cfg().crucianCarpTurnIntervalSec.get(); }
    public static double crucianCarpFakeOutChance() { return cfg().crucianCarpFakeOutChance.get(); }
    public static double crucianCarpFatigueThreshold() { return cfg().crucianCarpFatigueThreshold.get(); }
    public static double crucianCarpFatigueDurationSec() { return cfg().crucianCarpFatigueDurationSec.get(); }
    public static double crucianCarpStaminaRegen() { return cfg().crucianCarpStaminaRegen.get(); }
    public static double tilapiaMinKg() { return cfg().tilapiaMinKg.get(); }
    public static double tilapiaMaxKg() { return cfg().tilapiaMaxKg.get(); }
    public static double tilapiaPullSpeed() { return cfg().tilapiaPullSpeed.get(); }
    public static double tilapiaFatiguedPullSpeed() { return cfg().tilapiaFatiguedPullSpeed.get(); }
    public static double tilapiaStamina() { return cfg().tilapiaStamina.get(); }
    public static double tilapiaTensionGain() { return cfg().tilapiaTensionGain.get(); }
    public static double tilapiaSprintDurationSec() { return cfg().tilapiaSprintDurationSec.get(); }
    public static double tilapiaSprintIntervalSec() { return cfg().tilapiaSprintIntervalSec.get(); }
    public static double tilapiaTurnIntervalSec() { return cfg().tilapiaTurnIntervalSec.get(); }
    public static double tilapiaFakeOutChance() { return cfg().tilapiaFakeOutChance.get(); }
    public static double tilapiaFatigueThreshold() { return cfg().tilapiaFatigueThreshold.get(); }
    public static double tilapiaFatigueDurationSec() { return cfg().tilapiaFatigueDurationSec.get(); }
    public static double tilapiaStaminaRegen() { return cfg().tilapiaStaminaRegen.get(); }
    public static double mulletMinKg() { return cfg().mulletMinKg.get(); }
    public static double mulletMaxKg() { return cfg().mulletMaxKg.get(); }
    public static double mulletPullSpeed() { return cfg().mulletPullSpeed.get(); }
    public static double mulletFatiguedPullSpeed() { return cfg().mulletFatiguedPullSpeed.get(); }
    public static double mulletStamina() { return cfg().mulletStamina.get(); }
    public static double mulletTensionGain() { return cfg().mulletTensionGain.get(); }
    public static double mulletSprintDurationSec() { return cfg().mulletSprintDurationSec.get(); }
    public static double mulletSprintIntervalSec() { return cfg().mulletSprintIntervalSec.get(); }
    public static double mulletTurnIntervalSec() { return cfg().mulletTurnIntervalSec.get(); }
    public static double mulletFakeOutChance() { return cfg().mulletFakeOutChance.get(); }
    public static double mulletFatigueThreshold() { return cfg().mulletFatigueThreshold.get(); }
    public static double mulletFatigueDurationSec() { return cfg().mulletFatigueDurationSec.get(); }
    public static double mulletStaminaRegen() { return cfg().mulletStaminaRegen.get(); }
    public static double grassCarpMinKg() { return cfg().grassCarpMinKg.get(); }
    public static double grassCarpMaxKg() { return cfg().grassCarpMaxKg.get(); }
    public static double grassCarpPullSpeed() { return cfg().grassCarpPullSpeed.get(); }
    public static double grassCarpFatiguedPullSpeed() { return cfg().grassCarpFatiguedPullSpeed.get(); }
    public static double grassCarpStamina() { return cfg().grassCarpStamina.get(); }
    public static double grassCarpTensionGain() { return cfg().grassCarpTensionGain.get(); }
    public static double grassCarpSprintDurationSec() { return cfg().grassCarpSprintDurationSec.get(); }
    public static double grassCarpSprintIntervalSec() { return cfg().grassCarpSprintIntervalSec.get(); }
    public static double grassCarpTurnIntervalSec() { return cfg().grassCarpTurnIntervalSec.get(); }
    public static double grassCarpFakeOutChance() { return cfg().grassCarpFakeOutChance.get(); }
    public static double grassCarpFatigueThreshold() { return cfg().grassCarpFatigueThreshold.get(); }
    public static double grassCarpFatigueDurationSec() { return cfg().grassCarpFatigueDurationSec.get(); }
    public static double grassCarpStaminaRegen() { return cfg().grassCarpStaminaRegen.get(); }
    public static double blackCarpMinKg() { return cfg().blackCarpMinKg.get(); }
    public static double blackCarpMaxKg() { return cfg().blackCarpMaxKg.get(); }
    public static double blackCarpPullSpeed() { return cfg().blackCarpPullSpeed.get(); }
    public static double blackCarpFatiguedPullSpeed() { return cfg().blackCarpFatiguedPullSpeed.get(); }
    public static double blackCarpStamina() { return cfg().blackCarpStamina.get(); }
    public static double blackCarpTensionGain() { return cfg().blackCarpTensionGain.get(); }
    public static double blackCarpSprintDurationSec() { return cfg().blackCarpSprintDurationSec.get(); }
    public static double blackCarpSprintIntervalSec() { return cfg().blackCarpSprintIntervalSec.get(); }
    public static double blackCarpTurnIntervalSec() { return cfg().blackCarpTurnIntervalSec.get(); }
    public static double blackCarpFakeOutChance() { return cfg().blackCarpFakeOutChance.get(); }
    public static double blackCarpFatigueThreshold() { return cfg().blackCarpFatigueThreshold.get(); }
    public static double blackCarpFatigueDurationSec() { return cfg().blackCarpFatigueDurationSec.get(); }
    public static double blackCarpStaminaRegen() { return cfg().blackCarpStaminaRegen.get(); }
    public static double bandedLoachMinKg() { return cfg().bandedLoachMinKg.get(); }
    public static double bandedLoachMaxKg() { return cfg().bandedLoachMaxKg.get(); }
    public static double bandedLoachPullSpeed() { return cfg().bandedLoachPullSpeed.get(); }
    public static double bandedLoachFatiguedPullSpeed() { return cfg().bandedLoachFatiguedPullSpeed.get(); }
    public static double bandedLoachStamina() { return cfg().bandedLoachStamina.get(); }
    public static double bandedLoachTensionGain() { return cfg().bandedLoachTensionGain.get(); }
    public static double bandedLoachSprintDurationSec() { return cfg().bandedLoachSprintDurationSec.get(); }
    public static double bandedLoachSprintIntervalSec() { return cfg().bandedLoachSprintIntervalSec.get(); }
    public static double bandedLoachTurnIntervalSec() { return cfg().bandedLoachTurnIntervalSec.get(); }
    public static double bandedLoachFakeOutChance() { return cfg().bandedLoachFakeOutChance.get(); }
    public static double bandedLoachFatigueThreshold() { return cfg().bandedLoachFatigueThreshold.get(); }
    public static double bandedLoachFatigueDurationSec() { return cfg().bandedLoachFatigueDurationSec.get(); }
    public static double bandedLoachStaminaRegen() { return cfg().bandedLoachStaminaRegen.get(); }
    public static double hemiculterMinKg() { return cfg().hemiculterMinKg.get(); }
    public static double hemiculterMaxKg() { return cfg().hemiculterMaxKg.get(); }
    public static double hemiculterPullSpeed() { return cfg().hemiculterPullSpeed.get(); }
    public static double hemiculterFatiguedPullSpeed() { return cfg().hemiculterFatiguedPullSpeed.get(); }
    public static double hemiculterStamina() { return cfg().hemiculterStamina.get(); }
    public static double hemiculterTensionGain() { return cfg().hemiculterTensionGain.get(); }
    public static double hemiculterSprintDurationSec() { return cfg().hemiculterSprintDurationSec.get(); }
    public static double hemiculterSprintIntervalSec() { return cfg().hemiculterSprintIntervalSec.get(); }
    public static double hemiculterTurnIntervalSec() { return cfg().hemiculterTurnIntervalSec.get(); }
    public static double hemiculterFakeOutChance() { return cfg().hemiculterFakeOutChance.get(); }
    public static double hemiculterFatigueThreshold() { return cfg().hemiculterFatigueThreshold.get(); }
    public static double hemiculterFatigueDurationSec() { return cfg().hemiculterFatigueDurationSec.get(); }
    public static double hemiculterStaminaRegen() { return cfg().hemiculterStaminaRegen.get(); }
    public static double topmouthCulterMinKg() { return cfg().topmouthCulterMinKg.get(); }
    public static double topmouthCulterMaxKg() { return cfg().topmouthCulterMaxKg.get(); }
    public static double topmouthCulterPullSpeed() { return cfg().topmouthCulterPullSpeed.get(); }
    public static double topmouthCulterFatiguedPullSpeed() { return cfg().topmouthCulterFatiguedPullSpeed.get(); }
    public static double topmouthCulterStamina() { return cfg().topmouthCulterStamina.get(); }
    public static double topmouthCulterTensionGain() { return cfg().topmouthCulterTensionGain.get(); }
    public static double topmouthCulterSprintDurationSec() { return cfg().topmouthCulterSprintDurationSec.get(); }
    public static double topmouthCulterSprintIntervalSec() { return cfg().topmouthCulterSprintIntervalSec.get(); }
    public static double topmouthCulterTurnIntervalSec() { return cfg().topmouthCulterTurnIntervalSec.get(); }
    public static double topmouthCulterFakeOutChance() { return cfg().topmouthCulterFakeOutChance.get(); }
    public static double topmouthCulterFatigueThreshold() { return cfg().topmouthCulterFatigueThreshold.get(); }
    public static double topmouthCulterFatigueDurationSec() { return cfg().topmouthCulterFatigueDurationSec.get(); }
    public static double topmouthCulterStaminaRegen() { return cfg().topmouthCulterStaminaRegen.get(); }
    public static double zanderMinKg() { return cfg().zanderMinKg.get(); }
    public static double zanderMaxKg() { return cfg().zanderMaxKg.get(); }
    public static double zanderPullSpeed() { return cfg().zanderPullSpeed.get(); }
    public static double zanderFatiguedPullSpeed() { return cfg().zanderFatiguedPullSpeed.get(); }
    public static double zanderStamina() { return cfg().zanderStamina.get(); }
    public static double zanderTensionGain() { return cfg().zanderTensionGain.get(); }
    public static double zanderSprintDurationSec() { return cfg().zanderSprintDurationSec.get(); }
    public static double zanderSprintIntervalSec() { return cfg().zanderSprintIntervalSec.get(); }
    public static double zanderTurnIntervalSec() { return cfg().zanderTurnIntervalSec.get(); }
    public static double zanderFakeOutChance() { return cfg().zanderFakeOutChance.get(); }
    public static double zanderFatigueThreshold() { return cfg().zanderFatigueThreshold.get(); }
    public static double zanderFatigueDurationSec() { return cfg().zanderFatigueDurationSec.get(); }
    public static double zanderStaminaRegen() { return cfg().zanderStaminaRegen.get(); }
    public static double sapphireSnakeheadMinKg() { return cfg().sapphireSnakeheadMinKg.get(); }
    public static double sapphireSnakeheadMaxKg() { return cfg().sapphireSnakeheadMaxKg.get(); }
    public static double sapphireSnakeheadPullSpeed() { return cfg().sapphireSnakeheadPullSpeed.get(); }
    public static double sapphireSnakeheadFatiguedPullSpeed() { return cfg().sapphireSnakeheadFatiguedPullSpeed.get(); }
    public static double sapphireSnakeheadStamina() { return cfg().sapphireSnakeheadStamina.get(); }
    public static double sapphireSnakeheadTensionGain() { return cfg().sapphireSnakeheadTensionGain.get(); }
    public static double sapphireSnakeheadSprintDurationSec() { return cfg().sapphireSnakeheadSprintDurationSec.get(); }
    public static double sapphireSnakeheadSprintIntervalSec() { return cfg().sapphireSnakeheadSprintIntervalSec.get(); }
    public static double sapphireSnakeheadTurnIntervalSec() { return cfg().sapphireSnakeheadTurnIntervalSec.get(); }
    public static double sapphireSnakeheadFakeOutChance() { return cfg().sapphireSnakeheadFakeOutChance.get(); }
    public static double sapphireSnakeheadFatigueThreshold() { return cfg().sapphireSnakeheadFatigueThreshold.get(); }
    public static double sapphireSnakeheadFatigueDurationSec() { return cfg().sapphireSnakeheadFatigueDurationSec.get(); }
    public static double sapphireSnakeheadStaminaRegen() { return cfg().sapphireSnakeheadStaminaRegen.get(); }
    public static double platinumArowanaMinKg() { return cfg().platinumArowanaMinKg.get(); }
    public static double platinumArowanaMaxKg() { return cfg().platinumArowanaMaxKg.get(); }
    public static double platinumArowanaPullSpeed() { return cfg().platinumArowanaPullSpeed.get(); }
    public static double platinumArowanaFatiguedPullSpeed() { return cfg().platinumArowanaFatiguedPullSpeed.get(); }
    public static double platinumArowanaStamina() { return cfg().platinumArowanaStamina.get(); }
    public static double platinumArowanaTensionGain() { return cfg().platinumArowanaTensionGain.get(); }
    public static double platinumArowanaSprintDurationSec() { return cfg().platinumArowanaSprintDurationSec.get(); }
    public static double platinumArowanaSprintIntervalSec() { return cfg().platinumArowanaSprintIntervalSec.get(); }
    public static double platinumArowanaTurnIntervalSec() { return cfg().platinumArowanaTurnIntervalSec.get(); }
    public static double platinumArowanaFakeOutChance() { return cfg().platinumArowanaFakeOutChance.get(); }
    public static double platinumArowanaFatigueThreshold() { return cfg().platinumArowanaFatigueThreshold.get(); }
    public static double platinumArowanaFatigueDurationSec() { return cfg().platinumArowanaFatigueDurationSec.get(); }
    public static double platinumArowanaStaminaRegen() { return cfg().platinumArowanaStaminaRegen.get(); }
    public static double oxeyeTarponMinKg() { return cfg().oxeyeTarponMinKg.get(); }
    public static double oxeyeTarponMaxKg() { return cfg().oxeyeTarponMaxKg.get(); }
    public static double oxeyeTarponPullSpeed() { return cfg().oxeyeTarponPullSpeed.get(); }
    public static double oxeyeTarponFatiguedPullSpeed() { return cfg().oxeyeTarponFatiguedPullSpeed.get(); }
    public static double oxeyeTarponStamina() { return cfg().oxeyeTarponStamina.get(); }
    public static double oxeyeTarponTensionGain() { return cfg().oxeyeTarponTensionGain.get(); }
    public static double oxeyeTarponSprintDurationSec() { return cfg().oxeyeTarponSprintDurationSec.get(); }
    public static double oxeyeTarponSprintIntervalSec() { return cfg().oxeyeTarponSprintIntervalSec.get(); }
    public static double oxeyeTarponTurnIntervalSec() { return cfg().oxeyeTarponTurnIntervalSec.get(); }
    public static double oxeyeTarponFakeOutChance() { return cfg().oxeyeTarponFakeOutChance.get(); }
    public static double oxeyeTarponFatigueThreshold() { return cfg().oxeyeTarponFatigueThreshold.get(); }
    public static double oxeyeTarponFatigueDurationSec() { return cfg().oxeyeTarponFatigueDurationSec.get(); }
    public static double oxeyeTarponStaminaRegen() { return cfg().oxeyeTarponStaminaRegen.get(); }
    public static double atlanticSalmonMinKg() { return cfg().atlanticSalmonMinKg.get(); }
    public static double atlanticSalmonMaxKg() { return cfg().atlanticSalmonMaxKg.get(); }
    public static double atlanticSalmonPullSpeed() { return cfg().atlanticSalmonPullSpeed.get(); }
    public static double atlanticSalmonFatiguedPullSpeed() { return cfg().atlanticSalmonFatiguedPullSpeed.get(); }
    public static double atlanticSalmonStamina() { return cfg().atlanticSalmonStamina.get(); }
    public static double atlanticSalmonTensionGain() { return cfg().atlanticSalmonTensionGain.get(); }
    public static double atlanticSalmonSprintDurationSec() { return cfg().atlanticSalmonSprintDurationSec.get(); }
    public static double atlanticSalmonSprintIntervalSec() { return cfg().atlanticSalmonSprintIntervalSec.get(); }
    public static double atlanticSalmonTurnIntervalSec() { return cfg().atlanticSalmonTurnIntervalSec.get(); }
    public static double atlanticSalmonFakeOutChance() { return cfg().atlanticSalmonFakeOutChance.get(); }
    public static double atlanticSalmonFatigueThreshold() { return cfg().atlanticSalmonFatigueThreshold.get(); }
    public static double atlanticSalmonFatigueDurationSec() { return cfg().atlanticSalmonFatigueDurationSec.get(); }
    public static double atlanticSalmonStaminaRegen() { return cfg().atlanticSalmonStaminaRegen.get(); }
    public static double bluefinTunaMinKg() { return cfg().bluefinTunaMinKg.get(); }
    public static double bluefinTunaMaxKg() { return cfg().bluefinTunaMaxKg.get(); }
    public static double bluefinTunaPullSpeed() { return cfg().bluefinTunaPullSpeed.get(); }
    public static double bluefinTunaFatiguedPullSpeed() { return cfg().bluefinTunaFatiguedPullSpeed.get(); }
    public static double bluefinTunaStamina() { return cfg().bluefinTunaStamina.get(); }
    public static double bluefinTunaTensionGain() { return cfg().bluefinTunaTensionGain.get(); }
    public static double bluefinTunaSprintDurationSec() { return cfg().bluefinTunaSprintDurationSec.get(); }
    public static double bluefinTunaSprintIntervalSec() { return cfg().bluefinTunaSprintIntervalSec.get(); }
    public static double bluefinTunaTurnIntervalSec() { return cfg().bluefinTunaTurnIntervalSec.get(); }
    public static double bluefinTunaFakeOutChance() { return cfg().bluefinTunaFakeOutChance.get(); }
    public static double bluefinTunaFatigueThreshold() { return cfg().bluefinTunaFatigueThreshold.get(); }
    public static double bluefinTunaFatigueDurationSec() { return cfg().bluefinTunaFatigueDurationSec.get(); }
    public static double bluefinTunaStaminaRegen() { return cfg().bluefinTunaStaminaRegen.get(); }
    public static double juvenileSharkMinKg() { return cfg().juvenileSharkMinKg.get(); }
    public static double juvenileSharkMaxKg() { return cfg().juvenileSharkMaxKg.get(); }
    public static double juvenileSharkPullSpeed() { return cfg().juvenileSharkPullSpeed.get(); }
    public static double juvenileSharkFatiguedPullSpeed() { return cfg().juvenileSharkFatiguedPullSpeed.get(); }
    public static double juvenileSharkStamina() { return cfg().juvenileSharkStamina.get(); }
    public static double juvenileSharkTensionGain() { return cfg().juvenileSharkTensionGain.get(); }
    public static double juvenileSharkSprintDurationSec() { return cfg().juvenileSharkSprintDurationSec.get(); }
    public static double juvenileSharkSprintIntervalSec() { return cfg().juvenileSharkSprintIntervalSec.get(); }
    public static double juvenileSharkTurnIntervalSec() { return cfg().juvenileSharkTurnIntervalSec.get(); }
    public static double juvenileSharkFakeOutChance() { return cfg().juvenileSharkFakeOutChance.get(); }
    public static double juvenileSharkFatigueThreshold() { return cfg().juvenileSharkFatigueThreshold.get(); }
    public static double juvenileSharkFatigueDurationSec() { return cfg().juvenileSharkFatigueDurationSec.get(); }
    public static double juvenileSharkStaminaRegen() { return cfg().juvenileSharkStaminaRegen.get(); }
    public static double tigerfishMinKg() { return cfg().tigerfishMinKg.get(); }
    public static double tigerfishMaxKg() { return cfg().tigerfishMaxKg.get(); }
    public static double tigerfishPullSpeed() { return cfg().tigerfishPullSpeed.get(); }
    public static double tigerfishFatiguedPullSpeed() { return cfg().tigerfishFatiguedPullSpeed.get(); }
    public static double tigerfishStamina() { return cfg().tigerfishStamina.get(); }
    public static double tigerfishTensionGain() { return cfg().tigerfishTensionGain.get(); }
    public static double tigerfishSprintDurationSec() { return cfg().tigerfishSprintDurationSec.get(); }
    public static double tigerfishSprintIntervalSec() { return cfg().tigerfishSprintIntervalSec.get(); }
    public static double tigerfishTurnIntervalSec() { return cfg().tigerfishTurnIntervalSec.get(); }
    public static double tigerfishFakeOutChance() { return cfg().tigerfishFakeOutChance.get(); }
    public static double tigerfishFatigueThreshold() { return cfg().tigerfishFatigueThreshold.get(); }
    public static double tigerfishFatigueDurationSec() { return cfg().tigerfishFatigueDurationSec.get(); }
    public static double tigerfishStaminaRegen() { return cfg().tigerfishStaminaRegen.get(); }
    public static double pikeMinKg() { return cfg().pikeMinKg.get(); }
    public static double pikeMaxKg() { return cfg().pikeMaxKg.get(); }
    public static double pikePullSpeed() { return cfg().pikePullSpeed.get(); }
    public static double pikeFatiguedPullSpeed() { return cfg().pikeFatiguedPullSpeed.get(); }
    public static double pikeStamina() { return cfg().pikeStamina.get(); }
    public static double pikeTensionGain() { return cfg().pikeTensionGain.get(); }
    public static double pikeSprintDurationSec() { return cfg().pikeSprintDurationSec.get(); }
    public static double pikeSprintIntervalSec() { return cfg().pikeSprintIntervalSec.get(); }
    public static double pikeTurnIntervalSec() { return cfg().pikeTurnIntervalSec.get(); }
    public static double pikeFakeOutChance() { return cfg().pikeFakeOutChance.get(); }
    public static double pikeFatigueThreshold() { return cfg().pikeFatigueThreshold.get(); }
    public static double pikeFatigueDurationSec() { return cfg().pikeFatigueDurationSec.get(); }
    public static double pikeStaminaRegen() { return cfg().pikeStaminaRegen.get(); }
    public static double brownTroutMinKg() { return cfg().brownTroutMinKg.get(); }
    public static double brownTroutMaxKg() { return cfg().brownTroutMaxKg.get(); }
    public static double brownTroutPullSpeed() { return cfg().brownTroutPullSpeed.get(); }
    public static double brownTroutFatiguedPullSpeed() { return cfg().brownTroutFatiguedPullSpeed.get(); }
    public static double brownTroutStamina() { return cfg().brownTroutStamina.get(); }
    public static double brownTroutTensionGain() { return cfg().brownTroutTensionGain.get(); }
    public static double brownTroutSprintDurationSec() { return cfg().brownTroutSprintDurationSec.get(); }
    public static double brownTroutSprintIntervalSec() { return cfg().brownTroutSprintIntervalSec.get(); }
    public static double brownTroutTurnIntervalSec() { return cfg().brownTroutTurnIntervalSec.get(); }
    public static double brownTroutFakeOutChance() { return cfg().brownTroutFakeOutChance.get(); }
    public static double brownTroutFatigueThreshold() { return cfg().brownTroutFatigueThreshold.get(); }
    public static double brownTroutFatigueDurationSec() { return cfg().brownTroutFatigueDurationSec.get(); }
    public static double brownTroutStaminaRegen() { return cfg().brownTroutStaminaRegen.get(); }
    public static double mirrorCarpMinKg() { return cfg().mirrorCarpMinKg.get(); }
    public static double mirrorCarpMaxKg() { return cfg().mirrorCarpMaxKg.get(); }
    public static double mirrorCarpPullSpeed() { return cfg().mirrorCarpPullSpeed.get(); }
    public static double mirrorCarpFatiguedPullSpeed() { return cfg().mirrorCarpFatiguedPullSpeed.get(); }
    public static double mirrorCarpStamina() { return cfg().mirrorCarpStamina.get(); }
    public static double mirrorCarpTensionGain() { return cfg().mirrorCarpTensionGain.get(); }
    public static double mirrorCarpSprintDurationSec() { return cfg().mirrorCarpSprintDurationSec.get(); }
    public static double mirrorCarpSprintIntervalSec() { return cfg().mirrorCarpSprintIntervalSec.get(); }
    public static double mirrorCarpTurnIntervalSec() { return cfg().mirrorCarpTurnIntervalSec.get(); }
    public static double mirrorCarpFakeOutChance() { return cfg().mirrorCarpFakeOutChance.get(); }
    public static double mirrorCarpFatigueThreshold() { return cfg().mirrorCarpFatigueThreshold.get(); }
    public static double mirrorCarpFatigueDurationSec() { return cfg().mirrorCarpFatigueDurationSec.get(); }
    public static double mirrorCarpStaminaRegen() { return cfg().mirrorCarpStaminaRegen.get(); }
    public static double stripedBassMinKg() { return cfg().stripedBassMinKg.get(); }
    public static double stripedBassMaxKg() { return cfg().stripedBassMaxKg.get(); }
    public static double stripedBassPullSpeed() { return cfg().stripedBassPullSpeed.get(); }
    public static double stripedBassFatiguedPullSpeed() { return cfg().stripedBassFatiguedPullSpeed.get(); }
    public static double stripedBassStamina() { return cfg().stripedBassStamina.get(); }
    public static double stripedBassTensionGain() { return cfg().stripedBassTensionGain.get(); }
    public static double stripedBassSprintDurationSec() { return cfg().stripedBassSprintDurationSec.get(); }
    public static double stripedBassSprintIntervalSec() { return cfg().stripedBassSprintIntervalSec.get(); }
    public static double stripedBassTurnIntervalSec() { return cfg().stripedBassTurnIntervalSec.get(); }
    public static double stripedBassFakeOutChance() { return cfg().stripedBassFakeOutChance.get(); }
    public static double stripedBassFatigueThreshold() { return cfg().stripedBassFatigueThreshold.get(); }
    public static double stripedBassFatigueDurationSec() { return cfg().stripedBassFatigueDurationSec.get(); }
    public static double stripedBassStaminaRegen() { return cfg().stripedBassStaminaRegen.get(); }
    public static double arapaimaMinKg() { return cfg().arapaimaMinKg.get(); }
    public static double arapaimaMaxKg() { return cfg().arapaimaMaxKg.get(); }
    public static double arapaimaPullSpeed() { return cfg().arapaimaPullSpeed.get(); }
    public static double arapaimaFatiguedPullSpeed() { return cfg().arapaimaFatiguedPullSpeed.get(); }
    public static double arapaimaStamina() { return cfg().arapaimaStamina.get(); }
    public static double arapaimaTensionGain() { return cfg().arapaimaTensionGain.get(); }
    public static double arapaimaSprintDurationSec() { return cfg().arapaimaSprintDurationSec.get(); }
    public static double arapaimaSprintIntervalSec() { return cfg().arapaimaSprintIntervalSec.get(); }
    public static double arapaimaTurnIntervalSec() { return cfg().arapaimaTurnIntervalSec.get(); }
    public static double arapaimaFakeOutChance() { return cfg().arapaimaFakeOutChance.get(); }
    public static double arapaimaFatigueThreshold() { return cfg().arapaimaFatigueThreshold.get(); }
    public static double arapaimaFatigueDurationSec() { return cfg().arapaimaFatigueDurationSec.get(); }
    public static double arapaimaStaminaRegen() { return cfg().arapaimaStaminaRegen.get(); }
    public static double africanButterflyfishMinKg() { return cfg().africanButterflyfishMinKg.get(); }
    public static double africanButterflyfishMaxKg() { return cfg().africanButterflyfishMaxKg.get(); }
    public static double africanButterflyfishPullSpeed() { return cfg().africanButterflyfishPullSpeed.get(); }
    public static double africanButterflyfishFatiguedPullSpeed() { return cfg().africanButterflyfishFatiguedPullSpeed.get(); }
    public static double africanButterflyfishStamina() { return cfg().africanButterflyfishStamina.get(); }
    public static double africanButterflyfishTensionGain() { return cfg().africanButterflyfishTensionGain.get(); }
    public static double africanButterflyfishSprintDurationSec() { return cfg().africanButterflyfishSprintDurationSec.get(); }
    public static double africanButterflyfishSprintIntervalSec() { return cfg().africanButterflyfishSprintIntervalSec.get(); }
    public static double africanButterflyfishTurnIntervalSec() { return cfg().africanButterflyfishTurnIntervalSec.get(); }
    public static double africanButterflyfishFakeOutChance() { return cfg().africanButterflyfishFakeOutChance.get(); }
    public static double africanButterflyfishFatigueThreshold() { return cfg().africanButterflyfishFatigueThreshold.get(); }
    public static double africanButterflyfishFatigueDurationSec() { return cfg().africanButterflyfishFatigueDurationSec.get(); }
    public static double africanButterflyfishStaminaRegen() { return cfg().africanButterflyfishStaminaRegen.get(); }
    public static double jewelCichlidMinKg() { return cfg().jewelCichlidMinKg.get(); }
    public static double jewelCichlidMaxKg() { return cfg().jewelCichlidMaxKg.get(); }
    public static double jewelCichlidPullSpeed() { return cfg().jewelCichlidPullSpeed.get(); }
    public static double jewelCichlidFatiguedPullSpeed() { return cfg().jewelCichlidFatiguedPullSpeed.get(); }
    public static double jewelCichlidStamina() { return cfg().jewelCichlidStamina.get(); }
    public static double jewelCichlidTensionGain() { return cfg().jewelCichlidTensionGain.get(); }
    public static double jewelCichlidSprintDurationSec() { return cfg().jewelCichlidSprintDurationSec.get(); }
    public static double jewelCichlidSprintIntervalSec() { return cfg().jewelCichlidSprintIntervalSec.get(); }
    public static double jewelCichlidTurnIntervalSec() { return cfg().jewelCichlidTurnIntervalSec.get(); }
    public static double jewelCichlidFakeOutChance() { return cfg().jewelCichlidFakeOutChance.get(); }
    public static double jewelCichlidFatigueThreshold() { return cfg().jewelCichlidFatigueThreshold.get(); }
    public static double jewelCichlidFatigueDurationSec() { return cfg().jewelCichlidFatigueDurationSec.get(); }
    public static double jewelCichlidStaminaRegen() { return cfg().jewelCichlidStaminaRegen.get(); }
    public static double guppyMinKg() { return cfg().guppyMinKg.get(); }
    public static double guppyMaxKg() { return cfg().guppyMaxKg.get(); }
    public static double guppyPullSpeed() { return cfg().guppyPullSpeed.get(); }
    public static double guppyFatiguedPullSpeed() { return cfg().guppyFatiguedPullSpeed.get(); }
    public static double guppyStamina() { return cfg().guppyStamina.get(); }
    public static double guppyTensionGain() { return cfg().guppyTensionGain.get(); }
    public static double guppySprintDurationSec() { return cfg().guppySprintDurationSec.get(); }
    public static double guppySprintIntervalSec() { return cfg().guppySprintIntervalSec.get(); }
    public static double guppyTurnIntervalSec() { return cfg().guppyTurnIntervalSec.get(); }
    public static double guppyFakeOutChance() { return cfg().guppyFakeOutChance.get(); }
    public static double guppyFatigueThreshold() { return cfg().guppyFatigueThreshold.get(); }
    public static double guppyFatigueDurationSec() { return cfg().guppyFatigueDurationSec.get(); }
    public static double guppyStaminaRegen() { return cfg().guppyStaminaRegen.get(); }
    public static double elephantnoseMinKg() { return cfg().elephantnoseMinKg.get(); }
    public static double elephantnoseMaxKg() { return cfg().elephantnoseMaxKg.get(); }
    public static double elephantnosePullSpeed() { return cfg().elephantnosePullSpeed.get(); }
    public static double elephantnoseFatiguedPullSpeed() { return cfg().elephantnoseFatiguedPullSpeed.get(); }
    public static double elephantnoseStamina() { return cfg().elephantnoseStamina.get(); }
    public static double elephantnoseTensionGain() { return cfg().elephantnoseTensionGain.get(); }
    public static double elephantnoseSprintDurationSec() { return cfg().elephantnoseSprintDurationSec.get(); }
    public static double elephantnoseSprintIntervalSec() { return cfg().elephantnoseSprintIntervalSec.get(); }
    public static double elephantnoseTurnIntervalSec() { return cfg().elephantnoseTurnIntervalSec.get(); }
    public static double elephantnoseFakeOutChance() { return cfg().elephantnoseFakeOutChance.get(); }
    public static double elephantnoseFatigueThreshold() { return cfg().elephantnoseFatigueThreshold.get(); }
    public static double elephantnoseFatigueDurationSec() { return cfg().elephantnoseFatigueDurationSec.get(); }
    public static double elephantnoseStaminaRegen() { return cfg().elephantnoseStaminaRegen.get(); }
    public static double goldenLoachMinKg() { return cfg().goldenLoachMinKg.get(); }
    public static double goldenLoachMaxKg() { return cfg().goldenLoachMaxKg.get(); }
    public static double goldenLoachPullSpeed() { return cfg().goldenLoachPullSpeed.get(); }
    public static double goldenLoachFatiguedPullSpeed() { return cfg().goldenLoachFatiguedPullSpeed.get(); }
    public static double goldenLoachStamina() { return cfg().goldenLoachStamina.get(); }
    public static double goldenLoachTensionGain() { return cfg().goldenLoachTensionGain.get(); }
    public static double goldenLoachSprintDurationSec() { return cfg().goldenLoachSprintDurationSec.get(); }
    public static double goldenLoachSprintIntervalSec() { return cfg().goldenLoachSprintIntervalSec.get(); }
    public static double goldenLoachTurnIntervalSec() { return cfg().goldenLoachTurnIntervalSec.get(); }
    public static double goldenLoachFakeOutChance() { return cfg().goldenLoachFakeOutChance.get(); }
    public static double goldenLoachFatigueThreshold() { return cfg().goldenLoachFatigueThreshold.get(); }
    public static double goldenLoachFatigueDurationSec() { return cfg().goldenLoachFatigueDurationSec.get(); }
    public static double goldenLoachStaminaRegen() { return cfg().goldenLoachStaminaRegen.get(); }
    public static double caveBlindfishMinKg() { return cfg().caveBlindfishMinKg.get(); }
    public static double caveBlindfishMaxKg() { return cfg().caveBlindfishMaxKg.get(); }
    public static double caveBlindfishPullSpeed() { return cfg().caveBlindfishPullSpeed.get(); }
    public static double caveBlindfishFatiguedPullSpeed() { return cfg().caveBlindfishFatiguedPullSpeed.get(); }
    public static double caveBlindfishStamina() { return cfg().caveBlindfishStamina.get(); }
    public static double caveBlindfishTensionGain() { return cfg().caveBlindfishTensionGain.get(); }
    public static double caveBlindfishSprintDurationSec() { return cfg().caveBlindfishSprintDurationSec.get(); }
    public static double caveBlindfishSprintIntervalSec() { return cfg().caveBlindfishSprintIntervalSec.get(); }
    public static double caveBlindfishTurnIntervalSec() { return cfg().caveBlindfishTurnIntervalSec.get(); }
    public static double caveBlindfishFakeOutChance() { return cfg().caveBlindfishFakeOutChance.get(); }
    public static double caveBlindfishFatigueThreshold() { return cfg().caveBlindfishFatigueThreshold.get(); }
    public static double caveBlindfishFatigueDurationSec() { return cfg().caveBlindfishFatigueDurationSec.get(); }
    public static double caveBlindfishStaminaRegen() { return cfg().caveBlindfishStaminaRegen.get(); }
    public static double northAfricanCatfishMinKg() { return cfg().northAfricanCatfishMinKg.get(); }
    public static double northAfricanCatfishMaxKg() { return cfg().northAfricanCatfishMaxKg.get(); }
    public static double northAfricanCatfishPullSpeed() { return cfg().northAfricanCatfishPullSpeed.get(); }
    public static double northAfricanCatfishFatiguedPullSpeed() { return cfg().northAfricanCatfishFatiguedPullSpeed.get(); }
    public static double northAfricanCatfishStamina() { return cfg().northAfricanCatfishStamina.get(); }
    public static double northAfricanCatfishTensionGain() { return cfg().northAfricanCatfishTensionGain.get(); }
    public static double northAfricanCatfishSprintDurationSec() { return cfg().northAfricanCatfishSprintDurationSec.get(); }
    public static double northAfricanCatfishSprintIntervalSec() { return cfg().northAfricanCatfishSprintIntervalSec.get(); }
    public static double northAfricanCatfishTurnIntervalSec() { return cfg().northAfricanCatfishTurnIntervalSec.get(); }
    public static double northAfricanCatfishFakeOutChance() { return cfg().northAfricanCatfishFakeOutChance.get(); }
    public static double northAfricanCatfishFatigueThreshold() { return cfg().northAfricanCatfishFatigueThreshold.get(); }
    public static double northAfricanCatfishFatigueDurationSec() { return cfg().northAfricanCatfishFatigueDurationSec.get(); }
    public static double northAfricanCatfishStaminaRegen() { return cfg().northAfricanCatfishStaminaRegen.get(); }
    public static double rainbowTroutMinKg() { return cfg().rainbowTroutMinKg.get(); }
    public static double rainbowTroutMaxKg() { return cfg().rainbowTroutMaxKg.get(); }
    public static double rainbowTroutPullSpeed() { return cfg().rainbowTroutPullSpeed.get(); }
    public static double rainbowTroutFatiguedPullSpeed() { return cfg().rainbowTroutFatiguedPullSpeed.get(); }
    public static double rainbowTroutStamina() { return cfg().rainbowTroutStamina.get(); }
    public static double rainbowTroutTensionGain() { return cfg().rainbowTroutTensionGain.get(); }
    public static double rainbowTroutSprintDurationSec() { return cfg().rainbowTroutSprintDurationSec.get(); }
    public static double rainbowTroutSprintIntervalSec() { return cfg().rainbowTroutSprintIntervalSec.get(); }
    public static double rainbowTroutTurnIntervalSec() { return cfg().rainbowTroutTurnIntervalSec.get(); }
    public static double rainbowTroutFakeOutChance() { return cfg().rainbowTroutFakeOutChance.get(); }
    public static double rainbowTroutFatigueThreshold() { return cfg().rainbowTroutFatigueThreshold.get(); }
    public static double rainbowTroutFatigueDurationSec() { return cfg().rainbowTroutFatigueDurationSec.get(); }
    public static double rainbowTroutStaminaRegen() { return cfg().rainbowTroutStaminaRegen.get(); }
    public static double arcticCharMinKg() { return cfg().arcticCharMinKg.get(); }
    public static double arcticCharMaxKg() { return cfg().arcticCharMaxKg.get(); }
    public static double arcticCharPullSpeed() { return cfg().arcticCharPullSpeed.get(); }
    public static double arcticCharFatiguedPullSpeed() { return cfg().arcticCharFatiguedPullSpeed.get(); }
    public static double arcticCharStamina() { return cfg().arcticCharStamina.get(); }
    public static double arcticCharTensionGain() { return cfg().arcticCharTensionGain.get(); }
    public static double arcticCharSprintDurationSec() { return cfg().arcticCharSprintDurationSec.get(); }
    public static double arcticCharSprintIntervalSec() { return cfg().arcticCharSprintIntervalSec.get(); }
    public static double arcticCharTurnIntervalSec() { return cfg().arcticCharTurnIntervalSec.get(); }
    public static double arcticCharFakeOutChance() { return cfg().arcticCharFakeOutChance.get(); }
    public static double arcticCharFatigueThreshold() { return cfg().arcticCharFatigueThreshold.get(); }
    public static double arcticCharFatigueDurationSec() { return cfg().arcticCharFatigueDurationSec.get(); }
    public static double arcticCharStaminaRegen() { return cfg().arcticCharStaminaRegen.get(); }
    public static double burbotMinKg() { return cfg().burbotMinKg.get(); }
    public static double burbotMaxKg() { return cfg().burbotMaxKg.get(); }
    public static double burbotPullSpeed() { return cfg().burbotPullSpeed.get(); }
    public static double burbotFatiguedPullSpeed() { return cfg().burbotFatiguedPullSpeed.get(); }
    public static double burbotStamina() { return cfg().burbotStamina.get(); }
    public static double burbotTensionGain() { return cfg().burbotTensionGain.get(); }
    public static double burbotSprintDurationSec() { return cfg().burbotSprintDurationSec.get(); }
    public static double burbotSprintIntervalSec() { return cfg().burbotSprintIntervalSec.get(); }
    public static double burbotTurnIntervalSec() { return cfg().burbotTurnIntervalSec.get(); }
    public static double burbotFakeOutChance() { return cfg().burbotFakeOutChance.get(); }
    public static double burbotFatigueThreshold() { return cfg().burbotFatigueThreshold.get(); }
    public static double burbotFatigueDurationSec() { return cfg().burbotFatigueDurationSec.get(); }
    public static double burbotStaminaRegen() { return cfg().burbotStaminaRegen.get(); }
    public static double whitefishMinKg() { return cfg().whitefishMinKg.get(); }
    public static double whitefishMaxKg() { return cfg().whitefishMaxKg.get(); }
    public static double whitefishPullSpeed() { return cfg().whitefishPullSpeed.get(); }
    public static double whitefishFatiguedPullSpeed() { return cfg().whitefishFatiguedPullSpeed.get(); }
    public static double whitefishStamina() { return cfg().whitefishStamina.get(); }
    public static double whitefishTensionGain() { return cfg().whitefishTensionGain.get(); }
    public static double whitefishSprintDurationSec() { return cfg().whitefishSprintDurationSec.get(); }
    public static double whitefishSprintIntervalSec() { return cfg().whitefishSprintIntervalSec.get(); }
    public static double whitefishTurnIntervalSec() { return cfg().whitefishTurnIntervalSec.get(); }
    public static double whitefishFakeOutChance() { return cfg().whitefishFakeOutChance.get(); }
    public static double whitefishFatigueThreshold() { return cfg().whitefishFatigueThreshold.get(); }
    public static double whitefishFatigueDurationSec() { return cfg().whitefishFatigueDurationSec.get(); }
    public static double whitefishStaminaRegen() { return cfg().whitefishStaminaRegen.get(); }
    public static double taimenMinKg() { return cfg().taimenMinKg.get(); }
    public static double taimenMaxKg() { return cfg().taimenMaxKg.get(); }
    public static double taimenPullSpeed() { return cfg().taimenPullSpeed.get(); }
    public static double taimenFatiguedPullSpeed() { return cfg().taimenFatiguedPullSpeed.get(); }
    public static double taimenStamina() { return cfg().taimenStamina.get(); }
    public static double taimenTensionGain() { return cfg().taimenTensionGain.get(); }
    public static double taimenSprintDurationSec() { return cfg().taimenSprintDurationSec.get(); }
    public static double taimenSprintIntervalSec() { return cfg().taimenSprintIntervalSec.get(); }
    public static double taimenTurnIntervalSec() { return cfg().taimenTurnIntervalSec.get(); }
    public static double taimenFakeOutChance() { return cfg().taimenFakeOutChance.get(); }
    public static double taimenFatigueThreshold() { return cfg().taimenFatigueThreshold.get(); }
    public static double taimenFatigueDurationSec() { return cfg().taimenFatigueDurationSec.get(); }
    public static double taimenStaminaRegen() { return cfg().taimenStaminaRegen.get(); }
    public static double arcticCodMinKg() { return cfg().arcticCodMinKg.get(); }
    public static double arcticCodMaxKg() { return cfg().arcticCodMaxKg.get(); }
    public static double arcticCodPullSpeed() { return cfg().arcticCodPullSpeed.get(); }
    public static double arcticCodFatiguedPullSpeed() { return cfg().arcticCodFatiguedPullSpeed.get(); }
    public static double arcticCodStamina() { return cfg().arcticCodStamina.get(); }
    public static double arcticCodTensionGain() { return cfg().arcticCodTensionGain.get(); }
    public static double arcticCodSprintDurationSec() { return cfg().arcticCodSprintDurationSec.get(); }
    public static double arcticCodSprintIntervalSec() { return cfg().arcticCodSprintIntervalSec.get(); }
    public static double arcticCodTurnIntervalSec() { return cfg().arcticCodTurnIntervalSec.get(); }
    public static double arcticCodFakeOutChance() { return cfg().arcticCodFakeOutChance.get(); }
    public static double arcticCodFatigueThreshold() { return cfg().arcticCodFatigueThreshold.get(); }
    public static double arcticCodFatigueDurationSec() { return cfg().arcticCodFatigueDurationSec.get(); }
    public static double arcticCodStaminaRegen() { return cfg().arcticCodStaminaRegen.get(); }
    public static double capelinMinKg() { return cfg().capelinMinKg.get(); }
    public static double capelinMaxKg() { return cfg().capelinMaxKg.get(); }
    public static double capelinPullSpeed() { return cfg().capelinPullSpeed.get(); }
    public static double capelinFatiguedPullSpeed() { return cfg().capelinFatiguedPullSpeed.get(); }
    public static double capelinStamina() { return cfg().capelinStamina.get(); }
    public static double capelinTensionGain() { return cfg().capelinTensionGain.get(); }
    public static double capelinSprintDurationSec() { return cfg().capelinSprintDurationSec.get(); }
    public static double capelinSprintIntervalSec() { return cfg().capelinSprintIntervalSec.get(); }
    public static double capelinTurnIntervalSec() { return cfg().capelinTurnIntervalSec.get(); }
    public static double capelinFakeOutChance() { return cfg().capelinFakeOutChance.get(); }
    public static double capelinFatigueThreshold() { return cfg().capelinFatigueThreshold.get(); }
    public static double capelinFatigueDurationSec() { return cfg().capelinFatigueDurationSec.get(); }
    public static double capelinStaminaRegen() { return cfg().capelinStaminaRegen.get(); }
    public static double atlanticHerringMinKg() { return cfg().atlanticHerringMinKg.get(); }
    public static double atlanticHerringMaxKg() { return cfg().atlanticHerringMaxKg.get(); }
    public static double atlanticHerringPullSpeed() { return cfg().atlanticHerringPullSpeed.get(); }
    public static double atlanticHerringFatiguedPullSpeed() { return cfg().atlanticHerringFatiguedPullSpeed.get(); }
    public static double atlanticHerringStamina() { return cfg().atlanticHerringStamina.get(); }
    public static double atlanticHerringTensionGain() { return cfg().atlanticHerringTensionGain.get(); }
    public static double atlanticHerringSprintDurationSec() { return cfg().atlanticHerringSprintDurationSec.get(); }
    public static double atlanticHerringSprintIntervalSec() { return cfg().atlanticHerringSprintIntervalSec.get(); }
    public static double atlanticHerringTurnIntervalSec() { return cfg().atlanticHerringTurnIntervalSec.get(); }
    public static double atlanticHerringFakeOutChance() { return cfg().atlanticHerringFakeOutChance.get(); }
    public static double atlanticHerringFatigueThreshold() { return cfg().atlanticHerringFatigueThreshold.get(); }
    public static double atlanticHerringFatigueDurationSec() { return cfg().atlanticHerringFatigueDurationSec.get(); }
    public static double atlanticHerringStaminaRegen() { return cfg().atlanticHerringStaminaRegen.get(); }
    public static double wolffishMinKg() { return cfg().wolffishMinKg.get(); }
    public static double wolffishMaxKg() { return cfg().wolffishMaxKg.get(); }
    public static double wolffishPullSpeed() { return cfg().wolffishPullSpeed.get(); }
    public static double wolffishFatiguedPullSpeed() { return cfg().wolffishFatiguedPullSpeed.get(); }
    public static double wolffishStamina() { return cfg().wolffishStamina.get(); }
    public static double wolffishTensionGain() { return cfg().wolffishTensionGain.get(); }
    public static double wolffishSprintDurationSec() { return cfg().wolffishSprintDurationSec.get(); }
    public static double wolffishSprintIntervalSec() { return cfg().wolffishSprintIntervalSec.get(); }
    public static double wolffishTurnIntervalSec() { return cfg().wolffishTurnIntervalSec.get(); }
    public static double wolffishFakeOutChance() { return cfg().wolffishFakeOutChance.get(); }
    public static double wolffishFatigueThreshold() { return cfg().wolffishFatigueThreshold.get(); }
    public static double wolffishFatigueDurationSec() { return cfg().wolffishFatigueDurationSec.get(); }
    public static double wolffishStaminaRegen() { return cfg().wolffishStaminaRegen.get(); }
    public static double redfishMinKg() { return cfg().redfishMinKg.get(); }
    public static double redfishMaxKg() { return cfg().redfishMaxKg.get(); }
    public static double redfishPullSpeed() { return cfg().redfishPullSpeed.get(); }
    public static double redfishFatiguedPullSpeed() { return cfg().redfishFatiguedPullSpeed.get(); }
    public static double redfishStamina() { return cfg().redfishStamina.get(); }
    public static double redfishTensionGain() { return cfg().redfishTensionGain.get(); }
    public static double redfishSprintDurationSec() { return cfg().redfishSprintDurationSec.get(); }
    public static double redfishSprintIntervalSec() { return cfg().redfishSprintIntervalSec.get(); }
    public static double redfishTurnIntervalSec() { return cfg().redfishTurnIntervalSec.get(); }
    public static double redfishFakeOutChance() { return cfg().redfishFakeOutChance.get(); }
    public static double redfishFatigueThreshold() { return cfg().redfishFatigueThreshold.get(); }
    public static double redfishFatigueDurationSec() { return cfg().redfishFatigueDurationSec.get(); }
    public static double redfishStaminaRegen() { return cfg().redfishStaminaRegen.get(); }
    public static double sablefishMinKg() { return cfg().sablefishMinKg.get(); }
    public static double sablefishMaxKg() { return cfg().sablefishMaxKg.get(); }
    public static double sablefishPullSpeed() { return cfg().sablefishPullSpeed.get(); }
    public static double sablefishFatiguedPullSpeed() { return cfg().sablefishFatiguedPullSpeed.get(); }
    public static double sablefishStamina() { return cfg().sablefishStamina.get(); }
    public static double sablefishTensionGain() { return cfg().sablefishTensionGain.get(); }
    public static double sablefishSprintDurationSec() { return cfg().sablefishSprintDurationSec.get(); }
    public static double sablefishSprintIntervalSec() { return cfg().sablefishSprintIntervalSec.get(); }
    public static double sablefishTurnIntervalSec() { return cfg().sablefishTurnIntervalSec.get(); }
    public static double sablefishFakeOutChance() { return cfg().sablefishFakeOutChance.get(); }
    public static double sablefishFatigueThreshold() { return cfg().sablefishFatigueThreshold.get(); }
    public static double sablefishFatigueDurationSec() { return cfg().sablefishFatigueDurationSec.get(); }
    public static double sablefishStaminaRegen() { return cfg().sablefishStaminaRegen.get(); }
    public static double atlanticCodMinKg() { return cfg().atlanticCodMinKg.get(); }
    public static double atlanticCodMaxKg() { return cfg().atlanticCodMaxKg.get(); }
    public static double atlanticCodPullSpeed() { return cfg().atlanticCodPullSpeed.get(); }
    public static double atlanticCodFatiguedPullSpeed() { return cfg().atlanticCodFatiguedPullSpeed.get(); }
    public static double atlanticCodStamina() { return cfg().atlanticCodStamina.get(); }
    public static double atlanticCodTensionGain() { return cfg().atlanticCodTensionGain.get(); }
    public static double atlanticCodSprintDurationSec() { return cfg().atlanticCodSprintDurationSec.get(); }
    public static double atlanticCodSprintIntervalSec() { return cfg().atlanticCodSprintIntervalSec.get(); }
    public static double atlanticCodTurnIntervalSec() { return cfg().atlanticCodTurnIntervalSec.get(); }
    public static double atlanticCodFakeOutChance() { return cfg().atlanticCodFakeOutChance.get(); }
    public static double atlanticCodFatigueThreshold() { return cfg().atlanticCodFatigueThreshold.get(); }
    public static double atlanticCodFatigueDurationSec() { return cfg().atlanticCodFatigueDurationSec.get(); }
    public static double atlanticCodStaminaRegen() { return cfg().atlanticCodStaminaRegen.get(); }
    public static double greenlandHalibutMinKg() { return cfg().greenlandHalibutMinKg.get(); }
    public static double greenlandHalibutMaxKg() { return cfg().greenlandHalibutMaxKg.get(); }
    public static double greenlandHalibutPullSpeed() { return cfg().greenlandHalibutPullSpeed.get(); }
    public static double greenlandHalibutFatiguedPullSpeed() { return cfg().greenlandHalibutFatiguedPullSpeed.get(); }
    public static double greenlandHalibutStamina() { return cfg().greenlandHalibutStamina.get(); }
    public static double greenlandHalibutTensionGain() { return cfg().greenlandHalibutTensionGain.get(); }
    public static double greenlandHalibutSprintDurationSec() { return cfg().greenlandHalibutSprintDurationSec.get(); }
    public static double greenlandHalibutSprintIntervalSec() { return cfg().greenlandHalibutSprintIntervalSec.get(); }
    public static double greenlandHalibutTurnIntervalSec() { return cfg().greenlandHalibutTurnIntervalSec.get(); }
    public static double greenlandHalibutFakeOutChance() { return cfg().greenlandHalibutFakeOutChance.get(); }
    public static double greenlandHalibutFatigueThreshold() { return cfg().greenlandHalibutFatigueThreshold.get(); }
    public static double greenlandHalibutFatigueDurationSec() { return cfg().greenlandHalibutFatigueDurationSec.get(); }
    public static double greenlandHalibutStaminaRegen() { return cfg().greenlandHalibutStaminaRegen.get(); }
    public static double greenlandSharkMinKg() { return cfg().greenlandSharkMinKg.get(); }
    public static double greenlandSharkMaxKg() { return cfg().greenlandSharkMaxKg.get(); }
    public static double greenlandSharkPullSpeed() { return cfg().greenlandSharkPullSpeed.get(); }
    public static double greenlandSharkFatiguedPullSpeed() { return cfg().greenlandSharkFatiguedPullSpeed.get(); }
    public static double greenlandSharkStamina() { return cfg().greenlandSharkStamina.get(); }
    public static double greenlandSharkTensionGain() { return cfg().greenlandSharkTensionGain.get(); }
    public static double greenlandSharkSprintDurationSec() { return cfg().greenlandSharkSprintDurationSec.get(); }
    public static double greenlandSharkSprintIntervalSec() { return cfg().greenlandSharkSprintIntervalSec.get(); }
    public static double greenlandSharkTurnIntervalSec() { return cfg().greenlandSharkTurnIntervalSec.get(); }
    public static double greenlandSharkFakeOutChance() { return cfg().greenlandSharkFakeOutChance.get(); }
    public static double greenlandSharkFatigueThreshold() { return cfg().greenlandSharkFatigueThreshold.get(); }
    public static double greenlandSharkFatigueDurationSec() { return cfg().greenlandSharkFatigueDurationSec.get(); }
    public static double greenlandSharkStaminaRegen() { return cfg().greenlandSharkStaminaRegen.get(); }
    public static double sailfishMinKg() { return cfg().sailfishMinKg.get(); }
    public static double sailfishMaxKg() { return cfg().sailfishMaxKg.get(); }
    public static double sailfishPullSpeed() { return cfg().sailfishPullSpeed.get(); }
    public static double sailfishFatiguedPullSpeed() { return cfg().sailfishFatiguedPullSpeed.get(); }
    public static double sailfishStamina() { return cfg().sailfishStamina.get(); }
    public static double sailfishTensionGain() { return cfg().sailfishTensionGain.get(); }
    public static double sailfishSprintDurationSec() { return cfg().sailfishSprintDurationSec.get(); }
    public static double sailfishSprintIntervalSec() { return cfg().sailfishSprintIntervalSec.get(); }
    public static double sailfishTurnIntervalSec() { return cfg().sailfishTurnIntervalSec.get(); }
    public static double sailfishFakeOutChance() { return cfg().sailfishFakeOutChance.get(); }
    public static double sailfishFatigueThreshold() { return cfg().sailfishFatigueThreshold.get(); }
    public static double sailfishFatigueDurationSec() { return cfg().sailfishFatigueDurationSec.get(); }
    public static double sailfishStaminaRegen() { return cfg().sailfishStaminaRegen.get(); }
    public static double mahiMahiMinKg() { return cfg().mahiMahiMinKg.get(); }
    public static double mahiMahiMaxKg() { return cfg().mahiMahiMaxKg.get(); }
    public static double mahiMahiPullSpeed() { return cfg().mahiMahiPullSpeed.get(); }
    public static double mahiMahiFatiguedPullSpeed() { return cfg().mahiMahiFatiguedPullSpeed.get(); }
    public static double mahiMahiStamina() { return cfg().mahiMahiStamina.get(); }
    public static double mahiMahiTensionGain() { return cfg().mahiMahiTensionGain.get(); }
    public static double mahiMahiSprintDurationSec() { return cfg().mahiMahiSprintDurationSec.get(); }
    public static double mahiMahiSprintIntervalSec() { return cfg().mahiMahiSprintIntervalSec.get(); }
    public static double mahiMahiTurnIntervalSec() { return cfg().mahiMahiTurnIntervalSec.get(); }
    public static double mahiMahiFakeOutChance() { return cfg().mahiMahiFakeOutChance.get(); }
    public static double mahiMahiFatigueThreshold() { return cfg().mahiMahiFatigueThreshold.get(); }
    public static double mahiMahiFatigueDurationSec() { return cfg().mahiMahiFatigueDurationSec.get(); }
    public static double mahiMahiStaminaRegen() { return cfg().mahiMahiStaminaRegen.get(); }
    public static double alligatorGarMinKg() { return cfg().alligatorGarMinKg.get(); }
    public static double alligatorGarMaxKg() { return cfg().alligatorGarMaxKg.get(); }
    public static double alligatorGarPullSpeed() { return cfg().alligatorGarPullSpeed.get(); }
    public static double alligatorGarFatiguedPullSpeed() { return cfg().alligatorGarFatiguedPullSpeed.get(); }
    public static double alligatorGarStamina() { return cfg().alligatorGarStamina.get(); }
    public static double alligatorGarTensionGain() { return cfg().alligatorGarTensionGain.get(); }
    public static double alligatorGarSprintDurationSec() { return cfg().alligatorGarSprintDurationSec.get(); }
    public static double alligatorGarSprintIntervalSec() { return cfg().alligatorGarSprintIntervalSec.get(); }
    public static double alligatorGarTurnIntervalSec() { return cfg().alligatorGarTurnIntervalSec.get(); }
    public static double alligatorGarFakeOutChance() { return cfg().alligatorGarFakeOutChance.get(); }
    public static double alligatorGarFatigueThreshold() { return cfg().alligatorGarFatigueThreshold.get(); }
    public static double alligatorGarFatigueDurationSec() { return cfg().alligatorGarFatigueDurationSec.get(); }
    public static double alligatorGarStaminaRegen() { return cfg().alligatorGarStaminaRegen.get(); }
    public static double giantSiameseCarpMinKg() { return cfg().giantSiameseCarpMinKg.get(); }
    public static double giantSiameseCarpMaxKg() { return cfg().giantSiameseCarpMaxKg.get(); }
    public static double giantSiameseCarpPullSpeed() { return cfg().giantSiameseCarpPullSpeed.get(); }
    public static double giantSiameseCarpFatiguedPullSpeed() { return cfg().giantSiameseCarpFatiguedPullSpeed.get(); }
    public static double giantSiameseCarpStamina() { return cfg().giantSiameseCarpStamina.get(); }
    public static double giantSiameseCarpTensionGain() { return cfg().giantSiameseCarpTensionGain.get(); }
    public static double giantSiameseCarpSprintDurationSec() { return cfg().giantSiameseCarpSprintDurationSec.get(); }
    public static double giantSiameseCarpSprintIntervalSec() { return cfg().giantSiameseCarpSprintIntervalSec.get(); }
    public static double giantSiameseCarpTurnIntervalSec() { return cfg().giantSiameseCarpTurnIntervalSec.get(); }
    public static double giantSiameseCarpFakeOutChance() { return cfg().giantSiameseCarpFakeOutChance.get(); }
    public static double giantSiameseCarpFatigueThreshold() { return cfg().giantSiameseCarpFatigueThreshold.get(); }
    public static double giantSiameseCarpFatigueDurationSec() { return cfg().giantSiameseCarpFatigueDurationSec.get(); }
    public static double giantSiameseCarpStaminaRegen() { return cfg().giantSiameseCarpStaminaRegen.get(); }
    public static double electricEelMinKg() { return cfg().electricEelMinKg.get(); }
    public static double electricEelMaxKg() { return cfg().electricEelMaxKg.get(); }
    public static double electricEelPullSpeed() { return cfg().electricEelPullSpeed.get(); }
    public static double electricEelFatiguedPullSpeed() { return cfg().electricEelFatiguedPullSpeed.get(); }
    public static double electricEelStamina() { return cfg().electricEelStamina.get(); }
    public static double electricEelTensionGain() { return cfg().electricEelTensionGain.get(); }
    public static double electricEelSprintDurationSec() { return cfg().electricEelSprintDurationSec.get(); }
    public static double electricEelSprintIntervalSec() { return cfg().electricEelSprintIntervalSec.get(); }
    public static double electricEelTurnIntervalSec() { return cfg().electricEelTurnIntervalSec.get(); }
    public static double electricEelFakeOutChance() { return cfg().electricEelFakeOutChance.get(); }
    public static double electricEelFatigueThreshold() { return cfg().electricEelFatigueThreshold.get(); }
    public static double electricEelFatigueDurationSec() { return cfg().electricEelFatigueDurationSec.get(); }
    public static double electricEelStaminaRegen() { return cfg().electricEelStaminaRegen.get(); }
    // ==== END 鱼种数值 getter ====
    // @tuning-slot: gameGetter
    // ==== BEGIN 玩法数值 getter（生成，勿手改）====
    public static double rodWoodMaxWeightKg() { return cfg().rodWoodMaxWeightKg.get(); }
    public static double rodWoodHookWindowBonus() { return cfg().rodWoodHookWindowBonus.get(); }
    public static double rodWoodTensionCeiling() { return cfg().rodWoodTensionCeiling.get(); }
    public static double rodWoodTensionRecover() { return cfg().rodWoodTensionRecover.get(); }
    public static double rodWoodStaminaDrain() { return cfg().rodWoodStaminaDrain.get(); }
    public static double rodWoodMaterialFactor() { return cfg().rodWoodMaterialFactor.get(); }
    public static int rodWoodTier() { return cfg().rodWoodTier.get(); }
    public static double rodIronMaxWeightKg() { return cfg().rodIronMaxWeightKg.get(); }
    public static double rodIronHookWindowBonus() { return cfg().rodIronHookWindowBonus.get(); }
    public static double rodIronTensionCeiling() { return cfg().rodIronTensionCeiling.get(); }
    public static double rodIronTensionRecover() { return cfg().rodIronTensionRecover.get(); }
    public static double rodIronStaminaDrain() { return cfg().rodIronStaminaDrain.get(); }
    public static double rodIronMaterialFactor() { return cfg().rodIronMaterialFactor.get(); }
    public static int rodIronTier() { return cfg().rodIronTier.get(); }
    public static double rodGoldMaxWeightKg() { return cfg().rodGoldMaxWeightKg.get(); }
    public static double rodGoldHookWindowBonus() { return cfg().rodGoldHookWindowBonus.get(); }
    public static double rodGoldTensionCeiling() { return cfg().rodGoldTensionCeiling.get(); }
    public static double rodGoldTensionRecover() { return cfg().rodGoldTensionRecover.get(); }
    public static double rodGoldStaminaDrain() { return cfg().rodGoldStaminaDrain.get(); }
    public static double rodGoldMaterialFactor() { return cfg().rodGoldMaterialFactor.get(); }
    public static int rodGoldTier() { return cfg().rodGoldTier.get(); }
    public static double rodDiamondMaxWeightKg() { return cfg().rodDiamondMaxWeightKg.get(); }
    public static double rodDiamondHookWindowBonus() { return cfg().rodDiamondHookWindowBonus.get(); }
    public static double rodDiamondTensionCeiling() { return cfg().rodDiamondTensionCeiling.get(); }
    public static double rodDiamondTensionRecover() { return cfg().rodDiamondTensionRecover.get(); }
    public static double rodDiamondStaminaDrain() { return cfg().rodDiamondStaminaDrain.get(); }
    public static double rodDiamondMaterialFactor() { return cfg().rodDiamondMaterialFactor.get(); }
    public static int rodDiamondTier() { return cfg().rodDiamondTier.get(); }
    public static double rodNetheriteMaxWeightKg() { return cfg().rodNetheriteMaxWeightKg.get(); }
    public static double rodNetheriteHookWindowBonus() { return cfg().rodNetheriteHookWindowBonus.get(); }
    public static double rodNetheriteTensionCeiling() { return cfg().rodNetheriteTensionCeiling.get(); }
    public static double rodNetheriteTensionRecover() { return cfg().rodNetheriteTensionRecover.get(); }
    public static double rodNetheriteStaminaDrain() { return cfg().rodNetheriteStaminaDrain.get(); }
    public static double rodNetheriteMaterialFactor() { return cfg().rodNetheriteMaterialFactor.get(); }
    public static int rodNetheriteTier() { return cfg().rodNetheriteTier.get(); }
    public static double tensionBaseCeiling() { return cfg().tensionBaseCeiling.get(); }
    public static double pullSprint() { return cfg().pullSprint.get(); }
    public static double counterBonus() { return cfg().counterBonus.get(); }
    public static double counterPenalty() { return cfg().counterPenalty.get(); }
    public static double minReelGainRatio() { return cfg().minReelGainRatio.get(); }
    public static double maxFishSlowdown() { return cfg().maxFishSlowdown.get(); }
    public static double dampingToCapRatio() { return cfg().dampingToCapRatio.get(); }
    public static double capDampingBonus() { return cfg().capDampingBonus.get(); }
    public static double totalDrainTarget() { return cfg().totalDrainTarget.get(); }
    public static double nearShoreBase() { return cfg().nearShoreBase.get(); }
    public static double staminaEpsilonFloor() { return cfg().staminaEpsilonFloor.get(); }
    public static double staminaEpsilonRatio() { return cfg().staminaEpsilonRatio.get(); }
    public static int inputStaleLimit() { return cfg().inputStaleLimit.get(); }
    public static double tierSpeedBase() { return cfg().tierSpeedBase.get(); }
    public static double tierStaminaResistBase() { return cfg().tierStaminaResistBase.get(); }
    public static double tierGoldSpeedScale() { return cfg().tierGoldSpeedScale.get(); }
    public static double tierGoldStaminaScale() { return cfg().tierGoldStaminaScale.get(); }
    public static double tierRedSpeedScale() { return cfg().tierRedSpeedScale.get(); }
    public static double tierRedStaminaScale() { return cfg().tierRedStaminaScale.get(); }
    public static double tierRedStaminaMin() { return cfg().tierRedStaminaMin.get(); }
    public static double fatiguePullMultiplier() { return cfg().fatiguePullMultiplier.get(); }
    public static double reelFloorDrainBase() { return cfg().reelFloorDrainBase.get(); }
    public static double reelFloorDrainPerDamping() { return cfg().reelFloorDrainPerDamping.get(); }
    public static double reelFloorDrainCap() { return cfg().reelFloorDrainCap.get(); }
    public static double floorStruggleBase() { return cfg().floorStruggleBase.get(); }
    public static double floorStrugglePerTier() { return cfg().floorStrugglePerTier.get(); }
    public static double floorStruggleBonusSteady() { return cfg().floorStruggleBonusSteady.get(); }
    public static double floorStruggleBonusSluggish() { return cfg().floorStruggleBonusSluggish.get(); }
    public static double floorStruggleBonusBurst() { return cfg().floorStruggleBonusBurst.get(); }
    public static double floorStruggleBonusErratic() { return cfg().floorStruggleBonusErratic.get(); }
    public static double reelResistThreshold() { return cfg().reelResistThreshold.get(); }
    public static double reelResistTierWhite() { return cfg().reelResistTierWhite.get(); }
    public static double reelResistTierGreen() { return cfg().reelResistTierGreen.get(); }
    public static double reelResistTierBlue() { return cfg().reelResistTierBlue.get(); }
    public static double reelResistTierPurple() { return cfg().reelResistTierPurple.get(); }
    public static double reelResistTierGold() { return cfg().reelResistTierGold.get(); }
    public static double reelResistTierRed() { return cfg().reelResistTierRed.get(); }
    public static double reelResistStratSteady() { return cfg().reelResistStratSteady.get(); }
    public static double reelResistStratSluggish() { return cfg().reelResistStratSluggish.get(); }
    public static double reelResistStratBurst() { return cfg().reelResistStratBurst.get(); }
    public static double reelResistStratErratic() { return cfg().reelResistStratErratic.get(); }
    public static double reelResistDampingOffset() { return cfg().reelResistDampingOffset.get(); }
    public static double reelResistDampingCap() { return cfg().reelResistDampingCap.get(); }
    public static double fatiguedReelMinGain() { return cfg().fatiguedReelMinGain.get(); }
    public static double fatiguedTensionGainScale() { return cfg().fatiguedTensionGainScale.get(); }
    public static double fatiguedTensionRecoverScale() { return cfg().fatiguedTensionRecoverScale.get(); }
    public static double fatigueThresholdScale() { return cfg().fatigueThresholdScale.get(); }
    public static double idleFallFloor() { return cfg().idleFallFloor.get(); }
    public static double struggleSecondsBase() { return cfg().struggleSecondsBase.get(); }
    public static double struggleSecondsPerTier() { return cfg().struggleSecondsPerTier.get(); }
    public static double restSecondsBase() { return cfg().restSecondsBase.get(); }
    public static double restSecondsPerTier() { return cfg().restSecondsPerTier.get(); }
    public static int alpineLakeMinY() { return cfg().alpineLakeMinY.get(); }
    public static double coldWaterMaxTemp() { return cfg().coldWaterMaxTemp.get(); }
    public static int brokenIceToPackIceMinutes() { return cfg().brokenIceToPackIceMinutes.get(); }
    public static int packIceToIceMinutes() { return cfg().packIceToIceMinutes.get(); }
    public static int icePickDurabilityCost() { return cfg().icePickDurabilityCost.get(); }
    public static double iceHoleLineWearMultiplier() { return cfg().iceHoleLineWearMultiplier.get(); }
    public static double iceHoleHighValueBonus() { return cfg().iceHoleHighValueBonus.get(); }
    public static double openIceBiteTimeMultiplier() { return cfg().openIceBiteTimeMultiplier.get(); }
    public static double openIceHighValuePenalty() { return cfg().openIceHighValuePenalty.get(); }
    public static int fullChargeTicks() { return cfg().fullChargeTicks.get(); }
    public static int minChargeTicks() { return cfg().minChargeTicks.get(); }
    public static int chumMinAirTicks() { return cfg().chumMinAirTicks.get(); }
    public static int oversizeFishAction() { return cfg().oversizeFishAction.get(); }
    public static int compassSearchRadius() { return cfg().compassSearchRadius.get(); }
    public static int bigCatchAnnounceValue() { return cfg().bigCatchAnnounceValue.get(); }
    public static int bigCatchAnnounceMinIntervalSec() { return cfg().bigCatchAnnounceMinIntervalSec.get(); }
    // ==== END 玩法数值 getter ====
    // @tuning-slot: partsHelper
    // ==== BEGIN 配件数值工具（生成，勿手改）====
/** double 专用：ConfigValue<Double>.set 要 Double 而不是 Number。 */
private static void putDoubleValue(java.util.Map<String, Object> flat, String path,
                                   net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Double> value,
                                   int[] counter) {
    Object v = flat.get(path);
    if (!(v instanceof Number num)) { return; }
    try { value.set(num.doubleValue()); value.clearCache(); counter[0]++; }
    catch (Throwable t) { AnglersTackle.LOGGER.debug("[垂纶] 热重载跳过 {}：{}", path, t.toString()); }
}

/** int 专用（配件档位 tier）。 */
private static void putIntValue(java.util.Map<String, Object> flat, String path,
                                net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Integer> value,
                                int[] counter) {
    Object v = flat.get(path);
    if (!(v instanceof Number num)) { return; }
    try { value.set(num.intValue()); value.clearCache(); counter[0]++; }
    catch (Throwable t) { AnglersTackle.LOGGER.debug("[垂纶] 热重载跳过 {}：{}", path, t.toString()); }
}
    // ==== END 配件数值工具 ====

    /**
     * 给 {@link FishTuning} 用的句柄：**不抛异常**，未就绪时返回 {@code null}。
     *
     * <p>为什么和 {@link #cfg()} 不一样：{@code cfg()} 是给 getter 用的，
     * 那些 getter 只在钓鱼时被调用，早期调用属于"用错了"，抛异常能立刻定位。
     * 而 {@code FishTuning.build()} 是**主动**在装载阶段被调用的，
     * 那时配置可能真的还没注册完 —— 这是正常时序，不是错误。
     * 让它抛异常会被上层 try/catch 吞掉并打一条 WARN，日志里看着像出了问题。
     */
    public static Config cfgPublic() {
        return Config.INSTANCE;
    }

    private static Config cfg() {
        Config c = Config.INSTANCE;
        if (c == null) {
            throw new IllegalStateException("Config.INSTANCE 尚未初始化（配置在 mod 构造阶段才注册）");
        }
        return c;
    }
}
