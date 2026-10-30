/*
 * check-fish-layers.js —— 水层数据的自检（2026-10-30 铅锤系统第一步）。
 *
 * 守四件事：
 *   ① `FishLayers.java` 与生成器输出**逐字节一致**（手改必红 —— 与 FishHabits 同一纪律）；
 *   ② json 里的鱼**一个不多一个不少**，且与 `FishSpecies` 的 47 种对得上；
 *   ③ 每条鱼都有层、层必须是合法 id（拼错会被当成"没这个鱼"）；
 *   ④ 四层的语言键 zh/en 都在（缺了就显示成键名）。
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const exists = (p) => fs.existsSync(path.join(ROOT, p));

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };

console.log('=== 水层数据检查 ===');

const data = JSON.parse(read('tools/fish-layers.json'));
const layerIds = data.layers.map((l) => l.id);

// ① 生成物逐字节比对
const { render, OUT } = require('./gen-fish-layers.js');
const onDisk = exists('src/main/java/com/whitecobalt/anglertackle/fishing/FishLayers.java')
  ? read('src/main/java/com/whitecobalt/anglertackle/fishing/FishLayers.java') : '';
if (!onDisk) fail('FishLayers.java 不存在 —— 先跑 node tools/gen-fish-layers.js --write');
else if (onDisk.replace(/\r\n/g, '\n') !== render().replace(/\r\n/g, '\n')) {
  fail('FishLayers.java 与生成器输出不一致 —— 有人手改了生成物（改 json 再跑生成器）');
} else ok('FishLayers.java 与生成器输出逐字节一致（' + path.basename(OUT) + '）');

// ② 鱼种对齐
const speciesSrc = read('src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java');
const speciesIds = new Set([...speciesSrc.matchAll(/\bspecies\("([a-z0-9_]+)"/g)].map((m) => m[1]));
const habitIds = new Set([...read('src/main/java/com/whitecobalt/anglertackle/fishing/FishHabits.java')
  .matchAll(/FISH\.put\("([a-z0-9_]+)"/g)].map((m) => m[1]));
const layerIds47 = new Set(Object.keys(data.fish));

if (layerIds47.size === 47) ok('水层表 47 条');
else fail('水层表只有 ' + layerIds47.size + ' 条（应为 47）');

const missHabit = [...habitIds].filter((id) => !layerIds47.has(id));
if (missHabit.length) fail('这些鱼有习性但没有水层：' + missHabit.join(', '));
else ok('与 FishHabits 的鱼种集合一致');

// ③ 值合法
const illegal = Object.entries(data.fish).filter(([, v]) => !layerIds.includes(v));
if (illegal.length) fail('层 id 非法：' + illegal.map(([k, v]) => k + '=' + v).join(', '));
else ok('47 条的层 id 都合法（' + layerIds.join(' / ') + '）');

const count = {};
for (const v of Object.values(data.fish)) count[v] = (count[v] || 0) + 1;
const empty = layerIds.filter((id) => !count[id]);
if (empty.length) fail('这些层一条鱼都没有（铅锤会完全无效）：' + empty.join(', '));
else ok('四层都有鱼：' + layerIds.map((id) => id + '=' + count[id]).join(' '));

// ④ 语言键
for (const lang of ['zh_cn', 'en_us']) {
  const json = JSON.parse(read('src/main/resources/assets/anglertackle/lang/' + lang + '.json'));
  const missing = data.layers.filter((l) => !json['layer.anglertackle.' + l.id]).map((l) => l.id);
  if (missing.length) fail(lang + ' 缺水层语言键：' + missing.join(', '));
  else ok(lang + ' 四条水层语言键齐全');
}

// ---- 铅锤倍率接线（2026-10-30 第 4 步）----
const strip = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
{
  const spec = read('src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java');
  const body = strip(spec);

  if (!/bonus\.layerBonus\(\) != null/.test(body)) {
    fail('buildPool 里没有应用铅锤倍率（layerBonus）—— 装了铅锤等于没装');
  } else {
    // 位置：必须在"解完 k"之后、"取整"之前
    const iK = body.indexOf('double k = (p * other) / ((1.0 - p) * hvWeight)');
    const iLayer = body.indexOf('bonus.layerBonus() != null');
    const iRound = body.indexOf('Math.max(1, Math.round(weights.get(i)))');
    if (iK < 0 || iLayer < 0 || iRound < 0) {
      fail('切不出"解 k / 铅锤 / 取整"三段 —— 判据失效（改过实现？）');
    } else if (!(iK < iLayer && iLayer < iRound)) {
      fail('铅锤倍率不在"解 k 之后、取整之前" —— 放前面会被 k 反解掉（净效果为零），放后面乘不动小数');
    } else {
      ok('铅锤倍率乘在"解 k 之后、取整之前"（不会被反解掉）');
    }
  }

  const session = strip(read('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java'));
  if (/rodKind\(\) == FishSpecies\.RodKind\.LURE[\s\S]{0,400}sinkerLayerBonus/.test(session)) {
    ok('只有路亚场次才挂铅锤倍率（台钓恒为 null）');
  } else fail('没有"仅路亚"门禁 —— 台钓也会被铅锤影响（用户定案铅锤仅路亚可用）');

  if (/public static final RollBonus NONE = new RollBonus\(0\.0, null, null\);/.test(spec)) {
    ok('RollBonus.NONE 不带层倍率（未装铅锤时与改造前逐字节一致）');
  } else fail('RollBonus.NONE 变了 —— 没装铅锤的路径会被莫名改动');

  // 6 种铅锤的倍率必须和策划案 §4 一致（顺序：浅/中/底/贴底）
  const items = strip(read('src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java'));
  const want = {
    sinker_g8: [1.30, 1.00, 1.00, 1.00],
    sinker_g2: [1.05, 1.20, 1.00, 1.00],
    sinker_3b: [1.00, 1.30, 1.00, 1.00],
    sinker_5b: [1.00, 1.05, 1.20, 1.00],
    sinker_03: [1.00, 1.00, 1.10, 1.10],
    sinker_05: [1.00, 1.00, 1.00, 1.30],
  };
  const wrong = [];
  for (const [id, nums] of Object.entries(want)) {
    const m = new RegExp('ITEMS\\.register\\("' + id + '"[\\s\\S]{0,260}?SinkerItem\\(partProps\\([^)]*\\)((?:\\s*,\\s*[0-9.]+)+)\\)').exec(items);
    if (!m) { wrong.push(id + '（找不到注册）'); continue; }
    const got = m[1].split(',').map((s) => s.trim()).filter((s) => s.length > 0).map(Number);
    if (got.length !== 4 || got.some((v, i) => Math.abs(v - nums[i]) > 1e-9)) {
      wrong.push(id + ' 期望 [' + nums.join(', ') + '] 实际 [' + got.join(', ') + ']');
    }
  }
  if (wrong.length) fail('铅锤倍率与策划案不一致：' + wrong.join('；'));
  else ok('6 种铅锤的倍率与策划案 §4 逐项一致');
}


console.log('');
console.log(bad === 0 ? '✅ 水层数据自洽（生成物未被手改、47 条齐全、四层都有鱼、语言键齐）' : '❌ 水层检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
