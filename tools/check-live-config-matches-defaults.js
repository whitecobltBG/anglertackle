// 核对【游戏目录里实际生效的 toml】与【Config.java 的默认值】是否逐项一致。
//
// ── 这个检查的价值 ──────────────────────────────────────────
// "配置有没有被真正读到"是这套东西最容易出错的地方（踩过三次：
//   ① 逐件配件数值在注册期读配置 → 启动崩；
//   ② FishTuning.build() 忘了在启动时调用 → 所有鱼走兜底值、手感全软；
//   ③ 生成块重复注入 → 配置项被注册好几遍）。
// 玩家手动把配置重置成默认之后，toml 里的每一项都应当**等于**代码默认值。
// 任何一处不等，要么是有项目没被注册（NeoForge 不会给不存在的项写默认值），
// 要么是玩家改过。所以这是一条很强的端到端证据。
//
// 用法：node tools/check-live-config-matches-defaults.js
const fs = require('fs'), path = require('path');
const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..');

// ⚠ 负向测试（tools/negtest.ps1 把 ATL_ROOT 指向副本）时必须读**副本里的小样本 toml**：
//   这条检查读的是"游戏目录里的实况"，不注入就没法给它做负例 ——
//   负例会去读真实的那份配置，于是永远"通过"，而看着像"检查器抓不到问题"。
//   fake jar 故意不存在 → 走进"这批代码还没进过游戏"分支，缺项按 ⏳ 处理
//   （样本文件里当然不会写全 531 项）。
const NEGTEST = !!process.env.ATL_ROOT;
const TOML = NEGTEST
  ? path.join(ROOT, 'live-config.toml')
  : 'D:\\mc\\PCL 正式版 2.13.1.1\\.minecraft\\versions\\1.21.1-NeoForge_21.1.250\\config\\anglertackle-common.toml';
// ⚠⚠ jar 名**不许写死**（2026-10-11 修）。
// 这里原来硬编码成 `anglertackle-1.1.5.jar`（写它那天正好是 1.1.5），于是从 1.5.x 起
// 那个路径**永远不存在** ⇒ 下面的 statSync 抛 ENOENT ⇒ **每次都走"jar 尚未部署"那条宽松分支**
// ⇒ 这条守卫实际上瞎了几个月：症状是**全绿**，而它本该守的"新配置项到底有没有被游戏注册"
// 一直没在守（正是仓库最忌讳的"永远绿的检查器比没有检查器更坏"）。
// 正解是从 gradle.properties 取版本（唯一真源）拼出真正要看的那个 jar ——
// 与 check-build-scripts.js 同一条纪律："jar 名不许写死，否则下次改版本号就会静默看错文件"。
const MODS_DIR = 'D:\\mc\\PCL 正式版 2.13.1.1\\.minecraft\\versions\\1.21.1-NeoForge_21.1.250\\mods';
const MOD_VERSION = (() => {
  try {
    // ⚠ 键名是 `mod_version`（不是 `version`）—— 与 check-build-scripts.js 用**同一个**
    //   正则形状读同一个文件：版本不许有第二个来源。
    const m = /^\s*mod_version\s*=\s*(\d+\.\d+\.\d+)\s*$/m.exec(
      fs.readFileSync(path.join(ROOT, 'gradle.properties'), 'utf8'));
    return m ? m[1] : '';
  } catch (e) {
    return '';
  }
})();
if (!NEGTEST && !MOD_VERSION) {
  // 拼不出 jar 名 ⇒ 这条守卫会瞎。宁可**大声失败**，也不要静默退回宽松分支。
  console.error('❌ 读不到 gradle.properties 里的 version，无法确定该看哪个 jar（这条守卫会瞎）');
  process.exit(1);
}
const JAR = NEGTEST
  ? path.join(ROOT, 'live-config.jar')
  : path.join(MODS_DIR, 'anglertackle-' + MOD_VERSION + '.jar');
const CFG = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle/Config.java');

if (!fs.existsSync(TOML)) { console.error('❌ 读不到游戏里的 toml：' + TOML); process.exit(1); }

// ---- 0) 「toml 里缺一项」有两种完全不同的原因，必须先分清 ──────────────
//
//   ① 真的没注册 —— 这是这个检查要抓的 bug；
//   ② **toml 是上一次启动写的，比新 jar 还旧** —— 新加的配置项本来就还没机会被写进去，
//      NeoForge 会在下次启动时补上（默认值）。
//
// 分不清就会把"刚加了一个配置项"误判成"配置没注册"。判据用文件时间：
// toml 比 jar 旧 → 缺项算"⏳ 等下次启动"，不算失败；
// toml 比 jar 新（说明游戏已经用这个 jar 启动过、toml 被重写过）→ 缺项就是真问题。
//
// 第一次踩到：加 `gameplay.compassSearchRadius` 时，检查直接红着挡住了部署，
// 而那一项其实注册得好好的。若当时"把检查放宽"（比如缺项一律只警告），
// 这条端到端证据就废了 —— 所以正确的做法是**加一个能区分两种原因的判据**，
// 而不是降低判据强度。
let tomlOlderThanJar = false;
try {
  const t = fs.statSync(TOML).mtimeMs;
  const j = fs.statSync(JAR).mtimeMs;
  tomlOlderThanJar = t < j;
  console.log('toml 改动时间 ' + new Date(t).toLocaleString()
    + '，jar 改动时间 ' + new Date(j).toLocaleString()
    + ' → ' + (tomlOlderThanJar ? 'toml 比 jar 旧（游戏还没用这个 jar 启动过）' : 'toml 已是最新'));
} catch (e) {
  // ⚠️ 「读不到 jar」有两种成因，不能都当成"真问题"：
  //   ① jar 在，但 stat 失败（权限/路径写错）→ 判据缺失，按最严处理，缺项算真问题；
  //   ② **目标 jar 根本还没部署**（文件不存在）→ 与"toml 比 jar 旧"是**同一件事**：
  //      这批代码还没进过游戏，新加的配置项当然还没被写进 toml，
  //      NeoForge 会在下次启动时补上。
  //
  // 真实踩到（2026-10-01）：本地 jar 已经是 1.0.1，而游戏 mods 里仍是 1.0.0
  //（本轮是 `build-deploy.ps1 -NoDeploy`，按约定不部署）。于是"目标 jar 不存在"，
  // 判据落进 ① 分支，65 个新加的鱼种配置项被全部报成"没被注册" —— 而它们其实
  // 只是还没机会被写进 toml。降判据强度（缺项一律警告）是错的；正确做法是
  // 把"jar 尚未部署"识别出来，等价于"toml 比 jar 旧"。
  const missingJar = e && (e.code === 'ENOENT');
  if (missingJar) {
    tomlOlderThanJar = true;
    console.log('⚠ 目标 jar 尚未部署（' + JAR + '）—— 等价于"toml 比 jar 旧"：');
    console.log('   当前这批代码还没进过游戏，新加的配置项等下次启动时由 NeoForge 补写。');
    console.log('   已有的项仍然逐项比对（数值漂移照样报错）。');
  } else {
    console.log('⚠ 读不到 jar 时间（' + e.message + '），缺项一律按真实问题处理');
  }
}
console.log('');

// ---- 1) Config.java 的默认值（按 组.键 建立索引）
const src = fs.readFileSync(CFG, 'utf8');
const defaults = new Map();
let unparsed = 0;
// 逐个 push 分组切块，再在块里找 defineInRange
const pushRe = /\.push\("(\w+)"\)/g;
const marks = [];
let m;
while ((m = pushRe.exec(src))) marks.push({ id: m[1], at: m.index, end: m.index + m[0].length });
for (const mk of marks) {
  const pop = src.indexOf('b.pop()', mk.end);
  const body = src.slice(mk.end, pop < 0 ? src.length : pop);
  for (const stmt of body.split(';')) {
    const s = stmt.replace(/(\d)[DdFfLl](?![0-9A-Za-z_])/g, '$1');
    const d = /\.defineInRange\("([^"]+)",\s*([-+*/(). 0-9]+?)\s*,/.exec(s);
    if (d) {
      // ⚠⚠ 这里原先是 `Function('"return (' + d[2] + ')"')()` ——
      //   那个函数体**只是一个字符串字面量**（没有 return 语句），于是每一项都返回
      //   undefined，而下面的 `Math.abs(got - want) > 1e-9` 与 undefined/NaN 比较
      //   **恒为 false** —— 整个"数值比对"是空转的，只有"toml 里缺不缺这一项"
      //   那一半在工作。2026-10-02 加 bite 的 8 项时才暴露（报错信息里出现了
      //   "默认 undefined"）。永远绿的检查器比没有检查器更坏，所以修好之后
      //   再加一道保险：解析不出有限数就当场报错，不许静默变成 undefined。
      const v = Function('return (' + d[2] + ')')();
      if (!Number.isFinite(v)) {
        console.error('❌ 检查器自身失效：解析不出 ' + mk.id + '.' + d[1]
          + ' 的默认值（原文 ' + JSON.stringify(d[2]) + '）');
        unparsed++;
        continue;
      }
      defaults.set(mk.id + '.' + d[1], v);
      continue;
    }
    const b = /\.define\("([^"]+)",\s*(true|false)\)/.exec(s);
    if (b) defaults.set(mk.id + '.' + b[1], b[2] === 'true');
  }
}

// ---- 2) toml 里的实际值
/** 解析一份 toml → Map("分组.键" -> 原始字符串值)。 */
function parseToml(file) {
  const map = new Map();
  let group = null;
  for (const raw of fs.readFileSync(file, 'utf8').split('\n')) {
    const line = raw.trim();
    if (line.startsWith('[') && line.endsWith(']')) { group = line.slice(1, -1).trim(); continue; }
    if (!group || line.startsWith('#') || !line) continue;
    const eq = line.indexOf('=');
    if (eq <= 0) continue;
    const key = line.slice(0, eq).trim();
    let val = line.slice(eq + 1).trim();
    const hash = val.indexOf('#');
    if (hash >= 0) val = val.slice(0, hash).trim();
    map.set(group + '.' + key, val);
  }
  return map;
}
const live = parseToml(TOML);

// ---- 3) 比对
console.log('Config.java 默认值：' + defaults.size + ' 项');
console.log('toml 里实际有：' + live.size + ' 项');
console.log('');

// ⚠ 「值不一样」有两种完全不同的原因，混在一起会让这条检查变成"挡路的"：
//   ① 代码里**有意**改了默认值，而老 toml 还压着旧值 —— 真问题，去跑
//      tools/patch-config-changed-defaults.js（那一步只在 toml 值 == 旧默认值时才替换）。
//   ② 玩家（或调参器）自己调过的值 —— **不是问题**：调参器里那个"批量 ×1.5"
//      默认倍数就是为了调手感用的，用户 2026-10-02 就用它把所有鱼的拉力 ×1.5 了。
//   ②如果也算失败，这条检查会在任何一次调参之后永久挡住部署，人就会去把它关掉 ——
//   那才是真的失去它。所以旧默认值从**有意变更的登记表**里读（下面这个 Map），
//   命中它才算失败，其余只提示。
//
//   （这个区分是 2026-10-02 修好"默认值解析"之后立刻需要的：修好之前这条检查的
//     数值比对是空转的，21 项 ×1.5 的鱼拉速一直没人看见。）
// ⚠ 一个键可能有过**好几代**旧默认值（例：rodIronMaxWeightKg 20 → 25 → 30）。
//   这里原来用 Map<键, 单个旧值> + `set`，后一条会把前一条**覆盖掉** ——
//   于是"toml 里压着最早那代 20.0"就再也不算 stale，被当成"玩家自己调过的值"静默放过 ✗。
//   2026-10-08 就是负例 livecfg-stale-dotzero（正是为 20.0 写的那条）把它抓出来的。
//   现在每个键存**一列**旧默认值，命中任何一个都算 stale。
const staleDefaults = new Map();   // 裸键名 -> [有意变更之前的旧默认值, ...]
try {
  const patchSrc = fs.readFileSync(path.join(ROOT, 'tools/patch-config-changed-defaults.js'), 'utf8');
  for (const m of patchSrc.matchAll(/\['(\w+)',\s*'([^']+)',\s*'([^']+)'/g)) {
    if (!staleDefaults.has(m[1])) staleDefaults.set(m[1], []);
    staleDefaults.get(m[1]).push(m[2]);
  }
} catch (e) {
  console.error('❌ 读不到 tools/patch-config-changed-defaults.js（旧默认值表）：' + e.message);
  process.exit(1);
}

let notLive = 0, differs = 0, extra = 0, pending = 0, tuned = 0;

/**
 * toml 里的 double 写法不统一：NeoForge 可能写 `20.0`，而登记表里写的是 `20`。
 * 只做字符串比较的话，"压着旧默认值"会被误判成"玩家调过的值"——**静默漏报**，
 * 而且理由看着完全合理（"看着是调参器/玩家自己调过的值"）。
 * 2026-10-02 就是这么漏掉发布包里那份默认配置的（那里正是 `20.0`）。
 * 所以两边都能解析成数字时按数值比。
 */
const sameValue = (a, b) => {
  const sa = String(a).trim(), sb = String(b).trim();
  if (sa === sb) return true;
  if (sa === '' || sb === '') return false;
  const x = Number(sa), y = Number(sb);
  return Number.isFinite(x) && Number.isFinite(y) && Math.abs(x - y) < 1e-9;
};

for (const [k, want] of defaults) {
  if (!live.has(k)) {
    if (tomlOlderThanJar) {
      // 新加的项，还没机会被写进 toml
      console.log('⏳ toml 里还没有 ' + k + '（默认 ' + want + '）—— 下次启动游戏时 NeoForge 会补上');
      pending++;
    } else {
      console.error('❌ toml 里没有这一项：' + k + '（默认 ' + want + '）—— 游戏已经用当前 jar 启动过，'
        + '说明它**没被注册**（NeoForge 不会给没注册的项写默认值）');
      notLive++;
    }
    continue;
  }
  const raw = live.get(k);
  const got = typeof want === 'boolean' ? raw === 'true' : Number(raw);
  if (typeof want === 'boolean') {
    if (got !== want) { console.error('❌ ' + k + '：toml=' + raw + ' 默认=' + want); differs++; }
  } else if (!Number.isFinite(got) || Math.abs(got - want) > 1e-9) {
    const bare = k.slice(k.lastIndexOf('.') + 1);
    const stale = staleDefaults.get(bare);
    const diff = Number.isFinite(got) ? '（差 ' + (got - want).toFixed(6) + '）' : '';
    if (stale !== undefined && stale.some((v) => sameValue(raw, v))) {
      console.error('❌ ' + k + '：toml=' + raw + ' 默认=' + want + diff
        + ' —— toml 里压着**有意变更之前的旧默认值**（登记表里的 [' + stale.join(', ') + ']），'
        + '代码里的新默认值根本没生效。'
        + '跑 node tools/patch-config-changed-defaults.js --write 把它更新过来。');
      differs++;
    } else {
      console.log('⚠ ' + k + '：toml=' + raw + ' 默认=' + want + diff
        + ' —— 看着是调参器/玩家自己调过的值，不算问题（代码默认值没有被压住）');
      tuned++;
    }
  }
}
let stale = 0;
for (const k of live.keys()) {
  if (!defaults.has(k)) {
    // 2026-10-30：陈旧键**只提示不计失败** —— 删掉配置项（例如鱼钩）后 toml 里必然留残留，
    // 那不影响运行（NeoForge 会忽略），把它当红等于逼人去改玩家的配置文件。
    console.warn('⚠ toml 里有、代码里没有（陈旧键，可忽略）：' + k);
    stale++;
  }
}

// ⏳ 的项**不算失败** —— 它们只是还没轮到被写进文件。
// 但也绝不能静默略过：这里把它们数出来，让人一眼看到"有几项等着生效"。
// ---- 4) 发布包要带的那份默认配置（仓库根的 anglertackle-common.toml）----------------
//
// 它是给"还没跑过游戏"的玩家直接覆盖过去用的，所以**必须与代码默认值逐项一致** ——
// "玩家自己调过"那个豁免在这里不适用 ✗。
//
// ⚠ 2026-10-08 为什么加这一条：那份文件当时停在 10/07（缺 19 条新鱼的键、旧值也没跟上），
//   而生成命令 `sync-config-file.js --out` 每次都说"无需改"（那个早期 return 不写输出，
//   已一并修掉）。于是"发布包发出去的默认配置是错的"这件事**没有任何检查在看**。
//   用户 2026-10-02 也踩过同一类（发布包里那份的铁竿承重还是 20.0）。
const RELEASE_TOML = path.join(ROOT, 'anglertackle-common.toml');
let releaseBad = 0;
if (fs.existsSync(RELEASE_TOML)) {
  const rel = parseToml(RELEASE_TOML);
  for (const [k, want] of defaults) {
    if (!rel.has(k)) {
      console.error('❌ 发布用默认配置缺这一项：' + k + '（默认 ' + want + '）'
        + ' —— 跑 node tools/reset-release-defaults.js');
      releaseBad++;
      continue;
    }
    const raw = rel.get(k);
    const got = typeof want === 'boolean' ? raw === 'true' : Number(raw);
    const same = typeof want === 'boolean' ? got === want
      : (Number.isFinite(got) && Math.abs(got - want) < 1e-9);
    if (!same) {
      // ⚠ 修法**必须**是 reset-release-defaults.js，不是 sync-config-file.js：
      //   后者（正确地）只补缺失的键、**从不改已有键的值**（玩家的调参值要留住），
      //   所以它对"值漂移"这一类**一点用都没有** —— 2026-10-11 我就是照着旧提示敲的它，
      //   敲完错误原样还在 ✗。提示指错命令 = 下一个人再踩一次。
      console.error('❌ 发布用默认配置与默认值不同：' + k + '：文件=' + raw + ' 默认=' + want
        + ' —— 跑 node tools/reset-release-defaults.js');
      releaseBad++;
    }
  }
  for (const k of rel.keys()) {
    if (!defaults.has(k)) { console.warn('⚠ 发布用默认配置里有、代码里没有（陈旧键）：' + k); }
  }
  if (!releaseBad) {
    console.log('✅ 发布用默认配置（' + path.relative(ROOT, RELEASE_TOML).replace(/\\/g, '/')
      + '）与代码默认值逐项一致（' + defaults.size + ' 项）—— 可以直接覆盖给玩家用');
  }
} else {
  console.log('⚠ 仓库根没有 anglertackle-common.toml（发布包要带的那份默认配置）——'
    + ' 生成：node tools/reset-release-defaults.js');
}

// ⚠ 提示里给的命令**必须真的能修**（2026-10-11 真踩过：提示指了 sync-config-file.js，
//   而它对"值漂移"一点用都没有 ⇒ 照着敲一遍，错误原样还在 ✗）。
// ⚠⚠ 但"能不能修"这件事**只能在真实仓库里验**：负例副本里那份 toml 是 createFiles 造的
//   小样本（只有几个键），拿它跑 `--check` 必然判"与 863 项默认值不一致" ⇒ 对照组假失败 ✗
//   （2026-10-11 真踩过：livecfg-ok / livecfg-tuned 两条对照组因此变红）。
{
  const fixer = 'tools/reset-release-defaults.js';
  if (!fs.existsSync(path.join(ROOT, fixer))) {
    console.error('❌ 缺少 ' + fixer + ' —— 发布用默认配置的修法没有落点（提示会指向一条做不到的命令）');
    releaseBad++;
  } else if (process.env.ATL_ROOT) {
    console.log('ℹ 负例副本里跳过"修法自检"（副本里的 toml 是合成样本，不是发布用默认配置）');
  } else {
    const self = fs.readFileSync(__filename, 'utf8');
    if (!self.includes('reset-release-defaults.js')) {
      console.error('❌ 本检查器的修复提示没指向 ' + fixer + ' —— 下一个人会照着错的命令再踩一次');
      releaseBad++;
    }
  }
}

if (unparsed) {
  console.error('');
  console.error('❌ 有 ' + unparsed + ' 项的默认值没解析出来 —— 这是**检查器自身**坏了，不是配置的问题。'
    + '先修 check-live-config-matches-defaults.js 的解析，否则这条端到端证据是假的。');
  process.exit(1);
}
if (notLive === 0 && differs === 0 && releaseBad === 0) {
  console.log('✅ 游戏目录的 toml 与代码默认值**逐项一致**（已比对 ' + (defaults.size - pending) + ' 项'
    + (pending ? '，另有 ' + pending + ' 项等下次启动写入' : '') + '）');
  console.log('   → mod 确实注册并读到了全部配置项');
  if (tuned) {
    console.log('   另有 ' + tuned + ' 项与默认值不同，且不是任何"有意变更的旧默认值" —— '
      + '按调参结果对待（上面已逐条列出，不算问题）');
  }
  process.exit(0);
}
console.log('');
console.log('不一致：缺项 ' + notLive + '，值不同 ' + differs + '，陈旧键 ' + stale
  + '，发布用默认配置 ' + releaseBad
  + (pending ? '（另有 ' + pending + ' 项在等下次启动写入，不算失败）' : '')
  + (tuned ? '（另有 ' + tuned + ' 项是调参结果，不算失败）' : ''));
process.exit(1);
