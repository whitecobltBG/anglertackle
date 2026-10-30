const ATL_ROOT = process.env.ATL_ROOT ? require('path').resolve(process.env.ATL_ROOT) : require('path').join(__dirname, '..');
// 校验"配件数值配置化"没有偷改任何默认值。
//
// 这是整个改动唯一真正重要的正确性条件：配件数值从 Java 字面量搬进配置文件后，
// 默认值必须与搬之前逐位相同，否则玩家的手感会**静默变化**——配置文件里看到的
// 是他没改过的默认值，但那个默认值已经不是原来的数值了，这种 bug 基本不可能靠玩发现。
//
// ── 本检查**不能**证明的事 ───────────────────────────────────
// 它只证明"配置默认值 == 源码字面量"。如果数值被接到了别的配件头上，
// 两边是同一份错位数据，这里依然全绿 —— 那个由 tools/check-part-wiring.js 负责。
// （真实踩过：亮片拟饵的档位被接到了鱼图鉴上，这个脚本当时毫无反应。）
//
// 解析口径与生成器共用 tools/part-source.js —— 早先这里自己写了一份
// `[^)]*` 正则，跳过了单行写法的亮片拟饵，于是"备份真值"和"生成值"
// 各自错位一格却互相吻合，报了 79/79 全绿。
//
// 用法：node tools/check-part-config-parity.js
const fs = require('fs'), path = require('path');
const ROOT = ATL_ROOT;
const { parseParts } = require('./part-source.js');

const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
const shortId = (id) => id.replace(/_(line|reel|hook|lure)$/, '');

const bakPath = path.join(ROOT, '参考资料/_before_partcfg/ModItems.java.bak');
const cfgPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/Config.java');
if (!fs.existsSync(bakPath)) {
  console.error('❌ 找不到字面量版备份：' + bakPath);
  process.exit(1);
}

// ---------------------------------------------------------------- 1. 从备份抽"真值"
const truth = new Map();   // 配置项名 -> 期望默认值（数字）
// 2026-10-30：鱼钩已删除，备份里那两件不再参与默认值比对。
const REMOVED = new Set(['plain_hook', 'barbed_hook']);
for (const p of parseParts(fs.readFileSync(bakPath, 'utf8'), { literals: true })
    .filter((x) => !REMOVED.has(x.id))) {
  const prefix = shortId(p.id);
  truth.set(camel(prefix + '_durability'), p.durability);
  p.keys.forEach((k, i) => truth.set(camel(prefix + '_' + k), p.values[i]));
}

// ---------------------------------------------------------------- 2. 从 Config 抽"生成值"
const cfg = fs.readFileSync(cfgPath, 'utf8');
const i0 = cfg.indexOf('// ==== BEGIN 配件数值定义 ====');
const i1 = cfg.indexOf('// ==== END 配件数值定义 ====');
if (i0 < 0 || i1 < 0) {
  console.error('❌ Config.java 里找不到配件数值定义块');
  process.exit(1);
}
const block = cfg.slice(i0, i1);

const generated = new Map();
// 每个字段都以 `;` 结尾，所以直接按分号切段，比用正则猜调用链的括号配对可靠。
// 默认值可能是算式（lineLength 用的是 35.0 / 30.0 = "基准长度的几倍"），这里照样求值。
const gre = /(\w+)\s*=\s*b([^;]*);/g;
let g;
while ((g = gre.exec(block))) {
  const name = g[1], chain = g[2];
  const d = /\.defineInRange\("(\w+)",\s*([-+*/(). 0-9]+?)\s*,/.exec(chain);
  if (!d) { console.error('❌ ' + name + ' 没有 defineInRange'); process.exit(1); }
  generated.set(name, Function('"use strict";return (' + d[2] + ')')());
}

// ---------------------------------------------------------------- 3. 比对
//
// ★ 有意变更登记表。
//
// 这个脚本的职责是"默认值不许**悄悄**变"——它拿配置化之前的字面量备份
// （参考资料/_before_partcfg/ModItems.java.bak）当真值。但**玩家明确要求**的
// 改动是另一回事：那种改动必须发生，而且必须留下痕迹。
// 所以凡是"有意改动"，在这里登记一次；没登记的一律仍然按漂移报错。
//
// ⚠ 不要去改那个 .bak —— 它的全部价值就是"改动前的样子"。
//    改掉它等于把尺子削短，以后再也量不出漂移。
const INTENTIONAL = new Map([
  ['baitcastPullIdle', { from: 1.90, to: 1.98,
    why: '2026-09-30 用户定案："紫轮如果疲劳回拉少于1.9就加到1.98"（紫轮 = 水滴轮 baitcast_reel）' }],
  // 拟饵耐久按档位重排：亮片 40 → 米诺 30 → 软虫 26 → 浅层刺激 22 → 声诱/镀金 20。
  // 这两项是这次重排的结果，证据是源码与价目表都对得上 20：
  //   ModItems.java 字面量 partProps(20, …)；
  //   docs/18-商店价目表.md「每点耐久 29 × 耐久 20 = 580」「每点耐久 35 × 耐久 20 = 700」。
  // 而 .bak 是配置化**之前**的快照（18 / 14），所以差异是真的、且是有意的。
  ['lureRattleDurability', { from: 18, to: 20,
    why: '拟饵耐久重排：钢珠声诱 18 → 20。源码字面量与 docs/18 的价目算术（29 × 20 = 580）一致。' }],
  ['lureGildedDurability', { from: 14, to: 20,
    why: '拟饵耐久重排：镀金竞技 14 → 20。源码字面量与 docs/18 的价目算术（35 × 20 = 700）一致。' }],
  // 白档两件套的微调（用户 2026-10-01 定案）。.bak 是配置化之前的快照，
  // 那里两件都是干净的 1.00，所以差异是真的、且是有意的。
  // ⚠ 只有这 7 个键该变：耐久（hempDurability）/ 线长（hempLineLength）/ 档位（hempTier）
  //   以及手线轮的"没有的项"都不在表里 —— 用户没让动，动了就是漂移。
  ['hempTensionCeiling', { from: 1.00, to: 1.08, why: '麻线微调（用户定案）：张力上限 1.00 → 1.08。' }],
  ['hempTensionGen', { from: 1.00, to: 0.97, why: '麻线微调（用户定案）：张力生成 1.00 → 0.97（越低越不容易涨红）。' }],
  ['hempTensionRecover', { from: 1.00, to: 1.05, why: '麻线微调（用户定案）：张力回复 1.00 → 1.05。' }],
  ['handPullIdle', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：回拉（常态）1.00 → 1.08。' }],
  ['handPullStruggle', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：回拉（挣扎）1.00 → 1.08。' }],
  ['handStaminaDrain', { from: 1.00, to: 1.08, why: '手线轮微调（用户定案）：体力削减 1.00 → 1.08。' }],
  ['handDamping', { from: 1.00, to: 1.07, why: '手线轮微调（用户定案）：压制阻尼 1.00 → 1.07。' }],
  ['fluoroTensionCeiling', { from: 1.54, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['fluoroTensionGen', { from: 0.81, to: 0.78, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['fluoroTensionRecover', { from: 1.36, to: 1.42, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['fluoroLineLength', { from: 1.8333333333333333, to: 2, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['peTensionCeiling', { from: 1.94, to: 2.08, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['peTensionGen', { from: 0.76, to: 0.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['peTensionRecover', { from: 1.44, to: 1.52, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['titaniumTensionCeiling', { from: 2.2, to: 2.36, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['titaniumTensionGen', { from: 0.58, to: 0.56, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['titaniumTensionRecover', { from: 1.76, to: 1.78, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['heavyPullIdle', { from: 1.55, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
  ['heavyPullStruggle', { from: 1.55, to: 1.64, why: '2026-10-05 用户点名调整（鱼线紫/金/红 + 金轮回拉）' }],
]);

let bad = 0, checked = 0, intended = 0;
for (const [k, want] of truth) {
  if (!generated.has(k)) { console.error('❌ 缺少配置项：' + k + '（原值 ' + want + '）'); bad++; continue; }
  const got = generated.get(k);
  checked++;
  if (Math.abs(got - want) > 1e-9) {
    const ok = INTENTIONAL.get(k);
    if (ok && Math.abs(got - ok.to) < 1e-9 && Math.abs(want - ok.from) < 1e-9) {
      intended++;
      console.log('   ✅ 有意变更 ' + k + '：' + ok.from + ' → ' + ok.to);
      console.log('        ' + ok.why);
      continue;
    }
    console.error('❌ 默认值漂移 ' + k + '：原 ' + want + ' → 生成 ' + got);
    bad++;
  }
}

// ---------------------------------------------------------------- 4. 新增配件（登记放行）
//
// "多出配置项"有两种完全不同的性质，混在一起判断就会误报：
//   ⓐ **旧配件多出一项** → 说明有人手改了备份或生成器，是漂移，必须报错；
//   ⓑ **登记过的新配件** → 它的耐久/属性本来就不在"配置化之前"的 .bak 里，
//      多出来是**正常**的。
//
// ⚠️ 判据必须是"整件配件登记"，不能按单个键名放行 ——
//    按键名放行的话，将来有人给老配件加了同一对键，就会被静默放过。
//    所以下面按 **id 前缀** 匹配：登记了 `lureVibration`，就放行它名下所有键，
//    并且额外断言"这一组键的个数正好等于该配件应有的项数"。
//
// 用户 2026-10-01 定案："添加这三种路亚饵，具体数值与同级别一样"。
// 它们的档位/耐久逐项照抄同档位的既有拟饵，所以除了"是新增的"以外没有任何新数值。
const ADDED_PARTS = new Map([
  ['lureDepthfinder', { kind: 'lure', durability: 26, tier: 3,
    why: '2026-10-01 用户定案新增：蓝—深水探索米诺。数值与同档位（蓝 3）的 lure_soft 完全一致（耐久 26 / RARE / PartStats.lure(3)）。' }],
  ['lurePaddletail', { kind: 'lure', durability: 22, tier: 4,
    why: '2026-10-01 用户定案新增：紫-深水摆尾拟饵。数值与同档位（紫 4）的 lure_deep 完全一致（耐久 22 / EPIC / PartStats.lure(4)）。' }],
  ['lureVibration', { kind: 'lure', durability: 20, tier: 5,
    why: '2026-10-01 用户定案新增：金-深潜巨物振动饵。数值与同档位（金 5）的 lure_rattle 完全一致（耐久 20 / EPIC / PartStats.lure(5)）。' }],
  ['lureGlowspoon', { kind: 'lure', durability: 40, tier: 2,
    why: '2026-10-01 用户定案新增：绿-荧光亮片。数值与同档位（绿 2）的 lure_spoon 完全一致（耐久 40 / UNCOMMON / PartStats.lure(2)）；区别只在词条——亮片认 aggressive、荧光亮片认 timid（见 tools/fish-habits.json）。' }],
]);
// 每件拟饵应有的配置键：{prefix}Durability + PartStats.lure 的 1 个属性（tier）
const ADDED_PART_KEYS = 2;

const matchedAdded = new Set();
for (const k of generated.keys()) {
  if (truth.has(k)) continue;
  const hit = [...ADDED_PARTS.keys()].find((p) => k.startsWith(p) &&
    /^(Durability|Tier)$/.test(k.slice(p.length)));
  if (hit) { matchedAdded.add(hit); continue; }
  console.error('❌ 多出配置项：' + k + '（备份里没有对应数值）');
  bad++;
}
for (const [prefix, info] of ADDED_PARTS) {
  if (!matchedAdded.has(prefix)) {
    console.error('❌ 登记过的新配件 ' + prefix + ' 在 Config.java 里一个键都没找到 —— '
      + '要么注册被删了，要么登记表过期了');
    bad++;
    continue;
  }
  const keys = [...generated.keys()].filter((k) => k.startsWith(prefix) &&
    /^(Durability|Tier)$/.test(k.slice(prefix.length)));
  if (keys.length !== ADDED_PART_KEYS) {
    console.error('❌ 新配件 ' + prefix + ' 只有 ' + keys.length + ' 个配置键（应 ' + ADDED_PART_KEYS + '）'
      + '：' + keys.join(', '));
    bad++;
    continue;
  }
  if (generated.get(prefix + 'Durability') !== info.durability) {
    console.error('❌ 新配件 ' + prefix + ' 的耐久不是 ' + info.durability
      + '（实际 ' + generated.get(prefix + 'Durability') + '）');
    bad++;
    continue;
  }
  console.log('   ✅ 新增配件 ' + prefix + '：耐久 ' + info.durability
    + ' / 档位 ' + info.tier + '（' + keys.length + ' 个键已登记）');
  console.log('        ' + info.why);
}

console.log('比对 ' + checked + ' / ' + truth.size + ' 项默认值' +
            (intended ? '（其中 ' + intended + ' 项为已登记的有意变更）' : ''));
if (bad) { console.error('\n默认值不一致 ' + bad + ' 处 —— 手感会静默改变'); process.exit(1); }
console.log('✅ 其余默认值与配置化之前的字面量逐位相同');
