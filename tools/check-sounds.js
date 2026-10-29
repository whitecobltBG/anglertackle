/*
 * check-sounds.js —— 守住"三处同名"与"真的能被播放"。
 *
 * 用户 2026-10-29：
 *   "做路亚抛竿、收线对抗、上鱼摇轮的声音"（素材由用户后来提供的 5 段视频转出），
 *   "无论是鱼漂落入水中还是鱼咬钩都用原版音效，只是稍稍把其音频调高一丢丢"。
 *
 * 声音这种东西**坏了不报错**：注册 id 写成 A、sounds.json 键写成 B、文件名写成 C，
 * 游戏照常启动、日志一行不吭，只是**没声音** —— 而"没声音"玩家只会说"你没做"。
 * 所以这里逐项钉死。
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const exists = (p) => fs.existsSync(path.join(ROOT, p));

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };
const strip = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

console.log('=== 路亚音效检查 ===');

const RES = 'src/main/resources/assets/anglertackle';
const EXPECT = ['lure_cast', 'reel_fight', 'reel_tired', 'line_release', 'lure_land'];

// ---- 1. 注册表 ----
const regPath = 'src/main/java/com/whitecobalt/anglertackle/registry/ModSounds.java';
if (!exists(regPath)) {
  fail('没有 ModSounds.java —— 声音根本没注册');
} else {
  const reg = strip(read(regPath));
  const ids = [...reg.matchAll(/register\("([a-z0-9_]+)"\)/g)].map((m) => m[1]);
  const same = ids.length === EXPECT.length && EXPECT.every((e) => ids.includes(e));
  if (same) ok('ModSounds 注册了且只注册了 5 条：' + EXPECT.join(' / '));
  else fail('ModSounds 注册的 id 不对：[' + ids.join(', ') + ']');
  if (/createVariableRangeEvent/.test(reg)) ok('用 createVariableRangeEvent（射程由 sounds.json 的 attenuation_distance 决定）');
  else fail('没有用 createVariableRangeEvent —— 远处听不到');
}

// ---- 2. sounds.json ----
const sjPath = RES + '/sounds.json';
let sj = null;
if (!exists(sjPath)) fail('缺少 sounds.json');
else {
  try { sj = JSON.parse(read(sjPath)); } catch (e) { fail('sounds.json 非法 JSON：' + e.message); }
  if (sj) {
    const keys = Object.keys(sj);
    const same = keys.length === EXPECT.length && EXPECT.every((e) => keys.includes(e));
    if (same) ok('sounds.json 的键与注册 id 一一对应');
    else fail('sounds.json 的键不对：[' + keys.join(', ') + ']');

    let nsBad = 0, fileBad = 0;
    for (const k of keys) {
      for (const s of (sj[k].sounds || [])) {
        if (s.name !== 'anglertackle:' + k) nsBad++;
        if (!exists(RES + '/sounds/' + k + '.ogg')) fileBad++;
      }
    }
    if (nsBad === 0) ok('每条都指向 anglertackle:<同名>');
    else fail(nsBad + ' 条跨命名空间');
    if (fileBad === 0) ok('每条都找得到 sounds/<id>.ogg');
    else fail(fileBad + ' 条缺文件');
  }
}

// ---- 3. ogg 本体 ----
for (const id of EXPECT) {
  const p = RES + '/sounds/' + id + '.ogg';
  if (!exists(p)) { fail('缺少 ' + p); continue; }
  const buf = fs.readFileSync(path.join(ROOT, p));
  if (buf.slice(0, 4).toString('latin1') !== 'OggS') { fail(p + ' 不是 Ogg'); continue; }
  if (buf.length < 2000) { fail(p + ' 太小（' + buf.length + ' 字节）'); continue; }
  ok(id + '.ogg  ' + buf.length + ' 字节');
}

// ---- 4. 字幕 ----
for (const lang of ['zh_cn', 'en_us']) {
  const p = RES + '/lang/' + lang + '.json';
  if (!exists(p)) { fail('缺少 ' + p); continue; }
  const json = JSON.parse(read(p));
  const missing = EXPECT.filter((id) => !json['subtitles.anglertackle.' + id]);
  if (missing.length === 0) ok(lang + ' 五条字幕键齐全');
  else fail(lang + ' 缺：' + missing.join(', '));
}

// ---- 5. 接线 ----
const reelPath = 'src/main/java/com/whitecobalt/anglertackle/client/ReelSounds.java';
if (!exists(reelPath)) fail('没有 ReelSounds.java');
else {
  const reel = strip(read(reelPath));
  const uses = (name) => new RegExp('ModSounds\\.' + name + '\\.get\\(\\)').test(reel);

  // 收线/放线必须是**受控循环**：一个实例、状态一变立刻停
  if (/reelLoop = new LoopSound/.test(reel) && /releaseLoop = new LoopSound/.test(reel)) {
    ok('收线/放线都用受控循环音（LoopSound），不是反复 playSound');
  } else fail('收线还在用一次性 playSound —— 会叠加、切放线有延迟（用户报的第 1、2 条）');

  if (/reelLoop\.stopNow\(\)/.test(reel) && /releaseLoop\.stopNow\(\)/.test(reel)) {
    ok('切状态时立刻 stopNow()（跟手，不残留上一段）');
  } else fail('没有立刻停旧循环 —— 转放线时旧收线音还会拖着');

  if (/if \(reelLoop == null \|\| reelEvent != want\)/.test(reel)) {
    ok('只有"没在放"或"换了素材（力竭/对抗）"才新建实例（不会每次收线都叠一层）');
  } else fail('没有做"同素材复用实例"，快速收放会叠多个音频');

  if (uses('REEL_FIGHT') && uses('REEL_TIRED')) ok('收线：对抗 reel_fight / 力竭 reel_tired');
  else fail('收线没有按力竭切素材');

  if (/REEL_FADE_IN_SECONDS = 0\.3F/.test(reel) && /reelLoop\.fadeIn\(REEL_FADE_IN_SECONDS\)/.test(reel)) {
    ok('收线起声有 0.3 秒淡入（用户 2026-10-29 两次调整后定为 0.3）');
  } else fail('收线没有淡入 —— 用户明确要「点左键开拉时 0.5 秒淡入」');
  if (!/releaseLoop\.fadeIn/.test(reel)) ok('放线保持不淡入（其余不变）');
  else fail('放线也被加了淡入 —— 用户只要求对抗收线那一个');
  if (/new LoopSound\(ModSounds\.LINE_RELEASE/.test(reel)) ok('放线是受控循环（松手立刻起）');
  else fail('放线不是受控循环 —— 用户明确报过"放线不跟手"');

  // 一次性音：抛竿 / 上鱼走 SoundManager + forUI
  if (/SimpleSoundInstance\.forUI/.test(reel)) ok('一次性音走 SimpleSoundInstance.forUI（与坐标无关，必定听得见）');
  else fail('一次性音还在用 player.playSound（会被位置/静音盖住）');

  if (/prev != ClientFishingState\.STATE_CASTING && next == ClientFishingState\.STATE_CASTING/.test(reel)) {
    ok('抛竿：进入 CASTING 播 lure_cast');
  } else fail('抛竿音判据不对');

  if (/next == ClientFishingState\.STATE_LANDING/.test(reel) && uses('LURE_LAND')) ok('上鱼：进入 LANDING 播 lure_land');
  else fail('上鱼音没接（用户报"没有拉上鱼的音效"）');

  // 2026-10-29（用户："还是有停顿感"）：收线循环不许因 reeling 抖动被停。
  {
    const duck = /reelLoop\.follow\(state\.reeling\(\) \? baseVolume : baseVolume \* 0\.35F/.test(reel);
    const noStopInReel = !/if \(state\.reeling\(\)\) \{\s*stopReel\(\);/.test(reel);
    if (duck && noStopInReel) ok('对抗期间收线循环不停（没在收线只压低音量让位），输入抖动不会打断波形');
    else fail('收线循环仍会因 reeling 抖动被 stop —— 每抖一次就断一下（用户报的停顿感）');

    if (/RELEASE_DEBOUNCE_TICKS/.test(reel)) ok('放线判定有消抖（单帧丢输入不切放线）');
    else fail('放线没有消抖 —— reeling 每 2 tick 同步一次，单帧丢失就会切来切去');

    if (/tiredLatched/.test(reel)) ok('力竭后锁定素材（本轮对抗不再切回对抗音）');
    else fail('力竭没有锁定 —— 力竭期结束后会切回对抗音，听感又断一次');
  }

  if (/public static void silence/.test(reel)) ok('离开对抗/会话结束时把循环音掐掉（不会挂着响）');
  else fail('没有 silence() —— 退出对抗后循环音会一直响');

  // 2026-10-29 用户："浮漂落水声没有了" ⇒ 服务端那一份位置在浮漂上、音量又低，听不见；
  // 客户端必须在进入 WAITING 时补一声（UI 通道），并且按用户要求压到 0.5 以下。
  const splashBack = /STATE_WAITING[\s\S]{0,200}?FISHING_BOBBER_SPLASH, 0\.18[0-9]F, 1\.1[0-9]F/.test(reel);
  if (splashBack) ok('浮漂落水：客户端补一声原版 splash（音量 <0.5、音高 1.1x）');
  else fail('浮漂落水没有客户端那一声 —— 服务端那份在浮漂坐标上，玩家常常听不到（用户已报「没有了」）');

  if (/lureSession\(\)/.test(reel)) ok('全部只在路亚场次里响（台钓静音）');
  else fail('没有用 lureSession() 把关');

  if (!/LEVER_CLICK|FISHING_BOBBER_RETRIEVE/.test(reel)) ok('不再用原版机械音冒充摇轮');
  else fail('还在用原版机械音冒充摇轮');
}

// 循环音类本身必须真的循环 + 跟随玩家（否则远处/转身就听不见）
if (exists('src/main/java/com/whitecobalt/anglertackle/client/sound/LoopSound.java')) {
  const loop = strip(read('src/main/java/com/whitecobalt/anglertackle/client/sound/LoopSound.java'));
  if (/this\.looping = true/.test(loop) && /this\.relative = true/.test(loop)) {
    ok('LoopSound 是 looping + relative（循环、跟着玩家，不按坐标衰减）');
  } else fail('LoopSound 没有设 looping/relative —— 循环不起来或远处听不见');
} else fail('缺少 LoopSound.java');

const csPath = 'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java';
if (exists(csPath) && /ReelSounds\.onStateChanged\(prevState, this\.state\)/.test(read(csPath))) {
  ok('状态跃迁处确实调了 onStateChanged');
} else fail('ClientFishingState.accept 里没调 onStateChanged —— 写了也不会响');

// ---------------------------------------------------------------- 服务端旧音（2026-10-29）
//
// 用户："其他都是以前的老音效"。真凶之一就是服务端还在播原版 FISHING_BOBBER_THROW，
// 它和客户端的新素材叠在一起，听起来就像"没改"。
{
  const sess = strip(read('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java'));
  const guarded = /if \(rodType\(\) == com\.whitecobalt\.anglertackle\.item\.RodItem\.RodType\.FLOAT\) \{[\s\S]{0,220}?FISHING_BOBBER_THROW/.test(sess);
  const unguarded = /^(?![\s\S]*RodType\.FLOAT)[\s\S]*FISHING_BOBBER_THROW/m.test(sess)
    && (sess.match(/FISHING_BOBBER_THROW/g) || []).length !== (sess.match(/RodType\.FLOAT/g) || []).length;
  if (guarded && !unguarded) ok('服务端不再给路亚播原版抛投声（只剩台钓那一份）');
  else fail('服务端仍在无条件播 FISHING_BOBBER_THROW —— 会把客户端的新素材盖掉（用户听感："还是老音效"）');

  const bob = strip(read('src/main/java/com/whitecobalt/anglertackle/entity/BobberEntity.java'));
  if (/FISHING_BOBBER_SPLASH[\s\S]{0,200}?1\.15f/.test(bob)) ok('浮漂落水用原版 splash，但音高抬到 1.15+（用户要求）');
  else fail('落水 splash 没有抬高音高');
}


// 用户 2026-10-29："使用原版音效的部分声音再低个 20%"。
{
  const sess = strip(read('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java'));
  const bob = strip(read('src/main/java/com/whitecobalt/anglertackle/entity/BobberEntity.java'));
  // 2026-10-29 第三次下调（现值 ×0.6）：咬钩 0.20 / 落水 0.118 / 原版收线 0.076
  const bite = /FISHING_BOBBER_SPLASH, 0\.20f/.test(sess);
  const entry = /FISHING_BOBBER_SPLASH[\s\S]{0,160}?0\.11[0-9]f/.test(bob);
  const retrieve = /FISHING_BOBBER_RETRIEVE[\s\S]{0,160}?0\.07[0-9]f/.test(bob);
  if (bite && entry && retrieve) ok('原版音效音量已三次下调（咬钩 0.20 / 落水 0.118 / 收线 0.076）');
  else fail('原版音效音量不是最新一档（bite=' + bite + ' entry=' + entry + ' retrieve=' + retrieve + '）');
}


// 用户 2026-10-29："提前收线的声音也使用上鱼声"。
{
  const sess = strip(read('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java'));
  if (/RodType\.LURE\)[\s\S]{0,200}?ModSounds\.LURE_LAND\.get\(\)/.test(sess)) {
    ok('提前收线：路亚播上鱼声（lure_land），台钓仍用原版收线声');
  } else fail('提前收线没有改成上鱼声');
}


// 缩放"松手比按下慢"（2026-10-28 用户："钓上来后的视角缩放回复速度太快"）
{
  const cam = strip(read('src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java'));
  const press = /ZOOM_PRESS_PER_SECOND = 10\.0f/.test(cam);
  const release = /ZOOM_RELEASE_PER_SECOND = 1\.8f/.test(cam);
  if (press && release) ok('缩放按下 10/s、松手 1.8/s（松手明显更慢）');
  else fail('缩放的按下/松开速率不对（press=' + press + ' release=' + release + '）');
}


console.log('');
console.log(bad === 0 ? '✅ 路亚音效接线正确（三处同名、文件真实、播放点齐全）' : '❌ 音效检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
