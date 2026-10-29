/*
 * check-reel-sound.js —— 收线/放线声音的接线断言（2026-10-29 重写）。
 *
 * 历史：这个检查器原本守"只用原版音效、音量 ≤ 0.25"（用户 2026-10-05 的口径）。
 * 2026-10-29 用户改了口径：**用他录的真实素材**（5 段视频 → ogg），
 * 于是旧的三条（不许有音频文件 / 只用 SoundEvents / 音量 0.25）全部失效，
 * 这里按新设计重写；素材的"三处同名、真的是 Ogg、字幕齐全"由 check-sounds.js 守。
 *
 * 现在守：
 *   ① 五个自定义音效都用上了；
 *   ② 只在**路亚场次**里响（台钓静音）；
 *   ③ 收线/放线是受控循环（LoopSound + stopNow），不是反复 playSound；
 *   ④ 确实被客户端 tick 调用（否则写了也不响 —— 用户 2026-10-05 报过这个症状）。
 */
const fs = require('fs');
const path = require('path');
const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.join(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const { stripComments } = require('./java-source.js');
const readCode = (p) => stripComments(read(p));
const exists = (p) => fs.existsSync(path.join(ROOT, p));

let fail = 0;
const ok = (m) => console.log('  ✅ ' + m);
const bad = (m) => { console.log('  ❌ ' + m); fail++; };

console.log('=== 收线声音检查 ===\n');
const SND = 'src/main/java/com/whitecobalt/anglertackle/client/ReelSounds.java';
const EV = 'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java';

if (!exists(SND)) {
  bad('缺少 ' + SND + '（收线声音的实现）');
} else {
  const s = readCode(SND);

  const wants = ['LURE_CAST', 'REEL_FIGHT', 'REEL_TIRED', 'LINE_RELEASE', 'LURE_LAND'];
  const missing = wants.filter((w) => !new RegExp('ModSounds\\.' + w + '\\.get\\(\\)').test(s));
  if (missing.length) bad('这些自定义音效没被用到：' + missing.join(', '));
  else ok('五个自定义音效全部接线（抛竿/对抗收线/力竭收线/放线/上鱼）');

  if (/lureSession\(\)/.test(s) && /RodType\.LURE/.test(s)) {
    ok('有路亚门禁：服务端 lureSession 或手持路亚竿才响（台钓静音）');
  } else bad('没有路亚门禁 —— 台钓也会响（用户明确要求台钓不要这些声音）');

  if (/new LoopSound\(/.test(s) && /\.stopNow\(\)/.test(s)) {
    ok('收线/放线用受控循环（LoopSound + stopNow），不是反复 playSound');
  } else bad('收线声音不是受控循环 —— 会叠层、切换有延迟（用户报过）');

  const vols = [...s.matchAll(/([A-Z_]*VOLUME)\s*=\s*([\d.]+)F/g)].map((m) => ({ k: m[1], v: Number(m[2]) }));
  const tooLoud = vols.filter((x) => x.v > 1.0);
  if (tooLoud.length) bad('音量超过 1.0：' + tooLoud.map((x) => x.k + '=' + x.v).join(', '));
  else ok('音量都是具名常量且 ≤ 1.0：' + vols.map((x) => x.k + '=' + x.v).join(', '));
}

if (!exists(EV)) bad('缺少 ' + EV);
else if (!/ReelSounds\.tick\(/.test(read(EV))) bad('ClientEvents 没有调用 ReelSounds.tick(...) —— 声音永远不会响');
else ok('ClientEvents 的客户端 tick 里确实调了 ReelSounds.tick(state)');

console.log('');
if (fail === 0) {
  console.log('✅ 收线声音检查通过：五段素材都接上、只在路亚里响、受控循环、确实进了客户端 tick');
} else {
  console.log('❌ 收线声音检查失败：' + fail + ' 项');
}
process.exitCode = fail === 0 ? 0 : 1;
