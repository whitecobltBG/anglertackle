/*
 * check-reel-stall.js —— 复现并验证"一直长按左键收线，判定条卡在中间"。
 *
 * 玩家反馈："一直长按左键收线那个黑色判定条最终会卡到中间。"
 *
 * ── 根因 ──────────────────────────────────────────────────────────
 * 鱼的拉力**正比于当前张力**（含 tensionGain × gen），而收线的涨幅是**常数**：
 *     收线涨  = REEL_GAIN × tensionMult × gen           （常数）
 *     鱼拉    = PULL_IDLE × pullSpeed × tierSpeed
 *               × tensionGain × gen                     （正比于…不，是常数！）
 *
 * 等等——鱼拉这一项其实也是常数（不含张力）。真正让张力停住的是
 * **控杆关系倍率 tensionMult**：它把"收线涨"和"鱼拉"一起缩放。
 *   · 输入导致 tensionMult 变小时（正拉 ×0.5），收线涨会被砍半，
 *     而鱼的拉力同样被砍半 —— 两者比例不变，所以不是这个原因。
 *
 * 真正的原因：张力越高 → 进入黄/红区后 **efficiency 下降**，
 * 但那影响的是扣鱼力而不是张力。所以张力本该一直涨……
 *
 * 除非 `this.reeling` 变成了 false。而这正是**输入掉包**的表现：
 * 服务端 3 tick 没收到输入就判玩家松手，于是走"中立回落"分支，
 * 张力既不涨也不落（鱼的拉力刚好抵消回落）—— 判定条就此卡在中间。
 *
 * 这个脚本算清楚两件事：
 *   ① 收线时张力到底涨多快（应当恒为正 —— 只被削到 25%，不会变负）
 *   ② 中立时"鱼的拉力抵消回落"的那段区间（就是卡住的位置）
 *
 * 用法：node tools/check-reel-stall.js
 */
const fs = require('fs');
// 玩法数值现在住在配置文件里（[gameplay] 组），源码里没有对应常量了。
// 统一从 tools/tuning.js 的 cfg 取"游戏里实际生效的默认值"。
const __gameplayCfg = require('./tuning.js').cfg;
function __gameplay(key, fallback) {
  if (key in __gameplayCfg) return __gameplayCfg[key];
  throw new Error('配置里没有 ' + key + ' —— 检查 Config.java 的 [gameplay] 组');
}

const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..');
const sessSrc = fs.readFileSync(path.join(ROOT, 'src', 'main', 'java', 'com',
  'whitecobalt', 'anglertackle', 'fishing', 'FishingSession.java'), 'utf8');

// 调参值统一从 tools/tuning.js 取（读 Config.java 的默认值 + 源码里仍有的常量）。
// 曾经这里直接正则抓 Java 源码里的常量，常量一搬进 Config.java 就全线崩。
const TUNE = require('./tuning.js');
const readConst = (name) => {
  // Config 派生量优先；没有再从 Java 源码兜底
  if (TUNE[name] !== undefined) return TUNE[name];
  const m = new RegExp('(?:static final )?double ' + name + '\\s*=\\s*([\\d.]+)\\s*;').exec(sessSrc);
  if (!m) throw new Error('没找到常量 ' + name);
  return Number(m[1]);
};
const REEL_GAIN = readConst('REEL_GAIN');
const IDLE_LOSS = readConst('IDLE_LOSS');
const PULL_IDLE = readConst('PULL_IDLE');
const MIN_REEL_GAIN_RATIO = __gameplay('minReelGainRatio');
const MAX_FISH_SLOWDOWN = __gameplay('maxFishSlowdown');
const BASE_RECOVER = TUNE.BASE_RECOVER;
const W_LINE = TUNE.cfg.lineOnTension, W_ROD = TUNE.cfg.rodOnTension;
const WHITE_RATIO = TUNE.WHITE_ZONE_RATIO, YELLOW_RATIO = TUNE.YELLOW_ZONE_RATIO;
const TIER_SPEED = (t) => Math.pow(1.12, t);

console.log('从 FishingSession.java 读到：');
console.log('  REEL_GAIN           = ' + REEL_GAIN);
console.log('  IDLE_LOSS           = ' + IDLE_LOSS);
console.log('  PULL_IDLE           = ' + PULL_IDLE);
console.log('  MIN_REEL_GAIN_RATIO = ' + MIN_REEL_GAIN_RATIO
  + '   （收线时鱼拉最多削掉的涨势，保留 ' + (MIN_REEL_GAIN_RATIO * 100) + '%）');
console.log('  MAX_FISH_SLOWDOWN   = ' + MAX_FISH_SLOWDOWN
  + '   （中立时鱼拉最多减缓的回落，保留 ' + ((1 - MAX_FISH_SLOWDOWN) * 100) + '% 净回落）');

const FISH = [
  { name: '鲫鱼(白)', pull: 0.70, gain: 0.90, tier: 0 },
  { name: '翘嘴(紫)', pull: 1.35, gain: 1.20, tier: 3 },
  { name: '梭鲈(紫)', pull: 1.45, gain: 1.30, tier: 3 },
  { name: '鲑(红)', pull: 3.125, gain: 1.25, tier: 5 },
  { name: '蓝鳍金枪鱼(红)', pull: 3.4375, gain: 1.50, tier: 5 },
  { name: '幼鲨(红)', pull: 3.625, gain: 1.70, tier: 5 },
];
const LINE = [
  { name: '麻线', gen: 1.00, rec: 1.00, ceil: 1.00 },
  { name: '尼龙线', gen: 0.95, rec: 1.30, ceil: 1.28 },
  { name: '钛合金线', gen: 0.58, rec: 1.76, ceil: 2.20 },
];
const ROD = { name: '合金竿', ceil: 1.70, rec: 1.90 };
const composeCeiling = (rc, lc) => 100 * (1 + (lc - 1) * W_LINE) * (1 + (rc - 1) * W_ROD);
const composeRecover = (rr, lr) => BASE_RECOVER * (1 + (lr - 1) * W_LINE) * (1 + (rr - 1) * W_ROD);

const pad = (s, n) => String(s).padEnd(n);

console.log('\n=== ① 收线时张力涨速（修复后：恒为正，永不为零）===\n');
console.log('  鱼种               鱼线        上限   原始涨+鱼拉   钳制后涨速   判定');
console.log('  ' + '-'.repeat(86));
let fail = 0;
const assert = (cond, msg) => { console.log((cond ? '  ✅ ' : '  ❌ ') + msg); if (!cond) fail++; };

for (const f of FISH) {
  for (const l of LINE) {
    const ceil = composeCeiling(ROD.ceil, l.ceil);
    // 中立控杆 tensionMult = 1.0，张力取黄区中点（最容易被抵消的位置）
    const t = ceil * 0.7;
    const delta = REEL_GAIN * 1.0 * l.gen;
    const fishT = PULL_IDLE * f.pull * TIER_SPEED(f.tier) * f.gain * l.gen;
    const raw = delta + fishT;
    let gain = raw;
    const floor = delta * MIN_REEL_GAIN_RATIO;
    if (gain < floor) gain = floor;
    const stalled = gain <= 0.0001;
    if (stalled) fail++;
    const show = (l.name === '钛合金线' || f.name === '幼鲨(红)');
    if (show) {
      console.log('  ' + pad(f.name, 19) + pad(l.name, 12)
        + pad(ceil.toFixed(0), 7) + pad(raw.toFixed(3), 14)
        + pad(gain.toFixed(3), 14) + (stalled ? '❌ 卡住' : '✅ 会涨'));
    }
  }
}

console.log('\n=== ② 中立（没在收线）时的回落 —— 张力必须**永远在落** ===\n');
console.log('  鱼种               鱼线        上限   鱼拉/点tick  回落/点tick  实际净变化  判定');
console.log('  ' + '-'.repeat(96));
for (const f of FISH) {
  for (const l of LINE) {
    const ceil = composeCeiling(ROD.ceil, l.ceil);
    const rec = composeRecover(ROD.rec, l.rec);
    const recoverMult = rec / BASE_RECOVER;
    const fall = IDLE_LOSS * recoverMult;
    const fishT = PULL_IDLE * f.pull * TIER_SPEED(f.tier) * f.gain * l.gen;
    // 照抄新实现：鱼拉被限制在回落的 MAX_FISH_SLOWDOWN 倍以内
    const capped = Math.min(fishT, fall * MAX_FISH_SLOWDOWN);
    const net = capped - fall;      // 恒为负
    const show = (l.name === '钛合金线' || f.name === '幼鲨(红)');
    if (show) {
      console.log('  ' + pad(f.name, 19) + pad(l.name, 12)
        + pad(ceil.toFixed(0), 7) + pad(fishT.toFixed(3), 14)
        + pad(fall.toFixed(3), 14)
        + pad(net.toFixed(3), 13)
        + (net < 0 ? '✅ 在落' : '❌ 卡住'));
    }
  }
}
console.log('\n  修复前这里有一批组合的净变化恰好是 0（鱼拉 ≥ 回落，被夹紧到 0）——');
console.log('  张力纹丝不动，玩家看到的就是"条子卡在中间"。现在最慢也保留 10% 净回落。');

console.log('\n=== 断言 ===');
assert(MIN_REEL_GAIN_RATIO > 0 && MIN_REEL_GAIN_RATIO < 1,
  'MIN_REEL_GAIN_RATIO 在 (0,1) 之间：鱼拉能削减涨势、但不能抵消（当前 '
  + MIN_REEL_GAIN_RATIO + '）');

// 收线时张力恒增
let anyStall = 0;
for (const f of FISH) {
  for (const l of LINE) {
    const delta = REEL_GAIN * l.gen;
    const fishT = PULL_IDLE * f.pull * TIER_SPEED(f.tier) * f.gain * l.gen;
    const gain = Math.max(delta + fishT, delta * MIN_REEL_GAIN_RATIO);
    if (gain <= 0.0001) anyStall++;
  }
}
assert(anyStall === 0, '所有鱼种 × 鱼线组合下，收线时张力涨速都 > 0（共检查 '
  + (FISH.length * LINE.length) + ' 组）');

// 中立时张力必须**真的在落**（净变化严格为负），不能只是"不上升"
let anyStuck = 0;
for (const f of FISH) {
  for (const l of LINE) {
    const rec = composeRecover(ROD.rec, l.rec);
    const fall = IDLE_LOSS * (rec / BASE_RECOVER);
    const fishT = PULL_IDLE * f.pull * TIER_SPEED(f.tier) * f.gain * l.gen;
    const net = Math.min(fishT, fall * MAX_FISH_SLOWDOWN) - fall;
    if (net >= -1e-9) anyStuck++;
  }
}
assert(anyStuck === 0, '中立时张力净变化**严格为负**（永远在落，不会卡住）——'
  + '共检查 ' + (FISH.length * LINE.length) + ' 组，卡住 ' + anyStuck + ' 组');
assert(MAX_FISH_SLOWDOWN < 1.0, 'maxFishSlowdown < 1：鱼拉不能完全抵消回落（当前 '
  + MAX_FISH_SLOWDOWN + '）');

// 输入链路：客户端不节流、服务端容忍足够
const client = fs.readFileSync(path.join(ROOT, 'src', 'main', 'java', 'com', 'whitecobalt',
  'anglertackle', 'client', 'ClientFishingState.java'), 'utf8');
const strip = (t) => t.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
assert(!/inputCooldown\s*=\s*\d+/.test(strip(client)), '客户端不再节流输入包（每 tick 都发）');
// ⚠️ 容忍阈值现在住在配置里（[gameplay] inputStaleLimit），源码是 Tuning.inputStaleLimit()。
// 断言改成"配置里有这个值 + 代码真的用它"，而不是 grep 常量定义。
const staleLimit = require('./tuning.js').cfg.inputStaleLimit;
const usesTuning = /Tuning\.inputStaleLimit\(\)/.test(strip(sessSrc));
assert(typeof staleLimit === 'number' && staleLimit >= 3 && usesTuning,
  '服务端容忍 ' + staleLimit + ' tick（可配），且代码走 Tuning.inputStaleLimit()，余量充足');

// ================================================================ ③ 距离拉锯
// 用户 2026-10-06 反馈："有时候鱼即使力竭了但依然收线回拉不过来，收不收都一个游速。"
//
// 上面两节证明的是"张力条会动"，这一节证明的是**线收得回来**：
//   距离结算 = 鱼拽 − 收线（FishingSession 第 6 节）
//   鱼拽（挣扎）= 鱼拽系数 × pullSpeed × tierSpeed(档) × (疲劳 ? 疲劳倍率 : 1)
//   收线        = 收线系数 × 渔轮 pullStruggle（休息时用 pullIdle）
// 只要"收线 < 力竭鱼的鱼拽"，玩家看到的就是"它都力竭了，线还在往外走" ——
// 一个纯数值上的死角，不崩不报错，只有玩的人能感觉到。
//
// 两条方向相反的断言，缺一条都不行：
//   · 力竭时：收线**一定**回收（保底 fatiguedReelMinGain，与轮子好坏无关）
//   · 没力竭时：至少存在"鱼拽得过轮子"的组合（否则拉锯被抹平，玩法没了）
console.log('\n=== ③ 距离拉锯：力竭的鱼必须收得回来 ===\n');

const fishSrc2 = fs.readFileSync(path.join(ROOT, 'src', 'main', 'java', 'com',
  'whitecobalt', 'anglertackle', 'fishing', 'FishSpecies.java'), 'utf8');
const itemsSrc = fs.readFileSync(path.join(ROOT, 'src', 'main', 'java', 'com',
  'whitecobalt', 'anglertackle', 'registry', 'ModItems.java'), 'utf8');

/** 从 FishingSession 源码里读距离公式的系数；读不到就抛错，绝不静默沿用旧值。 */
function distConst(label, re) {
  const m = re.exec(sessSrc);
  if (!m) throw new Error('距离公式变了：读不到 ' + label + '，请更新 check-reel-stall.js 的正则');
  return Number(m[1]);
}
const FISH_OUT = distConst('挣扎时的鱼拽系数',
  /([\d.]+)\s*\*\s*this\.profile\.pullSpeed\(\)\s*\*\s*this\.tierSpeed/);
const REEL_COEF = distConst('收线系数', /double reelSpeed\s*=\s*([\d.]+)\s*\*\s*pullMult/);
const FLOOR_WIRED = /distDelta\s*=\s*Math\.min\(distDelta,\s*-Tuning\.fatiguedReelMinGain\(\)\)/.test(sessSrc);
const SPRINT_GATED = /if\s*\(this\.fishStruggling\s*&&\s*!this\.fatigued\)/.test(sessSrc);
const FATIGUE_MULT = __gameplay('fatiguePullMultiplier');
const REEL_FLOOR = __gameplay('fatiguedReelMinGain');

console.log('  鱼拽系数 ×' + FISH_OUT + ' / 收线系数 ×' + REEL_COEF
  + ' / 疲劳倍率 ×' + FATIGUE_MULT + ' / 保底 ' + REEL_FLOOR + ' 格每tick');
console.log('  力竭保底接线：' + (FLOOR_WIRED ? '是' : '**否**')
  + '；力竭不冲刺：' + (SPRINT_GATED ? '是' : '**否**') + '\n');

// 全部鱼种：new FishSpecies("id", ModItems.X, 最小, 最大, 档位表, 策略(pull, 体力, 张力))
const ALL_FISH = [...fishSrc2.matchAll(
  /new FishSpecies\(\s*"(\w+)",[\s\S]{0,220}?(?:steady|sluggish|burst|erratic)\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)/g)]
  .map((m) => ({ id: m[1], pull: Number(m[2]), stamina: Number(m[3]), gain: Number(m[4]) }));
if (ALL_FISH.length < 25) {
  throw new Error('鱼种只解析出 ' + ALL_FISH.length + ' 条（应 28 条）—— check-reel-stall.js 的正则要更新');
}
// 全部渔轮：PartStats.reel(pullIdle, pullStruggle, drain, damping, tier)
const ALL_REELS = [...itemsSrc.matchAll(
  /PartStats\.reel\(\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*([\d.]+),\s*(\d)\)/g)]
  .map((m) => ({ idle: Number(m[1]), struggle: Number(m[2]), tier: Number(m[5]) }));
if (ALL_REELS.length < 5) {
  throw new Error('渔轮只解析出 ' + ALL_REELS.length + ' 个（应 5 个）—— 正则要更新');
}
console.log('  扫描 ' + ALL_FISH.length + ' 条鱼 × 2 个高档位 × ' + ALL_REELS.length
  + ' 个渔轮，外加 3 个"磨损到近乎没回拉"的轮子\n');

// 磨损：见底配件会被降级（RodStats.of），实际 pullStruggle 可以低到接近 0。
const REEL_CASES = ALL_REELS.map((r) => ({ name: '轮' + r.tier, v: Math.min(r.struggle, TUNE.CAP_PULL) }))
  .concat([{ name: '磨损', v: 0.60 }, { name: '严重磨损', v: 0.30 }, { name: '见底', v: 0.0 }]);

let wouldStall = 0;        // 没有保底就会"力竭也拉不动"的组合
let stallWorst = null;
let freshOutpull = 0;      // 没力竭时鱼拽得过轮子的组合（玩法还在）
let tiredAlwaysRecovers = 0, tiredTotal = 0;
for (const f of ALL_FISH) {
  for (const tier of [4, 5]) {
    const outFresh = FISH_OUT * f.pull * TUNE.tierSpeed(tier);
    const outTired = outFresh * FATIGUE_MULT;
    for (const reel of REEL_CASES) {
      const reelSpeed = REEL_COEF * reel.v;
      const rawTired = outTired - reelSpeed;             // 没有保底时的净变化
      const withFloor = FLOOR_WIRED ? Math.min(rawTired, -REEL_FLOOR) : rawTired;
      tiredTotal++;
      if (rawTired > 0) {
        wouldStall++;
        if (!stallWorst || rawTired > stallWorst.net) {
          stallWorst = { id: f.id, tier, reel: reel.name, net: rawTired, pull: f.pull };
        }
      }
      if (withFloor <= -REEL_FLOOR + 1e-9) tiredAlwaysRecovers++;
      if (outFresh - reelSpeed > 0) freshOutpull++;
    }
  }
}
if (stallWorst) {
  console.log('  没有保底时会"力竭也拉不动"的最差组合：'
    + stallWorst.id + '（pullSpeed ' + stallWorst.pull + '）红金档 ' + stallWorst.tier
    + ' + ' + stallWorst.reel + ' → 线每 tick 还在往外走 ' + stallWorst.net.toFixed(4) + ' 格');
  console.log('  （共 ' + wouldStall + ' / ' + tiredTotal + ' 组；正是用户遇到的那种"收不收都一个游速"）\n');
}

assert(FLOOR_WIRED, '力竭期收线保底已接线：distance 的净变化被 clamp 到 −Tuning.fatiguedReelMinGain()');
assert(REEL_FLOOR > 0, 'fatiguedReelMinGain > 0（当前 ' + REEL_FLOOR
  + ' 格/tick ≈ ' + (REEL_FLOOR * 20).toFixed(1) + ' 格/秒；0 = 保底失效）');
assert(tiredAlwaysRecovers === tiredTotal,
  '力竭 + 收线：全部 ' + tiredTotal + ' 组都**一定回收**（含见底轮子）—— 实得 '
  + tiredAlwaysRecovers + ' 组');
assert(wouldStall > 0,
  '保底不是装饰：确实有 ' + wouldStall + ' 组"没有它就力竭也拉不动"（最差 +'
  + (stallWorst ? stallWorst.net.toFixed(4) : '?') + ' 格/tick）');
assert(freshOutpull > 0,
  '没力竭时鱼仍能拽得过轮子（' + freshOutpull + ' / ' + tiredTotal
  + ' 组）—— 拉锯没被保底抹平，"等它力竭"依然是策略');
assert(SPRINT_GATED,
  '力竭的鱼不再冲刺（冲刺期间疲劳不能续期，会让"力竭窗口"碎成一段一段）');


// ================================================================ ④ 力竭期的张力修正
// 用户 2026-10-06："加一个鱼力竭时张力涨速再乘 0.85，张力回复再乘 1.15。"
//
// 力竭是"反拉的最佳窗口"，这段时间线也该更安全：**涨得更慢、落得更快**。
// 注意它是"**再**乘"——控杆关系那边本来就有一套力竭 ×0.80（tensionMult），
// 所以力竭期收线的实际涨速是 0.80 × 0.85 = 0.68（同向控杆对比非力竭）。
//
// 断言两件相反的事：力竭时涨速**严格更慢**、回落**严格更快**，
// 而且两个方向都不能过头（收线仍然必须涨、中立仍然必须落）。
console.log('\n=== ④ 力竭期的张力修正（涨速 ×0.85、回复 ×1.15）===\n');

const FAT_GAIN = __gameplay('fatiguedTensionGainScale');
const FAT_REC = __gameplay('fatiguedTensionRecoverScale');
const GAIN_WIRED = /double fatigueGainScale = this\.fatigued \? Tuning\.fatiguedTensionGainScale\(\) : 1\.0;/.test(sessSrc);
const GAIN_USED = /gain \*= fatigueGainScale;/.test(sessSrc);
const REC_WIRED = /if \(this\.fatigued\) \{\s*recoverMult \*= Tuning\.fatiguedTensionRecoverScale\(\);\s*\}/.test(sessSrc);
const BASE_DELTA_WIRED = /this\.lastTensionDelta = this\.tension - beforeTension;/.test(sessSrc);

let riseNotSlower = 0, riseNonPositive = 0, fallNotFaster = 0, fallStalled = 0, swept = 0;
let worstRise = null;
for (const f of ALL_FISH) {
  for (const tier of [0, 3, 4, 5]) {
    const ts = TUNE.tierSpeed(tier);
    for (const l of LINE) {
      // 中立控杆（tensionMult = 1.0）—— 最保守的取值；正拉 ×0.5 / 反拉 ×1.4 只是等比缩放
      const delta = REEL_GAIN * 1.0 * l.gen;
      const fishT = PULL_IDLE * f.pull * ts * f.gain * l.gen;
      const riseNormal = Math.max(delta + fishT, delta * MIN_REEL_GAIN_RATIO);
      const riseFatigued = riseNormal * FAT_GAIN;
      const fallNormal = IDLE_LOSS * (composeRecover(ROD.rec, l.rec) / BASE_RECOVER);
      const fallFatigued = fallNormal * FAT_REC;
      // 中立时的净变化：鱼的拉力只能把回落削到 90%，永远为负
      const netFatigued = Math.min(fishT, fallFatigued * MAX_FISH_SLOWDOWN) - fallFatigued;
      swept++;
      if (!(riseFatigued > 0)) riseNonPositive++;
      if (!(riseFatigued < riseNormal - 1e-12)) riseNotSlower++;
      if (!(fallFatigued > fallNormal + 1e-12)) fallNotFaster++;
      if (!(netFatigued < 0)) fallStalled++;
      if (!worstRise || riseFatigued > worstRise.rise) {
        worstRise = { id: f.id, tier, line: l.name, rise: riseFatigued };
      }
    }
  }
}
console.log('  力竭涨速 ×' + FAT_GAIN + '（在控杆 ×0.80 之上）；力竭回复 ×' + FAT_REC);
console.log('  扫描 ' + ALL_FISH.length + ' 条鱼 × 4 个档位 × ' + LINE.length + ' 条线 = ' + swept + ' 组');
console.log('  力竭时最猛的收线涨速：' + worstRise.id + ' 档' + worstRise.tier + ' + ' + worstRise.line
  + ' → ' + worstRise.rise.toFixed(4) + ' 点/tick（仍为正 = 条子还在涨）\n');

assert(FAT_GAIN === 0.7, '力竭涨速倍率 = 0.7（当前 ' + FAT_GAIN + '）');
assert(FAT_REC === 1.35, '力竭回复倍率 = 1.35（当前 ' + FAT_REC + '）');
// ★ 方向断言（用户 2026-10-06 反问过"是不是弄反了"）：
//   涨速倍率必须 **< 1**（变红更慢）、回复倍率必须 **> 1**（回白更快）。
//   光断言 == 0.85 / == 1.15 只能挡住"数改了"，挡不住"接线接反"或"某个值调过头"——
//   把 0.85 写成 1.15 就成了"力竭时线涨得更凶"，那是玩家一定会踩到的反向事故。
assert(FAT_GAIN < 1.0 && FAT_REC > 1.0,
  '方向正确：变红速度 ×' + FAT_GAIN + '（<1 = 更难变红）、回白速度 ×' + FAT_REC
  + '（>1 = 更快回白）—— 两个数反了就是"力竭反而更危险"');
assert(GAIN_WIRED && GAIN_USED, '涨速修正已接线：fatigueGainScale 由 fatigued 决定，并乘在收线 gain 上');
assert(REC_WIRED, '回复修正已接线：力竭时 recoverMult ×Tuning.fatiguedTensionRecoverScale()');
assert(BASE_DELTA_WIRED,
  '张力轨迹的"净变化"取在结算**之前**（beforeTension）—— 否则那一列恒为 0，"僵持"计数全是假的');
assert(riseNotSlower === 0, '力竭时收线涨速**严格更慢**（' + swept + ' 组全部通过）');
assert(riseNonPositive === 0, '力竭时收线涨速仍然 > 0（条子还在涨，共 ' + swept + ' 组）');
assert(fallNotFaster === 0, '力竭时中立/放线回落**严格更快**（共 ' + swept + ' 组）');
assert(fallStalled === 0, '力竭时中立仍然**永远在落**（净变化严格为负，共 ' + swept + ' 组）');

// ---- ④b 力竭**什么时候开**：全局阈值倍率（用户 2026-10-06："实际感觉力竭更难拉了"）----
// 查那一局日志：31 条张力算式全是"力竭涨速×1.00" —— 鱼**一次都没进疲劳**。
// 根因是红档体力池 ×1.5 把"进疲劳的比例"落在 1.5 倍的绝对值上
// （450 池 × burst 0.34 = 要扣到 153 才亮【疲劳·快拉】）。
// 这条旋钮让窗口早晚可调，池子厚度不用动。
const TH_SCALE = __gameplay('fatigueThresholdScale');
const TH_HELPER = /private double fatigueThresholdNow\(\)\s*\{\s*return Math\.min\(1\.0,\s*this\.profile\.fatigueThreshold\(\)\s*\*\s*Tuning\.fatigueThresholdScale\(\)\);\s*\}/.test(sessSrc);
const TH_USED = /this\.fishStamina <= this\.fishStaminaMax \* fatigueThresholdNow\(\)/.test(sessSrc);
// 四个策略工厂里 new FightProfile(...) 的第 10 个参数就是 fatigueThreshold（0 起算 index 9）
const arcThreshold = {};
for (const m of fishSrc2.matchAll(
  /private static FightProfile (\w+)\(double pull, double stamina, double tensionGain(?:, double \w+)?\)\s*\{\s*return new FightProfile\(([\s\S]*?)\);\s*\}/g)) {
  const args = m[2].replace(/\/\/[^\n]*/g, '').split(',').map((s) => s.trim());
  arcThreshold[m[1]] = Number(args[9]);
}
const ARC_NAMES = Object.keys(arcThreshold);
console.log('\n  力竭阈值：' + ARC_NAMES.map((k) => k + ' ' + arcThreshold[k]).join(' / ')
  + '；全局倍率 ' + TH_SCALE);
for (const sc of [1.0, 1.25, 2.0]) {
  const shown = ARC_NAMES.map((k) => k + ' ' + Math.min(1.0, arcThreshold[k] * sc).toFixed(3)).join(' / ');
  console.log('    ×' + sc + ' → ' + shown);
}
assert(TH_SCALE > 0, '全局力竭阈值倍率 > 0（当前 ' + TH_SCALE + '；1.0 = 与鱼种值一致）');
assert(ARC_NAMES.length === 4 && ARC_NAMES.every((k) => arcThreshold[k] > 0 && arcThreshold[k] < 1),
  '四个策略的 fatigueThreshold 都解析出来了且在 (0,1)：' + ARC_NAMES.map((k) => k + '=' + arcThreshold[k]).join(', '));
assert(TH_HELPER && TH_USED,
  '阈值倍率已接线：fatigueThresholdNow() = min(1.0, 鱼种阈值 × Tuning.fatigueThresholdScale())，且进疲劳判定读它');
assert(ARC_NAMES.every((k) => Math.min(1.0, arcThreshold[k] * 2.0) <= 1.0),
  '倍率拉到配置上限 2.0 也不会超过 1.0（不会变成"一咬钩就力竭"；最高的一组 = '
  + Math.max(...ARC_NAMES.map((k) => Math.min(1.0, arcThreshold[k] * 2.0))).toFixed(2) + '）');

// ---- ⑤ 中立时的**最小净回落**（用户 2026-10-06："现在感觉张力下降好慢，所有时候"）----
// 症状的数值：他那局（铂金银龙鱼 红档，日志原文）鱼拉 1.650 vs 中立回落 0.51
//   → 净回落只有 0.05 点/tick ≈ **1 点/秒**。根因是"鱼拉最多把回落削到 90%"
//   是**按比例**削的：调 idleLoss 几乎没用（回落涨多少，鱼就多削多少）。
// 修法：给中立加一条**绝对下限**，与鱼多猛、装备好坏都无关。
console.log('\n=== ⑤ 中立的最小净回落（张力"下降好慢"）===\n');

const IDLE_FLOOR = __gameplay('idleFallFloor');
const FLOOR_NEUTRAL = /if \(!this\.releasing\) \{\s*net = Math\.min\(net, -Tuning\.idleFallFloor\(\)\);\s*\}/.test(sessSrc);
const FLOOR_USES_NET = /double net = cappedFish - fall;/.test(sessSrc);

let neutralTooSlow = 0, neutralNotFalling = 0, floorNeeded = 0, neutralSwept = 0;
let worstNeutral = null;
for (const f of ALL_FISH) {
  for (const tier of [0, 3, 4, 5]) {
    const ts = TUNE.tierSpeed(tier);
    for (const l of LINE) {
      for (const fat of [false, true]) {
        const fall = IDLE_LOSS * (composeRecover(ROD.rec, l.rec) / BASE_RECOVER) * (fat ? FAT_REC : 1.0);
        const fishT = PULL_IDLE * f.pull * ts * f.gain * l.gen;
        const raw = Math.min(fishT, fall * MAX_FISH_SLOWDOWN) - fall;   // 修之前
        const eff = FLOOR_NEUTRAL ? Math.min(raw, -IDLE_FLOOR) : raw;    // 修之后
        neutralSwept++;
        if (raw > -IDLE_FLOOR) {
          floorNeeded++;
          if (!worstNeutral || raw > worstNeutral.raw) {
            worstNeutral = { id: f.id, tier, line: l.name, raw, fat, pull: f.pull };
          }
        }
        if (eff > -IDLE_FLOOR + 1e-9) neutralTooSlow++;
        if (!(eff < 0)) neutralNotFalling++;
      }
    }
  }
}
if (worstNeutral) {
  console.log('  没有下限时最慢的中立回落：' + worstNeutral.id + '（pull ' + worstNeutral.pull
    + '）档' + worstNeutral.tier + ' + ' + worstNeutral.line + (worstNeutral.fat ? '（力竭）' : '')
    + ' → ' + worstNeutral.raw.toFixed(4) + ' 点/tick ≈ ' + (worstNeutral.raw * 20).toFixed(2) + ' 点/秒');
  console.log('  （共 ' + floorNeeded + ' / ' + neutralSwept + ' 组慢于下限 —— 用户遇到的就是这一档）');
  console.log('   ⚠ 扫描刻意包含"该鱼种其实到不了这一档"的组合，所以上面是**上界**；真实组合只会更快\n');
}

assert(IDLE_FLOOR > 0, '中立最小净回落 > 0（当前 ' + IDLE_FLOOR + ' 点/tick ≈ '
  + (IDLE_FLOOR * 20).toFixed(1) + ' 点/秒；0 = 关掉这条保底）');
assert(FLOOR_USES_NET && FLOOR_NEUTRAL,
  '下限已接线：中立分支算出 net 后夹到 −Tuning.idleFallFloor()，且**只在中立**（放线不受影响）');
assert(neutralTooSlow === 0, '中立时全部 ' + neutralSwept + ' 组的净回落都不慢于 −'
  + IDLE_FLOOR + ' 点/tick（与鱼多猛、装备好坏无关）—— 实得 ' + (neutralSwept - neutralTooSlow) + ' 组达标');
assert(neutralNotFalling === 0, '中立时仍然**永远在落**（净变化严格为负，共 ' + neutralSwept + ' 组）');
assert(floorNeeded > 0 && worstNeutral,
  '这条下限不是装饰：确实有 ' + floorNeeded + ' 组"没有它就慢到 1 点/秒"');

// ---- ⑥ 保底挣扎（用户 2026-10-06）----
// 用户原话："现有的疲劳体系让红金等鱼过于容易拉上来，就是只要拉到一次力竭之后这条鱼
//   即使再进入挣扎也不会超过一秒。我需要增加一个保底挣扎系数，**注意我说的挣扎指的是
//   鱼不力竭的状态**。该系数决定一条鱼保底挣扎的秒数，1 代表一秒。根据鱼的档位这个系数
//   增加，白 1 红 6 基准，再根据鱼的策略向上至多浮动 1，无向下浮动。"
// 落地：每进入一轮挣扎，把疲劳清掉并给一个"不许力竭"的倒计时；倒计时归零前疲劳点不亮。
console.log('\n=== ⑥ 保底挣扎（白 1 … 红 6 + 策略上浮 ≤1）===\n');

const FS_BASE = __gameplay('floorStruggleBase');
const FS_PER = __gameplay('floorStrugglePerTier');
const FS_BONUS = {
  STEADY: __gameplay('floorStruggleBonusSteady'),
  SLUGGISH: __gameplay('floorStruggleBonusSluggish'),
  BURST: __gameplay('floorStruggleBonusBurst'),
  ERRATIC: __gameplay('floorStruggleBonusErratic'),
};
const fishSrc3 = fishSrc2;   // 这个文件里的正则都在**原始源码**上跑（不剥注释），⑥ 沿用同一口径
const FLOOR_FN = /public static double floorStruggleSeconds\(int tier, FightProfile\.Archetype arch\)/.test(fishSrc3);
const FLOOR_SET = /this\.freshStruggleTicks = \(int\) Math\.round\(FishSpecies\.floorStruggleSeconds\(\s*this\.fishTier, this\.profile\.archetype\(\)\) \* 20\.0\);/.test(sessSrc);
const FATIGUE_CLEARED = /this\.freshStruggleTicks = \(int\) Math\.round\([\s\S]{0,240}?this\.fatigued = false;\s*this\.fatigueTicks = 0;/.test(sessSrc);
const FLOOR_GATED = /this\.freshStruggleTicks <= 0\s*&&\s*(?:this\.fatigueLockoutTicks <= 0\s*&&\s*)?!this\.sprinting/.test(sessSrc);
const FLOOR_TICKED = /if \(this\.freshStruggleTicks > 0\) \{\s*this\.freshStruggleTicks--;\s*\}/.test(sessSrc);

const fsFloor = (tier, arch) => FS_BASE + Math.max(0, Math.min(5, tier)) * FS_PER
  + Math.max(0.0, Math.min(1.0, FS_BONUS[arch]));
console.log('  ' + pad('档位', 8) + ['STEADY', 'SLUGGISH', 'BURST', 'ERRATIC'].map((k) => pad(k, 10)).join(''));
for (const tier of [0, 2, 4, 5]) {
  console.log('  ' + pad(tier, 8)
    + ['STEADY', 'SLUGGISH', 'BURST', 'ERRATIC'].map((k) => pad(fsFloor(tier, k).toFixed(2) + 's', 10)).join(''));
}
console.log('');

assert(FS_BASE === 1.0 && FS_PER === 1.0,
  '白档基准 = 1 秒、每档 +1 秒（当前 ' + FS_BASE + ' / ' + FS_PER + '）→ 红档 = 6 秒');
assert(Math.abs(fsFloor(0, 'STEADY') - 1.0) < 1e-9 && Math.abs(fsFloor(5, 'STEADY') - 6.0) < 1e-9,
  '"白 1 红 6"逐点复算通过（无策略加成时）');
const bonusOk = Object.values(FS_BONUS).every((b) => b >= 0 && b <= 1);
assert(bonusOk, '四个策略加成都在 [0,1] —— **只向上浮动、最多 1**（'
  + Object.entries(FS_BONUS).map(([k, v]) => k + ' ' + v).join(' / ') + '）');
assert(FLOOR_FN && FLOOR_SET && FATIGUE_CLEARED,
  '接线：轮次开头设 freshStruggleTicks，并把疲劳标记清掉（否则上一轮的"软"会带到这一轮）');
assert(FLOOR_GATED, '疲劳点亮的条件里带了 freshStruggleTicks <= 0（保底窗口内不许力竭）');
assert(FLOOR_TICKED, 'freshStruggleTicks 每 tick 递减（不然保底永不结束）');

// ---- ⑦ 收线速率抵抗（用户 2026-10-06）----
// "收线速率高于 1.2 的配置，各档鱼有不同抵抗：白 5% 绿 10% 蓝 15% 紫 20% 金 25% 红 32%；
//  策略上下浮动至多 5%；压制阻尼最多抵消 75%；让金轮大致触碰到转化极限；取整数 0.05。"
console.log('\n=== ⑦ 收线速率抵抗（含阻尼抵消，金轮贴 75% 极限）===\n');

const RR = {
  threshold: __gameplay('reelResistThreshold'),
  tiers: [__gameplay('reelResistTierWhite'), __gameplay('reelResistTierGreen'), __gameplay('reelResistTierBlue'),
    __gameplay('reelResistTierPurple'), __gameplay('reelResistTierGold'), __gameplay('reelResistTierRed')],
  strat: [__gameplay('reelResistStratSteady'), __gameplay('reelResistStratSluggish'),
    __gameplay('reelResistStratBurst'), __gameplay('reelResistStratErratic')],
  offsetPer: __gameplay('reelResistDampingOffset'),
  offsetCap: __gameplay('reelResistDampingCap'),
};
// ⚠ 2026-10-06 用户把这几个值**定成了新默认**（原话："固定现在的数值为新默认"）：
//   绿/蓝/紫/金/红 从 10/15/20/25/32% 调成 **12.5 / 18.5 / 27.5 / 55 / 75%**（白档 5% 不动）。
//   这条断言守的是"默认值不许被静默改掉"，所以定值一变就得同步改这里 —— 这正是它存在的意义。
const RR_TIER_WANT = [0.05, 0.125, 0.185, 0.275, 0.55, 0.75];
const RR_WIRED = /double pullResisted = FishSpecies\.resistReelSpeed\(pullBase, reelResistNow\);/.test(sessSrc)
  && /FishSpecies\.reelResist\(\s*this\.fishTier, this\.profile\.archetype\(\), this\.rodStats\.damping\(\)\)/.test(sessSrc);
const rrOf = (tier, strat, damping) => {
  const base = RR.tiers[tier];
  const s = Math.max(-0.05, Math.min(0.05, strat));
  const off = Math.min(RR.offsetCap, Math.max(0.0, (damping - 1.0) * RR.offsetPer));
  return base * (1 + s) * (1 - off);
};
const rrSpeed = (pull, resist) => pull <= RR.threshold ? pull
  : RR.threshold + (pull - RR.threshold) * Math.max(0, 1 - resist);

console.log('  档位   基准抵抗   水滴轮(1.40)实抗   金轮(2.50)实抗   水滴轮 1.98 收线 →   金轮 1.98 收线 →');
console.log('  ' + '-'.repeat(88));
for (const t of [0, 3, 4, 5]) {
  console.log('  ' + pad(t, 7) + pad((RR.tiers[t] * 100).toFixed(0) + '%', 11)
    + pad((rrOf(t, 0, 1.40) * 100).toFixed(1) + '%', 18)
    + pad((rrOf(t, 0, 2.50) * 100).toFixed(1) + '%', 17)
    + pad(rrSpeed(1.98, rrOf(t, 0, 1.40)).toFixed(3), 22)
    + rrSpeed(1.98, rrOf(t, 0, 2.50)).toFixed(3));
}
console.log('');

assert(JSON.stringify(RR.tiers) === JSON.stringify(RR_TIER_WANT),
  '六档基准抵抗 = 用户定值 5/12.5/18.5/27.5/55/75%（当前 '
  + RR.tiers.map((v) => (v * 100).toFixed(1) + '%').join('/') + '）');
assert(Math.abs(RR.threshold - 1.2) < 1e-9, '起效阈值 = 1.2（当前 ' + RR.threshold + '）');
assert(RR.strat.every((s) => s >= -0.05 - 1e-9 && s <= 0.05 + 1e-9),
  '四个策略浮动都在 ±5% 以内（' + RR.strat.join(' / ') + '）');
assert(Math.abs(Math.min(RR.offsetCap, (2.50 - 1.0) * RR.offsetPer) - RR.offsetCap) < 1e-9,
  '金轮（阻尼 2.50）的阻尼抵消 = ' + ((2.50 - 1.0) * RR.offsetPer).toFixed(2)
  + ' **正好贴住上限** ' + RR.offsetCap + ' —— 这就是"×' + RR.offsetPer + '"倒推的依据');
// 用户要求"取整数 0.05"：指的是**我这边定的系数**（阻尼抵消系数等）。
// ⚠ 六档基准抵抗里的红档 32% 是用户**点名给的数**，它本身不在 0.05 网格上
//   （0.32/0.05 = 6.4）—— 点名数优先于取整偏好，所以只对系数做网格断言，不碰它。
const on05 = (v) => Math.abs(v / 0.05 - Math.round(v / 0.05)) < 1e-9;
assert(on05(RR.offsetPer) && RR.strat.every(on05) && on05(RR.threshold) && on05(RR.offsetCap),
  '我这边定的系数都落在 0.05 网格上（阻尼抵消 ×' + RR.offsetPer + '、上限 ' + RR.offsetCap
  + '、阈值 ' + RR.threshold + '）');
assert(RR_WIRED, '接线：收线速率先过 FishSpecies.reelResist(...)，再过 resistReelSpeed(阈值以上打折)');
// 连续性：阈值处不跳变；且收线速率越高打完折仍然越高（单调）
let rrMonotone = true, rrJump = 0;
for (let p = 1.0; p <= 2.2; p += 0.01) {
  const a = rrSpeed(p, 0.32), b = rrSpeed(p + 0.01, 0.32);
  if (b < a - 1e-12) rrMonotone = false;
}
rrJump = Math.abs(rrSpeed(1.2, 0.32) - 1.2);
assert(rrMonotone && rrJump < 1e-12,
  '阈值处**不跳变**、且整体单调（只打超过 1.2 的那部分）—— 否则 1.21 会比 1.19 还慢');
// ⚠ 上面那条用的是**JS 模型**，它抓不到"Java 被改成整条打折"（模型没变、只有源码变了）——
//   所以必须再钉一次**源码结构**：阈值以下原样返回 + 只对超出段打折。
const RR_BODY = /public static double resistReelSpeed\(double pullSpeed, double resist\)\s*\{[\s\S]{0,400}?if \(pullSpeed <= threshold\)\s*\{\s*return pullSpeed;\s*\}[\s\S]{0,300}?return threshold \+ \(pullSpeed - threshold\)/.test(fishSrc3);
assert(RR_BODY,
  'Java 的 resistReelSpeed 确实是"阈值以下原样 + 只对超出段打折"（不是整条打折）');

console.log('\n' + (fail === 0 ? '全部通过' : fail + ' 项未通过'));
process.exitCode = fail === 0 ? 0 : 1;
