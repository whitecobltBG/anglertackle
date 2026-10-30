/*
 * shop-rules.js —— 钓鱼商店的**单一数据源**（价格 / 免费 / 兑换 / 限购补货 / 解锁链）。
 *
 * 为什么单独一个文件而不是散在代码里：
 *   商店这一块最容易出的错是"规则之间打架"—— 价格和档位对不上、
 *   兑换用的鱼不存在、解锁链有断档、限购没写补货时间。
 *   这些**在游戏里都只表现为"买不了 / 价格不对"**，而没人会去逐个核对 40 件商品。
 *   放成一份数据，就能在离线时全部断言掉（见 gen-shop-prices.js 的自检）。
 *
 * 档位约定（和 PartStats.partTier() 对齐，档位从 1 起）：
 *   竿   1=白木 2=绿铁 3=蓝金 4=紫钻 5=金合金
 *   鱼线 1=麻 2=棉 3=尼龙 4=碳氟 5=PE 6=钛合金
 *   渔轮 1=手线轮 2=纺车轮 3=鼓轮 4=水滴轮 5=大型纺车轮
 *   鱼钩 1=普通钩 2=倒刺钩
 *   拟饵 2=绿 3=蓝 4=紫 5=金 6=红（没有白档）
 *   鱼饵 0=面包屑 1=蚯蚓 2=玉米 3=虾 4=发光 5=秘制
 *   窝料 0=谷物 1=腥味肉 2=甜香果 3=秘制
 *
 * ⚠ 标记 `TODO` 的是用户没给、需要他确认的。**不要自己编数字填上** ——
 *   编了之后没人分得清哪个是设计、哪个是我猜的。
 */

/** 免费。 */
const FREE = { kind: 'free' };

/** 花鱼币买。 */
const buy = (price, extra = {}) => Object.assign({ kind: 'buy', price }, extra);

/** 用鱼兑换（不给钱）。 */
const exchange = (spec) => Object.assign({ kind: 'exchange' }, spec);

const RULES = {
  rod: {
    1: buy(200, { note: '第一次对话结束后**免费各给一根**（台钓 + 路亚），这是补买价' }),
    2: buy(300, { note: '台钓可直接买；绿路亚竿在"等级评定"交付**绿品质以上梭鲈**后免费给一根' }),
    3: buy(1500, { note: '等级评定交付**蓝品质以上梭鲈**后解锁' }),
    4: buy(4500, { note: '等级评定交付**紫品质以上任意鱼 ×2**后解锁' }),
    5: buy(15000, { note: '等级评定交付**金品质以上鱼 ×2**后解锁' }),
  },
  line: {
    1: FREE,
    2: buy(200),
    3: buy(500),
    4: buy(2300),
    5: buy(2700, { restock: { per: 5, minutes: 120 } }),
    6: exchange({ fish: { kind: 'anyRed' }, count: 2, restockMinutes: 120, consumes: true }),
  },
  reel: {
    1: FREE,
    2: buy(500),
    3: buy(3750),
    4: buy(20000),
    // 用户确认：**三种各一条**，且**所有兑换都消耗鱼**。
    5: exchange({
      fish: { kind: 'specific', ids: ['atlantic_salmon', 'brown_trout', 'tigerfish'] },
      grade: 'RED',
      count: 1,
      each: true,          // 三种各一条（不是任意一种）
      consumes: true,      // 鱼被收走
    }),
  },
  // ④ 铅锤（2026-10-30 替换鱼钩槽）：**统一 100 鱼币**（用户定案）。
  // ④ 铅锤（2026-10-30 替换鱼钩槽）：**统一 100 鱼币**（用户定案）。
  // 规则表是按**档位**索引的（生成器从 Rarity 得到 1/2/3），所以这里写 1~3。
  sinker: {
    1: buy(100, { note: `g8：浅层 ×1.30；g2：浅层 ×1.05 / 中层 ×1.20` }),
    2: buy(100, { note: `3b：中层 ×1.30；5b：中层 ×1.05 / 底层 ×1.20；0.3号：底层 ×1.05 / 贴底 ×1.20` }),
    3: buy(100, { note: `0.5号：贴底 ×1.30` }),
  },
  // 拟饵：**按每点耐久计价**（用户 2026-09-30 确认"拟饵每点耐久"）。
  //
  // 单价随档位上升（11→35），而耐久随档位下降（40→14），所以**总价各档接近**。
  // 这不是巧合也不是 bug —— 是"拟饵都是消耗品、各档总价差不多"的设计。
  // 总价由生成器从 ModItems 解析出的耐久算出来，直接显示在文档里。
  lure: {
    2: { kind: 'buy', perDurability: 11 },
    3: { kind: 'buy', perDurability: 15 },
    4: { kind: 'buy', perDurability: 24 },
    5: { kind: 'buy', perDurability: 29 },
    6: { kind: 'buy', perDurability: 35 },
  },
  // 鱼饵：2026-10-01 加了耐久（用户："台钓耐久和路亚一样，具体自己看着办"），
  // 所以计价方式统一成**每点耐久** —— 和拟饵完全一致。
  //
  // 耐久值定在 ModItems 里（40/40/30/22/20/20，按档位从小到大对齐拟饵），
  // 生成器会从 `.durability(N)` 解析出来，这里不用重复写。
  //
  // ⚠ 两边的"档位"语义其实不同（拟饵档位=能钓多大，鱼饵档位=品质下限），
  //   这里只是按顺序对齐。如果对齐方式不对，说一声。
  bait: {
    0: { kind: 'buy', perDurability: 11 },
    1: { kind: 'buy', perDurability: 15 },
    3: { kind: 'buy', perDurability: 24 },
    4: { kind: 'buy', perDurability: 29 },
    5: { kind: 'buy', perDurability: 35 },
  },
  chum: {
    // ⚠ 2026-10-11 用户口径："商店所有窝料定价提升 1 到 4 倍，**注意是阶梯级涨价**，
    //    如白涨一倍、紫涨 4 倍" ⇒ 按档位线性阶梯：×2 / ×2.5 / ×3 / ×4。
    //    原值 20 / 50 / 100 / 200 ⇒ 新值 40 / 125 / 300 / 800。
    //    为什么高档涨得更狠是有道理的：白窝只加金红占比，紫窝还**抬品质下限**（chum_floor.epic
    //    = 蓝以上）—— 两者不是一个量级的东西，等比例涨价反而会让紫窝显得太便宜。
    0: buy(40),
    1: buy(125),
    2: buy(300),
    3: buy(800),
  },
};

/**
 * 解锁链 —— 每一档要"交付什么"才解锁。
 *
 * `stage` 是竿的档位。`deliver` 描述等级评定的交付物。
 * `unlocks` 是这一步解锁的可购买范围（自然语言，落到代码时要变成具体集合）。
 */
const UNLOCK_CHAIN = [
  {
    stage: 1, label: '白', rodName: '木',
    deliver: null,
    unlocks: '开放商店（右键老柯）。白竿 200 可买；**路亚最低价配件**可买。',
    free: '第一次对话结束后免费给 **1 根木台钓竿 + 1 根木路亚竿**',
  },
  {
    stage: 2, label: '绿', rodName: '铁',
    deliver: { fish: 'zander', grade: 'UNCOMMON_OR_BETTER', count: 1 },
    unlocks: '绿路亚竿、绿鱼线、蓝拟饵、绿鱼饵、白窝料 可买（与台钓同价）',
    free: '交付后**免费给 1 根绿路亚竿**',
  },
  {
    stage: 3, label: '蓝', rodName: '金',
    deliver: { fish: 'zander', grade: 'RARE_OR_BETTER', count: 1 },
    unlocks: '高一级的路亚竿（蓝）与高一级的配件',
  },
  {
    stage: 4, label: '紫', rodName: '钻石',
    deliver: { fish: null, grade: 'EPIC_OR_BETTER', count: 2, note: '任意鱼' },
    unlocks: '**所有非红配件**可买',
  },
  {
    stage: 5, label: '金', rodName: '下界合金',
    deliver: { fish: null, grade: 'LEGENDARY_OR_BETTER', count: 2, note: '任意鱼' },
    unlocks: '**所有配件**可买 + **开放兑换**（红鱼线、金渔轮）',
  },
];

/** 补货规则汇总（限购那些）。 */
const RESTOCK = [
  { item: '金鱼线（PE 线）', rule: '每买 5 个售罄，等 120 分钟补货', key: 'line.5' },
  { item: '红鱼线（钛合金线）', rule: '每次兑换后等 120 分钟才能再兑换', key: 'line.6' },
];

/**
 * 等级评定（突破）规则。
 *
 * 用户 2026-10-02 把等级细分成了 **1–50 级 + 每 10 级一次突破**：
 * <blockquote>
 *   每钓上来一条鱼获得 10 经验，白/绿/蓝/紫/金/红 额外加 2/8/16/24/32/48；
 *   每级所需经验从 100 涨到 2000；每 10 级解锁一次评定界面，交材料突破到下一级，
 *   不突破就卡在那一级、不积攒经验；到 40 级突破完后不会再有评定任务。
 * </blockquote>
 *
 * 「等级评定后等级就上升了，不能反复交，消耗。不够时提示品质或种类不满足要求。」
 * —— 仍然是**单向一次性**的：交付 → 消耗 → 突破 → 永久解锁商店档位。
 *
 * ⚠ 这份是**文档/检查器用的镜像**，游戏里那份在
 * {@code player/AnglerLeveling.java} 的 {@code GATES}
 * （{@code tools/check-shop-ui.js} 会拿两边逐条比对）。
 * 键是**评定等级**（10/20/30/40），不再是"目标档位"。
 */
const RATING_RULES = {
  oneWay: true,          // 单向：评过就不能再评
  consumes: true,        // 交付的鱼被消耗
  rejectMessage: '品质或种类不满足要求',
  maxLevel: 50,
  xpPerCatch: 10,
  tierBonus: [2, 8, 16, 24, 32, 48],
  xpFirst: 100,
  xpLast: 2000,
  gateStep: 10,
  // 每个评定点的交付要求（键 = 等级；数组里每条 = 一种鱼）
  requirements: {
    10: [{ fish: 'zander', minGrade: 'UNCOMMON', count: 1 }],          // 绿梭鲈
    20: [{ fish: 'mullet', minGrade: 'RARE', count: 1 },               // 蓝鲻鱼
         { fish: 'zander', minGrade: 'RARE', count: 1 }],              // 蓝梭鲈
    30: [{ fish: 'zander', minGrade: 'EPIC', count: 1 },               // 紫梭鲈
         { fish: 'grass_carp', minGrade: 'RARE', count: 1 }],          // 蓝草鱼
    40: [{ fish: 'juvenile_shark', minGrade: 'LEGENDARY', count: 1 },  // 金幼鲨
         { fish: 'topmouth_culter', minGrade: 'EPIC', count: 1 }],     // 紫翘嘴
  },
};

/**
 * 界面要求（用户原话整理）。
 */
const UI_REQUIREMENTS = [
  '老柯**第一次对话结束后**才开启"右键点开钓鱼商店"；在那之前右键只有台词。',
  '商店内**物品分类分页**（竿 / 鱼线 / 渔轮 / 鱼钩 / 拟饵 / 鱼饵 / 窝料）。',
  '商店里要能进**等级评定**（交付鱼 → 解锁 + 送竿）。评定是**一次性**的，评过就不能再评。',
  '商店里要能进**任务兑换**（红鱼线、金渔轮）。兑换**消耗鱼**。',
  '未解锁的商品要**看得见但买不了**（显示解锁条件），而不是藏起来 —— ' +
    '否则玩家不知道"还要做什么"。',
  '交付物不满足时提示固定文案：**「品质或种类不满足要求」**。',
];

/**
 * 鱼 → **原版物品** 的兑换表（用户 2026-10-02 定案）。
 *
 * 用户原话："加一个兑换，用特定品质鱼兑换原版物品。如2紫色翘嘴兑换16铁锭，
 * 2金色狗鱼兑换3钻石，1大眼海鲢兑换经验修补，至少给我20种兑换表。"
 *
 * 规则：
 *   · `fish`      鱼种 id（必须真实存在，生成器会查）
 *   · `grade`     最低品质档（白/绿/蓝/紫/金/红 = 0..5）。**必须落在该鱼种的档位区间里**，
 *                 否则这条兑换永远兑不出来（比如给"只有白~绿"的鲫鱼写金档）。
 *                 生成器会逐个核对区间。
 *   · `count`     要几条
 *   · `result`    产出：原版物品 id + 个数
 *   · `enchant`   可选：附魔 id（1 级）。目前只有"经验修补"用得上 ——
 *                 附魔书不是普通物品，必须带组件，所以单独一个字段。
 *
 * 数值口径：**越难钓的鱼换越贵的东西**，且同一种鱼只开一条（26 种鱼各一条，
 * 玩家一眼就能记住"这种鱼能换什么"）。铁/铜/金/钻石的数量参考原版的稀有度梯度，
 * 保证"钓一条金枪鱼 ≈ 挖一小会儿矿"，但不至于让钓鱼变成印钞机。
 */
const EXCHANGES = [
  // ---- ★ 蓝/紫档 → 做菜配料（用户 2026-10-10 定案 ✓）----
  //   原话："新增蓝紫鱼兑换**做菜配料**的功能" ✓、"一次兑换**至少给 8 个配料** ✓，
  //   蓝紫鱼上交**至少 2 至多 4**" ✓、"**不改原来的东西**" ✗ ⇒ 这里只**新增行** ✓，
  //   原有的 31 条一条都不动 ✓（重复与"红档永远兑不出来"那两类老问题留给用户拍板 ✗）。
  //   配料 = anglertackle 的盐 / 酱油 / 芥末 / 鱼油（`check-kitchen-items.js` 的 KITCHEN 表 ✓）。
  //   价值口径：总价值 = 单件价值 × 数量 ✓，必须落在 VALUE_BAND（蓝 [24,160] / 紫 [60,400] ✓）。
  { fish: 'golden_loach', grade: 'BLUE', count: 3, result: { item: 'anglertackle:salt', count: 16 },
    note: '金泥鳅晒盐（每档都凑满 8 个以上的配料 ✓）' },
  { fish: 'cave_blindfish', grade: 'BLUE', count: 3, result: { item: 'anglertackle:soy_sauce', count: 8 },
    note: '盲鱼酿酱油：洞穴鱼没眼睛，靠鲜味取胜' },
  { fish: 'atlantic_herring', grade: 'BLUE', count: 4, result: { item: 'anglertackle:salt', count: 16 },
    note: '鲱鱼 → 盐：腌鲱鱼是现实里的老行当' },
  { fish: 'burbot', grade: 'PURPLE', count: 3, result: { item: 'anglertackle:mustard', count: 16 },
    note: '江鳕肝是名菜，配芥末' },
  { fish: 'whitefish', grade: 'PURPLE', count: 3, result: { item: 'anglertackle:fish_oil', count: 8 },
    note: '高白鲑脂肪厚，一榨就是油' },
  { fish: 'arctic_cod', grade: 'BLUE', count: 2, result: { item: 'anglertackle:fish_oil', count: 12 },
    note: '北极鳕鱼肝油：现实里的经典补品（⚠ 这条鱼只能出到蓝 ✓ 不是紫 ✓）' },
  { fish: 'wolffish', grade: 'PURPLE', count: 2, result: { item: 'anglertackle:soy_sauce', count: 32 },
    note: '狼鱼肉多，换来一整坛酱油' },
  { fish: 'sablefish', grade: 'PURPLE', count: 4, result: { item: 'anglertackle:fish_oil', count: 24 },
    note: '裸盖鱼（银鳕）油脂最多 ⇒ 换最多的一份油' },
  // ---- 白/绿档（入门鱼 → 基础物资）----
  { fish: 'banded_loach', grade: 'GREEN', count: 4, result: { item: 'minecraft:bone_meal', count: 16 },
    note: '横纹鱼太小，磨成骨粉喂窝料' },
  { fish: 'crucian_carp', grade: 'GREEN', count: 4, result: { item: 'minecraft:string', count: 8 },
    note: '鲫鱼 → 线：木路亚竿配方要线，前期最缺的就是它' },
  { fish: 'hemiculter', grade: 'GREEN', count: 4, result: { item: 'minecraft:bread', count: 4 },
    note: '白条鱼 → 面包：面包又能做白窝料，形成循环' },
  { fish: 'tilapia', grade: 'GREEN', count: 4, result: { item: 'minecraft:leather', count: 8 },
    note: '罗非鱼皮制革（现实里真有这条产业）' },
  // ---- 蓝档 ----
  { fish: 'grass_carp', grade: 'BLUE', count: 3, result: { item: 'minecraft:hay_block', count: 8 },
    note: '草鱼 → 干草块（吃草的大鱼）' },
  { fish: 'mullet', grade: 'BLUE', count: 3, result: { item: 'minecraft:kelp', count: 24 },
    note: '鲻鱼是咸淡水鱼 → 海带' },
  // ---- 紫档：矿物一律 16 个起步（用户："别给一个两个的太寒酸"）----
  { fish: 'zander', grade: 'PURPLE', count: 3, result: { item: 'minecraft:copper_ingot', count: 16 } },
  { fish: 'striped_bass', grade: 'PURPLE', count: 3, result: { item: 'minecraft:gold_ingot', count: 16 } },
  { fish: 'black_carp', grade: 'PURPLE', count: 3, result: { item: 'minecraft:iron_ingot', count: 16 } },
  // ★ 用户最早的原话就是"2紫色翘嘴兑换16铁锭"，这里**按原话保留 2 条**
  { fish: 'topmouth_culter', grade: 'PURPLE', count: 2, result: { item: 'minecraft:iron_ingot', count: 16 },
    note: '★ 用户指定：2 紫色翘嘴 → 16 铁锭' },
  // ---- 金档：绿宝石 / 鹦鹉螺壳已按用户要求换成附魔书（中坚实用附魔）----
  { fish: 'pike', grade: 'GOLD', count: 2, result: { item: 'minecraft:diamond', count: 3 },
    note: '★ 用户指定：2 金色狗鱼 → 3 钻石（钻石不按 16 个起步，按原话）' },
  { fish: 'north_african_catfish', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:unbreaking', level: 3,
    note: '耐久 III —— 所有装备都要，实用度第一梯队' },
  { fish: 'jewel_cichlid', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:sharpness', level: 5,
    note: '锋利 V —— 剑斧通用' },
  { fish: 'african_butterflyfish', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:protection', level: 4,
    note: '保护 IV —— 全套盔甲通用' },
  { fish: 'elephantnose', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:looting', level: 3,
    note: '抢夺 III —— 刷掉落物必带' },
  { fish: 'mirror_carp', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:luck_of_the_sea', level: 3,
    note: '海之眷顾 III —— 钓鱼人的本命附魔（镜子一样的鲤鱼）' },
  { fish: 'atlantic_salmon', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:lure', level: 3,
    note: '饵钓 III —— 钓鱼速度，和鲑鱼饵呼应' },
  { fish: 'brown_trout', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:respiration', level: 3,
    note: '水下呼吸 III —— 潜水找鱼点用得上' },
  { fish: 'guppy', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:depth_strider', level: 3,
    note: '深海探索者 III —— 水里走得快' },
  { fish: 'tigerfish', grade: 'GOLD', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:power', level: 5,
    note: '力量 V —— 弓' },
  // ---- 红档：全部换成**顶级附魔书**（用户："红档位的海绵金块海洋之心换掉"）----
  //   排序依据 = 实用度 × 稀缺性（见每条 note）。重锤三件套在 1.21 只能从
  //   试炼密室的**凶兆宝库**拿到，公认最稀有 → 放在最难钓的鱼上，
  //   且**每本要 3 条红鱼**（用户 2026-10-02："重锤相关附魔至少3条红鱼给换一本"）。
  { fish: 'sapphire_snakehead', grade: 'RED', count: 3,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:wind_burst', level: 3,
    note: '风爆 III —— 重锤最强附魔，只能出自凶兆宝库（台钓唯一的红鱼才配得上）' },
  { fish: 'juvenile_shark', grade: 'RED', count: 3,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:breach', level: 4,
    note: '破甲 IV —— 重锤无视护甲，PVP/打精英怪顶级' },
  { fish: 'bluefin_tuna', grade: 'RED', count: 3,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:density', level: 5,
    note: '密度 V —— 重锤落击伤害，同样出自凶兆宝库' },
  { fish: 'oxeye_tarpon', grade: 'RED', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:mending', level: 1,
    note: '★ 用户指定：1 大眼海鲢 → 经验修补**附魔书**（全局最实用）' },
  // ── 模组自有附魔兑换（2026-10-11 恢复 + 修正）────────────────────────────────
  //   这 5 条一度被删掉，因为生成器里有一条写死的规则"附魔必须是原版附魔"，
  //   它引的是用户 2026-10-02 那句"如果是说原创附魔就缓缓别弄"。
  //   ⚠ 用户 2026-10-11 澄清了那句话的语境：**"当时是在搞原创附魔制作，但很显然已经弄完了，
  //     这句话没有遵守的必要"** —— 也就是说"缓缓"是**临时口径**（等附魔做完），
  //     附魔早就做完了 ⇒ 这条规则**过期**，没有继续遵守的理由。
  //   ⇒ 所以：附魔照旧从**钓鱼战利品**里出，同时**也允许作兑换产出**（这本就是真内容）。
  //
  //   ⚠ 同时修掉**真正错的那部分**：这 4 条鱼的档位区间都是 P_G = {3,4}（紫~金，见 FishSpecies.java），
  //     而它们原来全写 `grade: 'RED'` ⇒ **5 条全部永远兑不出来**（生成器报过，只是被前面那堆
  //     "原版附魔"报错淹没了）。现在改成 **GOLD**（这条鱼真能到的最高档）。
  { fish: 'north_african_catfish', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'anglertackle:reinforced', level: 1,
    note: '模组自有附魔：加固（渔具更耐用）' },
  { fish: 'jewel_cichlid', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'anglertackle:rescue', level: 1,
    note: '模组自有附魔：救援（脱钩前多一次机会）' },
  { fish: 'african_butterflyfish', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'anglertackle:drag', level: 1,
    note: '模组自有附魔：拖拽（收线更省力）' },
  { fish: 'elephantnose', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'anglertackle:load_bearing', level: 1,
    note: '模组自有附魔：承重（鱼线更能吃重）' },
  { fish: 'north_african_catfish', grade: 'GOLD', count: 3,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'anglertackle:precise_hunt', level: 1,
    note: '模组自有附魔：精准猎杀（更容易咬钩）' },
  // ── A3（用户 2026-10-11）：主粮鱼 → **熏鱼 ×16、品质金、一次一组** ────────────────
  //   用户原话："兑换16个起步的熏鱼，品质金，具体种类随你，但**需要数据一致保证可堆叠性**，
  //             上线兑换一次一组"。
  //   ⚠ 这就是为什么要给 `fixedChunk`：熏鱼带着 FISH_CHUNK（鱼种/档位/重量）⇒
  //     16 个里只要一项不同就分成 16 摞 ✗ ⇒ 必须用**固定值**构造（见 ShopMaterials.resultStack ✓）。
  //   鱼种我挑的是"交什么鱼就熏什么鱼"（大西洋鳕 / 格陵兰大比目鱼各一条）✓
  //   重量取在**该鱼种的真实体重区间内**（鳕 2~40kg ⇒ 5kg ✓；大比目鱼 20~200kg ⇒ 30kg ✓），
  //   档位取 4 = **金** ✓（两者都是 P_G={3,4}，金档可达 ✓）。
  { fish: 'atlantic_cod', grade: 'GOLD', count: 2,
    result: { item: 'anglertackle:smoked_fish', count: 16 },
    fixedChunk: { species: 'atlantic_cod', tier: 4, weightKg: 5.0 },
    note: '★ 用户 A3：2 条金档大西洋鳕 → 16 个熏大西洋鳕（数据固定 ⇒ 一整个堆叠）' },
  { fish: 'greenland_halibut', grade: 'GOLD', count: 2,
    result: { item: 'anglertackle:smoked_fish', count: 16 },
    fixedChunk: { species: 'greenland_halibut', tier: 4, weightKg: 30.0 },
    note: '★ 用户 A3：2 条金档格陵兰大比目鱼 → 16 个熏格陵兰大比目鱼（数据固定 ⇒ 一整个堆叠）' },
  // ── 第二批 11 条（2026-10-11，按用户拍板结果配 ✓）─────────────────────────────
  //   A1 "尽量不要兑换所得重复" ⇒ 每条产出都**没被别处用过** ✓（鱼油除外 —— A2 明确允许 ✓）
  //   A2 "可以有鱼油兑换" ✓
  //   A5 平鲉**换别的**（原来撞镜鲤的海之眷顾 III ✗）⇒ 走紫水晶 ✓
  //   A4 鳄雀鳝**不做专门兑换** ✗ ⇒ 它走"顶替任意兑换要的红鱼"（见 ShopMaterials ✓）
  //   ⚠ 档位一律取"这条鱼**真能到达**的档"（否则永远兑不出来 —— 这正是这一轮修掉的病 ✓）
  { fish: 'capelin', grade: 'GREEN', count: 4,
    result: { item: 'anglertackle:fish_oil', count: 4 },
    note: 'A2：毛鳞鱼是工业鱼（鱼粉/鱼油）⇒ 换鱼油 ✓' },
  { fish: 'arctic_cod', grade: 'BLUE', count: 4,
    result: { item: 'minecraft:nautilus_shell', count: 2 },
    note: '北极鳕：FishBase 记"鱼粉与鱼油来源"⇒ 换海里来的材料 ✓' },
  { fish: 'rainbow_trout', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:emerald', count: 8 },
    note: '虹鳟：养殖食用鱼 ⇒ 换绿宝石（不撞任何已有产出 ✓）' },
  { fish: 'redfish', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:amethyst_shard', count: 12 },
    note: 'A5：平鲉换紫水晶（原来撞镜鲤的海之眷顾 III ✗）' },
  { fish: 'electric_eel', grade: 'GOLD', count: 2,
    result: { item: 'minecraft:redstone_block', count: 4 },
    note: '电鳗：它的"电"⇒ 换红石 ✓（不违反"不做原创附魔"✓）' },
  { fish: 'taimen', grade: 'RED', count: 3,
    result: { item: 'minecraft:netherite_scrap', count: 1 },
    note: '哲罗鲑：顶级掠食鲑科 ⇒ 换下界合金碎片 ✓' },
  { fish: 'arctic_char', grade: 'RED', count: 3,
    result: { item: 'minecraft:diamond_block', count: 1 },
    note: '北极红点鲑 ⇒ 换钻石块 ✓' },
  { fish: 'greenland_shark', grade: 'RED', count: 3,
    result: { item: 'minecraft:heart_of_the_sea', count: 1 },
    note: '格陵兰鲨：深海巨物 ⇒ 换海洋之心 ✓' },
  { fish: 'sailfish', grade: 'RED', count: 3,
    result: { item: 'minecraft:sponge', count: 8 },
    note: '雨伞旗鱼 ⇒ 换海绵（海底神殿的东西 ✓）—— ⚠ 数量从 4 提到 8：4 个只值 240，'
      + '低于红档下限 400（生成器当场抓住 ✓ 正是"红鱼换海晶灯"那类）' },
  { fish: 'mahi_mahi', grade: 'RED', count: 3,
    result: { item: 'minecraft:experience_bottle', count: 24 },
    note: '鲯鳅 ⇒ 换经验瓶 ✓（数量从 16 提到 24：16 个只值 320 < 400 ✗ 同样被生成器抓住）' },
  { fish: 'giant_siamese_carp', grade: 'RED', count: 3,
    result: { item: 'minecraft:golden_apple', count: 8 },
    note: '巨暹罗鲤：东南亚巨型鲤 ⇒ 换金苹果 ✓' },
  { fish: 'arapaima', grade: 'RED', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:fortune', level: 3,
    note: '时运 III —— 挖矿收益翻倍' },
  { fish: 'platinum_arowana', grade: 'RED', count: 1,
    result: { item: 'minecraft:enchanted_book', count: 1 }, enchant: 'minecraft:silk_touch', level: 1,
    note: '精准采集 —— 玻璃/冰/刷怪笼，附魔台上很难刷到' },
];

/**
 * 产出的"参考价值"（鱼币口径，用来核对**档位越高给得越贵**）。
 *
 * 不是定价，只是把"红鱼换海晶灯"这种明显不合理的组合卡住：每档一段允许区间。
 * 附魔书按**实用度 × 稀缺性**估值（重锤附魔最贵：只能从凶兆宝库出）。
 */
const RESULT_VALUE = {
  'minecraft:bone_meal': 1, 'minecraft:string': 2, 'minecraft:bread': 4, 'minecraft:leather': 5,
  'minecraft:kelp': 2, 'minecraft:hay_block': 6,
  // 做菜配料（用户 2026-10-10 定案：蓝/紫鱼可以换它们 ✓，"一次兑换至少给 8 个配料" ✓）
  //   口径与别的产出一致：**总价值 = 单件价值 × 数量** ✓，必须落进 VALUE_BAND ✓。
  //   盐最便宜（水桶 + 干海带 ✓）、鱼油最贵（要榨一堆鱼块 ✓）。
  'anglertackle:salt': 2, 'anglertackle:soy_sauce': 6,
  'anglertackle:mustard': 8, 'anglertackle:fish_oil': 12,
  'minecraft:copper_ingot': 8, 'minecraft:iron_ingot': 10, 'minecraft:gold_ingot': 15,
  'minecraft:glowstone': 12, 'minecraft:glow_ink_sac': 12, 'minecraft:amethyst_shard': 15,
  'minecraft:experience_bottle': 20, 'minecraft:emerald': 25, 'minecraft:ender_pearl': 25,
  'minecraft:name_tag': 30, 'minecraft:nautilus_shell': 40, 'minecraft:diamond': 60,
  'minecraft:sea_lantern': 20, 'minecraft:sponge': 60, 'minecraft:gold_block': 140,
  'minecraft:heart_of_the_sea': 400,
  // 附魔书：按"附魔 id"估值（等级已含在值里，见各自的档位）
  'minecraft:enchanted_book': 0,   // 占位：真正的价值按 enchant 查下表
  // A3（2026-10-11）：熏鱼按"一份顶一顿好饭"估 12 ⇒ 16 个 = 192，落在金档区间内 ✓
  'anglertackle:smoked_fish': 12,
  // 第二批 11 条兑换用到的（2026-10-11 补登记；数值按原版稀有度粗估 ✓ 只为卡住"越档越贵" ✓）
  'minecraft:redstone_block': 45,    // 电鳗（金档）：4 个 = 180 ✓ 落在 [160,900]
  'minecraft:netherite_scrap': 700,  // 哲罗鲑（红档）：1 个 = 700 ✓ 落在 [400,2500]
  'minecraft:diamond_block': 900,    // 北极红点鲑（红档）：1 个 = 900 ✓
  'minecraft:golden_apple': 60,      // 巨暹罗鲤（红档）：8 个 = 480 ✓
  'minecraft:sponge': 60,            // 雨伞旗鱼（红档）
  'minecraft:experience_bottle': 20, // 鲯鳅（红档）
  'minecraft:nautilus_shell': 40,    // 北极鳕（蓝档）：2 个 = 80 ✓ 落在 [24,160]
  'minecraft:emerald': 30,           // 虹鳟（金档）：8 个 = 240 ✓
  'minecraft:amethyst_shard': 20,    // 平鲉（金档）：12 个 = 240 ✓
  'minecraft:heart_of_the_sea': 600, // 格陵兰鲨（红档）：1 个 = 600 ✓
};

/** 附魔书的价值（按"实用度 × 稀缺性"排；重锤三件套最高）。 */
const ENCHANT_VALUE = {
  'minecraft:wind_burst': 1800, 'minecraft:breach': 1500, 'minecraft:density': 1300,
  'minecraft:mending': 1000, 'minecraft:fortune': 800, 'minecraft:silk_touch': 700,
  'anglertackle:reinforced': 400, 'anglertackle:rescue': 450, 'anglertackle:drag': 380,
  // ⚠ 2026-10-11 补：这两条原来漏登记 ⇒ 生成器报"没有登记价值"（这条报得**对** ✗ 不是过期规则）。
  //   值域要落在**金档**区间 [160,900] 内（这两条都被修成金档兑换）。
  'anglertackle:load_bearing': 420, 'anglertackle:precise_hunt': 430,
  'minecraft:unbreaking': 320, 'minecraft:sharpness': 350, 'minecraft:protection': 330,
  'minecraft:looting': 300, 'minecraft:power': 300, 'minecraft:luck_of_the_sea': 260,
  'minecraft:depth_strider': 240, 'minecraft:respiration': 220, 'minecraft:lure': 200,
};

/** 每个档位允许的产出价值区间 [下限, 上限]。 */
const VALUE_BAND = {
  WHITE: [4, 40], GREEN: [8, 60], BLUE: [24, 160],
  PURPLE: [60, 400], GOLD: [160, 900], RED: [400, 2500],
};

/** 品质档名 → 0 起算的档位（与 Quality 的枚举顺序一致）。 */
const GRADE_TIER = { WHITE: 0, GREEN: 1, BLUE: 2, PURPLE: 3, GOLD: 4, RED: 5 };

/**
 * **联赛商店**（`docs/39` §4.1，用户 2026-10-13 逐条给的价与限购）。
 *
 * <h2>为什么单开一张表，而不是塞进上面的 ENTRIES</h2>
 * 上面那张 `RULES` 的商品**是从 `ModItems.java` 解析出来的**（生成器的正则只认那 7 类配件 id），
 * 而联赛商店里有**四样东西根本不是模组配件**：附魔书、固定品质的鱼、原版物资、称号 ✗。
 * 所以这里单列，但**产物字段与 `Exchange` 完全同构**（`result.item/count` + `enchant/level` + `fixedChunk`）
 * ⇒ 发货时能直接把这条报价拼成一个 `Exchange` 丢给 `ShopMaterials.resultStack(...)`
 * —— **"加商品"仍然是同一套逻辑**，只是付钱方式从"交鱼"换成"花积分" ✓。
 *
 * <h2>字段</h2>
 * - `key`：稳定 id（**进了存档，永不改名**；也用于"每届限购"的计数键）
 * - `points`：积分价（用户给的数字，**一个都不许自己发明**）
 * - `limit`：**每届**限购几个（0 = 不限购）
 * - `tier`：给界面排序/取色用的档位（0 白 … 5 红）
 * - `result`：产物（`item` 可带命名空间 ⇒ 原版物品也装得下 ✓）
 * - `enchant` / `level`：附魔书用
 * - `fixedChunk`：固定鱼数据（红品质鱼用它，保证"同一种鱼能堆成一摞" —— 与熏鱼那条同一个理由 ✓）
 * - `title`：买到手时授予的称号**模板 id**（P3 的生成式称号，带赛季号）
 */
const LEAGUE_OFFERS = [
  // ---- 拟饵（用户："任意紫饵（此项代表两种兑换），限购 2（每种）"）----
  { key: 'lure_deep', points: 20, limit: 2, tier: 4, result: { item: 'anglertackle:lure_deep', count: 1 },
    note: '紫档拟饵之一' },
  { key: 'lure_paddletail', points: 20, limit: 2, tier: 4,
    result: { item: 'anglertackle:lure_paddletail', count: 1 }, note: '紫档拟饵之二' },
  // ---- 鱼线（颜色 ←→ 档位；用户："颜色加线，如金线是 pe，红线是钛合金记忆金属线"）----
  { key: 'nylon_line', points: 70, limit: 3, tier: 3, result: { item: 'anglertackle:nylon_line', count: 1 },
    note: '蓝线' },
  { key: 'fluoro_line', points: 280, limit: 3, tier: 4, result: { item: 'anglertackle:fluoro_line', count: 1 },
    note: '紫线' },
  { key: 'pe_line', points: 500, limit: 2, tier: 5, result: { item: 'anglertackle:pe_line', count: 1 },
    note: '金线' },
  // ⚠ 红线**两个价都是红线**（用户："红线在联赛商店设计里就是有两个兑换价格，一个低一个高"）✓
  { key: 'titanium_line_low', points: 750, limit: 1, tier: 6,
    result: { item: 'anglertackle:titanium_line', count: 1 }, note: '红线（低价档）' },
  { key: 'titanium_line_high', points: 1050, limit: 1, tier: 6,
    result: { item: 'anglertackle:titanium_line', count: 1 }, note: '红线（高价档）' },
  // ---- 红品质鱼 ×3（用固定鱼数据，与"熏鱼"同一个理由：能堆 ✓）----
  { key: 'red_tigerfish', points: 300, limit: 1, tier: 5,
    result: { item: 'anglertackle:tigerfish', count: 1,
      fixedChunk: { species: 'tigerfish', tier: 5, weightKg: 0 } }, note: '红品质虎鱼' },
  { key: 'red_atlantic_salmon', points: 300, limit: 1, tier: 5,
    result: { item: 'anglertackle:atlantic_salmon', count: 1,
      fixedChunk: { species: 'atlantic_salmon', tier: 5, weightKg: 0 } }, note: '红品质大西洋鲑' },
  { key: 'red_brown_trout', points: 300, limit: 1, tier: 5,
    result: { item: 'anglertackle:brown_trout', count: 1,
      fixedChunk: { species: 'brown_trout', tier: 5, weightKg: 0 } }, note: '红品质褐鳟' },
  // ---- 附魔书 ×2（走 Exchange 那条现成的附魔路 ✓）----
  { key: 'ench_high_quality_lure_3', points: 500, limit: 0, tier: 4,
    result: { item: 'minecraft:enchanted_book', count: 1 },
    enchant: 'anglertackle:high_quality_lure', level: 3, note: '高质吸引 III' },
  { key: 'ench_mending', points: 500, limit: 0, tier: 4,
    result: { item: 'minecraft:enchanted_book', count: 1 },
    enchant: 'minecraft:mending', level: 1, note: '经验修补' },
  // ---- 原版物资 ×3（item 带命名空间 ⇒ 装得下 ✓）----
  { key: 'iron_ingot_32', points: 200, limit: 1, tier: 1,
    result: { item: 'minecraft:iron_ingot', count: 32 }, note: '铁锭 ×32' },
  { key: 'diamond_8', points: 500, limit: 1, tier: 3,
    result: { item: 'minecraft:diamond', count: 8 }, note: '钻石 ×8' },
  { key: 'netherite_ingot_5', points: 700, limit: 1, tier: 5,
    result: { item: 'minecraft:netherite_ingot', count: 5 }, note: '下界合金锭 ×5' },
  // ---- 纪念称号（P3 的生成式称号：买到手是 S{n}{规模}联赛纪念，金色）----
  { key: 'memorial', points: 500, limit: 1, tier: 4, title: 'league_memorial',
    result: { item: '', count: 0 }, note: 'S{n}{规模}联赛纪念（称号）' },
];

module.exports = {
  RULES, UNLOCK_CHAIN, RESTOCK, UI_REQUIREMENTS, RATING_RULES,
  EXCHANGES, GRADE_TIER, RESULT_VALUE, VALUE_BAND, ENCHANT_VALUE,
  LEAGUE_OFFERS,
  FREE, buy, exchange,
};
