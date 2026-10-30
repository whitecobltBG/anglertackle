/*
 * check-chum-render-distance.js —— 窝料的渲染距离（2026-10-30 用户："稍微扔远点就看不到"）。
 *
 * 坑在哪：原版 `Entity.shouldRenderAtSqrDistance()` 是按**包围盒尺寸**算的 ——
 *   尺寸 0.3 的实体只有 (0.3 × 64)² ≈ 368 平方格 ≈ **19 格**。
 * 窝料是扔出去用的，19 格以外直接不画，玩家看到的就是"扔远了消失"。
 *
 * 这个检查器守两件事：
 *   ① `ChumEntity` 必须**覆盖** `shouldRenderAtSqrDistance`，且距离 ≥ 64 格；
 *   ② 注册表的 `clientTrackingRange`（区块）× 16 必须 ≥ 渲染距离 ——
 *      只改渲染不改追踪，会变成"能画但没数据"，照样看不见。
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const strip = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };

console.log('=== 窝料渲染距离检查 ===');

const ENT = 'src/main/java/com/whitecobalt/anglertackle/entity/ChumEntity.java';
const ent = strip(read(ENT));
const m = /shouldRenderAtSqrDistance\(double (\w+)\)\s*\{[\s\S]{0,200}?return \1 < ([0-9.]+) \* \2;/.exec(ent);
if (m) {
  const dist = Number(m[2]);
  if (dist >= 64) ok('ChumEntity 覆盖了渲染距离：' + dist + ' 格');
  else fail('渲染距离只有 ' + dist + ' 格 —— 扔远一点仍然会看不见');
} else {
  fail('ChumEntity 没有覆盖 shouldRenderAtSqrDistance —— 走原版"包围盒×64"，0.3 的实体只有约 19 格');
}

const REG = 'src/main/java/com/whitecobalt/anglertackle/registry/ModEntities.java';
const reg = strip(read(REG));
const range = /ENTITIES\.register\("chum"[\s\S]{0,400}?clientTrackingRange\((\d+)\)/.exec(reg);
if (range) {
  const chunks = Number(range[1]);
  const renderDist = m ? Number(m[2]) : 19;
  if (chunks * 16 >= renderDist) ok('追踪范围 ' + chunks + ' 区块（' + chunks * 16 + ' 格）≥ 渲染距离 ' + renderDist + ' 格');
  else fail('追踪范围只有 ' + chunks + ' 区块（' + chunks * 16 + ' 格）< 渲染距离 ' + renderDist + ' 格 —— 会"能画但没数据"');
} else fail('注册表里找不到 chum 的 clientTrackingRange');

console.log('');
console.log(bad === 0 ? '✅ 窝料渲染距离正常（128 格内都看得见）' : '❌ 窝料渲染距离检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
