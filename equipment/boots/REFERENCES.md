# 实际查看的靴子参考

以下资源来自官方项目，已查看8张原生16×16 PNG及4份护甲模型源码。这里只参考鞋头、脚背、靴口和小尺寸构图，没有将参考像素或模型复制到模组资源中。

## TeamTwilight/twilightforest

旅行者靴与雪人靴在原版腿部盒体前单独添加低鞋头；雪人靴还在上口添加宽收边。它们说明鞋头和靴口应通过真实体积形成轮廓，不能只在长方体正面画一条线。图标把前伸鞋尖与竖直靴筒分开。

- [knightmetal_boots.png](https://github.com/TeamTwilight/twilightforest/blob/1aab0ba0187b97aeac284fce48c0a6f097fefcbf/src/main/resources/assets/twilightforest/textures/item/knightmetal_boots.png)
- [travellers_boots.png](https://github.com/TeamTwilight/twilightforest/blob/1aab0ba0187b97aeac284fce48c0a6f097fefcbf/src/main/resources/assets/twilightforest/textures/item/travellers_boots.png)
- [yeti_boots.png](https://github.com/TeamTwilight/twilightforest/blob/1aab0ba0187b97aeac284fce48c0a6f097fefcbf/src/main/resources/assets/twilightforest/textures/item/yeti_boots.png)
- [YetiArmorModel.java](https://github.com/TeamTwilight/twilightforest/blob/1aab0ba0187b97aeac284fce48c0a6f097fefcbf/src/main/java/twilightforest/client/model/armor/YetiArmorModel.java)
- [TravellersGearModels.java](https://github.com/TeamTwilight/twilightforest/blob/1aab0ba0187b97aeac284fce48c0a6f097fefcbf/src/main/java/twilightforest/client/model/armor/TravellersGearModels.java)

## VazkiiMods/Botania

织魔靴的错位三分之四图标在16像素内保留两只鞋的前后关系；泰拉钢护甲源码把靴口作为独立体块。候选采用错位侧面构图和靴口收边，颜色、像素位置与装饰均重新设计。

- [manasteel_boots.png](https://github.com/VazkiiMods/Botania/blob/9015d171021210fb2803556a8df71a0a36427721/Xplat/src/main/resources/assets/botania/textures/item/manasteel_boots.png)
- [terrasteel_boots.png](https://github.com/VazkiiMods/Botania/blob/9015d171021210fb2803556a8df71a0a36427721/Xplat/src/main/resources/assets/botania/textures/item/terrasteel_boots.png)
- [manaweave_boots.png](https://github.com/VazkiiMods/Botania/blob/9015d171021210fb2803556a8df71a0a36427721/Xplat/src/main/resources/assets/botania/textures/item/manaweave_boots.png)
- [TerrasteelArmorModel.java](https://github.com/VazkiiMods/Botania/blob/9015d171021210fb2803556a8df71a0a36427721/Xplat/src/main/java/vazkii/botania/client/model/armor/TerrasteelArmorModel.java)

## AlexModGuy/Ice_and_Fire

龙钢与巨魔靴图标用深色边缘、低位鞋头和窄长靴筒区分高靴与普通方盒。候选的高靴沿用对应套装配色，在脚踝处明确转向脚背，鞋底保留低方跟。

- [dragonsteel_fire_boots.png](https://github.com/AlexModGuy/Ice_and_Fire/blob/02b2922003cbce3c67dad55dfea79acf1721e0db/src/main/resources/assets/iceandfire/textures/item/dragonsteel_fire_boots.png)
- [forest_troll_leather_boots.png](https://github.com/AlexModGuy/Ice_and_Fire/blob/02b2922003cbce3c67dad55dfea79acf1721e0db/src/main/resources/assets/iceandfire/textures/item/forest_troll_leather_boots.png)
- [ModelDragonsteelFireArmor.java](https://github.com/AlexModGuy/Ice_and_Fire/blob/02b2922003cbce3c67dad55dfea79acf1721e0db/src/main/java/com/github/alexthe666/iceandfire/client/model/armor/ModelDragonsteelFireArmor.java)

