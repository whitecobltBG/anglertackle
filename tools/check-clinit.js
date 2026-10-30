const ATL_ROOT = process.env.ATL_ROOT ? require('path').resolve(process.env.ATL_ROOT) : require('path').join(__dirname, '..');
// 字节码级检查：**任何类的 `<clinit>`（静态初始化）都不许读配置**。
//
// 为什么还要有这一层（源码已经有 check-no-config-at-registration.js 了）：
//   源码扫描是"按写法猜"，这一层是"看真正会跑的东西"。
//   2026-09-30 那次启动崩溃，源码扫描因为只扫 registry/ 而放行；
//   如果当时有这一层，它会直接指着 RodItem$RodSpec 的静态初始化说"这里在读配置"。
//   `javap -c` 看到的就是 JVM 执行的那份指令，不受书写风格、换行、语法糖影响。
//
// 为什么不用 child_process 抓 javap 输出：沙箱禁止命名管道，
// Node 的 stdio:'pipe' 直接 EPERM。所以这里让 pwsh 用**自己**的重定向把
// javap 输出写进文件（PowerShell 的管道不受该限制），再读文件。
const fs = require('fs'), path = require('path');
const { spawnSync } = require('child_process');

const ROOT = ATL_ROOT;
const CLASSES = path.join(ROOT, 'build/classes/java/main');
const DUMP = path.join(ROOT, 'build/clinit-dump.txt');

let bad = 0;
const ok = (m) => console.log('✅ ' + m);
const no = (m) => { console.log('❌ ' + m); bad++; };

if (!fs.existsSync(CLASSES)) {
  console.log('⚠ 还没有编译产物（build/classes/java/main 不存在），先跑一次 compileJava');
  process.exit(0);
}

// ---------------------------------------------------------------- 1. 找 javap
const jdk = fs.existsSync(path.join(ROOT, '.jdk'))
  ? fs.readdirSync(path.join(ROOT, '.jdk')).filter((d) => d.startsWith('jdk-')).sort().pop()
  : null;
const javap = jdk
  ? path.join(ROOT, '.jdk', jdk, 'bin', 'javap.exe')
  : 'javap';
if (!fs.existsSync(javap) && javap !== 'javap') {
  console.log('⚠ 找不到 javap（' + javap + '），跳过字节码检查');
  process.exit(0);
}

// ---------------------------------------------------------------- 2. 收集类名
const walk = (dir, out) => {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.class')) out.push(p);
  }
  return out;
};
const names = walk(CLASSES, []).map((p) =>
  path.relative(CLASSES, p).replace(/\.class$/, '').split(path.sep).join('.'));

// ---------------------------------------------------------------- 3. 反汇编（经 shell 重定向到文件）
//
// 找 shell 要按**绝对路径**：这台机器上 harness 说的 `pwsh` 其实并不在 PATH 里
//（实测是 Windows PowerShell 5.1，`Get-Command pwsh` 为空），
// 直接 spawn('pwsh') 会 ENOENT —— 而 ENOENT 的表现是 status:null，
// 和"javap 报错退出"长得一模一样，很容易误判成"检查没问题"。
const SHELLS = [
  'C:\\Program Files\\PowerShell\\7\\pwsh.exe',
  'C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe',
];
const shell = SHELLS.find((p) => fs.existsSync(p)) || 'pwsh';

const listFile = path.join(ROOT, 'build/_clinit-classes.txt');
fs.writeFileSync(listFile, names.join('\n'), 'utf8');
const psCmd = '& "' + javap + '" -p -c -classpath "' + CLASSES + '" ' +
  '(Get-Content -Encoding UTF8 "' + listFile + '") | Out-File -Encoding UTF8 "' + DUMP + '"';
const r = spawnSync(shell, ['-NoProfile', '-Command', psCmd], { stdio: 'inherit' });
if (r.error) {
  no('起不了 shell（' + shell + '）：' + r.error.message);
  console.log('\n❌ 有 ' + bad + ' 处问题');
  process.exit(1);
}
if (r.status !== 0 || !fs.existsSync(DUMP)) {
  no('javap 反汇编失败（exit ' + r.status + '）');
  console.log('\n❌ 有 ' + bad + ' 处问题');
  process.exit(1);
}

// ---------------------------------------------------------------- 4. 切出每个 `static {};` 的指令体
// javap 的方法体指令缩进 ≥4 空格，新的方法签名缩进正好 2 空格 —— 靠这个切。
// PowerShell 5.1 的 `Out-File -Encoding UTF8` 会带 BOM，读进来先去掉。
const dump = fs.readFileSync(DUMP, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n/g, '\n');
const lines = dump.split('\n');
let curClass = '(未知)';
const clinits = [];      // { cls, body }
for (let i = 0; i < lines.length; i++) {
  const m = /^(?:public |final |abstract |super |interface |class |enum |record )*.*?\b([\w.$]+)\s*\{$/.exec(lines[i]);
  if (m && /^[A-Za-z_]/.test(lines[i])) curClass = m[1];
  if (/^  static \{\};$/.test(lines[i])) {
    const body = [];
    for (let j = i + 1; j < lines.length; j++) {
      if (/^  \S/.test(lines[j])) break;      // 下一个方法/字段签名
      body.push(lines[j]);
    }
    clinits.push({ cls: curClass, body: body.join('\n') });
  }
}

// ---------------------------------------------------------------- 5. 判定
//  · Tuning.xxx()  —— 静态初始化里取配置值，**必然崩**
//  · ConfigValue.get —— 同上（Tuning 内部就是干这个的，直接调也一样崩）
const TUNING = /\/\/ Method com\/whitecobalt\/anglertackle\/fishing\/Tuning\.[\w$]+/;
const CONFGET = /ModConfigSpec\$ConfigValue\.get|ModConfigSpec\$ConfigValue\.getRaw/;

const hits = [];
for (const c of clinits) {
  const t = TUNING.test(c.body), g = CONFGET.test(c.body);
  if (t || g) {
    const which = [];
    if (t) which.push('调用 Tuning.*');
    if (g) which.push('调用 ConfigValue.get');
    hits.push({ cls: c.cls, which: which.join(' + '),
      sample: (c.body.match(/\/\/ Method [^\n]*(Tuning|ConfigValue)[^\n]*/) || [''])[0].trim() });
  }
}

if (!clinits.length) no('一个 `static {};` 都没解析出来 —— 解析规则可能和 javap 输出脱节了');
else if (hits.length) {
  no('有 ' + hits.length + ' 个类的静态初始化在读配置 —— 这会让**游戏启动即崩**：');
  for (const h of hits) {
    console.log('     ' + h.cls + '：' + h.which);
    if (h.sample) console.log('       ' + h.sample);
  }
  console.log('     改法：静态字段/常量里别存配置值，改成方法里现查（见 RodItem.RodSpec）。');
  bad += hits.length;
} else {
  ok('反汇编了 ' + names.length + ' 个类，其中 ' + clinits.length +
     ' 个有静态初始化 —— 没有一个读配置');
}

// ---------------------------------------------------------------- 6. 正面证据：RodSpec 的取值确实现查
{
  const spec = clinits.find((c) => /RodSpec$/.test(c.cls));
  if (!spec) {
    // RodSpec 只存材质、没有静态初始化，也是正常的 —— 那说明它连 <clinit> 都没有
    ok('RodItem$RodSpec 没有静态初始化块（五个常量只是 new RodSpec(材质)）');
  } else if (!TUNING.test(spec.body)) {
    ok('RodItem$RodSpec 的静态初始化里没有取配置');
  }
  // ⚠️ 必须带上结尾的 ` {`：`RodItem$RodSpec$Material` 也含 `RodItem$RodSpec` 这个子串，
  //    不加边界的话 find() 会先撞上枚举那个段（它一次 Tuning 都不调），于是永远数出 0。
  const rodBody = dump.split(/^Compiled from/m)
    .find((s) => s.includes('class com.whitecobalt.anglertackle.item.RodItem$RodSpec {')) || '';
  const n = (rodBody.match(/\/\/ Method com\/whitecobalt\/anglertackle\/fishing\/Tuning\.rod\w+:\(\)D/g) || []).length;
  const ti = (rodBody.match(/\/\/ Method com\/whitecobalt\/anglertackle\/fishing\/Tuning\.rod\w+:\(\)I/g) || []).length;
  if (n + ti >= 30) {
    ok('RodSpec 的取值方法里实打实有 ' + (n + ti) + ' 处 invokestatic 到 Tuning.rod*（6 项 × 5 材质）');
  } else {
    no('RodSpec 里只有 ' + (n + ti) + ' 处 Tuning.rod* 调用（应为 30）');
  }
}

try { fs.unlinkSync(listFile); } catch (e) { /* 清理失败无所谓 */ }
console.log(bad === 0 ? '\n✅ 字节码级：没有任何静态初始化读配置'
                      : '\n❌ 有 ' + bad + ' 处问题 —— 这会让游戏启动崩溃');
process.exit(bad ? 1 : 0);
