/*
 * fill-en-lang.js —— 把 en_us.json 补齐到和 zh_cn.json 键集一致。
 *
 * 背景：zh_cn 有 175 个键、en_us 只有 144 个，缺 31 个（饵料/窝料/装配界面的提示）。
 * 缺键的表现是游戏里那行直接显示成原始键名，很难在代码里看出来。
 *
 * 策略：以 **zh_cn 为完整源**，只补 en_us 缺的那些，并保留 en_us 已有的英文
 * （不覆盖已经翻译好的部分）。新补的英文由下面的 EN 表给出；
 * 表里没覆盖到的会**明确报出来**，不会静默塞个中文进去。
 *
 * 用法：node tools/fill-en-lang.js        # 只看缺什么
 *       node tools/fill-en-lang.js --write  # 真的写回
 */
const fs = require('fs');
const path = require('path');

const LANG = path.join(__dirname, '..', 'src', 'main', 'resources',
  'assets', 'anglertackle', 'lang');
const zhPath = path.join(LANG, 'zh_cn.json');
const enPath = path.join(LANG, 'en_us.json');
const zh = JSON.parse(fs.readFileSync(zhPath, 'utf8'));
const en = JSON.parse(fs.readFileSync(enPath, 'utf8'));

/** en_us 缺失键的英文翻译 */
const EN = {
  'gui.anglertackle.rod_type.float': 'Float Rod',
  'gui.anglertackle.rod_type.lure': 'Lure Rod',
  'gui.anglertackle.slot.bait': 'Bait',
  'gui.anglertackle.codex_rank': 'Leaderboard',
  'message.anglertackle.chum_cap': 'Chum limit reached: %s already in the world (server-wide)',
  'message.anglertackle.big_catch': '[Angler] %s landed a %s — %s kg, worth %s coins!',
  'message.anglertackle.announce_on': 'on',
  'message.anglertackle.announce_off': 'off',
  'message.anglertackle.announce_status': 'Big-catch announcements: %s (threshold %s coins)',
  'message.anglertackle.announce_set': 'Big-catch announcements: %s',
  'message.anglertackle.chum_needs_water': 'Chum must be placed in water',
  'message.anglertackle.chum_placed': 'Chum placed',
  'message.anglertackle.slot_not_applicable': '%s cannot be fitted to %s',
  'tooltip.anglertackle.bait_attraction': 'Targeted attraction: %s',
  'tooltip.anglertackle.bait_consumed': 'Consumed 1 per cast',
  'tooltip.anglertackle.bait_float_no': 'Not usable on a float rod',
  'tooltip.anglertackle.bait_float_ok': 'Usable on a float rod',
  'tooltip.anglertackle.bait_max_weight': 'Size limit: under %s kg',
  'tooltip.anglertackle.bait_min_weight': 'Size floor: over %s kg',
  'tooltip.anglertackle.bait_no_limit': 'Size limit: none',
  'tooltip.anglertackle.bait_price': 'Value: %s coins',
  'tooltip.anglertackle.bait_tier.common': 'Quality floor: White',
  'tooltip.anglertackle.bait_tier.uncommon': 'Quality floor: Green',
  'tooltip.anglertackle.bait_tier.rare': 'Quality floor: Blue',
  'tooltip.anglertackle.bait_tier.epic': 'Quality floor: Purple',
  'tooltip.anglertackle.bait_tier.legendary': 'Quality floor: Gold',
  'tooltip.anglertackle.bait_tier.mythic': 'Quality floor: Red',
  'tooltip.anglertackle.chum_duration': 'Lasts: %s s',
  'tooltip.anglertackle.chum_floating': 'Floating chum',
  'tooltip.anglertackle.chum_sinking': 'Sinking chum',
  'tooltip.anglertackle.chum_same_water': 'Only works in the same water body',
  'tooltip.anglertackle.chum_time': 'Attracts fish faster',
  'tooltip.anglertackle.chum_floor.common': 'Quality floor: White',
  'tooltip.anglertackle.chum_floor.uncommon': 'Quality floor: Green',
  'tooltip.anglertackle.chum_floor.rare': 'Quality floor: Blue',
  'tooltip.anglertackle.chum_floor.epic': 'Quality floor: Purple',
  'tooltip.anglertackle.parts_header': 'Fittings:',
  // ── 特殊处理产物 + 食用适宜度文案（2026-10-11）────────────────────────────
  'item.anglertackle.hakarl': 'Fermented Shark',
  'item.anglertackle.frozen_sashimi': 'Frozen Sashimi',
  'item.anglertackle.shocked_eel': 'Discharged Eel',
  'item.anglertackle.boneless_gar': 'Boneless Gar Fillet',
  'tooltip.anglertackle.palat.raw_parasite': '⚠ Raw: may cause nausea (cooking makes it safe)',
  'tooltip.anglertackle.palat.nausea': '⚠ Barely edible: always causes nausea',
  'tooltip.anglertackle.palat.wither': '⚠ Toxic: causes Wither (needs proper preparation)',
  'tooltip.anglertackle.palat.inedible': 'Inedible (ornamental / protected)',
  'tooltip.anglertackle.palat.chronic.mercury': '⚠ Long-term: mercury builds up (%s meals → Weakness)',
  'tooltip.anglertackle.palat.chronic.ciguatera': '⚠ Long-term: ciguatoxin builds up (%s meals → Nausea)',
  'tooltip.anglertackle.palat.chronic.dioxin': '⚠ Long-term: dioxin builds up (%s meals → Poison)',
  'tooltip.anglertackle.stats_header': 'Combined stats:',
  // ↓ 这些是 zh_cn 也缺、需要两边都补的
  'tooltip.anglertackle.slot.sinker': '[Sinker]',
  'tooltip.anglertackle.slot.lure': '[Lure]',
  'tooltip.anglertackle.slot.bait': '[Bait]',
};

/** zh_cn 缺失键的中文翻译 */
const ZH = {
  'tooltip.anglertackle.slot.lure': '【拟饵】',
  'tooltip.anglertackle.slot.bait': '【鱼饵】',
  'gui.anglertackle.slot.bait': '鱼饵',
};

// zh 先补
let zhAdded = 0;
for (const [k, v] of Object.entries(ZH)) {
  if (!(k in zh)) { zh[k] = v; zhAdded++; }
}
// en 补到"zh 的全部键"都有
let enAdded = 0;
const unresolved = [];
for (const k of Object.keys(zh)) {
  if (k in en) continue;
  if (k in EN) { en[k] = EN[k]; enAdded++; }
  else unresolved.push(k);
}

console.log('zh_cn 补键 ' + zhAdded + ' 个 → 共 ' + Object.keys(zh).length);
console.log('en_us 补键 ' + enAdded + ' 个 → 共 ' + Object.keys(en).length);

const stillMissing = Object.keys(zh).filter((k) => !(k in en));
console.log('\n' + (stillMissing.length === 0
  ? '✅ en_us 已覆盖 zh_cn 的全部 ' + Object.keys(zh).length + ' 个键'
  : '⚠ 还有 ' + stillMissing.length + ' 个键没有英文翻译（需要你补 EN 表）：'));
for (const k of stillMissing) console.log('    ' + k + '   // zh: ' + zh[k]);

const extra = Object.keys(en).filter((k) => !(k in zh));
if (extra.length) {
  console.log('\nℹ en_us 里多出 ' + extra.length + ' 个 zh_cn 没有的键（保留，不动）：');
  extra.forEach((k) => console.log('    ' + k));
}

if (process.argv.includes('--write')) {
  fs.writeFileSync(zhPath, JSON.stringify(sortKeys(zh), null, 2) + '\n', 'utf8');
  fs.writeFileSync(enPath, JSON.stringify(sortKeys(en), null, 2) + '\n', 'utf8');
  console.log('\n已写回两个语言文件（键按字母排序，便于 diff）。');
} else {
  console.log('\n（只预览，没写文件。加 --write 才会写回。）');
}

/** 按字母排序，让 diff 干净 */
function sortKeys(obj) {
  const out = {};
  for (const k of Object.keys(obj).sort()) out[k] = obj[k];
  return out;
}
