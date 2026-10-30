package com.whitecobalt.anglertackle.fishing;

import com.whitecobalt.anglertackle.registry.ModItems;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.function.ToDoubleFunction;

/**
 * 鱼种表：把"物品 / 体重区间 / 品质档 / 手感档案 / 中文名"绑在一起。
 *
 * <p><b>品质与手感的关系（用户定案）</b>：
 * <ul>
 *   <li><b>品质越高，游得越快</b> —— {@link #tierSpeed} 随档位指数上升</li>
 *   <li><b>品质越高，距离越容易被拉远</b> —— 同一件事的另一面：
 *       鱼游得快，线自然被拽出去得快</li>
 *   <li><b>品质越高，体力越抗磨</b> —— {@link #tierStaminaResist} 让高级鱼
 *       不会因为"体力上限高"就变得好钓</li>
 * </ul>
 *
 * <p>在此之上，三种策略让手感分化（见 {@link FightProfile.Archetype}）：
 * <ul>
 *   <li><b>爆发型 BURST</b>：挣扎时游得飞快、冲刺狠而频 —— 但耗力快，
 *       要趁它喘气那几秒抢线</li>
 *   <li><b>黏液型 SLUGGISH</b>：游得慢，可是 {@code tensionGain} 高 ——
 *       线黄得特别快，得频繁松手，靠耐心磨</li>
 *   <li><b>神经质 ERRATIC</b>：变向极频繁 + 假动作 —— 得紧跟预告方向，
 *       预判错了张力就飙</li>
 *   <li><b>均衡型 STEADY</b>：各项中庸，新手鱼</li>
 * </ul>
 *
 * @param id         鱼种 id（存档与图鉴用）
 * @param item       对应的物品
 * @param specMinKg      体重下限（kg）——规格值，运行期用 kgMin()
 * @param specMaxKg      体重上限（kg）——规格值，运行期用 kgMax()
 * @param tiers      品质阶梯（从低到高，至少 2 档）；数组长度决定等分份数
 * @param specProfile    对抗手感档案——规格值，运行期用 effectiveProfile()
 * @param rods       适用竿型
 */
public record FishSpecies(
        String id,
        DeferredItem<? extends Item> item,
        // ⚠ 下面是**源码里写着的规格值**，运行期请用 kgMin() / kgMax() / effectiveProfile()（走配置）
        double specMinKg,
        double specMaxKg,
        int[] tiers,
        FightProfile specProfile,
        RodKind rods
) {

    /**
     * 这种鱼能用哪种竿钓到。
     *
     * <p>用户要求："台钓和路亚能上的鱼要有区分，可以重叠部分但不能全品种重叠"。
     * 现实里的分工也正好是这个逻辑：
     * <ul>
     *   <li><b>台钓</b>（浮漂 + 饵料，钓底/钓浮）→ 底层与中层的杂食、草食鱼</li>
     *   <li><b>路亚</b>（拟饵，模仿小鱼）→ 掠食性鱼</li>
     *   <li><b>BOTH</b> → 中间地带：既吃饵也会咬拟饵的</li>
     * </ul>
     */
    public enum RodKind {
        /** 只能台钓。 */
        FLOAT,
        /** 只能路亚。 */
        LURE,
        /** 两种竿都能钓到（重叠部分）。 */
        BOTH
    }

    // ------------------------------------------------------------------ 品质档位
    // 用户定案（按鱼种逐条指定）：
    //   鲫鱼 白绿        罗非 白绿        鲻鱼 绿蓝
    //   草鱼 绿蓝        青鱼 绿蓝紫      翘嘴 蓝紫
    //   梭鲈 绿蓝紫      鲑   金红        金枪鱼 金红       幼鲨 金红

    private static final int[] W_G = {0, 1};              // 白 → 绿
    private static final int[] G_B = {1, 2};              // 绿 → 蓝
    private static final int[] B_P = {2, 3};              // 蓝 → 紫
    private static final int[] G_B_P = {1, 2, 3};         // 绿 → 蓝 → 紫
    private static final int[] G_R = {4, 5};              // 金 → 红
    private static final int[] R = {5};                   // 只有红一档
    private static final int[] P_G = {3, 4};              // 紫 → 金
    private static final int[] P_G_R = {3, 4, 5};         // 紫 → 金 → 红
    // 2026-10-08 用户点名新增：裸盖鱼"蓝紫金" —— 注意 {2,3,4} 这个组合以前没有，
    // 加它的时候要同步 tools/check-water-body.js 的 TIER 表（那份表解析不出来会直接报红，
    // 而且报的是"这些鱼种的档位表解析不出来"，是个很难联想的错 ✗）。
    private static final int[] B_P_G = {2, 3, 4};         // 蓝 → 紫 → 金

    // 档位口径（**0 起算**，与 Quality 的枚举顺序一致：白=0 绿=1 蓝=2 紫=3 金=4 红=5）：
    //   "紫~金" = {3, 4}，"金~红" = {4, 5}。
    // ⚠ 别再按 1 起算写（把紫写成 4），那会让所有兑换和等级评定整体错位一档。

    /**
     * 所有鱼种。
     *
     * <p><b>用户定案的两条硬规则（2026-09-29）：</b>
     * <ol>
     *   <li><b>台钓能且仅能钓出一种红鱼 = 蓝宝石雷龙。</b>
     *       所以任何 {@code RodKind.BOTH} 或 {@code FLOAT} 的鱼种都**不许**带红档。
     *       大西洋鲑原来正是"通用 + 金红"，已按此改成**仅路亚**。</li>
     *   <li>新增 5 种鱼的竿型分工：
     *       台钓 = 横纹鱼(W_G) / 蓝宝石雷龙(R) / 白条鱼(W_G)
     *       路亚 = 铂金银龙鱼(G_R) / 大眼海鲢(R)</li>
     * </ol>
     *
     * <p><b>⚠ 第三批 5 种（2026-10-01 用户逐条指定）**修改了上面第 ① 条**：</b>
     * 用户直接点名"孔雀鱼 = 金~红 + 台钓"，于是**台钓不再只有蓝宝石雷龙一种红鱼**。
     * 这是用户的设计决定，不是漏改 —— {@code tools/check-fish-species.js} 与
     * {@code tools/check-new-fish.js} 里那条"有且仅有"的断言已相应改成
     * "蓝宝石雷龙仍在台钓红鱼之列，且台钓红鱼不止一种"。
     * 其余不变：档位表必须升序去重且在 0..5、路亚至少两种红鱼、每个水域×竿型都非空。
     * <p>档位口径重申一次（**0 起算**）：紫=3 金=4 红=5。
     * 用户要求"紫~金"即 {@code {3, 4}}，"金~红"即 {@code {4, 5}}。
     */
    public static final java.util.List<FishSpecies> ALL = java.util.List.of(
            // ---- 台钓向：底层/中层杂食与草食 ----
            new FishSpecies("crucian_carp", ModItems.CRUCIAN_CARP, 0.10, 1.20, W_G,
                    FightProfile.CRUCIAN_CARP, RodKind.BOTH),
            new FishSpecies("tilapia", ModItems.TILAPIA, 0.20, 2.00, W_G,
                    steady(1.2, 90, 0.95), RodKind.FLOAT),
            new FishSpecies("mullet", ModItems.MULLET, 0.30, 2.50, G_B,
                    steady(2, 110, 1.05, 0.315), RodKind.BOTH),
            new FishSpecies("grass_carp", ModItems.GRASS_CARP, 1.50, 18.00, G_B,
                    sluggish(1.125, 220, 1.35), RodKind.BOTH),
            new FishSpecies("black_carp", ModItems.BLACK_CARP, 3.00, 35.00, G_B_P,
                    sluggish(1.05, 320, 1.55), RodKind.FLOAT),
            // ---- 新增：台钓小型鱼（用户定案）----
            //  横纹鱼 = 横纹南鳅 Schistura fasciolata（条鳅科）
            //    真实数据：常见 6cm / 最大 12cm，3~8g。急流山溪底层，肉食偏杂食。
            //    手感：几乎无搏斗 —— 体型太小，只能原地乱窜。所以 pullSpeed 很低、
            //    体力极少（25），是"刷量用的入门鱼"。
            //    ⚠ 体重只有几克，用 kg 记就是 0.003~0.03。
            new FishSpecies("banded_loach", ModItems.BANDED_LOACH, 0.003, 0.030, W_G,
                    steady(0.525, 25, 0.8), RodKind.FLOAT),
            //  白条鱼 = 䱗 Hemiculter leucisculus（鲤科，上层群游）
            //    真实数据：常见 10~17cm / 最大 29cm，20~60g / 最大 232g。杂食，上层。
            //    手感：群体抢食、接口多、节奏快；单尾挣扎又碎又急但**几乎无耐力**。
            //    所以 pullSpeed 偏高（碎急）、体力很低（45）。
            new FishSpecies("hemiculter", ModItems.HEMICULTER, 0.020, 0.232, W_G,
                    erratic(1.425, 45, 1.1), RodKind.FLOAT),
            // ---- 路亚向：掠食性 ----
            new FishSpecies("topmouth_culter", ModItems.TOPMOUTH_CULTER, 0.60, 6.00, B_P,
                    burst(2.025, 150, 1.2), RodKind.LURE),
            new FishSpecies("zander", ModItems.ZANDER, 1.00, 9.00, G_B_P,
                    erratic(2.175, 180, 1.3), RodKind.LURE),
            //  蓝宝石雷龙 = Channa ornatipinnis（鳢科，迷鳃鱼）
            //    真实数据：20~25cm / 最大 30cm，估 150~350g。底层伏击，纯肉食。
            //    手感：中短距离爆发极快、猛钻障碍物、横向摆头甩钩；耐力中等。
            //    所以 pullSpeed 高但体力中等，变向频繁（对应"钻障碍"）。
            //    ★ 台钓唯一的红鱼（用户定案）。
            new FishSpecies("sapphire_snakehead", ModItems.SAPPHIRE_SNAKEHEAD, 0.150, 0.350, R,
                    erratic(2.55, 130, 1.35), RodKind.FLOAT),
            //  铂金银龙鱼 = Osteoglossum bicirrhosum 铂金型（骨舌鱼科，上层掠食）
            //    真实数据：60~90cm，2~4kg / 最大 6kg。上层食鱼，会跃出水面捕食昆虫。
            //    手感：爆发力强、直线冲刺猛、频繁跃出水面；持续力一般。
            //    → burst（冲刺型）+ 较高的 fatigue 破绽，对应"短促爆发"。
            new FishSpecies("platinum_arowana", ModItems.PLATINUM_AROWANA, 1.500, 6.000, G_R,
                    burst(3.9, 300, 1.95), RodKind.LURE),
            //  大眼海鲢 = Megalops cyprinoides（海鲢科）
            //    真实数据：常见 30~45cm / 最大 150cm，常见 0.5~3kg / 最大 18kg。
            //    手感：**本批最强** —— 爆发极猛、连续跃出水面甩头、变向频繁、耐力极强。
            //    → burst + 最高体力 + 高变向，是这批鱼的难度顶点。
            new FishSpecies("oxeye_tarpon", ModItems.OXEYE_TARPON, 0.500, 18.000, R,
                    erratic(4.8, 400, 1.65), RodKind.LURE),
            // ---- 金红档：游速按用户定案抬到"金 ≥3.8、红上限 5.1" ----
            // 游速 = 基础 pullSpeed × tierSpeed(档位)：底数 1.12^档，**再乘手感修正**
            // （见 tierFeelSpeedScale，用户 2026-10-05）→ 金档 ×1.32、红档 ×1.85。
            // ⚠ 下面这份换算表是**手感修正之前**的旧值（金 ×1.57 / 红 ×1.76），
            //   现在金档要乘 0.84、红档乘 1.05，别照抄。
            // 基础值后来又**整体上调 25%**（用户定案："基础游速金品质以上上调 25%"）：
            //   鲑   2.50 → 3.125 → 金 4.91 / 红 5.50
            //   金枪 2.75 → 3.4375 → 金 5.40 / 红 6.06
            //   幼鲨 2.90 → 3.625 → 金 5.69 / 红 6.38
            // 用分数写（5/1.6 那种）不好读，直接写小数并注明来源。
            // ⚠ 这条例只作用于**鱼种基础值**，不是 tierSpeed 系数——
            //   金档以下的鱼不受影响（用户口径是"金品质以上"）。
            // ⚠ 鲑已从 BOTH 改成 LURE（用户定案：台钓能且仅能出蓝宝石雷龙一种红鱼）。
            new FishSpecies("atlantic_salmon", ModItems.ATLANTIC_SALMON, 2.00, 22.00, G_R,
                    burst(4.6875, 360, 1.25), RodKind.LURE),
            new FishSpecies("bluefin_tuna", ModItems.BLUEFIN_TUNA, 12.00, 180.00, G_R,
                    burst(5.15625, 420, 1.5), RodKind.LURE),
            new FishSpecies("juvenile_shark", ModItems.JUVENILE_SHARK, 8.00, 90.00, G_R,
                    erratic(5.4375, 380, 1.7), RodKind.LURE),

            // ================= 第二批 6 种（用户定案：**全部仅路亚**）=================
            // 参数按现有刻度校准（拉力 0.35~3.625 / 体力 25~420 / 张力系数 0.80~1.70）。
            // 习性依据来自联网查证，逐条写在下面；手感则是把这些习性映射到四种原型上：
            //   BURST    伏击 → 短促高爆发、间隔长、耐力低
            //   ERRATIC  高速伏击 → 变向频繁、有假动作
            //   STEADY   持续对抗 → 各项中庸偏上
            //   SLUGGISH 笨重力大 → 拉得慢但张力涨得快、耐力极高

            //  虎鱼 = 泰国虎鱼（Datnioides，丑鱼科）
            //    真实数据：常见 20~30cm，0.05~0.8kg。底层/中层高速伏击，攻击极快、变向刁钻。
            //    手感：起手极快、左右乱窜、假动作多，但**体型小所以耐力差**。
            //    → erratic（高变向 + 35% 假动作），拉力给到金档偏低、体力压低，
            //      做成"快而脆"的对手：反应慢就被甩钩，稳住两下就遛翻。
            new FishSpecies("tigerfish", ModItems.TIGERFISH, 0.050, 0.800, G_R,
                    erratic(3.3, 320, 3.1), RodKind.LURE),

            //  狗鱼 = 白斑狗鱼 Esox lucius
            //    真实数据：平均 40~55cm / 最长 150cm，最大 28.4kg。本种取 1~12kg。
            //    习性（物种库）：**伏击捕食者，长时间隐藏在水草间一动不动，
            //      等猎物出现后突然加速发动进攻**。细长鱼雷型身体。
            //    手感：中钩后直线猛冲、极少变向，但**冲刺短、耐力差**——
            //      遛过一个回合就明显泄力，是"一波流"对手。
            //    → burst，tensionGain 偏低（直线冲不带缠绕感），体力也不高。
            new FishSpecies("pike", ModItems.PIKE, 1.000, 12.000, P_G,
                    burst(3.6, 170, 1.15), RodKind.LURE),

            //  褐鳟 = Salmo trutta
            //    真实数据：常见 25~50cm，平均 0.3~1.5kg，优质水域可达 3kg+。本种取 0.5~6kg。
            //    习性（鳟钓竿资料）："冷水性掠食鱼，**爆发力强、冲刺迅猛**"。
            //    ★ 用户单独定调："**粘稠手感，并且是比较粘稠的一档**"——
            //      对应鳟鱼中钩后的摆头（head-shake）+ 原地顶水：
            //      线传回来是一顿一顿的黏滞阻力，不是鲑鳟那种长直线。
            //    → 用 burst 拿"冲刺迅猛"，但 tensionGain 给到 1.60（仅次于大眼海鲢 1.65）
            //      来体现"粘"：线涨得快，玩家必须频繁松手，不能硬拉。
            //      体力中等（200）——爆发猛但泄得也快，遛两回合就虚。
            new FishSpecies("brown_trout", ModItems.BROWN_TROUT, 0.500, 6.000, G_R,
                    burst(3.45, 400, 2.65), RodKind.LURE),

            //  镜鲤 = Cyprinus carpio var. 镜鲤（鲤科）
            //    真实数据：本种取 1.5~20kg。底层杂食，与现有草鱼/青鱼同族的耐力型。
            //    手感：拉得慢、方向稳、几乎不冲刺，但**磨得极久**——和草鱼/青鱼一个路子。
            //    用户口径："金红（后者很稀有）"→ G_R，且 tierSplit 里红档权重只有 2/7。
            //    → sluggish，体力拉高（380）做成"持久战"，张力系数 1.40 偏粘。
            new FishSpecies("mirror_carp", ModItems.MIRROR_CARP, 1.500, 20.000, G_R,
                    sluggish(3.6, 380, 2.8), RodKind.LURE),

            //  条纹鲈 = Morone saxatilis
            //    真实数据：本种取 1~15kg。洄游性掠食鱼，潮流里长时间顶流。
            //    手感：典型的"持续对抗型"——冲得不快不慢、方向中规中矩，
            //      但**耐力很足**，靠时间磨。用户口径："绿蓝紫"→ G_B_P（与梭鲈同档位）。
            //    → steady，拉力/体力都取"紫档偏上"，做成低档位里的硬骨头。
            new FishSpecies("striped_bass", ModItems.STRIPED_BASS, 1.000, 15.000, G_B_P,
                    steady(2.4, 240, 1.2), RodKind.LURE),

            //  巨骨舌鱼 = Arapaima gigas（骨舌鱼科）
            //    真实数据：成体 2~6m / 可达 100~200kg。本种取 20~100kg。
            //    习性（百度百科）：**"体型笨重，行动缓慢"、"具有强大的蛮力"、
            //      "上钩后虽然不跳出水面，但也拼命地摆动身躯或在水面使劲翻滚"**。
            //    手感：**拉不快**（笨重），但**张力涨得极快**（翻滚缠线）、
            //      **体力极高**（蛮力鱼，一小时的搏斗记录）、几乎不变向（翻滚而非转向）。
            //    → steady 的极端取值。用户拍板"不加第 5 种原型，用 STEADY 极端值顶着"，
            //      所以这里刻意把三个维度都推到现有刻度的上沿：
            //      体力 520（全表最高，超过大眼海鲢 400）、张力 1.75（全表最高）、
            //      拉力 1.80（**低于**同为紫金红的梭鲈 2.40）——"拉不动但拉不赢"。
            new FishSpecies("arapaima", ModItems.ARAPAIMA, 20.000, 100.000, P_G_R,
                    steady(3.3, 520, 3.2), RodKind.LURE),

            // ============ 第三批 5 种（用户 2026-10-01 逐条指定档位与竿型）============
            // ⚠ 档位口径：**0 起算**（白0 绿1 蓝2 紫3 金4 红5）。
            //   用户原话"紫~金" = P_G = {3,4}；"金~红" = G_R = {4,5}。
            // 习性依据逐条来自联网查证，完整资料（含来源 URL）在 fish_research/<id>.json。

            //  齿蝶鱼 = Pantodon buchholzi（齿蝶鱼科，单型科单型属）
            //    ⚠ 中文名 2026-10-11 修正：规范名是**齿蝶鱼**（科名齿蝶鱼科就是这么来的）；
            //      「非洲蝴蝶鱼」是水族俗称，而且**极易与海水蝴蝶鱼科 Chaetodontidae 混淆** ——
            //      本种是淡水鱼、跟海水蝴蝶鱼毫无亲缘关系。游戏里显示名已改成"齿蝶鱼"。
            //    真实数据：常见 10~12cm，最长 11.9cm TL（FishBase），水族箱最大 15cm。
            //    习性：**严格的水面层鱼**（FishBase：it can be seen on the surface waters），
            //      栖息在溪流、湖泊、沼泽中水流缓慢或静止、水草茂密处；黄昏最活跃；
            //      专性空气呼吸；会跃出水面捕食飞行昆虫。
            //    手感：用户 2026-10-01 定案"台钓的 4 种不单独调研手感"，所以按台钓常规
            //      小体型鱼给 —— 但水面鱼会跃起、方向碎，所以档位内取偏"稳中带碎"的
            //      中偏上变向，不做成神经质。
            //    ★ 档位 P_G（紫~金）· 仅台钓 · 出现水域 = 河流 / 沼泽（见 FishWaters）
            new FishSpecies("african_butterflyfish", ModItems.AFRICAN_BUTTERFLYFISH,
                    0.003, 0.015, P_G,
                    steady(1.65, 95, 1), RodKind.FLOAT),

            //  红宝石鱼 = Rubricatochromis bimaculatus（旧名 Hemichromis bimaculatus，丽鱼科）
            //    ⚠ 学名 2026-10-11 修正：属名 **Rubricatochromis** 是 2022 年 Lamboj & Koblmüller
            //      从 Hemichromis 里分出来的（Rubricatus = 拉丁语"染红的"，指本属占优/护幼时的鲜红体色）；
            //      水族书里的"红宝石鱼 = Hemichromis bimaculatus"是**旧名**，别再照抄。
            //    真实数据：常见 7.5cm TL（FishBase common length），最大 13.6cm SL / 10g，
            //      水族饲养最大 12cm。本种取 13cm 建模。
            //    习性：**底栖~中层**（FishBase 环境字段就是 benthopelagic），
            //      栖息在泥底与沙底的运河/河道，与有遮蔽的森林覆盖相关；成对、极凶。
            //    手感：体高侧扁 = 同样的体力下"拽得动但横着走"，所以拉力给得比
            //      同档位的水面鱼高一档、张力涨速也略高（侧扁鱼横着摆时线吃力）。
            //    ★ 档位 P_G（紫~金）· 仅台钓 · 出现水域 = 河流 / 湖泊（见 FishWaters）
            new FishSpecies("jewel_cichlid", ModItems.JEWEL_CICHLID,
                    0.020, 0.150, P_G,
                    steady(1.725, 130, 1.1), RodKind.FLOAT),

            //  孔雀鱼 = Poecilia reticulata（花鳉科，卵胎生）
            //    真实数据：**模组里最小的鱼** —— 雄鱼常见 2.8cm TL、雌鱼最长 6.0cm SL。
            //      本种取 4cm 建模（雄鱼与雌鱼之间）。
            //    习性：上层~表层小型鱼，栖息在温泉出水口、长满水草的沟渠与运河、
            //      小溪与植被茂密的湖泊；吃浮游动物、小昆虫与碎屑。
            //    手感：太小，**几乎无搏斗** —— 只能原地乱窜。所以拉力与张力都压到
            //      全表最低档附近（张力 0.95，是全表最宽容的一个）；体力 60 略高于
            //      白条鱼的 45，因为它档位更高（金红），不能比白绿档的鱼还好遛。
            //    ★ 档位 G_R（金~红）· 仅台钓 —— ⚠ 这是**用户点名**要的红档台钓鱼，
            //      因此台钓不再"有且仅有蓝宝石雷龙一种红鱼"（见 ALL 的类注释）。
            //    ★ 出现水域 = 湖泊 / 沼泽（见 FishWaters）
            new FishSpecies("guppy", ModItems.GUPPY,
                    0.001, 0.006, G_R,
                    steady(1.575, 60, 0.95), RodKind.FLOAT),

            //  长吻象鼻鱼 = Gnathonemus petersii（长颌鱼科，弱电鱼）
            //    真实数据：常见成体 20~25cm（SeriouslyFish 225mm SL），最大 35cm SL。
            //      本种取 25cm 建模。
            //    习性：**底栖且是浑浊泥底的底栖**（FishBase 环境字段 = demersal；
            //      SeriouslyFish：栖息在河流幽暗泥泞的河床与植被茂密处、水流缓慢），
            //      必须有软沙底；夜行；用下垂的管状吻在底质里翻找蠕虫与昆虫；
            //      靠尾部发电器官产生的**弱电场**导航、觅食与交流。
            //    手感：底栖、动作幅度小但**变向碎**（电场定位 = 贴着障碍物走），
            //      所以用 steady 但把变向感做出来；体力按它的体型给中档。
            //    ★ 档位 P_G（紫~金）· 仅台钓 · 出现水域 = 河流 / 洞穴（昏暗静水，见 FishWaters）
            new FishSpecies("elephantnose", ModItems.ELEPHANTNOSE,
                    0.030, 0.300, P_G,
                    steady(1.8, 170, 1.15), RodKind.FLOAT),

            // ---- 第四批 2 种（用户 2026-10-02 指定）：洞穴专属台钓鱼、蓝紫档 ----
            //
            //  金泥鳅 = 大鳞副泥鳅 Paramisgurnus dabryanus 的金黄（黄化）选育品系（鳅科）
            //    真实数据（fish_research/golden_loach.json，20 条来源）：
            //      常见成体 15~20cm、最大 23.1cm / 150g；体长∶体高 ≈ 6.6∶1（圆筒形）；
            //      **头短（头长 < 体高）**、尾柄侧扁且上下各一条发达皮质棱；
            //      须 **5 对共 10 根**、无脂鳍、尾鳍圆形、背鳍后缘平截偏后；
            //      眼被皮膜覆盖、**无眼下刺**、**眼球金红**（黄化而非白化）。
            //    习性：底层、夜间摄食、肠呼吸（浮头吞气）；★金黄品系**不钻泥**
            //      （CCTV-7 与农业农村部转载的辽宁验收消息两处都把它列为品种优点），
            //      所以模型按"贴底游动"画，而不是整条钻进泥里。
            //    手感：圆筒形、力气小的底栖鱼 —— 游速低、体力小、几乎不冲刺，
            //      用 steady 而不是 sluggish：它不是草鱼那种"线黄得飞快"的黏液型。
            //    ★ 档位 B_P（蓝~紫）· 仅台钓 · 出现水域 = 仅洞穴（见 FishWaters）
            new FishSpecies("golden_loach", ModItems.GOLDEN_LOACH,
                    0.030, 0.150, B_P,
                    steady(0.75, 60, 0.95), RodKind.FLOAT),

            //  盲鱼 = 墨西哥丽脂鲤 Astyanax mexicanus 的**洞穴型**（脂鲤科）
            //    按"把洞穴型独立成种"的系统则写作 Astyanax jordani，旧称乔氏丽脂鲤。
            //    真实数据（fish_research/cave_blindfish.json，33 条来源）：
            //      常见约 8cm、最大 10.0cm TL；体长∶体高 ≈ **2.0**
            //      （FishBase 照片实测 aspect ratio 2.03）——
            //      ★它是"长椭圆形、侧扁"的短胖体型，**不是细长条**；
            //      ★**有脂鳍**、尾鳍深叉 —— 脂鳍是与鲤科洞穴鱼（金线鲃那类）
            //        最关键的鉴别点；完全没有色素斑纹，通体乳白浅肉色半透明，
            //        隔着身子能看见脊椎与内脏；眼**完全退化**，成年只剩皮膜下的浅凹陷。
            //    习性：底~中层、地下暗河；靠侧线感知，对约 35Hz 的震动有
            //      "振动吸引行为"（VAB）；睡眠极少、无昼夜节律 —— 模型上可以画成
            //      几乎不停游动的小鱼。
            //    手感：8cm 的小型鱼，力气比金泥鳅还小 —— 游速低、几乎不冲刺。
            //    ★ 档位 B_P（蓝~紫）· 仅台钓 · 出现水域 = 仅洞穴（见 FishWaters）
            new FishSpecies("cave_blindfish", ModItems.CAVE_BLINDFISH,
                    0.005, 0.020, B_P,
                    steady(0.6, 45, 0.85), RodKind.FLOAT),

            //  北非胡子鲇 = Clarias gariepinus（胡子鲇科）—— ★ 本批**唯一的路亚鱼**
            //    真实数据：常见 90cm（FishBase common length），最大 170cm TL / 60kg；
            //      本种取 90cm 建模（与青鱼同尺寸档，但档位更高）。
            //    习性（ADW / FishBase 原文）：**底栖**（They are bottom dwellers and do
            //      most of their feeding there）；耐极低氧、专性/兼性空气呼吸，是"最后
            //      还能活在死水里的鱼"；偏好浅而沼泽化、底质软泥、水流平静的区域；
            //      夜行；杂食偏肉食。
            //    ★ 手感（用户要求"按真实习性确认手感"，所以这一条写全）：
            //      ADW 原文 "relatively poor swimmers that spends most of the time on
            //      the bottom of lakes and rivers" —— **游得很差的底栖鱼**。
            //      中钩后的真实表现是"沉底打桩 + 蛮力横向顶"，不是鲑鳟那种长直线冲刺。
            //      → 所以**不用 burst**（burst 是"挣扎时游得飞快、冲刺狠而频"，与
            //        "poor swimmer" 的习性直接矛盾），而用 **sluggish**：
            //        拉不快（pullSpeed 1.90 是紫金档的合理值，但远低于同档 burst 的
            //        2.3~2.6），张力涨得快（1.50 —— 大鲇鱼摆尾缠线），体力极高（420）
            //        —— 一场"拉不动但拉很久"的持久战，正好是鲇鱼的真实手感。
            //    ★ 档位 P_G（紫~金）· 仅路亚 · 出现水域 = 河流 / 湖泊 / 沼泽（见 FishWaters）
            new FishSpecies("north_african_catfish", ModItems.NORTH_AFRICAN_CATFISH,
                    2.000, 16.000, P_G,
                    sluggish(2.85, 420, 1.5), RodKind.LURE),

            // ================================================================ 第 1 批：冷水 5 条（用户 2026-10-08）
            //
            // 这一批同时验证**两条叠加修饰**：高寒湖（北极红点鲑 / 高白鲑）与
            // 冷水溪流（虹鳟 / 江鳕 / 哲罗鲑）。出鱼判定见 {@link FishWaters} 的 putMods ——
            // 这五条的本体掩码是 0，**只在命中的修饰水域里出现**；反过来，
            // 普通的湖鱼/河鱼照旧出现在这些水域里（高山湖里当然也有普通湖鱼）。

            //  虹鳟 = Oncorhynchus mykiss（鲑科 · 太平洋鲑属）
            //    ⚠ 中文名 2026-10-11 修正：**虹鳟不是"三文鱼"** —— "三文鱼"指大西洋鲑
            //      *Salmo salar*（Salmo 属），虹鳟是**太平洋鲑属** Oncorhynchus，两者不同属；
            //      国内"淡水三文鱼"是营销说法，而且淡水鱼生食确有寄生虫风险
            //      ⇒ 在料理线上它落 L2（生食概率反胃，见 FishPalatability）。
            //    真实数据：常见 25~45cm / 0.5~2kg，养到 5kg 以上不稀奇。本种取 0.4~8kg。
            //    习性：冷水高溶氧的**活水**，顶流而居。与褐鳟最显眼的差别是"跳"——
            //      中钩后频繁跃出水面（资料里写作 acrobatic jumps），而不是一味下潜顶水。
            //    → 与褐鳟（burst 2.30 / 体力 200 / 张力 1.60 的"粘稠手感"）**刻意错开**：
            //      虹鳟给 burst 2.10 / 170 / 1.30 —— 跳得凶、泄得快、线没那么黏。
            //      两条鱼放在同一条溪流里，玩家应当能凭手感分开。
            //    档位 P_G（紫~金）· 仅路亚 · 出现水域 = 冷水溪流
            new FishSpecies("rainbow_trout", ModItems.RAINBOW_TROUT, 0.400, 8.000, P_G_R,
                    burst(3.975, 280, 1.9), RodKind.LURE),

            //  北极红点鲑 = Salvelinus alpinus（鲑科 · 红点鲑属）
            //    真实数据：常见 0.5~3kg，大型湖型可到 7kg 以上。本种取 0.5~7kg。
            //    习性：分布**最北**的淡水鱼之一，高寒湖与冷水溪流的耐力型选手；
            //      繁殖期腹部转橙红（婚姻色），"红点鲑"这名字就是这么来的。
            //    → 用户点名"耐力型招牌，和爆发型形成反差"：sluggish 1.50 / **420 体力** / 1.30。
            //      420 是高寒湖里最高的体力（草鱼 220 / 青鱼 320 / 镜鲤 380 都在别的池子里），
            //      配合 sluggish 的低冲刺频率 → 一场"拉不快但磨很久"的持久战。
            //    档位 G_R（金~红）· **通用竿** · 出现水域 = 高寒湖
            //    ⚠ 它让"台钓红鱼"多了一种（原白名单只有蓝宝石雷龙与孔雀鱼）。
            //      这是用户 2026-10-08 批准 docs/33 §9.2 那份名单时一并批准的，
            //      check-new-fish.js 的 FLOAT_RED_ALLOWED 里已补上并注明出处。
            new FishSpecies("arctic_char", ModItems.ARCTIC_CHAR, 0.500, 7.000, G_R,
                    sluggish(3.975, 480, 2.3), RodKind.BOTH),

            //  江鳕 = Lota lota（鳕科 · 江鳕属）—— **唯一的淡水鳕科**
            //    真实数据：常见 40~60cm / 1~3kg，纪录级可到 25kg 上下。本种取 0.8~12kg。
            //    习性：**底栖**、冷水、夜行；**下颌一根须**（鳕科标志，淡水里很显眼）。
            //      中钩后是强烈的"缠线打桩"—— 英文俗名 lawyer 就是因为缠得难解。
            //    → sluggish 1.30 / 260 体力 / **张力 1.45**：打桩缠线的黏滞感用**张力**给，
            //      不用速度给（它本来就游不快）。
            //    档位 B_P（蓝~紫）· 仅台钓 · 出现水域 = 冷水溪流
            new FishSpecies("burbot", ModItems.BURBOT, 0.800, 12.000, G_B_P,
                    sluggish(1.95, 260, 1.45), RodKind.FLOAT),

            //  高白鲑 = Coregonus lavaretus（鲑科 · 白鲑属）
            //    真实数据：常见 30~50cm / 0.3~2kg，大型湖泊个体更大。本种取 0.2~3kg。
            //    习性：**中层**群游的小型浮游动物食性鱼，嘴小；白鲑属的鉴别点是
            //      **无须**、口小、体银白 —— 与同域的鲑属/红点鲑属一眼分开。
            //    → steady 0.90 / 140 体力 / 1.05：拉起来轻、方向稳、几乎不冲刺。
            //      它的真正职责是让高寒湖里**有非金红鱼可稀释**——否则北极红点鲑
            //      （金红）在高寒湖里会接近 100% 占比，重演洞穴那次"几竿三条雷龙"。
            //    档位 G_B（绿~蓝）· 仅台钓 · 出现水域 = 高寒湖
            new FishSpecies("whitefish", ModItems.WHITEFISH, 0.200, 3.000, G_B_P,
                    steady(1.35, 140, 1.05), RodKind.FLOAT),

            //  哲罗鲑 = Hucho taimen（鲑科 · 哲罗鱼属）—— **世界最大的鲑科**
            //    真实数据：常见 5~30kg，纪录级 1.5m 以上 / 60kg 级。本种取 3~45kg
            //      （木竿承重 8kg、铁竿 20kg 都拉不动它 —— 得金/钻/下界合金竿，
            //       并且会真实触发"超重会烧竿"那条规则）。
            //    习性：大江上游深潭的顶级掠食者，吞食鲑苗、水鸟甚至落水的小型哺乳动物；
            //      中钩后是**长直线冲刺 + 极高耐力**，与大西洋鲑同型但更"耗"。
            //    → burst 2.90 / **520 体力** / 1.35：拉力略低于洄游型的大西洋鲑（3.125，
            //      人家在海里长到成体），但体力取鲑科最高 —— 淡水里的"终极"是**磨**出来的，
            //      不是拽出来的。作为对照：巨骨舌鱼 520 体力但只有 1.80 的慢拉力。
            //    档位 G_R（金~红）· 仅路亚 · 出现水域 = 冷水溪流
            new FishSpecies("taimen", ModItems.TAIMEN, 3.000, 45.000, G_R,
                    burst(4.8, 560, 1.45), RodKind.LURE),

            // ================================================================ 第 3 批：极地 9 条（用户 2026-10-08）
            //
            // 这一批全部只在 **POLAR（极地水）** 出现 —— 判据是"群系常年覆冰且非高山"
            // （见 WaterBody.iceFishingBiome），与水体本体无关：冻河、冻洋、雪原湖都算 ✓
            //
            // ⚠ 数值口径（docs/进行中 第 3 批那张表）：**台钓/通用的最高档只到金**（P_G）——
            //   不给台钓再添红鱼（check-new-fish.js 的白名单是用户逐条点名的）。
            // ⚠ 本批 **有 6 条是"稀有档"**（P_G / G_R），绿蓝档只有 3 条 —— 这是刻意的：
            //   极地水是所有水体上的修饰，如果这里塞一堆低档常见鱼，会把老鱼的占比稀释掉
            //   （docs/33 §9.4 的稀释验收）。

            //  北极鳕 = Boreogadus saida（鳕科）
            //    真实数据：常见 15~25cm / 几十克，最大约 40cm。本种取 0.05~0.5kg。
            //    习性：**冰下成群**的小型鳕，北冰洋食物链的底座；下颌略长于上颌、须极小
            //      （与大西洋鳕**正好相反** —— 这是两者的鉴别点）。
            //    → steady 0.60 / 60 体力 / 0.95：拉起来轻、几乎不冲刺，极地的"刷量鱼"。
            //    档位 G_B（绿~蓝）· 仅台钓 · 出现水域 = 极地水
            new FishSpecies("arctic_cod", ModItems.ARCTIC_COD, 0.050, 0.500, G_B,
                    steady(0.9, 60, 0.95), RodKind.FLOAT),

            //  毛鳞鱼 = Mallotus villosus（胡瓜鱼科）
            //    真实数据：常见 10~15cm，最大约 20cm。本种取 0.01~0.1kg。
            //    习性：极地最重要的**饵料鱼**，鲸、鳕、海鸟都吃它；繁殖期雄鱼腹面泛红、
            //      体侧长出两对绒毛状鳞（"毛鳞"名字的由来）。
            //    → steady 0.50 / 40 体力 / 0.90：全模组最小的鱼之一，纯刷量。
            //    档位 G_B（绿~蓝）· 仅台钓 · 出现水域 = 极地水
            new FishSpecies("capelin", ModItems.CAPELIN, 0.010, 0.100, W_G,
                    steady(0.75, 40, 0.9), RodKind.FLOAT),

            //  大西洋鲱 = Clupea harengus（鲱科）
            //    真实数据：常见 25~30cm / 0.1~0.5kg。本种取 0.1~1.0kg。
            //    习性：**群游**中上层小鱼，蓝背银身、**无斑无侧线无脂鳍**（三无）；
            //      黄昏上浮、黎明下潜的昼夜垂直洄游。
            //    → steady 0.85 / 110 体力 / 1.00：方向稳、不冲刺。
            //    档位 B_P（蓝~紫）· 仅台钓 · 出现水域 = 极地水
            new FishSpecies("atlantic_herring", ModItems.ATLANTIC_HERRING, 0.100, 1.000, B_P,
                    steady(1.275, 110, 1), RodKind.FLOAT),

            //  狼鱼 = Anarhichas lupus（狼鳚科）
            //    真实数据：常见 60~100cm / 3~10kg，最大约 20kg。本种取 3~20kg。
            //    习性：**无腹鳍**、背鳍从头连到尾；前部犬齿 + 后部臼齿两套牙，专门咬碎
            //      海胆、贝类（"碎壳专家"）。冷水底层。
            //    → sluggish 1.55 / 300 体力 / **张力 1.50**：咬合力强、摆尾缠线，
            //      黏滞感用**张力**给，不给速度（它游得并不快）。
            //    档位 P_G（紫~金）· 仅台钓 · 出现水域 = 极地水
            new FishSpecies("wolffish", ModItems.WOLFFISH, 3.000, 20.000, P_G,
                    sluggish(2.325, 300, 1.5), RodKind.FLOAT),

            //  平鲉（红鱼）= Sebastes norvegicus（平鲉科）
            //    ⚠ 学名 2026-10-08 修正：Sebastes marinus 是**废弃误用名**
            //      （它的模式标本其实是鮨科的东西），现用 norvegicus（ITIS/WoRMS/FishBase 一致）。
            //    真实数据：常见 30~45cm / 1~5kg，最大约 15kg 且**能活 60 年**（10~12 岁才成熟）。
            //    习性：短身高背、大眼、鲜红（深水里红色等于黑色伪装）；**卵胎生**；
            //      慢速贴底，几乎不追饵。
            //    → sluggish 1.40 / 300 体力 / 1.30：慢、稳、磨。
            //    档位 P_G（紫~金）· 仅路亚 · 出现水域 = 极地水
            new FishSpecies("redfish", ModItems.REDFISH, 1.000, 15.000, G_R,
                    sluggish(4.2, 380, 2.3), RodKind.LURE),

            //  裸盖鱼（银鳕）= Anoplopoma fimbria（黑鲉科）
            //    真实数据：常见 60~100cm / 5~20kg，最大约 40kg 且**能活 94 年**。
            //    ⚠ 分类常被弄错：它是**鲉形目**（与平鲉近缘），不是鳕形目；
            //      "银鳕"是日语系叫法；中文语境的"银鳕鱼"是**贸易名** —— 市场上叫"银鳕鱼"的
            //      通常实指**犬牙鱼** *Dissostichus* spp.（南极犬牙鱼 / 巴塔哥尼亚犬牙鱼），
            //      那既不是本种、也不是鳕形目。所以"**贸易名**银鳕鱼"的汞报道与"裸盖鱼汞高"
            //      其实是**两件事**，只是恰好都指向同一种深水大鱼
            //      （本模组的累积汞依据见 docs/料理适配策划案.md §3）。
            //    习性：深水泥底的慢速食腐/掠食者，两个背鳍分离。
            //    → sluggish 1.60 / 380 体力 / 1.25：深水大鱼，上来得慢。
            //    档位 P_G（紫~金）· 仅路亚 · 出现水域 = 极地水
            new FishSpecies("sablefish", ModItems.SABLEFISH, 5.000, 40.000, B_P_G,
                    sluggish(2.4, 380, 1.25), RodKind.LURE),

            //  大西洋鳕 = Gadus morhua（鳕科）—— 极地主力
            //    真实数据：常见 60~100cm / 5~20kg，历史纪录 1.8m / 96kg。本种取 2~40kg。
            //    习性：上颌突出 + 一条**显眼的下颌长须** + 浅色侧线；
            //      底栖掠食，纽芬兰大渔场因它而兴、也因它而崩。
            //    → steady 1.70 / 320 体力 / 1.15：不快不慢、耐力足，一米级的硬骨头。
            //    档位 P_G（紫~金）· **通用** · 出现水域 = 极地水
            new FishSpecies("atlantic_cod", ModItems.ATLANTIC_COD, 2.000, 40.000, P_G,
                    steady(2.55, 320, 1.15), RodKind.BOTH),

            //  格陵兰大比目鱼 = Reinhardtius hippoglossoides（鲽科）
            //    真实数据：常见 60~120cm / 20~70kg，最大可达 200kg 级。本种取 20~200kg
            //      （**木竿/铁竿都拉不动** —— 会真实触发"超重会烧竿"）。
            //    习性：比目鱼里少见的**会离底上浮捕食**的一种（主食毛鳞鱼/极地鳕），
            //      两只眼都挤在背脊那一侧。
            //    → sluggish 1.90 / 600 体力 / 1.45：贴底巨物，拉不动但极耐。
            //    档位 G_R（金~红）· 仅路亚 · 出现水域 = 极地水
            new FishSpecies("greenland_halibut", ModItems.GREENLAND_HALIBUT, 20.000, 200.000, P_G,
                    sluggish(2.85, 600, 1.45), RodKind.LURE),

            //  格陵兰鲨 = Somniosus microcephalus（睡鲨科）—— 极地终极
            //    真实数据（本次调研**推翻了三条常见说法**，别照抄网上那套）：
            //      · "6~7 米 / 1 吨"站不住：FishBase 把 7.3m 标为 unverified，**1 吨查无来源**；
            //        实测最大 502cm、可信上限约 550cm（雌）；
            //      · "90~100% 被眼寄生虫感染"是**太平洋睡鲨**的数据，本种没有同行评议的感染率；
            //      · "几乎全盲"已被 Fogg et al. 2026 (Nature Comms) 推翻 —— 视网膜完整、
            //        暗光视觉高度专门化，正确说法是"角膜常被寄生虫附着→视力严重受损"。
            //      寿命 ≥272 年、约 156 岁才性成熟、巡航 **0.34 m/s**（全模组最慢）。
            //    → sluggish 2.20 / **900 体力** / 1.30：拉力其实不高（它真的游得慢），
            //      但体力是全模组第一 —— 传说级就该是"你拉得动一点点，但要拉一辈子"。
            //      本种取 80~500kg：**必须钻/下界合金竿**，木竿铁竿必烧。
            //    档位 G_R（金~红）· 仅路亚 · 出现水域 = 极地水
            new FishSpecies("greenland_shark", ModItems.GREENLAND_SHARK, 80.000, 500.000, G_R,
                    sluggish(4.8, 400, 1.75), RodKind.LURE),

            // ================================================================ 第 4 批：远洋 5 条（用户 2026-10-08 逐条定档）
            //
            // 档位由用户定案：旗鱼 金红 / 鲯鳅 金红 / 鳄雀鳝 **紫金红** / 巨暹罗鲤 金红 / 电鳗 紫金。
            // ⚠ **电鳗不做放电机制**（用户原话："放不放电对手感影响不大"）——
            //   所以这一批**没有任何新机制**，纯加鱼 ✓（新机制最容易出 bug，能省就省）。

            //  雨伞旗鱼 = Istiophorus platypterus（旗鱼科）
            //    真实数据：常见 1.5~2.5m / 30~60kg，纪录约 90kg。本种取 20~90kg。
            //    习性：背鳍展开如帆（种名 platypterus 即"宽鳍"）；吻突用于抽打鱼群、常集体围猎。
            //    ⚠ **"最快 110 km/h"要按谣传处理**（调研结论）：Svendsen 2016（Biology Open,
            //      PMC5087677）按肌肉收缩时间测得**上限 8.3±1.4 m/s ≈ 30 km/h**，并明写
            //      "sailfish are incapable of exceeding 10-15 m/s"；那个 110 km/h 出自
            //      **一次垂钓观测（3 秒拉出 100 码线，且计时时鱼正在跃出水面）**。
            //      → 图鉴文案可以写"常被引用的说法"，**数值不许用 110**。
            //      又：按体长校正后旗鱼反而是这批里最慢的 —— 但它仍是**爆发型**猎手，
            //      所以手感上给"冲刺顶点"，不给"耐力"。
            //    → burst **3.50** / 300 体力 / 1.20：**全模组最高拉力**（速度顶点就该在这儿，
            //      比蓝鳍金枪鱼的 3.4375 再高一点），但体力只有 300 —— 冲刺之王，不是耐力之王。
            //    档位 G_R（金~红）· 仅路亚 · 出现水域 = 深海
            new FishSpecies("sailfish", ModItems.SAILFISH, 20.000, 90.000, G_R,
                    burst(6.3, 380, 1.25), RodKind.LURE),

            //  鲯鳅 = Coryphaena hippurus（鲯鳅科）
            //    真实数据：常见 0.5~1.5m / 3~15kg，最大约 40kg。本种取 1~20kg。
            //    习性：**生长极快**（数月性成熟、寿命通常不超过 4~5 年）、捕食迅猛；
            //      体色金蓝绿、**离水后迅速褪色**；雌雄异形（雄鱼额部隆起成方头）。
            //      中文别名"鱰""鬼头刀"。
            //    → burst 2.70 / 200 体力 / 1.10：快而脆（现实里也是"爆发式繁殖的中型掠食者"）。
            //    档位 G_R（金~红）· 仅路亚 · 出现水域 = 深海
            new FishSpecies("mahi_mahi", ModItems.MAHI_MAHI, 1.000, 20.000, G_R,
                    burst(5, 240, 1.55, 0.378), RodKind.LURE),

            //  鳄雀鳝 = Atractosteus spatula（雀鳝科 · **大雀鳝属**）
            //    真实数据：常见 1.2~2m / 20~50kg，最大 3m / 140kg 级。本种取 20~80kg。
            //    习性：**不是"鳄鱼"也不是雀鳝属的普通种** —— 它是大雀鳝属唯一的大型种；
            //      能用鳔**吞咽空气辅助呼吸**（所以能活在低氧死水）；**卵有毒**；
            //      硬鳞（gadroidean 型）像铠甲；**行动笨重但极耐缠斗**。
            //    ⚠ 入侵种身份有**最高级别依据**（调研找到的，别只写"新闻报道"）：
            //      中国政府网 2022-12-23《重点管理外来入侵物种名录》（六部门联合发布，
            //      鱼类 3 种：鳄雀鳝 / 豹纹翼甲鲶 / 齐氏罗非鱼，2023-01-01 施行）。
            //      ⚠ 别写成"一直在《中国外来入侵物种名单》上" —— 2022 年 8 月时它**还不在**旧名单里。
            //    → erratic 2.30 / 400 体力 / 1.40：中钩后是猛烈翻滚挣扎（不是长直线冲刺）。
            //    档位 **P_G_R（紫~金~红）** · 仅路亚 · 出现水域 = 河流 / 湖泊（入侵种）
            new FishSpecies("alligator_gar", ModItems.ALLIGATOR_GAR, 20.000, 80.000, P_G_R,
                    erratic(4.425, 480, 1.55), RodKind.LURE),

            //  巨暹罗鲤 = Catlocarpio siamensis（鲤科）
            //    真实数据：**世界最大的鲤科鱼类**。⚠ 常被引用的"1918 年 300kg 纪录"经调研
            //      **查不到**：可追的链条是 FishBase 300kg → Ref.10431（1994 年调查论文，
            //      不是纪录报告）→ 钓获纪录库标成 questionable/anecdotal；
            //      **FishBase 自己就写着"Maximum length of 300 cm needs confirmation"**。
            //      所以本种取 **30~200kg**（保守上限，注释里带这条存疑说明）。
            //      IUCN：**CR (A2abcd)，评估日 2011-04-05**（Hogan Z.）。
            //    ⚠ 中文名两点：FishBase 的正式中文名是**「暹罗印度鲤」**，"巨暹罗鲤"是别名 ✓
            //      （沿用用户名单里的叫法 ✓）；而"巨鲶鲤"**不是本种**（疑与湄公河巨鲶混淆 ✗）。
            //    习性：头大、无须（或须极退化）、以水生植物与藻类为食；成年在河流深潭、幼鱼在泛滥平原。
            //    → sluggish 1.95 / **600 体力** / 1.60：巨型鲤科的"拉不动但极耐"，
            //      和巨骨舌鱼（1.80/520/1.75）一个路子、但更重更磨。
            //    档位 G_R（金~红）· **仅台钓** · 出现水域 = 河流
            //    ⚠ 它是**台钓红鱼** —— 用户 2026-10-08 点头进了白名单
            //      （check-new-fish / check-fish-species / check-env-table 三处都要加 ✓）。
            new FishSpecies("giant_siamese_carp", ModItems.GIANT_SIAMESE_CARP, 30.000, 200.000, G_R,
                    sluggish(3.975, 480, 1.6), RodKind.FLOAT),

            //  电鳗 = Electrophorus electricus（电鳗目 · 裸背电鳗科）
            //    ⚠ 学名 2026-10-11 修正：**"电鳗"曾经是一个种，现在是三个** ——
            //      2019 年 de Santana et al.（Nature Communications 10:4000）按分布把属拆成
            //      **圭亚那地盾的 E. electricus s.s.**、**巴西地盾的 E. voltai「伏特电鳗」**
            //      与**亚马孙低地的 E. varii**。本模组按 **E. electricus s.s.**
            //      （FishBase：restricted to the Guiana Shield）记；
            //      网上最常引用的 **860V 纪录属于 E. voltai**，别安到本种头上。
            //    真实数据：本种（**2019 年拆分后的 *E. electricus* s.s.**）实测最大约 1000mm TL，
            //      本种取 **1~12kg**。
            //    ⚠ 调研推翻了三处常见写法，别照抄网上那套：
            //      · **不是三种放电** —— Catania 原文只说**两种 EOD**（波形同为约 1ms 单相脉冲，
            //        只是强度不同）；可辩护的三分法是"低压定位／低压通讯·诱肌颤／高压捕猎防御"，
            //        而后两者共用同一波形；
            //      · **860V 不属于本种** —— 那是 *E. voltai*（n=1）；*E. electricus* 实测 **480V**；
            //      · **"250cm / 20kg"是 2019 年拆分前的属级记录**，无法归属到本种 ✗；
            //        另外"约 1 安培"本轮**任何来源都没找到**（唯一电流值是 40~50mA），不要引用。
            //    习性：**不是鳗**（属电鳗目，与鲶形目近缘）；夜行、浑水底栖，靠电定位与捕猎；
            //      放电显著耗能，需要休息与进食才恢复。
            //    → erratic 1.80 / 260 体力 / **1.45**：长条身体扭动缠绕（黏滞感给张力）。
            //    档位 **P_G（紫~金）** · 仅路亚 · 出现水域 = 沼泽 / 河流
            //    ⚠ **不做放电机制**（用户 2026-10-08："放不放电对手感影响不大"）✓
            new FishSpecies("electric_eel", ModItems.ELECTRIC_EEL, 1.000, 12.000, P_G,
                    erratic(2.7, 260, 1.45), RodKind.LURE)
    );

    /**
     * 按 id 查鱼种，找不到返回 {@code null}。
     *
     * <p>为什么返回 null 而不是抛异常：调用方大多是**显示**路径
     * （鱼的物品提示、图鉴详情），而物品是可以被指令/存档编辑塞进任意
     * 鱼种 id 的。为一个"背包里有一条数据损坏的鱼"把玩家的客户端搞崩，
     * 代价远大于"这一行提示不显示"。
     *
     * <p>26 条鱼的线性查找：调用频率是"打开提示/打开图鉴"级别，
     * 建索引带来的状态维护成本比这点查找高。
     */
    public static FishSpecies byId(String id) {
        if (id == null) {
            return null;
        }
        for (FishSpecies s : ALL) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    /** 是否认识这个鱼种 id（供图鉴与指令校验用）。 */
    public static boolean isKnown(String id) {
        return byId(id) != null;
    }


    // ================================================================ 运行期数值
    //
    //  下面三个取值方法走 {@link FishTuning}（即配置文件），而不是 record 字段。
    //  record 的组件是 spec* 开头的那几个 —— "源码里写着的规格值"，
    //  运行期一律不读，只由 tools/check-fish-config-parity.js 逐项比对
    //  它们与配置默认值一致，防止记录和实际悄悄分叉。

    /** 体重下限（kg），运行期从配置取。 */
    public double kgMin() {
        return FishTuning.minKg(id);
    }

    /** 体重上限（kg），运行期从配置取。 */
    public double kgMax() {
        return FishTuning.maxKg(id);
    }

    /**
     * 对抗手感档案，运行期从配置取。
     *
     * <p>每次调用新建一个 {@link FightProfile} —— 一场对抗只在咬钩进 FIGHT 时
     * 取一次快照，所以"改配置只影响下一场、不影响正在遛的这条鱼"是天然成立的。
     */
    public FightProfile effectiveProfile() {
        return FishTuning.profile(id, specProfile.archetype());
    }

    /** 源码记录的体重下限（仅供对照，运行期不要用）。 */
    public double specMinKgValue() {
        return specMinKg;
    }

    /** 源码记录的体重上限（仅供对照，运行期不要用）。 */
    public double specMaxKgValue() {
        return specMaxKg;
    }

    /** 这种鱼最低能出到几档（用于按竿的承重筛鱼）。 */
    public int minTier() {
        int m = tiers[0];
        for (int t : tiers) {
            m = Math.min(m, t);
        }
        return m;
    }

    /** 这种鱼最高能出到几档。 */
    public int maxTier() {
        int m = tiers[0];
        for (int t : tiers) {
            m = Math.max(m, t);
        }
        return m;
    }

    // ------------------------------------------------------------------ 品质 → 手感
    //
    //  ⚠️ 下面这些系数**不是独立生效的**，它们全部要和竿/线/轮的合成属性相乘
    //  （合成见 PartComposer），形成"鱼 vs 装备"的对决：
    //
    //    张力上涨  = 收线/放线基准 × 控杆关系 × 线.tensionGen × 鱼.tierSpeed × 鱼.tensionGain
    //                └─ 线越高级 tensionGen 越小；鱼越高级 tierSpeed 越大
    //    张力上限  = 100 × 线.tensionCeiling(60%) × 竿.tensionCeiling(40%)
    //                └─ 鱼不参与——**这条线是玩家自己的装备决定的**
    //    线被拽远  = 鱼.tierSpeed × 鱼.pullSpeed  −  竿.pullIdle × 轮.pullIdle
    //                └─ 鱼游得快就拽得远，但好渔轮拉得回来
    //    鱼力扣除  = 0.20/秒 × 张力效率 × 控杆关系 × 轮.staminaDrain × 竿.staminaDrain
    //                ÷ 鱼.tierStaminaResist
    //                └─ 装备越好扣得越快，鱼越高级越抗磨
    //
    //  所以同一档鱼，用木竿麻线是硬仗、用合金竿PE线是碾压；
    //  反过来同一个人钓白档和红档，难度差好几倍。这就是"参数和鱼竿有联系"。

    /**
     * 品质档位 → **游动速度倍率**（用户定案："品质越高的鱼相对越快"）。
     *
     * <p>每档 ×1.12，六档下来是 1.00 → 1.76；**再乘一层手感修正**
     * （见 {@link #tierFeelSpeedScale(int)}，用户 2026-10-05 定案）：
     * 金档 ×0.84 → 1.32、红档 ×1.05 → 1.85 —— 金红之间的差距从 12% 拉到 40%。
     * 这一个系数同时影响三件事（本质是同一件事：鱼游得快）：
     * <ul>
     *   <li>张力上涨速度 —— 和**鱼线的 tensionGen** 相乘</li>
     *   <li>线被拽出去的速度 —— 和**渔轮的 pullIdle** 对抗</li>
     *   <li>漂被拖动得多急 —— 玩家最直观的感受</li>
     * </ul>
     */
    public static double tierSpeed(int tier) {
        return Math.pow(Tuning.tierSpeedBase(), Math.max(0, Math.min(5, tier)))
                * tierFeelSpeedScale(tier);
    }

    /**
     * 品质档位 → **游速手感修正**（用户 2026-10-05 定案）。
     *
     * <p>原话："发现金档和红手感可能没有差距，游速体力都一样。
     * 现有金游速降 16%、体力不变；红移速加 5%、体力加 50%。"
     *
     * <p>它是乘在 {@link #tierSpeed(int)} 底数曲线**上面**的，所以白绿蓝紫四档一字不动，
     * 只有金红两档被拉开：档位从此不只决定体重/经济价值，也决定手感。
     *
     * <p>⚠ 体力那一半**不在这里**，而是 {@link #tierFeelStaminaScale(int)}：
     * 游速每 tick 都要读，体力只在咬钩那一刻定池子上限，两个时机不一样。
     */
    public static double tierFeelSpeedScale(int tier) {
        if (tier == 4) {
            return Tuning.tierGoldSpeedScale();
        }
        if (tier == 5) {
            return Tuning.tierRedSpeedScale();
        }
        return 1.0;
    }

    /**
     * 品质档位 → **体力池手感修正**（用户 2026-10-05 定案：金档体力不变、红档 +50%）。
     *
     * <p>乘在 {@code FightProfile.stamina()} 上，决定的是**咬钩那一刻**就定死的
     * 体力池上限（{@code FishingSession.fishStaminaMax}），不是每 tick 的消耗速率 ——
     * 消耗速率那条线已经有 {@link #tierStaminaResist(int)}（每档 ×1.18）在管。
     *
     * <p>为什么不塞进 {@code FightProfile}：{@code FightProfile} 是**按鱼种**存的快照，
     * 同种鱼的金档与红档共用同一份；档位是**这一条个体**的事，两个维度不能混。
     */
    public static double tierFeelStaminaScale(int tier) {
        if (tier == 4) {
            return Tuning.tierGoldStaminaScale();
        }
        if (tier == 5) {
            return Tuning.tierRedStaminaScale();
        }
        return 1.0;
    }

    /**
     * 红档的**体力基准下限**（用户 2026-10-06 定案："给所有红档鱼设体力池下限：基准至少 200"）。
     *
     * <p>起因："有些红鱼似乎体力基准连 200 都没到。" 确实 —— 能出红档的 11 种鱼里有三种：
     * 蓝宝石雷龙鱼 130（台钓**唯一**的红鱼，且体重区间 100% 落在红档）、虎鱼 120、孔雀鱼 60。
     * 而"遛多久"看的是 {@code 体力池 ÷ tierStaminaResist}：
     * 雷龙红档 (130×1.5)÷2.29 ≈ 85，比一条普通草鱼（蓝档 220÷1.39 ≈ 158）还好遛 ——
     * 一条"红档"鱼比低档鱼还软，档位就失去意义了。
     *
     * <p>⚠ 抬的是**基准**、不是最终池子：调用方先抬到下限，**再**乘
     * {@link #tierFeelStaminaScale(int)} —— 所以默认下限 200 的红档鱼实际是 300。
     * 这个顺序是有意的：两个旋钮各管各的（下限管"红档不该太软"，
     * 倍率管"红档整体比金档厚 50%"），调其中一个不会把另一个的效果吃掉。
     *
     * <p>只对**红档**生效：金档的虎鱼（基准 120）原样不动。
     *
     * @param tier        该鱼个体实际抽到的档位（0 白 … 5 红）
     * @param baseStamina 该鱼种的基准体力（配置里的那一条）
     * @return 抬过下限的基准体力
     */
    public static double redStaminaFloor(int tier, double baseStamina) {
        if (tier != 5) {
            return baseStamina;
        }
        return Math.max(baseStamina, Tuning.tierRedStaminaMin());
    }

    /**
     * 品质档位 → **体力抗性倍率**（高级鱼"更耐遛"）。
     *
     * <p>每档 ×1.18。它直接**除**在收线扣鱼力的公式里，
     * 和**渔轮的 staminaDrain × 鱼竿的 staminaDrain** 对冲：
     * 装备好就扣得快，鱼高级就抗磨，两边拉锯。
     */
    public static double tierStaminaResist(int tier) {
        return Math.pow(Tuning.tierStaminaResistBase(), Math.max(0, Math.min(5, tier)));
    }

    /** 这条鱼的实际游动速度倍率（按它的档位）。 */
    public static double speedForWeight(FishSpecies s, double weightKg) {
        return tierSpeed(s.tierForWeight(weightKg));
    }

    /**
     * **收线速率抵抗系数** —— 用户 2026-10-06 定案（所有鱼都有）。
     *
     * <p>用户原话："对于收线速率高于 1.2 的鱼竿配置（或称之为双拉属性，一般由渔轮负责，
     * 比如水滴轮是 1.9 左右），各个档位的鱼有不同的抵抗，比如白是回拉速率降低 5%，
     * 绿 10%，蓝 15%，紫 20%，金 25%，红 32%。对于不同策略的鱼该系数可能上下浮动至多 5%。
     * 而该速率的最终影响可以由压制阻尼转化抵消部分，至多抵消不超过该系数的 75%。
     * 通过合理的设计使得金轮大致触碰到转化极限。"
     *
     * <p>公式：{@code 档位基准 × (1 + clamp(−5%, +5%, 策略浮动)) × (1 − clamp(0, 75%, 阻尼抵消))}，
     * 阻尼抵消 = {@code (压制阻尼 − 1) × 0.5} —— 系数取 0.5 是**为金轮倒推**的：
     * 大型纺车轮阻尼 2.50 → 0.75，正好贴住 75% 上限（用户要求"金轮触碰转化极限"）。
     *
     * @param tier   这条鱼个体实际抽到的档位（0 白 … 5 红）
     * @param arch   这条鱼的策略
     * @param damping 这根竿配出来的压制阻尼（渔轮提供，见 PartComposer.damping）
     * @return 0~1 的抵抗比例（乘在收线速率上）
     */
    public static double reelResist(int tier, FightProfile.Archetype arch, double damping) {
        double base = switch (Math.max(0, Math.min(5, tier))) {
            case 0 -> Tuning.reelResistTierWhite();
            case 1 -> Tuning.reelResistTierGreen();
            case 2 -> Tuning.reelResistTierBlue();
            case 3 -> Tuning.reelResistTierPurple();
            case 4 -> Tuning.reelResistTierGold();
            default -> Tuning.reelResistTierRed();
        };
        double strat = switch (arch) {
            case STEADY -> Tuning.reelResistStratSteady();
            case SLUGGISH -> Tuning.reelResistStratSluggish();
            case BURST -> Tuning.reelResistStratBurst();
            case ERRATIC -> Tuning.reelResistStratErratic();
        };
        // "上下浮动至多 5%"：夹在 [−5%, +5%]
        double stratClamped = Math.max(-0.05, Math.min(0.05, strat));
        double resist = base * (1.0 + stratClamped);
        // 压制阻尼抵消一部分，但**最多 75%**（再好的轮子也留 1/4）
        // ★ 与 tooltip 共用 PartComposer.reelResistOffset（不许在这里再写一遍公式）
        double offset = PartComposer.reelResistOffset(damping);
        return Math.max(0.0, resist * (1.0 - offset));
    }

    /**
     * 把收线速率按抵抗打折 —— **只打超过阈值的那部分**。
     *
     * <p>为什么不整条打折：那样在阈值处会跳变（1.19 不受罚、1.21 掉 5% 之后反而比 1.19 慢），
     * 玩家会觉得"换了个更快的轮子结果更拉不动"。只打超出段既满足"高于 1.2 才开始抵抗"，
     * 又保证**单调**：收线速率越高，打完折仍然越高。
     */
    public static double resistReelSpeed(double pullSpeed, double resist) {
        double threshold = Tuning.reelResistThreshold();
        if (pullSpeed <= threshold) {
            return pullSpeed;
        }
        return threshold + (pullSpeed - threshold) * Math.max(0.0, 1.0 - resist);
    }

    /**
     * **保底挣扎秒数** —— 用户 2026-10-06 定案。
     *
     * <p>用户原话："现有的疲劳体系让红金等鱼过于容易拉上来，就是只要拉到一次力竭之后
     * 这条鱼即使再进入挣扎也不会超过一秒。我需要增加一个保底挣扎系数，**注意我说的挣扎
     * 指的是鱼不力竭的状态**。该系数决定一条鱼保底挣扎的秒数，1 代表一秒。根据鱼的档位
     * 这个系数增加，白 1 红 6 基准，再根据鱼的策略向上至多浮动 1，无向下浮动。"
     *
     * <p>落地语义：每**进入一轮挣扎**，这条鱼至少有这么多秒是**没力竭**的 ——
     * 轮次开头把疲劳标记清掉，且这段时间内疲劳不能重新点亮（见 {@code FishingSession}
     * 的 {@code freshStruggleTicks}）。于是"拉到一次力竭就整条报废"变成
     * "**每一轮都得重新把它遛软**"。
     *
     * <p>公式：{@code 白档基准 + 档位 × 每档增加 + clamp(0, 1, 策略加成)} ——
     * 默认白 1.0、每档 +1.0（红 = 6.0），策略加成只**向上**浮动、最多 +1。
     *
     * @param tier 这条鱼个体实际抽到的档位（0 白 … 5 红）
     * @param arch 这条鱼的手感策略（{@link FightProfile.Archetype}）
     */
    public static double floorStruggleSeconds(int tier, FightProfile.Archetype arch) {
        double base = Tuning.floorStruggleBase()
                + Math.max(0, Math.min(5, tier)) * Tuning.floorStrugglePerTier();
        double bonus = switch (arch) {
            case STEADY -> Tuning.floorStruggleBonusSteady();
            case SLUGGISH -> Tuning.floorStruggleBonusSluggish();
            case BURST -> Tuning.floorStruggleBonusBurst();
            case ERRATIC -> Tuning.floorStruggleBonusErratic();
        };
        // "向上至多浮动 1，无向下浮动"：加成夹在 [0,1]，负值一律当 0
        return base + Math.max(0.0, Math.min(1.0, bonus));
    }

    /** 这条鱼的实际体力抗性倍率（按它的档位）。 */
    public static double staminaResistForWeight(FishSpecies s, double weightKg) {
        return tierStaminaResist(s.tierForWeight(weightKg));
    }

    // ------------------------------------------------------------------ 挣扎循环
    /**
     * 挣扎持续秒数（用户定案：2~10 秒，**越高级挣扎越久**）。
     *
     * <p>按品质档线性插值：白 2s → 绿 3.6s → 蓝 5.2s → 紫 6.8s → 金 8.4s → 红 10s。
     */
    public static double struggleSeconds(int tier) {
        return Tuning.struggleSecondsBase() + Math.max(0, Math.min(5, tier)) * Tuning.struggleSecondsPerTier();
    }

    /**
     * 休息持续秒数（用户定案：5~3 秒，**越高级休息越短**）。
     *
     * <p>按品质档线性插值：白 5s → 绿 4.6s → 蓝 4.2s → 紫 3.8s → 金 3.4s → 红 3s。
     * 和挣扎时长正好反向，于是高级鱼"忙得多、歇得少"，压迫感自然就上来了。
     */
    public static double restSeconds(int tier) {
        return Tuning.restSecondsBase() - Math.max(0, Math.min(5, tier)) * Tuning.restSecondsPerTier();
    }

    /**
     * 按体重判定这条鱼是第几档品质。
     *
     * <p>把 [kgMin(), kgMax()] **等分**成 {@code tiers.length} 段，
     * 每段对应 tiers 里的一个档位。这样"越大越值钱"是单调的，
     * 玩家看到鱼名颜色就知道这条够不够大。
     */
    /**
     * 本鱼种的**档位分界权重**（等长于 {@code tiers}）。
     *
     * <p><b>为什么不能等分：</b>原来是 {@code idx = floor(ratio * n)}，
     * 把体重区间**平均**切成 n 份，每档占 1/n。
     * 对档位表只有两档的鱼（比如金红鱼的 {@code {4,5}}，n=2），
     * 这直接意味着**金鱼和红鱼永远五五开**——"档5 比 档4 稀有"这个设定
     * 在鱼种内部完全插不上手（那张基础权重表管的是"这个鱼种整体出现多少"）。
     *
     * <p>但这**不是**"几十杆看不到金红鱼"的原因：整体出现率是
     * {@link #buildPool} 的权重决定的，跟这条分界无关。
     * 这里只负责让"红比金稀有"这件事在鱼种内部也成立。
     *
     * <p>权重越小 → 该档越稀有。低档保持平均，金收窄到 1/2，红再收窄。
     */
    public int[] tierSplit() {
        int n = tiers.length;
        int[] out = new int[n];
        for (int i = 0; i < n; i++) {
            out[i] = switch (tiers[i]) {
                case 5 -> 2;
                case 4 -> 5;
                default -> 6;
            };
        }
        return out;
    }

    /**
     * 体重 → 品质档（**按 {@link #tierSplit()} 的权重划分区间**）。
     *
     * <p>边界必须偏上取：{@code floor} 在体重正好压在分界线上时会掉档。
     * 比如鲻鱼（0.3~2.5kg，两档）分界在 1.4kg，
     * 而 {@code (1.4 - 0.3) / 2.2} 在浮点里是 {@code 0.49999999999999994}，
     * {@code floor} 判成低一档。饵料下限拿它做硬判定，
     * 于是"金饵不出蓝档以下"会漏 0.1%（离线模拟 2 万次抓到 17 条）。
     */
    public int tierForWeight(double weightKg) {
        int n = tiers.length;
        int[] split = tierSplit();
        int total = 0;
        for (int w : split) {
            total += w;
        }
        if (total <= 0) {
            // 退化情形（tierSplit 全 0）：回到等分，保证永远能判出一档
            double fallbackRatio = (weightKg - kgMin()) / Math.max(1.0e-6, kgMax() - kgMin());
            int idx = (int) Math.floor(fallbackRatio * n + 1.0e-9);
            return tiers[Math.max(0, Math.min(n - 1, idx))];
        }

        double span = Math.max(1.0e-6, kgMax() - kgMin());
        double ratio = Math.max(0.0, Math.min(0.9999999, (weightKg - kgMin()) / span));
        // 权重划分下的累积边界：第 i 档覆盖 [cum_i/total, cum_{i+1}/total)
        double target = ratio * total;
        double cum = 0.0;
        for (int i = 0; i < n; i++) {
            cum += split[i];
            // 1e-9 让正好压在边界上的体重归到上一档（与左闭右开一致）
            if (target < cum - 1.0e-9) {
                return tiers[i];
            }
        }
        return tiers[n - 1];
    }

    // ------------------------------------------------------------------ 手感速写
    //
    //  四种策略**刻意做成互补的取舍**，而不是"一种比另一种强"：
    //
    //             挣扎时游速  张力上涨  变向频率  冲刺      应对
    //   均衡型      中          中        中      中      随便打
    //   爆发型      极快        中        低      狠而频  趁疲劳期反拉
    //   黏液型      慢          极快      低      几乎无  频繁松手，耐心磨
    //   神经质      快          偏高      极高    中      紧跟预告，预判错就飙
    //
    //  参数含义见 FightProfile 的文档；这里只写典型值。

    // ================================================================== 疲劳期倍率
    /**
     * 疲劳期游速倍率——默认 0.5，**定义在 {@code FightProfile.fatiguePull()}**（读 gameplay 配置）。
     *
     * <p>这里只是个别名，方便本文件四个策略函数读。改数值请改那边，
     * 因为 {@code FightProfile.CRUCIAN_CARP}（鲫鱼的具名档案）和 Codec 的默认值
     * 也要跟着走，三个地方必须一致。
     */

    /** 均衡型：各项中庸，新手鱼。 */
    private static FightProfile steady(double pull, double stamina, double tensionGain) {
        return steady(pull, stamina, tensionGain, FightProfile.fatiguePull());
    }

    /**
     * 同 {@link #steady}，但**疲劳期拉力用显式比值**而不是全局旋钮。
     *
     * <p>⚠ 2026-10-17：把玩家调好的数值设为出厂默认时发现，鲻鱼与鲯鳅的"疲劳期/正常"比值
     * 与全局的 0.42 不同（0.315 / 0.378）—— 那两条是他单独调过的。全局旋钮表达不了，
     * 所以留一个显式比值入口，别再把它们拉平。
     */
    private static FightProfile steady(double pull, double stamina, double tensionGain, double fatigueRatio) {
        return new FightProfile(FightProfile.Archetype.STEADY,
                pull, pull * fatigueRatio, 0.8, 4.5, 3.5, 0.0, tensionGain, stamina, 0.30, 2.0, 0.02);
    }

    /**
     * 黏液型：**游得慢，但线黄得飞快**。
     *
     * <p>参数特征：{@code pullSpeed} 低（拽不动）但 {@code tensionGain} 高（线涨得快），
     * 而且几乎不冲刺。应对方式完全不同——不是"趁它歇抢线"，
     * 而是**频繁松手让线回落**，靠持久战磨。
     */
    private static FightProfile sluggish(double pull, double stamina, double tensionGain) {
        return new FightProfile(FightProfile.Archetype.SLUGGISH,
                pull, pull * FightProfile.fatiguePull(),
                0.4,     // 冲刺很短
                8.0,     // 8 秒才冲一次，几乎可以忽略
                5.5,     // 变向很少，方向稳定
                0.0,     // 无假动作
                tensionGain,   // ★ 这个数值明显偏高（见调用处 1.35~1.55）
                stamina, 0.28, 2.6, 0.015);
    }

    /**
     * 爆发型：**挣扎时游得飞快**，冲刺又狠又频。
     *
     * <p>参数特征：{@code pullSpeed} 高、{@code sprintIntervalSec} 短（2.6 秒就冲一次）、
     * 冲刺时长 1.6 秒。疲劳期掉到统一的 0.5——
     * 所以胜负手是"扛住它的冲刺，趁它喘气那几秒拼命抢线"。
     */
    private static FightProfile burst(double pull, double stamina, double tensionGain) {
        return burst(pull, stamina, tensionGain, FightProfile.fatiguePull());
    }

    /** 同 {@link #burst}，疲劳期拉力用显式比值（见 {@code steady} 四参版的说明）。 */
    private static FightProfile burst(double pull, double stamina, double tensionGain, double fatigueRatio) {
        return new FightProfile(FightProfile.Archetype.BURST,
                pull,
                pull * fatigueRatio,
                1.6,           // 冲刺 1.6 秒
                2.6,           // 每 2.6 秒冲一次，很凶
                3.0,
                0.10,
                tensionGain, stamina, 0.34, 2.4, 0.02);
    }

    /**
     * 神经质：**变向极频繁 + 假动作**。
     *
     * <p>参数特征：{@code turnIntervalSec} 只有 1.3 秒（别的鱼 3~5 秒），
     * 而且 35% 概率放**假动作**（预告一个方向、实际反方向）。
     * 玩家得紧跟预告方向，预判错了张力立刻飙——考的是反应不是耐心。
     */
    private static FightProfile erratic(double pull, double stamina, double tensionGain) {
        return new FightProfile(FightProfile.Archetype.ERRATIC,
                pull, pull * FightProfile.fatiguePull(),
                0.9,
                3.0,
                1.3,     // ★ 1.3 秒就变一次向
                0.35,    // ★ 35% 假动作
                tensionGain, stamina, 0.32, 2.0, 0.02);
    }

    // ------------------------------------------------------------------ 抽取

    /** 该鱼种能长的最短体长（cm）——用体重反推的近似值，仅用于显示区间。 */
    public double lengthRangeCm() {
        return 12.0 + Math.cbrt(kgMax()) * 26.0;
    }

    // ------------------------------------------------------------------ 抛投距离
    /**
     * 抛投距离对"高级鱼出现率"的影响（用户定案）。
     *
     * <p>近抛（8 米）水浅、离岸近，大鱼警戒不进；高抛（24 米）能打到深水区，
     * 高级鱼概率反而上升。这就是"想搏大物就得敢往远处抛"的取舍——
     * 代价是距离远、线被拽出去的风险也大。
     *
     * <table>
     *   <tr><th>抛投</th><th>紫</th><th>金</th><th>红</th></tr>
     *   <tr><td>近抛 8 米</td><td>−5%</td><td>−3%</td><td>−1.5%</td></tr>
     *   <tr><td>平抛 16 米</td><td colspan="3">不变</td></tr>
     *   <tr><td>高抛 24 米</td><td>+5%</td><td>+3%</td><td>+1.5%</td></tr>
     * </table>
     */
    public enum CastRange {
        /** 近抛（约 8 米）：高级鱼概率降低。 */
        NEAR,
        /** 平抛（约 16 米）：基准。 */
        LEVEL,
        /** 高抛（约 24 米）：高级鱼概率提高。 */
        FAR;

        /**
         * 该抛投距离下，某个档位的权重修正倍率。
         *
         * <p>只影响紫（3）以上；白绿蓝三档与抛投距离无关——
         * 小鱼到处都是，抛哪都一样。
         */
        public double weightMultiplier(int tier) {
            if (this == LEVEL || tier < 3) {
                return 1.0;
            }
            // 用户给的百分比是"概率变化量"，这里换算成权重倍率
            double delta = switch (tier) {
                case 3 -> 0.05;    // 紫 ±5%
                case 4 -> 0.03;    // 金 ±3%
                default -> 0.015;  // 红 ±1.5%
            };
            if (this == NEAR) {
                return Math.max(0.05, 1.0 - delta * 3.0);   // 降：最多压到 85%~95%
            }
            return 1.0 + delta * 6.0;                        // 升：1.09 ~ 1.30
        }
    }

    /**
     * 这一竿的**菜品效果加成**（docs/28 §3 的钓获类三条：渔运 / 爆口 / 空军）。
     *
     * <p>做成一个参数对象而不是再往签名里塞两个参数：{@code roll} 与 {@code buildPool}
     * 的形参已经 12 个（鱼池的每一层过滤都要留一个），再加两个位置参数，
     * 调用点会变成一长串看不出含义的 {@code null, 0.0}。
     *
     * <p>{@link #NONE} 表示"没有这些效果" —— 抽签路径与加效果之前**逐字节相同**。
     *
     * @param luckPoints    渔运：金红总爆率要加的**百分点**（绝对值，不是倍率）
     * @param speciesWeight 爆口 / 空军：每条鱼的权重倍率（{@code null} = 不加成）
     */
    public record RollBonus(double luckPoints, ToDoubleFunction<FishSpecies> speciesWeight,
                            double[] layerBonus) {

        /** 没有任何钓获类效果。 */
        public static final RollBonus NONE = new RollBonus(0.0, null, null);

        /** 兼容老调用点（菜品效果只带渔运/爆口，不带铅锤）。 */
        public RollBonus(double luckPoints, ToDoubleFunction<FishSpecies> speciesWeight) {
            this(luckPoints, speciesWeight, null);
        }

        /** 挂上铅锤的四层倍率（下标 = {@code FishLayers.Layer.ordinal()}；null = 不启用）。 */
        public RollBonus withLayers(double[] layers) {
            return new RollBonus(this.luckPoints, this.speciesWeight, layers);
        }

        public boolean isEmpty() {
            return this.luckPoints <= 0.0 && this.speciesWeight == null && this.layerBonus == null;
        }
    }

    /**
     * 按"能钓到的鱼种集合 + 品质权重 + 抛投距离"抽一条鱼。
     *
     * <p>权重设计：低品质鱼更常见（白 40 / 绿 26 / 蓝 16 / 紫 10 / 金 6 / 红 2）。
     * 再乘上 {@link CastRange#weightMultiplier}——近抛压高级鱼、高抛抬高级鱼。
     *
     * @param random      随机源（服务端玩家自己的，保证可复现）
     * @param rodKind     这根竿的类型（台钓/路亚），决定能碰到哪些鱼
     * @param maxTier     这根竿最多能出到几档（竿材质的档位上限，
     *                    见 {@link com.whitecobalt.anglertackle.item.RodItem.RodSpec#tier()}）
     * @param minTier     鱼饵的品质下限——低于它的鱼不会咬钩（0 = 不限）
     * @param maxWeightKg 承重上限（kg），0 = 不限
     * @param castRange   这次抛了多远
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, null);
    }

    /**
     * 同上，并叠加两个权重修正 + 鱼种档位过滤。
     *
     * @param tierBonus      档位 → 权重倍率（红饵的金/红鱼加成）；不需要时传 {@code null}
     * @param hvTargetPct    "金+红"鱼种的目标占比（%）；&lt;=0 表示不调整
     * @param speciesMinTier 鱼种必须能达到的最低档（&lt;=0 表示不过滤）；
     *                       测试拟饵靠它把"出不了金红的鱼种"全部挡在池外
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, speciesMinTier, null);
    }

    /** 同上，并按水体筛选（{@code water == null} 表示不筛）。 */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, speciesMinTier, water, 0);
    }

    /**
     * 同上，并按**装备中拟饵认的习性词条**给命中鱼种加权。
     *
     * @param habitMask 拟饵认的词条位掩码；0 = 不按词条加权
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water,
                                   int habitMask) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, 0);
    }

    /**
     * 同上，外加【精准猎获】附魔的额外词条百分点（见
     * {@link #buildPool(RodKind, int, int, double, CastRange, java.util.function.IntToDoubleFunction, double, int, WaterBody, int, int)}）。
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water,
                                   int habitMask, int habitExtraPct) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, habitExtraPct, null);
    }

    /**
     * 同上，外加**环境**（时段 / 天气 / 月相，见 docs/24）。
     *
     * <p>环境在池子层面改的是"抽到哪种鱼"：受时段/天气加成的组占比 +10 个百分点，
     * 月相白名单鱼分摊爆率预算。它**不改金红占比**（那是 {@code hvTargetPct} 的事），
     * 也不改咬钩节奏（唯一例外是夜间台钓 −10%，在 {@link BiteTiming} 里）。
     *
     * @param env 环境快照；{@code null} = 不启用（图鉴、调试、离线自检都传 null）
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water,
                                   int habitMask, int habitExtraPct, Environment env) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, habitExtraPct, env, RollBonus.NONE);
    }

    /**
     * 同上，外加**钓获类菜品效果**（渔运 / 爆口 / 空军，见 docs/28 §3）。
     *
     * @param bonus {@link RollBonus#NONE} = 与没有这些效果时逐字节相同
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water,
                                   int habitMask, int habitExtraPct, Environment env,
                                   RollBonus bonus) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, 0, habitMask, habitExtraPct, env, bonus);
    }

    /**
     * 同上，外加**水域修饰**（用户 2026-10-08：极地 / 高寒湖 / 冷水溪流）。
     *
     * <p>⚠ 修饰**不参与**下面那两步"池子空了"的退让：退让放宽的是**档位**
     * （这一竿够不够得着），而修饰和本体一样属于"这里有什么鱼"的**物理事实** ——
     * 把修饰退掉就等于"在冷水溪流里钓出普通河鱼"，与当年
     * "在溶洞里钓出草鱼青鱼"是同一类荒谬（见本方法下面那段事故记录）。
     *
     * @param waterMods 落点命中的修饰位掩码（{@link WaterBody#modifiers}）；
     *                  0 = 不看修饰（老调用点、离线自检）
     */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct, int speciesMinTier, WaterBody water,
                                   int waterMods, int habitMask, int habitExtraPct, Environment env,
                                   RollBonus bonus) {
        java.util.List<PoolEntry> pool = buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, speciesMinTier, water, waterMods, habitMask, habitExtraPct,
                env, bonus);

        // ★ 水体过滤**绝不允许**成为"钓不到鱼"的原因。
        //
        // 真实事故（2026-09-30，水体判定上线当天）：测试拟饵出绿鱼。
        // 链条是这样的 ——
        //   ① 水体过滤先把池子收成"这片水里有的鱼"；
        //   ② 测试饵要求 speciesMinTier = 5，把够不到红档的又全筛掉；
        //   ③ 池子空了，落到下面那句兜底 ALL.get(0)（鲫鱼，档位表 [0,1]）；
        //   ④ rollIndividual 发现鲫鱼到不了 5 档，把下限抬到它自己的最高档 1；
        //   ⑤ 于是"必出红鱼"的测试饵稳定产出**绿鱼**。
        // 日志原样：
        //   抽取输入：测试拟饵=true 竿档位上限=5 咬钩下限=2
        //   抽取结果：鱼种=crucian_carp 档位表=[0, 1] 个体下限=5 体重=0.88 实得档位=1
        //
        // 修法：池子空了要**分两步退**，而不是一步退到"不限水体"。
        //
        // ★ 2026-10-02 第二次事故（用户实测："我在溶洞拿台钓红饵紫窝半天全是草鱼青鱼"）：
        //   铁竿（档位上限蓝 2）+ 秘制窝（下限蓝 2）在洞穴水域 ——
        //   洞穴的台钓鱼只有横纹鱼[0,1]（太低）、长吻象鼻鱼[3,4]、蓝宝石雷龙鱼[5]（太高），
        //   中间**没有蓝档**，于是池子空了、直接退回不限水体，
        //   抽出两条草鱼一条青鱼 —— 全是河湖鱼。
        //   日志原样：
        //     [垂纶] 洞穴水域 水里没有满足条件的鱼种（竿型=FLOAT 档位2~2），已退回不限水体抽取
        //     [垂纶] 抽取结果：鱼种=grass_carp 档位表=[1, 2] 体重=13.73 实得档位=2
        //   在洞里钓到草鱼，比钓到一条小的洞穴鱼荒谬得多：
        //   **水体是"这里有什么鱼"（物理事实），档位只是"这一竿够不够得着"。**
        //   所以第一步只放宽档位下限（水体照旧），第二步才轮到退回不限水体。
        if (pool.isEmpty() && water != null) {
            pool = buildPool(rodKind, maxTier, 0, maxWeightKg, castRange,
                    tierBonus, hvTargetPct, speciesMinTier, water, waterMods, habitMask, habitExtraPct,
                    env, bonus);
            if (!pool.isEmpty()) {
                FishingManager.LOGGER.info(
                        "[垂纶] {} 里没有正好落在档位 {}~{} 的鱼，已放宽到该水域的全部档位（水体不放宽）",
                        water.cn(), minTier, maxTier);
            }
        }
        if (pool.isEmpty() && water != null) {
            pool = buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange,
                    tierBonus, hvTargetPct, speciesMinTier, null, habitMask, habitExtraPct, env, bonus);
            FishingManager.LOGGER.warn("[垂纶] {} 水里没有满足条件的鱼种（竿型={} 档位{}~{}），" +
                            "已退回不限水体抽取 —— 该水域的鱼种覆盖可能有缺口",
                    water.cn(), rodKind, minTier, maxTier);
        }
        if (pool.isEmpty()) {
            return ALL.get(0);   // 兜底：至少能钓到鲫鱼
        }
        int total = 0;
        for (PoolEntry e : pool) {
            total += e.weight();
        }
        int pick = random.nextInt(total);
        for (PoolEntry e : pool) {
            pick -= e.weight();
            if (pick < 0) {
                return e.species();
            }
        }
        return pool.get(pool.size() - 1).species();
    }

    /** 带档位加成与占比目标、但不限鱼种档位的版本。 */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus,
                                   double hvTargetPct) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, 0);
    }

    /** 带档位加成但不调占比的版本。 */
    public static FishSpecies roll(RandomSource random, RodKind rodKind, int maxTier, int minTier,
                                   double maxWeightKg, CastRange castRange,
                                   java.util.function.IntToDoubleFunction tierBonus) {
        return roll(random, rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus, -1.0, 0);
    }

    /**
     * 鱼池里的一条候选鱼：鱼种 + 它的抽取权重。
     *
     * @param species 鱼种
     * @param weight  抽取权重（已含饵料与抛投距离修正）
     */
    public record PoolEntry(FishSpecies species, int weight) {
    }

    /**
     * 算出"当前这套配置实际能钓到哪些鱼"——**不消耗随机数**。
     *
     * <p>存在的理由是调试：饵料下限、竿承重、竿型、抛投距离四个条件叠加之后，
     * 玩家看到的"怎么还能钓到这种鱼"光看代码很难推。把池子直接打出来，
     * 一眼就能看出是哪条过滤规则没生效。
     *
     * @param tierBonus   按品质档给的额外权重倍率（红饵的金/红鱼加成），
     *                    签名同 {@code FishingSession.baitTierBonus(int)}；
     *                    不需要时传 {@code null}
     * @param hvTargetPct **"金+红"鱼种的目标权重占比（%）**，&lt;=0 表示不调整。
     *                    这是台钓饵料的核心作用（用户定案"金红总概率 3% 到 10%"）。
     *                    ⚠ 必须在**池子层面**精确求解，不能在外面按固定基准占比反推：
     *                    台钓池只含 1 种金红鱼、路亚池含 3 种，基准占比差一倍多，
     *                    用固定基准算出来的倍率永远对不上目标（实测金饵 5.4% vs 目标 3%）。
     * @param speciesMinTier **鱼种必须"能达到"的最低档**：{@code s.maxTier() < 本值} 的鱼种
     *                    直接不进池（&lt;=0 表示不过滤）。
     *                    ⚠ 这和 {@code minTier} 是**两件事**：{@code minTier} 管的是
     *                    "鱼的最高档低于饵料下限就不咬"（按最高档比较），
     *                    而这个是"这个鱼种根本出不了这么高的档"。
     *                    测试拟饵必须用这个：它要把每条鱼都钉成红档，
     *                    但鲻鱼档位表是 {1,2}，在个体层够不到 5 会被兜底抬到 2 ——
     *                    **结果就是"测试拟饵出蓝货"**。只有当池子里只剩能出金红的鱼种，
     *                    "必出红鱼"才成立。
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, speciesMinTier, null);
    }

    /**
     * 同上，并按**水体**筛选。
     *
     * @param water 落点水体；{@code null} = 不筛（调试指令、老调用点、离线自检用）
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange,
                tierBonus, hvTargetPct, speciesMinTier, water, 0);
    }

    /**
     * 同上，并按**装备中拟饵认的习性词条**给命中的鱼种加权。
     *
     * @param habitMask 拟饵认的词条位掩码（见 {@link FishHabits#lureMask(String)}）；
     *                  0 = 不按词条加权（台钓、没装拟饵、测试拟饵、离线自检）
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water, int habitMask) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, 0);
    }

    /**
     * 同上，外加"精准猎获"附魔的**额外词条百分点**（加算在 {@code FishHabits.BONUS_PERCENT} 上）。
     *
     * <p>用户 2026-10-02 的附魔【精准猎获】：每级让鱼饵的上鱼类型权重 +2%（加算，
     * 不动稀有度那条）。刻意做成一个显式参数而不是全局状态：
     * 池子权重的每一分变化都必须能从调用点读出来，否则"某类鱼突然变多"根本查不到源头。
     *
     * @param habitExtraPct 额外百分点（0 = 与加附魔之前完全一致）
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water, int habitMask, int habitExtraPct) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, habitExtraPct, null);
    }

    /**
     * 同上，外加**环境**（时段 / 天气 / 月相）。
     *
     * <p>环境刻意做成**显式的可空参数**、而不是从全局状态里取：池子权重的每一分变化
     * 都必须能从调用点读出来（与 {@code habitExtraPct} 同一条纪律）——
     * 否则"某类鱼突然变多"根本查不到源头。{@code null} = 不启用环境，
     * 图鉴 / 调试指令 / 离线自检 / 旧调用点都传 null，行为与实现之前逐字节一致。
     *
     * @param env 环境快照；{@code null} = 不启用
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water, int habitMask, int habitExtraPct,
                                                      Environment env) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, habitMask, habitExtraPct, env, RollBonus.NONE);
    }

    /**
     * 同上，外加**钓获类菜品效果**（渔运 / 爆口 / 空军）。
     *
     * <p>两者都**不参与**金红占比那套精确求解（与习性词条、环境同一条纪律）：
     * <ul>
     *   <li><b>渔运</b>改的**就是**这个占比目标，所以它加在 {@code hvTargetPct} 上
     *       （绝对百分点，用户定案："总爆率提升，非相对。毕竟金红平摊加成"）；</li>
     *   <li><b>爆口 / 空军</b>是"某些鱼种的权重 ×1.05"，乘在**解完 k 之后**、
     *       取整之前 —— 位置很要紧：放到解 k 之前的话，金红那批鱼的加成会被
     *       k 原样吃掉（k 是按"改过的权重"反解的，同一个乘数会被除掉，净效果为零），
     *       玩家会看到"吃了鱼丸但一条大鱼都没多"。</li>
     * </ul>
     *
     * @param waterMods 落点命中的**水域修饰**位掩码（{@link WaterBody#modifiers}）；
     *                  0 = 不看修饰（老调用点、离线自检、指令不带水样时用）
     * @param bonus {@link RollBonus#NONE} = 与没有这些效果时逐字节相同
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water, int habitMask, int habitExtraPct,
                                                      Environment env, RollBonus bonus) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus,
                hvTargetPct, speciesMinTier, water, 0, habitMask, habitExtraPct, env, bonus);
    }

    /**
     * 同上，外加**水域修饰**过滤（用户 2026-10-08 的三个叠加修饰）。
     *
     * @param waterMods 见上一个重载
     */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus,
                                                      double hvTargetPct, int speciesMinTier,
                                                      WaterBody water, int waterMods, int habitMask,
                                                      int habitExtraPct,
                                                      Environment env, RollBonus bonus) {
        int[] weightByTier = baseWeightByTier();
        java.util.List<FishSpecies> kept = new java.util.ArrayList<>();
        java.util.List<Double> weights = new java.util.ArrayList<>();
        double total = 0.0;
        double hvWeight = 0.0;

        for (FishSpecies s : ALL) {
            // 台钓竿只能碰 FLOAT/BOTH，路亚竿只能碰 LURE/BOTH
            if (s.rods != RodKind.BOTH && s.rods != rodKind) {
                continue;
            }
            // ★ 鱼种"根本出不了这么高的档" → 不进池（测试拟饵用）
            if (speciesMinTier > 0 && s.maxTier() < speciesMinTier) {
                continue;
            }
            // 这种鱼的**最低档**都超过竿的上限 → 钓不到
            if (s.minTier() > maxTier) {
                continue;
            }
            // ★ 鱼饵下限：这种鱼的**最高档**都低于饵的下限 → 它不会咬这个饵
            if (s.maxTier() < minTier) {
                continue;
            }
            // ★ 水体过滤：这片水里不出的鱼，直接不进池。
            //
            // 放在最前面（比竿型、档位都早）：它是"这片水里有没有这种鱼"的
            // 物理事实，而竿型/档位是"你钓不钓得到"。先问有没有，再问够不够得着。
            // ★ 水体 + **水域修饰**过滤（用户 2026-10-08：三个叠加修饰）。
            //
            //   公式与 docs/33 §9.1 逐字一致：
            //     出鱼 = 本体匹配 **或**（修饰命中 且 该鱼种声明了这个修饰位）
            //
            //   ⚠ "或"是**有意**的：普通鱼种（modsOf = 0）在修饰水域里照旧出现 ——
            //     高山湖里当然也能钓到普通湖鱼。反过来，声明了修饰的鱼种
            //     （如虹鳟 = 冷水溪流）在**普通**河里不会出现，因为它的本体掩码是 0。
            if (water != null) {
                int wantMods = FishWaters.modsOf(s.id);
                boolean byBody = FishWaters.livesIn(s.id, water);
                boolean byMod = wantMods != 0 && (waterMods & wantMods) != 0;
                if (!byBody && !byMod) {
                    continue;
                }
            }

            // ★ 这里**故意不按承重过滤**。
            //
            // 早先有一行 `if (maxWeightKg > 0 && s.kgMin() > maxWeightKg) continue;`，
            // 用意是"竿扛不住的鱼种干脆不出现"。用户定案把它去掉了：
            //   "不用做筛选，只要上超承重鱼就清空钓上它的鱼竿的耐久，
            //    或者说如果上鱼检测是超承重鱼就飞快消耗当前鱼竿耐久，不用等钓上来。"
            //
            // 原因也说得通：过滤只挡得住"整个鱼种都超重"的情况，挡不住
            // "鱼种跨度大、个体超重"（大西洋鲑 2~22kg，8kg 的木竿照样能抽到 21kg 的）。
            // 与其做一道漏风的墙，不如让超重鱼真的咬钩，然后用**烧竿**来惩罚 ——
            // 玩家因此多了一个抉择：放线让鱼跑掉保竿，还是硬拉丢竿。
            //
            // maxWeightKg 仍留在签名里：调用方按它决定要不要提示玩家、
            // 以及咬钩时的超重判定（见 FishingSession.checkOverCapacity）。
            int base = weightByTier[Math.min(weightByTier.length - 1, s.minTier())];
            // 抛投距离修正：按这个鱼种的**最高档**判断（它最值钱的那一面）
            double w = base * castRange.weightMultiplier(s.maxTier());
            // 红饵的金/红鱼加成：按这个鱼种的最高档给倍率
            if (tierBonus != null) {
                w *= tierBonus.applyAsDouble(s.maxTier());
            }
            kept.add(s);
            weights.add(w);
            total += w;
            if (s.maxTier() >= 4) {
                hvWeight += w;   // "金+红"鱼种（最高档 ≥ 4 的）
            }
        }

        // ★ 把"金+红"的总占比**精确**拉到目标值。
        //   设金红权重乘以 k、其余不变，要使 (k·hv)/(k·hv + other) = p
        //   解得 k = p·other / ((1-p)·hv)。这样任何池子组成都能命中目标，
        //   不需要"基准占比"这种魔法常数。
        //
        //   ★ 渔运（docs/28 §3）就加在这个目标上，而且是**绝对百分点**（用户定案：
        //     "总爆率提升，非相对"）。两种入口必须分开处理：
        //       ① 饵料已经承诺了占比（hvTargetPct > 0）→ 直接加上去；
        //       ② 没有承诺（< 0，路亚/低档饵）→ 先按**当前池子**算出金红占比再 +N。
        //     少了 ② 就会出事故：hvTargetPct = −1 时"−1 + 3 = 2" 把路亚原本 20%+ 的
        //     金红占比**压到 2%** —— 吃了红烧鱼反而钓不到金红鱼。
        double targetPct = hvTargetPct;
        if (bonus != null && bonus.luckPoints() > 0.0) {
            if (targetPct > 0.0) {
                targetPct += bonus.luckPoints();
            } else if (hvWeight > 0.0 && total > hvWeight) {
                targetPct = hvWeight / total * 100.0 + bonus.luckPoints();
            }
        }
        if (targetPct > 0.0 && hvWeight > 0.0 && total > hvWeight) {
            double p = Math.min(0.95, targetPct / 100.0);
            double other = total - hvWeight;
            double k = (p * other) / ((1.0 - p) * hvWeight);
            for (int i = 0; i < kept.size(); i++) {
                if (kept.get(i).maxTier() >= 4) {
                    weights.set(i, weights.get(i) * k);
                }
            }
        }

        // ★ 爆口 / 空军：按体重区间中值归类后给**该鱼种**的权重乘 1.05/级。
        //
        //   位置：解完 k、**取整之前**。放这里有两个理由 ——
        //   ① 放解 k 之前，金红那批鱼的加成会被 k 反解掉（净效果为零，见 buildPool 的 javadoc）；
        //   ② 放到取整之后（习性词条那一步）就只能乘整数权重，最小档位权重是 3，
        //      3×1.05 = 3 —— 又是一分钱加成都没有的老问题。
        //   乘在 double 上再统一取整，"每条鱼 +5%"才是真的 +5%。
        if (bonus != null && bonus.speciesWeight() != null) {
            for (int i = 0; i < kept.size(); i++) {
                double mul = bonus.speciesWeight().applyAsDouble(kept.get(i));
                if (mul != 1.0) {
                    weights.set(i, weights.get(i) * mul);
                }
            }
        }

        // ★ 铅锤（2026-10-30）：按"这条鱼属于哪一水层"乘倍率。
        //
        //   位置**必须**在这里：解 k 之后、取整之前。
        //   ① 放到解 k 之前 → 金红那批鱼的倍率会被 k 反解掉，净效果为零（爆口/空军踩过）；
        //   ② 放到取整之后 → 只能乘整数权重，最小档位权重是 3，3×1.30=3 等于没加成。
        //   只在路亚场次才会传进来（FishingSession 侧门禁，台钓恒为 null）。
        if (bonus != null && bonus.layerBonus() != null) {
            for (int i = 0; i < kept.size(); i++) {
                FishLayers.Layer layer = FishLayers.of(kept.get(i).id());
                if (layer == null) {
                    continue;   // 没登记水层的鱼不受铅锤影响（不静默当中层）
                }
                double mul = bonus.layerBonus()[layer.ordinal()];
                if (mul != 1.0) {
                    weights.set(i, weights.get(i) * mul);
                }
            }
        }
        java.util.List<PoolEntry> pool = new java.util.ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            FishSpecies s = kept.get(i);
            int w = (int) Math.max(1, Math.round(weights.get(i)));
            // ★ 习性词条加成（用户 2026-10-01 定案）：
            //   拟饵认的词条命中这条鱼 → 该鱼种权重 ×1.10（命中多个词条也**不叠加**）。
            //
            //   为什么放在**取整之后**、而不是上面解 k 之前：
            //   ① 权重到这一步才是整数，而最小的档位权重就是 3 —— 3×1.10 再取整仍是 3，
            //      等于一点加成都没有（保底 +1 写在 FishHabits.applyBonus 里）；
            //   ② 用户原话"不加稀有权重，只加钓上来这种鱼的权重"，
            //      所以它**不参与**上面那套"金+红占比精确求解"，
            //      饵料承诺的占比不会被词条改动。
            w = FishHabits.applyBonus(w, habitMask, s.id(), habitExtraPct);
            pool.add(new PoolEntry(s, w));
        }

        // ★ 环境（时间 / 天气 / 月相）—— 用户 2026-10-02 定案，见 docs/24-时间天气月相策划.md。
        //
        //   放在**最后一步**、而且是**独立的占比求解**，理由与习性加成完全相同：
        //   上面那套 hvTargetPct 是"饵料承诺的金+红占比"，环境不许改写它
        //   （环境改的是"抽到哪种鱼"，饵料承诺改的是"金红鱼合计占多少"，
        //    两者纠缠在一起会让调好的稀有度参数失效）。
        if (env != null && Tuning.environmentEnabled()) {
            applyEnvironment(pool, env);
        }
        return pool;
    }

    // ---------------------------------------------------------------- 环境占比求解
    //
    // 数学部分（反解倍率）搬到了 {@link EnvShare} —— 那是一个**纯算法类**，
    // 所以它能被 `java` 直接跑起来做算例回归（tools/check-environment.js 每次构建都调它）。
    // 这里只留"把三个维度组织起来、迭代到收敛"这部分。

    /**
     * 把三个维度依次施加到池子上，最多迭代 3 轮。
     *
     * <p><b>为什么要迭代</b>：三组会重叠（一条鱼可能又活跃、又爱雨、又逢满月），
     * 后一组施加时会把前一组的占比稀释（分母又变大了）。迭代只影响"吃满没吃满"，
     * <b>不可能超</b> —— 因为每步的 target 都是 {@code 当前占比 + deltaPct} 且带安全阀。
     *
     * <p><b>为什么可以就地改 {@link PoolEntry#weight()} 之外的东西</b>：
     * PoolEntry 是 record，权重不可变，所以这里抽出权重数组来算、算完再重建列表。
     */
    private static void applyEnvironment(java.util.List<PoolEntry> pool, Environment env) {
        int n = pool.size();
        if (n == 0) {
            return;
        }
        java.util.List<Integer> w = new java.util.ArrayList<>(n);
        for (PoolEntry e : pool) {
            w.add(e.weight());
        }
        double timeDelta = Tuning.envTimeDeltaPct();
        double weatherDelta = Tuning.envWeatherDeltaPct();
        double cap = Tuning.envShareCap();

        // 施加前的占比：报告里要的就是"吃到了几个百分点"，不是绝对值。
        double timeBefore = EnvShare.shareOf(w, (i) -> env.matchesTime(FishHabits.timeMaskOf(pool.get(i).species().id())));
        double weatherBefore = EnvShare.shareOf(w, (i) -> env.matchesWeather(FishHabits.weatherMaskOf(pool.get(i).species().id())));

        for (int round = 0; round < 3; round++) {
            EnvShare.raiseShare(w, (i) -> env.matchesTime(FishHabits.timeMaskOf(pool.get(i).species().id())),
                    timeDelta, cap);
            EnvShare.raiseShare(w, (i) -> env.matchesWeather(FishHabits.weatherMaskOf(pool.get(i).species().id())),
                    weatherDelta, cap);

            // 月相：分两笔预算 —— 金红档一笔（用户定案 5 点）、紫档一笔（15 点）。
            java.util.List<Boolean> goldRed = new java.util.ArrayList<>(n);
            java.util.List<Boolean> purple = new java.util.ArrayList<>(n);
            for (PoolEntry e : pool) {
                int kind = FishHabits.moonOf(e.species().id());
                boolean hit = env.matchesMoon(kind);
                int max = e.species().maxTier();
                goldRed.add(hit && max >= 4);
                purple.add(hit && max == 3);
            }
            EnvShare.spendBudget(w, goldRed, Tuning.envMoonGoldRedDeltaPct(),
                    Tuning.envMoonShareMode(), cap);
            EnvShare.spendBudget(w, purple, Tuning.envMoonPurpleDeltaPct(),
                    Tuning.envMoonShareMode(), cap);
        }

        // 报告：给 /atl rod 与 envLogToChat 用 —— 环境系统是"玩家看不见的权重改动"，
        // 没有这个的话"改了没反应"和"没生效"根本分不清。
        double timeAfter = EnvShare.shareOf(w, (i) -> env.matchesTime(FishHabits.timeMaskOf(pool.get(i).species().id())));
        double weatherAfter = EnvShare.shareOf(w, (i) -> env.matchesWeather(FishHabits.weatherMaskOf(pool.get(i).species().id())));
        LAST_ENV_REPORT.set(String.format(
                "%s ｜ 池子 %d 种：时段组 %.1f%%→%.1f%%（+%.1f 点，上限 %.0f） 天气组 %.1f%%→%.1f%%（+%.1f 点）"
                        + " ｜ 月相预算 金红 %.0f / 紫 %.0f 点（%s）",
                env.describe(), n,
                timeBefore * 100, timeAfter * 100, (timeAfter - timeBefore) * 100, timeDelta * 100,
                weatherBefore * 100, weatherAfter * 100, (weatherAfter - weatherBefore) * 100,
                Tuning.envMoonGoldRedDeltaPct() * 100, Tuning.envMoonPurpleDeltaPct() * 100,
                Tuning.envMoonShareMode() == 0 ? "组内同倍率" : "按条数均摊"));

        for (int i = 0; i < n; i++) {
            pool.set(i, new PoolEntry(pool.get(i).species(), w.get(i)));
        }
    }

    /**
     * 最近一次环境加权的**人话报告**（没有环境时为空串）。
     *
     * <p>留一份静态快照而不是改 {@code buildPool} 的返回类型：池子的返回值是
     * {@code List<PoolEntry>}，被图鉴/调试/自检等十几处消费，为了日志改签名得不偿失。
     * 服务端抽签是单线程调用，所以这份"最近一次"就是**当前这一竿**的那次。
     */
    private static final ThreadLocal<String> LAST_ENV_REPORT = ThreadLocal.withInitial(() -> "");

    /** 读最近一次环境报告（见 {@link #LAST_ENV_REPORT}）；没启用环境时是空串。 */
    public static String lastEnvReport() {
        return LAST_ENV_REPORT.get();
    }

    /** 带档位加成、但不调占比、也不限鱼种档位的版本。 */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange,
                                                      java.util.function.IntToDoubleFunction tierBonus) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, tierBonus, -1.0, 0);
    }

    /**
     * 鱼种抽取的**基础权重表**，按鱼种的**最低档**索引。
     *
     * <p><b>这张表决定了"金红鱼多久见一次"，是玩家反馈的核心参数。</b>
     * 踩过的坑：三个金红鱼种（大西洋鲑 / 蓝鳍金枪鱼 / 幼鲨）的 {@code minTier()} 都是 4，
     * 拿到的权重只有 6，而它们要跟权重 26 / 16 的一堆低档鱼抢——
     * 顶配路亚实测金以上合计只有 17%，平均 6 杆才见一条，
     * 玩起来就是"几十杆看不到这两种等级的鱼"。
     *
     * <p>调这张表只影响**鱼种**出现的频次，不影响"钓上来那条是金是红"
     * ——后者由 {@link #tierSplit()} 决定。
     *
     * <p>单独抽成方法是为了让离线调参脚本能直接读源码里的这张表
     * （见 {@code tools/check-top-end.js}），不用两边各抄一份然后漂移。
     */
    public static int[] baseWeightByTier() {
        // 用户反馈："几十杆下去看不到这两种等级的鱼……反而怀疑是不是稀有度出了问题概率这么低"。
        // 原表 {40,26,16,10,6,2} 下顶配路亚金 12.4% / 红 5.0%（平均 8 杆见金、20 杆见红），
        // 玩起来像 bug。新表把档 3/4/5 抬到 12/12/6：
        //   顶配金 21.3% / 红 8.5%（金以上 29.8%，约 3.4 杆见一条）
        //   金:红 ≈ 2.5:1，红鱼仍然明显更稀有
        // 低档配置不受影响——木/铁/金竿的金以上恒为 0%（竿的档位上限把鱼种整个挡住）。
        return new int[]{40, 26, 16, 12, 12, 6};
    }

    /** 不带额外加成的版本（图鉴、调试等场景）。 */
    public static java.util.List<PoolEntry> buildPool(RodKind rodKind, int maxTier, int minTier,
                                                      double maxWeightKg, CastRange castRange) {
        return buildPool(rodKind, maxTier, minTier, maxWeightKg, castRange, null);
    }

    /**
     * 为这条鱼 roll 一个个体：体长与体重**联动**（大鱼既长又重），
     * 再**按体重判定品质档**（用户定案的等级规则）。
     *
     * <p>小鱼的"三次方"关系：同种鱼体重近似正比于体长的三次方，
     * 所以先 roll 一个体型系数，再让体长按开三次方走——
     * 这样长 1.3 倍的鱼大约重 2.2 倍，符合直觉。
     */
    public FishData rollIndividual(RandomSource random, long gameTime, String catcherName) {
        return rollIndividual(random, gameTime, catcherName, 0);
    }

    /**
     * 同上，但强制这条鱼的**最终品质不低于 {@code minTier}**。
     *
     * <p><b>为什么需要这个下限：</b>
     * {@link #roll(RandomSource, RodKind, int, int, double, CastRange)} 里的饵料过滤
     * 看的是鱼种的**最高档** {@link #maxTier()}，而实际钓上来那条鱼的品质是
     * 这里按**体重**单独 roll 的。对档位表跨多档的鱼种（比如鲫鱼 {@code {0,1,1,2,2,3}}），
     * 用金饵虽然能过滤掉"整个鱼种都低于蓝档"的鱼，却挡不住同一种鱼里
     * **体型小的那些个体**——结果就是"用金饵还能钓到绿鱼"。
     *
     * <p>做法是**直接在达标档位的体重区间里采样**，而不是"摇完再看合不合格"。
     * 一开始写的是拒绝采样（不合格就重摇，最多 12 次），但离线模拟 2 万次
     * 发现重摇用完后的兜底仍会吐出 0.1% 的不达标鱼——规则是硬的，
     * 0.1% 也是违规。现在改成精确解：
     * 品质档由体重等分区间决定（见 {@link #tierForWeight(double)}），
     * 所以"达到下限"等价于"体重落在若干个已知区间里"。
     * 把这些区间的总跨度求出来，先按跨度均匀取一个体重，
     * 再反解出对应的体型系数——命中率 100%，且分布形状不变
     * （原分布在档位内是单调的，切片内均匀采样与之一致）。
     *
     * @param minTier 允许的最低品质档（0 = 不限）
     */
    public FishData rollIndividual(RandomSource random, long gameTime, String catcherName, int minTier) {
        // 先把下限夹到本鱼种**实际能达到**的档位范围内。
        // 调用方已经按 maxTier() 过滤过鱼种，正常情况下夹不动；
        // 这里只是防御——万一没过滤（比如兜底路径），也不会摇出一个不存在的档来。
        int floor = minTier;
        boolean reachable = false;
        for (int t : tiers) {
            if (t >= floor) {
                reachable = true;
                break;
            }
        }
        if (!reachable) {
            floor = maxTier();   // 这个鱼种到不了下限 → 抬到它能达到的最高档
        }

        int n = tiers.length;
        int[] split = tierSplit();
        int splitTotal = 0;
        for (int w : split) {
            splitTotal += w;
        }
        if (splitTotal <= 0) {
            splitTotal = n;
            java.util.Arrays.fill(split, 1);
        }
        double span = Math.max(1.0e-6, kgMax() - kgMin());

        // 收集所有"档位 >= 下限"的体重区间 [weightLo, weightHi]。
        // ⚠ 区间必须按 tierSplit 的**权重划分**算，不能等分——
        // 否则这里采出来的体重大量落在 tierForWeight 判定的另一档上，
        // 下限过滤会直接失效（曾经就是这么错的）。
        double[] lo = new double[n];
        double[] hi = new double[n];
        double cum = 0.0;
        int count = 0;
        for (int i = 0; i < n; i++) {
            double from = cum;
            cum += split[i];
            if (tiers[i] < floor) {
                continue;   // 这一档低于下限，跳过
            }
            lo[count] = kgMin() + span * (from / splitTotal);
            hi[count] = kgMin() + span * (cum / splitTotal);
            count++;
        }
        // 邻接的达标档位要合并，否则会在分界处重复采样（区间各自左闭右开就不用了，
        // 但去重仍能让"均匀取一点"的语义更清楚）
        int merged = 0;
        for (int i = 1; i < count; i++) {
            if (Math.abs(lo[i] - hi[merged]) < 1.0e-9) {
                hi[merged] = hi[i];   // 和上一段连着的，接上
            } else {
                merged++;
                lo[merged] = lo[i];
                hi[merged] = hi[i];
            }
        }
        count = merged + 1;

        double totalSpan = 0.0;
        for (int i = 0; i < count; i++) {
            totalSpan += hi[i] - lo[i];
        }

        double weight;
        if (count == 0 || totalSpan <= 0.0) {
            // 理论上到不了（reachable 已保证至少一档）；保险起见给区间上限
            weight = kgMax();
        } else {
            // 在这个体重区间里均匀取一点
            double pick = random.nextDouble() * totalSpan;
            weight = hi[count - 1];
            for (int i = 0; i < count; i++) {
                double w = hi[i] - lo[i];
                if (pick < w) {
                    weight = lo[i] + pick;
                    break;
                }
                pick -= w;
            }
        }
        // 取区间上限那一侧（不越过 hi），保证四舍五入后仍留在达标区间里
        weight = Math.round(weight * 100.0) / 100.0;
        if (count > 0) {
            double hiMax = hi[count - 1];
            if (weight > hiMax && hiMax - 0.005 >= lo[count - 1]) {
                weight = Math.round((hiMax - 0.005) * 100.0) / 100.0;
            }
        }

        // 体长按体重开三次方映射，保证"重的一定更长"
        double wRatio = (weight - kgMin()) / span;
        double maxLen = lengthRangeCm();
        double minLen = maxLen * 0.45;
        double length = minLen + (maxLen - minLen) * Math.cbrt(Math.max(0.0, wRatio));
        length = Math.round(length * 10.0) / 10.0;

        // ★ 品质档由体重决定（按 tierSplit 的权重划分），颜色直接作用在鱼名上
        int tier = tierForWeight(weight);

        // ★ 把真实 tier 一并存进 FishData。
        //   以前只存 SizeGrade，而金(4)和红(5)都映射到 RECORD —— 反推回来只能是同一个值，
        //   于是每条金鱼都被当成红鱼（名字、图鉴最高品质、以后的定价全错）。
        //   信息在这一行还完整，存下来即可，不必反推。
        return new FishData(id, (float) length, (float) weight, FishData.SizeGrade.fromTier(tier),
                gameTime, java.util.Optional.ofNullable(catcherName), tier);
    }

    private FishData rollIndividualOnce(RandomSource random, long gameTime, String catcherName) {
        // ★ 先抽品质档，再在该档的体重区间里取体重。
        //
        // 为什么不能"先摇体重再看它落哪档"：
        //   体重按 Math.pow(sizeFactor, 3.0) 三次方压向小值（大鱼少），
        //   而档位边界是按**线性体重区间**划的，两者根本不匹配 ——
        //   高档位那条窄带落在分布的长薄上尾里，命中率远超标称。
        //   40 万次离线采样（tools/diag-tier-dist.js）实测：
        //     标称 白40 绿26 蓝16 紫12 金12 红6
        //     实际 白14 绿24 蓝16 紫 6 金22 红18
        //   红鱼比标称多 3 倍、紫鱼少一半，金红比例整个反了（玩家反馈"金钓不上、红反而多"）。
        //   改成先定档后取体重后，档位占比严格等于 baseWeightByTier()。
        int tier = rollTier(random);

        // 该档对应的体重区间（按 tierSplit 权重划分），与 tierForWeight 的边界完全一致
        double[] win = weightWindowForTier(tier);
        // 区间内仍保持"大鱼少"：向区间下端偏置，但不越出本档
        double raw = Math.max(random.nextDouble(), random.nextDouble() * 0.85);
        double w = 0.35 + raw * 0.65;                       // 0.35 ~ 1.0
        double weight = win[0] + (win[1] - win[0]) * (w * w * w);
        // ⚠️ 体重存进 FishData 前会四舍五入到 0.01kg，边界上的值一舍入就跨档，
        //    "先定档再取体重"会被破坏（小体重鱼种尤其致命：白条鱼 0.003~0.03kg
        //    整个范围只有 3 个 0.01 刻度）。
        //    所以落点必须落在**本档合法的 0.01 刻度**上，用整数刻度算，避免浮点退格
        //    （0.9 - 0.01 在浮点里是 0.89，会一路退错）。
        weight = snapToTierGrid(weight, tier, win);

        // 体长按体重开三次方映射，保证"重的一定更长"
        double wRatio = (weight - kgMin()) / Math.max(1.0e-6, kgMax() - kgMin());
        double maxLen = lengthRangeCm();
        double minLen = maxLen * 0.45;
        double length = minLen + (maxLen - minLen) * Math.cbrt(Math.max(0.0, wRatio));
        length = Math.round(length * 10.0) / 10.0;

        // 同上：tier 在这一行还是完整的，直接存下来，不要留给以后反推。
        return new FishData(id, (float) length, (float) weight, FishData.SizeGrade.fromTier(tier),
                gameTime, java.util.Optional.ofNullable(catcherName), tier);
    }

    /**
     * 把体重吸附到"本档合法的 0.01kg 刻度"上。
     *
     * <p>合法性判据用整数刻度（体重 ×100）而不是浮点比较，避免 {@code 0.9 - 0.01 == 0.89}
     * 这类误差把退格算错。找不到合法刻度（档位窄到不足一个刻度）时夹回区间内。
     */
    private double snapToTierGrid(double weight, int tier, double[] win) {
        final int step = 100;                       // 0.01kg
        int lo = (int) Math.ceil(win[0] * step - 1.0e-9);
        int hi = (int) Math.floor(win[1] * step + 1.0e-9);
        int want = (int) Math.round(weight * step);
        // 从落点向外找最近的合法刻度
        for (int d = 0; d <= Math.max(1, hi - lo + 1); d++) {
            for (int s = -1; s <= 1; s += 2) {
                int c = want + s * d;
                if (c < lo || c > hi) {
                    continue;
                }
                double v = c / (double) step;
                if (tierForWeight(v) == tier) {
                    return v;
                }
            }
        }
        // 该档窄到没有合法刻度：夹回区间内（分布会略有偏差，但保证不越档）
        double v = Math.min(Math.max(weight, win[0]), win[1]);
        return Math.round(v * step) / (double) step;
    }

    /**
     * 按本鱼种 {@link #tiers} 的标称权重抽一个品质档（与全局爆率表无关，那是鱼种层的事）。
     *
     * <p>同一档位出现多次时（理论上不会，{@code tiers} 是升序去重的）权重相加。
     */
    private int rollTier(RandomSource random) {
        int[] split = tierSplit();
        int total = 0;
        for (int s : split) {
            total += s;
        }
        if (total <= 0) {
            // 退化：等分（和 tierForWeight 的兜底保持一致）
            return tiers[Math.min(tiers.length - 1, random.nextInt(tiers.length))];
        }
        int pick = random.nextInt(total);
        int cum = 0;
        for (int i = 0; i < tiers.length; i++) {
            cum += split[i];
            if (pick < cum) {
                return tiers[i];
            }
        }
        return tiers[tiers.length - 1];
    }

    /**
     * 某个品质档对应的体重区间 {@code [下限, 上限)}，上端封顶到 {@link #kgMax()}。
     *
     * <p>边界算法与 {@link #tierForWeight(double)} 必须**逐字对应**，否则先定档再取体重
     * 会摇出落在别的档里的体重，{@code minTier} 下限过滤随之失效。
     */
    private double[] weightWindowForTier(int wantTier) {
        int n = tiers.length;
        int[] split = tierSplit();
        int total = 0;
        for (int s : split) {
            total += s;
        }
        double span = Math.max(1.0e-6, kgMax() - kgMin());
        if (total <= 0) {
            // 退化：等分区间
            int idx = 0;
            for (int i = 0; i < n; i++) {
                if (tiers[i] == wantTier) {
                    idx = i;
                    break;
                }
            }
            return new double[]{kgMin() + span * idx / n, kgMin() + span * (idx + 1) / n};
        }
        double cum = 0.0;
        for (int i = 0; i < n; i++) {
            double from = cum;
            cum += split[i];
            if (tiers[i] == wantTier) {
                return new double[]{kgMin() + span * (from / total), kgMin() + span * (cum / total)};
            }
        }
        // wantTier 不在 tiers 里（调用方已保证不会）——退回全区间
        return new double[]{kgMin(), kgMax()};
    }
}
