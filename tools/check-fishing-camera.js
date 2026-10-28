/*
 * check-fishing-camera.js —— 守住对抗期相机那两条**用户口径**。
 *
 * 用户 2026-10-01 的原话：
 *   ① "鱼上钩后视角跟着鱼钩跑"；
 *   ② "玩家点击左键收线时再轻微放大，松开放线回到上鱼时的放大水平"。
 *
 * 这两句话翻成代码约束，就是下面这几条 —— 每一条都对应一种"看着实现了、其实坏了"：
 *
 *   A. 拉近必须**乘以当前 FOV**，不能 setFOV(写死的数)。
 *      用户的基线是"上鱼时画面上本来就有的视野水平"（可能叠着别的效果）。
 *      写死数字 = 把基线顶掉，松手也回不到原样。这是本条功能的核心语义。
 *   B. 只准改**渲染视角**：不许出现 player.setYRot/setXRot。
 *      改了真实朝向会连带影响抛投方向、原版瞄准与位置校验，而玩家只是想看浮漂。
 *   C. 帧间隔只能在 ComputeCameraAngles 里算一次。
 *      两个事件每帧各一次、CameraAngles 先跑；如果 ComputeFov 里再算一次，
 *      时间差恒为 0，渐入永远走不完（表现：拉近要么不来、要么瞬间跳）。
 *   D. 画面上的"在收线"必须和发给服务端的那个布尔同源（ClientFishingState.reeling()）。
 *      自己去读 keyAttack 会和机制错位。
 *   E. 跟随只在第一人称生效（第三人称是玩家自己在运镜），且偏航走最短弧。
 *
 * 用法：node tools/check-fishing-camera.js
 */
const fs = require('fs');
const path = require('path');

// ATL_ROOT 供 tools/negtest.ps1 指向临时副本；不给就是仓库根目录 ✓
const ROOT = process.env.ATL_ROOT ? path.resolve(process.env.ATL_ROOT) : path.resolve(__dirname, '..');
const read = (p) => fs.readFileSync(path.join(ROOT, p), 'utf8');

let bad = 0;
const ok = (m) => console.log('  ✅ ' + m);
const fail = (m) => { console.log('  ❌ ' + m); bad++; };

console.log('=== 对抗期相机检查 ===\n');

const CAM = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
const STATE = 'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java';
const CFG = 'src/main/java/com/whitecobalt/anglertackle/Config.java';

if (!fs.existsSync(path.join(ROOT, CAM))) {
  fail('没有 ' + CAM + ' —— 这个功能整个不见了');
  console.log('\n❌ 1 项问题');
  process.exit(1);
}
const cam = read(CAM);
const state = read(STATE);
const cfg = read(CFG);

/** 取一个 @SubscribeEvent 方法体。 */
function handler(name) {
  const m = new RegExp('public static void ' + name + '\\s*\\([^)]*\\)\\s*\\{').exec(cam);
  if (!m) return null;
  // 从方法起点往后找配平的大括号
  let i = cam.indexOf('{', m.index), depth = 0, j = i;
  for (; j < cam.length; j++) {
    if (cam[j] === '{') depth++;
    else if (cam[j] === '}') { depth--; if (depth === 0) break; }
  }
  return cam.slice(m.index, j + 1);
}

// ---------------------------------------------------------------- 接线
// ⚠ 两个方法体在下面多处要用，所以在块外声明。
//   （曾经把"相机是否读 state.reeling()"写成 `class FishingCamera[\s\S]{0,4000}?…`，
//     结果别处一插代码、窗口滑过去就**假报**了一条"没在读 reeling" ——
//     检查器报错指向的地方必须是真出错的地方，否则下次会查错方向。）
const anglesBody = handler('onCameraAngles');
const fovBody = handler('onComputeFov');
{
  const angles = anglesBody;
  const fov = fovBody;
  let n = 0;
  if (!angles) { fail('找不到 onCameraAngles（视角跟随）'); n++; }
  else if (!/ViewportEvent\.ComputeCameraAngles/.test(angles)) { fail('onCameraAngles 的参数不是 ComputeCameraAngles'); n++; }
  if (!fov) { fail('找不到 onComputeFov（收线拉近）'); n++; }
  else if (!/ViewportEvent\.ComputeFov/.test(fov)) { fail('onComputeFov 的参数不是 ComputeFov'); n++; }
  if (!/@EventBusSubscriber\([\s\S]{0,120}?Dist\.CLIENT/.test(cam)) {
    fail('FishingCamera 没有注册成客户端事件订阅者（Dist.CLIENT）');
    n++;
  }
  if (n === 0) ok('两个事件都接上了：ComputeCameraAngles（跟随）+ ComputeFov（拉近）');

  // ---------------------------------------------------------------- A 乘以当前 FOV
  if (fov) {
    if (!/setFOV\(\s*event\.getFOV\(\)\s*\*/.test(fov)) {
      fail('ComputeFov 里不是"乘以当前 FOV"：用户要的是"松手回到上鱼时的放大水平"，' +
        '基线是画面上本来就有的视野；写成 setFOV(固定数值) 会把基线顶掉，松手回不到原样');
    } else if (/setFOV\(\s*[\d.]+/.test(fov)) {
      fail('ComputeFov 里出现了 setFOV(数字字面量) —— 同上，会把基线顶掉');
    } else {
      ok('拉近是 event.getFOV() × 系数（叠在当前视野上，不覆盖基线）');
    }
  }

  // ---------------------------------------------------------------- B 只改渲染视角
  // 允许出现在注释里，所以只查**代码**里的调用
  const codeOnly = cam.replace(/\/\*[\s\S]*?\*\//g, ' ')
    .split('\n').map((l) => l.replace(/\/\/.*$/, '')).join('\n');
  // ⚠ 2026-10-28 语义变更（用户最终口径）：上鱼后**必须**把玩家真实朝向转到浮漂最后位置，
  //   所以 setYRot/setXRot 允许存在 —— 但只准出现在 stepTurn()（转向）里，
  //   绝不允许出现在对抗跟随（onCameraAngles 主体）里，否则会边跟边抢鼠标。
  const bodyEnd = codeOnly.indexOf('beginTurnToLastAim');
  const mainBody = bodyEnd > 0 ? codeOnly.slice(0, bodyEnd) : codeOnly;
  const real = ['setYRot', 'setXRot', 'setYHeadRot', 'setYBodyRot']
    .filter((m) => new RegExp('\\.' + m + '\\s*\\(').test(mainBody));
  if (real.length) {
    fail('FishingCamera 动了玩家的**真实朝向**：' + real.join(', ') +
      ' —— 只准改渲染视角（event.setYaw/setPitch），改真实朝向会连带影响抛投方向与原版瞄准');
  } else {
    ok('只写 event.setYaw/setPitch，没有碰玩家的真实朝向');
  }

  // ---------------------------------------------------------------- C 帧间隔只算一次
  if (fov && /beginFrame\s*\(/.test(fov)) {
    fail('ComputeFov 里又算了一次帧间隔 —— 它每帧排在 ComputeCameraAngles 之后，' +
      '这里算出来恒为 0，渐入永远走不完（表现：拉近不来 / 瞬间跳）');
  } else if (angles && /beginFrame\s*\(/.test(angles)) {
    ok('帧间隔只在 ComputeCameraAngles 里算一次，ComputeFov 复用');
  } else {
    fail('没有任何一处按时间推进渐入 —— 渐入会变成"每帧固定比例"，帧率一变手感就变');
  }

  // ---------------------------------------------------------------- E 第一人称 + 最短弧
  if (angles) {
    if (/isFirstPerson\(\)/.test(angles)) ok('跟随只在第一人称生效');
    else fail('没有判第一人称 —— 第三人称下会被强行拽镜头（那是玩家自己在运镜）');
    if (/wrapDegrees\(/.test(angles)) ok('偏航走最短弧（wrapDegrees），不会在 ±180° 处甩一整圈');
    else fail('偏航没有走最短弧 —— 目标在正后方时镜头会绕远路转一整圈');
  }

  // ---------------------------------------------------------------- F 丝滑（不可跳帧）
  //
  // 用户 2026-10-01："移动镜头卡卡的，希望丝滑点"。
  // 卡顿的根因是**目标位置是 10Hz 的离散值**（服务端每 2 tick 才同步一次浮漂坐标），
  // 每帧直接拿它算角度，画面就以 10Hz 一跳一跳。所以这两条是"丝滑"的硬保证：
  //   F1. 瞄准点必须按**时间**做指数平滑（不是固定比例 —— 那会随帧率变手感）；
  //   F2. 有客户端实体时优先用它的**插值位置**（每帧都在动，最顺）。
  if (angles) {
    if (/smoothTarget\s*=\s*smoothTarget\.lerp\(\s*bob\s*,\s*ease\(dt,\s*POS_TAU\)\s*\)/.test(angles)) {
      ok('瞄准点按时间做指数平滑（跟浮漂而不是踩 10Hz 台阶）');
    } else {
      fail('瞄准点没有走时间平滑 —— 直接拿同步坐标（每 2 tick 一份）当目标，' +
        '镜头会以 10Hz 一跳一跳，也就是玩家说的"卡卡的"');
    }
    if (/event\.setYaw\(smoothYaw\)/.test(angles) && /event\.setPitch\(smoothPitch\)/.test(angles)) {
      ok('写进事件的是**自己维护的平滑角度**，不是与玩家朝向的固定比例混合');
    } else {
      fail('写进事件的不是平滑角度 —— 与玩家朝向按固定比例混合的结果只跟鼠标有关、不带时间，目标一跳画面就跳');
    }
    if (/bob\.distanceToSqr\(smoothTarget\)\s*>\s*SNAP_DIST_SQ/.test(angles)) {
      ok('目标跳太远（换浮漂/重抛）时直接吸附，不会从旧位置慢慢滑过去');
    } else {
      fail('没有"跳变就吸附"的保护 —— 重新抛竿时镜头会横穿整个世界滑过去');
    }
  }
  if (/Math\.exp\(-dt \/ tau\)/.test(cam)) {
    ok('平滑系数是帧率无关的指数逼近 1−e^(−dt/τ)');
  } else {
    fail('平滑系数不是指数逼近 —— 写成"每帧固定 20%"这类的话，30fps 与 240fps 的收敛速度差 8 倍');
  }
  if (/getPosition\(\(float\)\s*partialTick\)/.test(cam)) {
    ok('有客户端实体时优先用它的插值位置（每帧都在动，比同步包顺）');
  } else {
    fail('没有用实体的插值位置 —— 会话同步包每 2 tick 才一份，这是卡顿的主因之一');
  }
}

// ---------------------------------------------------------------- D 收线与机制同源
{
  if (/public boolean reeling\(\)/.test(state)) ok('ClientFishingState 暴露了 reeling()');
  else fail('ClientFishingState 没有 reeling() —— 相机会去自己读按键，和机制错位');

  if (/this\.primaryHeld\s*=\s*primaryHeld;/.test(state)) {
    ok('reeling() 存的就是**发给服务端的那个布尔**');
  } else {
    fail('primaryHeld 没有从 sendInput 的局部变量赋值 —— 画面上的"在收线"和机制上的不是同一个值');
  }
  if (/FishingInputPayload\.pack\(\s*primaryHeld/.test(state)) {
    ok('同一个布尔确实进了输入包（画面与机制同源）');
  } else {
    fail('输入包里用的不是 primaryHeld —— 两者会漂移');
  }
  if (/public void reset\(\)[\s\S]{0,400}?primaryHeld = false;/.test(state)) {
    ok('reset() 会清掉 reeling（换世界不会残留拉近）');
  } else {
    fail('reset() 没有清 primaryHeld —— 上一杆按着左键时，新世界会带着拉近进来');
  }
  if (fovBody && /\.reeling\(\)/.test(fovBody)) {
    ok('相机读的是 state.reeling()，没有自己读 keyAttack');
  } else {
    fail('ComputeFov 里没有用 state.reeling() —— 应该跟机制同源，别自己读按键');
  }
}

// ---------------------------------------------------------------- G 2026-10-16 用户报的五条
//
// 用户原话（这一轮修的就是这五条，逐条钉成断言）：
//   ① HUD 那行里去掉「收线中」与「张力 白/黄/红」两段；
//   ⑥ 教程书点左栏换章之后翻回第一页；
//   ③ 没上鱼的时候屏幕上什么都没有，要显示「（鱼上钩时左键收杆）」；
//   ④ 上鱼之后还要被锁移动 1 秒左右；
//   ⑤ 部分新存档不发教程。
{
  const HUD = 'src/main/java/com/whitecobalt/anglertackle/client/hud/FishingHud.java';
  const EV = 'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java';
  const GUIDE = 'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java';
  const JOIN = 'src/main/java/com/whitecobalt/anglertackle/event/FirstJoinEvents.java';
  const BOX = 'src/main/java/com/whitecobalt/anglertackle/player/MailBox.java';
  const ENT = 'src/main/java/com/whitecobalt/anglertackle/player/MailEntry.java';
  const SESSION = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java';
  const MANAGER = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java';
  const SYNC = 'src/main/java/com/whitecobalt/anglertackle/network/SessionSyncPayload.java';
  const STATEJ = 'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java';

  /** 去掉注释后的**代码**：所有"存在性"判据都必须走它（注释里提过的名字不算数）。 */
  const code = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

  /** 取一个方法的方法体（按大括号配平），找不到返回 null。 */
  function bodyOf(src, name) {
    const m = new RegExp('(?:public|private|protected|static|final|\\s)+[\\w<>\\[\\].]+\\s+'
      + name + '\\s*\\([^)]*\\)\\s*\\{').exec(src);
    if (!m) return null;
    let i = src.indexOf('{', m.index), depth = 0, j = i;
    for (; j < src.length; j++) {
      if (src[j] === '{') depth++;
      else if (src[j] === '}') { depth--; if (depth === 0) break; }
    }
    return src.slice(i, j + 1);
  }

  const has = (rel, fallback) => {
    try { return read(rel); } catch { fail('找不到 ' + rel); return fallback; }
  };
  const hud = has(HUD, '');
  const ev = has(EV, '');
  const guide = has(GUIDE, '');
  const join = has(JOIN, '');
  const box = has(BOX, '');
  const ent = has(ENT, '');
  const sess = has(SESSION, '');

  // ---- ① 状态行里不许再出现「收线中」/「张力 <档>」
  {
    const fb = bodyOf(sess, 'fightHint');
    if (!fb) {
      fail('FishingSession 里找不到 fightHint() —— 状态行整个不见了？');
    } else {
      const c = code(fb);
      // 判据看的是**真的拼进那一行**（含 § 颜色码的那种字面量），不是"文件里还有这两个词"
      if (/收线中/.test(c)) {
        fail('状态行里还有「收线中」—— 用户定案去掉：那是玩家自己按着的键，写出来等于念他的操作');
      } else if (/张力\\s*["+]|"\\s*张力/.test(c) || /张力 §/.test(fb)) {
        fail('状态行里还有「张力 <颜色>」—— 用户定案去掉：张力条就在屏幕上，不必再写一遍');
      } else {
        ok('状态行里没有「收线中」也没有「张力 X」（①）');
      }
      if (/鱼力/.test(fb) && /lineHint/.test(fb)) {
        ok('保留的项还在：距离（lineHint）与鱼力百分比（①）');
      } else {
        fail('距离或鱼力被一起删掉了 —— 用户只要求去掉那两段，其余要留');
      }
    }
  }

  // ---- ④ "上鱼后给鱼、视角复原，一秒后允许移动"（用户 2026-10-16 纠正过一次设计）
  //
  // 用户原话：①"上鱼后应该一秒内无法移动，不然有人收不住方向键会掉进水里"；
  //          ②（看了实现之后）"我想要的是**上鱼后给鱼、视角缩放恢复正常，一秒后允许移动**"。
  // 我第一版把三件事绑在同一个 LANDING 状态上 ⇒ 玩家看到"视角仍然没正常、也不给鱼；一秒后才给鱼" ✗。
  // 现在拆成三份，这一组就是钉住这个拆分的。
  {
    const codeEv = code(ev);
    const lock = bodyOf(state, 'movementLocked');
    if (lock && /movementLockTicks\s*>\s*0/.test(code(lock))) {
      ok('movementLocked() 认"服务端给的倒计时"（上鱼后那一秒由它撑着）');
    } else {
      fail('movementLocked() 不看 movementLockTicks —— 上鱼后立刻放开移动键，'
        + '收线时按住的方向键会把玩家带进水里（用户定的功能）');
    }
    if (/state\.movementLocked\(\)/.test(codeEv)) {
      ok('ClientEvents 的移动键接管走 movementLocked()');
    } else {
      fail('ClientEvents 没有用 movementLocked() 当接管判据');
    }
    if (/if\s*\(\s*state\.movementLocked\(\)\s*\)\s*\{/.test(codeEv)
      && /leftImpulse\s*=\s*0\.0f/.test(codeEv) && /forwardImpulse\s*=\s*0\.0f/.test(codeEv)) {
      ok('移动意图确实是在 movementLocked() 里清掉的');
    } else {
      fail('找不到"在 movementLocked() 里清 leftImpulse/forwardImpulse"—— 锁没接上');
    }

    // 视角/条子只认 FIGHT（= 上鱼那一刻就收）—— 与"移动还锁一秒"**必须分开**
    const fight = bodyOf(state, 'inFight');
    const bars = bodyOf(state, 'tensionBarsVisible');
    if (fight && /STATE_FIGHT/.test(fight) && !/STATE_LANDING/.test(fight)
      && bars && /STATE_FIGHT/.test(bars) && !/STATE_LANDING/.test(bars)) {
      ok('视角与条子只认 FIGHT（上鱼那一刻就复原），不再拖到 LANDING');
    } else {
      fail('inFight()/tensionBarsVisible() 还把 LANDING 算进去了 —— 那正是"上鱼后视角迟迟不正常"的原因'
        + '（用户报的"上鱼后视角仍然没正常"）');
    }
    // ⚠⚠ 2026-10-16 用户定案：台钓钓上鱼会立刻放大视野，而他要的是「不变」。
    //   ⇒ 拉近/跟随必须多判一道**竿型**：只判 inFight()（服务端状态）挡不住「状态串一帧」，
    //     而台钓根本没有「收线拉近」这个概念（它一上鱼就直接收鱼）。
    {
      const camCode2 = code(cam);
      const fovSrc = /onComputeFov[\s\S]*$/.exec(camCode2);
      const fovSrcText = fovSrc ? fovSrc[0] : '';
      // 三票同时成立才允许缩放（服务端竿型 + FIGHT + 手上路亚竿）
      // 2026-10-28 结构变更：总闸改成"锁存（lureZoomLatched）+ 慢放" ——
      // 一出 FIGHT 不再硬切零，而是用慢速率放回；台钓拿不到锁存，所以仍然是结构上为零。
      const fov = /if \(st\.lureSession\(\)\) \{\s*lureZoomLatched = true;/.test(fovSrcText)
        && /if \(!lureZoomLatched \|\| !holdingLureRod\(\)\) \{[\s\S]{0,60}?zoom = 0\.0f;/.test(fovSrcText)
        && fovSrcText.indexOf('zoom = 0.0f') < fovSrcText.indexOf('setFOV');
      // 2026-10-28 结构变更：want 拆成了 fighting‖lingering，竿型条件挪进 followable。
      const follow = /boolean followable = cfgFollow\(\) && holdingLureRod\(\)/.test(camCode2);
      if (fov && follow) {
        ok('收线拉近只给路亚：总闸 = 在对抗 && 手拿路亚竿；其余时刻 zoom 立刻清零（台钓绝不缩放）');
      } else {
        fail('收线拉近没有「非路亚立刻清零」这道总闸（fov=' + fov + ' follow=' + follow + '）—— '
          + '上一场路亚的 zoom 残留会跟到台钓里，表现为「台钓上鱼后视野被放大」');
      }
      if (/private static boolean holdingLureRod\(\)/.test(camCode2)) {
        ok('判竿型的辅助方法 holdingLureRod() 在（只认 LURE，认不出来返回 false）');
      } else {
        fail('找不到 holdingLureRod() —— 竿型判据没有落点');
      }
    }

    const camCode = code(cam);
    if (/st\.inFight\(\)/.test(camCode) && !/st\.movementLocked\(\)/.test(camCode)) {
      ok('相机的"跟随 / 拉近"只认 FIGHT（上鱼那一刻就松开，不跟移动锁绑在一起）');
    } else {
      fail('相机用了 movementLocked()（或没走 inFight()）—— 鱼都给了镜头还原地黏着');
    }

    // 服务端：上鱼**当场**结算（不再等 LANDING 的 1.25~1.5 秒）
    const codeSess = code(sess);
    if (/finishCatch\(\);/.test(codeSess) && /catchMoveLockTicks\s*=\s*CATCH_MOVE_LOCK_TICKS/.test(codeSess)) {
      ok('服务端在两个"上鱼"分支里都**当场** finishCatch() + 记下移动锁时长');
    } else {
      fail('上鱼分支没有当场 finishCatch()（或没记 CATCH_MOVE_LOCK_TICKS）—— '
        + '鱼又要等到 LANDING 结束才给，用户报过这个"不给鱼"');
    }
    // ⚠⚠ 2026-10-16 用户报「台钓出现了路亚张力状态条一闪而过」时查出来的**关键分野**：
    //   台钓 onHooked() **直接进 LANDING**（没有张力对抗），路亚才进 FIGHT。
    //   张力条只认 FIGHT ⇒ 台钓天然不该出现那条。
    //   这条断言把「两条路」钉住：将来谁把台钓也改成 FIGHT，张力条就会在台钓里冒出来，这里当场报红。
    {
      const hooked = bodyOf(code(sess), 'onHooked');
      if (!hooked) {
        fail('找不到 FishingSession.onHooked() —— 提竿成功那条路被改了结构？');
      } else {
        const floatFirst = hooked.indexOf('state = State.LANDING');
        const lureFight = hooked.indexOf('state = State.FIGHT');
        if (/RodType\.FLOAT/.test(hooked) && floatFirst > 0 && lureFight > floatFirst) {
          ok('台钓与路亚是两条路：FLOAT 直接进 LANDING、FIGHT 只留给路亚');
        } else {
          fail('onHooked() 里台钓不再「直接收鱼」（或 FIGHT 排在 FLOAT 分支之前）—— '
            + '张力条/对抗会在台钓里冒出来，而台钓本来没有张力对抗');
        }
      }
    }


    // ⚠⚠ 2026-10-28（用户："台钓上鱼后视野会缩放"）：**冲刺只许在 FIGHT 里关**。
    //   冲刺状态直接乘原版 FOV（×1.15）；在 LANDING（上鱼后那 1 秒）关掉它，
    //   视野就会从 1.15 收回 1.0 —— 看上去正是"视野缩了一下"。
    {
      const evCode = code(ev);
      const mgrCode3 = code(has(MANAGER, ''));
      const clientOk = /if \(state\.inFight\(\)\) \{\s*mc\.player\.setSprinting\(false\);/.test(evCode);
      const serverOk = /if \(session\.state\(\) == FishingSession\.State\.FIGHT\) \{\s*player\.setSprinting\(false\);/.test(mgrCode3);
      if (clientOk && serverOk) {
        ok('冲刺只能在对抗中关（上鱼后的移动锁不碰冲刺 —— 否则台钓 FOV 会跳一下）');
      } else {
        fail('移动锁里无条件关冲刺（client=' + clientOk + ' server=' + serverOk + '）—— '
          + '冲刺一关，原版 FOV 从 1.15 收回 1.0，台钓上鱼时就会「视野缩一下」');
      }
    }

    // 服务端那一半也必须在（1.6.6 只有客户端锁，用户实测「还是能动」）
    {
      const bm = bodyOf(code(sess), 'blocksMovement');
      if (bm && /FIGHT/.test(bm) && /LANDING/.test(bm)) {
        ok('State.blocksMovement() = FIGHT + LANDING（服务端的权威判据）');
      } else {
        fail('State.blocksMovement() 不是「FIGHT 或 LANDING」—— 服务端那半没锁上鱼后的落地段');
      }
      const mgrCode2 = code(has(MANAGER, ''));
      if (/session\.state\(\)\.blocksMovement\(\)/.test(mgrCode2)) {
        ok('FishingManager 的移动速度锁走 blocksMovement()（服务端权威：客户端怎么发包都走不动）');
      } else {
        fail('FishingManager 还在用 == State.FIGHT 当锁的判据 —— 上鱼后那段速度属性会被立刻还原，客户端清冲量压不住动量与水流，玩家照样能走（用户 2026-10-16 实测过）');
      }
      // ⚠⚠ 2026-10-16（用户报"移动没锁一秒"）：那一秒必须**活过会话**。
      //   上鱼那一刻会话就结束了（为的是立刻给鱼），end() 会 restoreMoveSpeed() ⇒
      //   服务端的速度锁只活到"给鱼"那一刻，只剩客户端清冲量（压不住动量与水流）✗。
      // ⚠ 判据要盯**或**（`||`），不能只搜到方法名 —— 把 `||` 改成 `&&` 也照样搜得到，
      //   而那正是「那一秒不生效」的写法（这条是被负例 fight-lock-server-only-fight 教出来的）。
      if (/blocksMovement\(\)\s*\|\|\s*catchLockActive\(player\)/.test(mgrCode2)
        && /moveLockUntil/.test(mgrCode2)) {
        ok('上鱼后那一秒另有独立记时（moveLockUntil / catchLockActive）—— 活过会话本身');
      } else {
        fail('服务端的移动锁只认"会话还在" —— 而上鱼那一刻会话就结束了，于是那一秒里速度属性已经还原，只剩客户端清冲量（压不住动量与水流）⇒ 玩家能走');
      }
      if (/markCatchMoveLock/.test(code(sess))) {
        ok('上鱼结算（finishCatch）把那一秒记进了 FishingManager');
      } else {
        fail('finishCatch 没有 markCatchMoveLock() —— 那一秒没人记，等于没锁');
      }
      // 登出时必须一起清：否则带着没到期的锁再进来，tick 里"未到期就压着速度属性"
      // 会让玩家一落地就走不动（看着莫名其妙）
      if (/forget\(ServerPlayer player\)[\s\S]{0,400}moveLockUntil\.remove/.test(mgrCode2)) {
        ok('登出时那一秒的锁一起清（不会把锁带过存档边界）');
      } else {
        fail('forget() 没清 moveLockUntil —— 带着没到期的锁登出再进来会走不动');
      }
      if (/expireCatchMoveLock\(p\)/.test(mgrCode2)) {
        ok('到期会清掉那把锁（不会把玩家永久锁住）');
      } else {
        fail('没有 expireCatchMoveLock —— 锁上不还，玩家会永久走不动');
      }
      if (/restoreMoveSpeed\(player\)/.test(mgrCode2) && /savedMoveSpeed/.test(mgrCode2)) {
        ok('速度属性仍然是「存原值 → 归零 → 还原」（不会把玩家永久锁住）');
      } else {
        fail('找不到 restoreMoveSpeed / savedMoveSpeed —— 万一锁上没还，玩家会永久走不动');
      }
    }

    if (/landingTicks\s*=\s*0/.test(codeSess)) {
      ok('上鱼分支把 landingTicks 置 0（LANDING 不再拖时间，只剩迁移/兜底）');
    } else {
      fail('上鱼分支仍写 landingTicks = 25/30 —— 那 1.25~1.5 秒的空等又回来了');
    }
    const mgrCode = code(has(MANAGER, ''));
    // ⚠ 判据用"两个 indexOf 的先后"，不用正则（正则里那对斜杠要再转义一层，
    //   写错一次就是整份检查器语法错误 —— 2026-10-16 当场踩过）。
    const iSend = mgrCode.indexOf('ModNetwork.sendTo(player, session.snapshot())');
    const iEnd = mgrCode.indexOf('end(session)', iSend);
    if (iSend >= 0 && iEnd > iSend && iEnd - iSend < 6000) {
      ok('上鱼结算在 end(session) **之前**推了最终快照（客户端据此立刻收视角 + 开始倒计时）');
    } else {
      fail('上鱼结算没有在 end() 之前推最终快照 —— 客户端只能等 20 tick 超时，'
        + '又变成"视角迟迟不收、鱼也晚给"');
    }

    // 同步包：字段要真的进了编解码（手写编解码漏一个就整体错位）
    const pl = has(SYNC, '');
    if (/byte movementLockTicks/.test(pl) && /buf\.writeByte\(p\.movementLockTicks\)/.test(pl)
      && /buf\.readByte\(\)/.test(pl)) {
      ok('SessionSyncPayload 的 movementLockTicks 在字段/写/读三处都在');
    } else {
      fail('movementLockTicks 没进 SessionSyncPayload 的手写编解码 —— 客户端拿到的锁时长会是垃圾值');
    }
    const st = has(STATEJ, '');
    if (/this\.movementLockTicks\s*=\s*Math\.max\(0, p\.movementLockTicks\(\)\)/.test(st)
      && /if \(movementLockTicks > 0\) \{\s*movementLockTicks--;/.test(st.replace(/\s+/g, ' '))) {
      ok('客户端收下锁时长并每 tick 递减（倒计时只有一个真源：服务端给的数）');
    } else {
      fail('客户端没有"收下 + 递减" movementLockTicks —— 那一秒锁不住');
    }
  }
  // ---- ③ 等咬钩时的那行提示
  {
    const ab = bodyOf(state, 'awaitingBite');
    if (!ab || !/STATE_WAITING/.test(ab)) {
      fail('ClientFishingState 的 awaitingBite() 不是"浮漂落定后的 WAITING"—— '
        + '范围给大会在浮漂还在飞的时候就弹字，给小了（比如要求 hookWindow）就永远不会显示');
    } else {
      ok('awaitingBite() 只认 WAITING（③）');
    }
    // ⚠ 判据盯的是"**真的调了那个渲染函数**"，不是"文件里出现过 awaitingBite 这串字符"：
    //   第一版写的是 /state\.awaitingBite\(\)/ —— 那只匹配到 `if (...)` 的**条件行**，
    //   负例把下面那行**调用**注释掉之后条件行还在 ⇒ 断言照样绿（漏检，当场实测到）。
    //   判据改成"在 render() 里、剥注释之后，能匹配到独立的 renderAwaitBiteHint(...) 调用"。
    const renderBody = bodyOf(hud, 'render');
    const callRe = /(^|\n)[ \t]*renderAwaitBiteHint\s*\(/;
    if (renderBody && callRe.test(code(renderBody))) {
      ok('HUD 在 render() 里真的调了 renderAwaitBiteHint()（③）');
    } else {
      fail('HUD 的 render() 里没有对 renderAwaitBiteHint() 的**调用** —— 那行提示永远不会出现'
        + '（⚠ 判据必须剥注释 + 盯调用：只查 `state.awaitingBite()` 这串字符的话，'
        + '负例把调用注释掉、条件行还在，断言照样通过 —— 2026-10-16 现场栽过一次）');
    }

    const rb = bodyOf(hud, 'renderAwaitBiteHint');
    if (rb && /hud\.anglertackle\.await_bite/.test(rb)) {
      ok('提示文字走语言键 hud.anglertackle.await_bite（中英各一份，check-lang 守）');
    } else {
      fail('renderAwaitBiteHint 里没有用 hud.anglertackle.await_bite —— 中英会各写各的');
    }
    if (rb && /screenH\s*-\s*\d+/.test(rb)) {
      ok('提示画在**底部**（左下角、耐久面板上方）—— 不与准心下的动作栏/经验条抢同一段竖向空间');
    } else {
      fail('提示又画到屏幕中心线附近了 —— 那一带是动作栏（"！提竿！"/对抗状态行）与经验条，'
        + '三样挤在一起就是用户 2026-10-16 截到的"文本重叠"');
    }
    if (rb && /graphics\.fill\(/.test(rb)) {
      ok('提示带半透明黑底（唯一一个没有底板的 HUD 文字压在亮水面上读不清）');
    } else {
      fail('提示没有底板 —— 亮水面/木板上会看不清（张力条与耐久面板都有底板，这条也该有）');
    }
  }

  // ---- ⑥ 换章才回第 1 页
  {
    const ab = bodyOf(guide, 'acceptNewChapter');
    if (!ab) {
      fail('GuideScreen 里找不到 acceptNewChapter()');
    } else {
      const c = code(ab);
      if (/payload\.index\(\)\s*!=\s*this\.data\.index\(\)/.test(c)
        || /this\.data\.index\(\)\s*!=\s*payload\.index\(\)/.test(c)) {
        ok('换章判据是"服务端回的章序号变了"（⑥）');
      } else {
        fail('acceptNewChapter 没有比较章节序号 —— 无条件 page=0 就是"点左栏永远回第一页"（用户报的 ⑥）');
      }
      if (/if\s*\(\s*chapterChanged\s*\)\s*\{?[\s\S]{0,120}?this\.page\s*=\s*0;/.test(c)) {
        ok('page = 0 被关在"真的换章"这个分支里');
      } else {
        fail('page = 0 不在换章分支里 —— 同章刷新也会把玩家踢回第一页');
      }
    }

    // ⚠⚠ 2026-10-16 用户**第二次**报同一个症状（"翻到第 3 页后再点【当前这一章】还会跳回第 1 页"）。
    //   上面那条判据（比章节序号）是"收到回包之后"的补救 —— 它把这条路径交给了一次网络往返。
    //   现在必须在**事件入口**就挡掉：点当前章 = 什么都不做（不发包、不重建、不动 page）。
    const toc = bodyOf(guide, 'rebuildToc');
    if (toc && /if\s*\(\s*index\s*==\s*data\.index\(\)\s*\)\s*\{\s*return;/.test(code(toc))) {
      ok('目录按钮在入口就挡掉"点当前章"（index == data.index() ⇒ return，页码不可能变）（⑥）');
    } else {
      fail('目录按钮没有"点当前章直接返回"的守卫 —— 它仍然要靠服务端回包 + 序号比较才不跳页码，'
        + '只要那条路上有任何偏差，玩家看到的就是"点当前章又跳回第一页"（用户报过两次）');
    }
    // ⚠⚠ 用户第二次报的「看错对象」：我第一版以为是正文翻页，其实他说的是**左栏目录自己跳回第 1 页**。
    //   18 章、一屏 8 行 ⇒ 目录有 3 页；换章时把 tocTop 一起归零，玩家在第 2 页点第 10 章，
    //   目录立刻弹回第 1 页，他没法接着往下点。
    if (ab && !/this\.tocTop\s*=\s*0/.test(code(ab))) {
      ok('换章只归零正文页码，**不动目录页码**（左栏不会弹回第 1 页）（⑥）');
    } else {
      fail('acceptNewChapter 里还有 tocTop = 0 —— 换章时左栏目录会弹回第 1 页（用户为此报过两次）');
    }
    if (toc && /tocPages\(\)/.test(code(toc)) && /private int tocPages\(\)/.test(guide)) {
      ok('目录页数只有一个算法 tocPages()（init 与 rebuildToc 共用，不会各算一份）');
    } else {
      fail('目录页数在两处各算一遍 —— 迟早漂（打开书时定位到当前章、翻页时又说越界）');
    }
    if (/if\s*\(tocTop\s*==\s*0\s*&&\s*tocRows\s*>\s*0\)/.test(code(guide))) {
      ok('打开书时把目录翻到**当前章所在**那一页（读到一半的书，目录停在第 1 页也是 bug）');
    } else {
      fail('init() 没有把目录定位到当前章 —— 打开一本读到一半的书，目录会从第 1 页开始');
    }

    if (toc && /if\s*\(\s*index\s*==\s*data\.index\(\)\s*\)\s*\{\s*b\.active\s*=\s*false;/.test(code(toc))) {
      ok('当前章在左栏里被置灰（b.active = false）—— 与"点了没反应"是同一件事，且不会让人以为界面坏了');
    } else {
      fail('当前章没有置灰 —— 按钮看起来能点、点了却没反应，玩家会以为界面坏了');
    }
  }

  // ---- ⑤ 没领的新手礼包反复提醒
  {
    const kit = bodyOf(join, 'unclaimedKit');
    if (kit && /"starter_kit"\.equals\(m\.template\(\)\)/.test(kit) && /!m\.claimed\(\)/.test(kit)) {
      ok('登录时会查"邮箱里那封新手礼包还没领"（判据只看模板 id + claimed）（⑤）');
    } else {
      fail('FirstJoinEvents 里没有 unclaimedKit() —— 玩家没注意到那句提示就永远不知道去哪领（⑤）');
    }
    if (/if\s*\(unclaimedKit\(player\)\)\s*\{[\s\S]{0,200}?displayClientMessage\([\s\S]{0,200}?,\s*true\)/.test(code(join))) {
      ok('没领礼包时用**动作栏**（true）再说一遍 —— 聊天栏那行会被后面的话顶上去');
    } else {
      fail('那句提醒没有走动作栏（displayClientMessage(..., true)）—— 等于还是只发一条会滚走的聊天行');
    }
    if (/MailBox nudged = box\.withKitUnread\(\);/.test(join)
      && /PlayerData\.setMails\(player, nudged\)/.test(join)) {
      ok('礼包没领就把它标回未读，于是每次登录的未读提示都会再提一次');
    } else {
      fail('没有 withKitUnread() 那一步 —— 未读提示只提一次，错过就没了');
    }
    if (code(join).indexOf('unclaimedKit(player)') < code(join).indexOf('if (data.guideGiven())')) {
      ok('这段在 guideGiven() 早退**之前** —— 每次登录都会跑到（放在后面会被早退挡掉）');
    } else {
      fail('礼包检查被放到了 guideGiven() 早退之后 —— 老玩家永远走不到那一段');
    }
    const ku = bodyOf(box, 'withKitUnread');
    if (ku && /"starter_kit"\.equals\(m\.template\(\)\)/.test(ku)
      && /!m\.claimed\(\)/.test(ku) && /!m\.unread\(\)/.test(ku)) {
      ok('withKitUnread() 只改"已读且没领"的那封（本来就未读的原样放回）');
    } else {
      fail('withKitUnread() 的判据不是"已读且没领" —— 条件写反的话它退化成"原样返回"，'
        + '而那一次它照样编译得过（本轮真栽过一次）');
    }
    const wu = bodyOf(ent, 'withUnread');
    if (wu && /!read \? this :/.test(wu)) ok('MailEntry.withUnread() 幂等（本来就未读就返回 this）');
    else fail('MailEntry.withUnread() 不是幂等写法 —— 每登录一次就重建一个新对象，看不出坏但白写盘');
    const zh = read('src/main/resources/assets/anglertackle/lang/zh_cn.json');
    const en = read('src/main/resources/assets/anglertackle/lang/en_us.json');
    for (const k of ['hud.anglertackle.await_bite', 'message.anglertackle.starter_kit_unclaimed']) {
      if (!zh.includes('"' + k + '"')) fail('zh_cn.json 缺语言键 ' + k);
      else if (!en.includes('"' + k + '"')) fail('en_us.json 缺语言键 ' + k);
      else ok('语言键中英成对：' + k);
    }
  }
}

// ---------------------------------------------------------------- 配置项
{
  const decls = ['hookCameraFollow', 'hookCameraFollowStrength', 'reelZoomPercent'];
  let n = 0;
  for (const k of decls) {
    const declared = new RegExp('public final ModConfigSpec\\.\\w+Value ' + k + ';').test(cfg);
    const defined = new RegExp('\\b' + k + ' = b\\.comment').test(cfg);
    if (!declared || !defined) { fail('配置项 ' + k + ' 声明/定义不齐（declared=' + declared + ' defined=' + defined + '）'); n++; }
  }
  if (n === 0) ok('三个配置项（开关 / 跟随强度 / 拉近百分比）都在 [render] 组里');
  if (!/defineInRange\("hookCameraFollowStrength", 1\.0, 0\.0, 1\.0\)/.test(cfg)) {
    fail('hookCameraFollowStrength 的默认值/范围不是 1.0 / 0~1（用户 2026-10-28：跟随强度太低，拉满）');
  }
  if (!/defineInRange\("reelZoomPercent", 75\.0, 70\.0, 100\.0\)/.test(cfg)) {
    fail('reelZoomPercent 的默认值/范围不是 75 / 70~100（100 = 不拉近；用户 2026-10-28 定为 75）');
  }
}

// ---------------------------------------------------------------- 移动锁 × 视野（2026-10-28）
//
// 用户反复报"台钓上鱼后视野缩了一下"，查了四轮才定位：真凶是**移动锁**把
// MOVEMENT_SPEED 属性归零，而原版 AbstractClientPlayer.getFieldOfViewModifier()
// 把移动速度算进视野：f *= (speed / walkingSpeed + 1) / 2 —— 归零就是 0.5，
// 80 的视野当场变 40，再随 GameRenderer.tickFov() 每 tick 靠近一半地缓回来。
// 所以这条断言盯两件事：锁定期间**不许写属性**，必须用"位置回拉 + 速度清零"。
{
  // 本块在顶层作用域，用不到函数内部的 code()/has() 快捷方式，就地取一份。
  const strip4 = (s) => s.replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
  const mgrCode4 = strip4(read('src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java'));
  const attrZero = /MOVEMENT_SPEED[\s\S]{0,200}?setBaseValue\(0\.0\)/.test(mgrCode4);
  const pinBack = /lockPositions\.computeIfAbsent/.test(mgrCode4)
    && /teleportTo\(lock\.x, lock\.y, lock\.z\)/.test(mgrCode4)
    && /setDeltaMovement\(0\.0, 0\.0, 0\.0\)/.test(mgrCode4);
  if (attrZero) {
    fail('移动锁里还在把 MOVEMENT_SPEED 归零 —— 原版 getFieldOfViewModifier() 会因此把视野乘 0.5（80→40），'
      + '用户看到的"台钓上鱼后视野缩一下"就是这么来的');
  } else if (!pinBack) {
    fail('移动锁既没写属性、也找不到"位置回拉 + 速度清零"（lockPositions / teleportTo / setDeltaMovement）——'
      + '锁会失效，玩家能走着把鱼拽走');
  } else {
    ok('移动锁不许碰 MOVEMENT_SPEED（否则视野会被原版公式砍半）；用位置回拉 + 速度清零');
  }
}


// ---------------------------------------------------------------- 手感数值（2026-10-28）
//
// 用户四条反馈：① 上鱼后垂直方向没跟上；② 缩放回复太快；③ 抛竿时转动困难、还在跟随；
// ④ 跟随强度太低。这里把"数值层"的答案钉住，免得以后被人悄悄调回去。
{
  const cam = read('src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java')
    .replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
  const followFast = /RAMP_PER_SECOND = 8\.0f/.test(cam);
  const asym = /ZOOM_PRESS_PER_SECOND = 10\.0f/.test(cam) && /ZOOM_RELEASE_PER_SECOND = 1\.8f/.test(cam)
    && /stepAsym\(zoom, want, frameDt, ZOOM_PRESS_PER_SECOND, ZOOM_RELEASE_PER_SECOND\)/.test(cam);
  // 2026-10-28 改：停留**不再**绑 LANDING（LANDING 只有 1.25 秒，会把"停在那边"变成"1 秒后闪回"）；
  // 防漏进下一次抛竿改由"新抛竿时清空 lingerActive"负责。
}




// ---------------------------------------------------------------- lastAim 的来源（2026-10-28）
//
// 踩过的坑：浮漂实体消失后 bobberAimPos() 会退回同步包里的坐标，而那是**抛竿原点**
// （往往就在玩家脚下）⇒ ① 收尾看向错的地方；② 水平距离≈0 直接 return，角度根本不算。
{
  const cam = read('src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java')
    .replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');
  const gated = /Vec3 bob = \(followable && st\.bobberActive\(\)\) \? bobberAimPos\(event\.getPartialTick\(\)\) : null;/.test(cam);
  const ungated = /Vec3 bob = followable \? bobberAimPos\(event\.getPartialTick\(\)\) : null;/.test(cam);
  if (gated && !ungated) {
    ok('lastAim 只采信还在场的浮漂实体（实体消失后不再退回抛竿原点）');
  } else {
    fail('lastAim 采样没有用 bobberActive 把关（gated=' + gated + ' ungated=' + ungated
      + '）—— 浮漂消失后 bobberAimPos() 会退回同步包里的**抛竿原点**，收尾会看向"点击左键的地方"，'
      + '而且那个点常在脚下，水平距离≈0 会让角度直接 return');
  }
}




// ---------------------------------------------------------------- 上鱼后"真的转过去"（2026-10-28）
//
// 用户最终口径："不是视角不动，而是最终要更新**实际视角位置**到那里"。
// 所以上鱼后必须改**玩家本人的 yaw/pitch**（setYRot/setXRot），而不是只用
// event.setYaw 盖画面 —— 盖画面一停手就弹回去，那就是他一路看到的"闪回"。
{
  const cam = read('src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java')
    .replace(/\/\/[^\n]*/g, ' ').replace(/\/\*[\s\S]*?\*\//g, ' ');

  const begins = /beginTurnToLastAim\(event\.getPartialTick\(\)\)/.test(cam)
    && /Vec3 eye = mc\.player\.getEyePosition/.test(cam);
  const turnsPlayer = /mc\.player\.setYRot\(ny\);/.test(cam)
    && /mc\.player\.setXRot\(np\);/.test(cam)
    && /mc\.player\.setYHeadRot\(ny\);/.test(cam);
  const snapsExact = /mc\.player\.setYRot\(turnYaw\);/.test(cam)
    && /mc\.player\.setXRot\(turnPitch\);/.test(cam);
  const stopsAtEnd = /turning = false;/.test(cam);

  const seamless = /if \(hasAngles\) \{\s*mc\.player\.setYRot\(smoothYaw\);/.test(cam);
  if (begins && turnsPlayer && snapsExact && stopsAtEnd && seamless) {
    ok('上鱼后把玩家真实视角转到浮漂最后位置（起步先用画面当前角度对齐，无 0 帧跳变）');
  } else {
    fail('上鱼后没有"真的把视角转过去"（begin=' + begins + ' turn=' + turnsPlayer
      + ' snap=' + snapsExact + ' stop=' + stopsAtEnd + ' seamless=' + seamless
      + '）—— 只用 event.setYaw 盖画面的话，一停手就会弹回去（用户看到的"闪回"）');
  }
}


console.log('');
console.log(bad === 0 ? '✅ 相机接线正确（只改渲染视角、拉近叠在当前 FOV 上）' : '❌ 相机检查未通过（' + bad + ' 项）');
process.exitCode = bad === 0 ? 0 : 1;
