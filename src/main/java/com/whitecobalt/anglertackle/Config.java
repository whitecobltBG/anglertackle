package com.whitecobalt.anglertackle;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * 配置与平衡参数。
 *
 * <p>数值来源见设计文档：
 * <ul>
 *   <li>{@code docs/02-钓鱼小游戏机制.md} §9 — 手感调参清单（cast / hookWindow / tension / bite）</li>
 *   <li>{@code docs/11-配件系统详表.md} §2 — 六槽属性合成权重与上限（parts）</li>
 *   <li>{@code docs/03-数值表.md} §9.3 — 经济平衡（chum / bait / value）</li>
 *   <li>{@code docs/09-原版冲突与渲染优化.md} §4 — 渲染与实体同步（render）</li>
 * </ul>
 *
 * <p>所有默认值都刻意做成"可直接改"，方便整合包作者调平衡，不必重新编译。
 */
public final class Config {

    public static final ModConfigSpec SPEC;
    public static final Config INSTANCE;

    static {
        Pair<Config, ModConfigSpec> pair = new ModConfigSpec.Builder().configure(Config::new);
        INSTANCE = pair.getLeft();
        SPEC = pair.getRight();
    }

    // ------------------------------------------------------------------ 抛投
    public final ModConfigSpec.DoubleValue chargeTimeSeconds;

    // ------------------------------------------------------------ 提竿窗口
    public final ModConfigSpec.DoubleValue hookWindowBase;
    public final ModConfigSpec.DoubleValue hookWindowMin;

    // ---------------------------------------------------------------- 张力
    public final ModConfigSpec.DoubleValue tensionReelGain;
    public final ModConfigSpec.DoubleValue tensionReleaseLoss;
    public final ModConfigSpec.DoubleValue tensionIdleLoss;
    public final ModConfigSpec.DoubleValue tensionWhiteMax;
    public final ModConfigSpec.DoubleValue tensionYellowMax;
    public final ModConfigSpec.DoubleValue tensionCriticalFlash;
    public final ModConfigSpec.DoubleValue tensionBaseRecover;

    // ------------------------------------------------------------ 咬钩等待
    public final ModConfigSpec.DoubleValue biteMinWait;
    public final ModConfigSpec.DoubleValue biteMaxWait;
    public final ModConfigSpec.DoubleValue biteHardCap;
    public final ModConfigSpec.DoubleValue biteFloorSeconds;
    public final ModConfigSpec.DoubleValue biteFloatRodCutPercent;
    public final ModConfigSpec.DoubleValue biteBaitCutWhitePercent;
    public final ModConfigSpec.DoubleValue biteBaitCutGreenPercent;
    public final ModConfigSpec.DoubleValue biteBaitCutBluePercent;
    public final ModConfigSpec.DoubleValue biteBaitCutPurplePercent;
    public final ModConfigSpec.DoubleValue biteBaitCutGoldPercent;
    public final ModConfigSpec.DoubleValue biteBaitCutRedPercent;

    // ------------------------------------------------- 时间 / 天气 / 月相
    // 见 docs/24-时间天气月相策划.md（v2 · 占比约束版）。
    public final ModConfigSpec.BooleanValue environmentEnabled;
    public final ModConfigSpec.DoubleValue envTimeDeltaPct;
    public final ModConfigSpec.DoubleValue envWeatherDeltaPct;
    public final ModConfigSpec.DoubleValue envMoonGoldRedDeltaPct;
    public final ModConfigSpec.DoubleValue envMoonPurpleDeltaPct;
    public final ModConfigSpec.IntValue envMoonShareMode;
    public final ModConfigSpec.DoubleValue envShareCapPct;
    public final ModConfigSpec.BooleanValue envShowCondition;
    public final ModConfigSpec.BooleanValue envLogToChat;
    public final ModConfigSpec.DoubleValue biteNightCutPercent;

    // ------------------------------------------------------- 配件属性合成
    public final ModConfigSpec.DoubleValue weightLineOnTension;
    public final ModConfigSpec.DoubleValue weightRodOnTension;
    public final ModConfigSpec.DoubleValue weightLineOnRecover;
    public final ModConfigSpec.DoubleValue weightRodOnRecover;
    public final ModConfigSpec.DoubleValue weightReelOnDrain;
    public final ModConfigSpec.DoubleValue weightRodOnDrain;
    public final ModConfigSpec.DoubleValue capTensionCeiling;
    public final ModConfigSpec.DoubleValue capTensionRecover;
    public final ModConfigSpec.DoubleValue capStaminaDrain;
    public final ModConfigSpec.DoubleValue capPull;
    public final ModConfigSpec.DoubleValue capDamping;
    public final ModConfigSpec.DoubleValue pullIdleBase;
    public final ModConfigSpec.DoubleValue pullStruggleBase;
    public final ModConfigSpec.DoubleValue staminaDrainBase;

    // ---------------------------------------------------------------- 窝料
    public final ModConfigSpec.IntValue chumParticleIntervalTicks;
    public final ModConfigSpec.IntValue chumParticlesPerBurst;
    public final ModConfigSpec.DoubleValue chumRadius;
    /** 全服同时存在的窝料上限（条）。见构造函数里 {@code [chum]} 组的注释。 */
    public final ModConfigSpec.IntValue chumServerCap;

    // ---------------------------------------------------------------- 称号
    // 称号的**门槛数字**全在这一组（用户 2026-10-11："这些数字全部进配置，不写死在代码里"）。
    // 细则见构造函数里 {@code [titles]} 组的注释；命名规则见 TitleDefs。
    /** 聊天里的称号后缀模式：0 = full_line（玩家[称号]：内容）/ 1 = message_only / 2 = vanilla。 */
    public final ModConfigSpec.IntValue titleChatMode;
    public final ModConfigSpec.IntValue titleCodexCount5;
    public final ModConfigSpec.IntValue titleCodexCount10;
    public final ModConfigSpec.IntValue titleCodexCount24;
    public final ModConfigSpec.IntValue titleCodexCount40;
    public final ModConfigSpec.IntValue titleGoldCount10;
    public final ModConfigSpec.DoubleValue titleGiantKg5;
    public final ModConfigSpec.DoubleValue titleGiantKg20;
    public final ModConfigSpec.DoubleValue titleGiantKg50;
    public final ModConfigSpec.DoubleValue titleGiantKg100;
    public final ModConfigSpec.IntValue titleGarCount;
    public final ModConfigSpec.IntValue titleRedCount;
    public final ModConfigSpec.IntValue titleNightCount;
    public final ModConfigSpec.IntValue titleFullMoonCount;
    public final ModConfigSpec.IntValue titleIceCount;
    public final ModConfigSpec.IntValue titleChumCount;
    public final ModConfigSpec.IntValue titleLineBreakCount;
    public final ModConfigSpec.IntValue titleDishMadeCount;
    public final ModConfigSpec.IntValue titleDishEatenCount;

    // ---------------------------------------------------------------- 邮件
    // 邮件箱（用户 2026-10-11 点名要的邮件系统；策划 docs/36 §2.4）。细则见构造函数里 {@code [mail]} 组。
    /** 邮件保管期（**现实天**）：过期只作废附件、正文保留。 */
    public final ModConfigSpec.IntValue mailKeepDays;
    /** 邮箱条数上限；到顶**拒绝投递**（WARN + 提示），绝不静默丢件。 */
    public final ModConfigSpec.IntValue mailMaxMails;
    /** 服主手写正文的长度上限（发送侧拒绝 + 渲染侧再截断，两道都要有）。 */
    public final ModConfigSpec.IntValue mailBodyMaxChars;
    /** 进世界时是否给一次"你有未读邮件"的动作栏提示（**只一次**）。 */
    public final ModConfigSpec.BooleanValue mailUnreadLoginNotice;

    // ---------------------------------------------------------------- 委托
    // 老柯的每日 / 周常任务（用户 2026-10-14 定案；策划 docs/40）。细则见构造函数里 {@code [quest]} 组。
    /** 委托系统总开关（出问题时一键停掉，不用回滚版本）。 */
    public final ModConfigSpec.BooleanValue questEnabled;
    /** 每游戏日投递几条每日委托（用户口径：5）。 */
    public final ModConfigSpec.IntValue questDailyCount;
    /** 每游戏周投递几条周常委托（用户口径：5）。 */
    public final ModConfigSpec.IntValue questWeeklyCount;
    /** 完成一个每日委托给多少活跃度（用户口径：3）。 */
    public final ModConfigSpec.IntValue questActivityPerDaily;
    /** 完成一个周常委托给多少活跃度（用户口径：8）。 */
    public final ModConfigSpec.IntValue questActivityPerWeekly;
    /** 每周活跃度上限（用户口径：100；奖励档位 25/50/75/100）。 */
    public final ModConfigSpec.IntValue questActivityCapWeekly;

    // ---------------------------------------------------------------- 联赛
    // 联赛（用户 2026-10-11 定案；策划 docs/37）。细则见构造函数里 {@code [league]} 组。
    /**
     * 计时口径：true = 现实天（默认）/ false = 游戏日。
     *
     * <p>⚠ 为什么是布尔，而不是文档里写的 {@code clockMode = real_days|game_days} 字符串：
     * 调参器（{@code tools/tuning-tool.template.html}）只认**数字与布尔**两种字段，
     * 加一种字符串字段要同时改模板、schema 生成器与两个检查器 —— 那是在用户天天用的
     * 调参器上做手术。而这里只有两个取值，一个开关表达得完全一样清楚
     * （同一条思路见 {@code titleChatMode}：那张表也是用一个整数表达三种模式）。
     * `docs/37` §6 已同步改成这个键名。
     */
    public final ModConfigSpec.BooleanValue leagueUseRealDays;
    /** 邀请资格线（钓鱼等级；默认 21 = 过了 Lv.20 评定②）。 */
    public final ModConfigSpec.IntValue leagueMinLevel;
    /** 打开后未达线者也收邀请（新服防"长期无人参赛"）。 */
    public final ModConfigSpec.BooleanValue leagueAllowBelowLevel;
    /** 小型联赛的**推送间隔**（按所选口径的"天"）。 */
    public final ModConfigSpec.IntValue leagueSmallDays;
    /** 中型联赛的**推送间隔**（按所选口径的"天"）。 */
    public final ModConfigSpec.IntValue leagueMediumDays;
    /** 大型联赛的**推送间隔**（按所选口径的"天"）。 */
    public final ModConfigSpec.IntValue leagueLargeDays;
    /** 一期**硬截止**：推送后几个**游戏日**结算（默认 5）。 */
    public final ModConfigSpec.IntValue leaguePeriodGameDays;
    /** 每人窗口：签收后多少**游戏分钟**内计分（默认 30）。 */
    public final ModConfigSpec.IntValue leaguePlayerWindowMinutes;
    /** 现实口径下每期的起始**服务器本地时刻**（默认 12 点，学 LoL 的"各服本地 12:00:00"）。 */
    public final ModConfigSpec.IntValue leaguePeriodBoundaryHour;
    /**
     * 计分口径：0 = 最重一尾（默认）/ 1 = 总重量 / 2 = 金红尾数 / 3 = 图鉴完成度。
     *
     * <p>⚠ 顺序**必须**与 {@code League.Rule} 的枚举顺序逐字一致（{@code Rule.byMode} 按下标取），
     * 守着这条的是 {@code tools/check-league.js}。
     * ⚠ 可刷的两项（最短上鱼 / 连击）**不在选项里**：调研结论说它们极易被脚本刷。
     */
    public final ModConfigSpec.IntValue leagueRuleMode;
    /** 有效成绩下限：本期至少上鱼 N 尾才进榜、才有名次奖（防挂机/小号）。 */
    public final ModConfigSpec.IntValue leagueMinCatchesToRank;
    /** 参与奖（参赛但没进前十）。 */
    public final ModConfigSpec.IntValue leagueRewardCoinsParticipant;
    /** 第 4~10 名。 */
    public final ModConfigSpec.IntValue leagueRewardCoinsTop10;
    /** 第 1 名。 */
    public final ModConfigSpec.IntValue leagueRewardCoins1st;
    /** 第 2 名。 */
    public final ModConfigSpec.IntValue leagueRewardCoins2nd;
    /** 第 3 名。 */
    public final ModConfigSpec.IntValue leagueRewardCoins3rd;
    /** 第 1 名的垂纶经验。 */
    public final ModConfigSpec.IntValue leagueRewardXp1st;
    /** 第 2 名的垂纶经验。 */
    public final ModConfigSpec.IntValue leagueRewardXp2nd;
    /** 第 3 名的垂纶经验。 */
    public final ModConfigSpec.IntValue leagueRewardXp3rd;
    /** 中型联赛的**鱼币与经验**倍率。 */
    public final ModConfigSpec.DoubleValue leagueScaleMediumCoinsXp;
    /** 大型联赛的**鱼币与经验**倍率。 */
    public final ModConfigSpec.DoubleValue leagueScaleLargeCoinsXp;
    /** 大型联赛的**原版物资**倍率（模组物品不缩放）。 */
    public final ModConfigSpec.DoubleValue leagueScaleLargeVanillaItems;
    /** 联赛**奖励类**邮件（邀请 / 成绩单，里面有奖励）的保管期（现实天，默认 365）。 */
    public final ModConfigSpec.IntValue leagueRewardMailKeepDays;
    /** 联赛**通知类**邮件（未参赛公示等）的保管期（现实天）。 */
    public final ModConfigSpec.IntValue leagueNoticeMailKeepDays;
    /** 登录时补发当前期邀请（关掉就严格按文档"中途不补发"）。 */
    public final ModConfigSpec.BooleanValue leagueInviteOnLogin;

    // ---------------------------------------------------------------- 比赛
    // 钓鱼比赛（用户 2026-10-11 定案；策划 docs/35）。细则见构造函数里 {@code [contest]} 组。
    /** 不填时长时用多少分钟。 */
    public final ModConfigSpec.IntValue contestDefaultMinutes;
    /** 默认赛制：0 = 最重一尾 / 1 = 总重量 / 2 = 金红尾数。 */
    public final ModConfigSpec.IntValue contestDefaultRuleMode;
    /** 低于这个在线人数就不开赛（默认 1 = 单人可玩）。 */
    public final ModConfigSpec.IntValue contestMinPlayers;
    /** 第 1 名奖励（鱼币）。 */
    public final ModConfigSpec.IntValue contestRewardFirst;
    /** 第 2 名奖励（鱼币）。 */
    public final ModConfigSpec.IntValue contestRewardSecond;
    /** 第 3 名奖励（鱼币）。 */
    public final ModConfigSpec.IntValue contestRewardThird;
    /** 自动开赛间隔（分钟；0 = 只手动开赛）。 */
    public final ModConfigSpec.IntValue contestAutoEveryMinutes;
    /** 结算时公告前几名（1~10）。 */
    public final ModConfigSpec.IntValue contestAnnounceTop;

    // ------------------------------------------------------------ 品质门槛
    // （见构造函数里的说明：这一组还没实现，暂不提供配置项）

    // ---------------------------------------------------------------- 耐久
    public final ModConfigSpec.IntValue rodDurabilityPerBite;
    public final ModConfigSpec.IntValue lineDurabilityPerBite;
    public final ModConfigSpec.IntValue lureDurabilityPerBite;
    public final ModConfigSpec.IntValue lineDurabilityPerBreak;

    // ==== BEGIN 配件数值声明（gen-part-config.js 生成，勿手改）====
    public final ModConfigSpec.IntValue hempDurability;
    public final ModConfigSpec.DoubleValue hempTensionCeiling;
    public final ModConfigSpec.DoubleValue hempTensionGen;
    public final ModConfigSpec.DoubleValue hempTensionRecover;
    public final ModConfigSpec.DoubleValue hempLineLength;
    public final ModConfigSpec.IntValue hempTier;
    public final ModConfigSpec.IntValue cottonDurability;
    public final ModConfigSpec.DoubleValue cottonTensionCeiling;
    public final ModConfigSpec.DoubleValue cottonTensionGen;
    public final ModConfigSpec.DoubleValue cottonTensionRecover;
    public final ModConfigSpec.DoubleValue cottonLineLength;
    public final ModConfigSpec.IntValue cottonTier;
    public final ModConfigSpec.IntValue nylonDurability;
    public final ModConfigSpec.DoubleValue nylonTensionCeiling;
    public final ModConfigSpec.DoubleValue nylonTensionGen;
    public final ModConfigSpec.DoubleValue nylonTensionRecover;
    public final ModConfigSpec.DoubleValue nylonLineLength;
    public final ModConfigSpec.IntValue nylonTier;
    public final ModConfigSpec.IntValue fluoroDurability;
    public final ModConfigSpec.DoubleValue fluoroTensionCeiling;
    public final ModConfigSpec.DoubleValue fluoroTensionGen;
    public final ModConfigSpec.DoubleValue fluoroTensionRecover;
    public final ModConfigSpec.DoubleValue fluoroLineLength;
    public final ModConfigSpec.IntValue fluoroTier;
    public final ModConfigSpec.IntValue peDurability;
    public final ModConfigSpec.DoubleValue peTensionCeiling;
    public final ModConfigSpec.DoubleValue peTensionGen;
    public final ModConfigSpec.DoubleValue peTensionRecover;
    public final ModConfigSpec.DoubleValue peLineLength;
    public final ModConfigSpec.IntValue peTier;
    public final ModConfigSpec.IntValue titaniumDurability;
    public final ModConfigSpec.DoubleValue titaniumTensionCeiling;
    public final ModConfigSpec.DoubleValue titaniumTensionGen;
    public final ModConfigSpec.DoubleValue titaniumTensionRecover;
    public final ModConfigSpec.DoubleValue titaniumLineLength;
    public final ModConfigSpec.IntValue titaniumTier;
    public final ModConfigSpec.IntValue handDurability;
    public final ModConfigSpec.DoubleValue handPullIdle;
    public final ModConfigSpec.DoubleValue handPullStruggle;
    public final ModConfigSpec.DoubleValue handStaminaDrain;
    public final ModConfigSpec.DoubleValue handDamping;
    public final ModConfigSpec.IntValue handTier;
    public final ModConfigSpec.IntValue spinningDurability;
    public final ModConfigSpec.DoubleValue spinningPullIdle;
    public final ModConfigSpec.DoubleValue spinningPullStruggle;
    public final ModConfigSpec.DoubleValue spinningStaminaDrain;
    public final ModConfigSpec.DoubleValue spinningDamping;
    public final ModConfigSpec.IntValue spinningTier;
    public final ModConfigSpec.IntValue drumDurability;
    public final ModConfigSpec.DoubleValue drumPullIdle;
    public final ModConfigSpec.DoubleValue drumPullStruggle;
    public final ModConfigSpec.DoubleValue drumStaminaDrain;
    public final ModConfigSpec.DoubleValue drumDamping;
    public final ModConfigSpec.IntValue drumTier;
    public final ModConfigSpec.IntValue baitcastDurability;
    public final ModConfigSpec.DoubleValue baitcastPullIdle;
    public final ModConfigSpec.DoubleValue baitcastPullStruggle;
    public final ModConfigSpec.DoubleValue baitcastStaminaDrain;
    public final ModConfigSpec.DoubleValue baitcastDamping;
    public final ModConfigSpec.IntValue baitcastTier;
    public final ModConfigSpec.IntValue heavyDurability;
    public final ModConfigSpec.DoubleValue heavyPullIdle;
    public final ModConfigSpec.DoubleValue heavyPullStruggle;
    public final ModConfigSpec.DoubleValue heavyStaminaDrain;
    public final ModConfigSpec.DoubleValue heavyDamping;
    public final ModConfigSpec.IntValue heavyTier;
    public final ModConfigSpec.IntValue lureSpoonDurability;
    public final ModConfigSpec.IntValue lureSpoonTier;
    public final ModConfigSpec.IntValue lureGlowspoonDurability;
    public final ModConfigSpec.IntValue lureGlowspoonTier;
    public final ModConfigSpec.IntValue lureMinnowDurability;
    public final ModConfigSpec.IntValue lureMinnowTier;
    public final ModConfigSpec.IntValue lureDeepDurability;
    public final ModConfigSpec.IntValue lureDeepTier;
    public final ModConfigSpec.IntValue lureRattleDurability;
    public final ModConfigSpec.IntValue lureRattleTier;
    public final ModConfigSpec.IntValue lureGildedDurability;
    public final ModConfigSpec.IntValue lureGildedTier;
    public final ModConfigSpec.IntValue lureSoftDurability;
    public final ModConfigSpec.IntValue lureSoftTier;
    public final ModConfigSpec.IntValue lureDepthfinderDurability;
    public final ModConfigSpec.IntValue lureDepthfinderTier;
    public final ModConfigSpec.IntValue lurePaddletailDurability;
    public final ModConfigSpec.IntValue lurePaddletailTier;
    public final ModConfigSpec.IntValue lureVibrationDurability;
    public final ModConfigSpec.IntValue lureVibrationTier;
    public final ModConfigSpec.IntValue lureTestRedDurability;
    public final ModConfigSpec.IntValue lureTestRedTier;
    // ==== END 配件数值声明 ====

    // ==== BEGIN 鱼种数值声明（gen-fish-config.js 生成，勿手改）====
    public final ModConfigSpec.DoubleValue crucianCarpMinKg;
    public final ModConfigSpec.DoubleValue crucianCarpMaxKg;
    public final ModConfigSpec.DoubleValue crucianCarpPullSpeed;
    public final ModConfigSpec.DoubleValue crucianCarpFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue crucianCarpStamina;
    public final ModConfigSpec.DoubleValue crucianCarpTensionGain;
    public final ModConfigSpec.DoubleValue crucianCarpSprintDurationSec;
    public final ModConfigSpec.DoubleValue crucianCarpSprintIntervalSec;
    public final ModConfigSpec.DoubleValue crucianCarpTurnIntervalSec;
    public final ModConfigSpec.DoubleValue crucianCarpFakeOutChance;
    public final ModConfigSpec.DoubleValue crucianCarpFatigueThreshold;
    public final ModConfigSpec.DoubleValue crucianCarpFatigueDurationSec;
    public final ModConfigSpec.DoubleValue crucianCarpStaminaRegen;
    public final ModConfigSpec.DoubleValue tilapiaMinKg;
    public final ModConfigSpec.DoubleValue tilapiaMaxKg;
    public final ModConfigSpec.DoubleValue tilapiaPullSpeed;
    public final ModConfigSpec.DoubleValue tilapiaFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue tilapiaStamina;
    public final ModConfigSpec.DoubleValue tilapiaTensionGain;
    public final ModConfigSpec.DoubleValue tilapiaSprintDurationSec;
    public final ModConfigSpec.DoubleValue tilapiaSprintIntervalSec;
    public final ModConfigSpec.DoubleValue tilapiaTurnIntervalSec;
    public final ModConfigSpec.DoubleValue tilapiaFakeOutChance;
    public final ModConfigSpec.DoubleValue tilapiaFatigueThreshold;
    public final ModConfigSpec.DoubleValue tilapiaFatigueDurationSec;
    public final ModConfigSpec.DoubleValue tilapiaStaminaRegen;
    public final ModConfigSpec.DoubleValue mulletMinKg;
    public final ModConfigSpec.DoubleValue mulletMaxKg;
    public final ModConfigSpec.DoubleValue mulletPullSpeed;
    public final ModConfigSpec.DoubleValue mulletFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue mulletStamina;
    public final ModConfigSpec.DoubleValue mulletTensionGain;
    public final ModConfigSpec.DoubleValue mulletSprintDurationSec;
    public final ModConfigSpec.DoubleValue mulletSprintIntervalSec;
    public final ModConfigSpec.DoubleValue mulletTurnIntervalSec;
    public final ModConfigSpec.DoubleValue mulletFakeOutChance;
    public final ModConfigSpec.DoubleValue mulletFatigueThreshold;
    public final ModConfigSpec.DoubleValue mulletFatigueDurationSec;
    public final ModConfigSpec.DoubleValue mulletStaminaRegen;
    public final ModConfigSpec.DoubleValue grassCarpMinKg;
    public final ModConfigSpec.DoubleValue grassCarpMaxKg;
    public final ModConfigSpec.DoubleValue grassCarpPullSpeed;
    public final ModConfigSpec.DoubleValue grassCarpFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue grassCarpStamina;
    public final ModConfigSpec.DoubleValue grassCarpTensionGain;
    public final ModConfigSpec.DoubleValue grassCarpSprintDurationSec;
    public final ModConfigSpec.DoubleValue grassCarpSprintIntervalSec;
    public final ModConfigSpec.DoubleValue grassCarpTurnIntervalSec;
    public final ModConfigSpec.DoubleValue grassCarpFakeOutChance;
    public final ModConfigSpec.DoubleValue grassCarpFatigueThreshold;
    public final ModConfigSpec.DoubleValue grassCarpFatigueDurationSec;
    public final ModConfigSpec.DoubleValue grassCarpStaminaRegen;
    public final ModConfigSpec.DoubleValue blackCarpMinKg;
    public final ModConfigSpec.DoubleValue blackCarpMaxKg;
    public final ModConfigSpec.DoubleValue blackCarpPullSpeed;
    public final ModConfigSpec.DoubleValue blackCarpFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue blackCarpStamina;
    public final ModConfigSpec.DoubleValue blackCarpTensionGain;
    public final ModConfigSpec.DoubleValue blackCarpSprintDurationSec;
    public final ModConfigSpec.DoubleValue blackCarpSprintIntervalSec;
    public final ModConfigSpec.DoubleValue blackCarpTurnIntervalSec;
    public final ModConfigSpec.DoubleValue blackCarpFakeOutChance;
    public final ModConfigSpec.DoubleValue blackCarpFatigueThreshold;
    public final ModConfigSpec.DoubleValue blackCarpFatigueDurationSec;
    public final ModConfigSpec.DoubleValue blackCarpStaminaRegen;
    public final ModConfigSpec.DoubleValue bandedLoachMinKg;
    public final ModConfigSpec.DoubleValue bandedLoachMaxKg;
    public final ModConfigSpec.DoubleValue bandedLoachPullSpeed;
    public final ModConfigSpec.DoubleValue bandedLoachFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue bandedLoachStamina;
    public final ModConfigSpec.DoubleValue bandedLoachTensionGain;
    public final ModConfigSpec.DoubleValue bandedLoachSprintDurationSec;
    public final ModConfigSpec.DoubleValue bandedLoachSprintIntervalSec;
    public final ModConfigSpec.DoubleValue bandedLoachTurnIntervalSec;
    public final ModConfigSpec.DoubleValue bandedLoachFakeOutChance;
    public final ModConfigSpec.DoubleValue bandedLoachFatigueThreshold;
    public final ModConfigSpec.DoubleValue bandedLoachFatigueDurationSec;
    public final ModConfigSpec.DoubleValue bandedLoachStaminaRegen;
    public final ModConfigSpec.DoubleValue hemiculterMinKg;
    public final ModConfigSpec.DoubleValue hemiculterMaxKg;
    public final ModConfigSpec.DoubleValue hemiculterPullSpeed;
    public final ModConfigSpec.DoubleValue hemiculterFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue hemiculterStamina;
    public final ModConfigSpec.DoubleValue hemiculterTensionGain;
    public final ModConfigSpec.DoubleValue hemiculterSprintDurationSec;
    public final ModConfigSpec.DoubleValue hemiculterSprintIntervalSec;
    public final ModConfigSpec.DoubleValue hemiculterTurnIntervalSec;
    public final ModConfigSpec.DoubleValue hemiculterFakeOutChance;
    public final ModConfigSpec.DoubleValue hemiculterFatigueThreshold;
    public final ModConfigSpec.DoubleValue hemiculterFatigueDurationSec;
    public final ModConfigSpec.DoubleValue hemiculterStaminaRegen;
    public final ModConfigSpec.DoubleValue topmouthCulterMinKg;
    public final ModConfigSpec.DoubleValue topmouthCulterMaxKg;
    public final ModConfigSpec.DoubleValue topmouthCulterPullSpeed;
    public final ModConfigSpec.DoubleValue topmouthCulterFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue topmouthCulterStamina;
    public final ModConfigSpec.DoubleValue topmouthCulterTensionGain;
    public final ModConfigSpec.DoubleValue topmouthCulterSprintDurationSec;
    public final ModConfigSpec.DoubleValue topmouthCulterSprintIntervalSec;
    public final ModConfigSpec.DoubleValue topmouthCulterTurnIntervalSec;
    public final ModConfigSpec.DoubleValue topmouthCulterFakeOutChance;
    public final ModConfigSpec.DoubleValue topmouthCulterFatigueThreshold;
    public final ModConfigSpec.DoubleValue topmouthCulterFatigueDurationSec;
    public final ModConfigSpec.DoubleValue topmouthCulterStaminaRegen;
    public final ModConfigSpec.DoubleValue zanderMinKg;
    public final ModConfigSpec.DoubleValue zanderMaxKg;
    public final ModConfigSpec.DoubleValue zanderPullSpeed;
    public final ModConfigSpec.DoubleValue zanderFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue zanderStamina;
    public final ModConfigSpec.DoubleValue zanderTensionGain;
    public final ModConfigSpec.DoubleValue zanderSprintDurationSec;
    public final ModConfigSpec.DoubleValue zanderSprintIntervalSec;
    public final ModConfigSpec.DoubleValue zanderTurnIntervalSec;
    public final ModConfigSpec.DoubleValue zanderFakeOutChance;
    public final ModConfigSpec.DoubleValue zanderFatigueThreshold;
    public final ModConfigSpec.DoubleValue zanderFatigueDurationSec;
    public final ModConfigSpec.DoubleValue zanderStaminaRegen;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadMinKg;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadMaxKg;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadPullSpeed;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadStamina;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadTensionGain;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadSprintDurationSec;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadSprintIntervalSec;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadTurnIntervalSec;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadFakeOutChance;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadFatigueThreshold;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadFatigueDurationSec;
    public final ModConfigSpec.DoubleValue sapphireSnakeheadStaminaRegen;
    public final ModConfigSpec.DoubleValue platinumArowanaMinKg;
    public final ModConfigSpec.DoubleValue platinumArowanaMaxKg;
    public final ModConfigSpec.DoubleValue platinumArowanaPullSpeed;
    public final ModConfigSpec.DoubleValue platinumArowanaFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue platinumArowanaStamina;
    public final ModConfigSpec.DoubleValue platinumArowanaTensionGain;
    public final ModConfigSpec.DoubleValue platinumArowanaSprintDurationSec;
    public final ModConfigSpec.DoubleValue platinumArowanaSprintIntervalSec;
    public final ModConfigSpec.DoubleValue platinumArowanaTurnIntervalSec;
    public final ModConfigSpec.DoubleValue platinumArowanaFakeOutChance;
    public final ModConfigSpec.DoubleValue platinumArowanaFatigueThreshold;
    public final ModConfigSpec.DoubleValue platinumArowanaFatigueDurationSec;
    public final ModConfigSpec.DoubleValue platinumArowanaStaminaRegen;
    public final ModConfigSpec.DoubleValue oxeyeTarponMinKg;
    public final ModConfigSpec.DoubleValue oxeyeTarponMaxKg;
    public final ModConfigSpec.DoubleValue oxeyeTarponPullSpeed;
    public final ModConfigSpec.DoubleValue oxeyeTarponFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue oxeyeTarponStamina;
    public final ModConfigSpec.DoubleValue oxeyeTarponTensionGain;
    public final ModConfigSpec.DoubleValue oxeyeTarponSprintDurationSec;
    public final ModConfigSpec.DoubleValue oxeyeTarponSprintIntervalSec;
    public final ModConfigSpec.DoubleValue oxeyeTarponTurnIntervalSec;
    public final ModConfigSpec.DoubleValue oxeyeTarponFakeOutChance;
    public final ModConfigSpec.DoubleValue oxeyeTarponFatigueThreshold;
    public final ModConfigSpec.DoubleValue oxeyeTarponFatigueDurationSec;
    public final ModConfigSpec.DoubleValue oxeyeTarponStaminaRegen;
    public final ModConfigSpec.DoubleValue atlanticSalmonMinKg;
    public final ModConfigSpec.DoubleValue atlanticSalmonMaxKg;
    public final ModConfigSpec.DoubleValue atlanticSalmonPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticSalmonFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticSalmonStamina;
    public final ModConfigSpec.DoubleValue atlanticSalmonTensionGain;
    public final ModConfigSpec.DoubleValue atlanticSalmonSprintDurationSec;
    public final ModConfigSpec.DoubleValue atlanticSalmonSprintIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticSalmonTurnIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticSalmonFakeOutChance;
    public final ModConfigSpec.DoubleValue atlanticSalmonFatigueThreshold;
    public final ModConfigSpec.DoubleValue atlanticSalmonFatigueDurationSec;
    public final ModConfigSpec.DoubleValue atlanticSalmonStaminaRegen;
    public final ModConfigSpec.DoubleValue bluefinTunaMinKg;
    public final ModConfigSpec.DoubleValue bluefinTunaMaxKg;
    public final ModConfigSpec.DoubleValue bluefinTunaPullSpeed;
    public final ModConfigSpec.DoubleValue bluefinTunaFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue bluefinTunaStamina;
    public final ModConfigSpec.DoubleValue bluefinTunaTensionGain;
    public final ModConfigSpec.DoubleValue bluefinTunaSprintDurationSec;
    public final ModConfigSpec.DoubleValue bluefinTunaSprintIntervalSec;
    public final ModConfigSpec.DoubleValue bluefinTunaTurnIntervalSec;
    public final ModConfigSpec.DoubleValue bluefinTunaFakeOutChance;
    public final ModConfigSpec.DoubleValue bluefinTunaFatigueThreshold;
    public final ModConfigSpec.DoubleValue bluefinTunaFatigueDurationSec;
    public final ModConfigSpec.DoubleValue bluefinTunaStaminaRegen;
    public final ModConfigSpec.DoubleValue juvenileSharkMinKg;
    public final ModConfigSpec.DoubleValue juvenileSharkMaxKg;
    public final ModConfigSpec.DoubleValue juvenileSharkPullSpeed;
    public final ModConfigSpec.DoubleValue juvenileSharkFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue juvenileSharkStamina;
    public final ModConfigSpec.DoubleValue juvenileSharkTensionGain;
    public final ModConfigSpec.DoubleValue juvenileSharkSprintDurationSec;
    public final ModConfigSpec.DoubleValue juvenileSharkSprintIntervalSec;
    public final ModConfigSpec.DoubleValue juvenileSharkTurnIntervalSec;
    public final ModConfigSpec.DoubleValue juvenileSharkFakeOutChance;
    public final ModConfigSpec.DoubleValue juvenileSharkFatigueThreshold;
    public final ModConfigSpec.DoubleValue juvenileSharkFatigueDurationSec;
    public final ModConfigSpec.DoubleValue juvenileSharkStaminaRegen;
    public final ModConfigSpec.DoubleValue tigerfishMinKg;
    public final ModConfigSpec.DoubleValue tigerfishMaxKg;
    public final ModConfigSpec.DoubleValue tigerfishPullSpeed;
    public final ModConfigSpec.DoubleValue tigerfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue tigerfishStamina;
    public final ModConfigSpec.DoubleValue tigerfishTensionGain;
    public final ModConfigSpec.DoubleValue tigerfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue tigerfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue tigerfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue tigerfishFakeOutChance;
    public final ModConfigSpec.DoubleValue tigerfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue tigerfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue tigerfishStaminaRegen;
    public final ModConfigSpec.DoubleValue pikeMinKg;
    public final ModConfigSpec.DoubleValue pikeMaxKg;
    public final ModConfigSpec.DoubleValue pikePullSpeed;
    public final ModConfigSpec.DoubleValue pikeFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue pikeStamina;
    public final ModConfigSpec.DoubleValue pikeTensionGain;
    public final ModConfigSpec.DoubleValue pikeSprintDurationSec;
    public final ModConfigSpec.DoubleValue pikeSprintIntervalSec;
    public final ModConfigSpec.DoubleValue pikeTurnIntervalSec;
    public final ModConfigSpec.DoubleValue pikeFakeOutChance;
    public final ModConfigSpec.DoubleValue pikeFatigueThreshold;
    public final ModConfigSpec.DoubleValue pikeFatigueDurationSec;
    public final ModConfigSpec.DoubleValue pikeStaminaRegen;
    public final ModConfigSpec.DoubleValue brownTroutMinKg;
    public final ModConfigSpec.DoubleValue brownTroutMaxKg;
    public final ModConfigSpec.DoubleValue brownTroutPullSpeed;
    public final ModConfigSpec.DoubleValue brownTroutFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue brownTroutStamina;
    public final ModConfigSpec.DoubleValue brownTroutTensionGain;
    public final ModConfigSpec.DoubleValue brownTroutSprintDurationSec;
    public final ModConfigSpec.DoubleValue brownTroutSprintIntervalSec;
    public final ModConfigSpec.DoubleValue brownTroutTurnIntervalSec;
    public final ModConfigSpec.DoubleValue brownTroutFakeOutChance;
    public final ModConfigSpec.DoubleValue brownTroutFatigueThreshold;
    public final ModConfigSpec.DoubleValue brownTroutFatigueDurationSec;
    public final ModConfigSpec.DoubleValue brownTroutStaminaRegen;
    public final ModConfigSpec.DoubleValue mirrorCarpMinKg;
    public final ModConfigSpec.DoubleValue mirrorCarpMaxKg;
    public final ModConfigSpec.DoubleValue mirrorCarpPullSpeed;
    public final ModConfigSpec.DoubleValue mirrorCarpFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue mirrorCarpStamina;
    public final ModConfigSpec.DoubleValue mirrorCarpTensionGain;
    public final ModConfigSpec.DoubleValue mirrorCarpSprintDurationSec;
    public final ModConfigSpec.DoubleValue mirrorCarpSprintIntervalSec;
    public final ModConfigSpec.DoubleValue mirrorCarpTurnIntervalSec;
    public final ModConfigSpec.DoubleValue mirrorCarpFakeOutChance;
    public final ModConfigSpec.DoubleValue mirrorCarpFatigueThreshold;
    public final ModConfigSpec.DoubleValue mirrorCarpFatigueDurationSec;
    public final ModConfigSpec.DoubleValue mirrorCarpStaminaRegen;
    public final ModConfigSpec.DoubleValue stripedBassMinKg;
    public final ModConfigSpec.DoubleValue stripedBassMaxKg;
    public final ModConfigSpec.DoubleValue stripedBassPullSpeed;
    public final ModConfigSpec.DoubleValue stripedBassFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue stripedBassStamina;
    public final ModConfigSpec.DoubleValue stripedBassTensionGain;
    public final ModConfigSpec.DoubleValue stripedBassSprintDurationSec;
    public final ModConfigSpec.DoubleValue stripedBassSprintIntervalSec;
    public final ModConfigSpec.DoubleValue stripedBassTurnIntervalSec;
    public final ModConfigSpec.DoubleValue stripedBassFakeOutChance;
    public final ModConfigSpec.DoubleValue stripedBassFatigueThreshold;
    public final ModConfigSpec.DoubleValue stripedBassFatigueDurationSec;
    public final ModConfigSpec.DoubleValue stripedBassStaminaRegen;
    public final ModConfigSpec.DoubleValue arapaimaMinKg;
    public final ModConfigSpec.DoubleValue arapaimaMaxKg;
    public final ModConfigSpec.DoubleValue arapaimaPullSpeed;
    public final ModConfigSpec.DoubleValue arapaimaFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue arapaimaStamina;
    public final ModConfigSpec.DoubleValue arapaimaTensionGain;
    public final ModConfigSpec.DoubleValue arapaimaSprintDurationSec;
    public final ModConfigSpec.DoubleValue arapaimaSprintIntervalSec;
    public final ModConfigSpec.DoubleValue arapaimaTurnIntervalSec;
    public final ModConfigSpec.DoubleValue arapaimaFakeOutChance;
    public final ModConfigSpec.DoubleValue arapaimaFatigueThreshold;
    public final ModConfigSpec.DoubleValue arapaimaFatigueDurationSec;
    public final ModConfigSpec.DoubleValue arapaimaStaminaRegen;
    public final ModConfigSpec.DoubleValue africanButterflyfishMinKg;
    public final ModConfigSpec.DoubleValue africanButterflyfishMaxKg;
    public final ModConfigSpec.DoubleValue africanButterflyfishPullSpeed;
    public final ModConfigSpec.DoubleValue africanButterflyfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue africanButterflyfishStamina;
    public final ModConfigSpec.DoubleValue africanButterflyfishTensionGain;
    public final ModConfigSpec.DoubleValue africanButterflyfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue africanButterflyfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue africanButterflyfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue africanButterflyfishFakeOutChance;
    public final ModConfigSpec.DoubleValue africanButterflyfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue africanButterflyfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue africanButterflyfishStaminaRegen;
    public final ModConfigSpec.DoubleValue jewelCichlidMinKg;
    public final ModConfigSpec.DoubleValue jewelCichlidMaxKg;
    public final ModConfigSpec.DoubleValue jewelCichlidPullSpeed;
    public final ModConfigSpec.DoubleValue jewelCichlidFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue jewelCichlidStamina;
    public final ModConfigSpec.DoubleValue jewelCichlidTensionGain;
    public final ModConfigSpec.DoubleValue jewelCichlidSprintDurationSec;
    public final ModConfigSpec.DoubleValue jewelCichlidSprintIntervalSec;
    public final ModConfigSpec.DoubleValue jewelCichlidTurnIntervalSec;
    public final ModConfigSpec.DoubleValue jewelCichlidFakeOutChance;
    public final ModConfigSpec.DoubleValue jewelCichlidFatigueThreshold;
    public final ModConfigSpec.DoubleValue jewelCichlidFatigueDurationSec;
    public final ModConfigSpec.DoubleValue jewelCichlidStaminaRegen;
    public final ModConfigSpec.DoubleValue guppyMinKg;
    public final ModConfigSpec.DoubleValue guppyMaxKg;
    public final ModConfigSpec.DoubleValue guppyPullSpeed;
    public final ModConfigSpec.DoubleValue guppyFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue guppyStamina;
    public final ModConfigSpec.DoubleValue guppyTensionGain;
    public final ModConfigSpec.DoubleValue guppySprintDurationSec;
    public final ModConfigSpec.DoubleValue guppySprintIntervalSec;
    public final ModConfigSpec.DoubleValue guppyTurnIntervalSec;
    public final ModConfigSpec.DoubleValue guppyFakeOutChance;
    public final ModConfigSpec.DoubleValue guppyFatigueThreshold;
    public final ModConfigSpec.DoubleValue guppyFatigueDurationSec;
    public final ModConfigSpec.DoubleValue guppyStaminaRegen;
    public final ModConfigSpec.DoubleValue elephantnoseMinKg;
    public final ModConfigSpec.DoubleValue elephantnoseMaxKg;
    public final ModConfigSpec.DoubleValue elephantnosePullSpeed;
    public final ModConfigSpec.DoubleValue elephantnoseFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue elephantnoseStamina;
    public final ModConfigSpec.DoubleValue elephantnoseTensionGain;
    public final ModConfigSpec.DoubleValue elephantnoseSprintDurationSec;
    public final ModConfigSpec.DoubleValue elephantnoseSprintIntervalSec;
    public final ModConfigSpec.DoubleValue elephantnoseTurnIntervalSec;
    public final ModConfigSpec.DoubleValue elephantnoseFakeOutChance;
    public final ModConfigSpec.DoubleValue elephantnoseFatigueThreshold;
    public final ModConfigSpec.DoubleValue elephantnoseFatigueDurationSec;
    public final ModConfigSpec.DoubleValue elephantnoseStaminaRegen;
    public final ModConfigSpec.DoubleValue goldenLoachMinKg;
    public final ModConfigSpec.DoubleValue goldenLoachMaxKg;
    public final ModConfigSpec.DoubleValue goldenLoachPullSpeed;
    public final ModConfigSpec.DoubleValue goldenLoachFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue goldenLoachStamina;
    public final ModConfigSpec.DoubleValue goldenLoachTensionGain;
    public final ModConfigSpec.DoubleValue goldenLoachSprintDurationSec;
    public final ModConfigSpec.DoubleValue goldenLoachSprintIntervalSec;
    public final ModConfigSpec.DoubleValue goldenLoachTurnIntervalSec;
    public final ModConfigSpec.DoubleValue goldenLoachFakeOutChance;
    public final ModConfigSpec.DoubleValue goldenLoachFatigueThreshold;
    public final ModConfigSpec.DoubleValue goldenLoachFatigueDurationSec;
    public final ModConfigSpec.DoubleValue goldenLoachStaminaRegen;
    public final ModConfigSpec.DoubleValue caveBlindfishMinKg;
    public final ModConfigSpec.DoubleValue caveBlindfishMaxKg;
    public final ModConfigSpec.DoubleValue caveBlindfishPullSpeed;
    public final ModConfigSpec.DoubleValue caveBlindfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue caveBlindfishStamina;
    public final ModConfigSpec.DoubleValue caveBlindfishTensionGain;
    public final ModConfigSpec.DoubleValue caveBlindfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue caveBlindfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue caveBlindfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue caveBlindfishFakeOutChance;
    public final ModConfigSpec.DoubleValue caveBlindfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue caveBlindfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue caveBlindfishStaminaRegen;
    public final ModConfigSpec.DoubleValue northAfricanCatfishMinKg;
    public final ModConfigSpec.DoubleValue northAfricanCatfishMaxKg;
    public final ModConfigSpec.DoubleValue northAfricanCatfishPullSpeed;
    public final ModConfigSpec.DoubleValue northAfricanCatfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue northAfricanCatfishStamina;
    public final ModConfigSpec.DoubleValue northAfricanCatfishTensionGain;
    public final ModConfigSpec.DoubleValue northAfricanCatfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue northAfricanCatfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue northAfricanCatfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue northAfricanCatfishFakeOutChance;
    public final ModConfigSpec.DoubleValue northAfricanCatfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue northAfricanCatfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue northAfricanCatfishStaminaRegen;
    public final ModConfigSpec.DoubleValue rainbowTroutMinKg;
    public final ModConfigSpec.DoubleValue rainbowTroutMaxKg;
    public final ModConfigSpec.DoubleValue rainbowTroutPullSpeed;
    public final ModConfigSpec.DoubleValue rainbowTroutFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue rainbowTroutStamina;
    public final ModConfigSpec.DoubleValue rainbowTroutTensionGain;
    public final ModConfigSpec.DoubleValue rainbowTroutSprintDurationSec;
    public final ModConfigSpec.DoubleValue rainbowTroutSprintIntervalSec;
    public final ModConfigSpec.DoubleValue rainbowTroutTurnIntervalSec;
    public final ModConfigSpec.DoubleValue rainbowTroutFakeOutChance;
    public final ModConfigSpec.DoubleValue rainbowTroutFatigueThreshold;
    public final ModConfigSpec.DoubleValue rainbowTroutFatigueDurationSec;
    public final ModConfigSpec.DoubleValue rainbowTroutStaminaRegen;
    public final ModConfigSpec.DoubleValue arcticCharMinKg;
    public final ModConfigSpec.DoubleValue arcticCharMaxKg;
    public final ModConfigSpec.DoubleValue arcticCharPullSpeed;
    public final ModConfigSpec.DoubleValue arcticCharFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue arcticCharStamina;
    public final ModConfigSpec.DoubleValue arcticCharTensionGain;
    public final ModConfigSpec.DoubleValue arcticCharSprintDurationSec;
    public final ModConfigSpec.DoubleValue arcticCharSprintIntervalSec;
    public final ModConfigSpec.DoubleValue arcticCharTurnIntervalSec;
    public final ModConfigSpec.DoubleValue arcticCharFakeOutChance;
    public final ModConfigSpec.DoubleValue arcticCharFatigueThreshold;
    public final ModConfigSpec.DoubleValue arcticCharFatigueDurationSec;
    public final ModConfigSpec.DoubleValue arcticCharStaminaRegen;
    public final ModConfigSpec.DoubleValue burbotMinKg;
    public final ModConfigSpec.DoubleValue burbotMaxKg;
    public final ModConfigSpec.DoubleValue burbotPullSpeed;
    public final ModConfigSpec.DoubleValue burbotFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue burbotStamina;
    public final ModConfigSpec.DoubleValue burbotTensionGain;
    public final ModConfigSpec.DoubleValue burbotSprintDurationSec;
    public final ModConfigSpec.DoubleValue burbotSprintIntervalSec;
    public final ModConfigSpec.DoubleValue burbotTurnIntervalSec;
    public final ModConfigSpec.DoubleValue burbotFakeOutChance;
    public final ModConfigSpec.DoubleValue burbotFatigueThreshold;
    public final ModConfigSpec.DoubleValue burbotFatigueDurationSec;
    public final ModConfigSpec.DoubleValue burbotStaminaRegen;
    public final ModConfigSpec.DoubleValue whitefishMinKg;
    public final ModConfigSpec.DoubleValue whitefishMaxKg;
    public final ModConfigSpec.DoubleValue whitefishPullSpeed;
    public final ModConfigSpec.DoubleValue whitefishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue whitefishStamina;
    public final ModConfigSpec.DoubleValue whitefishTensionGain;
    public final ModConfigSpec.DoubleValue whitefishSprintDurationSec;
    public final ModConfigSpec.DoubleValue whitefishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue whitefishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue whitefishFakeOutChance;
    public final ModConfigSpec.DoubleValue whitefishFatigueThreshold;
    public final ModConfigSpec.DoubleValue whitefishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue whitefishStaminaRegen;
    public final ModConfigSpec.DoubleValue taimenMinKg;
    public final ModConfigSpec.DoubleValue taimenMaxKg;
    public final ModConfigSpec.DoubleValue taimenPullSpeed;
    public final ModConfigSpec.DoubleValue taimenFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue taimenStamina;
    public final ModConfigSpec.DoubleValue taimenTensionGain;
    public final ModConfigSpec.DoubleValue taimenSprintDurationSec;
    public final ModConfigSpec.DoubleValue taimenSprintIntervalSec;
    public final ModConfigSpec.DoubleValue taimenTurnIntervalSec;
    public final ModConfigSpec.DoubleValue taimenFakeOutChance;
    public final ModConfigSpec.DoubleValue taimenFatigueThreshold;
    public final ModConfigSpec.DoubleValue taimenFatigueDurationSec;
    public final ModConfigSpec.DoubleValue taimenStaminaRegen;
    public final ModConfigSpec.DoubleValue arcticCodMinKg;
    public final ModConfigSpec.DoubleValue arcticCodMaxKg;
    public final ModConfigSpec.DoubleValue arcticCodPullSpeed;
    public final ModConfigSpec.DoubleValue arcticCodFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue arcticCodStamina;
    public final ModConfigSpec.DoubleValue arcticCodTensionGain;
    public final ModConfigSpec.DoubleValue arcticCodSprintDurationSec;
    public final ModConfigSpec.DoubleValue arcticCodSprintIntervalSec;
    public final ModConfigSpec.DoubleValue arcticCodTurnIntervalSec;
    public final ModConfigSpec.DoubleValue arcticCodFakeOutChance;
    public final ModConfigSpec.DoubleValue arcticCodFatigueThreshold;
    public final ModConfigSpec.DoubleValue arcticCodFatigueDurationSec;
    public final ModConfigSpec.DoubleValue arcticCodStaminaRegen;
    public final ModConfigSpec.DoubleValue capelinMinKg;
    public final ModConfigSpec.DoubleValue capelinMaxKg;
    public final ModConfigSpec.DoubleValue capelinPullSpeed;
    public final ModConfigSpec.DoubleValue capelinFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue capelinStamina;
    public final ModConfigSpec.DoubleValue capelinTensionGain;
    public final ModConfigSpec.DoubleValue capelinSprintDurationSec;
    public final ModConfigSpec.DoubleValue capelinSprintIntervalSec;
    public final ModConfigSpec.DoubleValue capelinTurnIntervalSec;
    public final ModConfigSpec.DoubleValue capelinFakeOutChance;
    public final ModConfigSpec.DoubleValue capelinFatigueThreshold;
    public final ModConfigSpec.DoubleValue capelinFatigueDurationSec;
    public final ModConfigSpec.DoubleValue capelinStaminaRegen;
    public final ModConfigSpec.DoubleValue atlanticHerringMinKg;
    public final ModConfigSpec.DoubleValue atlanticHerringMaxKg;
    public final ModConfigSpec.DoubleValue atlanticHerringPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticHerringFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticHerringStamina;
    public final ModConfigSpec.DoubleValue atlanticHerringTensionGain;
    public final ModConfigSpec.DoubleValue atlanticHerringSprintDurationSec;
    public final ModConfigSpec.DoubleValue atlanticHerringSprintIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticHerringTurnIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticHerringFakeOutChance;
    public final ModConfigSpec.DoubleValue atlanticHerringFatigueThreshold;
    public final ModConfigSpec.DoubleValue atlanticHerringFatigueDurationSec;
    public final ModConfigSpec.DoubleValue atlanticHerringStaminaRegen;
    public final ModConfigSpec.DoubleValue wolffishMinKg;
    public final ModConfigSpec.DoubleValue wolffishMaxKg;
    public final ModConfigSpec.DoubleValue wolffishPullSpeed;
    public final ModConfigSpec.DoubleValue wolffishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue wolffishStamina;
    public final ModConfigSpec.DoubleValue wolffishTensionGain;
    public final ModConfigSpec.DoubleValue wolffishSprintDurationSec;
    public final ModConfigSpec.DoubleValue wolffishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue wolffishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue wolffishFakeOutChance;
    public final ModConfigSpec.DoubleValue wolffishFatigueThreshold;
    public final ModConfigSpec.DoubleValue wolffishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue wolffishStaminaRegen;
    public final ModConfigSpec.DoubleValue redfishMinKg;
    public final ModConfigSpec.DoubleValue redfishMaxKg;
    public final ModConfigSpec.DoubleValue redfishPullSpeed;
    public final ModConfigSpec.DoubleValue redfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue redfishStamina;
    public final ModConfigSpec.DoubleValue redfishTensionGain;
    public final ModConfigSpec.DoubleValue redfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue redfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue redfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue redfishFakeOutChance;
    public final ModConfigSpec.DoubleValue redfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue redfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue redfishStaminaRegen;
    public final ModConfigSpec.DoubleValue sablefishMinKg;
    public final ModConfigSpec.DoubleValue sablefishMaxKg;
    public final ModConfigSpec.DoubleValue sablefishPullSpeed;
    public final ModConfigSpec.DoubleValue sablefishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue sablefishStamina;
    public final ModConfigSpec.DoubleValue sablefishTensionGain;
    public final ModConfigSpec.DoubleValue sablefishSprintDurationSec;
    public final ModConfigSpec.DoubleValue sablefishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue sablefishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue sablefishFakeOutChance;
    public final ModConfigSpec.DoubleValue sablefishFatigueThreshold;
    public final ModConfigSpec.DoubleValue sablefishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue sablefishStaminaRegen;
    public final ModConfigSpec.DoubleValue atlanticCodMinKg;
    public final ModConfigSpec.DoubleValue atlanticCodMaxKg;
    public final ModConfigSpec.DoubleValue atlanticCodPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticCodFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue atlanticCodStamina;
    public final ModConfigSpec.DoubleValue atlanticCodTensionGain;
    public final ModConfigSpec.DoubleValue atlanticCodSprintDurationSec;
    public final ModConfigSpec.DoubleValue atlanticCodSprintIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticCodTurnIntervalSec;
    public final ModConfigSpec.DoubleValue atlanticCodFakeOutChance;
    public final ModConfigSpec.DoubleValue atlanticCodFatigueThreshold;
    public final ModConfigSpec.DoubleValue atlanticCodFatigueDurationSec;
    public final ModConfigSpec.DoubleValue atlanticCodStaminaRegen;
    public final ModConfigSpec.DoubleValue greenlandHalibutMinKg;
    public final ModConfigSpec.DoubleValue greenlandHalibutMaxKg;
    public final ModConfigSpec.DoubleValue greenlandHalibutPullSpeed;
    public final ModConfigSpec.DoubleValue greenlandHalibutFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue greenlandHalibutStamina;
    public final ModConfigSpec.DoubleValue greenlandHalibutTensionGain;
    public final ModConfigSpec.DoubleValue greenlandHalibutSprintDurationSec;
    public final ModConfigSpec.DoubleValue greenlandHalibutSprintIntervalSec;
    public final ModConfigSpec.DoubleValue greenlandHalibutTurnIntervalSec;
    public final ModConfigSpec.DoubleValue greenlandHalibutFakeOutChance;
    public final ModConfigSpec.DoubleValue greenlandHalibutFatigueThreshold;
    public final ModConfigSpec.DoubleValue greenlandHalibutFatigueDurationSec;
    public final ModConfigSpec.DoubleValue greenlandHalibutStaminaRegen;
    public final ModConfigSpec.DoubleValue greenlandSharkMinKg;
    public final ModConfigSpec.DoubleValue greenlandSharkMaxKg;
    public final ModConfigSpec.DoubleValue greenlandSharkPullSpeed;
    public final ModConfigSpec.DoubleValue greenlandSharkFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue greenlandSharkStamina;
    public final ModConfigSpec.DoubleValue greenlandSharkTensionGain;
    public final ModConfigSpec.DoubleValue greenlandSharkSprintDurationSec;
    public final ModConfigSpec.DoubleValue greenlandSharkSprintIntervalSec;
    public final ModConfigSpec.DoubleValue greenlandSharkTurnIntervalSec;
    public final ModConfigSpec.DoubleValue greenlandSharkFakeOutChance;
    public final ModConfigSpec.DoubleValue greenlandSharkFatigueThreshold;
    public final ModConfigSpec.DoubleValue greenlandSharkFatigueDurationSec;
    public final ModConfigSpec.DoubleValue greenlandSharkStaminaRegen;
    public final ModConfigSpec.DoubleValue sailfishMinKg;
    public final ModConfigSpec.DoubleValue sailfishMaxKg;
    public final ModConfigSpec.DoubleValue sailfishPullSpeed;
    public final ModConfigSpec.DoubleValue sailfishFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue sailfishStamina;
    public final ModConfigSpec.DoubleValue sailfishTensionGain;
    public final ModConfigSpec.DoubleValue sailfishSprintDurationSec;
    public final ModConfigSpec.DoubleValue sailfishSprintIntervalSec;
    public final ModConfigSpec.DoubleValue sailfishTurnIntervalSec;
    public final ModConfigSpec.DoubleValue sailfishFakeOutChance;
    public final ModConfigSpec.DoubleValue sailfishFatigueThreshold;
    public final ModConfigSpec.DoubleValue sailfishFatigueDurationSec;
    public final ModConfigSpec.DoubleValue sailfishStaminaRegen;
    public final ModConfigSpec.DoubleValue mahiMahiMinKg;
    public final ModConfigSpec.DoubleValue mahiMahiMaxKg;
    public final ModConfigSpec.DoubleValue mahiMahiPullSpeed;
    public final ModConfigSpec.DoubleValue mahiMahiFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue mahiMahiStamina;
    public final ModConfigSpec.DoubleValue mahiMahiTensionGain;
    public final ModConfigSpec.DoubleValue mahiMahiSprintDurationSec;
    public final ModConfigSpec.DoubleValue mahiMahiSprintIntervalSec;
    public final ModConfigSpec.DoubleValue mahiMahiTurnIntervalSec;
    public final ModConfigSpec.DoubleValue mahiMahiFakeOutChance;
    public final ModConfigSpec.DoubleValue mahiMahiFatigueThreshold;
    public final ModConfigSpec.DoubleValue mahiMahiFatigueDurationSec;
    public final ModConfigSpec.DoubleValue mahiMahiStaminaRegen;
    public final ModConfigSpec.DoubleValue alligatorGarMinKg;
    public final ModConfigSpec.DoubleValue alligatorGarMaxKg;
    public final ModConfigSpec.DoubleValue alligatorGarPullSpeed;
    public final ModConfigSpec.DoubleValue alligatorGarFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue alligatorGarStamina;
    public final ModConfigSpec.DoubleValue alligatorGarTensionGain;
    public final ModConfigSpec.DoubleValue alligatorGarSprintDurationSec;
    public final ModConfigSpec.DoubleValue alligatorGarSprintIntervalSec;
    public final ModConfigSpec.DoubleValue alligatorGarTurnIntervalSec;
    public final ModConfigSpec.DoubleValue alligatorGarFakeOutChance;
    public final ModConfigSpec.DoubleValue alligatorGarFatigueThreshold;
    public final ModConfigSpec.DoubleValue alligatorGarFatigueDurationSec;
    public final ModConfigSpec.DoubleValue alligatorGarStaminaRegen;
    public final ModConfigSpec.DoubleValue giantSiameseCarpMinKg;
    public final ModConfigSpec.DoubleValue giantSiameseCarpMaxKg;
    public final ModConfigSpec.DoubleValue giantSiameseCarpPullSpeed;
    public final ModConfigSpec.DoubleValue giantSiameseCarpFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue giantSiameseCarpStamina;
    public final ModConfigSpec.DoubleValue giantSiameseCarpTensionGain;
    public final ModConfigSpec.DoubleValue giantSiameseCarpSprintDurationSec;
    public final ModConfigSpec.DoubleValue giantSiameseCarpSprintIntervalSec;
    public final ModConfigSpec.DoubleValue giantSiameseCarpTurnIntervalSec;
    public final ModConfigSpec.DoubleValue giantSiameseCarpFakeOutChance;
    public final ModConfigSpec.DoubleValue giantSiameseCarpFatigueThreshold;
    public final ModConfigSpec.DoubleValue giantSiameseCarpFatigueDurationSec;
    public final ModConfigSpec.DoubleValue giantSiameseCarpStaminaRegen;
    public final ModConfigSpec.DoubleValue electricEelMinKg;
    public final ModConfigSpec.DoubleValue electricEelMaxKg;
    public final ModConfigSpec.DoubleValue electricEelPullSpeed;
    public final ModConfigSpec.DoubleValue electricEelFatiguedPullSpeed;
    public final ModConfigSpec.DoubleValue electricEelStamina;
    public final ModConfigSpec.DoubleValue electricEelTensionGain;
    public final ModConfigSpec.DoubleValue electricEelSprintDurationSec;
    public final ModConfigSpec.DoubleValue electricEelSprintIntervalSec;
    public final ModConfigSpec.DoubleValue electricEelTurnIntervalSec;
    public final ModConfigSpec.DoubleValue electricEelFakeOutChance;
    public final ModConfigSpec.DoubleValue electricEelFatigueThreshold;
    public final ModConfigSpec.DoubleValue electricEelFatigueDurationSec;
    public final ModConfigSpec.DoubleValue electricEelStaminaRegen;
    // ==== END 鱼种数值声明 ====

    // ==== BEGIN 玩法数值声明 ====
    public final ModConfigSpec.DoubleValue rodWoodMaxWeightKg;
    public final ModConfigSpec.DoubleValue rodWoodHookWindowBonus;
    public final ModConfigSpec.DoubleValue rodWoodTensionCeiling;
    public final ModConfigSpec.DoubleValue rodWoodTensionRecover;
    public final ModConfigSpec.DoubleValue rodWoodStaminaDrain;
    public final ModConfigSpec.DoubleValue rodWoodMaterialFactor;
    public final ModConfigSpec.IntValue rodWoodTier;
    public final ModConfigSpec.DoubleValue rodIronMaxWeightKg;
    public final ModConfigSpec.DoubleValue rodIronHookWindowBonus;
    public final ModConfigSpec.DoubleValue rodIronTensionCeiling;
    public final ModConfigSpec.DoubleValue rodIronTensionRecover;
    public final ModConfigSpec.DoubleValue rodIronStaminaDrain;
    public final ModConfigSpec.DoubleValue rodIronMaterialFactor;
    public final ModConfigSpec.IntValue rodIronTier;
    public final ModConfigSpec.DoubleValue rodGoldMaxWeightKg;
    public final ModConfigSpec.DoubleValue rodGoldHookWindowBonus;
    public final ModConfigSpec.DoubleValue rodGoldTensionCeiling;
    public final ModConfigSpec.DoubleValue rodGoldTensionRecover;
    public final ModConfigSpec.DoubleValue rodGoldStaminaDrain;
    public final ModConfigSpec.DoubleValue rodGoldMaterialFactor;
    public final ModConfigSpec.IntValue rodGoldTier;
    public final ModConfigSpec.DoubleValue rodDiamondMaxWeightKg;
    public final ModConfigSpec.DoubleValue rodDiamondHookWindowBonus;
    public final ModConfigSpec.DoubleValue rodDiamondTensionCeiling;
    public final ModConfigSpec.DoubleValue rodDiamondTensionRecover;
    public final ModConfigSpec.DoubleValue rodDiamondStaminaDrain;
    public final ModConfigSpec.DoubleValue rodDiamondMaterialFactor;
    public final ModConfigSpec.IntValue rodDiamondTier;
    public final ModConfigSpec.DoubleValue rodNetheriteMaxWeightKg;
    public final ModConfigSpec.DoubleValue rodNetheriteHookWindowBonus;
    public final ModConfigSpec.DoubleValue rodNetheriteTensionCeiling;
    public final ModConfigSpec.DoubleValue rodNetheriteTensionRecover;
    public final ModConfigSpec.DoubleValue rodNetheriteStaminaDrain;
    public final ModConfigSpec.DoubleValue rodNetheriteMaterialFactor;
    public final ModConfigSpec.IntValue rodNetheriteTier;
    public final ModConfigSpec.DoubleValue tensionBaseCeiling;
    public final ModConfigSpec.DoubleValue pullSprint;
    public final ModConfigSpec.DoubleValue counterBonus;
    public final ModConfigSpec.DoubleValue counterPenalty;
    public final ModConfigSpec.DoubleValue minReelGainRatio;
    public final ModConfigSpec.DoubleValue maxFishSlowdown;
    public final ModConfigSpec.DoubleValue dampingToCapRatio;
    public final ModConfigSpec.DoubleValue capDampingBonus;
    public final ModConfigSpec.DoubleValue totalDrainTarget;
    public final ModConfigSpec.DoubleValue nearShoreBase;
    public final ModConfigSpec.DoubleValue staminaEpsilonFloor;
    public final ModConfigSpec.DoubleValue staminaEpsilonRatio;
    public final ModConfigSpec.IntValue inputStaleLimit;
    public final ModConfigSpec.DoubleValue tierSpeedBase;
    public final ModConfigSpec.DoubleValue tierStaminaResistBase;
    public final ModConfigSpec.DoubleValue tierGoldSpeedScale;
    public final ModConfigSpec.DoubleValue tierGoldStaminaScale;
    public final ModConfigSpec.DoubleValue tierRedSpeedScale;
    public final ModConfigSpec.DoubleValue tierRedStaminaScale;
    public final ModConfigSpec.DoubleValue tierRedStaminaMin;
    public final ModConfigSpec.DoubleValue fatiguePullMultiplier;
    public final ModConfigSpec.DoubleValue reelFloorDrainBase;
    public final ModConfigSpec.DoubleValue reelFloorDrainPerDamping;
    public final ModConfigSpec.DoubleValue reelFloorDrainCap;
    public final ModConfigSpec.DoubleValue floorStruggleBase;
    public final ModConfigSpec.DoubleValue floorStrugglePerTier;
    public final ModConfigSpec.DoubleValue floorStruggleBonusSteady;
    public final ModConfigSpec.DoubleValue floorStruggleBonusSluggish;
    public final ModConfigSpec.DoubleValue floorStruggleBonusBurst;
    public final ModConfigSpec.DoubleValue floorStruggleBonusErratic;
    public final ModConfigSpec.DoubleValue reelResistThreshold;
    public final ModConfigSpec.DoubleValue reelResistTierWhite;
    public final ModConfigSpec.DoubleValue reelResistTierGreen;
    public final ModConfigSpec.DoubleValue reelResistTierBlue;
    public final ModConfigSpec.DoubleValue reelResistTierPurple;
    public final ModConfigSpec.DoubleValue reelResistTierGold;
    public final ModConfigSpec.DoubleValue reelResistTierRed;
    public final ModConfigSpec.DoubleValue reelResistStratSteady;
    public final ModConfigSpec.DoubleValue reelResistStratSluggish;
    public final ModConfigSpec.DoubleValue reelResistStratBurst;
    public final ModConfigSpec.DoubleValue reelResistStratErratic;
    public final ModConfigSpec.DoubleValue reelResistDampingOffset;
    public final ModConfigSpec.DoubleValue reelResistDampingCap;
    public final ModConfigSpec.DoubleValue fatiguedReelMinGain;
    public final ModConfigSpec.DoubleValue fatiguedTensionGainScale;
    public final ModConfigSpec.DoubleValue fatiguedTensionRecoverScale;
    public final ModConfigSpec.DoubleValue fatigueThresholdScale;
    public final ModConfigSpec.DoubleValue idleFallFloor;
    public final ModConfigSpec.DoubleValue struggleSecondsBase;
    public final ModConfigSpec.DoubleValue struggleSecondsPerTier;
    public final ModConfigSpec.DoubleValue restSecondsBase;
    public final ModConfigSpec.DoubleValue restSecondsPerTier;
    public final ModConfigSpec.IntValue alpineLakeMinY;
    public final ModConfigSpec.DoubleValue coldWaterMaxTemp;
    public final ModConfigSpec.IntValue brokenIceToPackIceMinutes;
    public final ModConfigSpec.IntValue packIceToIceMinutes;
    public final ModConfigSpec.IntValue icePickDurabilityCost;
    public final ModConfigSpec.DoubleValue iceHoleLineWearMultiplier;
    public final ModConfigSpec.DoubleValue iceHoleHighValueBonus;
    public final ModConfigSpec.DoubleValue openIceBiteTimeMultiplier;
    public final ModConfigSpec.DoubleValue openIceHighValuePenalty;
    public final ModConfigSpec.IntValue fullChargeTicks;
    public final ModConfigSpec.IntValue minChargeTicks;
    public final ModConfigSpec.IntValue chumMinAirTicks;
    public final ModConfigSpec.IntValue oversizeFishAction;
    public final ModConfigSpec.IntValue compassSearchRadius;
    public final ModConfigSpec.IntValue bigCatchAnnounceValue;
    public final ModConfigSpec.IntValue bigCatchAnnounceMinIntervalSec;
    // ==== END 玩法数值声明 ====

    // ---------------------------------------------------------------- 渲染
    public final ModConfigSpec.IntValue ghostLineBeyondBlocks;
    public final ModConfigSpec.BooleanValue hookCameraFollow;
    public final ModConfigSpec.DoubleValue hookCameraFollowStrength;
    public final ModConfigSpec.DoubleValue reelZoomPercent;

    private Config(ModConfigSpec.Builder b) {
        // ---------------------------------------------------------- 抛投
        b.comment("""
                抛投 / Casting
                注意：抛投已在 M2 改成**视角抛投**（低头近 / 平视中 / 抬头远），
                所以早期设计的"蓄力绿区 / 落点误差"配置项已删除 ——
                留着它们只会让人以为能调。""").push("cast");
        chargeTimeSeconds = b
                .comment("蓄力到满所需秒数（只影响 HUD 蓄力条的显示节奏）")
                .defineInRange("chargeTimeSeconds", 1.6D, 0.2D, 10.0D);
        b.pop();

        // ------------------------------------------------------ 提竿窗口
        b.comment("提竿窗口 / Hook window（用户定案：基础 2.0 秒）").push("hookWindow");
        hookWindowBase = b.comment("基础窗口秒数（鱼钩已删除，现在只有这一个来源）")
                .defineInRange("base", 2.0D, 0.05D, 5.0D);
        hookWindowMin = b.comment("窗口下限秒数（加成算完后不会低于它）")
                .defineInRange("min", 1.0D, 0.05D, 5.0D);
        b.pop();

        // ---------------------------------------------------------- 张力
        b.comment("""
                张力模型 / Tension（反馈以鱼线颜色为主，HUD 只做辅助）
                ⚠ whiteMax / yellowMax 是**相对于基准张力上限 100** 的值：
                代码里按比例使用（whiteMax/100），好线把上限抬到 300 时
                白区黄区会同步放大，否则"好线反而全程红区"。""").push("tension");
        tensionReelGain = b.comment("收线时每 tick 增加的张力")
                .defineInRange("reelGain", 0.9D, 0.0D, 20.0D);
        tensionReleaseLoss = b.comment("放线时每 tick 减少的张力")
                .defineInRange("releaseLoss", 1.6D, 0.0D, 20.0D);
        tensionIdleLoss = b.comment("不操作时每 tick 自然松弛的张力")
                .defineInRange("idleLoss", 0.35D, 0.0D, 20.0D);
        tensionWhiteMax = b.comment("白区（安全）上限，基准 100 的百分比")
                .defineInRange("whiteMax", 55.0D, 1.0D, 100.0D);
        tensionYellowMax = b.comment("黄区上限（超过进红区），基准 100 的百分比")
                .defineInRange("yellowMax", 82.0D, 1.0D, 100.0D);
        tensionCriticalFlash = b.comment("鱼线开始深红闪烁的张力值（基准 100 的百分比）")
                .defineInRange("criticalFlash", 98.0D, 1.0D, 100.0D);
        tensionBaseRecover = b.comment("基础张力回复速率（点/tick，放线时由红转白的速度）")
                .defineInRange("baseRecover", 1.6D, 0.0D, 20.0D);
        b.pop();

        // ------------------------------------------------------ 咬钩等待
        b.comment("咬钩等待 / Bite timing（玩家扛不住长时间干等，上限压到 20 秒）").push("bite");
        biteMinWait = b.comment("最短等待秒数")
                .defineInRange("minWaitSeconds", 10.0D, 0.1D, 60.0D);
        biteMaxWait = b.comment("最长等待秒数的随机量（实际等待 = min + rand × 本值）")
                .defineInRange("maxWaitSeconds", 14.0D, 0.1D, 120.0D);
        biteHardCap = b.comment("硬上限秒数（任何修正后都不得超过）")
                .defineInRange("hardCapSeconds", 30.0D, 1.0D, 300.0D);

        // ★ 台钓专属减时 + 总下限（用户定案 2026-10-02）：
        //   "台钓的咬钩时间单独下降 30%，台钓饵料对咬钩时间也进行下降……
        //    以上提到的数值属于**加算**，但配合打窝至多做到 3 秒一条鱼。"
        //
        //   所以等待是这样算出来的（见 FishingSession.beginWaiting）：
        //     基础 = min + rand × max
        //     减时 = 台钓 30% + 该饵档位的百分比   ← 加算，不是连乘
        //     实得 = 基础 × (1 − 减时) × 打窝倍率 × (流动 1.3)
        //     最后落到 [floorSeconds, hardCapSeconds] 区间里
        //
        //   ⚠ 30% **只给台钓竿**：路亚竿的拟饵槽不吃这一项（用户只说了台钓）。
        //   ⚠ 饵料那 6 个数是**按品质档位**（0白 1绿 2蓝 3紫 4金 5红），
        //     和 BaitItem.tier() / equippedBaitTier() 同一套刻度 —— 新增读档位的
        //     代码必须走 equippedBaitTier()，别自己再数一遍（见 AGENTS.md 第二条）。
        //   ⚠ 下限必须存在：叠加减时 + 打窝之后最小那一杆会算到 2.2 秒，
        //     没有下限就等于"越堆越快、没有尽头"，用户要的是**至多 3 秒一条**。
        biteFloorSeconds = b.comment("等待下限秒数（台钓减时 + 打窝叠加后也不会低于它；用户定案 3 秒）")
                .defineInRange("floorSeconds", 3.0D, 0.1D, 60.0D);
        biteFloatRodCutPercent = b.comment("台钓竿专属：咬钩等待下降百分比（路亚竿不吃这一项）")
                .defineInRange("floatRodCutPercent", 30.0D, 0.0D, 100.0D);
        biteBaitCutWhitePercent = b.comment("白档饵（0）的额外下降百分比")
                .defineInRange("baitCutWhitePercent", 5.0D, 0.0D, 100.0D);
        biteBaitCutGreenPercent = b.comment("绿档饵（1）的额外下降百分比")
                .defineInRange("baitCutGreenPercent", 8.0D, 0.0D, 100.0D);
        biteBaitCutBluePercent = b.comment("蓝档饵（2）的额外下降百分比（当前没有蓝档饵，留给以后）")
                .defineInRange("baitCutBluePercent", 12.0D, 0.0D, 100.0D);
        biteBaitCutPurplePercent = b.comment("紫档饵（3，活虾）的额外下降百分比")
                .defineInRange("baitCutPurplePercent", 16.0D, 0.0D, 100.0D);
        biteBaitCutGoldPercent = b.comment("金档饵（4，发光饵）的额外下降百分比")
                .defineInRange("baitCutGoldPercent", 18.5D, 0.0D, 100.0D);
        biteBaitCutRedPercent = b.comment("红档饵（5，秘制香饵）的额外下降百分比")
                .defineInRange("baitCutRedPercent", 21.0D, 0.0D, 100.0D);
        b.pop();

        // ------------------------------------------ 时间 / 天气 / 月相
        // 用户 2026-10-02 定案的口径（原话）：
        //   "时间对鱼权重影响我最多给到所有受加成鱼加起来一共 10%。天气一样。
        //    月相至多给 5% 金红总爆率加成，15% 紫爆率加成。"
        // 并给出两个算例作为口径基准：
        //   2 种鱼各 50%、其中 1 种受加成  → 40 / 60
        //   4 种鱼各 25%、其中 2 种受加成  → 20 / 20 / 30 / 30
        //
        // ⚠ 落地方式（很容易写错，见 docs/24 §4.1）：
        //   倍率**不写死**，而是**反解** —— 让"受加成那一组"的归一化总占比恰好 +deltaPct。
        //   两个算例反解出来的 k 都是 1.5；若写死 1.2 只能得到 54.55/45.45（+4.55 点），
        //   与算例不符。所以那几个 deltaPct 才是"旋钮"，k 是算出来的。
        //
        // ⚠ 只加不减：受加成鱼的权重乘 k，**其余鱼的权重一个都不动**。
        //   它们占比下降（50 → 40）纯粹是归一化的必然结果，不是惩罚项。
        //   这是与 v1（"相反时段 ×0.45"那种真减权）最大的区别。
        b.comment("""
                时间 / 天气 / 月相 / Time · Weather · Moon
                环境只改"水里此刻有哪些鱼愿意咬钩"，**不改咬钩节奏**（唯一例外见 biteNightCutPercent）。

                口径（用户定案）：受加成那一组鱼的**归一化总占比**最多提升这么多个百分点；
                组内每条鱼乘同一个倍率，倍率由池子反解得出（不写死）。
                只加不减 —— 组外鱼的权重不会被乘任何小于 1 的数。""").push("environment");
        environmentEnabled = b.comment("环境系统总开关（false = 完全回到未实现前的行为）")
                .define("enabled", true);
        envTimeDeltaPct = b.comment("时段组占比最多提升几个百分点（50/50 一种加成 → 40/60 就是 10）")
                .defineInRange("timeDeltaPct", 10.0D, 0.0D, 20.0D);
        envWeatherDeltaPct = b.comment("天气组占比最多提升几个百分点")
                .defineInRange("weatherDeltaPct", 10.0D, 0.0D, 20.0D);
        envMoonGoldRedDeltaPct = b.comment("月相：所有金红鱼一起分享的爆率预算（百分点）")
                .defineInRange("moonGoldRedDeltaPct", 5.0D, 0.0D, 15.0D);
        envMoonPurpleDeltaPct = b.comment("月相：所有紫鱼一起分享的爆率预算（百分点）")
                .defineInRange("moonPurpleDeltaPct", 15.0D, 0.0D, 30.0D);
        envMoonShareMode = b.comment("月相预算分法：0 = 组内同倍率（总和可能略微超出），1 = 按条数均摊（恰好用完预算）")
                .defineInRange("moonShareMode", 1, 0, 1);
        envShareCapPct = b.comment("安全阀：任何一组的占比不得超过这个值（防极端池子算出组占比 100% 的倍率）")
                .defineInRange("shareCapPct", 90.0D, 50.0D, 100.0D);
        envShowCondition = b.comment("在 HUD 上显示当前时段 / 天气 / 月相（一行小字）")
                .define("showCondition", true);
        envLogToChat = b.comment("调试：每次咬钩把环境与各组实际吃到的增量打到聊天栏")
                .define("logToChat", false);
        biteNightCutPercent = b.comment("夜间台钓的咬钩等待减免百分比（0 = 关闭；只作用于夜晚 + 台钓竿）")
                .defineInRange("nightCutPercent", 10.0D, 0.0D, 50.0D);
        b.pop();

        // --------------------------------------------------- 配件属性合成
        b.comment("""
                六槽配件属性合成 / Part attribute composition
                权重决定"某项属性由谁主导"，上限防止数值失控。
                详见 docs/11-配件系统详表.md §2。""").push("parts");
        weightLineOnTension = b.comment("鱼线对张力上限的贡献权重")
                .defineInRange("lineOnTension", 0.60D, 0.0D, 1.0D);
        weightRodOnTension = b.comment("鱼竿对张力上限的贡献权重")
                .defineInRange("rodOnTension", 0.40D, 0.0D, 1.0D);
        weightLineOnRecover = b.comment("鱼线对张力回复速度的贡献权重")
                .defineInRange("lineOnRecover", 0.60D, 0.0D, 1.0D);
        weightRodOnRecover = b.comment("鱼竿对张力回复速度的贡献权重")
                .defineInRange("rodOnRecover", 0.40D, 0.0D, 1.0D);
        weightReelOnDrain = b.comment("渔轮对体力削减的贡献权重")
                .defineInRange("reelOnDrain", 0.60D, 0.0D, 1.0D);
        weightRodOnDrain = b.comment("鱼竿对体力削减的贡献权重")
                .defineInRange("rodOnDrain", 0.40D, 0.0D, 1.0D);
        capTensionCeiling = b.comment("张力上限倍率硬上限")
                .defineInRange("capTensionCeiling", 3.00D, 1.0D, 10.0D);
        capTensionRecover = b.comment("张力回复倍率硬上限（放线时由红转白的速度）")
                .defineInRange("capTensionRecover", 2.20D, 1.0D, 10.0D);
        capStaminaDrain = b.comment("体力削减倍率硬上限")
                .defineInRange("capStaminaDrain", 2.60D, 1.0D, 10.0D);
        capPull = b.comment("回拉速度倍率硬上限")
                .defineInRange("capPull", 2.30D, 1.0D, 10.0D);
        capDamping = b.comment("压制阻尼倍率硬上限")
                .defineInRange("capDamping", 2.70D, 1.0D, 10.0D);
        pullIdleBase = b.comment("不挣扎时基础回拉速度（格/tick）")
                .defineInRange("pullIdleBase", 0.15D, 0.001D, 2.0D);
        pullStruggleBase = b.comment("挣扎时基础回拉速度（格/tick）")
                .defineInRange("pullStruggleBase", 0.35D, 0.001D, 2.0D);
        staminaDrainBase = b.comment("收线时每秒扣掉的鱼力比例（相对满体力）")
                .defineInRange("staminaDrainBase", 0.20D, 0.001D, 5.0D);
        b.pop();

        // ---------------------------------------------------------- 窝料
        b.comment("""
                窝料 / Chum（做成实体、会散开、时长按窝料档次）
                注意：时长是**每种窝料自己的**（ChumItem 构造参数，30~300 分钟档），
                所以这里没有"全局时长"配置 —— 那种配置要么被忽略、要么把档次差异抹平。""").push("chum");
        chumParticleIntervalTicks = b.comment("粒子生成间隔（tick）；越大越省性能")
                .defineInRange("particleIntervalTicks", 6, 1, 200);
        chumParticlesPerBurst = b.comment("每次生成的粒子数（批量生成，降低总粒子量）")
                .defineInRange("particlesPerBurst", 6, 1, 32);
        chumRadius = b.comment("窝子半径（格）——白圈画的就是这个半径")
                .defineInRange("radius", 10.0D, 1.0D, 64.0D);
        chumServerCap = b.comment("""
                全服同时存在的窝料上限（条）
                用户 2026-10-11 定案："全服至多存在 40 个窝料"—— 防的是
                "一个人一直打窝、全队跟着吃"。到期的窝料自己会释放名额，
                所以这个数是**同时存在**的条数，不是"每人能打多少"。
                数为 0 或负数时按 1 处理（mod 内部夹取）。""")
                .defineInRange("serverCap", 40, 0, 400);
        b.pop();

        // ---------------------------------------------------------- 称号
        // ⚠ 说明里**不许出现英文双引号**：调参器的注释正则按 "..." 截取，
        //   带一个英文引号就会整条匹配失败 ⇒ 玩家在调参器上只看到一个数字
        //   （教训记在 tools/check-tuning-schema.js 的注释里）。要引用就用「」。
        b.comment("""
                称号 / Titles（用户 2026-10-11：「开发称号系统」；「备选加过渡加。然后推进。」）
                这里只有**门槛数字**与**聊天后缀模式**；称号的名字、颜色、获得条件的文案
                分别在语言文件与 title/TitleDefs.java 里（那里是唯一的表）。
                三条口径（改动前先读一遍）：
                  1) 数字可以随便调 —— 但**别把门槛调到拿不到**（例如「金鳞全录」要求
                     「所有能出金的鱼种都出过金」，那个种数是从 FishSpecies 数出来的，
                     不是这里配的：47 种里有 17 种的档位上限只到紫，写 47 就是永远拿不到 ✗）；
                  2) 聊天后缀默认 0（full_line）= 玩家[称号]：内容。它会绕过原版签名聊天
                     （这条消息变成系统消息）；在意兼容就改成 1 或 2；
                  3) 没佩戴称号的玩家聊天行为**逐字不变**（纯加法）。""").push("titles");
        titleChatMode = b.comment("""
                聊天里的称号后缀模式
                0 = full_line：玩家[称号]：内容（默认；绕过原版签名聊天，这条消息算系统消息）
                1 = message_only：<玩家> [称号]内容（保留原版签名聊天，只是消息内容被改写）
                2 = vanilla：聊天里不加任何东西（称号仍可在 /atl title list 看到）""")
                .defineInRange("chatMode", 0, 0, 2);
        titleCodexCount5 = b.comment("「鱼种记录者」门槛：图鉴点亮多少种")
                .defineInRange("codexCount5", 5, 1, 47);
        titleCodexCount10 = b.comment("「鱼种编目者」门槛：图鉴点亮多少种")
                .defineInRange("codexCount10", 10, 1, 47);
        titleCodexCount24 = b.comment("「鱼类鉴定家」门槛：图鉴点亮多少种")
                .defineInRange("codexCount24", 24, 1, 47);
        titleCodexCount40 = b.comment("「鱼类学者」门槛：图鉴点亮多少种")
                .defineInRange("codexCount40", 40, 1, 47);
        titleGoldCount10 = b.comment("「金鳞十种」门槛：多少种鱼钓到过金档以上")
                .defineInRange("goldCount10", 10, 1, 47);
        titleGiantKg5 = b.comment("「大物钓手」门槛：单尾多重（kg）")
                .defineInRange("giantKg5", 5.0D, 0.1D, 1000.0D);
        titleGiantKg20 = b.comment("「巨物猎手」门槛：单尾多重（kg）")
                .defineInRange("giantKg20", 20.0D, 0.1D, 1000.0D);
        titleGiantKg50 = b.comment("「巨物专家」门槛：单尾多重（kg）")
                .defineInRange("giantKg50", 50.0D, 0.1D, 1000.0D);
        titleGiantKg100 = b.comment("""
                「巨物大师」门槛：单尾多重（kg）
                默认 100：能"出金档就必然 ≥100kg"的只有 4 种（格陵兰鲨 380–500 /
                巨暹罗鲤 151–200 / 蓝鳍金枪鱼 132–180 / 格陵兰大比目鱼 118–200）。
                ⚠ 巨骨舌鱼的上限正好是 100.00kg，数学上够得到但要 1/5 万级别的极限 roll
                ⇒ 实际按 4 种算（依据见 docs/38 §4.1）。""")
                .defineInRange("giantKg100", 100.0D, 0.1D, 5000.0D);
        titleGarCount = b.comment("「鳄雀鳝猎手」门槛：钓到多少条鳄雀鳝（入侵种）")
                .defineInRange("garCount", 5, 1, 999);
        titleRedCount = b.comment("「红鳞收藏家」门槛：累计钓到多少条红档鱼")
                .defineInRange("redCount", 10, 1, 999);
        titleNightCount = b.comment("「夜钓客」门槛：夜晚时段累计上鱼多少尾")
                .defineInRange("nightCount", 50, 1, 9999);
        titleFullMoonCount = b.comment("「月满垂纶」门槛：满月夜累计上鱼多少尾")
                .defineInRange("fullMoonCount", 10, 1, 9999);
        titleIceCount = b.comment("「冰钓人」门槛：冰洞里累计上鱼多少尾")
                .defineInRange("iceCount", 10, 1, 9999);
        titleChumCount = b.comment("「打窝老手」门槛：累计打窝多少次")
                .defineInRange("chumCount", 100, 1, 99999);
        titleLineBreakCount = b.comment("「断线专家」门槛：累计断线多少次（这个「专家」是反着说的）")
                .defineInRange("lineBreakCount", 50, 1, 99999);
        titleDishMadeCount = b.comment("""
                「鱼宴大厨」门槛：做过多少种菜型
                默认 10 = DishRecipe.Dish 的全部菜型（红烧/清蒸/香煎/炸块/鱼汤/奶油鱼汤/
                炖/鱼丸/刺身/整鱼下锅）⇒ 做完即"菜谱全通"。填大于菜型数的值等于拿不到。""")
                .defineInRange("dishMadeCount", 10, 1, 64);
        titleDishEatenCount = b.comment("「美食家」门槛：吃过多少种不同的菜型")
                .defineInRange("dishEatenCount", 5, 1, 64);
        b.pop();

        // ---------------------------------------------------------- 邮件
        // ⚠ 说明里**不许出现英文双引号**（调参器的注释正则按 "..." 截取，详见 tools/check-tuning-schema.js）。
        b.comment("""
                邮件箱 / Mail（用户 2026-10-11 点名要的邮件系统；策划 docs/36）
                四条口径（改动前先读一遍）：
                  1) **保管期是现实天**：过期只**作废附件**、正文保留 —— 玩家会觉得
                     「我的邮件被人删了」如果整封消失 ✗；
                  2) **到上限就拒绝投递**（WARN + 提示发信人），绝不静默丢件、
                     也不悄悄挤掉最旧的一封 ✗；
                  3) 服主正文超长：**发送侧拒绝 + 渲染侧再截断**，两道都要有
                     （只做一道 ⇒ 要么溢出面板，要么写进去再没提示）；
                  4) 未读提示**只提示一次**（进世界那一下），不是每 tick 弹。""").push("mail");
        mailKeepDays = b.comment("""
                邮件保管期（现实天）
                超过它的邮件：**附件作废**（点了也领不到）、**正文保留**当记录。
                界面在过期前 3 天会标红提醒（那 3 天不配置 —— 它是"提醒"不是"规则"）。""")
                .defineInRange("keepDays", 30, 1, 3650);
        mailMaxMails = b.comment("""
                邮箱条数上限
                到顶后**拒绝投递**：写一条 WARN 日志、并给收件人一句提示。
                ⚠ 不许改成"挤掉最旧的" —— 那会让玩家在最需要那封信的时候发现它没了 ✗。""")
                .defineInRange("maxMails", 200, 1, 5000);
        mailBodyMaxChars = b.comment("""
                服主手写正文的长度上限（字符）
                发送侧超长直接拒绝；渲染侧**再截断一次**（两道都要有，理由见组注释）。""")
                .defineInRange("bodyMaxChars", 500, 20, 5000);
        mailUnreadLoginNotice = b.comment("""
                进世界时给一次「你有未读邮件」的动作栏提示
                **只提示一次**（登录那一下）；关掉它仍可在邮箱界面/tooltip 上看到未读数。""")
                .define("unreadLoginNotice", true);
        b.pop();

        // ---------------------------------------------------------- 委托
        // ⚠ 说明里**不许出现英文双引号**（调参器的注释正则按 "..." 截取，详见 tools/check-tuning-schema.js）。
        b.comment("""
                委托 / Quest（用户 2026-10-14 定案；策划 docs/40）
                四条口径（改动前先读一遍）：
                  1) **每日 = 每游戏日**：日界取 getDayTime() 的日序号，**游戏内天明刷新**
                     （不用 getGameTime —— 那只是纯 tick 计数，会在半夜刷、玩家感知错位 ✗）；
                  2) **同时可接 5 个、池子 >= 8 种**：池子条目数掉到 8 以下 check-quest.js 判红；
                  3) 奖励**二选一**：给饵料就不给鱼币（用户原话：饵料的费用算很多了）；
                  4) 活跃度**跨周清零**，上限见 activityCapWeekly（档位 25/50/75/100 各发一次）。""").push("quest");
        // ⚠ TOML 键名**不能**叫 "enabled"：[environment] 组已经占了它，而调参器
        //   （tools/gen-tuning-schema.js）按**扁平键名**判重 ⇒ 两处会各算一份冲突的值、
        //   导出的表也分不清是哪个开关（闸就死在这一步：2026-10-14 第三轮当场踩到）。
        //   ⚠ 另外这段注释**必须写在文本块外面**：check-config-structure.js 要求文本块的收尾
        //   紧跟 .push( / .define( / .defineInRange(，中间插注释会被判"收尾被拆散"。
        //   （也**别在注释里写出连续三个引号** —— 那个检查器是数三引号的奇偶，注释里的也算 ✗）
        questEnabled = b.comment("""
                委托系统总开关
                关掉之后：不再投递委托信、也不再记进度（已接的委托**保留在存档里不动**）。
                做这个开关是为了出问题时能一键停掉，不用回滚版本。""")
                .define("enable", true);
        questDailyCount = b.comment("""
                每游戏日投递的每日委托条数
                默认 5（用户 2026-10-14：每天 5 种可做，池子种类更多）。""")
                .defineInRange("dailyCount", 5, 1, 8);
        questWeeklyCount = b.comment("""
                每游戏周投递的周常委托条数
                默认 5（用户口径同上）。""")
                .defineInRange("weeklyCount", 5, 1, 8);
        questActivityPerDaily = b.comment("""
                完成一个每日委托给多少活跃度
                用户定案 3；一周最多 5 条 x 3 = 15。""")
                .defineInRange("activityPerDaily", 3, 1, 100);
        questActivityPerWeekly = b.comment("""
                完成一个周常委托给多少活跃度
                用户定案 8；一周 5 条 = 40。""")
                .defineInRange("activityPerWeekly", 8, 1, 100);
        questActivityCapWeekly = b.comment("""
                每周活跃度上限（用户定案 100）
                满档不用满勤：5 条周常 40 + 每日 4 天 x 15 = 100，够得到。
                四个档位奖励在 25 / 50 / 75 / 100 各发一次。""")
                .defineInRange("activityCapWeekly", 100, 10, 1000);
        b.pop();

        // ---------------------------------------------------------- 联赛
        // ⚠ 说明里**不许出现英文双引号**（调参器的注释正则按 "..." 截取，详见 tools/check-tuning-schema.js）。
        b.comment("""
                联赛 / League（用户 2026-10-11 定案；策划 docs/37）
                五条口径（改动前先读一遍）：
                  1) **一期的时间轴**：推送邀请 → 全局硬截止 = 推送后 5 游戏日（≈运行 100 分钟）
                     → **不管谁打没打满，到点一律结算**；每人签收后各有 **30 游戏分钟**的窗口，
                     窗口终点 = min（签收刻 + 30 分钟, 硬截止）；
                  2) **时间只用累计刻**（世界开了多久），不用世界时刻 ——
                     睡觉跳夜、/time set、冻结时间**都影响不了联赛进度**；
                     现实口径那一半用 UTC 毫秒，系统时间往前调不会让赛季瞬间跳完；
                  3) 三级**轮转**（小 → 中 → 大），同一时刻只开一级：分级就是分割人口，
                     人少的服切成三份会进入"离场级联"（调研实证）⇒ 本版不提供"并开"开关；
                  4) **计分口径只有一份**：与排行榜同一套算法；可刷的「最短上鱼」「连击」
                     **不进联赛**（只作排行榜展示）；
                  5) **绝不重置永久数据**：图鉴 / 纪录 / 等级 / 鱼币 / 限购一律不碰，
                     联赛只重置"本期成绩"。""").push("league");
        leagueUseRealDays = b.comment("""
                计时口径（用户要的那个旋钮）
                true（默认）= 推送间隔按**现实天**（7/30/90 天，好理解）；
                false = 推送间隔按**游戏日**（30/120/360 游戏日；1 游戏日 = 24000 刻 = 20 分钟，
                即 10/40/120 小时**服务器真的在跑**的时间 —— 每天只开 2 小时的服，大联赛实际是 60 个现实日）。
                ⚠ 每期的**硬截止**与每人的**窗口**永远是游戏时间，不随这个开关变。""")
                .define("useRealDays", true);
        leagueMinLevel = b.comment("""
                邀请资格线（钓鱼等级）
                默认 21 = 用户原话"高于 20" = 正好**通过了 Lv.20 评定②**的下一个等级。""")
                .defineInRange("minLevel", 21, 1, 50);
        leagueAllowBelowLevel = b.comment("""
                打开后**未达线者也收邀请**
                调研提醒：硬门槛会让新服长期无人参赛（够格的人根本还没练出来）。
                打开它等于把联赛当"周常活动"用，谁都能来。""")
                .define("allowBelowLevel", false);
        leagueSmallDays = b.comment("""
                小型联赛的**推送间隔**（按所选口径的"天"）
                ⚠ 它是"多久推一次邀请"，不是"一期多久" —— 一期永远是"推送 + 硬截止"。""")
                .defineInRange("smallDays", 7, 1, 3650);
        leagueMediumDays = b.comment("""
                中型联赛的推送间隔（按所选口径的"天"）
                奖励按 scaleMediumCoinsXp 缩放（鱼币与经验）。""")
                .defineInRange("mediumDays", 30, 1, 3650);
        leagueLargeDays = b.comment("""
                大型联赛的推送间隔（按所选口径的"天"）
                奖励按 scaleLargeCoinsXp 缩放（鱼币与经验）+ scaleLargeVanillaItems（原版物资）。""")
                .defineInRange("largeDays", 90, 1, 3650);
        leaguePeriodGameDays = b.comment("""
                一期**硬截止**：推送后几个**游戏日**结算（用户定案 5）
                1 游戏日 = 24000 刻 = 20 分钟 ⇒ 5 游戏日 ≈ 运行 100 分钟。
                ⚠ 到点**不管谁打没打满，一律结算**（用户原话"无论有没有打满，联赛结算时也强行截止"）。
                ⚠ 用游戏日而不是现实天：服务器停机期间两边都停，这样"一期"的长度才对得上"玩了多久"。""")
                .defineInRange("periodGameDays", 5, 1, 100);
        leaguePlayerWindowMinutes = b.comment("""
                **每人窗口**：签收后多少**游戏分钟**内计分（用户定案 30）
                窗口从**签收那一刻**起算（不是"只在钓鱼时消耗"）；终点还要被硬截止截断
                ⇒ 越晚签收，能用的时间越短（第 90 分钟签收只剩 10 分钟）。
                窗口走完了**什么都不会发生**：只是本期不再计分，钓鱼照旧（只计分不惩罚）。""")
                .defineInRange("playerWindowMinutes", 30, 1, 1440);
        leaguePeriodBoundaryHour = b.comment("""
                现实口径下每期的起始**服务器本地时刻**（0~23，默认 12 点）
                学 LoL 的"各服本地 12:00:00"：期与期的边界落在同一个钟点上，
                玩家能自己算出来下一期什么时候开 ✓。游戏口径不用这一项。""")
                .defineInRange("periodBoundaryHour", 12, 0, 23);
        leagueRuleMode = b.comment("""
                计分口径（开期时**钉死**，中途改只影响下一期）
                0 = 单鱼最重（默认，与排行榜主榜同源）；1 = 总重量；
                2 = 金红鱼尾数（金 4 / 红 5）；3 = 图鉴完成度。
                ⚠ 顺序与代码里的枚举一一对应，别只改注释。
                ⚠ 可刷的两项（2 = 最短上鱼 / 连击）**不在选项里** —— 调研结论：
                这两项极易被脚本刷，只作排行榜展示。""")
                .defineInRange("ruleMode", 0, 0, 3);
        leagueMinCatchesToRank = b.comment("""
                有效成绩下限：本期至少上鱼 N 尾才进榜、才有名次奖（默认 1）
                学 WoW 角斗士的"门槛 + 场次下限"双条件：光签收不上鱼 ⇒
                只拿到签收小礼，**没有名次奖**（小号因此没有收益）。""")
                .defineInRange("minCatchesToRank", 1, 1, 1000);
        leagueRewardCoinsParticipant = b.comment("""
                参与奖（参赛但没进前十）的鱼币（小型基准，中型 ×2、大型 ×4）""")
                .defineInRange("rewardCoinsParticipant", 500, 0, 10000000);
        leagueRewardCoinsTop10 = b.comment("""
                第 4~10 名的鱼币（小型基准）""")
                .defineInRange("rewardCoinsTop10", 2000, 0, 10000000);
        leagueRewardCoins1st = b.comment("""
                第 1 名的鱼币（小型基准；中型 ×2、大型 ×4）
                量级参照：40000 币 ≈ 8.3 条红档鱼的卖价（大型第 1 名）。""")
                .defineInRange("rewardCoins1st", 10000, 0, 100000000);
        leagueRewardCoins2nd = b.comment("""
                第 2 名的鱼币（小型基准）""")
                .defineInRange("rewardCoins2nd", 5000, 0, 100000000);
        leagueRewardCoins3rd = b.comment("""
                第 3 名的鱼币（小型基准）""")
                .defineInRange("rewardCoins3rd", 3000, 0, 100000000);
        leagueRewardXp1st = b.comment("""
                第 1 名的垂纶经验（小型基准；中型 ×2、大型 ×4）
                ⚠ 大型第 1 名 = 4800 经验 ≈ 中后期 4 级 —— 这个量级偏高，用户知道并认可；
                要调就改这里与下面两项（经验走**直接入账**，因为邮件只能带鱼币和物品）。""")
                .defineInRange("rewardXp1st", 1200, 0, 1000000);
        leagueRewardXp2nd = b.comment("""
                第 2 名的垂纶经验（小型基准）""")
                .defineInRange("rewardXp2nd", 600, 0, 1000000);
        leagueRewardXp3rd = b.comment("""
                第 3 名的垂纶经验（小型基准）""")
                .defineInRange("rewardXp3rd", 300, 0, 1000000);
        leagueScaleMediumCoinsXp = b.comment("""
                中型联赛的**鱼币与经验**倍率（用户定案 ×2）""")
                .defineInRange("scaleMediumCoinsXp", 2.0, 1.0, 100.0);
        leagueScaleLargeCoinsXp = b.comment("""
                大型联赛的**鱼币与经验**倍率（用户定案 ×4）""")
                .defineInRange("scaleLargeCoinsXp", 4.0, 1.0, 100.0);
        leagueScaleLargeVanillaItems = b.comment("""
                大型联赛的**原版物资**倍率（用户定案 ×1.5）
                ⚠ **只作用于原版物资**（钻石 / 下界合金锭）；模组物品（红金紫线、拟饵、窝料）
                不缩放，签收礼也不缩放 —— 否则大型签收礼里的「钻石 ×1」会变成 1.5 个 ✗。
                表里所有 ×1.5 都整除（8→12 / 6→9 / 2→3），不会出现半颗钻石。""")
                .defineInRange("scaleLargeVanillaItems", 1.5, 1.0, 100.0);
        leagueRewardMailKeepDays = b.comment("""
                联赛**奖励类**邮件（邀请、成绩单 —— 里面有奖励）的保管期（现实天，默认 365）
                ⚠ 据调研改：30 天会惩罚休假玩家（MC 生态有"离线 14 天清数据"的实测坑）。
                奖励躺在邮箱里等你回来，而不是"回来发现过期了"。""")
                .defineInRange("rewardMailKeepDays", 365, 1, 36500);
        leagueNoticeMailKeepDays = b.comment("""
                联赛**通知类**邮件（未参赛公示、没上榜说明）的保管期（现实天）""")
                .defineInRange("noticeMailKeepDays", 30, 1, 36500);
        leagueInviteOnLogin = b.comment("""
                登录时**补发**当前这一期的邀请（默认开）
                ⚠ 文档 §7 写的是"中途加入不补发"（防掐着结算前一天进来捡便宜），
                但**够格名单只存在于玩家附件里**（离线读不到）⇒ 推送那一刻不在线的人
                永远收不到邀请，每周只上两天的玩家可能一期都参加不了 ✗。
                而"每人窗口 + 硬截止截断"本身就消灭了那个便宜（越晚签收窗口越短），
                所以默认补发；想要严格按文档就关掉这一项。""")
                .define("inviteOnLogin", true);
        b.pop();

        // ---------------------------------------------------------- 比赛
        b.comment("""
                钓鱼比赛 / Contest（用户 2026-10-11 定案；策划 docs/35）
                四条口径（改动前先读一遍）：
                  1) **全员自动参加**（不报名）：开赛后任何人钓到的鱼都自动计入 ——
                     报名 = 玩家忘了报、中途加入要不要算、旁边看着的人最扫兴，一次消掉；
                  2) 时间用**累计刻**，存的是**绝对结束刻**（不是"剩余秒数"）⇒
                     服务器重启后比赛继续跑剩余时间；
                  3) 一场结束才允许开下一场（**重叠会被拒绝**）；
                  4) 奖励只发鱼币、**没有参加奖**（有参加奖就会出现"开小号挂着"的刷法）。""").push("contest");
        contestDefaultMinutes = b.comment("""
                默认时长（分钟，游戏时间）
                ⚠ 是"服务器真的开着跑了多久"，不是现实时钟 —— 停机期间比赛不推进。""")
                .defineInRange("defaultMinutes", 30, 1, 1440);
        contestDefaultRuleMode = b.comment("""
                默认赛制（开赛时**钉死**，中途改只影响下一场）
                0 = 最重一尾（默认，一杆翻盘、戏剧性最强，与排行榜主榜同源）；
                1 = 总重量（一直在追，但会变成"钓得多"而不是"钓得大"）；
                2 = 金红尾数（红鱼本来就稀 ⇒ 多数场次 0:0 收场）。
                ⚠ 顺序与代码里的枚举一一对应，别只改注释。""")
                .defineInRange("defaultRuleMode", 0, 0, 2);
        contestMinPlayers = b.comment("""
                低于这个**在线人数**就不开赛（默认 1 = 单人也能开，自己跟自己比）""")
                .defineInRange("minPlayers", 1, 1, 100);
        contestRewardFirst = b.comment("""
                第 1 名奖励（鱼币，默认 300 ≈ 两条金档鱼）
                量级是"值得参加"而不是"收入来源"；觉得多就砍半、觉得少就翻倍。""")
                .defineInRange("rewardFirst", 300, 0, 1000000);
        contestRewardSecond = b.comment("""
                第 2 名奖励（鱼币）""")
                .defineInRange("rewardSecond", 150, 0, 1000000);
        contestRewardThird = b.comment("""
                第 3 名奖励（鱼币）""")
                .defineInRange("rewardThird", 80, 0, 1000000);
        contestAutoEveryMinutes = b.comment("""
                自动开赛间隔（分钟；**0 = 只手动开赛**，默认）
                填 1440 就是"每天一场"（按服务器运行时间算）。
                ⚠ 自动开赛也要满足 minPlayers，且不会打断正在进行的比赛。""")
                .defineInRange("autoEveryMinutes", 0, 0, 100000);
        contestAnnounceTop = b.comment("""
                结算时全服公告前几名（1~10，默认 3）
                ⚠ 只公告**这么多个**：成绩表里再多也不刷屏（中途每上一条大鱼都广播会变成骚扰 ✗）。""")
                .defineInRange("announceTop", 3, 1, 10);
        b.pop();

        // ------------------------------------------------------ 品质门槛
        b.comment("""
                品质门槛 / Rarity gate
                说明：**这一组还没有实现**，所以暂不提供配置项。
                当前生效的规则是：饵料/拟饵只决定鱼种档位下限（baitMinTier），
                金红占比由饵料档次与打窝倍率共同决定。
                等"蓝品质以下配置不许出红鱼"真正写进抽鱼逻辑后，再在这里加配置。""").push("rarityGate");
        b.pop();

        // ---------------------------------------------------------- 耐久
        b.comment("耐久 / Durability（咬钩即扣，与是否切线逃离无关）").push("durability");
        rodDurabilityPerBite = b.comment("每次咬钩扣除的鱼竿耐久（还会再乘竿的材质系数）")
                .defineInRange("rodPerBite", 2, 0, 100);
        lineDurabilityPerBite = b.comment("每次鱼咬钩扣除的鱼线耐久")
                .defineInRange("linePerBite", 1, 0, 100);
        lureDurabilityPerBite = b.comment("每次鱼咬钩扣除的拟饵耐久")
                .defineInRange("lurePerBite", 1, 0, 100);
        lineDurabilityPerBreak = b.comment("断线时额外扣除的鱼线耐久")
                .defineInRange("linePerBreak", 5, 0, 100);
        b.pop();

        // ---------------------------------------------------------- 配件数值
        b.comment("""
                配件数值 / Part stats（每个配件的属性）。
                由 tools/gen-part-config.js 从 ModItems.java 生成，默认值与代码里的原值一一对应，
                所以不改这些项时手感和以前完全一样。
                耐久上限是注册期固定的，改它要重启游戏；其余属性热重载即生效。
                """).push("partsStats");
        // ==== BEGIN 配件数值定义 ====
                hempDurability = b
                        .comment("麻线（hemp_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("hempDurability", 50, 0, 100000);
                hempTensionCeiling = b
                        .comment("麻线（hemp_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("hempTensionCeiling", 1.08, 0.0D, 100.0D);
                hempTensionGen = b
                        .comment("麻线（hemp_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("hempTensionGen", 0.97, 0.0D, 100.0D);
                hempTensionRecover = b
                        .comment("麻线（hemp_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("hempTensionRecover", 1.05, 0.0D, 100.0D);
                hempLineLength = b
                        .comment("麻线（hemp_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("hempLineLength", 1.00, 0.0D, 100.0D);
                hempTier = b
                        .comment("麻线（hemp_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("hempTier", 1, 1, 6);
                cottonDurability = b
                        .comment("棉线（cotton_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("cottonDurability", 50, 0, 100000);
                cottonTensionCeiling = b
                        .comment("棉线（cotton_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("cottonTensionCeiling", 1.20, 0.0D, 100.0D);
                cottonTensionGen = b
                        .comment("棉线（cotton_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("cottonTensionGen", 0.91, 0.0D, 100.0D);
                cottonTensionRecover = b
                        .comment("棉线（cotton_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("cottonTensionRecover", 1.15, 0.0D, 100.0D);
                cottonLineLength = b
                        .comment("棉线（cotton_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("cottonLineLength", 35.0 / 30.0, 0.0D, 100.0D);
                cottonTier = b
                        .comment("棉线（cotton_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("cottonTier", 2, 1, 6);
                nylonDurability = b
                        .comment("尼龙线（nylon_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("nylonDurability", 40, 0, 100000);
                nylonTensionCeiling = b
                        .comment("尼龙线（nylon_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("nylonTensionCeiling", 1.28, 0.0D, 100.0D);
                nylonTensionGen = b
                        .comment("尼龙线（nylon_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("nylonTensionGen", 0.95, 0.0D, 100.0D);
                nylonTensionRecover = b
                        .comment("尼龙线（nylon_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("nylonTensionRecover", 1.30, 0.0D, 100.0D);
                nylonLineLength = b
                        .comment("尼龙线（nylon_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("nylonLineLength", 45.0 / 30.0, 0.0D, 100.0D);
                nylonTier = b
                        .comment("尼龙线（nylon_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("nylonTier", 3, 1, 6);
                fluoroDurability = b
                        .comment("碳氟线（fluoro_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("fluoroDurability", 35, 0, 100000);
                fluoroTensionCeiling = b
                        .comment("碳氟线（fluoro_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("fluoroTensionCeiling", 1.64, 0.0D, 100.0D);
                fluoroTensionGen = b
                        .comment("碳氟线（fluoro_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("fluoroTensionGen", 0.78, 0.0D, 100.0D);
                fluoroTensionRecover = b
                        .comment("碳氟线（fluoro_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("fluoroTensionRecover", 1.42, 0.0D, 100.0D);
                fluoroLineLength = b
                        .comment("碳氟线（fluoro_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("fluoroLineLength", 60.0 / 30.0, 0.0D, 100.0D);
                fluoroTier = b
                        .comment("碳氟线（fluoro_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("fluoroTier", 4, 1, 6);
                peDurability = b
                        .comment("编织 PE 线（pe_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("peDurability", 30, 0, 100000);
                peTensionCeiling = b
                        .comment("编织 PE 线（pe_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("peTensionCeiling", 2.08, 0.0D, 100.0D);
                peTensionGen = b
                        .comment("编织 PE 线（pe_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("peTensionGen", 0.64, 0.0D, 100.0D);
                peTensionRecover = b
                        .comment("编织 PE 线（pe_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("peTensionRecover", 1.52, 0.0D, 100.0D);
                peLineLength = b
                        .comment("编织 PE 线（pe_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("peLineLength", 65.0 / 30.0, 0.0D, 100.0D);
                peTier = b
                        .comment("编织 PE 线（pe_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("peTier", 5, 1, 6);
                titaniumDurability = b
                        .comment("钛合金记忆金属线（titanium_line） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("titaniumDurability", 30, 0, 100000);
                titaniumTensionCeiling = b
                        .comment("钛合金记忆金属线（titanium_line） · 张力上限 —— 越高，线越不容易被拉崩")
                        .defineInRange("titaniumTensionCeiling", 2.36, 0.0D, 100.0D);
                titaniumTensionGen = b
                        .comment("钛合金记忆金属线（titanium_line） · 张力涨速 —— 越小，收线时张力涨得越慢（越小越安全）")
                        .defineInRange("titaniumTensionGen", 0.56, 0.0D, 100.0D);
                titaniumTensionRecover = b
                        .comment("钛合金记忆金属线（titanium_line） · 张力回复 —— 越大，放线时张力落得越快（越大越安全）")
                        .defineInRange("titaniumTensionRecover", 1.78, 0.0D, 100.0D);
                titaniumLineLength = b
                        .comment("钛合金记忆金属线（titanium_line） · 线长 —— 相对麻线的倍率，决定抛投射程上限")
                        .defineInRange("titaniumLineLength", 70.0 / 30.0, 0.0D, 100.0D);
                titaniumTier = b
                        .comment("钛合金记忆金属线（titanium_line） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("titaniumTier", 6, 1, 6);
                handDurability = b
                        .comment("手线轮（hand_reel） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("handDurability", 0, 0, 100000);
                handPullIdle = b
                        .comment("手线轮（hand_reel） · 回拉速度（不挣扎） —— 鱼不挣扎时收线拉回多快")
                        .defineInRange("handPullIdle", 1.08, 0.0D, 100.0D);
                handPullStruggle = b
                        .comment("手线轮（hand_reel） · 回拉速度（挣扎） —— 鱼挣扎时收线拉回多快")
                        .defineInRange("handPullStruggle", 1.08, 0.0D, 100.0D);
                handStaminaDrain = b
                        .comment("手线轮（hand_reel） · 体力削减 —— 越大，鱼掉体力越快（越大越好钓）")
                        .defineInRange("handStaminaDrain", 1.08, 0.0D, 100.0D);
                handDamping = b
                        .comment("手线轮（hand_reel） · 压制阻尼 —— 抑制鱼回气；高于 1 的部分另有转化")
                        .defineInRange("handDamping", 1.07, 0.0D, 100.0D);
                handTier = b
                        .comment("手线轮（hand_reel） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("handTier", 1, 1, 6);
                spinningDurability = b
                        .comment("纺车轮（spinning_reel） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("spinningDurability", 0, 0, 100000);
                spinningPullIdle = b
                        .comment("纺车轮（spinning_reel） · 回拉速度（不挣扎） —— 鱼不挣扎时收线拉回多快")
                        .defineInRange("spinningPullIdle", 1.25, 0.0D, 100.0D);
                spinningPullStruggle = b
                        .comment("纺车轮（spinning_reel） · 回拉速度（挣扎） —— 鱼挣扎时收线拉回多快")
                        .defineInRange("spinningPullStruggle", 1.25, 0.0D, 100.0D);
                spinningStaminaDrain = b
                        .comment("纺车轮（spinning_reel） · 体力削减 —— 越大，鱼掉体力越快（越大越好钓）")
                        .defineInRange("spinningStaminaDrain", 1.25, 0.0D, 100.0D);
                spinningDamping = b
                        .comment("纺车轮（spinning_reel） · 压制阻尼 —— 抑制鱼回气；高于 1 的部分另有转化")
                        .defineInRange("spinningDamping", 1.20, 0.0D, 100.0D);
                spinningTier = b
                        .comment("纺车轮（spinning_reel） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("spinningTier", 2, 1, 6);
                drumDurability = b
                        .comment("鼓轮（drum_reel） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("drumDurability", 0, 0, 100000);
                drumPullIdle = b
                        .comment("鼓轮（drum_reel） · 回拉速度（不挣扎） —— 鱼不挣扎时收线拉回多快")
                        .defineInRange("drumPullIdle", 1.45, 0.0D, 100.0D);
                drumPullStruggle = b
                        .comment("鼓轮（drum_reel） · 回拉速度（挣扎） —— 鱼挣扎时收线拉回多快")
                        .defineInRange("drumPullStruggle", 1.45, 0.0D, 100.0D);
                drumStaminaDrain = b
                        .comment("鼓轮（drum_reel） · 体力削减 —— 越大，鱼掉体力越快（越大越好钓）")
                        .defineInRange("drumStaminaDrain", 1.60, 0.0D, 100.0D);
                drumDamping = b
                        .comment("鼓轮（drum_reel） · 压制阻尼 —— 抑制鱼回气；高于 1 的部分另有转化")
                        .defineInRange("drumDamping", 1.45, 0.0D, 100.0D);
                drumTier = b
                        .comment("鼓轮（drum_reel） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("drumTier", 3, 1, 6);
                baitcastDurability = b
                        .comment("水滴轮（baitcast_reel） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("baitcastDurability", 0, 0, 100000);
                baitcastPullIdle = b
                        .comment("水滴轮（baitcast_reel） · 回拉速度（不挣扎） —— 鱼不挣扎时收线拉回多快")
                        .defineInRange("baitcastPullIdle", 1.98, 0.0D, 100.0D);
                baitcastPullStruggle = b
                        .comment("水滴轮（baitcast_reel） · 回拉速度（挣扎） —— 鱼挣扎时收线拉回多快")
                        .defineInRange("baitcastPullStruggle", 1.95, 0.0D, 100.0D);
                baitcastStaminaDrain = b
                        .comment("水滴轮（baitcast_reel） · 体力削减 —— 越大，鱼掉体力越快（越大越好钓）")
                        .defineInRange("baitcastStaminaDrain", 1.40, 0.0D, 100.0D);
                baitcastDamping = b
                        .comment("水滴轮（baitcast_reel） · 压制阻尼 —— 抑制鱼回气；高于 1 的部分另有转化")
                        .defineInRange("baitcastDamping", 1.40, 0.0D, 100.0D);
                baitcastTier = b
                        .comment("水滴轮（baitcast_reel） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("baitcastTier", 4, 1, 6);
                heavyDurability = b
                        .comment("大型纺车轮（heavy_reel） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("heavyDurability", 0, 0, 100000);
                heavyPullIdle = b
                        .comment("大型纺车轮（heavy_reel） · 回拉速度（不挣扎） —— 鱼不挣扎时收线拉回多快")
                        .defineInRange("heavyPullIdle", 1.64, 0.0D, 100.0D);
                heavyPullStruggle = b
                        .comment("大型纺车轮（heavy_reel） · 回拉速度（挣扎） —— 鱼挣扎时收线拉回多快")
                        .defineInRange("heavyPullStruggle", 1.64, 0.0D, 100.0D);
                heavyStaminaDrain = b
                        .comment("大型纺车轮（heavy_reel） · 体力削减 —— 越大，鱼掉体力越快（越大越好钓）")
                        .defineInRange("heavyStaminaDrain", 2.30, 0.0D, 100.0D);
                heavyDamping = b
                        .comment("大型纺车轮（heavy_reel） · 压制阻尼 —— 抑制鱼回气；高于 1 的部分另有转化")
                        .defineInRange("heavyDamping", 2.50, 0.0D, 100.0D);
                heavyTier = b
                        .comment("大型纺车轮（heavy_reel） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("heavyTier", 5, 1, 6);
                lureSpoonDurability = b
                        .comment("亮片拟饵（lure_spoon） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureSpoonDurability", 40, 0, 100000);
                lureSpoonTier = b
                        .comment("亮片拟饵（lure_spoon） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureSpoonTier", 2, 1, 6);
                lureGlowspoonDurability = b
                        .comment("荧光亮片（lure_glowspoon） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureGlowspoonDurability", 40, 0, 100000);
                lureGlowspoonTier = b
                        .comment("荧光亮片（lure_glowspoon） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureGlowspoonTier", 2, 1, 6);
                lureMinnowDurability = b
                        .comment("米诺鱼拟饵（lure_minnow） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureMinnowDurability", 30, 0, 100000);
                lureMinnowTier = b
                        .comment("米诺鱼拟饵（lure_minnow） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureMinnowTier", 3, 1, 6);
                lureDeepDurability = b
                        .comment("浅层刺激拟饵（lure_deep） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureDeepDurability", 22, 0, 100000);
                lureDeepTier = b
                        .comment("浅层刺激拟饵（lure_deep） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureDeepTier", 4, 1, 6);
                lureRattleDurability = b
                        .comment("钢珠声诱拟饵（lure_rattle） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureRattleDurability", 20, 0, 100000);
                lureRattleTier = b
                        .comment("钢珠声诱拟饵（lure_rattle） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureRattleTier", 5, 1, 6);
                lureGildedDurability = b
                        .comment("镀金竞技拟饵（lure_gilded） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureGildedDurability", 20, 0, 100000);
                lureGildedTier = b
                        .comment("镀金竞技拟饵（lure_gilded） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureGildedTier", 6, 1, 6);
                lureSoftDurability = b
                        .comment("软虫拟饵（lure_soft） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureSoftDurability", 26, 0, 100000);
                lureSoftTier = b
                        .comment("软虫拟饵（lure_soft） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureSoftTier", 3, 1, 6);
                lureDepthfinderDurability = b
                        .comment("深水探索米诺（lure_depthfinder） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureDepthfinderDurability", 26, 0, 100000);
                lureDepthfinderTier = b
                        .comment("深水探索米诺（lure_depthfinder） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureDepthfinderTier", 3, 1, 6);
                lurePaddletailDurability = b
                        .comment("深水摆尾拟饵（lure_paddletail） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lurePaddletailDurability", 22, 0, 100000);
                lurePaddletailTier = b
                        .comment("深水摆尾拟饵（lure_paddletail） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lurePaddletailTier", 4, 1, 6);
                lureVibrationDurability = b
                        .comment("深潜巨物振动饵（lure_vibration） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureVibrationDurability", 20, 0, 100000);
                lureVibrationTier = b
                        .comment("深潜巨物振动饵（lure_vibration） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureVibrationTier", 5, 1, 6);
                lureTestRedDurability = b
                        .comment("【测试】红档拟饵（必出红鱼）（lure_test_red） · 耐久上限 —— 用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏")
                        .defineInRange("lureTestRedDurability", 9999, 0, 100000);
                lureTestRedTier = b
                        .comment("【测试】红档拟饵（必出红鱼）（lure_test_red） · 品质档位 —— 1白 2绿 3蓝 4紫 5金 6红")
                        .defineInRange("lureTestRedTier", 6, 1, 6);
        // ==== END 配件数值定义 ====
        b.pop();

        // ---------------------------------------------------------- 鱼种数值
        b.comment("""
                鱼种数值 / Per-fish stats（每条鱼的体重与对抗手感）。
                由 tools/gen-fish-config.js 从 FishSpecies.java 生成，默认值与代码里的原值一一对应，
                所以不改这些项时手感和以前完全一样。
                改完 /atl config reload 即生效：只影响之后咬钩的鱼，不影响正在遛的那条。
                每条的 13 项含义相同：体重上下限、拉力（不挣扎/疲劳期）、体力、张力涨速、
                冲刺时长与间隔、变向间隔、假动作概率、进疲劳的体力比例、疲劳持续、不挣扎时回气。
                """).push("fishStats");
        // ==== BEGIN 鱼种数值定义 ====
        crucianCarpMinKg = b
                .comment("鲫鱼（crucian_carp） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("crucianCarpMinKg", 0.1, 0.001, 500);
        crucianCarpMaxKg = b
                .comment("鲫鱼（crucian_carp） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("crucianCarpMaxKg", 1.2, 0.001, 500);
        crucianCarpPullSpeed = b
                .comment("鲫鱼（crucian_carp） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("crucianCarpPullSpeed", 1.05, 0, 20);
        crucianCarpFatiguedPullSpeed = b
                .comment("鲫鱼（crucian_carp） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("crucianCarpFatiguedPullSpeed", 0.441, 0, 20);
        crucianCarpStamina = b
                .comment("鲫鱼（crucian_carp） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("crucianCarpStamina", 80.0, 1, 5000);
        crucianCarpTensionGain = b
                .comment("鲫鱼（crucian_carp） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("crucianCarpTensionGain", 0.9, 0, 5);
        crucianCarpSprintDurationSec = b
                .comment("鲫鱼（crucian_carp） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("crucianCarpSprintDurationSec", 0.6, 0, 60);
        crucianCarpSprintIntervalSec = b
                .comment("鲫鱼（crucian_carp） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("crucianCarpSprintIntervalSec", 5.5, 0.1, 60);
        crucianCarpTurnIntervalSec = b
                .comment("鲫鱼（crucian_carp） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("crucianCarpTurnIntervalSec", 4.5, 0.2, 60);
        crucianCarpFakeOutChance = b
                .comment("鲫鱼（crucian_carp） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("crucianCarpFakeOutChance", 0.0, 0, 1);
        crucianCarpFatigueThreshold = b
                .comment("鲫鱼（crucian_carp） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("crucianCarpFatigueThreshold", 0.3, 0.01, 1);
        crucianCarpFatigueDurationSec = b
                .comment("鲫鱼（crucian_carp） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("crucianCarpFatigueDurationSec", 2.0, 0, 120);
        crucianCarpStaminaRegen = b
                .comment("鲫鱼（crucian_carp） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("crucianCarpStaminaRegen", 0.02, 0, 1);
        tilapiaMinKg = b
                .comment("罗非鱼（tilapia） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("tilapiaMinKg", 0.2, 0.001, 500);
        tilapiaMaxKg = b
                .comment("罗非鱼（tilapia） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("tilapiaMaxKg", 2.0, 0.001, 500);
        tilapiaPullSpeed = b
                .comment("罗非鱼（tilapia） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("tilapiaPullSpeed", 1.2, 0, 20);
        tilapiaFatiguedPullSpeed = b
                .comment("罗非鱼（tilapia） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("tilapiaFatiguedPullSpeed", 0.504, 0, 20);
        tilapiaStamina = b
                .comment("罗非鱼（tilapia） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("tilapiaStamina", 90.0, 1, 5000);
        tilapiaTensionGain = b
                .comment("罗非鱼（tilapia） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("tilapiaTensionGain", 0.95, 0, 5);
        tilapiaSprintDurationSec = b
                .comment("罗非鱼（tilapia） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("tilapiaSprintDurationSec", 0.8, 0, 60);
        tilapiaSprintIntervalSec = b
                .comment("罗非鱼（tilapia） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("tilapiaSprintIntervalSec", 4.5, 0.1, 60);
        tilapiaTurnIntervalSec = b
                .comment("罗非鱼（tilapia） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("tilapiaTurnIntervalSec", 3.5, 0.2, 60);
        tilapiaFakeOutChance = b
                .comment("罗非鱼（tilapia） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("tilapiaFakeOutChance", 0.0, 0, 1);
        tilapiaFatigueThreshold = b
                .comment("罗非鱼（tilapia） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("tilapiaFatigueThreshold", 0.3, 0.01, 1);
        tilapiaFatigueDurationSec = b
                .comment("罗非鱼（tilapia） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("tilapiaFatigueDurationSec", 2.0, 0, 120);
        tilapiaStaminaRegen = b
                .comment("罗非鱼（tilapia） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("tilapiaStaminaRegen", 0.02, 0, 1);
        mulletMinKg = b
                .comment("鲻鱼（mullet） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("mulletMinKg", 0.3, 0.001, 500);
        mulletMaxKg = b
                .comment("鲻鱼（mullet） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("mulletMaxKg", 2.5, 0.001, 500);
        mulletPullSpeed = b
                .comment("鲻鱼（mullet） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("mulletPullSpeed", 2.0, 0, 20);
        mulletFatiguedPullSpeed = b
                .comment("鲻鱼（mullet） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("mulletFatiguedPullSpeed", 0.63, 0, 20);
        mulletStamina = b
                .comment("鲻鱼（mullet） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("mulletStamina", 110.0, 1, 5000);
        mulletTensionGain = b
                .comment("鲻鱼（mullet） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("mulletTensionGain", 1.05, 0, 5);
        mulletSprintDurationSec = b
                .comment("鲻鱼（mullet） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("mulletSprintDurationSec", 0.8, 0, 60);
        mulletSprintIntervalSec = b
                .comment("鲻鱼（mullet） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("mulletSprintIntervalSec", 4.5, 0.1, 60);
        mulletTurnIntervalSec = b
                .comment("鲻鱼（mullet） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("mulletTurnIntervalSec", 3.5, 0.2, 60);
        mulletFakeOutChance = b
                .comment("鲻鱼（mullet） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("mulletFakeOutChance", 0.0, 0, 1);
        mulletFatigueThreshold = b
                .comment("鲻鱼（mullet） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("mulletFatigueThreshold", 0.3, 0.01, 1);
        mulletFatigueDurationSec = b
                .comment("鲻鱼（mullet） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("mulletFatigueDurationSec", 2.0, 0, 120);
        mulletStaminaRegen = b
                .comment("鲻鱼（mullet） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("mulletStaminaRegen", 0.02, 0, 1);
        grassCarpMinKg = b
                .comment("草鱼（grass_carp） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("grassCarpMinKg", 1.5, 0.001, 500);
        grassCarpMaxKg = b
                .comment("草鱼（grass_carp） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("grassCarpMaxKg", 18.0, 0.001, 500);
        grassCarpPullSpeed = b
                .comment("草鱼（grass_carp） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("grassCarpPullSpeed", 1.125, 0, 20);
        grassCarpFatiguedPullSpeed = b
                .comment("草鱼（grass_carp） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("grassCarpFatiguedPullSpeed", 0.4725, 0, 20);
        grassCarpStamina = b
                .comment("草鱼（grass_carp） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("grassCarpStamina", 220.0, 1, 5000);
        grassCarpTensionGain = b
                .comment("草鱼（grass_carp） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("grassCarpTensionGain", 1.35, 0, 5);
        grassCarpSprintDurationSec = b
                .comment("草鱼（grass_carp） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("grassCarpSprintDurationSec", 0.4, 0, 60);
        grassCarpSprintIntervalSec = b
                .comment("草鱼（grass_carp） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("grassCarpSprintIntervalSec", 8.0, 0.1, 60);
        grassCarpTurnIntervalSec = b
                .comment("草鱼（grass_carp） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("grassCarpTurnIntervalSec", 5.5, 0.2, 60);
        grassCarpFakeOutChance = b
                .comment("草鱼（grass_carp） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("grassCarpFakeOutChance", 0.0, 0, 1);
        grassCarpFatigueThreshold = b
                .comment("草鱼（grass_carp） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("grassCarpFatigueThreshold", 0.28, 0.01, 1);
        grassCarpFatigueDurationSec = b
                .comment("草鱼（grass_carp） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("grassCarpFatigueDurationSec", 2.6, 0, 120);
        grassCarpStaminaRegen = b
                .comment("草鱼（grass_carp） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("grassCarpStaminaRegen", 0.015, 0, 1);
        blackCarpMinKg = b
                .comment("青鱼（black_carp） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("blackCarpMinKg", 3.0, 0.001, 500);
        blackCarpMaxKg = b
                .comment("青鱼（black_carp） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("blackCarpMaxKg", 35.0, 0.001, 500);
        blackCarpPullSpeed = b
                .comment("青鱼（black_carp） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("blackCarpPullSpeed", 1.05, 0, 20);
        blackCarpFatiguedPullSpeed = b
                .comment("青鱼（black_carp） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("blackCarpFatiguedPullSpeed", 0.441, 0, 20);
        blackCarpStamina = b
                .comment("青鱼（black_carp） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("blackCarpStamina", 320.0, 1, 5000);
        blackCarpTensionGain = b
                .comment("青鱼（black_carp） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("blackCarpTensionGain", 1.55, 0, 5);
        blackCarpSprintDurationSec = b
                .comment("青鱼（black_carp） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("blackCarpSprintDurationSec", 0.4, 0, 60);
        blackCarpSprintIntervalSec = b
                .comment("青鱼（black_carp） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("blackCarpSprintIntervalSec", 8.0, 0.1, 60);
        blackCarpTurnIntervalSec = b
                .comment("青鱼（black_carp） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("blackCarpTurnIntervalSec", 5.5, 0.2, 60);
        blackCarpFakeOutChance = b
                .comment("青鱼（black_carp） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("blackCarpFakeOutChance", 0.0, 0, 1);
        blackCarpFatigueThreshold = b
                .comment("青鱼（black_carp） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("blackCarpFatigueThreshold", 0.28, 0.01, 1);
        blackCarpFatigueDurationSec = b
                .comment("青鱼（black_carp） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("blackCarpFatigueDurationSec", 2.6, 0, 120);
        blackCarpStaminaRegen = b
                .comment("青鱼（black_carp） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("blackCarpStaminaRegen", 0.015, 0, 1);
        bandedLoachMinKg = b
                .comment("横纹鱼（banded_loach） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("bandedLoachMinKg", 0.003, 0.001, 500);
        bandedLoachMaxKg = b
                .comment("横纹鱼（banded_loach） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("bandedLoachMaxKg", 0.03, 0.001, 500);
        bandedLoachPullSpeed = b
                .comment("横纹鱼（banded_loach） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("bandedLoachPullSpeed", 0.525, 0, 20);
        bandedLoachFatiguedPullSpeed = b
                .comment("横纹鱼（banded_loach） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("bandedLoachFatiguedPullSpeed", 0.2205, 0, 20);
        bandedLoachStamina = b
                .comment("横纹鱼（banded_loach） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("bandedLoachStamina", 25.0, 1, 5000);
        bandedLoachTensionGain = b
                .comment("横纹鱼（banded_loach） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("bandedLoachTensionGain", 0.8, 0, 5);
        bandedLoachSprintDurationSec = b
                .comment("横纹鱼（banded_loach） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("bandedLoachSprintDurationSec", 0.8, 0, 60);
        bandedLoachSprintIntervalSec = b
                .comment("横纹鱼（banded_loach） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("bandedLoachSprintIntervalSec", 4.5, 0.1, 60);
        bandedLoachTurnIntervalSec = b
                .comment("横纹鱼（banded_loach） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("bandedLoachTurnIntervalSec", 3.5, 0.2, 60);
        bandedLoachFakeOutChance = b
                .comment("横纹鱼（banded_loach） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("bandedLoachFakeOutChance", 0.0, 0, 1);
        bandedLoachFatigueThreshold = b
                .comment("横纹鱼（banded_loach） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("bandedLoachFatigueThreshold", 0.3, 0.01, 1);
        bandedLoachFatigueDurationSec = b
                .comment("横纹鱼（banded_loach） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("bandedLoachFatigueDurationSec", 2.0, 0, 120);
        bandedLoachStaminaRegen = b
                .comment("横纹鱼（banded_loach） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("bandedLoachStaminaRegen", 0.02, 0, 1);
        hemiculterMinKg = b
                .comment("白条鱼（hemiculter） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("hemiculterMinKg", 0.02, 0.001, 500);
        hemiculterMaxKg = b
                .comment("白条鱼（hemiculter） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("hemiculterMaxKg", 0.232, 0.001, 500);
        hemiculterPullSpeed = b
                .comment("白条鱼（hemiculter） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("hemiculterPullSpeed", 1.425, 0, 20);
        hemiculterFatiguedPullSpeed = b
                .comment("白条鱼（hemiculter） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("hemiculterFatiguedPullSpeed", 0.5985, 0, 20);
        hemiculterStamina = b
                .comment("白条鱼（hemiculter） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("hemiculterStamina", 45.0, 1, 5000);
        hemiculterTensionGain = b
                .comment("白条鱼（hemiculter） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("hemiculterTensionGain", 1.1, 0, 5);
        hemiculterSprintDurationSec = b
                .comment("白条鱼（hemiculter） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("hemiculterSprintDurationSec", 0.9, 0, 60);
        hemiculterSprintIntervalSec = b
                .comment("白条鱼（hemiculter） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("hemiculterSprintIntervalSec", 3.0, 0.1, 60);
        hemiculterTurnIntervalSec = b
                .comment("白条鱼（hemiculter） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("hemiculterTurnIntervalSec", 1.3, 0.2, 60);
        hemiculterFakeOutChance = b
                .comment("白条鱼（hemiculter） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("hemiculterFakeOutChance", 0.35, 0, 1);
        hemiculterFatigueThreshold = b
                .comment("白条鱼（hemiculter） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("hemiculterFatigueThreshold", 0.32, 0.01, 1);
        hemiculterFatigueDurationSec = b
                .comment("白条鱼（hemiculter） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("hemiculterFatigueDurationSec", 2.0, 0, 120);
        hemiculterStaminaRegen = b
                .comment("白条鱼（hemiculter） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("hemiculterStaminaRegen", 0.02, 0, 1);
        topmouthCulterMinKg = b
                .comment("翘嘴（topmouth_culter） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("topmouthCulterMinKg", 0.6, 0.001, 500);
        topmouthCulterMaxKg = b
                .comment("翘嘴（topmouth_culter） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("topmouthCulterMaxKg", 6.0, 0.001, 500);
        topmouthCulterPullSpeed = b
                .comment("翘嘴（topmouth_culter） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("topmouthCulterPullSpeed", 2.025, 0, 20);
        topmouthCulterFatiguedPullSpeed = b
                .comment("翘嘴（topmouth_culter） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("topmouthCulterFatiguedPullSpeed", 0.8504999999999999, 0, 20);
        topmouthCulterStamina = b
                .comment("翘嘴（topmouth_culter） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("topmouthCulterStamina", 150.0, 1, 5000);
        topmouthCulterTensionGain = b
                .comment("翘嘴（topmouth_culter） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("topmouthCulterTensionGain", 1.2, 0, 5);
        topmouthCulterSprintDurationSec = b
                .comment("翘嘴（topmouth_culter） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("topmouthCulterSprintDurationSec", 1.6, 0, 60);
        topmouthCulterSprintIntervalSec = b
                .comment("翘嘴（topmouth_culter） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("topmouthCulterSprintIntervalSec", 2.6, 0.1, 60);
        topmouthCulterTurnIntervalSec = b
                .comment("翘嘴（topmouth_culter） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("topmouthCulterTurnIntervalSec", 3.0, 0.2, 60);
        topmouthCulterFakeOutChance = b
                .comment("翘嘴（topmouth_culter） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("topmouthCulterFakeOutChance", 0.1, 0, 1);
        topmouthCulterFatigueThreshold = b
                .comment("翘嘴（topmouth_culter） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("topmouthCulterFatigueThreshold", 0.34, 0.01, 1);
        topmouthCulterFatigueDurationSec = b
                .comment("翘嘴（topmouth_culter） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("topmouthCulterFatigueDurationSec", 2.4, 0, 120);
        topmouthCulterStaminaRegen = b
                .comment("翘嘴（topmouth_culter） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("topmouthCulterStaminaRegen", 0.02, 0, 1);
        zanderMinKg = b
                .comment("梭鲈（zander） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("zanderMinKg", 1.0, 0.001, 500);
        zanderMaxKg = b
                .comment("梭鲈（zander） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("zanderMaxKg", 9.0, 0.001, 500);
        zanderPullSpeed = b
                .comment("梭鲈（zander） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("zanderPullSpeed", 2.175, 0, 20);
        zanderFatiguedPullSpeed = b
                .comment("梭鲈（zander） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("zanderFatiguedPullSpeed", 0.9134999999999999, 0, 20);
        zanderStamina = b
                .comment("梭鲈（zander） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("zanderStamina", 180.0, 1, 5000);
        zanderTensionGain = b
                .comment("梭鲈（zander） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("zanderTensionGain", 1.3, 0, 5);
        zanderSprintDurationSec = b
                .comment("梭鲈（zander） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("zanderSprintDurationSec", 0.9, 0, 60);
        zanderSprintIntervalSec = b
                .comment("梭鲈（zander） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("zanderSprintIntervalSec", 3.0, 0.1, 60);
        zanderTurnIntervalSec = b
                .comment("梭鲈（zander） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("zanderTurnIntervalSec", 1.3, 0.2, 60);
        zanderFakeOutChance = b
                .comment("梭鲈（zander） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("zanderFakeOutChance", 0.35, 0, 1);
        zanderFatigueThreshold = b
                .comment("梭鲈（zander） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("zanderFatigueThreshold", 0.32, 0.01, 1);
        zanderFatigueDurationSec = b
                .comment("梭鲈（zander） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("zanderFatigueDurationSec", 2.0, 0, 120);
        zanderStaminaRegen = b
                .comment("梭鲈（zander） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("zanderStaminaRegen", 0.02, 0, 1);
        sapphireSnakeheadMinKg = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("sapphireSnakeheadMinKg", 0.15, 0.001, 500);
        sapphireSnakeheadMaxKg = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("sapphireSnakeheadMaxKg", 0.35, 0.001, 500);
        sapphireSnakeheadPullSpeed = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("sapphireSnakeheadPullSpeed", 2.55, 0, 20);
        sapphireSnakeheadFatiguedPullSpeed = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("sapphireSnakeheadFatiguedPullSpeed", 1.071, 0, 20);
        sapphireSnakeheadStamina = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("sapphireSnakeheadStamina", 130.0, 1, 5000);
        sapphireSnakeheadTensionGain = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("sapphireSnakeheadTensionGain", 1.35, 0, 5);
        sapphireSnakeheadSprintDurationSec = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("sapphireSnakeheadSprintDurationSec", 0.9, 0, 60);
        sapphireSnakeheadSprintIntervalSec = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("sapphireSnakeheadSprintIntervalSec", 3.0, 0.1, 60);
        sapphireSnakeheadTurnIntervalSec = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("sapphireSnakeheadTurnIntervalSec", 1.3, 0.2, 60);
        sapphireSnakeheadFakeOutChance = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("sapphireSnakeheadFakeOutChance", 0.35, 0, 1);
        sapphireSnakeheadFatigueThreshold = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("sapphireSnakeheadFatigueThreshold", 0.32, 0.01, 1);
        sapphireSnakeheadFatigueDurationSec = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("sapphireSnakeheadFatigueDurationSec", 2.0, 0, 120);
        sapphireSnakeheadStaminaRegen = b
                .comment("蓝宝石雷龙鱼（sapphire_snakehead） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("sapphireSnakeheadStaminaRegen", 0.02, 0, 1);
        platinumArowanaMinKg = b
                .comment("铂金银龙鱼（platinum_arowana） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("platinumArowanaMinKg", 1.5, 0.001, 500);
        platinumArowanaMaxKg = b
                .comment("铂金银龙鱼（platinum_arowana） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("platinumArowanaMaxKg", 6.0, 0.001, 500);
        platinumArowanaPullSpeed = b
                .comment("铂金银龙鱼（platinum_arowana） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("platinumArowanaPullSpeed", 3.9, 0, 20);
        platinumArowanaFatiguedPullSpeed = b
                .comment("铂金银龙鱼（platinum_arowana） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("platinumArowanaFatiguedPullSpeed", 1.638, 0, 20);
        platinumArowanaStamina = b
                .comment("铂金银龙鱼（platinum_arowana） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("platinumArowanaStamina", 300.0, 1, 5000);
        platinumArowanaTensionGain = b
                .comment("铂金银龙鱼（platinum_arowana） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("platinumArowanaTensionGain", 1.95, 0, 5);
        platinumArowanaSprintDurationSec = b
                .comment("铂金银龙鱼（platinum_arowana） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("platinumArowanaSprintDurationSec", 1.6, 0, 60);
        platinumArowanaSprintIntervalSec = b
                .comment("铂金银龙鱼（platinum_arowana） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("platinumArowanaSprintIntervalSec", 2.6, 0.1, 60);
        platinumArowanaTurnIntervalSec = b
                .comment("铂金银龙鱼（platinum_arowana） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("platinumArowanaTurnIntervalSec", 3.0, 0.2, 60);
        platinumArowanaFakeOutChance = b
                .comment("铂金银龙鱼（platinum_arowana） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("platinumArowanaFakeOutChance", 0.1, 0, 1);
        platinumArowanaFatigueThreshold = b
                .comment("铂金银龙鱼（platinum_arowana） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("platinumArowanaFatigueThreshold", 0.34, 0.01, 1);
        platinumArowanaFatigueDurationSec = b
                .comment("铂金银龙鱼（platinum_arowana） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("platinumArowanaFatigueDurationSec", 2.4, 0, 120);
        platinumArowanaStaminaRegen = b
                .comment("铂金银龙鱼（platinum_arowana） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("platinumArowanaStaminaRegen", 0.02, 0, 1);
        oxeyeTarponMinKg = b
                .comment("大眼海鲢（oxeye_tarpon） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("oxeyeTarponMinKg", 0.5, 0.001, 500);
        oxeyeTarponMaxKg = b
                .comment("大眼海鲢（oxeye_tarpon） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("oxeyeTarponMaxKg", 18.0, 0.001, 500);
        oxeyeTarponPullSpeed = b
                .comment("大眼海鲢（oxeye_tarpon） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("oxeyeTarponPullSpeed", 4.8, 0, 20);
        oxeyeTarponFatiguedPullSpeed = b
                .comment("大眼海鲢（oxeye_tarpon） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("oxeyeTarponFatiguedPullSpeed", 2.016, 0, 20);
        oxeyeTarponStamina = b
                .comment("大眼海鲢（oxeye_tarpon） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("oxeyeTarponStamina", 400.0, 1, 5000);
        oxeyeTarponTensionGain = b
                .comment("大眼海鲢（oxeye_tarpon） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("oxeyeTarponTensionGain", 1.65, 0, 5);
        oxeyeTarponSprintDurationSec = b
                .comment("大眼海鲢（oxeye_tarpon） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("oxeyeTarponSprintDurationSec", 0.9, 0, 60);
        oxeyeTarponSprintIntervalSec = b
                .comment("大眼海鲢（oxeye_tarpon） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("oxeyeTarponSprintIntervalSec", 3.0, 0.1, 60);
        oxeyeTarponTurnIntervalSec = b
                .comment("大眼海鲢（oxeye_tarpon） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("oxeyeTarponTurnIntervalSec", 1.3, 0.2, 60);
        oxeyeTarponFakeOutChance = b
                .comment("大眼海鲢（oxeye_tarpon） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("oxeyeTarponFakeOutChance", 0.35, 0, 1);
        oxeyeTarponFatigueThreshold = b
                .comment("大眼海鲢（oxeye_tarpon） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("oxeyeTarponFatigueThreshold", 0.32, 0.01, 1);
        oxeyeTarponFatigueDurationSec = b
                .comment("大眼海鲢（oxeye_tarpon） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("oxeyeTarponFatigueDurationSec", 2.0, 0, 120);
        oxeyeTarponStaminaRegen = b
                .comment("大眼海鲢（oxeye_tarpon） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("oxeyeTarponStaminaRegen", 0.02, 0, 1);
        atlanticSalmonMinKg = b
                .comment("大西洋鲑（atlantic_salmon） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("atlanticSalmonMinKg", 2.0, 0.001, 500);
        atlanticSalmonMaxKg = b
                .comment("大西洋鲑（atlantic_salmon） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("atlanticSalmonMaxKg", 22.0, 0.001, 500);
        atlanticSalmonPullSpeed = b
                .comment("大西洋鲑（atlantic_salmon） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("atlanticSalmonPullSpeed", 4.6875, 0, 20);
        atlanticSalmonFatiguedPullSpeed = b
                .comment("大西洋鲑（atlantic_salmon） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("atlanticSalmonFatiguedPullSpeed", 1.96875, 0, 20);
        atlanticSalmonStamina = b
                .comment("大西洋鲑（atlantic_salmon） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("atlanticSalmonStamina", 360.0, 1, 5000);
        atlanticSalmonTensionGain = b
                .comment("大西洋鲑（atlantic_salmon） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("atlanticSalmonTensionGain", 1.25, 0, 5);
        atlanticSalmonSprintDurationSec = b
                .comment("大西洋鲑（atlantic_salmon） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("atlanticSalmonSprintDurationSec", 1.6, 0, 60);
        atlanticSalmonSprintIntervalSec = b
                .comment("大西洋鲑（atlantic_salmon） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("atlanticSalmonSprintIntervalSec", 2.6, 0.1, 60);
        atlanticSalmonTurnIntervalSec = b
                .comment("大西洋鲑（atlantic_salmon） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("atlanticSalmonTurnIntervalSec", 3.0, 0.2, 60);
        atlanticSalmonFakeOutChance = b
                .comment("大西洋鲑（atlantic_salmon） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("atlanticSalmonFakeOutChance", 0.1, 0, 1);
        atlanticSalmonFatigueThreshold = b
                .comment("大西洋鲑（atlantic_salmon） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("atlanticSalmonFatigueThreshold", 0.34, 0.01, 1);
        atlanticSalmonFatigueDurationSec = b
                .comment("大西洋鲑（atlantic_salmon） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("atlanticSalmonFatigueDurationSec", 2.4, 0, 120);
        atlanticSalmonStaminaRegen = b
                .comment("大西洋鲑（atlantic_salmon） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("atlanticSalmonStaminaRegen", 0.02, 0, 1);
        bluefinTunaMinKg = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("bluefinTunaMinKg", 12.0, 0.001, 500);
        bluefinTunaMaxKg = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("bluefinTunaMaxKg", 180.0, 0.001, 500);
        bluefinTunaPullSpeed = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("bluefinTunaPullSpeed", 5.15625, 0, 20);
        bluefinTunaFatiguedPullSpeed = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("bluefinTunaFatiguedPullSpeed", 2.165625, 0, 20);
        bluefinTunaStamina = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("bluefinTunaStamina", 420.0, 1, 5000);
        bluefinTunaTensionGain = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("bluefinTunaTensionGain", 1.5, 0, 5);
        bluefinTunaSprintDurationSec = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("bluefinTunaSprintDurationSec", 1.6, 0, 60);
        bluefinTunaSprintIntervalSec = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("bluefinTunaSprintIntervalSec", 2.6, 0.1, 60);
        bluefinTunaTurnIntervalSec = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("bluefinTunaTurnIntervalSec", 3.0, 0.2, 60);
        bluefinTunaFakeOutChance = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("bluefinTunaFakeOutChance", 0.1, 0, 1);
        bluefinTunaFatigueThreshold = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("bluefinTunaFatigueThreshold", 0.34, 0.01, 1);
        bluefinTunaFatigueDurationSec = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("bluefinTunaFatigueDurationSec", 2.4, 0, 120);
        bluefinTunaStaminaRegen = b
                .comment("蓝鳍金枪鱼（bluefin_tuna） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("bluefinTunaStaminaRegen", 0.02, 0, 1);
        juvenileSharkMinKg = b
                .comment("幼鲨（juvenile_shark） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("juvenileSharkMinKg", 8.0, 0.001, 500);
        juvenileSharkMaxKg = b
                .comment("幼鲨（juvenile_shark） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("juvenileSharkMaxKg", 90.0, 0.001, 500);
        juvenileSharkPullSpeed = b
                .comment("幼鲨（juvenile_shark） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("juvenileSharkPullSpeed", 5.4375, 0, 20);
        juvenileSharkFatiguedPullSpeed = b
                .comment("幼鲨（juvenile_shark） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("juvenileSharkFatiguedPullSpeed", 2.28375, 0, 20);
        juvenileSharkStamina = b
                .comment("幼鲨（juvenile_shark） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("juvenileSharkStamina", 380.0, 1, 5000);
        juvenileSharkTensionGain = b
                .comment("幼鲨（juvenile_shark） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("juvenileSharkTensionGain", 1.7, 0, 5);
        juvenileSharkSprintDurationSec = b
                .comment("幼鲨（juvenile_shark） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("juvenileSharkSprintDurationSec", 0.9, 0, 60);
        juvenileSharkSprintIntervalSec = b
                .comment("幼鲨（juvenile_shark） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("juvenileSharkSprintIntervalSec", 3.0, 0.1, 60);
        juvenileSharkTurnIntervalSec = b
                .comment("幼鲨（juvenile_shark） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("juvenileSharkTurnIntervalSec", 1.3, 0.2, 60);
        juvenileSharkFakeOutChance = b
                .comment("幼鲨（juvenile_shark） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("juvenileSharkFakeOutChance", 0.35, 0, 1);
        juvenileSharkFatigueThreshold = b
                .comment("幼鲨（juvenile_shark） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("juvenileSharkFatigueThreshold", 0.32, 0.01, 1);
        juvenileSharkFatigueDurationSec = b
                .comment("幼鲨（juvenile_shark） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("juvenileSharkFatigueDurationSec", 2.0, 0, 120);
        juvenileSharkStaminaRegen = b
                .comment("幼鲨（juvenile_shark） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("juvenileSharkStaminaRegen", 0.02, 0, 1);
        tigerfishMinKg = b
                .comment("虎鱼（tigerfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("tigerfishMinKg", 0.05, 0.001, 500);
        tigerfishMaxKg = b
                .comment("虎鱼（tigerfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("tigerfishMaxKg", 0.8, 0.001, 500);
        tigerfishPullSpeed = b
                .comment("虎鱼（tigerfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("tigerfishPullSpeed", 3.3, 0, 20);
        tigerfishFatiguedPullSpeed = b
                .comment("虎鱼（tigerfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("tigerfishFatiguedPullSpeed", 1.386, 0, 20);
        tigerfishStamina = b
                .comment("虎鱼（tigerfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("tigerfishStamina", 320.0, 1, 5000);
        tigerfishTensionGain = b
                .comment("虎鱼（tigerfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("tigerfishTensionGain", 3.1, 0, 5);
        tigerfishSprintDurationSec = b
                .comment("虎鱼（tigerfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("tigerfishSprintDurationSec", 0.9, 0, 60);
        tigerfishSprintIntervalSec = b
                .comment("虎鱼（tigerfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("tigerfishSprintIntervalSec", 3.0, 0.1, 60);
        tigerfishTurnIntervalSec = b
                .comment("虎鱼（tigerfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("tigerfishTurnIntervalSec", 1.3, 0.2, 60);
        tigerfishFakeOutChance = b
                .comment("虎鱼（tigerfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("tigerfishFakeOutChance", 0.35, 0, 1);
        tigerfishFatigueThreshold = b
                .comment("虎鱼（tigerfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("tigerfishFatigueThreshold", 0.32, 0.01, 1);
        tigerfishFatigueDurationSec = b
                .comment("虎鱼（tigerfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("tigerfishFatigueDurationSec", 2.0, 0, 120);
        tigerfishStaminaRegen = b
                .comment("虎鱼（tigerfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("tigerfishStaminaRegen", 0.02, 0, 1);
        pikeMinKg = b
                .comment("狗鱼（pike） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("pikeMinKg", 1.0, 0.001, 500);
        pikeMaxKg = b
                .comment("狗鱼（pike） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("pikeMaxKg", 12.0, 0.001, 500);
        pikePullSpeed = b
                .comment("狗鱼（pike） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("pikePullSpeed", 3.6, 0, 20);
        pikeFatiguedPullSpeed = b
                .comment("狗鱼（pike） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("pikeFatiguedPullSpeed", 1.512, 0, 20);
        pikeStamina = b
                .comment("狗鱼（pike） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("pikeStamina", 170.0, 1, 5000);
        pikeTensionGain = b
                .comment("狗鱼（pike） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("pikeTensionGain", 1.15, 0, 5);
        pikeSprintDurationSec = b
                .comment("狗鱼（pike） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("pikeSprintDurationSec", 1.6, 0, 60);
        pikeSprintIntervalSec = b
                .comment("狗鱼（pike） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("pikeSprintIntervalSec", 2.6, 0.1, 60);
        pikeTurnIntervalSec = b
                .comment("狗鱼（pike） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("pikeTurnIntervalSec", 3.0, 0.2, 60);
        pikeFakeOutChance = b
                .comment("狗鱼（pike） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("pikeFakeOutChance", 0.1, 0, 1);
        pikeFatigueThreshold = b
                .comment("狗鱼（pike） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("pikeFatigueThreshold", 0.34, 0.01, 1);
        pikeFatigueDurationSec = b
                .comment("狗鱼（pike） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("pikeFatigueDurationSec", 2.4, 0, 120);
        pikeStaminaRegen = b
                .comment("狗鱼（pike） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("pikeStaminaRegen", 0.02, 0, 1);
        brownTroutMinKg = b
                .comment("褐鳟（brown_trout） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("brownTroutMinKg", 0.5, 0.001, 500);
        brownTroutMaxKg = b
                .comment("褐鳟（brown_trout） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("brownTroutMaxKg", 6.0, 0.001, 500);
        brownTroutPullSpeed = b
                .comment("褐鳟（brown_trout） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("brownTroutPullSpeed", 3.45, 0, 20);
        brownTroutFatiguedPullSpeed = b
                .comment("褐鳟（brown_trout） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("brownTroutFatiguedPullSpeed", 1.449, 0, 20);
        brownTroutStamina = b
                .comment("褐鳟（brown_trout） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("brownTroutStamina", 400.0, 1, 5000);
        brownTroutTensionGain = b
                .comment("褐鳟（brown_trout） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("brownTroutTensionGain", 2.65, 0, 5);
        brownTroutSprintDurationSec = b
                .comment("褐鳟（brown_trout） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("brownTroutSprintDurationSec", 1.6, 0, 60);
        brownTroutSprintIntervalSec = b
                .comment("褐鳟（brown_trout） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("brownTroutSprintIntervalSec", 2.6, 0.1, 60);
        brownTroutTurnIntervalSec = b
                .comment("褐鳟（brown_trout） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("brownTroutTurnIntervalSec", 3.0, 0.2, 60);
        brownTroutFakeOutChance = b
                .comment("褐鳟（brown_trout） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("brownTroutFakeOutChance", 0.1, 0, 1);
        brownTroutFatigueThreshold = b
                .comment("褐鳟（brown_trout） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("brownTroutFatigueThreshold", 0.34, 0.01, 1);
        brownTroutFatigueDurationSec = b
                .comment("褐鳟（brown_trout） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("brownTroutFatigueDurationSec", 2.4, 0, 120);
        brownTroutStaminaRegen = b
                .comment("褐鳟（brown_trout） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("brownTroutStaminaRegen", 0.02, 0, 1);
        mirrorCarpMinKg = b
                .comment("镜鲤（mirror_carp） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("mirrorCarpMinKg", 1.5, 0.001, 500);
        mirrorCarpMaxKg = b
                .comment("镜鲤（mirror_carp） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("mirrorCarpMaxKg", 20.0, 0.001, 500);
        mirrorCarpPullSpeed = b
                .comment("镜鲤（mirror_carp） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("mirrorCarpPullSpeed", 3.6, 0, 20);
        mirrorCarpFatiguedPullSpeed = b
                .comment("镜鲤（mirror_carp） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("mirrorCarpFatiguedPullSpeed", 1.512, 0, 20);
        mirrorCarpStamina = b
                .comment("镜鲤（mirror_carp） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("mirrorCarpStamina", 380.0, 1, 5000);
        mirrorCarpTensionGain = b
                .comment("镜鲤（mirror_carp） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("mirrorCarpTensionGain", 2.8, 0, 5);
        mirrorCarpSprintDurationSec = b
                .comment("镜鲤（mirror_carp） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("mirrorCarpSprintDurationSec", 0.4, 0, 60);
        mirrorCarpSprintIntervalSec = b
                .comment("镜鲤（mirror_carp） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("mirrorCarpSprintIntervalSec", 8.0, 0.1, 60);
        mirrorCarpTurnIntervalSec = b
                .comment("镜鲤（mirror_carp） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("mirrorCarpTurnIntervalSec", 5.5, 0.2, 60);
        mirrorCarpFakeOutChance = b
                .comment("镜鲤（mirror_carp） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("mirrorCarpFakeOutChance", 0.0, 0, 1);
        mirrorCarpFatigueThreshold = b
                .comment("镜鲤（mirror_carp） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("mirrorCarpFatigueThreshold", 0.28, 0.01, 1);
        mirrorCarpFatigueDurationSec = b
                .comment("镜鲤（mirror_carp） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("mirrorCarpFatigueDurationSec", 2.6, 0, 120);
        mirrorCarpStaminaRegen = b
                .comment("镜鲤（mirror_carp） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("mirrorCarpStaminaRegen", 0.015, 0, 1);
        stripedBassMinKg = b
                .comment("条纹鲈（striped_bass） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("stripedBassMinKg", 1.0, 0.001, 500);
        stripedBassMaxKg = b
                .comment("条纹鲈（striped_bass） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("stripedBassMaxKg", 15.0, 0.001, 500);
        stripedBassPullSpeed = b
                .comment("条纹鲈（striped_bass） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("stripedBassPullSpeed", 2.4, 0, 20);
        stripedBassFatiguedPullSpeed = b
                .comment("条纹鲈（striped_bass） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("stripedBassFatiguedPullSpeed", 1.008, 0, 20);
        stripedBassStamina = b
                .comment("条纹鲈（striped_bass） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("stripedBassStamina", 240.0, 1, 5000);
        stripedBassTensionGain = b
                .comment("条纹鲈（striped_bass） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("stripedBassTensionGain", 1.2, 0, 5);
        stripedBassSprintDurationSec = b
                .comment("条纹鲈（striped_bass） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("stripedBassSprintDurationSec", 0.8, 0, 60);
        stripedBassSprintIntervalSec = b
                .comment("条纹鲈（striped_bass） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("stripedBassSprintIntervalSec", 4.5, 0.1, 60);
        stripedBassTurnIntervalSec = b
                .comment("条纹鲈（striped_bass） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("stripedBassTurnIntervalSec", 3.5, 0.2, 60);
        stripedBassFakeOutChance = b
                .comment("条纹鲈（striped_bass） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("stripedBassFakeOutChance", 0.0, 0, 1);
        stripedBassFatigueThreshold = b
                .comment("条纹鲈（striped_bass） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("stripedBassFatigueThreshold", 0.3, 0.01, 1);
        stripedBassFatigueDurationSec = b
                .comment("条纹鲈（striped_bass） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("stripedBassFatigueDurationSec", 2.0, 0, 120);
        stripedBassStaminaRegen = b
                .comment("条纹鲈（striped_bass） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("stripedBassStaminaRegen", 0.02, 0, 1);
        arapaimaMinKg = b
                .comment("巨骨舌鱼（arapaima） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("arapaimaMinKg", 20.0, 0.001, 500);
        arapaimaMaxKg = b
                .comment("巨骨舌鱼（arapaima） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("arapaimaMaxKg", 100.0, 0.001, 500);
        arapaimaPullSpeed = b
                .comment("巨骨舌鱼（arapaima） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("arapaimaPullSpeed", 3.3, 0, 20);
        arapaimaFatiguedPullSpeed = b
                .comment("巨骨舌鱼（arapaima） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("arapaimaFatiguedPullSpeed", 1.386, 0, 20);
        arapaimaStamina = b
                .comment("巨骨舌鱼（arapaima） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("arapaimaStamina", 520.0, 1, 5000);
        arapaimaTensionGain = b
                .comment("巨骨舌鱼（arapaima） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("arapaimaTensionGain", 3.2, 0, 5);
        arapaimaSprintDurationSec = b
                .comment("巨骨舌鱼（arapaima） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("arapaimaSprintDurationSec", 0.8, 0, 60);
        arapaimaSprintIntervalSec = b
                .comment("巨骨舌鱼（arapaima） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("arapaimaSprintIntervalSec", 4.5, 0.1, 60);
        arapaimaTurnIntervalSec = b
                .comment("巨骨舌鱼（arapaima） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("arapaimaTurnIntervalSec", 3.5, 0.2, 60);
        arapaimaFakeOutChance = b
                .comment("巨骨舌鱼（arapaima） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("arapaimaFakeOutChance", 0.0, 0, 1);
        arapaimaFatigueThreshold = b
                .comment("巨骨舌鱼（arapaima） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("arapaimaFatigueThreshold", 0.3, 0.01, 1);
        arapaimaFatigueDurationSec = b
                .comment("巨骨舌鱼（arapaima） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("arapaimaFatigueDurationSec", 2.0, 0, 120);
        arapaimaStaminaRegen = b
                .comment("巨骨舌鱼（arapaima） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("arapaimaStaminaRegen", 0.02, 0, 1);
        africanButterflyfishMinKg = b
                .comment("齿蝶鱼（african_butterflyfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("africanButterflyfishMinKg", 0.003, 0.001, 500);
        africanButterflyfishMaxKg = b
                .comment("齿蝶鱼（african_butterflyfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("africanButterflyfishMaxKg", 0.015, 0.001, 500);
        africanButterflyfishPullSpeed = b
                .comment("齿蝶鱼（african_butterflyfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("africanButterflyfishPullSpeed", 1.65, 0, 20);
        africanButterflyfishFatiguedPullSpeed = b
                .comment("齿蝶鱼（african_butterflyfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("africanButterflyfishFatiguedPullSpeed", 0.693, 0, 20);
        africanButterflyfishStamina = b
                .comment("齿蝶鱼（african_butterflyfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("africanButterflyfishStamina", 95.0, 1, 5000);
        africanButterflyfishTensionGain = b
                .comment("齿蝶鱼（african_butterflyfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("africanButterflyfishTensionGain", 1.0, 0, 5);
        africanButterflyfishSprintDurationSec = b
                .comment("齿蝶鱼（african_butterflyfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("africanButterflyfishSprintDurationSec", 0.8, 0, 60);
        africanButterflyfishSprintIntervalSec = b
                .comment("齿蝶鱼（african_butterflyfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("africanButterflyfishSprintIntervalSec", 4.5, 0.1, 60);
        africanButterflyfishTurnIntervalSec = b
                .comment("齿蝶鱼（african_butterflyfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("africanButterflyfishTurnIntervalSec", 3.5, 0.2, 60);
        africanButterflyfishFakeOutChance = b
                .comment("齿蝶鱼（african_butterflyfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("africanButterflyfishFakeOutChance", 0.0, 0, 1);
        africanButterflyfishFatigueThreshold = b
                .comment("齿蝶鱼（african_butterflyfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("africanButterflyfishFatigueThreshold", 0.3, 0.01, 1);
        africanButterflyfishFatigueDurationSec = b
                .comment("齿蝶鱼（african_butterflyfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("africanButterflyfishFatigueDurationSec", 2.0, 0, 120);
        africanButterflyfishStaminaRegen = b
                .comment("齿蝶鱼（african_butterflyfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("africanButterflyfishStaminaRegen", 0.02, 0, 1);
        jewelCichlidMinKg = b
                .comment("红宝石鱼（jewel_cichlid） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("jewelCichlidMinKg", 0.02, 0.001, 500);
        jewelCichlidMaxKg = b
                .comment("红宝石鱼（jewel_cichlid） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("jewelCichlidMaxKg", 0.15, 0.001, 500);
        jewelCichlidPullSpeed = b
                .comment("红宝石鱼（jewel_cichlid） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("jewelCichlidPullSpeed", 1.725, 0, 20);
        jewelCichlidFatiguedPullSpeed = b
                .comment("红宝石鱼（jewel_cichlid） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("jewelCichlidFatiguedPullSpeed", 0.7245, 0, 20);
        jewelCichlidStamina = b
                .comment("红宝石鱼（jewel_cichlid） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("jewelCichlidStamina", 130.0, 1, 5000);
        jewelCichlidTensionGain = b
                .comment("红宝石鱼（jewel_cichlid） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("jewelCichlidTensionGain", 1.1, 0, 5);
        jewelCichlidSprintDurationSec = b
                .comment("红宝石鱼（jewel_cichlid） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("jewelCichlidSprintDurationSec", 0.8, 0, 60);
        jewelCichlidSprintIntervalSec = b
                .comment("红宝石鱼（jewel_cichlid） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("jewelCichlidSprintIntervalSec", 4.5, 0.1, 60);
        jewelCichlidTurnIntervalSec = b
                .comment("红宝石鱼（jewel_cichlid） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("jewelCichlidTurnIntervalSec", 3.5, 0.2, 60);
        jewelCichlidFakeOutChance = b
                .comment("红宝石鱼（jewel_cichlid） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("jewelCichlidFakeOutChance", 0.0, 0, 1);
        jewelCichlidFatigueThreshold = b
                .comment("红宝石鱼（jewel_cichlid） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("jewelCichlidFatigueThreshold", 0.3, 0.01, 1);
        jewelCichlidFatigueDurationSec = b
                .comment("红宝石鱼（jewel_cichlid） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("jewelCichlidFatigueDurationSec", 2.0, 0, 120);
        jewelCichlidStaminaRegen = b
                .comment("红宝石鱼（jewel_cichlid） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("jewelCichlidStaminaRegen", 0.02, 0, 1);
        guppyMinKg = b
                .comment("孔雀鱼（guppy） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("guppyMinKg", 0.001, 0.001, 500);
        guppyMaxKg = b
                .comment("孔雀鱼（guppy） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("guppyMaxKg", 0.006, 0.001, 500);
        guppyPullSpeed = b
                .comment("孔雀鱼（guppy） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("guppyPullSpeed", 1.575, 0, 20);
        guppyFatiguedPullSpeed = b
                .comment("孔雀鱼（guppy） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("guppyFatiguedPullSpeed", 0.6615, 0, 20);
        guppyStamina = b
                .comment("孔雀鱼（guppy） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("guppyStamina", 60.0, 1, 5000);
        guppyTensionGain = b
                .comment("孔雀鱼（guppy） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("guppyTensionGain", 0.95, 0, 5);
        guppySprintDurationSec = b
                .comment("孔雀鱼（guppy） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("guppySprintDurationSec", 0.8, 0, 60);
        guppySprintIntervalSec = b
                .comment("孔雀鱼（guppy） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("guppySprintIntervalSec", 4.5, 0.1, 60);
        guppyTurnIntervalSec = b
                .comment("孔雀鱼（guppy） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("guppyTurnIntervalSec", 3.5, 0.2, 60);
        guppyFakeOutChance = b
                .comment("孔雀鱼（guppy） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("guppyFakeOutChance", 0.0, 0, 1);
        guppyFatigueThreshold = b
                .comment("孔雀鱼（guppy） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("guppyFatigueThreshold", 0.3, 0.01, 1);
        guppyFatigueDurationSec = b
                .comment("孔雀鱼（guppy） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("guppyFatigueDurationSec", 2.0, 0, 120);
        guppyStaminaRegen = b
                .comment("孔雀鱼（guppy） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("guppyStaminaRegen", 0.02, 0, 1);
        elephantnoseMinKg = b
                .comment("长吻象鼻鱼（elephantnose） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("elephantnoseMinKg", 0.03, 0.001, 500);
        elephantnoseMaxKg = b
                .comment("长吻象鼻鱼（elephantnose） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("elephantnoseMaxKg", 0.3, 0.001, 500);
        elephantnosePullSpeed = b
                .comment("长吻象鼻鱼（elephantnose） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("elephantnosePullSpeed", 1.8, 0, 20);
        elephantnoseFatiguedPullSpeed = b
                .comment("长吻象鼻鱼（elephantnose） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("elephantnoseFatiguedPullSpeed", 0.756, 0, 20);
        elephantnoseStamina = b
                .comment("长吻象鼻鱼（elephantnose） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("elephantnoseStamina", 170.0, 1, 5000);
        elephantnoseTensionGain = b
                .comment("长吻象鼻鱼（elephantnose） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("elephantnoseTensionGain", 1.15, 0, 5);
        elephantnoseSprintDurationSec = b
                .comment("长吻象鼻鱼（elephantnose） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("elephantnoseSprintDurationSec", 0.8, 0, 60);
        elephantnoseSprintIntervalSec = b
                .comment("长吻象鼻鱼（elephantnose） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("elephantnoseSprintIntervalSec", 4.5, 0.1, 60);
        elephantnoseTurnIntervalSec = b
                .comment("长吻象鼻鱼（elephantnose） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("elephantnoseTurnIntervalSec", 3.5, 0.2, 60);
        elephantnoseFakeOutChance = b
                .comment("长吻象鼻鱼（elephantnose） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("elephantnoseFakeOutChance", 0.0, 0, 1);
        elephantnoseFatigueThreshold = b
                .comment("长吻象鼻鱼（elephantnose） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("elephantnoseFatigueThreshold", 0.3, 0.01, 1);
        elephantnoseFatigueDurationSec = b
                .comment("长吻象鼻鱼（elephantnose） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("elephantnoseFatigueDurationSec", 2.0, 0, 120);
        elephantnoseStaminaRegen = b
                .comment("长吻象鼻鱼（elephantnose） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("elephantnoseStaminaRegen", 0.02, 0, 1);
        goldenLoachMinKg = b
                .comment("金泥鳅（golden_loach） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("goldenLoachMinKg", 0.03, 0.001, 500);
        goldenLoachMaxKg = b
                .comment("金泥鳅（golden_loach） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("goldenLoachMaxKg", 0.15, 0.001, 500);
        goldenLoachPullSpeed = b
                .comment("金泥鳅（golden_loach） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("goldenLoachPullSpeed", 0.75, 0, 20);
        goldenLoachFatiguedPullSpeed = b
                .comment("金泥鳅（golden_loach） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("goldenLoachFatiguedPullSpeed", 0.315, 0, 20);
        goldenLoachStamina = b
                .comment("金泥鳅（golden_loach） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("goldenLoachStamina", 60.0, 1, 5000);
        goldenLoachTensionGain = b
                .comment("金泥鳅（golden_loach） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("goldenLoachTensionGain", 0.95, 0, 5);
        goldenLoachSprintDurationSec = b
                .comment("金泥鳅（golden_loach） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("goldenLoachSprintDurationSec", 0.8, 0, 60);
        goldenLoachSprintIntervalSec = b
                .comment("金泥鳅（golden_loach） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("goldenLoachSprintIntervalSec", 4.5, 0.1, 60);
        goldenLoachTurnIntervalSec = b
                .comment("金泥鳅（golden_loach） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("goldenLoachTurnIntervalSec", 3.5, 0.2, 60);
        goldenLoachFakeOutChance = b
                .comment("金泥鳅（golden_loach） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("goldenLoachFakeOutChance", 0.0, 0, 1);
        goldenLoachFatigueThreshold = b
                .comment("金泥鳅（golden_loach） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("goldenLoachFatigueThreshold", 0.3, 0.01, 1);
        goldenLoachFatigueDurationSec = b
                .comment("金泥鳅（golden_loach） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("goldenLoachFatigueDurationSec", 2.0, 0, 120);
        goldenLoachStaminaRegen = b
                .comment("金泥鳅（golden_loach） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("goldenLoachStaminaRegen", 0.02, 0, 1);
        caveBlindfishMinKg = b
                .comment("盲鱼（cave_blindfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("caveBlindfishMinKg", 0.005, 0.001, 500);
        caveBlindfishMaxKg = b
                .comment("盲鱼（cave_blindfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("caveBlindfishMaxKg", 0.02, 0.001, 500);
        caveBlindfishPullSpeed = b
                .comment("盲鱼（cave_blindfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("caveBlindfishPullSpeed", 0.6, 0, 20);
        caveBlindfishFatiguedPullSpeed = b
                .comment("盲鱼（cave_blindfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("caveBlindfishFatiguedPullSpeed", 0.252, 0, 20);
        caveBlindfishStamina = b
                .comment("盲鱼（cave_blindfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("caveBlindfishStamina", 45.0, 1, 5000);
        caveBlindfishTensionGain = b
                .comment("盲鱼（cave_blindfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("caveBlindfishTensionGain", 0.85, 0, 5);
        caveBlindfishSprintDurationSec = b
                .comment("盲鱼（cave_blindfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("caveBlindfishSprintDurationSec", 0.8, 0, 60);
        caveBlindfishSprintIntervalSec = b
                .comment("盲鱼（cave_blindfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("caveBlindfishSprintIntervalSec", 4.5, 0.1, 60);
        caveBlindfishTurnIntervalSec = b
                .comment("盲鱼（cave_blindfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("caveBlindfishTurnIntervalSec", 3.5, 0.2, 60);
        caveBlindfishFakeOutChance = b
                .comment("盲鱼（cave_blindfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("caveBlindfishFakeOutChance", 0.0, 0, 1);
        caveBlindfishFatigueThreshold = b
                .comment("盲鱼（cave_blindfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("caveBlindfishFatigueThreshold", 0.3, 0.01, 1);
        caveBlindfishFatigueDurationSec = b
                .comment("盲鱼（cave_blindfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("caveBlindfishFatigueDurationSec", 2.0, 0, 120);
        caveBlindfishStaminaRegen = b
                .comment("盲鱼（cave_blindfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("caveBlindfishStaminaRegen", 0.02, 0, 1);
        northAfricanCatfishMinKg = b
                .comment("北非胡子鲇（north_african_catfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("northAfricanCatfishMinKg", 2.0, 0.001, 500);
        northAfricanCatfishMaxKg = b
                .comment("北非胡子鲇（north_african_catfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("northAfricanCatfishMaxKg", 16.0, 0.001, 500);
        northAfricanCatfishPullSpeed = b
                .comment("北非胡子鲇（north_african_catfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("northAfricanCatfishPullSpeed", 2.85, 0, 20);
        northAfricanCatfishFatiguedPullSpeed = b
                .comment("北非胡子鲇（north_african_catfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("northAfricanCatfishFatiguedPullSpeed", 1.197, 0, 20);
        northAfricanCatfishStamina = b
                .comment("北非胡子鲇（north_african_catfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("northAfricanCatfishStamina", 420.0, 1, 5000);
        northAfricanCatfishTensionGain = b
                .comment("北非胡子鲇（north_african_catfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("northAfricanCatfishTensionGain", 1.5, 0, 5);
        northAfricanCatfishSprintDurationSec = b
                .comment("北非胡子鲇（north_african_catfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("northAfricanCatfishSprintDurationSec", 0.4, 0, 60);
        northAfricanCatfishSprintIntervalSec = b
                .comment("北非胡子鲇（north_african_catfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("northAfricanCatfishSprintIntervalSec", 8.0, 0.1, 60);
        northAfricanCatfishTurnIntervalSec = b
                .comment("北非胡子鲇（north_african_catfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("northAfricanCatfishTurnIntervalSec", 5.5, 0.2, 60);
        northAfricanCatfishFakeOutChance = b
                .comment("北非胡子鲇（north_african_catfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("northAfricanCatfishFakeOutChance", 0.0, 0, 1);
        northAfricanCatfishFatigueThreshold = b
                .comment("北非胡子鲇（north_african_catfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("northAfricanCatfishFatigueThreshold", 0.28, 0.01, 1);
        northAfricanCatfishFatigueDurationSec = b
                .comment("北非胡子鲇（north_african_catfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("northAfricanCatfishFatigueDurationSec", 2.6, 0, 120);
        northAfricanCatfishStaminaRegen = b
                .comment("北非胡子鲇（north_african_catfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("northAfricanCatfishStaminaRegen", 0.015, 0, 1);
        rainbowTroutMinKg = b
                .comment("虹鳟（rainbow_trout） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("rainbowTroutMinKg", 0.4, 0.001, 500);
        rainbowTroutMaxKg = b
                .comment("虹鳟（rainbow_trout） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("rainbowTroutMaxKg", 8.0, 0.001, 500);
        rainbowTroutPullSpeed = b
                .comment("虹鳟（rainbow_trout） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("rainbowTroutPullSpeed", 3.975, 0, 20);
        rainbowTroutFatiguedPullSpeed = b
                .comment("虹鳟（rainbow_trout） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("rainbowTroutFatiguedPullSpeed", 1.6695, 0, 20);
        rainbowTroutStamina = b
                .comment("虹鳟（rainbow_trout） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("rainbowTroutStamina", 280.0, 1, 5000);
        rainbowTroutTensionGain = b
                .comment("虹鳟（rainbow_trout） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("rainbowTroutTensionGain", 1.9, 0, 5);
        rainbowTroutSprintDurationSec = b
                .comment("虹鳟（rainbow_trout） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("rainbowTroutSprintDurationSec", 1.6, 0, 60);
        rainbowTroutSprintIntervalSec = b
                .comment("虹鳟（rainbow_trout） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("rainbowTroutSprintIntervalSec", 2.6, 0.1, 60);
        rainbowTroutTurnIntervalSec = b
                .comment("虹鳟（rainbow_trout） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("rainbowTroutTurnIntervalSec", 3.0, 0.2, 60);
        rainbowTroutFakeOutChance = b
                .comment("虹鳟（rainbow_trout） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("rainbowTroutFakeOutChance", 0.1, 0, 1);
        rainbowTroutFatigueThreshold = b
                .comment("虹鳟（rainbow_trout） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("rainbowTroutFatigueThreshold", 0.34, 0.01, 1);
        rainbowTroutFatigueDurationSec = b
                .comment("虹鳟（rainbow_trout） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("rainbowTroutFatigueDurationSec", 2.4, 0, 120);
        rainbowTroutStaminaRegen = b
                .comment("虹鳟（rainbow_trout） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("rainbowTroutStaminaRegen", 0.02, 0, 1);
        arcticCharMinKg = b
                .comment("北极红点鲑（arctic_char） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("arcticCharMinKg", 0.5, 0.001, 500);
        arcticCharMaxKg = b
                .comment("北极红点鲑（arctic_char） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("arcticCharMaxKg", 7.0, 0.001, 500);
        arcticCharPullSpeed = b
                .comment("北极红点鲑（arctic_char） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("arcticCharPullSpeed", 3.975, 0, 20);
        arcticCharFatiguedPullSpeed = b
                .comment("北极红点鲑（arctic_char） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("arcticCharFatiguedPullSpeed", 1.6695, 0, 20);
        arcticCharStamina = b
                .comment("北极红点鲑（arctic_char） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("arcticCharStamina", 480.0, 1, 5000);
        arcticCharTensionGain = b
                .comment("北极红点鲑（arctic_char） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("arcticCharTensionGain", 2.3, 0, 5);
        arcticCharSprintDurationSec = b
                .comment("北极红点鲑（arctic_char） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("arcticCharSprintDurationSec", 0.4, 0, 60);
        arcticCharSprintIntervalSec = b
                .comment("北极红点鲑（arctic_char） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("arcticCharSprintIntervalSec", 8.0, 0.1, 60);
        arcticCharTurnIntervalSec = b
                .comment("北极红点鲑（arctic_char） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("arcticCharTurnIntervalSec", 5.5, 0.2, 60);
        arcticCharFakeOutChance = b
                .comment("北极红点鲑（arctic_char） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("arcticCharFakeOutChance", 0.0, 0, 1);
        arcticCharFatigueThreshold = b
                .comment("北极红点鲑（arctic_char） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("arcticCharFatigueThreshold", 0.28, 0.01, 1);
        arcticCharFatigueDurationSec = b
                .comment("北极红点鲑（arctic_char） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("arcticCharFatigueDurationSec", 2.6, 0, 120);
        arcticCharStaminaRegen = b
                .comment("北极红点鲑（arctic_char） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("arcticCharStaminaRegen", 0.015, 0, 1);
        burbotMinKg = b
                .comment("江鳕（burbot） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("burbotMinKg", 0.8, 0.001, 500);
        burbotMaxKg = b
                .comment("江鳕（burbot） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("burbotMaxKg", 12.0, 0.001, 500);
        burbotPullSpeed = b
                .comment("江鳕（burbot） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("burbotPullSpeed", 1.95, 0, 20);
        burbotFatiguedPullSpeed = b
                .comment("江鳕（burbot） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("burbotFatiguedPullSpeed", 0.819, 0, 20);
        burbotStamina = b
                .comment("江鳕（burbot） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("burbotStamina", 260.0, 1, 5000);
        burbotTensionGain = b
                .comment("江鳕（burbot） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("burbotTensionGain", 1.45, 0, 5);
        burbotSprintDurationSec = b
                .comment("江鳕（burbot） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("burbotSprintDurationSec", 0.4, 0, 60);
        burbotSprintIntervalSec = b
                .comment("江鳕（burbot） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("burbotSprintIntervalSec", 8.0, 0.1, 60);
        burbotTurnIntervalSec = b
                .comment("江鳕（burbot） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("burbotTurnIntervalSec", 5.5, 0.2, 60);
        burbotFakeOutChance = b
                .comment("江鳕（burbot） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("burbotFakeOutChance", 0.0, 0, 1);
        burbotFatigueThreshold = b
                .comment("江鳕（burbot） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("burbotFatigueThreshold", 0.28, 0.01, 1);
        burbotFatigueDurationSec = b
                .comment("江鳕（burbot） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("burbotFatigueDurationSec", 2.6, 0, 120);
        burbotStaminaRegen = b
                .comment("江鳕（burbot） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("burbotStaminaRegen", 0.015, 0, 1);
        whitefishMinKg = b
                .comment("高白鲑（whitefish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("whitefishMinKg", 0.2, 0.001, 500);
        whitefishMaxKg = b
                .comment("高白鲑（whitefish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("whitefishMaxKg", 3.0, 0.001, 500);
        whitefishPullSpeed = b
                .comment("高白鲑（whitefish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("whitefishPullSpeed", 1.35, 0, 20);
        whitefishFatiguedPullSpeed = b
                .comment("高白鲑（whitefish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("whitefishFatiguedPullSpeed", 0.5670000000000001, 0, 20);
        whitefishStamina = b
                .comment("高白鲑（whitefish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("whitefishStamina", 140.0, 1, 5000);
        whitefishTensionGain = b
                .comment("高白鲑（whitefish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("whitefishTensionGain", 1.05, 0, 5);
        whitefishSprintDurationSec = b
                .comment("高白鲑（whitefish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("whitefishSprintDurationSec", 0.8, 0, 60);
        whitefishSprintIntervalSec = b
                .comment("高白鲑（whitefish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("whitefishSprintIntervalSec", 4.5, 0.1, 60);
        whitefishTurnIntervalSec = b
                .comment("高白鲑（whitefish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("whitefishTurnIntervalSec", 3.5, 0.2, 60);
        whitefishFakeOutChance = b
                .comment("高白鲑（whitefish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("whitefishFakeOutChance", 0.0, 0, 1);
        whitefishFatigueThreshold = b
                .comment("高白鲑（whitefish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("whitefishFatigueThreshold", 0.3, 0.01, 1);
        whitefishFatigueDurationSec = b
                .comment("高白鲑（whitefish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("whitefishFatigueDurationSec", 2.0, 0, 120);
        whitefishStaminaRegen = b
                .comment("高白鲑（whitefish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("whitefishStaminaRegen", 0.02, 0, 1);
        taimenMinKg = b
                .comment("哲罗鲑（taimen） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("taimenMinKg", 3.0, 0.001, 500);
        taimenMaxKg = b
                .comment("哲罗鲑（taimen） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("taimenMaxKg", 45.0, 0.001, 500);
        taimenPullSpeed = b
                .comment("哲罗鲑（taimen） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("taimenPullSpeed", 4.8, 0, 20);
        taimenFatiguedPullSpeed = b
                .comment("哲罗鲑（taimen） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("taimenFatiguedPullSpeed", 2.016, 0, 20);
        taimenStamina = b
                .comment("哲罗鲑（taimen） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("taimenStamina", 560.0, 1, 5000);
        taimenTensionGain = b
                .comment("哲罗鲑（taimen） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("taimenTensionGain", 1.45, 0, 5);
        taimenSprintDurationSec = b
                .comment("哲罗鲑（taimen） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("taimenSprintDurationSec", 1.6, 0, 60);
        taimenSprintIntervalSec = b
                .comment("哲罗鲑（taimen） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("taimenSprintIntervalSec", 2.6, 0.1, 60);
        taimenTurnIntervalSec = b
                .comment("哲罗鲑（taimen） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("taimenTurnIntervalSec", 3.0, 0.2, 60);
        taimenFakeOutChance = b
                .comment("哲罗鲑（taimen） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("taimenFakeOutChance", 0.1, 0, 1);
        taimenFatigueThreshold = b
                .comment("哲罗鲑（taimen） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("taimenFatigueThreshold", 0.34, 0.01, 1);
        taimenFatigueDurationSec = b
                .comment("哲罗鲑（taimen） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("taimenFatigueDurationSec", 2.4, 0, 120);
        taimenStaminaRegen = b
                .comment("哲罗鲑（taimen） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("taimenStaminaRegen", 0.02, 0, 1);
        arcticCodMinKg = b
                .comment("北极鳕（arctic_cod） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("arcticCodMinKg", 0.05, 0.001, 500);
        arcticCodMaxKg = b
                .comment("北极鳕（arctic_cod） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("arcticCodMaxKg", 0.5, 0.001, 500);
        arcticCodPullSpeed = b
                .comment("北极鳕（arctic_cod） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("arcticCodPullSpeed", 0.9, 0, 20);
        arcticCodFatiguedPullSpeed = b
                .comment("北极鳕（arctic_cod） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("arcticCodFatiguedPullSpeed", 0.378, 0, 20);
        arcticCodStamina = b
                .comment("北极鳕（arctic_cod） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("arcticCodStamina", 60.0, 1, 5000);
        arcticCodTensionGain = b
                .comment("北极鳕（arctic_cod） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("arcticCodTensionGain", 0.95, 0, 5);
        arcticCodSprintDurationSec = b
                .comment("北极鳕（arctic_cod） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("arcticCodSprintDurationSec", 0.8, 0, 60);
        arcticCodSprintIntervalSec = b
                .comment("北极鳕（arctic_cod） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("arcticCodSprintIntervalSec", 4.5, 0.1, 60);
        arcticCodTurnIntervalSec = b
                .comment("北极鳕（arctic_cod） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("arcticCodTurnIntervalSec", 3.5, 0.2, 60);
        arcticCodFakeOutChance = b
                .comment("北极鳕（arctic_cod） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("arcticCodFakeOutChance", 0.0, 0, 1);
        arcticCodFatigueThreshold = b
                .comment("北极鳕（arctic_cod） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("arcticCodFatigueThreshold", 0.3, 0.01, 1);
        arcticCodFatigueDurationSec = b
                .comment("北极鳕（arctic_cod） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("arcticCodFatigueDurationSec", 2.0, 0, 120);
        arcticCodStaminaRegen = b
                .comment("北极鳕（arctic_cod） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("arcticCodStaminaRegen", 0.02, 0, 1);
        capelinMinKg = b
                .comment("毛鳞鱼（capelin） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("capelinMinKg", 0.01, 0.001, 500);
        capelinMaxKg = b
                .comment("毛鳞鱼（capelin） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("capelinMaxKg", 0.1, 0.001, 500);
        capelinPullSpeed = b
                .comment("毛鳞鱼（capelin） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("capelinPullSpeed", 0.75, 0, 20);
        capelinFatiguedPullSpeed = b
                .comment("毛鳞鱼（capelin） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("capelinFatiguedPullSpeed", 0.315, 0, 20);
        capelinStamina = b
                .comment("毛鳞鱼（capelin） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("capelinStamina", 40.0, 1, 5000);
        capelinTensionGain = b
                .comment("毛鳞鱼（capelin） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("capelinTensionGain", 0.9, 0, 5);
        capelinSprintDurationSec = b
                .comment("毛鳞鱼（capelin） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("capelinSprintDurationSec", 0.8, 0, 60);
        capelinSprintIntervalSec = b
                .comment("毛鳞鱼（capelin） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("capelinSprintIntervalSec", 4.5, 0.1, 60);
        capelinTurnIntervalSec = b
                .comment("毛鳞鱼（capelin） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("capelinTurnIntervalSec", 3.5, 0.2, 60);
        capelinFakeOutChance = b
                .comment("毛鳞鱼（capelin） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("capelinFakeOutChance", 0.0, 0, 1);
        capelinFatigueThreshold = b
                .comment("毛鳞鱼（capelin） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("capelinFatigueThreshold", 0.3, 0.01, 1);
        capelinFatigueDurationSec = b
                .comment("毛鳞鱼（capelin） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("capelinFatigueDurationSec", 2.0, 0, 120);
        capelinStaminaRegen = b
                .comment("毛鳞鱼（capelin） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("capelinStaminaRegen", 0.02, 0, 1);
        atlanticHerringMinKg = b
                .comment("大西洋鲱（atlantic_herring） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("atlanticHerringMinKg", 0.1, 0.001, 500);
        atlanticHerringMaxKg = b
                .comment("大西洋鲱（atlantic_herring） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("atlanticHerringMaxKg", 1.0, 0.001, 500);
        atlanticHerringPullSpeed = b
                .comment("大西洋鲱（atlantic_herring） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("atlanticHerringPullSpeed", 1.275, 0, 20);
        atlanticHerringFatiguedPullSpeed = b
                .comment("大西洋鲱（atlantic_herring） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("atlanticHerringFatiguedPullSpeed", 0.5355, 0, 20);
        atlanticHerringStamina = b
                .comment("大西洋鲱（atlantic_herring） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("atlanticHerringStamina", 110.0, 1, 5000);
        atlanticHerringTensionGain = b
                .comment("大西洋鲱（atlantic_herring） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("atlanticHerringTensionGain", 1.0, 0, 5);
        atlanticHerringSprintDurationSec = b
                .comment("大西洋鲱（atlantic_herring） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("atlanticHerringSprintDurationSec", 0.8, 0, 60);
        atlanticHerringSprintIntervalSec = b
                .comment("大西洋鲱（atlantic_herring） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("atlanticHerringSprintIntervalSec", 4.5, 0.1, 60);
        atlanticHerringTurnIntervalSec = b
                .comment("大西洋鲱（atlantic_herring） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("atlanticHerringTurnIntervalSec", 3.5, 0.2, 60);
        atlanticHerringFakeOutChance = b
                .comment("大西洋鲱（atlantic_herring） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("atlanticHerringFakeOutChance", 0.0, 0, 1);
        atlanticHerringFatigueThreshold = b
                .comment("大西洋鲱（atlantic_herring） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("atlanticHerringFatigueThreshold", 0.3, 0.01, 1);
        atlanticHerringFatigueDurationSec = b
                .comment("大西洋鲱（atlantic_herring） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("atlanticHerringFatigueDurationSec", 2.0, 0, 120);
        atlanticHerringStaminaRegen = b
                .comment("大西洋鲱（atlantic_herring） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("atlanticHerringStaminaRegen", 0.02, 0, 1);
        wolffishMinKg = b
                .comment("狼鱼（wolffish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("wolffishMinKg", 3.0, 0.001, 500);
        wolffishMaxKg = b
                .comment("狼鱼（wolffish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("wolffishMaxKg", 20.0, 0.001, 500);
        wolffishPullSpeed = b
                .comment("狼鱼（wolffish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("wolffishPullSpeed", 2.325, 0, 20);
        wolffishFatiguedPullSpeed = b
                .comment("狼鱼（wolffish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("wolffishFatiguedPullSpeed", 0.9765, 0, 20);
        wolffishStamina = b
                .comment("狼鱼（wolffish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("wolffishStamina", 300.0, 1, 5000);
        wolffishTensionGain = b
                .comment("狼鱼（wolffish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("wolffishTensionGain", 1.5, 0, 5);
        wolffishSprintDurationSec = b
                .comment("狼鱼（wolffish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("wolffishSprintDurationSec", 0.4, 0, 60);
        wolffishSprintIntervalSec = b
                .comment("狼鱼（wolffish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("wolffishSprintIntervalSec", 8.0, 0.1, 60);
        wolffishTurnIntervalSec = b
                .comment("狼鱼（wolffish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("wolffishTurnIntervalSec", 5.5, 0.2, 60);
        wolffishFakeOutChance = b
                .comment("狼鱼（wolffish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("wolffishFakeOutChance", 0.0, 0, 1);
        wolffishFatigueThreshold = b
                .comment("狼鱼（wolffish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("wolffishFatigueThreshold", 0.28, 0.01, 1);
        wolffishFatigueDurationSec = b
                .comment("狼鱼（wolffish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("wolffishFatigueDurationSec", 2.6, 0, 120);
        wolffishStaminaRegen = b
                .comment("狼鱼（wolffish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("wolffishStaminaRegen", 0.015, 0, 1);
        redfishMinKg = b
                .comment("平鲉（redfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("redfishMinKg", 1.0, 0.001, 500);
        redfishMaxKg = b
                .comment("平鲉（redfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("redfishMaxKg", 15.0, 0.001, 500);
        redfishPullSpeed = b
                .comment("平鲉（redfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("redfishPullSpeed", 4.2, 0, 20);
        redfishFatiguedPullSpeed = b
                .comment("平鲉（redfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("redfishFatiguedPullSpeed", 1.764, 0, 20);
        redfishStamina = b
                .comment("平鲉（redfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("redfishStamina", 380.0, 1, 5000);
        redfishTensionGain = b
                .comment("平鲉（redfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("redfishTensionGain", 2.3, 0, 5);
        redfishSprintDurationSec = b
                .comment("平鲉（redfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("redfishSprintDurationSec", 0.4, 0, 60);
        redfishSprintIntervalSec = b
                .comment("平鲉（redfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("redfishSprintIntervalSec", 8.0, 0.1, 60);
        redfishTurnIntervalSec = b
                .comment("平鲉（redfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("redfishTurnIntervalSec", 5.5, 0.2, 60);
        redfishFakeOutChance = b
                .comment("平鲉（redfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("redfishFakeOutChance", 0.0, 0, 1);
        redfishFatigueThreshold = b
                .comment("平鲉（redfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("redfishFatigueThreshold", 0.28, 0.01, 1);
        redfishFatigueDurationSec = b
                .comment("平鲉（redfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("redfishFatigueDurationSec", 2.6, 0, 120);
        redfishStaminaRegen = b
                .comment("平鲉（redfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("redfishStaminaRegen", 0.015, 0, 1);
        sablefishMinKg = b
                .comment("裸盖鱼（sablefish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("sablefishMinKg", 5.0, 0.001, 500);
        sablefishMaxKg = b
                .comment("裸盖鱼（sablefish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("sablefishMaxKg", 40.0, 0.001, 500);
        sablefishPullSpeed = b
                .comment("裸盖鱼（sablefish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("sablefishPullSpeed", 2.4, 0, 20);
        sablefishFatiguedPullSpeed = b
                .comment("裸盖鱼（sablefish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("sablefishFatiguedPullSpeed", 1.008, 0, 20);
        sablefishStamina = b
                .comment("裸盖鱼（sablefish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("sablefishStamina", 380.0, 1, 5000);
        sablefishTensionGain = b
                .comment("裸盖鱼（sablefish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("sablefishTensionGain", 1.25, 0, 5);
        sablefishSprintDurationSec = b
                .comment("裸盖鱼（sablefish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("sablefishSprintDurationSec", 0.4, 0, 60);
        sablefishSprintIntervalSec = b
                .comment("裸盖鱼（sablefish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("sablefishSprintIntervalSec", 8.0, 0.1, 60);
        sablefishTurnIntervalSec = b
                .comment("裸盖鱼（sablefish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("sablefishTurnIntervalSec", 5.5, 0.2, 60);
        sablefishFakeOutChance = b
                .comment("裸盖鱼（sablefish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("sablefishFakeOutChance", 0.0, 0, 1);
        sablefishFatigueThreshold = b
                .comment("裸盖鱼（sablefish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("sablefishFatigueThreshold", 0.28, 0.01, 1);
        sablefishFatigueDurationSec = b
                .comment("裸盖鱼（sablefish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("sablefishFatigueDurationSec", 2.6, 0, 120);
        sablefishStaminaRegen = b
                .comment("裸盖鱼（sablefish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("sablefishStaminaRegen", 0.015, 0, 1);
        atlanticCodMinKg = b
                .comment("大西洋鳕（atlantic_cod） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("atlanticCodMinKg", 2.0, 0.001, 500);
        atlanticCodMaxKg = b
                .comment("大西洋鳕（atlantic_cod） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("atlanticCodMaxKg", 40.0, 0.001, 500);
        atlanticCodPullSpeed = b
                .comment("大西洋鳕（atlantic_cod） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("atlanticCodPullSpeed", 2.55, 0, 20);
        atlanticCodFatiguedPullSpeed = b
                .comment("大西洋鳕（atlantic_cod） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("atlanticCodFatiguedPullSpeed", 1.071, 0, 20);
        atlanticCodStamina = b
                .comment("大西洋鳕（atlantic_cod） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("atlanticCodStamina", 320.0, 1, 5000);
        atlanticCodTensionGain = b
                .comment("大西洋鳕（atlantic_cod） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("atlanticCodTensionGain", 1.15, 0, 5);
        atlanticCodSprintDurationSec = b
                .comment("大西洋鳕（atlantic_cod） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("atlanticCodSprintDurationSec", 0.8, 0, 60);
        atlanticCodSprintIntervalSec = b
                .comment("大西洋鳕（atlantic_cod） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("atlanticCodSprintIntervalSec", 4.5, 0.1, 60);
        atlanticCodTurnIntervalSec = b
                .comment("大西洋鳕（atlantic_cod） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("atlanticCodTurnIntervalSec", 3.5, 0.2, 60);
        atlanticCodFakeOutChance = b
                .comment("大西洋鳕（atlantic_cod） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("atlanticCodFakeOutChance", 0.0, 0, 1);
        atlanticCodFatigueThreshold = b
                .comment("大西洋鳕（atlantic_cod） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("atlanticCodFatigueThreshold", 0.3, 0.01, 1);
        atlanticCodFatigueDurationSec = b
                .comment("大西洋鳕（atlantic_cod） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("atlanticCodFatigueDurationSec", 2.0, 0, 120);
        atlanticCodStaminaRegen = b
                .comment("大西洋鳕（atlantic_cod） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("atlanticCodStaminaRegen", 0.02, 0, 1);
        greenlandHalibutMinKg = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("greenlandHalibutMinKg", 20.0, 0.001, 500);
        greenlandHalibutMaxKg = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("greenlandHalibutMaxKg", 200.0, 0.001, 500);
        greenlandHalibutPullSpeed = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("greenlandHalibutPullSpeed", 2.85, 0, 20);
        greenlandHalibutFatiguedPullSpeed = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("greenlandHalibutFatiguedPullSpeed", 1.197, 0, 20);
        greenlandHalibutStamina = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("greenlandHalibutStamina", 600.0, 1, 5000);
        greenlandHalibutTensionGain = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("greenlandHalibutTensionGain", 1.45, 0, 5);
        greenlandHalibutSprintDurationSec = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("greenlandHalibutSprintDurationSec", 0.4, 0, 60);
        greenlandHalibutSprintIntervalSec = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("greenlandHalibutSprintIntervalSec", 8.0, 0.1, 60);
        greenlandHalibutTurnIntervalSec = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("greenlandHalibutTurnIntervalSec", 5.5, 0.2, 60);
        greenlandHalibutFakeOutChance = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("greenlandHalibutFakeOutChance", 0.0, 0, 1);
        greenlandHalibutFatigueThreshold = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("greenlandHalibutFatigueThreshold", 0.28, 0.01, 1);
        greenlandHalibutFatigueDurationSec = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("greenlandHalibutFatigueDurationSec", 2.6, 0, 120);
        greenlandHalibutStaminaRegen = b
                .comment("格陵兰大比目鱼（greenland_halibut） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("greenlandHalibutStaminaRegen", 0.015, 0, 1);
        greenlandSharkMinKg = b
                .comment("格陵兰鲨（greenland_shark） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("greenlandSharkMinKg", 80.0, 0.001, 500);
        greenlandSharkMaxKg = b
                .comment("格陵兰鲨（greenland_shark） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("greenlandSharkMaxKg", 500.0, 0.001, 500);
        greenlandSharkPullSpeed = b
                .comment("格陵兰鲨（greenland_shark） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("greenlandSharkPullSpeed", 4.8, 0, 20);
        greenlandSharkFatiguedPullSpeed = b
                .comment("格陵兰鲨（greenland_shark） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("greenlandSharkFatiguedPullSpeed", 2.016, 0, 20);
        greenlandSharkStamina = b
                .comment("格陵兰鲨（greenland_shark） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("greenlandSharkStamina", 400.0, 1, 5000);
        greenlandSharkTensionGain = b
                .comment("格陵兰鲨（greenland_shark） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("greenlandSharkTensionGain", 1.75, 0, 5);
        greenlandSharkSprintDurationSec = b
                .comment("格陵兰鲨（greenland_shark） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("greenlandSharkSprintDurationSec", 0.4, 0, 60);
        greenlandSharkSprintIntervalSec = b
                .comment("格陵兰鲨（greenland_shark） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("greenlandSharkSprintIntervalSec", 8.0, 0.1, 60);
        greenlandSharkTurnIntervalSec = b
                .comment("格陵兰鲨（greenland_shark） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("greenlandSharkTurnIntervalSec", 5.5, 0.2, 60);
        greenlandSharkFakeOutChance = b
                .comment("格陵兰鲨（greenland_shark） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("greenlandSharkFakeOutChance", 0.0, 0, 1);
        greenlandSharkFatigueThreshold = b
                .comment("格陵兰鲨（greenland_shark） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("greenlandSharkFatigueThreshold", 0.28, 0.01, 1);
        greenlandSharkFatigueDurationSec = b
                .comment("格陵兰鲨（greenland_shark） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("greenlandSharkFatigueDurationSec", 2.6, 0, 120);
        greenlandSharkStaminaRegen = b
                .comment("格陵兰鲨（greenland_shark） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("greenlandSharkStaminaRegen", 0.015, 0, 1);
        sailfishMinKg = b
                .comment("雨伞旗鱼（sailfish） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("sailfishMinKg", 20.0, 0.001, 500);
        sailfishMaxKg = b
                .comment("雨伞旗鱼（sailfish） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("sailfishMaxKg", 90.0, 0.001, 500);
        sailfishPullSpeed = b
                .comment("雨伞旗鱼（sailfish） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("sailfishPullSpeed", 6.3, 0, 20);
        sailfishFatiguedPullSpeed = b
                .comment("雨伞旗鱼（sailfish） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("sailfishFatiguedPullSpeed", 2.646, 0, 20);
        sailfishStamina = b
                .comment("雨伞旗鱼（sailfish） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("sailfishStamina", 380.0, 1, 5000);
        sailfishTensionGain = b
                .comment("雨伞旗鱼（sailfish） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("sailfishTensionGain", 1.25, 0, 5);
        sailfishSprintDurationSec = b
                .comment("雨伞旗鱼（sailfish） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("sailfishSprintDurationSec", 1.6, 0, 60);
        sailfishSprintIntervalSec = b
                .comment("雨伞旗鱼（sailfish） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("sailfishSprintIntervalSec", 2.6, 0.1, 60);
        sailfishTurnIntervalSec = b
                .comment("雨伞旗鱼（sailfish） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("sailfishTurnIntervalSec", 3.0, 0.2, 60);
        sailfishFakeOutChance = b
                .comment("雨伞旗鱼（sailfish） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("sailfishFakeOutChance", 0.1, 0, 1);
        sailfishFatigueThreshold = b
                .comment("雨伞旗鱼（sailfish） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("sailfishFatigueThreshold", 0.34, 0.01, 1);
        sailfishFatigueDurationSec = b
                .comment("雨伞旗鱼（sailfish） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("sailfishFatigueDurationSec", 2.4, 0, 120);
        sailfishStaminaRegen = b
                .comment("雨伞旗鱼（sailfish） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("sailfishStaminaRegen", 0.02, 0, 1);
        mahiMahiMinKg = b
                .comment("鲯鳅（mahi_mahi） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("mahiMahiMinKg", 1.0, 0.001, 500);
        mahiMahiMaxKg = b
                .comment("鲯鳅（mahi_mahi） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("mahiMahiMaxKg", 20.0, 0.001, 500);
        mahiMahiPullSpeed = b
                .comment("鲯鳅（mahi_mahi） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("mahiMahiPullSpeed", 5.0, 0, 20);
        mahiMahiFatiguedPullSpeed = b
                .comment("鲯鳅（mahi_mahi） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("mahiMahiFatiguedPullSpeed", 1.8900000000000001, 0, 20);
        mahiMahiStamina = b
                .comment("鲯鳅（mahi_mahi） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("mahiMahiStamina", 240.0, 1, 5000);
        mahiMahiTensionGain = b
                .comment("鲯鳅（mahi_mahi） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("mahiMahiTensionGain", 1.55, 0, 5);
        mahiMahiSprintDurationSec = b
                .comment("鲯鳅（mahi_mahi） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("mahiMahiSprintDurationSec", 1.6, 0, 60);
        mahiMahiSprintIntervalSec = b
                .comment("鲯鳅（mahi_mahi） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("mahiMahiSprintIntervalSec", 2.6, 0.1, 60);
        mahiMahiTurnIntervalSec = b
                .comment("鲯鳅（mahi_mahi） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("mahiMahiTurnIntervalSec", 3.0, 0.2, 60);
        mahiMahiFakeOutChance = b
                .comment("鲯鳅（mahi_mahi） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("mahiMahiFakeOutChance", 0.1, 0, 1);
        mahiMahiFatigueThreshold = b
                .comment("鲯鳅（mahi_mahi） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("mahiMahiFatigueThreshold", 0.34, 0.01, 1);
        mahiMahiFatigueDurationSec = b
                .comment("鲯鳅（mahi_mahi） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("mahiMahiFatigueDurationSec", 2.4, 0, 120);
        mahiMahiStaminaRegen = b
                .comment("鲯鳅（mahi_mahi） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("mahiMahiStaminaRegen", 0.02, 0, 1);
        alligatorGarMinKg = b
                .comment("鳄雀鳝（alligator_gar） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("alligatorGarMinKg", 20.0, 0.001, 500);
        alligatorGarMaxKg = b
                .comment("鳄雀鳝（alligator_gar） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("alligatorGarMaxKg", 80.0, 0.001, 500);
        alligatorGarPullSpeed = b
                .comment("鳄雀鳝（alligator_gar） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("alligatorGarPullSpeed", 4.425, 0, 20);
        alligatorGarFatiguedPullSpeed = b
                .comment("鳄雀鳝（alligator_gar） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("alligatorGarFatiguedPullSpeed", 1.8584999999999998, 0, 20);
        alligatorGarStamina = b
                .comment("鳄雀鳝（alligator_gar） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("alligatorGarStamina", 480.0, 1, 5000);
        alligatorGarTensionGain = b
                .comment("鳄雀鳝（alligator_gar） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("alligatorGarTensionGain", 1.55, 0, 5);
        alligatorGarSprintDurationSec = b
                .comment("鳄雀鳝（alligator_gar） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("alligatorGarSprintDurationSec", 0.9, 0, 60);
        alligatorGarSprintIntervalSec = b
                .comment("鳄雀鳝（alligator_gar） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("alligatorGarSprintIntervalSec", 3.0, 0.1, 60);
        alligatorGarTurnIntervalSec = b
                .comment("鳄雀鳝（alligator_gar） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("alligatorGarTurnIntervalSec", 1.3, 0.2, 60);
        alligatorGarFakeOutChance = b
                .comment("鳄雀鳝（alligator_gar） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("alligatorGarFakeOutChance", 0.35, 0, 1);
        alligatorGarFatigueThreshold = b
                .comment("鳄雀鳝（alligator_gar） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("alligatorGarFatigueThreshold", 0.32, 0.01, 1);
        alligatorGarFatigueDurationSec = b
                .comment("鳄雀鳝（alligator_gar） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("alligatorGarFatigueDurationSec", 2.0, 0, 120);
        alligatorGarStaminaRegen = b
                .comment("鳄雀鳝（alligator_gar） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("alligatorGarStaminaRegen", 0.02, 0, 1);
        giantSiameseCarpMinKg = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("giantSiameseCarpMinKg", 30.0, 0.001, 500);
        giantSiameseCarpMaxKg = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("giantSiameseCarpMaxKg", 200.0, 0.001, 500);
        giantSiameseCarpPullSpeed = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("giantSiameseCarpPullSpeed", 3.975, 0, 20);
        giantSiameseCarpFatiguedPullSpeed = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("giantSiameseCarpFatiguedPullSpeed", 1.6695, 0, 20);
        giantSiameseCarpStamina = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("giantSiameseCarpStamina", 480.0, 1, 5000);
        giantSiameseCarpTensionGain = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("giantSiameseCarpTensionGain", 1.6, 0, 5);
        giantSiameseCarpSprintDurationSec = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("giantSiameseCarpSprintDurationSec", 0.4, 0, 60);
        giantSiameseCarpSprintIntervalSec = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("giantSiameseCarpSprintIntervalSec", 8.0, 0.1, 60);
        giantSiameseCarpTurnIntervalSec = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("giantSiameseCarpTurnIntervalSec", 5.5, 0.2, 60);
        giantSiameseCarpFakeOutChance = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("giantSiameseCarpFakeOutChance", 0.0, 0, 1);
        giantSiameseCarpFatigueThreshold = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("giantSiameseCarpFatigueThreshold", 0.28, 0.01, 1);
        giantSiameseCarpFatigueDurationSec = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("giantSiameseCarpFatigueDurationSec", 2.6, 0, 120);
        giantSiameseCarpStaminaRegen = b
                .comment("巨暹罗鲤（giant_siamese_carp） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("giantSiameseCarpStaminaRegen", 0.015, 0, 1);
        electricEelMinKg = b
                .comment("电鳗（electric_eel） · 体重下限（kg） —— 这种鱼最小能有多重。⚠ 它和体重上限一起决定档位怎么分——改小了会让低档鱼变少")
                .defineInRange("electricEelMinKg", 1.0, 0.001, 500);
        electricEelMaxKg = b
                .comment("电鳗（electric_eel） · 体重上限（kg） —— 这种鱼最大能有多重。线长/承重不够时会钓不上来（见 buildPool 的承重过滤）")
                .defineInRange("electricEelMaxKg", 12.0, 0.001, 500);
        electricEelPullSpeed = b
                .comment("电鳗（electric_eel） · 拉力（不挣扎） —— 挣扎时把线拽出去的速度。越大越拉不动。这是「上钩两秒就能感觉出多大」的主要来源。⚠ 这是**基准值**：实际游速 = 本值 × 档位曲线 1.12^档 × 金/红手感修正（金 ×0.84、红 ×1.05）")
                .defineInRange("electricEelPullSpeed", 2.7, 0, 20);
        electricEelFatiguedPullSpeed = b
                .comment("电鳗（electric_eel） · 拉力（疲劳期） —— 遛到疲劳后的拉拽速度。默认 = 正常拉力 × gameplay.fatiguePullMultiplier（当前 0.42）—— 改大就等于「疲劳期反而不划算」")
                .defineInRange("electricEelFatiguedPullSpeed", 1.1340000000000001, 0, 20);
        electricEelStamina = b
                .comment("电鳗（electric_eel） · 体力上限 —— 鱼有多少体力。越大越耐磨，要遛更久。它是「持久战」强度的总开关。⚠ 这是**基准值**（按鱼种）：实际体力池 = 本值 × 档位倍率（金档 ×1.0、红档 ×1.5）。红档还有一条**基准下限**（gameplay.tierRedStaminaMin，默认 200）：基准低于它的红档鱼先被抬到下限，再乘倍率 —— 否则台钓唯一的红鱼（蓝宝石雷龙鱼 130）比一条普通草鱼还好遛。咬钩时日志里会打一行「体力池：X = 基准 Y × 档位倍率 Z」——对账看那一行，别看这里的基准")
                .defineInRange("electricEelStamina", 260.0, 1, 5000);
        electricEelTensionGain = b
                .comment("电鳗（electric_eel） · 张力涨速 —— 这条鱼让线涨得多快。>1 = 线黄得快（黏液型鱼的味道），<1 = 宽容")
                .defineInRange("electricEelTensionGain", 1.45, 0, 5);
        electricEelSprintDurationSec = b
                .comment("电鳗（electric_eel） · 冲刺时长（秒） —— 每次冲刺持续几秒。0 = 这条鱼不冲刺")
                .defineInRange("electricEelSprintDurationSec", 0.9, 0, 60);
        electricEelSprintIntervalSec = b
                .comment("电鳗（electric_eel） · 冲刺间隔（秒） —— 每隔几秒冲一次。越小越凶（爆发型 2.6，黏液型 8.0 几乎不冲）")
                .defineInRange("electricEelSprintIntervalSec", 3.0, 0.1, 60);
        electricEelTurnIntervalSec = b
                .comment("电鳗（electric_eel） · 变向间隔（秒） —— 每隔几秒变一次向。越小越神经质（神经质型 1.3，黏液型 5.5 方向很稳）")
                .defineInRange("electricEelTurnIntervalSec", 1.3, 0.2, 60);
        electricEelFakeOutChance = b
                .comment("电鳗（electric_eel） · 假动作概率（0~1） —— 假动作概率：预告一个方向、实际往反方向跑。0~1（神经质型 0.35）")
                .defineInRange("electricEelFakeOutChance", 0.35, 0, 1);
        electricEelFatigueThreshold = b
                .comment("电鳗（electric_eel） · 进疲劳的体力比例（0~1） —— 体力降到这个比例以下就进疲劳期（0.3 = 剩 30% 时）")
                .defineInRange("electricEelFatigueThreshold", 0.32, 0.01, 1);
        electricEelFatigueDurationSec = b
                .comment("电鳗（electric_eel） · 疲劳持续（秒） —— 疲劳期持续几秒。这段时间是反拉的最佳窗口")
                .defineInRange("electricEelFatigueDurationSec", 2.0, 0, 120);
        electricEelStaminaRegen = b
                .comment("电鳗（electric_eel） · 不挣扎时回气（/s） —— 不挣扎时每秒回多少体力（会被渔轮阻尼压制）。越大越难遛翻")
                .defineInRange("electricEelStaminaRegen", 0.02, 0, 1);
        // ==== END 鱼种数值定义 ====
        b.pop();

        // ---------------------------------------------------------- 玩法数值
        b.comment("""
                玩法数值 / Gameplay constants（决胜手感的那些系数）。
                鱼竿五种材质、张力模型、档位曲面、抛投与窝料 —— 这些原本写死在代码里，
                现在都能调。默认值与源码原值逐位相同，不改就与以前完全一样。
                改完 /atl config reload 即生效（正在遛的鱼不受影响）。
                """).push("gameplay");
        // ==== BEGIN 玩法数值定义 ====
        rodWoodMaxWeightKg = b
                .comment("木竿 · 承重上限（kg） —— 这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**")
                .defineInRange("rodWoodMaxWeightKg", 8.0, 0, 1000);
        rodWoodHookWindowBonus = b
                .comment("木竿 · 提竿窗口加成（秒） —— 加到基础提竿窗口上的秒数")
                .defineInRange("rodWoodHookWindowBonus", 0.0, 0, 5);
        rodWoodTensionCeiling = b
                .comment("木竿 · 张力上限倍率 —— 与鱼线按权重合成，决定\"线绷到多少会断\"")
                .defineInRange("rodWoodTensionCeiling", 1.0, 0, 10);
        rodWoodTensionRecover = b
                .comment("木竿 · 张力回复倍率 —— 与鱼线合成，决定放线时张力落多快")
                .defineInRange("rodWoodTensionRecover", 1.0, 0, 10);
        rodWoodStaminaDrain = b
                .comment("木竿 · 体力削减倍率 —— 与渔轮合成，决定收线时鱼掉体力多快")
                .defineInRange("rodWoodStaminaDrain", 1.0, 0, 10);
        rodWoodMaterialFactor = b
                .comment("木竿 · 耐久损耗系数 —— 越小越耐用（扣耐久时乘它）")
                .defineInRange("rodWoodMaterialFactor", 1.0, 0, 5);
        rodWoodTier = b
                .comment("木竿 · 品质档位上限（1~6） —— 这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出")
                .defineInRange("rodWoodTier", 3, 1, 6);
        rodIronMaxWeightKg = b
                .comment("铁竿 · 承重上限（kg） —— 这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**")
                .defineInRange("rodIronMaxWeightKg", 30.0, 0, 1000);
        rodIronHookWindowBonus = b
                .comment("铁竿 · 提竿窗口加成（秒） —— 加到基础提竿窗口上的秒数")
                .defineInRange("rodIronHookWindowBonus", 0.1, 0, 5);
        rodIronTensionCeiling = b
                .comment("铁竿 · 张力上限倍率 —— 与鱼线按权重合成，决定\"线绷到多少会断\"")
                .defineInRange("rodIronTensionCeiling", 1.2, 0, 10);
        rodIronTensionRecover = b
                .comment("铁竿 · 张力回复倍率 —— 与鱼线合成，决定放线时张力落多快")
                .defineInRange("rodIronTensionRecover", 1.2, 0, 10);
        rodIronStaminaDrain = b
                .comment("铁竿 · 体力削减倍率 —— 与渔轮合成，决定收线时鱼掉体力多快")
                .defineInRange("rodIronStaminaDrain", 1.2, 0, 10);
        rodIronMaterialFactor = b
                .comment("铁竿 · 耐久损耗系数 —— 越小越耐用（扣耐久时乘它）")
                .defineInRange("rodIronMaterialFactor", 0.85, 0, 5);
        rodIronTier = b
                .comment("铁竿 · 品质档位上限（1~6） —— 这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出")
                .defineInRange("rodIronTier", 4, 1, 6);
        rodGoldMaxWeightKg = b
                .comment("金竿 · 承重上限（kg） —— 这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**")
                .defineInRange("rodGoldMaxWeightKg", 21.0, 0, 1000);
        rodGoldHookWindowBonus = b
                .comment("金竿 · 提竿窗口加成（秒） —— 加到基础提竿窗口上的秒数")
                .defineInRange("rodGoldHookWindowBonus", 0.25, 0, 5);
        rodGoldTensionCeiling = b
                .comment("金竿 · 张力上限倍率 —— 与鱼线按权重合成，决定\"线绷到多少会断\"")
                .defineInRange("rodGoldTensionCeiling", 1.1, 0, 10);
        rodGoldTensionRecover = b
                .comment("金竿 · 张力回复倍率 —— 与鱼线合成，决定放线时张力落多快")
                .defineInRange("rodGoldTensionRecover", 1.15, 0, 10);
        rodGoldStaminaDrain = b
                .comment("金竿 · 体力削减倍率 —— 与渔轮合成，决定收线时鱼掉体力多快")
                .defineInRange("rodGoldStaminaDrain", 1.15, 0, 10);
        rodGoldMaterialFactor = b
                .comment("金竿 · 耐久损耗系数 —— 越小越耐用（扣耐久时乘它）")
                .defineInRange("rodGoldMaterialFactor", 0.9, 0, 5);
        rodGoldTier = b
                .comment("金竿 · 品质档位上限（1~6） —— 这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出")
                .defineInRange("rodGoldTier", 6, 1, 6);
        rodDiamondMaxWeightKg = b
                .comment("钻石竿 · 承重上限（kg） —— 这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**")
                .defineInRange("rodDiamondMaxWeightKg", 50.0, 0, 1000);
        rodDiamondHookWindowBonus = b
                .comment("钻石竿 · 提竿窗口加成（秒） —— 加到基础提竿窗口上的秒数")
                .defineInRange("rodDiamondHookWindowBonus", 0.2, 0, 5);
        rodDiamondTensionCeiling = b
                .comment("钻石竿 · 张力上限倍率 —— 与鱼线按权重合成，决定\"线绷到多少会断\"")
                .defineInRange("rodDiamondTensionCeiling", 1.6, 0, 10);
        rodDiamondTensionRecover = b
                .comment("钻石竿 · 张力回复倍率 —— 与鱼线合成，决定放线时张力落多快")
                .defineInRange("rodDiamondTensionRecover", 1.7, 0, 10);
        rodDiamondStaminaDrain = b
                .comment("钻石竿 · 体力削减倍率 —— 与渔轮合成，决定收线时鱼掉体力多快")
                .defineInRange("rodDiamondStaminaDrain", 1.6, 0, 10);
        rodDiamondMaterialFactor = b
                .comment("钻石竿 · 耐久损耗系数 —— 越小越耐用（扣耐久时乘它）")
                .defineInRange("rodDiamondMaterialFactor", 0.7, 0, 5);
        rodDiamondTier = b
                .comment("钻石竿 · 品质档位上限（1~6） —— 这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出")
                .defineInRange("rodDiamondTier", 4, 1, 6);
        rodNetheriteMaxWeightKg = b
                .comment("下界合金竿 · 承重上限（kg） —— 这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**")
                .defineInRange("rodNetheriteMaxWeightKg", 999.0, 0, 1000);
        rodNetheriteHookWindowBonus = b
                .comment("下界合金竿 · 提竿窗口加成（秒） —— 加到基础提竿窗口上的秒数")
                .defineInRange("rodNetheriteHookWindowBonus", 0.3, 0, 5);
        rodNetheriteTensionCeiling = b
                .comment("下界合金竿 · 张力上限倍率 —— 与鱼线按权重合成，决定\"线绷到多少会断\"")
                .defineInRange("rodNetheriteTensionCeiling", 1.7, 0, 10);
        rodNetheriteTensionRecover = b
                .comment("下界合金竿 · 张力回复倍率 —— 与鱼线合成，决定放线时张力落多快")
                .defineInRange("rodNetheriteTensionRecover", 1.9, 0, 10);
        rodNetheriteStaminaDrain = b
                .comment("下界合金竿 · 体力削减倍率 —— 与渔轮合成，决定收线时鱼掉体力多快")
                .defineInRange("rodNetheriteStaminaDrain", 1.8, 0, 10);
        rodNetheriteMaterialFactor = b
                .comment("下界合金竿 · 耐久损耗系数 —— 越小越耐用（扣耐久时乘它）")
                .defineInRange("rodNetheriteMaterialFactor", 0.5, 0, 5);
        rodNetheriteTier = b
                .comment("下界合金竿 · 品质档位上限（1~6） —— 这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出")
                .defineInRange("rodNetheriteTier", 5, 1, 6);
        tensionBaseCeiling = b
                .comment("张力上限基准（点） —— 所有张力上限都按它缩放。改大 = 整体变宽容（线更不容易断）")
                .defineInRange("tensionBaseCeiling", 100.0, 10, 1000);
        pullSprint = b
                .comment("冲刺时的拉拽基数 —— 鱼冲刺时用来算张力的基值（不冲刺时用「配件属性合成」里的 pullIdleBase）")
                .defineInRange("pullSprint", 0.95, 0, 5);
        counterBonus = b
                .comment("反向控杆奖励 —— A/D 压住鱼挣扎方向时的张力倍率修正。负值 = 张力反而降")
                .defineInRange("counterBonus", -0.5, -2, 1);
        counterPenalty = b
                .comment("同向控杆惩罚 —— 顺着鱼的方向控杆时的张力倍率修正（正值 = 张力涨更凶）")
                .defineInRange("counterPenalty", 0.35, 0, 3);
        minReelGainRatio = b
                .comment("收线涨势保底比例（0~1） —— 收线时鱼拉最多把涨势削到这个比例。0 = 鱼够猛就能把张力压住不涨")
                .defineInRange("minReelGainRatio", 0.25, 0, 1);
        maxFishSlowdown = b
                .comment("鱼拖慢收线的上限（0~1） —— 鱼最多能把收线速度拖慢多少。1 = 完全拖停")
                .defineInRange("maxFishSlowdown", 0.9, 0, 1);
        dampingToCapRatio = b
                .comment("阻尼折算上限的比例 —— 渔轮阻尼中\"能折算进张力上限\"的那部分比例")
                .defineInRange("dampingToCapRatio", 0.5, 0, 1);
        capDampingBonus = b
                .comment("阻尼对上限的加成 —— 上一条折算后乘的系数，直接加到张力上限上")
                .defineInRange("capDampingBonus", 1.2, 0, 5);
        totalDrainTarget = b
                .comment("拉干阈值（0~1） —— 累计扣掉鱼多少体力就能\"拉干强制上鱼\"（1.0 = 扣满一轮体力）")
                .defineInRange("totalDrainTarget", 0.9, 0.1, 3);
        nearShoreBase = b
                .comment("近岸基础距离（格） —— 中鱼后线至少被拽到这么远（防止一上钩就贴脸）")
                .defineInRange("nearShoreBase", 6.0, 1, 40);
        staminaEpsilonFloor = b
                .comment("体力判定下限 —— \"体力是否已耗尽\"的绝对容差，避免浮点残值反复触发")
                .defineInRange("staminaEpsilonFloor", 0.05, 0, 50);
        staminaEpsilonRatio = b
                .comment("体力判定比例（0~1） —— 同上，按体力上限的比例取容差（取两者较大值）")
                .defineInRange("staminaEpsilonRatio", 0.005, 0, 0.5);
        inputStaleLimit = b
                .comment("输入超时上限（tick） —— 客户端输入包超过这么久没更新就当作\"没在操作\"")
                .defineInRange("inputStaleLimit", 5, 1, 100);
        tierSpeedBase = b
                .comment("档位游速底数 —— 每升一档游速乘它。六档下来是 底数^5 —— 1.12 约 1.76 倍")
                .defineInRange("tierSpeedBase", 1.12, 1, 3);
        tierStaminaResistBase = b
                .comment("档位体力抗性底数 —— 每升一档体力抗性乘它（抗性直接除在扣体力公式里）")
                .defineInRange("tierStaminaResistBase", 1.18, 1, 3);
        tierGoldSpeedScale = b
                .comment("金档 · 游速手感倍率 —— 金档(4)鱼游速在底数曲线之上再乘它。0.84 = 降 16%（用户定案）")
                .defineInRange("tierGoldSpeedScale", 0.84, 0, 3);
        tierGoldStaminaScale = b
                .comment("金档 · 体力池倍率 —— 金档(4)鱼体力池上限乘它。用户定案是 1.0 —— **金档体力不变**")
                .defineInRange("tierGoldStaminaScale", 1.0, 0, 3);
        tierRedSpeedScale = b
                .comment("红档 · 游速手感倍率 —— 红档(5)鱼游速在底数曲线之上再乘它。1.05 = 加 5%（用户定案）")
                .defineInRange("tierRedSpeedScale", 1.05, 0, 3);
        tierRedStaminaScale = b
                .comment("红档 · 体力池倍率 —— 红档(5)鱼体力池上限乘它。1.5 = 加 50%（用户定案）⚠ 长体力鱼会更磨")
                .defineInRange("tierRedStaminaScale", 1.5, 0, 5);
        tierRedStaminaMin = b
                .comment("红档 · 体力基准下限 —— 红档鱼的**基准体力**至少这么多（咬钩时先抬到下限，**再**乘上面的红档倍率，所以默认下限 200 的红档鱼实际是 300）。0 = 关掉下限")
                .defineInRange("tierRedStaminaMin", 200.0, 0, 5000);
        fatiguePullMultiplier = b
                .comment("疲劳期拉拽倍率 —— 遛到疲劳后拉拽速度乘它。**每条鱼的疲劳期拉力默认 = 正常拉力 × 本值**（由 tools/fish-source.js 推导，改它要重跑生成链）。⚠ 大于 1 = 疲劳期反而更难拉，别调错方向")
                .defineInRange("fatiguePullMultiplier", 0.42, 0, 2);
        reelFloorDrainBase = b
                .comment("保底体力削减（下限）（点/秒） —— **装了渔轮**的路亚，拉鱼时每秒**至少**削减这么多鱼力（与鱼多高檔无关）。0 = 关掉这条保底（回到\"扣力全看单轮上限\"）")
                .defineInRange("reelFloorDrainBase", 5.0, 0, 100);
        reelFloorDrainPerDamping = b
                .comment("保底体力削减 · 每点阻尼转化（点/秒） —— 压制阻尼每高出 1.0 一点，保底削减 +这么多点/秒。默认 **10** 是**为金轮倒推**的：大型纺车轮阻尼 2.50 → 5 + 1.5×10 = **20.0**，正好贴住上限")
                .defineInRange("reelFloorDrainPerDamping", 10.0, 0, 100);
        reelFloorDrainCap = b
                .comment("保底体力削减（上限）（点/秒） —— 保底削减的硬上限（用户定案 20）。它**不受单轮扣力上限限制** —— 单轮额度用完的那几轮也照扣，所以它是\"磨鱼到底\"的那条底线")
                .defineInRange("reelFloorDrainCap", 20.0, 0, 200);
        floorStruggleBase = b
                .comment("保底挣扎（白档秒数）（秒） —— 每轮挣扎开头，鱼**至少**有这么多秒是\"没力竭\"的（白档基准）。1 = 一秒")
                .defineInRange("floorStruggleBase", 1.0, 0, 60);
        floorStrugglePerTier = b
                .comment("保底挣扎 · 每档增加（秒） —— 每高一档多保底这么多秒 —— 白 1、红 6（用户定案）")
                .defineInRange("floorStrugglePerTier", 1.0, 0, 60);
        floorStruggleBonusSteady = b
                .comment("保底挣扎 · 均衡型加成（秒） —— 均衡型的策略浮动（0~1，**只上抬不下压**）")
                .defineInRange("floorStruggleBonusSteady", 0.0, 0, 1);
        floorStruggleBonusSluggish = b
                .comment("保底挣扎 · 黏液型加成（秒） —— 黏液型的策略浮动（0~1，只上抬不下压）")
                .defineInRange("floorStruggleBonusSluggish", 0.0, 0, 1);
        floorStruggleBonusBurst = b
                .comment("保底挣扎 · 爆发型加成（秒） —— 爆发型的策略浮动 —— 默认给满 +1（爆发型是最难缠的那一类）")
                .defineInRange("floorStruggleBonusBurst", 1.0, 0, 1);
        floorStruggleBonusErratic = b
                .comment("保底挣扎 · 神经质加成（秒） —— 神经质型的策略浮动（默认 +0.5）")
                .defineInRange("floorStruggleBonusErratic", 0.5, 0, 1);
        reelResistThreshold = b
                .comment("收线抵抗 · 起效阈值 —— 双拉属性（渔轮的回拉速度）低于它就不受抵抗 —— 慢配置本来就不占便宜")
                .defineInRange("reelResistThreshold", 1.2, 0, 10);
        reelResistTierWhite = b
                .comment("收线抵抗 · 白档（比例） —— 白档鱼对高收线速率的抵抗（回拉速率降低这么多）")
                .defineInRange("reelResistTierWhite", 0.05, 0, 1);
        reelResistTierGreen = b
                .comment("收线抵抗 · 绿档（比例） —— 绿档鱼的抵抗")
                .defineInRange("reelResistTierGreen", 0.125, 0, 1);
        reelResistTierBlue = b
                .comment("收线抵抗 · 蓝档（比例） —— 蓝档鱼的抵抗")
                .defineInRange("reelResistTierBlue", 0.185, 0, 1);
        reelResistTierPurple = b
                .comment("收线抵抗 · 紫档（比例） —— 紫档鱼的抵抗")
                .defineInRange("reelResistTierPurple", 0.275, 0, 1);
        reelResistTierGold = b
                .comment("收线抵抗 · 金档（比例） —— 金档鱼的抵抗")
                .defineInRange("reelResistTierGold", 0.55, 0, 1);
        reelResistTierRed = b
                .comment("收线抵抗 · 红档（比例） —— 红档鱼的抵抗（用户定案 32%）")
                .defineInRange("reelResistTierRed", 0.75, 0, 1);
        reelResistStratSteady = b
                .comment("收线抵抗 · 均衡型浮动 —— 策略浮动（**上下至多 5%**）：正值 = 更抗拉")
                .defineInRange("reelResistStratSteady", 0.0, -0.05, 0.05);
        reelResistStratSluggish = b
                .comment("收线抵抗 · 黏液型浮动 —— 黏液型游得慢，抵抗也给到下限 −5%")
                .defineInRange("reelResistStratSluggish", -0.05, -0.05, 0.05);
        reelResistStratBurst = b
                .comment("收线抵抗 · 爆发型浮动 —— 爆发型最抗拉，给到上限 +5%")
                .defineInRange("reelResistStratBurst", 0.05, -0.05, 0.05);
        reelResistStratErratic = b
                .comment("收线抵抗 · 神经质浮动 —— 神经质型的策略浮动（默认 0）")
                .defineInRange("reelResistStratErratic", 0.0, -0.05, 0.05);
        reelResistDampingOffset = b
                .comment("收线抵抗 · 每点阻尼抵消（比例） —— 压制阻尼每高出 1.0 一点，抵消这么多抵抗。默认 **0.5** 是**为金轮倒推**的：大型纺车轮阻尼 2.50 → (2.50−1)×0.5 = **0.75**，正好贴住抵消上限")
                .defineInRange("reelResistDampingOffset", 0.5, 0, 2);
        reelResistDampingCap = b
                .comment("收线抵抗 · 抵消上限（比例） —— 压制阻尼最多抵消掉抵抗的这么多（用户定案 75%）—— 再好的轮子也留 1/4")
                .defineInRange("reelResistDampingCap", 0.75, 0, 1);
        fatiguedReelMinGain = b
                .comment("力竭期收线保底（格/tick） —— 鱼力竭时，收线的净回收速度**至少**这么多格/tick（0.05 ≈ 1 格/秒）。0 = 关掉保底（装备差就可能力竭了也拉不动）")
                .defineInRange("fatiguedReelMinGain", 0.05, 0, 1);
        fatiguedTensionGainScale = b
                .comment("力竭期 · 变红速度倍率 —— 鱼力竭时，收线的张力涨速**再乘**它（在控杆关系 ×0.8 之上）。0.7 = **变红速度降 30%**（越小越难把线拉红）。⚠ 方向：<1 才是\"更安全\"，填 >1 等于力竭反而更危险")
                .defineInRange("fatiguedTensionGainScale", 0.7, 0, 3);
        fatiguedTensionRecoverScale = b
                .comment("力竭期 · 回白速度倍率 —— 鱼力竭时，中立/放线的张力回落**再乘**它。1.35 = **回白速度加 35%**（越大越快退回白区）。⚠ 方向：>1 才是\"更安全\"")
                .defineInRange("fatiguedTensionRecoverScale", 1.35, 0, 5);
        fatigueThresholdScale = b
                .comment("力竭阈值倍率（全局） —— 所有鱼\"进疲劳的体力比例\"**再乘**它。1.0 = 原样；1.25 = 鱼还剩 42.5%（burst 0.34 时）就进力竭、更容易拉开差距。⚠ 结果夹在 1.0：>1 不会变成\"一咬钩就力竭\"")
                .defineInRange("fatigueThresholdScale", 1.0, 0.1, 2);
        idleFallFloor = b
                .comment("中立 · 最小净回落（点/tick） —— **中立**（不按任何键）时，张力每 tick 至少回落这么多 —— 鱼再猛也不能把它拖得比这更慢。0.3 ≈ 6 点/秒（0.50 条子约 8 秒从红退回白）。⚠ 放线不受它影响（那一档本来就快）；0 = 关掉，恢复\"干站着被慢慢拖进黄区\"的压力")
                .defineInRange("idleFallFloor", 0.3, 0, 5);
        struggleSecondsBase = b
                .comment("挣扎秒数（白档）（秒） —— 白档鱼每轮挣扎持续多久（每高一档再加下面那个增量）")
                .defineInRange("struggleSecondsBase", 2.0, 0, 60);
        struggleSecondsPerTier = b
                .comment("挣扎秒数每档增量（秒） —— 每升一档多挣扎这么久")
                .defineInRange("struggleSecondsPerTier", 1.6, 0, 30);
        restSecondsBase = b
                .comment("休息秒数（白档）（秒） —— 白档鱼每轮休息多久")
                .defineInRange("restSecondsBase", 5.0, 0, 60);
        restSecondsPerTier = b
                .comment("休息秒数每档递减（秒） —— 每升一档少休息这么久 —— 高级鱼\"忙得多、歇得少\"")
                .defineInRange("restSecondsPerTier", 0.4, 0, 30);
        alpineLakeMinY = b
                .comment("高寒湖 · 最低高度（格） —— 湖泊的**水面高度**达到它就算高寒湖（高山湖的本质是\"高度 + 冷\"）。120 是雪线附近 —— 单看群系会漏掉\"雪线以上的普通山地湖\"。⚠ 群系在 #anglertackle:alpine 里的湖**不看高度**，一律算高寒湖")
                .defineInRange("alpineLakeMinY", 120, -64, 320);
        coldWaterMaxTemp = b
                .comment("冷水溪流 · 最高温度（温度） —— 河流所在群系的**基础温度**不超过它就算冷水溪流（哲罗鲑、江鳕住那儿）。⚠ 默认 0.3 而不是 0.2：针叶林 taiga 是 0.25、平原 plains 是 0.8 —— 取 0.2 会把**针叶林溪流**排除掉，而那正是哲罗鲑现实里的栖息地。⚠ 群系在 #anglertackle:cold_streams 里的河**不看温度**，一律算冷水溪流")
                .defineInRange("coldWaterMaxTemp", 0.3, -1, 2);
        brokenIceToPackIceMinutes = b
                .comment("冰钓 · 碎冰变浮冰（分钟） —— 碎冰闲置这么久之后变成浮冰（原版 packed_ice）。0 = 立刻变")
                .defineInRange("brokenIceToPackIceMinutes", 5, 0, 120);
        packIceToIceMinutes = b
                .comment("冰钓 · 浮冰变回冰（分钟） —— 浮冰闲置这么久之后变回原版冰（用户定案：**会**变回冰，于是还能再凿）")
                .defineInRange("packIceToIceMinutes", 10, 0, 240);
        icePickDurabilityCost = b
                .comment("冰钓 · 凿一次扣耐久（点） —— 凿冰面 / 砸冰洞各扣多少耐久。凿冰镐耐久 250，所以默认能凿 250 次")
                .defineInRange("icePickDurabilityCost", 1, 0, 100);
        iceHoleLineWearMultiplier = b
                .comment("冰钓 · 洞内鱼线磨损（倍率） —— 洞内钓鱼时**鱼线**的磨损倍率（2.0 = 寿命减半）。⚠ **只乘鱼线、不乘竿**（用户定案）—— \"有条件的玩家可以自己挖开一大片水面来避免这种损耗\"")
                .defineInRange("iceHoleLineWearMultiplier", 2.0, 0.1, 10);
        iceHoleHighValueBonus = b
                .comment("冰钓 · 洞内金红爆率（百分数） —— 洞内抛竿对\"金+红鱼种目标占比\"的**加算**量（0.01 = +1 个百分点）")
                .defineInRange("iceHoleHighValueBonus", 0.01, 0, 0.2);
        openIceBiteTimeMultiplier = b
                .comment("冰钓 · 冰上咬钩时间（倍率） —— **覆冰群系 · 没凿洞**时咬钩等待时间乘它（1.5 = 延长 50%）。⚠ 只对\"非高山且常年覆冰\"的群系生效，其他群系一律 1.0")
                .defineInRange("openIceBiteTimeMultiplier", 1.5, 0.1, 5);
        openIceHighValuePenalty = b
                .comment("冰钓 · 冰上金红爆率（百分数） —— 覆冰群系**没凿洞**时对金红总占比的**加算**扣减（0.01 = −1 个百分点）")
                .defineInRange("openIceHighValuePenalty", 0.01, 0, 0.2);
        fullChargeTicks = b
                .comment("蓄力满所需 tick（tick） —— 按住右键到蓄力满要多久（20 tick = 1 秒）")
                .defineInRange("fullChargeTicks", 32, 1, 400);
        minChargeTicks = b
                .comment("最短蓄力 tick（tick） —— 低于这个 tick 数不算抛出（防误触）")
                .defineInRange("minChargeTicks", 3, 1, 100);
        chumMinAirTicks = b
                .comment("窝料最小滞空（tick） —— 窝料出手后至少飞这么久才允许落水（防止贴脸扔）")
                .defineInRange("chumMinAirTicks", 10, 0, 200);
        oversizeFishAction = b
                .comment("超重鱼的处理方式（0/1） —— 0 = 个体重量**限幅到竿的承重**（钓得上来，但上限就是承重）；1 = 该个体直接**切线跑掉**（只掉耐久不给鱼）。⚠ 改这一项会明显改变难度")
                .defineInRange("oversizeFishAction", 0, 0, 1);
        compassSearchRadius = b
                .comment("追踪罗盘搜索半径（格） —— 罗盘指针往外找多远的渔人小屋（格）。⚠ 这个值**不是**随便给的：小屋的 placement spacing 是 34 区块（=544 格），random_spread 会让每个 34×34 网格里随机落一个候选点，玩家到最近候选点最坏约 spacing×√2/2 ≈ 385 格；再叠加群系校验失败（不是每个候选点都落在河流/沼泽/沙滩上）要跳到更远的网格 —— 所以 400 会**经常找不到**，1200 才覆盖得住两格的最坏情况。⚠ 搜索是**一圈一圈往外扩**的（原版 /locate 也是这套），找到就停，所以平时开销取决于最近的屋子有多远、与这个上限无关；只有\"附近真的没有\"时才会走满。")
                .defineInRange("compassSearchRadius", 1200, 32, 4000);
        bigCatchAnnounceValue = b
                .comment("大鱼公告价值门槛（鱼币） —— 钓到的鱼**价值 ≥ 这个数**时，向全服公告一条（0 = 每条都公告，会很吵）。价值口径 = `CoinPricing.priceFor(档位, 尺寸系数)`，与**卖价同一个函数** —— 公告里写的钱数必须与玩家卖出去拿到的一样，两处各算一份就会打架 ✗。⚠ 默认 **5050 是用户 2026-10-11 亲口给的数**，不是随手取的：红档基准价 4800、尺寸浮动 ±8% ⇒ 红鱼的价格区间是 [4416, 5184]，5050 正好落在**红档里体型偏大**的那一段（尺寸系数 ≥ 0.65，约红鱼的上游 17%）；而金档最高只有 1380 × 1.08 = 1490 ⇒ **金档及以下永远够不到**。⇒ 这条门的语义就是\"**一条特别大的红鱼**\"，改小它等于把公告变成\"谁都能上电视\"。")
                .defineInRange("bigCatchAnnounceValue", 5050, 0, 100000);
        bigCatchAnnounceMinIntervalSec = b
                .comment("大鱼公告最小间隔（秒） —— 两条大鱼公告之间至少隔这么久（0 = 不节流）。⚠ 防的是\"一群人同时上鱼\"或\"一个人连竿\"把聊天栏刷爆 —— 公告是给别人看的调味品，刷屏就变成骚扰 ✗。")
                .defineInRange("bigCatchAnnounceMinIntervalSec", 10, 0, 600);
        // ==== END 玩法数值定义 ====
        b.pop();

        // ---------------------------------------------------------- 渲染
        b.comment("""
                渲染与实体同步 / Rendering
                注意：实体追踪范围（clientTrackingRange）是**注册期常量**，
                运行时改配置改不动它，所以这里不提供该项 —— 免得改了没反应。
                超出视距时的兜底靠"幽灵鱼线"，见 docs/09-原版冲突与渲染优化.md。""").push("render");
        ghostLineBeyondBlocks = b.comment("超过该距离改用简化线型（格）")
                .defineInRange("ghostLineBeyondBlocks", 64, 8, 256);
        hookCameraFollow = b.comment("""
                上钩后视角跟随浮漂 / Camera follows the bobber
                对抗期间把**渲染用**的相机朝向拉向浮漂。只改画面、不改玩家真实朝向
                （不碰 setYRot/setXRot），所以瞄准、交互、服务端判定全都不受影响。
                第一人称下生效；第三人称不动镜头（那是玩家自己在运镜）。""")
                .define("hookCameraFollow", true);
        hookCameraFollowStrength = b.comment("""
                跟随强度 0~1：1 = 完全对准浮漂，0.5 = 只拉一半（还能用鼠标偏一点）
                实际强度还会乘以一个约 0.25 秒的渐入，避免刚中鱼时镜头猛甩。""")
                .defineInRange("hookCameraFollowStrength", 1.0, 0.0, 1.0);
        reelZoomPercent = b.comment("""
                收线时视野缩到原来的百分之几 / Zoom while reeling
                ⚠ **相对当前 FOV 相乘**，不是写死一个数值 —— 所以它是叠在其它视野效果
                之上的"再来一点"，松手就回到原来的水平（用户口径："松开放线回到上鱼时的放大水平"）。
                100 = 不拉近。只在对抗中且左键按住时生效。""")
                .defineInRange("reelZoomPercent", 75.0, 70.0, 100.0);
        b.pop();
    }
}
