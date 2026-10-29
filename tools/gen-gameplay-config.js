// 把"还写死的玩法数值"全部配置化。
//
// ── 背景 ────────────────────────────────────────────────────
// 到这一步为止，[fishStats]（逐条鱼）与 [partsStats]（逐件配件）已经进配置，
// 但**决胜手感的那些系数**还散在 Java 里写死：
//   · 鱼竿五种材质的承重/窗口/张力倍率（RodSpec）
//   · 张力上限基准 100、冲刺拉拽 0.95、反向控杆奖惩、阻尼上限、拉干阈值…
//   · 档位曲面：tierSpeed 1.12、体力抗性 1.18、挣扎/休息秒数、疲劳期倍率
//   · 抛投蓄力 tick、窝料最小滞空、合成基准
// 玩家想调"上线"，必须能碰这些 —— 否则只能改鱼种表的表面数值，
// 一动系数就要重新编译。
//
// 这个脚本生成 [gameplay] 这一组（字段声明 / 定义 / Tuning getter / 热重载映射），
// 并配一份 JSON 清单供 tools/wire-gameplay.js 做代码替换。
//
// 用法：node tools/gen-gameplay-config.js [--write]
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
const outJson = path.join(__dirname, 'gameplay-config.json');

const GROUP = 'gameplay';

// ------------------------------------------------------------------ 条目定义
// key：TOML 键名 = Java 字段名（保持一致，方便手改）
// cn ：中文标签（调参器显示）
// desc：作用说明
// def：默认值（**必须与源码里的原值逐位相同**，check-gameplay-parity.js 会核对）
// min/max：范围
// type：double / int
const ITEMS = [
  // ================================================================ 鱼竿材质（五种 × 7 项）
  // 说明写在第一项上，其余沿用；调参器按前缀分组显示。
  //
  // 承重上限（每行第 4 个数）用户 2026-10-02 改过两项：
  //   铁竿 20 → 25、钻石竿 40 → 50（"金属杆承重上限改到25，钻石杆承重上限改到50"）。
  // ⚠ 改这里必须同步改 tools/check-gameplay-parity.js 里那张期望值表，
  //   否则构建会在"源码值与期望值不一致"那一步红。
  //
  // ★ 2026-10-08 用户定案（原话："我之前调参的时候把鱼竿一些数据改了但游戏里没改动
  //   比如承重比如品质上限"）：把他 10/06 亲手调过、后来被一次配置重置冲掉的这套值
  //   **烧进默认值** —— 这样以后重置配置也不会丢：
  //     木竿 档位上限 1 → **3**      铁竿 承重 25 → **30**、档位上限 2 → **4**
  //     金竿 承重 12 → **21**、档位上限 3 → **6**（6 = 不再限制，金竿也能出红鱼）
  //   钻石/下界合金不动（50 / 999，档位 4 / 5）。
  //   用户明确选了"全部照搬（含档位上限）"，所以"五级材质各占一个档位"这条旧设计
  //   不再成立 —— tools/dump-rods.js 里那段按旧设计写的断言也一并改了。
  ...['wood:木竿:2000:8:0:1.00:1.00:1.00:1.00:3',
      'iron:铁竿:3500:30:0.10:1.20:1.20:1.20:0.85:4',
      'gold:金竿:2500:21:0.25:1.10:1.15:1.15:0.90:6',
      'diamond:钻石竿:5000:50:0.20:1.60:1.70:1.60:0.70:4',
      'netherite:下界合金竿:8000:999:0.30:1.70:1.90:1.80:0.50:5']
    .flatMap((row) => {
      const [id, cn, dur, wt, win, ceil, rec, drain, mat, tier] = row.split(':');
      const K = id.charAt(0).toUpperCase() + id.slice(1);
      return [
        { key: 'rod' + K + 'MaxWeightKg', cn: cn + ' · 承重上限', unit: 'kg', def: Number(wt),
          min: 0, max: 1000, type: 'double',
          desc: '这根竿能钓多重的鱼（0 = 不限）。⚠ 这是硬闸门：超过的鱼**钓不上来**' },
        { key: 'rod' + K + 'HookWindowBonus', cn: cn + ' · 提竿窗口加成', unit: '秒', def: Number(win),
          min: 0, max: 5, type: 'double', desc: '加到基础提竿窗口上的秒数' },
        { key: 'rod' + K + 'TensionCeiling', cn: cn + ' · 张力上限倍率', unit: '', def: Number(ceil),
          min: 0, max: 10, type: 'double', desc: '与鱼线按权重合成，决定"线绷到多少会断"' },
        { key: 'rod' + K + 'TensionRecover', cn: cn + ' · 张力回复倍率', unit: '', def: Number(rec),
          min: 0, max: 10, type: 'double', desc: '与鱼线合成，决定放线时张力落多快' },
        { key: 'rod' + K + 'StaminaDrain', cn: cn + ' · 体力削减倍率', unit: '', def: Number(drain),
          min: 0, max: 10, type: 'double', desc: '与渔轮合成，决定收线时鱼掉体力多快' },
        { key: 'rod' + K + 'MaterialFactor', cn: cn + ' · 耐久损耗系数', unit: '', def: Number(mat),
          min: 0, max: 5, type: 'double', desc: '越小越耐用（扣耐久时乘它）' },
        { key: 'rod' + K + 'Tier', cn: cn + ' · 品质档位上限', unit: '1~6', def: Number(tier),
          min: 1, max: 6, type: 'int',
          desc: '这根竿最高能出到几档（1白 2绿 3蓝 4紫 5金 6红）。⚠ 调高会让高级鱼变容易出' },
      ];
    }),

  // ================================================================ 张力模型（全局）
  { key: 'tensionBaseCeiling', cn: '张力上限基准', unit: '点', def: 100.0, min: 10, max: 1000,
    type: 'double', desc: '所有张力上限都按它缩放。改大 = 整体变宽容（线更不容易断）' },
  { key: 'pullSprint', cn: '冲刺时的拉拽基数', unit: '', def: 0.95, min: 0, max: 5,
    type: 'double', desc: '鱼冲刺时用来算张力的基值（不冲刺时用「配件属性合成」里的 pullIdleBase）' },
  { key: 'counterBonus', cn: '反向控杆奖励', unit: '', def: -0.5, min: -2, max: 1,
    type: 'double', desc: 'A/D 压住鱼挣扎方向时的张力倍率修正。负值 = 张力反而降' },
  { key: 'counterPenalty', cn: '同向控杆惩罚', unit: '', def: 0.35, min: 0, max: 3,
    type: 'double', desc: '顺着鱼的方向控杆时的张力倍率修正（正值 = 张力涨更凶）' },
  { key: 'minReelGainRatio', cn: '收线涨势保底比例', unit: '0~1', def: 0.25, min: 0, max: 1,
    type: 'double', desc: '收线时鱼拉最多把涨势削到这个比例。0 = 鱼够猛就能把张力压住不涨' },
  { key: 'maxFishSlowdown', cn: '鱼拖慢收线的上限', unit: '0~1', def: 0.90, min: 0, max: 1,
    type: 'double', desc: '鱼最多能把收线速度拖慢多少。1 = 完全拖停' },
  { key: 'dampingToCapRatio', cn: '阻尼折算上限的比例', unit: '', def: 0.50, min: 0, max: 1,
    type: 'double', desc: '渔轮阻尼中"能折算进张力上限"的那部分比例' },
  { key: 'capDampingBonus', cn: '阻尼对上限的加成', unit: '', def: 1.20, min: 0, max: 5,
    type: 'double', desc: '上一条折算后乘的系数，直接加到张力上限上' },
  { key: 'totalDrainTarget', cn: '拉干阈值', unit: '0~1', def: 0.90, min: 0.1, max: 3,
    type: 'double', desc: '累计扣掉鱼多少体力就能"拉干强制上鱼"（1.0 = 扣满一轮体力）' },
  { key: 'nearShoreBase', cn: '近岸基础距离', unit: '格', def: 6.0, min: 1, max: 40,
    type: 'double', desc: '中鱼后线至少被拽到这么远（防止一上钩就贴脸）' },
  { key: 'staminaEpsilonFloor', cn: '体力判定下限', unit: '', def: 0.05, min: 0, max: 50,
    type: 'double', desc: '"体力是否已耗尽"的绝对容差，避免浮点残值反复触发' },
  { key: 'staminaEpsilonRatio', cn: '体力判定比例', unit: '0~1', def: 0.005, min: 0, max: 0.5,
    type: 'double', desc: '同上，按体力上限的比例取容差（取两者较大值）' },
  { key: 'inputStaleLimit', cn: '输入超时上限', unit: 'tick', def: 5, min: 1, max: 100,
    type: 'int', desc: '客户端输入包超过这么久没更新就当作"没在操作"' },

  // ================================================================ 档位曲面（品质 → 手感）
  { key: 'tierSpeedBase', cn: '档位游速底数', unit: '', def: 1.12, min: 1, max: 3,
    type: 'double', desc: '每升一档游速乘它。六档下来是 底数^5 —— 1.12 约 1.76 倍' },
  { key: 'tierStaminaResistBase', cn: '档位体力抗性底数', unit: '', def: 1.18, min: 1, max: 3,
    type: 'double', desc: '每升一档体力抗性乘它（抗性直接除在扣体力公式里）' },
  // ---- 金/红档手感修正（用户 2026-10-05 定案）----------------------------------
  // 原话："发现金档和红手感可能没有差距，游速体力都一样。现有金游速降 16%、体力不变；
  //        红移速加 5%、体力加 50%。"
  // 底数曲线（1.12^档）本身只把金红拉开 12%，玩家感觉不出来，所以金红两档再各乘一层。
  // ⚠ 只影响**档位 4 / 5**：白绿蓝紫一字不动；1.0 = 关掉这条修正。
  { key: 'tierGoldSpeedScale', cn: '金档 · 游速手感倍率', unit: '', def: 0.84, min: 0, max: 3,
    type: 'double', desc: '金档(4)鱼游速在底数曲线之上再乘它。0.84 = 降 16%（用户定案）' },
  { key: 'tierGoldStaminaScale', cn: '金档 · 体力池倍率', unit: '', def: 1.0, min: 0, max: 3,
    type: 'double', desc: '金档(4)鱼体力池上限乘它。用户定案是 1.0 —— **金档体力不变**' },
  { key: 'tierRedSpeedScale', cn: '红档 · 游速手感倍率', unit: '', def: 1.05, min: 0, max: 3,
    type: 'double', desc: '红档(5)鱼游速在底数曲线之上再乘它。1.05 = 加 5%（用户定案）' },
  { key: 'tierRedStaminaScale', cn: '红档 · 体力池倍率', unit: '', def: 1.5, min: 0, max: 5,
    type: 'double', desc: '红档(5)鱼体力池上限乘它。1.5 = 加 50%（用户定案）⚠ 长体力鱼会更磨' },
  // 用户 2026-10-06："有些红鱼似乎体力基准连200都没到" → 定案"给所有红档鱼设体力池下限：基准至少 200"。
  // 能出红档的 11 种鱼里，有三种基准不到 200：蓝宝石雷龙鱼 130（台钓唯一的红鱼，且 100% 红档）、
  // 虎鱼 120、孔雀鱼 60。它们的游速不低（雷龙红档 3.15，和鲑同级），所以实际手感是"猛但几秒就遛翻"。
  { key: 'tierRedStaminaMin', cn: '红档 · 体力基准下限', unit: '', def: 200, min: 0, max: 5000,
    type: 'double', desc: '红档鱼的**基准体力**至少这么多（咬钩时先抬到下限，**再**乘上面的红档倍率，'
      + '所以默认下限 200 的红档鱼实际是 300）。0 = 关掉下限' },
  { key: 'fatiguePullMultiplier', cn: '疲劳期拉拽倍率', unit: '', def: 0.42, min: 0, max: 2,
    type: 'double', desc: '遛到疲劳后拉拽速度乘它。**每条鱼的疲劳期拉力默认 = 正常拉力 × 本值**'
      + '（由 tools/fish-source.js 推导，改它要重跑生成链）。⚠ 大于 1 = 疲劳期反而更难拉，别调错方向' },
  // ================================================================ 保底体力削减（渔轮，用户 2026-10-06）
  // 用户原话："拉鱼时每秒最少削减的鱼力，不受上限限制。由渔轮提供，根据压制阻尼转化而来。
  //   无论渔轮压制阻尼多少，该属性最小值为5……上限20。"
  // 公式：clamp(下限, 上限, 下限 + max(0, 压制阻尼 − 1) × 每点阻尼转化)
  //   → 默认 5 + (阻尼−1) × **10**（用户要求系数取整或 .5，10 正好是整数），夹在 [5,20]：
  //     手线轮 1.07→5.7 / 纺车轮 1.20→7.0 / 鼓式轮 1.45→9.5 / 水滴轮 1.40→9.0 / 大型纺车轮 2.50→20.0
  //   "×10"是**倒推**出来的：金轮阻尼 2.50 → 5 + 15 = **20.0**，正好贴住上限（用户要求"金轮贴近20"）。
  { key: 'reelFloorDrainBase', cn: '保底体力削减（下限）', unit: '点/秒', def: 5.0, min: 0, max: 100,
    type: 'double', desc: '**装了渔轮**的路亚，拉鱼时每秒**至少**削减这么多鱼力（与鱼多高檔无关）。'
      + '0 = 关掉这条保底（回到"扣力全看单轮上限"）' },
  { key: 'reelFloorDrainPerDamping', cn: '保底体力削减 · 每点阻尼转化', unit: '点/秒', def: 10.0,
    min: 0, max: 100, type: 'double',
    desc: '压制阻尼每高出 1.0 一点，保底削减 +这么多点/秒。默认 **10** 是**为金轮倒推**的：'
      + '大型纺车轮阻尼 2.50 → 5 + 1.5×10 = **20.0**，正好贴住上限' },
  { key: 'reelFloorDrainCap', cn: '保底体力削减（上限）', unit: '点/秒', def: 20.0, min: 0, max: 200,
    type: 'double', desc: '保底削减的硬上限（用户定案 20）。它**不受单轮扣力上限限制** —— '
      + '单轮额度用完的那几轮也照扣，所以它是"磨鱼到底"的那条底线' },
  // ================================================================ 保底挣扎（用户 2026-10-06）
  // 用户原话："现有的疲劳体系让红金等鱼过于容易拉上来，就是只要拉到一次力竭之后这条鱼
  //   即使再进入挣扎也不会超过一秒。我需要增加一个保底挣扎系数，**注意我说的挣扎指的是
  //   鱼不力竭的状态**。该系数决定一条鱼保底挣扎的秒数，1 代表一秒。根据鱼的档位这个系数
  //   增加，白 1 红 6 基准，再根据鱼的策略向上至多浮动 1，无向下浮动。"
  // → 语义：每**进入一轮挣扎**，这条鱼至少有 N 秒是"没力竭"的 ——
  //   轮次开头把疲劳清掉，且这 N 秒内疲劳不能重新点亮。N = 白档基准 + 档位×每档 + 策略加成。
  { key: 'floorStruggleBase', cn: '保底挣扎（白档秒数）', unit: '秒', def: 1.0, min: 0, max: 60,
    type: 'double', desc: '每轮挣扎开头，鱼**至少**有这么多秒是"没力竭"的（白档基准）。1 = 一秒' },
  { key: 'floorStrugglePerTier', cn: '保底挣扎 · 每档增加', unit: '秒', def: 1.0, min: 0, max: 60,
    type: 'double', desc: '每高一档多保底这么多秒 —— 白 1、红 6（用户定案）' },
  { key: 'floorStruggleBonusSteady', cn: '保底挣扎 · 均衡型加成', unit: '秒', def: 0.0, min: 0, max: 1,
    type: 'double', desc: '均衡型的策略浮动（0~1，**只上抬不下压**）' },
  { key: 'floorStruggleBonusSluggish', cn: '保底挣扎 · 黏液型加成', unit: '秒', def: 0.0, min: 0, max: 1,
    type: 'double', desc: '黏液型的策略浮动（0~1，只上抬不下压）' },
  { key: 'floorStruggleBonusBurst', cn: '保底挣扎 · 爆发型加成', unit: '秒', def: 1.0, min: 0, max: 1,
    type: 'double', desc: '爆发型的策略浮动 —— 默认给满 +1（爆发型是最难缠的那一类）' },
  { key: 'floorStruggleBonusErratic', cn: '保底挣扎 · 神经质加成', unit: '秒', def: 0.5, min: 0, max: 1,
    type: 'double', desc: '神经质型的策略浮动（默认 +0.5）' },
  // ================================================================ 收线速率抵抗（用户 2026-10-06）
  // 用户原话："增加一个收线速率抵抗系数，所有鱼的。对于收线速率高于 1.2 的鱼竿配置
  //   （或称之为双拉属性，一般由渔轮负责，比如水滴轮是 1.9 左右），各个档位的鱼有不同的抵抗，
  //   比如白是回拉速率降低 5%，绿 10%，蓝 15%，紫 20%，金 25%，红 32%。
  //   对于不同策略的鱼该系数可能上下浮动至多 5%。而该速率的最终影响可以由压制阻尼转化抵消部分，
  //   至多抵消不超过该系数的 75%。通过合理的设计使得金轮大致触碰到转化极限。取整数 0.05。"
  // 落地：只对**超过阈值 1.2 的那部分**打折（否则 1.21 会比 1.19 还慢 —— 不连续），
  //   抵抗 = 档位基准 × (1 + 策略浮动∈[−5%,+5%]) × (1 − 阻尼抵消∈[0,75%])。
  //   阻尼抵消系数取 **0.5**：(阻尼−1)×0.5，金轮阻尼 2.50 → 0.75 **正好贴住上限**（用户要求）。
  { key: 'reelResistThreshold', cn: '收线抵抗 · 起效阈值', unit: '', def: 1.2, min: 0, max: 10,
    type: 'double', desc: '双拉属性（渔轮的回拉速度）低于它就不受抵抗 —— 慢配置本来就不占便宜' },
  { key: 'reelResistTierWhite', cn: '收线抵抗 · 白档', unit: '比例', def: 0.05, min: 0, max: 1,
    type: 'double', desc: '白档鱼对高收线速率的抵抗（回拉速率降低这么多）' },
  { key: 'reelResistTierGreen', cn: '收线抵抗 · 绿档', unit: '比例', def: 0.125, min: 0, max: 1,
    type: 'double', desc: '绿档鱼的抵抗' },
  { key: 'reelResistTierBlue', cn: '收线抵抗 · 蓝档', unit: '比例', def: 0.185, min: 0, max: 1,
    type: 'double', desc: '蓝档鱼的抵抗' },
  { key: 'reelResistTierPurple', cn: '收线抵抗 · 紫档', unit: '比例', def: 0.275, min: 0, max: 1,
    type: 'double', desc: '紫档鱼的抵抗' },
  { key: 'reelResistTierGold', cn: '收线抵抗 · 金档', unit: '比例', def: 0.55, min: 0, max: 1,
    type: 'double', desc: '金档鱼的抵抗' },
  { key: 'reelResistTierRed', cn: '收线抵抗 · 红档', unit: '比例', def: 0.75, min: 0, max: 1,
    type: 'double', desc: '红档鱼的抵抗（用户定案 32%）' },
  { key: 'reelResistStratSteady', cn: '收线抵抗 · 均衡型浮动', unit: '', def: 0.0, min: -0.05, max: 0.05,
    type: 'double', desc: '策略浮动（**上下至多 5%**）：正值 = 更抗拉' },
  { key: 'reelResistStratSluggish', cn: '收线抵抗 · 黏液型浮动', unit: '', def: -0.05, min: -0.05, max: 0.05,
    type: 'double', desc: '黏液型游得慢，抵抗也给到下限 −5%' },
  { key: 'reelResistStratBurst', cn: '收线抵抗 · 爆发型浮动', unit: '', def: 0.05, min: -0.05, max: 0.05,
    type: 'double', desc: '爆发型最抗拉，给到上限 +5%' },
  { key: 'reelResistStratErratic', cn: '收线抵抗 · 神经质浮动', unit: '', def: 0.0, min: -0.05, max: 0.05,
    type: 'double', desc: '神经质型的策略浮动（默认 0）' },
  { key: 'reelResistDampingOffset', cn: '收线抵抗 · 每点阻尼抵消', unit: '比例', def: 0.5, min: 0, max: 2,
    type: 'double', desc: '压制阻尼每高出 1.0 一点，抵消这么多抵抗。默认 **0.5** 是**为金轮倒推**的：'
      + '大型纺车轮阻尼 2.50 → (2.50−1)×0.5 = **0.75**，正好贴住抵消上限' },
  { key: 'reelResistDampingCap', cn: '收线抵抗 · 抵消上限', unit: '比例', def: 0.75, min: 0, max: 1,
    type: 'double', desc: '压制阻尼最多抵消掉抵抗的这么多（用户定案 75%）—— 再好的轮子也留 1/4' },
  // 用户 2026-10-06："有时候鱼即使力竭了但依然收线回拉不过来，收不收都一个游速。"
  // 根因：距离结算 = 鱼拽 − 收线，而低档/磨损的轮子收线速度（0.22 × pullStruggle）
  // 可能仍小于力竭鱼的拽力；于是"力竭"只是个标签，线照样往外走。
  // 这条保底把话钉死：**力竭 = 一定收得回来**（最少这个速度）。
  { key: 'fatiguedReelMinGain', cn: '力竭期收线保底', unit: '格/tick', def: 0.05, min: 0, max: 1,
    type: 'double', desc: '鱼力竭时，收线的净回收速度**至少**这么多格/tick（0.05 ≈ 1 格/秒）。'
      + '0 = 关掉保底（装备差就可能力竭了也拉不动）' },
  // 用户 2026-10-06："加一个鱼力竭时张力涨速再乘 0.85，张力回复再乘 1.15。"
  // 力竭是"反拉的最佳窗口"，这段时间线也该更安全：涨得更慢、落得更快。
  // ⚠ "再乘"：控杆关系那边本来就有一套力竭 ×0.80（见 FishingSession 第 2 节），两者相乘生效
  //   —— 所以力竭期收线的实际涨速 = 0.80 × 0.85 = 0.68（同向控杆对比非力竭）。
  { key: 'fatiguedTensionGainScale', cn: '力竭期 · 变红速度倍率', unit: '', def: 0.7, min: 0, max: 3,
    type: 'double', desc: '鱼力竭时，收线的张力涨速**再乘**它（在控杆关系 ×0.8 之上）。'
      + '0.7 = **变红速度降 30%**（越小越难把线拉红）。⚠ 方向：<1 才是"更安全"，填 >1 等于力竭反而更危险' },
  { key: 'fatiguedTensionRecoverScale', cn: '力竭期 · 回白速度倍率', unit: '', def: 1.35, min: 0, max: 5,
    type: 'double', desc: '鱼力竭时，中立/放线的张力回落**再乘**它。'
      + '1.35 = **回白速度加 35%**（越大越快退回白区）。⚠ 方向：>1 才是"更安全"' },
  // 用户 2026-10-06："我在游戏中实际感觉力竭更难拉了" ⇒ 查日志发现那一局**鱼一次都没进疲劳**
  //   （31 条张力算式全是"力竭涨速×1.00"），根因是红档体力池 ×1.5 把"进疲劳的比例"落在
  //   1.5 倍的绝对值上：450 池 × burst 阈值 0.34 = 要扣到 153 才亮【疲劳·快拉】。
  //   这条旋钮让"力竭窗口什么时候开"可以整体调，不用动池子厚度。
  { key: 'fatigueThresholdScale', cn: '力竭阈值倍率（全局）', unit: '', def: 1.0, min: 0.1, max: 2,
    type: 'double', desc: '所有鱼"进疲劳的体力比例"**再乘**它。1.0 = 原样；'
      + '1.25 = 鱼还剩 42.5%（burst 0.34 时）就进力竭、更容易拉开差距。'
      + '⚠ 结果夹在 1.0：>1 不会变成"一咬钩就力竭"' },
  // 用户 2026-10-06："现在感觉张力下降好慢，所有时候。"
  //   实测（他那局日志）：鱼拉 1.650 vs 中立回落 0.51 → 净回落只有 0.05 点/tick ≈ 1 点/秒。
  //   根因是"鱼拉最多削到回落的 90%"是**按比例**削的 —— 调 idleLoss 没用。
  { key: 'idleFallFloor', cn: '中立 · 最小净回落', unit: '点/tick', def: 0.3, min: 0, max: 5,
    type: 'double', desc: '**中立**（不按任何键）时，张力每 tick 至少回落这么多 —— 鱼再猛也不能'
      + '把它拖得比这更慢。0.3 ≈ 6 点/秒（0.50 条子约 8 秒从红退回白）。'
      + '⚠ 放线不受它影响（那一档本来就快）；0 = 关掉，恢复"干站着被慢慢拖进黄区"的压力' },
  { key: 'struggleSecondsBase', cn: '挣扎秒数（白档）', unit: '秒', def: 2.0, min: 0, max: 60,
    type: 'double', desc: '白档鱼每轮挣扎持续多久（每高一档再加下面那个增量）' },
  { key: 'struggleSecondsPerTier', cn: '挣扎秒数每档增量', unit: '秒', def: 1.6, min: 0, max: 30,
    type: 'double', desc: '每升一档多挣扎这么久' },
  { key: 'restSecondsBase', cn: '休息秒数（白档）', unit: '秒', def: 5.0, min: 0, max: 60,
    type: 'double', desc: '白档鱼每轮休息多久' },
  { key: 'restSecondsPerTier', cn: '休息秒数每档递减', unit: '秒', def: 0.4, min: 0, max: 30,
    type: 'double', desc: '每升一档少休息这么久 —— 高级鱼"忙得多、歇得少"' },

  // ================================================================ 水域修饰（用户 2026-10-08）
  // 用户："不止做极地鱼，本轮计划 20 条可以都加。"（见 docs/33 §9.1）
  // 三个修饰**不是新的水体值**而是叠加：极地横跨河/湖/海，高寒湖 = 湖泊 + 冷，
  // 做成新水体值会破坏"深海继承海洋"那条特判，还会让 check-water-body 的 96 组合判据整片失效。
  // 判定代码在 WaterBody.modifiers()（群系标签 + 这两个阈值），守着它的是 check-water-body.js 第 ⑨ 节。
  { key: 'alpineLakeMinY', cn: '高寒湖 · 最低高度', unit: '格', def: 120, min: -64, max: 320,
    type: 'int', desc: '湖泊的**水面高度**达到它就算高寒湖（高山湖的本质是"高度 + 冷"）。'
      + '120 是雪线附近 —— 单看群系会漏掉"雪线以上的普通山地湖"。'
      + '⚠ 群系在 #anglertackle:alpine 里的湖**不看高度**，一律算高寒湖' },
  { key: 'coldWaterMaxTemp', cn: '冷水溪流 · 最高温度', unit: '温度', def: 0.3, min: -1, max: 2,
    type: 'double', desc: '河流所在群系的**基础温度**不超过它就算冷水溪流（哲罗鲑、江鳕住那儿）。'
      + '⚠ 默认 0.3 而不是 0.2：针叶林 taiga 是 0.25、平原 plains 是 0.8 —— '
      + '取 0.2 会把**针叶林溪流**排除掉，而那正是哲罗鲑现实里的栖息地。'
      + '⚠ 群系在 #anglertackle:cold_streams 里的河**不看温度**，一律算冷水溪流' },

  // ================================================================ 冰钓（用户 2026-10-08 拍板，docs/33 §8）
  // 三个形态：冰面 --凿冰镐--> 冰洞 --冰镐--> 碎冰 --闲置--> 浮冰(--闲置--> 冰)。
  // ⚠ **冰洞不会回冻**（用户定案），所以这里**没有**"冰洞回冻时间"这一项。
  // 数值口径（用户原话）：洞内咬钩时间不变、金红总爆率 +1%（**加算**）、鱼线磨损 ×2 且**只加鱼线**；
  // 覆冰群系**非洞**抛竿：咬钩时间 ×1.5、金红总爆率 −1%（加算）。
  { key: 'brokenIceToPackIceMinutes', cn: '冰钓 · 碎冰变浮冰', unit: '分钟', def: 5, min: 0, max: 120,
    type: 'int', desc: '碎冰闲置这么久之后变成浮冰（原版 packed_ice）。0 = 立刻变' },
  { key: 'packIceToIceMinutes', cn: '冰钓 · 浮冰变回冰', unit: '分钟', def: 10, min: 0, max: 240,
    type: 'int', desc: '浮冰闲置这么久之后变回原版冰（用户定案：**会**变回冰，于是还能再凿）' },
  { key: 'icePickDurabilityCost', cn: '冰钓 · 凿一次扣耐久', unit: '点', def: 1, min: 0, max: 100,
    type: 'int', desc: '凿冰面 / 砸冰洞各扣多少耐久。凿冰镐耐久 250，所以默认能凿 250 次' },
  { key: 'iceHoleLineWearMultiplier', cn: '冰钓 · 洞内鱼线磨损', unit: '倍率', def: 2.0, min: 0.1, max: 10,
    type: 'double', desc: '洞内钓鱼时**鱼线**的磨损倍率（2.0 = 寿命减半）。'
      + '⚠ **只乘鱼线、不乘竿**（用户定案）—— "有条件的玩家可以自己挖开一大片水面来避免这种损耗"' },
  { key: 'iceHoleHighValueBonus', cn: '冰钓 · 洞内金红爆率', unit: '百分数', def: 0.01, min: 0, max: 0.2,
    type: 'double', desc: '洞内抛竿对"金+红鱼种目标占比"的**加算**量（0.01 = +1 个百分点）' },
  { key: 'openIceBiteTimeMultiplier', cn: '冰钓 · 冰上咬钩时间', unit: '倍率', def: 1.5, min: 0.1, max: 5,
    type: 'double', desc: '**覆冰群系 · 没凿洞**时咬钩等待时间乘它（1.5 = 延长 50%）。'
      + '⚠ 只对"非高山且常年覆冰"的群系生效，其他群系一律 1.0' },
  { key: 'openIceHighValuePenalty', cn: '冰钓 · 冰上金红爆率', unit: '百分数', def: 0.01, min: 0, max: 0.2,
    type: 'double', desc: '覆冰群系**没凿洞**时对金红总占比的**加算**扣减（0.01 = −1 个百分点）' },

  // ================================================================ 抛投与窝料
  { key: 'fullChargeTicks', cn: '蓄力满所需 tick', unit: 'tick', def: 32, min: 1, max: 400,
    type: 'int', desc: '按住右键到蓄力满要多久（20 tick = 1 秒）' },
  { key: 'minChargeTicks', cn: '最短蓄力 tick', unit: 'tick', def: 3, min: 1, max: 100,
    type: 'int', desc: '低于这个 tick 数不算抛出（防误触）' },
  { key: 'chumMinAirTicks', cn: '窝料最小滞空', unit: 'tick', def: 10, min: 0, max: 200,
    type: 'int', desc: '窝料出手后至少飞这么久才允许落水（防止贴脸扔）' },

  // ================================================================ 承重行为（本次修的 bug）
  { key: 'oversizeFishAction', cn: '超重鱼的处理方式', unit: '0/1', def: 0, min: 0, max: 1,
    type: 'int',
    desc: '0 = 个体重量**限幅到竿的承重**（钓得上来，但上限就是承重）；' +
          '1 = 该个体直接**切线跑掉**（只掉耐久不给鱼）。⚠ 改这一项会明显改变难度' },

  // ================================================================ 追踪罗盘
  { key: 'compassSearchRadius', cn: '追踪罗盘搜索半径', unit: '格', def: 1200, min: 32, max: 4000,
    type: 'int',
    desc: '罗盘指针往外找多远的渔人小屋（格）。'
    + '⚠ 这个值**不是**随便给的：小屋的 placement spacing 是 34 区块（=544 格），'
    + 'random_spread 会让每个 34×34 网格里随机落一个候选点，玩家到最近候选点最坏约 spacing×√2/2 ≈ 385 格；'
    + '再叠加群系校验失败（不是每个候选点都落在河流/沼泽/沙滩上）要跳到更远的网格 —— '
    + '所以 400 会**经常找不到**，1200 才覆盖得住两格的最坏情况。'
    + '⚠ 搜索是**一圈一圈往外扩**的（原版 /locate 也是这套），找到就停，'
    + '所以平时开销取决于最近的屋子有多远、与这个上限无关；只有"附近真的没有"时才会走满。' },

  // ================================================================ 大鱼公告（2026-10-11 用户："公告可以搞"）
  { key: 'bigCatchAnnounceValue', cn: '大鱼公告价值门槛', unit: '鱼币', def: 5050, min: 0, max: 100000,
    type: 'int',
    desc: '钓到的鱼**价值 ≥ 这个数**时，向全服公告一条（0 = 每条都公告，会很吵）。'
    + '价值口径 = `CoinPricing.priceFor(档位, 尺寸系数)`，与**卖价同一个函数** —— '
    + '公告里写的钱数必须与玩家卖出去拿到的一样，两处各算一份就会打架 ✗。'
    + '⚠ 默认 **5050 是用户 2026-10-11 亲口给的数**，不是随手取的：'
    + '红档基准价 4800、尺寸浮动 ±8% ⇒ 红鱼的价格区间是 [4416, 5184]，'
    + '5050 正好落在**红档里体型偏大**的那一段（尺寸系数 ≥ 0.65，约红鱼的上游 17%）；'
    + '而金档最高只有 1380 × 1.08 = 1490 ⇒ **金档及以下永远够不到**。'
    + '⇒ 这条门的语义就是"**一条特别大的红鱼**"，改小它等于把公告变成"谁都能上电视"。' },
  { key: 'bigCatchAnnounceMinIntervalSec', cn: '大鱼公告最小间隔', unit: '秒', def: 10, min: 0, max: 600,
    type: 'int',
    desc: '两条大鱼公告之间至少隔这么久（0 = 不节流）。'
    + '⚠ 防的是"一群人同时上鱼"或"一个人连竿"把聊天栏刷爆 —— '
    + '公告是给别人看的调味品，刷屏就变成骚扰 ✗。' },
];

// ------------------------------------------------------------------ 校验
{
  const dup = ITEMS.map((i) => i.key).filter((k, i, a) => a.indexOf(k) !== i);
  if (dup.length) { console.error('❌ 重复的 key: ' + dup.join(', ')); process.exit(1); }
  for (const it of ITEMS) {
    if (!it.cn || !it.desc) { console.error('❌ ' + it.key + ' 缺中文文案'); process.exit(1); }
    if (it.def < it.min || it.def > it.max) {
      console.error('❌ ' + it.key + ' 默认值 ' + it.def + ' 越界 [' + it.min + ',' + it.max + ']');
      process.exit(1);
    }
    if (it.type === 'int' && !Number.isInteger(it.def)) {
      console.error('❌ ' + it.key + ' 是 int 但默认值不是整数：' + it.def); process.exit(1);
    }
  }
}

console.log('[gameplay] ' + ITEMS.length + ' 项（鱼竿材质 ' + ITEMS.filter((i) => i.key.startsWith('rod')).length + ' 项）');

const literal = (v, type) => (type === 'int' ? String(v) : Number.isInteger(v) ? v.toFixed(1) : String(v));
const safe = (s) => s.replace(/"/g, '\\"');

const decl = [], defs = [], apply = [], getters = [];
for (const it of ITEMS) {
  const isInt = it.type === 'int';
  decl.push('    public final ModConfigSpec.' + (isInt ? 'IntValue' : 'DoubleValue') + ' ' + it.key + ';');
  defs.push('        ' + it.key + ' = b',
            '                .comment("' + safe(it.cn + (it.unit ? '（' + it.unit + '）' : '') +
              ' —— ' + it.desc) + '")',
            '                .defineInRange("' + it.key + '", ' + literal(it.def, it.type) + ', ' +
              it.min + ', ' + it.max + ');');
  apply.push('        ' + (isInt ? 'putIntValue' : 'putDoubleValue') + '(flat, "' + GROUP + '.' + it.key +
             '", c.' + it.key + ', counter);');
  getters.push('    public static ' + (isInt ? 'int' : 'double') + ' ' + it.key +
               '() { return cfg().' + it.key + '.get(); }');
}

if (!WRITE) {
  console.log('\n--- 声明（前 3）---');
  decl.slice(0, 3).forEach((l) => console.log(l));
  console.log('--- 定义（前 2 项）---');
  defs.slice(0, 3).forEach((l) => console.log(l));
  console.log('--- getter（前 3）---');
  getters.slice(0, 3).forEach((l) => console.log(l));
  console.log('\n（加 --write 写入 Config.java / Tuning.java 并导出 gameplay-config.json）');
  process.exit(0);
}

// ------------------------------------------------------------------ 注入
const { stripExact } = require('./generated-blocks.js');

function injectOnce(text, anchor, payload, marker) {
  if (!text.includes(anchor)) throw new Error('找不到锚点：' + anchor.slice(0, 60));
  const before = text.split(marker).length - 1;
  const out = text.replace(anchor, () => anchor + payload);
  const after = out.split(marker).length - 1;
  if (after !== before + 1) {
    throw new Error('注入后「' + marker + '」出现 ' + after + ' 次（预期 ' + (before + 1) + '）');
  }
  return out;
}

// ---- Config.java
let cfg = readLF(cfgPath);
const GB = '    // ==== BEGIN 玩法数值声明 ====';
const GE = '    // ==== END 玩法数值声明 ====';
const DB = '        // ==== BEGIN 玩法数值定义 ====';
const DE = '        // ==== END 玩法数值定义 ====';

// 定义块前面那段"页眉"（分组注释 + push("gameplay")）。
//
// ⚠ 这里踩过一个**会累积**的坑：旧版只剥了 DB..DE+'b.pop();'，**没剥页眉**，
//   而重新注入时又把页眉写了一遍 —— 于是每跑一次 `--write`，Config.java 里就
//   多留一份 `// ---- 玩法数值` + `push("gameplay")`，而 pop() 只有一个。
//   后果是 push/pop 收支不平（13 推 12 弹），后面所有分组都被挤到下一层。
//   更坏的是它**不报错**：游戏照常启动、配置照常读，只是 TOML 的分组层级悄悄错了。
//   而 rebuild-config.js 的 --check 能发现它 —— 但那一步当时不在构建闸门里。
//
//   现在：先把**所有**页眉删干净（顺带清掉历史遗留的孤立页眉），再删定义块主体。
const HEADER = [
  '        // ---------------------------------------------------------- 玩法数值',
  '        b.comment("""',
  '                玩法数值 / Gameplay constants（决胜手感的那些系数）。',
  '                鱼竿五种材质、张力模型、档位曲面、抛投与窝料 —— 这些原本写死在代码里，',
  '                现在都能调。默认值与源码原值逐位相同，不改就与以前完全一样。',
  '                改完 /atl config reload 即生效（正在遛的鱼不受影响）。',
  '                """).push("' + GROUP + '");',
].join('\n');
{
  let swept = 0;
  while (cfg.includes(HEADER)) { cfg = cfg.replace(HEADER, ''); swept++; }
  if (swept > 1) console.log('  ℹ 清掉了 ' + (swept - 1) + ' 份历史遗留的「玩法数值」页眉');
}
cfg = stripExact(cfg, GB, GE).text;
cfg = stripExact(cfg, DB, DE + '\n        b.pop();').text;

// 声明插在鱼种声明块之后
const declAnchor = '    // ==== END 鱼种数值声明 ====';
cfg = injectOnce(cfg, declAnchor, '\n\n' + GB + '\n' + decl.join('\n') + '\n' + GE,
  'public final ModConfigSpec.DoubleValue rodWoodMaxWeightKg;');

// 定义插在鱼种定义块之后
const defAnchor = '        // ==== END 鱼种数值定义 ====\n        b.pop();';
cfg = injectOnce(cfg, defAnchor, '\n\n' + [
  HEADER,
  DB,
].join('\n') + '\n' + defs.join('\n') + '\n' + DE + '\n        b.pop();',
  'b.comment("""\n                玩法数值 / Gameplay constants');

// ---- 结构守卫
//
// 只断言"BEGIN 块有 1 份"是不够的 —— 上面那个页眉累积的坑里，
// 两份 BEGIN 标记始终是 1 份，计数器一路绿灯，坏掉的是**页眉和 push/pop**。
// 所以这里把这三样一起断言。
{
  const nBegin = (cfg.match(/BEGIN 玩法数值/g) || []).length;
  const nPush = (cfg.match(/\.push\("gameplay"\)/g) || []).length;
  const nPop = (cfg.match(/^\s*b\.pop\(\);\s*$/gm) || []).length;
  const nPushAll = (cfg.match(/\.push\("/g) || []).length;
  const problems = [];
  if (nBegin !== 2) problems.push('「BEGIN 玩法数值」出现 ' + nBegin + ' 次（声明 + 定义，应为 2）');
  if (nPush !== 1) problems.push('push("gameplay") 出现 ' + nPush + ' 次（应为 1）—— 页眉又累积了');
  if (nPushAll !== nPop) problems.push('push/pop 收支不平：' + nPushAll + ' 推 / ' + nPop + ' 弹'
    + '（差 ' + (nPushAll - nPop) + '）—— 后面所有分组会被挤到下一层，且**不会报错**');
  if (problems.length) throw new Error('Config.java 生成块结构异常：\n    - ' + problems.join('\n    - '));
}
writeKeep(cfgPath, cfg);
console.log('✅ Config.java：' + decl.length + ' 个字段');

// ---- Tuning.java
//
// ⚠️ 不要用"把某个锚点行连同自己的内容一起插回去"的写法。
// 早先这里用 `// ==== 热重载` 当 getter 锚点，并把那行复制到新块末尾 ——
// 而 gen-fish-config 也用同一行当锚点，两个生成器互相干扰：
// 一个插完另一个下次找不到干净锚点，于是重复插入，
// 实测 Tuning.java 涨到 5301 行、同一个块有 7 份。
// 现在统一走 tools/tuning-source.js：每个块用**自己那一对** BEGIN/END 标记，
// 块之间不共享锚点。
const TS = require('./tuning-source.js');
let tun = readLF(tunPath);
tun = TS.writeSection(tun, 'gameReload', apply);
tun = TS.writeSection(tun, 'gameGetter', getters);
const tunProblems = TS.checkSections(tun);
if (tunProblems.length) throw new Error('Tuning.java 生成块结构异常：' + tunProblems.join('；'));
writeKeep(tunPath, tun);
console.log('✅ Tuning.java：' + getters.length + ' 个 getter + ' + apply.length + ' 行热重载映射');
console.log('✅ Tuning.java：' + getters.length + ' 个 getter + ' + apply.length + ' 行热重载映射');

// ---- 导出清单（供 wire-gameplay.js 做代码替换）
fs.writeFileSync(outJson, JSON.stringify({
  group: GROUP,
  items: ITEMS.map((it) => ({ key: it.key, cn: it.cn, def: it.def, type: it.type })),
}, null, 2) + '\n', 'utf8');
console.log('✅ tools/gameplay-config.json：' + ITEMS.length + ' 项');
