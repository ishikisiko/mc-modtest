---
navigation:
  title: 入门仪式
  parent: index.md
  position: 10
  icon: myvillage:spirit_testing_stele
item_ids:
  - myvillage:spirit_testing_stele
  - myvillage:technique_inheritance_stele
---

# 入门仪式

测灵与传功必须分开完成，并按顺序分别右键两块石碑。

普通生存玩家可以从修仙面板进入个人阡镇秘境，在药庐院内使用这两块碑。打开面板：<KeyBind id="key.myvillage.open_cultivation_profile" /> →「行迹」→「进入阡镇秘境」。详细入口见[开局与阡镇学徒](./first_steps.md)。进入秘境不会自动接受任务。

<Row>
  <BlockImage id="myvillage:spirit_testing_stele" scale="1.5" />
  <BlockImage id="myvillage:technique_inheritance_stele" scale="1.5" />
</Row>

## 第一步：测灵

右键 <ItemLink id="myvillage:spirit_testing_stele" />。服务端会为未觉醒的凡人生成确定性的灵根，并把阶段推进到“已感气”。相同世界种子、玩家 UUID、可用元素定义及算法版本不变时，结果不变。

再次使用测灵碑**不会重抽、覆盖或洗练已有灵根**，也不会自动传承功法、增加修为、稳定度、灵力或熟练度。

测灵界面还会显示天命候选。选择三项，满足界面显示的点数预算与互斥限制，再确认选择。锁定候选只是在再次抽取时保留它，不等于已经选中；已经确认的天命不会因重新开碑而重抽。效果、尚无效果的占位说明和技艺资质见[天命、技艺与技能分配](./fates_and_skills.md)。

## 第二步：传功

觉醒后，右键 <ItemLink id="myvillage:technique_inheritance_stele" />。满足当前定义要求时，你会习得《基础吐纳诀》，初始熟练度为 `0`。

再次使用传功碑只会报告已学会，**不会把已有熟练度重置为 `0`**。传功本身也不会执行吐纳、增加修为或直接晋层。

两块碑没有工作台配方。除阡镇药庐已有设施外，创造物品栏和管理员给予也可取得；不必开启作弊才能完成上述入门路线。

完成两步后，前往[打坐与冲关](./cultivation_loop.md)。
