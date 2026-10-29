// 生成块边界的唯一定义 + 确定性的剥除/拼装。
//
// ── 为什么需要这个模块 ──────────────────────────────────────
// 之前的做法是"就地删旧块、再插入新块"。它有三个致命弱点，全都真实踩过：
//   ① 标记常量写漏几个字符（少个 `====`、多个空格）→ 匹配不上 → 静默不删 → 每跑一次多一份；
//   ② 收尾的 `b.pop();` 落在块外 → 被后一个生成器挤走 → 那一组永不闭合、吞掉后续分组；
//   ③ 一旦文件里积压了不带标记的孤儿副本，任何"按标记删除"都清不掉它们，
//      只能靠"再写一个修复器"，而修复器又引入新的边界判断 —— 越修越乱。
//
// 现在改成**确定性重建**：不从现状出发做增量修改，而是
//   1) 用下面这张表把"生成块"全部剥掉，只留手写配置；
//   2) 再按固定顺序把生成的内容拼回去。
// 这样每次的输出只取决于「手写部分 + 源码」，与文件的历史状态无关 ——
// 跑一百次和跑一次的结果完全一样。
//
// ⚠️ 新增生成块时，必须同时在这里登记边界，否则它会被当成手写内容留在文件里。
const fs = require('fs');

/**
 * 生成块清单。`begin` / `end` 都是**原样的文本**（可以多行），
 * 与生成器实际写出的那一串完全一致 —— 不省略、不化简。
 *
 * 顺序无所谓（会全部剥掉），但 end 必须能唯一标识该块的结尾。
 */
const BLOCKS = [
  {
    name: '配件数值声明',
    begin: '    // ==== BEGIN 配件数值声明（gen-part-config.js 生成，勿手改）====',
    end: '    // ==== END 配件数值声明 ====',
  },
  {
    name: '配件数值定义',
    begin: '        // ==== BEGIN 配件数值定义 ====',
    end: '        // ==== END 配件数值定义 ====\n        b.pop();',
  },
  {
    name: '鱼种数值声明',
    begin: '    // ==== BEGIN 鱼种数值声明（gen-fish-config.js 生成，勿手改）====',
    end: '    // ==== END 鱼种数值声明 ====',
  },
  {
    name: '鱼种数值定义',
    begin: '        // ==== BEGIN 鱼种数值定义 ====',
    end: '        // ==== END 鱼种数值定义 ====\n        b.pop();',
  },
  {
    // ⚠️ 新增生成块必须在这里登记，否则 peelAll 剥不掉它 ——
    // 症状是"剥完还剩一个 pop"，报"深度为 -1"（踩过一次：加了 [gameplay] 却忘了登记）。
    name: '玩法数值声明',
    begin: '    // ==== BEGIN 玩法数值声明 ====',
    end: '    // ==== END 玩法数值声明 ====',
  },
  {
    name: '玩法数值定义',
    begin: '        // ==== BEGIN 玩法数值定义 ====',
    end: '        // ==== END 玩法数值定义 ====\n        b.pop();',
  },
];

/** 与生成器共用的 stripBlock：按原样多行文本匹配，行首行尾允许空白。 */
function stripExact(text, begin, end) {
  const toRe = (s) => s.split('\n')
    .map((line) => '[ \\t]*' + line.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + '[ \\t]*')
    .join('\\r?\\n');
  const re = new RegExp('^' + toRe(begin) + '\\r?\\n[\\s\\S]*?^' + toRe(end) + '\\r?\\n?', 'm');
  if (!re.test(text)) return { text, hit: false };
  return { text: text.replace(re, ''), hit: true };
}

/**
 * 第二遍清扫：删掉**标记已丢失的孤儿块**。
 *
 * 怎么会有这种东西：早先的生成器把 `b.pop();` 写在块**外**，后一个生成器往 END 标记后
 * 插内容时把 pop 挤走；再加上几次误删标记，文件里就积压了一批"只剩正文、没有 BEGIN/END"
 * 的副本。它们靠标记删不掉，必须换个判据。
 *
 * 判据：**空壳块**。孤儿副本只会包含"段头注释 + b.comment 说明 + .push("组名")"，
 * 一条 `defineInRange` 都没有（数值都在带标记的那份里）。所以：
 *   从 `// ---- 数值` 段头开始，若在遇到下一条 push/下一个段头/END 标记之前
 *   **一条 defineInRange 都没有**，那它就是空壳，整段删掉。
 * 反过来，只要区间里有 defineInRange，就说明是有内容的块，一律不碰。
 */
function sweepOrphanShells(text, onlyGroup) {
  const lines = text.split('\n');
  const out = [];
  let removed = 0;
  // onlyGroup：只清这一组的空壳（例如 '鱼种数值'）。
  // ⚠️ 必须能限定：配件组的地标里那个 push("partsStats") **必须保留**（它标记插入点），
  // 一刀切会把配件的地标也当空壳删掉，深度直接变负数（踩过）。
  const headRe = onlyGroup
    ? new RegExp('^\\s*\\/\\/ -+ ' + onlyGroup + '\\s*$')
    : /^\s*\/\/ -+ (鱼种数值|配件数值|玩法数值)\s*$/;
  for (let i = 0; i < lines.length; i++) {
    if (!headRe.test(lines[i])) { out.push(lines[i]); continue; }

    // 从段头往后界定"这个块"的范围：到下一个段头 / 下一个 BEGIN 标记 / 下一个 END 标记为止
    let end = i + 1;
    while (end < lines.length) {
      const l = lines[end];
      if (/^\s*\/\/ -+ (鱼种数值|配件数值)\s*$/.test(l)) break;
      if (/==== (BEGIN|END)/.test(l)) break;
      // 遇到"下一个手写分组"的开头也停（那说明这个空壳一直贴到了下一组）
      if (/^\s*\/\/ -{4,} /.test(l) && !/数值/.test(l)) break;
      end++;
    }
    const seg = lines.slice(i, end).join('\n');
    const hasFields = /\.defineInRange\(|\.define\(/.test(seg);
    const hasPush = /\.push\("/.test(seg);
    if (!hasFields && hasPush) {
      removed++;
      i = end - 1;      // 整段丢弃
      continue;
    }
    out.push(lines[i]);
  }
  return { text: out.join('\n'), removed };
}

/**
 * 把 Config.java 里的生成块**全部**剥掉，只留手写配置。
 *
 * 会反复剥直到一个标记都不剩 —— 积压的重复副本里，靠前那份删掉之后后面那份才会露出来。
 */
function peelAll(src) {
  let text = src;
  const log = [];
  for (const b of BLOCKS) {
    let n = 0;
    for (let guard = 0; guard < 200; guard++) {
      const r = stripExact(text, b.begin, b.end);
      if (!r.hit) break;
      text = r.text;
      n++;
    }
    if (n) log.push(b.name + ' ×' + n);
  }

  // 第二遍：清扫标记已丢失的空壳块
  const swept = sweepOrphanShells(text);
  text = swept.text;
  if (swept.removed) log.push('孤儿空壳 ×' + swept.removed);

  // 第三遍：收敛连续空行。
  // 每剥掉一个块都会留下它前面的空行，反复几轮就积成一片
  //（实测段声明上方堆了 10 个空行，而且每跑一次还多 3 个 —— 行数一轮一轮涨）。
  // 生成块之间约定用"一个空行"分隔，所以把 2 个以上连续空行压成 1 个。
  const before = text;
  text = text.replace(/\r?\n(?:[ \t]*\r?\n){2,}/g, '\n\n');
  if (text !== before) log.push('收敛空行');

  // 残渣检查
  const residue = [];
  for (const b of BLOCKS) {
    if (text.includes(b.begin) || text.includes(b.end)) residue.push('标记残留：' + b.name);
  }
  for (const probe of ['crucianCarpMinKg', 'hempDurability', 'push("partsStats")', 'push("fishStats")']) {
    if (text.includes(probe)) residue.push('内容残留：' + probe);
  }
  return { text, log, residue };
}

/** 校验一份 Config.java 的结构是否自洽。 */
function checkStructure(text) {
  const lines = text.split('\n');
  let depth = 0, minDepth = 0;
  const pushes = [], pops = [];
  lines.forEach((l, i) => {
    if (/\.push\("/.test(l)) { depth++; pushes.push(i + 1); }
    else if (/^\s*b\.pop\(\);\s*$/.test(l)) { depth--; pops.push(i + 1); if (depth < minDepth) minDepth = depth; }
  });
  const problems = [];
  if (depth !== 0) problems.push('push/pop 深度结束时为 ' + depth + '（应为 0）');
  if (minDepth < 0) problems.push('深度曾掉到 ' + minDepth + '（pop 多于 push）');
  return {
    ok: problems.length === 0,
    problems,
    pushCount: pushes.length,
    popCount: pops.length,
    nDefine: (text.match(/\.defineInRange\(/g) || []).length,
    nDefineBool: (text.match(/\.define\(/g) || []).length,
  };
}

/** 数某个块在文件里出现了几次。 */
function countBlock(text, name) {
  const b = BLOCKS.find((x) => x.name === name);
  if (!b) throw new Error('未登记的块：' + name);
  return text.split(b.begin).length - 1;
}

module.exports = { BLOCKS, stripExact, peelAll, sweepOrphanShells, checkStructure, countBlock };
