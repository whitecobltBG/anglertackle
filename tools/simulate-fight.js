/*
 * simulate-fight.js —— **逐 tick 复刻** FishingSession.tickFight 的对抗模拟。
 *
 * 目的（用户 2026-10-08 的要求）：
 *   "自己搭一个模拟，建立尽量真实的环境模拟钓鱼，比如游戏里大约能几轮钓上来
 *    在模拟也是一样的结果。方便你确认数值设计。"
 *
 * ── 它替换了谁 ───────────────────────────────────────────────────
 *   本文件以前是一个**粗糙模型**：鱼种/体力/拉力全是手抄的，档位按体重**等分**
 *   （而游戏里是按 tierSplit 权重非等分），随机数用 Math.random，
 *   单轮扣力上限写成固定 0.9/轮数，装备干脆不存在。它只能回答"节奏大概什么样"，
 *   回答不了"紫轮紫线遛一条红鱼要几回合"——而后者才是要确认的东西。
 *   （同类粗糙模型还有 tools/simulate-full-fight.js，现在同样被本文件取代。）
 *
 *   现在所有数值**现读源码/配置**，一个都不手抄：
 *     · 鱼种 13 个数值 + 档位表 …… tools/fish-source.js（仓库唯一解析口径）
 *     · 竿/线/轮/钩 配件表      …… ModItems.java 的 partProps 表
 *     · 竿身材质系数            …… Config.java 的 rod* 项
 *     · 全部玩法旋钮            …… tools/tuning.js（Config 默认值）
 *     · 随机数                  …… **Java 同款 LCG**（同种子 ⇒ 同序列）
 *     · 属性合成                …… PartComposer 的加权/钳制公式
 *
 * ── 为什么必须逐句照抄 ───────────────────────────────────────────
 *   一场对抗的手感由**好几层相乘**决定：档位游速 × 鱼线张力系数 × 控杆关系 ×
 *   力竭修正 × 单轮扣力上限 × 收线速率抵抗 × 鱼的状态（挣扎/休息/冲刺/保底挣扎）。
 *   任何一层抄错，结论就变成"另一个世界的"，而它照样会打印一张漂亮的表。
 *   所以本文件里凡是有算式的行，注释都标了 FishingSession.java 的**行号**：
 *   改 Java 就要回来改这里，两边不一致时以 Java 为准。
 *
 * ── 用法 ─────────────────────────────────────────────────────────
 *   node tools/simulate-fight.js                    # 默认：紫轮紫线 vs 全部金/红档鱼
 *   node tools/simulate-fight.js --check            # 只跑模拟器自检（断言）
 *   node tools/simulate-fight.js --all              # 47 种鱼 × 每个档位
 *   node tools/simulate-fight.js --fish arctic_char
 *   node tools/simulate-fight.js --gear gold --rod diamond
 *   node tools/simulate-fight.js --seeds 300 --policy average
 *   node tools/simulate-fight.js --trace arctic_char   # 打这条鱼的逐轮明细
 *   node tools/simulate-fight.js --gear blue --rod iron --tiers 1,2   # 看低档鱼
 *
 * ── "回合"是什么（用户的对账口径）───────────────────────────────
 *   鱼上钩后按「挣扎 → 休息 → 挣扎 …」循环，**一次挣扎算一回合**
 *   （{@code roundsDone}）。轮数上限由 {@code roundsForTier} 决定：
 *   红 5~7 / 金 4~5 / 其余固定 2 —— 轮次用尽后鱼再也不挣扎，
 *   于是"回合数"就是这场对抗的骨架，也就是玩家嘴里"拉扯了几回合"。
 *
 * ── 已验证（2026-10-08，紫轮紫线 + 下界合金竿 · 熟练玩家 · 抛 20 格）──
 *   用户口径原话："紫轮紫线与红鱼最少拉扯了 4 回合（镜鲤太拉了不算，
 *   此处仅距离大西洋鲑等）"。
 *
 *   ① 标定**前**（旧数值）：红档中位 3 回合；大西洋鲑/幼鲨/哲罗鲑/旗鱼/鳄雀鳝
 *      已经是 4 回合 ✓，但 格陵兰鲨/鲯鳅 只有 3；镜鲤 1、巨骨舌鱼 1、虎鱼 2、
 *      褐鳟 2、铂金银龙鱼 2、平鲉 2、北极红点鲑 2 —— 全靠"拉干"拖上来。
 *   ② 标定**后**（用户 2026-10-08 的方法论："回合数理应由张力与游速博弈共同
 *      设计，游不快的就改张力"）：把 10 条软鱼的 tensionGain 抬到
 *      1.75→4.00(巨骨舌鱼) / 1.50→3.10(虎鱼) / 1.60→2.65(褐鳟) /
 *      1.30→1.95(铂金银龙鱼) / 1.45→2.30(平鲉·北极红点鲑) / 1.45→1.90(虹鳟) /
 *      1.45→1.75(格陵兰鲨) / 1.20→1.55(鲯鳅)，镜鲤则先抬拉力
 *      1.10→**2.40**（用户指定"2.4 游速起步"）+ 张力 1.40→2.80。
 *      结果：**红档 17 行全部 ≥4 回合（中位 4，范围 3~5）**，耗时中位 33.5s→46.3s。
 *   ③ 副作用（都是可接受/符合预期的方向）：
 *      · 装备梯度变陡（红档上鱼率）：白 **0%** → 绿 **0%** → 蓝 37% → 紫 100% → 金 100%
 *        （白/绿是**100% 逃脱**：收线速度 < 鱼的拽线速度，线被拽完）
 *      · 操作水平终于有牙齿：完美/熟练/一般 都是 100%，"手生"从 50% 掉到 **17%**
 *   ④ 回合数**主要由距离博弈决定**（鱼拽线 vs 收线），体力/轮次只在鱼池子小时抢先：
 *      抛投距离（红档回合中位）：8 格 1 / 14 格 2 / 20 格 3→4 / 24 格 3→4
 */
'use strict';

const path = require('path');
const fs = require('fs');

const { cfg } = require('./tuning.js');
const { parseFish } = require('./fish-source.js');

const ROOT = path.join(__dirname, '..');
const TPS = 20;
const TIER_CN = ['白', '绿', '蓝', '紫', '金', '红'];

// ============================================================================
//  0. Java 随机数（java.util.Random / LegacyRandomSource 的 48 位 LCG）
//
//     为什么不用 Math.random：tickFight 里每一次 nextInt / nextBoolean /
//     nextDouble 都推进**同一条**序列（变向、假动作、轮数上限），**读取顺序**
//     也会影响结果。用同一种发生器、同样的读取顺序，模拟的随机结构才和游戏
//     是同一个；同种子还能完全复现，方便对着日志排查。
//
//     ⚠ 玩家的操作**绝不能**用这条序列：游戏里玩家按键不消耗鱼的随机数。
//       所以策略另开一条（seed ^ 0x9E3779B9），保证鱼的那条是纯净的。
// ============================================================================
const MASK48 = (1n << 48n) - 1n;
const LCG_MULT = 0x5DEECE66Dn;
const LCG_ADD = 0xBn;

class JavaRandom {
  constructor(seed) {
    this.seed = (BigInt(Math.trunc(seed)) ^ LCG_MULT) & MASK48;
  }
  next(bits) {
    this.seed = (this.seed * LCG_MULT + LCG_ADD) & MASK48;
    return Number(this.seed >> BigInt(48 - bits));
  }
  nextInt(bound) {
    if (bound <= 0) throw new Error('nextInt 的 bound 必须为正：' + bound);
    if ((bound & -bound) === bound) {
      return Number((BigInt(bound) * BigInt(this.next(31))) >> 31n);
    }
    let bits, val;
    do {
      bits = this.next(31);
      val = bits % bound;
    } while (bits - val + (bound - 1) < 0);
    return val;
  }
  nextBoolean() { return this.next(1) !== 0; }
  nextDouble() {
    return (this.next(26) * 67108864 + this.next(27)) / 9007199254740992;
  }
}

// ============================================================================
//  1. 数据源
// ============================================================================

/** 六槽配件表 —— 与 tools/dump-part-stats.js 读的是同一段注册代码。 */
function readParts() {
  const src = fs.readFileSync(path.join(
    ROOT, 'src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java'), 'utf8');
  const re = /(\w+)\s*=\s*ITEMS\.register\("([a-z_0-9]+)"[\s\S]*?partProps\(\s*(\d+)\s*,\s*Rarity\.(\w+)\s*,\s*PartStats\.(\w+)\(([^)]*)\)\s*(?:,\s*([\d.]+))?\s*\)/g;
  const out = {};
  let m;
  while ((m = re.exec(src))) {
    out[m[2]] = {
      id: m[2], kind: m[5], durability: Number(m[3]), rarity: m[4],
      args: m[6].split(',').map((s) => Number(Function('"use strict";return (' + s.trim() + ')')())),
      // 大型纺车轮的专属"单轮扣力上限"倍率（TacklePartItem.roundCapBonus）
      roundCapBonus: m[7] ? Number(m[7]) : 1.0,
    };
  }
  return out;
}

const ROD_MATERIALS = {
  wood:      { cn: '木',       key: 'rodWood',      tier: 1 },
  iron:      { cn: '铁',       key: 'rodIron',      tier: 2 },
  gold:      { cn: '金',       key: 'rodGold',      tier: 3 },
  diamond:   { cn: '钻石',     key: 'rodDiamond',   tier: 4 },
  netherite: { cn: '下界合金', key: 'rodNetherite', tier: 5 },
};

/** 竿身规格（Config.java 的 rod* 项；RodItem.RodSpec 读的就是这些）。 */
function readRod(material) {
  const meta = ROD_MATERIALS[material];
  if (!meta) throw new Error('未知竿身：' + material + '（可选 ' + Object.keys(ROD_MATERIALS).join('/') + '）');
  const g = (suffix) => {
    const key = meta.key + suffix;
    const v = cfg[key];
    if (typeof v !== 'number') throw new Error('读不到配置项 ' + key);
    return v;
  };
  return {
    material, cn: meta.cn, tier: meta.tier,
    tensionCeiling: g('TensionCeiling'),
    tensionRecover: g('TensionRecover'),
    staminaDrain: g('StaminaDrain'),
    materialFactor: g('MaterialFactor'),
    maxWeightKg: g('MaxWeightKg'),
    // hookWindowBonus 随鱼钩一起删除（2026-10-30），这里不再读。
  };
}

// ============================================================================
//  2. 属性合成（PartComposer.java 逐句复刻）
// ============================================================================

/** 权重：谁主导哪项属性。PartComposer 顶部注释写的是"线 60% / 竿 40%"。 */
function weights() {
  const pick = (k, fallback) => (typeof cfg[k] === 'number' ? cfg[k] : fallback);
  return {
    lineOnTension: pick('lineOnTension', 0.60),
    rodOnTension: pick('rodOnTension', 0.40),
    lineOnRecover: pick('lineOnRecover', 0.60),
    rodOnRecover: pick('rodOnRecover', 0.40),
    reelOnDrain: pick('reelOnDrain', 0.60),
    rodOnDrain: pick('rodOnDrain', 0.40),
  };
}

const BASE_TENSION_CEILING = 100.0;   // PartComposer.BASE_TENSION_CEILING

/** 合成一根竿的最终属性（RodStats.of 的等价物）。 */
function composeStats(rod, line, reel, hook) {
  const W = weights();
  const weighted = (part, w) => 1.0 + (part - 1.0) * w;
  const L = line.args, R = reel.args;
  const damping = Math.min(R[3], cfg.capDamping);
  return {
    // PartComposer.tensionCeiling / tensionRecover / tensionGen
    tensionCeiling: Math.min(
      BASE_TENSION_CEILING * weighted(L[0], W.lineOnTension) * weighted(rod.tensionCeiling, W.rodOnTension),
      BASE_TENSION_CEILING * cfg.capTensionCeiling),
    tensionRecover: Math.min(
      cfg.baseRecover * weighted(L[2], W.lineOnRecover) * weighted(rod.tensionRecover, W.rodOnRecover),
      cfg.baseRecover * cfg.capTensionRecover),
    tensionGen: L[1],
    // PartComposer.staminaDrain / pullIdle / pullStruggle / damping
    staminaDrain: Math.min(weighted(R[2], W.reelOnDrain) * weighted(rod.staminaDrain, W.rodOnDrain),
      cfg.capStaminaDrain),
    pullIdle: Math.min(R[0], cfg.capPull),
    pullStruggle: Math.min(R[1], cfg.capPull),
    damping,
    lineLength: 30.0 * L[3],                       // PartComposer.lineLength
    hookWindowBonus: 0.0,   // 鱼钩已删除（2026-10-30）：窗口只剩 hookWindowBase
    // PartComposer.floorDrainPerSec（保底体力削减，5~20 点/秒）
    floorDrainPerSec: Math.max(cfg.reelFloorDrainBase, Math.min(
      cfg.reelFloorDrainBase + Math.max(0.0, damping - 1.0) * cfg.reelFloorDrainPerDamping,
      cfg.reelFloorDrainCap)),
    roundCapBonus: reel.roundCapBonus || 1.0,
    // 白/黄区占张力上限的比例（FishingSession.whiteMax / yellowMax）
    whiteZoneRatio: cfg.whiteMax / 100.0,
    yellowZoneRatio: cfg.yellowMax / 100.0,
  };
}

// ============================================================================
//  3. 鱼种派生量（FishSpecies 的静态方法）
// ============================================================================
function tierSpeedOf(tier) {
  let v = Math.pow(cfg.tierSpeedBase, Math.max(0, Math.min(5, tier)));
  if (tier === 4) v *= cfg.tierGoldSpeedScale;
  if (tier === 5) v *= cfg.tierRedSpeedScale;
  return v;
}
const tierResistOf = (tier) => Math.pow(cfg.tierStaminaResistBase, Math.max(0, Math.min(5, tier)));
const tierStaminaScaleOf = (tier) =>
  (tier === 4 ? cfg.tierGoldStaminaScale : tier === 5 ? cfg.tierRedStaminaScale : 1.0);

const struggleSecondsOf = (t) => cfg.struggleSecondsBase + Math.max(0, Math.min(5, t)) * cfg.struggleSecondsPerTier;
const restSecondsOf = (t) => cfg.restSecondsBase - Math.max(0, Math.min(5, t)) * cfg.restSecondsPerTier;

/** FishSpecies.floorStruggleSeconds（保底挣扎：每轮开头至少这么多秒不力竭）。 */
function floorStruggleSecondsOf(tier, arch) {
  const base = cfg.floorStruggleBase + Math.max(0, Math.min(5, tier)) * cfg.floorStrugglePerTier;
  const bonus = {
    STEADY: cfg.floorStruggleBonusSteady, SLUGGISH: cfg.floorStruggleBonusSluggish,
    BURST: cfg.floorStruggleBonusBurst, ERRATIC: cfg.floorStruggleBonusErratic,
  }[arch] || 0;
  return base + Math.max(0.0, Math.min(1.0, bonus));
}

/** FishSpecies.reelResist（收线速率抵抗，按档位 × 策略 × 阻尼抵消）。 */
function reelResistOf(tier, arch, damping) {
  const base = [cfg.reelResistTierWhite, cfg.reelResistTierGreen, cfg.reelResistTierBlue,
    cfg.reelResistTierPurple, cfg.reelResistTierGold, cfg.reelResistTierRed][Math.max(0, Math.min(5, tier))];
  const strat = {
    STEADY: cfg.reelResistStratSteady, SLUGGISH: cfg.reelResistStratSluggish,
    BURST: cfg.reelResistStratBurst, ERRATIC: cfg.reelResistStratErratic,
  }[arch] || 0;
  const stratClamped = Math.max(-0.05, Math.min(0.05, strat));
  // PartComposer.reelResistOffset：阻尼抵消，最多 75%
  const offset = Math.min(cfg.reelResistDampingCap,
    Math.max(0.0, (damping - 1.0) * cfg.reelResistDampingOffset));
  return Math.max(0.0, base * (1.0 + stratClamped) * (1.0 - offset));
}

/** FishSpecies.resistReelSpeed：只打超过阈值 1.2 的那部分（保证单调）。 */
function resistReelSpeed(pull, resist) {
  const th = cfg.reelResistThreshold;
  if (pull <= th) return pull;
  return th + (pull - th) * Math.max(0.0, 1.0 - resist);
}

const tiersOf = (species) => species.tierList;

/**
 * 档位常量表：FishSpecies.java 里写的是 `private static final int[] G_R = {4, 5};`，
 * 鱼种定义里引用的是**常量名**（`new FishSpecies("...", ..., G_R, ...)`）——
 * 所以必须先把常量解析出来，否则档位会读成 NaN，整张表静默作废。
 */
function readTierConsts() {
  const src = fs.readFileSync(path.join(
    ROOT, 'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java'), 'utf8');
  const out = {};
  const re = /static final int\[\]\s+(\w+)\s*=\s*\{([^}]*)\}/g;
  let m;
  while ((m = re.exec(src))) {
    const list = m[2].split(',').map((x) => Number(x.trim())).filter((x) => Number.isFinite(x));
    if (list.length) out[m[1]] = list;
  }
  if (!Object.keys(out).length) throw new Error('从 FishSpecies.java 一个档位常量都没读到（正则失效？）');
  return out;
}

/** FishSpecies.tierSplit：金档收窄到 5、红档 2、其余 6（越小越稀有）。 */
const tierSplitOf = (tiers) => tiers.map((t) => (t === 5 ? 2 : t === 4 ? 5 : 6));

/** FishSpecies.tierForWeight：按 tierSplit 权重划分体重区间。 */
function tierForWeight(species, weightKg) {
  const tiers = tiersOf(species);
  const split = tierSplitOf(tiers);
  const total = split.reduce((a, b) => a + b, 0);
  const span = Math.max(1e-6, species.maxKg - species.minKg);
  const ratio = Math.max(0, Math.min(0.9999999, (weightKg - species.minKg) / span));
  const target = ratio * total;
  let cum = 0;
  for (let i = 0; i < tiers.length; i++) {
    cum += split[i];
    if (target < cum - 1e-9) return tiers[i];
  }
  return tiers[tiers.length - 1];
}

/** 取"正好落在目标档位中间"的代表体重：模拟"这条鱼的这个档位"。 */
function weightForTier(species, wantTier) {
  const tiers = tiersOf(species);
  const split = tierSplitOf(tiers);
  const total = split.reduce((a, b) => a + b, 0);
  let cum = 0;
  for (let i = 0; i < tiers.length; i++) {
    if (tiers[i] === wantTier) {
      const mid = (cum / total + (cum + split[i]) / total) / 2;
      return species.minKg + (species.maxKg - species.minKg) * mid;
    }
    cum += split[i];
  }
  return (species.minKg + species.maxKg) / 2;
}

// ============================================================================
//  4. 玩家策略
//
//     ★ 输入模型（**必须照抄游戏**，否则策略会做出玩家做不到的操作）：
//       ModNetwork.handleInput → `applyInput(player, reeling, !reeling, dir)`
//       也就是说：
//         · 按住左键 = 收线（reeling）
//         · 松开左键 = 放线（releasing = !reeling）
//         · 控杆方向 = A/D（counterDir ∈ {-1,0,+1}）
//       **玩家没有"中立"这一档** —— 中立只在输入掉包超时（inputStaleLimit）时出现。
//       所以张力控制在真人手里是**开关式**的：按 = 涨，松 = 落。
//       （我第一版策略把"鱼在歇 + 张力偏高"当成了中立，于是张力几乎不落、
//         在黄区以上待满 2.2 秒就断线 —— 模拟出来 73% 断线，而真人不会那样操作。）
//
//     反应延迟：HUD 上看到 → 手上做到 ≈ 150~250ms，所以策略读的是若干 tick
//     之前的快照，不是本 tick 的真值。
// ============================================================================
const POLICIES = {
  // reelBelow / releaseAbove 之间的死区 = "手停在原处"，避免每 tick 抖动
  perfect: { cn: '完美', lag: 0,  accuracy: 1.00, reelBelow: 0.62, releaseAbove: 0.62, againstBelow: 0.45 },
  good:    { cn: '熟练', lag: 4,  accuracy: 0.90, reelBelow: 0.56, releaseAbove: 0.72, againstBelow: 0.38 },
  average: { cn: '一般', lag: 8,  accuracy: 0.70, reelBelow: 0.50, releaseAbove: 0.76, againstBelow: 0.30 },
  sloppy:  { cn: '手生', lag: 12, accuracy: 0.50, reelBelow: 0.44, releaseAbove: 0.82, againstBelow: 0.24 },
  // 两档"风格"对照：用来量"回合数对打法有多敏感"（同一套数值，只改敢不敢拉）
  timid:   { cn: '保守', lag: 4,  accuracy: 0.85, reelBelow: 0.40, releaseAbove: 0.62, againstBelow: 0.20 },
  greedy:  { cn: '贪拉', lag: 4,  accuracy: 0.90, reelBelow: 0.72, releaseAbove: 0.80, againstBelow: 0.55 },
};

// ============================================================================
//  5. 对抗主循环 —— FishingSession.tickFight 的逐句复刻
//     （行号 = src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java）
// ============================================================================
function fight(o) {
  const { species, tier, weightKg, gear, policy, seed, castDistance } = o;
  const V = species.values;
  const rnd = new JavaRandom(seed);                    // 鱼的那条序列（纯净）
  const playerRnd = new JavaRandom((seed ^ 0x9E3779B9) >>> 0);   // 玩家读数用

  // ---------- onHooked()（2003~2159）：定下这一场的全部初值 ----------
  const maxRounds = tier >= 5 ? 5 + rnd.nextInt(3) : tier === 4 ? 4 + rnd.nextInt(2) : 2;  // 436
  const baseStamina = tier === 5 ? Math.max(V.stamina, cfg.tierRedStaminaMin) : V.stamina;  // 804
  const fishStaminaMax = baseStamina * tierStaminaScaleOf(tier);                            // 2043
  let fishStamina = fishStaminaMax;
  const tensionCeiling = gear.tensionCeiling;                                               // 1049
  const whiteMax = tensionCeiling * gear.whiteZoneRatio;                                    // 1055
  const yellowMax = tensionCeiling * gear.yellowZoneRatio;                                  // 1060
  const redZoneLimit = Math.round(Math.max(0.6, Math.min(5.0, 2.2)) * TPS);                 // 2187 = 44
  let tension = 20.0;                                                                       // 2059
  let distance = Math.max(cfg.nearShoreBase + 2.0, castDistance);                            // 2062
  const maxDistance = Math.max(distance + 5.0, gear.lineLength);                             // 2067
  const tierSpeed = tierSpeedOf(tier);                                                      // 2110
  const tierStaminaResist = tierResistOf(tier);                                             // 2111
  const fatigueThreshold = Math.min(1.0, V.fatigueThreshold * cfg.fatigueThresholdScale);    // 247
  const profile = V;

  let struggleDir = 0;                       // -1 左 / 0 直 / +1 右
  let struggleSwitchTicks = Math.round(V.turnIntervalSec * TPS);                             // 2068
  let sprinting = false;
  let sprintTicks = 0;
  let sprintCooldownTicks = Math.round(V.sprintIntervalSec * TPS);                           // 2069
  let fatigued = false, fatigueTicks = 0, freshStruggleTicks = 0;
  let fishStruggling = false, struggleTicks = 1;                                             // 2114/2120
  let roundDrain = 0, roundsDone = 0, roundsExhausted = false;                               // 2115~2118
  let redZoneTicks = 0;                                                                      // 2060
  let holding = false;                       // 左键是否按住（true = 收线）
  let reeling = false, releasing = true, counterDir = 0;

  const obs = [];                            // HUD 观测环（反应延迟用）
  const hist = {
    rounds: 0, ticks: 0, route: null, reason: null, maxRounds,
    tensionMax: tension, distanceMin: distance, roundLog: [],
    reelTicks: 0, releaseTicks: 0, capHits: 0, capMisses: 0,
  };
  const MAX_TICKS = TPS * 900;               // 15 分钟硬上限（正常一场 1~3 分钟）

  for (let tick = 1; tick <= MAX_TICKS; tick++) {
    // ---------- 玩家操作（读 lag 个 tick 之前的 HUD 快照）----------
    const snap = (policy.lag > 0 && obs.length > policy.lag)
      ? obs[obs.length - 1 - policy.lag]
      : (obs.length ? obs[obs.length - 1] : { tension, struggling: fishStruggling, dir: struggleDir });
    const ratio = snap.tension / tensionCeiling;
    // 开关式张力控制（= 游戏的输入映射）：张力高了就松手，低了就按住，中间保持
    if (ratio > policy.releaseAbove) holding = false;
    else if (ratio < policy.reelBelow) holding = true;
    reeling = holding;
    releasing = !holding;          // ★ ModNetwork.handleInput: releasing = !reeling
    if (snap.dir === 0) {
      counterDir = 0;
    } else if (playerRnd.nextDouble() < policy.accuracy) {
      // 张力低 → 反拉（磨得快、涨得凶）；张力高 → 正拉（省力、安全）
      counterDir = ratio < policy.againstBelow ? -snap.dir : ratio > 0.50 ? snap.dir : 0;
    } else {
      counterDir = 0;                        // 方向读错 = 干脆不控杆
    }
    hist.reelTicks += reeling ? 1 : 0;
    hist.releaseTicks += releasing ? 1 : 0;

    // ================= 1a. 左右变向（2304）=================
    if (--struggleSwitchTicks <= 0) {
      if (profile.archetype === 'ERRATIC' || profile.archetype === 'BURST') {
        struggleDir = rnd.nextBoolean() ? -1 : 1;
      } else {
        const r = rnd.nextInt(3);
        struggleDir = r === 0 ? -1 : r === 1 ? 1 : 0;
      }
      const base = Math.round(profile.turnIntervalSec * TPS);
      struggleSwitchTicks = Math.floor(base / 2) + rnd.nextInt(Math.max(2, base));
    }

    // ================= 1b. 挣扎循环（2328）=================
    if (--struggleTicks <= 0) {
      if (!roundsExhausted) fishStruggling = !fishStruggling;
      else fishStruggling = false;
      roundDrain = 0.0;
      if (fishStruggling) {
        roundsDone++;
        if (roundsDone >= maxRounds) roundsExhausted = true;
        struggleTicks = Math.round(struggleSecondsOf(tier) * TPS);
        freshStruggleTicks = Math.round(floorStruggleSecondsOf(tier, profile.archetype) * TPS);
        fatigued = false; fatigueTicks = 0;
        struggleDir = rnd.nextBoolean() ? -1 : 1;
        struggleSwitchTicks = 12 + rnd.nextInt(20);
        hist.roundLog.push({
          round: roundsDone, tick, distance,
          staminaPct: fishStamina / fishStaminaMax * 100,
          capPct: roundDrainCap(tier, fishStaminaMax, fishStamina, maxRounds, roundsDone, roundsExhausted, gear) * 100,
          exhaustAfter: roundsExhausted,
        });
      } else {
        struggleTicks = roundsExhausted ? TPS * 600 : Math.round(restSecondsOf(tier) * TPS);
        sprinting = false;
      }
    }

    // ================= 1b-2. 挣扎期间乱窜（2379）=================
    if (fishStruggling) {
      if (--struggleSwitchTicks <= 0) {
        if (profile.archetype === 'ERRATIC' || profile.archetype === 'BURST') {
          struggleDir = rnd.nextBoolean() ? -1 : 1;
        } else {
          const r = rnd.nextInt(3);
          struggleDir = r === 0 ? -1 : r === 1 ? 1 : 0;
        }
        const base = Math.round(profile.turnIntervalSec * TPS);
        struggleSwitchTicks = Math.max(6, Math.floor(base / 2) + rnd.nextInt(Math.max(2, base)));
      }
    } else {
      struggleDir = 0;                       // 休息：方向归零（2399）
    }

    // ================= 1c. 冲刺（2415，力竭的鱼不再冲刺）=================
    if (fishStruggling && !fatigued) {
      if (sprinting) {
        if (--sprintTicks <= 0) {
          sprinting = false;
          sprintCooldownTicks = Math.round(profile.sprintIntervalSec * TPS);
        }
      } else if (--sprintCooldownTicks <= 0) {
        sprinting = true;
        sprintTicks = Math.round(profile.sprintDurationSec * TPS);
        // 假动作只影响提示（fakeOutPending），对物理量没有作用 —— 照样消耗随机数
        if (profile.fakeOutChance > 0 && rnd.nextDouble() < profile.fakeOutChance) { /* 假动作 */ }
      }
    }

    // ================= 1c. 疲劳（2439，保底挣扎期内不许力竭）=================
    if (freshStruggleTicks > 0) freshStruggleTicks--;
    if (fatigued) {
      if (--fatigueTicks <= 0) fatigued = false;
    } else if (freshStruggleTicks <= 0 && !sprinting && fishStamina <= fishStaminaMax * fatigueThreshold) {
      fatigued = true;
      fatigueTicks = Math.round(V.fatigueDurationSec * TPS);
    }

    // ================= 2. 控杆三方向（2457）=================
    const fishAxis = struggleDir;
    let relation;
    if (fishAxis === 0 || counterDir === 0) relation = 'NEUTRAL';
    else if (counterDir === fishAxis) relation = 'WITH_FISH';
    else relation = 'AGAINST_FISH';
    let drainMult = relation === 'WITH_FISH' ? 0.65 : relation === 'NEUTRAL' ? 1.00 : 1.50;
    let tensionMult = relation === 'WITH_FISH' ? 0.50 : relation === 'NEUTRAL' ? 1.00 : 1.40;
    if (fatigued) { tensionMult *= 0.80; drainMult *= 1.20; }     // 2478

    // ================= 3. 张力（2512~2660）=================
    const gen = gear.tensionGen;
    let recoverMult = gear.tensionRecover / cfg.baseRecover;      // 2513
    const fatigueGainScale = fatigued ? cfg.fatiguedTensionGainScale : 1.0;
    if (fatigued) recoverMult *= cfg.fatiguedTensionRecoverScale;

    let delta;
    if (reeling) delta = cfg.reelGain * tensionMult * gen;        // 2528
    else if (releasing) delta = -cfg.releaseLoss * recoverMult;   // 2530
    else delta = -cfg.idleLoss * recoverMult;                     // 2532

    const fishPull = sprinting ? cfg.pullStruggleBase : cfg.pullIdleBase;   // 2538
    const fishTension = fishPull * profile.pullSpeed * tierSpeed * profile.tensionGain * gen;

    if (reeling) {
      let gain = delta + fishTension;                             // 2605
      const floor = delta * cfg.minReelGainRatio;                 // 2606
      if (gain < floor) gain = floor;
      gain *= fatigueGainScale;                                   // 2610
      tension += gain;
    } else {
      const fall = -delta;                                        // 2624
      const cappedFish = Math.min(fishTension, fall * cfg.maxFishSlowdown);
      let net = cappedFish - fall;
      if (!releasing) net = Math.min(net, -cfg.idleFallFloor);     // 2642
      tension += net;
    }
    tension = Math.max(0.0, tension);                             // 2658
    hist.tensionMax = Math.max(hist.tensionMax, tension);

    // ================= 4. 红区与断线（2664）=================
    if (tension > yellowMax) {
      if (++redZoneTicks > redZoneLimit) {
        return finish('断线', '红区超时', hist, { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick });
      }
    } else {
      redZoneTicks = Math.max(0, redZoneTicks - 2);                // 2671
    }
    if (tension >= tensionCeiling) {
      return finish('断线', '张力触顶', hist, { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick });
    }

    // ================= 5. 鱼体力（2699）=================
    const efficiency = tension <= whiteMax ? 1.0 : tension <= yellowMax ? 0.6 : 0.2;   // 2699
    const cap = fishStaminaMax * roundDrainCap(
      tier, fishStaminaMax, fishStamina, maxRounds, roundsDone, roundsExhausted, gear);  // 2708
    if (reeling) {
      const drain = cfg.staminaDrainBase / TPS * fishStaminaMax * efficiency * drainMult
        * gear.staminaDrain / tierStaminaResist;                                          // 2705
      const allowed0 = roundDrain < cap ? Math.min(drain, cap - roundDrain) : 0.0;         // 2710
      const allowed = Math.max(allowed0, gear.floorDrainPerSec / TPS);                     // 2717 保底削减
      fishStamina -= allowed;
      roundDrain += allowed;
      if (allowed0 > 0) hist.capHits++; else hist.capMisses++;
    }
    if (sprinting) fishStamina -= 0.10;                                                    // 2722
    else if (!fatigued) fishStamina += profile.staminaRegen / Math.max(0.5, gear.damping);  // 2725
    fishStamina = Math.max(0.0, Math.min(fishStaminaMax, fishStamina));                     // 2727

    // ================= 6. 距离（2752~2805）=================
    const strugglingNow = fishStruggling;
    const pullBase = strugglingNow ? gear.pullStruggle : gear.pullIdle;                     // 2757
    const reelResistNow = reelResistOf(tier, profile.archetype, gear.damping);              // 2758
    const pullMult = resistReelSpeed(pullBase, reelResistNow);                              // 2760
    const fishOutNow = strugglingNow
      ? 0.055 * profile.pullSpeed * tierSpeed * (fatigued ? profile.fatiguedPullSpeed : 1.0)
      : 0.006;                                                                              // 2769

    if (reeling) {
      const reelSpeed = 0.22 * pullMult;                                                    // 2776
      let distDelta = fishOutNow - reelSpeed;
      if (fatigued) distDelta = Math.min(distDelta, -cfg.fatiguedReelMinGain);              // 2792 力竭保底
      distance += distDelta;
    } else if (releasing) {
      distance += strugglingNow ? 0.05 * tierSpeed : 0.004;                                 // 2797
    } else {
      distance += strugglingNow ? 0.012 + 0.020 * profile.pullSpeed * tierSpeed : 0.002;     // 2801
    }
    distance = Math.max(0.0, distance);                                                     // 2805
    hist.distanceMin = Math.min(hist.distanceMin, distance);

    if (distance >= maxDistance) {
      return finish('逃脱', '线被放完', hist, { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick });
    }

    // ================= 7. 上鱼判定（2830，只有两条路）=================
    const tired = fishStamina <= Math.max(cfg.staminaEpsilonFloor, fishStaminaMax * cfg.staminaEpsilonRatio);
    if (distance <= 0.0) {
      return finish('上鱼', '拉干', hist, { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick });
    }
    if (tired) {
      return finish('上鱼', '遛翻', hist, { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick });
    }

    // ---------- 记下本 tick 的观测（下一 tick 的策略读它）----------
    obs.push({ tension, struggling: fishStruggling, dir: struggleDir });
    if (obs.length > 64) obs.shift();
  }
  return finish('超时', '15 分钟没结果', hist,
    { roundsDone, distance, fishStamina, fishStaminaMax, tension, tick: MAX_TICKS });
}

/** FishingSession.roundDrainCap()（209）—— 单轮最多扣掉"体力上限"的比例。 */
function roundDrainCap(tier, max, staminaNow, maxRounds, roundsDone, exhausted, gear) {
  if (tier < 4) return 1.0;                       // 低档：不限，装备好就一轮上
  const bonus = Math.min(cfg.capDampingBonus,
    Math.max(0.0, gear.damping - 1.0) * cfg.dampingToCapRatio * gear.roundCapBonus);   // 213
  const target = max * (cfg.totalDrainTarget + bonus);
  const drained = Math.max(0.0, max - staminaNow);                                    // 218 用实际已扣
  const remaining = Math.max(0.0, target - drained);
  if (exhausted) return 1.0;                                                          // 221
  const roundsLeft = Math.max(1, maxRounds - roundsDone + 1);
  return max <= 0 ? 1.0 : (remaining / roundsLeft) / max;
}

function finish(route, reason, hist, st) {
  hist.rounds = st.roundsDone;
  hist.ticks = st.tick;
  hist.route = route + (reason ? '·' + reason : '');
  hist.landingRoute = route;
  hist.reason = reason;
  hist.staminaMax = st.fishStaminaMax;
  hist.endStaminaPct = st.fishStaminaMax > 0 ? st.fishStamina / st.fishStaminaMax * 100 : 0;
  hist.endDistance = st.distance;
  hist.endTension = st.tension;
  return hist;
}

// ============================================================================
//  6. 一批种子 → 统计
// ============================================================================
function pctOf(arr, p) {
  if (!arr.length) return NaN;
  const s = [...arr].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.max(0, Math.round((s.length - 1) * p)))];
}
const histOf = (arr, key) => arr.reduce((m, r) => (m[r[key]] = (m[r[key]] || 0) + 1, m), {});

function runCase(species, tier, weightKg, gear, policyName, seeds, castDistance) {
  const policy = POLICIES[policyName];
  const rows = [];
  for (let i = 0; i < seeds; i++) {
    rows.push(fight({ species, tier, weightKg, gear, policy, castDistance, seed: 1000003 + i * 7919 }));
  }
  const caught = rows.filter((r) => r.landingRoute === '上鱼');
  const rounds = caught.map((r) => r.rounds);
  const secs = caught.map((r) => r.ticks / TPS);
  return {
    species, tier, weightKg, gear, rows,
    maxRounds: rows[0].maxRounds,
    caughtRate: caught.length / rows.length,
    snapRate: rows.filter((r) => r.landingRoute === '断线').length / rows.length,
    escapeRate: rows.filter((r) => r.landingRoute === '逃脱').length / rows.length,
    timeoutRate: rows.filter((r) => r.landingRoute === '超时').length / rows.length,
    roundsP: { p05: pctOf(rounds, 0.05), p50: pctOf(rounds, 0.50), p95: pctOf(rounds, 0.95) },
    secondsP: { p05: pctOf(secs, 0.05), p50: pctOf(secs, 0.50), p95: pctOf(secs, 0.95) },
    routeHist: histOf(caught, 'reason'),
    roundHist: histOf(caught, 'rounds'),
    drainCaps: rows[0].roundLog.map((x) => x.capPct),
  };
}

// ============================================================================
//  7. 标定尺：扫一个字段，看它把"回合数"推到哪（用户 2026-10-08 的方法论）
//
//  "回合数事实上理应由**张力与游速**博弈共同设计，游不快的就改张力直到达到预期。"
//
//  机制上确实如此：回合数主要由**距离博弈**决定（鱼拽线 vs 玩家收线），
//  而"张力涨速"决定玩家能按住左键多久 —— 张力涨得快 → 被迫频繁松手 →
//  收线量下降 → 线不会被迅速收干 → 回合数拉长。
//  所以对"游不快"的鱼，抬 tensionGain 是正解：抬 pullSpeed 只会让它把线
//  拽得更远、还更容易把张力顶到断线，反而更不像"一条游不快的鱼"。
// ============================================================================
function sweepField(species, field, values, tiers, policyName, seeds, castDistance, parts, line, reel) {
  const rows = [];
  for (const v of values) {
    const patched = { ...species, values: { ...species.values, [field]: v } };
    for (const t of tiers) {
      const w = weightForTier(patched, t);
      const rod = pickRod(null, t, w);
      if (rod.maxWeightKg < w) continue;
      const gear = composeStats(rod, line, reel, parts.barbed_hook);
      rows.push({ value: v, tier: t, rod, r: runCase(patched, t, w, gear, policyName, seeds, castDistance) });
    }
  }
  return rows;
}

/** 求解：最小的字段值，使**最高档**的中位回合 ≥ target 且断线率 ≤ snapLimit。 */
function solveForTarget(species, field, target, snapLimit, policyName, seeds, castDistance, parts, line, reel) {
  const tiers = tiersOf(species).slice(-1);          // 最高档（红 > 金）
  const cur = species.values[field];
  const step = Math.max(0.02, Math.round(cur * 0.05 * 100) / 100);
  const trace = [];
  for (let v = cur + step; v <= cur * 4 + 0.5; v += step) {
    const rounded = Math.round(v * 100) / 100;
    const r = sweepField(species, field, [rounded], tiers, policyName, seeds, castDistance, parts, line, reel)[0];
    if (!r) continue;
    trace.push({ v: rounded, med: r.r.roundsP.p50, snap: r.r.snapRate, sec: r.r.secondsP.p50, caught: r.r.caughtRate });
    if (!isNaN(r.r.roundsP.p50) && r.r.roundsP.p50 >= target && r.r.snapRate <= snapLimit) {
      return { best: rounded, trace, current: cur, tier: tiers[0] };
    }
  }
  return { best: null, trace, current: cur, tier: tiers[0] };
}

// ============================================================================
//  8. 命令行
// ============================================================================
const GEAR_PRESETS = {
  white:  { cn: '白轮白线（手线轮 + 麻线）',        reel: 'hand_reel',     line: 'hemp_line' },
  green:  { cn: '绿轮绿线（纺车轮 + 棉线）',        reel: 'spinning_reel', line: 'cotton_line' },
  blue:   { cn: '蓝轮蓝线（鼓式轮 + 尼龙线）',      reel: 'drum_reel',     line: 'nylon_line' },
  purple: { cn: '紫轮紫线（水滴轮 + 碳氟线）',      reel: 'baitcast_reel', line: 'fluoro_line' },
  gold:   { cn: '金轮金线（大型纺车轮 + PE线）',    reel: 'heavy_reel',    line: 'pe_line' },
  top:    { cn: '金轮红线（大型纺车轮 + 钛合金线）', reel: 'heavy_reel',   line: 'titanium_line' },
};

function parseArgs(argv) {
  const a = {
    gear: 'purple', rod: null, policy: 'good', seeds: 200, cast: 20, fish: [], all: false,
    trace: null, tiers: null, sweep: null, field: 'tensionGain', values: null, target: null, snapLimit: 0.10,
  };
  for (let i = 0; i < argv.length; i++) {
    const k = argv[i];
    const val = () => argv[++i];
    if (k === '--gear') a.gear = val();
    else if (k === '--rod') a.rod = val();
    else if (k === '--policy') a.policy = val();
    else if (k === '--seeds') a.seeds = Number(val());
    else if (k === '--cast') a.cast = Number(val());
    else if (k === '--fish') a.fish.push(val());
    else if (k === '--all') a.all = true;
    else if (k === '--trace') a.trace = val();
    else if (k === '--line') a.line = val();
    else if (k === '--reel') a.reel = val();
    else if (k === '--tiers') a.tiers = val().split(',').map(Number);
    else if (k === '--sweep') a.sweep = val();
    else if (k === '--field') a.field = val();
    else if (k === '--values') a.values = val().split(',').map(Number);
    else if (k === '--target') a.target = Number(val());
    else if (k === '--snap-limit') a.snapLimit = Number(val());
    else if (k === '--help' || k === '-h') a.help = true;
    else throw new Error('未知参数：' + k);
  }
  return a;
}

function loadFish() {
  const consts = readTierConsts();
  const fish = parseFish(fs.readFileSync(path.join(
    ROOT, 'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java'), 'utf8'));
  for (const f of fish) {
    f.tierConst = String(f.tiers).trim();
    f.tierList = consts[f.tierConst];
    if (!f.tierList) {
      throw new Error(f.id + ' 的档位常量 `' + f.tierConst + '` 在 FishSpecies.java 里找不到定义');
    }
  }
  return fish;
}

/** 没指定竿身就挑"够得到这个档位、又扛得住这条鱼"的最便宜竿。 */
function pickRod(wantedMaterial, tier, weightKg) {
  if (wantedMaterial) return readRod(wantedMaterial);
  for (const m of ['wood', 'iron', 'gold', 'diamond', 'netherite']) {
    const rod = readRod(m);
    if (rod.tier >= tier && rod.maxWeightKg >= weightKg) return rod;
  }
  return readRod('netherite');
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) { console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0]); return 0; }
  if (!POLICIES[args.policy]) throw new Error('未知策略：' + args.policy + '（可选 ' + Object.keys(POLICIES).join('/') + '）');

  const parts = readParts();
  const preset = GEAR_PRESETS[args.gear];
  if (!preset) throw new Error('未知装备预设：' + args.gear + '（可选 ' + Object.keys(GEAR_PRESETS).join('/') + '）');
  const reel = parts[args.reel || preset.reel];
  const line = parts[args.line || preset.line];
  const hook = parts.barbed_hook;
  if (!reel || !line || !hook) throw new Error('配件表里找不到：' + [args.reel || preset.reel, args.line || preset.line, 'barbed_hook']);

  const all = loadFish();
  const pol = POLICIES[args.policy];

  console.log('  === 垂纶 · 对抗模拟（逐 tick 复刻 FishingSession.tickFight）===');
  console.log('  装备：' + preset.cn
    + '   竿身：' + (args.rod ? ROD_MATERIALS[args.rod].cn + '路亚竿' : '按档位自动挑'));
  console.log('  策略：' + pol.cn + '（反应延迟 ' + pol.lag + ' tick ≈ ' + (pol.lag * 50) + 'ms，读向准确率 '
    + Math.round(pol.accuracy * 100) + '%）   种子数：' + args.seeds + '   抛投：' + args.cast + ' 格');
  console.log('  鱼种表：' + all.length + ' 种（现读 FishSpecies.java + Config.java + ModItems.java）');

  // ---------- 标定模式：扫一个字段，看回合数怎么动 ----------
  if (args.sweep) {
    const sp = all.find((f) => f.id === args.sweep);
    if (!sp) throw new Error('找不到鱼种 ' + args.sweep);
    const tiers = tiersOf(sp);
    const cur = sp.values[args.field];
    if (typeof cur !== 'number') throw new Error('字段 ' + args.field + ' 不是数值（可选 tensionGain / pullSpeed / stamina …）');
    const pol2 = POLICIES[args.policy];
    console.log('  鱼种：' + sp.name + '(' + sp.id + ') 档位表 ' + sp.tierConst
      + ' = {' + tiers.join(',') + '}   拉力 ' + sp.values.pullSpeed
      + '   体力 ' + sp.values.stamina + '   当前 ' + args.field + ' = ' + cur);
    console.log('  装备：' + preset.cn + '   策略：' + pol2.cn + '   种子数：' + args.seeds + '   抛投：' + args.cast + ' 格');
    console.log('');

    if (args.target != null) {
      // 求解：最小改动量达到目标回合
      const sol = solveForTarget(sp, args.field, args.target, args.snapLimit, args.policy,
        args.seeds, args.cast, parts, line, reel);
      console.log('  === 求解：' + args.field + ' 抬到多少，' + TIER_CN[sol.tier] + '档中位回合 ≥ ' + args.target
        + '（断线率 ≤ ' + (args.snapLimit * 100).toFixed(0) + '%）===');
      console.log('  当前值 ' + sol.current + ' → 候选（逐档试）：');
      for (const t of sol.trace) {
        console.log('    ' + args.field + ' = ' + String(t.v).padEnd(7)
          + '中位 ' + String(t.med).padEnd(4) + '回合   耗时 ' + (isNaN(t.sec) ? '-' : t.sec.toFixed(1) + 's')
          + '   上鱼 ' + (t.caught * 100).toFixed(0) + '%   断线 ' + (t.snap * 100).toFixed(0) + '%'
          + (t.med >= args.target ? '   ← 达标' : ''));
      }
      console.log('');
      console.log(sol.best != null
        ? '  → 建议 ' + sp.id + ' 的 ' + args.field + '：' + sol.current + ' → **' + sol.best + '**'
        : '  → 在扫描范围内（到 ' + (sol.current * 4 + 0.5).toFixed(2) + '）没找到达标值，需要换别的字段或放宽目标');
      return 0;
    }

    // 手动指定候选值（不给就围绕当前值取 6 档）
    const values = args.values || [1.0, 1.2, 1.45, 1.7, 2.0, 2.4].filter((v) => true);
    console.log('  ' + args.field.padEnd(9) + '档'.padEnd(4) + '回合(最小/中位/最大)'.padEnd(21)
      + '上鱼'.padEnd(7) + '断线'.padEnd(7) + '逃脱'.padEnd(7) + '耗时中位'.padEnd(11) + '上鱼方式');
    console.log('  ' + '-'.repeat(96));
    for (const row of sweepField(sp, args.field, values, tiers, args.policy, args.seeds, args.cast, parts, line, reel)) {
      const r = row.r;
      const pc = (v) => (v * 100).toFixed(0) + '%';
      console.log('  ' + String(row.value).padEnd(9) + TIER_CN[row.tier].padEnd(4)
        + (r.roundsP.p05 + ' / ' + r.roundsP.p50 + ' / ' + r.roundsP.p95).padEnd(21)
        + pc(r.caughtRate).padEnd(7) + pc(r.snapRate).padEnd(7) + pc(r.escapeRate).padEnd(7)
        + (isNaN(r.secondsP.p50) ? '-' : r.secondsP.p50.toFixed(1) + 's').padEnd(11)
        + Object.entries(r.routeHist).map(([k, v]) => k + ' ' + v).join(' '));
    }
    return 0;
  }

  // 挑出要跑的 (鱼 × 档位)
  let list = [];
  for (const s of all) {
    if (s.rods === 'FLOAT') continue;                    // 台钓不进入对抗（onHooked 直接收鱼）
    for (const t of tiersOf(s)) {
      if (args.tiers && !args.tiers.includes(t)) continue;
      if (!args.tiers && !args.all && t < 4) continue;   // 默认只看金/红档
      list.push({ species: s, tier: t, weightKg: weightForTier(s, t) });
    }
  }
  if (args.fish.length) list = list.filter((x) => args.fish.includes(x.species.id));
  if (!list.length) { console.log('\n  没有匹配的鱼（检查 --fish 的 id / --tiers）。'); return 1; }

  console.log('');
  console.log('  ' + '鱼种'.padEnd(20) + '档'.padEnd(4) + '体重'.padEnd(9) + '拉力'.padEnd(7)
    + '回合(最小/中位/最大)'.padEnd(21) + '上鱼'.padEnd(7) + '断线'.padEnd(7)
    + '逃脱'.padEnd(7) + '耗时中位'.padEnd(11) + '上鱼方式');
  console.log('  ' + '-'.repeat(120));

  const results = [];
  for (const item of list) {
    const rod = pickRod(args.rod, item.tier, item.weightKg);
    if (rod.maxWeightKg < item.weightKg) continue;       // 超承重：游戏里会烧竿，不进入对抗
    const gear = composeStats(rod, line, reel, hook);
    const r = runCase(item.species, item.tier, item.weightKg, gear, args.policy, args.seeds, args.cast);
    r.rod = rod;
    results.push(r);

    const dist = Object.entries(r.roundHist).sort((a, b) => a[0] - b[0]).map(([k, v]) => k + '轮×' + v).join(' ');
    const routeCn = Object.entries(r.routeHist).map(([k, v]) => k + ' ' + v).join(' ');
    const pc = (v) => (v * 100).toFixed(0) + '%';
    const rr = (v) => (isNaN(v) ? '-' : String(v));
    console.log('  ' + (item.species.name + ' ' + item.species.id).padEnd(20)
      + TIER_CN[item.tier].padEnd(4)
      + (item.weightKg.toFixed(1) + 'kg').padEnd(9)
      + item.species.values.pullSpeed.toFixed(2).padEnd(7)
      + (rr(r.roundsP.p05) + ' / ' + rr(r.roundsP.p50) + ' / ' + rr(r.roundsP.p95)).padEnd(21)
      + pc(r.caughtRate).padEnd(7) + pc(r.snapRate).padEnd(7) + pc(r.escapeRate).padEnd(7)
      + (isNaN(r.secondsP.p50) ? '-' : r.secondsP.p50.toFixed(1) + 's').padEnd(11)
      + routeCn);

    if (args.trace === item.species.id) {
      console.log('     ── ' + item.species.name + '（' + TIER_CN[item.tier] + '档 ' + item.weightKg.toFixed(2)
        + 'kg）逐轮明细，第一颗种子 ──');
      console.log('        竿身 ' + rod.cn + '竿  张力上限 ' + gear.tensionCeiling.toFixed(1)
        + '（白 ' + (gear.tensionCeiling * gear.whiteZoneRatio).toFixed(1)
        + ' / 黄 ' + (gear.tensionCeiling * gear.yellowZoneRatio).toFixed(1) + '）'
        + '  线长 ' + gear.lineLength.toFixed(0) + ' 格'
        + '  体力池 ' + r.rows[0].staminaMax.toFixed(1)
        + '（基准 ' + item.species.values.stamina.toFixed(1) + ' × 档位倍率 '
        + tierStaminaScaleOf(item.tier).toFixed(2) + '）  档位游速 ×' + tierSpeedOf(item.tier).toFixed(3)
        + '  体力抗性 ×' + tierResistOf(item.tier).toFixed(3));
      for (const e of r.rows[0].roundLog) {
        console.log('        第 ' + e.round + ' 轮 @ ' + (e.tick / TPS).toFixed(1) + 's  鱼力 '
          + e.staminaPct.toFixed(1) + '%  本轮扣力上限 ' + e.capPct.toFixed(1) + '% 距离 '
          + e.distance.toFixed(1) + ' 格' + (e.exhaustAfter ? '  ← 最后一轮' : ''));
      }
      const f0 = r.rows[0];
      console.log('        结局：' + f0.route + '  ' + f0.rounds + '/' + f0.maxRounds + ' 回合  '
        + (f0.ticks / TPS).toFixed(1) + 's  收场张力 ' + f0.endTension.toFixed(1)
        + '  剩鱼力 ' + f0.endStaminaPct.toFixed(2) + '%  剩线 ' + f0.endDistance.toFixed(1) + ' 格');
    }
  }

  // ---------- 汇总（用户的对账口径：紫轮紫线 vs 红鱼几回合）----------
  const aggOf = (tier) => {
    const arr = results.filter((r) => r.tier === tier);
    if (!arr.length) return null;
    const meds = arr.map((r) => r.roundsP.p50).filter((v) => !isNaN(v)).sort((a, b) => a - b);
    const secs = arr.map((r) => r.secondsP.p50).filter((v) => !isNaN(v));
    return {
      n: arr.length,
      caught: arr.reduce((a, r) => a + r.caughtRate, 0) / arr.length,
      minRounds: Math.min(...arr.map((r) => (isNaN(r.roundsP.p05) ? Infinity : r.roundsP.p05))),
      maxRounds: Math.max(...arr.map((r) => (isNaN(r.roundsP.p95) ? -Infinity : r.roundsP.p95))),
      medRounds: meds.length ? meds[Math.floor(meds.length / 2)] : NaN,
      avgSeconds: secs.length ? secs.reduce((a, b) => a + b, 0) / secs.length : NaN,
    };
  };
  console.log('');
  console.log('  === 汇总 ===');
  for (const t of [5, 4, 3, 2, 1, 0]) {
    const a = aggOf(t);
    if (!a) continue;
    console.log('  ' + TIER_CN[t] + '档：' + String(a.n).padStart(2) + ' 行 · 上鱼率 '
      + (a.caught * 100).toFixed(0) + '% · 回合数 ' + a.minRounds + '~' + a.maxRounds
      + '（中位 ' + a.medRounds + '）· 平均耗时中位 '
      + (isNaN(a.avgSeconds) ? '-' : a.avgSeconds.toFixed(1) + 's'));
  }
  // ---------- 与用户口径对照 ----------
  //   用户 2026-10-08："紫轮紫线与红鱼最少拉扯了 4 回合（镜鲤太拉了不算，
  //   此处仅距离大西洋鲑等）"。这里把"参考组"和"偏软的"分开列，
  //   **不写成红绿断言** —— 设计目标是用户定的，工具只负责把差距摊开。
  const REFERENCE = ['atlantic_salmon', 'juvenile_shark', 'bluefin_tuna', 'taimen',
    'sailfish', 'alligator_gar', 'mahi_mahi', 'greenland_shark'];
  const refRows = results.filter((r) => r.tier === 5 && REFERENCE.includes(r.species.id));
  if (refRows.length) {
    console.log('');
    console.log('  === 与用户口径对照 ===');
    console.log('  用户口径：紫轮紫线与红鱼最少拉扯 4 回合（镜鲤太拉了不算，比照大西洋鲑等）');
    console.log('  参考组（用户点名的强红鱼）：');
    for (const r of refRows) {
      const okMark = r.roundsP.p50 >= 4 ? '✓' : '✗ 比口径少 ' + (4 - r.roundsP.p50) + ' 回合';
      console.log('    ' + r.species.name.padEnd(8) + '拉力 ' + r.species.values.pullSpeed.toFixed(2)
        + '  ' + r.roundsP.p05 + '/' + r.roundsP.p50 + '/' + r.roundsP.p95 + ' 回合'
        + '  ' + (isNaN(r.secondsP.p50) ? '-' : r.secondsP.p50.toFixed(1) + 's')
        + '  ' + okMark);
    }
    const soft = results.filter((r) => r.tier === 5 && !isNaN(r.roundsP.p50) && r.roundsP.p50 <= 2)
      .sort((a, b) => b.species.values.pullSpeed - a.species.values.pullSpeed);
    if (soft.length) {
      console.log('  偏软的（中位 ≤2 回合，与你定的"红鱼拉力 ≥2.65"红线对照）：');
      for (const r of soft) {
        console.log('    ' + r.species.name.padEnd(8) + '拉力 ' + r.species.values.pullSpeed.toFixed(2)
          + (r.species.values.pullSpeed < 2.65 ? ' ← 低于 2.65 红线' : '') + '  '
          + r.roundsP.p50 + ' 回合 / ' + (isNaN(r.secondsP.p50) ? '-' : r.secondsP.p50.toFixed(1) + 's')
          + '  ' + Object.entries(r.routeHist).map(([k, v]) => k + ' ' + v).join(' '));
      }
    }
  }
  return 0;
}

// ============================================================================
//  8. 自检（--check）：把"这个模拟本身可信"也变成可证伪的断言
// ============================================================================
function selfCheck() {
  const fails = [];
  const ok = (cond, msg) => { console.log('  ' + (cond ? '✅' : '❌') + ' ' + msg); if (!cond) fails.push(msg); };
  console.log('  === 模拟器自检 ===');

  // ① 随机数发生器必须真的是 java.util.Random（拿 Java 的已知序列对账）
  const seq = new JavaRandom(42);
  const v1 = seq.next(32) | 0;
  const v2 = seq.next(32) | 0;
  ok(v1 === -1170105035 && v2 === 234785527,
    'JavaRandom(42) 连读两个 nextInt() = ' + v1 + ', ' + v2 + '（java.util.Random 的已知值 -1170105035, 234785527）');
  // nextDouble 的位拼装：必须等于同一条序列上 next(26)、next(27) 拼出来的值
  const db = new JavaRandom(42);
  const dbl = db.nextDouble();
  const dc = new JavaRandom(42);
  const expectD = (dc.next(26) * 67108864 + dc.next(27)) / 9007199254740992;
  ok(dbl === expectD, 'nextDouble() 的位拼装与 next(26)+next(27) 一致：' + dbl);

  // ② 数据链：合成出来的属性必须都是有限值（缺配置项就会变 NaN）
  const parts = readParts();
  const rod = readRod('netherite');
  const gear = composeStats(rod, parts.fluoro_line, parts.baitcast_reel, parts.barbed_hook);
  const bad = Object.entries(gear).filter(([, v]) => typeof v === 'number' && !Number.isFinite(v));
  ok(bad.length === 0, '紫轮紫线 + 下界合金竿 合成属性无 NaN：'
    + '上限 ' + gear.tensionCeiling.toFixed(1) + ' 回复 ' + gear.tensionRecover.toFixed(2)
    + ' gen ' + gear.tensionGen + ' 扣力 ' + gear.staminaDrain.toFixed(3)
    + ' 回拉 ' + gear.pullIdle + '/' + gear.pullStruggle + ' 阻尼 ' + gear.damping.toFixed(2)
    + ' 保底 ' + gear.floorDrainPerSec.toFixed(1) + '/s 线长 ' + gear.lineLength.toFixed(0));

  // ③ 体重分档：代表体重必须真的落在目标档位
  const fish = loadFish();
  const salmon = fish.find((f) => f.id === 'atlantic_salmon') || fish[fish.length - 1];
  const w5 = weightForTier(salmon, 5);
  ok(tierForWeight(salmon, w5) === 5,
    salmon.id + ' 的代表红档体重 ' + w5.toFixed(2) + 'kg 判档 = ' + tierForWeight(salmon, w5));

  // ④ 同种子必须完全可复现
  const a1 = fight({ species: salmon, tier: 5, weightKg: w5, gear, policy: POLICIES.good, seed: 12345, castDistance: 20 });
  const a2 = fight({ species: salmon, tier: 5, weightKg: w5, gear, policy: POLICIES.good, seed: 12345, castDistance: 20 });
  ok(a1.rounds === a2.rounds && a1.ticks === a2.ticks && a1.route === a2.route,
    '同种子完全复现：' + a1.route + ' / ' + a1.rounds + ' 回合 / ' + (a1.ticks / TPS).toFixed(1) + 's');

  // ⑤ 每条鱼的档位表必须解析成功（常量名 → 数组），否则整张表会静默作废
  const unparsed = fish.filter((f) => !Array.isArray(f.tierList) || !f.tierList.length);
  ok(unparsed.length === 0, '全部 ' + fish.length + ' 种鱼的档位表都解析成功'
    + '（例：' + salmon.id + ' = ' + salmon.tierConst + ' → {' + salmon.tierList.join(',') + '}）');

  // ⑥ 设计不变量：模拟出来的世界必须自洽
  //    · 回合数 ∈ [1, maxRounds]（roundsForTier 的硬边界）
  //    · "拉干" ⇒ 线真的收到 0；"遛翻" ⇒ 鱼力真的 ≤ 0.5%
  //    · 每个 tick 玩家必定在"收线"或"放线"（releasing = !reeling，没有中立档）
  const inv = [];
  let samples = 0;
  let minR = Infinity, maxR = 0;
  const routeAll = {};
  for (const f of fish) {
    for (const t of tiersOf(f)) {
      if (f.rods === 'FLOAT') continue;
      const tw = weightForTier(f, t);
      const rg = pickRod(null, t, tw);
      if (rg.maxWeightKg < tw) continue;
      const g = composeStats(rg, parts.fluoro_line, parts.baitcast_reel, parts.barbed_hook);
      for (let i = 0; i < 6; i++) {
        const r = fight({ species: f, tier: t, weightKg: tw, gear: g, policy: POLICIES.good, seed: 900 + i * 313, castDistance: 20 });
        samples++;
        const key = r.landingRoute === '上鱼' ? r.reason : r.landingRoute;
        routeAll[key] = (routeAll[key] || 0) + 1;
        if (r.landingRoute === '上鱼') { minR = Math.min(minR, r.rounds); maxR = Math.max(maxR, r.rounds); }
        if (r.rounds < 1 || r.rounds > r.maxRounds) inv.push(f.id + ' 回合数越界 ' + r.rounds + '/' + r.maxRounds);
        if (r.landingRoute === '上鱼' && r.reason === '拉干' && r.endDistance > 0.001) inv.push(f.id + ' 拉干但距离 ' + r.endDistance);
        if (r.landingRoute === '上鱼' && r.reason === '遛翻' && r.endStaminaPct > 0.5 + 1e-9) inv.push(f.id + ' 遛翻但鱼力 ' + r.endStaminaPct.toFixed(2) + '%');
        if (r.reelTicks + r.releaseTicks !== r.ticks) inv.push(f.id + ' 有 tick 既没收线也没放线（中立 ' + (r.ticks - r.reelTicks - r.releaseTicks) + '）');
      }
    }
  }
  ok(inv.length === 0, '设计不变量（' + samples + ' 场）：回合数 ∈ [1, maxRounds]（实得 ' + minR + '~' + maxR
    + '）、拉干 ⇒ 距离 0、遛翻 ⇒ 鱼力 ≤0.5%、每 tick 必有收线或放线'
    + (inv.length ? '　例：' + inv.slice(0, 3).join('；') : ''));
  console.log('  ℹ 结局分布：' + Object.entries(routeAll).map(([k, v]) => k + ' ' + v).join(' / '));

  console.log('');
  console.log('  ' + (fails.length === 0 ? '自检全部通过' : fails.length + ' 项未通过'));
  return fails.length === 0 ? 0 : 1;
}

if (require.main === module) {
  if (process.argv.includes('--check')) {
    process.exitCode = selfCheck();
  } else {
    process.exitCode = main();
  }
}

module.exports = {
  JavaRandom, readParts, readRod, composeStats, fight, roundDrainCap,
  tierForWeight, weightForTier, tiersOf, POLICIES, GEAR_PRESETS, runCase, selfCheck, loadFish,
};
