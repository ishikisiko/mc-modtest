# 十二套衣甲独立审美复核

复核日期：2026-10-28。范围为炼气、筑基、金丹、元婴十二套；化神不在本次结论内。最终取舍及修整由主代理负责，用户审美接受为 `not_verified`。

## 结论

十二套已经有可识别的独立构图，不能概括成同一人台换色；但也不能据此认定已经满足“精致、越境越仙神”的最终目标。**筑基和元婴的同境结构差异最明确；金丹两套战甲的身体语言仍接近，跨境材质等级也没有稳定拉开。** 本批相对最强为 `yuanying_xinghe` 星河天衣：冠、离身袖幕、背部曲线与长摆形成统一空间关系。最弱为 `qi_chixia` 赤霞护道：虽非补丁农装，却最接近亮面塑料古代兵俑，竖冠、圆肩、直筒护臂共同压过了修仙气质。

目前有两类应优先修的真实技术问题：肩／腰胯覆盖缺口，以及内层穿出外裳。其后再处理九色琉光的材质可读性、战甲玩具感、青帝身体进阶不足。下文只列五项修改问题，不将“还能更豪华”当作缺陷，也不建议普遍加光环。

## 证据身份与观察边界

- 已逐张查看 `capture12/` 中十二套的 `-iso.png`、`-back.png`、`-ingot.png`，共 **36 张实际模型截图**；判断先来自图片，再用源码定位。组件数量、中文说明不作为美观程度的替代证据。
- 图片对应的 [固定 HTML](../../../reports/immortal-armor-atlas/review/levels1-4-review.html) SHA-256 为 `2e2e93b1f3b5fc9624d6def53fa75c2a2a77f06cd7339e90f1c06a239f2ac62a`，与 `capture12/capture-report.json` 记录一致；其内嵌十二套 catalogue 与本次读取的 `levels/level1–4.json` 内容逐对象一致。源码、截图散列见 [身份记录](../../../reports/immortal-armor-atlas/review/levels1-4-review-identity.json)。共享目录后续若改设计，需要重新确认对应画面，不能复用本次结论冒充修后通过。
- 另外对该固定 HTML 内嵌的 Three.js、engine 和 catalogue 做了轻量离线射线回读，见 [结果](../../../reports/immortal-armor-atlas/review/levels1-4-mesh-probe.json) 与 [只读探针](../../../reports/immortal-armor-atlas/review/levels1-4-mesh-probe.cjs)。没有读取正在改动的 core 来替换本批截图依据；探针是几何佐证，不是新增摄影棚截图。
- 按当前 checkout 的 `AGENTS.md`、`BRIEF.md` 和本次授权，**允许局部辅片和横向接缝**。没有发现十二套以全身碎片堆砌为主体；不把局部云肩、羽叶、膝片或正常衣襟裁切判为违规。
- 旧缩略图离屏相机深度条纹已排除在造型评价外。当前图片的细碎锯齿、孤立噪点不单独列作模型缺陷；只有下文回读支持的覆盖／穿出问题列为技术缺陷。没有据静态图片断言透明排序存在程序错误。
- 本次未运行图像生成、Minecraft、Gradle，未启动服务、创建子代理、切换 checkout 或提交。没有改设计、生成器或 core。动态动作、完整侧面及所有旋转角度、游戏适配、手感、音效、FPS、多人和用户审美认可均为 `not_verified`。

## 同境差异与四境递进

| 境界 | 同境三款的实际结构差异 | 材质与仙神气质递进判断 |
|---|---|---|
| 炼气 | 墨青是窄袖、分腿、后半围燕尾；听雨是钟袖、低圆冠、长钟裳；赤霞是竖冠、硬护臂和中央尖裾。轮廓能够区分，不是三套换色。 | 完整曲面与冠服已摆脱农装，但大面过于光滑、袖腿普遍接近管件。墨青接近山门执事，听雨有温润器物感，赤霞更像兵甲玩具。作为最低境可以克制，但仍需精确的软硬分工。 |
| 筑基 | 短甲分腿的凌云、宽袖全长礼装的玉衡、单肩／斜襟／偏置后裾的夜阙，三种穿戴结构最容易区分。 | 礼制、交领和不对称构图比炼气更完整；“灵材更贵”却不是每套都能从反光直接读出。凌云仍偏白青制服，夜阙有高阶武侠执事感，玉衡的玉披更有器物识别。 |
| 金丹 | 太阴的垂袖和非对称长衣与另外两款明显不同。金乌与紫霄的圆肩／上挑肩、胸壳、护臂、分腿、后绶有真实差异，但全身层面仍属于接近的战甲骨架。 | 日月雷符号可辨，太阴的冷色长衣与垂袖最能支撑仙器定位。金乌、紫霄更亮、更尖，不稳定等于更贵；离开冠饰后，金丹身体对筑基的提升偏弱。 |
| 元婴 | 星河的长衣与离身曲线、琉光的折面透明短装与独立长腿、青帝的宽袖大裳及生长背枝，三种体量组织清楚。 | 星河通过身体周围的真实间隙实现进阶；琉光在锭上成功呈现透明异质，但穿戴叠层偏混浊；青帝的背枝与叶面成立，正面绿色整甲／米白钟裳仍偏礼服，进阶较依赖高冠。 |

**总体递进成立于设计意图和部分轮廓，尚未在所有套装的材质与身体上稳定成立。** 炼气至筑基是裁切与礼制升级，筑基至金丹的稳定提升最弱，元婴开始出现真正不同的空间与透明行为。不能凭冠高、配件变多或材质参数认定逐级全部通过。

锭方面，十二套均保持低扁削角家族形状，这一共同轮廓符合任务；刻纹、内含物和上下层材质有可见区别。九色透明锭是最明确的异质跃迁，金乌的深胎暖顶对比也清楚；其余多数仍属金属表面变体，不能说每升一境就必然更贵。现有低视点使顶面纹样不易看全，因此本次不判定顶面微纹细节已经充分呈现，也不把共同锭形本身列为问题。

## 每套简评

以下均指本批摄影棚图片的效果；“相对较强”不是用户验收通过。

| 套装 id／名称 | 简短评价 |
|---|---|
| `qi_moqing` 墨青巡霄 | 暗胸、浅裤、单斜领和长背裳组织干净，是克制的低境巡行装。小冠与窄袖仍接近武侠执事；背腰裤层穿出形成补丁观感，必须先修真实层间关系。锭的深青胎与细银边联系明确。 |
| `qi_tingyu` 青铜听雨 | 炼气中最有独立软装气质，钟袖、雨滴前裾与低冠相互呼应。铜胸与浅青瓷的对照温润；整片袖、裙仍较僵直，近似釉面模型服装，但不属于破布农装。 |
| `qi_chixia` 赤霞护道 | **本批相对最弱。** 清楚完整但像红色兵俑：直立冠刃、圆金属肩、套管护臂、前中尖牌一起产生玩具兵感。浅色肘带是内袖材质，不能仅凭白色就说裸露。铜红锭有自身矿纹，但未扭转穿戴风格。 |
| `zhuji_baiqing_lingyun` 白青凌云 | 短衣、独立长腿、上扬薄云肩有轻锐方向，冠比普通束发更鲜明。正面白袖加细裤仍像精工武侠制服，云肩实际显得较薄，肩部人台露点削弱完成度。锭的白青、深青底和玉包体对应清楚。 |
| `zhuji_yuheng_yizhuang` 玉衡仪装 | 筑基中礼装定位最完整，高桥冠、乳玉交领、宽袖及前后垂绶前后成立，避免了普通战甲模板。大裙筒偏平静，玉与绢的图像差距有限，肩露点属技术问题。玉锭保持温润整体感。 |
| `zhuji_wuyao_yeque` 乌曜夜阙 | 筑基中身体构图最强：大斜襟、单肩檐和偏置背裾是真差异。仍偏高规格武侠／玄门执事，超凡感克制，但与其层级不冲突。暗金属与紫绫的软硬反差有效，露肩需修。 |
| `jindan_jinwu` 金乌曜阳 | 暖金／乌金／深赤配色有主次，胸章没有膨胀失控，背面有独立安排。圆鼓肩、三支冠焰与平滑胸壳容易读成西式幻想玩具兵，身体的仙器气质落后于名字；后腰胯有真实覆盖缺口。锭的黑胎与暖顶优于简单通体金色。 |
| `jindan_taiyin` 太阴霜华 | 金丹中最强，垂袖、单斜襟、长短前摆和完整后裳共同建立月宫服制，月冠不是唯一主题载体。银甲、蓝内层与霜绫略显浅平，但主体仍有明确安静面，适合保留。月银锭与穿戴材质一致。 |
| `jindan_zixiao` 紫霄雷宸 | 雷冠、向上肩峰和窄长后仪襟有肃厉识别；躯体仍像深紫光滑玩具甲，雷意过度集中在冠与尖肩。与金乌不能称纯换色，但两套身体类型仍近，前腰胯人台外露需先补齐。紫锭的雷线可辨。 |
| `yuanying_xinghe` 星河天衣 | **本批相对最强。** 暗蓝长衣、透明后袖、两条离身曲线和背部轴线形成同一空间语汇，冠下也有进阶。前胸大叶形护面略重、背裙仍光滑，但不足以推翻整体；暗蓝锭克制且与衣甲一致。 |
| `yuanying_jiuse` 九色琉光 | 与其他元婴的折面短装和长腿差异充分，锭内九色包体是明确亮点。穿戴却像浅青乳塑与透明贴膜叠在一起，胸、肩、腿同时出现多层亮轮廓，琉璃的清贵、通透尚未成立；这是本批最需重看材质组织的套装。 |
| `yuanying_qingdi` 青帝长生 | 冠枝、肩后玉叶和背干能读出生长，背面强于正面。大面积绿胸甲与米白钟裳仍像精工礼袍，普通披袖与大冠的等级差过大；是“主要靠冠升级”风险最高者，但已有宽袖、背枝，不能说完全只有冠。绿顶、铜胎锭有清楚材质联系。 |

## 五项高价值修改问题

### 1. 优先：肩与腰胯确有人台暴露【技术缺陷】

**套装与组件定位：**

- `zhuji_baiqing_lingyun`：`cloud_underbody`、`sleeve_左/右`、`cloud_shoulder_左/右`；[level2.py:117](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level2.py:117)、[level2.py:211](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level2.py:211)、[level2.py:233](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level2.py:233)。
- `zhuji_yuheng_yizhuang`：`jade_underbody`、`jade_sleeve_左/右`、`jade_mantle`；[level2.py:332](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level2.py:332)。
- `zhuji_wuyao_yeque`：`night_underbody`、`night_sleeve_左/右`、`night_left_mantle`／`night_right_mantle`；[level2.py:476](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level2.py:476)。
- `jindan_jinwu`：`waist_girdle`、`l_trouser/r_trouser`、`back_tail_-1/1`；[level3.py:128](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:128)、[level3.py:145](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:145)、[level3.py:156](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:156)。
- `jindan_zixiao`：`waist`、`l_trouser/r_trouser`、`front_ritual_tab`；[level3.py:316](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:316)、[level3.py:333](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:333)、[level3.py:344](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:344)。

**已观察／确认：** 筑基三套正背肩缝有肤色小球；正面射线在凌云、玉衡 `(x=.20,y=1.46)` 及夜阙 `(.19,1.46)` 首先命中 `avatar_右肩`。金乌背面 `(0,.97)` 首先命中 `avatar_躯体`，紫霄正面 `(.04,.96)` 同样命中人台躯体，与截图腰下浅色缺口对应。它们不是正常面孔／双手露出，也不是浅色内衬。

**落地修改：** 在 authored 服装几何中把贴身肩桥连续接到袖山，覆盖肩部实际包络；保留外云肩、玉披、单肩檐的差异，不能靠加大外肩板遮洞。金丹两套补一圈真实闭合髋袴，与裤腿及腰封有明确重叠，前后绶带只作外层。不要删人台或把人台染成衣服颜色来通过。修后至少核对这五套前侧／后侧原缺口，不据单视点推称全角度通过。

### 2. 优先：墨青内裤穿出背裳，直接制造补丁观感【技术缺陷】

**套装／组件：** `qi_moqing`；`mq_trouser_l/r`、`mq_hip`、`mq_longback`、`mq_back_lining`。[level1.py:66](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level1.py:66)、[level1.py:148](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level1.py:148)。主证据为 [墨青背面图](../../../reports/immortal-armor-atlas/review/capture12/qi_moqing-back.png)。

**已观察／确认：** 背腰两侧出现成片白裤，浅色块破坏完整暗裳。固定模型背向射线在 `(x=.14,y=.94)` 先命中 `mq_trouser_r` 的 `z=-.082267`，外裳却在 `z=-.065345`，内裤向背后穿出约 **16.9 毫米**。这是实际表面次序反转，不是旧相机深度条纹。其余套装有相似浅斑不能全部类推为同因，需作者分别核对。

**落地修改：** 用双裤腿在髋段的联合包络确定背裳和衬层截面，局部调整 `.88–1.02` 附近的外裳 rx/rz 和环位置；外裳内壁需在裤／髋外表面之外，并留出厚度与缝隙。维持腰部收束，不把整件放大成桶。同步检查浅衬、髋围与裤顶的相互次序；不要用新饰片盖白斑或只改材质颜色。

### 3. 九色穿戴材质读成乳塑叠膜，透明优势没有转成高级感【审美判断】

**套装／组件：** `yuanying_jiuse`；`g_body_inner`、`g_shoulder_inner`、`g_body_shell`、`g_back_shell`、`g_collar`、`g_over_sleeve_l/r`、`g_greave_l/r`、`g_crown_fan`。材质 `porcelain/glass/frost` 位于 [level4.py:178](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:178)，躯干层叠在 [level4.py:205](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:205)，袖腿在 [level4.py:221](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:221)。

**已观察：** [正侧](../../../reports/immortal-armor-atlas/review/capture12/yuanying_jiuse-iso.png) 与背面中，胸口、披领、袖、侧襟和胫甲都在争夺浅白边线；近色不透明内衣紧贴透明外壳，整体像雾青塑料。大冠虽然有折面，仍容易读成薄片招牌。相反，[独立锭](../../../reports/immortal-armor-atlas/review/capture12/yuanying_jiuse-ingot.png) 的完整透明体与小彩核清楚，说明应保留琉璃方向。这不是对未实现物理折射的追责，也没有足够证据认定为排序 bug。

**落地修改：** 在 authored 数据里选定一处主透明体（优先披领／背瀑），其余袖壳减少重复包覆和重合轮廓；把身体内衬与磨砂承缘的明度、冷暖和面积拉开，适量使用已有 `dark` 托住透明边，避免所有内外层都是浅青白。给冠扇实际折角和前后体量，削弱整圈同宽白边；留一个安静完整胸面，不同时强调滴形胸棱、外壳折面和大磨砂披领。沿用现有透明度接口，不要求擅改 core 或添加光效。

### 4. 赤霞、金乌、紫霄的圆肩与套管组合偏玩具甲【审美判断】

**套装／组件：**

- `qi_chixia`：`cx_cuirass`、`cx_shoulder_l/r`、`cx_upperarm_l/r`、`cx_bracer_l/r`、`cx_crown_blade`；[level1.py:454](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level1.py:454)、[level1.py:468](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level1.py:468)，冠组件见本套 JSON 的同 id。
- `jindan_jinwu`：`sun_breast`、`l_shoulder/r_shoulder`、`crown_flame_0/1/2`；[level3.py:138](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:138)、[level3.py:168](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:168)。
- `jindan_zixiao`：`central_breast`、`l_shoulder/r_shoulder`、`l_upper_arm/r_upper_arm`；[level3.py:324](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level3.py:324)。

**已观察：** 三者都有平滑胸桶、独立鼓肩、圆筒臂和规则小腿护面，颜色比体态变化更抢眼。赤霞头顶直刃尤其像玩具兵；金乌的三焰像三支直立金叉；紫霄把主要雷性集中在头顶剪影与尖肩。没有发现全身方板或违规满身分片，问题在曲面的比例与组织，不能以“都是连续曲面”自证高级。

**落地修改：** 赤霞将孤立竖刃改成贴冠向后起势的连续霞脊，肩壳压低并与锁骨方向衔接，护臂沿肘腕出现真实收放，保留低境克制。金乌将肩穹压扁、后掠，形成宽而整的日翼肩胸关系，冠焰也向后收势；紫霄则维持窄长，以非均匀截面、偏置／扭转的连续胸肋表达斜向雷棱，避免又一对充气尖肩。先拉开金乌“横展”、紫霄“纵敛”的身体结构，再微调亮暗面。无需加碎片、更多宝石或背轮。

### 5. 青帝正面身体的元婴等级不足，过度依赖冠枝【审美判断】

**套装／组件：** `yuanying_qingdi`；`w_front`、`w_front_robe`、`w_back_robe`、`w_sleeve_l/r`、`w_chest_vein_l/r`、`w_chest_fork_l/r`、`w_front_leaf`；[level4.py:291](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:291)、[level4.py:303](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:303)、[level4.py:309](/tmp/mc-immortal-armor-atlas-20261028/art/immortal-armor-atlas/authors/level4.py:309)。

**已观察：** [正侧](../../../reports/immortal-armor-atlas/review/capture12/yuanying_qingdi-iso.png) 的胸甲近似完整绿罩，细枝脉不够改变体积；米白裙面占下身大半，宽袖在末端仍有平截套筒感。遮住高冠后，与筑基玉衡的贵重礼装差距不稳。背干、肩叶与前短后长已有价值，应保留，不能简单下结论为“只有大冠”。

**落地修改：** 把现有胸脉的少量主枝转成甲面本身的浅隆起／收束，令肩、胸、腰共用生长方向；不靠在平罩上加一墙细线。调整前后裳截面，减弱近地的整齐钟形扩张，明确叶形收尖与前后高度差；袖末曲率与背枝、长绶呼应，使身体也有生长而非套筒语言。保持大面留白，冠不必继续变高，也不需要新增光环。

## 跨境统一建议

1. 先统一“连续内衬 → 外衣／甲壳”的真实包络与层间余量，再作精细装饰；第 1、2 项修复应成为本批定稿前的实际画面检查条件。
2. 复拍时保持当前同一摄影棚，分别比较硬材、织物、透明体的高光宽度、暗面和边缘厚度。优先修作者材质／几何组织，不通过把高境整体打得更亮来制造升级。
3. 并排比较时暂时遮住冠的上半部，检查肩胸、袖、腰与下摆是否仍能体现同境差异和跨境进阶。遮冠是审阅方法，不是删冠要求；也不是要求每套增加超凡配件。

以上是独立复核意见与几何佐证，交主代理决定修整范围；没有作设计替换或最终用户审美验收。
