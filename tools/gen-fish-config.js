// 把"每条鱼的数值"配置化。
//
// ── 用法 ────────────────────────────────────────────────────
//   node tools/gen-fish-config.js            # 预览
//   node tools/gen-fish-config.js --write    # 写入
//
// 与配件那套（gen-part-config.js）同构，但**不需要**"先字面量后改写"的两步走：
// 鱼这边不在 FishSpecies.java 里就地替换数值，而是在 FishTuning 的生成区里
// 生成"按 id 读配置"的代码。FishSpecies.java 里的原始数值原样留着，
// 只作为**设计意图的记录**（tools/check-fish-config-parity.js 会逐项比对它们
// 与配置默认值一致，防止记录与实际悄悄分叉）。
//
// 幂等：所有注入都走"标记块替换"（先删旧块再插新块），可以反复运行。
const fs = require('fs'), path = require('path');

// 2026-10-29 修生成链：Config.java / Tuning.java 是 CRLF，而所有锚点字符串都写成 \n，
// 直接 includes 永远 false（症状：找不到锚点 / 找不到定义锚点 / 残渣）。
// 读入统一转 LF，写回按原换行还原 —— 这样既不改变工作区文件的换行，又能让锚点匹配。
const __EOL = new Map();
function readLF(p) {
  const raw = fs.readFileSync(p, 'utf8');
  const crlf = raw.includes('\r\n');
  __EOL.set(p, crlf);
  return crlf ? raw.replace(/\r\n/g, '\n') : raw;
}
function writeKeep(p, text) {
  fs.writeFileSync(p, __EOL.get(p) ? text.replace(/\n/g, '\r\n') : text, 'utf8');
}

const ROOT = path.join(__dirname, '..');
const WRITE = process.argv.includes('--write');

const cfgPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/Config.java');
const tunPath = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/fishing/Tuning.java');
const fishTunPath = path.join(ROOT,
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishTuning.java');
const speciesPath = path.join(ROOT,
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java');

const { parseFish, FIELDS, DESC } = require('./fish-source.js');
const fish = parseFish(fs.readFileSync(speciesPath, 'utf8'));
if (!fish.length) {
  throw new Error('在 FishSpecies.java 里没解析到任何鱼种 —— 解析器与源码写法脱节了');
}

const GROUP = 'fishStats';
const camel = (s) => s.replace(/_([a-z])/g, (_, c) => c.toUpperCase());

/**
 * 生成进 Java 的字符串必须转义双引号。
 *
 * ⚠️ 踩过的坑：中文说明里随手写了英文双引号（"持久战"），
 * 于是生成的 `.comment("...这是"持久战"强度的总开关...")` 在第二个引号处提前闭合，
 * 后面整段变成语法垃圾，Config.java 报出几十条"需要')'或','"。
 * 报错位置在生成出来的文件里，跟真正的原因（文案里的引号）隔着两层，很难往回追。
 *
 * 所以：① 中文文案统一用「」而不是 ""；② 这里再兜一道，把漏掉的转义掉。
 */
function safe(text) {
  if (text.includes('"')) {
    console.warn('⚠ 文案里有 ASCII 双引号，已自动转义（建议改用「」）：' + text.slice(0, 40) + '…');
    return text.replace(/"/g, '\\"');
  }
  return text;
}

// 生成配置项名：crucianCarp + MinKg → crucianCarpMinKg。
// 前缀用**鱼种 id** 而不是物品名，因为 id 是存档/图鉴的键，最稳定。
const fieldName = (f, field) => camel(f.id) + field.key.charAt(0).toUpperCase() + field.key.slice(1);

// ---------------------------------------------------------------- 数值格式化
// 配置默认值必须**逐位保真**：这是"改配置不影响默认手感"的唯一保证。
//
// ⚠️ 早先按字段精度截断（toFixed(decimals)），把 1.5625 写成 1.563、
// 3.4375 写成 3.438 —— 看着只是末位差，但那 4 项的手感已经和源码不一样了，
// 而 check-fish-config-parity.js 会逐项比对、当场报出来。
// 现在只做一件事：给整数补个 .0，其余一律用 JS 自己的最短往返表示
//（String(v) 产出的是能精确还原该 double 的最短字符串，不会多也不会少）。
function literal(v) {
  if (!Number.isFinite(v)) throw new Error('默认值不是有限数：' + v);
  if (Number.isInteger(v)) return v.toFixed(1);   // 80 → 80.0，读起来统一
  return String(v);                                // 1.5625 → 1.5625（保真）
}

const decl = [], defs = [], apply = [], getters = [], reads = [];

for (const f of fish) {
  const prefix = camel(f.id);
  const lines = [];
  lines.push('            next.put("' + f.id + '", new Values(');
  for (let i = 0; i < FIELDS.length; i++) {
    const fd = FIELDS[i];
    const name = fieldName(f, fd);
    decl.push('    public final ModConfigSpec.DoubleValue ' + name + ';');
    defs.push('        ' + name + ' = b',
              '                .comment("' + safe(f.name + '（' + f.id + '） · ' + fd.cn +
                (fd.unit ? '（' + fd.unit + '）' : '') + ' —— ' + DESC[fd.key]) + '")',
              '                .defineInRange("' + name + '", ' + literal(f.values[fd.key]) +
                ', ' + fd.min + ', ' + fd.max + ');');
    apply.push('        putDoubleValue(flat, "' + GROUP + '.' + name + '", c.' + name + ', counter);');
    getters.push('    public static double ' + name + '() { return cfg().' + name + '.get(); }');
    // FishTuning 的生成区里按 Values 的字段顺序读取
    lines.push('                    c.' + name + '.get()' + (i === FIELDS.length - 1 ? '));' : ','));
  }
  reads.push(lines.join('\n'));
}

const totalFields = fish.length * FIELDS.length;
console.log('鱼种 ' + fish.length + ' 条 × ' + FIELDS.length + ' 项 = 配置项 ' + totalFields + ' 项');

if (!WRITE) {
  console.log('\n--- 字段声明（前 4 行）---');
  decl.slice(0, 4).forEach((l) => console.log(l));
  console.log('--- 配置定义（前 3 行）---');
  defs.slice(0, 3).forEach((l) => console.log(l));
  console.log('--- FishTuning 读取（前 6 行）---');
  reads[0].split('\n').slice(0, 6).forEach((l) => console.log(l));
  console.log('\n--- 中文说明样例 ---');
  console.log(DESC.pullSpeed);
  console.log('\n（加 --write 写入三个文件）');
  process.exit(0);
}

// ---------------------------------------------------------------- 工具
/**
 * ⚠️⚠️ 绝对不要用 `str.replace(anchor, replacementString)` 注入生成的代码。
 *
 * String.replace 的**字符串**替换串会展开 `$` 特殊序列：
 *   $& 整个匹配、$' 匹配之后的所有内容、$` 匹配之前的所有内容、$1..$9 捕获组。
 * 生成的配置代码里只要出现 `$'`（例如中文注释里写「只影响 $' 之后咬钩的鱼」
 * 这种带单引号的写法），替换结果就会把**锚点之后的整篇配置**复制一遍插进来。
 *
 * 真实事故：鱼种那组的中文说明里有「**之后**咬钩的鱼」，
 * 而它前面紧跟一个 `$`，于是每次生成都在 Config.java 里插进 25 份重复的
 * `.push("fishStats")` 块 —— 编译期还看不出来，因为多出来的块语法完全合法，
 * 只是 273 个配置项被重复注册了 25 次。
 *
 * 所以：一律用**替换函数** `replace(anchor, () => text)`，函数返回值不做任何展开。
 * 另外在写盘前断言"注入的标志性字符串只出现一次"，把这类事故挡在生成期。
 */
function injectOnce(text, anchor, payload, marker) {
  if (!text.includes(anchor)) {
    throw new Error('找不到锚点：' + anchor.trim());
  }
  const before = text.split(marker).length - 1;
  const out = text.replace(anchor, () => anchor + payload);
  const after = out.split(marker).length - 1;
  if (after !== before + 1) {
    throw new Error('注入后「' + marker + '」出现了 ' + after + ' 次（预期 ' + (before + 1) +
                    ' 次）—— 替换串里的 $ 序列被展开了，生成的代码重复了');
  }
  return out;
}

/**
 * 删掉 [begin, end] 两个标记**以及它们之间的全部内容**，标记行本身也一并删掉。
 *
 * ⚠️ begin / end 都允许是**多行**（例如 "END 标记\n        b.pop();"）——
 * 生成块的边界本来就是三行：BEGIN 标记、END 标记、b.pop();。
 *
 * 早先这个函数用 `[^\n]*\n` 收尾，**只吃掉 END 那一行**、把紧跟的 b.pop() 留在原地。
 * 于是"删旧块 → 插新块"每跑一次就多出一个孤儿 b.pop()：
 * push/pop 深度从 0 涨到 1、2、3…，而配置项数量完全不变，看起来一切正常。
 * 症状是 fishStats 那组永远不闭合、把后面的分组吞成子表。
 * 为了这一行绕了很久，所以这里把 begin/end 当成**原样的多行字符串**处理：
 * 逐行转义，行首行尾允许空白 —— 空块、满块、单行标记都能正确工作。
 */
function stripBlock(text, begin, end) {
  const toRe = (s) => s.split('\n')
    .map((line) => '[ \\t]*' + line.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[ \\t]*')
    .join('\\r?\\n');
  const re = new RegExp('^' + toRe(begin) + '\\r?\\n[\\s\\S]*?^' + toRe(end) + '\\r?\\n?', 'm');
  if (!re.test(text)) return text;
  return text.replace(re, '');
}

/**
 * 写盘前的最后一道闸：生成块必须**恰好一份**。
 *
 * 为什么需要：这类故障的症状是"同一块内容每跑一次多一份"，而每一份单独看都
 * 语法合法、NeoForge 也照常加载 —— 不会崩、不会报错，只是配置文件越来越长、
 * 分组嵌套越来越深。等发现时文件已经乱了（真实踩过：跑了 4 轮变成 4 份，
 * Config.java 从 1700 行涨到 2800 行）。
 *
 * 所以在这里直接数标记出现次数，多一份就拒绝写入并报错。
 * 这比"事后修复"可靠得多：文件永远不会被写坏。
 */
function assertSingle(text, marker, label) {
  const n = text.split(marker).length - 1;
  if (n !== 1) {
    throw new Error(label + ' 出现了 ' + n + ' 次（预期正好 1 次）——\n' +
      '  说明「' + marker.trim() + '」这一块被重复生成了。\n' +
      '  最可能的原因：stripBlock 的标记常量与实际文件里的写法不一致（少个 ==== 、\n' +
      '  多了个空格之类），导致删旧块失败、每次都又插一份。\n' +
      '  文件**没有被写入**，可以先检查上面那句 stripBlock 的常量。');
  }
}

// ---------------------------------------------------------------- Config.java
//
// 幂等：只剥**自己的**块（鱼种声明 + 鱼种定义），然后重新插入。
//
// ⚠️ 这里**不能**用 generated-blocks.js 的 peelAll —— 那会把配件块一起剥掉，
// 而本脚本需要 `END 配件数值声明` 当插入锚点，剥掉就找不到锚点了（踩过）。
// peelAll 是"整文件重置"用的，只在需要从零重建时才调用：
//     先 peelAll（回到纯手写配置）→ 再依次跑配件生成器、鱼种生成器。
//
// 边界常量与插入时写出的那一串**逐字一致**（多行也照原样写）：
// 少一个 ==== 都会导致静默不删、每跑一次多一份。
const { stripExact, checkStructure, sweepOrphanShells } = require('./generated-blocks.js');
{
  let cur = readLF(cfgPath);
  let peeled = 0;
  for (let guard = 0; guard < 50; guard++) {
    // ⚠️ 地标要用**内容**来界定，不能把 BEGIN 标记当边界：
    // `push("fishStats")` 与 `BEGIN 鱼种数值定义` 是两行紧邻的文本，
    // 若 strip 从 BEGIN 起、到 END 止，那个 push 会被留在原地 ——
    // 剥完之后它就成了孤儿，把后面的 render 组套成它的子表（真踩过）。
    // 所以从 `push("组名");` 那一行起剥到 END + b.pop()。
    const a = stripExact(cur,
      '    // ==== BEGIN 鱼种数值声明（gen-fish-config.js 生成，勿手改）====',
      '    // ==== END 鱼种数值声明 ====');
    const b = stripExact(a.text,
      '                """).push("fishStats");\n        // ==== BEGIN 鱼种数值定义 ====',
      '        // ==== END 鱼种数值定义 ====\n        b.pop();');
    if (!a.hit && !b.hit) break;
    cur = b.text;
    peeled++;
  }
  if (peeled) console.log('剥掉旧的鱼种块 ×' + peeled);
  // 第二遍：清扫**鱼种的空壳块** —— 历史上标记丢失时留下的副本只剩
  // "段头注释 + 说明 + push"、一条 defineInRange 都没有。它们靠标记删不掉，
  // 会一直挂在文件里让行数每轮增长（表现为"项数不变但行数 +10"）。
  // ⚠️ 必须限定 onlyGroup='鱼种数值'：配件组的地标 push 要留着当插入锚点。
  const swept = sweepOrphanShells(cur, '鱼种数值');
  if (swept.removed) {
    cur = swept.text;
    console.log('清扫孤儿空壳 ×' + swept.removed);
  }
  if (!cur.includes('// ==== END 配件数值声明 ====')) {
    throw new Error('Config.java 里没有配件数值声明块 —— 本脚本要靠它当插入锚点。\n' +
                    '  请按顺序跑：gen-part-config.js 然后 gen-fish-config.js。\n' +
                    '  （Config.java 被整文件重置过的话，先跑 gen-part-config.js 重建配件块。）');
  }
  // 剥掉鱼种块之后应回到"只有配件块"的地标状态：那是**结构完整**的，
  // 配件块自带 BEGIN..END..b.pop() 三行，所以深度归零。
  // （踩过两次：先断言"必须剩 1"、再断言"必须归零"，都不对 —— 正确判据就是
  //  用 checkStructure 看它自洽，而不是猜一个具体数字。）
  const c = checkStructure(cur);
  if (!c.ok) {
    throw new Error('剥掉鱼种块后 Config.java 结构不完整：' + c.problems.join('；') +
                    '\n  正常应剩下"手写分组 + 配件数值块"，两者都自带收尾 pop。\n' +
                    '  先跑 gen-part-config.js 重建配件块。');
  }
  writeKeep(cfgPath, cur);
}
let cfg = readLF(cfgPath);
// ── 生成块的边界：BEGIN 标记 .. END 标记 .. b.pop(); 三行**作为一个整体** ──
//
// ⚠️ 这三行必须一起删、一起插，`b.pop();` 不能被留在块外。
// 它结束的是"这一组 TOML 表"；一旦落在 END 标记之后，后一个生成器往 END 后面
// 插内容时就会把它挤到更远，于是这一组**永远不闭合**，把后面所有分组吞成自己的子表。
// 真实事故：partsStats 吞掉了 273 项鱼种配置，调参器里 partsStats 显示 352 项、
// fishStats 整组消失；NeoForge 照常加载（嵌套 TOML 合法），TOML 里变成
// [partsStats.fishStats]，而 Tuning.reload 找的是 fishStats.* —— 那一组热重载全失效。
//
// 做法：把 pop 写进边界常量里，stripBlock 与 injectOnce 共用同一个常量，
// 就不会出现"删的时候不含 pop、插的时候含 pop"这种错位。
const FISH_BEGIN = '        // ==== BEGIN 鱼种数值定义 ====';
const FISH_END = '        // ==== END 鱼种数值定义 ====\n        b.pop();';
const PART_END = '        // ==== END 配件数值定义 ====\n        b.pop();';
// ⚠️ 声明块的 BEGIN/END 也必须**带结尾的 ====**。
// 这里漏过一次（写成 'BEGIN 鱼种数值声明'），于是 stripBlock 永远匹配不到、
// 每次都又插一份声明块，而定义块删得掉 —— 症状是"段头多一份、配置项却不多"，
// 极难从现象反推原因。判定方法与 gen-fish-config.js 里其余常量保持一致：
// 用**注入时原样写出的那串文本**，一个字都不省。
const DECL_BEGIN = '    // ==== BEGIN 鱼种数值声明（gen-fish-config.js 生成，勿手改）====';
const DECL_END = '    // ==== END 鱼种数值声明 ====';
cfg = stripBlock(cfg, DECL_BEGIN, DECL_END);
cfg = stripBlock(cfg, FISH_BEGIN, FISH_END);

// 声明：插在配件数值声明块之后
const declAnchor = '    // ==== END 配件数值声明 ====';
cfg = injectOnce(cfg, declAnchor, '\n\n' +
  '    // ==== BEGIN 鱼种数值声明（gen-fish-config.js 生成，勿手改）====\n' +
  decl.join('\n') + '\n    // ==== END 鱼种数值声明 ====',
  'public final ModConfigSpec.DoubleValue crucianCarpMinKg;');

// 定义：插在配件数值定义块之后
const defAnchor = PART_END;
cfg = injectOnce(cfg, defAnchor, '\n\n' + [
  '        // ---------------------------------------------------------- 鱼种数值',
  '        b.comment("""',
  '                鱼种数值 / Per-fish stats（每条鱼的体重与对抗手感）。',
  '                由 tools/gen-fish-config.js 从 FishSpecies.java 生成，默认值与代码里的原值一一对应，',
  '                所以不改这些项时手感和以前完全一样。',
  '                改完 /atl config reload 即生效：只影响之后咬钩的鱼，不影响正在遛的那条。',
  '                每条的 13 项含义相同：体重上下限、拉力（不挣扎/疲劳期）、体力、张力涨速、',
  '                冲刺时长与间隔、变向间隔、假动作概率、进疲劳的体力比例、疲劳持续、不挣扎时回气。',
  '                """).push("' + GROUP + '");',
  '        // ==== BEGIN 鱼种数值定义 ====',
].join('\n') + '\n' + defs.join('\n') + '\n        // ==== END 鱼种数值定义 ====\n        b.pop();',
  // ⚠️ 这块**自带收尾的 b.pop()**（末尾那行）。早先没有 ——
  // 那时鱼种内容接在配件块的 push 之下，fishStats 与 partsStats 变成同一个
  // TOML 组；剥掉鱼种块之后配件那个 push 就成了孤立的，深度永远剩 1，
  // 脚本无法幂等。现在两块各自 push/pop，是真正的兄弟组。
  'b.comment("""\n                鱼种数值 / Per-fish stats');
// 声明块也断言：它同样是一次性注入，重复了就是 strip 没匹配上
assertSingle(cfg, DECL_BEGIN, '鱼种数值声明块');
assertSingle(cfg, FISH_BEGIN, '鱼种数值定义块');
writeKeep(cfgPath, cfg);
console.log('✅ Config.java：' + decl.length + ' 个字段');

// ---------------------------------------------------------------- Tuning.java
//
// 同 gen-part-config：只登记，不写盘（见那边的注释）。
const TS = require('./tuning-source.js');
let tun = readLF(tunPath);
tun = TS.writeSection(tun, 'fishReload', apply);
tun = TS.writeSection(tun, 'fishGetter', getters);
const tunProblems = TS.checkSections(tun);
if (tunProblems.length) throw new Error('Tuning.java 生成块结构异常：' + tunProblems.join('；'));
writeKeep(tunPath, tun);
console.log('✅ Tuning.java：' + getters.length + ' 个 getter + ' + apply.length + ' 行热重载映射');

// ---------------------------------------------------------------- FishTuning.java
//
// 锚点判定用**带行首缩进和结尾 ==== 的完整标记**，不能用 '// ==== BEGIN 生成区' 这种前缀：
// 本文件的 Javadoc 里也写着 "从 {@code // ==== BEGIN 生成区} 到 ..." ——
// 用前缀判定的话，真标记被删掉之后 includes() 仍为真（命中的是 Javadoc），
// 守卫形同虚设，真正的故障会被掩盖成"写回失败"这种看不出原因的报错。（踩过。）
const FT_BEGIN = '            // ==== BEGIN 生成区（gen-fish-config.js，勿手改）====';
const FT_END = '            // ==== END 生成区 ====';
{
  let ft = readLF(fishTunPath);
  const hasBegin = ft.includes(FT_BEGIN);
  const hasEnd = ft.includes(FT_END);
  if (!hasBegin || !hasEnd) {
    throw new Error('FishTuning.java 的生成区标记不完整（BEGIN=' + hasBegin + ' END=' + hasEnd + '）。\n' +
                    '  · 正常情况下模板里就该有这两行（12 空格缩进，不要改动）：\n' +
                    '        ' + FT_BEGIN + '\n' +
                    '        ' + FT_END + '\n' +
                    '  · 请把它们加回 build() 里 Map<String, Values> next 那行之后，再重跑本脚本。');
  }
  const body = ft.slice(ft.indexOf(FT_BEGIN) + FT_BEGIN.length, ft.indexOf(FT_END));
  if (body.trim() && !/next\.put\("/.test(body)) {
    throw new Error('FishTuning.java 的生成区里有无法识别的内容，不敢覆盖：\n' +
                    body.split('\n').slice(0, 4).join('\n'));
  }
  const re = new RegExp(
    FT_BEGIN.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[\\s\\S]*?' +
    FT_END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
  // 用替换函数（不做 $ 展开）
  ft = ft.replace(re, () => FT_BEGIN + '\n' + reads.join('\n\n') + '\n' + FT_END);
  writeKeep(fishTunPath, ft);
  const back = readLF(fishTunPath);
  if (!back.includes(FT_BEGIN) || !back.includes(FT_END) || !/next\.put\("crucian_carp"/.test(back)) {
    throw new Error('FishTuning.java 写回后自检失败');
  }
}
console.log('✅ FishTuning.java：' + fish.length + ' 条鱼的读取');

console.log('\n下一步：node tools/gen-tuning-schema.js 然后 node tools/build-tuning-tool.js');
