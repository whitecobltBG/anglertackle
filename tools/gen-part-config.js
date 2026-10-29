// 把"配件数值"配置化：从 ModItems.java 抽数值 → 生成 Config 字段/getter/热重载映射，
// 再把 ModItems 里的字面量换成 Tuning.xxx() 调用。
//
// 为什么用生成器而不是手写：59 个属性值 + 20 个耐久散在 20 个物品定义里，
// 手抄必然错一两个，而错的表现是"某个配件悄悄变了"——极难发现。
//
// ── 用法（顺序很重要）────────────────────────────────────────
//   node tools/gen-part-config.js --write      # 从 ModItems 抽数值，写 Config.java / Tuning.java
//   node tools/rewrite-part-stats.js --write   # 把 ModItems 的字面量换成 Tuning 调用
//
// ⚠️ 必须先跑本脚本再跑 rewrite：本脚本要读 ModItems 里的**字面量**，
//    一旦被改写成 Tuning.xxx() 就抽不到数值了（踩过：报"参数个数 1 与预期 5 不符"）。
//    重跑时必须先把 ModItems 还原成字面量版。
//
// ── 幂等性 ──────────────────────────────────────────────────
//   本脚本对 Config.java / Tuning.java 的注入全部走"标记块替换"（先删旧块再插新块），
//    所以可以反复运行。踩过的坑：早先 apply 段是直接 append，跑两次就出现两份
//    同一个配置项，编译能过、行为一样，但文件越跑越长。
const fs = require('fs'), path = require('path');
const ROOT = path.join(__dirname, '..');
const WRITE = process.argv.includes('--write');

const modItemsPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java');
const configPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/Config.java');
const tuningPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/fishing/Tuning.java');

// ---------------------------------------------------------------- 抽取
const src = fs.readFileSync(modItemsPath, 'utf8');

const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
const shortId = (id) => id.replace(/_(line|reel|hook|lure)$/, '');

// 解析口径与 rewrite-part-stats.js 共用（tools/part-source.js）。
// ⚠️ 不要在这里另写一份正则：两边作用域规则一旦不同就会整体错位，
//    每个配件拿到前一个配件的数值，而且成对出现的数据互相自洽、查不出来。
// ---------------------------------------------------------------- 中文标注
// 配置键必须是 ASCII 驼峰（TOML 键、热重载路径、玩家手改都要用它），
// 但 "hempTensionCeiling" 这种名字看不出是哪个配件的哪个属性。
// 所以每条注释都写成「中文名 · 属性 = 作用」的形式，调参器直接显示它。
//
// ⚠️ 档位阶梯是 1–6 = 白绿蓝紫金红（不是 0–5）。
//   判据在 PartStats.partTier() 的消费方：Quality.of(stats.partTier() - 1)，
//   而 Quality 的序号 0 就是白。亮片写 2 出来的名字是绿的，也印证 1 起。
//   ModItems 里那句"tier = 品质下限（0 白 1 绿 …）"是**错的**，已一并改正。
const STAT_CN = {
  tensionCeiling: ['张力上限', '越高，线越不容易被拉崩'],
  tensionGen: ['张力涨速', '越小，收线时张力涨得越慢（越小越安全）'],
  tensionRecover: ['张力回复', '越大，放线时张力落得越快（越大越安全）'],
  lineLength: ['线长', '相对麻线的倍率，决定抛投射程上限'],
  pullIdle: ['回拉速度（不挣扎）', '鱼不挣扎时收线拉回多快'],
  pullStruggle: ['回拉速度（挣扎）', '鱼挣扎时收线拉回多快'],
  staminaDrain: ['体力削减', '越大，鱼掉体力越快（越大越好钓）'],
  damping: ['压制阻尼', '抑制鱼回气；高于 1 的部分另有转化'],
  windowBonus: ['提竿窗口加成', '单位是秒，加到基础提竿窗口上'],
  tier: ['品质档位', '1白 2绿 3蓝 4紫 5金 6红'],
};
const TIER_CN = { 1: '白', 2: '绿', 3: '蓝', 4: '紫', 5: '金', 6: '红' };

const { parseParts, KEYS, zhName } = require('./part-source.js');

// ── 先判断源码处在哪个阶段 ────────────────────────────────────
// 这个脚本要求 ModItems.java 是**数字字面量**版（它要从字面量里抽默认值）。
// 但 tools/rewrite-part-stats.js 会把那些字面量改写成 Tuning.xxx()，而那个改动
// 不可逆 —— 改写之后本脚本再跑就只会报"参数不是数字字面量"然后失败。
//
// 这曾经让 tools/build-deploy.ps1 的 2/6 步在第二次构建时直接挂掉：
// 生成链是"一次性"的，而构建是反复跑的。修法不是让人记得先还原，
// 而是让本脚本**认出"已经生成过了"并干净跳过**。
const probe = parseParts(src, { literals: false });
const alreadyDone = probe.length > 0 &&
                    probe.every((p) => p.args.every((a) => /^Tuning\.\w+\(\)$/.test(a.trim())));

// 需要"重建"的两条路径：
//   ① ModItems 还是字面量版 → 从字面量生成（下面走正常流程）
//   ② ModItems 已是 Tuning 版、但 Config.java 里的配件段被剥掉了
//      → 从 tools/part-config-snapshot.json 重建
// ② 是必须有的：确定性重建（peelAll）每次都会把配件段剥干净再重拼，
// 如果这时只会报"生成到一半"，整条链就跑不通了。
const SNAP = path.join(__dirname, 'part-config-snapshot.json');
let rebuildFromSnapshot = false;
if (alreadyDone) {
  const cfgText = fs.readFileSync(configPath, 'utf8');
  const hasPartBlock = cfgText.includes('// ==== BEGIN 配件数值定义 ====');
  if (hasPartBlock) {
    const i0 = cfgText.indexOf('// ==== BEGIN 配件数值定义 ====');
    const i1 = cfgText.indexOf('// ==== END 配件数值定义 ====');
    const block = cfgText.slice(i0, i1);
    const missing = [];
    for (const p of probe) {
      const prefix = p.id.replace(/_(line|reel|hook|lure)$/, '');
      const camel = (x) => x.replace(/_([a-z])/g, (_, c) => c.toUpperCase());
      if (!block.includes(camel(prefix + '_durability') + ' = b')) missing.push(p.id);
    }
    if (missing.length) {
      throw new Error('ModItems.java 已改写，但 Config.java 里缺这些配件的配置项：' +
                      missing.join(', ') + '\n无法从快照安全重建（快照只覆盖整套）。');
    }
    console.log('已生成过：ModItems.java 是 Tuning 调用形式，Config.java 里 ' +
                probe.length + ' 个配件的配置项齐全 —— 跳过。');
    console.log('（要改数值：改配置文件即可，游戏里 /atl config reload 生效。）');
    console.log('（要新增/删除配件：先从 参考资料/_before_partcfg/ 还原成字面量版再跑本脚本。）');
    process.exit(0);
  }
  // 配件段不在 → 从快照重建
  if (!fs.existsSync(SNAP)) {
    throw new Error('ModItems.java 已经是 Tuning 形式，Config.java 里没有配件段，' +
                    '且找不到重建快照 tools/part-config-snapshot.json。\n' +
                    '请从 参考资料/_before_partcfg/ 还原三个文件后重跑（走字面量生成路径）。');
  }
  rebuildFromSnapshot = true;
  console.log('Config.java 里配件段缺失，将从快照重建（' + probe.length + ' 个配件）');
}

// ⚠️ 从快照重建时**不能**用 literals 模式解析 ModItems —— 那时它已经是
// `Tuning.xxx()` 调用形式了，解析器会抛"参数不是数字字面量"（踩过）。
// 快照路径只需要 probe（数一下有几个配件），字面量路径才需要真值。
let parts = [];
if (rebuildFromSnapshot) {
  parts = probe.map((p) => ({
    id: p.id, name: p.name, durability: p.durability, kind: p.kind, args: p.args,
  }));
} else {
  const parsed = parseParts(src, { literals: true });
  parts = parsed.map((p) => ({
    id: p.id, name: p.name, durability: p.durability, kind: p.kind, args: p.args,
  }));
}
if (!parts.length) {
  throw new Error('在 ModItems.java 里没抽到任何配件 —— 解析器与源码写法脱节了。');
}
console.log('配件 ' + parts.length + ' 个（' +
            (rebuildFromSnapshot ? '来自快照重建' : '从源码字面量解析') + '）');
{
  // 中文名覆盖自检：lang 文件里缺条目时退回 id，肉眼看不出问题，
  // 但调参器上会出现一串英文 —— 那正是这次要消灭的东西。
  const noZh = parts.filter((p) => p.name === p.id);
  if (noZh.length) {
    console.warn('⚠ 这些配件在 zh_cn.json 里没有中文名，调参器只能显示 id：' +
                 noZh.map((p) => p.id).join(', '));
  }
}

// ---------------------------------------------------------------- 生成
// ⚠️ group 必须是 Config.java 里 .push("...") 的真实段名，不能按 kind 造一个。
// 热重载映射走的是 "段名.键名" 的 TOML 路径，段名写错 → map.get() 返回 null →
// 那一项静默不生效。踩过：这里写成 kind 派的 lines/reels/hooks/lures，
// 而配置里只有 [parts_stats]，59 个属性项全部热重载失效（改完没反应，
// 只有重启才生效，看起来像"随机失灵"）。
const GROUP = 'partsStats';

const decl = [], defs = [], apply = [], getters = [];

if (rebuildFromSnapshot) {
  // ── 从快照重建 ────────────────────────────────────────────
  // 快照里存的就是"每个字段：字段名 / 配置键 / 默认值 / 上下限 / 类型 / 中文说明"，
  // 与从字面量生成出来的内容等价 —— 所以重建的结果与"从字面量重跑一遍"逐字节一致。
  // 断言这一点：重建前后字段数必须相同，且每个字段的键与快照一致。
  const snap = JSON.parse(fs.readFileSync(SNAP, 'utf8'));
  for (const f of snap.fields) {
    const isInt = f.type === 'int';
    decl.push('    public final ModConfigSpec.' + (isInt ? 'IntValue' : 'DoubleValue') + ' ' + f.name + ';');
    defs.push('        ' + f.name + ' = b',
              '                .comment("' + f.comment + '")',
              '                .defineInRange("' + f.key + '", ' + f.def + ', ' + f.min + ', ' + f.max + ');');
    apply.push('        ' + (isInt ? 'putIntValue' : 'putDoubleValue') + '(flat, "' + GROUP + '.' + f.key +
               '", c.' + f.name + ', counter);');
    getters.push('    public static ' + (isInt ? 'int' : 'double') + ' ' + f.name +
                 '() { return cfg().' + f.name + '.get(); }');
  }
  if (decl.length !== snap.count) {
    throw new Error('快照重建字段数不符：' + decl.length + ' != ' + snap.count);
  }
  console.log('从快照重建 ' + decl.length + ' 项');
} else {
for (const p of parts) {
  const keys = KEYS[p.kind];
  if (!keys || keys.length !== p.args.length) {
    throw new Error(p.id + ' 参数个数 ' + p.args.length + ' 与预期 ' +
                    (keys ? keys.length : '?') + ' 不符');
  }
  const prefix = shortId(p.id);
  // 注释里的属性名带 + （中文名 · 属性 —— 作用），调参器直接显示这段文字。
  const tag = (label, desc) => p.name + '（' + p.id + '） · ' + label + ' —— ' + desc;

  // 耐久：注册期值，但仍生成配置项（可见 + 便于将来放开）
  const durField = camel(prefix + '_durability');
  decl.push('    public final ModConfigSpec.IntValue ' + durField + ';');
  defs.push('                ' + durField + ' = b',
            '                        .comment("' +
              tag('耐久上限', '用一次扣一点，扣光配件就损坏。⚠ 注册期固定，改它必须重启游戏') +
              '")',
            '                        .defineInRange("' + durField + '", ' + p.durability + ', 0, 100000);');
  // IntValue 必须配 putIntValue：putDoubleValue 收的是 ConfigValue<Double>，
  // 传 IntValue 进去编译就报"不兼容的类型"
  apply.push('        putIntValue(flat, "' + GROUP + '.' + durField + '", c.' + durField + ', counter);');

  for (let i = 0; i < keys.length; i++) {
    const key = keys[i], expr = p.args[i], f = camel(prefix + '_' + key);
    // expr 是**源码原文**（可能是算式 35.0 / 30.0），原样写进 defineInRange
    // 以保持"默认值与搬之前逐位相同"（check-part-config-parity.js 会逐项核对）。
    const [label, desc] = STAT_CN[key];
    // tier 是 PartStats 里的 int 字段，其余是 double
    const isInt = key === 'tier';
    if (isInt) {
      decl.push('    public final ModConfigSpec.IntValue ' + f + ';');
      defs.push('                ' + f + ' = b',
                '                        .comment("' + tag(label, desc) + '")',
                '                        .defineInRange("' + f + '", ' + expr + ', 1, 6);');
      apply.push('        putIntValue(flat, "' + GROUP + '.' + f + '", c.' + f + ', counter);');
      getters.push('    public static int ' + f + '() { return cfg().' + f + '.get(); }');
    } else {
      decl.push('    public final ModConfigSpec.DoubleValue ' + f + ';');
      defs.push('                ' + f + ' = b',
                '                        .comment("' + tag(label, desc) + '")',
                '                        .defineInRange("' + f + '", ' + expr + ', 0.0D, 100.0D);');
      apply.push('        putDoubleValue(flat, "' + GROUP + '.' + f + '", c.' + f + ', counter);');
      getters.push('    public static double ' + f + '() { return cfg().' + f + '.get(); }');
    }
  }
}
}   // ← 结束 else（非快照重建路径）

console.log('配件 ' + parts.length + ' 个；属性 ' + parts.reduce((a, p) => a + p.args.length, 0) +
            ' 个；配置项 ' + decl.length + ' 项');

if (!WRITE) {
  console.log('\n--- 字段声明（前 6 行）---');
  decl.slice(0, 6).forEach((l) => console.log(l));
  console.log('--- getter（前 4 行）---');
  getters.slice(0, 4).forEach((l) => console.log(l));
  console.log('\n（加 --write 写入 Config.java / Tuning.java）');
  process.exit(0);
}

// ---------------------------------------------------------------- 工具
/**
 * 删掉 [begin, end] 两个标记之间的整段，**以及两个标记行本身**。
 *
 * ⚠️ 不能用"从 begin 的位置往后 indexOf(end)"：当生成区是空的（两个标记相邻）时，
 * end 就紧跟 begin，按那个算法会把 begin 那行留下、只删掉一半 ——
 * 结果文件里积攒出一堆孤立的 `====` 行，Config.java 直接被写坏（踩过）。
 * 也不能用"删完再判断标记还在不在"，那是自相矛盾的（stripBlock 的职责就是删标记）。
 *
 * 这里按**标记行的完整形式**匹配：begin/end 各吃掉一整行（含缩进与行尾），
 * 空块与满块都能正确工作。
 */
function stripBlock(text, begin, end) {
  // begin / end 允许是**多行**（生成块的边界本来就是
  // "BEGIN 标记 / END 标记 / b.pop();" 三行）。逐行转义、行首行尾允许空白。
  // 早先用 `[^\n]*\n` 收尾，只吃掉 END 那一行、把 b.pop() 留成孤儿，
  // 每跑一次深度就 +1（详见 gen-fish-config.js 里同名函数的注释）。
  const toRe = (s) => s.split('\n')
    .map((line) => '[ \\t]*' + line.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[ \\t]*')
    .join('\\r?\\n');
  const re = new RegExp('^' + toRe(begin) + '\\r?\\n[\\s\\S]*?^' + toRe(end) + '\\r?\\n?', 'm');
  if (!re.test(text)) return text;
  return text.replace(re, '');
}

// ---------------------------------------------------------------- Config.java
//
// ── 确定性重建 ──────────────────────────────────────────────
// 这一步不从"当前文件"出发做增量修改，而是先把**所有**生成块剥干净
//（配件 + 鱼种，无论谁留下的、留了几份），只留手写配置，再从干净态插自己的块。
// 于是每次的输出只取决于「手写部分 + ModItems.java」，与文件的历史状态无关：
// 跑一百次和跑一次完全一样。
//
// 为什么必须这样：早期"就地删旧块再插新块"的写法，只要**一个**标记常量写得不一样
//（少个 ====、多个空格），就会静默不删、每跑一次多一份；而积压的副本一旦连标记都丢了，
// 就再也删不掉，只能不断写新修复器 —— 真实踩过，Config.java 从 1700 行涨到 2800 行。
const { peelAll, checkStructure, sweepOrphanShells } = require('./generated-blocks.js');
/** 本次读入的 Config.java 是否 CRLF（块内赋值、块外写回时要用）。 */
let FILE_CRLF = false;
{
  const beforeRaw = fs.readFileSync(configPath, 'utf8');
  // 2026-10-29: Config.java 是 CRLF，而所有锚点字符串都写成 \n，直接 includes 永远 false，
  // 会报「找不到定义锚点」。这里统一成 LF 处理，写回时再按原换行还原。
  FILE_CRLF = beforeRaw.includes('\r\n');
  const before = FILE_CRLF ? beforeRaw.replace(/\r\n/g, '\n') : beforeRaw;
  const r = peelAll(before);
  if (r.residue.length) {
    throw new Error('Config.java 剥除生成块后仍有残渣，不敢继续：\n  ' + r.residue.join('\n  ') +
                    '\n（残渣 = 有一份副本的标记丢了。人工看一眼，或从备份还原后重跑。）');
  }
  // peelAll 已经把两种空壳都清过了，但**鱼种块的地标**（push("fishStats") 那一行）
  // 是留给 gen-fish-config 当插入锚点的，所以这里只针对配件组再扫一次，
  // 防止历史上遗留的配件空壳让文件行数一轮一轮涨上去。
  const swept = sweepOrphanShells(r.text, '配件数值');
  let text = swept.text;
  if (swept.removed) console.log('清扫配件孤儿空壳 ×' + swept.removed);
  const c = checkStructure(text);
  if (!c.ok) {
    throw new Error('Config.java 剥除生成块后结构不完整：' + c.problems.join('；') +
                    '\n（手写部分本身的 push/pop 就不配对，先修手写部分。）');
  }
  if (r.log.length) console.log('剥除生成块：' + r.log.join('  '));
  fs.writeFileSync(configPath, FILE_CRLF ? text.replace(/\n/g, '\r\n') : text, 'utf8');
}
let cfg = fs.readFileSync(configPath, 'utf8').replace(/\r\n/g, '\n');
// 声明块没有 pop；定义块的边界含收尾的 b.pop();
const PART_DECL_END = '    // ==== END 配件数值声明 ====';
const PART_DEF_END = '        // ==== END 配件数值定义 ====\n        b.pop();';

const declAnchor = '    public final ModConfigSpec.IntValue lineDurabilityPerBreak;';
if (!cfg.includes(declAnchor)) throw new Error('Config.java 找不到字段锚点: ' + declAnchor);
cfg = cfg.replace(declAnchor, () => declAnchor + '\n\n' +
  '    // ==== BEGIN 配件数值声明（gen-part-config.js 生成，勿手改）====\n' +
  decl.join('\n') + '\n    // ==== END 配件数值声明 ====');

const defAnchor = '        lineDurabilityPerBreak = b.comment("断线时额外扣除的鱼线耐久")\n' +
                  '                .defineInRange("linePerBreak", 5, 0, 100);\n        b.pop();';
if (!cfg.includes(defAnchor)) throw new Error('Config.java 找不到定义锚点: ' + defAnchor);
cfg = cfg.replace(defAnchor, () => defAnchor + '\n\n' + [
  '        // ---------------------------------------------------------- 配件数值',
  // ⚠️ 文本块开引号后必须直接换行；写成 b.comment("""> 会报"起始分隔符序列非法"
  '        b.comment("""',
  '                配件数值 / Part stats（每个配件的属性）。',
  '                由 tools/gen-part-config.js 从 ModItems.java 生成，默认值与代码里的原值一一对应，',
  '                所以不改这些项时手感和以前完全一样。',
  '                耐久上限是注册期固定的，改它要重启游戏；其余属性热重载即生效。',
  '                """).push("' + GROUP + '");',
  '        // ==== BEGIN 配件数值定义 ====',
].join('\n') + '\n' + defs.join('\n') +
  // ⚠️ `b.pop();` 必须写在**生成块之内**（紧跟 END 标记），不能留在块外。
  // 它结束的是"配件这一组 TOML 表"；一旦它落在 END 标记之后，
  // 后一个生成器（gen-fish-config.js）往 END 标记后面插内容时就会把它挤到更后面，
  // 于是这个组永远不闭合，把后面所有分组吞成自己的子表。
  // 事故后果：调参器里 partsStats 显示 352 项、fishStats 整组消失，
  // 且 TOML 变成 [partsStats.fishStats]，那一组的热重载全部失效。
  // 所以生成块 = BEGIN 标记 .. END 标记 .. b.pop(); 这三行一起删、一起插，才不会被挤开。
  '\n        // ==== END 配件数值定义 ====\n        b.pop();');
fs.writeFileSync(configPath, FILE_CRLF ? cfg.replace(/\n/g, '\r\n') : cfg, 'utf8');
console.log('✅ Config.java：' + decl.length + ' 个字段');

// ---------------------------------------------------------------- Tuning.java
//
// 只**登记**内容，不写盘：块的位置由 tools/tuning-source.js 按 SECTIONS 顺序统一决定。
//
// 为什么不能在这里直接写：块的位置靠"前一个块的 END 标记"决定，而
// partsGetter 的前一个块是 gameReload（属于玩法生成器）—— 跑本脚本时它还不存在，
// 于是"怎么排顺序都会有某个生成器报错"。改成登记 + 统一提交后，顺序不再重要。
const TS = require('./tuning-source.js');
let tun = fs.readFileSync(tuningPath, 'utf8');
tun = TS.writeSection(tun, 'partsReload', apply);
tun = TS.writeSection(tun, 'partsGetter', getters);
tun = TS.writeSection(tun, 'partsHelper', [
  '/** double 专用：ConfigValue<Double>.set 要 Double 而不是 Number。 */',
  'private static void putDoubleValue(java.util.Map<String, Object> flat, String path,',
  '                                   net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Double> value,',
  '                                   int[] counter) {',
  '    Object v = flat.get(path);',
  '    if (!(v instanceof Number num)) { return; }',
  '    try { value.set(num.doubleValue()); value.clearCache(); counter[0]++; }',
  '    catch (Throwable t) { AnglersTackle.LOGGER.debug("[垂纶] 热重载跳过 {}：{}", path, t.toString()); }',
  '}',
  '',
  '/** int 专用（配件档位 tier）。 */',
  'private static void putIntValue(java.util.Map<String, Object> flat, String path,',
  '                                net.neoforged.neoforge.common.ModConfigSpec.ConfigValue<Integer> value,',
  '                                int[] counter) {',
  '    Object v = flat.get(path);',
  '    if (!(v instanceof Number num)) { return; }',
  '    try { value.set(num.intValue()); value.clearCache(); counter[0]++; }',
  '    catch (Throwable t) { AnglersTackle.LOGGER.debug("[垂纶] 热重载跳过 {}：{}", path, t.toString()); }',
  '}',
]);
const tunProblems = TS.checkSections(tun);
if (tunProblems.length) throw new Error('Tuning.java 生成块结构异常：' + tunProblems.join('；'));
fs.writeFileSync(tuningPath, tun, 'utf8');
console.log('✅ Tuning.java：' + getters.length + ' 个 getter + ' + apply.length + ' 行热重载映射');
console.log('\n下一步（顺序不能反）：node tools/rewrite-part-stats.js --write');
