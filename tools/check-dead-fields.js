/*
 * check-dead-fields.js —— 找出"定义了、tooltip 显示了、但游戏逻辑从不读取"的配件属性。
 *
 * 触发理由：`pullStruggle` 就是这么个死字段 —— 玩家按 tooltip 选轮子，
 * 实际那个数一点作用都没有。一个属性表里很可能不止一个（后来果然又挖出 5 个）。
 *
 * 判定方式（**逐环查链路，并打印证据行**）：
 *   PartStats 字段
 *     → PartComposer 合成函数（读它）
 *       → RodStats.from(...) 调用该合成函数
 *         → FishingSession / FishingManager 读 RodStats 的对应值
 *
 * ⚠ 早先写过"一个跨文件大正则统计消费处"的版本，越写越脆：
 *   接收者名字（line/reel/stats/ps…）一改就对不上，动不动整张表报"全死"。
 *   现在每一步都单独匹配、并把**命中的代码行**打出来，
 *   读的人一眼能看出是真通过还是正则没对上，不会静默骗人。
 *
 * 用法：node tools/check-dead-fields.js
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

const SRC = path.join(process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..'),
        'src', 'main', 'java', 'com', 'whitecobalt', 'anglertackle');

function walk(dir) {
  const out = [];
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) out.push(...walk(p));
    else if (e.name.endsWith('.java')) out.push({ file: p, name: e.name, text: fs.readFileSync(p, 'utf8') });
  }
  return out;
}
const ALL = walk(SRC);
const withName = (n) => {
  const f = ALL.find((x) => x.name === n);
  if (!f) throw new Error('找不到文件 ' + n);
  return f.text;
};

const partStats = withName('PartStats.java');
const composer = withName('PartComposer.java');
const rodStats = withName('RodStats.java');

// ---- 1. 读 PartStats 的字段 ----
const rec = /public record PartStats\(([\s\S]*?)\)\s*\{/.exec(partStats);
if (!rec) throw new Error('没找到 PartStats record');
// 逐个参数取"最后一个词"当字段名：形如 `double tensionCeiling,`
// ⚠ 注释必须先剥掉，否则行尾的 `// 张力上限倍率` 会被当成字段名的一部分
const FIELDS = rec[1].split(',')
  .map((seg) => seg.replace(/\/\/.*$/gm, '').replace(/\s+/g, ' ').trim())
  .filter((seg) => seg)
  .map((seg) => seg.split(' ').pop())
  .filter((n) => /^\w+$/.test(n));
if (FIELDS.length < 8) throw new Error('字段解析失败：' + FIELDS.join(','));

/** 逐环检查表：PartStats 字段 → [PartComposer 函数, RodStats 取值, 最终消费文件] */
const CHAIN = [
  ['tensionCeiling',  'tensionCeiling',  'tensionCeiling',  ['FishingSession.java']],
  ['tensionGen',      'tensionGen',      'tensionGen',      ['FishingSession.java']],
  ['tensionRecover',  'tensionRecover',  'tensionRecover',  ['FishingSession.java']],
  ['lineLength',      'lineLength',      'lineLength',      ['FishingSession.java']],
  ['pullIdle',        'pullIdle',        'pullIdle',        ['FishingSession.java']],
  ['pullStruggle',    'pullStruggle',    'pullStruggle',    ['FishingSession.java']],
  ['staminaDrain',    'staminaDrain',    'staminaDrain',    ['FishingSession.java']],
  ['damping',         'damping',         'damping',         ['FishingSession.java']],
];

/** 在 text 里找第一个匹配，返回 "行号: 内容" */
function evidence(text, re) {
  const lines = text.split('\n');
  for (let i = 0; i < lines.length; i++) {
    if (re.test(lines[i])) return (i + 1) + ': ' + lines[i].trim();
  }
  return null;
}

let fail = 0;
const assert = (cond, msg) => { if (!cond) { console.log('  ❌ ' + msg); fail++; } };

console.log('=== PartStats 字段 → 消费链路（逐环验证）===\n');
console.log('  字段            ①Composer读  ②RodStats调  ③最终使用  证据');
console.log('  ' + '-'.repeat(104));

for (const [field, fn, rodField, consumers] of CHAIN) {
  // ① PartComposer 的函数体里读了这个字段（形如 line.xxx() / reel.xxx()）
  const ev1 = evidence(composer, new RegExp('\\b\\w+\\s*\\.\\s*' + field + '\\(\\)'));
  // ② RodStats 调用了该合成函数
  const ev2 = evidence(rodStats, new RegExp('PartComposer\\.' + fn + '\\('));
  // ③ 最终消费：在 FishingSession 等文件里读 RodStats 的同名值
  let ev3 = null;
  for (const c of consumers) {
    ev3 = evidence(withName(c), new RegExp('\\b\\w+\\s*\\.\\s*' + rodField + '\\(\\)'));
    if (ev3) break;
  }
  const ok = !!(ev1 && ev2 && ev3);
  if (!ok) fail++;
  console.log('  ' + field.padEnd(16)
    + (ev1 ? '✅' : '❌').padEnd(13) + (ev2 ? '✅' : '❌').padEnd(13)
    + (ev3 ? '✅' : '❌').padEnd(12)
    + (ok ? ev3.slice(0, 46) : '链路断了'));
  if (!ok) {
    if (!ev1) console.log('      ① 没找到 PartComposer 读 ' + field);
    if (!ev2) console.log('      ② 没找到 RodStats 调 PartComposer.' + fn);
    if (!ev3) console.log('      ③ 没找到 ' + consumers.join('/') + ' 读 ' + rodField);
  }
}

// ---- 档位：单独判（它是元数据，消费方是品质着色）----
console.log('\n  字段            消费方');
console.log('  ' + '-'.repeat(60));
{
  // 证据：TacklePartItem.quality() 里读了 partTier()（名字是 partTier 不是 tier）
  const ev = evidence(withName('TacklePartItem.java'), /partTier\(\)/);
  const has = FIELDS.includes('tier') && !!ev;
  if (!has) fail++;
  console.log('  tier            ' + (has ? '✅ 品质着色：' + ev.slice(0, 46) : '❌ 无人消费'));
}

// ---- 检查"多余的字段"：PartStats 里有、但没进检查表 ----
const covered = new Set([...CHAIN.map((c) => c[0]), 'tier']);
const extra = FIELDS.filter((f) => !covered.has(f));
assert(extra.length === 0, 'PartStats 里有字段没进本检查表：' + extra.join(', ')
  + '（新增字段请同时加进 CHAIN，否则这个脚本会漏掉它）');
if (extra.length) {
  console.log('\n  ⚠ 漏检字段：' + extra.join(', '));
}

console.log('\n' + (fail === 0 ? '全部通过：所有属性都有真实的消费链路' : fail + ' 项未通过'));
console.log('\n  历史记录：');
console.log('    · pullStruggle —— 曾是死字段，导致"挣扎回拉"调了没用（已接上）');
console.log('    · 浮漂三属性 + 鱼钩两属性 —— 全无实现，整个浮漂槽已随物品一起删除');
console.log('    加新属性时**先想好谁消费**，别只加字段和 tooltip ——');
console.log('    玩家会照着 tooltip 选配件，显示假的属性就是在骗人。');

// ========================================================================
// 补充：档位 → 颜色/名称的映射必须"只有一份"
// ========================================================================
// 踩过的坑（同一类错误犯了三次，每次都是"另写一份映射"）：
//   ① 鱼线/拟饵：Quality.of(tier) 少减 1 → 颜色整体高一档
//   ② 饵料：BaitItem 手写 switch 且从 case 1 起 → 整列偏低一档
//      （紫显示成蓝、金显示成紫、红显示成金）
// 根因都是"手抄一份档位映射"，两边各自的逻辑都自洽，于是检查也发现不了。
// 这里直接查源码：所有把档位换算成品名/颜色的地方，必须引用 Quality。
console.log('\n=== 档位映射是否只有一份（防"手抄一份"再犯）===');
{
  const checks = [
    ['TacklePartItem.java', /Quality\.of\(stats\.partTier\(\)\s*-\s*1\)/, '鱼线/拟饵：Quality.of(partTier()-1)'],
    ['BaitItem.java', /Quality\.of\(tier\)\.color\(\)/, '饵料颜色：Quality.of(tier).color()'],
    // tooltip 已改成显示"高价值鱼目标占比"，不再显示档位名，
    // 所以 BaitItem 里只剩 tierColor 一处在用 Quality —— 上面的检查覆盖了。
    ['TacklePartItem.java', /qualityLine\(stack\)/, '配件 tooltip 的品质行'],
  ];
  for (const [file, re, label] of checks) {
    const src = ALL.find((f) => f.name === file);
    const ok = src && re.test(src.text);
    if (!ok) fail++;
    console.log('  ' + (ok ? '✅' : '❌') + ' ' + label);
  }
  // 反例：不该再出现手写的档位 switch（出现才是 bug）
  // ⚠ 必须先剥掉注释：我在 BaitItem 的 javadoc 里**引用**了那段历史代码做说明
  //   （"原来是 case 1 -> "uncommon" ..."），不剥注释就会把它当成现行代码误报一次。
  const baitSrc = ALL.find((f) => f.name === 'BaitItem.java');
  const baitCode = baitSrc.text.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
  const handWritten = /case 1 ->\s*"(uncommon|rare|epic|legendary|mythic)"/.test(baitCode);
  if (handWritten) fail++;
  console.log('  ' + (handWritten ? '❌ 发现手写的档位 switch（历史 bug 现场）'
    : '✅ BaitItem 里没有手写的档位 switch'));
}

console.log('\n' + (fail === 0 ? '全部通过' : fail + ' 项未通过'));

// ========================================================================
// 补充：输入包发送频率 与 服务端掉包容忍 必须配套
// ========================================================================
// 踩过的坑：客户端每 2 tick 发一次输入包，服务端容忍 3 tick —— 余量只剩 1 tick。
// 网络一抖动服务端就误判成"松手"，表现为
//   · 张力卡住不动（走了"中立回落"分支）
//   · 继续拉线鱼距不回收（收线分支没走）
//   · HUD 还显示旧状态（客户端靠之前收到的快照）
// 玩家报的"卡中间 + 鱼距不回收"就是这个。
console.log('\n=== 输入链路：发送频率 vs 服务端容忍（必须配套）===');
{
  const client = ALL.find((f) => f.name === 'ClientFishingState.java');
  const session = ALL.find((f) => f.name === 'FishingSession.java');
  // ⚠ 必须先剥注释：修 bug 时习惯把**旧代码**写进注释做说明
  //   （"曾经写成 inputCooldown = 2…"），不剥注释就会把历史代码当成现行代码误报。
  //   这个坑在 BaitItem 的档位 switch 检查上已经踩过一次。
  const stripComments = (t) => t.replace(/\/\/[^\n]*/g, '').replace(/\/\*[\s\S]*?\*\//g, '');
  const clientCode = stripComments(client.text);
  const sessionCode = stripComments(session.text);
  // 客户端有没有节流
  const throttled = /inputCooldown\s*=\s*\d+/.test(clientCode);
  // 服务端容忍阈值。⚠️ 这个值现在住在配置里（[gameplay] inputStaleLimit），
  // 源码里是 Tuning.inputStaleLimit()，grep 常量已经拿不到了。
  const gp = require('./tuning.js').cfg;
  const usesTuning = /Tuning\.inputStaleLimit\(\)/.test(sessionCode);
  const limit = usesTuning ? gp.inputStaleLimit : null;

  if (throttled) fail++;
  console.log('  ' + (throttled ? '❌ 客户端仍在节流输入包（每 N tick 发一次）'
    : '✅ 客户端每 tick 都发输入包（不节流）'));
  if (limit === null) {
    fail++;
    console.log('  ❌ 没找到 INPUT_STALE_LIMIT 常量');
  } else {
    const ok = limit >= 3;
    if (!ok) fail++;
    console.log('  ' + (ok ? '✅' : '❌') + ' 服务端容忍 ' + limit + ' tick 未收到输入才判松手'
      + (throttled ? '（但客户端有节流，余量可能不够）' : '（每 tick 都发，余量充足）'));
  }
  console.log('  说明：两端频率必须配套。发送越稀疏，容忍阈值就越要放宽；');
  console.log('        否则一次丢包 = 玩家被强制松手。');
}

console.log('\n' + (fail === 0 ? '全部通过' : fail + ' 项未通过'));
process.exitCode = fail === 0 ? 0 : 1;
