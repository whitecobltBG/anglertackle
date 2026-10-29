/*
 * check-fatigue-flicker.js —— 力竭状态不许"每轮闪一下"。
 *
 * 用户 2026-10-29 的原话：
 *   "感觉应该是游戏本身机制上会有些问题，比如把鱼拉力竭后还想挣扎结果发现鱼力早超过
 *    某个阈值所以立刻改回力竭状态导致听起来断断续续"。
 *
 * 查下来确实是：状态机是 if (fatigued) { 到期 → false } else if (体力仍低于阈值 → true)，
 * 两分支互斥 ⇒ 退出力竭的**下一 tick** 就能立刻重新进入，体力没回上去时必然发生。
 * 客户端 fatigued 每轮抖一次，音效在 reel_tired / reel_fight 之间反复重建 ⇒ 一顿一顿。
 *
 * 这个检查器盯三件事：
 *   1. 退出力竭时给**再进入冷却**（fatigueLockoutTicks = 10）；
 *   2. 进入力竭的条件里必须带 `fatigueLockoutTicks <= 0`；
 *   3. 客户端音效那边还有一层消抖（TIRED_DEBOUNCE_TICKS），双保险。
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const strip = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };

console.log('=== 力竭状态抖动检查 ===');

const SESS = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java';
const sess = strip(read(SESS));

if (/private int fatigueLockoutTicks/.test(sess)) ok('有 fatigueLockoutTicks 字段');
else fail('没有 fatigueLockoutTicks —— 力竭退出后会被立刻重新点亮（每轮闪一下）');

if (/this\.fatigued = false;\s*this\.fatigueLockoutTicks = \d+;/.test(sess)) {
  ok('退出力竭时给了再进入冷却');
} else fail('退出力竭时没有设冷却 —— 体力仍在阈值下时会"退出→下一 tick 重进"');

if (/this\.fatigueLockoutTicks <= 0/.test(sess)) ok('进入力竭的条件里带了冷却判断');
else fail('进入力竭没有判冷却 —— 冷却写了也没用');

// 冷却必须**先**递减再判状态（否则冷却永远为 0）
if (/if \(this\.fatigueLockoutTicks > 0\) \{\s*this\.fatigueLockoutTicks--;\s*\}/.test(sess)) {
  ok('冷却每 tick 递减（且在本帧判定之前）');
} else fail('冷却没有递减 —— 一旦进入就永远出不来了');

// 客户端音效侧的消抖
const REEL = 'src/main/java/com/whitecobalt/anglertackle/client/ReelSounds.java';
const reel = strip(read(REEL));
if (/TIRED_DEBOUNCE_TICKS/.test(reel) && /tiredTicks >= TIRED_DEBOUNCE_TICKS/.test(reel)) {
  ok('音效侧还有一层消抖（连续力竭 N tick 才换素材）');
} else fail('音效侧没有消抖 —— 状态一抖就重建循环音，听感断断续续');

console.log('');
console.log(bad === 0 ? '✅ 力竭状态稳定（两处抖动都堵住了）' : '❌ 力竭抖动检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
