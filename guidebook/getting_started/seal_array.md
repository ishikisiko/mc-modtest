---
navigation:
  title: 扩展附魔台与篆纹阵
  parent: index.md
  position: 56
  icon: yuanxian:seal_table
item_ids:
  - yuanxian:enchanting_socket
  - yuanxian:crystal_shelf
  - yuanxian:focus_shelf
  - yuanxian:seal_table
  - yuanxian:seal_tile
  - yuanxian:seal_rack
  - yuanxian:seal_pivot
  - yuanxian:array_stone
  - yuanxian:array_stone_refined
  - yuanxian:array_stone_supreme
---

# 扩展附魔台与篆纹阵

## 用原版附魔台起步

在附魔台正下方放一格阵石托座，潜行右键附魔台即可开启扩展界面。围绕附魔台搭一圈 5 × 5 的书架，内圈八格留空，正面留一格进出。每列最多生效一座书架，可以与附魔台同高或抬高一格。同一高度内，台子和书架之间留出空隙。

下图左边是俯视摆法，右边是该层的立体位置。箭头在正面入口。图中的 **1** 是阵石托座，**2** 是原版附魔台，**3** 是普通书架。灰色部件已在下方摆好。

**地板层**把托座放在中央，台面在它正上方。

![地板层，阵石托座在中央](./enchanting_table/floor.png)

**空托座**搭四座普通书架，强度可达 4。

![台面层，空托座配四座普通书架](./enchanting_table/empty_socket.png)

**入门阵石**装好后，再按图补四座普通书架，强度可达 8。

![台面层，入门阵石配八座普通书架](./enchanting_table/entry_stone.png)

### 想多抽几条候选时

普通书架每座增加一点强度。紫晶书架增加一条候选，最多四条。定向书架让你选一条适用的附魔，提高它在抽选时的权重。两种书架都占外圈位置。同一列台面层和上一层都有书架时，只计先找到的有效书架。

下例在正面两角先放普通垫块（**4**），后排中央放定向书架（**5**）。最后把两座紫晶书架（**6**）放到垫块上。

![台面层，定向书架居后，正面两角留垫块](./enchanting_table/mixed_table.png)

![上一层，两个垫块上各放一座紫晶书架](./enchanting_table/mixed_raised.png)

## 炼制阵石

普通炼器鼎的「炼制阵石」可用紫水晶和修为炼出阵石。拿着阵石潜行右键原版附魔台，或者直接右键托座，可以装入阵石。空手右键外露的托座可以取回阵石。

| 阵石 | 强度上限 | 炼制条件 |
| --- | ---: | --- |
| 空托座 | 4 | 无需炼制 |
| 入门阵石 | 8 | 炼气三层、120 修为、1 块紫水晶碎片 |
| 凝纹阵石 | 12 | 炼气六层、360 修为、4 块紫水晶碎片 |
| 合印阵石 | 15 | 炼气九层、720 修为、8 块紫水晶碎片 |

实际附魔强度取普通书架贡献和阵石上限的较低值。以效率为例，I 至 V 级分别需要 0、4、8、12、15 点强度。

## 抽选、刻写和洗去附魔

把装备或普通书放进「器物」槽，朱砂灵墨放进「灵墨」槽。用青金石抽选时先把青金石放进对应槽，每次消耗一颗；也可直接用三点原版经验抽选。

点击候选条目选中它，用旁边的加减按钮调整等级。「刻写」按钮显示本次墨量。候选随器物保存，可以取走，再放回台上继续。已有附魔可从下方选择后升一级，只付增加的墨量；用两份灵墨「洗去」会移除该条附魔。想把效果存下来，可以到[仙纹图书馆](./cangwen_array.md)调配。

装备沿用附魔原本的适用条件。精铁镐、斧、铲、锄按各自工具类别获得适用附魔。炼器品质、柄材、耐久损伤和炼灵专长随原器物保留。

## 摆放篆纹阵

篆纹阵台由原版附魔台加工而成，右键直接开启刻写界面。以阵台正面为准，按图摆放四角的承枢、底座和铜简架。成阵后，四扇阵叶会随抽选与刻写展开，中央悬印会降下。

| 图中数字 | 部件 | 数量 |
| --- | --- | ---: |
| 1 | 篆纹阵砖 | 80 |
| 2 | 铜简架 | 11 |
| 3 | 篆纹承枢 | 16 |
| 4 | 篆纹阵台 | 1 |
| 5 | 阵石托座 | 1 |

![台面下一层，铺 9 × 9 底座，阵石托座位于中央](./seal_array/placement_1.png)

![台面层，放入阵台、铜简架和四角承枢](./seal_array/placement_2.png)

![承枢第二层，四角继续往上搭](./seal_array/placement_3.png)

![承枢第三层](./seal_array/placement_4.png)

![承枢第四层](./seal_array/placement_5.png)

成阵后，铜简架相当于普通书架，其余可用位置可以补书架。强度达到 12 时，点击候选边上的「留纹」，下次抽选可保留这一条及选定的等级。强度达到 15 时，可以同时选中两条相容的附魔，一次刻写。

## 工作台配方

### 附魔台托座和书架

<Recipe id="yuanxian:enchanting_socket" />

<Recipe id="yuanxian:crystal_shelf" />

<Recipe id="yuanxian:focus_shelf" />

### 篆纹阵部件

<Recipe id="yuanxian:seal_table" />

<Recipe id="yuanxian:seal_tile" />

<Recipe id="yuanxian:seal_pivot" />

<Recipe id="yuanxian:seal_rack" />

抽选后的刻写用[朱砂灵墨](./talismans.md)。阵石通过普通炼器鼎炼制，取出后可以换到另一座托座上。

[查看仙纹图书馆与藏纹阵枢](./cangwen_array.md)
