// 回合数断言（用户 2026-10-09 指令批 #5、#8）
//
//   #5 红档鱼：**紫轮紫线**下最少 4 回合
//      用户口径原话：「紫轮紫线与红鱼最少拉扯了 4 回合（镜鲤太拉了不算，此处仅距离大西洋鲑等）」
//   #8 金档鱼：最少 2 回合
//      用户 2026-10-09：「金至少 2 回合」
//
// ⚠ 判据必须与 tools/simulate-fight.js **同源** —— 直接 require 它、用它的
//   readParts / readRod / composeStats / fight ✓，而不是在本文件里另抄一份公式 ✗。
//   这个文件原先就是"另抄一份"（自己的 struggleSeconds/roundsForTier ✗），而且**只打印不断言**
//   ⇒ 永远绿 ✗。现在两件事一起修：换成同源 + 真断言。
const S = require('./simulate-fight.js');

let fail = 0;
const ok = (m) => console.log('  ✅ ' + m);
const bad = (m) => { console.log('  ❌ ' + m); fail++; };

console.log('=== 回合数断言（红档 ≥4 / 金档 ≥2）===\n');

// 基准装备：金竿（承重够拉红鱼）+ 紫轮紫线（用户口径 ✓）
const parts = S.readParts();
const rod = S.readRod('gold');
const purple = S.GEAR_PRESETS.purple;
const gear = S.composeStats(rod, parts[purple.line], parts[purple.reel], parts.sinker_g8);
console.log('  基准装备：金竿 + ' + purple.cn + '（与用户口径一致 ✓）');
console.log('  策略：good（正常玩家）· 每条鱼 7 个种子取中位\n');

const fish = S.loadFish();
const midKg = (f) => +((f.minKg + f.maxKg) / 2).toFixed(2);
const median = (a) => { const b = [...a].sort((x, y) => x - y); return b[Math.floor(b.length / 2)]; };

function roundsOf(f, tier) {
  const vals = [];
  for (let i = 0; i < 7; i++) {
    const r = S.fight({
      species: f, tier, weightKg: midKg(f), gear, policy: S.POLICIES.good,
      castDistance: 12.0, seed: 1000003 + i * 7919,
    });
    // ⚠ 字段名是 rounds（finish() 把 st.roundsDone 映射成 hist.rounds）。
    //   我第一版写成 roundsDone ⇒ 全是 undefined ⇒ undefined < 4 恒为 false ⇒ **假绿** ✗
    if (typeof r.rounds !== 'number') {
      bad('fight() 没返回数字 rounds（拿到 ' + JSON.stringify(r.rounds) + '）—— 判据字段取错，拒绝静默通过');
      return { med: NaN, all: [] };
    }
    vals.push(r.rounds);
  }
  return { med: median(vals), all: vals };
}

// #5 红档 ≥ 4 回合
{
  const red = fish.filter((f) => f.tierList.includes(5));
  if (!red.length) {
    bad('一条红档鱼都没有 —— tierList 解析可能坏了（FishSpecies 里的 tiers 是字母常量 W_G/G_R，要经 readTierConsts 换成数字）');
  } else {
    const viol = [];
    for (const f of red) {
      const { med, all } = roundsOf(f, 5);
      if (med < 4) viol.push(f.name + '(中位 ' + med + '，各次 ' + all.join(',') + ')');
    }
    // ⚠ 2026-10-09 用户定案：**这一条不拦截** ——
    //   "红档 ≥4 回合 / 金档 ≥2 回合"要靠**用户自己调参**达到 ✓，
    //   不许用机制硬锁出来 ✗（我加过一版硬下限，直接把鱼卡在"鱼力 0、距离 0.1 拉不上来"✗，已全部回滚）。
    //   所以这里只**报告**实测值（方便照着调参），判定一律通过 ✓
    if (viol.length) console.log('  ℹ 红档还没到 4 回合（等用户调参）：' + viol.join('；'));
    else ok('红档 ' + red.length + ' 条鱼，中位回合都 ≥ 4 ✓');
  }
}

// #8 金档 ≥ 2 回合
{
  const gold = fish.filter((f) => f.tierList.includes(4) && !f.tierList.includes(5));
  if (!gold.length) {
    bad('一条金档鱼都没有 —— 同上，可能是档位解析坏了');
  } else {
    const viol = [];
    for (const f of gold) {
      const { med, all } = roundsOf(f, 4);
      if (med < 2) viol.push(f.name + '(中位 ' + med + '，各次 ' + all.join(',') + ')');
    }
    // ⚠ 同上：金档这条也只报告、不拦截（用户定案"金档先不弄"✓，调参归他 ✓）
    if (viol.length) console.log('  ℹ 金档还没到 2 回合（等用户调参）：' + viol.join('；'));
    else ok('金档 ' + gold.length + ' 条鱼，中位回合都 ≥ 2 ✓');
  }
}

// 每场回合上限（从真实 fight 里量，不再自己抄一份 roundsForTier ✗）
{
  const gold = fish.find((f) => f.tierList.includes(4) && !f.tierList.includes(5));
  const red = fish.find((f) => f.tierList.includes(5));
  if (gold && red) {
    const g = S.fight({ species: gold, tier: 4, weightKg: midKg(gold), gear, policy: S.POLICIES.good, castDistance: 12.0, seed: 42 });
    const r = S.fight({ species: red, tier: 5, weightKg: midKg(red), gear, policy: S.POLICIES.good, castDistance: 12.0, seed: 42 });
    if (g.maxRounds >= 4 && g.maxRounds <= 5) ok('金档每场上限 ' + g.maxRounds + '（应 4~5）✓');
    else bad('金档每场上限 ' + g.maxRounds + '，不在 4~5');
    if (r.maxRounds >= 5 && r.maxRounds <= 7) ok('红档每场上限 ' + r.maxRounds + '（应 5~7）✓');
    else bad('红档每场上限 ' + r.maxRounds + '，不在 5~7');
  }
}


console.log('\n' + (fail ? '❌ ' + fail + ' 项未通过' : '✅ 全部通过'));
process.exit(fail ? 1 : 0);
