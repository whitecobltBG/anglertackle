const ATL_ROOT = process.env.ATL_ROOT ? require('path').resolve(process.env.ATL_ROOT) : require('path').join(__dirname, '..');
// 校验 ModItems 里每个配件的数值是**它自己的**字面量，而且没接配置。
//
// ── 这个检查的断言方向变过一次，记下来免得再搞反 ──────────────
// 原来它要求"每个配件都接在 Tuning.xxx() 上"（那时想让配件数值跟配置走）。
// 那个方向是**错的**：在物品定义里读配置会在 RegisterEvent 阶段抛
//   "Cannot get config value before config is loaded."
// 游戏启动即崩（真实事故）。
//
// 现在的方向：
//   ① 每个配件的 PartStats 参数必须是**数字字面量**（不是 Tuning 调用）
//   ② 每个配件拿到的数值必须与 tools/part-source.js 解析出的**它自己**的数值一致
//   ③ ModItems 里不许出现任何 Tuning / Config 取值
//
// ① 是"不能崩"，② 是"不能张冠李戴"。这两件事都必须守：
// 早先两个生成脚本各自用正则解析 ModItems，双双跳过了单行写法的亮片拟饵，
// 于是每个拟饵拿到前一个物品的档位 —— 编译通过、默认值比对全绿（两边同一份错位数据），
// 但六档拟饵的颜色全乱了。
//
// 用法：node tools/check-part-wiring.js
const fs = require('fs'), path = require('path');
const ROOT = ATL_ROOT;
const { parseParts } = require('./part-source.js');

const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
const shortId = (id) => id.replace(/_(line|reel|sinker|lure)$/, '');

/**
 * 有意变更登记表 —— key = {@code <部件id>.<字段名>}。
 *
 * 这个脚本拿"配置化之前的字面量备份"当真值，职责是抓**悄悄**改数值。
 * 但玩家明确要求的改动是另一回事：那种改动必须发生，也必须留下痕迹。
 * 所以只放行这里登记过的，其余一律照旧报错。
 * ⚠ 不要去改那个 .bak —— 它的全部价值就是"改动前的样子"。
 *
 * （check-part-config-parity.js 里有同一张表，改一处要同步改两处。）
 */
const INTENTIONAL = new Map([
  ['baitcast_reel.pullIdle', { from: 1.90, to: 1.98,
    why: '2026-09-30 用户定案：紫轮（水滴轮）疲劳回拉少于 1.9 → 加到 1.98' }],
  // 白档两件套微调（用户 2026-10-01 定案）。.bak 里两件都是干净的 1.00。
  // ⚠ 只有这 7 项；耐久/线长/档位没动 —— 动了就是漂移。
  ['hemp_line.tensionCeiling', { from: 1.00, to: 1.08, why: '麻线微调（用户定案）：张力上限 1.00 → 1.08' }],
  ['hemp_line.tensionGen', { from: 1.00, to: 0.97, why: '麻线微调（用户定案）：张力生成 1.00 → 0.97' }],
  ['hemp_line.tensionRecover', { from: 1.00, to: 1.05, why: '麻线微调（用户定案）：张力回复 1.00 → 1.05' }],
  ['hand_reel.pullIdle', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：回拉（常态）1.00 → 1.08' }],
  ['hand_reel.pullStruggle', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：回拉（挣扎）1.00 → 1.08' }],
  ['hand_reel.staminaDrain', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：体力削减 1.00 → 1.08' }],
  ['hand_reel.damping', { from: 1.00, to: 1.07, why: '手线轮微调（用户定案）：压制阻尼 1.00 → 1.07' }],
  ['fluoro_line.tensionCeiling', { from: 1.54, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['fluoro_line.tensionGen', { from: 0.81, to: 0.78, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['fluoro_line.tensionRecover', { from: 1.36, to: 1.42, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['fluoro_line.lineLength', { from: 1.8333333333333333, to: 2, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['pe_line.tensionCeiling', { from: 1.94, to: 2.08, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['pe_line.tensionGen', { from: 0.76, to: 0.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['pe_line.tensionRecover', { from: 1.44, to: 1.52, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['titanium_line.tensionCeiling', { from: 2.2, to: 2.36, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['titanium_line.tensionGen', { from: 0.58, to: 0.56, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['titanium_line.tensionRecover', { from: 1.76, to: 1.78, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['heavy_reel.pullIdle', { from: 1.55, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
  ['heavy_reel.pullStruggle', { from: 1.55, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红三档 + 金轮回拉）' }],
]);
let intended = 0;

const itemsPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java');
const src = fs.readFileSync(itemsPath, 'utf8');

let bad = 0;

// ---------------------------------------------------------------- ① 不许读配置
const tuningCalls = src.match(/Tuning\.\w+\(\)/g) || [];
if (tuningCalls.length) {
  console.error('❌ ModItems.java 里有 ' + tuningCalls.length + ' 处配置取值 —— 会在注册期崩溃：');
  console.error('     ' + tuningCalls.slice(0, 4).join(', '));
  console.error('   物品 supplier 在 RegisterEvent 阶段执行，那时配置还没加载。');
  bad++;
} else {
  console.log('✅ ModItems.java 里没有任何配置取值（注册期安全）');
}
if (src.includes('import com.whitecobalt.anglertackle.fishing.Tuning;')) {
  console.error('❌ ModItems.java 还 import 了 Tuning');
  bad++;
}

// ---------------------------------------------------------------- ② 数值不许错位
// 期望来源：备份（字面量版）—— 它记录的是"每个配件本该有什么数值"
const bakPath = path.join(ROOT, '参考资料/_before_partcfg/ModItems.java.bak');
if (!fs.existsSync(bakPath)) {
  console.error('❌ 找不到字面量版备份：' + bakPath);
  process.exit(1);
}
const expected = parseParts(fs.readFileSync(bakPath, 'utf8'), { literals: true });

const EPS = 1e-9;
// 2026-10-30 起鱼钩已删除：备份里那两件不再参与"数值不许错位"的比对。
const SKIP = new Set(['plain_hook', 'barbed_hook']);
for (const p of expected.filter((x) => !SKIP.has(x.id))) {
  // 从当前 ModItems 里抓这个配件的 PartStats.<kind>(...) 实参
  const re = new RegExp('ITEMS\\.register\\("' + p.id + '"[\\s\\S]*?PartStats\\.' + p.kind + '\\(');
  const m = re.exec(src);
  if (!m) { console.error('❌ ModItems.java 里找不到 ' + p.id + ' 的 PartStats.' + p.kind + '('); bad++; continue; }

  // 括号配对取实参
  let depth = 0, end = -1;
  const from = m.index + m[0].length;
  for (let q = from; q < src.length; q++) {
    if (src[q] === '(') depth++;
    else if (src[q] === ')') { if (depth === 0) { end = q; break; } depth--; }
  }
  if (end < 0) { console.error('❌ ' + p.id + ' 的括号没配对'); bad++; continue; }

  const args = src.slice(from, end).split(',').map((s) => s.trim());
  if (args.length !== p.keys.length) {
    console.error('❌ ' + p.id + '：实参 ' + args.length + ' 个，期望 ' + p.keys.length + ' 个');
    bad++;
    continue;
  }
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (!/^[-+*/(). 0-9]+$/.test(a)) {
      console.error('❌ ' + p.id + ' 第 ' + (i + 1) + ' 个参数不是数字字面量：' + a);
      bad++;
      continue;
    }
    const got = Function('"use strict";return (' + a + ')')();
    const want = p.values[i];
    if (Math.abs(got - want) > EPS) {
      // ★ 有意变更登记表（与 check-part-config-parity.js 保持一致）。
      //
      // 这个脚本拿"配置化之前的字面量备份"当真值，用来抓**悄悄**改数值。
      // 但玩家明确要求的改动是另一回事：那种改动必须发生，也得留下痕迹。
      // 所以这里只放行**已登记**的那些，其余一律照旧报错。
      // ⚠ 不要去改 .bak —— 它的全部价值就是"改动前的样子"。
      const key = p.id + '.' + p.keys[i];
      const ok = INTENTIONAL.get(key);
      if (ok && Math.abs(got - ok.to) < EPS && Math.abs(want - ok.from) < EPS) {
        intended++;
        console.log('   ✅ 有意变更 ' + key + '：' + ok.from + ' → ' + ok.to);
        continue;
      }
      console.error('❌ ' + key + ' 数值张冠李戴：' +
                    '现在是 ' + got + '，应为 ' + want);
      bad++;
    }
  }
}

// ---------------------------------------------------------------- ③ 反向：不该有多余的
{
  const probe = /PartStats\.(\w+)\(/g;
  const kinds = new Set();
  let m;
  while ((m = probe.exec(src))) kinds.add(m[1]);
  for (const k of kinds) {
    if (!['line', 'reel', 'sinker', 'lure'].includes(k)) {
      console.error('❌ 出现了未知的 PartStats.' + k + '(...)');
      bad++;
    }
  }
}

console.log('核对 ' + expected.length + ' 个配件的数值，共 ' +
            expected.reduce((a, p) => a + p.keys.length, 0) + ' 个参数');
if (bad) { console.error('\n问题 ' + bad + ' 处'); process.exit(1); }
console.log('✅ 每个配件都是自己的数字字面量，且注册期不读配置');
