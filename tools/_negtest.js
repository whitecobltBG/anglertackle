/*
 * _negtest.js —— 造"改坏的临时副本"，供 tools/negtest.ps1 断言检查脚本会红。
 *
 * 为什么需要负向测试：本仓库出过"检查脚本永远通过"的事故 ——
 * check-gameplay-parity.js 曾在 JS 里把 Java 的算法重新实现一遍（连 Math.min(hiRaw, CAP)
 * 都照抄），等于自己证明自己。**永远绿的检查器比没有检查器更坏**：
 * 它会让人以为这件事已经被守住了。
 *
 * 做法：真实源文件**全程只读**，复制到 build/negtest/<case>/ 再改坏，
 * 然后由 PowerShell 用 ATL_ROOT 指向副本跑检查、断言退出码。
 * （不在这里 spawnSync：沙箱禁止 node 用管道捕获子进程输出，会 EPERM。）
 *
 * 用法：node tools/_negtest.js     → 然后 tools/negtest.ps1
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const OUT = path.join(ROOT, 'build', 'negtest');

// 三个检查器读到的文件的并集。
//
// ⚠ 键必须用**完整相对路径**，不能用文件名：世界生成的结构 JSON 和模板池
//   都叫 fisherman_hut.json，按文件名当键会互相覆盖 —— 第一版就是这么挂的
//   （把模板池的内容当成了结构 JSON，于是 placement 是 undefined）。
//   所以下面所有用例都通过 at.get('文件名') 取，取不到或有歧义就报错。
const FILES = [
  // 2026-10-29 音效：负例要改 sounds.json ⇒ 必须进副本（否则读真仓库那份，改坏也不红）
  'src/main/resources/assets/anglertackle/sounds.json',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModSounds.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ReelSounds.java',
  // 食用适宜度（2026-10-10）：新模块 + 两处接线**必须进副本**。
  // 为什么不进副本就等于没测：负例跑的是"真仓库里的检查器 + ATL_ROOT 指向的副本"，
  // 副本里没有这个文件，检查器就会读到真仓库那份 ⇒ 改坏了也不红（tools/dish-name.js 刚踩过）。
  'src/main/java/com/whitecobalt/anglertackle/recipe/FishPalatability.java',
  // 抛竿换图（2026-10-11）：负例要改模型 JSON 与 ClientSetup ⇒ 必须在主 FILES 里 ✓
  'src/main/resources/assets/anglertackle/models/item/wood_float_rod.json',
  'src/main/resources/assets/anglertackle/models/item/wood_float_rod_cast.json',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientSetup.java',
  'src/main/java/com/whitecobalt/anglertackle/recipe/PalatabilityLedger.java',
  'src/main/java/com/whitecobalt/anglertackle/recipe/FermentingData.java',
  'src/main/java/com/whitecobalt/anglertackle/event/BarrelFermenting.java',
  'src/main/java/com/whitecobalt/anglertackle/item/DishItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/FishChunkItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/FishItem.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/CookbookScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java',
  // 学名/中文名修正（check-fish-names.js）的负例要**改**这两份调研资料的 scientificName
  // ⇒ 必须在主 FILES 里（EXTRA 只拷贝、不进内存表，at.get 读不到）
  'fish_research/jewel_cichlid.json',
  'fish_research/electric_eel.json',
  // 板凳：check-stool-wiring 的判例要**改**这三个文件 ⇒ 必须在主 FILES 里 ✓
  'src/main/java/com/whitecobalt/anglertackle/block/StoolBlock.java',
  'src/main/java/com/whitecobalt/anglertackle/block/CushionedStoolBlock.java',
  'src/main/java/com/whitecobalt/anglertackle/entity/SeatEntity.java',
  // 构建/发布脚本的版本号来源（check-build-scripts.js 的负例要改它）
  'gradle.properties',
  // 收线声音（check-reel-sound.js 的负例要改它）
  'src/main/java/com/whitecobalt/anglertackle/client/ReelSounds.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java',
  // 版本档位政策（同一个检查器的 G 段读它）——⚠ 少了它副本里会直接抛异常，
  //   连带"未改动的副本"那条对照组假失败（踩过）
  'tools/version-policy.json',
  // S→C 的包注册与处理（check-codex / check-player-data / check-shop-ui 现在查它）
  'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java',
  // 客户端输入包（check-reel-stall.js 第②节读它：不许节流）。
  // ⚠ 以前没列进来，于是那个检查器一旦用 ATL_ROOT 跑就 ENOENT —— 也就是
  //   "它在负例里根本跑不起来"，第③节的对照组第一次跑就撞上了这件事。
  'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java',
  // 2026-10-16 修用户报的五条 bug（①③④⑤⑥）：新断言在 check-fishing-camera.js 的 [G] 组，
  //   负例要改这两个文件 ⇒ 必须登记。不登记的后果见文件头那条铁律
  //   （这条我 2026-10-15 在 QuestTiers.java 上踩过一次：锚点预检直接找不到文件）。
  'src/main/java/com/whitecobalt/anglertackle/client/hud/FishingHud.java',
  'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java',
  // 食物数值（check-food-values.js 的负例要改它）
  'src/main/java/com/whitecobalt/anglertackle/fishing/FoodValues.java',
  // 菜型效果（check-dish-effects.js）：注册表、映射逻辑、吃菜入口、策划文档
  'src/main/java/com/whitecobalt/anglertackle/effect/ModEffects.java',
  'src/main/java/com/whitecobalt/anglertackle/effect/DishEffects.java',
  'src/main/java/com/whitecobalt/anglertackle/item/DishItem.java',
  // 档位文字颜色的负例要改这几个（check-tooltip-colors.js）：
  // ⚠ EXTRA.tooltipColors 只是"拷进副本给检查器读"，**不进 at.get() 的那张表** ——
  //   要改的文件必须列在 FILES 里，否则报"找不到文件 FishChunkItem.java"。
  'src/main/java/com/whitecobalt/anglertackle/item/FishChunkItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/FishItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/ChumItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/Quality.java',
  'docs/28-菜型效果策划.md',
  // 菜谱（check-cookbook.js）的策划文档
  'docs/29-菜谱与悬浮显示.md',
  // 菜型效果的**行为**（check-dish-effect-hooks.js）：数值常量、四个事件挂点、
  // 两栖的潮涌能量、明目的扫描。少了它的后果是"对照组假失败"（读不到文件直接红）。
  'src/main/java/com/whitecobalt/anglertackle/effect/DishEffectHooks.java',
  // 明目的"报坐标 + 客户端画线"那条链（同属 check-dish-effect-hooks.js）
  'src/main/java/com/whitecobalt/anglertackle/network/OreSightPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientOreSight.java',
  'src/main/java/com/whitecobalt/anglertackle/client/render/OreSightRenderer.java',
  // 菜谱与悬浮显示（check-cookbook.js，1.2.1）
  'src/main/java/com/whitecobalt/anglertackle/item/CookbookItem.java',
  'src/main/java/com/whitecobalt/anglertackle/effect/EffectText.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/CookbookScreen.java',
  'src/main/resources/data/anglertackle/recipe/cookbook.json',
  'src/main/resources/data/anglertackle/advancement/recipes/cookbook.json',
  'src/main/resources/data/anglertackle/guide/guide.json',
  // 配方体检（debug/RecipeAudit.java）—— 只被构建读，不进副本也没关系，
  // 但留着它以后写"体检规则"的负例方便
  'src/main/java/com/whitecobalt/anglertackle/debug/RecipeAudit.java',
  // 语言文件（check-recipe-book.js 的负例要删掉一个"红烧%s"名字模板）
  'src/main/resources/assets/anglertackle/lang/zh_cn.json',
  // 食物数值表（check-food-values.js 的负例要改文档里的数字）
  'docs/27-食物数值表.md',
  'src/main/resources/assets/anglertackle/lang/en_us.json',
  // 菜配方（生成物）：手改它的负例要能通过 at.get 读到
  'src/main/resources/data/anglertackle/recipe/braised_fish/grass_carp.json',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModItems.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModEntities.java',
  // 1.2.5 高风险负例要改的（⚠ EXTRA 只负责"拷给检查器读"，要改的文件必须列在这里）
  'src/main/java/com/whitecobalt/anglertackle/registry/ModAttachments.java',
  // check-guide-numbers 新增的"/atl 子命令必须存在"断言要读它
  'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java',
  // 森罗联动负例要改它（⚠ EXTRA 只拷给检查器读，要改的必须列在 FILES 里）
  'src/main/resources/data/anglertackle/tags/item/cooking_oil.json',
  // 兼容配方负例要改它（同样：要改的文件必须列在 FILES 里）
  'src/main/resources/data/anglertackle/recipe/kaleidoscope/pot/braised_fish.json',
  'src/main/java/com/whitecobalt/anglertackle/item/PartStats.java',
  'src/main/java/com/whitecobalt/anglertackle/AnglersTackle.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientSetup.java',
  'src/main/java/com/whitecobalt/anglertackle/client/render/FishermanRenderer.java',
  'src/main/java/com/whitecobalt/anglertackle/entity/FishermanEntity.java',
  'src/main/resources/assets/anglertackle/lang/zh_cn.json',
  'src/main/resources/assets/anglertackle/lang/en_us.json',
  'src/main/resources/assets/anglertackle/models/item/fisherman_spawn_egg.json',
  'tools/vanilla-tags.json',
  'src/main/resources/data/anglertackle/tags/worldgen/biome/has_fisherman_hut.json',
  'src/main/resources/data/anglertackle/worldgen/structure/fisherman_hut.json',
  'src/main/resources/data/anglertackle/worldgen/template_pool/fisherman_hut.json',
  'src/main/resources/data/anglertackle/worldgen/structure_set/fisherman_hut.json',
  // 追踪罗盘
  'src/main/java/com/whitecobalt/anglertackle/Config.java',
  // 热重载映射（check-tuning-schema.js 读它；⚠ 少了它那条用例会 ENOENT 假失败）
  'src/main/java/com/whitecobalt/anglertackle/fishing/Tuning.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModTags.java',
  'src/main/resources/data/anglertackle/tags/worldgen/structure/fisherman_hut.json',
  // 罗盘的"始终指向"这条链路（2026-09-30 加）
  'src/main/java/com/whitecobalt/anglertackle/network/ModNetwork.java',
  // 商店规则（价格 / 解锁 / 兑换 / 限购）
  'tools/shop-rules.js',
  'src/main/resources/data/anglertackle/tags/item/fishes.json',
  'docs/18-商店价目表.md',
  // 鱼的外形（贴图/建模管线）
  'tools/fish-appearance.json',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModModelLayers.java',
  'src/main/java/com/whitecobalt/anglertackle/fish/FishVariants.java',
  'src/main/java/com/whitecobalt/anglertackle/item/FishSpawnEggItem.java',
  'src/main/java/com/whitecobalt/anglertackle/client/model/ModFishModel.java',
  'src/main/java/com/whitecobalt/anglertackle/client/render/ModFishRenderer.java',
  // 鱼线端点（竿尖锚点，2026-10-02 加）
  'src/main/java/com/whitecobalt/anglertackle/client/render/FishingLineRenderer.java',
  'src/main/java/com/whitecobalt/anglertackle/client/render/RodTipAnchor.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/item/RodItem.java',
  // 路亚竿图标的生成器（check-rod-icon-quality.js 会读它核对"竿梢收尖"的常量）
  'tools/gen-lure-rods.js',
  // 抛竿落点（check-bobber-cast.js 读这两份源码里的物理与落水判定）
  'src/main/java/com/whitecobalt/anglertackle/entity/BobberEntity.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java',
  'tools/check-comment-stripping.js',
  'tools/check-retrieve.js',
  'tools/java-source.js',
  'src/main/java/com/whitecobalt/anglertackle/network/MailPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/network/SessionSyncPayload.java',
  // 竿的「握着」模型（check-line-anchor.js 第四组断言读它们）—— 负例要改
  'src/main/resources/assets/anglertackle/models/item/rod_grip.json',
  'src/main/resources/assets/anglertackle/models/item/diamond_float_rod.json',
  // 商店价目表生成器现在要读它核对"兑换要求的品质落在该鱼种的档位区间内"。
  // 少了它，生成器直接抛异常 → 对照组全红（2026-10-02 踩过一次）。
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java',
  // 水体判定与归属表（check-water-body.js 读这两个）
  'src/main/java/com/whitecobalt/anglertackle/fishing/WaterBody.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishWaters.java',
  // 冰钓四条数值链（check-ice-fishing.js 读它）
  'src/main/java/com/whitecobalt/anglertackle/fishing/IceFishing.java',
  // 冰钓的方块与凿冰镐（同一个检查器读）
  'src/main/java/com/whitecobalt/anglertackle/item/IcePickItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/RodItem.java',
  'src/main/java/com/whitecobalt/anglertackle/block/IceHoleBlock.java',
  'src/main/java/com/whitecobalt/anglertackle/block/BrokenIceBlock.java',
  'src/main/java/com/whitecobalt/anglertackle/block/PackIceBlock.java',
  // /atl water 与 /atl pool 的取样点（check-water-body.js 第 ⑦ 节：两处必须同源）
  'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java',
  // 水域修饰的三个群系标签（用户 2026-10-08：极地 / 高寒湖 / 冷水溪流）。
  // ⚠ 少一份，check-water-body.js 第 ⑨ 节在**副本**里就 existsSync=false →
  //   "缺群系标签文件"直接报红，于是**对照组**（未改动，期望通过）假失败 ——
  //   和上面 ClientFishingState 那次是同一个坑。
  'src/main/resources/data/anglertackle/tags/worldgen/biome/ice_fishing.json',
  'src/main/resources/data/anglertackle/tags/worldgen/biome/alpine.json',
  'src/main/resources/data/anglertackle/tags/worldgen/biome/cold_streams.json',
  // 渔夫小屋定位线索（check-hut-clue.js 读这四个）
  'src/main/java/com/whitecobalt/anglertackle/item/HutClueItem.java',
  'src/main/resources/data/anglertackle/recipe/hut_clue.json',
  'src/main/resources/assets/anglertackle/models/item/hut_clue.json',
  // 村庄渔夫小屋的注入链 + 改造清单（check-hut-loot.js / check-enchantments.js 都读它们）
  'src/main/resources/data/anglertackle/loot_table/inject/village_fisher_fish.json',
  'src/main/resources/data/anglertackle/loot_modifiers/village_fisher_fish.json',
  'tools/glm-entries.js',
  // 老柯小屋的战利品表（check-hut-loot.js 要拿它和村庄那一池逐字节比对）
  'src/main/resources/data/anglertackle/loot_table/chests/fisherman_hut.json',
  // 垂纶等级（check-angler-level.js / check-guide-numbers.js 读它）
  'src/main/java/com/whitecobalt/anglertackle/player/AnglerLeveling.java',
  'src/main/java/com/whitecobalt/anglertackle/player/FishermanProgress.java',
  // 图鉴里程碑（check-guide-numbers.js 要拿它的常量复算"满收集共多少鱼币"；
  // 缺了它检查器会以 ENOENT 崩掉 —— 而崩溃在负例里表现为"红"，看着像抓到了，
  // 其实是**假通过**：2026-10-02 加 guidenum-* 那批用例时对照组就是这么红的）
  'src/main/java/com/whitecobalt/anglertackle/economy/CodexRewards.java',
  'src/main/java/com/whitecobalt/anglertackle/shop/ShopManager.java',
  // 铁竿/钻石竿的承重上限写在这个生成器的 ITEMS 表里（用户点名改过这两项）
  'tools/gen-gameplay-config.js',
  // 商店界面检查器要读它核对兑换行的品质配色（六档颜色错位那次就是它）
  'src/main/java/com/whitecobalt/anglertackle/shop/ShopMaterials.java',
  // check-exchange-special.js 还要读这两个（它核对"生成器有没有 emit 固定组件"与"生成物里有没有"）
  // ⚠ 不进副本 ⇒ read() 抛 ENOENT ⇒ **对照组先红**（2026-10-11 踩过）
  'tools/gen-shop-prices.js',
  'src/main/java/com/whitecobalt/anglertackle/shop/ShopCatalog.java',
  // 砂轮拆配件（check-grindstone.js 读这三个）
  'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java',
  // 铁砧换件退旧件（check-anvil-return.js 读它；RodModManager 已在上面）
  'src/main/java/com/whitecobalt/anglertackle/event/AnvilEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/RodModManager.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModDataComponents.java',
  // 六个附魔（check-enchantments.js 要复算数值：常量在 RodEnchants，
  // 闸门与回落点在 FishingSession，词条加成基准在 FishHabits；
  // 生成器本身也要拷进去 —— 它一缺，检查器 require 就抛异常，
  // 期望失败的用例会因此"变红"，看着像抓到了，其实和目标无关）。
  'src/main/java/com/whitecobalt/anglertackle/fishing/RodEnchants.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java',
  // 保底体力削减（check-reel-floor-drain.js）：推导在 PartComposer、
  // 装配在 RodStats、提示在 TacklePartItem、渔轮数值在 ModItems。
  // ⚠ 少一个，对照组就会 ENOENT 假失败（那次 check-reel-stall 就是这么踩的）。
  'src/main/java/com/whitecobalt/anglertackle/fishing/PartComposer.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/RodStats.java',
  'src/main/java/com/whitecobalt/anglertackle/item/TacklePartItem.java',
  // 咬钩减时（check-bite-time.js 读这两个 + FishingSession/RodItem/Config/手册）
  'src/main/java/com/whitecobalt/anglertackle/fishing/BiteTiming.java',
  'src/main/java/com/whitecobalt/anglertackle/item/BaitItem.java',
  // 多人鱼线（check-line-multiplayer.js，2026-10-10 联机实测）：判例要**改**这两个文件 ⇒ 必须在主 FILES 里 ✓
  //   · FishingLineRenderer：删掉 renderOtherPlayersLines 调用 ⇒ 多人下又看不见线 ✗
  //   · BobberEntity：ownerId() 是客户端唯一能拿到的"浮漂主人"✓（同步字段 DATA_OWNER ✓）
  'src/main/java/com/whitecobalt/anglertackle/client/render/FishingLineRenderer.java',
  'src/main/java/com/whitecobalt/anglertackle/entity/BobberEntity.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/FishHabits.java',
  // 环境（时间/天气/月相，check-environment.js 读这两个 + fish-habits.json + Config
  // + BiteTiming + FishingSession）
  'src/main/java/com/whitecobalt/anglertackle/fishing/Environment.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/EnvShare.java',
  // 环境条件表（鱼的 time/weather/moon）—— 走 EXTRA.env 拷进副本
  'tools/fish-habits.json',
  // 贴图守护的两个数据源（check-item-icons / check-fish-textures 的负例要改它们）
  '参考资料/图标指纹.json',
  '参考资料/鱼图/_baseline/arapaima.json',
  // ⚠ 配方的负例用**完整路径**（见下面 rodrecipe-extra-rod）：仓库里有 3 个同名
  //   fish_rack.json（recipe / advancement / loot_table），basename 会"有歧义"直接抛错。
  //   这条 FILES 是给"别处也要改配方"准备的，本来就在配方那一批里，别重复加。
  // 配置文件的"补新键"工具与它的数据源（check-config-sync.js 会真的跑一遍
  // tools/sync-config-file.js，所以要连 schema 一起拷进副本 —— 少一个，
  // 期望失败的用例会因为"缺文件"变红，看着像抓到了，其实和目标无关）
  'tools/sync-config-file.js',
  'tools/tuning-schema.json',
  // "有意变更过的默认值"登记表（check-live-config-matches-defaults.js 从它读旧默认值，
  // 用来区分"代码改了默认值而 toml 压着旧值"与"玩家自己调过参"）
  'tools/patch-config-changed-defaults.js',
  'tools/gen-enchantments.js',
  // 打窝料抛出（check-chum-throw.js 读实体 + 渲染器）
  'src/main/java/com/whitecobalt/anglertackle/entity/ChumEntity.java',
  'src/main/java/com/whitecobalt/anglertackle/client/render/ChumRenderer.java',
  // 打窝链路（check-chum.js）：判定端（ChumManager）与全服名额表（ChumRegistry）
  // ⚠ 两个都必须进 FILES：前者要**改**（把 owner 过滤加回去 = 共享被改回独享），
  //   后者要存在（否则对照组直接因为读不到文件变红 —— 假失败）
  'src/main/java/com/whitecobalt/anglertackle/fishing/ChumManager.java',
  'src/main/java/com/whitecobalt/anglertackle/fishing/ChumRegistry.java',
  // 大鱼公告（check-big-catch-announce.js）：这两份都要**改**
  //（去掉闸门 / Codec 改成 fieldOf）⇒ 不在 FILES 里的话 at.get 直接抛异常，
  // 那 4 条负例一条都跑不起来（症状又是"造副本失败"）✗
  'src/main/java/com/whitecobalt/anglertackle/fishing/BigCatchAnnounce.java',
  'src/main/java/com/whitecobalt/anglertackle/player/PlayerFishingData.java',
  // ⚠ 公告检查器还要**现算**红/金两档的价格区间（把"5050 是什么意思"钉死）⇒
  //   它必须读得到 CoinPricing，否则对照组 ENOENT 变红（2026-10-11 真踩过）
  'src/main/java/com/whitecobalt/anglertackle/economy/CoinPricing.java',
  // 配置修法自检要用到它（工具不在副本里 ⇒ 断言"存在"会假红）
  'tools/reset-release-defaults.js',
  // 四个界面的自适应（check-ui-fit.js 读 UiLayout + 四个 Screen）
  'src/main/java/com/whitecobalt/anglertackle/client/screen/UiLayout.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/RodModScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/ShopScreen.java',
  // 配方书解锁：配方与进度各取一个进 FILES（要给用例改），其余的走 EXTRA.recipeAdv
  'tools/gen-recipe-advancements.js',
  'src/main/resources/data/anglertackle/recipe/fish_rack.json',
  'src/main/resources/data/anglertackle/advancement/recipes/fish_rack.json',
  // 料理物品（check-kitchen-items.js 的负例要改这几份）：
  //   · 三条配方 JSON（盐 / 芥末 / 鱼刀）—— 配料被改掉就得红
  //   · 鱼刀类（耐久常量）与调料类（tooltip 键拼法）
  //   · 图标指纹表（上面已经有了，靠 basename 取）
  'src/main/resources/data/anglertackle/recipe/fish_knife.json',
  'src/main/resources/data/anglertackle/recipe/salt.json',
  'src/main/resources/data/anglertackle/recipe/mustard.json',
  'src/main/java/com/whitecobalt/anglertackle/item/FishKnifeItem.java',
  'src/main/java/com/whitecobalt/anglertackle/item/SeasoningItem.java',
  // 菜品贴图覆盖检查（check-dish-coverage.js）读策划文档 + 生成器的 ITEMS 声明；
  // 两个负例要改它们（把菜名改成没图的做法 / 声明一张不存在的图）
  'docs/25-鱼料理与配料方案.md',
  // 学名/中文名修正 7 条（check-fish-names.js）：它要把策划文档里那张 7 行表也数一遍，
  // 少拷这一份 ⇒ 副本里读不到表 ⇒ 对照组与负例都会因为"缺文档"变红（假通过）
  'docs/料理适配策划案.md',
  'tools/gen-dish-icons.js',
  // 菜配方的生成器：check-dish-recipes.js 与 check-recipe-book.js 都会 require 它
  // （少了它 → 对照组因为缺文件变红，看着像"抓到了变异"，其实什么都没验证）
  'tools/gen-dish-recipes.js',
  // 烹饪线（切块 / 加热三件套 / 榨油 / 菜型配方）—— check-cutting / check-cooking /
  // check-dish-recipes 三个检查器的负例要改这些文件
  // ⚠ 要**改**的配方 JSON 必须进 FILES（EXTRA 只拷贝、不进内存表，at.get 读不到）
  'src/main/resources/data/anglertackle/recipe/fish_chunk_to_fish_chunk_cooked.json',
  'src/main/java/com/whitecobalt/anglertackle/recipe/CuttingRecipe.java',
  'src/main/java/com/whitecobalt/anglertackle/recipe/DishRecipe.java',
  'src/main/java/com/whitecobalt/anglertackle/recipe/DataCookingRecipe.java',
  // 营火那条单独一个文件：它必须继承原版 CampfireCookingRecipe（JEI 会强转）
  'src/main/java/com/whitecobalt/anglertackle/recipe/ChunkCampfireRecipe.java',
  'src/main/java/com/whitecobalt/anglertackle/recipe/OilPressingRecipe.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModRecipes.java',
  // 附魔 JSON 也各取几个进 FILES（要留给用例手改，模拟"改了生成物"）；
  // 其余与三张标签、durability 标签走 EXTRA.ench。
  // ⚠ EXTRA 先拷、FILES 后写（见下面 extraPats 的注释），所以这里不会被覆盖。
  // 等级阶梯那几个用例要**同时**改生成器和对应的 JSON（两边一致 → 漂移检查绿），
  // 这样红的就只可能是阶梯断言本身，而不是"生成物漂移"这个无关理由。
  'src/main/resources/data/anglertackle/enchantment/rescue.json',
  'src/main/resources/data/anglertackle/enchantment/reinforced.json',
  'src/main/resources/data/anglertackle/enchantment/drag.json',
  'src/main/resources/data/anglertackle/enchantment/high_quality_lure.json',
  // 宝箱专属附魔的战利品注入链（check-enchantments.js 的"宝箱阶梯"一节读这三个）。
  // 同样要留出"生成器 + 生成物一起改"的用法，否则红的是漂移、不是那条断言。
  'src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json',
  'src/main/resources/data/anglertackle/loot_modifiers/high_quality_lure_book.json',
  'src/main/resources/data/anglertackle/loot_table/inject/high_quality_lure_book.json',
  // 合成栏装配件（check-craft-assembly.js 读这四个）
  'src/main/java/com/whitecobalt/anglertackle/recipe/RodAssemblyRecipe.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModRecipes.java',
  'src/main/resources/data/anglertackle/recipe/rod_assembly.json',
  // 手册（"三种装配方式"那一页是 special 配方唯一的发现途径，检查器会核对它在不在）
  'src/main/resources/data/anglertackle/guide/guide.json',
  // 教程书的兜底实现（check-guide-sync.js 要断言"内容只有一份、没有硬编码章节"）
  'src/main/java/com/whitecobalt/anglertackle/guide/GuideData.java',
  // 输入法锁定小工具的启动脚本（check-ps1-encoding.js 现在全仓库递归扫 .bat，
  // 这几个是双击运行的那种 —— 混进中文会把下一行命令吃掉，必须一起守）
  '工具/输入法锁定/编译.bat',
  '工具/输入法锁定/开机自启.bat',
  '工具/输入法锁定/取消开机自启.bat',
  // 构建脚本自身：check-ps1-encoding.js 的"BOM 被吃掉"这条断言要拿它做负例
  // （它带 BOM，且是命令行开关最怕丢 BOM 的那个 —— 丢了 -NoDeploy 会被静默忽略）
  'tools/build-deploy.ps1',
  // ⚠ 发布脚本也必须列在**这里**（而不是只挂在 EXTRA.buildscripts 上）：
  //   EXTRA 只拷贝、不进内存表，`at.get()` 读不到它 ——
  //   2026-10-08 加"发布说明不许写死版本/鱼种数"两条负例时就踩了：
  //   造副本阶段直接抛 `Error: 找不到文件 make-release.ps1`，**整个负例批次中止** ✗
  //   （而闸门的报错只写"造副本失败"，不说是哪个用例 —— 得手动跑 _negtest.js 才看得到）。
  'tools/make-release.ps1',
  // 调参器的三件套：检查器要跑**生成物**（调参器.html）并与 tuner.html 逐字节比对，
  // 无头执行还要 require('./browser-shim.js')。
  // ⚠ 这两条以前都不在 FILES 里 ⇒ 副本里 browser-shim.js 根本不存在 ⇒
  //   check-tuning-tool.js **一个负例都没有**（那一整块功能从来没被证伪过 ✗）。
  'tools/browser-shim.js',
  'tuner.html',
  // ⚠ 调参器.html 也一样：它原先只挂在 EXTRA.selfConsist 里（只拷贝、不进内存表），
  //   而负例要**改**它 ⇒ `at.get('调参器.html')` 抛"找不到文件"，整个批次中止 ✗。
  //   记住这条规律：**EXTRA 是给检查器读的，FILES 才是给用例改的**。
  '调参器.html',
  // 进度看板（check-progress-doc.js）：生成器与生成物都必须在 FILES 里 ——
  //   负例要**改** gen-progress.js（把数量写死）与 docs/进度.md（去掉"勿手改"标记）。
  // ⚠ 2026-10-11 实测：gen-progress.js 的表头原先写死 "119 检查器"，而当时是 130 个，
  //   且**没有任何检查在看它** —— 看板的数字一直在撒谎。
  'tools/gen-progress.js',
  'docs/进度.md',
  // 称号系统（check-titles.js，2026-10-11）：检查器要读**这一整套** ——
  //   表 / 授予逻辑 / 聊天 / 附件 / 文档 / 两种语言。少一份就是"检查组件的假失败"✗
  'src/main/java/com/whitecobalt/anglertackle/title/TitleDefs.java',
  'src/main/java/com/whitecobalt/anglertackle/title/Titles.java',
  'src/main/java/com/whitecobalt/anglertackle/event/TitleChatEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/player/PlayerTitles.java',
  'src/main/java/com/whitecobalt/anglertackle/registry/ModAttachments.java',
  'docs/38-称号系统策划案.md',
  'src/main/resources/assets/anglertackle/lang/zh_cn.json',
  'src/main/resources/assets/anglertackle/lang/en_us.json',
  // 负例执行器自身（check-negtest-runner.js，2026-10-12）：这两个文件是"检查器能否被证伪"
  // 这条链上的**执行者** —— 它们坏掉的症状是"看起来全绿"，所以也得有负例盯着 ✓
  'tools/_negrun.js',
  'tools/negtest.ps1',
  // ⚠ 2026-10-12：`tools/_negtest.js` **自己**也要进副本 —— 新增的四条断言
  //   （硬链接写穿防护 / 真源码指纹复核 / 分片清单顺序 / 子进程不开管道）读的就是它。
  //   不进副本的话，check-negtest-runner.js 在副本里读不到它 ⇒ 对照组先红，
  //   而闸报出来的是那句误导性的存在漏检（这个坑今天已经踩过两次）。
  'tools/_negtest.js',
  // 邮件箱（check-mail.js，2026-10-12）：整条链都要进副本 —— 数据、服务、附件、指令、配置。
  // ⚠ 少一份的后果是"检查组件的假失败"（读不到文件直接红），不是"跑不过"。
  'src/main/java/com/whitecobalt/anglertackle/player/MailBox.java',
  'src/main/java/com/whitecobalt/anglertackle/player/MailEntry.java',
  'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java',
  'src/main/java/com/whitecobalt/anglertackle/event/FirstJoinEvents.java',
  // 邮箱界面（check-mail.js 的 M2 组要读它；负例还要改它）
  'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java',
  // 称号**界面**（2026-10-12 第二阶段：用户"称号佩戴在哪，很难发现。"）——
  //   ⚠ 这一批是**踩过一次**才补的：新写的源码文件不会自动进副本，
  //   而 check-titles.js 的界面组要读它们、负例还要**改**它们 ⇒
  //   第一版跑闸时直接抛"找不到文件 TitleScreen.java"，闸报的是那句极具误导性的
  //   "[负向测试未通过：存在漏检的检查脚本]"（AGENTS.md 里记过这个坑：
  //   负例报红要先分清"某条用例不符"还是"整段没跑起来"）。
  //   ⇒ 规律：**新加一个被检查器读（或被负例改）的文件，就必须加进 FILES**，
  //     否则对照组会先红（假失败），而不是"跑不过"。
  'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientTitles.java',
  'src/main/java/com/whitecobalt/anglertackle/network/TitlePayload.java',
  'src/main/java/com/whitecobalt/anglertackle/network/TitleActionPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/title/TitleSnapshot.java',
  // 退出世界清缓存那一段（check-titles.js 的界面组要读它；负例还要改它）
  'src/main/java/com/whitecobalt/anglertackle/event/CoinSyncEvents.java',
  // 联赛（check-league.js，2026-10-12 用户"继续推进" ⇒ docs/37 L2–L4）：整条链都要进副本。
  // ⚠ 同一条规律：**新加一个"被检查器读或被负例改"的文件，就必须进 FILES**，
  //   否则对照组先红（假失败），而闸报出来的是那句误导性的"存在漏检的检查脚本"。
  'src/main/java/com/whitecobalt/anglertackle/league/League.java',
  'src/main/java/com/whitecobalt/anglertackle/league/LeagueData.java',
  'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java',
  // 联赛积分（`docs/39` P1/P2）：check-league 读它、负例还要**改**它 ⇒ 必须进 FILES
  // （不进的话副本里没这个文件，预检会报"找不到文件"、而闸里表现为对照组假失败 ✗）
  'src/main/java/com/whitecobalt/anglertackle/league/LeaguePoints.java',
  // 联赛商店（`docs/39` P4）：check-league 的 K 段要读它（页签/点击/tab 下标）
  // ⇒ 不进清单就是"对照组先红"（2026-10-13 实测栽在这条上，第二次同源）
  'src/main/java/com/whitecobalt/anglertackle/network/ShopActionPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/mail/MailActions.java',
  // 退件兜底（2026-10-12 M4）：check-anvil-return / check-grindstone 都读它的 drop 兜底，
  // 负例还要**改**它 ⇒ 必须进 FILES（否则对照组先红、而闸报的是"存在漏检的检查脚本"）。
  'src/main/java/com/whitecobalt/anglertackle/mail/MailReturns.java',
  // M4 的三处改造点（check-codex / check-mail / check-anvil-return 都要读）
  'src/main/java/com/whitecobalt/anglertackle/economy/CodexRewards.java',
  'src/main/java/com/whitecobalt/anglertackle/event/AnvilEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/shop/ShopManager.java',
  'src/main/java/com/whitecobalt/anglertackle/leaderboard/Leaderboard.java',
  'src/main/java/com/whitecobalt/anglertackle/leaderboard/LeaderboardHooks.java',
  'src/main/java/com/whitecobalt/anglertackle/event/ServerEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/player/FishermanData.java',
  'docs/37-联赛功能策划案.md',
  // 联赛积分与赛季制（`docs/39`，2026-10-13 P1/P2）：check-league 的 I 段要拿它**逐条对账**
  // ⇒ 不进清单的话副本里读不到、对照组（league-ok）先红 ✗
  // （实测就栽在这：闸报的是"存在漏检的检查脚本"，但真凶是副本缺文件 —— 与 §那两条坑同源）
  'docs/39-联赛赛季与积分策划案.md',
  // 比赛（check-contest.js，2026-10-12 docs/35 M2）：同上，新文件必须进 FILES，
  // 否则对照组先红、而闸报的是那句误导性的"存在漏检的检查脚本"。
  'src/main/java/com/whitecobalt/anglertackle/contest/Contest.java',
  'src/main/java/com/whitecobalt/anglertackle/contest/ContestData.java',
  'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java',
  'src/main/java/com/whitecobalt/anglertackle/leaderboard/RankSnapshot.java',
  // 部署护栏（check-kaleidoscope-compat.js 的 I 组，2026-10-12 用户开不了游戏那次）：
  //   ⚠ 这个 toml 原先只挂在 EXTRA.kaleidoscope 上（只拷贝、不进内存表）——
  //   而新负例要**改**它，所以必须列进 FILES（AGENTS.md 那条规律：EXTRA 是给检查器读的，
  //   FILES 才是给用例改的）。少了它造副本阶段会直接抛"找不到文件 neoforge.mods.toml" ✗。
  'src/compat/resources/META-INF/neoforge.mods.toml',
  // 头顶名牌（check-titles.js 的 J 组，2026-10-13 §2.1）：四个新文件 +
  //   ModNetwork / ClientPayloadHandlers / CoinSyncEvents 都已在上面（Titles.java 更早就在）。
  //   ⚠ 同一条规律：新加一个"被检查器读或被负例改"的文件，就必须进 FILES ——
  //   不进的话副本里没有它，检查器 read() 直接抛 ⇒ **对照组先红**（假失败），
  //   而闸报出来的是那句误导性的"存在漏检的检查脚本"。
  'src/main/java/com/whitecobalt/anglertackle/network/TitleNameplatePayload.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientNameplates.java',
  'src/main/java/com/whitecobalt/anglertackle/client/TitleNameplateEvents.java',
  'src/main/java/com/whitecobalt/anglertackle/event/TitleNameplateSync.java',
  // 委托（check-quest.js，2026-10-14；策划 docs/40）：这三个新文件**用例要改**，
  //   所以必须进 FILES（同上面那条规律：不进 FILES ⇒ 副本里没有它 ⇒ 对照组先红）。
  'src/main/java/com/whitecobalt/anglertackle/player/QuestData.java',
  // ⚠ check-quest.js 的 F 组读 PlayerData（三个访问器），**检查器读的文件必须全在 FILES** ——
  //   少一个的后果不是"少一条用例"，而是**对照组直接崩**（副本里读不到 ⇒ ENOENT ⇒ exit 1），
  //   表现成"quest-ok 期望 0 实际 1"，看着像检查器坏了。2026-10-14 第十次跑闸当场踩到
  //   （这个缺失从 Q1 就埋下了，因为那几轮整套负例一直没能跑通）。
  'src/main/java/com/whitecobalt/anglertackle/player/PlayerData.java',
  'src/main/java/com/whitecobalt/anglertackle/quest/QuestDef.java',
  'src/main/java/com/whitecobalt/anglertackle/quest/QuestPool.java',
  'src/main/java/com/whitecobalt/anglertackle/quest/QuestService.java',
  // Q2/Q3（上鱼推进 + 活跃度，2026-10-14 第二轮）：判定表要能被用例改
  //   （LeaderboardHooks / MailScreen / ModItems / FishingSession 本来就在 FILES 里，不重复登记）
  'src/main/java/com/whitecobalt/anglertackle/quest/QuestGoal.java',
  // ⚠ 2026-10-15 补登记：活跃度的**四档阈值表**已从 `QuestService.TIER_AT` 抽到 `QuestTiers.AT`。
  //   这个文件不进副本的后果有两条，当天都真实发生了：
  //   ① 负例 `quest-activity-tier` 改的是副本，副本里没这个文件 ⇒ 改不到真源码 ⇒ **改坏了也不红**；
  //   ② 锚点预检直接报「找不到文件 QuestTiers.java」⇒ **整条负例阶段停摆**（不是某条用例不符）。
  'src/main/java/com/whitecobalt/anglertackle/quest/QuestTiers.java',
  // 成就系统（docs/41 §7 A1，2026-10-15）：check-achievement.js 要读它、负例还要改它 ⇒
  // 不进副本的后果与上面 QuestTiers 那两条一模一样（改到副本外 / 锚点预检直接停摆）。
  'src/main/java/com/whitecobalt/anglertackle/player/AchievementData.java',
  // A1 的发放与定义表（2026-10-15 补登记）：check-achievement.js 的 [C]/[D]/[E] 组要读这两个，
  // 负例还要改它们（"id 没有语言键""指令没接进 register"两条都靠改这里）。
  'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementDef.java',
  // A4 面板（2026-10-15 晚）：check-achievement.js 的 [H] 组要**读**这三个文件才对得上，
  // 读不到就会在副本里抛错 ⇒ 连对照组都红（施工单 §5-4 点名的三个，别漏）。
  'src/main/java/com/whitecobalt/anglertackle/network/AchievementPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientAchievements.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/AchievementScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java',
  // Q4（面板 + 三个入口，2026-10-14 第三轮）：三个新文件要能被用例改
  'src/main/java/com/whitecobalt/anglertackle/network/QuestPayload.java',
  'src/main/java/com/whitecobalt/anglertackle/client/screen/QuestScreen.java',
  'src/main/java/com/whitecobalt/anglertackle/client/ClientQuests.java',
  'src/main/java/com/whitecobalt/anglertackle/event/QuestEvents.java',
];

// 只有部分用例需要的额外文件（带 * 通配），例如 21 份调研资料与 21 张贴图。
// 不放进 FILES 是因为它们有 40 多个文件、且大多数用例用不到 ——
// 每个用例都拷一份会让负向测试白白多写十几 MB。
//
// 覆盖这三个检查器的用例会**自动**带上 EXTRA.fish（见下面的 extraPats），
// 用例里写不写 extra 都一样。
const FISH_CHECKERS = new Set([
  'check-spawn-egg.js', 'check-fish-model.js', 'check-fish-appearance.js',
  // 学名/中文名修正 7 条要读 fish_research/<id>.json 的 nameZh / scientificName
  // ⚠ 漏拷 ⇒ 副本里那 7 份调研资料全缺 ⇒ 负例与对照组都会因为"读不到资料"变红（假通过）
  'check-fish-names.js',
]);

// 环境检查器除了那几个源码文件，还要读 **tools/fish-habits.json**（条件表）。
// ⚠ 少拷它的后果是"对照组假失败"：env-ok 会因为读不到 json 而红，
//   看起来像"这一组负例都抓到了变异"，其实什么都没验证。
const ENV_CHECKERS = new Set(['check-environment.js']);
// 图鉴链检查器要**整棵 java 树** + 语言/模型/配方/标签 ⇒ 单独一组 EXTRA。
// ⚠ 漏拷的后果是它 read() 抛异常，**对照组也红** ⇒ 负例"看起来都抓到了"，其实全废 ✗
const CODEX_CHECKERS = new Set(['check-codex.js']);
// 同理，竿图标对 check-line-anchor.js 是**必需**的：它是"负例期望失败"的检查器，
// 少一张图标会让它因为别的原因变红 —— 于是负例**假通过**（看着像"变异被抓到了"）。
// 所以这里也一律自动带上，不靠用例自己写 extra。
const ROD_ICON_CHECKERS = new Set(['check-line-anchor.js', 'check-rod-icon-quality.js']);
// 配方书解锁检查器要读**全部**配方与进度文件；漏拷的后果同样是"负例假通过"
// （期望失败的用例会因为缺文件而红，看着像抓到了，其实和目标无关）。
const RECIPE_ADV_CHECKERS = new Set(['check-recipe-advancements.js']);
// 附魔检查器会把**全部**附魔 JSON、竿的物品标签、两张原版出处标签和
// #minecraft:enchantable/durability 逐字节与生成器比一遍；少拷一个文件，
// 期望失败的用例就会因为"缺文件"变红（假通过）。
const ENCH_CHECKERS = new Set(['check-enchantments.js']);
// 料理物品检查器要读：配方 JSON、图标指纹表、策划文档、两个新物品类。
// ⚠ 漏拷的后果同样是"负例假通过"（期望失败的用例会因为缺文件而红，看着像抓到了）。
const KITCHEN_CHECKERS = new Set(['check-kitchen-items.js']);
// 烹饪线检查器一律带 EXTRA.recipes（配方 JSON + 配方书进度 + 鱼块数据类 + 物品标签）
// ⚠ 2026-10-11 补 `check-recipe-advancements.js`：它原来不在这个集合里 ⇒ 它的副本**不复制配方**
//   ⇒ 以前一直没事，只是因为**老配方不引用标签**；新加的 `chum_meat_from_raw_chunks.json`
//   引用了 `#anglertackle:raw_fish_chunk`，副本里没有那个标签文件，于是**对照组假失败**
//   （缺文件也会红，看着像抓到了变异，其实什么都没验证）。
const RECIPE_CHECKERS = new Set(['check-cutting.js', 'check-cooking.js', 'check-dish-recipes.js',
  'check-recipe-advancements.js']);
// 抛竿换图检查器要读**全部 10 个台钓竿模型**（5 主 + 5 cast）⇒ 单独一组 EXTRA 通配复制，
// 否则副本里缺模型，**对照组**就会先红（假失败）。ClientSetup 也在 FILES 里（负例要改它）。
const RODCAST_CHECKERS = new Set(['check-rod-cast-model.js']);
// 配方书检查器还要 tags/item（它要判"#anglertackle:fishes 这个标签文件在不在"）
const BOOK_CHECKERS = new Set(['check-recipe-book.js']);
// 构建/发布脚本检查器要两个 .ps1 与 gradle 展开后的 mods.toml（见 EXTRA.buildscripts）
const BUILDSCRIPT_CHECKERS = new Set(['check-build-scripts.js']);
// 菜型效果检查器要那 11 张 HUD 图标（自己画的，在 textures/mob_effect/ 下）
const EFFECT_CHECKERS = new Set(['check-dish-effects.js']);
// 效果**行为**检查器（check-dish-effect-hooks.js）要读的源码面：
//   DishEffectHooks 自己在 FILES 里，另外三个是它的接入点（钓鱼链两个 + 环境快照）。
// ⚠ 少拷的后果是"对照组假失败" —— 读不到文件直接红，看着像抓到了变异，其实什么都没验证。
const EFFECT_HOOK_CHECKERS = new Set(['check-dish-effect-hooks.js']);
// 菜谱检查器（check-cookbook.js）要读：物品注册、网络注册、模型/贴图、语言文件、
// 配方与进度、教程 json、FoodValues。少拷任何一份都会变成"对照组假失败"。
const COOKBOOK_CHECKERS = new Set(['check-cookbook.js']);
// 档位文字颜色检查器（check-tooltip-colors.js）要读：六个带 tooltip 的物品类、
// Quality 自己、菜谱界面（三档标签）、策划文档。
const TOOLTIP_COLOR_CHECKERS = new Set(['check-tooltip-colors.js']);
// 四个"守用户验收物、却一直没有负例"的高风险检查器（1.2.5 工程债）：
//   · check-over-capacity    —— 要 FishSpecies（在 FILES 里）
//   · check-player-data      —— 要附件注册与玩家数据那几个类
//   · check-self-consistency —— 要列贴图/models/lang 目录（它自己 readdir）
//   · check-dead-fields      —— 它 walk 整棵 java 树，副本里必须有一整棵（否则对照组就红）
const OVERCAP_CHECKERS = new Set(['check-over-capacity.js']);
const PLAYERDATA_CHECKERS = new Set(['check-player-data.js']);
const SELFCONSIST_CHECKERS = new Set(['check-self-consistency.js']);
const DEADFIELDS_CHECKERS = new Set(['check-dead-fields.js']);
// 森罗物语联动（check-kaleidoscope-compat.js）：要读我们的标签 + 两个命名空间下的标签文件
// + docs/30（策划文档）。少了它就"对照组假失败"。
const KALEIDO_CHECKERS = new Set(['check-kaleidoscope-compat.js']);
const EXTRA = {
  // 烹饪线的数据面：配方 JSON + 配方书进度 + 鱼块数据类
  // （check-cutting / check-cooking / check-dish-recipes 三个检查器都要读；
  //  少了它的后果是"对照组假失败"—— 缺文件也会红，看着像抓到了变异，其实什么都没验证）
  recipes: [
    'src/main/resources/data/anglertackle/recipe/**/*.json',
    'src/main/resources/data/anglertackle/advancement/recipes/**/*.json',
    // ⚠ 2026-10-11：**配方会引用物品标签**（例如腥味肉窝那条引用了 `#anglertackle:raw_fish_chunk`）。
    //   副本里没有标签文件 ⇒ 检查器报"引用了不存在的标签"⇒ 对照组假失败。
    'src/main/resources/data/anglertackle/tags/item/**/*.json',
    'src/main/java/com/whitecobalt/anglertackle/fishing/FishChunkData.java',
  ],
  // 抛竿换图：把 10 个台钓竿模型与 10 张贴图**逐个列全**。
  // ⚠ 别用 `*_float_rod*.json` 这种"名字中间带通配"的写法 —— 那套 expandExtra 只支持
  //   `/**/` 递归通配，中间通配不生效 ⇒ 副本里会缺文件 ⇒ **对照组假失败**（2026-10-11 踩过）。
  rodCast: [
    'src/main/resources/assets/anglertackle/models/item/wood_float_rod.json',
    'src/main/resources/assets/anglertackle/models/item/iron_float_rod.json',
    'src/main/resources/assets/anglertackle/models/item/gold_float_rod.json',
    'src/main/resources/assets/anglertackle/models/item/diamond_float_rod.json',
    'src/main/resources/assets/anglertackle/models/item/netherite_float_rod.json',
    'src/main/resources/assets/anglertackle/models/item/wood_float_rod_cast.json',
    'src/main/resources/assets/anglertackle/models/item/iron_float_rod_cast.json',
    'src/main/resources/assets/anglertackle/models/item/gold_float_rod_cast.json',
    'src/main/resources/assets/anglertackle/models/item/diamond_float_rod_cast.json',
    'src/main/resources/assets/anglertackle/models/item/netherite_float_rod_cast.json',
    'src/main/resources/assets/anglertackle/textures/item/wood_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/iron_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/gold_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/diamond_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/netherite_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/wood_float_rod_cast.png',
    'src/main/resources/assets/anglertackle/textures/item/iron_float_rod_cast.png',
    'src/main/resources/assets/anglertackle/textures/item/gold_float_rod_cast.png',
    'src/main/resources/assets/anglertackle/textures/item/diamond_float_rod_cast.png',
    'src/main/resources/assets/anglertackle/textures/item/netherite_float_rod_cast.png',
    'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java',
  ],
  // 环境条件表（time/weather/moon）。它同时在 FILES 里（环境负例要改它），
  // 这里再列一次是为了让别的检查器自动带上它 —— 少拷的后果是"对照组假失败"。
  env: [
    'tools/fish-habits.json',
  ],
  // 图鉴链检查器（check-codex.js）会把**整棵 java 树**读进来（"记账只有一处写入点"
  // 必须全树扫），还会读语言文件、模型、配方与 fishes 标签。
  // ⚠ 少拷的后果不是"跑不过"，而是它 `read()` 直接抛异常 ⇒ **对照组假失败**
  //   （看着像"负例都抓到了"，其实什么都没验证 —— 本仓库踩过同类的坑）。
  codex: [
    'src/main/java/**/*.java',
    'src/main/resources/assets/anglertackle/lang/*.json',
    'src/main/resources/assets/anglertackle/models/item/codex_book.json',
    // ⚠ 贴图也必须带：检查器要断言"模型 layer0 指向的贴图真的在磁盘上"。
    //   我第一次漏了它 ⇒ **对照组**因为这行断言红了（"指向不存在的贴图"），
    //   而它跟变异毫无关系 —— 正是 AGENTS.md 里写的"对照组假失败"✗。
    'src/main/resources/assets/anglertackle/textures/item/codex_book.png',
    'src/main/resources/data/anglertackle/recipe/codex_book.json',
    'src/main/resources/data/anglertackle/tags/item/fishes.json',
  ],
  // 附魔的数据面（全部由 tools/gen-enchantments.js 生成）
  ench: [
    'src/main/resources/data/anglertackle/enchantment/*.json',
    'src/main/resources/data/anglertackle/tags/item/enchantable/rod.json',
    'src/main/resources/data/minecraft/tags/enchantment/*.json',
    'src/main/resources/data/minecraft/tags/item/enchantable/durability.json',
  ],
  fish: [
    'fish_research/*.json',
    'src/main/resources/assets/anglertackle/textures/entity/fish/*.png',
    // 21 个刷怪蛋模型：check-fish-model.js 的"生成物同步"与 check-spawn-egg.js 的
    // "有模型 json" 都会读它们，缺了就变成对照组的假失败。
    'src/main/resources/assets/anglertackle/models/item/*_spawn_egg.json',
  ],
  // 10 张竿图标：check-line-anchor.js 会从 PNG 里**重新量一遍竿尖像素**，
  // 再和 RodTipAnchor.java 里的常量比对。缺了图标它会直接报"找不到图标"。
  rod: [
    'src/main/resources/assets/anglertackle/textures/item/*_float_rod.png',
    'src/main/resources/assets/anglertackle/textures/item/*_lure_rod.png',
  ],
  // 配方与解锁进度：check-recipe-advancements.js 要把**每一条**都逐字节比一遍
  recipeAdv: [
    'src/main/resources/data/anglertackle/recipe/**/*.json',
    'src/main/resources/data/anglertackle/advancement/recipes/**/*.json',
  ],
  // 料理物品（调料 / 鱼刀）：配方、图标、模型、指纹表、策划文档、两个物品类。
  // 图标是 PNG，**必须整体复制**（不能读成 utf8）—— expandExtra 走的是 copyFileSync。
  kitchen: [
    'src/main/resources/data/anglertackle/recipe/**/*.json',
    'src/main/resources/assets/anglertackle/models/item/*.json',
    'src/main/resources/assets/anglertackle/textures/item/*.png',
    'src/main/java/com/whitecobalt/anglertackle/item/FishKnifeItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/SeasoningItem.java',
    '参考资料/图标指纹.json',
    'docs/25-鱼料理与配料方案.md',
  ],
  // 配方书（check-recipe-book.js）：配方 JSON + 解锁进度 + 物品标签。
  // ⚠ 少拷 tags/item/*.json 的后果是"对照组假失败"：切块/整条油炸引用的
  //   #anglertackle:fishes 会被判成"标签文件不存在"，看着像抓到了变异，其实什么都没验证。
  book: [
    'src/main/resources/data/anglertackle/recipe/**/*.json',
    'src/main/resources/data/anglertackle/advancement/recipes/**/*.json',
    'src/main/resources/data/anglertackle/tags/item/*.json',
  ],
  // 构建/发布脚本（check-build-scripts.js）：两个脚本 + gradle 展开后的 mods.toml。
  // ⚠ mods.toml 是**构建产物**（build/resources/main/…），它一缺，那条"jar 里的版本
  //   对不对"的断言就会静默跳过 —— 于是负例看着是绿的，其实什么都没验证。
  buildscripts: [
    'tools/build-deploy.ps1',
    'tools/make-release.ps1',
    'build/resources/main/META-INF/neoforge.mods.toml',
  ],
  // 菜型效果（check-dish-effects.js）：11 张 HUD 图标。
  // ⚠ 少拷的后果是"对照组假失败"（找不到图标会被判成"没画"）——看着像抓到了变异，其实什么都没验证。
  effects: [
    'src/main/resources/assets/anglertackle/textures/mob_effect/*.png',
  ],
  // 效果行为（check-dish-effect-hooks.js）：它的接入点分布在钓鱼链与环境快照里。
  // DishEffectHooks.java 自己走 FILES（负例要改它），这里补的是"被它读、但不改"的那三份。
  effectHooks: [
    'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java',
    'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java',
    'src/main/java/com/whitecobalt/anglertackle/fishing/Environment.java',
  ],
  // 菜谱（check-cookbook.js）：界面/物品/配方/进度/模型/图标/教程/食物数值。
  // ⚠ 少拷的后果同样是"对照组假失败"（读不到文件直接红，看着像抓到了变异）。
  cookbook: [
    'src/main/java/com/whitecobalt/anglertackle/client/screen/CookbookScreen.java',
    'src/main/java/com/whitecobalt/anglertackle/client/screen/TextWidget.java',
    'src/main/java/com/whitecobalt/anglertackle/client/screen/UiLayout.java',
    'src/main/java/com/whitecobalt/anglertackle/network/CookbookPayload.java',
    'src/main/java/com/whitecobalt/anglertackle/network/CookbookRequestPayload.java',
    'src/main/resources/assets/anglertackle/models/item/cookbook.json',
    'src/main/resources/assets/anglertackle/textures/item/cookbook.png',
    'src/main/resources/data/anglertackle/guide/guide.json',
    'src/main/resources/data/anglertackle/advancement/recipes/cookbook.json',
  ],
  // 档位文字颜色（check-tooltip-colors.js）：六个带 tooltip 的物品类 + Quality + 菜谱界面。
  // ⚠ 少拷的后果同样是"对照组假失败"。
  tooltipColors: [
    'src/main/java/com/whitecobalt/anglertackle/item/Quality.java',
    'src/main/java/com/whitecobalt/anglertackle/item/FishChunkItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/FishItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/RodItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/ChumItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/BaitItem.java',
    'src/main/java/com/whitecobalt/anglertackle/item/DishItem.java',
  ],
  // 超重惩罚（check-over-capacity）：只要 FishSpecies（已在 FILES 里），这里留空说明用意
  overcap: [],
  // 玩家数据（check-player-data）：附件注册 + 玩家数据类 + 主类 + 网络
  // ⚠ 这里**不能**再列 ModAttachments.java：它在 FILES 里（要变异），
  //   EXTRA 是后拷的，会把变异覆盖回原样 —— 负例就永远抓不到（2026-10-03 踩过）。
  playerData: [
    'src/main/java/com/whitecobalt/anglertackle/player/*.java',
    'src/main/java/com/whitecobalt/anglertackle/network/*.java',
    'src/main/java/com/whitecobalt/anglertackle/command/*.java',
    'src/main/java/com/whitecobalt/anglertackle/event/*.java',
    'src/main/java/com/whitecobalt/anglertackle/client/*.java',
    'src/main/java/com/whitecobalt/anglertackle/client/**/*.java',
    'src/main/java/com/whitecobalt/anglertackle/AnglersTackle.java',
  ],
  // 自洽性（check-self-consistency）：它 readdir 贴图/models，还要 lang 与两个源文件
  selfConsist: [
    'src/main/resources/assets/anglertackle/textures/item/*.png',
    'src/main/resources/assets/anglertackle/models/item/*.json',
    'src/main/java/com/whitecobalt/anglertackle/fishing/FishSpecies.java',
    'src/main/java/com/whitecobalt/anglertackle/Config.java',
    'tools/tuning-schema.json',
    'tools/tuning-tool.template.html',
    'tools/dev-menu.ps1',
    'tools/reset-config.ps1',
    'tools/build-deploy.ps1',
    'tools/make-release.ps1',
    '调参器.html',
    '调参器.bat',
    '重置配置.bat',
    'src/main/resources/icon.png',
    '参考资料/鱼图/_baseline/*.json',
    // 它还要看构建产物与发布包（那两条断言只能在有产物时成立）——
    // 副本里必须把这些也带上，否则**对照组**会以"没有构建产物"变红（假失败）
    'build/libs/*.jar',
    '发布包/*',
  ],
  // 死字段（check-dead-fields）：它 walk 整棵 java 树，所以整棵都拷
  deadFields: [
    'src/main/java/**/*.java',
  ],
  // 森罗物语联动（docs/30）：三个命名空间下的物品标签 + 策划文档 + **扩展包源码**
  // ⚠ 扩展包那几个文件必须一起拷：1.3.3 起该检查器多了 H 段（extension pack 白名单/资源/依赖/
  //   thin jar）。副本里没有 src/compat 时它会报"扩展包不见了" —— 那就变成**对照组假失败**
  //   （看着像抓到了变异，其实是被拷进去的源码不齐）。
  kaleidoscope: [
    'src/main/resources/data/anglertackle/tags/item/*.json',
    'src/main/resources/data/c/tags/item/*.json',
    'src/main/resources/data/c/tags/item/foods/*.json',
    'src/main/resources/data/kaleidoscope_cookery/tags/item/*.json',
    'src/main/resources/data/anglertackle/recipe/kaleidoscope/**/*.json',
    'src/main/java/com/whitecobalt/anglertackle/registry/ModTags.java',
    'docs/30-森罗物语联动策划.md',
    'src/compat/java/**/*.java',
    'src/compat/resources/META-INF/neoforge.mods.toml',
  ],
};

// ⚠ 二进制文件必须单独一列，**只能整体复制，不能读成 utf8 再写回去** ——
//   那样会把 NBT 里的非 UTF-8 字节替换成 U+FFFD，文件就废了。
//   （而且废掉的表现是"结构静默不生成"，不是报错。）
const BIN_FILES = [
  'src/main/resources/data/anglertackle/structure/fisherman_hut.nbt',
  // 定位线索的图标：check-hut-clue.js 要断言它在这个副本里（PNG 不能当文本读写）
  'src/main/resources/assets/anglertackle/textures/item/hut_clue.png',
];

/** 按文件名取值/存值的包装，同名文件有歧义时直接报错（而不是悄悄拿错那个）。 */
/**
 * 带断言的字符串替换。
 *
 * ⚠ 负例里**一律用它**，不要直接 .replace()：
 *   直接 replace 在锚点没匹配时会原样返回，于是"负例什么都没改" ——
 *   用例照样跑、照样通过，看起来和"检查器漏检"一模一样。
 *   重构一次（改个参数名、挪个方法）就会有一批负例悄悄失效，
 *   而它们失效的方式是**报出假问题**，比不报还费时间。
 *   踩过一次：把 findNearestMapStructure 的 origin 参数改成 sp.blockPosition()
 *   之后，compass-radius-blocks 这个负例就不再修改任何东西了。
 */
function mustReplace(src, from, to, label) {
  if (!src.includes(from)) {
    throw new Error('负例构造失败「' + label + '」：锚点没匹配上 → ' + from.slice(0, 70));
  }
  return src.split(from).join(to);
}

/**
 * 展开 'a/b/*.json' 这样的通配，返回相对路径列表。
 *
 * <p>支持两种写法：
 *   · `a/b/*.json`  —— 只扫一层（原来的行为）；
 *   · `a/**​/*.json` —— **递归**（菜配方在 `recipe/<菜型>/<鱼种>.json` 里，
 *     只扫一层的话那 31 条根本不会进副本，对照组会因为"缺文件"假失败 ——
 *     2026-10-03 改成一条鱼一道菜时就踩在这个坑上）。
 */
// ⚠ 通配展开要**缓存**：`src/main/java/**/*.java` 这种模式走一次完整目录树，
//   而它会被上百个用例各展开一次（改前每次建副本都重新 walk 一遍整棵 java 树）✗。
//   模式在整轮构建里是常量 ⇒ 缓存结果天经地义（文件内容可能被用例改，但**路径集合**不变）。
const extraCache = new Map();
function expandExtraCached(pat) {
  if (!extraCache.has(pat)) extraCache.set(pat, expandExtra(pat));
  return extraCache.get(pat);
}

// ⚠ 目录也缓存：`mkdirSync(recursive)` 是**每个文件**都调一次（14 万个文件 × 一条递归建目录链），
//   在 Windows 上这是纯粹的浪费 —— 同一棵树里绝大多数目录只该建一次。
let mkdirCache = new Set();
function mkdirsOnce(absDir) {
  if (mkdirCache.has(absDir)) return;
  fs.mkdirSync(absDir, { recursive: true });
  mkdirCache.add(absDir);
}

function expandExtra(pat) {
  if (!pat.includes('*')) return [pat];
  const recursive = pat.includes('**');
  const cut = pat.indexOf(recursive ? '**' : '*');
  const dirRel = pat.slice(0, cut).replace(/\/+$/, '');
  const suffix = path.basename(pat).replace(/\*/g, '');
  const dir = path.join(ROOT, dirRel);
  if (!fs.existsSync(dir)) return [];
  const out = [];
  const walk = (abs, rel) => {
    for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
      const a = path.join(abs, e.name);
      const r = rel ? rel + '/' + e.name : e.name;
      if (e.isDirectory()) {
        if (recursive) walk(a, r);
      } else if (e.name.endsWith(suffix)) {
        out.push(path.posix.join(dirRel.split(path.sep).join('/'), r));
      }
    }
  };
  walk(dir, '');
  return out;
}

/** 最小 PNG 写出（只给负例造损坏贴图用；filter=None / 8bit / RGBA）。 */
function writeRgbaPng(file, w, h, px) {
  const zlib = require('zlib');
  const T = (() => { const t = new Int32Array(256); for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320 ^ (c >>> 1)) : (c >>> 1); t[n] = c; } return t; })();
  const crc = (b) => { let c = 0xFFFFFFFF; for (let i = 0; i < b.length; i++) c = T[(c ^ b[i]) & 0xFF] ^ (c >>> 8); return (c ^ 0xFFFFFFFF) >>> 0; };
  const ch = (type, data) => { const l = Buffer.alloc(4); l.writeUInt32BE(data.length, 0); const t = Buffer.from(type, 'ascii'); const c = Buffer.alloc(4); c.writeUInt32BE(crc(Buffer.concat([t, data])), 0); return Buffer.concat([l, t, data, c]); };
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const c = px(x, y), o = y * (w * 4 + 1) + 1 + x * 4;
      raw[o] = c[0]; raw[o + 1] = c[1]; raw[o + 2] = c[2]; raw[o + 3] = c[3];
    }
  }
  const ih = Buffer.alloc(13); ih.writeUInt32BE(w, 0); ih.writeUInt32BE(h, 4); ih[8] = 8; ih[9] = 6;
  // ⚠⚠ 必须走 writeInCopy（先断链再写）：这条路径**真的改坏过真源码** ——
  //   2026-10-12 加硬链接提速后，`model-texture-blank` 透过链接把
  //   `textures/entity/fish/pike.png` 涂成了全透明（328 字节 → 87 字节），
  //   而当时的指纹复核只盯着 FILES、没覆盖 EXTRA 里那些贴图 ⇒ **它一声没响** ✗。
  //   真正暴露它的是 `check-fish-model.js` 的**对照组**：model-ok 期望通过却红了 ✓
  //   （⇒ 对照组不是形式主义，它就是"检查器坏了 / 环境坏了"的报警器）。
  writeInCopy(file, Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A]),
    ch('IHDR', ih), ch('IDAT', zlib.deflateSync(raw, { level: 9 })), ch('IEND', Buffer.alloc(0)),
  ]));
}

function makeAccessor(files, dirty, dest) {
  const find = (name) => {
    const hits = Object.keys(files).filter((k) => k === name || k.endsWith('/' + name));
    if (hits.length === 0) throw new Error('找不' + '到文件 ' + name);
    if (hits.length > 1) throw new Error('文件名 ' + name + ' 有歧义，匹配到：' + hits.join(' | '));
    return hits[0];
  };
  // 按需读：第一次访问某个文件时才真读它（见文件顶部的"复制策略"那一段）
  const ensure = (rel) => {
    if (files[rel] === null || files[rel] === undefined) {
      files[rel] = fs.readFileSync(path.join(dest, rel), 'utf8');
    }
    return files[rel];
  };
  return {
    get: (name) => ensure(find(name)),
    set: (name, text) => {
      const rel = find(name);
      files[rel] = text;
      dirty.add(rel);          // ★ 只有登记过的文件才会写回副本（其余保持硬链接）
    },
    has: (name) => Object.keys(files).some((k) => k === name || k.endsWith('/' + name)),
  };
}

// 每个用例：一个子目录 + 要跑哪个检查器 + 期望的退出码
const CASES = [
  // ---- 联赛商店（`docs/39` P4）----
  // ⚠ 这三条 1.5.100 时曾因"疑似写穿真源码"被撤下；2026-10-13 已给 writeInCopy 加了两道
  //   fail-closed 自检（目标必须在 build/negtest 下 + 断链后目标必须不存在），
  //   同类问题现在**只会抛错停下、不会再静默改坏仓库** ⇒ 按新规矩补回。
  { dir: 'league-shop-no-pay', checker: 'check-league.js', expect: 1,
    desc: '联赛商店不扣积分直接发货（白送）—— `spend` 一旦消失，检查器必须当场红',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/shop/ShopManager.java';
      at.set(F, mustReplace(at.get(F),
        'LeaguePoints.spend(player, o.points())', 'true', 'league-shop-no-pay'));
    } },
  { dir: 'league-shop-price-wrong', checker: 'check-league.js', expect: 1,
    desc: '把红线从 750 改成 700 ⇒ 与用户给的价目对不上（**要改生成物**：规则表改不动已生成的目录）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/shop/ShopCatalog.java';
      at.set(F, mustReplace(at.get(F),
        '"titanium_line_low", 750', '"titanium_line_low", 700', 'league-shop-price-wrong'));
    } },
  { dir: 'league-shop-tab-gone', checker: 'check-league.js', expect: 1,
    desc: '界面忘接联赛页签 ⇒ 商店里买了也没入口（一个负例只跑它声明的那个检查器 ⇒ expect 只能是 1）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/ShopScreen.java';
      at.set(F, mustReplace(at.get(F), 'TAB_LEAGUE = 11', 'TAB_LEAGUE_TYPO = 11',
        'league-shop-tab-gone'));
    } },
  // ---- check-spawn-egg.js ----
{ dir: 'stool-state-not-reported', checker: 'check-stool-wiring.js', expect: 1,
    desc: '方块有 COLOR 属性却没在 createBlockStateDefinition 上报（进游戏是紫黑缺失模型）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/block/CushionedStoolBlock.java';
      at.set(F, mustReplace(at.get(F),
        'builder.add(COLOR, FACING);',
        'builder.add(FACING);', 'stool-state-not-reported'));
    } },

  { dir: 'egg-ok', checker: 'check-spawn-egg.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'egg-deprecated-ctor', checker: 'check-spawn-egg.js', expect: 1,
    desc: '改回被弃用的 new SpawnEggItem(...)',
    mutate: (at) => {
      at.set('ModItems.java', at.get('ModItems.java')
        .replace(/new net\.neoforged\.neoforge\.common\.DeferredSpawnEggItem\(/,
          'new net.minecraft.world.item.SpawnEggItem(')
        .replace(/DeferredItem<\s*net\.neoforged\.neoforge\.common\.DeferredSpawnEggItem\s*>/,
          'DeferredItem<net.minecraft.world.item.SpawnEggItem>'));
    } },

  { dir: 'egg-eager-get', checker: 'check-spawn-egg.js', expect: 1,
    desc: '实体参数写成 .get()（把注册顺序的坑踩回来）',
    mutate: (at) => {
      at.set('ModItems.java', at.get('ModItems.java').replace(
        /(DeferredSpawnEggItem\(\s*)com\.whitecobalt\.anglertackle\.registry\.ModEntities\.FISHERMAN,/,
        '$1com.whitecobalt.anglertackle.registry.ModEntities.FISHERMAN.get(),'));
    } },

  { dir: 'egg-no-lang', checker: 'check-spawn-egg.js', expect: 1,
    desc: '删掉刷怪蛋的中英文语言键',
    mutate: (at) => {
      for (const k of ['zh_cn.json', 'en_us.json']) {
        const o = JSON.parse(at.get(k));
        delete o['item.anglertackle.fisherman_spawn_egg'];
        at.set(k, JSON.stringify(o, null, 2) + '\n');
      }
    } },

  { dir: 'egg-no-attributes', checker: 'check-spawn-egg.js', expect: 1,
    desc: '删掉 EntityAttributeCreationEvent 里的属性注册',
    mutate: (at) => {
      // ⚠ 用 mustReplace：锚点过时（比如这段被改成了多行、括号数变了）时**直接报错**，
      //   而不是静默不改 —— 静默不改会让负例永远"通过"，表现成"检查器漏检"。
      //   这个用例就踩过：把属性注册改成 for 循环块之后，旧正则的括号数对不上，
      //   于是负例失效、报出"check-spawn-egg 漏检"的假问题。
      at.set('AnglersTackle.java', mustReplace(at.get('AnglersTackle.java'),
        'e.put(ModEntities.FISHERMAN.get(), com.whitecobalt.anglertackle.entity.FishermanEntity.createAttributes().build());',
        '/* 属性注册被负向测试删掉了 */',
        'FISHERMAN 属性注册'));
    } },

  { dir: 'egg-no-model', checker: 'check-spawn-egg.js', expect: 1,
    desc: '删掉刷怪蛋的模型 json', mutate: () => {},
    deleteFiles: ['models/item/fisherman_spawn_egg.json'] },

  { dir: 'egg-not-in-tab', checker: 'check-spawn-egg.js', expect: 1,
    desc: '从创造页签里拿掉刷怪蛋',
    mutate: (at) => {
      at.set('ModItems.java', at.get('ModItems.java').replace(
        /^[ \t]*output\.accept\(FISHERMAN_SPAWN_EGG\.get\(\)\);[ \t]*$/m, ''));
    } },

  // ---- check-npc-fisherman.js ----
  { dir: 'npc-ok', checker: 'check-npc-fisherman.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'npc-no-mobinteract', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '去掉 mobInteract（右键 NPC 会完全没反应）',
    mutate: (at) => {
      // 只删方法签名那一行，方法体留着 —— 够让检查脚本认不出来，
      // 又不会把文件改成语法不通的样子（检查器是正则，不编译）。
      at.set('FishermanEntity.java', at.get('FishermanEntity.java').replace(
        /protected InteractionResult mobInteract\(Player player, InteractionHand hand\) \{/,
        'protected InteractionResult notMobInteract(Player player, InteractionHand hand) {'));
    } },

  { dir: 'npc-client-getoffers', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '把 getOffers() 挪到客户端分支外（客户端会抛异常）',
    mutate: (at) => {
      at.set('FishermanEntity.java', at.get('FishermanEntity.java').replace(/\n\}\s*$/, `
    /** 负向测试注入：没有任何 isClientSide 守卫的取商品 */
    public MerchantOffers badPeek() {
        return this.getOffers();
    }
}
`));
    } },

  { dir: 'npc-no-rewardxp', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '删掉 rewardTradeXp（AbstractVillager 的 abstract 方法）',
    mutate: (at) => {
      at.set('FishermanEntity.java', at.get('FishermanEntity.java').replace(
        /protected void rewardTradeXp\(MerchantOffer offer\) \{/,
        'protected void rewardTradeXpRenamed(MerchantOffer offer) {'));
    } },

  { dir: 'npc-no-renderer', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '不注册渲染器（实体会隐形，碰撞箱还在）',
    mutate: (at) => {
      at.set('ClientSetup.java', at.get('ClientSetup.java').replace(
        /event\.registerEntityRenderer\(ModEntities\.FISHERMAN\.get\(\),/,
        '// event.registerEntityRenderer(ModEntities.FISHERMAN.get(),'));
    } },

  { dir: 'npc-no-persistence', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '去掉 setPersistenceRequired（玩家走远他就消失）',
    mutate: (at) => {
      at.set('FishermanEntity.java', at.get('FishermanEntity.java').replace(
        /this\.setPersistenceRequired\(\);/,
        '// this.setPersistenceRequired();'));
    } },

  { dir: 'npc-no-home-claim', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '去掉"第一 tick 自己认领落脚点为家"（老柯永远不回家）',
    mutate: (at) => {
      // ⚠ 必须**全局**换掉，不能只改声明那一处：
      //   第一版只把 `private void adoptSpawnAsHome()` 改了名，而 tick() 里的
      //   **调用点**还叫这个名字，于是检查器照样匹配到 —— 负例自己失效了，
      //   表现为"这个用例没被抓到"。负例写得太弱和检查器漏检长得一模一样。
      at.set('FishermanEntity.java', at.get('FishermanEntity.java')
        .split('adoptSpawnAsHome').join('disabledHomeClaim'));
    } },

  { dir: 'npc-one-line', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '把台词池砍到 1 句（退化成"只有这一句话"的告示牌）',
    mutate: (at) => {
      const src = at.get('FishermanEntity.java');
      const m = /(private\s+static\s+final\s+String\[\]\s+LINES\s*=\s*\{)([\s\S]*?)(\n\s*\};)/.exec(src);
      if (!m) throw new Error('负例构造失败：找不到 LINES 数组');
      at.set('FishermanEntity.java',
        src.replace(m[0], m[1] + '\n            "竿子先支好，货我还没备齐……",' + m[3]));
    } },

  { dir: 'npc-missing-line-lang', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '删掉一条台词的中文语言键（聊天栏会显示成键名）',
    mutate: (at) => {
      const o = JSON.parse(at.get('zh_cn.json'));
      delete o['msg.anglertackle.fisherman.line.7'];
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'npc-random-line', checker: 'check-npc-fisherman.js', expect: 1,
    desc: '把轮流取台词改成纯随机（会连着撞同一句，感受等于只有一句）',
    mutate: (at) => {
      at.set('FishermanEntity.java', at.get('FishermanEntity.java')
        .replace(/nextLine = \(nextLine \+ 1\) % LINES\.length;/,
          'nextLine = this.random.nextInt(LINES.length);'));
    } },

  // ---- check-shop-prices.js ----
  { dir: 'shop-ok', checker: 'check-shop-prices.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'shop-doc-drift', checker: 'check-shop-prices.js', expect: 1,
    desc: '手改价目表里的一个数字（生成物漂移）',
    mutate: (at) => {
      const src = at.get('docs/18-商店价目表.md');
      // 把"15000"改成"1500"，模拟有人手改文档
      if (!src.includes('| 15000 |')) throw new Error('负例构造失败：文档里找不到 15000');
      at.set('docs/18-商店价目表.md', src.replace('| 15000 |', '| 1500 |'));
    } },

  { dir: 'shop-exchange-bad-fish', checker: 'check-shop-prices.js', expect: 1,
    desc: '金轮兑换写成不存在的鱼 id（兑换永远不成功）',
    mutate: (at) => {
      at.set('shop-rules.js', mustReplace(at.get('shop-rules.js'),
        "'atlantic_salmon', 'brown_trout', 'tigerfish'",
        "'atlantic_salmon', 'brown_trouts', 'tigerfish'",
        '金轮兑换鱼 id'));
    } },

  { dir: 'shop-no-restock-time', checker: 'check-shop-prices.js', expect: 1,
    desc: '限购却不写补货时间（玩家永久买不到）',
    mutate: (at) => {
      at.set('shop-rules.js', mustReplace(at.get('shop-rules.js'),
        '5: buy(2700, { restock: { per: 5, minutes: 120 } }),',
        '5: buy(2700, { restock: { per: 5, minutes: 0 } }),',
        '金线补货时间'));
    } },

  { dir: 'shop-missing-tier-rule', checker: 'check-shop-prices.js', expect: 1,
    desc: '规则表删掉金竿那一档（有商品却没规则）',
    mutate: (at) => {
      at.set('shop-rules.js', mustReplace(at.get('shop-rules.js'),
        '    5: buy(15000, { note:',
        '    4.5: buy(15000, { note:',
        '金竿档位键'));
    } },

  { dir: 'shop-price-inversion', checker: 'check-shop-prices.js', expect: 1,
    desc: '蓝竿比绿竿便宜（价格倒挂，玩家只会买蓝竿）',
    mutate: (at) => {
      at.set('shop-rules.js', mustReplace(at.get('shop-rules.js'),
        '    3: buy(1500, { note:',
        '    3: buy(100, { note:',
        '蓝竿价格'));
    } },

  // ---- check-vanilla-tags.js ----
  { dir: 'vtags-ok', checker: 'check-vanilla-tags.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'vtags-missing', checker: 'check-vanilla-tags.js', expect: 1,
    desc: '引用不存在的 #minecraft:is_swamp（**这就是这次结构永远不生成的根因**）',
    mutate: (at) => {
      const f = 'src/main/resources/data/anglertackle/tags/worldgen/biome/has_fisherman_hut.json';
      const o = JSON.parse(at.get(f));
      o.values = ['#minecraft:is_river', '#minecraft:is_swamp', '#minecraft:is_beach'];
      at.set(f, JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'vtags-empty', checker: 'check-vanilla-tags.js', expect: 1,
    desc: '标签 values 清空（空标签 = 任何地方都不生成）',
    mutate: (at) => {
      const f = 'src/main/resources/data/anglertackle/tags/worldgen/biome/has_fisherman_hut.json';
      const o = JSON.parse(at.get(f));
      o.values = [];
      at.set(f, JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'vtags-bad-registry', checker: 'check-vanilla-tags.js', expect: 1,
    desc: '把标签放错注册表目录（路径写错时 vanilla 清单里没有那个注册表）',
    renameDirs: [['data/anglertackle/tags/worldgen/biome', 'data/anglertackle/tags/worldgen/biomes']] },

  // ---- check-hut-structure.js ----
  { dir: 'hut-ok', checker: 'check-hut-structure.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'hut-plural-dir', checker: 'check-hut-structure.js', expect: 1,
    desc: '把 structure 目录改成复数 structures（1.21 不认，结构静默不生成）',
    renameDirs: [['data/anglertackle/structure', 'data/anglertackle/structures']] },

  { dir: 'hut-empty-tag', checker: 'check-hut-structure.js', expect: 1,
    desc: '群系标签 values 清空（空标签 = 任何群系都不生成）',
    mutate: (at) => {
      at.set('has_fisherman_hut.json', JSON.stringify({ replace: false, values: [] }, null, 2) + '\n');
    } },

  { dir: 'hut-truncated-nbt', checker: 'check-hut-structure.js', expect: 1,
    desc: '把结构 NBT 截断成 100 字节（模拟被编辑器改坏）',
    truncateBin: [['data/anglertackle/structure/fisherman_hut.nbt', 100]] },

  { dir: 'hut-no-structure-set', checker: 'check-hut-structure.js', expect: 1,
    desc: '删掉 structure_set（**小屋永远不会生成**，而且日志里一个字都没有）',
    deleteData: ['worldgen/structure_set/fisherman_hut.json'] },

  { dir: 'hut-placement-in-structure', checker: 'check-hut-structure.js', expect: 1,
    desc: '把 placement 写回结构 JSON（1.21 静默忽略它，会让人以为结构配好了）',
    mutate: (at) => {
      const sp = 'data/anglertackle/worldgen/structure/fisherman_hut.json';
      const o = JSON.parse(at.get(sp));
      o.placement = { type: 'minecraft:random_spread', spacing: 34, separation: 9, salt: 1 };
      at.set(sp, JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'hut-bad-salt', checker: 'check-hut-structure.js', expect: 1,
    desc: 'structure_set 的 salt 写成字符串（分布规则解析不了）',
    mutate: (at) => {
      const sp = 'data/anglertackle/worldgen/structure_set/fisherman_hut.json';
      const o = JSON.parse(at.get(sp));
      o.placement.salt = 'abc';
      at.set(sp, JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'hut-no-struct-json', checker: 'check-hut-structure.js', expect: 1,
    desc: '删掉世界生成的结构 JSON（小屋永远不生成）',
    deleteData: ['worldgen/structure/fisherman_hut.json'] },

  { dir: 'hut-size-6', checker: 'check-hut-structure.js', expect: 1,
    desc: '把 size 从 1 改成 6（单体结构却去找 jigsaw 接口拼图）',
    mutate: (at) => {
      const o = JSON.parse(at.get('data/anglertackle/worldgen/structure/fisherman_hut.json'));
      o.size = 6;
      at.set('data/anglertackle/worldgen/structure/fisherman_hut.json', JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'hut-sep-gt-spacing', checker: 'check-hut-structure.js', expect: 1,
    desc: 'separation 比 spacing 还大（永远排不下）',
    mutate: (at) => {
      // 注意改的是 **structure_set** 里的 placement，不是结构 JSON ——
      // 结构 JSON 里根本没有这个字段（这一点是 2026-10-01 才查清的）
      const sp = 'data/anglertackle/worldgen/structure_set/fisherman_hut.json';
      const o = JSON.parse(at.get(sp));
      o.placement.spacing = 10;
      o.placement.separation = 20;
      at.set(sp, JSON.stringify(o, null, 2) + '\n');
    } },

  // 下面三条改的是**二进制 NBT**。用 lib-nbt 的带类型读写来做，
  // 因为这几处恰好是"游戏里看着一模一样、就是不出实体"的那种错。
  { dir: 'hut-pos-wrong-type', checker: 'check-hut-structure.js', expect: 1,
    desc: '把实体的 pos 从 List<Double> 改成 List<Int>（游戏读不出坐标）',
    mutateNbt: (typed) => {
      const e0 = typed.root.v.entities.v.items[0];
      e0.v.pos = { t: 9, v: { et: 3, items: e0.v.pos.v.items.map((x) => ({ t: 3, v: Math.round(x.v) })) } };
    } },

  { dir: 'hut-no-entity', checker: 'check-hut-structure.js', expect: 1,
    desc: '把 entities 清空（老柯不会出现）',
    mutateNbt: (typed) => { typed.root.v.entities.v.items = []; } },

  { dir: 'hut-stray-entity', checker: 'check-hut-structure.js', expect: 1,
    desc: '结构里多塞一个实体（模拟存结构时把生物也框了进去）',
    mutateNbt: (typed) => {
      const items = typed.root.v.entities.v.items;
      items.push(JSON.parse(JSON.stringify(items[0])));
    } },

  // ---- check-hut-clue.js（渔夫小屋定位线索：替代罗盘）----
  // 用户 2026-10-02："清楚罗盘物品，改为「渔夫小屋定位线索」…借一下原版查找结构指令的原理。"
  { dir: 'clue-ok', checker: 'check-hut-clue.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'clue-no-search', checker: 'check-hut-clue.js', expect: 1,
    desc: '不调 findNearestMapStructure（右键什么都没发生）',
    mutate: (at) => {
      at.set('HutClueItem.java', mustReplace(at.get('HutClueItem.java'),
        'BlockPos target = serverLevel.findNearestMapStructure(',
        'BlockPos target = null;   // 变异：不搜了\n        if (false) serverLevel.findNearestMapStructure(',
        'clue-no-search'));
    } },

  { dir: 'clue-radius-blocks', checker: 'check-hut-clue.js', expect: 1,
    desc: '把"格"当"区块"传（搜索范围小 16 倍，几乎永远找不到）',
    mutate: (at) => {
      at.set('HutClueItem.java', mustReplace(at.get('HutClueItem.java'),
        'int radiusChunks = Math.max(1, Tuning.compassSearchRadius() / 16);',
        'int radiusChunks = Tuning.compassSearchRadius();', 'clue-radius-blocks'));
    } },

  { dir: 'clue-no-consume', checker: 'check-hut-clue.js', expect: 1,
    desc: '用完不消耗（8 张纸 + 一条鱼可以无限用）',
    mutate: (at) => {
      at.set('HutClueItem.java', mustReplace(at.get('HutClueItem.java'),
        'stack.shrink(1);   // 一次性道具：报完就没了（8 张纸 + 一条鱼换一条线索）',
        '// 变异：不消耗', 'clue-no-consume'));
    } },

  { dir: 'clue-recipe-paper', checker: 'check-hut-clue.js', expect: 1,
    desc: '配方只放 4 张纸（与用户"8张纸"不符）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/recipe/hut_clue.json';
      at.set(p, mustReplace(at.get(p),
        '    {\n      "item": "minecraft:paper"\n    },\n    {\n      "item": "minecraft:paper"\n    },\n    {\n      "item": "minecraft:paper"\n    },\n    {\n      "item": "minecraft:paper"\n    },\n',
        '    {\n      "item": "minecraft:paper"\n    },\n', 'clue-recipe-paper'));
    } },

  { dir: 'clue-compass-back', checker: 'check-hut-clue.js', expect: 1,
    desc: '罗盘又留了一件没删干净（模型文件在）',
    createFiles: [['src/main/resources/assets/anglertackle/models/item/tracking_compass.json',
      '{\n  "parent": "minecraft:item/generated"\n}\n']],
    mutate: () => {} },

  // 用户 2026-10-02："教程里没透露钓鱼小屋线索的制作方法。"
  // 当时 tooltip 里没有那一行（语言键是孤儿），手册那页也埋在第八章 ——
  // 这两条负例分别守住"物品要说"和"手册要说"。
  { dir: 'clue-no-tooltip', checker: 'check-hut-clue.js', expect: 1,
    desc: '物品 tooltip 不再写制作方法（玩家只能靠翻手册第八章）',
    mutate: (at) => {
      at.set('HutClueItem.java', mustReplace(at.get('HutClueItem.java'),
        'lines.add(Component.translatable("item.anglertackle.hut_clue.craft")',
        'lines.add(Component.translatable("item.anglertackle.hut_clue.craft_gone")',
        'clue-no-tooltip'));
    } },

  { dir: 'clue-guide-no-recipe', checker: 'check-hut-clue.js', expect: 1,
    desc: '手册里删掉"纸 ×8 + 一条鱼"那句（玩家在教程里就查不到配方了）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  渔夫小屋定位线索 = 纸 ×8 + 任意一条鱼",',
        '"  渔夫小屋定位线索：去问老柯吧",', 'clue-guide-no-recipe'));
    } },

  // ---- check-guide-numbers.js（改数值必须改文本）----
  // 用户 2026-10-02（看到 tooltip 还是 20kg 那张图）："以后数值改动后记得改文本。"
  { dir: 'guidenum-ok', checker: 'check-guide-numbers.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'guidenum-weight-drift', checker: 'check-guide-numbers.js', expect: 1,
    desc: '改了配置里的承重却没改手册（手册还写着旧数字）',
    mutate: (at) => {
      // ⚠ 锚点必须用**当前真值**：这条原来把 `…:3500:25:` 改成 `…:3500:30:`，
      //   而 2026-10-08 用户把铁竿承重正式定成 30 之后，锚点 25 就不存在了 ——
      //   mustReplace 匹配不上会当场抛错，**整个负例批次一起中止** ✗
      //   （闸门只会打印"造副本失败"，不说是哪个用例）。改数值时记得回来改这里。
      at.set('gen-gameplay-config.js', mustReplace(at.get('gen-gameplay-config.js'),
        "'iron:铁竿:3500:30:", "'iron:铁竿:3500:45:", 'guidenum-weight-drift'));
    } },

  { dir: 'guidenum-level-drift', checker: 'check-guide-numbers.js', expect: 1,
    desc: '改了升级经验上限却没改手册（手册还写着 1200）',
    mutate: (at) => {
      at.set('AnglerLeveling.java', mustReplace(at.get('AnglerLeveling.java'),
        'public static final int XP_LAST = 1200;', 'public static final int XP_LAST = 900;',
        'guidenum-level-drift'));
    } },

  { dir: 'guidenum-old-rating', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册里留着旧的"等级 1 到 5"那套说法',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"【等级 1 到 50】",', '"【等级 1 到 5】",', 'guidenum-old-rating'));
    } },

  // ⚠ 下面 6 条是 2026-10-02 用户拿着截图报的那批文本错误 ——
  //   它们共同点是"手册复述了代码里的某个事实，而没有任何断言守着它"。
  // ⚠ 锚点会跟着被守的那句话一起改（2026-10-08 第 1 批加完 33 种鱼）：
  //   锚点对不上时，_negtest.js 是**在造副本阶段就抛异常**，整份负向测试直接不跑 ——
  //   而日志里最后一行还是上一条用例的说明，看起来像"卡住了"，不像"锚点过期"。
  { dir: 'guidenum-fish-count', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册的鱼种数量与 FishSpecies 对不上（就是"21 种"那次的类型）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  本模组共 47 种鱼，分 6 类水体 + 3 种极寒水域：",', '"  本模组共 24 种鱼，分 6 类水体 + 3 种极寒水域：",',
        'guidenum-fish-count'));
    } },

  { dir: 'guidenum-codex-coins', checker: 'check-guide-numbers.js', expect: 1,
    desc: '图鉴满收集的鱼币数与"每 2 种 50 币 × 种数"算不出来',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"     47 种全部点亮共 1150 鱼币",', '"     47 种全部点亮共 500 鱼币",',
        'guidenum-codex-coins'));
    } },

  { dir: 'guidenum-tab-count', checker: 'check-guide-numbers.js', expect: 1,
    desc: '商店页签数写错（就是漏了「兑换」那次的类型；2026-10-13 又漏过「联赛」）',
    mutate: (at) => {
      // ⚠ 锚点跟着手册走：2026-10-13 加「联赛」页签后，手册那句从"十一个"改成了"十二个"
      //   ⇒ 这里也要改（**锚点漂移**的典型：改文案时顺手把锚点改没）
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"【十二个页签】",', '"【十个页签】",', 'guidenum-tab-count'));
    } },

  { dir: 'guidenum-sell-all', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册又说"卖鱼页一次卖光背包里的鱼"（实际点一条卖一条）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  卖鱼页点哪一条就卖哪一条，",', '"  卖鱼页一次卖光背包里的鱼。",',
        'guidenum-sell-all'));
    } },

  { dir: 'guidenum-zkey', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册又教玩家"按 Z"开改装界面（那个入口已经删了，按了没反应）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  ① 潜行 + 松开右键开六槽界面；",', '"  ① 手上拿竿按 Z 开六槽界面；",',
        'guidenum-zkey'));
    } },

  { dir: 'guidenum-no-unequip', checker: 'check-guide-numbers.js', expect: 1,
    desc: '删掉"怎么把配件卸下来"的说明（用户点名要补的那一条）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"    换 = 从背包里挑一件换上",\n          "    卸 = 取下来，配件回背包",\n',
        '"    换 = 从背包里挑一件换上",\n', 'guidenum-no-unequip'));
    } },

  { dir: 'guidenum-sneak-removed', checker: 'check-guide-numbers.js', expect: 1,
    desc: '把"潜行 + 松开右键"的检测从 ClientEvents 里删掉（手册写的入口就没了）',
    mutate: (at) => {
      at.set('ClientEvents.java', mustReplace(at.get('ClientEvents.java'),
        'boolean sneakRelease = mc.player.isShiftKeyDown() && wasUsingItem && !usingNow;',
        'boolean sneakRelease = false;   // 变异：入口被删了', 'guidenum-sneak-removed'));
    } },

  { dir: 'guidenum-mullet-typo', checker: 'check-guide-numbers.js', expect: 1,
    desc: '把鱼名写回错字「淄鱼」（正名是「鲻鱼」，它还会灌进 Config 的注释）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '"item.anglertackle.mullet": "鲻鱼",', '"item.anglertackle.mullet": "淄鱼",',
        'guidenum-mullet-typo'));
    } },

  // ---- check-ps1-encoding.js（.ps1 必须带 BOM / .bat 必须纯 ASCII）----
  // 2026-10-02：这个检查器原来只扫 tools/，而"输入法锁定"小工具跑到了
  // 工具\输入法锁定\ —— 那里同样有双击运行的 .bat，等于漏在闸门外面。
  // 现在改成全仓库递归，这几条负例守住它。
  { dir: 'ps1enc-ok', checker: 'check-ps1-encoding.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'ps1enc-bat-chinese', checker: 'check-ps1-encoding.js', expect: 1,
    desc: '.bat 里混进中文（cmd 按 OEM 代码页读，会把下一行的命令吃掉 —— 真实踩过）',
    mutate: (at) => {
      // ⚠ 锚点要和文件**逐字**一致。第一版凭记忆写成 'rem Build ime-lock.exe'，
      //   而文件里是 'rem  Build'（两个空格）—— mustReplace 当场抛异常、
      //   整个 _negtest.js 崩掉，副本没更新；接着单独跑用例时检查器读的是**旧副本**，
      //   于是"用例没抓到"看起来像检查器漏检。锚点尽量挑短且唯一的整行。
      at.set('编译.bat', mustReplace(at.get('编译.bat'),
        'echo Compiling with:', 'echo 正在编译:', 'ps1enc-bat-chinese'));
    } },

  { dir: 'ps1enc-bat-bom', checker: 'check-ps1-encoding.js', expect: 1,
    desc: '.bat 被编辑器加上 UTF-8 BOM（第一条命令之前多出三个字节）',
    mutate: (at) => {
      at.set('开机自启.bat', '\uFEFF' + at.get('开机自启.bat'));
    } },

  { dir: 'ps1enc-ps1-no-bom', checker: 'check-ps1-encoding.js', expect: 1,
    desc: '某个 .ps1 的 BOM 被吃掉（powershell 5.1 会按 GBK 解码成乱码再报语法错）',
    mutate: (at) => {
      const f = 'tools/build-deploy.ps1';
      let s = at.get(f);
      if (s.charCodeAt(0) === 0xFEFF) s = s.slice(1);
      at.set(f, s);
    } },

  // ---- check-water-body.js（水体判定 + 鱼种归属表）----
  // 2026-10-02 用户实测："我刚刚在溶洞拿台钓红饵紫窝半天全是草鱼青鱼。"
  // → 洞穴是地形不是群系，地下暗河的群系还是河流/平原，于是出了地表鱼。
  //   用户定案加了"地下判定"（y<55 且上下 20 格内有洞穴标志方块），
  //   这几条守住它 —— 这个检查器此前**一条负例都没有**。
  { dir: 'water-ok', checker: 'check-water-body.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'water-cave-noscan', checker: 'check-water-body.js', expect: 1,
    desc: '去掉地下判定（洞里的水又会被当成地表水体，继续出草鱼青鱼）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        '        if (looksLikeCave(level, pos)) {\n            return CAVE;\n        }\n',
        '', 'water-cave-noscan'));
    } },

  { dir: 'water-cave-stone', checker: 'check-water-body.js', expect: 1,
    desc: '把石头当成洞穴标志方块（等于"y<55 的水一律算洞穴"，地下湖的地表鱼全被砍）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET,',
        'Blocks.MOSS_BLOCK, Blocks.MOSS_CARPET, Blocks.STONE,', 'water-cave-stone'));
    } },

  { dir: 'water-cave-y', checker: 'check-water-body.js', expect: 1,
    desc: '改掉地下判定的高度阈值（用户定的是 y55 以下）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'private static final int CAVE_MAX_Y = 55;',
        'private static final int CAVE_MAX_Y = 200;', 'water-cave-y'));
    } },

  { dir: 'water-cave-no-fish', checker: 'check-water-body.js', expect: 1,
    desc: '把洞穴的鱼全改成湖泊鱼（洞穴水域变成空池 —— 会退回不限水体，抽出别的物种）',
    mutate: (at) => {
      const src = at.get('FishWaters.java');
      const n = src.split('M_CAVE').length - 1;
      if (n < 3) throw new Error('负例构造失败「water-cave-no-fish」：M_CAVE 只找到 ' + n + ' 处');
      at.set('FishWaters.java', src.split('M_CAVE').join('M_LAKE'));
    } },

  // ---- 水域修饰（用户 2026-10-08：极地 / 高寒湖 / 冷水溪流）----
  // 三个修饰是**叠加**的，不是新的水体值（docs/33 §9.1）。这几条守的全是**静默失败**：
  // 标签里写错一个 id、阈值写死、标签名对不上 —— 游戏全都不报错，只是"那片水永远不算极地"。
  { dir: 'water-mod-polar-alpine', checker: 'check-water-body.js', expect: 1,
    desc: '去掉"极地排除高山群系"那条（用户明确要求"仅适用于非高山且常年覆冰群系"，'
      + 'JSON 标签表达不了"不在某标签里"，所以这条只能写在代码里）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'if (biome.is(IS_ICE_FISHING) && !alpine) {',
        'if (biome.is(IS_ICE_FISHING)) {', 'water-mod-polar-alpine'));
    } },

  { dir: 'water-mod-tag-typo', checker: 'check-water-body.js', expect: 1,
    desc: '极地标签里把 snowy_taiga 写成 snowy_taiga_（群系 id 写错一个字符 —— '
      + '游戏只 warning，那片雪原永远不算极地水）',
    mutate: (at) => {
      at.set('ice_fishing.json', mustReplace(at.get('ice_fishing.json'),
        '"minecraft:snowy_taiga"', '"minecraft:snowy_taiga_"', 'water-mod-tag-typo'));
    } },

  { dir: 'water-mod-hardcoded', checker: 'check-water-body.js', expect: 1,
    desc: '把高寒湖的高度阈值写死成 120（调参器里改了没反应 —— damping_bonus 那次就是这么坏的）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'pos.getY() >= Tuning.alpineLakeMinY()', 'pos.getY() >= 120', 'water-mod-hardcoded'));
    } },

  { dir: 'water-mod-alpine-body', checker: 'check-water-body.js', expect: 1,
    desc: '高寒湖不再限定"水体是湖泊"（高山上的河也会变成高寒湖）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'if (body == LAKE && (', 'if ((', 'water-mod-alpine-body'));
    } },

  { dir: 'water-mod-leak', checker: 'check-water-body.js', expect: 1,
    desc: '把修饰位 M_POLAR 混进水体归属表（那是按位解析的表，多一个位会让普通鱼种的掩码串位）',
    mutate: (at) => {
      at.set('FishWaters.java', mustReplace(at.get('FishWaters.java'),
        'put("brown_trout", M_RIVER);', 'put("brown_trout", M_RIVER | M_POLAR);', 'water-mod-leak'));
    } },

  { dir: 'water-mod-bit', checker: 'check-water-body.js', expect: 1,
    desc: '把极地位改成 1<<5（和洞穴位撞车 —— 洞穴鱼会突然算成极地鱼，反之亦然）',
    mutate: (at) => {
      at.set('WaterBody.java', mustReplace(at.get('WaterBody.java'),
        'public static final int M_POLAR = 1 << 6;',
        'public static final int M_POLAR = 1 << 5;', 'water-mod-bit'));
    } },

  // ---- 稀释上限（docs/33 §9.4：老鱼在同一片水里的占比不许低于 75%）----
  // 往冷水溪流里再塞 3 条专属鱼 → 老鱼占比掉到 75% 以下 → 必须红。
  // （用已存在的鱼 id，只改修饰登记，不动 FishSpecies —— 这一条要测的是"稀释断言"本身）
  { dir: 'water-dilution', checker: 'check-water-body.js', expect: 1,
    desc: '往冷水溪流多塞 3 条专属鱼（老鱼占比跌破 75%，玩家会感觉"原来的鱼变少了"）',
    mutate: (at) => {
      let s = at.get('FishWaters.java');
      s = mustReplace(s, 'putMods("burbot", M_COLD_STREAM);',
        'putMods("burbot", M_COLD_STREAM);\n        putMods("pike", M_COLD_STREAM);\n'
        + '        putMods("zander", M_COLD_STREAM);\n        putMods("tigerfish", M_COLD_STREAM);',
        'water-dilution');
      at.set('FishWaters.java', s);
    } },

  // ---- 冰钓四条数值链（第 2 批，用户 2026-10-08 拍板）----
  { dir: 'ice-hv-unit', checker: 'check-ice-fishing.js', expect: 1,
    desc: '把金红占比的 ×100 换算去掉（配置是 0.01 比例、hvPct 是"10 = 10%"的百分数，'
      + '少了这一步 +1% 会变成 +0.01%，等于没加而且还查不出来）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'hvPct + iceHvDelta * 100.0', 'hvPct + iceHvDelta', 'ice-hv-unit'));
    } },

  { dir: 'ice-hole-bite', checker: 'check-ice-fishing.js', expect: 1,
    desc: '去掉"洞内咬钩时间不变"的早退（洞里也会被延长 50%，与用户定案相反）',
    mutate: (at) => {
      at.set('IceFishing.java', mustReplace(at.get('IceFishing.java'),
        '        if (inIceHole(level, pos)) {\n            return 1.0;\n        }\n',
        '', 'ice-hole-bite'));
    } },

  { dir: 'ice-refreeze-knob', checker: 'check-ice-fishing.js', expect: 1,
    desc: '偷偷加一个"冰洞回冻"配置项（用户定案是**不会回冻**，这条必须红）',
    mutate: (at) => {
      at.set('gen-gameplay-config.js', mustReplace(at.get('gen-gameplay-config.js'),
        "  { key: 'packIceToIceMinutes',",
        "  { key: 'iceHoleRefreezeMinutes', cn: '冰钓 · 冰洞回冻', unit: '分钟', def: 0, min: 0, max: 120,\n"
        + "    type: 'int', desc: '（这个键不该存在）' },\n  { key: 'packIceToIceMinutes',",
        'ice-refreeze-knob'));
    } },

  { dir: 'ice-pick-missing-ice', checker: 'check-ice-fishing.js', expect: 1,
    desc: '凿冰镐少认一种冰（frosted_ice 凿不出洞，玩家只会以为那种冰不能用）',
    mutate: (at) => {
      at.set('IcePickItem.java', mustReplace(at.get('IcePickItem.java'),
        '|| state.is(Blocks.FROSTED_ICE)', '', 'ice-pick-missing-ice'));
    } },

  { dir: 'ice-hole-refreeze', checker: 'check-ice-fishing.js', expect: 1,
    desc: '给 IceHoleBlock 加上回冻调度（直接推翻用户"冰洞不会回冻"的定案）',
    mutate: (at) => {
      at.set('IceHoleBlock.java', mustReplace(at.get('IceHoleBlock.java'),
        '        super(properties);\n    }',
        '        super(properties);\n    }\n\n'
        + '    @Override\n'
        + '    public void onPlace(net.minecraft.world.level.block.state.BlockState state,\n'
        + '                        net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos,\n'
        + '                        net.minecraft.world.level.block.state.BlockState oldState, boolean moved) {\n'
        + '        if (level instanceof net.minecraft.server.level.ServerLevel s) {\n'
        + '            s.scheduleTick(pos, this, 1200);\n'
        + '        }\n    }', 'ice-hole-refreeze'));
    } },

  { dir: 'ice-line-rod', checker: 'check-ice-fishing.js', expect: 1,
    desc: '把线损倍率顺手乘到**鱼竿**的损耗上（用户定案"只加鱼线，竿不加"）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'int base = Math.max(0, duraCfg.rodDurabilityPerBite.get());',
        'int base = Math.max(0, duraCfg.rodDurabilityPerBite.get()\n'
        + '                * (int) Math.round(IceFishing.lineWearMultiplier(iceLevel(), icePos())));',
        'ice-line-rod'));
    } },

  { dir: 'ice-hole-distance-clobbered', checker: 'check-ice-fishing.js', expect: 1,
    desc: '去掉"冰洞 12 米不许被真实距离覆盖"的门槛（= 用户报的 bug：冰洞上钩后显示 6 米）',
    mutate: (at) => {
      // 把 if 包着的两句还原成"无条件覆盖" —— 正是修复前那行代码。
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        '        if (!this.iceHoleCast) {\n'
        + '            this.castDistance = this.player.position().distanceTo(this.bobber.position());\n'
        + '        }',
        '        this.castDistance = this.player.position().distanceTo(this.bobber.position());',
        'ice-hole-distance-clobbered'));
    } },
  { dir: 'ice-hole-any-biome', checker: 'check-ice-fishing.js', expect: 1,
    desc: '把"洞内还要看群系"那道门槛去掉（等于"把冰运到平原挖个洞就能钓北极鳕"——'
      + '用户 2026-10-08 当场否掉的设计）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'if (IceFishing.inIceHole(waterLevel, this.bobber.blockPosition())\n'
        + '                    && WaterBody.iceFishingBiome(waterLevel, this.bobber.blockPosition())) {',
        'if (IceFishing.inIceHole(waterLevel, this.bobber.blockPosition())) {',
        'ice-hole-any-biome'));
    } },

  { dir: 'ice-buff-any-biome', checker: 'check-ice-fishing.js', expect: 1,
    desc: '去掉爆率加成的群系门槛（用户定案"不在那种群系就只生效线损耗"，'
      + '去掉后"把冰运到温带"就能白拿 +1%）',
    mutate: (at) => {
      at.set('IceFishing.java', mustReplace(at.get('IceFishing.java'),
        '        if (!WaterBody.iceFishingBiome(level, pos)) {\n            return 0.0;   // 不在覆冰名单里：洞内也只生效线损（用户定案）\n        }\n',
        '', 'ice-buff-any-biome'));
    } },

  { dir: 'polar-no-common-tier', checker: 'check-water-body.js', expect: 1,
    desc: '把极地三条常见档鱼全改成高档（极地水里没有低档鱼 —— 新手拿木竿/铁竿在极地钓不到东西）',
    mutate: (at) => {
      let s = at.get('FishSpecies.java');
      s = mustReplace(s, 'new FishSpecies("arctic_cod", ModItems.ARCTIC_COD, 0.050, 0.500, G_B,',
        'new FishSpecies("arctic_cod", ModItems.ARCTIC_COD, 0.050, 0.500, G_R,', 'polar-no-common-tier-1');
      s = mustReplace(s, 'new FishSpecies("capelin", ModItems.CAPELIN, 0.010, 0.100, W_G,',
        'new FishSpecies("capelin", ModItems.CAPELIN, 0.010, 0.100, G_R,', 'polar-no-common-tier-2');
      s = mustReplace(s, 'new FishSpecies("atlantic_herring", ModItems.ATLANTIC_HERRING, 0.100, 1.000, B_P,',
        'new FishSpecies("atlantic_herring", ModItems.ATLANTIC_HERRING, 0.100, 1.000, P_G,',
        'polar-no-common-tier-3');
      at.set('FishSpecies.java', s);
    } },

  // ---- /atl water 与 /atl pool 的取样点（第 0 批验收手段）----
  // ⚠ 2026-10-11：锚点从 `Commands.literal("water")` 改成 `lit("water")` ——
  //   子指令统一走 lit()（权限一处真源）。锚点不跟着改的话，
  //   症状是**整份负例阶段连跑都跑不起来**（"造副本失败"），而不是某条用例不符 ✗。
  { dir: 'water-cmd-missing', checker: 'check-water-body.js', expect: 1,
    desc: '删掉 /atl water（第 0 批的验收手段没了 —— 玩家没法自查"这片水判成了什么"）',
    mutate: (at) => {
      at.set('AnglerCommands.java', mustReplace(at.get('AnglerCommands.java'),
        '                .then(lit("water").executes(ctx -> {',
        '                .then(lit("waterX").executes(ctx -> {', 'water-cmd-missing'));
    } },

  { dir: 'water-pool-null', checker: 'check-water-body.js', expect: 1,
    desc: '把 /atl pool 的水体参数改回 null（体检报告会列出这片水里根本没有的鱼）',
    mutate: (at) => {
      at.set('AnglerCommands.java', mustReplace(at.get('AnglerCommands.java'),
        'null, hv, 0, water, habitMask)', 'null, hv, 0, null, habitMask)', 'water-pool-null'));
    } },

  { dir: 'water-probe-fixed', checker: 'check-water-body.js', expect: 1,
    desc: '取样点不再优先用浮漂落点（指令说的和实际判的可能不是同一片水）',
    mutate: (at) => {
      at.set('AnglerCommands.java', mustReplace(at.get('AnglerCommands.java'),
        'return s.bobber().blockPosition();', 'return player.blockPosition();', 'water-probe-fixed'));
    } },

  // ---- 鱼种数超过里程碑上限（2026-10-08）----
  // 真事：MAX_MILESTONES 原本是 15（= 30 种鱼），第 1 批冷水 5 条加完已经 33 种 ——
  // 第 31~33 种鱼点亮时**一个鱼币都不发**，没有报错也没有日志。
  // 这个负例把上限改成 34（越过 int 位掩码的符号位）：金币总数不变（仍是 800），
  // 所以红的**只可能**是那条新的"上限够不够用"断言，不会与别的断言混在一起。
  { dir: 'guidenum-milestone-cap', checker: 'check-guide-numbers.js', expect: 1,
    desc: '把 MAX_MILESTONES 改成 34（越过 int 位掩码的符号位，表现是"图鉴满了却再也不发奖励"）',
    mutate: (at) => {
      at.set('CodexRewards.java', mustReplace(at.get('CodexRewards.java'),
        'private static final int MAX_MILESTONES = 25;',
        'private static final int MAX_MILESTONES = 34;', 'guidenum-milestone-cap'));
    } },

  // ---- 台钓取消档位上限（用户 2026-10-02："取消台钓的档位上限机制并同步文本"）----
  { dir: 'guidenum-float-cap-back', checker: 'check-guide-numbers.js', expect: 1,
    desc: '把台钓竿的档位上限加回来（用户已经取消了这条机制）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        '            if (rodItem.rodType() == com.whitecobalt.anglertackle.item.RodItem.RodType.FLOAT) {\n'
        + '                return 5;\n'
        + '            }\n',
        '', 'guidenum-float-cap-back'));
    } },

  // ---- check-guide-numbers.js：2026-10-12 新增的三章（称号 / 联赛 / 比赛）----
  //   这三章讲的全是配置里的数字 ⇒ 每条断言都要能"改一个数字就红"（都已手工证伪过）。
  { dir: 'guidenum-titles-count', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册「称号」章的个数写成 38，而 TitleDefs 里是 40 ⇒ 手册在撒谎',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'), '共 40 个称号', '共 38 个称号',
        'guidenum-titles-count'));
    } },
  { dir: 'guidenum-league-window', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册「联赛」章把"签收后 30 游戏分钟"写成 20 ⇒ 玩家按错的窗口安排时间',
    mutate: (at) => {
      // ⚠ 锚点里**不许带 Markdown 的 \*\***：手册正文里的星号已经被 check-guide-sync 清掉了，
      //   带着星号的锚点会匹配不上 ⇒ 造副本阶段抛异常（那次的闸报的还是那句误导性的
      //   "存在漏检的检查脚本"）。锚点要用**会留下来的那部分文本**。
      at.set('guide.json', mustReplace(at.get('guide.json'), '签收那一刻起，30 游戏分钟',
        '签收那一刻起，20 游戏分钟', 'guidenum-league-window'));
    } },
  { dir: 'guidenum-contest-reward', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册「比赛」章把冠军奖励写成 999 ⇒ 与配置对不上',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'), '第 1 名 300 鱼币', '第 1 名 999 鱼币',
        'guidenum-contest-reward'));
    } },
  { dir: 'guidenum-contest-chapter-gone', checker: 'check-guide-numbers.js', expect: 1,
    desc: '把「比赛」那一章从手册里删掉 ⇒ 新系统上线却没有对应的那一章',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'), '"id": "contest"',
        '"id": "contest_removed"', 'guidenum-contest-chapter-gone'));
    } },

  // ---- check-mail.js：用户 2026-10-12 报的三个界面 bug（配截图）----  //   这三条都是"不报错、只是玩家读不到 / 看不懂"：正文被截断、正文没法翻、
  //   以及服务端发来的枚举 id 没在客户端翻译（截图里写着「第 1 期small联赛邀请」）。
  { dir: 'mail-body-truncated', checker: 'check-mail.js', expect: 1,
    desc: '正文行数写死 6 行 ⇒ 长信被附件区拦腰截断（用户截图："到点不管有没有打满一律" 就断在那儿）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), 'int maxLines = Math.max(1, (bodyBottom - ty) / LINE_STEP);',
        'int maxLines = 6;', 'mail-body-truncated'));
    } },
  { dir: 'mail-no-body-scroll', checker: 'check-mail.js', expect: 1,
    desc: '详情栏上的滚轮不再滚正文 ⇒ 长信读不到后面（用户原话："没有类似滚轮或翻页的设计"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        if (mx >= detX && mx <= detX + detW && selected() != null) {',
        '        if (false) {', 'mail-no-body-scroll'));
    } },
  { dir: 'mail-no-scroll-hint', checker: 'check-mail.js', expect: 1,
    desc: '去掉滚动指示 ⇒ 玩家不知道下面还有字（这正是那次没发现"能滚"的原因）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), 'if (maxScroll > 0) {', 'if (false) {',
        'mail-no-scroll-hint'));
    } },
  { dir: 'mail-tier-not-localized', checker: 'check-mail.js', expect: 1,
    desc: '邮件参数不翻译 ⇒ 标题显示「第 1 期small联赛邀请」（截图里就是这个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      // ⚠ 锚点在 2026-10-14 的"委托邮件要能翻 #语言键"重写里被换过一次
      //   （原文那行 `boolean tier = ...` 改成了 if / else-if 结构）
      //   —— 又是"改实现要顺手改负例锚点"的那一类，见文件顶部 --check-anchors 的说明。
      at.set(F, mustReplace(at.get(F), 'if (r.template.startsWith("league") && i == 1',
        'if (false && i == 1', 'mail-tier-not-localized'));
    } },
  { dir: 'mail-markdown-back', checker: 'check-mail.js', expect: 1,
    desc: '把 Markdown 的 ** 放回邮件正文（游戏不渲染，玩家看到多出来的星号）',
    mutate: (at) => {
      // ⚠ 锚点在 2026-10-12 的"短句改写"里被换过一次（原文改成了 `签收后你有 %s 游戏分钟，`）
      //   —— 这正是"改文案要顺手改负例锚点"的那一类；见文件顶部 `--check-anchors` 的说明。
      const F = 'src/main/resources/assets/anglertackle/lang/zh_cn.json';
      at.set(F, mustReplace(at.get(F), '这一笔是 %s 鱼币。',
        '这一笔是 **%s 鱼币**。', 'mail-markdown-back'));
    } },

  // ---- check-mail.js / check-codex.js / check-anvil-return.js：2026-10-12 第二次实测之后新增 ----
  //   这一批守的是"正文能读、不压字"与"奖励改成寄信之后不许静默丢"。
  { dir: 'mail-no-scrollbar', checker: 'check-mail.js', expect: 1,
    desc: '删掉细滚动条 ⇒ 长信读不到后面（用户："没有类似滚轮或翻页的设计"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, C_LABEL);',
        '            // 滚动条被删掉（负例）', 'mail-no-scrollbar'));
    } },
  { dir: 'mail-body-line-too-long', checker: 'check-mail.js', expect: 1,
    desc: '正文写成一句长句 ⇒ 折行折在"5 / 游戏日"之间，用户原话"读起来戛然而止"',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/lang/zh_cn.json';
      at.set(F, mustReplace(at.get(F), '点【签收】＝领见面礼 + 报名。',
        '点【签收】＝领取这份见面礼并且报名参赛，之后你钓到的每一条鱼都会计入本期联赛的成绩表。',
        'mail-body-line-too-long'));
    } },
  { dir: 'mail-no-wrapcjk', checker: 'check-mail.js', expect: 1,
    desc: '正文折行退回 font.split（逐字符填）⇒ "5 游戏日"被从中间劈开',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        'java.util.List<String> lines = UiLayout.wrapCjk(body.getString(), Math.max(40, w - 8));',
        'java.util.List<String> lines = java.util.List.of(body.getString());', 'mail-no-wrapcjk'));
    } },
  { dir: 'codex-mark-before-send', checker: 'check-codex.js', expect: 1,
    desc: '图鉴里程碑"先置位、后投递" ⇒ 邮箱满时那笔钱永久拿不到，而且不报错',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/economy/CodexRewards.java';
      at.set(F, mustReplace(at.get(F), '        long coins = pending * COINS_PER_MILESTONE;',
        '        PlayerData.update(player, d -> d.markClaimed(0));\n'
        + '        long coins = pending * COINS_PER_MILESTONE;', 'codex-mark-before-send'));
    } },
  { dir: 'codex-mark-in-full-box', checker: 'check-codex.js', expect: 1,
    desc: '满箱分支里照样置位 ⇒ 奖励静默消失（与"先投递后置位"配套的第二道闸）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/economy/CodexRewards.java';
      at.set(F, mustReplace(at.get(F),
        '                    Tuning.mailMaxMails()).withStyle(ChatFormatting.RED), false);',
        '                    Tuning.mailMaxMails()).withStyle(ChatFormatting.RED), false);\n'
        + '            PlayerData.update(player, d -> d.markClaimed(0));', 'codex-mark-in-full-box'));
    } },
  { dir: 'mailreturn-no-drop-fallback', checker: 'check-anvil-return.js', expect: 1,
    desc: 'MailReturns 去掉"掉在脚下"兜底 ⇒ 邮箱也满时配件凭空消失（比改造前更糟）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailReturns.java';
      at.set(F, mustReplace(at.get(F), '        player.drop(stack, false);',
        '        // 兜底被删掉（负例）', 'mailreturn-no-drop-fallback'));
    } },

  // ---- check-tuning-tool.js（调参器页面：静态检查 + 无头执行一遍）----
  // ⚠ 这一块以前**一个负例都没有**：副本里缺 tools/browser-shim.js（无头执行要 require 它）
  //   与 tuner.html（要和 调参器.html 逐字节比对），检查器在副本里根本跑不起来 ✗。
  //   两条文件已经补进 FILES；下面第一条就是"未改动的副本必须通过"的对照组。
  { dir: 'tuner-ok', checker: 'check-tuning-tool.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过 —— 它同时证明副本里四个文件都带齐了）',
    mutate: () => {} },
  { dir: 'tuner-revert-broken', checker: 'check-tuning-tool.js', expect: 1,
    desc: '「还原已选为默认值」被改成不还原（after = before，等于点了没反应）—— 必须红',
    mutate: (at) => {
      // 两个文件一起改：调参器.html 是检查器真正跑的生成物，tuner.html 要与它逐字节一致
      //（只改一个的话，红的理由会变成"两份不一致"，而不是"还原失效" ✗ 红得不对地方）。
      const broken = (src) => mustReplace(src, 'const after = f.def;', 'const after = before;',
        'tuner-revert-broken');
      at.set('调参器.html', broken(at.get('调参器.html')));
      at.set('tuner.html', broken(at.get('tuner.html')));
    } },

  { dir: 'guidenum-tiercap-drift', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册把铁竿的"路亚上限"写成紫档（配置里是金档）—— 玩家会照着错的档位判断能钓什么鱼',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '铁竿    承重 30 kg    路亚上限 金档',
        '铁竿    承重 30 kg    路亚上限 紫档', 'guidenum-tiercap-drift'));
    } },
  { dir: 'guidenum-tiercap-text', checker: 'check-guide-numbers.js', expect: 1,
    desc: '手册又写回"上限X档"的老表（那是"所有竿都有上限"时代的说法）',
    mutate: (at) => {
      // ⚠ 锚点用的是手册**当前**那一行：2026-10-08 用户把木竿上限改成 3（紫档）之后，
      //   这里原来的"路亚上限 绿档"就不存在了 —— mustReplace 匹配不上会当场抛错、
      //   整个负例批次中止 ✗。改手册的竿阶梯时记得回来同步这条。
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  木竿    承重 8 kg     路亚上限 紫档",',
        '"  木竿    上限紫档   承重 8 kg",', 'guidenum-tiercap-text'));
    } },

  // ---- check-hut-loot.js（村庄渔夫小屋也要能刷出 mod 鱼）----
  { dir: 'villageloot-ok', checker: 'check-hut-loot.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'villageloot-wrong-chest', checker: 'check-hut-loot.js', expect: 1,
    desc: '注入到别的箱子（不是原版渔夫小屋）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/loot_modifiers/village_fisher_fish.json';
      at.set(p, mustReplace(at.get(p),
        '"minecraft:chests/village/village_fisher"', '"minecraft:chests/abandoned_mineshaft"',
        'villageloot-wrong-chest'));
    } },

  { dir: 'villageloot-not-listed', checker: 'check-hut-loot.js', expect: 1,
    desc: '修饰符没登记进 global_loot_modifiers.json（根本不会被加载）',
    mutate: (at) => {
      const p = 'src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json';
      at.set(p, mustReplace(at.get(p),
        ',\n    "anglertackle:village_fisher_fish"', '', 'villageloot-not-listed'));
    } },

  { dir: 'villageloot-chance-drift', checker: 'check-hut-loot.js', expect: 1,
    desc: '村庄那一池的概率与小屋不一致（用户要求"数值相同"）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/loot_table/inject/village_fisher_fish.json';
      at.set(p, mustReplace(at.get(p), '"chance": 0.015', '"chance": 0.15',
        'villageloot-chance-drift'));
    } },

  // ---- check-fish-appearance.js ----
  { dir: 'fish-ok', checker: 'check-fish-appearance.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', extra: EXTRA.fish, mutate: () => {} },

  { dir: 'fish-color-invented', checker: 'check-fish-appearance.js', expect: 1,
    desc: '把鲫鱼背部改成"看着像"的编造色（调研资料里没有这个值）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"back": "#4A5A3C"', '"back": "#4C5B3E"', 'fish-color-invented'));
    } },

  { dir: 'fish-species-missing', checker: 'check-fish-appearance.js', expect: 1,
    desc: '外形数据里删掉一种鱼（标签/实体比它多）',
    extra: EXTRA.fish,
    mutate: (at) => {
      const j = JSON.parse(at.get('fish-appearance.json'));
      delete j.fish.pike;
      at.set('fish-appearance.json', JSON.stringify(j, null, 2));
    } },

  { dir: 'fish-group-unknown', checker: 'check-fish-appearance.js', expect: 1,
    desc: '把一种鱼指到不存在的体型组 Z',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"group": "D", "lengthCm": 70', '"group": "Z", "lengthCm": 70', 'fish-group-unknown'));
    } },

  { dir: 'fish-pattern-unknown', checker: 'check-fish-appearance.js', expect: 1,
    desc: '花纹写成生成器不认识的名字（会被静默忽略，鱼变素色）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"pattern": ["bars"], "patternContrast": 0.75', '"pattern": ["barz"], "patternContrast": 0.75',
        'fish-pattern-unknown'));
    } },

  { dir: 'fish-shape-unknown', checker: 'check-fish-appearance.js', expect: 1,
    desc: '形状参数写了 buildParts 不认识的名字（会被静默忽略，鱼没变化）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"secondDorsal": true, "secondDorsalPos": 0.74',
        '"secondDorsal": true, "secondDorsallPos": 0.74', 'fish-shape-unknown'));
    } },

  { dir: 'fish-barbel-too-long', checker: 'check-fish-appearance.js', expect: 1,
    desc: '把口须加长到 6 单位（就是用户说的"嘴上两根很诡异的长条"）',
    extra: EXTRA.fish,
    mutate: (at) => {
      // 象鼻鱼是唯一保留须的鱼（长吻鱼只有一根斜须），拿它做负例
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"barbels": 1, "barbelLen": 2,\n                 "barbelAt": "lower", "barbelDroop": 1.6',
        '"barbels": 1, "barbelLen": 6,\n                 "barbelAt": "lower", "barbelDroop": 1.6',
        'fish-barbel-too-long'));
    } },

  // ---- check-fish-model.js ----
  { dir: 'model-ok', checker: 'check-fish-model.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', extra: EXTRA.fish, mutate: () => {} },

  { dir: 'model-java-drift', checker: 'check-fish-model.js', expect: 1,
    desc: '手改了 FishVariants 的缩放（生成物与数据源漂移）',
    extra: EXTRA.fish,
    mutate: (at) => {
      // ⚠ 不要写死具体数值：体长算法一调锚点就失配，用例本身得跟着改一轮。
      //   用正则只匹配"前缀 + 第一个数字"，数值怎么变都命得中。
      //   FishVariants 在改成"一种鱼一层"之后只剩 sx/sy/sz，不再有 group 参数。
      const src = at.get('FishVariants.java');
      const out = src.replace(/(BY_SPECIES\.put\("pike", new Variant\()[0-9.]+F/, '$11.9F');
      if (out === src) throw new Error('负例构造失败「model-java-drift」：找不到 pike 的缩放行');
      at.set('FishVariants.java', out);
    } },

  { dir: 'model-texture-missing', checker: 'check-fish-model.js', expect: 1,
    desc: '删掉一种鱼的贴图（游戏里会变成紫黑格）',
    extra: EXTRA.fish,
    deleteFiles: ['textures/entity/fish/pike.png'] },

  { dir: 'model-texture-blank', checker: 'check-fish-model.js', expect: 1,
    desc: '把贴图整张涂成全透明（零件直接看不见，不报错）',
    extra: EXTRA.fish,
    mutatePng: (dest) => {
      // ⚠ 尺寸必须是**该鱼自己的图集尺寸**（pike 现在是 64×32）。
      //   写成 64×64 会以"尺寸不对"变红 —— 那样断言的理由就不是我们要验的那一条了。
      const S = require('./fish-model.js').species.pike;
      writeRgbaPng(path.join(dest, 'src/main/resources/assets/anglertackle/textures/entity/fish/pike.png'),
        S.atlasW, S.atlasH, () => [0, 0, 0, 0]);
    } },

  { dir: 'model-no-template', checker: 'check-fish-model.js', expect: 1,
    desc: '删掉某条鱼的模板声明（会静默退回 v3 几何，正是"不如第一版"的那一版）',
    extra: EXTRA.fish,
    mutate: (at) => {
      const j = JSON.parse(at.get('fish-appearance.json'));
      delete j.fish.pike.shape.style;
      at.set('fish-appearance.json', JSON.stringify(j, null, 2));
    } },

  { dir: 'model-renderer-unwired', checker: 'check-fish-model.js', expect: 1,
    desc: '渲染器不再施加 FishVariants 的缩放（21 种鱼全长一个尺寸）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('ModFishRenderer.java', mustReplace(at.get('ModFishRenderer.java'),
        'pose.scale(v.sx(), v.sy(), v.sz());', 'pose.scale(1.0F, 1.0F, 1.0F);',
        'model-renderer-unwired'));
    } },

  { dir: 'model-client-hardcoded', checker: 'check-fish-model.js', expect: 1,
    desc: 'ClientSetup 把鱼种写死（加鱼种/换鱼种时静默错位，鱼变成空气）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('ClientSetup.java', mustReplace(at.get('ClientSetup.java'),
        'String species = e.getKey();', 'String species = "mullet";',
        'model-client-hardcoded'));
    } },

  { dir: 'model-hitbox-hardcoded', checker: 'check-fish-model.js', expect: 1,
    desc: '把手写的 SIZE_OF 尺寸表加回 ModEntities（碰撞箱出现第二个真源）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('ModEntities.java', mustReplace(at.get('ModEntities.java'),
        'public static final java.util.Map<String, DeferredHolder<EntityType<?>, EntityType<com.whitecobalt.anglertackle.entity.ModFish>>> FISHES =',
        'private static final java.util.Map<String, String> SIZE_OF =\n' +
        '            java.util.Map.ofEntries(java.util.Map.entry("arapaima", "1.1f, 0.6f"));\n\n' +
        '    public static final java.util.Map<String, DeferredHolder<EntityType<?>, EntityType<com.whitecobalt.anglertackle.entity.ModFish>>> FISHES =',
        'model-hitbox-hardcoded'));
    } },

  { dir: 'model-stale-variants', checker: 'check-fish-model.js', expect: 1,
    desc: '旧的 client/FishVariants.java 又冒出来（两个真源会慢慢走散）',
    extra: EXTRA.fish,
    createFiles: [['src/main/java/com/whitecobalt/anglertackle/client/FishVariants.java',
      'package com.whitecobalt.anglertackle.client;\n\npublic final class FishVariants {\n}\n']] },

  { dir: 'fish-big-shrank', checker: 'check-fish-appearance.js', expect: 1,
    desc: '把巨骨舌鱼的体长改小（"该大的要大"这条断言应当抓住）',
    extra: EXTRA.fish,
    mutate: (at) => {
      at.set('fish-appearance.json', mustReplace(at.get('fish-appearance.json'),
        '"group": "D", "lengthCm": 200,\n      "colors": { "back": "#4E6157"',
        '"group": "D", "lengthCm": 45,\n      "colors": { "back": "#4E6157"',
        'fish-big-shrank'));
    } },

  // ---- check-spawn-egg.js（鱼的蛋是数据驱动的，走另一条分支） ----
  { dir: 'egg-fish-ok', checker: 'check-spawn-egg.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'egg-fish-not-in-tab', checker: 'check-spawn-egg.js', expect: 1,
    desc: '把虎鱼刷怪蛋从创造页签里拿掉（游戏里拿不到）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        '                        output.accept(TIGERFISH_SPAWN_EGG.get());\n', '',
        'egg-fish-not-in-tab'));
    } },

  { dir: 'egg-fish-wrong-base', checker: 'check-spawn-egg.js', expect: 1,
    desc: '让 FishSpawnEggItem 改继承原版 SpawnEggItem（踩回"注册顺序"的坑）',
    mutate: (at) => {
      at.set('FishSpawnEggItem.java', mustReplace(at.get('FishSpawnEggItem.java'),
        'public class FishSpawnEggItem extends DeferredSpawnEggItem {',
        'public class FishSpawnEggItem extends net.minecraft.world.item.SpawnEggItem {',
        'egg-fish-wrong-base'));
    } },

  { dir: 'egg-fish-no-lang', checker: 'check-spawn-egg.js', expect: 1,
    desc: '删掉巨骨舌鱼刷怪蛋的中文名（tooltip 会显示成键名）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '  "item.anglertackle.arapaima_spawn_egg": "巨骨舌鱼刷怪蛋",\n', '',
        'egg-fish-no-lang'));
    } },

  { dir: 'egg-fish-missing-one', checker: 'check-spawn-egg.js', expect: 1,
    desc: '漏掉狗鱼的刷怪蛋（21 种里少一个，肉眼很难发现）',
    mutate: (at) => {
      const s = at.get('ModItems.java');
      const head = '    public static final DeferredItem<com.whitecobalt.anglertackle.item.FishSpawnEggItem> PIKE_SPAWN_EGG =';
      const from = s.indexOf(head);
      if (from < 0) throw new Error('负例构造失败：找不到 PIKE_SPAWN_EGG');
      const end = s.indexOf('new Item.Properties()));', from) + 'new Item.Properties()));'.length;
      at.set('ModItems.java', s.slice(0, from) + s.slice(end));
    } },

  // ---- check-line-anchor.js（鱼线的上端必须接在竿尖上）----
  { dir: 'line-ok', checker: 'check-line-anchor.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'line-no-exact-tip', checker: 'check-line-anchor.js', expect: 1,
    desc: '第一人称不再用 RodTipAnchor.worldTip()（退回"照抄原版常数"，线头又离开竿尖）',
    mutate: (at) => {
      const s = at.get('FishingLineRenderer.java');
      const from = s.indexOf('            Vec3 exact = RodTipAnchor.worldTip');
      if (from < 0) throw new Error('负例构造失败：找不到 worldTip 调用点');
      const to = s.indexOf('\n            }\n', from) + '\n            }\n'.length;
      at.set('FishingLineRenderer.java', s.slice(0, from) + s.slice(to));
    } },

  { dir: 'line-no-fallback', checker: 'check-line-anchor.js', expect: 1,
    desc: '删掉原版近平面兜底（第一帧还没渲染过手时会没有竿尖可用）',
    mutate: (at) => {
      at.set('FishingLineRenderer.java', mustReplace(at.get('FishingLineRenderer.java'),
        '.getNearPlane()', '.getPosition()', 'line-no-fallback'));
    } },

  { dir: 'line-offhand-overwrite', checker: 'check-line-anchor.js', expect: 1,
    desc: '去掉"双手都有竿时主手优先"（副手后渲染会覆盖，线挂到另一只手）',
    mutate: (at) => {
      at.set('RodTipAnchor.java', mustReplace(at.get('RodTipAnchor.java'),
        '        if (event.getHand() == InteractionHand.OFF_HAND\n'
        + '                && player.getMainHandItem().getItem() instanceof RodItem) {\n'
        + '            return;\n'
        + '        }',
        '        // 副手不再跳过', 'line-offhand-overwrite'));
    } },

  { dir: 'line-no-center', checker: 'check-line-anchor.js', expect: 1,
    desc: '去掉模型空间居中的 translate(−0.5,−0.5,−0.5)（整体偏半格）',
    mutate: (at) => {
      at.set('RodTipAnchor.java', mustReplace(at.get('RodTipAnchor.java'),
        '        ps.translate(-0.5F, -0.5F, -0.5F);', '', 'line-no-center'));
    } },

  // ⚠ 2026-10-02 用户第二次报的就是这个："端点不固定、摇视角反着跑、落进玩家身体里"。
  //   手部 PoseStack 里乘的是"相机旋转的逆"，必须乘回 modelview 才是相机空间。
  { dir: 'line-no-modelview', checker: 'check-line-anchor.js', expect: 1,
    desc: '去掉"乘回 modelview"这一步（端点随视角反向乱跑，就是本次事故本身）',
    mutate: (at) => {
      at.set('RodTipAnchor.java', mustReplace(at.get('RodTipAnchor.java'),
        '\n        tip.mul(RenderSystem.getModelViewMatrix());', '', 'line-no-modelview'));
    } },

  { dir: 'line-camera-basis', checker: 'check-line-anchor.js', expect: 1,
    desc: '世界坐标改成"相机位置 + 相机空间偏移"（不做正交基换算，线头随视角乱飞）',
    mutate: (at) => {
      at.set('RodTipAnchor.java', mustReplace(at.get('RodTipAnchor.java'),
        '        return new Vec3(\n'
        + '                pos.x - left.x() * tip.x + up.x() * tip.y - look.x() * tip.z,\n'
        + '                pos.y - left.y() * tip.x + up.y() * tip.y - look.y() * tip.z,\n'
        + '                pos.z - left.z() * tip.x + up.z() * tip.y - look.z() * tip.z);',
        '        return pos.add(tip);', 'line-camera-basis'));
    } },

  { dir: 'line-tip-const-drift', checker: 'check-line-anchor.js', expect: 1,
    desc: '某把竿的竿尖与图标对不上（改了表没改图 / 改了图没改表）',
    // ⚠ 2026-10-11：真源从"两个常量"改成 **TIP_BY_ITEM 逐把一张表**（用户口径：
    //   "贴图别改，要改只能改鱼线位置，让每一把杆的鱼线显示有自己的位置"）
    //   ⇒ 这条负例改成**改表里的一项**，改常量已经抓不到了 ✗。
    //   同样不写死数值：按"当前值 +2"推导（写死的话，以后改表就会像 2026-10-02 那样把负例弄抛异常）。
    mutate: (at) => {
      const s = at.get('RodTipAnchor.java');
      const m = /entry\("wood_lure_rod",\s*new double\[\]\{\s*([0-9.]+)\s*,\s*([0-9.]+)\s*\}/.exec(s);
      if (!m) throw new Error('负例构造失败：TIP_BY_ITEM 里找不到 wood_lure_rod');
      at.set('RodTipAnchor.java', s.replace(m[0],
        'entry("wood_lure_rod", new double[]{' + (Number(m[1]) + 2) + '.0, ' + m[2] + '}'));
    } },

  { dir: 'line-display-transform', checker: 'check-line-anchor.js', expect: 1,
    desc: '不用物品自己的 firstperson 显示变换（写死 GUI 那套）',
    mutate: (at) => {
      at.set('RodTipAnchor.java', mustReplace(at.get('RodTipAnchor.java'),
        'right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND',
        'ItemDisplayContext.GUI', 'line-display-transform'));
    } },

  { dir: 'line-useanim-changed', checker: 'check-line-anchor.js', expect: 1,
    desc: '竿的使用动画从 BOW 换成 SPEAR（追踪器只复刻了 BOW 那条分支）',
    mutate: (at) => {
      at.set('RodItem.java', mustReplace(at.get('RodItem.java'),
        'return net.minecraft.world.item.UseAnim.BOW;',
        'return net.minecraft.world.item.UseAnim.SPEAR;', 'line-useanim-changed'));
    } },

  { dir: 'line-clear-gone', checker: 'check-line-anchor.js', expect: 1,
    desc: '退出世界不再清竿尖记录（新世界第一帧会用上一个位置）',
    mutate: (at) => {
      at.set('ClientEvents.java', mustReplace(at.get('ClientEvents.java'),
        'com.whitecobalt.anglertackle.client.render.RodTipAnchor.clear();', '',
        'line-clear-gone'));
    } },

  // ---- check-rod-icon-quality.js（竿梢必须是尖的）----
  { dir: 'rod-icon-ok', checker: 'check-rod-icon-quality.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："需要做一下竿'尖'，现在像是平的。"
  // 断言分两半：图标 PNG 真的收尖了 / 生成器里的收尖常量还在。
  // 这条负例打的是后一半 —— 改了常量却忘了重跑生成器时，只验 PNG 会漏过去。
  { dir: 'rod-tip-flat', checker: 'check-rod-icon-quality.js', expect: 1,
    desc: '把生成器的收尖关掉（TIP_TAPER_START = 1.0，竿梢又变成平口）',
    mutate: (at) => {
      const s = at.get('gen-lure-rods.js');
      const m = /TIP_TAPER_START = Number\(process\.env\.ATL_LURE_TAPER \?\? [0-9.]+\)/.exec(s);
      if (!m) throw new Error('负例构造失败：找不到 TIP_TAPER_START');
      at.set('gen-lure-rods.js', s.replace(m[0],
        'TIP_TAPER_START = Number(process.env.ATL_LURE_TAPER ?? 1.0)'));
    } },

  // ---- check-bobber-cast.js（抛竿不能原地落水）----
  { dir: 'bobber-ok', checker: 'check-bobber-cast.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："只要靠近水源或坐在船上就容易原地下漂。"
  // 打的是"落水判定只看下面有没有水、不看自己降没降到水面"这个根因。
  { dir: 'bobber-instant-land', checker: 'check-bobber-cast.js', expect: 1,
    desc: '落水判定去掉"必须降到水面"那一步（漂出竿第一 tick 就砸在脚下）',
    mutate: (at) => {
      at.set('BobberEntity.java', mustReplace(at.get('BobberEntity.java'),
        '                // ★ 只有"漂降到水面"才算落水（见方法头那段说明）\n'
        + '                double surface = p.getY() + this.level().getFluidState(p).getHeight(this.level(), p);\n'
        + '                if (py <= surface + WATER_LAND_MARGIN) {\n'
        + '                    return p;\n'
        + '                }',
        '                return p;', 'bobber-instant-land'));
    } },

  // 用户 2026-10-02："希望低头落点平视落点抬头落点跟之前差不多。"
  // 打的是"射程反解按等高落地算、漏掉起点比水面高约 1.9 格"这个根因。
  { dir: 'bobber-solve-no-drop', checker: 'check-bobber-cast.js', expect: 1,
    desc: '射程反解不带落差（低头 8 米实际打成 13.6 米）',
    mutate: (at) => {
      at.set('FishingManager.java', mustReplace(at.get('FishingManager.java'),
        'solveSpeed(distance, angle, drop)', 'solveSpeed(distance, angle, 0.0)',
        'bobber-solve-no-drop'));
    } },

  // ---- check-chum-throw.js（打窝料要丢得出去、还要够远）----
  { dir: 'chum-ok', checker: 'check-chum-throw.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："打窝料可以稍微再抛远一点。"
  { dir: 'chum-early-land', checker: 'check-chum-throw.js', expect: 1,
    desc: '落水判定变回"脚下 0~−2 格有水就落"（窝料离水面两三格就停下）',
    mutate: (at) => {
      at.set('ChumEntity.java', mustReplace(at.get('ChumEntity.java'),
        '            double surface = p.getY() + this.level().getFluidState(p).getHeight(this.level(), p);\n'
        + '            if (this.getY() <= surface + WATER_LAND_MARGIN) {\n'
        + '                return p;\n'
        + '            }',
        '            return p;', 'chum-early-land'));
    } },

  { dir: 'chum-slow', checker: 'check-chum-throw.js', expect: 1,
    desc: '初速被调回 0.75（抬头 45° 只有 16.8 米）',
    mutate: (at) => {
      at.set('ChumEntity.java', mustReplace(at.get('ChumEntity.java'),
        'THROW_SPEED = 0.85', 'THROW_SPEED = 0.75', 'chum-slow'));
    } },

  // ---- check-ui-fit.js（四个界面都要装得进窗口）----
  { dir: 'uifit-ok', checker: 'check-ui-fit.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："很明显，图鉴的内容屏幕装不下了。" → 四个界面一起改成自适应
  { dir: 'uifit-fixed-panel', checker: 'check-ui-fit.js', expect: 1,
    desc: 'UiLayout 不再按窗口 clamp（面板又变成写死的设计尺寸）',
    mutate: (at) => {
      at.set('UiLayout.java', mustReplace(at.get('UiLayout.java'),
        'return Math.max(minW, Math.min(designW, windowW - MARGIN));',
        'return designW;', 'uifit-fixed-panel'));
    } },

  { dir: 'uifit-fixed-rows', checker: 'check-ui-fit.js', expect: 1,
    desc: '指南界面每页行数写死 13（不按面板高度算）',
    mutate: (at) => {
      at.set('GuideScreen.java', mustReplace(at.get('GuideScreen.java'),
        'rowsPerPage = UiLayout.rows(panelH, BODY_TOP, FOOTER_H, LINE_STEP);',
        'rowsPerPage = 13;', 'uifit-fixed-rows'));
    } },

  { dir: 'uifit-no-truncate', checker: 'check-ui-fit.js', expect: 1,
    desc: '超宽文字不截断（窄面板下会压出边框）',
    mutate: (at) => {
      at.set('UiLayout.java', mustReplace(at.get('UiLayout.java'),
        'return font.plainSubstrByWidth(text, room) + dots;',
        'return text;', 'uifit-no-truncate'));
    } },

  // ---- check-grindstone.js（砂轮：洗附魔 + 拆配件 + 配件带回原耐久）----
  { dir: 'grindstone-ok', checker: 'check-grindstone.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："砂轮卸载鱼竿配件时不会返还物品……配件保留使用过的耐久数据"
  { dir: 'grindstone-rebuild-part', checker: 'check-grindstone.js', expect: 1,
    desc: '拆配件时按 id 重造物品栈（耐久回满 —— 用户明确不许）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/fishing/RodModManager.java';
      at.set(p, mustReplace(at.get(p),
        '            out.add(part.copy());   // copy：不动竿上那份记录，避免两份数据互相影响',
        '            out.add(new ItemStack(BuiltInRegistries.ITEM.get('
        + 'ResourceLocation.tryParse(idOf(part)))));', 'grindstone-rebuild-part'));
    } },

  { dir: 'grindstone-memmap', checker: 'check-grindstone.js', expect: 1,
    desc: '待退配件改回"用 ItemStack 当 Map 键"（取走时对不上，配件退不回来）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java';
      at.set(p, mustReplace(at.get(p),
        '    private GrindstoneEvents() {\n    }',
        '    private static final java.util.Map<ItemStack, java.util.List<ItemStack>> PENDING ='
        + ' new java.util.HashMap<>();\n\n    private GrindstoneEvents() {\n    }',
        'grindstone-memmap'));
    } },

  // ⚠ 这一条是**真正的回归守卫**：修好"用 ItemStack 当键"之后配件仍然退不回，
  //   因为取走时读的是 getNewTopItem() —— 那是输入槽的残余（默认 EMPTY），不是产出物。
  { dir: 'grindstone-newtop', checker: 'check-grindstone.js', expect: 1,
    desc: '取走时把 getNewTopItem() 当成产出物（它是输入槽的残余，默认 EMPTY → 静默退回 0 个）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java';
      at.set(p, mustReplace(at.get(p),
        '        ItemStack rod = rodOf(event.getTopItem(), event.getBottomItem());\n'
        + '        if (rod.isEmpty() || !RodModManager.hasAnyPart(rod)) {\n'
        + '            return;\n'
        + '        }\n\n'
        + '        List<ItemStack> parts = RodModManager.stripAllParts(rod);',
        '        ItemStack rod = rodOf(event.getNewTopItem(), event.getNewBottomItem());\n'
        + '        if (rod.isEmpty() || !RodModManager.hasAnyPart(rod)) {\n'
        + '            return;\n'
        + '        }\n\n'
        + '        List<ItemStack> parts = RodModManager.stripAllParts(rod);',
        'grindstone-newtop'));
    } },

  { dir: 'grindstone-client-refund', checker: 'check-grindstone.js', expect: 1,
    desc: '去掉服务端守卫（客户端预测也退一份，出现幽灵配件）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java';
      at.set(p, mustReplace(at.get(p),
        'if (player == null || player.level().isClientSide()) {', 'if (player == null) {',
        'grindstone-client-refund'));
    } },

  { dir: 'grindstone-full-lost', checker: 'check-grindstone.js', expect: 1,
    desc: '背包满时不做兜底（配件凭空消失）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java';
      // ⚠ 锚点跟着实现走（2026-10-12 M4 之后这里已经是"寄信 or 掉脚下"）：
      //   本用例把它换成"只 add、什么都不兜底"，也就是修复前那个会丢件的写法。
      at.set(p, mustReplace(at.get(p),
        '            if (!player.getInventory().add(p)) {\n'
        + '                // 背包满 ⇒ **先寄信**（`docs/36` §4.1 第 10 条，2026-10-12 M4 改造）；\n'
        + '                // 邮箱也满 ⇒ MailReturns 内部退回"掉在脚下" ✓ 两处共用同一个兜底策略。\n'
        + '                com.whitecobalt.anglertackle.mail.MailReturns.toMailboxOrDrop(player, p, "return_item");\n'
        + '            }',
        '            player.getInventory().add(p);', 'grindstone-full-lost'));
    } },
  { dir: 'mail-markdown-back2', checker: 'check-mail.js', expect: 1,
    desc: '把 Markdown 的 ** 放回邮件正文（锚点跟着 2026-10-12 的短句改写更新）',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/lang/zh_cn.json';
      at.set(F, mustReplace(at.get(F), '签收后你有 %s 游戏分钟，',
        '签收后你有 %s 游戏分钟（**从点下按钮那一刻**），', 'mail-markdown-back2'));
    } },

  { dir: 'grindstone-no-enchant-clear', checker: 'check-grindstone.js', expect: 1,
    desc: '产出物不清附魔（"洗掉竿子附魔时拆除配件"只做到半件）',
    mutate: (at) => {
      const p = 'src/main/java/com/whitecobalt/anglertackle/event/GrindstoneEvents.java';
      at.set(p, mustReplace(at.get(p),
        '        EnchantmentHelper.setEnchantments(stripped, ItemEnchantments.EMPTY);',
        '        // 变异：不清附魔', 'grindstone-no-enchant-clear'));
    } },

  // ---- check-recipe-advancements.js（配方要接进原版配方书）----
  { dir: 'recipeadv-ok', checker: 'check-recipe-advancements.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // 用户 2026-10-02："把目前所有的合成配方接入原版配方书。"
  // ⚠ 两个文件同名（recipe/fish_rack.json 与 advancement/recipes/fish_rack.json），
  //   所以这里必须写**完整相对路径** —— 只写文件名的话 accessor 会报"有歧义"。
  { dir: 'recipeadv-hand-edited', checker: 'check-recipe-advancements.js', expect: 1,
    desc: '手改了进度文件（生成物漂移）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/advancement/recipes/fish_rack.json';
      at.set(p, mustReplace(at.get(p),
        '"items": "minecraft:stick"', '"items": "minecraft:oak_planks"',
        'recipeadv-hand-edited'));
    } },

  { dir: 'recipeadv-stale', checker: 'check-recipe-advancements.js', expect: 1,
    desc: '改了配方却没重跑生成器（进度里的材料还是旧的）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/recipe/fish_rack.json';
      at.set(p, mustReplace(at.get(p),
        '"item": "minecraft:stick"', '"item": "minecraft:oak_planks"',
        'recipeadv-stale'));
    } },

  // 用户 2026-10-02："字体颜色金用的蓝色很不符常理" + "不止金品的颜色有问题"
  { dir: 'shop-tier-color', checker: 'check-shop-ui.js', expect: 1,
    desc: '兑换行的品质颜色改回手写颜色码（白=灰、绿=深灰、紫=绿、金=青）',
    mutate: (at) => {
      at.set('ShopMaterials.java', mustReplace(at.get('ShopMaterials.java'),
        'com.whitecobalt.anglertackle.item.Quality.of(t).color().toString()',
        '"§" + "789abc".charAt(t)', 'shop-tier-color'));
    } },

  // ---- check-part-durability.js（配件耐久走完一圈不能被洗成全新）----
  // 用户 2026-10-02："可以看到拆下来的配件确实有耐久，但装回去就满了。"
  { dir: 'partdur-ok', checker: 'check-part-durability.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'partdur-equip-rebuild', checker: 'check-part-durability.js', expect: 1,
    desc: '装上去的配件按类型重造（磨损的配件一装就满耐久）',
    mutate: (at) => {
      at.set('RodModManager.java', mustReplace(at.get('RodModManager.java'),
        'ItemStack newPart = candidate.copyWithCount(1);',
        'ItemStack newPart = new ItemStack(candidate.getItem());', 'partdur-equip-rebuild'));
    } },

  { dir: 'partdur-unequip-rebuild', checker: 'check-part-durability.js', expect: 1,
    desc: '【卸下】按钮按 id 重造退回件（卸下来就满耐久）',
    mutate: (at) => {
      at.set('RodModManager.java', mustReplace(at.get('RodModManager.java'),
        'ItemStack oldStack = slotStackOf(parts, slot).copy();',
        'ItemStack oldStack = new ItemStack(BuiltInRegistries.ITEM.get('
        + 'ResourceLocation.parse(oldId)));', 'partdur-unequip-rebuild'));
    } },

  { dir: 'partdur-anvil-rebuild', checker: 'check-part-durability.js', expect: 1,
    desc: '铁砧装上时按类型重造（铁砧这条路又把耐久洗掉）',
    mutate: (at) => {
      at.set('RodModManager.java', mustReplace(at.get('RodModManager.java'),
        'setSlot(rodStack, currentParts(rodStack), slot, part);',
        'setSlot(rodStack, currentParts(rodStack), slot, new ItemStack(part.getItem()));',
        'partdur-anvil-rebuild'));
    } },

  { dir: 'partdur-setslot-id', checker: 'check-part-durability.js', expect: 1,
    desc: 'setSlot 退回"按 id 重造"（四条路一起翻新 —— 这就是用户报的原样）',
    mutate: (at) => {
      at.set('RodModManager.java', mustReplace(at.get('RodModManager.java'),
        'ItemStack store = part == null || part.isEmpty() ? ItemStack.EMPTY : part.copyWithCount(1);',
        'ItemStack store = ModDataComponents.RodParts.stackFromId('
        + 'BuiltInRegistries.ITEM.getKey(part.getItem()).toString());', 'partdur-setslot-id'));
    } },

  // ---- check-anvil-return.js（铁砧换件：旧配件退回背包）----
  // 用户 2026-10-02："那就再做退回背包。"
  { dir: 'anvilret-ok', checker: 'check-anvil-return.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'anvilret-in-update', checker: 'check-anvil-return.js', expect: 1,
    desc: '把退件挪进预览事件（玩家每动一下槽位就刷一个旧件出来）',
    mutate: (at) => {
      at.set('AnvilEvents.java', mustReplace(at.get('AnvilEvents.java'),
        '        event.setOutput(result);\n        event.setCost(1);',
        '        event.setOutput(result);\n'
        + '        if (event.getPlayer() != null) {\n'
        + '            event.getPlayer().getInventory().add(RodModManager.partAt(left, 0).copy());\n'
        + '        }\n'
        + '        event.setCost(1);', 'anvilret-in-update'));
    } },

  { dir: 'anvilret-output', checker: 'check-anvil-return.js', expect: 1,
    desc: '从产出物上找旧件（产出物是换好之后的竿，旧件已经不在上面了）',
    mutate: (at) => {
      at.set('AnvilEvents.java', mustReplace(at.get('AnvilEvents.java'),
        '        ItemStack left = event.getLeft();\n        ItemStack right = event.getRight();\n'
        + '        if (!(left.getItem() instanceof RodItem rodItem) || right.isEmpty()) {\n'
        + '            return;\n        }\n\n'
        + '        // 槽位判定和"装上去"走**同一个方法**，否则可能退错槽位的那一件',
        '        ItemStack left = event.getLeft();\n        ItemStack right = event.getRight();\n'
        + '        if (!(left.getItem() instanceof RodItem rodItem) || right.isEmpty()) {\n'
        + '            return;\n        }\n\n'
        + '        left = event.getOutput();\n'
        + '        // 槽位判定和"装上去"走**同一个方法**，否则可能退错槽位的那一件',
        'anvilret-output'));
    } },

  { dir: 'anvilret-rebuild', checker: 'check-anvil-return.js', expect: 1,
    desc: '旧件按 id 重造（退回来的耐久是满的）',
    mutate: (at) => {
      at.set('AnvilEvents.java', mustReplace(at.get('AnvilEvents.java'),
        'ItemStack old = RodModManager.partAt(left, slot).copy();',
        'ItemStack old = new ItemStack(right.getItem());', 'anvilret-rebuild'));
    } },

  { dir: 'anvilret-no-guard', checker: 'check-anvil-return.js', expect: 1,
    desc: '去掉服务端守卫（客户端预测也退一份）',
    mutate: (at) => {
      at.set('AnvilEvents.java', mustReplace(at.get('AnvilEvents.java'),
        '        if (player.level().isClientSide()) {\n            return;\n        }',
        '        // 变异：去掉服务端守卫', 'anvilret-no-guard'));
    } },

  { dir: 'anvilret-full-lost', checker: 'check-anvil-return.js', expect: 1,
    desc: '背包满时退回改造前的行为（直接丢掉，既不寄信也不掉在脚下）',
    // ⚠ 锚点 2026-10-12 换过一次：M4 之后"掉在脚下"那句搬进了 MailReturns，
    //   铁砧这边只剩一行 `MailReturns.toMailboxOrDrop(player, old, "return_item");`。
    //   老锚点（`if (!player.getInventory().add(old)) { player.drop(...) }`）当场失配，
    //   而失配的症状是造副本阶段一句「负例构造失败」，闸最终却报
    //   「[负向测试未通过：存在漏检的检查脚本]」——看起来像检查器抓不到变异 ✗。
    //   就是它促成了上面那个 `--check-anchors` 两秒预检。
    mutate: (at) => {
      at.set('AnvilEvents.java', mustReplace(at.get('AnvilEvents.java'),
        'MailReturns.toMailboxOrDrop(player, old, "return_item");',
        ';   // 变异：既不寄信、也没有兜底 —— 背包满时旧件当场消失',
        'anvilret-full-lost'));
    } },

  // ---- check-craft-assembly.js（工作台 / 随身 2×2 合成栏装配件）----
  // 用户 2026-10-02："加一个工作台装配件和自身合成栏装配件功能。"
  { dir: 'craftasm-ok', checker: 'check-craft-assembly.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'craftasm-custom-type', checker: 'check-craft-assembly.js', expect: 1,
    desc: '给配方建一个自己的 RecipeType（两个合成栏都查不到它，且不报错）',
    mutate: (at) => {
      at.set('RodAssemblyRecipe.java', mustReplace(at.get('RodAssemblyRecipe.java'),
        '    @Override\n    public RecipeSerializer<?> getSerializer() {',
        '    @Override\n'
        + '    public net.minecraft.world.item.crafting.RecipeType<?> getType() {\n'
        + '        return com.whitecobalt.anglertackle.registry.ModRecipes.ROD_ASSEMBLY_TYPE.get();\n'
        + '    }\n\n'
        + '    @Override\n    public RecipeSerializer<?> getSerializer() {',
        'craftasm-custom-type'));
    } },

  { dir: 'craftasm-third-item', checker: 'check-craft-assembly.js', expect: 1,
    desc: '不挡住"多放的第二件东西"（原版会连它一起吃掉）',
    mutate: (at) => {
      at.set('RodAssemblyRecipe.java', mustReplace(at.get('RodAssemblyRecipe.java'),
        '                if (!part.isEmpty()) {\n'
        + '                    return null;   // 两件配件：一次一件，不猜顺序（也保证不会有第三格被吃掉）\n'
        + '                }\n',
        '                // 变异：多出来的那格不管了\n', 'craftasm-third-item'));
    } },

  { dir: 'craftasm-no-slotcheck', checker: 'check-craft-assembly.js', expect: 1,
    desc: '不判"这件配件装得到这根竿上吗"（台钓竿也能装拟饵）',
    mutate: (at) => {
      at.set('RodAssemblyRecipe.java', mustReplace(at.get('RodAssemblyRecipe.java'),
        '        int slot = RodModManager.partSlotOf(part, rodItem.rodType());\n'
        + '        if (slot < 0) {\n            return null;\n        }',
        '        int slot = 0;', 'craftasm-no-slotcheck'));
    } },

  { dir: 'craftasm-new-stack', checker: 'check-craft-assembly.js', expect: 1,
    desc: '产出物按物品种类重造（耐久/已有配件/附魔全丢）',
    mutate: (at) => {
      at.set('RodAssemblyRecipe.java', mustReplace(at.get('RodAssemblyRecipe.java'),
        'ItemStack out = plan.rod.copy();',
        'ItemStack out = new ItemStack(plan.rod.getItem());', 'craftasm-new-stack'));
    } },

  { dir: 'craftasm-rebuild-old', checker: 'check-craft-assembly.js', expect: 1,
    desc: '旧配件按 id 重造（退回来的耐久是满的）',
    mutate: (at) => {
      at.set('RodAssemblyRecipe.java', mustReplace(at.get('RodAssemblyRecipe.java'),
        'ItemStack old = RodModManager.partAt(plan.rod, plan.slot).copy();',
        'ItemStack old = new ItemStack(plan.part.getItem());', 'craftasm-rebuild-old'));
    } },

  { dir: 'craftasm-no-register', checker: 'check-craft-assembly.js', expect: 1,
    desc: '主类里不注册序列化器（配方 JSON 解析失败，游戏里只有一行日志）',
    mutate: (at) => {
      at.set('AnglersTackle.java', mustReplace(at.get('AnglersTackle.java'),
        '        com.whitecobalt.anglertackle.registry.ModRecipes.register(modBus);',
        '        // 变异：不注册序列化器', 'craftasm-no-register'));
    } },

  { dir: 'craftasm-no-guide', checker: 'check-craft-assembly.js', expect: 1,
    desc: '手册里不提这条装配方式（special 配方进不了配方书，玩家无从得知）',
    mutate: (at) => {
      // ⚠ 这个锚点在手册里出现 **2 次**（第三章的【三种装配方式】+ 第十一章的 FAQ）。
      //   只删一处不再是"手册里不提了" —— 另一处仍然是玩家的发现途径，
      //   所以断言不该红，用例也就不该期望红（2026-10-02 加线索那页时，
      //   第一章出现了"2×2 合成栏"字样，检查器被**无关页**满足，
      //   这个用例当场变成"期望 1 实际 0"）。两处一起删才等于"手册里不提"。
      const src = at.get('guide.json');
      const anchor = '"  ③ 工作台 / 随身 2×2 合成栏：",';
      const n = src.split(anchor).length - 1;
      if (n !== 2) {
        throw new Error('负例构造失败「craftasm-no-guide」：锚点找到 ' + n
          + ' 处，应当是 2 处（第三章 + FAQ）—— 手册改了写法就得同步改这里');
      }
      at.set('guide.json', src.split(anchor).join('"  ③ （被负向测试删掉了）：",'));
    } },

  // ---- check-angler-level.js（垂纶等级 1–50 + 每 10 级突破）----
  // 用户 2026-10-02："细分钓鱼等级…一共50级…每10级解锁一次评定…不突破就卡在那一级。"
  { dir: 'level-ok', checker: 'check-angler-level.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'level-gate-missing', checker: 'check-angler-level.js', expect: 1,
    desc: '评定点少一个（Lv.30 那一级永远过不去，只有卡在那里的玩家才会发现）',
    mutate: (at) => {
      at.set('AnglerLeveling.java', mustReplace(at.get('AnglerLeveling.java'),
        'public static final int[] GATE_LEVELS = {10, 20, 30, 40};',
        'public static final int[] GATE_LEVELS = {10, 20, 40};', 'level-gate-missing'));
    } },

  { dir: 'level-xp-cap', checker: 'check-angler-level.js', expect: 1,
    desc: '卡在评定时还在攒经验（"不突破就卡在那一级"这条规则失效）',
    mutate: (at) => {
      at.set('FishermanProgress.java', mustReplace(at.get('FishermanProgress.java'),
        'if (amount <= 0 || maxLevel() || atGate()) {',
        'if (amount <= 0 || maxLevel()) {', 'level-xp-cap'));
    } },

  { dir: 'level-curve-flat', checker: 'check-angler-level.js', expect: 1,
    desc: '曲线终点从 1200 改成 600（升级所需经验不再涨到用户要的那个数）',
    mutate: (at) => {
      at.set('AnglerLeveling.java', mustReplace(at.get('AnglerLeveling.java'),
        'public static final int XP_LAST = 1200;',
        'public static final int XP_LAST = 600;', 'level-curve-flat'));
    } },

  { dir: 'level-xp-hook', checker: 'check-angler-level.js', expect: 1,
    desc: '上鱼不给经验（等级永远停在 1 级，而且没有任何报错）',
    mutate: (at) => {
      at.set('FishingManager.java', mustReplace(at.get('FishingManager.java'),
        'int levelsUp = com.whitecobalt.anglertackle.player.FishermanData.addXp(player, gainedXp);',
        'int levelsUp = 0;', 'level-xp-hook'));
    } },

  { dir: 'level-migration', checker: 'check-angler-level.js', expect: 1,
    desc: 'CODEC 不再读旧的 stage 字段（老玩家一读档，商店全锁回去）',
    mutate: (at) => {
      at.set('FishermanProgress.java', mustReplace(at.get('FishermanProgress.java'),
        `Codec.INT.fieldOf("stage").orElse(1).forGetter((FishermanProgress p) -> p.stage())`,
        'Codec.INT.fieldOf("unused_legacy").orElse(1).forGetter((FishermanProgress p) -> 1)',
        'level-migration'));
    } },

  { dir: 'level-sell-warn', checker: 'check-angler-level.js', expect: 1,
    desc: '卖鱼不再提示"下一级突破任务的鱼"（材料被默默卖掉）',
    mutate: (at) => {
      at.set('ShopMaterials.java', mustReplace(at.get('ShopMaterials.java'),
        `        int gate = com.whitecobalt.anglertackle.player.AnglerLeveling.nextGate(level);
        if (gate != 0 && matchesGate(id, tier, gate)) {
            return "这是 §e下一次等级评定（Lv." + gate + "）§r 要交的鱼（" + name + "）";
        }`,
        '        // 变异：去掉"下一级突破任务"的提示', 'level-sell-warn'));
    } },

  { dir: 'level-repair-pct', checker: 'check-angler-level.js', expect: 1,
    desc: '修竿收费改回 50%（用户要求改成 25%）',
    mutate: (at) => {
      at.set('ShopManager.java', mustReplace(at.get('ShopManager.java'),
        'public static final int REPAIR_PERCENT = 25;',
        'public static final int REPAIR_PERCENT = 50;', 'level-repair-pct'));
    } },

  // ---- check-enchantments.js（六个附魔：数据接线 + 对现有权重/张力系统的影响）----
  // 用户 2026-10-02："做完附魔要检测会不会对现有内容产生影响，比如加权重的附魔会不会让
  // 现有权重系统崩溃，若会则打回重做。"
  { dir: 'ench-ok', checker: 'check-enchantments.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'ench-no-affinity', checker: 'check-enchantments.js', expect: 1,
    desc: '删掉 RodItem.getEnchantmentValue() 重写（亲和度 0 → 附魔台对竿什么都不出，用户原报）',
    mutate: (at) => {
      at.set('RodItem.java', mustReplace(at.get('RodItem.java'),
        'public int getEnchantmentValue() {', 'public int getEnchantmentValueRenamed() {',
        'ench-no-affinity'));
    } },

  { dir: 'ench-enchantable-api', checker: 'check-enchantments.js', expect: 1,
    desc: '把 1.21.2+ 才有的 .enchantable(...) 写回 ModItems（1.21.1 编译都过不了）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        'RodItem.RodType.LURE, RodItem.RodSpec.WOOD);',
        'RodItem.RodType.LURE, RodItem.RodSpec.WOOD).enchantable(15);',
        'ench-enchantable-api'));
    } },

  { dir: 'ench-json-drift', checker: 'check-enchantments.js', expect: 1,
    desc: '手改了附魔 JSON（生成物漂移 → 这个附魔的出处/等级以后再也不会跟着规则走）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/enchantment/rescue.json';
      at.set(p, mustReplace(at.get(p), '"weight": 2', '"weight": 5', 'ench-json-drift'));
    } },

  { dir: 'ench-no-lang', checker: 'check-enchantments.js', expect: 1,
    desc: '删掉抢救的中文语言键（附魔台里会显示成键名）',
    mutate: (at) => {
      const o = JSON.parse(at.get('zh_cn.json'));
      delete o['enchantment.anglertackle.rescue'];
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },

  // ↓↓ 以下四条是"影响检测"：数据面完全不动，只改数值与闸门 ——
  //    漂移检查抓不到它们，抓它们的必须是复算出来的断言。
  { dir: 'ench-weight-runaway', checker: 'check-enchantments.js', expect: 1,
    desc: '精准猎获每级 +2% 改成 +30%（满级 ×2.6 → 权重系统被单类鱼主导，按用户要求打回）',
    mutate: (at) => {
      at.set('RodEnchants.java', mustReplace(at.get('RodEnchants.java'),
        'PRECISE_HUNT_PERCENT_PER_LEVEL = 2;', 'PRECISE_HUNT_PERCENT_PER_LEVEL = 30;',
        'ench-weight-runaway'));
    } },

  { dir: 'ench-loadbearing-zero', checker: 'check-enchantments.js', expect: 1,
    desc: '承重保护每级 −20% 改成 −25%（满级正好把超承重消耗削成 0 → 惩罚消失）',
    mutate: (at) => {
      at.set('RodEnchants.java', mustReplace(at.get('RodEnchants.java'),
        'LOAD_BEARING_PER_LEVEL = 0.20;', 'LOAD_BEARING_PER_LEVEL = 0.25;',
        'ench-loadbearing-zero'));
    } },

  { dir: 'ench-rescue-always', checker: 'check-enchantments.js', expect: 1,
    desc: '去掉抢救的"每场只触发一次"闸门（变成永远断不了线）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'if (enchRodIsLure && enchRescue > 0 && !rescueUsed) {',
        'if (enchRodIsLure && enchRescue > 0) {', 'ench-rescue-always'));
    } },

  { dir: 'ench-no-lure-gate', checker: 'check-enchantments.js', expect: 1,
    desc: '韧性保护的竿型闸门被拿掉（台钓竿也会白吃这个路亚附魔）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        '* (enchRodIsLure ? 1.0 + RodEnchants.REINFORCED_PER_LEVEL * enchReinforced : 1.0);',
        '* (1.0 + RodEnchants.REINFORCED_PER_LEVEL * enchReinforced);',
        'ench-no-lure-gate'));
    } },

  // ---- 等级阶梯（用户 2026-10-02："自创附魔只有最高级，没有 1,2,3 级"）----
  // ⚠ 这几个用例把生成器和对应的附魔 JSON **一起**改成一致的 ——
  //   只改生成器的话，"生成物漂移"那条会先红，用例就不是在考阶梯断言了。
  { dir: 'ench-lv-max-table', checker: 'check-enchantments.js', expect: 1,
    desc: '把满级拉回附魔台能刷的范围（满配台子永远只给满级 —— 用户报的原样）',
    mutate: (at) => {
      // ⚠ 只动 min_cost（生成器 + JSON 同步改），max_cost 两边都不动 ——
      //   动一半的话"生成物漂移"会先红，这个用例就不再是在考阶梯断言了。
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        'minCost: [5, 13]', 'minCost: [5, 8]', 'ench-lv-max-table/gen'));
      const p = 'src/main/resources/data/anglertackle/enchantment/reinforced.json';
      at.set(p, mustReplace(at.get(p),
        '"base": 5,\n    "per_level_above_first": 13', '"base": 5,\n    "per_level_above_first": 8',
        'ench-lv-max-table/json'));
    } },

  { dir: 'ench-lv-gap', checker: 'check-enchantments.js', expect: 1,
    desc: '拉扯的成本级差拉太大（2 级在附魔台上永远刷不出来 → 3 级附魔只有 1 级能刷）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        'minCost: [15, 9]', 'minCost: [30, 12]', 'ench-lv-gap/gen'));
      const p = 'src/main/resources/data/anglertackle/enchantment/drag.json';
      at.set(p, mustReplace(at.get(p),
        '"base": 15,\n    "per_level_above_first": 9', '"base": 30,\n    "per_level_above_first": 12',
        'ench-lv-gap/json'));
    } },

  { dir: 'ench-lv-no-low', checker: 'check-enchantments.js', expect: 1,
    desc: '常见附魔的 1 级起点太高（裸台子一个小台子都刷不到它）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        'minCost: [5, 13], maxCost: [45, 13]', 'minCost: [20, 13], maxCost: [45, 13]',
        'ench-lv-no-low/gen'));
      const p = 'src/main/resources/data/anglertackle/enchantment/reinforced.json';
      at.set(p, mustReplace(at.get(p), '"base": 5,', '"base": 20,', 'ench-lv-no-low/json'));
    } },

  { dir: 'ench-lv-single-out', checker: 'check-enchantments.js', expect: 1,
    desc: '单级附魔（抢救）的起点被抬到 30 以上（附魔台永远拿不到）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        'minCost: [30, 0], maxCost: [80, 0]', 'minCost: [40, 0], maxCost: [80, 0]',
        'ench-lv-single-out/gen'));
      const p = 'src/main/resources/data/anglertackle/enchantment/rescue.json';
      at.set(p, mustReplace(at.get(p), '"base": 30,', '"base": 40,', 'ench-lv-single-out/json'));
    } },

  // ---- 宝箱阶梯（"附魔台刷不出的也做完了等级吗" → 宝箱专属那条路）----
  { dir: 'ench-chest-nodrop', checker: 'check-enchantments.js', expect: 1,
    desc: '修饰符类型写错一个词（整条注入失效，附魔永远不掉、且没有任何日志）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        "type: 'neoforge:add_table',", "type: 'neoforge:add_loot_table',",
        'ench-chest-nodrop/gen'));
      const p = 'src/main/resources/data/anglertackle/loot_modifiers/high_quality_lure_book.json';
      at.set(p, mustReplace(at.get(p), '"type": "neoforge:add_table"',
        '"type": "neoforge:add_loot_table"', 'ench-chest-nodrop/json'));
    } },

  { dir: 'ench-chest-mineshaft', checker: 'check-enchantments.js', expect: 1,
    desc: '把注入目标改成矿道（不再是"仅在超高价值战利品箱"）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        "'minecraft:chests/end_city_treasure',", "'minecraft:chests/abandoned_mineshaft',",
        'ench-chest-mineshaft/gen'));
      const p = 'src/main/resources/data/anglertackle/loot_modifiers/high_quality_lure_book.json';
      at.set(p, mustReplace(at.get(p),
        '"loot_table_id": "minecraft:chests/end_city_treasure"',
        '"loot_table_id": "minecraft:chests/abandoned_mineshaft"', 'ench-chest-mineshaft/json'));
    } },

  { dir: 'ench-chest-levels', checker: 'check-enchantments.js', expect: 1,
    desc: '宝箱表的能量区间收窄（5 级里只有 4、5 级开得出来，1~3 级等于不存在）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        'levels: [20, 50]', 'levels: [40, 50]', 'ench-chest-levels/gen'));
      const p = 'src/main/resources/data/anglertackle/loot_table/inject/high_quality_lure_book.json';
      at.set(p, mustReplace(at.get(p), '"min": 20', '"min": 40', 'ench-chest-levels/json'));
    } },

  // ⚠ 2026-10-02：全局清单改成由 tools/glm-entries.js 统一声明（一个文件一个写者），
  //   所以这条负例改成"从共享清单里删掉自己的条目" —— 症状与原来一样：
  //   修饰符不会被加载、附魔永远不掉。
  { dir: 'ench-chest-unlisted', checker: 'check-enchantments.js', expect: 1,
    desc: '修饰符没写进 global_loot_modifiers.json（根本不会被加载）',
    mutate: (at) => {
      at.set('glm-entries.js', mustReplace(at.get('glm-entries.js'),
        "  'anglertackle:high_quality_lure_book',\n", '', 'ench-chest-unlisted/gen'));
      const p = 'src/main/resources/data/neoforge/loot_modifiers/global_loot_modifiers.json';
      at.set(p, mustReplace(at.get(p),
        '    "anglertackle:high_quality_lure_book",\n', '', 'ench-chest-unlisted/json'));
    } },

  { dir: 'ench-chest-badid', checker: 'check-enchantments.js', expect: 1,
    desc: '注入表里编了一个不存在的原版 id（整张表失效，东西不掉）',
    mutate: (at) => {
      at.set('gen-enchantments.js', mustReplace(at.get('gen-enchantments.js'),
        "condition: 'minecraft:random_chance'", "condition: 'minecraft:random_chance_roll'",
        'ench-chest-badid/gen'));
      const p = 'src/main/resources/data/anglertackle/loot_table/inject/high_quality_lure_book.json';
      at.set(p, mustReplace(at.get(p), '"minecraft:random_chance"',
        '"minecraft:random_chance_roll"', 'ench-chest-badid/json'));
    } },

  // ---- check-bite-time.js（台钓 −30% + 饵料档位，加算，下限 3 秒）----
  // 用户 2026-10-02："台钓的咬钩时间单独下降 30%，台钓饵料对咬钩时间也进行下降……
  //   以上提到的数值属于加算，但配合打窝至多做到 3 秒一条鱼。记得同步文本显示与教程。"
  { dir: 'bite-ok', checker: 'check-bite-time.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  // ---------------------------------------------------------------- 环境系统
  // ⚠ 算例回归那一条（A 段）在副本里跑的是**真实项目**的编译产物（检查器有回退），
  //   所以下面这些负例改的是数据、配置与源码结构 —— 那三块都是从副本读的。
  { dir: 'env-ok', checker: 'check-environment.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'env-data-missing-moon', checker: 'check-environment.js', expect: 1,
    desc: '某条鱼漏写 moon —— "忘了写"必须报错，而不是静默变成全天鱼',
    mutate: (at) => {
      // ⚠ 锚点必须带鱼名："moon": "full" 这种片段在 json 里不唯一，
      //   而 mustReplace 要求**恰好命中一处**（多了会抛"锚点匹配到多处"）。
      at.set('fish-habits.json', mustReplace(at.get('fish-habits.json'),
        '"mirror_carp": {\n      "habits": [\n        "bottom"\n      ],\n'
        + '      "time": [\n        "all"\n      ],\n      "weather": [\n        "all"\n      ],\n'
        + '      "moon": "full"',
        '"mirror_carp": {\n      "habits": [\n        "bottom"\n      ],\n'
        + '      "time": [\n        "all"\n      ],\n      "weather": [\n        "all"\n      ]',
        'env-data-missing-moon'));
    } },

  { dir: 'env-data-bad-time', checker: 'check-environment.js', expect: 1,
    desc: '时段取值写成不认识的词（morning）',
    mutate: (at) => {
      at.set('fish-habits.json', mustReplace(at.get('fish-habits.json'),
        '"hemiculter": {\n      "habits": [\n        "surface",\n        "timid"\n      ],\n'
        + '      "time": [\n        "day"\n      ]',
        '"hemiculter": {\n      "habits": [\n        "surface",\n        "timid"\n      ],\n'
        + '      "time": [\n        "morning"\n      ]',
        'env-data-bad-time'));
    } },

  { dir: 'env-cap-loosened', checker: 'check-environment.js', expect: 1,
    desc: '把时段 delta 的**上限**从 20 放宽到 60（等于把用户定的 10 个百分点作废）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/Config.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/Config.java'),
          '.defineInRange("timeDeltaPct", 10.0D, 0.0D, 20.0D)',
          '.defineInRange("timeDeltaPct", 10.0D, 0.0D, 60.0D)', 'env-cap-loosened'));
    } },

  { dir: 'env-moon-budget-over', checker: 'check-environment.js', expect: 1,
    desc: '金红爆率预算从 5 调到 30（超出用户定的 5 个百分点）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/Config.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/Config.java'),
          '.defineInRange("moonGoldRedDeltaPct", 5.0D, 0.0D, 15.0D)',
          '.defineInRange("moonGoldRedDeltaPct", 30.0D, 0.0D, 60.0D)', 'env-moon-budget-over'));
    } },

  { dir: 'env-night-always', checker: 'check-environment.js', expect: 1,
    desc: '夜间减免不再限定"台钓 + 夜晚"（路亚竿白天也白吃 −10%）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/fishing/BiteTiming.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/fishing/BiteTiming.java'),
          'if (!floatRod || !night) {\n            return 0.0;\n        }',
          'if (false) {\n            return 0.0;\n        }', 'env-night-always'));
    } },

  { dir: 'env-thunder-after-rain', checker: 'check-environment.js', expect: 1,
    desc: '雷暴与降雨的判断顺序反了（雷暴被当成普通降雨，加强系数失效）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/fishing/Environment.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/fishing/Environment.java'),
          'int w = level.isThundering() ? THUNDER : (level.isRaining() ? RAIN : CLEAR);',
          'int w = level.isRaining() ? (level.isThundering() ? RAIN : THUNDER) : CLEAR;',
          'env-thunder-after-rain'));
    } },

  { dir: 'env-share-penalty', checker: 'check-environment.js', expect: 1,
    desc: '给"组外鱼"减权（本系统只加不减；这会让非活跃时段的收获变差）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/fishing/EnvShare.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/fishing/EnvShare.java'),
          '        if (group <= 0 || total - group <= 0 || deltaPct <= 0.0) {',
          '        if (group <= 0 || total - group <= 0 || deltaPct <= 0.0) {\n'
          + '            for (int i = 0; i < weights.size(); i++) {\n'
          + '                if (!inGroup.test(i)) {\n'
          + '                    weights.set(i, (int) Math.max(1, Math.round(weights.get(i) * 0.9)));\n'
          + '                }\n'
          + '            }',
          'env-share-penalty'));
    } },

  { dir: 'env-session-twice', checker: 'check-environment.js', expect: 1,
    desc: '会话里不再取样环境（Environment.now(...) 不见了 → 环境系统整个不生效）',
    mutate: (at) => {
      at.set('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java',
        mustReplace(at.get('src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java'),
          'this.env = Environment.now(waterLevel);', 'this.env = null;', 'env-session-twice'));
    } },

  // ---------------------------------------------------------------- 检查器体检的补网
  // 2026-10-03 自查：102 个检查器里只有 30 个有负例，9 个"守用户验收物"的高风险检查器
  // 从来没被证伪过（其中 check-item-icons / check-fish-textures 正是这次真出过事的两处）。
  // 这一组就是给它们补网 —— 每条都对应一次真实事故或一条用户定案。
  { dir: 'iconfp-changed', checker: 'check-item-icons.js', expect: 1,
    desc: '物品贴图被换（把指纹表里 arapaima 的哈希改掉 —— 模拟"有人重画了这张图"）',
    mutate: (at) => {
      const j = JSON.parse(at.get('图标指纹.json'));
      j.arapaima = 'deadbeef'.repeat(8);
      at.set('图标指纹.json', JSON.stringify(j, null, 1) + '\n');
    } },

  { dir: 'iconfp-missing', checker: 'check-item-icons.js', expect: 1,
    desc: '指纹表里少了一条（新贴图没纳入守护 —— 这样它以后被换也没人知道）',
    mutate: (at) => {
      const j = JSON.parse(at.get('图标指纹.json'));
      delete j.arapaima;
      at.set('图标指纹.json', JSON.stringify(j, null, 1) + '\n');
    } },

  { dir: 'fishtex-baseline-drift', checker: 'check-fish-textures.js', expect: 1,
    desc: '鱼贴图比例基线被改（模拟"把鱼上下拉高了"—— 这正是当年 --seal 那次事故）',
    mutate: (at) => {
      // 锚点用带空格的写法：基线文件是 JSON.stringify(..., null, 2) 生成的
      at.set('arapaima.json', mustReplace(at.get('arapaima.json'),
        '"ar": 4,', '"ar": 1.5,', 'fishtex-baseline-drift'));
    } },

  { dir: 'fishhabits-missing-fish', checker: 'check-fish-habits.js', expect: 1,
    desc: '条件表里少了一条鱼（词条链的四端对不上）',
    mutate: (at) => {
      const j = JSON.parse(at.get('fish-habits.json'));
      delete j.fish.arapaima;
      at.set('fish-habits.json', JSON.stringify(j, null, 2) + '\n');
    } },

  { dir: 'rodrecipe-extra-rod', checker: 'check-rod-recipes.js', expect: 1,
    desc: '给铁路亚竿也加了配方（用户明确说"其他等级杆子不需要加配方"）',
    mutate: (at) => {
      // ⚠ 必须用**完整路径**：仓库里有 3 个同名的 fish_rack.json，
      //   用 basename 会命中多个 → find() 抛"文件名有歧义"（这个坑前人踩过并留了注释）。
      const p = 'src/main/resources/data/anglertackle/recipe/fish_rack.json';
      at.set(p, mustReplace(at.get(p),
        '"id": "anglertackle:fish_rack"', '"id": "anglertackle:iron_lure_rod"', 'rodrecipe-extra-rod'));
    } },

  { dir: 'bite-multiply', checker: 'check-bite-time.js', expect: 1,
    desc: '加算改成连乘（每个数字单独看都对，实得时间却对不上）',
    mutate: (at) => {
      // ⚠ 锚点跟着实现走：环境系统给 totalCut 加了夜间那一项（docs/24），
      //   所以这里换掉的是**三参版本**里那一行完整的加算式。
      at.set('BiteTiming.java', mustReplace(at.get('BiteTiming.java'),
        'double sum = (rodCutPercent() + Math.max(0.0, baitCutPercent) + nightCutPercent(true, night) * 100.0) / 100.0;',
        'double sum = 1.0 - (1.0 - rodCutPercent() / 100.0) * (1.0 - Math.max(0.0, baitCutPercent) / 100.0);',
        'bite-multiply'));
    } },

  { dir: 'bite-night-always', checker: 'check-bite-time.js', expect: 1,
    desc: '夜间减免不再限定"台钓 + 夜晚"（路亚竿白天也白吃 −10%）',
    mutate: (at) => {
      at.set('BiteTiming.java', mustReplace(at.get('BiteTiming.java'),
        'if (!floatRod || !night) {\n            return 0.0;\n        }',
        'if (false) {\n            return 0.0;\n        }', 'bite-night-always'));
    } },

  { dir: 'bite-tier-shift', checker: 'check-bite-time.js', expect: 1,
    desc: '紫档饵错读成金档（同一套刻度错一档，正是 AGENTS.md 里那两次事故）',
    mutate: (at) => {
      at.set('BiteTiming.java', mustReplace(at.get('BiteTiming.java'),
        'case 3 -> c.biteBaitCutPurplePercent.get();',
        'case 3 -> c.biteBaitCutGoldPercent.get();', 'bite-tier-shift'));
    } },

  { dir: 'bite-case-6', checker: 'check-bite-time.js', expect: 1,
    desc: '把 1 起算的拟饵刻度混进来（多一个 case 6）',
    mutate: (at) => {
      at.set('BiteTiming.java', mustReplace(at.get('BiteTiming.java'),
        'case 5 -> c.biteBaitCutRedPercent.get();',
        'case 5 -> c.biteBaitCutRedPercent.get();\n            case 6 -> c.biteBaitCutRedPercent.get();',
        'bite-case-6'));
    } },

  { dir: 'bite-lure-gets-it', checker: 'check-bite-time.js', expect: 1,
    desc: '去掉"只给台钓竿"的闸门（路亚竿白吃 −30% 和饵料减时）',
    mutate: (at) => {
      at.set('BiteTiming.java', mustReplace(at.get('BiteTiming.java'),
        '        if (!floatRod) {\n            return 0.0;\n        }\n', '',
        'bite-lure-gets-it'));
    } },

  { dir: 'bite-empty-slot', checker: 'check-bite-time.js', expect: 1,
    desc: '空槽也按白档算（把饵拿下来反而更快，−5%）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        '        String id = parts.bait();\n        if (id == null || id.isEmpty()) {\n            return 0.0;\n        }\n', '',
        'bite-empty-slot'));
    } },

  { dir: 'bite-no-floor', checker: 'check-bite-time.js', expect: 1,
    desc: '删掉 3 秒下限（"配合打窝至多 3 秒一条鱼"永远达不到）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'base = Math.max(base, BiteTiming.floorSeconds());',
        '// 变异：下限被删掉了', 'bite-no-floor'));
    } },

  { dir: 'bite-no-tooltip', checker: 'check-bite-time.js', expect: 1,
    desc: '饵料 tooltip 不再显示减时（数值生效但玩家看不到）',
    mutate: (at) => {
      at.set('BaitItem.java', mustReplace(at.get('BaitItem.java'),
        '"tooltip.anglertackle.bait_cut_time"', '"tooltip.anglertackle.bait_cut_time_removed"',
        'bite-no-tooltip'));
    } },

  { dir: 'bite-guide-drift', checker: 'check-bite-time.js', expect: 1,
    desc: '手册里的百分比与配置对不上（红饵写成 −50%）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '红饵 −51%', '红饵 −50%', 'bite-guide-drift'));
    } },

  { dir: 'bite-floor-drift', checker: 'check-bite-time.js', expect: 1,
    desc: '手册里的地板秒数与 bite.floorSeconds 对不上',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '3 秒是地板', '2 秒是地板', 'bite-floor-drift'));
    } },

  { dir: 'bite-no-lang', checker: 'check-bite-time.js', expect: 1,
    desc: '删掉减时那行的中文语言键（tooltip 会显示成键名）',
    mutate: (at) => {
      const o = JSON.parse(at.get('zh_cn.json'));
      delete o['tooltip.anglertackle.rod_cut_time'];
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },

  // ---- check-config-sync.js（新键必须补进自己的表）----
  // 2026-10-02 真实事故：sync-config-file.js 把 bite 的 8 个新键追加到了文件末尾
  // 却没写表头，于是它们落进了当时最后一个分组 [render]（NeoForge 只读 bite.*）。
  { dir: 'cfgsync-ok', checker: 'check-config-sync.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'cfgsync-tail-append', checker: 'check-config-sync.js', expect: 1,
    desc: '补入已有分组时改成"末尾追加"（新键会落进最后一个分组 —— 就是这次事故）',
    mutate: (at) => {
      at.set('sync-config-file.js', mustReplace(at.get('sync-config-file.js'),
        '  insertAt.set(at, (insertAt.get(at) || []).concat(extrasByGroup.get(m.group.id)));',
        '  m.isNew = true;   // 变异：不插进原表，改为末尾追加',
        'cfgsync-tail-append'));
    } },

  { dir: 'cfgsync-drops-autolines', checker: 'check-config-sync.js', expect: 1,
    desc: '把游戏自动加的 Default/Range 注释当成自己的注释一起重写（与游戏来回折腾）',
    mutate: (at) => {
      at.set('sync-config-file.js', mustReplace(at.get('sync-config-file.js'),
        "const isAutoLine = (text) => AUTO_COMMENT.test(text.replace(/^\\s*#\\s?/, ''));",
        'const isAutoLine = (text) => false;   // 变异：不再忽略自动行',
        'cfgsync-drops-autolines'));
    } },
  { dir: 'cfgsync-release-overwrite', checker: 'check-config-sync.js', expect: 1,
    desc: '护栏被拆掉：拿"游戏里那份调过参的 toml"当基底去覆盖发布用默认配置（2026-10-11 真踩过，104 个键被写成调参值）',
    mutate: (at) => {
      const F = 'sync-config-file.js';
      const s = at.get(F);
      const i = s.indexOf("    console.error('❌ --out 指向仓库根的「发布用默认配置」");
      if (i < 0) throw new Error('找不到护栏');
      const end = s.indexOf('process.exit(2);', i);
      if (end < 0) throw new Error('护栏里找不到 exit(2)');
      at.set(F, s.slice(0, i) + s.slice(end + 'process.exit(2);'.length));
    } },

  // ---- check-guide-sync.js（教程书：内容一份、结构完整、不许有过期说法）----
  { dir: 'guidesync-ok', checker: 'check-guide-sync.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过 —— 它同时证明副本里文件带齐了）', mutate: () => {} },

  { dir: 'guidesync-md-stars', checker: 'check-guide-sync.js', expect: 1,
    desc: '手册里贴回 Markdown 的 **（游戏里不渲染，玩家只会看到多出来的星号）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"  这时经验不再积攒，",', '"  这时**经验不再积攒**，",', 'guidesync-md-stars'));
    } },

  // ---- check-live-config-matches-defaults.js（配置真的被注册、也确实被读到了）----
  // 这条检查读的是**游戏目录里的实况**，所以负例在副本根放一份小样本 toml，
  // checker 在 ATL_ROOT 模式下读它（见那边的注释）。样本里只写几项，
  // 其余项走"还没进过游戏"的 ⏳ 分支 —— 于是下面每一条红的理由都只剩目标那一条。
  { dir: 'livecfg-ok', checker: 'check-live-config-matches-defaults.js', expect: 0,
    desc: '小样本 toml 与默认值一致（对照组，应当通过）',
    createFiles: [['live-config.toml',
      '[bite]\n\tminWaitSeconds = 10\n\tmaxWaitSeconds = 14\n\thardCapSeconds = 30\n']] },

  { dir: 'livecfg-extra', checker: 'check-live-config-matches-defaults.js', expect: 1,
    desc: 'toml 里有一项代码里没有的键（代码删了项、文件里还留着）',
    createFiles: [['live-config.toml',
      '[bite]\n\tminWaitSeconds = 10\n\n[zzz_ghost]\n\tnope = 1\n']] },

  { dir: 'livecfg-stale-default', checker: 'check-live-config-matches-defaults.js', expect: 1,
    desc: 'toml 里压着"有意变更之前的旧默认值"（compassSearchRadius=400）—— 新默认值根本没生效',
    createFiles: [['live-config.toml', '[gameplay]\n\tcompassSearchRadius = 400\n']] },

  // ⚠ 这一条专门盯**写法差异**：文件里是 `20.0`、登记表里是 `20`，
  //   只做字符串比较就会把它误判成"玩家调过的值"而静默放过 ——
  //   2026-10-02 就是这么漏掉发布包里那份默认配置的（铁竿承重 20.0）。
  { dir: 'livecfg-stale-dotzero', checker: 'check-live-config-matches-defaults.js', expect: 1,
    desc: '旧默认值写成 20.0（与登记表里的 20 只是写法不同）也必须被抓到',
    createFiles: [['live-config.toml', '[gameplay]\n\trodIronMaxWeightKg = 20.0\n']] },

  { dir: 'livecfg-tuned', checker: 'check-live-config-matches-defaults.js', expect: 0,
    desc: '调参器调过的值（鱼拉速 ×1.5）不算失败 —— 否则调一次参就永久挡住部署',
    createFiles: [['live-config.toml', '[fishStats]\n\tcrucianCarpPullSpeed = 1.05\n']] },

  // ⚠ 发布包要带的那份默认配置（仓库根 anglertackle-common.toml）也必须与默认值**逐项一致**：
  //   它是给"还没跑过游戏"的玩家直接覆盖用的，"玩家自己调过"那个豁免在这里不适用 ✗。
  //   2026-10-08 实测：那份文件停在 10/07（缺 19 条新鱼的键、当天改的值也没跟上），
  //   而生成命令 `sync-config-file.js --out` 每次都说"无需改" —— 因为它在"输入不需要改"
  //   那一支里直接 return，根本没写输出文件（已一并修掉）。
  //   对照组就是上面几条：副本里没有 anglertackle-common.toml 时，这条检查只提示、不失败。
  { dir: 'livecfg-release-drift', checker: 'check-live-config-matches-defaults.js', expect: 1,
    desc: '发布用默认配置压着旧值（tigerfishTensionGain=1.5，默认 3.1）—— 必须红，否则发出去的默认配置是错的',
    createFiles: [['live-config.toml', '[bite]\n\tminWaitSeconds = 10\n'],
      ['anglertackle-common.toml', '[fishStats]\n\ttigerfishTensionGain = 1.5\n']] },

  // ---- check-kitchen-items.js（料理线上参与配方的物品）----
  // 用户 2026-10-03：先把"各种参与配方的 mod 物品贴图"做出来，并给配方
  //（鱼刀 = 一铁锭 + 一任意木头按钮 + 一木棍）。
  { dir: 'kitchen-ok', checker: 'check-kitchen-items.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'kitchen-knife-recipe-drift', checker: 'check-kitchen-items.js', expect: 1,
    desc: '鱼刀配方里的"任意木头按钮"被写死成某一种木头（用户要的是任意）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/recipe/fish_knife.json';
      at.set(p, mustReplace(at.get(p),
        '"tag": "minecraft:wooden_buttons"', '"item": "minecraft:oak_button"',
        'kitchen-knife-recipe-drift'));
    } },

  { dir: 'kitchen-salt-no-bucket', checker: 'check-kitchen-items.js', expect: 1,
    desc: '盐的配方里去掉水桶（用户定的"水桶参与制作理应返还水桶"就没人管了）',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/recipe/salt.json';
      at.set(p, mustReplace(at.get(p),
        '    {\n      "item": "minecraft:water_bucket"\n    },\n', '',
        'kitchen-salt-no-bucket'));
    } },

  { dir: 'kitchen-icon-unprotected', checker: 'check-kitchen-items.js', expect: 1,
    desc: '新图标没纳入指纹守护（被生成器覆盖也不会有人知道 —— 21 张手绘图就是这么没的）',
    mutate: (at) => {
      const o = JSON.parse(at.get('参考资料/图标指纹.json'));
      delete o.fish_knife;
      at.set('参考资料/图标指纹.json', JSON.stringify(o, null, 2) + '\n');
    } },

  { dir: 'kitchen-durability-drift', checker: 'check-kitchen-items.js', expect: 1,
    desc: '文档里的鱼刀耐久还是 64，代码被改成 32（经典的"文档抄代码"漂移）',
    mutate: (at) => {
      at.set('FishKnifeItem.java', mustReplace(at.get('FishKnifeItem.java'),
        'public static final int DURABILITY = 64;', 'public static final int DURABILITY = 32;',
        'kitchen-durability-drift'));
    } },

  { dir: 'kitchen-tooltip-missing', checker: 'check-kitchen-items.js', expect: 1,
    desc: '删掉芥末的中文 tooltip 键（游戏里那一行会显示成一串键名）',
    mutate: (at) => {
      const o = JSON.parse(at.get('zh_cn.json'));
      delete o['tooltip.anglertackle.mustard'];
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },

  // ---- check-cutting.js / check-cooking.js / check-dish-recipes.js（烹饪线）----
  { dir: 'cut-ok', checker: 'check-cutting.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'cut-formula-drift', checker: 'check-cutting.js', expect: 1,
    desc: '份数公式的"每块重量"被改（0.25 → 0.5）：便宜鱼会多出一倍材料',
    mutate: (at) => {
      at.set('CuttingRecipe.java', mustReplace(at.get('CuttingRecipe.java'),
        'KG_PER_CHUNK = {0.25f, 1.0f, 3.0f}', 'KG_PER_CHUNK = {0.5f, 1.0f, 3.0f}', 'cut-formula-drift'));
    } },
  { dir: 'cut-no-remaining', checker: 'check-cutting.js', expect: 1,
    desc: '切块不再覆写 getRemainingItems（以后加容器材料就没人返还了）',
    mutate: (at) => {
      at.set('CuttingRecipe.java', mustReplace(at.get('CuttingRecipe.java'),
        'public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {',
        'public NonNullList<ItemStack> getRemainingItems_unused(CraftingInput input) {', 'cut-no-remaining'));
    } },

  { dir: 'cook-ok', checker: 'check-cooking.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'cook-missing-recipe', checker: 'check-cooking.js', expect: 1,
    desc: '删掉一条加热配方（顶级鱼排拿去烟熏什么都不出）',
    mutate: () => {}, deleteData: ['recipe/supreme_fish_steak_to_smoked_fish.json'] },
  { dir: 'cook-result-swap', checker: 'check-cooking.js', expect: 1,
    desc: '熔炉那条的产物改成熏鱼：三设备产出撞车，"三设备三产出"失效',
    mutate: (at) => {
      const p = 'src/main/resources/data/anglertackle/recipe/fish_chunk_to_fish_chunk_cooked.json';
      at.set(p, mustReplace(at.get(p), '"id": "anglertackle:fish_chunk_cooked"', '"id": "anglertackle:smoked_fish"', 'cook-result-swap'));
    } },
  { dir: 'cook-no-data-copy', checker: 'check-cooking.js', expect: 1,
    desc: 'DataCookingRecipe 不再把 FISH_CHUNK 写到产物上（烤完丢掉鱼种）',
    mutate: (at) => {
      at.set('DataCookingRecipe.java', mustReplace(at.get('DataCookingRecipe.java'),
        'out.set(ModDataComponents.FISH_CHUNK.get(), data);', '// 变异：不继承数据', 'cook-no-data-copy'));
    } },
  { dir: 'cook-oily-drift', checker: 'check-cooking.js', expect: 1,
    desc: '榨油的多脂鱼名单被改（鲻鱼不再算多脂）',
    mutate: (at) => {
      at.set('OilPressingRecipe.java', mustReplace(at.get('OilPressingRecipe.java'),
        '"mullet",              // 鲻鱼', '"not_mullet",          // 变异', 'cook-oily-drift'));
    } },
  // 用户 2026-10-03："即使显示能做也弄不了" —— 两个入口各堵一次：
  //   ① 营火配方不继承原版类 → JEI 强转 ClassCastException（三条配方查不到）
  //   ② 鱼块/整鱼没有默认数据 → 创造模式 / JEI 拿到的材料永远做不出菜
  { dir: 'cook-campfire-not-vanilla', checker: 'check-cooking.js', expect: 1,
    desc: '营火配方改回继承 DataCookingRecipe（JEI 会把它强转成原版营火配方 → 整条报错）',
    mutate: (at) => {
      at.set('ChunkCampfireRecipe.java', mustReplace(at.get('ChunkCampfireRecipe.java'),
        'public class ChunkCampfireRecipe extends CampfireCookingRecipe {',
        'public class ChunkCampfireRecipe extends DataCookingRecipe {', 'cook-campfire-not-vanilla'));
    } },
  { dir: 'cook-no-default-chunk-data', checker: 'check-cooking.js', expect: 1,
    desc: '鱼块不带默认 FISH_CHUNK 组件（创造模式 / JEI 拿到的块做不出任何菜）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        '.component(ModDataComponents.FISH_CHUNK.get(), sampleChunkData(group))', '', 'cook-no-default-chunk-data'));
    } },
  { dir: 'cook-no-default-fish-data', checker: 'check-cooking.js', expect: 1,
    desc: '整鱼不带默认 FISH_DATA 组件（创造模式 / JEI 拿到的鱼切不出块）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        '.component(ModDataComponents.FISH_DATA.get(), defaultFishData(name))', '', 'cook-no-default-fish-data'));
    } },
  { dir: 'cook-default-queries-species', checker: 'check-cooking.js', expect: 1,
    desc: '默认数据里去查鱼种表（物品注册期配置还没加载 → 专用服务器直接崩，1.1.11 真踩过）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        '        return new com.whitecobalt.anglertackle.fishing.FishData(species, 30f, 1.0f,',
        '        int t = com.whitecobalt.anglertackle.fishing.FishSpecies.byId(species).tiers()[0];\n'
        + '        return new com.whitecobalt.anglertackle.fishing.FishData(species, 30f, 1.0f,',
        'cook-default-queries-species'));
    } },
  { dir: 'cook-default-bad-tier', checker: 'check-cooking.js', expect: 1,
    desc: '鱼块默认档位改成鲑鱼档位表里没有的 0（默认数据与鱼种数据脱节）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        'SAMPLE_CHUNK_TIER = {1, 2, 5}', 'SAMPLE_CHUNK_TIER = {1, 2, 0}', 'cook-default-bad-tier'));
    } },

  { dir: 'dish-ok', checker: 'check-dish-recipes.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'dish-allow-drift', checker: 'check-dish-recipes.js', expect: 1,
    desc: '逐鱼许可表与 §13.5 不一致（鲫鱼的奶白鱼汤被删）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'put(m, "crucian_carp", Dish.SOUP, Dish.CREAMY);', 'put(m, "crucian_carp", Dish.SOUP);', 'dish-allow-drift'));
    } },
  { dir: 'dish-name-template-gone', checker: 'check-dish-recipes.js', expect: 1,
    desc: '菜名模板被改名（把"油炸%s"改成"炸制%s"）—— 证明菜名分类是从语言文件模板现推的，不是手抄的表',
    mutate: (at) => {
      // 2026-10-10：两条检查器原来各抄一份手写菜名正则表，补 19 条鱼时双双认不出新菜名（假红）。
      // 改成 tools/dish-name.js 从 dish.anglertackle.<id> 的名字模板现推之后，
      // 改掉模板 ⇒ 文档里的菜名再也认不出来 ⇒ 文档与 Java 的逐鱼表必然对不上 ⇒ 必须红。
      const o = JSON.parse(at.get('zh_cn.json'));
      o['dish.anglertackle.deep_fried_fish'] = '炸制%s';
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },
  { dir: 'dish-food-drift', checker: 'check-dish-recipes.js', expect: 1,
    desc: '菜的饱食度不再按真实档位（改成固定取 2 档的值）',
    mutate: (at) => {
      // ⚠ 具体数字（3/4/5/6/7/8 与 4/5/6/7/8/9）由 check-food-values.js 守；
      //   这条只守"有没有接上 FoodValues、有没有用真实档位"。
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'return com.whitecobalt.anglertackle.fishing.FoodValues.dish(tier);',
        'return com.whitecobalt.anglertackle.fishing.FoodValues.dish(2);', 'dish-food-drift'));
    } },
  { dir: 'dish-no-bucket-return', checker: 'check-dish-recipes.js', expect: 1,
    desc: '水桶不再返还空桶（玩家做一次汤丢一个铁桶 —— 用户专门提醒过的坑）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'for (int i : m.waterSlots()) out.set(i, new ItemStack(Items.BUCKET));', '// 变异：不返还水桶', 'dish-no-bucket-return'));
    } },
  { dir: 'dish-json-missing', checker: 'check-dish-recipes.js', expect: 1,
    desc: '删掉海鲢刺身的配方 JSON（少了它就是整条菜消失，没有任何提示）',
    mutate: () => {}, deleteData: ['recipe/sashimi/oxeye_tarpon.json'] },
  // ⚠ 1.1.7 第一版**游戏启动就崩**，根因就在这：许可表写成"类级静态字段 + 静态块"，
  //   而 Dish 枚举常量要读外层的 SOY 等静态字段 → 两个 <clinit> 互锁 →
  //   List.of(null) NPE → ExceptionInInitializerError（构建**完全看不出来**）。
  //   这个负例把修法改回崩溃写法，检查器必须红。
  { dir: 'dish-eager-static', checker: 'check-dish-recipes.js', expect: 1,
    desc: '许可表退回"类级静态字段 + 静态块"（= 1.1.7 的启动崩溃写法）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        '    private static final class AllowedTable {\n        static final Map<String, Set<Dish>> TABLE = build();\n    }',
        '    private static Map<String, Set<Dish>> TABLE = build();\n'
        + '    static { Set<Dish> warm = Set.of(Dish.SOUP); }',
        'dish-eager-static'));
    } },
  { dir: 'dish-count-by-stack', checker: 'check-dish-recipes.js', expect: 1,
    desc: '主料改回"按个数数"（用户原话：往配方栏里塞多个相同物品就合不了 / 没有批量合成）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        '                baseSlots++;', '                baseSlots += s.getCount();', 'dish-count-by-stack'));
    } },

  // ---- check-dish-coverage.js（每道菜都有图 / 每张图都有人用）----
  // ⚠ 这里原本 4 个用例被删掉过，理由是"整个成品菜层取消（44 道菜 + 13 件物品都不做）"——
  //   那个理由**早就作废了**：成品菜层后来按用户要求做了（12 张图 / 44 道菜），
  //   检查器也一直在构建链里跑。于是有一段时间它**一个负例都没有**，
  //   等于没人证明过它真的会红 —— 补回来。
  // ---- check-fish-palatability.js（食用适宜度 L0~L5，用户 2026-10-10）----
  // 用户原话："根据描述做分级，越难吃 debuff 越强。有些不进菜单"、
  //          "你是在做老功能，别连代码都不会兼容"。
  { dir: 'palat-ok', checker: 'check-fish-palatability.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'palat-l5-in-menu', checker: 'check-fish-palatability.js', expect: 1,
    desc: '把不可食用的盲鱼塞进菜品菜单（= "有些不进菜单"被推翻；它同时是"受保护不食用"那条设定）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'put(m, "platinum_arowana", Dish.STEAMED, Dish.BRAISED);',
        'put(m, "platinum_arowana", Dish.STEAMED, Dish.BRAISED);\n        put(m, "cave_blindfish", Dish.STEAMED);',
        'palat-l5-in-menu'));
    } },
  { dir: 'palat-unwired', checker: 'check-fish-palatability.js', expect: 1,
    desc: '吃完菜不再调 apply（分了级却没人执行：吃了没反应，而且不报错）',
    mutate: (at) => {
      at.set('DishItem.java', mustReplace(at.get('DishItem.java'),
        'com.whitecobalt.anglertackle.recipe.FishPalatability.apply(',
        'com.whitecobalt.anglertackle.recipe.FishPalatability.applyNowhere(',
        'palat-unwired'));
    } },
  { dir: 'palat-raw-always', checker: 'check-fish-palatability.js', expect: 1,
    desc: '"生"写死成 true（所有熟菜也吃出生食惩罚：虹鳟煮熟了照样中寄生虫）',
    mutate: (at) => {
      at.set('DishItem.java', mustReplace(at.get('DishItem.java'),
        '"sashimi".equals(dishId())', 'true', 'palat-raw-always'));
    } },
  { dir: 'palat-chunk-unwired', checker: 'check-fish-palatability.js', expect: 1,
    desc: '生块那条路断掉（L2 的"生食有寄生虫"就只在刺身上生效 —— 而生块才是更常见的那条路）',
    mutate: (at) => {
      at.set('FishChunkItem.java', mustReplace(at.get('FishChunkItem.java'),
        'FishPalatability.apply(player, stack, !cooked)',
        'FishPalatability.apply(player, stack, false)',
        'palat-chunk-unwired'));
    } },
  { dir: 'palat-tooltip-own-table', checker: 'check-fish-palatability.js', expect: 1,
    desc: '悬浮文本自己写死一句负面文案（不调 warningKey）—— 界面与真正发的效果分叉的开始',
    mutate: (at) => {
      at.set('DishItem.java', mustReplace(at.get('DishItem.java'),
        'com.whitecobalt.anglertackle.recipe.FishPalatability.warningKey(',
        '"tooltip.anglertackle.palat.nausea" /* palat-tooltip-own-table */ + (',
        'palat-tooltip-own-table'));
    } },
  { dir: 'palat-cookbook-own-table', checker: 'check-fish-palatability.js', expect: 1,
    desc: '菜谱界面的味道提示自己查表（不调 warningKey）—— 就是"菜谱说没事、吃了反胃"的开端',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'com.whitecobalt.anglertackle.recipe.FishPalatability.warningKey(pair.species(), raw)',
        '("atlantic_cod".equals(pair.species()) ? "tooltip.anglertackle.palat.wither" : null)',
        'palat-cookbook-own-table'));
    } },
  { dir: 'palat-fish-tooltip-gone', checker: 'check-fish-palatability.js', expect: 1,
    desc: '整鱼悬浮文本不再说"不可食用"（L5 五条鱼在菜谱里永远看不到 ⇒ 玩家彻底不知道该鱼为什么没用）',
    mutate: (at) => {
      at.set('FishItem.java', mustReplace(at.get('FishItem.java'),
        'if (!com.whitecobalt.anglertackle.recipe.FishPalatability.inDishMenu(data.speciesId())) {',
        'if (false) {',
        'palat-fish-tooltip-gone'));
    } },
  { dir: 'palat-inedible-own-list', checker: 'check-fish-palatability.js', expect: 1,
    desc: '菜谱里的"不可食用"名单改成界面自己写死的清单（不调 inedibleSpecies）—— 与等级表分叉的开端',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'com.whitecobalt.anglertackle.recipe.FishPalatability.inedibleSpecies()',
        'java.util.List.of("guppy", "jewel_cichlid")',
        'palat-inedible-own-list'));
    } },
  { dir: 'palat-chronic-unwired', checker: 'check-fish-palatability.js', expect: 1,
    desc: '不再记慢性风险（汞/雪卡/二噁英永远不累积 —— 用户定的 C①"累积型"落不了地）',
    mutate: (at) => {
      at.set('FishPalatability.java', mustReplace(at.get('FishPalatability.java'),
        '            applyChronic(player, species);', '            /* palat-chronic-unwired */',
        'palat-chronic-unwired'));
    } },
  { dir: 'palat-chronic-after-bite', checker: 'check-fish-palatability.js', expect: 1,
    desc: '"记慢性"挪到"判即时负面"之后 —— L0 却有汞的裸盖鱼永远不计数（正是慢性最该生效的那条鱼）',
    mutate: (at) => {
      let s = at.get('FishPalatability.java');
      s = mustReplace(s, '            applyChronic(player, species);\n\n            Bite bite',
        '            Bite bite', 'palat-chronic-after-bite/remove');
      s = mustReplace(s, '            if (bite == Bite.NONE) return;',
        '            applyChronic(player, species);\n            if (bite == Bite.NONE) return;',
        'palat-chronic-after-bite/add');
      at.set('FishPalatability.java', s);
    } },
  { dir: 'palat-chronic-hardcoded', checker: 'check-fish-palatability.js', expect: 1,
    desc: '慢性风险文案把"吃够 10 次"写死（不用 %s）—— 调 THRESHOLD 时悬浮文本开始撒谎',
    mutate: (at) => {
      const o = JSON.parse(at.get('zh_cn.json'));
      o['tooltip.anglertackle.palat.chronic.mercury'] = '⚠ 长期食用：汞会在体内蓄积（吃够 10 次会虚弱）';
      at.set('zh_cn.json', JSON.stringify(o, null, 2) + '\n');
    } },
  { dir: 'palat-ledger-renamed', checker: 'check-fish-palatability.js', expect: 1,
    desc: '账本 FILE_ID 改名 —— 老存档里已经攒了一半的累积计数全丢',
    mutate: (at) => {
      at.set('PalatabilityLedger.java', mustReplace(at.get('PalatabilityLedger.java'),
        'FILE_ID = "anglertackle_palatability"', 'FILE_ID = "anglertackle_palatability_v2"',
        'palat-ledger-renamed'));
    } },
  { dir: 'ferment-no-preserve', checker: 'check-fish-palatability.js', expect: 1,
    desc: '发酵产物不再搬原鱼的组件 —— 用户口径"鱼的数据不变"被破坏（丢了 FISH_DATA 就认不出是哪条鲨鱼）',
    mutate: (at) => {
      at.set('BarrelFermenting.java', mustReplace(at.get('BarrelFermenting.java'),
        '        out.applyComponents(in.getComponents());', '        // ferment-no-preserve',
        'ferment-no-preserve'));
    } },
  { dir: 'ferment-no-throttle', checker: 'check-fish-palatability.js', expect: 1,
    desc: 'tick 扫描不再按 20 tick 节流（每 tick 全量遍历清单）',
    mutate: (at) => {
      at.set('BarrelFermenting.java', mustReplace(at.get('BarrelFermenting.java'),
        'if (level.getGameTime() % SCAN_INTERVAL != 0) {', 'if (false) {', 'ferment-no-throttle'));
    } },
  { dir: 'ferment-world-scan', checker: 'check-fish-palatability.js', expect: 1,
    desc: '发酵扫描里冒出"强加载区块"的写法（正是"为了找矿强加载区块，卡服"那一类事故）',
    mutate: (at) => {
      at.set('BarrelFermenting.java', mustReplace(at.get('BarrelFermenting.java'),
        '            BlockEntity be = level.getBlockEntity(e.pos);',
        '            level.getChunk(e.pos);\n            BlockEntity be = level.getBlockEntity(e.pos);',
        'ferment-world-scan'));
    } },
  { dir: 'ferment-eel-back', checker: 'check-fish-palatability.js', expect: 1,
    desc: '把电鳗写回 L4（分类错误复发：科普中国说它是"著名的食用鱼"，危险是电击不是毒性）',
    mutate: (at) => {
      at.set('FishPalatability.java', mustReplace(at.get('FishPalatability.java'),
        '        put(m, L4, "greenland_shark");  // 格陵兰鲨：鲜肉含尿素 + TMAO，FishBase "Poisonous to eat"',
        '        put(m, L4, "greenland_shark", "electric_eel");',
        'ferment-eel-back'));
    } },
  { dir: 'palat-processed-dish', checker: 'check-fish-palatability.js', expect: 1,
    desc: '加工品改用 FishChunkItem —— 它保留着 FISH_DATA（格陵兰鲨 L4），吃"安全的"腌鲨肉反而会凋零',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        'ITEMS.register("frozen_sashimi",\n            () -> new Item(new Item.Properties().stacksTo(64)',
        'ITEMS.register("frozen_sashimi",\n            () -> new com.whitecobalt.anglertackle.item.FishChunkItem(new Item.Properties().stacksTo(64)',
        'palat-processed-dish'));
    } },
  { dir: 'palat-processed-no-food', checker: 'check-fish-palatability.js', expect: 1,
    desc: '处理过的电鳗丢掉 .food(...) —— "处理完就安全"变成"处理完是个 64 堆叠的摆件"',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        'ITEMS.register("shocked_eel",\n            () -> new Item(new Item.Properties().stacksTo(64)\n'
        + '                    .food(new net.minecraft.world.food.FoodProperties.Builder()\n'
        + '                            .nutrition(6).saturationModifier(0.8F).build())));',
        'ITEMS.register("shocked_eel",\n            () -> new Item(new Item.Properties().stacksTo(64)));',
        'palat-processed-no-food'));
    } },
  // ---- check-fish-names.js（学名/中文名修正 7 条，2026-10-11）----
  { dir: 'fishnames-ok', checker: 'check-fish-names.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'fishnames-jewel-old', checker: 'check-fish-names.js', expect: 1,
    desc: '红宝石鱼的学名退回水族书旧名 Hemichromis bimaculatus（2022 年已分出 Rubricatochromis）',
    mutate: (at) => {
      const F = 'fish_research/jewel_cichlid.json';
      at.set(F, mustReplace(at.get(F),
        '"scientificName": "Rubricatochromis bimaculatus（旧名 Hemichromis bimaculatus）",',
        '"scientificName": "Hemichromis bimaculatus",', 'fishnames-jewel-old'));
    } },
  { dir: 'fishnames-electric-naked', checker: 'check-fish-names.js', expect: 1,
    desc: '电鳗的学名写成拆分前的裸名（2019 年拆成三种这件事被抹掉，"860V 属于 E. voltai"也没了）',
    mutate: (at) => {
      const F = 'fish_research/electric_eel.json';
      const s = at.get(F);
      const from = /"scientificName": "Electrophorus electricus（[\s\S]*?）",\n/.exec(s);
      if (!from) throw new Error('找不到电鳗的 scientificName 整行');
      at.set(F, s.replace(from[0], '"scientificName": "Electrophorus electricus",\n'));
    } },
  { dir: 'fishnames-butterfly-display', checker: 'check-fish-names.js', expect: 1,
    desc: '游戏里显示名退回"非洲蝴蝶鱼"（规范名是齿蝶鱼 —— 这是唯一真会显示给玩家的那处）',
    mutate: (at) => {
      at.set('lang/zh_cn.json', mustReplace(at.get('lang/zh_cn.json'),
        '"item.anglertackle.african_butterflyfish": "齿蝶鱼",',
        '"item.anglertackle.african_butterflyfish": "非洲蝴蝶鱼",',
        'fishnames-butterfly-display'));
    } },
  { dir: 'fishnames-table-row', checker: 'check-fish-names.js', expect: 1,
    desc: '策划文档里那张"修正 7 条"表被删掉一行（表与实际规则数不再相等）',
    mutate: (at) => {
      const lines = at.get('docs/料理适配策划案.md').split('\n');
      const head = lines.findIndex((l) => /^\s*\| # \|/.test(l) && l.includes('原来的写法'));
      if (head < 0) throw new Error('找不到修正表表头');
      lines.splice(head + 2, 1);   // 删第一行数据
      at.set('docs/料理适配策划案.md', lines.join('\n'));
    } },
  // ---- check-tuning-fingerprint.js（#7：调参不能被版本更新改回去，2026-10-11）----
  // 这条检查器平时**跳过**（没有《调参变更记录》时如实说明并 exit 0）⇒ 它原来一条负例都没有，
  // 也就是说"跳过"与"通过"看起来一模一样。这里用 createFiles 造出最小夹具（记录 + toml），
  // 让它真的跑起来，再逐条证伪。
  { dir: 'tuningprint-ok', checker: 'check-tuning-fingerprint.js', expect: 0,
    desc: '对照组：记录里的两个键在 toml 里原样健在（应当通过）',
    createFiles: [
      ['调参变更记录.md', '# 调参变更记录\n\n<!-- atl-tuning-fingerprint\nsaved=2026-01-01T00:00:00.000Z\n'
        + 'crucianCarpPullSpeed=1.05\ntopmouthCulterPullSpeed=1.42\n-->\n'],
      ['config/anglertackle-common.toml', '[fish]\n    crucianCarpPullSpeed = 1.05\n'
        + '    topmouthCulterPullSpeed = 1.42\n'],
    ],
    mutate: () => {} },
  { dir: 'tuningprint-value-changed', checker: 'check-tuning-fingerprint.js', expect: 1,
    desc: '版本更新把调过的值冲回默认（toml 里的值 ≠ 你保存时的值）',
    createFiles: [
      ['调参变更记录.md', '# 调参变更记录\n\n<!-- atl-tuning-fingerprint\nsaved=2026-01-01T00:00:00.000Z\n'
        + 'crucianCarpPullSpeed=1.05\n-->\n'],
      ['config/anglertackle-common.toml', '[fish]\n    crucianCarpPullSpeed = 1.00\n'],
    ],
    mutate: () => {} },
  { dir: 'tuningprint-key-missing', checker: 'check-tuning-fingerprint.js', expect: 1,
    desc: '键改名/挪分组（记录里有、toml 里找不到 ⇒ 你调的值无处可迁）',
    createFiles: [
      ['调参变更记录.md', '# 调参变更记录\n\n<!-- atl-tuning-fingerprint\nsaved=2026-01-01T00:00:00.000Z\n'
        + 'crucianCarpPullSpeed=1.05\n-->\n'],
      ['config/anglertackle-common.toml', '[fish]\n    crucianCarpPullSpeedNew = 1.05\n'],
    ],
    mutate: () => {} },
  { dir: 'tuningprint-newest-wins', checker: 'check-tuning-fingerprint.js', expect: 0,
    desc: '两份记录不许互相覆盖：同一键取 saved= 最新的那份（拿旧记录去打新值就会误报）',
    createFiles: [
      ['调参变更记录.md', '# 调参变更记录（旧）\n\n<!-- atl-tuning-fingerprint\nsaved=2026-01-01T00:00:00.000Z\n'
        + 'crucianCarpPullSpeed=1.05\n-->\n'],
      ['docs/调参变更记录-第二次.md', '# 调参变更记录（新）\n\n<!-- atl-tuning-fingerprint\n'
        + 'saved=2026-03-01T00:00:00.000Z\ncrucianCarpPullSpeed=1.20\n-->\n'],
      ['config/anglertackle-common.toml', '[fish]\n    crucianCarpPullSpeed = 1.20\n'],
    ],
    mutate: () => {} },
  // ---- check-chum.js：同水体共享 + 全服 40 上限（用户 2026-10-11）----
  { dir: 'chum-ok', checker: 'check-chum.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'chum-owner-gate', checker: 'check-chum.js', expect: 1,
    desc: '加成判定按"谁打的窝"过滤 ⇒ 同水体共享被悄悄改回独享（不崩、不报错，只是别人站进白圈没加成）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/ChumManager.java';
      at.set(F, mustReplace(at.get(F),
        'e -> e.isActive() && e.isAlive());',
        'e -> e.isActive() && e.isAlive() && e.ownerUuid() != null);',
        'chum-owner-gate'));
    } },
  { dir: 'chum-cap-no-reserve', checker: 'check-chum.js', expect: 1,
    desc: '抛窝不再占全服名额（上限没接上：谁都能无限打窝，防刷规则落空）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/item/ChumItem.java';
      at.set(F, mustReplace(at.get(F),
        '.reserve(chum.getUUID(), now, expireAt)',
        '.reserveDISABLED(chum.getUUID(), now, expireAt)',
        'chum-cap-no-reserve'));
    } },
  { dir: 'chum-cap-after-spawn', checker: 'check-chum.js', expect: 1,
    desc: '先 addFreshEntity 再占名额（满员时水面上已经先冒出一条窝子，而且物品已经扣了）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/item/ChumItem.java';
      const s = at.get(F);
      const iRes = s.indexOf('if (!com.whitecobalt.anglertackle.fishing.ChumRegistry.get(serverLevel)');
      const iFail = s.indexOf('return InteractionResultHolder.fail(stack);', iRes);
      if (iRes < 0 || iFail < 0) throw new Error('找不到名额检查块');
      const iBlockEnd = s.indexOf('}', iFail) + 1;    // 连 if 的右花括号一起搬走
      const block = s.slice(iRes, iBlockEnd);
      const withoutBlock = s.slice(0, iRes) + s.slice(iBlockEnd);
      const marker = 'serverLevel.addFreshEntity(chum);';
      const iAdd = withoutBlock.indexOf(marker);
      if (iAdd < 0) throw new Error('找不到 addFreshEntity');
      at.set(F, withoutBlock.slice(0, iAdd + marker.length) + '\n' + block
        + withoutBlock.slice(iAdd + marker.length));
    } },
  // ---- check-command-permissions.js（指令默认人人可用，除非明显影响平衡；用户 2026-10-11）----
  { dir: 'cmdperm-ok', checker: 'check-command-permissions.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'cmdperm-stock-open', checker: 'check-command-permissions.js', expect: 1,
    desc: '把 resetstock 从 OP 白名单里删掉 ⇒ 普通玩家能一直清零限购（限购 1 的附魔书可以反复买）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '            "resetstock",\n', '', 'cmdperm-stock-open'));
    } },
  { dir: 'cmdperm-rank-gated', checker: 'check-command-permissions.js', expect: 1,
    desc: '把 rank 加进 OP 白名单 ⇒ 违背用户"排行榜无需权限"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      // ⚠ 锚点用**第一条**（stage）而不是最后一条：白名单后面还会加（grant/revoke/send 都加过），
      //   锚在末尾的话每加一条就要回来改一次（2026-10-12 已经因为加了 send 断过一次 ✗）。
      at.set(F, mustReplace(at.get(F), '            "stage",\n',
        '            "stage",\n            "rank",\n',
        'cmdperm-rank-gated'));
    } },
  { dir: 'cmdperm-bare-literal', checker: 'check-command-permissions.js', expect: 1,
    desc: '某条指令退回手写 Commands.literal ⇒ 权限又变成"当时那个人随手决定"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '.then(lit("shop")', '.then(Commands.literal("shop")',
        'cmdperm-bare-literal'));
    } },
  { dir: 'cmdperm-second-requires', checker: 'check-command-permissions.js', expect: 1,
    desc: '某条指令上又手写一处 hasPermission（权限出现第二个真源，以后改一处漏一处）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '.then(lit("codex").executes(',
        '.then(lit("codex").requires(src -> src.hasPermission(2)).executes(', 'cmdperm-second-requires'));
    } },
  // ---- 称号的两条（2026-10-11；第一条 2026-10-12 换了触发方式）----
  { dir: 'cmdperm-title-group-gated', checker: 'check-command-permissions.js', expect: 1,
    desc: '把 /atl title 这层塞进 OP 白名单 ⇒ Brigadier 向下继承，把 list/use 一起锁掉'
      + '（2026-10-12 起 title 自己也可执行 ⇒ 它的门槛只可能从 OP_ONLY 来，'
      + '所以这条负例现在锚在 OP_ONLY 上；检查器的"恰好这几条"会当场判红）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '            "stage",\n',
        '            "stage",\n            "title",\n', 'cmdperm-title-group-gated'));
    } },
  { dir: 'cmdperm-title-grant-open', checker: 'check-command-permissions.js', expect: 1,
    desc: '把 grant 从 OP 白名单里删掉 ⇒ 任何人都能给自己发称号（含"服主授予"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '            "grant",\n', '', 'cmdperm-title-grant-open'));
    } },
  { dir: 'cmdperm-mail-send-open', checker: 'check-command-permissions.js', expect: 1,
    desc: '把 send 从 OP 白名单里删掉 ⇒ 任何人都能给任意玩家发信并附带鱼币（凭空给钱）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      // ⚠ 锚点用 `"send",`（带逗号）而不是 `"send");`：
      //   白名单后面还会加条目（2026-10-12 加 start/settle 时就把 `"send");` 变成了 `"send",`，
      //   这条负例当场在"造副本"阶段抛锚点错 —— 而闸报出来的是那句极具误导性的
      //   "存在漏检的检查脚本"✗。锚点要挑**不会被下一个人改掉**的写法。
      at.set(F, mustReplace(at.get(F), '            "send",', '            "send_off",',
        'cmdperm-mail-send-open'));
    } },

  // ---- check-titles.js：称号系统（用户 2026-10-11："开发称号系统"）----
  { dir: 'titles-ok', checker: 'check-titles.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'titles-name-bare-noun', checker: 'check-titles.js', expect: 1,
    desc: '把一个名字改成光秃秃的「巨物」（用户点名的写法：人家不知道干嘛）',
    mutate: (at) => {
      at.set('src/main/resources/assets/anglertackle/lang/zh_cn.json',
        mustReplace(at.get('src/main/resources/assets/anglertackle/lang/zh_cn.json'),
          '"title.anglertackle.giant_100": "巨物大师"', '"title.anglertackle.giant_100": "巨物"',
          'titles-name-bare-noun'));
    } },
  { dir: 'titles-name-too-long', checker: 'check-titles.js', expect: 1,
    desc: '名字写到 8 个字（聊天里要后缀在玩家名后面，长了就挤）',
    mutate: (at) => {
      at.set('src/main/resources/assets/anglertackle/lang/zh_cn.json',
        mustReplace(at.get('src/main/resources/assets/anglertackle/lang/zh_cn.json'),
          '"title.anglertackle.big_three": "三冠王"',
          '"title.anglertackle.big_three": "天下无敌的三冠王"', 'titles-name-too-long'));
    } },
  { dir: 'titles-name-fantasy', checker: 'check-titles.js', expect: 1,
    desc: '名字改成奇幻称谓（「钓神至尊」）—— 这才是用户说的"中二"',
    mutate: (at) => {
      at.set('src/main/resources/assets/anglertackle/lang/zh_cn.json',
        mustReplace(at.get('src/main/resources/assets/anglertackle/lang/zh_cn.json'),
          '"title.anglertackle.big_three": "三冠王"',
          '"title.anglertackle.big_three": "钓神至尊"', 'titles-name-fantasy'));
    } },
  { dir: 'titles-missing-lang', checker: 'check-titles.js', expect: 1,
    desc: '删掉一个称号的中文名 ⇒ 游戏里聊天/列表显示成一串 title.anglertackle.xxx',
    mutate: (at) => {
      at.set('src/main/resources/assets/anglertackle/lang/zh_cn.json',
        mustReplace(at.get('src/main/resources/assets/anglertackle/lang/zh_cn.json'),
          '"title.anglertackle.night_owl": "夜钓客",\n', '', 'titles-missing-lang'));
    } },
  { dir: 'titles-doc-drift', checker: 'check-titles.js', expect: 1,
    desc: '代码表里加一个文档没有的 id（表与 docs/38 脱节 ⇒ 文档不再是实现依据）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/TitleDefs.java';
      at.set(F, mustReplace(at.get(F), 'new Def("storm_rider", 3, false),',
        'new Def("storm_rider", 3, false),\n            new Def("ghost_title", 1, false),',
        'titles-doc-drift'));
    } },
  { dir: 'titles-gold-hardcoded', checker: 'check-titles.js', expect: 1,
    desc: '「金鳞全录」的种数写死成 47（= 永远拿不到的条件：17 种鱼档位上限只到紫）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/Titles.java';
      at.set(F, mustReplace(at.get(F), 'if (sp != null && sp.maxTier() >= 4) {',
        'if (sp != null && 47 >= 4) {', 'titles-gold-hardcoded'));
    } },
  { dir: 'titles-nonrepeat-guardless', checker: 'check-titles.js', expect: 1,
    desc: '去掉 §9.6 那道闸（不可重复称号已拥有 ⇒ 不 +1）—— 重复 grant 又会留下无意义计数',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/Titles.java';
      at.set(F, mustReplace(at.get(F), 'if (def != null && !def.repeatable() && cur.has(id)) {',
        'if (false) {', 'titles-nonrepeat-guardless'));
    } },
  // ---- check-achievement.js：成就系统（docs/41 §7 A1，2026-10-15）----
  // A2 自动达成（2026-10-15 晚，双 AI 接力）：[F] 组每条断言各配一条负例 —— 这一组盯的是
  // "钓到鱼会不会自动点亮"，它坏了玩家只会觉得"怎么没反应"、不会报错，所以必须证明改坏了真会红。
  { dir: 'ach-oncatch-not-called', checker: 'check-achievement.js', expect: 1,
    desc: '挂钩点不再调 AchievementService.onCatch —— 上鱼类成就退化成"只能手敲指令"，等于没做 A2',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/leaderboard/LeaderboardHooks.java';
      at.set(F, mustReplace(at.get(F), 'AchievementService.onCatch(',
        'AchievementServiceX.onCatch(', 'ach-oncatch-not-called'));
    } },
  { dir: 'ach-oncatch-no-codex-plus1', checker: 'check-achievement.js', expect: 1,
    desc: '去掉图鉴种数的 +1 时间差补正 —— 刚好凑满里程碑的那一杆永远点亮不了（差一条鱼，极难复现）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), 'int codexAfter = fd.codexCount() + (newSpecies ? 1 : 0);',
        'int codexAfter = fd.codexCount();', 'ach-oncatch-no-codex-plus1'));
    } },
  { dir: 'ach-codex-hardcoded-total', checker: 'check-achievement.js', expect: 1,
    desc: '鱼类通鉴的总数写死成 47 —— 以后加鱼种它就永远点不亮了（必须读 FishSpecies.ALL.size()）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), 'if (codexAfter >= FishSpecies.ALL.size()) {',
        'if (codexAfter >= 47) {', 'ach-codex-hardcoded-total'));
    } },
  { dir: 'ach-tier-const-wrong', checker: 'check-achievement.js', expect: 1,
    desc: '红档常量从 5 改成 6 —— 档位口径（0 起：白0…红5）被写错，"开门红"永远不会触发',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), 'private static final int TIER_RED = 5;',
        'private static final int TIER_RED = 6;', 'ach-tier-const-wrong'));
    } },
  { dir: 'ach-def-missing-entry', checker: 'check-achievement.js', expect: 1,
    desc: '定义表少一条（11 → 10）—— 少了的那条成就玩家永远拿不到，而代码看起来一切正常',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementDef.java';
      at.set(F, mustReplace(at.get(F), '        put("a_codex_5", 1);       // 鱼种记录者：图鉴 5 种\n',
        '', 'ach-def-missing-entry'));
    } },
  // A5（同条件授予**既有**称号，docs/41 §9）两条负例：静默失败与顺序错位都抓。
  { dir: 'ach-no-title-grant', checker: 'check-achievement.js', expect: 1,
    desc: '拿掉 Titles.grant 那一行 —— 同条件的既有称号永远授予不了，玩家只会觉得"说好的称号呢"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), '            Titles.grant(player, titleId);',
        '            // Titles.grant(player, titleId);', 'ach-no-title-grant'));
    } },
  { dir: 'ach-title-id-typo', checker: 'check-achievement.js', expect: 1,
    desc: '映射里的称号 id 打错一个字母（codex_24 → codex_24x）—— Titles.grant 会**静默返回 false**，什么都不发生',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementDef.java';
      at.set(F, mustReplace(at.get(F), 'Map.entry("a_codex_24", "codex_24"),', 'Map.entry("a_codex_24", "codex_24x"),',
        'ach-title-id-typo'));
    } },
  // A4 面板（docs/41 §7）三条负例：接线断在哪一步，症状都是"敲了 /atl ach 什么都没发生"。
  { dir: 'ach-screen-unregistered', checker: 'check-achievement.js', expect: 1,
    desc: '客户端不再注册 AchievementPayload —— 服务端推了没人收，面板永远弹不出来（"没反应"最难查）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java';
      at.set(F, mustReplace(at.get(F),
        'playToClient(com.whitecobalt.anglertackle.network.AchievementPayload.TYPE',
        'playToClientX(com.whitecobalt.anglertackle.network.AchievementPayload.TYPE',
        'ach-screen-unregistered'));
    } },
  { dir: 'ach-screen-no-open', checker: 'check-achievement.js', expect: 1,
    desc: '收到快照但不开界面（setScreen 注释掉）—— 数据到了、界面不弹，玩家以为没做',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java';
      at.set(F, mustReplace(at.get(F),
        '                        mc.setScreen(new com.whitecobalt.anglertackle.client.screen.AchievementScreen());',
        '                        // mc.setScreen(new com.whitecobalt.anglertackle.client.screen.AchievementScreen());',
        'ach-screen-no-open'));
    } },
  { dir: 'ach-sync-not-sent', checker: 'check-achievement.js', expect: 1,
    desc: '/atl ach 不再推快照 —— 界面永远停在"读取中…"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F),
        '        com.whitecobalt.anglertackle.network.ModNetwork.syncAchievements(player);',
        '        // com.whitecobalt.anglertackle.network.ModNetwork.syncAchievements(player);',
        'ach-sync-not-sent'));
    } },
  { dir: 'ach-screen-hazy-render', checker: 'check-achievement.js', expect: 1,
    desc: '把 super.render() 加回来 —— 全屏暗化画在面板与文字之后，界面糊成一层雾（用户 2026-10-15 截图抓到的那次）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/AchievementScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        this.renderBackground(g, mouseX, mouseY, partialTick);',
        '        this.renderBackground(g, mouseX, mouseY, partialTick);\n'
        + '        super.render(g, mouseX, mouseY, partialTick);', 'ach-screen-hazy-render'));
    } },
  { dir: 'ach-level-hook-missing', checker: 'check-achievement.js', expect: 1,
    desc: '升级钩子从唯一经验入口（FishermanData.addXp）里拿掉 —— 等级类成就永远点不亮，而升级本身一切正常',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/FishermanData.java';
      at.set(F, mustReplace(at.get(F),
        'com.whitecobalt.anglertackle.achievement.AchievementService.syncLevel(sp, g.next().level());',
        '// syncLevel removed', 'ach-level-hook-missing'));
    } },
  { dir: 'ach-dead-entry', checker: 'check-achievement.js', expect: 1,
    desc: '有条目但没有触发点（把 i_rain 的 unlock 注释掉）—— 玩家永远点不亮，而界面一切正常，最难发现',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), 'unlock(player, "i_rain");',
        '// unlock(player, "i_rain");', 'ach-dead-entry'));
    } },
  { dir: 'achievement-ok', checker: 'check-achievement.js', expect: 0,
    desc: '对照组：源码不动时成就检查必须通过（缺了它，下面两条"抓到了"分不清是真抓到还是检查器自己坏了）' },
  { dir: 'achievement-no-copyondeath', checker: 'check-achievement.js', expect: 1,
    desc: '成就附件去掉 copyOnDeath() —— 死一次成就全掉（与称号/委托同一条口径：永久数据不许因死亡丢失）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/registry/ModAttachments.java';
      at.set(F, mustReplace(at.get(F), '.serialize(AchievementData.CODEC)\n                    .copyOnDeath()',
        '.serialize(AchievementData.CODEC)', 'achievement-no-copyondeath'));
    } },
  { dir: 'achievement-fieldof', checker: 'check-achievement.js', expect: 1,
    desc: '成就 Codec 的 optionalFieldOf 改成 fieldOf —— 老存档没有这个键，整份成就数据读不进来（零迁移纪律被破坏）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/AchievementData.java';
      at.set(F, mustReplace(at.get(F), 'UNLOCKED_CODEC.optionalFieldOf("unlocked", Set.of())',
        'UNLOCKED_CODEC.fieldOf("unlocked")', 'achievement-fieldof'));
    } },
  { dir: 'achievement-def-id-without-lang', checker: 'check-achievement.js', expect: 1,
    desc: '定义表里加了一条成就但没补语言键 —— 游戏里会把它显示成一串键名（[D] 组必须抓到）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementDef.java';
      // 2026-10-15 A2 改锚点：样板 id first_cast 已按策划案改名为 a_first_catch
      //   （id 与 docs/41 §4 逐字一致）—— 锚点跟着实现走，别让它烂在真树之外。
      at.set(F, mustReplace(at.get(F), 'put("a_first_catch", 1);', 'put("a_first_catch_typo", 1);',
        'achievement-def-id-without-lang'));
    } },
  { dir: 'achievement-mail-full-swallows', checker: 'check-achievement.js', expect: 1,
    desc: '把"邮箱满 ⇒ 不置位"的判据写反（!== 写成 ==）—— 邮箱满时会静默丢奖励（[C] 组必须抓到）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/achievement/AchievementService.java';
      at.set(F, mustReplace(at.get(F), '!= MailService.Result.OK', '== MailService.Result.OK',
        'achievement-mail-full-swallows'));
    } },
  { dir: 'achievement-unwired', checker: 'check-achievement.js', expect: 1,
    desc: '服务写了但没接进 register(...) —— /atl ach 根本不存在，玩家永远碰不到（[E] 组必须抓到）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), 'registerAchievements(dispatcher);',
        '// registerAchievements(dispatcher);', 'achievement-unwired'));
    } },
  // ---- check-fishing-camera.js [G] 组：2026-10-16 用户报的五条 bug（①③④⑤⑥）----
  // 这一组全是"不崩不报错、只是手感/提示悄悄不对"的毛病 —— 正是最需要证伪的那类：
  // 玩家看到的是"上鱼后被锁一秒"，而代码每一行都编译得过、日志里一个字都没有。
  { dir: 'fishing-ok', checker: 'check-fishing-camera.js', expect: 0,
    desc: '对照组：源码不动时相机/状态行/HUD 检查必须通过（缺了它，"抓到了"与"检查器自己坏了"分不清）' },
  { dir: 'fight-lock-only-in-fight', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把移动锁退回"只认 FIGHT"（inFight）—— 等于删掉用户 2026-10-16 定的功能'
      + '"上鱼后一秒内无法移动，不然有人收不住方向键会掉进水里"（我当天第一版就把它当 bug 改错了 ✗）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java';
      at.set(F, mustReplace(at.get(F), 'if (state.movementLocked()) {', 'if (state.inFight()) {',
        'fight-lock-only-in-fight'));
    } },
  { dir: 'fight-lock-no-landing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把「上鱼后那一秒」从移动锁里拿掉（movementLockTicks 不再参与）—— 收线时按住的方向键会把玩家带进水里（用户定的功能）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java';
      at.set(F, mustReplace(at.get(F),
        "        return state == STATE_FIGHT || movementLockTicks > 0;",
        "        return state == STATE_FIGHT;",
        'fight-lock-no-landing'));
    } },

  // ---- 2026-10-28：收线拉近的总闸（用户："不要牵连路亚，只要台钓不放大"）----

  // ---- 2026-10-28：冲刺只能在对抗中关（台钓落鱼时 FOV 跳变的真凶）----

  // ---- 2026-10-28：移动锁归零 MOVEMENT_SPEED ⇒ 原版把视野乘 0.5（台钓上鱼后 80→40 的真凶）----

  // ---- 2026-10-28：上鱼后不许闪回（必须继续盯着浮漂最后位置）----

  // ---- 2026-10-28 用户："钓上来后的视角缩放回复速度太快" ----
  { dir: 'zoom-release-too-fast', checker: 'check-sounds.js', expect: 1,
    desc: '把缩放"松手速率"改成和按下一样快 —— 松手瞬间弹回（用户报过的"回复太快"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        'ZOOM_RELEASE_PER_SECOND = 1.8f',
        'ZOOM_RELEASE_PER_SECOND = 10.0f',
        'zoom-release-too-fast'));
    } },

  // ---- 2026-10-28 用户："钓鱼抛竿后视角转动困难且抛竿时也追随浮漂" ----

  // ---- 2026-10-28：收尾不许看向"抛竿原点"（浮漂消失后仍采样同步坐标）----

  // ---- 2026-10-28：上鱼后必须真的转视角，不许只盖画面 ----

  // ---- 2026-10-28：转向起步不许跳回抛竿点（先用画面当前角度对齐）----

  // ---- 2026-10-29：sounds.json 指向别的命名空间 ⇒ 游戏里没声音且不报错 ----

  // ---- 2026-10-29：力竭退出后立刻重进 ⇒ 状态每轮闪一下（用户听到的"断断续续"）----
  { dir: 'fatigue-lockout-removed', checker: 'check-fatigue-flicker.js', expect: 1,
    desc: '把力竭的再进入冷却判断去掉 —— 体力仍在阈值下时会"退出→下一 tick 重进"，客户端 fatigued 每轮抖一次',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java';
      at.set(F, mustReplace(at.get(F),
        '                && this.fatigueLockoutTicks <= 0\n',
        '\n',
        'fatigue-lockout-removed'));
    } },
  { dir: 'sound-json-cross-namespace', checker: 'check-sounds.js', expect: 1,
    desc: 'sounds.json 里把 anglertackle:lure_cast 写成 minecraft:lure_cast —— 注册表/键/文件名三处不一致，进游戏静音但日志一声不吭',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/sounds.json';
      at.set(F, mustReplace(at.get(F),
        '"name": "anglertackle:lure_cast"',
        '"name": "minecraft:lure_cast"',
        'sound-json-cross-namespace'));
    } },
  { dir: 'turn-starts-with-jump', checker: 'check-fishing-camera.js', expect: 1,
    desc: '去掉"起步对齐"—— 停手那一帧真实朝向还停在抛竿方向，画面先跳到抛竿点再慢慢转过去（用户描述的那个观感）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        'if (hasAngles) {',
        'if (false) {',
        'turn-starts-with-jump'));
    } },
  { dir: 'turn-only-covers-render', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把"转真实视角"退回成"只盖渲染画面" —— 一停手就弹回原朝向，正是用户反复报的"闪回"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        'mc.player.setYRot(ny);',
        '// mc.player.setYRot(ny);',
        'turn-only-covers-render'));
    } },
  { dir: 'lastaim-uses-cast-origin', checker: 'check-fishing-camera.js', expect: 1,
    desc: 'lastAim 采样不再要求浮漂在场 —— 实体消失后 bobberAimPos 退回同步包里的抛竿原点，收尾会看向"点击左键的地方"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        'Vec3 bob = (followable && st.bobberActive()) ? bobberAimPos(event.getPartialTick()) : null;',
        'Vec3 bob = followable ? bobberAimPos(event.getPartialTick()) : null;',
        'lastaim-uses-cast-origin'));
    } },
  { dir: 'movelock-attr-zero-halves-fov', checker: 'check-fishing-camera.js', expect: 1,
    desc: '移动锁改回把 MOVEMENT_SPEED 归零 —— 原版 getFieldOfViewModifier 会算出 (0/walk+1)/2=0.5，视野被砍半',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java';
      at.set(F, mustReplace(at.get(F),
        'player.setDeltaMovement(0.0, 0.0, 0.0);',
        'player.setDeltaMovement(0.0, 0.0, 0.0);\n'
          + '                var speedAttr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);\n'
          + '                if (speedAttr != null) { speedAttr.setBaseValue(0.0); }',
        'movelock-attr-zero-halves-fov'));
    } },
  { dir: 'sprint-off-in-landing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '移动锁里无条件关冲刺 —— 冲刺×1.15 的 FOV 被收回 1.0，台钓上鱼时会看到「视野缩一下」',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java';
      at.set(F, mustReplace(at.get(F),
        'if (state.inFight()) {',
        'if (true) {',
        'sprint-off-in-landing'));
    } },
  { dir: 'reel-zoom-leaks-to-float', checker: 'check-fishing-camera.js', expect: 1,
    desc: '拆掉"非路亚立刻清零"这道总闸 —— 上一场路亚的 zoom 残留会跟到台钓里，表现为台钓上鱼后视野被放大（用户反复反馈过的那个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        "if (!lureZoomLatched || !holdingLureRod()) {",
        "if (false) {",
        'reel-zoom-leaks-to-float'));
    } },
  { dir: 'camera-follow-during-landing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '相机跟随也改用 movementLocked() —— 鱼都进包了镜头还黏着浮漂一秒（相机跟的是张力，不该跟锁绑）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/FishingCamera.java';
      at.set(F, mustReplace(at.get(F),
        'boolean fighting = followable && st.inFight()',
        'boolean fighting = followable && st.movementLocked()', 'camera-follow-during-landing'));
    } },
  { dir: 'hud-status-shows-reel', checker: 'check-fishing-camera.js', expect: 1,
    desc: '状态行里把「收线中/放线」加回去 —— 用户明确要求去掉的那一段（①）又回来了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java';
      at.set(F, mustReplace(at.get(F),
        '                        + "  §7|  " + phase\n',
        '                        + "  §7|  " + phase\n'
        + '                        + "  §7|  " + (this.reeling ? "§e收线中" : "§7放线")\n',
        'hud-status-shows-reel'));
    } },
  { dir: 'hud-await-bite-missing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '删掉"等咬钩"那行提示的调用 —— 屏幕上又变回一片空白，玩家不知道上钩后要按左键（③）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/hud/FishingHud.java';
      at.set(F, mustReplace(at.get(F), '            renderAwaitBiteHint(graphics, screenW, screenH);',
        '            // renderAwaitBiteHint(graphics, screenW, screenH);', 'hud-await-bite-missing'));
    } },
  { dir: 'guide-page-always-resets', checker: 'check-fishing-camera.js', expect: 1,
    desc: '换章判据拿掉（无条件 page=0）—— 点左栏永远跳回第一页（用户报的 ⑥ 原样复现）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java';
      at.set(F, mustReplace(at.get(F),
        'boolean chapterChanged = payload.index() != this.data.index();',
        'boolean chapterChanged = true;', 'guide-page-always-resets'));
    } },
  { dir: 'guide-toc-same-chapter-guard', checker: 'check-fishing-camera.js', expect: 1,
    desc: '删掉"点当前章直接返回"的入口守卫（用户第二次报的那个症状：翻到第 3 页再点当前章又回第 1 页）'
      + '—— 守卫没了之后页码只能靠"服务端回包 + 序号比较"这条网络往返兜着',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java';
      at.set(F, mustReplace(at.get(F),
        '                if (index == data.index()) {\n                    return;\n                }\n',
        '', 'guide-toc-same-chapter-guard'));
    } },
  { dir: 'guide-current-chapter-clickable', checker: 'check-fishing-camera.js', expect: 1,
    desc: '当前章不置灰（b.active = false 拿掉）—— 按钮看起来能点、点了却没反应，玩家会以为界面坏了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            if (index == data.index()) {\n                b.active = false;\n            }\n',
        '', 'guide-current-chapter-clickable'));
    } },
  { dir: 'rod-grip-ok', checker: 'check-line-anchor.js', expect: 0,
    desc: '对照组：源码不动时鱼线锚点 + 竿的「握着」姿势检查必须通过' },
  // ---- 2026-10-16 用户复验的三条：奖励信不带奖励 / 收信不通知 / 活跃度提示命中框太小 ----
  // ---- 2026-10-16：剥注释顺序（它自己也是一条会被踩的纪律）----
  { dir: 'comment-strip-order', checker: 'check-comment-stripping.js', expect: 1,
    desc: '把某个检查器改回「先剥块注释、再剥行注释」—— 行注释里的块注释开头会吞掉后面一大段真实代码，断言于是该红也绿（2026-10-16 实测吞掉 FishingManager 约 200 行）',
    mutate: (at) => {
      // ⚠ 锚点必须用 String.raw 且**逐字**照抄真源（含有没有空格）—— 少一个空格就是
      //   「锚点没匹配上」，而 --check-anchors 会当场告诉你（本次连踩两回）。
      const F = 'tools/check-retrieve.js';
      const src = at.get(F);
      const broken = src.replace(
        String.raw`.replace(/\/\/[^\n]*/g,'').replace(/\/\*[\s\S]*?\*\//g,'')`,
        String.raw`.replace(/\/\*[\s\S]*?\*\//g,'').replace(/\/\/[^\n]*/g,'')`);
      if (broken === src) throw new Error('锚点没匹配上：check-retrieve.js 的剥注释写法变了');
      at.set(F, broken, 'comment-strip-order');
    } },
  { dir: 'comment-strip-ok', checker: 'check-comment-stripping.js', expect: 0,
    desc: '对照组：源码不动时剥注释检查必须通过' },
  { dir: 'quest-reward-mail-empty', checker: 'check-quest.js', expect: 1,
    desc: '奖励信又传空附件（0L, List.of()）—— 信里看不到任何奖励（用户 2026-10-16 报的正是这个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/quest/QuestService.java';
      // ⚠ 锚点写成**单行**（别含真实换行）—— 含换行的锚点在这里踩过一次「锚点没匹配上」，
      //   而它花的排查时间比这条断言本身还长。这一行同时含鱼币与物品两个分支，够独一无二。
      at.set(F, mustReplace(at.get(F),
        'def.givesCoins() ? def.coins() : 0L, reward)',
        '0L, java.util.List.of())',
        'quest-reward-mail-empty'));
    } },
  { dir: 'mail-arrival-not-announced', checker: 'check-quest.js', expect: 1,
    desc: '投递成功后不通知玩家 —— 新信要等下次登录才看得见（用户报的「不会立刻弹出」）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F), '        announce(to, entry);',
        '        // announce(to, entry);', 'mail-arrival-not-announced'));
    } },
  // ---- 2026-10-16：上鱼后那一秒必须「活过会话」----
  { dir: 'catch-lock-dies-with-session', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把服务端的移动锁改回"只认会话还在"—— 上鱼那一刻会话就结束了，于是那一秒里速度属性已经还原，玩家带着动量走出去（用户 2026-10-16 报的「移动没锁一秒」）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java';
      at.set(F, mustReplace(at.get(F),
        "if (session.state().blocksMovement() || catchLockActive(player)) {",
        "if (session.state().blocksMovement()) {",
        'catch-lock-dies-with-session'));
    } },
  // ---- 2026-10-16：新信提示必须"两条路都有"（聊天栏 + 动作栏）----
  { dir: 'mail-toast-only', checker: 'check-mail.js', expect: 1,
    desc: '新信只发动作栏（那一行两秒淡出、不进聊天记录）—— 玩家刚上鱼时必然错过，然后以为「要等下次登录才寄来」（用户 2026-10-16 复验的原话）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        "mail_arrived\", title), false);",
        "mail_arrived\", title), true);",
        'mail-toast-only'));
    } },
  { dir: 'mail-push-autoopens', checker: 'check-quest.js', expect: 1,
    desc: '后台收信也用 autoOpen=true 推快照 —— 玩家正在钓鱼/打怪时会被弹开一个邮箱',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F), 'MailSnapshot.of(to, "", false)',
        'MailSnapshot.of(to, "", true)', 'mail-push-autoopens'));
    } },
  { dir: 'quest-activity-hitbox-tiny', checker: 'check-quest.js', expect: 1,
    desc: '活跃度行命中框缩回紧贴文字 —— 玩家稍偏一点就「什么都没有」（用户报的活跃奖励节点没提示）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/QuestScreen.java';
      at.set(F, mustReplace(at.get(F),
        'if (inRect(mouseX, mouseY, left + 3, top + HEADER_H - 2, panelW - 6, ACTIVITY_H + 8)) {',
        'if (inRect(mouseX, mouseY, left + 6, top + HEADER_H, panelW - 12, ACTIVITY_H + 6)) {',
        'quest-activity-hitbox-tiny'));
    } },
  { dir: 'rod-grip-parent-reverted', checker: 'check-line-anchor.js', expect: 1,
    desc: '有个竿模型退回 item/generated（= 用户说的"像木棍那样端着"）—— 握着姿势整个丢了',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/models/item/diamond_float_rod.json';
      at.set(F, mustReplace(at.get(F), '"parent": "anglertackle:item/rod_grip"',
        '"parent": "minecraft:item/generated"', 'rod-grip-parent-reverted'));
    } },
  { dir: 'rod-grip-third-person-upside-down', checker: 'check-line-anchor.js', expect: 1,
    desc: '第三人称 Z 角改回 +55（照抄剑的符号）—— 竿被倒过来，手柄朝天（用户 2026-10-16 报的正是这个；'
      + 'J3 那条比数字的断言抓不到它，只有「竿到底朝哪」这条算方向的能抓）',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/models/item/rod_grip.json';
      const o = JSON.parse(at.get(F));
      o.display.thirdperson_righthand.rotation = [0, -90, 55];
      at.set(F, JSON.stringify(o, null, 2) + '\n', 'rod-grip-third-person-upside-down');
    } },
  { dir: 'rod-grip-gui-stripped', checker: 'check-line-anchor.js', expect: 1,
    desc: 'display 里只留 firstperson/hirdperson、把 gui 那几项删掉 —— 背包图标/掉落物/头上/展示框一起坏（display 是整体替换父级那一份，不是逐键合并）',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/models/item/rod_grip.json';
      const o = JSON.parse(at.get(F));
      delete o.display.gui; delete o.display.ground; delete o.display.head; delete o.display.fixed;
      at.set(F, JSON.stringify(o, null, 2) + '\n', 'rod-grip-gui-stripped');
    } },
  { dir: 'rod-grip-fp-vanilla', checker: 'check-line-anchor.js', expect: 1,
    desc: '把第一人称改回原版那一套数字 —— 换了父级却什么都没变（与 generated/handheld 逐字相同）',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/models/item/rod_grip.json';
      at.set(F, mustReplace(at.get(F),
        '"translation": [\n        1.05,\n        3.45,\n        0.95\n      ]',
        '"translation": [\n        1.13,\n        3.2,\n        1.13\n      ]', 'rod-grip-fp-vanilla'));
    } },
  { dir: 'guide-toc-reset-on-chapter', checker: 'check-fishing-camera.js', expect: 1,
    desc: '换章时把目录页码一起归零（tocTop = 0）—— 用户报过两次的「点左栏目录弹回第 1 页」',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/GuideScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            this.page = 0;          // ⚠ 只动正文页码 —— tocTop 一个字节都不许碰（见上面的纪律 1）',
        '            this.page = 0;\n            this.tocTop = 0;', 'guide-toc-reset-on-chapter'));
    } },
  { dir: 'fight-lock-server-only-fight', checker: 'check-fishing-camera.js', expect: 1,
    desc: '服务端那半把「上鱼后那一秒」从判据里拿掉（catchLockActive 被短路成死代码）——「客户端怎么发包都走不动」这条保证就只剩对抗期了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingManager.java';
      at.set(F, mustReplace(at.get(F),
        "if (session.state().blocksMovement() || catchLockActive(player)) {",
        "if (session.state().blocksMovement() || (false && catchLockActive(player))) {",
        'fight-lock-server-only-fight'));
    } },


  { dir: 'fight-lock-state-enum-narrow', checker: 'check-fishing-camera.js', expect: 1,
    desc: 'State.blocksMovement() 只认 FIGHT —— 服务端与客户端两侧同时丢掉「上鱼后还锁着」',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/FishingSession.java';
      at.set(F, mustReplace(at.get(F),
        "            return this == FIGHT || this == LANDING;",
        "            return this == FIGHT;",
        'fight-lock-state-enum-narrow'));
    } },


  { dir: 'hud-await-bite-centered', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把等咬钩提示挪回屏幕中心线下方（screenH/2+24）—— 那一带是动作栏与经验条，'
      + '三样挤在一起就是用户 2026-10-16 截图里的"文本重叠"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/hud/FishingHud.java';
      at.set(F, mustReplace(at.get(F),
        '        int y = screenH - 118;           // 面板从 screenH-12-rows*11 往上长，这里是它上面的空白',
        '        int y = screenH / 2 + 24;', 'hud-await-bite-centered'));
    } },
  { dir: 'hud-await-bite-no-backing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '去掉提示的半透明黑底 —— 亮水面/木板上读不清（张力条与耐久面板都有底板，这条也该有）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/hud/FishingHud.java';
      at.set(F, mustReplace(at.get(F),
        '        graphics.fill(x - 3, y - 4, x + w + 3, y + 10, 0x90000000);\n', '',
        'hud-await-bite-no-backing'));
    } },
  { dir: 'kit-remind-missing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '删掉"礼包还没领"的登录提醒 —— 玩家漏看那一行聊天就以为没发教程（用户报的 ⑤）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/FirstJoinEvents.java';
      at.set(F, mustReplace(at.get(F), 'if (unclaimedKit(player)) {', 'if (false) {',
        'kit-remind-missing'));
    } },
  { dir: 'kit-unread-condition-flipped', checker: 'check-fishing-camera.js', expect: 1,
    desc: 'withKitUnread 的条件写反（!m.unread() → m.unread()）—— 方法退化成"原样返回"而照旧编译得过'
      + '（这个反条件本轮真的犯过一次，全靠断言抓住）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/MailBox.java';
      at.set(F, mustReplace(at.get(F), '&& !m.unread()) {', '&& m.unread()) {',
        'kit-unread-condition-flipped'));
    } },
  { dir: 'tension-bars-hide-on-landing', checker: 'check-fishing-camera.js', expect: 1,
    desc: '把 LANDING 加回张力条/鱼力条的可见性 —— 鱼都给了、条子还挂着、视角还没回来',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientFishingState.java';
      // ⚠ 单行锚点：多行（带引号/注释）的那种改一次实现就烂一次（今天已栽两回）
      at.set(F, mustReplace(at.get(F),
        'public boolean tensionBarsVisible() {',
        'public boolean tensionBarsVisible() {\n        if (true) { return state == STATE_FIGHT || state == STATE_LANDING; }',
        'tension-bars-hide-on-landing'));
    } },
  { dir: 'titles-chat-cancel-all', checker: 'check-titles.js', expect: 1,
    desc: '去掉"没佩戴就放行"的早退 ⇒ 未使用该功能的玩家聊天也被改写（违背纯加法）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/TitleChatEvents.java';
      at.set(F, mustReplace(at.get(F), '            if (id.isEmpty() || !Titles.get(player).has(id)) {\n'
        + '                return;   // 没佩戴（或佩了个已经没有的 id）⇒ 原版聊天照旧 ✓\n            }',
        '', 'titles-chat-cancel-all'));
    } },
  { dir: 'titles-chat-self-only', checker: 'check-titles.js', expect: 1,
    desc: '把广播改成只发给发言者自己 ⇒ 称号变成"只有自己看得见"的花架子（用户点名不许）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/TitleChatEvents.java';
      at.set(F, mustReplace(at.get(F), 'player.server.getPlayerList().broadcastSystemMessage(line, false);',
        'player.sendSystemMessage(line);', 'titles-chat-self-only'));
    } },
  { dir: 'titles-no-copy-on-death', checker: 'check-titles.js', expect: 1,
    desc: '称号附件去掉 copyOnDeath ⇒ 玩家死一次称号全没（"永久拥有"当场失效）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/registry/ModAttachments.java';
      at.set(F, mustReplace(at.get(F),
        '                    .builder(() -> PlayerTitles.EMPTY)\n'
        + '                    .serialize(PlayerTitles.CODEC)\n'
        + '                    .copyOnDeath()\n',
        '                    .builder(() -> PlayerTitles.EMPTY)\n'
        + '                    .serialize(PlayerTitles.CODEC)\n', 'titles-no-copy-on-death'));
    } },

  // ---- check-titles.js 的界面组（2026-10-12，用户："称号佩戴在哪，很难发现。"）----
  //   这一批的共同点：**每一条坏掉都不报错**，只是"玩家还是找不到 / 状态悄悄不对"。
  { dir: 'codex-footer-overlap', checker: 'check-codex.js', expect: 1,
    desc: '翻页 [>] 又锚回面板右缘 ⇒ 它和「排行榜」按钮叠在同一块地方，'
      + '谁接到点击取决于加入顺序（原来的 bug 原样复现）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java';
      at.set(F, mustReplace(at.get(F), '}).bounds(nextX, top + panelH - 22, navW, 18).build();',
        '}).bounds(left + panelW - 34, top + panelH - 22, 26, 18).build();', 'codex-footer-overlap'));
    } },
  { dir: 'titles-no-command-entry', checker: 'check-titles.js', expect: 1,
    desc: '把 /atl title 的 .executes 删掉 ⇒ 界面还在，但玩家没有任何一条路能打开它（"很难发现"原样复现）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F),
        '                        .executes(ctx -> openTitles(ctx.getSource()))\n', '',
        'titles-no-command-entry'));
    } },
  { dir: 'titles-hide-locked', checker: 'check-titles.js', expect: 1,
    desc: '默认改成"只看已获得" ⇒ 没拿到的称号连条件都看不见，与"发现得了"正好相反',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java';
      at.set(F, mustReplace(at.get(F), 'private boolean onlyOwned = false;',
        'private boolean onlyOwned = true;', 'titles-hide-locked'));
    } },
  { dir: 'titles-client-equips', checker: 'check-titles.js', expect: 1,
    desc: '界面自己改数据（不发动作包）⇒ 客户端能决定"我戴着哪个"，服务端权威那条纪律破了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        PacketDistributor.sendToServer(new TitleActionPayload(\n'
        + '                r.equipped ? TitleActionPayload.UNEQUIP : TitleActionPayload.EQUIP, r.id));',
        '        com.whitecobalt.anglertackle.player.PlayerData.updateTitles(null, x -> x.withEquipped(r.id));',
        'titles-client-equips'));
    } },
  { dir: 'titles-use-bypasses-equip', checker: 'check-titles.js', expect: 1,
    desc: '/atl title use 绕开 Titles.equip 自己改数据 ⇒ 指令与界面各判一套（迟早分叉）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F),
        'var r = com.whitecobalt.anglertackle.title.Titles.equip(player, id);',
        'var r = com.whitecobalt.anglertackle.title.Titles.EquipResult.OK;',
        'titles-use-bypasses-equip'));
    } },
  { dir: 'titles-payload-uuid', checker: 'check-titles.js', expect: 1,
    desc: '给动作包加一个 targetUuid ⇒ 协议层出现"替别人佩戴"的可能（可以改别人的显示身份）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/network/TitleActionPayload.java';
      at.set(F, mustReplace(at.get(F), 'public record TitleActionPayload(int action, String titleId)',
        'public record TitleActionPayload(int action, String titleId, java.util.UUID targetUuid)',
        'titles-payload-uuid'));
    } },
  { dir: 'titles-screen-drawstring', checker: 'check-titles.js', expect: 1,
    desc: '界面退回 drawString（不用 TextWidget）⇒ 本机环境下文字是糊的（docs/16 明令）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        TextWidget widget = new TextWidget(x, y - 2, w, 12, UiLayout.fitText(text, w), color, true, 0, 0);',
        '        Object widget = null;', 'titles-screen-drawstring'));
    } },
  { dir: 'titles-no-codex-entry', checker: 'check-titles.js', expect: 1,
    desc: '删掉图鉴上的「称号」按钮 ⇒ 玩家最常打开的界面里找不到它（入口少一个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java';
      at.set(F, mustReplace(at.get(F),
        '                                com.whitecobalt.anglertackle.network.TitleActionPayload.OPEN, "")))',
        '                                com.whitecobalt.anglertackle.network.MailActionPayload.OPEN, -1)))',
        'titles-no-codex-entry'));
    } },
  { dir: 'titles-no-keys', checker: 'check-titles.js', expect: 1,
    desc: '把 keyPressed 覆写改名（键盘导航没了）⇒ 只能用鼠标逐行点；'
      + '⚠ 这条专门盯"只搜字符串"那种松判据：方法体里的 super.keyPressed / case 265 还在，'
      + '所以判据必须限定在**覆写的方法体**里（与 check-mail.js 的 mail-no-keys 同一类）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java';
      at.set(F, mustReplace(at.get(F),
        '    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {',
        '    public boolean _removedKeys(int keyCode, int scanCode, int modifiers) {',
        'titles-no-keys'));
    } },
  { dir: 'titles-got-hint-removed', checker: 'check-titles.js', expect: 1,
    desc: '拿到称号时那句提示不再提"去哪佩戴" ⇒ 玩家拿到手仍然不知道下一步做什么',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/lang/zh_cn.json';
      at.set(F, mustReplace(at.get(F),
        '"message.anglertackle.title_got": "§6获得称号：%s§7（/atl title 或图鉴里的「称号」按钮可以佩戴）"',
        '"message.anglertackle.title_got": "§6获得称号：%s"', 'titles-got-hint-removed'));
    } },
  { dir: 'titles-row-format-mismatch', checker: 'check-titles.js', expect: 1,
    desc: '快照少写一个字段 ⇒ 客户端按 6 个解析，每行都被丢掉（表现是"界面一片空白"，不报错）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/TitleSnapshot.java';
      // ⚠ 锚点 2026-10-13 搬过家：拼行原来散在 of() 里，为了"生成式实例"补行时收成了
      //   唯一的 row(...) 辅助方法（两处各拼一遍会让 check-titles 把字段数数错 ✗）
      //   所以这条负例改打 row() 里那行 —— 它才是现在"字段数"的唯一来源 ✓
      at.set(F, mustReplace(at.get(F),
        "        return id + '|' + (owned ? 1 : 0) + '|' + count + '|' + (equipped ? 1 : 0) + '|' + holders + '|'\n"
        + "                + String.join(SEP_NAME, shown);",
        "        return id + '|' + (owned ? 1 : 0) + '|' + count + '|' + (equipped ? 1 : 0) + '|' + holders\n"
        + "                + String.join(SEP_NAME, shown);",
        'titles-row-format-mismatch'));
    } },
  { dir: 'titles-no-cache-clear', checker: 'check-titles.js', expect: 1,
    desc: '退出世界不清称号缓存 ⇒ 换存档先闪一眼上一个档的"已获得"（用户会当成真的拿到了）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/CoinSyncEvents.java';
      at.set(F, mustReplace(at.get(F),
        '            com.whitecobalt.anglertackle.client.ClientTitles.clear();\n', '',
        'titles-no-cache-clear'));
    } },
  { dir: 'titles-projection-writes', checker: 'check-titles.js', expect: 1,
    desc: '给客户端看的快照投影里顺手调了 equip ⇒ 只读投影开始改数据（最难查的一类错）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/TitleSnapshot.java';
      at.set(F, mustReplace(at.get(F), '            List<String> who = Titles.holders(player, def.id());',
        '            Titles.equip(player, def.id());\n'
        + '            List<String> who = Titles.holders(player, def.id());',
        'titles-projection-writes'));
    } },
  { dir: 'titles-no-empty-state', checker: 'check-titles.js', expect: 1,
    desc: '"只看已获得"筛成空时那一行字不画 ⇒ 空列表与"界面坏了"长得一模一样',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/TitleScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            addText(listX + 6, listTop + 20, tr("gui.anglertackle.title_empty_hint", snap.total()),\n'
        + '                    C_HINT, LIST_W - 12);',
        '            // 空状态提示删掉（负例）',
        'titles-no-empty-state'));
    } },

  // ---- check-titles.js 的 J 组：头顶名牌（2026-10-13，用户："需要让玩家在他人显示视角里
  //      看到名字后有已经佩戴的称号。"）----
  //   这一批的共同点：**每一条坏掉都不报错**，只是"别人看不到 / 看到不该看的 /
  //   网络线程碰了每帧读的缓存"。与 F 段（聊天）是同一条口径的两个显示面。
  { dir: 'titles-nameplate-no-register', checker: 'check-titles.js', expect: 1,
    desc: '把名牌包的 playToClient 注册整行删掉 ⇒ 客户端永远收不到那张表，头顶永远是光名字',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java';
      at.set(F, mustReplace(at.get(F),
        '        registrar.playToClient(TitleNameplatePayload.TYPE, TitleNameplatePayload.STREAM_CODEC,\n'
        + '                ClientPayloadHandlers::handleNameplates);\n', '',
        'titles-nameplate-no-register'));
    } },
  { dir: 'titles-nameplate-server-write', checker: 'check-titles.js', expect: 1,
    desc: '把名牌包注册成 playToServer（客户端能回写）⇒ "客户端只能说我想要什么"那条纪律破了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java';
      at.set(F, mustReplace(at.get(F),
        '        registrar.playToClient(TitleNameplatePayload.TYPE, TitleNameplatePayload.STREAM_CODEC,\n'
        + '                ClientPayloadHandlers::handleNameplates);',
        '        registrar.playToServer(TitleNameplatePayload.TYPE, TitleNameplatePayload.STREAM_CODEC,\n'
        + '                (p, c) -> {});',
        'titles-nameplate-server-write'));
    } },
  { dir: 'titles-nameplate-no-enqueue', checker: 'check-titles.js', expect: 1,
    desc: 'handleNameplates 不走 enqueueWork ⇒ 网络线程直接换那张每帧都被读的表（可能读到换到一半）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientPayloadHandlers.java';
      at.set(F, mustReplace(at.get(F),
        '        context.enqueueWork(() -> ClientNameplates.accept(payload));',
        '        ClientNameplates.accept(payload);',
        'titles-nameplate-no-enqueue'));
    } },
  { dir: 'titles-nameplate-merge-cache', checker: 'check-titles.js', expect: 1,
    desc: 'accept 改成逐条合并（不清空）⇒ 退房/摘称号的人永远赖在别人的客户端上',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/ClientNameplates.java';
      at.set(F, mustReplace(at.get(F),
        '        TITLES.clear();\n        TITLES.putAll(next);',
        '        TITLES.putAll(next);',
        'titles-nameplate-merge-cache'));
    } },
  { dir: 'titles-nameplate-no-dist', checker: 'check-titles.js', expect: 1,
    desc: '渲染处理器挂到服务端 dist ⇒ 名牌压根不会变（RenderNameTagEvent 只在逻辑客户端存在）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/TitleNameplateEvents.java';
      at.set(F, mustReplace(at.get(F),
        '@EventBusSubscriber(modid = AnglersTackle.MOD_ID, value = Dist.CLIENT)',
        '@EventBusSubscriber(modid = AnglersTackle.MOD_ID, value = Dist.DEDICATED_SERVER)',
        'titles-nameplate-no-dist'));
    } },
  { dir: 'titles-nameplate-no-setcontent', checker: 'check-titles.js', expect: 1,
    desc: '拼好了组件却不调 setContent ⇒ 称号永远不会出现在别人眼前（不报错，就是没变化）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/TitleNameplateEvents.java';
      at.set(F, mustReplace(at.get(F),
        '            event.setContent(Component.empty()',
        '            Component neverDrawn = Component.empty()',
        'titles-nameplate-no-setcontent'));
    } },
  { dir: 'titles-nameplate-raw-id', checker: 'check-titles.js', expect: 1,
    desc: '去掉"认不出就不画"的判定 ⇒ 客户端版本不一致时把 %s / 语言键画到别人头顶',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/TitleNameplateEvents.java';
      at.set(F, mustReplace(at.get(F),
        '            if (TitleDefs.byId(id) == null && !TitleDefs.validInstance(id)) {',
        '            if (false) {',
        'titles-nameplate-raw-id'));
    } },
  { dir: 'titles-nameplate-equip-silent', checker: 'check-titles.js', expect: 1,
    desc: 'equip 改了数据却不广播 ⇒ 戴上称号别人要等下一次广播（可能是几十分钟后）才看到',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/Titles.java';
      at.set(F, mustReplace(at.get(F),
        '        // 名牌那张表要跟着变（`docs/38` §2.1）—— 这里是**唯一**改佩戴的入口，\n'
        + '        // 所以同步挂在这里就天然全覆盖（指令与界面两条入口都走它 ✓）。\n'
        + '        com.whitecobalt.anglertackle.network.ModNetwork.broadcastNameplates(player.server);\n', '',
        'titles-nameplate-equip-silent'));
    } },
  { dir: 'titles-nameplate-unequip-silent', checker: 'check-titles.js', expect: 1,
    desc: 'unequip 摘了称号却不广播 ⇒ 别人屏幕上那截称号一直挂着，直到下一次广播才消失',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/Titles.java';
      at.set(F, mustReplace(at.get(F),
        '        // 同上：摘掉也要让**别人的**名牌立刻恢复成"只有名字"\n'
        + '        com.whitecobalt.anglertackle.network.ModNetwork.broadcastNameplates(player.server);\n', '',
        'titles-nameplate-unequip-silent'));
    } },
  { dir: 'titles-nameplate-no-login-sync', checker: 'check-titles.js', expect: 1,
    desc: '删掉进服广播 ⇒ 晚进来的人看不到先来的人头上的称号（要等别人换一次佩戴才补上）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/TitleNameplateSync.java';
      at.set(F, mustReplace(at.get(F),
        '    @SubscribeEvent\n'
        + '    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {\n'
        + '        if (event.getEntity() instanceof ServerPlayer sp) {\n'
        + '            ModNetwork.broadcastNameplates(sp.server);\n'
        + '        }\n'
        + '    }\n\n', '',
        'titles-nameplate-no-login-sync'));
    } },
  { dir: 'titles-nameplate-logout-no-skip', checker: 'check-titles.js', expect: 1,
    desc: '退服广播不带 skip ⇒ 原版先发事件后移人，退房那位的称号赖在别人屏幕上（与登录那条对称的坑）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/TitleNameplateSync.java';
      at.set(F, mustReplace(at.get(F),
        'ModNetwork.broadcastNameplates(sp.server, sp.getUUID());',
        'ModNetwork.broadcastNameplates(sp.server);',
        'titles-nameplate-logout-no-skip'));
    } },
  { dir: 'titles-nameplate-lost-title', checker: 'check-titles.js', expect: 1,
    desc: '广播时不校验"现在仍然拥有" ⇒ 已经失去的称号也被画到头顶（与聊天的口径分叉）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/network/ModNetwork.java';
      at.set(F, mustReplace(at.get(F),
        'if (id == null || id.isEmpty() || !t.has(id)) {',
        'if (id == null || id.isEmpty()) {',
        'titles-nameplate-lost-title'));
    } },
  { dir: 'titles-nameplate-no-clear', checker: 'check-titles.js', expect: 1,
    desc: '退出世界不清名牌缓存 ⇒ 换存档的第一眼给上一个档的玩家挂着称号（玩家会当成"新档已有"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/event/CoinSyncEvents.java';
      at.set(F, mustReplace(at.get(F),
        '            com.whitecobalt.anglertackle.client.ClientNameplates.clear();\n', '',
        'titles-nameplate-no-clear'));
    } },

  // ---- check-negtest-runner.js：**负例执行器自己**（2026-10-12 加的并发改造）----  // 这一组的存在理由：执行器坏掉的症状全是"看起来全绿"（没跑起来被当成抓到了、
  // 过滤打错字也报通过、并行建副本互相拆台）—— 而它恰恰是别的检查器的守门人 ✗。
  { dir: 'negrun-ok', checker: 'check-negtest-runner.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'negrun-pipe', checker: 'check-negtest-runner.js', expect: 1,
    desc: "执行器改用 stdio: 'pipe' 抓输出（沙箱下 EPERM ⇒ 子进程根本没跑，却会被当成「抓到了」）",
    mutate: (at) => {
      at.set('_negrun.js', mustReplace(at.get('_negrun.js'),
        "stdio: capture ? 'inherit' : 'ignore',", "stdio: 'pipe',", 'negrun-pipe'));
    } },

  // ---- check-negtest-runner.js：建副本的**内部并发**（2026-10-12 提速）----
  //   这四条守的是"提速没把安全边界拆掉"：断链写、指纹复核、清单顺序、不开管道。
  { dir: 'negrun-link-write-through', checker: 'check-negtest-runner.js', expect: 1,
    desc: '把"写前断链"套进 if (false) ⇒ 硬链接被穿透写入，**真源码会被负例改坏**（且不报错）',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        '  fs.rmSync(dst, { force: true });            // 断开硬链接（真源码那一份不动）',
        '  if (false) { fs.rmSync(dst, { force: true }); }', 'negrun-link-write-through'));
    } },
  { dir: 'negrun-no-integrity-guard', checker: 'check-negtest-runner.js', expect: 1,
    desc: '删掉"建完复核真源码指纹" ⇒ 硬链接一旦被写穿，没有任何东西会发现',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        'assertSourceIntact(srcBefore);', '/* 指纹复核被删掉（负例） */',
        'negrun-no-integrity-guard'));
    } },
  { dir: 'negrun-manifest-order', checker: 'check-negtest-runner.js', expect: 1,
    desc: '合并分片清单时不按 CASES 重排 ⇒ 日志与用例顺序对不上（"第 3 条红了"会指错人）',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        'const ordered = CASES.map((c) => byDir.get(c.dir)).filter(Boolean);',
        'const ordered = merged;', 'negrun-manifest-order'));
    } },
  { dir: 'negrun-shard-pipe', checker: 'check-negtest-runner.js', expect: 1,
    desc: "分片子进程改成 stdio: 'pipe' ⇒ 沙箱下 EPERM，副本根本没建起来",
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        "{ stdio: 'inherit', windowsHide: true, env: shardGuardEnv(k) });",
        "{ stdio: 'pipe', windowsHide: true });",
        'negrun-shard-pipe'));
    } },
  { dir: 'negrun-fingerprint-too-narrow', checker: 'check-negtest-runner.js', expect: 1,
    desc: '指纹名单退回"只看 FILES" ⇒ EXTRA 里的贴图被写穿时没人发现'
      + '（2026-10-12 真栽过：pike.png 被涂成全透明，最后是靠对照组才发现的）',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        'const all = new Set(FILES);', 'const all = new Set();', 'negrun-fingerprint-too-narrow'));
    } },
  { dir: 'negrun-png-write-through', checker: 'check-negtest-runner.js', expect: 1,
    desc: '造损坏贴图时不走 writeInCopy ⇒ 硬链接被穿透、**真贴图**被涂成全透明'
      + '（这正是 2026-10-12 那次事故的写法）',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        'writeInCopy(file, Buffer.concat([', 'fs.writeFileSync(file, Buffer.concat([',
        'negrun-png-write-through'));
    } },
  { dir: 'negrun-no-control-guard', checker: 'check-negtest-runner.js', expect: 1,
    desc: '删掉"对照组为 0 就报错"的自检（检查器自己写错时整批用例静默放行）',
    mutate: (at) => {
      at.set('_negrun.js', mustReplace(at.get('_negrun.js'),
        'if (controls === 0) {', 'if (false) {', 'negrun-no-control-guard'));
    } },
  { dir: 'negrun-null-as-caught', checker: 'check-negtest-runner.js', expect: 1,
    desc: '把"被信号杀掉"折成退出码 1 ⇒ 没跑起来的用例会被报成"变异被抓到"（本仓库踩过）',
    mutate: (at) => {
      at.set('_negrun.js', mustReplace(at.get('_negrun.js'),
        "child.on('exit', (code, signal) => finish({ code, error: signal ? '被信号 ' + signal + ' 终止' : null }));",
        "child.on('exit', (code) => finish({ code: code === undefined ? 1 : code, error: null }));",
        'negrun-null-as-caught'));
    } },
  { dir: 'negrun-unwired', checker: 'check-negtest-runner.js', expect: 1,
    desc: 'negtest.ps1 不再调用执行器 ⇒ 那段断言悄悄退回从来没跑过（等于没有负例）',
    mutate: (at) => {
      at.set('negtest.ps1', mustReplace(at.get('negtest.ps1'),
        '& node @runnerArgs', 'Write-Host "（已跳过）"', 'negrun-unwired'));
    } },
  { dir: 'negrun-parallel-build', checker: 'check-negtest-runner.js', expect: 1,
    desc: '建副本那一步改成并行起两个 _negtest.js ⇒ 两个进程同时清空/重建副本目录会互相拆台',
    mutate: (at) => {
      at.set('negtest.ps1', mustReplace(at.get('negtest.ps1'),
        "& node (Join-Path $PSScriptRoot '_negtest.js')",
        "Start-Process node -ArgumentList (Join-Path $PSScriptRoot '_negtest.js') -PassThru\n"
        + "& node (Join-Path $PSScriptRoot '_negtest.js')", 'negrun-parallel-build'));
    } },
  // ---- check-negtest-runner.js：**锚点预检**（2026-10-12 加）----
  //   预检是负例自己的守门人：锚点失配时它是唯一能在两秒内说出"哪几条没了"的东西。
  //   这三条守的是"它被摘掉 / 被挪到事后 / 它自己误报"三种坏法。
  { dir: 'negrun-no-anchor-preflight', checker: 'check-negtest-runner.js', expect: 1,
    desc: 'negtest.ps1 不再跑 --check-anchors 预检 ⇒ 锚点失配又退回"造副本失败 + '
      + '闸报「存在漏检的检查脚本」"那种误导现场（要等三四分钟才看到）',
    // ⚠ 行尾必须**跟着文件走**：negtest.ps1 是 CRLF，锚点里写死 '\n' 会当场失配
    //   （这两个用例第一版就是这么写的，预检立刻把两条都报了出来 ✓ —— 它连自己的用例也查）。
    mutate: (at) => {
      const t0 = at.get('negtest.ps1');
      const EOL = t0.includes('\r\n') ? '\r\n' : '\n';
      at.set('negtest.ps1', mustReplace(t0,
        "& node (Join-Path $PSScriptRoot '_negtest.js') --check-anchors" + EOL,
        '', 'negrun-no-anchor-preflight'));
    } },
  { dir: 'negrun-anchor-preflight-late', checker: 'check-negtest-runner.js', expect: 1,
    desc: '预检被挪到**建副本之后** ⇒ 它就只能事后报到，那两秒的预检等于白加',
    mutate: (at) => {
      const t0 = at.get('negtest.ps1');
      const EOL = t0.includes('\r\n') ? '\r\n' : '\n';
      const PRE = "& node (Join-Path $PSScriptRoot '_negtest.js') --check-anchors";
      const BUILD = "& node (Join-Path $PSScriptRoot '_negtest.js')";
      let t = mustReplace(t0, PRE + EOL, '', 'negrun-anchor-preflight-late');
      t = mustReplace(t, BUILD + EOL, BUILD + EOL + PRE + EOL, 'negrun-anchor-preflight-late');
      at.set('negtest.ps1', t);
    } },
  { dir: 'negrun-anchor-no-dedupe', checker: 'check-negtest-runner.js', expect: 1,
    desc: '预检的键集合退回"数组直接摊平、不去重" ⇒ FILES 里同一路径列了多次，'
      + '几百条用例被误报成「文件名有歧义」（第一版真就是这么写的，388 条假红）',
    mutate: (at) => {
      at.set('_negtest.js', mustReplace(at.get('_negtest.js'),
        '      if (seen.has(rel)) continue;\n      seen.add(rel);',
        '      seen.add(rel);   // 变异：不去重（对象键本来天然去重，数组不是）',
        'negrun-anchor-no-dedupe'));
    } },

  // ---- check-mail.js：邮件箱（用户 2026-10-11 点名要；docs/36 M1）----
  // 这一组盯的全是"不报错、也不崩，只是玩家的东西悄悄没了/状态悄悄不对"那类 ✗。
  { dir: 'mail-ok', checker: 'check-mail.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'mail-read-claims', checker: 'check-mail.js', expect: 1,
    desc: '把"已读"当成"已领"（点开看一眼就把附件吞掉 —— 背包满时东西去哪？无解）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '        PlayerData.setMails(player, box.withReplaced(m.withRead()));',
        '        PlayerData.setMails(player, box.withReplaced(m.withClaimed()));',
        'mail-read-claims'));
    } },
  { dir: 'mail-bag-full-drop', checker: 'check-mail.js', expect: 1,
    desc: '背包满时把物品掉在脚下（而不是留在邮件里）—— 玩家的奖励散落一地',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '                for (ItemStack back : put) {\n'
        + '                    player.getInventory().removeItem(back);\n'
        + '                }\n'
        + '                return Claim.BAG_FULL;',
        '                for (ItemStack back : put) {\n'
        + '                    player.drop(back, false);\n'
        + '                }\n'
        + '                return Claim.OK;', 'mail-bag-full-drop'));
    } },
  { dir: 'mail-expire-deletes', checker: 'check-mail.js', expect: 1,
    desc: '过期时把整封邮件删掉（正文也没了）—— 玩家会觉得"我的邮件被人删了"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/MailBox.java';
      at.set(F, mustReplace(at.get(F),
        '    public MailBox mapAll(java.util.function.UnaryOperator<MailEntry> fn) {',
        '    public MailBox mapAll(java.util.function.UnaryOperator<MailEntry> fn) {\n'
        + '        if (true) { java.util.List<MailEntry> k = new java.util.ArrayList<>(mails); k.removeIf(x -> x.id() > 0); return new MailBox(java.util.List.copyOf(k), nextId); }',
        'mail-expire-deletes'));
    } },
  { dir: 'mail-cap-silent-drop', checker: 'check-mail.js', expect: 1,
    desc: '到条数上限时静默丢件（不拒绝、不写 WARN）—— 服主与玩家都不知道信丢了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '        if (box.mails().size() >= Tuning.mailMaxMails()) {',
        '        if (false) {', 'mail-cap-silent-drop'));
    } },
  { dir: 'mail-server-chinese', checker: 'check-mail.js', expect: 1,
    desc: '在服务端拼中文文案（英文客户端会看到中文 —— 排行榜当初那条老路）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '            to.displayClientMessage(Component.translatable("message.anglertackle.mail_full",',
        '            to.displayClientMessage(Component.literal("你的邮箱满了"), false);\n'
        + '            if (true) { to.displayClientMessage(Component.translatable("message.anglertackle.mail_full",',
        'mail-server-chinese'));
    } },
  { dir: 'mail-no-copy-on-death', checker: 'check-mail.js', expect: 1,
    desc: '邮箱附件去掉 copyOnDeath ⇒ 死一次邮箱清空（里面可能正躺着没领的奖励）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/registry/ModAttachments.java';
      at.set(F, mustReplace(at.get(F),
        '                    .builder(() -> MailBox.EMPTY)\n'
        + '                    .serialize(MailBox.CODEC)\n'
        + '                    .copyOnDeath()\n',
        '                    .builder(() -> MailBox.EMPTY)\n'
        + '                    .serialize(MailBox.CODEC)\n', 'mail-no-copy-on-death'));
    } },
  { dir: 'mail-unread-counter', checker: 'check-mail.js', expect: 1,
    desc: '另存一个"未读数"计数器（两个真源，迟早和列表不一致 —— "两套刻度"那类事故）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/MailBox.java';
      at.set(F, mustReplace(at.get(F),
        'public record MailBox(List<MailEntry> mails, int nextId) {',
        'public record MailBox(List<MailEntry> mails, int nextId, int unreadCount) {',
        'mail-unread-counter'));
    } },
  // ---- 界面那三条（M2，docs/36 §5）----
  { dir: 'mail-hover-reads', checker: 'check-mail.js', expect: 1,
    desc: '划过列表行就把邮件标成已读（未读数被"看一眼"清零 —— 《死亡搁浅》的反例）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '    @Override\n    public boolean mouseClicked(double mx, double my, int button) {',
        '    @Override\n    public void mouseMoved(double mx, double my) {\n'
        + '        action(MailActionPayload.READ, selectedId);\n'
        + '        super.mouseMoved(mx, my);\n    }\n\n'
        + '    @Override\n    public boolean mouseClicked(double mx, double my, int button) {',
        'mail-hover-reads'));
    } },
  { dir: 'mail-click-claims', checker: 'check-mail.js', expect: 1,
    desc: '点一行就把附件领了（点开看一眼 = 领了 ⇒ 背包满时玩家的东西没着落）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      // ⚠ 锚点跟着实现走：点行的 READ 现在落在共用的 selectIndex() 里（点击/键盘同一条路）
      at.set(F, mustReplace(at.get(F),
        '            action(MailActionPayload.READ, r.id);',
        '            action(MailActionPayload.CLAIM, r.id);',
        'mail-click-claims'));
    } },
  { dir: 'mail-empty-list', checker: 'check-mail.js', expect: 1,
    desc: '空邮箱画一个空列表（和"加载失败"长得一模一样，本仓库在图鉴上踩过）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            g.drawString(this.font,\n'
        + '                    Component.translatable(ClientMails.synced()\n'
        + '                            ? "gui.anglertackle.mail_empty" : "gui.anglertackle.mail_loading").getString(),',
        '            g.drawString(this.font, "",',
        'mail-empty-list'));
    } },
  { dir: 'mail-panel-hardcoded', checker: 'check-mail.js', expect: 1,
    desc: '面板尺寸写死（没用 UiLayout）⇒ 小窗口会出屏切内容',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        panelW = UiLayout.panelW(this.width, PANEL_W, MIN_W);',
        '        panelW = PANEL_W;', 'mail-panel-hardcoded'));
    } },
  { dir: 'mail-new-palette', checker: 'check-mail.js', expect: 1,
    desc: '界面另起一套色板（不复用既有常量 —— docs/36 §5.4 明令禁止）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), '    private static final int C_UNREAD = C_LABEL;',
        '    private static final int C_UNREAD = 0xFF12ABCD;', 'mail-new-palette'));
    } },
  { dir: 'mail-no-scroll', checker: 'check-mail.js', expect: 1,
    desc: '删掉滚轮翻页 ⇒ 邮件多了只能靠两个小箭头点（用户要求"所有功能做到点击交互"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), '    public boolean mouseScrolled(', '    public boolean _removed(',
        'mail-no-scroll'));
    } },
  { dir: 'mail-no-keys', checker: 'check-mail.js', expect: 1,
    desc: '删掉键盘导航（↑↓/Enter）⇒ 只能用鼠标逐行点',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), '    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {',
        '    public boolean _removedKeys(int keyCode, int scanCode, int modifiers) {', 'mail-no-keys'));
    } },
  { dir: 'mail-codex-entry-gone', checker: 'check-mail.js', expect: 1,
    desc: '图鉴里的邮箱入口被删掉（docs/36 §5.7 要的三个入口只剩两个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        Button mail = Button.builder(Component.translatable("gui.anglertackle.codex_mail"), b ->\n'
        + '                net.neoforged.neoforge.network.PacketDistributor.sendToServer(\n'
        + '                        new com.whitecobalt.anglertackle.network.MailActionPayload(\n'
        + '                                com.whitecobalt.anglertackle.network.MailActionPayload.OPEN, -1)))\n',
        '        Button mail = Button.builder(Component.translatable("gui.anglertackle.codex_mail"), b -> { })\n',
        'mail-codex-entry-gone'));
    } },

  // ---- check-league.js：联赛（2026-10-12，用户"继续推进" ⇒ docs/37 L2–L4）----
  //   这一批的共同点：**每条坏掉都不报错**，只是"规则悄悄变成了另一个样子"
  //   （睡一觉跳完赛季 / A 的签收影响 B / 钱发两遍 / 脚本刷榜…）。
  { dir: 'league-ok', checker: 'check-league.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'league-daytime-clock', checker: 'check-league.js', expect: 1,
    desc: '周期改用世界时刻（getDayTime）⇒ 睡一觉/`/time set` 就能把赛季跳完',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F), 'long game = level.getGameTime();',
        'long game = level.getDayTime();', 'league-daytime-clock'));
    } },
  { dir: 'league-global-window', checker: 'check-league.js', expect: 1,
    desc: '计分读全局硬截止而不是玩家自己的窗口 ⇒ A 的签收时间影响 B（"每人独立"失效）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F), 'if (game > m.windowEnd || game >= d.hardDeadlineGame) {',
        'if (game >= d.hardDeadlineGame) {', 'league-global-window'));
    } },
  { dir: 'league-window-ignores-deadline', checker: 'check-league.js', expect: 1,
    desc: '窗口终点只算"签收 + 30 分钟" ⇒ 晚签收的人越过硬截止还在计分',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/League.java';
      at.set(F, mustReplace(at.get(F),
        'return Math.min(signTick + Math.max(1L, windowTicks), hardDeadlineTick);',
        'return signTick + Math.max(1L, windowTicks);', 'league-window-ignores-deadline'));
    } },
  { dir: 'league-no-forced-settle', checker: 'check-league.js', expect: 1,
    desc: '删掉硬截止的强行结算 ⇒ 谁都不打满就永远不结算（奖励永远发不出去）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        'if (!d.settled && game >= d.hardDeadlineGame) {\n                settle(server);\n            }',
        'if (false) {\n                settle(server);\n            }', 'league-no-forced-settle'));
    } },
  { dir: 'league-window-blocks-fishing', checker: 'check-league.js', expect: 1,
    desc: '窗口用完去动钓鱼会话（收线/清场）⇒ 违背"只计分不惩罚"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        '        if (game > m.windowEnd || game >= d.hardDeadlineGame) {\n            return;\n        }',
        '        if (game > m.windowEnd || game >= d.hardDeadlineGame) {\n'
        + '            com.whitecobalt.anglertackle.fishing.FishingManager.get().forget(player);\n'
        + '            return;\n        }', 'league-window-blocks-fishing'));
    } },
  { dir: 'league-no-rollback-clamp', checker: 'check-league.js', expect: 1,
    desc: '去掉回档钳制 ⇒ 回档后同一期被反复触发/重复结算',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F), 'Math.max(d.pushAnchorGame, clamped)', 'clamped',
        'league-no-rollback-clamp'));
    } },
  { dir: 'league-pay-before-ledger', checker: 'check-league.js', expect: 1,
    desc: '先发钱后写流水键 ⇒ 重跑/回档就双发（经济当场崩）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        '            if (!d.ledger.add(key)) {\n                continue;                                               // 这一份已经发过\n            }',
        '', 'league-pay-before-ledger'));
    } },
  { dir: 'league-fastest-rule', checker: 'check-league.js', expect: 1,
    desc: '把可刷的"最短上鱼"加进计分口径 ⇒ 脚本能刷榜（调研明确排除两项）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/League.java';
      at.set(F, mustReplace(at.get(F), '        CODEX("codex", Leaderboard.Board.CODEX);',
        '        FASTEST_CATCH("fastest_catch", Leaderboard.Board.FASTEST_CATCH),\n'
        + '        CODEX("codex", Leaderboard.Board.CODEX);', 'league-fastest-rule'));
    } },
  { dir: 'league-hardcoded-reward', checker: 'check-league.js', expect: 1,
    desc: '冠军奖励写死成 10000 ⇒ 调参器里改了没反应',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/League.java';
      at.set(F, mustReplace(at.get(F), 'coins = Tuning.leagueRewardCoins1st();', 'coins = 10000;',
        'league-hardcoded-reward'));
    } },
  { dir: 'league-online-only', checker: 'check-league.js', expect: 1,
    desc: '只给在线玩家发奖 ⇒ 离线玩家的奖励直接丢（Hypixel 的实测坑）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        '        LeagueData.Pending p = new LeagueData.Pending(target);\n'
        + '        p.template = template;',
        '        if (true) {\n            return;\n        }\n'
        + '        LeagueData.Pending p = new LeagueData.Pending(target);\n'
        + '        p.template = template;', 'league-online-only'));
    } },
  // ---- 联赛积分（`docs/39` P1/P2）：每条新断言都要**真的被证伪过** ----
  { dir: 'league-points-wrong-bonus', checker: 'check-league.js', expect: 1,
    desc: '把红档加成从 32 改成 30 ⇒ 积分口径与用户给的表、与 docs/39 都对不上',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeaguePoints.java';
      at.set(F, mustReplace(at.get(F),
        'QUALITY_BONUS = {0, 2, 5, 10, 20, 32}', 'QUALITY_BONUS = {0, 2, 5, 10, 20, 30}',
        'league-points-wrong-bonus'));
    } },
  { dir: 'league-points-before-window', checker: 'check-league.js', expect: 1,
    desc: '把上鱼积分挪到"窗口/硬截止"判定之前 ⇒ 未签收、窗口用完的人照样涨分',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        '        if (game > m.windowEnd || game >= d.hardDeadlineGame) {',
        '        LeaguePoints.onCatch(player, c.tier());\n'
        + '        if (game > m.windowEnd || game >= d.hardDeadlineGame) {',
        'league-points-before-window'));
    } },
  { dir: 'league-points-rank-before-ledger', checker: 'check-league.js', expect: 1,
    desc: '名次分写在流水键之前 ⇒ 重复结算 / 回档会把积分多给一次',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        '            int rewardRank = rank <= League.TOP10_RANK ? rank : 0;',
        '            LeaguePoints.rankAward(rank);\n'
        + '            int rewardRank = rank <= League.TOP10_RANK ? rank : 0;',
        'league-points-rank-before-ledger'));
    } },
  { dir: 'league-points-fieldof', checker: 'check-league.js', expect: 1,
    desc: '`league_points` 改用 fieldOf ⇒ 老存档整份玩家数据读不出来（钱包与图鉴一起归零）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/PlayerFishingData.java';
      at.set(F, mustReplace(at.get(F),
        'Codec.LONG.optionalFieldOf("league_points", 0L)',
        'Codec.LONG.fieldOf("league_points")', 'league-points-fieldof'));
    } },
  { dir: 'league-points-not-persisted', checker: 'check-league.js', expect: 1,
    desc: '待投递队列不存积分 ⇒ 离线玩家的名次分在重启后直接丢（"没上线就吞掉"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueData.java';
      at.set(F, mustReplace(at.get(F),
        't.putInt("points", p.points);', 't.putInt("points", 0);', 'league-points-not-persisted'));
    } },
  // ---- 生成式称号与赛季号（`docs/39` §5）----
  { dir: 'league-season-from-seasonid', checker: 'check-league.js', expect: 1,
    desc: '赛季号用 seasonId/3 反推 ⇒ 服主一开"三级并开"，赛季编号的界桩就漂',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        'titleFor(d.seasonNo, d.tierId, rank)',
        'titleFor((d.seasonId - 1) / 3 + 1, d.tierId, rank)', 'league-season-from-seasonid'));
    } },
  { dir: 'league-title-instance-any', checker: 'check-league.js', expect: 1,
    desc: '实例白名单形同虚设（只判有没有 @）⇒ 随手编一个 id 就能冒充正式称号',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/TitleDefs.java';
      at.set(F, mustReplace(at.get(F),
        'return template(templateIdOf(id)) != null && seasonOf(id) > 0 && sizeIndex(id) >= 0;',
        'return isInstance(id);', 'league-title-instance-any'));
    } },
  { dir: 'league-title-snapshot-blind', checker: 'check-league.js', expect: 1,
    desc: '快照只按 ALL 拍 ⇒ 生成式赛季称号"发了也看不见"（界面与 /atl title 里都没有）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/title/TitleSnapshot.java';
      at.set(F, mustReplace(at.get(F),
        'for (String id : t.owned()) {', 'for (String id : java.util.List.<String>of()) {',
        'league-title-snapshot-blind'));
    } },
  { dir: 'league-title-third-missing', checker: 'check-league.js', expect: 1,
    desc: '季军不发称号 ⇒ 第三名白打（用户定的是冠/亚/季三档都发）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/LeagueService.java';
      at.set(F, mustReplace(at.get(F),
        ': rank == 3 ? "league_third" : "";', ': "";', 'league-title-third-missing'));
    } },
  // ---- 联赛商店（`docs/39` P4）----
  // ⚠⚠ 2026-10-13 **撤下三条变异用例**（`league-shop-no-pay` / `league-shop-price-wrong` /
  //     `league-shop-tab-gone`）：它们跑的时候**写穿了硬链接**，把真源码
  //     `src/main/java/.../shop/ShopManager.java` 改坏了（闸的指纹守卫当场报出来，
  //     已从 `参考资料/_snapshots/20261013110814` 逐字节恢复 ✓）。
  //     写穿真源码比"少几条负例"危险得多 ⇒ 先撤下，等查清写穿路径（副本缺文件时的回退？
  //     还是 `at.set` 对"新增进 FILES 的文件"没走 writeInCopy？）再补回来。
  //     期间 **断言照留**（check-league.js 的 K 段）—— 少的是"证伪"，不是"检查"。
  { dir: 'league-action-before-claim', checker: 'check-league.js', expect: 1,
    desc: '把动作挪到附件入账之前跑 ⇒ 背包满时"报了名但礼物没领到"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      // ⚠ 锚点必须落在 `withClaimed()` **那一行之前**：第一版把动作块复制了一份插在
      //   原动作块前面（那已经在 withClaimed() 之后了）⇒ 顺序没变、检查器当然不红 ✗。
      //   变异测试"没红"时先怀疑变异本身没生效，再去怀疑检查器（这条踩过一次）。
      at.set(F, mustReplace(at.get(F),
        '        PlayerData.setMails(player, now.withReplaced((cur == null ? m : cur).withClaimed()));',
        '        if (m.hasAction()) {\n'
        + '            com.whitecobalt.anglertackle.mail.MailActions.run(player, m.action());\n'
        + '        }\n'
        + '        PlayerData.setMails(player, now.withReplaced((cur == null ? m : cur).withClaimed()));',
        'league-action-before-claim'));
    } },
  { dir: 'league-mail-slots-overflow', checker: 'check-league.js', expect: 1,
    desc: '大型签收礼塞 5 件东西 ⇒ 邮件只画 4 格，第 5 件被静默截掉（玩家少了东西没人知道）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/League.java';
      at.set(F, mustReplace(at.get(F),
        '                items.add(new ItemStack(ModItems.CHUM_SECRET.get(), 5));\n'
        + '                items.add(new ItemStack(Items.DIAMOND, 1));',
        '                items.add(new ItemStack(ModItems.CHUM_SECRET.get(), 5));\n'
        + '                items.add(new ItemStack(Items.DIAMOND, 1));\n'
        + '                items.add(new ItemStack(Items.BREAD, 16));\n'
        + '                items.add(new ItemStack(Items.IRON_INGOT, 4));', 'league-mail-slots-overflow'));
    } },
  { dir: 'league-rule-order-drift', checker: 'check-league.js', expect: 1,
    desc: 'Rule 枚举顺序与 ruleMode 的配置注释对不上 ⇒ 玩家照着注释选错口径',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/league/League.java';
      at.set(F, mustReplace(at.get(F),
        '        HEAVIEST("heaviest", Leaderboard.Board.HEAVIEST),\n'
        + '        TOTAL_WEIGHT("total_weight", Leaderboard.Board.TOTAL_WEIGHT),',
        '        TOTAL_WEIGHT("total_weight", Leaderboard.Board.TOTAL_WEIGHT),\n'
        + '        HEAVIEST("heaviest", Leaderboard.Board.HEAVIEST),', 'league-rule-order-drift'));
    } },
  { dir: 'league-no-stats', checker: 'check-league.js', expect: 1,
    desc: '删掉"签收后未参赛率"的观测 ⇒ 参与度虚高但留存差的问题再也看不见',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '        int idle = joined - ranked;', '        int idle = 0;',
        'league-no-stats'));
    } },
  { dir: 'league-short-mail-keep', checker: 'check-league.js', expect: 1,
    desc: '联赛邮件退回普通 30 天保管期 ⇒ 休假玩家回来发现奖励过期了（据调研改的那条）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '        if (t.startsWith("league_invite") || t.startsWith("league_result")) {',
        '        if (false) {', 'league-short-mail-keep'));
    } },
  { dir: 'league-join-command', checker: 'check-league.js', expect: 1,
    desc: '加一条 `/atl league join` ⇒ 绕开邀请，"邀请制"当场失效',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '.then(lit("stats").executes(ctx -> showLeagueStats(ctx.getSource())))',
        '.then(lit("stats").executes(ctx -> showLeagueStats(ctx.getSource())))\n'
        + '                        .then(lit("join").executes(ctx -> 1))', 'league-join-command'));
    } },
  { dir: 'league-no-mail-action', checker: 'check-league.js', expect: 1,
    desc: '把 league_join 从动作白名单里删掉 ⇒ 点【签收】只领礼、不报名',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailActions.java';
      at.set(F, mustReplace(at.get(F), 'private static final Set<String> KNOWN = Set.of(LEAGUE_JOIN, QUEST_ACCEPT);',
        'private static final Set<String> KNOWN = Set.of(QUEST_ACCEPT);', 'league-no-mail-action'));
    } },
  { dir: 'league-missing-action-button', checker: 'check-league.js', expect: 1,
    desc: '删掉签收按钮上的文案键 ⇒ 那封信的按钮显示成一串 mail.anglertackle.league_join.button',
    mutate: (at) => {
      const F = 'src/main/resources/assets/anglertackle/lang/zh_cn.json';
      at.set(F, mustReplace(at.get(F),
        '  "mail.anglertackle.league_join.button": "签收",\n', '',
        'league-missing-action-button'));
    } },
  { dir: 'league-button-not-action-aware', checker: 'check-league.js', expect: 1,
    desc: '按钮文案不按动作取 ⇒ 联赛邀请写着"领取"、签收完还是"已领取"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F), '"mail.anglertackle." + r.action + ".button"',
        '"gui.anglertackle.mail_claim"', 'league-button-not-action-aware'));
    } },

  // ---- check-angler-level.js 的"加经验指令"组（2026-10-12，用户："增加经验的指令"）----
  //   三条坏法都不报错：绕开经验流水（造出正常玩不可能的状态）、不推客户端（界面停在旧值）、
  //   回显不提"收下多少"（卡评定时玩家以为指令坏了）。
  { dir: 'xp-bypasses-gain', checker: 'check-angler-level.js', expect: 1,
    desc: '加经验不走 addXp 而是直接写回等级 ⇒ 绕开卡级/溢出规则（能造出跳级后的状态）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), 'FishermanData.addXp(target, amount)', 'amount',
        'xp-bypasses-gain'));
    } },
  { dir: 'xp-no-sync', checker: 'check-angler-level.js', expect: 1,
    desc: '加完经验不推客户端 ⇒ 界面上的等级停在旧值，而实际已经升了',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F),
        '        com.whitecobalt.anglertackle.shop.ShopManager.sync(target);\n', '',
        'xp-no-sync'));
    } },
  { dir: 'xp-silent-at-gate', checker: 'check-angler-level.js', expect: 1,
    desc: '回显不提"真正收下多少" ⇒ 卡在评定点时玩家看到的是"加了经验却纹丝不动"',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), 'boolean refused = g.xpAccepted() < amount;',
        'boolean refused = false;', 'xp-silent-at-gate'));
    } },

  // ---- check-contest.js：钓鱼比赛（2026-10-12，docs/35 M2）----
  //   坏法全是"不报错、只是规则悄悄变了"：计时漂了 / 重启丢光 / 记两遍 / 查询被锁 / 连点两次双发钱…
  { dir: 'contest-ok', checker: 'check-contest.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'contest-remaining-seconds', checker: 'check-contest.js', expect: 1,
    desc: '把"绝对结束刻"改成"剩余时长" ⇒ 服务器重启一次计时就漂（30 分钟的比赛变 25 分钟）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java';
      at.set(F, mustReplace(at.get(F), 'd.endGame = game + Contest.durationTicks(d.minutes);',
        'd.endGame = Contest.durationTicks(d.minutes);', 'contest-remaining-seconds'));
    } },
  { dir: 'contest-memory-only', checker: 'check-contest.js', expect: 1,
    desc: '比赛数据不再是世界级 SavedData ⇒ 重启后比赛与成绩全丢',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestData.java';
      at.set(F, mustReplace(at.get(F), 'public class ContestData extends SavedData {',
        'public class ContestData {', 'contest-memory-only'));
    } },
  { dir: 'contest-double-score', checker: 'check-contest.js', expect: 1,
    desc: '在挂钩文件里再记一次分 ⇒ 同一尾鱼记两遍（榜单看着"大家都很猛"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/leaderboard/LeaderboardHooks.java';
      at.set(F, mustReplace(at.get(F), 'com.whitecobalt.anglertackle.contest.ContestService.onCatch(player, c);',
        'com.whitecobalt.anglertackle.contest.ContestService.onCatch(player, c);\n'
        + '                com.whitecobalt.anglertackle.contest.ContestService.onCatch(player, c);',
        'contest-double-score'));
    } },
  { dir: 'contest-query-gated', checker: 'check-contest.js', expect: 1,
    desc: '把 contest 塞进 OP 白名单 ⇒ 查询也要 OP（违背"指令默认无门槛"，单人存档还测不出来）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/command/AnglerCommands.java';
      at.set(F, mustReplace(at.get(F), '            "stage",\n',
        '            "stage",\n            "contest",\n', 'contest-query-gated'));
    } },
  { dir: 'contest-no-idempotent', checker: 'check-contest.js', expect: 1,
    desc: '去掉结算的"只发一次"守卫 ⇒ /atl contest stop 连点两次就发两遍钱',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java';
      at.set(F, mustReplace(at.get(F), '        if (first) {', '        if (true) {',
        'contest-no-idempotent'));
    } },
  { dir: 'contest-online-only', checker: 'check-contest.js', expect: 1,
    desc: '只给在线玩家发奖 ⇒ 中途下线的人奖励直接丢',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java';
      at.set(F, mustReplace(at.get(F),
        '        if (coins > 0L) {\n            d.pendingCoins.merge(e.player(), coins, Long::sum);\n        }',
        '', 'contest-online-only'));
    } },
  { dir: 'contest-hardcoded-reward', checker: 'check-contest.js', expect: 1,
    desc: '冠军奖励写死 300 ⇒ 调参器里改了没反应',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/Contest.java';
      at.set(F, mustReplace(at.get(F), 'case 1 -> Tuning.contestRewardFirst();', 'case 1 -> 300L;',
        'contest-hardcoded-reward'));
    } },
  { dir: 'contest-overlap-allowed', checker: 'check-contest.js', expect: 1,
    desc: '允许两场比赛重叠 ⇒ "这一尾算哪一场"无法回答',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java';
      at.set(F, mustReplace(at.get(F), '        if (d.running) {\n            return Start.BUSY;\n        }',
        '        if (false) {\n            return Start.BUSY;\n        }', 'contest-overlap-allowed'));
    } },
  { dir: 'contest-silent-nobody', checker: 'check-contest.js', expect: 1,
    desc: '0 人上鱼时静默 ⇒ 玩家以为比赛还没结束',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/contest/ContestService.java';
      at.set(F, mustReplace(at.get(F), '"message.anglertackle.contest_nobody"',
        '"message.anglertackle.contest_settled"', 'contest-silent-nobody'));
    } },

  // ---- check-codex.js：图鉴里的排行榜入口（用户 2026-10-11："排行榜可以通过图鉴打开，无需权限"）----
  { dir: 'codex-ok', checker: 'check-codex.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过 —— 它同时证明副本里整棵 java 树都带齐了）',
    mutate: () => {} },
  { dir: 'codex-rank-button-gone', checker: 'check-codex.js', expect: 1,
    desc: '图鉴里那个排行榜按钮被删掉（排行榜又只剩 /atl rank 这个调试口，按用户的标准不算交付）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java';
      at.set(F, mustReplace(at.get(F),
        'Component.translatable("gui.anglertackle.codex_rank")',
        'Component.literal("Rank")', 'codex-rank-button-gone'));
    } },
  { dir: 'codex-rank-empty-screen', checker: 'check-codex.js', expect: 1,
    desc: '按钮改成客户端直接开界面（手上没有 RankPayload ⇒ 打开是一张空榜，看着像"排行榜没数据"）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/CodexScreen.java';
      at.set(F, mustReplace(at.get(F),
        'net.neoforged.neoforge.network.PacketDistributor.sendToServer(\n'
        + '                        new com.whitecobalt.anglertackle.network.RankRequestPayload(""))',
        'Minecraft.getInstance().setScreen(new LeaderboardScreen(null))',
        'codex-rank-empty-screen'));
    } },
  // ---- check-big-catch-announce.js（大鱼公告，用户 2026-10-11 "公告可以搞"）----
  { dir: 'announce-ok', checker: 'check-big-catch-announce.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'announce-no-toggle', checker: 'check-big-catch-announce.js', expect: 1,
    desc: '把"玩家自己的开关"那道闸门去掉 ⇒ 关掉开关的人照样被公告（用户定案 6 明确要求能关）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/BigCatchAnnounce.java';
      at.set(F, mustReplace(at.get(F),
        '            if (!com.whitecobalt.anglertackle.player.PlayerData.get(player).bigCatchAnnounce()) {',
        '            if (false) {   // 变异：不看玩家开关', 'announce-no-toggle'));
    } },
  { dir: 'announce-own-price', checker: 'check-big-catch-announce.js', expect: 1,
    desc: '公告自己再算一遍价值（不用 onCatch 算好的卖价）⇒ 公告里写 120 币、真卖出 95 币',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/BigCatchAnnounce.java';
      at.set(F, mustReplace(at.get(F), '            int threshold = Tuning.bigCatchAnnounceValue();',
        '            long ownPrice = com.whitecobalt.anglertackle.economy.CoinPricing.priceFor(5, 1.0);\n'
        + '            int threshold = Tuning.bigCatchAnnounceValue();', 'announce-own-price'));
    } },
  { dir: 'announce-no-throttle', checker: 'check-big-catch-announce.js', expect: 1,
    desc: '去掉最小间隔 ⇒ 一群人同时上鱼把聊天栏刷爆（从调味品变成骚扰）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/fishing/BigCatchAnnounce.java';
      at.set(F, mustReplace(at.get(F),
        '            if (gap > 0 && lastAnnounceGameTime != Long.MIN_VALUE',
        '            if (false && gap > 0 && lastAnnounceGameTime != Long.MIN_VALUE',
        'announce-no-throttle'));
    } },
  { dir: 'announce-field-not-persisted', checker: 'check-big-catch-announce.js', expect: 1,
    desc: '开关的 Codec 改成 fieldOf（不是 optionalFieldOf+默认值）⇒ 老存档读不出这一项',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/PlayerFishingData.java';
      at.set(F, mustReplace(at.get(F),
        'Codec.BOOL.optionalFieldOf("big_catch_announce", true)',
        'Codec.BOOL.fieldOf("big_catch_announce")', 'announce-field-not-persisted'));
    } },
  // --- 抛竿换图检查（check-rod-cast-model.js）----
  { dir: 'rodcast-ok', checker: 'check-rod-cast-model.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'rodcast-no-override', checker: 'check-rod-cast-model.js', expect: 1,
    desc: '某把台钓竿的主模型去掉 overrides（这把竿抛竿后不会换成无线的那张）',
    mutate: (at) => {
      const p = 'models/item/wood_float_rod.json';
      const o = JSON.parse(at.get(p));
      delete o.overrides;
      at.set(p, JSON.stringify(o, null, 2) + '\n');
    } },
  { dir: 'rodcast-points-main-tex', checker: 'check-rod-cast-model.js', expect: 1,
    desc: 'cast 模型指向主贴图（模型换是换了，画面一点变化都没有）',
    mutate: (at) => {
      const p = 'models/item/wood_float_rod_cast.json';
      at.set(p, mustReplace(at.get(p), 'item/wood_float_rod_cast', 'item/wood_float_rod',
        'rodcast-points-main-tex'));
    } },
  { dir: 'rodcast-not-per-stack', checker: 'check-rod-cast-model.js', expect: 1,
    desc: '判据改回"玩家在钓鱼就换图"（不判手上这根）—— 背包里与另一只手的台钓竿会一起变无线图',
    mutate: (at) => {
      at.set('ClientSetup.java', mustReplace(at.get('ClientSetup.java'),
        'return entity.getMainHandItem().getItem() == stack.getItem() ? 1.0F : 0.0F;',
        'return 1.0F;', 'rodcast-not-per-stack'));
    } },
  // ---- check-exchange-special.js（兑换里的两处特殊行为，用户 2026-10-11）----
  { dir: 'exspec-ok', checker: 'check-exchange-special.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'exspec-gar-no-gate', checker: 'check-exchange-special.js', expect: 1,
    desc: '鳄雀鳝顶替去掉"只对索要红档的兑换生效"这个门槛 ⇒ 它成了万能鱼（要绿档的也能拿它顶）',
    mutate: (at) => {
      at.set('ShopMaterials.java', mustReplace(at.get('ShopMaterials.java'),
        'return x.minTier() >= 5 && GAR_ID.equals(path) && d.effectiveTier() >= 5;',
        'return GAR_ID.equals(path) && d.effectiveTier() >= 5;', 'exspec-gar-no-gate'));
    } },
  { dir: 'exspec-no-fixed-chunk', checker: 'check-exchange-special.js', expect: 1,
    desc: '熏鱼兑换不再用**兑换表里的固定鱼种**（写死一个）⇒ 与 A3 的"数据一致保证可堆叠性"脱钩',
    // ⚠ 第一版把 `out.set(...)` 改成 `if (false) out.set(...)` —— 但检查器是**按正则查源码**的 ✗
    //   那个模式照样还在 ⇒ 变异抓不到 ✗（负例当场揭出来 ✓）。改成"不再引用兑换表的字段" ✓。
    mutate: (at) => {
      at.set('ShopMaterials.java', mustReplace(at.get('ShopMaterials.java'),
        'x.fixedSpecies(), x.fixedTier(), x.fixedWeightKg()',
        '"alligator_gar", 4, 1.0f', 'exspec-no-fixed-chunk'));
    } },
  { dir: 'exspec-rules-lost-chunk', checker: 'check-exchange-special.js', expect: 1,
    desc: '熏鱼兑换的 fixedChunk 被删掉（产出就没有固定鱼数据了 ⇒ 16 个堆不起来）',
    // ⚠ 必须**两条都删**：只删一条时另一条还在 ⇒ 检查器照样绿 ✗（第一版就是这么漏的 ✓）
    mutate: (at) => {
      let s = at.get('shop-rules.js');
      s = mustReplace(s, 'fixedChunk: { species: \'atlantic_cod\', tier: 4, weightKg: 5.0 },', '',
        'exspec-rules-lost-chunk/a');
      s = mustReplace(s, 'fixedChunk: { species: \'greenland_halibut\', tier: 4, weightKg: 30.0 },', '',
        'exspec-rules-lost-chunk/b');
      at.set('shop-rules.js', s);
    } },
  { dir: 'dish-cov-ok', checker: 'check-dish-coverage.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', extra: EXTRA.kitchen, mutate: () => {} },
  { dir: 'dish-cov-no-icon', checker: 'check-dish-coverage.js', expect: 1,
    desc: '生成器声明了刺身图但磁盘上没有 → 那道菜只能借别的菜的图（玩家看不出区别）',
    extra: EXTRA.kitchen, mutate: () => {}, deleteFiles: ['textures/item/sashimi.png'] },
  { dir: 'dish-cov-unknown-dish', checker: 'check-dish-coverage.js', expect: 1,
    desc: '§13.5 里冒出一道归不到任何贴图的新做法（草鱼丸 → 草鱼球）',
    extra: EXTRA.kitchen,
    mutate: (at) => {
      at.set('docs/25-鱼料理与配料方案.md', mustReplace(at.get('docs/25-鱼料理与配料方案.md'),
        '**草鱼丸**（工作台）', '**草鱼球**（工作台）', 'dish-cov-unknown-dish'));
    } },
  { dir: 'dish-cov-unused-icon', checker: 'check-dish-coverage.js', expect: 1,
    desc: '把所有整条油炸都改成红烧 → 那张 deep_fried_fish 白画了（没有任何地方会提示）',
    extra: EXTRA.kitchen,
    mutate: (at) => {
      // ⚠ 整条油炸的**菜数会随着补鱼变多**：最早两道（横纹鱼、白条鱼），2026-10-10 补到三道
      //   （多了毛鳞鱼）。第一版把两道菜名写死在这里，于是加完第三道之后这条负例**假绿**了
      //   —— 改掉两道，那张图还有第三道在用，检查器当然不红。
      //   所以这里不写死菜名：一次把所有 `**油炸…**` 改光，加多少道都跟着走。
      let doc = at.get('docs/25-鱼料理与配料方案.md');
      const n = (doc.match(/\*\*油炸/g) || []).length;
      if (n < 1) throw new Error('dish-cov-unused-icon：§13.5 里一道整条油炸都没有，这条负例已经失效');
      doc = doc.replace(/\*\*油炸/g, '**红烧');
      at.set('docs/25-鱼料理与配料方案.md', doc);
    } },

  // ---- check-recipe-book.js（12 条自定义配方要真的进原版配方书）----
  // 用户 2026-10-03："内容写进原版配方书"。这 12 条（切块/榨油/10 条菜型）原来都是 special，
  // 原版 ClientRecipeBook 第一句 `if (!recipe.isSpecial() && !recipe.isIncomplete())` 就把它们挡在外面。
  // 下面 5 个负例分别把"进得去"的三个条件与两张表各拆一处 —— 每一处都必须被抓到。
  // ---- check-recipe-book.js（配方进得去 · 而且显示的鱼种与定义一致）----
  // 用户 2026-10-03 两次反馈：① "内容写进原版配方书"（原来切块/榨油/菜配方都是 special）；
  // ② "配方必须完全与定义一致。现在的问题是配方显示模糊，什么鱼都可以做菜。"
  //   （于是菜配方从"一个菜型一条"改成"一条鱼一道菜"，共 31 条）
  // 所以下面既守"进得去"的三个条件，也守"一条鱼一道菜"这条显示规则。
  { dir: 'book-ok', checker: 'check-recipe-book.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'book-not-special', checker: 'check-recipe-book.js', expect: 1,
    desc: 'DishRecipe 的 isSpecial() 改回 true（= 配方书里一条菜都看不到，且不报任何错）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        '    public boolean isSpecial() {\n        return false;\n    }',
        '    public boolean isSpecial() {\n        return true;\n    }', 'book-not-special'));
    } },
  { dir: 'book-no-ingredients', checker: 'check-recipe-book.js', expect: 1,
    desc: 'getIngredients() 被改名（材料表没了 → isIncomplete() = 照样不进配方书）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        '    public NonNullList<Ingredient> getIngredients() {',
        '    public NonNullList<Ingredient> getIngredients_unused() {', 'book-no-ingredients'));
    } },
  { dir: 'book-pair-drift', checker: 'check-recipe-book.js', expect: 1,
    desc: '逐鱼表里把鲫鱼的奶白鱼汤删掉 → 磁盘上就多出一条"定义里没有"的配方',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'put(m, "crucian_carp", Dish.SOUP, Dish.CREAMY);',
        'put(m, "crucian_carp", Dish.SOUP);', 'book-pair-drift'));
    } },
  { dir: 'book-unlock-drift', checker: 'check-recipe-book.js', expect: 1,
    desc: '解锁材料从"那条鱼"改成笼统的熟块（再也不是"钓到它才解锁"）',
    mutate: (at) => {
      at.set('gen-recipe-advancements.js', mustReplace(at.get('gen-recipe-advancements.js'),
        "  if (dish) return NS + ':' + dish[1];        // 钓到这条鱼 → 解锁它的那道菜",
        "  if (dish) return NS + ':fish_chunk_cooked';", 'book-unlock-drift'));
    } },
  { dir: 'book-exempt-again', checker: 'check-recipe-book.js', expect: 1,
    desc: '把一条菜配方塞回 RECIPE_BOOK_EXEMPT（豁免 = 配方书里永远看不到）',
    mutate: (at) => {
      at.set('gen-recipe-advancements.js', mustReplace(at.get('gen-recipe-advancements.js'),
        "const RECIPE_BOOK_EXEMPT = new Set(['rod_assembly']);",
        "const RECIPE_BOOK_EXEMPT = new Set(['rod_assembly', 'braised_fish/grass_carp']);",
        'book-exempt-again'));
    } },
  { dir: 'book-dish-name-missing', checker: 'check-recipe-book.js', expect: 1,
    desc: '语言文件里少了"红烧%s"这个模板 → 配方书 / JEI 里又会退回笼统的"红烧鱼"',
    mutate: (at) => {
      at.set('lang/zh_cn.json', mustReplace(at.get('lang/zh_cn.json'),
        '  "dish.anglertackle.braised_fish": "红烧%s",\n', '', 'book-dish-name-missing'));
    } },
  // 用户 2026-10-03 抓到的第二个 bug："刺身需要大西洋鲑的**普通鱼块**，
  // 但显然普通鱼块不可能被大西洋鲑做出来"（鲑鱼是 {4,5} 档，只出顶级鱼排）。
  // 下面两条把这个 bug 的两个入口各堵一次。
  { dir: 'book-chunk-tier-hardcoded', checker: 'check-recipe-book.js', expect: 1,
    desc: '展示用的鱼块档位写死成 0（红档鱼被显示成"普通鱼块"—— 用户抓到的原样）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'stack.set(ModDataComponents.FISH_CHUNK.get(), new FishChunkData(species, sampleTier(species), 0.5f));',
        'stack.set(ModDataComponents.FISH_CHUNK.get(), new FishChunkData(species, 0, 0.5f));',
        'book-chunk-tier-hardcoded'));
    } },
  { dir: 'book-doc-tier-drift', checker: 'check-recipe-book.js', expect: 1,
    desc: '§13.5 把大西洋鲑的材料列写成"普通鱼块"（定义与鱼的档位表对不上）',
    mutate: (at) => {
      at.set('docs/25-鱼料理与配料方案.md', mustReplace(at.get('docs/25-鱼料理与配料方案.md'),
        '| 19 | 大西洋鲑 | 顶级鱼排 |', '| 19 | 大西洋鲑 | 普通鱼块 |', 'book-doc-tier-drift'));
    } },
  { dir: 'dish-recipe-hand-edited', checker: 'check-dish-recipes.js', expect: 1,
    desc: '手改一条菜配方 JSON（生成物不许手改：显示会与逐鱼表脱节）',
    mutate: (at) => {
      at.set('recipe/braised_fish/grass_carp.json', mustReplace(at.get('recipe/braised_fish/grass_carp.json'),
        '"type": "anglertackle:braised_fish/grass_carp"',
        '"type": "anglertackle:braised_fish/black_carp"', 'dish-recipe-hand-edited'));
    } },
  { dir: 'dish-recipe-extra', checker: 'check-dish-recipes.js', expect: 1,
    desc: '凭空多出一条"表里没有"的菜配方（红烧罗非鱼）—— 玩家会照着它摆材料然后做不出来',
    mutate: () => {},
    createFiles: [['src/main/resources/data/anglertackle/recipe/braised_fish/tilapia.json',
      '{\n  "type": "anglertackle:braised_fish/tilapia",\n  "category": "misc"\n}\n']] },

  // ---- check-build-scripts.js（版本号只有一个来源）----
  // 2026-10-03 真事故：两个脚本各自写死了 anglertackle-1.1.7.jar，改了 mod_version 之后
  // gradle 产出的是 1.1.8.jar，而脚本照旧部署 build\libs 里那个**上一版的同名旧文件** ——
  // "构建成功 / 部署成功 / 打包成功"三步全绿，发出去的全是旧版。

  { dir: 'reelsound-unwired', checker: 'check-reel-sound.js', expect: 1,
    desc: '把客户端 tick 里的 ReelSounds.tick(...) 删掉 —— 声音永远不响（用户这次报的就是"收线没声音"）',
    mutate: (at) => {
      const rel = 'src/main/java/com/whitecobalt/anglertackle/client/ClientEvents.java';
      const src = at.get(rel);
      const next = src.replace(/\s*\/\/ 收线的声音[^\n]*\n\s*ReelSounds\.tick\(state\);/, '');
      if (next === src) throw new Error('reelsound-unwired：改不动 ClientEvents');
      at.set(rel, next);
    } },

  { dir: 'version-tier-cross', checker: 'check-build-scripts.js', expect: 1,
    desc: '版本号跨档（1.5.2 → 1.6.x）—— 用户 2026-10-04 定：没他允许不许跨档，哪怕是 1.5.100 也得忍着',
    // ⚠ 不写死版本号：把次版本号 +1，所以以后版本涨了这条负例照样有效
    mutate: (at) => {
      const rel = 'gradle.properties';
      const src = at.get(rel);
      const next = src.replace(/^mod_version=(\d+)\.(\d+)\./m,
        (m, a, b) => 'mod_version=' + a + '.' + (Number(b) + 1) + '.');
      if (next === src) throw new Error('version-tier-cross：改不动 mod_version');
      at.set(rel, next);
    } },
  { dir: 'buildscripts-ok', checker: 'check-build-scripts.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'buildscripts-hardcoded', checker: 'check-build-scripts.js', expect: 1,
    desc: '把 jar 名写死回 build-deploy.ps1（下次改版本号就会静默部署上一版）',
    mutate: (at) => {
      at.set('build-deploy.ps1', mustReplace(at.get('build-deploy.ps1'),
        '$Name = "anglertackle-$ModVersion.jar"',
        '$Name = "anglertackle-0.0.1.jar"', 'buildscripts-hardcoded'));
    } },
  { dir: 'buildscripts-version-drift', checker: 'check-build-scripts.js', expect: 1,
    desc: 'gradle.properties 的版本与 jar 里装的版本对不上（= 发布了上一版）',
    mutate: (at) => {
      // ⚠ 不能把版本号写死在这个用例里：下一版一改 mod_version，锚点就失效，
      //   而 mustReplace 会**当场抛异常**挡住整个负例批次（2026-10-03 改 1.1.9 时正好踩到）。
      const cur = /mod_version=(\d+\.\d+\.\d+)/.exec(at.get('gradle.properties'))[1];
      at.set('gradle.properties', mustReplace(at.get('gradle.properties'),
        'mod_version=' + cur, 'mod_version=9.9.9', 'buildscripts-version-drift'));
    } },
  // 发布说明是**唯一直接给玩家看的东西**：2026-10-08 实测里面写着 "v1.0.1" 和 "21 种鱼"
  //（而当时已经是 1.5.42 / 47 种），而且没有任何检查在看它。
  { dir: 'buildscripts-readme-version', checker: 'check-build-scripts.js', expect: 1,
    desc: '发布说明里把版本号写死回 v1.0.1（说明的版本必须来自 gradle.properties）',
    mutate: (at) => {
      at.set('make-release.ps1', mustReplace(at.get('make-release.ps1'),
        'v$ModVersion', 'v1.0.1', 'buildscripts-readme-version'));
    } },
  { dir: 'buildscripts-readme-fishcount', checker: 'check-build-scripts.js', expect: 1,
    desc: '发布说明里把鱼种数写死回 21 种（鱼种数必须从 FishSpecies.java 数出来）',
    mutate: (at) => {
      at.set('make-release.ps1', mustReplace(at.get('make-release.ps1'),
        '$FishCount 种鱼', '21 种鱼', 'buildscripts-readme-fishcount'));
    } },

  { dir: 'buildscripts-no-fresh-check', checker: 'check-build-scripts.js', expect: 1,
    desc: '删掉"jar 必须是本次构建产出的"那条断言（旧文件又能蒙混过关）',
    mutate: (at) => {
      at.set('build-deploy.ps1', mustReplace(at.get('build-deploy.ps1'),
        'if ((Get-Item $Jar).LastWriteTime -lt $BuildStart) {',
        'if ($false) {', 'buildscripts-no-fresh-check'));
    } },

  // ---- check-progress-doc.js（看板里的检查器数量不许写死）----
  // 2026-10-11 实测：gen-progress.js 的表头写死 "119 检查器"，而闸门里跑的是 130 个，
  // 且没有任何检查在看它 ⇒ 看板的第一眼数字一直在撒谎 ✗。
  // ⚠ 第 ④ 条（看板数量 == 磁盘条数）**只在真仓库跑**：副本里只拷了几个检查器，
  //   而 docs/进度.md 是从真仓库原样拷来的 ⇒ 不跳过就是对照组假失败。
  //   那一条的证伪是手工做的（改掉 docs/进度.md 表头数字 → 红 → 跑生成器还原）。
  { dir: 'progress-ok', checker: 'check-progress-doc.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'progress-count-hardcoded', checker: 'check-progress-doc.js', expect: 1,
    desc: '把检查器数量写死回表头（119 检查器 那次的类型：数字会一直撒谎）',
    mutate: (at) => {
      at.set('tools/gen-progress.js', mustReplace(at.get('tools/gen-progress.js'),
        "'| 日志 | ' + checkerCount + ' 检查器 | 负例 | 判定 |'",
        "'| 日志 | 119 检查器 | 负例 | 判定 |'", 'progress-count-hardcoded'));
    } },
  { dir: 'progress-count-constant', checker: 'check-progress-doc.js', expect: 1,
    desc: '不再去数 tools/check-*.js，改成直接给常量（撒得更隐蔽，同样要红）',
    mutate: (at) => {
      const src = at.get('tools/gen-progress.js');
      const anchor = src.match(/const checkerCount = exists\('tools'\)[\s\S]*?: 0;/);
      if (!anchor) throw new Error('progress-count-constant：改不动 checkerCount 的算法');
      at.set('tools/gen-progress.js', src.replace(anchor[0], 'const checkerCount = 99;'));
    } },
  { dir: 'progress-doc-not-generated', checker: 'check-progress-doc.js', expect: 1,
    desc: '去掉生成物的"自动生成，勿手改"标记（下一个人就会去手改它，而下次生成覆盖）',
    mutate: (at) => {
      at.set('docs/进度.md', mustReplace(at.get('docs/进度.md'),
        '# 进度看板（自动生成，勿手改）', '# 进度看板', 'progress-doc-not-generated'));
    } },

  // ---- check-food-values.js（按档位的饱食度阶梯）----
  // 用户 2026-10-03："根据鱼品质做饱食度区分 … 每差一级差半格 … 每个档次再差半格 …
  // 但无论如何加工成的烹饪品至少比熟鱼块高半格。"
  // 单位：1 格 = 一个鸡腿图标 = 2 点 nutrition，半格 = 1 点。
  { dir: 'food-ok', checker: 'check-food-values.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'food-doc-wrong-number', checker: 'check-food-values.js', expect: 1,
    desc: '把文档里"菜 0 档 4 / 4.0"改成 5 / 4.0（文档与源码脱节）',
    mutate: (at) => {
      at.set('docs/27-食物数值表.md', mustReplace(at.get('docs/27-食物数值表.md'),
        '| 0 白 | 4（2 格） | 4.0 |', '| 0 白 | 5（2 格） | 4.0 |', 'food-doc-wrong-number'));
    } },
  { dir: 'food-doc-behaviour-stale', checker: 'check-food-values.js', expect: 1,
    desc: 'docs/27 §5 把"行为已实现"删掉（代码里已经有事件挂点，文档却停留在只有框架）',
    mutate: (at) => {
      at.set('docs/27-食物数值表.md', mustReplace(at.get('docs/27-食物数值表.md'),
        '11 项效果的**行为也已实现**（1.1.17）', '11 项效果的**行为还在做**（1.1.17）',
        'food-doc-behaviour-stale'));
    } },
  { dir: 'food-saturation-doubled', checker: 'check-food-values.js', expect: 1,
    desc: 'saturationModifier 改回 1.0（实际饱和度变成饱食度的两倍 —— 第一版的错）',
    mutate: (at) => {
      at.set('FoodValues.java', mustReplace(at.get('FoodValues.java'),
        'return 0.5f;', 'return 1.0f;', 'food-saturation-doubled'));
    } },
  { dir: 'food-dish-not-taller', checker: 'check-food-values.js', expect: 1,
    desc: '菜不再比熟块高半格（dishHunger 改成 + 0，用户明确要求的下限没了）',
    mutate: (at) => {
      at.set('FoodValues.java', mustReplace(at.get('FoodValues.java'),
        'return cookedHunger(tier) + 1;', 'return cookedHunger(tier) + 0;', 'food-dish-not-taller'));
    } },
  { dir: 'food-dish-bucket', checker: 'check-food-values.js', expect: 1,
    desc: '菜退回"按三个材料档次分桶"（同档内的半格区分消失）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'foodFor(m.data().tier())', 'foodFor(FishChunkData.groupOfTier(m.data().tier()))',
        'food-dish-bucket'));
    } },

  // ---- check-dish-effects.js（docs/28 的 11 项菜型效果）----
  // 用户 2026-10-03 定案；这一层的错法全都不会报错，只会让玩家白吃一道菜或拿到错的东西。
  { dir: 'effects-ok', checker: 'check-dish-effects.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'effects-missing-icon', checker: 'check-dish-effects.js', expect: 1,
    desc: '删掉一张效果的 HUD 图标（游戏里变成紫黑格，玩家不知道吃到了什么）',
    mutate: () => {}, deleteFiles: ['textures/mob_effect/fishing_luck.png'] },
  { dir: 'effects-level-uncapped', checker: 'check-dish-effects.js', expect: 1,
    desc: '等级不封顶（至高 2 的效果被红档鱼顶到 5 级）',
    mutate: (at) => {
      at.set('DishEffects.java', mustReplace(at.get('DishEffects.java'),
        'int level = Math.max(1, Math.min(maxLevel, tier));',
        'int level = Math.max(1, tier);', 'effects-level-uncapped'));
    } },
  { dir: 'effects-mutex-gone', checker: 'check-dish-effects.js', expect: 1,
    desc: '删掉爆口 × 空军互斥（两道菜一起吃 = 双倍权重）',
    mutate: (at) => {
      at.set('DishEffects.java', mustReplace(at.get('DishEffects.java'),
        '        if (gotBig) {\n            player.removeEffect(ModEffects.SMALL_FRY);\n        }\n',
        '', 'effects-mutex-gone'));
    } },
  { dir: 'effects-map-drift', checker: 'check-dish-effects.js', expect: 1,
    desc: '把红烧鱼的效果从渔运换成强力（代码与 docs/28 §4 的映射表脱节）',
    mutate: (at) => {
      at.set('DishEffects.java', mustReplace(at.get('DishEffects.java'),
        'return newGrant(ModEffects.FISHING_LUCK, tier, 2);',
        'return newGrant(ModEffects.RANGED_POWER, tier, 2);', 'effects-map-drift'));
    } },

  // ---- check-dish-effect-hooks.js（docs/28 §3 的 11 项效果**行为**，1.1.17）----
  // 这一层的错法全都不报错：效果照给、图标照显示，只是数值/位置错了。
  // 用户 2026-10-03 定案"做效果行为"，每一条数值都是他给的，所以这里逐条钉住。
  { dir: 'effecthooks-ok', checker: 'check-dish-effect-hooks.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'effecthooks-luck-number', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '渔运每级加成从 3 个百分点改成 6（源码与 docs/28 §3 的数值脱节）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        'public static final double LUCK_POINTS_PER_LEVEL = 3.0;',
        'public static final double LUCK_POINTS_PER_LEVEL = 6.0;', 'effecthooks-luck-number'));
    } },
  { dir: 'effecthooks-luck-target', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '渔运只保留"饵料承诺占比"那条入口（路亚上会把金红占比压到 3%）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        '            } else if (hvWeight > 0.0 && total > hvWeight) {\n'
        + '                targetPct = hvWeight / total * 100.0 + bonus.luckPoints();\n'
        + '            }\n',
        '            }\n', 'effecthooks-luck-target'));
    } },
  { dir: 'effecthooks-species-after-round', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '爆口/空军的鱼种加成挪到"解金红占比 k"之前（会被 k 原样除回去，净效果为零）',
    mutate: (at) => {
      const src = at.get('FishSpecies.java');
      const m = src.match(/        \/\/ ★ 爆口 \/ 空军[\s\S]*?\n        \}\n\n/);
      if (!m) throw new Error('锚点过时：找不到爆口/空军的加成块（effecthooks-species-after-round）');
      let out = mustReplace(src, m[0], '', 'effecthooks-species-after-round');
      out = mustReplace(out, '        double targetPct = hvTargetPct;',
        m[0] + '        double targetPct = hvTargetPct;', 'effecthooks-species-after-round');
      at.set('FishSpecies.java', out);
    } },
  { dir: 'effecthooks-giant-uncapped', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '巨物猎手去掉"金档以上"的护栏（紫档鱼咬钩也加回拉速度）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        '        if (hookedTier < GIANT_MIN_TIER) {\n            return 1.0;\n        }\n',
        '', 'effecthooks-giant-uncapped'));
    } },
  { dir: 'effecthooks-melee-buffed', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '强力不再判弹射物（近战也吃 +15%/级，用户说的是"远程武器"）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        'source.getDirectEntity() instanceof Projectile',
        'source.getDirectEntity() != null', 'effecthooks-melee-buffed'));
    } },
  { dir: 'effecthooks-permanent-modifier', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '速度修饰符改成永久（进存档，效果到期后永久加速）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        'inst.addTransientModifier(new AttributeModifier(id, amount,',
        'inst.addPermanentModifier(new AttributeModifier(id, amount,',
        'effecthooks-permanent-modifier'));
    } },
  { dir: 'effecthooks-no-chunk-guard', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '明目扫矿不看 hasChunkAt（会为了找矿强加载区块，卡服）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        '                        if (!level.hasChunkAt(p)) {\n'
        + '                            continue;               // 未加载的区块：跳过。绝不为了扫描去强加载区块\n'
        + '                        }\n',
        '', 'effecthooks-no-chunk-guard'));
    } },
  { dir: 'effecthooks-doc-todo', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: 'docs/28 §6 把已经做完的明目又标回 ⏳（下一个人会照着文档再实现一遍）',
    mutate: (at) => {
      at.set('28-菜型效果策划.md', mustReplace(at.get('28-菜型效果策划.md'),
        '| 明目（线条指引） | ✅ 1.1.17 |', '| 明目（线条指引） | ⏳ |',
        'effecthooks-doc-todo'));
    } },
  // ---- 1.1.18：用户实测反馈的两条（水下移速不生效 / 明目粒子看不清又卡）----
  { dir: 'effecthooks-ocean-movement-attr', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '海洋亲和的水下加速改回 MOVEMENT_SPEED（用户实测过的那个 bug：只有 FOV 变、移速不变）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        'setModifier(player, NeoForgeMod.SWIM_SPEED, OCEAN_SWIM_ID,',
        'setModifier(player, Attributes.MOVEMENT_SPEED, OCEAN_SWIM_ID,',
        'effecthooks-ocean-movement-attr'));
    } },
  { dir: 'effecthooks-oresight-particles', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '明目改回发粒子（用户已定案改成客户端画线：粒子多会卡、也看不出方向）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        '            ModNetwork.sendTo(player, new OreSightPayload(scan.target, true));',
        '            level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,\n'
        + '                    0.0, 0.0, 0.0, 1, 0.0, 0.0, 0.0, 0.0);',
        'effecthooks-oresight-particles'));
    } },
  { dir: 'effecthooks-oresight-no-dot', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '删掉指引线上的流动点（只剩一条静态线 —— 用户第一条反馈就是"看不出方向"）',
    mutate: (at) => {
      at.set('OreSightRenderer.java', mustReplace(at.get('OreSightRenderer.java'),
        '        drawFlowDot(lines, matrix, start, delta.scale(1.0 / dist), dist,',
        '        // drawFlowDot(lines, matrix, start, delta.scale(1.0 / dist), dist,',
        'effecthooks-oresight-no-dot'));
    } },
  { dir: 'effecthooks-oresight-no-timeout', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '客户端去掉心跳超时（效果到期/丢包后会留下一条指向空气的线）',
    mutate: (at) => {
      at.set('ClientOreSight.java', mustReplace(at.get('ClientOreSight.java'),
        'TIMEOUT_TICKS = 60', 'TIMEOUT_TICKS = 60_000_000',
        'effecthooks-oresight-no-timeout'));
    } },
  // ---- 1.1.19：用户实测"指引线乱跳，疑似指向多目标但不是最近的" ----
  { dir: 'effecthooks-oresight-no-hysteresis', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '去掉选目标的滞回（每次都改指"这一轮扫到的最近那块"，线会来回抽搐）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        '        boolean clearlyCloser = neu < cur - ORE_SIGHT_SWITCH_MARGIN * ORE_SIGHT_SWITCH_MARGIN\n'
        + '                || neu < cur * ORE_SIGHT_SWITCH_RATIO * ORE_SIGHT_SWITCH_RATIO;\n'
        + '        return clearlyCloser ? best : current;',
        '        return best;',
        'effecthooks-oresight-no-hysteresis'));
    } },
  { dir: 'effecthooks-oresight-first-hit', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '去掉"只找更近的"比较（变成"第一个撞见的矿"，正是用户报的"指的不是最近的"）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        '                        if (sq >= bestSq) {\n                            continue;\n                        }\n',
        '', 'effecthooks-oresight-first-hit'));
    } },
  { dir: 'effecthooks-oresight-no-throttle', checker: 'check-dish-effect-hooks.js', expect: 1,
    desc: '全量扫描不再节流（每 tick 扫 3.6 万格）',
    mutate: (at) => {
      at.set('DishEffectHooks.java', mustReplace(at.get('DishEffectHooks.java'),
        'if (player.tickCount >= scan.nextSweepTick) {', 'if (true) {',
        'effecthooks-oresight-no-throttle'));
    } },

  // ---- check-cookbook.js（菜谱 + 悬浮显示，1.2.1）----
  // 这一层的错法都不报错：玩家只是看不到、或者看到一份与实物不符的数字。
  { dir: 'cookbook-ok', checker: 'check-cookbook.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'cookbook-hover-own-numbers', checker: 'check-cookbook.js', expect: 1,
    desc: '悬浮显示自己算效果（不调 DishEffects）—— 与真正发的效果分叉',
    mutate: (at) => {
      at.set('DishItem.java', mustReplace(at.get('DishItem.java'),
        'DishEffects.grantsFor(id, data.tier())',
        'com.whitecobalt.anglertackle.effect.DishEffects.grantsFor("sashimi", 4)',
        'cookbook-hover-own-numbers'));
    } },
  { dir: 'cookbook-screen-own-table', checker: 'check-cookbook.js', expect: 1,
    desc: '菜谱界面的材料自己抄一张表（不读 allowedPairs）—— 会写出"能做但做不出来"',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'for (DishRecipe.Pair pair : DishRecipe.allowedPairs()) {',
        'for (DishRecipe.Pair pair : java.util.List.<DishRecipe.Pair>of()) {',
        'cookbook-screen-own-table'));
    } },
  { dir: 'cookbook-recipe-wrong-ingredient', checker: 'check-cookbook.js', expect: 1,
    desc: '菜谱配方改成"生鱼 + 书"（用户定的是原版任意熟鱼）',
    mutate: (at) => {
      // ⚠ 必须用**完整相对路径**：配方与进度都叫 cookbook.json，用文件名会报"有歧义"
      const rel = 'src/main/resources/data/anglertackle/recipe/cookbook.json';
      at.set(rel, mustReplace(at.get(rel),
        '"tag": "c:foods/cooked_fish"', '"tag": "c:foods/raw_fish"',
        'cookbook-recipe-wrong-ingredient'));
    } },
  { dir: 'cookbook-item-client-class', checker: 'check-cookbook.js', expect: 1,
    desc: '菜谱物品直接在 common 侧开界面（专用服务器会因此装不进模组）',
    mutate: (at) => {
      at.set('CookbookItem.java', mustReplace(at.get('CookbookItem.java'),
        '            PacketDistributor.sendToServer(CookbookRequestPayload.INSTANCE);',
        '            net.minecraft.client.Minecraft.getInstance()'
        + '.setScreen(new com.whitecobalt.anglertackle.client.screen.CookbookScreen());',
        'cookbook-item-client-class'));
    } },
  { dir: 'cookbook-guide-wrong-tier', checker: 'check-cookbook.js', expect: 1,
    desc: '教程里"金档菜 8 点"改成 7 点（手册数字与 FoodValues 脱节）',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '"    金          8 点    7 点",', '"    金          7 点    7 点",',
        'cookbook-guide-wrong-tier'));
    } },
  { dir: 'cookbook-no-advancement', checker: 'check-cookbook.js', expect: 1,
    desc: '删掉配方解锁进度里的 rewards.recipes（配方书里永远看不到菜谱那一条）',
    mutate: (at) => {
      const rel = 'src/main/resources/data/anglertackle/advancement/recipes/cookbook.json';
      at.set(rel, mustReplace(at.get(rel),
        '"recipes": [\n      "anglertackle:cookbook"\n    ]', '"recipes": []',
        'cookbook-no-advancement'));
    } },
  { dir: 'cookbook-doc-missing-rule', checker: 'check-cookbook.js', expect: 1,
    desc: 'docs/29 把"悬浮显示的数字来自 grantsFor"这条纪律删掉（下一个人就会另抄一份数值）',
    mutate: (at) => {
      at.set('29-菜谱与悬浮显示.md', mustReplace(at.get('29-菜谱与悬浮显示.md'),
        '`DishEffects.grantsFor(dishId, 档位)`', '一份我抄好的表',
        'cookbook-doc-missing-rule'));
    } },
  // ---- 1.2.2：用户第二轮反馈（12 道只介绍 10 道 / 配方说不清）----
  { dir: 'cookbook-missing-device-dish', checker: 'check-cookbook.js', expect: 1,
    desc: '菜谱漏掉设备菜（熏鱼/炭烤鱼）—— 正是用户报的"12道菜只介绍10道"',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'List.of("smoked_fish", "charred_fish")', 'List.of("smoked_fish")',
        'cookbook-missing-device-dish'));
    } },
  { dir: 'cookbook-no-ingredients', checker: 'check-cookbook.js', expect: 1,
    desc: '「做法」页不再列基础与配料（只剩鱼 + 效果 —— 用户报的"配方表达不完整"）',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'out.add(new Line(section("extra") + (dish.extraItems().isEmpty()',
        'out.add(new Line(section("extra") + (java.util.List.<Item>of().isEmpty()',
        'cookbook-no-ingredients'));
    } },
  { dir: 'cookbook-hardcoded-source', checker: 'check-cookbook.js', expect: 1,
    desc: '「配料怎么来」改成手抄一张表（不再问配方管理器 —— 配方一改界面就撒谎）',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'for (RecipeHolder<?> holder : mc.level.getRecipeManager().getRecipes()) {',
        'for (RecipeHolder<?> holder : java.util.List.<RecipeHolder<?>>of()) {',
        'cookbook-hardcoded-source'));
    } },
  { dir: 'cookbook-tooltip-missing-extra', checker: 'check-cookbook.js', expect: 1,
    desc: '把红烧鱼 tooltip 里的"糖"删掉（悬浮显示与真实配料脱节）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '熟块 + 酱油 + 糖红烧：家常味', '熟块 + 酱油红烧：家常味',
        'cookbook-tooltip-missing-extra'));
    } },

  // ---- 1.2.4：用户看着截图指出"很明显，鱼块的做法不是这样的" ----
  { dir: 'cookbook-cut-knife-rightclick', checker: 'check-cookbook.js', expect: 1,
    desc: '切块那句又写回"整鱼 --鱼刀右键--> 鱼块"（切块其实是工作台配方）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '① 整鱼放进工作台 --> 鱼块 ×%s~%s（切几块看重量）',
        '① 整鱼 --鱼刀右键--> 鱼块（%s~%s 块）',
        'cookbook-cut-knife-rightclick'));
    } },
  { dir: 'cookbook-heat-three-devices', checker: 'check-cookbook.js', expect: 1,
    desc: '加热那句又写成"熔炉 / 营火 / 烟熏炉 --> 熟鱼块"（营火出炭烤鱼、烟熏炉出熏鱼）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '② 鱼块 --熔炉--> 熟鱼块',
        '② 鱼块 --熔炉 / 营火 / 烟熏炉--> 熟鱼块',
        'cookbook-heat-three-devices'));
    } },
  { dir: 'cookbook-cut-hardcoded-range', checker: 'check-cookbook.js', expect: 1,
    desc: '切块份数写死在文案里（不再从 CuttingRecipe 常量现取）',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'CuttingRecipe.MIN_CHUNKS, CuttingRecipe.MAX_CHUNKS).getString();',
        '1, 12).getString();',
        'cookbook-cut-hardcoded-range'));
    } },
  { dir: 'cookbook-guide-knife-cut', checker: 'check-cookbook.js', expect: 1,
    desc: '教程里又写成"拿鱼刀切块"',
    mutate: (at) => {
      at.set('guide.json', mustReplace(at.get('guide.json'),
        '  "  切块：整条鱼放进工作台就行，",',
        '  "  切块：手里拿鱼刀，对着鱼右键",',
        'cookbook-guide-knife-cut'));
    } },

  // ---- check-tooltip-colors.js（1.2.3：用户"自查所有悬浮显示，该显示颜色的却是白色"）----
  { dir: 'tooltipcolor-ok', checker: 'check-tooltip-colors.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'tooltipcolor-chunk-white', checker: 'check-tooltip-colors.js', expect: 1,
    desc: '鱼块档次段改回按 group 手写表（绿档显白、金红档的顶级显紫 —— 用户报的那一类）',
    mutate: (at) => {
      at.set('FishChunkItem.java', mustReplace(at.get('FishChunkItem.java'),
        '.withStyle(Quality.of(data.tier()).color()))',
        '.withStyle(group == 2 ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.WHITE))',
        'tooltipcolor-chunk-white'));
    } },
  { dir: 'tooltipcolor-dish-gray', checker: 'check-tooltip-colors.js', expect: 1,
    desc: '菜的档次段改回 GRAY（"顶级"和"普通"看起来一样）',
    mutate: (at) => {
      at.set('DishItem.java', mustReplace(at.get('DishItem.java'),
        '.withStyle(Quality.of(data.tier()).color())));',
        '.withStyle(ChatFormatting.GRAY)));',
        'tooltipcolor-dish-gray'));
    } },
  { dir: 'tooltipcolor-grade-gray', checker: 'check-tooltip-colors.js', expect: 1,
    desc: '鱼的尺寸评级值去掉品质色（"纪录级"与"幼体"同色，而售价差 3.5 倍）',
    mutate: (at) => {
      at.set('FishItem.java', mustReplace(at.get('FishItem.java'),
        'Component.translatable("grade.anglertackle." + data.grade().key())\n'
        + '                                .withStyle(Quality.of(data.effectiveTier()).color()))',
        'Component.translatable("grade.anglertackle." + data.grade().key()))',
        'tooltipcolor-grade-gray'));
    } },
  // ---- check-line-multiplayer.js（2026-10-10 联机实测：对方的鱼线完全不渲染 ✗）----
  { dir: 'line-mp-no-others', checker: 'check-line-multiplayer.js', expect: 1,
    desc: '删掉"画别人的线"的调用 ⇒ 多人下对方的鱼线又看不见了（用户实测报的就是这个）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/render/FishingLineRenderer.java';
      at.set(F, mustReplace(at.get(F),
        '        renderOtherPlayersLines(event);\n', '', 'line-mp-no-others'));
    } },
  { dir: 'line-mp-nearest-again', checker: 'check-line-multiplayer.js', expect: 1,
    desc: '配对又改回"就近的握竿玩家"⇒ A 抛远、B 站近时线会画到 B 手上（用户指出的反例）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/render/FishingLineRenderer.java';
      at.set(F, mustReplace(at.get(F),
        'java.util.UUID ownerId = bobber.ownerId();',
        'java.util.UUID ownerId = null;\n            double best = 40.0 * 40.0;\n            for (net.minecraft.world.entity.player.Player p2 : holders) { double d = p2.distanceToSqr(bobber); if (d < best) { best = d; ownerId = p2.getUUID(); } }',
        'line-mp-nearest-again'));
    } },

  { dir: 'tooltipcolor-rod-aqua-only', checker: 'check-tooltip-colors.js', expect: 1,
    desc: '竿的"档位上限"那个档位名不再带品质色（整行 AQUA）',
    mutate: (at) => {
      at.set('RodItem.java', mustReplace(at.get('RodItem.java'),
        'Component.translatable("tooltip.anglertackle.quality." + q.key())\n'
        + '                                    .withStyle(q.color()))',
        'Component.translatable("tooltip.anglertackle.quality." + q.key())',
        'tooltipcolor-rod-aqua-only'));
    } },
  { dir: 'tooltipcolor-chum-own-table', checker: 'check-tooltip-colors.js', expect: 1,
    desc: '孵化料又抄回一份自己的档位颜色 switch（与 Quality 分叉的开始）',
    mutate: (at) => {
      at.set('ChumItem.java', mustReplace(at.get('ChumItem.java'),
        '        //   现在价格由 ShopCatalog 统一给出，tooltip 里的旧数字只会和"价值"打架。\n    }',
        '        //   现在价格由 ShopCatalog 统一给出，tooltip 里的旧数字只会和"价值"打架。\n'
        + '    }\n\n'
        + '    private static ChatFormatting tierColor(int tier) {\n'
        + '        return switch (tier) {\n'
        + '            case 1 -> ChatFormatting.GREEN;\n'
        + '            default -> ChatFormatting.WHITE;\n'
        + '        };\n'
        + '    }',
        'tooltipcolor-chum-own-table'));
    } },

  // ---- 1.2.5 工程债：四个"守用户验收物、却一直没有负例"的高风险检查器 ----
  // （原来 5 个，其中 check-hollow 根本不是闸门 —— 它从不设退出码，
  //   已改名为 tools/report-fish-hollow.js，见那边的头部注释）
  { dir: 'overcap-ok', checker: 'check-over-capacity.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'overcap-weight-filter', checker: 'check-over-capacity.js', expect: 1,
    desc: 'buildPool 又按承重过滤鱼种（大西洋鲑这类"跨度大"的鱼直接进不了池，烧竿惩罚永远不触发）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        '            int base = weightByTier[Math.min(weightByTier.length - 1, s.minTier())];',
        '            if (maxWeightKg > 0 && s.kgMin() > maxWeightKg) {\n'
        + '                continue;\n'
        + '            }\n'
        + '            int base = weightByTier[Math.min(weightByTier.length - 1, s.minTier())];',
        'overcap-weight-filter'));
    } },
  { dir: 'playerdata-ok', checker: 'check-player-data.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'playerdata-no-copy-on-death', checker: 'check-player-data.js', expect: 1,
    desc: '附件少写 copyOnDeath（玩家死一次，鱼币与图鉴全清空）',
    mutate: (at) => {
      // ⚠ 锚点必须把 serialize 那一行带上：文件里有两处 .copyOnDeath()（鱼币/图鉴 与 老柯进度），
      //   只写 .copyOnDeath() 会报"有歧义"
      at.set('ModAttachments.java', mustReplace(at.get('ModAttachments.java'),
        '                    .serialize(PlayerFishingData.CODEC)\n                    .copyOnDeath()',
        '                    .serialize(PlayerFishingData.CODEC)',
        'playerdata-no-copy-on-death'));
    } },
  { dir: 'selfconsist-ok', checker: 'check-self-consistency.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'selfconsist-fish-not-in-tab', checker: 'check-self-consistency.js', expect: 1,
    desc: '有一种鱼没进创造栏（玩家在创造模式里找不到它）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        '                        output.accept(GRASS_CARP.get());\n',
        '',
        'selfconsist-fish-not-in-tab'));
    } },
  { dir: 'deadfields-ok', checker: 'check-dead-fields.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'deadfields-dead-component', checker: 'check-dead-fields.js', expect: 1,
    desc: 'PartStats 加一个没人读的字段（就是 pullStruggle 那类"玩家按 tooltip 选、实际没用"的坑）',
    mutate: (at) => {
      at.set('PartStats.java', mustReplace(at.get('PartStats.java'),
        '        int tier\n', '        double deadTestField,\n        int tier\n',
        'deadfields-dead-component'));
    } },

  // ---- check-kaleidoscope-compat.js（docs/30 森罗物语联动，1.3.0）----
  { dir: 'kaleidoscope-ok', checker: 'check-kaleidoscope-compat.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },
  { dir: 'kaleidoscope-required-true', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '他们的油脂写成 required:true（没装森罗时整个标签加载失败，我们的配方全废）',
    mutate: (at) => {
      at.set('cooking_oil.json', mustReplace(at.get('cooking_oil.json'),
        '"required": false', '"required": true', 'kaleidoscope-required-true'));
    } },
  { dir: 'kaleidoscope-drop-our-oil', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '替代标签里把**我们自己的**鱼油删掉（没装森罗时反而做不出菜）',
    mutate: (at) => {
      at.set('cooking_oil.json', mustReplace(at.get('cooking_oil.json'),
        '    "anglertackle:fish_oil",\n', '', 'kaleidoscope-drop-our-oil'));
    } },
  { dir: 'kaleidoscope-override-recipe', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '在 data/kaleidoscope_cookery/recipe/ 下放文件（那是**覆盖他们的配方**，踩红线）',
    mutate: () => {},
    createFiles: [['src/main/resources/data/kaleidoscope_cookery/recipe/pot/hacked.json', '{}']] },
  { dir: 'kaleidoscope-raw-salmon-mixup', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '把我们的鱼挂进 c:foods/raw_salmon（"这条是鲑鱼"——语义错，鲑鱼刺身会接受鲫鱼）',
    mutate: () => {},
    createFiles: [['src/main/resources/data/c/tags/item/foods/raw_salmon.json',
      JSON.stringify({ values: ['#anglertackle:fishes'] }, null, 2)]] },
  { dir: 'kaleidoscope-hardcoded-need', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '鱼的油位写回死物品（别的模组的同类油脂就顶不了）',
    mutate: (at) => {
      at.set('DishRecipe.java', mustReplace(at.get('DishRecipe.java'),
        'private static final Need OIL = mat(ModTags.COOKING_OIL, () -> ModItems.FISH_OIL.get());',
        'private static final Need OIL = mat(() -> ModItems.FISH_OIL.get());',
        'kaleidoscope-hardcoded-need'));
    } },
  { dir: 'kaleidoscope-recipe-no-condition', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '兼容配方删掉 mod_loaded 条件（没装森罗时会加载一条类型不存在的配方）',
    mutate: (at) => {
      const rel = 'src/main/resources/data/anglertackle/recipe/kaleidoscope/pot/braised_fish.json';
      at.set(rel, mustReplace(at.get(rel), '"neoforge:conditions": [\n    {\n      "type": "neoforge:mod_loaded",\n      "modid": "kaleidoscope_cookery"\n    }\n  ],\n', '', 'kaleidoscope-recipe-no-condition'));
    } },
  { dir: 'kaleidoscope-recipe-their-dish', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '兼容配方的产物改成他们的菜（那就成了替他们做菜，不是"做我们的菜"）',
    mutate: (at) => {
      const rel = 'src/main/resources/data/anglertackle/recipe/kaleidoscope/pot/braised_fish.json';
      at.set(rel, mustReplace(at.get(rel), '"id": "anglertackle:braised_fish"', '"id": "kaleidoscope_cookery:sweet_and_sour_pork"', 'kaleidoscope-recipe-their-dish'));
    } },
  // ---- 部署护栏（2026-10-12 用户开不了游戏那次事故）----
  { dir: 'kaleidoscope-deploy-unguarded', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '部署脚本又把扩展包改成无条件拷贝（没装森罗的实例会被它崩在加载阶段）',
    mutate: (at) => {
      at.set('build-deploy.ps1', mustReplace(at.get('build-deploy.ps1'),
        'if ((Test-Path $CompatJar) -and $hasKaleido -and -not $compatDisabled) {',
        'if ((Test-Path $CompatJar)) {', 'kaleidoscope-deploy-unguarded'));
    } },
  { dir: 'kaleidoscope-ignore-disabled', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '无视用户在 PCL 里点的"禁用"（.jar.disabled）⇒ 把人家关掉的东西重新打开',
    mutate: (at) => {
      at.set('build-deploy.ps1', mustReplace(at.get('build-deploy.ps1'),
        "'anglertackle-kaleidoscope-*.jar.disabled'", "'never-matches-anything'",
        'kaleidoscope-ignore-disabled'));
    } },
  { dir: 'kaleidoscope-wrong-note-back', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: 'mods.toml 里那句错的说法又写回去（"没装森罗也能正常加载" ⇒ 用户再次开不了游戏）',
    mutate: (at) => {
      at.set('neoforge.mods.toml', mustReplace(at.get('neoforge.mods.toml'),
        '# 垂纶 × 森罗物语 扩展包',
        '# 垂纶 × 森罗物语 扩展包\n#   不装森罗时本包整包不加载（下面 depends 是 required）→ 主 jar 的原路径完全照旧。',
        'kaleidoscope-wrong-note-back'));
    } },
  { dir: 'kaleidoscope-hard-dependency', checker: 'check-kaleidoscope-compat.js', expect: 1,
    desc: '主 jar 里直接引用他们的类（变成硬依赖，没装森罗直接崩）',
    mutate: (at) => {
      at.set('CookbookScreen.java', mustReplace(at.get('CookbookScreen.java'),
        'public class CookbookScreen extends Screen {',
        'public class CookbookScreen extends Screen {\n'
        + '    // 故意引用他们的类（负例）：\n'
        + '    private static final String THEIR = "com.github.ysbbbbbb.kaleidoscopecookery.api.item.IHasContainer";',
        'kaleidoscope-hard-dependency'));
    } },

  // ---- check-tier-feel.js（金/红档手感修正，用户 2026-10-05）----
  { dir: 'tier-feel-ok', checker: 'check-tier-feel.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'tier-feel-gold-back', checker: 'check-tier-feel.js', expect: 1,
    desc: '把金档游速旋钮改回 1.0（等于没改 —— 金红手感又一样了）',
    mutate: (at) => {
      at.set('gen-gameplay-config.js', mustReplace(at.get('gen-gameplay-config.js'),
        "{ key: 'tierGoldSpeedScale', cn: '金档 · 游速手感倍率', unit: '', def: 0.84, min: 0, max: 3,",
        "{ key: 'tierGoldSpeedScale', cn: '金档 · 游速手感倍率', unit: '', def: 1.00, min: 0, max: 3,",
        'tier-feel-gold-back'));
    } },

  { dir: 'tier-feel-no-speed-wire', checker: 'check-tier-feel.js', expect: 1,
    desc: 'tierSpeed 不再乘手感修正（旋钮还在，但没接上）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        '\n                * tierFeelSpeedScale(tier)', '', 'tier-feel-no-speed-wire'));
    } },

  { dir: 'tier-feel-stamina-gone', checker: 'check-tier-feel.js', expect: 1,
    desc: '体力池不乘档位修正（红档体力加 50% 没生效）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'this.fishStaminaMax = baseStamina * FishSpecies.tierFeelStaminaScale(hookedTier);',
        'this.fishStaminaMax = baseStamina;', 'tier-feel-stamina-gone'));
    } },

  { dir: 'tier-feel-tier-off-by-one', checker: 'check-tier-feel.js', expect: 1,
    desc: '金档分支写成 tier == 3（档位 0 起算，3 是紫档 —— 改错了人）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        'if (tier == 4) {\n            return Tuning.tierGoldSpeedScale();',
        'if (tier == 3) {\n            return Tuning.tierGoldSpeedScale();',
        'tier-feel-tier-off-by-one'));
    } },

  // ---- check-reel-stall.js 第③节（距离拉锯：力竭的鱼必须收得回来，用户 2026-10-06）----
  { dir: 'reelstall-ok', checker: 'check-reel-stall.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'reelstall-no-floor', checker: 'check-reel-stall.js', expect: 1,
    desc: '删掉力竭期收线保底（力竭了、轮子又差，线照样往外走）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        '            if (this.fatigued) {\n'
        + '                distDelta = Math.min(distDelta, -Tuning.fatiguedReelMinGain());\n'
        + '            }\n',
        '', 'reelstall-no-floor'));
    } },

  { dir: 'reelstall-sprint-tired', checker: 'check-reel-stall.js', expect: 1,
    desc: '力竭的鱼又能冲刺了（冲刺期间疲劳不能续期，"力竭窗口"被切碎）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'if (this.fishStruggling && !this.fatigued) {',
        'if (this.fishStruggling) {', 'reelstall-sprint-tired'));
    } },

  // ---- check-tier-feel.js ⑤（红档体力基准下限，用户 2026-10-06 定案）----
  { dir: 'tierstam-floor-off', checker: 'check-tier-feel.js', expect: 1,
    desc: '红档体力下限失效（redStaminaFloor 对所有档位都原样返回）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        'return Math.max(baseStamina, Tuning.tierRedStaminaMin());',
        'return baseStamina;', 'tierstam-floor-off'));
    } },

  { dir: 'tierstam-floor-unwired', checker: 'check-tier-feel.js', expect: 1,
    desc: '体力池不再走 redStaminaFloor（下限写了但没接上）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'double baseStamina = FishSpecies.redStaminaFloor(hookedTier, rawStamina);',
        'double baseStamina = rawStamina;', 'tierstam-floor-unwired'));
    } },

  // ---- check-reel-stall.js 第④节（力竭期张力：涨速 ×0.85、回复 ×1.15，用户 2026-10-06）----
  { dir: 'fatetension-gain-off', checker: 'check-reel-stall.js', expect: 1,
    desc: '力竭期涨速不再 ×0.85（疲劳窗口又不安全了）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'gain *= fatigueGainScale;', '', 'fatetension-gain-off'));
    } },

  { dir: 'fatetension-rec-off', checker: 'check-reel-stall.js', expect: 1,
    desc: '力竭期回复不再 ×1.15（落得和非力竭一样慢）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'recoverMult *= Tuning.fatiguedTensionRecoverScale();', '', 'fatetension-rec-off'));
    } },

  { dir: 'fatethreshold-noscale', checker: 'check-reel-stall.js', expect: 1,
    desc: '进疲劳的判定不再走全局阈值倍率（旋钮写了但没接上）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'this.fishStamina <= this.fishStaminaMax * fatigueThresholdNow()',
        'this.fishStamina <= this.fishStaminaMax * this.profile.fatigueThreshold()',
        'fatethreshold-noscale'));
    } },

  { dir: 'idlefall-noguarantee', checker: 'check-reel-stall.js', expect: 1,
    desc: '中立不再有最小净回落（条子又能慢到 1 点/秒）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'if (!this.releasing) {\n                net = Math.min(net, -Tuning.idleFallFloor());\n            }\n',
        '', 'idlefall-noguarantee'));
    } },

  // ---- check-reel-floor-drain.js（保底体力削减，用户 2026-10-06 定案）----
  { dir: 'floordrain-ok', checker: 'check-reel-floor-drain.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'floordrain-unwired', checker: 'check-reel-floor-drain.js', expect: 1,
    desc: '扣力时不取保底（保底被单轮上限吃掉 = 用户最不想要的那种）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'allowed = Math.max(allowed, this.rodStats.floorDrainPerSec() / 20.0);',
        '', 'floordrain-unwired'));
    } },

  { dir: 'floordrain-inline-formula', checker: 'check-reel-floor-drain.js', expect: 1,
    desc: 'tooltip 里把公式重写一遍（damping_bonus 那次"写死三个数"的老路）',
    mutate: (at) => {
      at.set('TacklePartItem.java', mustReplace(at.get('TacklePartItem.java'),
        'com.whitecobalt.anglertackle.fishing.PartComposer.floorDrainPerSec(stats)',
        '(stats.damping() - 1.0) * 10.0 + 5.0', 'floordrain-inline-formula'));
    } },

  { dir: 'floordrain-gold-drift', checker: 'check-reel-floor-drain.js', expect: 1,
    desc: '金轮阻尼被改小（保底贴不住 20 了，倒推的依据失效）',
    mutate: (at) => {
      at.set('ModItems.java', mustReplace(at.get('ModItems.java'),
        'PartStats.reel(1.64, 1.64, 2.30, 2.50, 5)', 'PartStats.reel(1.64, 1.64, 2.30, 1.80, 5)',
        'floordrain-gold-drift'));
    } },

  // ---- check-tuning-schema.js（调参器的中文说明，用户 2026-10-06 报"有几项没有说明"）----
  { dir: 'tuningschema-nocomment', checker: 'check-tuning-schema.js', expect: 1,
    desc: '某一项的中文说明被吃掉（调参器那一行只剩数字）',
    mutate: (at) => {
      const rel = 'tools/tuning-schema.json';
      const src = at.get(rel);
      if (!/档位游速底数/.test(src)) {
        throw new Error('负例构造失败「tuningschema-nocomment」：schema 里找不到锚点');
      }
      at.set(rel, src.replace(/("comment"\s*:\s*")[^"]*档位游速底数[^"]*(")/, '$1$2'));
    } },

  // ---- check-reel-stall.js 第⑥节（保底挣扎，用户 2026-10-06 定案）----
  { dir: 'floorstruggle-ungated', checker: 'check-reel-stall.js', expect: 1,
    desc: '保底窗口内又能点亮疲劳（红金鱼一轮软到底，用户要修的就是这个）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'this.freshStruggleTicks <= 0\n                && this.fatigueLockoutTicks <= 0\n                && !this.sprinting',
        '!this.sprinting', 'floorstruggle-ungated'));
    } },

  { dir: 'floorstruggle-notick', checker: 'check-reel-stall.js', expect: 1,
    desc: '保底倒计时不再递减（等于永久无敌，鱼再也不会力竭）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'if (this.freshStruggleTicks > 0) {\n            this.freshStruggleTicks--;\n        }\n',
        '', 'floorstruggle-notick'));
    } },

  // ---- check-reel-stall.js 第⑦节（收线速率抵抗，用户 2026-10-06）----
  { dir: 'reelresist-unwired', checker: 'check-reel-stall.js', expect: 1,
    desc: '收线速率不再过抵抗（高双拉配置对高档鱼毫无代价）',
    mutate: (at) => {
      at.set('FishingSession.java', mustReplace(at.get('FishingSession.java'),
        'double pullResisted = FishSpecies.resistReelSpeed(pullBase, reelResistNow);',
        'double pullResisted = pullBase;', 'reelresist-unwired'));
    } },

  { dir: 'reelresist-jump', checker: 'check-reel-stall.js', expect: 1,
    desc: '抵抗改成"整条打折"（阈值处跳变：1.21 比 1.19 还慢）',
    mutate: (at) => {
      at.set('FishSpecies.java', mustReplace(at.get('FishSpecies.java'),
        '        if (pullSpeed <= threshold) {\n            return pullSpeed;\n        }\n'
        + '        return threshold + (pullSpeed - threshold) * Math.max(0.0, 1.0 - resist);',
        '        return pullSpeed * Math.max(0.0, 1.0 - resist);', 'reelresist-jump'));
    } },

  // ---- check-quest.js（老柯的每日/周常委托；用户 2026-10-14 定案，策划 docs/40）----
  { dir: 'quest-ok', checker: 'check-quest.js', expect: 0,
    desc: '未改动的副本（对照组，应当通过）', mutate: () => {} },

  { dir: 'quest-pool-shrink', checker: 'check-quest.js', expect: 1,
    desc: '每日池被砍到 7 条（低于用户要求的"种类数不低于 8"，抽出来的 5 条会高度重复）',
    mutate: (at) => {
      const F = 'QuestPool.java';
      at.set(F, mustReplace(at.get(F),
        '            new QuestDef("daily_species_crucian", QuestDef.Period.DAILY, QuestDef.Goal.GET_SPECIES,\n'
        + '                    List.of("crucian_carp", "1"), 35L, "", 0, 1, 8),\n'
        + '            new QuestDef("daily_species_tilapia", QuestDef.Period.DAILY, QuestDef.Goal.GET_SPECIES,\n'
        + '                    List.of("tilapia", "1"), 35L, "", 0, 1, 8),\n'
        + '            new QuestDef("daily_species_mullet", QuestDef.Period.DAILY, QuestDef.Goal.GET_SPECIES,\n'
        + '                    List.of("mullet", "1"), 35L, "", 0, 1, 8),\n'
        + '            new QuestDef("daily_weight_sum", QuestDef.Period.DAILY, QuestDef.Goal.GET_WEIGHT_SUM,\n'
        + '                    List.of("3.0"), 40L, "", 0, 1, 8),\n'
        + '            new QuestDef("daily_biggest", QuestDef.Period.DAILY, QuestDef.Goal.GET_BIGGEST,\n'
        + '                    List.of("1.5"), 40L, "", 0, 1, 8),\n'
        + '            new QuestDef("daily_longest", QuestDef.Period.DAILY, QuestDef.Goal.GET_LONGEST,\n'
        + '                    List.of("45"), 35L, "", 0, 1, 8),\n',
        '            // （负例：每日池被砍到 7 条）\n',
        'quest-pool-shrink'));
    } },

  { dir: 'quest-reward-both', checker: 'check-quest.js', expect: 1,
    desc: '一条委托同时给鱼币和物品（违背用户"给饵料就别给鱼币"，产出会失控）',
    mutate: (at) => {
      const F = 'QuestPool.java';
      at.set(F, mustReplace(at.get(F), '30L, "", 0, 1, 10),', '30L, "salt", 1, 1, 10),', 'quest-reward-both'));
    } },

  { dir: 'quest-weekly-red', checker: 'check-quest.js', expect: 1,
    desc: '周常要求红档（用户明令禁止：玩家等级很可能连钻石竿都没有）',
    mutate: (at) => {
      const F = 'QuestPool.java';
      at.set(F, mustReplace(at.get(F), 'List.of("4", "3"), 260L', 'List.of("5", "3"), 260L', 'quest-weekly-red'));
    } },

  { dir: 'quest-fieldof', checker: 'check-quest.js', expect: 1,
    desc: 'QuestData 的 Codec 从 optionalFieldOf 改成 fieldOf（老存档整份委托数据读不进来）',
    mutate: (at) => {
      const F = 'QuestData.java';
      at.set(F, mustReplace(at.get(F),
        'Codec.LONG.optionalFieldOf("last_daily_day", 0L).forGetter(QuestData::lastDailyDay),',
        'Codec.LONG.fieldOf("last_daily_day").forGetter(QuestData::lastDailyDay),',
        'quest-fieldof'));
    } },

  { dir: 'quest-daytime', checker: 'check-quest.js', expect: 1,
    desc: '日界改用 getGameTime（纯 tick 计数 ⇒ 会在半夜刷委托，用户点名的"乱套"）',
    mutate: (at) => {
      at.set('QuestService.java', mustReplace(at.get('QuestService.java'),
        'Math.floorDiv(level.getDayTime(), Environment.DAY_LENGTH)',
        'Math.floorDiv(level.getGameTime(), Environment.DAY_LENGTH)', 'quest-daytime'));
    } },

  { dir: 'quest-accept-unlisted', checker: 'check-quest.js', expect: 1,
    desc: 'quest_accept 掉出邮件动作白名单（玩家点【接下委托】被静默忽略）',
    mutate: (at) => {
      at.set('MailActions.java', mustReplace(at.get('MailActions.java'),
        'Set.of(LEAGUE_JOIN, QUEST_ACCEPT)', 'Set.of(LEAGUE_JOIN)', 'quest-accept-unlisted'));
    } },

  { dir: 'quest-lang-missing', checker: 'check-quest.js', expect: 1,
    desc: 'zh_cn 少一个委托语言键（中文客户端里会显示成一串键名）',
    mutate: (at) => {
      at.set('zh_cn.json', mustReplace(at.get('zh_cn.json'),
        '  "mail.anglertackle.quest_daily.title": "老柯的每日委托",\n', '', 'quest-lang-missing'));
    } },

  // ---- Q2 / Q3（2026-10-14 第二轮：钩接线 → 判定 → 结算 → 活跃度）----
  { dir: 'quest-oncatch-hook', checker: 'check-quest.js', expect: 1,
    desc: 'LeaderboardHooks 里的 QuestService.onCatch 接线被删（钓再多鱼也不推进委托）',
    mutate: (at) => {
      const F = 'LeaderboardHooks.java';
      at.set(F, mustReplace(at.get(F),
        '                com.whitecobalt.anglertackle.quest.QuestService.onCatch(\n'
        + '                        player, c.fishId(), c.weightKg(), c.lengthCm(), c.tier());\n',
        '                // （负例：接线删掉）\n', 'quest-oncatch-hook'));
    } },

  { dir: 'quest-oncatch-notry', checker: 'check-quest.js', expect: 1,
    desc: 'onCatch 接线没包在自己的 try 里（委托抛异常会让上鱼失败 —— 玩法被拖下水）',
    mutate: (at) => {
      const F = 'LeaderboardHooks.java';
      at.set(F, mustReplace(at.get(F),
        '            try {\n                com.whitecobalt.anglertackle.quest.QuestService.onCatch(',
        '            if (true) {\n                com.whitecobalt.anglertackle.quest.QuestService.onCatch(',
        'quest-oncatch-notry'));
    } },

  { dir: 'quest-activity-tier', checker: 'check-quest.js', expect: 1,
    desc: '活跃度四档被改成 25/50/75/99（用户点名的是 25,50,75,100）',
    mutate: (at) => {
      // 2026-10-15 修锚点：档位表已从 QuestService.TIER_AT 抽到 QuestTiers.AT（上一个人改了实现、
      // 没顺手改这条负例 ⇒ 锚点预检报「quest-activity-tier 锚点没匹配上」，整条负例阶段停摆）。
      // 锚点取"整行声明"，因为这一行正是这条例要证伪的东西，下一次改档位的人一定会看到它。
      at.set('QuestTiers.java', mustReplace(at.get('QuestTiers.java'),
        'public static final int[] AT = {25, 50, 75, 100};',
        'public static final int[] AT = {25, 50, 75, 99};', 'quest-activity-tier'));
    } },

  { dir: 'quest-activity-hardcode', checker: 'check-quest.js', expect: 1,
    desc: '活跃度增益写成硬编码 3（配置项名存实亡：玩家改配置不生效）',
    mutate: (at) => {
      at.set('QuestService.java', mustReplace(at.get('QuestService.java'),
        '? Tuning.questActivityPerWeekly() : Tuning.questActivityPerDaily()', '? 3 : 3',
        'quest-activity-hardcode'));
    } },

  { dir: 'quest-collected-fieldof', checker: 'check-quest.js', expect: 1,
    desc: 'Row.collected 从 optionalFieldOf 改成 fieldOf（加字段前的老存档进度行读不进来）',
    mutate: (at) => {
      at.set('QuestData.java', mustReplace(at.get('QuestData.java'),
        'Codec.STRING.optionalFieldOf("collected", "").forGetter(Row::collected)',
        'Codec.STRING.fieldOf("collected").forGetter(Row::collected)', 'quest-collected-fieldof'));
    } },

  { dir: 'quest-questname-key', checker: 'check-quest.js', expect: 1,
    desc: '某条委托缺英文名（英文客户端在面板/奖励信里看到一串 key）',
    mutate: (at) => {
      at.set('en_us.json', mustReplace(at.get('en_us.json'),
        '  "quest.anglertackle.weekly_any_25.name": "Catch 25 fish",\n', '', 'quest-questname-key'));
    } },

  { dir: 'quest-goal-not-pure', checker: 'check-quest.js', expect: 1,
    desc: 'QuestGoal 里引用了服务端类型（判定不再纯 ⇒ 脱离服务端测不了、也容易顺手改数据）',
    mutate: (at) => {
      at.set('QuestGoal.java', mustReplace(at.get('QuestGoal.java'),
        'private static final int WEIGHT_SCALE = 100;',
        'private static final PlayerData WEIGHT_SCALE = null;', 'quest-goal-not-pure'));
    } },

  // ---- Q4（面板 + 三个入口）----
  { dir: 'quest-panel-writeback', checker: 'check-quest.js', expect: 1,
    desc: '任务面板里偷偷加了回写（C→S 包）—— 面板是只读的：接取在邮件、领取在邮箱',
    mutate: (at) => {
      const F = 'QuestScreen.java';
      at.set(F, mustReplace(at.get(F),
        '    // ------------------------------------------------------------------ 输入',
        '    // （负例）回写\n'
        + '    void hack() { net.neoforged.neoforge.network.PacketDistributor.sendToServer(null); }\n\n'
        + '    // ------------------------------------------------------------------ 输入', 'quest-panel-writeback'));
    } },

  { dir: 'quest-panel-superrender', checker: 'check-quest.js', expect: 1,
    desc: '面板调了 super.render(...) ⇒ 会再吃一次 renderBackground，界面发灰（用户环境里一眼看出）',
    mutate: (at) => {
      const F = 'QuestScreen.java';
      at.set(F, mustReplace(at.get(F),
        '        for (net.minecraft.client.gui.components.Renderable r : this.renderables) {',
        '        super.render(g, mouseX, mouseY, partialTick);\n'
        + '        for (net.minecraft.client.gui.components.Renderable r : this.renderables) {',
        'quest-panel-superrender'));
    } },

  { dir: 'quest-mail-entry', checker: 'check-quest.js', expect: 1,
    desc: '邮件【接下委托】之后不再打开面板（玩家接完不知道去哪看进度）',
    mutate: (at) => {
      at.set('MailActions.java', mustReplace(at.get('MailActions.java'),
        '                com.whitecobalt.anglertackle.network.ModNetwork.syncQuests(player, "");\n',
        '                // （负例）入口断\n', 'quest-mail-entry'));
    } },

  { dir: 'quest-npc-shop-gone', checker: 'check-quest.js', expect: 1,
    desc: '老柯的普通右键不再开商店 —— 那是用户定案过的行为，面板只该走潜行那一路',
    mutate: (at) => {
      at.set('FishermanEntity.java', mustReplace(at.get('FishermanEntity.java'),
        'com.whitecobalt.anglertackle.shop.ShopManager.open(sp2);',
        '// （负例）商店入口没了', 'quest-npc-shop-gone'));
    } },

  // ---- 2026-10-14 验收实测修复：委托信的【接下委托】按钮 ----
  // quest_accept 是**没有附件**的动作信。按钮要能出现且点得动，需要四处同时成立：
  //   寄信挂动作（QuestService）×2 / 登录升级旧信 / claimed 初始值 / claim 无附件分支跑动作 /
  //   界面 canClaim 放行 / withAction 打回 claimed=false。
  //   四处各配一条变异，全部实测判红才算数（A1：永远绿的检查器比没有检查器更坏）。
  { dir: 'quest-daily-no-action', checker: 'check-quest.js', expect: 1,
    desc: '每日委托信寄出时不带 quest_accept 动作 —— 邮件里根本没有【接下委托】按钮（2026-10-14 验收实测真 bug）',
    mutate: (at) => {
      at.set('QuestService.java', mustReplace(at.get('QuestService.java'),
        '        MailService.Result r = MailService.deliver(player, MAIL_DAILY,\n'
        + '                List.of(String.valueOf(count)), 0L, List.of(), MailActions.QUEST_ACCEPT);',
        '        MailService.Result r = MailService.deliver(player, MAIL_DAILY,\n'
        + '                List.of(String.valueOf(count)), 0L, List.of());', 'quest-daily-no-action'));
    } },
  { dir: 'quest-weekly-no-action', checker: 'check-quest.js', expect: 1,
    desc: '周常委托信寄出时不带 quest_accept 动作 —— 同上，按钮没了',
    mutate: (at) => {
      at.set('QuestService.java', mustReplace(at.get('QuestService.java'),
        '        MailService.Result r = MailService.deliver(player, MAIL_WEEKLY,\n'
        + '                List.of(String.valueOf(count)), 0L, List.of(), MailActions.QUEST_ACCEPT);',
        '        MailService.Result r = MailService.deliver(player, MAIL_WEEKLY,\n'
        + '                List.of(String.valueOf(count)), 0L, List.of());', 'quest-weekly-no-action'));
    } },
  { dir: 'quest-no-legacy-upgrade', checker: 'check-quest.js', expect: 1,
    desc: '登录时不升级 1.6.2 旧委托信 —— 老信永远没有按钮，玩家要等下次刷新才能接',
    mutate: (at) => {
      at.set('QuestService.java', mustReplace(at.get('QuestService.java'),
        '        upgradeLegacyLetters(player);',
        '        // （负例）不升级旧信', 'quest-no-legacy-upgrade'));
    } },
  { dir: 'mail-claimed-init-noaction', checker: 'check-mail.js', expect: 1,
    desc: 'send() 的 claimed 初始值没算上 action —— 带动作的信一寄出来就是"已办完"，按钮恒灰',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '                !(hasAttachment || hasAction),',
        '                !hasAttachment,', 'mail-claimed-init-noaction'));
    } },
  { dir: 'mail-claim-noattachment-action', checker: 'check-mail.js', expect: 1,
    desc: 'claim() 的无附件分支不执行动作 —— quest_accept 跑不起来，按钮就是个摆设',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/mail/MailService.java';
      at.set(F, mustReplace(at.get(F),
        '            if (m.hasAction()) {\n'
        + '                MailActions.run(player, m.action());\n'
        + '            }\n'
        + '            return Claim.NOTHING;',
        '            if (m.hasAction()) { }\n'
        + '            return Claim.NOTHING;', 'mail-claim-noattachment-action'));
    } },
  { dir: 'mail-canclaim-no-action', checker: 'check-mail.js', expect: 1,
    desc: 'canClaim() 不放行"只有动作"的信 —— 委托按钮恒灰，玩家点了没反应（验收实测症状）',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/client/screen/MailScreen.java';
      at.set(F, mustReplace(at.get(F),
        '            return !expired && !claimed && (coins > 0 || !items.isEmpty() || !action.isEmpty());',
        '            return !expired && !claimed && (coins > 0 || !items.isEmpty());',
        'mail-canclaim-no-action'));
    } },
  { dir: 'mail-withaction-claimed', checker: 'check-mail.js', expect: 1,
    desc: 'withAction() 不把 claimed 打回 false —— 升级完按钮还是灰的',
    mutate: (at) => {
      const F = 'src/main/java/com/whitecobalt/anglertackle/player/MailEntry.java';
      at.set(F, mustReplace(at.get(F),
        '                : new MailEntry(id, template, args, sentAt, read, false, coins, items, action);',
        '                : new MailEntry(id, template, args, sentAt, read, true, coins, items, action);',
        'mail-withaction-claimed'));
    } },
];

// ⚠⚠ 清空副本目录**不能只靠一次 rmSync(recursive)** —— 2026-10-11 它把负例阶段拖垮了好几次：
//   ```
//   Error: ENOTEMPTY, Directory not empty: \\?\...\build\negtest
//       at Object.<anonymous> (tools/_negtest.js:3955)
//   造副本失败
//   ```
//   ⚠ 而它的**症状极具误导性**：闸里看到的是"[负向测试未通过：存在漏检的检查脚本]" ✗
//     —— 看起来像"某个检查器抓不到变异" ✗，实际连跑都没跑起来 ✗。
//   当天我先后错怪了三件事（都不对）：① 有进程占着目录（查了：0 个 node 进程 ✗）
//     ② 路径超过 MAX_PATH（量了：最深 160 字符 ✗）③ 与另一个 negtest 并发（单独跑一次照样炸 ✗）。
//   **真凶**：Windows 上 `rmSync(recursive)` 删这种"层层嵌套 + 上千文件"的目录时，
//     会因为目录句柄/杀毒扫描之类的原因报 ENOTEMPTY，而**逐项删就没问题**（手工验证过 ✓）。
//   ⇒ 所以改成：**先逐项删子项（带重试），再删目录本身**；真删不掉就**改名让路**（不能让整条负例阶段停摆）。
// ============================================================================
//  复制策略：**硬链接 + 写时断开**（2026-10-12）
//
//  为什么改：一次闸里负例阶段 ~470 秒，其中绝大部分是 **530 棵树 × ~150 个文件**
//  的"复制 + 全量读进内存 + 全量写回"（约 24 万次文件操作）。而每个用例真正改动的
//  文件通常只有 1~2 个 —— 也就是说 99% 的读写是白做的。
//
//  新做法（**验证强度一点没降**：每棵树的内容与改动前逐字节相同）：
//    · 未改动的文件用 `linkSync` 建**硬链接**（同一卷上几乎零成本，不复制数据）；
//    · 只有用例真正 `at.set` 过的文件才**先断开链接再写**（见 writeInCopy）；
//    · 内容**按需读**（用例 `at.get` 到哪个文件才读哪个）。
//
//  ⚠⚠ 硬链接与真源码**共享同一个 inode**：直接 writeFileSync 会**穿过链接改掉真源码**，
//  而且没有任何报错 —— 这是整条负例链上最贵的错误（真仓库被负例改坏，却只表现为
//  "某个检查器莫名其妙变了行为"）。所以：
//    1. 副本里的**每一次写入都必须走 writeInCopy()**（先 rmSync 断开链接，再写）；
//    2. 建副本**前后各记一次真源码的指纹**，对不上就当场报错退出（见 assertSourceIntact）。
//  守着这两条的是 tools/check-negtest-runner.js 与负例 negrun-link-write-through。
// ============================================================================
let linkCount = 0;
let copyCount = 0;

/** 硬链接优先，失败（跨卷/权限/文件系统不支持）就退回真拷贝 —— 两者内容一致 ✓。 */
function linkOrCopy(src, dst) {
  try {
    fs.linkSync(src, dst);
    linkCount++;
    return 'link';
  } catch (e) {
    fs.copyFileSync(src, dst);
    copyCount++;
    return 'copy';
  }
}

/**
 * 往副本里写一个文件：**先断开硬链接**再写。
 *
 * <p>⚠ 顺序反了就会改到真源码（见上面那段说明）。`rmSync` 删掉的只是链接本身，
 * 真文件不受影响 ✓；随后写出来的是副本自己的新文件 ✓。
 */
function writeInCopy(dst, data) {
  // ⚠ 无条件的第一句（不套 if (fs.existsSync(...))）：force: true 本来就容忍"不存在"，
  //   而套一层条件等于给"以后手滑改成 if (false)"留了门 —— 那样断链静默失效、
  //   真源码当场被写坏。守着这条的是 tools/check-negtest-runner.js（断言它是函数体第一条语句）。
  fs.rmSync(dst, { force: true });            // 断开硬链接（真源码那一份不动）
  // ★ 2026-10-13 新增：**两道 fail-closed 自检**（放在断链之后，不影响上面那条"第一条语句"的断言）。
  //
  //   为什么加：1.5.100 那一轮**真的把真源码改坏过**（`shop/ShopManager.java`，闸的指纹复核抓到），
  //   而事后排查：4 个调用点传的**都是副本路径**、`fs.rmSync` 也**确实会断开硬链接**
  //   （已用最小实验证过：建硬链接 → rmSync → 写，真文件**没变**）。也就是说，
  //   **真因没能从现有证据唯一确定**（那一轮恰好是磁盘写满的一轮，最可能是环境性因素）。
  //   ⇒ 与其猜，不如把它变成"**再也不可能静默发生**"：
  //     ① 目标必须在 build/negtest 之下 ⇒ 挡住"哪天有人传了真路径"（`pike.png` 事故的第二种形态）；
  //     ② 断链之后目标**必须已不存在** ⇒ 挡住"断链以任何原因失效"。
  //   任一不成立就**抛错停下**（fail closed）—— 宁可不跑，也不能悄悄改坏真仓库。
  if (!/(^|[\\/])negtest[\\/]/.test(dst)) {
    throw new Error('writeInCopy 的目标不在 build/negtest 之下：' + dst
      + '（这条自检是防"把真路径传进来"—— 真出手就是整个仓库被改坏）');
  }
  if (fs.existsSync(dst)) {
    throw new Error('writeInCopy 断链失败：' + dst + ' 仍然存在'
      + '（这样写下去会穿透硬链接、改坏真源码 —— 停下来，别写）');
  }
  mkdirsOnce(path.dirname(dst));
  fs.writeFileSync(dst, data);
}

/**
 * 真源码指纹（用来证明"负例没有把仓库改坏"）。
 *
 * <p>⚠⚠ 名单**必须覆盖"建副本会碰到的一切"**，不能只看 {@link FILES}：
 *   2026-10-12 第一版只指纹了 FILES，而 `model-texture-blank` 那类负例改的是
 *   `EXTRA.fish` 里的贴图 —— 硬链接被写穿、真 `pike.png` 被涂成全透明（328→87 字节），
 *   指纹复核**一声没响**（那个文件根本不在它的名单里）✗。
 *   暴露它的是 `check-fish-model.js` 的**对照组**：model-ok 期望通过却红了 ✓。
 *   ⇒ 名单 = FILES ∪ 所有 EXTRA 组展开后的文件 ∪ BIN_FILES（去重）。
 */
function sourceFingerprint() {
  const crypto = require('crypto');
  const all = new Set(FILES);
  for (const pat of Object.values(EXTRA).flat()) {
    for (const rel of expandExtraCached(pat)) all.add(rel);
  }
  for (const rel of BIN_FILES) all.add(rel);
  const out = new Map();
  for (const rel of all) {
    const p = path.join(ROOT, rel);
    if (!fs.existsSync(p)) continue;
    try {
      out.set(rel, crypto.createHash('sha1').update(fs.readFileSync(p)).digest('hex'));
    } catch (e) {
      out.set(rel, 'unreadable');
    }
  }
  return out;
}

/** 建完副本后复核：真源码必须**一个字节都没变**。 */
function assertSourceIntact(before) {
  const after = sourceFingerprint();
  const broken = [];
  for (const [rel, h] of before) {
    if (after.get(rel) !== h) broken.push(rel);
  }
  if (broken.length) {
    console.error('\n❌❌ 负例把真源码改坏了（硬链接被穿透写入）：' + broken.slice(0, 5).join('、')
      + (broken.length > 5 ? ' 等 ' + broken.length + ' 个' : ''));
    console.error('   这一定是"没走 writeInCopy() 就写文件"造成的 —— 修 _negtest.js，'
      + '并跑 git/快照恢复这些文件。**不许忽略这条**：真仓库已经被改坏了。');
    process.exit(1);
  }
  console.log('  ✅ 真源码指纹复核通过（' + before.size + ' 个文件一个字节都没变；'
    + '硬链接 ' + linkCount + ' 个 / 真拷贝 ' + copyCount + ' 个）');
}

// ============================================================================
//  建副本的**并发**（2026-10-12 第二次提速）
//
//  先把"每个文件一次文件系统操作"这条成本压下去（硬链接 + 写时断开 + 按需读 +
//  目录/通配缓存）：~470 秒 → 298 秒。剩下的 298 秒是 531 棵树 × ~275 个文件的
//  **纯文件系统开销**（NTFS 元数据 + 杀毒逐个扫描），单进程压不动了。
//
//  所以把**建副本**也切成 N 份并发（与断言阶段 `_negrun.js` 同一个思路）：
//    · `node tools/_negtest.js`              父进程：清目录 → 起 N 个子进程 → 合并清单
//    · `node tools/_negtest.js --shard k/N`  子进程：只建第 k 份，写自己的清单分片
//
//  ⚠ 并发边界（**与 AGENTS.md §三 那条不变式不冲突**）：
//    · 清目录只在父进程做一次；每个用例一棵独立目录，子进程之间**互不写**；
//    · **仍然禁止**"两个 negtest 进程同时跑"（那才会互相清目录）✗。
//  ⚠ 为什么用"各自写清单分片 + 父进程合并"而不是 IPC：沙箱禁止命名管道，
//    而 `fork` 的 IPC 通道就是管道 ⇒ EPERM。子进程只用 `stdio: 'inherit'`（允许），
//    结果通过**文件**汇总 ✓。
// ============================================================================
const argvAll = process.argv.join(' ');
let shardIndex = 0;
let shardCount = 1;
const shardMatch = /--shard[= ](\d+)\/(\d+)/.exec(argvAll);
if (shardMatch) {
  shardIndex = Number(shardMatch[1]);
  shardCount = Number(shardMatch[2]);
}
const IS_SHARD = shardCount > 1;
/** 建副本的并发路数（可用 `--jobs N` 覆盖；1 = 老的单进程行为）。 */
const BUILD_JOBS = (() => {
  const m = /--jobs[= ](\d+)/.exec(argvAll);
  return m ? Math.max(1, Number(m[1])) : 4;
})();

// ============================================================================
//  `--check-anchors`：**两秒钟的预检**（2026-10-12 加）
//
//  为什么需要它：负例的锚点是"源码里的一段原文"，而**改文案/改实现就会把锚点改没**。
//  锚点没了的表现极其误导 —— 造副本阶段抛一句 `负例构造失败「x」`，
//  而闸最终报的是 **[负向测试未通过：存在漏检的检查脚本]**（看起来像"检查器抓不到变异"，
//  其实连跑都没跑起来），而且要等三四分钟才看得到 ✗。
//  今天为此栽了三次（`"send");`、`**签收那一刻起…**`、以及改写邮件短句那次）。
//
//  做法：把每个用例的 mutate 在**内存里**跑一遍 —— 访问器直接读真源码、
//  写入只丢进内存 Map（**一个字节都不落盘**），任何 mustReplace 匹配不上就立刻报错退出 ✓。
//  它不建副本、不跑检查器，所以只要 1~2 秒。
//
//  ⚠ 它被调**两次**，这是故意的：
//    · `tools/negtest.ps1` 显式跑一次（`--check-anchors`，闸里标成 0/2 步），
//      为的是"建副本之前"就先指名道姓地报出来；
//    · **建副本自己开头也跑一次** —— 因为 `node tools\_negtest.js` 是可以被直接跑的
//      （AGENTS.md 里就是这么写的），只接在 ps1 上的话那条路会绕过预检，
//      又退回"造副本失败 + 闸报「存在漏检的检查脚本」"那种误导现场 ✗。
//      多花 1 秒，换"无论从哪个入口进来都不可能绕过" ✓
// ============================================================================
function runAnchorPreflight() {
  const t0 = Date.now();
  const failures = [];
  // ⚠ 键集合必须**逐字复刻**建副本时的 `files` 对象（见 makeAccessor 的调用点）：
  //   · 只有 FILES 里的路径会登记（EXTRA 只拷不登记 ⇒ at.get 一个 EXTRA 路径在真跑时也会抛"找不到"）；
  //   · 不存在的文件不登记（`if (!fs.existsSync(src)) continue;`）；
  //   · 对象键天然**去重** —— FILES 里同一路径列了多次是允许的（用数组就会报"有歧义 ✗"）。
  //   第一版预检就是漏了最后一条：388 条用例全被误报成"文件名有歧义"（同一路径重复出现）；
  //   守着它的是负例 `negrun-anchor-no-dedupe`（它把这一条 continue 删掉）。
  const anchorKeys = (() => {
    const seen = new Set();
    const out = [];
    for (const rel of FILES) {
      if (seen.has(rel)) continue;
      seen.add(rel);
      if (fs.existsSync(path.join(ROOT, rel))) out.push(rel);
    }
    return out;
  })();
  for (const c of CASES) {
    const files = new Map();
    const findKey = (name) => {
      // 路径解析与建副本时同一条规则：支持"完整相对路径"与"唯一文件名"
      const hits = anchorKeys.filter((k) => k === name || k.endsWith('/' + name));
      if (hits.length === 0) throw new Error('找不到文件 ' + name);
      if (hits.length > 1) throw new Error('文件名 ' + name + ' 有歧义：' + hits.join(' | '));
      return hits[0];
    };
    try {
      (c.mutate || (() => {}))({
        get: (name) => {
          const rel = findKey(name);
          if (!files.has(rel)) files.set(rel, fs.readFileSync(path.join(ROOT, rel), 'utf8'));
          return files.get(rel);
        },
        set: (name, text) => { files.set(findKey(name), text); },
        has: (name) => anchorKeys.some((k) => k === name || k.endsWith('/' + name)),
      });
    } catch (err) {
      failures.push(c.dir + '  ←  ' + err.message);
    }
  }
  return { failures, ms: Date.now() - t0 };
}

/** 预检失败就打印清单并以退出码 1 结束（**不许静默放过**）。 */
function anchorPreflightOrDie(label) {
  const { failures, ms } = runAnchorPreflight();
  if (!failures.length) return ms;
  console.error('❌ 负例锚点预检未通过（' + failures.length + '/' + CASES.length + ' 条'
    + (label ? '，' + label : '') + '）：');
  for (const f of failures) console.error('   ' + f);
  console.error('   ⇒ 改文案/改实现时**顺手改这些锚点**：锚点要挑"下一个人不会顺手改掉"的写法。');
  process.exit(1);
}

if (/--check-anchors/.test(argvAll) && !IS_SHARD) {
  const ms = anchorPreflightOrDie('');
  console.log('✅ 负例锚点预检通过（' + CASES.length + ' 条，'
    + Math.round(ms / 1000) + ' 秒，未写任何文件）');
  process.exit(0);
}

// ---- 清目录也**并发**：`--purge k/N` 只删第 k 片（顶层条目按序号取模）----
//  为什么值得：531 棵树 ≈ 14.6 万个硬链接，逐个 unlink 在 NTFS 上要 ~70 秒 ——
//  和"建"一样是纯文件系统开销，切成 4 片就只剩 ~20 秒 ✓。
//  清目录**只在父进程调度**：子进程只删自己那一片的顶层条目，绝不碰别的东西 ✓。
const purgeMatch = /--purge[= ](\d+)\/(\d+)/.exec(argvAll);
if (purgeMatch) {
  const pi = Number(purgeMatch[1]);
  const pn = Number(purgeMatch[2]);
  if (!fs.existsSync(OUT)) process.exit(0);
  const entries = fs.readdirSync(OUT);
  for (let i = 0; i < entries.length; i++) {
    if ((i % pn) !== pi) continue;
    purgeEntry(path.join(OUT, entries[i]));
  }
  process.exit(0);
}

// ---- 建副本之前**再跑一次**锚点预检 ----
//  为什么明知 ps1 刚跑过还要再来一遍：`node tools\_negtest.js` 是**可以直接跑的**
//  （AGENTS.md 里就这么写），只接在 ps1 上的话那条入口会绕过预检，锚点失配又变成
//  "造副本失败 + 闸报「存在漏检的检查脚本」"。多花 1 秒换"从任何入口都绕不过去" ✓
//  分片子进程不跑（父进程已经跑过；子进程只负责自己那一片的建树）。
if (!IS_SHARD) {
  const ms = anchorPreflightOrDie('建副本前的兜底预检');
  console.log('   锚点预检已通过（' + Math.round(ms / 1000) + ' 秒，未写任何文件）');
}

/**
 * 删**一个顶层条目**（一个副本目录，或上一轮留下的 -stale-* / 分片清单）。
 *
 * <p>从 {@link purgeDir} 里抽出来，好让并发清目录的子进程按片调用同一个实现
 * （实现只有一份，串行/并发两条路的"删不掉怎么办"完全一致 ✓）。
 */
function purgeEntry(p) {
  const sleep = (ms) => { try { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); } catch (e) { /* 忽略 */ } };
  for (let round = 0; round < 3; round++) {
    try {
      fs.rmSync(p, { recursive: true, force: true, maxRetries: 5, retryDelay: 120 });
      return true;
    } catch (err) {
      // 逐项也删不掉：如果是目录，进去再逐项删一层（深层嵌套常见）
      try {
        if (fs.existsSync(p) && fs.statSync(p).isDirectory()) purgeDir(p);
        fs.rmSync(p, { recursive: true, force: true, maxRetries: 5, retryDelay: 120 });
        return true;
      } catch (err2) { /* 留给下一轮重试 */ }
    }
    sleep(150);
  }
  return !fs.existsSync(p);
}

function purgeDir(dir) {
  const sleep = (ms) => { try { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms); } catch (e) { /* 忽略 */ } };
  if (!fs.existsSync(dir)) return;
  for (let round = 0; round < 3; round++) {
    for (const e of fs.readdirSync(dir)) {
      const p = path.join(dir, e);
      try {
        fs.rmSync(p, { recursive: true, force: true, maxRetries: 5, retryDelay: 120 });
      } catch (err) {
        // 逐项也删不掉：如果是目录，进去再逐项删一层（深层嵌套常见）
        try {
          if (fs.statSync(p).isDirectory()) purgeDir(p);
          fs.rmSync(p, { recursive: true, force: true, maxRetries: 5, retryDelay: 120 });
        } catch (err2) { /* 留给下一轮重试 */ }
      }
    }
    try {
      fs.rmdirSync(dir);
      return;
    } catch (err) {
      sleep(150);
    }
  }
  // 兜底：删不掉就改名让路（名字带时间戳，不会挡住下一次运行）
  try {
    fs.renameSync(dir, dir + '-stale-' + Date.now());
    console.log('⚠ 旧副本目录删不掉，已改名让路：' + path.basename(dir) + '-stale-*');
  } catch (err) {
    console.log('⚠ 旧副本目录既删不掉也改不了名（后续写入可能失败）：' + err.message);
  }
}
// ---- 父进程：并发清目录 → 并发建副本 → 合并清单 ----
if (!IS_SHARD && BUILD_JOBS > 1) {
  const { spawn } = require('child_process');
  const t0 = Date.now();
  // ⚠⚠ 给第 k 个分片造一份**独立的** safe-delete 守卫状态目录（2026-10-13 实测加）。
  //   根因：平台给 node 注入的删除垫片里那层批量守卫，锁就是 `mkdir .lock`（2 秒超时）。
  //   分片建副本时，每棵副本都有 writeInCopy 的 `rmSync`（断硬链接那一句）⇒
  //   4 路同时去拿**同一把**锁 ⇒ 偶发 2 秒超时 ⇒ 分片崩在半路，
  //   症状是「❌ 1 个子进程建副本失败」，而闸报的是极具误导性的
  //   「存在漏检的检查脚本」（看着像检查器抓不到变异，其实副本只建了一部分 ✗）。
  //   各片一个状态目录 ⇒ 各拿各的锁；守卫的**计数与阈值保护完全不变** ✓
  //   （没有守卫环境变量时返回 undefined = 原样继承父进程环境）。
  const shardGuardEnv = (k) => {
    const dir = process.env.CODEBUDDY_SAFE_DELETE_BULK_STATE_DIR;
    return dir ? { ...process.env, CODEBUDDY_SAFE_DELETE_BULK_STATE_DIR: dir + '-shard-' + k } : undefined;
  };
  // 清目录：4 路各删一片（顶层条目取模）
  if (fs.existsSync(OUT)) {
    fs.mkdirSync(OUT, { recursive: true });
    const before = fs.readdirSync(OUT).length;
    if (before > 0) {
      console.log('=== 清理上一轮的 ' + before + ' 项（' + BUILD_JOBS + ' 路并发）===');
      const purge = [];
      for (let k = 0; k < BUILD_JOBS; k++) {
        purge.push(new Promise((resolve) => {
          const ch = spawn(process.execPath, [__filename, '--purge', k + '/' + BUILD_JOBS],
            { stdio: 'inherit', windowsHide: true, env: shardGuardEnv(k) });
          ch.on('exit', (code) => resolve(code === null ? 1 : code));
        }));
      }
      Promise.all(purge).then(() => buildAll());
      return;
    }
  }
  buildAll();
  function buildAll() {
    fs.mkdirSync(OUT, { recursive: true });
    console.log('=== 建 ' + CASES.length + ' 棵副本（' + BUILD_JOBS + ' 路并发）===');
    const kids = [];
    for (let k = 0; k < BUILD_JOBS; k++) {
      kids.push(new Promise((resolve) => {
        // ⚠ stdio: 'inherit' —— 沙箱允许（被禁的只有 'pipe'）。子进程平时不打印，
        //   一旦崩了，错误文本能直接落到闸日志里（这正是我们要的可见性）。
        // ⚠ env: shardGuardEnv(k) —— 同上：每片一把守卫锁，别 4 路共抢一把。
        const ch = spawn(process.execPath, [__filename, '--shard', k + '/' + BUILD_JOBS],
          { stdio: 'inherit', windowsHide: true, env: shardGuardEnv(k) });
        ch.on('exit', (code) => resolve(code === null ? 1 : code));
      }));
    }
    Promise.all(kids).then((codes) => {
      const bad = codes.filter((c) => c !== 0).length;
      if (bad > 0) {
        console.error('❌ ' + bad + ' 个子进程建副本失败');
        process.exit(1);
      }
      // 合并分片清单（**按用例原始顺序**，与串行时逐字一致）
      const merged = [];
      for (let k = 0; k < BUILD_JOBS; k++) {
        const f = path.join(OUT, 'manifest-shard-' + k + '.json');
        if (!fs.existsSync(f)) continue;
        merged.push(...JSON.parse(fs.readFileSync(f, 'utf8')));
        fs.rmSync(f, { force: true });
      }
      if (merged.length !== CASES.length) {
        console.error('❌ 清单条数不对：' + merged.length + ' ≠ ' + CASES.length);
        process.exit(1);
      }
      const byDir = new Map(merged.map((m) => [m.dir, m]));
      const ordered = CASES.map((c) => byDir.get(c.dir)).filter(Boolean);
      for (const m of ordered) {
        console.log('  ' + m.checker.padEnd(24) + m.dir.padEnd(24) + m.desc);
      }
      fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(ordered, null, 2), 'utf8');
      console.log('  ⏱ 清目录 + 建副本共 ' + Math.round((Date.now() - t0) / 1000) + ' 秒');
      console.log('\n共 ' + ordered.length + ' 个用例 → ' + path.relative(ROOT, OUT));
      process.exit(0);
    });
  }
} else {
if (!IS_SHARD) purgeDir(OUT);
const srcBefore = sourceFingerprint();

const manifest = [];
for (let caseIndex = 0; caseIndex < CASES.length; caseIndex++) {
  const c = CASES[caseIndex];
  // 分片：只建属于本片的用例（`index % N === k`，与父进程的合并顺序无关 ——
  // 父进程最后按 CASES 顺序重排，所以日志与串行时逐字一致 ✓）
  if (IS_SHARD && (caseIndex % shardCount) !== shardIndex) continue;
  const dest = path.join(OUT, c.dir);
  mkdirCache = new Set();          // 每个用例一棵新树 ⇒ 目录缓存按用例清空
  // ⚠ files 里先只放"**路径**"（值 null = 还没读），用例 at.get 到哪个才读哪个 ——
  //   这就是"按需读"：绝大多数用例只碰 1~2 个文件，不必把 150 个都读进内存。
  const files = {};
  const dirty = new Set();
  // 按需复制的额外文件（支持最后一段用 * 通配）。二进制安全：整体复制。
  //
  // ⚠ 鱼的三个检查器**一律**带上 EXTRA.fish，不靠用例自己写：
  //   漏写的后果不是"跑不过"，而是**假通过** —— 负例本来就期望失败，
  //   少了模型文件它也会失败，只是失败原因和目标毫无关系。
  //   （踩过：egg-fish-* 忘了带 extra，四个用例全是靠"缺模型"变红的。）
  //
  // ⚠⚠ EXTRA **必须先拷**，再写用例改过的 FILES：
  //   反过来（先写 FILES 再拷 EXTRA）的话，某个文件同时出现在两边时，
  //   EXTRA 那份会把用例的变异**覆盖掉**，于是负例静默失效 ——
  //   2026-10-02 的 recipeadv-hand-edited 就是这么假通过的（它红的真实原因是
  //   生成器没被拷进副本，跟"手改进度"毫无关系）。
  const extraPats = (c.extra || [])
    .concat(FISH_CHECKERS.has(c.checker) ? EXTRA.fish : [])
    .concat(ROD_ICON_CHECKERS.has(c.checker) ? EXTRA.rod : [])
    .concat(RECIPE_ADV_CHECKERS.has(c.checker) ? EXTRA.recipeAdv : [])
    .concat(ENV_CHECKERS.has(c.checker) ? EXTRA.env : [])
  .concat(CODEX_CHECKERS.has(c.checker) ? EXTRA.codex : [])
    .concat(ENCH_CHECKERS.has(c.checker) ? EXTRA.ench : [])
    .concat(KITCHEN_CHECKERS.has(c.checker) ? EXTRA.kitchen : [])
    .concat(RECIPE_CHECKERS.has(c.checker) ? EXTRA.recipes : [])
    .concat(RODCAST_CHECKERS.has(c.checker) ? EXTRA.rodCast : [])
    .concat(BOOK_CHECKERS.has(c.checker) ? EXTRA.book : [])
    .concat(BUILDSCRIPT_CHECKERS.has(c.checker) ? EXTRA.buildscripts : [])
    .concat(EFFECT_CHECKERS.has(c.checker) ? EXTRA.effects : [])
  .concat(EFFECT_HOOK_CHECKERS.has(c.checker) ? EXTRA.effectHooks : [])
  .concat(COOKBOOK_CHECKERS.has(c.checker) ? EXTRA.cookbook : [])
  .concat(TOOLTIP_COLOR_CHECKERS.has(c.checker) ? EXTRA.tooltipColors : [])
  .concat(OVERCAP_CHECKERS.has(c.checker) ? EXTRA.overcap : [])
  .concat(PLAYERDATA_CHECKERS.has(c.checker) ? EXTRA.playerData : [])
  .concat(SELFCONSIST_CHECKERS.has(c.checker) ? EXTRA.selfConsist : [])
  .concat(DEADFIELDS_CHECKERS.has(c.checker) ? EXTRA.deadFields : [])
  .concat(KALEIDO_CHECKERS.has(c.checker) ? EXTRA.kaleidoscope : []);
  for (const pat of extraPats) {
    for (const rel of expandExtraCached(pat)) {
      const dst = path.join(dest, rel);
      mkdirsOnce(path.dirname(dst));
      linkOrCopy(path.join(ROOT, rel), dst);
    }
  }
  for (const rel of FILES) {
    const src = path.join(ROOT, rel);
    const dst = path.join(dest, rel);
    if (!fs.existsSync(src)) continue;
    mkdirsOnce(path.dirname(dst));
    linkOrCopy(src, dst);
    files[rel] = null;                  // 路径登记，内容按需读（见 makeAccessor 的 reader）
  }
  (c.mutate || (() => {}))(makeAccessor(files, dirty, dest));
  // ⚠ **只写回用例真改过的文件**（原来是把 150 个文件全写一遍 —— 白写 99%）
  for (const rel of dirty) {
    writeInCopy(path.join(dest, rel), files[rel]);
  }
  // 二进制文件：整体复制，绝不经过字符串
  for (const rel of BIN_FILES) {
    const src = path.join(ROOT, rel);
    const dst = path.join(dest, rel);
    mkdirsOnce(path.dirname(dst));
    if (fs.existsSync(src)) linkOrCopy(src, dst);
  }
  for (const [rel, keepBytes] of c.truncateBin || []) {
    const p = path.join(dest, 'src/main/resources/' + rel);
    const buf = fs.readFileSync(p);
    writeInCopy(p, buf.subarray(0, keepBytes));     // ⚠ 必须走 writeInCopy：这是**截断真文件**的路径
  }
  // 往副本里**新造**文件（例如"旧的重复真源又冒出来了"这种负例）
  for (const [rel, text] of c.createFiles || []) {
    writeInCopy(path.join(dest, rel), text);
  }
  for (const [from, to] of c.renameDirs || []) {
    const a = path.join(dest, 'src/main/resources/' + from);
    const b = path.join(dest, 'src/main/resources/' + to);
    if (fs.existsSync(a)) fs.renameSync(a, b);
  }
  for (const rel of c.deleteData || []) {
    const p = path.join(dest, 'src/main/resources/data/anglertackle/' + rel);
    if (fs.existsSync(p)) fs.rmSync(p);
  }
  for (const rel of c.deleteFiles || []) {
    const p = path.join(dest, 'src/main/resources/assets/anglertackle/' + rel);
    if (fs.existsSync(p)) fs.rmSync(p);
  }
  // 改二进制 NBT：用带类型的读写，改完写回。这样负例本身也是"真正的 NBT"，
  // 不会因为构造出一个畸形文件而让检查脚本以"解析失败"这种无关理由变红。
  if (c.mutateNbt) {
    const nbt = require('./lib-nbt');
    const p = path.join(dest, 'src/main/resources/data/anglertackle/structure/fisherman_hut.nbt');
    const typed = nbt.readNbtTyped(p);
    c.mutateNbt(typed);
    // ⚠ 先断开硬链接再交给 writer 写（否则它会穿过链接改掉真源码 ✗）
    if (fs.existsSync(p)) fs.rmSync(p, { force: true });
    nbt.writeNbtFile(p, typed.name, typed.root);
  }
  // 造损坏贴图（必须走真正的 PNG 编码，否则检查器会以"解析失败"这种无关理由变红）
  if (c.mutatePng) c.mutatePng(dest);
  manifest.push({ dir: c.dir, checker: c.checker, desc: c.desc, expect: c.expect });
  console.log('  ' + c.checker.padEnd(24) + c.dir.padEnd(24) + c.desc);
}

// ★ 建完副本**必须**复核真源码没被动过（硬链接写穿是这条链上最贵的错误，见顶部说明）
assertSourceIntact(srcBefore);

if (IS_SHARD) {
  // 子进程：把清单写成**自己的分片**（父进程稍后合并；不碰 manifest.json ✓）
  fs.writeFileSync(path.join(OUT, 'manifest-shard-' + shardIndex + '.json'),
    JSON.stringify(manifest, null, 2), 'utf8');
} else {
  fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify(manifest, null, 2), 'utf8');
  console.log('\n共 ' + CASES.length + ' 个用例 → ' + path.relative(ROOT, OUT));
}
}
