const ATL_ROOT = process.env.ATL_ROOT ? require('path').resolve(process.env.ATL_ROOT) : require('path').join(__dirname, '..');
// 守住"注册期不许读配置"这条 —— 违反的代价是**游戏启动即崩**。
//
// ── 真实事故（两次，同一类，别再有第三次）─────────────────────
//
// 【第一次】用脚本把 ModItems.java 里配件数值的字面量换成 `Tuning.xxx()`，
// 想让"每个配件的属性"跟着配置走。结果启动直接崩：
//
//   java.lang.IllegalStateException: Cannot get config value before config is loaded.
//       at Tuning.hempTensionCeiling(Tuning.java:177)
//       at ModItems.lambda$static$1(ModItems.java:117)
//       at DeferredRegister$Items.lambda$register$0
//
// 【第二次】把鱼竿五种材质的数值改成读配置，写成
// `public static final RodSpec WOOD = new RodSpec(2000, Tuning.rodWoodMaxWeightKg(), …)`。
// 这回崩点**不在 registry/ 里**，而在别的类的 `<clinit>`：
//
//   java.lang.ExceptionInInitializerError: null
//     Caused by: IllegalStateException: Cannot get config value before config is loaded.
//       at Tuning.rodWoodMaxWeightKg(Tuning.java:1124)
//       at RodItem$RodSpec.<clinit>(RodItem.java:330)
//       at ModItems.<clinit>(ModItems.java:40)
//       at AnglersTackle.<init>(AnglersTackle.java:66)
//
// 第一次的检查脚本只扫 registry/*.java，于是**完全没拦住第二次**。
// 教训：危险的不是"哪个文件"，而是"**静态初始化里读了配置**" ——
// 因为 ModItems.<clinit> 会顺着 `RodSpec.WOOD` 这类静态引用，把任意类的
// <clinit> 拖进注册期执行。所以下面加了一条全局规则。
//
// 原因是 NeoForge 的时序（不是实现细节，是硬约束）：
//   ① mod 构造：AnglersTackle 里 container.registerConfig(...) —— 只是**登记**
//   ② 所有 mod 构造完成之后，NeoForge 才真正加载配置文件
//   ③ RegisterEvent 派发 → DeferredRegister 的 supplier 执行 → 物品被创建
// ② 在 ③ 之前，但 ③ 里读的是"② 之后才有"的东西？不 —— ③ 发生在 ② **之前**
//（registerConfig 只是把 spec 挂到容器上，真正 loadConfigs 更晚）。
// 所以物品 supplier 里任何 `Config.INSTANCE.xxx.get()` 都必然失败；
// 而 mod 构造期被牵动的任何 <clinit> 同理。
//
// 更根本的一层：逐件配件数值**本来就不该做成配置**。配件属性是靠
// `partProps(..., PartStats.xxx(...))` 在注册期烘进物品的 PART_STATS 数据组件的，
// 运行期 RodStats.statsOf() 读的是那个组件。就算能读配置，也只是"把当时的配置值
// 烤进物品"，改配置对已注册物品毫无作用。这是引擎时序决定的，不是实现问题。
//
// 用法：node tools/check-no-config-at-registration.js
const fs = require('fs'), path = require('path');
const ROOT = ATL_ROOT;
const JAVA = path.join(ROOT, 'src/main/java/com/whitecobalt/anglertackle');

let bad = 0;
const ok = (m) => console.log('✅ ' + m);
const no = (m) => { console.log('❌ ' + m); bad++; };

/** 会由 DeferredRegister 在 RegisterEvent 阶段执行的类。 */
const REGISTRY_FILES = [
  'registry/ModItems.java',
  'registry/ModEntities.java',
  'registry/ModDataComponents.java',
  'registry/ModBlocks.java',
  'registry/ModBlockEntities.java',
  'registry/ModMenus.java',
  'registry/ModSounds.java',
];

// 允许出现的例外：注册**与数值无关**的东西时读配置是安全的吗？不安全 ——
// 一律不允许在 registration 文件里出现 Config / Tuning 的取值调用。
const FORBIDDEN = [
  { re: /Tuning\.\w+\(\)/g, what: 'Tuning.xxx() 取值调用' },
  { re: /Config\.INSTANCE\.\w+\.get\(\)/g, what: 'Config.INSTANCE.xxx.get() 取值调用' },
];

let checked = 0;
for (const rel of REGISTRY_FILES) {
  const p = path.join(JAVA, rel);
  if (!fs.existsSync(p)) continue;
  checked++;
  const src = fs.readFileSync(p, 'utf8');
  const lines = src.split('\n');
  for (const { re, what } of FORBIDDEN) {
    lines.forEach((l, i) => {
      const t = l.trim();
      if (t.startsWith('//') || t.startsWith('*')) return;
      const m = l.match(re);
      if (m && m.length) {
        no(rel + ':' + (i + 1) + ' 在注册文件里出现 ' + what + '：' + t.slice(0, 70));
        bad++;
      }
    });
  }
}
if (!bad) ok('检查了 ' + checked + ' 个 registry 文件，注册期没有任何配置取值');

// ---------------------------------------------------------------- 全局：静态初始化里不许读配置
//
// 这条是第二次事故之后补的，也是真正管用的一条。
// 规则：**任何类的静态字段初始化式、静态初始化块里，都不许调用 Tuning.xxx() / Config 取值。**
// 理由：注册期会顺着静态引用把别的类的 <clinit> 拖进来执行，文件路径完全预料不到。
// 运行期读配置是**完全正常**的 —— 但那只应该发生在方法体里，不是 <clinit> 里。
//
// 为什么可以一刀切：给静态常量赋一个"随配置变的值"本身就是错的 ——
// 常量语义（一个类加载期就固定、之后永不变的东西）和配置语义（运行期随时可改）
// 是矛盾的。真想让它在 reload 后变，就得做成方法调用（见 RodItem.RodSpec 的写法）。
{
  const EXEMPT = new Set([
    // 这两处就是"定义配置项"和"读配置项"本身，不在此列
    'Config.java',
    'fishing/Tuning.java',
    // FishTuning 的静态表由 ModConfigEvent 在配置加载后填充，是**事件驱动**的，
    // 不是 <clinit>；这里只挡 <clinit>，所以它本来也不会被误报（留着说明用途）。
  ]);

  /** 收集 .java 文件（递归）。 */
  const walk = (dir, out) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) walk(p, out);
      else if (e.name.endsWith('.java')) out.push(p);
    }
    return out;
  };

  const files = fs.existsSync(JAVA) ? walk(JAVA, []) : [];
  const { scanStaticInitConfigReads } = require('./static-init-scan.js');
  const hits = [];

  for (const p of files) {
    const rel = path.relative(JAVA, p).split(path.sep).join('/');
    if (EXEMPT.has(rel)) continue;
    for (const h of scanStaticInitConfigReads(fs.readFileSync(p, 'utf8'))) {
      hits.push({ rel, line: h.line, text: h.text });
    }
  }

  if (hits.length) {
    no('有 ' + hits.length + ' 处在**静态初始化**里读配置 —— 这会让游戏启动即崩（第二次事故就是这么来的）：');
    for (const h of hits) {
      console.log('     ' + h.rel + ':' + h.line + '  ' + h.text.slice(0, 76));
    }
    console.log('     改法：别把配置值存进 static 字段/常量，改成方法里现查。');
    console.log('     参考 RodItem.RodSpec：只存"是哪种材质"，取值时 switch 到 Tuning.xxx()。');
    bad += hits.length;
  } else {
    ok('扫描了 ' + files.length + ' 个 java 文件：没有任何静态初始化读配置');
  }
}

// ---------------------------------------------------------------- 反向：ModItems 必须保持字面量
{
  const p = path.join(JAVA, 'registry/ModItems.java');
  if (fs.existsSync(p)) {
    const src = fs.readFileSync(p, 'utf8');
    if (src.includes('import com.whitecobalt.anglertackle.fishing.Tuning;')) {
      no('ModItems.java 又 import 了 Tuning —— 它会在注册期读配置，游戏将启动崩溃');
    } else {
      ok('ModItems.java 没有 import Tuning（配件数值保持注册期字面量）');
    }
    // 抽查一个配件，确认 partProps/PartStats 用的是数字
    const sample = /PartStats\.line\(([^)]*)\)/.exec(src);
    if (sample) {
      const args = sample[1].split(',').map((s) => s.trim());
      const allNum = args.every((a) => /^[-+*/(). 0-9]+$/.test(a));
      if (allNum) ok('抽查 PartStats.line(' + sample[1].trim() + ')：全是数字字面量');
      else no('PartStats.line 的参数里有非数字：' + sample[1].trim());
    }
  }
}

// ---------------------------------------------------------------- 反向：竿的数值必须是"现查"
//
// 只挡住 <clinit> 还不够 —— 有人可能改成在 RodItem 构造器里读配置，
// 那同样是注册期（RodItem 是 RegisterEvent 里 new 出来的）。
// 所以这里正面断言：RodSpec 的取值方法里必须真的调用 Tuning，
// 而构造器/常量声明里不能出现 Tuning。
{
  const p = path.join(JAVA, 'item/RodItem.java');
  if (!fs.existsSync(p)) no('找不到 item/RodItem.java');
  else {
    const src = fs.readFileSync(p, 'utf8');
    const specStart = src.indexOf('public static final class RodSpec');
    if (specStart < 0) {
      no('RodItem 里没有 RodSpec 类 —— 结构变了，这条断言需要跟着改');
    } else {
      const body = src.slice(specStart);
      const constDecl = body.slice(0, body.indexOf('private final Material material'));
      if (/Tuning\./.test(constDecl)) {
        no('RodSpec 的常量声明里出现 Tuning —— 那是 <clinit>，启动会崩');
      } else {
        ok('RodSpec 的五个常量只存材质，声明里没有 Tuning 调用');
      }
      const accessors = body.slice(body.indexOf('private final Material material'));
      const n = (accessors.match(/Tuning\.rod\w+\(\)/g) || []).length;
      if (n >= 30) ok('RodSpec 的取值方法里有 ' + n + ' 处现查 Tuning（6 项 × 5 材质）');
      else no('RodSpec 只有 ' + n + ' 处 Tuning 调用（应为 30 = 6 项 × 5 材质）—— 可能漏了某项');
    }
  }
}

// ---------------------------------------------------------------- 说明白哪些配置才有效
console.log('');
console.log('说明：以下两组配置**运行期生效**（PartComposer / FishingSession 在运行期读取）：');
console.log('  · [parts]      配件属性合成的权重与上限');
console.log('  · [partsStats] 逐件配件数值 —— ⚠ 这一组只是**记录当前数值**，改了不会有任何效果');
console.log('     因为配件属性是注册期烘进 PART_STATS 数据组件的，运行期读组件而不是配置。');
console.log('     （鱼种数值 [fishStats] 是运行期读取的，改它有效。）');

console.log(bad ? '\n❌ 有 ' + bad + ' 处违规 —— 这会让游戏启动崩溃' : '\n✅ 注册期不读配置，安全');
process.exit(bad ? 1 : 0);
