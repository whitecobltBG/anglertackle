/*
 * gen-shop-prices.js —— 生成 docs/18-商店价目表.md（价目 + 解锁链 + 兑换 + 补货 + 界面要求）。
 *
 * 两个数据来源，都是"单一来源"：
 *   ① 配件清单与档位 —— 从 ModItems.java **解析**（不手写，手写必然漂移）
 *   ② 价格 / 免费 / 兑换 / 限购 / 解锁链 —— 来自 tools/shop-rules.js
 *
 * 为什么值得这么绕：商店这块的错**在游戏里只表现为"买不了/价格不对"**，
 * 而没人会去逐个核对 40 件商品。放成数据就能离线把规则之间的矛盾全断言掉。
 *
 * 用法：
 *   node tools/gen-shop-prices.js            预览
 *   node tools/gen-shop-prices.js --write    写入 docs/18-商店价目表.md
 *   node tools/gen-shop-prices.js --check    断言"文档 == 重新生成的结果"（漂移检查）
 */
const fs = require('fs');
const path = require('path');
// ATL_ROOT：把检查指向一棵**改坏的临时副本**（负向测试用）。
// 注意 shop-rules.js 也要跟着从副本里 require —— 否则负例改了副本里的规则，
// 被加载的却还是真实目录那一份，负例就静默失效了。
const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..');
const TOOLS = process.env.ATL_ROOT ? path.join(ROOT, 'tools') : __dirname;
const { RULES, UNLOCK_CHAIN, RESTOCK, UI_REQUIREMENTS, RATING_RULES, EXCHANGES, LEAGUE_OFFERS, GRADE_TIER,
  RESULT_VALUE, VALUE_BAND, ENCHANT_VALUE } = require(path.join(TOOLS, 'shop-rules.js'));
const OUT = path.join(ROOT, 'docs/18-商店价目表.md');
// ShopCatalog.java 的输出路径。定义必须在使用之前 —— CLI 块写在文件中间，
// 而这个 const 原本在它后面，会撞上 TDZ（Cannot access before initialization）。
const JAVA_OUT = path.join(ROOT,
  'src/main/java/com/whitecobalt/anglertackle/shop/ShopCatalog.java');

const itemSrc = fs.readFileSync(path.join(ROOT,
  'src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java'), 'utf8');
const zh = JSON.parse(fs.readFileSync(path.join(ROOT,
  'src/main/resources/assets/anglertackle/lang/zh_cn.json'), 'utf8'));
const fishes = JSON.parse(fs.readFileSync(path.join(ROOT,
  'src/main/resources/data/anglertackle/tags/item/fishes.json'), 'utf8'))
  .values.map((v) => v.replace('anglertackle:', ''));

/**
 * 品质档名（白/绿/蓝/紫/金/红）→ 中文。
 *
 * ⚠ 别和下面那个 `GRADE_CN` 混了：那个映射的是解锁链里的 `UNCOMMON_OR_BETTER`
 * 这种"以上"写法。两个表都得在**用到之前**声明（文档段在文件上半部分拼字符串，
 * 声明放后面会撞 TDZ）。
 */
const GRADE_CN_SHORT = { WHITE: '白', GREEN: '绿', BLUE: '蓝', PURPLE: '紫', GOLD: '金', RED: '红' };

// ================================================================ 解析 ModItems 的档位
const CATEGORY = [
  [/^(wood|iron|gold|diamond|netherite)_(float|lure)_rod$/, 'rod'],
  [/^(hemp|cotton|nylon|fluoro|pe|titanium)_line$/, 'line'],
  [/^(hand|spinning|drum|baitcast|heavy)_reel$/, 'reel'],
  [/^sinker_/, 'sinker'],
  [/^lure_/, 'lure'],
  [/^bait_/, 'bait'],
  [/^chum_/, 'chum'],
];
const MATERIAL_TIER = { WOOD: 1, IRON: 2, GOLD: 3, DIAMOND: 4, NETHERITE: 5 };
const RARITY_TIER = { COMMON: 1, UNCOMMON: 2, RARE: 3, EPIC: 4 };
// 大型纺车轮是第 5 档（金轮）：属性上碾压水滴轮（damping 2.50 vs 1.40），不能同档
const EXCEPTIONS = { heavy_reel: { tier: 5 } };
// 调试物品不进商店。不显式列出来的话它会安静地混进价目表
const NOT_FOR_SALE = { lure_test_red: '调试物品 —— 不上架（发布前要删，见 docs/13 开放项40）' };

const lines = itemSrc.split('\n');
const entries = [];
for (let i = 0; i < lines.length; i++) {
  const m = /(?:ITEMS\.register|=\s*rod)\(\s*"([a-z0-9_.]+)"/.exec(lines[i]);
  if (!m) continue;
  const id = m[1];
  const cat = (CATEGORY.find(([re]) => re.test(id)) || [])[1];
  if (!cat) continue;

  const chunk = lines.slice(i, i + 6).join(' ');
  let tier = null, how = '', durability = null;
  // 耐久：partProps(N, ...) 的第一个参数。拟饵"按每点耐久计价"要用它。
  const dp = /partProps\(\s*(\d+)\s*,/.exec(chunk);
  if (dp) durability = Number(dp[1]);
  // 鱼饵不用 partProps，耐久写在 Item.Properties 上（2026-10-01 加的）
  if (durability === null) {
    const dd = /\.durability\((\d+)\)/.exec(chunk);
    if (dd) durability = Number(dd[1]);
  }
  const ps = /PartStats\.(?:line|lure)\([^)]*?(\d+)\s*\)/.exec(chunk);
  if (ps) { tier = Number(ps[1]); how = 'PartStats 档位'; }
  if (tier === null) {
    const ctor = /new (?:BaitItem|ChumItem)\(\s*(\d+)\s*,/.exec(chunk);
    if (ctor) { tier = Number(ctor[1]); how = '构造器档位'; }
  }
  if (tier === null && cat === 'rod') {
    const mt = /RodSpec\.(WOOD|IRON|GOLD|DIAMOND|NETHERITE)/.exec(chunk);
    if (mt) { tier = MATERIAL_TIER[mt[1]]; how = '材质 RodSpec.' + mt[1]; }
  }
  if (tier === null) {
    const r = /Rarity\.(COMMON|UNCOMMON|RARE|EPIC)/.exec(chunk);
    if (r) { tier = RARITY_TIER[r[1]]; how = '稀有度 ' + r[1]; }
  }
  if (EXCEPTIONS[id]) { tier = EXCEPTIONS[id].tier; how = '显式例外'; }

  entries.push({ id, cat, tier, how, durability, line: i + 1 });
}

// ================================================================ 套规则
const problems = [];
for (const e of entries) {
  const model = RULES[e.cat];
  e.name = zh['item.anglertackle.' + e.id] || ('⚠ 缺中文名 ' + e.id);
  e.notForSale = NOT_FOR_SALE[e.id] || null;
  if (e.tier === null) { problems.push(e.id + '（第 ' + e.line + ' 行）解析不出档位'); continue; }
  e.rule = model[e.tier];
  if (e.rule === undefined) {
    problems.push(e.id + ' 的档位 ' + e.tier + ' 在规则表里没有（'
      + Object.keys(model).join('/') + '）');
    continue;
  }
  // 每点耐久计价（拟饵）：价格 = 耐久 × 单价。
  // 耐久解析不出来的话**必须报错**而不是当 0 —— 当 0 会让这件商品免费，
  // 而且它在文档里看起来完全正常（"0 币"）。
  if (e.rule.perDurability !== undefined) {
    if (!e.durability) {
      problems.push(e.id + ' 是每点耐久计价，但解析不出耐久（partProps 的第一个参数）'
        + ' —— 当 0 处理会让它免费');
    } else {
      e.price = e.durability * e.rule.perDurability;
      e.rule = Object.assign({}, e.rule, { kind: 'buy', price: e.price });
    }
  }
}

const ORDER = ['rod', 'line', 'reel', 'sinker', 'lure', 'bait', 'chum'];

// ---------------------------------------------------------------- 解锁档位
//
// 用户只**举例**说了绿档解锁什么（"绿线、蓝拟饵、绿鱼饵、白窝料"，档位 0~3），
// 但另外两条把规则钉死了：
//   · 紫档"解锁**所有非红**配件"  → 紫(4) 解锁 ≤5 档
//   · 金档"解锁**所有**配件购买和兑换" → 金(5) 解锁 6 档（红）+ 兑换
// 能同时满足这两条的只有一条：**stage N 解锁 ≤ N+1 档**。
// 白档(1)≤2、绿档(2)≤3、蓝档(3)≤4、紫档(4)≤5、金档(5)≤6，
// 而且绿档 ≤3 恰好覆盖用户举的那四个例子。所以采用它 ——
// 但这是**推断**，不是用户原话，文档里会标出来。
const unlockStageOf = (tier) => Math.max(1, Math.min(5, tier - 1));

/**
 * **竿**的解锁档位和配件不是一套规则。
 *
 * 配件：stage N 解锁 ≤N+1 档（紫档"所有非红"=≤5、金档"所有"=≤6 两条钉死的）。
 * 竿：**解锁档位 = 自己的档位**。竿是"等级的标志物" —— 绿竿就是绿档的凭证，
 *     不可能在绿档之前买到。写 tier-1 的话绿竿在白档就能买，
 *     那"等级评定"就形同虚设了（评定本来就是为了卡住竿）。
 */
const unlockStageOfEntry = (cat, tier) =>
  cat === 'rod' ? Math.max(1, Math.min(5, tier)) : unlockStageOf(tier);
const CAT_NAME = { rod: '竿', line: '鱼线', reel: '渔轮', sinker: '铅锤', lure: '拟饵', bait: '鱼饵', chum: '窝料' };

// ================================================================ 规则自检
const checks = [];

// ① 兑换用的鱼必须真实存在（写错 id 的表现是"兑换永远不成功"）
for (const chain of UNLOCK_CHAIN) {
  if (chain.deliver && chain.deliver.fish && !fishes.includes(chain.deliver.fish)) {
    checks.push('❌ 解锁链「' + chain.label + '」要求交付的鱼 `' + chain.deliver.fish + '` 不在鱼种表里');
  }
}
for (const [cat, model] of Object.entries(RULES)) {
  for (const [tier, rule] of Object.entries(model)) {
    if (rule.kind !== 'exchange') continue;
    const ids = rule.fish && rule.fish.ids;
    if (!ids) continue;
    for (const id of ids) {
      if (!fishes.includes(id)) {
        checks.push('❌ ' + CAT_NAME[cat] + ' ' + tier + ' 档的兑换物 `' + id + '` 不在鱼种表里');
      }
    }
  }
}

// ② 同类别里价格必须随档位非递减（高档更便宜的话玩家只会买最便宜的，高档形同虚设）
//
// ⚠ 拟饵是例外，而且**必须**是例外：它按"每点耐久"定价，而耐久随档位**下降**
//   （绿 40 → 红 14），单价随档位上升（11 → 35），两者的乘积各档接近，
//   所以总价天然可能不单调（实测：红 490 < 金 522 < 紫 528）。
//   判据要落在**每点单价**上 —— 那才是玩家真正在比较的东西
//   （红 35 币/次 vs 金 29 币/次，金其实更划算）。
//   拿总价去判会逼着一个正确的设计改坏。
for (const cat of ORDER) {
  const arr = entries.filter((e) => e.cat === cat && e.rule && e.rule.kind === 'buy')
    .sort((a, b) => a.tier - b.tier);
  const val = (e) => {
    const r = RULES[e.cat][e.tier];
    return r && r.perDurability !== undefined ? r.perDurability : e.rule.price;
  };
  const unit = RULES[cat] && Object.values(RULES[cat]).some((r) => r.perDurability !== undefined);
  for (let i = 1; i < arr.length; i++) {
    if (arr[i].tier > arr[i - 1].tier && val(arr[i]) < val(arr[i - 1])) {
      checks.push('❌ ' + CAT_NAME[cat] + '：' + arr[i].id + '（' + arr[i].tier + ' 档 '
        + val(arr[i]) + (unit ? ' 币/点耐久' : '') + '）比 ' + arr[i - 1].id + '（'
        + arr[i - 1].tier + ' 档 ' + val(arr[i - 1]) + '）' + (unit ? '便宜' : '便宜'));
    }
  }
  if (unit) {
    checks.push('ℹ ' + CAT_NAME[cat] + '按**每点耐久**计价，所以只看单价单调；'
      + '总价各档接近是设计意图（见文档里的说明）');
  }
}

// ③ **档位缺口**：规则表有某一档、但没有任何商品是这档（或反过来）
for (const cat of ORDER) {
  const tiers = new Set(entries.filter((e) => e.cat === cat).map((e) => e.tier));
  const ruleTiers = new Set(Object.keys(RULES[cat]).map(Number));
  for (const t of ruleTiers) {
    if (!tiers.has(t)) checks.push('⚠ ' + CAT_NAME[cat] + ' 的规则表里有 ' + t + ' 档，但没有任何商品是这个档位');
  }
  for (const t of tiers) {
    if (!ruleTiers.has(t)) checks.push('❌ ' + CAT_NAME[cat] + ' 有 ' + t + ' 档的商品，但规则表里没有这一档');
  }
}

// ③b 手滑兜底（规则表是手写的，这些错没人会逐个核对 39 件商品）
{
  // 免费商品必须正好是这 2 件。多一件 = 白送（比如把某条线手滑写成 FREE）
  const EXPECT_FREE = ['hemp_line', 'hand_reel'].sort();
  const free = entries.filter((e) => !e.notForSale && e.rule && e.rule.kind === 'free')
    .map((e) => e.id).sort();
  if (free.join(',') === EXPECT_FREE.join(',')) {
    checks.push('ℹ 免费商品正好是预期的 2 件：' + free.join(', '));
  } else {
    checks.push('❌ 免费商品清单不对。实际：[' + free.join(', ') + ']，预期：['
      + EXPECT_FREE.join(', ') + ']');
  }
  // 0 币的"购买" = 手滑（想写免费就该写 FREE）
  for (const e of entries) {
    if (e.notForSale || !e.rule) continue;
    if (e.rule.kind === 'buy' && !(e.rule.price > 0)) {
      checks.push('❌ ' + e.id + ' 是"购买"但价格是 ' + e.rule.price + ' —— 等于白送，想免费请写 FREE');
    }
    if (e.rule.kind === 'exchange' && !e.rule.fish) {
      checks.push('❌ ' + e.id + ' 是"兑换"但没写兑换物');
    }
  }
  // 竿和配件的解锁档位规则不同（见 unlockStageOfEntry 的注释），这里钉死
  for (const e of entries) {
    if (e.notForSale) continue;
    const want = e.cat === 'rod' ? Math.max(1, Math.min(5, e.tier))
                                 : Math.max(1, Math.min(5, e.tier - 1));
    const got = unlockStageOfEntry(e.cat, e.tier);
    if (got !== want) checks.push('❌ ' + e.id + ' 的解锁档位算出来是 ' + got + '，应为 ' + want);
  }
}

// ④ 用户没给的项必须显式标 TODO，不能悄悄留空
const todo = entries.filter((e) => e.rule && e.rule.kind === 'TODO');

// ⑤ 限购必须带补货时间（"售罄"却不补货 = 永久买不到）
for (const cat of ORDER) {
  for (const [tier, rule] of Object.entries(RULES[cat])) {
    if (rule.restock && !(rule.restock.minutes > 0)) {
      checks.push('❌ ' + CAT_NAME[cat] + ' ' + tier + ' 档有售卖上限，但没写补货时间 —— 玩家会永久买不到');
    }
    if (rule.kind === 'exchange' && rule.restockMinutes !== undefined && !(rule.restockMinutes > 0)) {
      checks.push('❌ ' + CAT_NAME[cat] + ' ' + tier + ' 档兑换写了补货时间但值不合法');
    }
  }
}

// ================================================================ 渲染
function priceCell(e) {
  if (e.notForSale) return '**不上架**';
  const r = e.rule;
  if (!r) return '?';
  if (r.kind === 'free') return '**免费**';
  if (r.kind === 'TODO') return '⚠ 待定';
  if (r.kind === 'buy') {
    let s = String(r.price);
    if (r.restock) s += '（每买 ' + r.restock.per + ' 个售罄，等 ' + r.restock.minutes + ' 分钟补货）';
    return s;
  }
  if (r.kind === 'exchange') {
    const f = r.fish.kind === 'anyRed'
      ? '任意红品质鱼'
      : r.fish.ids.map((i) => zh['item.anglertackle.' + i] || i).join(' / ');
    let s = '兑换：' + (r.grade ? '红品质 ' : '') + f
      + (r.each ? ' **各一条**' : (r.count ? ' ×' + r.count : ''));
    if (r.consumes) s += '（消耗）';
    if (r.restockMinutes) s += '（每次兑换后等 ' + r.restockMinutes + ' 分钟）';
    return s;
  }
  return '?';
}

function fishesCell(e) {
  if (e.notForSale || !e.rule || e.rule.kind !== 'buy' || e.rule.price === undefined) return '—';
  return (e.rule.price / 35).toFixed(1) + ' 条白鱼';
}

const GRADE_CN = {
  UNCOMMON_OR_BETTER: '绿品质以上', RARE_OR_BETTER: '蓝品质以上',
  EPIC_OR_BETTER: '紫品质以上', LEGENDARY_OR_BETTER: '金品质以上',
};

const L = [];
L.push('# 18 · 钓鱼商店（老柯）');
L.push('');
L.push('> 📌 **开工必读** —— 本文件是《垂纶》策划族的一员。动手前先读 `docs/CONVENTIONS.md`（协作规则 + 每轮自我暗示）与 `docs/接力.md`（接手须知）；项目记忆只认 `docs/` 下的文件。');
L.push('');
L.push('> **本文件由 `tools/gen-shop-prices.js` 生成，不要手改。**');
L.push('> 配件清单与档位从 `ModItems.java` 解析；价格 / 免费 / 兑换 / 限购 / 解锁链来自 `tools/shop-rules.js`。');
L.push('> 改规则 → 改 `shop-rules.js` → 重跑 `node tools/gen-shop-prices.js --write`。');
L.push('');
L.push('## 定价锚点');
L.push('');
L.push('玩家赚鱼币的唯一途径是**把鱼卖给老柯**，所以价格要用鱼来衡量才有手感。');
L.push('鱼价见 `economy/CoinPricing.BASE_BY_TIER`：');
L.push('');
L.push('| 鱼的档位 | 白 | 绿 | 蓝 | 紫 | 金 | 红 |');
L.push('|---|---|---|---|---|---|---|');
L.push('| 收购价（币） | 35 | 80 | 170 | 465 | 1380 | 4800 |');
L.push('');
L.push('## 解锁链（等级评定）');
L.push('');
L.push('| 竿档位 | 等级评定交付 | 解锁 | 额外赠送 |');
L.push('|---|---|---|---|');
for (const c of UNLOCK_CHAIN) {
  let d = '—（开局即有）';
  if (c.deliver) {
    const who = c.deliver.fish
      ? (zh['item.anglertackle.' + c.deliver.fish] || c.deliver.fish)
      : '任意鱼';
    d = (GRADE_CN[c.deliver.grade] || c.deliver.grade) + ' ' + who
      + (c.deliver.count ? ' ×' + c.deliver.count : '');
  }
  L.push('| ' + c.label + '（' + c.rodName + '，' + c.stage + ' 档） | ' + d + ' | ' + c.unlocks + ' | ' + (c.free || '—') + ' |');
}
L.push('');
L.push('## 价目表（共 ' + entries.length + ' 件配件）');
L.push('');
for (const cat of ORDER) {
  const list = entries.filter((e) => e.cat === cat).sort((a, b) => (a.tier - b.tier) || a.id.localeCompare(b.id));
  if (!list.length) continue;
  L.push('### ' + CAT_NAME[cat]);
  L.push('');
  L.push('| 物品 | id | 档位 | 价格 / 获取方式 | ≈ 几条白鱼 | 备注 |');
  L.push('|---|---|---|---|---|---|');
  for (const e of list) {
    const note = [];
    if (e.notForSale) note.push(e.notForSale);
    if (e.rule && e.rule.note) note.push(e.rule.note);
    if (e.durability && e.price !== undefined && !e.notForSale) {
      note.push('每点耐久 ' + (RULES[e.cat][e.tier].perDurability || '?')
        + ' × 耐久 ' + e.durability + ' = ' + e.price);
    }
    if (e.rule && e.rule.ambiguous) note.push('⚠ ' + e.rule.ambiguous);
    if (e.rule && e.rule.kind === 'TODO') note.push('⚠ ' + e.rule.note);
    note.push('档位来源：' + e.how);
    L.push('| ' + e.name + ' | `' + e.id + '` | ' + e.tier + ' | ' + priceCell(e)
      + ' | ' + fishesCell(e) + ' | ' + note.join('；') + ' |');
  }
  L.push('');
}
L.push('## 兑换表（鱼 → 原版物品）');
L.push('');
L.push('用户 2026-10-02："加一个兑换，用特定品质鱼兑换原版物品……至少给我 20 种。"');
L.push('**兑换消耗鱼**（和红鱼线/金渔轮一样）。品质要求写在表里，' +
  '必须落在该鱼种**能达到的档位区间**内，否则永远兑不出来（生成器会逐个核对）。');
L.push('');
L.push('| 要几条 | 鱼（最低品质） | 产出 | 备注 |');
L.push('|---|---|---|---|');
for (const x of EXCHANGES) {
  const name = zh['item.anglertackle.' + x.fish] || x.fish;
  const out = '`' + x.result.item + '` ×' + x.result.count
    + (x.enchant ? '（附魔：`' + x.enchant + '` 1 级）' : '');
  L.push('| ' + x.count + ' | ' + GRADE_CN_SHORT[x.grade] + '品质以上 ' + name + ' | ' + out + ' | '
    + (x.note || '') + ' |');
}
L.push('');
L.push('共 **' + EXCHANGES.length + '** 条。');
L.push('');
L.push('## 补货机制');
L.push('');
L.push('| 商品 | 规则 |');
L.push('|---|---|');
for (const r of RESTOCK) L.push('| ' + r.item + ' | ' + r.rule + ' |');
L.push('');
L.push('⚠ **补货按玩家算还是按服务器算，还没定。** 按玩家算的话每人各有各的库存；');
L.push('按服务器算的话，一个服里几个人就能把金线抢光。默认建议**按玩家**。');
L.push('');
L.push('## 界面要求');
L.push('');
for (const u of UI_REQUIREMENTS) L.push('- ' + u);
L.push('');
L.push('## 还没做的');
L.push('');
L.push('- **商店界面本身**（Menu / Screen / 分页 / 评定 / 兑换）—— 一行代码都还没写');
L.push('- `hasTalkedToFisherman` 玩家标记（决定右键老柯是"只有台词"还是"打开商店"）');
L.push('- 卖出（玩家把鱼卖给老柯）—— 鱼价已有 `CoinPricing`，缺的是界面');
L.push('- 限购与补货的存储（按玩家、跨存档）');
L.push('- 规则落进 Java（`shop/ShopCatalog.java`）—— 等下面几个待确认项定完');
L.push('');
L.push('## 等级评定（突破）规则');
L.push('');
L.push('| 项 | 规则 |');
L.push('|---|---|');
L.push('| 是否可重复 | **不可**。评过就突破了，不能再交 |');
L.push('| 交付物 | **消耗**（交了就没了） |');
L.push('| 不满足时 | 提示固定文案：**「' + RATING_RULES.rejectMessage + '」** |');
L.push('| 垂纶等级 | 1–50；每条鱼 ' + RATING_RULES.xpPerCatch + ' 经验，品质额外 +'
  + RATING_RULES.tierBonus.join('/') + ' |');
L.push('| 升级经验 | ' + RATING_RULES.xpFirst + '（1 级）线性涨到 ' + RATING_RULES.xpLast + '（49 级） |');
L.push('| 卡级 | 停在评定点时不积攒经验，交完材料才继续升 |');
L.push('');
L.push('| 评定点 | 交付要求 |');
L.push('|---|---|');
const GRADE_CN_FULL = { UNCOMMON: '绿品质', RARE: '蓝品质', EPIC: '紫品质', LEGENDARY: '金品质' };
for (const [level, list] of Object.entries(RATING_RULES.requirements)) {
  const parts = (Array.isArray(list) ? list : [list]).map((req) => {
    const who = req.fish ? (zh['item.anglertackle.' + req.fish] || req.fish) : '任意鱼';
    const g = GRADE_CN_FULL[req.minGrade] || req.minGrade;
    return g + ' ' + who + ' ×' + (req.count || 1);
  });
  L.push('| Lv.' + level + ' | ' + parts.join('、') + ' |');
}
L.push('');
L.push('## 已确认的设计决定（2026-09-30）');
L.push('');
L.push('这几条原本是待确认项，用户已答复，记在这里免得以后又当成"没定"：');
L.push('');
L.push('- **拟饵按"每点耐久"计价**。所以绿拟饵总价 = 40 × 11 = 440，红拟饵 = 14 × 35 = 490。');
L.push('  ⚠ 副作用：**各档拟饵总价接近、且不单调**（红 490 < 金 522 < 紫 528）。');
L.push('  这是设计的自然结果，不是 bug：耐久随档位降（40→14）、单价随档位升（11→35）。');
L.push('  玩家比较的应该是**每次使用的成本**：红 35 币/次，金 29 币/次 —— 金其实更划算。');
L.push('  买高档拟饵买到的是"能钓更大的鱼"，不是"更便宜的消耗品"。');
L.push('- **鱼饵按路亚饵（拟饵）价格算**：鱼饵没有耐久，所以每个 = 1 × 对应单价。');
L.push('  档位按从小到大顺序对齐拟饵的 5 个价格（0→11、1→15、3→24、4→29、5→35）。');
L.push('- **金渔轮兑换要三种鱼各一条**（大西洋鲑 / 褐鳟 / 虎鱼，红品质），不是任意一种。');
L.push('- **所有兑换都消耗鱼**（金线的"任意两条红鱼"、金轮的三种鱼，交了就没了）。');
L.push('- **等级评定一次性**：评过就不能再交，交付物消耗，不满足时提示「品质或种类不满足要求」。');
L.push('- **紫档可以买金档配件**（用户确认"紫评定能买金"）。');
L.push('');
if (todo.length) {
  L.push('## ⚠ 仍未确定');
  L.push('');
  L.push('1. **"买 5 轮售罄"** 的"5 轮"是 5 个还是 5 次？补货按玩家还是按服务器？（暂按：5 个 / 按玩家）');
  L.push('2. **鱼饵档位不连续**：代码里是 0 / 0 / 1 / 3 / 4 / 5 —— **没有 2 档**（面包屑和蚯蚓共用 0 档）。');
  L.push('   所以鱼饵只有 5 个价位，不是 6 个。这是代码现状，不是定价遗漏。');
  L.push('');
}
const md = L.join('\n');

// ================================================================ 鱼 → 原版物品 兑换表
//
// 用户 2026-10-02："加一个兑换，用特定品质鱼兑换原版物品……至少给我20种兑换表。"
//
// 这里从 FishSpecies.java 现读**每种鱼的档位区间**，用来核对兑换表里的最低品质：
// 要求写在该鱼种达不到的档位上（比如给"只有白~绿"的鲫鱼写金档）时，
// 这条兑换**永远兑不出来**，而界面上完全看不出来 —— 正是要离线卡住的那类错。
const fishRanges = (() => {
  const src = fs.readFileSync(path.join(ROOT,
    'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java'), 'utf8');
  const consts = new Map();
  for (const m of src.matchAll(/private static final int\[\]\s+([A-Z_]+)\s*=\s*\{([^}]*)\}/g)) {
    consts.set(m[1], m[2].split(',').map((s) => Number(s.trim())).filter((n) => !Number.isNaN(n)));
  }
  const start = src.indexOf('ALL = java.util.List.of(');
  if (start < 0) throw new Error('找不到 FishSpecies.ALL');
  let depth = 0, end = start;
  for (; end < src.length; end++) {
    if (src[end] === '(') depth++;
    else if (src[end] === ')') { depth--; if (depth === 0) { end++; break; } }
  }
  const block = src.slice(start, end);
  const out = new Map();
  // ⚠ 必须**连体重那两个数字一起匹配**：档位常量后面还跟着战斗档案
  //   （steady(...) / erratic(...) / burst(...)），不锁定前四个参数的话
  //   非贪婪匹配会一路滑到**下一条鱼**的档位常量上 —— 那样核出来的区间是别人的，
  //   "兑换品质必须落在该鱼种区间内"这条断言就变成随机通过/失败了。
  for (const m of block.matchAll(
    /new FishSpecies\(\s*"([a-z_]+)"\s*,\s*ModItems\.[A-Z0-9_]+\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([A-Z_]+)\s*,/g)) {
    const arr = consts.get(m[4]);
    if (!arr) throw new Error('读不出档位常量 ' + m[4] + '（' + m[1] + '）');
    out.set(m[1], [Math.min(...arr), Math.max(...arr)]);
  }
  return out;
})();

const exchangeWarnings = [];
{
  const seen = new Set();      // 出现过哪些鱼（下面"还没有兑换"那条信息用它）
  const seenSig = new Set();   // 出现过哪些**完整条目**（抓"完全重复"用）
  for (const x of EXCHANGES) {
    const name = zh['item.anglertackle.' + x.fish] || x.fish;
    if (!fishes.includes(x.fish)) {
      checks.push('❌ 兑换表里的鱼 `' + x.fish + '` 不在鱼种表里');
      continue;
    }
    // ⚠ 2026-10-11 改：这里原来写死"一种鱼只该有一条兑换"。
    //   那条规则与用户后来定下的口径冲突（**允许一种鱼开多条** —— 同一味鱼在不同档位
    //   给不同的东西本来就是设计意图，例如北非胡子鲇：金档耐久 III，另有两条模组附魔）。
    //   所以判据从"每条鱼只能出现一次"换成"**不许出现一模一样的重复条目**" ——
    //   后者才是真该抓的错（复制粘贴留下的重复），前者会把有意为之的设计判红。
    seen.add(x.fish);
    const sig = [x.fish, x.grade, x.count, x.result.item, x.result.count, x.enchant || '', x.level || 0].join('|');
    if (seenSig.has(sig)) {
      checks.push('❌ 兑换表里有一条**完全重复**的条目（' + name + ' / ' + x.grade + ' / '
        + (x.enchant || x.result.item) + '）—— 复制粘贴留下的，玩家会看到两个一样的兑换');
    }
    seenSig.add(sig);
    const range = fishRanges.get(x.fish);
    if (!range) {
      checks.push('❌ 读不出 ' + name + ' 的档位区间（兑换表要拿它核对最低品质）');
      continue;
    }
    const min = GRADE_TIER[x.grade];
    if (min === undefined) {
      checks.push('❌ ' + name + ' 的兑换写了未知品质 `' + x.grade + '`');
    } else if (min < range[0] || min > range[1]) {
      checks.push('❌ ' + name + ' 的兑换要求 **' + GRADE_CN_SHORT[x.grade] + '** 品质，但这条鱼只能出 '
        + GRADE_CN_SHORT[Object.keys(GRADE_TIER).find((k) => GRADE_TIER[k] === range[0])] + '~'
        + GRADE_CN_SHORT[Object.keys(GRADE_TIER).find((k) => GRADE_TIER[k] === range[1])]
        + ' —— 这条兑换永远兑不出来');
    }
    if (!(x.count >= 1)) checks.push('❌ ' + name + ' 的兑换条数不合法：' + x.count);
    if (!/^[a-z_]+:[a-z_/]+$/.test(x.result.item)) {
      checks.push('❌ ' + name + ' 的产出物品 id 格式不对：' + x.result.item);
    }
    if (!(x.result.count >= 1)) checks.push('❌ ' + name + ' 的产出数量不合法：' + x.result.count);
    // ⚠ 用户 2026-10-02："高品质鱼兑换的物品却能出现海晶灯这样的廉价物。"
    //   判据：每个档位的产出价值必须落在 VALUE_BAND[档位] 区间内
    //   （价值表在 shop-rules.js，按原版稀有度粗估；不是定价，只是卡住明显不合理的组合）。
    const unit = x.enchant !== undefined
      ? ENCHANT_VALUE[x.enchant]
      : RESULT_VALUE[x.result.item];
    if (unit === undefined || unit === 0) {
      checks.push('❌ ' + name + ' 的产出 `' + (x.enchant || x.result.item)
        + '` 没有登记价值（请补进 shop-rules.js 的 RESULT_VALUE / ENCHANT_VALUE，'
        + '否则核不了"越档越贵"）');
    } else {
      const value = unit * x.result.count;
      const band = VALUE_BAND[x.grade];
      if (band && (value < band[0] || value > band[1])) {
        checks.push('❌ ' + name + '（' + GRADE_CN_SHORT[x.grade] + '档）的产出价值 ' + value
          + ' 不在该档区间 [' + band[0] + ', ' + band[1] + '] 内 —— '
          + (value < band[0] ? '高档鱼给了太廉价的东西（用户报过：红鱼换海晶灯）'
                             : '低档鱼给得太贵了'));
      }
    }
    // ⚠ 用户 2026-10-02 明确："兑换附魔特指附魔书"。
    //   产出必须是 `minecraft:enchanted_book` —— 写成 `minecraft:book` + 附魔组件的话，
    //   拿到手的是一本**普通书**（书不能用附魔，那个组件等于摆设），玩家会以为兑换坏了。
    if (x.enchant !== undefined && x.result.item !== 'minecraft:enchanted_book') {
      checks.push('❌ ' + name + ' 带附魔但产出不是附魔书（' + x.result.item
        + '）—— 用户明确"兑换附魔特指附魔书"，请写 minecraft:enchanted_book');
    }
    if (x.enchant !== undefined && !/^[a-z_]+:[a-z_/]+$/.test(x.enchant)) {
      checks.push('❌ ' + name + ' 的附魔 id 格式不对：' + x.enchant);
    }
    // ⚠ 2026-10-11 改：这里原来写死"附魔必须是原版附魔"（引用户 2026-10-02
    //   "如果是说原创附魔就缓缓别弄"）。用户当天澄清了那句话的语境：
    //   **"当时是在搞原创附魔制作，但很显然已经弄完了，这句话没有遵守的必要"**
    //   —— "缓缓"是**临时口径**（等附魔做完再说），现在附魔早就做完了 ⇒ 规则**过期**。
    //   保留的判据改成"命名空间必须是我们认识的"（原版 / 本模组），拼错照样红：
    //   真正"这个附魔存不存在"由 ENCHANT_VALUE 那张**人工维护的价值表**兜底
    //   （没登记的会报"没有登记价值"⇒ 编不出一个不存在的附魔）。
    if (x.enchant !== undefined && !/^(minecraft|anglertackle):/.test(x.enchant)) {
      checks.push('❌ ' + name + ' 的附魔命名空间不认识（' + x.enchant
        + '）—— 只允许 minecraft:（原版）或 anglertackle:（本模组）');
    }
  }
  const missing = fishes.filter((f) => !seen.has(f));
  if (missing.length) {
    // 不是硬错误：但"每种鱼都能换点什么"是这张表的设计意图，缺了要说出来
    exchangeWarnings.push('ℹ 这些鱼还没有兑换：' + missing.join(', '));
  }
  if (EXCHANGES.length < 20) {
    checks.push('❌ 兑换表只有 ' + EXCHANGES.length + ' 条，用户要的是**至少 20 种**');
  }
  // 用户点名要的三条：写死在这里，改没了就红
  const must = [
    ['topmouth_culter', 'PURPLE', 2, 'minecraft:iron_ingot', 16],
    ['pike', 'GOLD', 2, 'minecraft:diamond', 3],
    ['oxeye_tarpon', 'RED', 1, 'minecraft:enchanted_book', 1, 'minecraft:mending'],
  ];
  for (const [fish, grade, count, item, n, ench] of must) {
    const hit = EXCHANGES.find((x) => x.fish === fish && x.grade === grade && x.count === count
      && x.result.item === item && x.result.count === n && x.enchant === ench);
    if (!hit) {
      checks.push('❌ 用户点名的兑换不见了：' + count + ' ' + GRADE_CN_SHORT[grade] + (zh['item.anglertackle.' + fish] || fish)
        + ' → ' + n + ' ' + item + (ench ? '（' + ench + '）' : ''));
    }
  }
}

function build() {
  const total = entries.length;
  const byCat = ORDER.map((c) => CAT_NAME[c] + ' ' + entries.filter((e) => e.cat === c).length).join('，');
  console.log('解析到配件 ' + total + ' 件：' + byCat);
  console.log('兑换表 ' + EXCHANGES.length + ' 条（鱼 → 原版物品，' +
    EXCHANGES.filter((x) => x.enchant).length + ' 条带附魔）');
  if (problems.length) {
    console.log('\n❌ 有 ' + problems.length + ' 处解析/定价问题：');
    for (const p of problems) console.log('   - ' + p);
  }
  for (const c of checks) console.log(c);
  for (const w of exchangeWarnings) console.log(w);
  if (todo.length) console.log('\n⚠ ' + todo.length + ' 件商品价格待用户确认（没有编数字填）');
  return {
    md, problems, checks, todo, total, entries,
    fail: problems.length + checks.filter((c) => c.startsWith('❌')).length,
  };
}

if (require.main === module) {
  const r = build();
  if (process.argv.includes('--check')) {
    const cur = fs.existsSync(OUT) ? fs.readFileSync(OUT, 'utf8') : null;
    if (cur === null) {
      console.error('❌ ' + path.relative(ROOT, OUT) + ' 不存在 —— 跑 node tools/gen-shop-prices.js --write');
      process.exit(1);
    }
    if (cur !== r.md) {
      console.error('\n❌ docs/18-商店价目表.md 与规则不一致（生成物漂移）');
      console.error('   修法：node tools/gen-shop-prices.js --write');
      const A = cur.split('\n'), B = r.md.split('\n');
      for (let i = 0; i < Math.max(A.length, B.length); i++) {
        if (A[i] !== B[i]) {
          console.error('   第一处差异在第 ' + (i + 1) + ' 行：');
          console.error('     文档里： ' + (A[i] === undefined ? '(没有这一行)' : A[i]));
          console.error('     应当是： ' + (B[i] === undefined ? '(不该有这一行)' : B[i]));
          break;
        }
      }
      process.exit(1);
    }
    console.log('\n✅ docs/18-商店价目表.md 与规则一致（' + r.total + ' 件配件）');
    // Java 目录也要比对 —— 它才是游戏真正读的那份
    const js = javaSource(r);
    const curJ = fs.existsSync(JAVA_OUT) ? fs.readFileSync(JAVA_OUT, 'utf8') : null;
    if (curJ !== js) {
      console.error('❌ ShopCatalog.java 与规则不一致（生成物漂移）');
      console.error('   修法：node tools/gen-shop-prices.js --write');
      process.exit(1);
    }
    console.log('✅ ShopCatalog.java 与规则一致');
    process.exit(r.fail === 0 ? 0 : 1);
  }
  if (process.argv.includes('--write')) {
    fs.mkdirSync(path.dirname(OUT), { recursive: true });
    fs.writeFileSync(OUT, r.md, 'utf8');
    console.log('\n✅ 已写出 ' + path.relative(ROOT, OUT));
    const js = javaSource(r);
    fs.mkdirSync(path.dirname(JAVA_OUT), { recursive: true });
    fs.writeFileSync(JAVA_OUT, js, 'utf8');
    console.log('✅ 已写出 ' + path.relative(ROOT, JAVA_OUT)
      + '（' + r.entries.filter((e) => !e.notForSale).length + ' 件商品）');
  } else {
    console.log('\n（预览模式，加 --write 写入 ' + path.relative(ROOT, OUT) + '）');
  }
  process.exit(r.fail === 0 ? 0 : 1);
}

// ================================================================ 生成 ShopCatalog.java

function javaSource(r) {
  const J = [];
  J.push('package com.whitecobalt.anglertackle.shop;');
  J.push('');
  J.push('import java.util.List;');
  J.push('');
  J.push('/**');
  J.push(' * 商店目录（老柯卖什么、多少钱、几档解锁）。');
  J.push(' *');
  J.push(' * <p><b>本文件由 {@code tools/gen-shop-prices.js} 生成，不要手改。</b>');
  J.push(' * 规则在 {@code tools/shop-rules.js}，改动后重跑：');
  J.push(' * {@code node tools/gen-shop-prices.js --write}。');
  J.push(' *');
  J.push(' * <p>为什么生成而不是手写：40 件商品的价格/免费/兑换/限购散在手写代码里，');
  J.push(' * 改一次规则就要记得改好几处，而漏改的表现是"某件商品价格不对"——');
  J.push(' * 没人会去逐个核对 40 件。生成 + 漂移检查能保证规则和代码永远一致。');
  J.push(' */');
  J.push('public final class ShopCatalog {');
  J.push('');
  J.push('    /** 商品分类（商店的"分页"就按它分）。 */');
  J.push('    public enum Category { ROD, LINE, REEL, SINKER, LURE, BAIT, CHUM }');
  J.push('');
  J.push('    /** 获取方式。 */');
  J.push('    public enum Kind {');
  J.push('        /** 免费（白/绿鱼钩、麻线、手线轮）。 */');
  J.push('        FREE,');
  J.push('        /** 花鱼币买。 */');
  J.push('        BUY,');
  J.push('        /** 用鱼兑换（消耗鱼，不给钱）。 */');
  J.push('        EXCHANGE');
  J.push('    }');
  J.push('');
  J.push('    /**');
  J.push('     * 一条商品。');
  J.push('     *');
  J.push('     * @param id            物品 id（不含命名空间）');
  J.push('     * @param category      分类（分页用）');
  J.push('     * @param tier          品质档位 1–6');
  J.push('     * @param kind          获取方式');
  J.push('     * @param price         买价（{@code BUY} 才有意义；{@code FREE} 是 0）');
  J.push('     * @param unlockStage   需要老柯等级到几档才能买（1 = 开局）');
  J.push('     * @param stockPer      每买几个售罄（0 = 不限购）');
  J.push('     * @param restockMinutes 售罄后等几分钟补货（0 = 不补货，即不限购）');
  J.push('     * @param exchange      兑换要求的描述（人类可读，判定逻辑另写）；空串 = 不是兑换');
  J.push('     */');
  J.push('    public record Entry(String id, Category category, int tier, Kind kind, int price,');
  J.push('                        int unlockStage, int stockPer, int restockMinutes, String exchange) {');
  J.push('');
  J.push('        public boolean limited() {');
  J.push('            return stockPer > 0 && restockMinutes > 0;');
  J.push('        }');
  J.push('    }');
  J.push('');
  J.push('    public static final List<Entry> ENTRIES = List.of(');  const entryLines = [];
  for (const cat of ORDER) {
    const list = r.entries.filter((e) => e.cat === cat)
      .sort((a, b) => (a.tier - b.tier) || a.id.localeCompare(b.id));
    if (!list.length) continue;
    entryLines.push('            // ---- ' + CAT_NAME[cat]);
    for (const e of list) {
      if (e.notForSale) {
        entryLines.push('            // ' + e.id + ' —— 调试物品，**不进商店**（' + e.notForSale + '）');
        continue;
      }
      const rule = e.rule || {};
      const kind = rule.kind === 'free' ? 'FREE' : (rule.kind === 'exchange' ? 'EXCHANGE' : 'BUY');
      const price = rule.price || 0;
      const per = (rule.restock && rule.restock.per) || 0;
      const mins = (rule.restock && rule.restock.minutes) || rule.restockMinutes || 0;
      let ex = '';
      if (kind === 'EXCHANGE') {
        ex = (rule.fish.kind === 'anyRed' ? 'anyRed' : rule.fish.ids.join('+'))
          + (rule.grade ? '@' + rule.grade : '')
          + 'x' + (rule.count || 1)
          + (rule.each ? '@each' : '');
      }
      entryLines.push('            new Entry("' + e.id + '", Category.' + cat.toUpperCase() + ', ' + e.tier
        + ', Kind.' + kind + ', ' + price + ', ' + unlockStageOfEntry(e.cat, e.tier) + ', ' + per + ', ' + mins
        + ', "' + ex + '")');
    }
  }
  // 用 join 而不是逐行带逗号 —— 最后一条不能有逗号
  J.push(entryLines.join(',\n'));
  J.push('    );');
  J.push('');
  J.push('    /**');
  J.push('     * 一条「鱼 → 原版物品」的兑换（用户 2026-10-02 定案）。');
  J.push('     *');
  J.push('     * <p>与上面的 {@link Entry} 分开：那个是"卖模组配件"，这个是"用鱼换原版物品"，');
  J.push('     * 产出可以是原版物品（甚至附魔书），不受配件目录约束。');
  J.push('     *');
  J.push('     * @param fishId      要交的鱼种 id');
  J.push('     * @param minTier     最低品质档（**0 起算**：0 白 … 5 红），达到这一档及以上的该种鱼才算');
  J.push('     * @param fishCount   要几条');
  J.push('     * @param resultId    产出物品 id（可以是原版物品）');
  J.push('     * @param resultCount 产出几个');
  J.push('     * @param enchant     附魔 id（空串 = 不附魔）—— 附魔书不是普通物品，得单独带组件');
  J.push('     * @param enchantLevel 附魔等级（1 = 不写等级的普通附魔）');
  J.push('     * @param fixedSpecies 产出的**固定**鱼种（空串 = 产出不带鱼数据）。用户 2026-10-11 的 A3：');
  J.push('     *                     兑换"熏鱼"必须给固定组件 —— 否则 16 个各带一份不同数据 ⇒ **堆不起来** ✗');
  J.push('     * @param fixedTier    固定档位（0 起算；金 = 4）');
  J.push('     * @param fixedWeightKg 固定重量（kg）—— 它也是组件的一部分，必须固定');
  J.push('     */');
  J.push('    public record Exchange(String fishId, int minTier, int fishCount,');
  J.push('                           String resultId, int resultCount, String enchant, int enchantLevel,');
  J.push('                           String fixedSpecies, int fixedTier, float fixedWeightKg) {');
  J.push('');
  J.push('        public boolean enchanted() {');
  J.push('            return !enchant.isEmpty();');
  J.push('        }');
  J.push('');
  J.push('        /** 这条兑换的产出要不要写**固定鱼数据**（熏鱼那两条用得上）。 */');
  J.push('        public boolean fixedFishData() {');
  J.push('            return !fixedSpecies.isEmpty();');
  J.push('        }');
  J.push('    }');
  J.push('');
  J.push('    public static final List<Exchange> EXCHANGES = List.of(');
  J.push(EXCHANGES.map((x) => '            new Exchange("' + x.fish + '", ' + GRADE_TIER[x.grade]
    + ', ' + x.count + ', "' + x.result.item + '", ' + x.result.count
    + ', "' + (x.enchant || '') + '", ' + (x.level || 1)
    // ★ A3（2026-10-11）：固定鱼数据三件套 —— 兑换熏鱼时用它保证"16 个能堆成一摞" ✓
    + ', "' + (x.fixedChunk ? x.fixedChunk.species : '') + '"'
    + ', ' + (x.fixedChunk ? x.fixedChunk.tier : 0)
    + ', ' + (x.fixedChunk ? x.fixedChunk.weightKg : 0) + 'f)').join(',\n'));
  J.push('    );');
  J.push('');
  // ---- 联赛商店（`docs/39` §4）：花**联赛积分**换东西 ----
  //   为什么单开一张表：附魔书 / 固定品质鱼 / 原版物资 / 称号**都不是模组配件**，
  //   上面那张 Entry（从 ModItems 解析）装不下 ✗；但产物字段与 Exchange **同构**
  //   ⇒ 发货时拼一个 Exchange 交给 ShopMaterials.resultStack(...) —— **加商品仍是同一套逻辑** ✓
  J.push('    /**');
  J.push('     * 一条**联赛商店报价**（花联赛积分换东西，`docs/39` §4）。');
  J.push('     *');
  J.push('     * <p>产物字段与 {@link Exchange} 同构：发货时拼一个 Exchange 丢给');
  J.push('     * {@code ShopMaterials.resultStack(...)} 即可，所以附魔书 / 固定品质鱼 / 原版物资');
  J.push('     * **全都走现成那条路**，不需要第二套发货代码 ✓');
  J.push('     *');
  J.push('     * @param id             稳定 id（进存档，**永不改名**；也是"每届限购"的计数键）');
  J.push('     * @param points         积分价');
  J.push('     * @param limitPerSeason 每届限购几个（0 = 不限购）');
  J.push('     * @param tier           档位（0 白 … 5 红；界面排序与取色用）');
  J.push('     * @param resultId       产物物品 id（**可带命名空间** ⇒ 原版物品也装得下）');
  J.push('     * @param resultCount    产物数量');
  J.push('     * @param enchant        附魔 id（空串 = 不附魔）');
  J.push('     * @param enchantLevel   附魔等级');
  J.push('     * @param fixedSpecies   固定鱼种（空串 = 不带鱼数据）');
  J.push('     * @param fixedTier      固定档位（0 起算；红 = 5）');
  J.push('     * @param fixedWeightKg  固定重量');
  J.push('     * @param titleTemplate  买到手授予的称号模板 id（空串 = 不发称号）');
  J.push('     */');
  J.push('    public record LeagueOffer(String id, int points, int limitPerSeason, int tier,');
  J.push('                             String resultId, int resultCount, String enchant, int enchantLevel,');
  J.push('                             String fixedSpecies, int fixedTier, float fixedWeightKg,');
  J.push('                             String titleTemplate) {');
  J.push('');
  J.push('        /** 每届限购 0 = 不限购。 */');
  J.push('        public boolean limited() {');
  J.push('            return limitPerSeason > 0;');
  J.push('        }');
  J.push('    }');
  J.push('');
  J.push('    public static final List<LeagueOffer> LEAGUE_OFFERS = List.of(');
  J.push(LEAGUE_OFFERS.map((o) => {
    const rr = o.result || {};
    const fx = rr.fixedChunk || null;
    return '            new LeagueOffer("' + o.key + '", ' + o.points + ', ' + (o.limit || 0)
      + ', ' + o.tier + ', "' + (rr.item || '') + '", ' + (rr.count || 0)
      + ', "' + (o.enchant || '') + '", ' + (o.level || 1)
      + ', "' + (fx ? fx.species : '') + '"'
      + ', ' + (fx ? fx.tier : 0)
      + ', ' + (fx ? fx.weightKg : 0) + 'f'
      + ', "' + (o.title || '') + '")';
  }).join(',\n'));
  J.push('    );');
  J.push('');
  J.push('    private ShopCatalog() {');
  J.push('    }');
  J.push('}');
  return J.join('\n') + '\n';
}

module.exports = { build, OUT, JAVA_OUT, javaSource, ORDER, CAT_NAME, unlockStageOf };
