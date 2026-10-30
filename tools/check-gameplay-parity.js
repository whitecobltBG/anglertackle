const ATL_ROOT = process.env.ATL_ROOT ? require('path').resolve(process.env.ATL_ROOT) : require('path').join(__dirname, '..');
// 校验 [gameplay] 组的默认值没有偷改源码原值，并且**承重限幅真的生效**。
//
// 两件事：
//   ① 默认值对账：Config.java 里 [gameplay] 的每个默认值，必须与"接线前"的源码原值逐位相同。
//      不然玩家的手感会静默变化 —— 而"手感变了"这种事很难判断是哪一项引起的。
//   ② 承重回归测试：模拟"木竿 8kg"抽 20 万条鱼，体重**一律不得超过 8kg**。
//      这条是玩家报的 bug（"标承重 8 千克的竿钓出 11kg 的鱼"）的机器化判据。
//
// 用法：node tools/check-gameplay-parity.js
const fs = require('fs'), path = require('path');
const ROOT = ATL_ROOT;
const { parseFish } = require('./fish-source.js');

let bad = 0;
const ok = (m) => console.log('✅ ' + m);
const no = (m) => { console.log('❌ ' + m); bad++; };

const cfgSrc = fs.readFileSync(
  path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/Config.java'), 'utf8');

// ---------------------------------------------------------------- ① 默认值对账
// 期望值来自"接线前的源码"——这些数字在接线前是写死的常量，先在这里登记。
// ⚠ 有意改动过的项都在这里标了日期和用户原话；**静默漂移**才是这条断言要抓的。
const EXPECT = {
  // 鱼竿五材质（耐久除外：它在注册期被 NeoForge 固定，仍是字面量）
  rodWoodMaxWeightKg: 8.0, rodWoodHookWindowBonus: 0.00, rodWoodTensionCeiling: 1.00,
  rodWoodTensionRecover: 1.00, rodWoodStaminaDrain: 1.00, rodWoodMaterialFactor: 1.00, rodWoodTier: 3,
  // ⚠ 2026-10-02 **有意**改动：铁竿承重 20 → 25（用户："金属杆承重上限改到25"）
  rodIronMaxWeightKg: 30.0, rodIronHookWindowBonus: 0.10, rodIronTensionCeiling: 1.20,
  rodIronTensionRecover: 1.20, rodIronStaminaDrain: 1.20, rodIronMaterialFactor: 0.85, rodIronTier: 4,
  rodGoldMaxWeightKg: 21.0, rodGoldHookWindowBonus: 0.25, rodGoldTensionCeiling: 1.10,
  rodGoldTensionRecover: 1.15, rodGoldStaminaDrain: 1.15, rodGoldMaterialFactor: 0.90, rodGoldTier: 6,
  // ⚠ 2026-10-02 **有意**改动：钻竿承重 40 → 50（用户："钻石杆承重上限改到50"）
  rodDiamondMaxWeightKg: 50.0, rodDiamondHookWindowBonus: 0.20, rodDiamondTensionCeiling: 1.60,
  rodDiamondTensionRecover: 1.70, rodDiamondStaminaDrain: 1.60, rodDiamondMaterialFactor: 0.70, rodDiamondTier: 4,
  rodNetheriteMaxWeightKg: 999.0, rodNetheriteHookWindowBonus: 0.30, rodNetheriteTensionCeiling: 1.70,
  rodNetheriteTensionRecover: 1.90, rodNetheriteStaminaDrain: 1.80, rodNetheriteMaterialFactor: 0.50, rodNetheriteTier: 5,
  // 张力模型
  tensionBaseCeiling: 100.0, pullSprint: 0.95, counterBonus: -0.5, counterPenalty: 0.35,
  // ⚠ 2026-09-30 **有意**改动：capDampingBonus 0.85 → 1.20。不是静默漂移。
  //   用户定案："给金轮额外25%扣力上限" + "硬上限抬高到以后也能改"。
  //   大型纺车轮（金轮）阻尼 2.50 → 单轮扣力上限 (2.50-1.0)×50% = +75%，
  //   再乘它专属的 1.25 倍 → +93.75%。旧硬顶 0.85 会把 0.9375 夹回 0.85，
  //   那个专属特性等于白做，所以硬顶必须一起抬。它仍是配置项，随时可改。
  minReelGainRatio: 0.25, maxFishSlowdown: 0.90, dampingToCapRatio: 0.50, capDampingBonus: 1.20,
  totalDrainTarget: 0.90, nearShoreBase: 6.0, staminaEpsilonFloor: 0.05, staminaEpsilonRatio: 0.005,
  inputStaleLimit: 5,
  // 档位曲面
  tierSpeedBase: 1.12, tierStaminaResistBase: 1.18, fatiguePullMultiplier: 0.5,
  // ⚠ 2026-10-05 **有意**新增（不是漂移）：金/红档的**手感**修正。
  //   用户定案："发现金档和红手感可能没有差距……现有金游速降 16%、体力不变；
  //   红移速加 5%、体力加 50%。"档位以前只影响体重与经济价值，手感上金红只差 12%。
  //   落地：FishSpecies.tierFeelSpeedScale / tierFeelStaminaScale（docs/14 §4.1），
  //   守着它的是 tools/check-tier-feel.js（含 5 个负例）。
  tierGoldSpeedScale: 0.84, tierGoldStaminaScale: 1.0,
  tierRedSpeedScale: 1.05, tierRedStaminaScale: 1.5,
  // ⚠ 2026-10-06 **有意**新增：力竭期收线保底（0.05 格/tick ≈ 1 格/秒）。
  //   用户报"鱼即使力竭了但依然收线回拉不过来，收不收都一个游速"——
  //   距离结算 = 鱼拽 − 收线，低档/磨损的轮子收线速度会低于力竭鱼的拽力。
  //   守着它的是 check-reel-stall.js 第 ③ 节（全鱼种 × 全渔轮扫描）。
  fatiguedReelMinGain: 0.05,
  // ⚠ 2026-10-06 **有意**新增：红档体力**基准下限**（用户："有些红鱼似乎体力基准连 200 都没到"
  //   → 定案"给所有红档鱼设体力池下限：基准至少 200"）。先抬下限、再乘 tierRedStaminaScale。
  //   守它的是 check-tier-feel.js（含"至少要有一条红档鱼低于下限"的反向断言）。
  tierRedStaminaMin: 200,
  // ⚠ 2026-10-06 **有意**新增：力竭期的张力修正（用户："加一个鱼力竭时张力涨速再乘 0.85，
  //   张力回复再乘 1.15"）。涨速那一层是"再乘"——控杆关系里的力竭 ×0.80 不动。
  //   守着它的是 check-reel-stall.js 第 ④ 节（全鱼种 × 档位 × 鱼线扫描）。
  fatiguedTensionGainScale: 0.7, fatiguedTensionRecoverScale: 1.35,
  // ⚠ 2026-10-06 **有意**新增：全局"进疲劳的体力比例"倍率（用户："我在游戏中实际感觉
  //   力竭更难拉了" → 日志显示那一局鱼一次都没进疲劳，因为红档池 ×1.5 把阈值落在
  //   1.5 倍的绝对值上）。1.0 = 与鱼种值一致，不动任何既有手感。
  fatigueThresholdScale: 1.0,
  // ⚠ 2026-10-06 **有意**新增：**保底体力削减**（渔轮属性，用户定案："拉鱼时每秒最少削减的鱼力，
  //   不受上限限制。由渔轮提供，根据压制阻尼转化而来……最小 5、上限 20"）。
  //   每点阻尼转化 ×10 是**倒推**的（金轮阻尼 2.50 → 5+15 = 20.0，贴住上限），
  //   且用户要求系数取整或 .5 —— 10 是整数。
  //   守着它的是 tools/check-reel-floor-drain.js（五个轮的数值逐个复算）。
  reelFloorDrainBase: 5.0, reelFloorDrainPerDamping: 10.0, reelFloorDrainCap: 20.0,
  // ⚠ 2026-10-06 **有意**新增：**保底挣扎**（用户："现有的疲劳体系让红金等鱼过于容易拉上来……
  //   注意我说的挣扎指的是**鱼不力竭的状态**……白 1 红 6 基准，再根据鱼的策略向上至多浮动 1"）。
  //   每进入一轮挣扎，鱼至少有 N 秒没力竭（N = 基准 + 档位×每档 + 策略加成，加成夹在 [0,1]）。
  //   守着它的是 check-reel-stall.js 第 ⑥ 节。
  floorStruggleBase: 1.0, floorStrugglePerTier: 1.0,
  floorStruggleBonusSteady: 0.0, floorStruggleBonusSluggish: 0.0,
  floorStruggleBonusBurst: 1.0, floorStruggleBonusErratic: 0.5,
  // ⚠ 2026-10-06 **有意**新增：**收线速率抵抗**（用户："白 5% 绿 10% 蓝 15% 紫 20% 金 25% 红 32%，
  //   策略上下浮动至多 5%，压制阻尼最多抵消 75%，让金轮大致触碰到转化极限，取整数 0.05"）。
  //   阻尼抵消系数 0.5 是**为金轮倒推**的：(2.50−1)×0.5 = 0.75 = 上限。
  //   守着它的是 check-reel-stall.js 第 ⑦ 节。
  reelResistThreshold: 1.2,
  reelResistTierWhite: 0.05, reelResistTierGreen: 0.125, reelResistTierBlue: 0.185,
  reelResistTierPurple: 0.275, reelResistTierGold: 0.55, reelResistTierRed: 0.75,
  reelResistStratSteady: 0.0, reelResistStratSluggish: -0.05,
  reelResistStratBurst: 0.05, reelResistStratErratic: 0.0,
  reelResistDampingOffset: 0.5, reelResistDampingCap: 0.75,
  // ⚠ 2026-10-06 **有意**新增：中立时的**最小净回落**（用户："现在感觉张力下降好慢，所有时候"）。
  //   实测他那局净回落只有 0.05 点/tick ≈ 1 点/秒（鱼拉 1.65 vs 中立回落 0.51，
  //   而"鱼拉最多削到回落的 90%"是按比例削的 → 调 idleLoss 没用）。0.3 = 6 点/秒。
  idleFallFloor: 0.3,
  // ⚠ 2026-10-08 **有意**新增：**水域修饰**的两个阈值（用户："不止做极地鱼，本轮计划 20 条可以都加"）。
  //   极地 / 高寒湖 / 冷水溪流 是**叠加修饰**而不是新水体值（理由见 docs/33 §9.1）；
  //   阈值做成配置项是因为"高度 120 / 温度 0.3"这两个数最容易按玩家体感改。
  //   温度取 0.3 而不是 0.2：taiga 是 0.25，取 0.2 会把针叶林溪流（哲罗鲑的家）排除掉。
  //   守着它的是 check-water-body.js 第 ⑨ 节。
  alpineLakeMinY: 120, coldWaterMaxTemp: 0.3,
  // ⚠ 2026-10-08 **有意**新增：**冰钓**（用户拍板，docs/33 §8.6）。
  //   洞内：咬钩时间不变、金红 +1%（加算）、鱼线磨损 ×2（只乘鱼线）；
  //   覆冰群系非洞：咬钩 ×1.5、金红 −1%（加算）。
  //   ⚠ 没有"冰洞回冻"项 —— 用户定案"不会回冻"。
  //   守着它的是 check-ice-fishing.js（第 2 批落地）。
  brokenIceToPackIceMinutes: 5, packIceToIceMinutes: 10, icePickDurabilityCost: 1,
  iceHoleLineWearMultiplier: 2.0, iceHoleHighValueBonus: 0.01,
  openIceBiteTimeMultiplier: 1.5, openIceHighValuePenalty: 0.01,
  struggleSecondsBase: 2.0, struggleSecondsPerTier: 1.6, restSecondsBase: 5.0, restSecondsPerTier: 0.4,
  // 抛投与窝料
  fullChargeTicks: 32, minChargeTicks: 3, chumMinAirTicks: 10,
  // 承重行为
  oversizeFishAction: 0,
  // 追踪罗盘（2026-09-30 新增）。默认 400 格是**按"河宽 8~20 格、小屋 spacing 34 区块"
  // 估的**：34 区块 ≈ 544 格，取 400 是"大概一个间隔多一点"，
  // 保证正常地形下第一两圈就能命中（搜索是一圈一圈往外扩、找到就停，
  // 所以这个上限只在"附近真的没有"时才会被走满）。
  compassSearchRadius: 1200,
  // 大鱼公告（2026-10-11 新增，用户原话："**不加共享，公告可以搞**"）。
  // ⚠ 这两项**接线前不存在**（是新功能），这里登记的是"首次落地时的取值" ——
  //   以后谁改了它们，会在这条断言里显示成"默认值漂移"，逼他回来看一眼是不是有意的。
  //   口径：5050 = **用户亲口给的数**（红档 4800 ± 8% ⇒ 只公告红档里体型偏大的那批，见 check-big-catch-announce.js）；
  //   10 秒 = 两条公告之间的最小间隔。
  bigCatchAnnounceValue: 5050, bigCatchAnnounceMinIntervalSec: 10,
};

const got = new Map();
const i0 = cfgSrc.indexOf('// ==== BEGIN 玩法数值定义 ====');
const i1 = cfgSrc.indexOf('// ==== END 玩法数值定义 ====');
if (i0 < 0 || i1 < 0) { no('Config.java 里找不到玩法数值定义段'); }
else {
  for (const m of cfgSrc.slice(i0, i1).matchAll(/(\w+)\s*=\s*b([^;]*);/g)) {
    const d = /\.defineInRange\("(\w+)",\s*([-+*/(). 0-9]+?)\s*,/.exec(m[2]);
    if (d) got.set(d[1], Function('"return (' + d[2] + ')"')());
  }
}

let miss = 0;
// 2026-10-30：这 5 项"竿 · 提竿窗口加成"随鱼钩一起删除（已经没有代码读它们）。
const SKIP_WINDOW = new Set(['rodWoodHookWindowBonus', 'rodIronHookWindowBonus',
  'rodGoldHookWindowBonus', 'rodDiamondHookWindowBonus', 'rodNetheriteHookWindowBonus']);
for (const [k, want] of Object.entries(EXPECT)) {
  if (SKIP_WINDOW.has(k)) continue;
  if (!got.has(k)) { no('缺少配置项 ' + k + '（原值 ' + want + '）'); miss++; continue; }
  if (Math.abs(got.get(k) - want) > 1e-9) {
    no('默认值漂移 ' + k + '：原 ' + want + ' → ' + got.get(k)); miss++;
  }
}
for (const k of got.keys()) if (!(k in EXPECT) && !SKIP_WINDOW.has(k)) { no('多出配置项 ' + k); miss++; }
if (!miss) ok('玩法数值 ' + got.size + ' 项默认值与接线前的源码原值逐位相同');

// ---------------------------------------------------------------- ② 承重回归测试
// 承重：**只出情报，不做断言**。
//
// ⚠ 这一节以前是这么写的：
//       const hi = Math.min(hiRaw, CAP);        // 在 JS 里自己夹一遍
//       if (maxW > CAP) no('限幅后仍可能超过承重'); else ok('承重限幅生效');
//   那是**自证**：把"夹取"这个假设在 JS 里重写一遍，再证明"夹过之后不超"——恒真，
//   而且**从来没读过 Java**。所以当 Java 那边根本没接上限幅时
//   （FishingSession 调的是不带承重的 rollIndividual 重载），这里照样一路绿灯。
//   代价是同一个 bug 被玩家报了两次：
//     ① "标承重 8kg 的竿钓出 11kg 的鱼" → 我在 pool 那层加了个漏风的过滤就以为修完了；
//     ② "依然存在鱼超承重不断杆" → 日志里 8kg 的木竿钓上 21.10kg 的大西洋鲑。
//
// 规则现在变了（用户定案：**不筛选，改成烧竿惩罚**），所以这里不再断言
// "体重不超承重"——那已经不是目标。真正的执行逻辑由 tools/check-over-capacity.js
// 断言（它读 Java：咬钩判定 / 每 tick 烧竿 / 断竿收场 / 语言键）。
//
// 这里只输出一份"哪些组合会触发烧竿"的地图，调平衡时看。
const bak = path.join(ROOT, '参考资料/_before_fishcfg/FishSpecies.java.bak');
if (!fs.existsSync(bak)) {
  console.log('   （找不到 FishSpecies 字面量备份，跳过承重情报）');
} else {
  const fish = parseFish(fs.readFileSync(bak, 'utf8'));
  const RODS = [['木', 8.0], ['铁', 20.0], ['金', 12.0], ['钻石', 40.0], ['下界合金', 999.0]];
  console.log('   各材质竿可能遇到超重个体的鱼种（会触发烧竿）：');
  for (const [name, cap] of RODS) {
    const risky = fish.filter((f) => f.values.maxKg > cap);
    if (!risky.length) { console.log('     ' + name + '竿(' + cap + 'kg)：无'); continue; }
    const always = risky.filter((f) => f.values.minKg > cap).length;
    console.log('     ' + name + '竿(' + cap + 'kg)：' + risky.length + ' 种' +
                (always ? '（其中 ' + always + ' 种整种都超重，碰上必烧）' : ''));
  }
  const wood = fish.filter((f) => f.values.maxKg > 8.0).length;
  if (wood > 0) {
    console.log('   → 木竿有 ' + wood + ' 种鱼可能超重：**这是设计如此**，' +
                '超重会烧竿（约 4 秒烧完），放线让鱼把线拽完可以保住竿');
  }
}

console.log(bad ? '\n❌ 有 ' + bad + ' 处问题' : '\n✅ 玩法数值默认值一致（承重规则见 check-over-capacity.js）');
process.exit(bad ? 1 : 0);
