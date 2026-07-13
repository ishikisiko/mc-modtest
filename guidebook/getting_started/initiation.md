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

<Row>
  <BlockImage id="myvillage:spirit_testing_stele" scale="1.5" />
  <BlockImage id="myvillage:technique_inheritance_stele" scale="1.5" />
</Row>

## 第一步：测灵

右键 <ItemLink id="myvillage:spirit_testing_stele" />。服务端会为未觉醒的凡人生成确定性的灵根，并把阶段推进到“已感气”。相同世界种子、玩家 UUID、可用元素定义及算法版本不变时，结果不变。

再次使用测灵碑**不会重抽、覆盖或洗练已有灵根**，也不会自动传承功法、增加修为、稳定度、灵力或熟练度。

## 第二步：传功

觉醒后，右键 <ItemLink id="myvillage:technique_inheritance_stele" />。满足当前定义要求时，你会习得《基础吐纳诀》，初始熟练度为 `0`。

再次使用传功碑只会报告已学会，**不会把已有熟练度重置为 `0`**。传功本身也不会执行吐纳、增加修为或直接晋层。

两块碑目前只在创造物品栏中提供，也可通过 `/give` 获取；没有配方、自然生成或宗门结构放置。

完成两步后，前往[打坐与冲关](./cultivation_loop.md)。
