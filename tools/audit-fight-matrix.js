/*
 * audit-fight-matrix.js —— 用 simulate-fight.js 当库，跑"装备 × 鱼种"矩阵找**反常**。
 *
 * 它不是"检查器"（不进闸门、也不该进）：它跑的是一场场模拟，看的是**设计层面**的
 * 反常，而不是代码与文档是否一致。放在 tools/ 是为了别再写成 build/ 里的一次性脚本
 * （那种脚本跑完就丢，下次想复查只能重写一遍）。
 *
 * 查这五类"设计上不该出现"的情况：
 *   ① 更好装备反而更差（上鱼率下降 / 回合数明显变多）—— 单调性被破坏；
 *   ② 顶配也拿不下（上鱼率 0）；
 *   ③ 必断线 / 必逃脱 / 必超时（某一档装备下 >50% 的场次是同一种失败）；
 *   ④ 耗时离谱（中位 > 120 秒，等于玩家在罚站）；
 *   ⑤ 超承重（鱼比竿能扛的重，游戏里会烧竿 —— 这一条由 buildPool 的过滤负责，
 *      这里只是再确认一遍没人绕过它）。
 *
 * ⚠ ③ 里"低配打高档鱼必逃脱"是**预期**（白/绿轮对红鱼：收线速度 < 鱼的拽线速度，
 *   线被拽完）。所以看结果时要按"装备档位 × 鱼档位"读，别把梯度当 bug。
 *
 * 用法：
 *   node tools/audit-fight-matrix.js                 # 默认每格 30 场
 *   node tools/audit-fight-matrix.js --seeds=100      # 更稳（更慢）
 *   node tools/audit-fight-matrix.js --gear=purple    # 只看某一档装备
 */
'use strict';
const path = require('path');
const sim = require('./simulate-fight.js');

const SEEDS = Number((process.argv.find((a) => a.startsWith('--seeds=')) || '').split('=')[1]) || 30;
const ONLY_GEAR = (process.argv.find((a) => a.startsWith('--gear=')) || '').split('=')[1] || null;
const ALL_GEARS = ['white', 'green', 'blue', 'purple', 'gold', 'top'];
const GEARS = ONLY_GEAR ? [ONLY_GEAR] : ALL_GEARS;
const TIER_CN = ['白', '绿', '蓝', '紫', '金', '红'];
const CAST = 20;

const parts = sim.readParts();
const fish = sim.loadFish();
const findings = [];
const rows = [];

for (const sp of fish) {
  if (sp.rods === 'FLOAT') continue;             // 台钓不进入对抗
  for (const tier of sim.tiersOf(sp)) {
    const w = sim.weightForTier(sp, tier);
    const per = {};
    for (const g of GEARS) {
      const preset = sim.GEAR_PRESETS[g];
      const rodName = ['wood', 'iron', 'gold', 'diamond', 'netherite'].find((m) => {
        const r = sim.readRod(m);
        return r.tier >= tier && r.maxWeightKg >= w;
      });
      if (!rodName) { per[g] = { overCapacity: true }; continue; }
      const rod = sim.readRod(rodName);
      const gear = sim.composeStats(rod, parts[preset.line], parts[preset.reel], parts.sinker_g8);
      const r = sim.runCase(sp, tier, w, gear, 'good', SEEDS, CAST);
      per[g] = {
        catch: r.caughtRate, snap: r.snapRate, esc: r.escapeRate, to: r.timeoutRate,
        rounds: r.roundsP.p50, sec: r.secondsP.p50, rod: rod.cn,
      };
    }
    rows.push({ id: sp.id, name: sp.name, tier, w, per });

    for (let i = 1; i < GEARS.length; i++) {
      const a = per[GEARS[i - 1]], b = per[GEARS[i]];
      if (!a || !b || a.overCapacity || b.overCapacity) continue;
      if (b.catch < a.catch - 1e-9) {
        findings.push('① 单调性：' + sp.name + '(' + sp.id + ') ' + TIER_CN[tier] + '档 '
          + GEARS[i - 1] + ' 上鱼 ' + (a.catch * 100).toFixed(0) + '% → '
          + GEARS[i] + ' ' + (b.catch * 100).toFixed(0) + '%（更好装备反而更差）');
      }
      if (!isNaN(a.rounds) && !isNaN(b.rounds) && b.rounds > a.rounds + 1) {
        findings.push('① 单调性：' + sp.name + ' ' + GEARS[i - 1] + ' ' + a.rounds
          + ' 回合 → ' + GEARS[i] + ' ' + b.rounds + ' 回合（更好装备反而更磨）');
      }
    }
    const top = per[GEARS[GEARS.length - 1]];
    if (top && !top.overCapacity && top.catch < 1e-9) {
      findings.push('② 顶配也拿不下：' + sp.name + '(' + sp.id + ') ' + w.toFixed(1) + 'kg'
        + '（断线 ' + (top.snap * 100).toFixed(0) + '% / 逃脱 ' + (top.esc * 100).toFixed(0) + '%）');
    }
    for (const g of GEARS) {
      const p = per[g];
      if (!p || p.overCapacity) continue;
      if (p.snap > 0.5) findings.push('③ 必断线：' + sp.name + ' ' + g + ' 档装备 ' + (p.snap * 100).toFixed(0) + '%');
      if (p.esc > 0.5) findings.push('③ 必逃脱：' + sp.name + ' ' + g + ' 档装备 ' + (p.esc * 100).toFixed(0) + '%');
      if (p.to > 0.5) findings.push('③ 必超时：' + sp.name + ' ' + g + ' 档装备 ' + (p.to * 100).toFixed(0) + '%');
      if (!isNaN(p.sec) && p.sec > 120) {
        findings.push('④ 耗时离谱：' + sp.name + ' ' + g + ' 档中位 ' + p.sec.toFixed(0) + 's');
      }
      if (p.overCapacity) findings.push('⑤ 超承重：' + sp.name + ' ' + w.toFixed(1) + 'kg 没有竿扛得住');
    }
  }
}

console.log('=== 装备 × 鱼种矩阵体检（' + rows.length + ' 行 × ' + GEARS.length
  + ' 档装备，每格 ' + SEEDS + ' 场，抛 ' + CAST + ' 格）===');
console.log('');
console.log('  鱼种'.padEnd(22) + '档'.padEnd(4) + GEARS.map((g) => g.padEnd(13)).join(''));
console.log('  ' + '-'.repeat(22 + 4 + GEARS.length * 13));
for (const r of rows) {
  const cells = GEARS.map((g) => {
    const p = r.per[g];
    if (!p || p.overCapacity) return '—'.padEnd(13);
    return ((p.catch * 100).toFixed(0) + '%' + (isNaN(p.rounds) ? '' : '/' + p.rounds + '轮')).padEnd(13);
  }).join('');
  console.log('  ' + (r.name + ' ' + r.id).padEnd(22) + TIER_CN[r.tier].padEnd(4) + cells);
}

console.log('');
console.log('=== 反常 ' + findings.length + ' 条 ===');
for (const f of findings) console.log('  ' + f);
if (!findings.length) {
  console.log('  （没有单调性破坏、拿不下的鱼、必断线/必逃脱/必超时、耗时离谱、超承重）');
}
console.log('');
console.log('ℹ "低配打高档鱼必逃脱"属于预期梯度（收线速度 < 鱼的拽线速度）—— 读结果时按"装备档 × 鱼档"看。');
