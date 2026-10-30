/*
 * check-mail-claim-all.js —— 邮件「一键领取」的接线断言（2026-10-30 用户："邮件加一键领取功能"）。
 *
 * 为什么值得单独守：
 *   ① 批量领取**必须复用单封的 claim()**。一旦有人为了"快"另写一份发奖逻辑，
 *      "过期作废 / 背包满整封回滚 / 鱼币同步 / 标记已领"就会两套规则打架，
 *      而这类 bug 只在"背包刚好满"时出现 —— 单人测试几乎碰不到。
 *   ② 只准领**带附件**的信。`quest_accept` 那种"点按钮=接委托"的动作型邮件有玩家意图，
 *      被一键领取顺手执行掉，等于替玩家做了决定。
 *   ③ 背包满要**停下并提示**，不能继续空转，也不能把已领的悄悄回滚。
 */
const fs = require('fs');
const path = require('path');

const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');
const exists = (p) => fs.existsSync(path.join(ROOT, p));
const strip = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };

console.log('=== 邮件一键领取检查 ===');

// ① 动作常量
const PAY = 'src/main/java/com/whitecobalt/anglertackle/network/MailActionPayload.java';
const pay = strip(read(PAY));
if (/public static final int CLAIM_ALL = \d+;/.test(pay)) ok('MailActionPayload 有 CLAIM_ALL 动作常量');
else fail('没有 CLAIM_ALL 动作常量 —— 客户端点了也没人处理');

// ② 服务端批量逻辑
const SVC = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
const svc = strip(read(SVC));
if (/public static ClaimAll claimAll\(ServerPlayer player\)/.test(svc)) ok('MailService.claimAll(player) 存在');
else fail('没有 claimAll —— 一键领取没有服务端实现');

// ⚠ 判据必须**只看 claimAll 自己的方法体**：
//   `m.hasAttachment() && !m.claimed()` 在 expireAttachments 里也出现过一次，
//   全文件搜的话，负例把 claimAll 那条改掉、另一处还能让断言变绿（假绿，实测踩到）。
const claimAllBody = (() => {
  const a = svc.indexOf('public static ClaimAll claimAll(');
  const b = svc.indexOf('public record ClaimAll', a + 10);
  return a >= 0 && b > a ? svc.slice(a, b) : '';
})();
if (!claimAllBody) fail('切不出 claimAll 方法体 —— 检查器的判据失效了');

if (/Claim r = claim\(player, id\);/.test(claimAllBody)) ok('批量复用单封的 claim()（规则只有一份）');
else fail('批量没有调用 claim() —— 说明另写了一份发奖逻辑，规则会分叉');

if (/m\.hasAttachment\(\) && !m\.claimed\(\)/.test(claimAllBody)) ok('只领"有附件且未领"的信（动作型邮件不会被顺手执行）');
else fail('批量范围没限制在"有附件" —— 会把 quest_accept 这类动作邮件替玩家执行掉');

if (/r == Claim\.BAG_FULL[\s\S]{0,80}?break;/.test(claimAllBody)) ok('背包满时停下（不空转、不回滚已领的）');
else fail('背包满没有 break —— 会继续白试后面的信');

// ③ 分发
const NET = 'src/main/java/com/whitecobalt/anglertackle/network/ModNetwork.java';
const net = strip(read(NET));
if (/case MailActionPayload\.CLAIM_ALL/.test(net) && /MailService\.claimAll\(player\)/.test(net)) {
  ok('ModNetwork 里认 CLAIM_ALL 并调到 claimAll');
} else fail('网络层没接 CLAIM_ALL —— 按钮发出去也没反应');

if (/all\.bagFull\(\)[\s\S]{0,120}?err = "bag_full"/.test(net)) ok('背包满会回错误码 bag_full（界面能提示）');
else fail('背包满没有回错误码 —— 玩家只会觉得"没领到"');

// ④ 界面按钮
const SCR = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
const scr = strip(read(SCR));
if (/MailActionPayload\.CLAIM_ALL/.test(scr)) ok('邮件界面有【一键领取】按钮');
else fail('邮件界面没有【一键领取】按钮');

if (/claimAll\.active = snap != null && snap\.claimable\(\) > 0;/.test(scr)) {
  ok('没有可领邮件时按钮置灰（不会空点）');
} else fail('按钮没有"可领才可点"的判断');

// ⑤ 语言键
for (const lang of ['zh_cn', 'en_us']) {
  const p = 'src/main/resources/assets/anglertackle/lang/' + lang + '.json';
  if (!exists(p)) { fail('缺少 ' + p); continue; }
  const json = JSON.parse(read(p));
  const keys = ['gui.anglertackle.mail_claim_all', 'message.anglertackle.mail_claim_all'];
  const missing = keys.filter((k) => !json[k]);
  if (missing.length) fail(lang + ' 缺语言键：' + missing.join(', '));
  else ok(lang + ' 两条语言键齐全');
}

// 2026-10-30 用户截图："翻页键与已读重叠" ⇒ 底部这几个按钮的 x 区间必须互斥。
// 取的是 .bounds(left + N, by, W, 18) 里的 N/W（只认 y 用 `by` 的那批，避开详情栏按钮）。
{
  const rects = [...scr.matchAll(/\.bounds\(left \+ (\d+), by, (\d+), 18\)/g)]
    .map((m) => ({ x: Number(m[1]), w: Number(m[2]) }))
    .sort((a, b) => a.x - b.x);
  let overlap = null;
  for (let i = 1; i < rects.length; i++) {
    if (rects[i].x < rects[i - 1].x + rects[i - 1].w) {
      overlap = rects[i - 1].x + '-' + (rects[i - 1].x + rects[i - 1].w) + ' vs ' + rects[i].x;
    }
  }
  if (rects.length < 3) fail('底部按钮只解析出 ' + rects.length + ' 个 —— 判据失效（改过 bounds 写法？）');
  else if (overlap) fail('底部按钮重叠：' + overlap + '（用户截图反馈过"翻页键与已读重叠"）');
  else ok('底部按钮互不重叠：' + rects.map((r) => r.x + '~' + (r.x + r.w)).join(' | '));
}


console.log('');
console.log(bad === 0 ? '✅ 一键领取接线正确（复用 claim / 只领附件 / 背包满停止 / 有按钮与提示）' : '❌ 一键领取检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
