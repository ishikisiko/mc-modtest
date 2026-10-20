package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.entity.ModEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

/**
 * Items registry for the myvillage mod. Exposes hand-placeable decor and the
 * simple fox spawn egg through the dedicated {@code myvillage:main} creative
 * tab.
 *
 * <p>The rockery has 56 sub-block variants but only ONE item id
 * ({@code myvillage:rockery_block}); the variant to place is rolled at placement
 * time in {@link com.example.myvillage.block.RockeryBlock#getStateForPlacement},
 * so the inventory stays a single slot (mirrors vanilla behaviour for blocks
 * whose appearance varies per-placement, e.g. flower pots / mycelium).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MyVillageMod.MOD_ID);

    public static final DeferredItem<QualityPickerItem> QUALITY_PICKER =
            ITEMS.registerItem("quality_picker", QualityPickerItem::new);

    /** DeferredRegister for the {@code myvillage:main} creative tab. */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyVillageMod.MOD_ID);

    /**
     * The rockery block as a placeable item. Single item id; placement rolls a
     * random generic variant (peak/slope/base/corner/standalone) — see
     * {@code RockeryBlock.GENERIC_VARIANTS} and {@code getStateForPlacement}.
     */
    public static final DeferredItem<BlockItem> ROCKERY_BLOCK_ITEM =
            ITEMS.registerItem("rockery_block",
                    props -> new BlockItem(ModBlocks.ROCKERY_BLOCK.get(), props));

    /** Simple placeable block item used as a smoke target for the item pipeline. */
    public static final DeferredItem<BlockItem> TEST_ITEM_BLOCK_ITEM =
            ITEMS.registerItem("test_item_block",
                    props -> new BlockItem(ModBlocks.TEST_ITEM_BLOCK.get(), props));

    public static final DeferredItem<BlockItem> SPIRIT_TESTING_STELE_ITEM =
            ITEMS.registerItem("spirit_testing_stele",
                    props -> new BlockItem(ModBlocks.SPIRIT_TESTING_STELE.get(), props));

    public static final DeferredItem<BlockItem> TECHNIQUE_INHERITANCE_STELE_ITEM =
            ITEMS.registerItem("technique_inheritance_stele",
                    props -> new BlockItem(ModBlocks.TECHNIQUE_INHERITANCE_STELE.get(), props));

    public static final DeferredItem<BlockItem> SCRIPTURE_SHELF_ITEM =
            ITEMS.registerItem("scripture_shelf",
                    props -> new BlockItem(ModBlocks.SCRIPTURE_SHELF.get(), props));

    public static final DeferredItem<CultivationHandbookItem> CULTIVATION_HANDBOOK =
            ITEMS.registerItem("cultivation_handbook",
                    props -> new CultivationHandbookItem(props.stacksTo(1)));

    public static final DeferredItem<DeferredSpawnEggItem> SIMPLE_FOX_SPAWN_EGG =
            ITEMS.registerItem("simple_fox_spawn_egg",
                    props -> new DeferredSpawnEggItem(
                            ModEntities.SIMPLE_FOX,
                            0xD77A2F,
                            0xF1C58F,
                            props));

    public static final DeferredItem<DeferredSpawnEggItem> DEMON_WOLF_SPAWN_EGG =
            ITEMS.registerItem("demon_wolf_spawn_egg",
                    props -> new DeferredSpawnEggItem(
                            ModEntities.DEMON_WOLF,
                            0x353B44,
                            0x4FD0C8,
                            props));

    public static final DeferredItem<DeferredSpawnEggItem> CULTIVATOR_SPAWN_EGG =
            ITEMS.registerItem("cultivator_spawn_egg",
                    props -> new DeferredSpawnEggItem(
                            ModEntities.CULTIVATOR,
                            0xDBE3EC,
                            0x2A3A6E,
                            props));

    public static final DeferredItem<DeferredSpawnEggItem> THUNDER_TIGER_SPAWN_EGG =
            ITEMS.registerItem("thunder_tiger_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.THUNDER_TIGER,
                            0xB9C0C6, 0x252930, props));

    public static final DeferredItem<RideableFlyingSwordItem> RIDEABLE_FLYING_SWORD =
            ITEMS.registerItem("rideable_flying_sword",
                    props -> new RideableFlyingSwordItem(props.stacksTo(1)));

    public static final DeferredItem<SwordItem> QINGFENG_SWORD =
            ITEMS.registerItem("qingfeng_sword",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> XUANYUE_ZHENSHAN_SWORD =
            ITEMS.registerItem("xuanyue_zhenshan_sword",
                    props -> new SwordItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> CHILIAN_LIHUO_SWORD =
            ITEMS.registerItem("chilian_lihuo_sword",
                    props -> new SwordItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> QINGXIAO_LIUYUN_SWORD =
            ITEMS.registerItem("qingxiao_liuyun_sword",
                    props -> new SwordItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> LINGXIAO_SPEAR =
            ITEMS.registerItem("lingxiao_spear",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 4, -2.8F))));

    public static final DeferredItem<SwordItem> XUANTIE_GAUNTLET =
            ITEMS.registerItem("xuantie_gauntlet",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 1, -1.8F))));

    public static final DeferredItem<BlockItem> SPIRIT_SOIL = ITEMS.registerItem("spirit_soil",
            props -> new BlockItem(ModBlocks.SPIRIT_SOIL.get(), props));
    public static final DeferredItem<ForgingMaterialItem> XUANTIE_INGOT = ITEMS.registerItem("xuantie_ingot",
            props -> new ForgingMaterialItem(props,"xuantie_ingot"));
    public static final DeferredItem<ForgingMaterialItem> CHITONG_INGOT = ITEMS.registerItem("chitong_ingot",
            props -> new ForgingMaterialItem(props,"chitong_ingot"));
    public static final DeferredItem<ForgingMaterialItem> EDGE_TEMPERING_SAND = ITEMS.registerItem("edge_tempering_sand",
            props -> new ForgingMaterialItem(props,"edge_tempering_sand"));
    public static final DeferredItem<BlockItem> FORGING_TABLE = ITEMS.registerItem("forging_table",
            props -> new BlockItem(ModBlocks.FORGING_TABLE.get(), props));
    public static final DeferredItem<ForgePatternItem> PATTERN_QINGFENG_SWORD = ITEMS.registerItem("pattern_qingfeng_sword",
            props -> new ForgePatternItem(props, "qingfeng_sword"));
    public static final DeferredItem<ForgePatternItem> PATTERN_LINGXIAO_SPEAR = ITEMS.registerItem("pattern_lingxiao_spear",
            props -> new ForgePatternItem(props, "lingxiao_spear"));
    public static final DeferredItem<BlockItem> ALCHEMY_FURNACE = ITEMS.registerItem("alchemy_furnace",
            props -> new BlockItem(ModBlocks.ALCHEMY_FURNACE.get(), props));
    public static final DeferredItem<BlockItem> QUENCHING_TROUGH = ITEMS.registerItem("quenching_trough",
            props -> new BlockItem(ModBlocks.QUENCHING_TROUGH.get(), props));
    public static final DeferredItem<BlockItem> FORGING_ANVIL = ITEMS.registerItem("forging_anvil",
            props -> new BlockItem(ModBlocks.FORGING_ANVIL.get(), props));
    public static final DeferredItem<com.example.myvillage.forging.WorkshopTools.Pickaxe> FORGED_PICKAXE = ITEMS.registerItem("forged_pickaxe",
            props -> new com.example.myvillage.forging.WorkshopTools.Pickaxe(props.attributes(net.minecraft.world.item.PickaxeItem.createAttributes(Tiers.IRON,1F,-2.8F))));
    public static final DeferredItem<ForgePatternItem> PATTERN_FORGED_PICKAXE = ITEMS.registerItem("pattern_forged_pickaxe",
            props -> new ForgePatternItem(props,"forged_pickaxe"));
    public static final DeferredItem<com.example.myvillage.forging.WorkshopTools.Axe> FORGED_AXE = ITEMS.registerItem("forged_axe",
            props -> new com.example.myvillage.forging.WorkshopTools.Axe(props.attributes(net.minecraft.world.item.AxeItem.createAttributes(Tiers.IRON,6F,-3.1F))));
    public static final DeferredItem<ForgePatternItem> PATTERN_FORGED_AXE = ITEMS.registerItem("pattern_forged_axe",
            props -> new ForgePatternItem(props,"forged_axe"));
    public static final DeferredItem<com.example.myvillage.forging.WorkshopTools.Shovel> FORGED_SHOVEL = ITEMS.registerItem("forged_shovel",
            props -> new com.example.myvillage.forging.WorkshopTools.Shovel(props.attributes(net.minecraft.world.item.ShovelItem.createAttributes(Tiers.IRON,1.5F,-3.0F))));
    public static final DeferredItem<ForgePatternItem> PATTERN_FORGED_SHOVEL = ITEMS.registerItem("pattern_forged_shovel",
            props -> new ForgePatternItem(props,"forged_shovel"));
    public static final DeferredItem<com.example.myvillage.forging.WorkshopTools.Hoe> FORGED_HOE = ITEMS.registerItem("forged_hoe",
            props -> new com.example.myvillage.forging.WorkshopTools.Hoe(props.attributes(net.minecraft.world.item.HoeItem.createAttributes(Tiers.IRON,-2F,-1.0F))));
    public static final DeferredItem<ForgePatternItem> PATTERN_FORGED_HOE = ITEMS.registerItem("pattern_forged_hoe",
            props -> new ForgePatternItem(props,"forged_hoe"));
    public static final java.util.Map<String,DeferredItem<com.example.myvillage.forging.WorkpieceItem>> WORKPIECES =
            com.example.myvillage.forging.WorkshopItems.register(ITEMS);

    public static final DeferredItem<ItemNameBlockItem> SPIRIT_LEAF = ITEMS.registerItem("spirit_leaf",
            props -> new ItemNameBlockItem(ModBlocks.SPIRIT_LEAF_CROP.get(), props));
    public static final DeferredItem<ItemNameBlockItem> DEW_BLOSSOM = ITEMS.registerItem("dew_blossom",
            props -> new ItemNameBlockItem(ModBlocks.DEW_BLOSSOM_CROP.get(), props));
    public static final DeferredItem<ItemNameBlockItem> SUNROOT = ITEMS.registerItem("sunroot",
            props -> new ItemNameBlockItem(ModBlocks.SUNROOT_CROP.get(), props));
    public static final DeferredItem<ItemNameBlockItem> BLOOD_BERRY = ITEMS.registerItem("blood_berry",
            props -> new ItemNameBlockItem(ModBlocks.BLOOD_BERRY_CROP.get(), props));
    public static final DeferredItem<ItemNameBlockItem> CLOUD_FUNGUS = ITEMS.registerItem("cloud_fungus",
            props -> new ItemNameBlockItem(ModBlocks.CLOUD_FUNGUS_CROP.get(), props));
    public static final DeferredItem<ItemNameBlockItem> CALM_LOTUS = ITEMS.registerItem("calm_lotus",
            props -> new ItemNameBlockItem(ModBlocks.CALM_LOTUS_CROP.get(), props));
    public static final DeferredItem<FormulaItem> FORMULA_HUILING = ITEMS.registerItem("formula_huiling", props -> new FormulaItem(props, "huiling"));
    public static final DeferredItem<AlchemyPillItem> PILL_HUILING = ITEMS.registerItem("pill_huiling", props -> new AlchemyPillItem(props.stacksTo(16), "huiling"));
    public static final DeferredItem<FormulaItem> FORMULA_YANGYUAN = ITEMS.registerItem("formula_yangyuan", props -> new FormulaItem(props, "yangyuan"));
    public static final DeferredItem<AlchemyPillItem> PILL_YANGYUAN = ITEMS.registerItem("pill_yangyuan", props -> new AlchemyPillItem(props.stacksTo(16), "yangyuan"));
    public static final DeferredItem<FormulaItem> FORMULA_NINGSHEN = ITEMS.registerItem("formula_ningshen", props -> new FormulaItem(props, "ningshen"));
    public static final DeferredItem<AlchemyPillItem> PILL_NINGSHEN = ITEMS.registerItem("pill_ningshen", props -> new AlchemyPillItem(props.stacksTo(16), "ningshen"));

    public static final DeferredItem<DemonLurePearlItem> DEMON_LURE_PEARL =
            ITEMS.registerItem("demon_lure_pearl", DemonLurePearlItem::new);

    public static final DeferredItem<SpiritStoneBagItem> SPIRIT_STONE_BAG =
            ITEMS.registerItem("spirit_stone_bag", SpiritStoneBagItem::new);

    public static final DeferredItem<Item> LOW_GRADE_SPIRIT_STONE =
            ITEMS.registerSimpleItem("low_grade_spirit_stone");

    public static final DeferredItem<BlockItem> SPIRIT_STONE_ORE_ITEM =
            ITEMS.registerItem("spirit_stone_ore",
                    props -> new BlockItem(ModBlocks.SPIRIT_STONE_ORE.get(), props));

    public static final DeferredItem<BlockItem> XUANTIE_ORE_ITEM = ITEMS.registerItem("xuantie_ore",
            props -> new BlockItem(ModBlocks.XUANTIE_ORE.get(),props));
    public static final DeferredItem<BlockItem> EDGE_TEMPERING_SAND_DEPOSIT_ITEM = ITEMS.registerItem("edge_tempering_sand_deposit",
            props -> new BlockItem(ModBlocks.EDGE_TEMPERING_SAND_DEPOSIT.get(),props));

    public static final DeferredItem<BlockItem> DEEPSLATE_SPIRIT_STONE_ORE_ITEM =
            ITEMS.registerItem("deepslate_spirit_stone_ore",
                    props -> new BlockItem(ModBlocks.DEEPSLATE_SPIRIT_STONE_ORE.get(), props));

    // Technique manuals (秘籍): one item per category x grade (黄 玄 地 天); the technique is a data
    // component (ModDataComponents.TECHNIQUE). Order: category (core, active, movement, body), then grade.
    public static final DeferredItem<TechniqueManualItem> MANUAL_CORE_HUANG =
            ITEMS.registerItem("manual_core_huang",
                    props -> new TechniqueManualItem(TechniqueCategory.CORE, 1, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_CORE_XUAN =
            ITEMS.registerItem("manual_core_xuan",
                    props -> new TechniqueManualItem(TechniqueCategory.CORE, 2, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_CORE_DI =
            ITEMS.registerItem("manual_core_di",
                    props -> new TechniqueManualItem(TechniqueCategory.CORE, 3, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_CORE_TIAN =
            ITEMS.registerItem("manual_core_tian",
                    props -> new TechniqueManualItem(TechniqueCategory.CORE, 4, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_ACTIVE_HUANG =
            ITEMS.registerItem("manual_active_huang",
                    props -> new TechniqueManualItem(TechniqueCategory.ACTIVE, 1, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_ACTIVE_XUAN =
            ITEMS.registerItem("manual_active_xuan",
                    props -> new TechniqueManualItem(TechniqueCategory.ACTIVE, 2, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_ACTIVE_DI =
            ITEMS.registerItem("manual_active_di",
                    props -> new TechniqueManualItem(TechniqueCategory.ACTIVE, 3, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_ACTIVE_TIAN =
            ITEMS.registerItem("manual_active_tian",
                    props -> new TechniqueManualItem(TechniqueCategory.ACTIVE, 4, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_MOVEMENT_HUANG =
            ITEMS.registerItem("manual_movement_huang",
                    props -> new TechniqueManualItem(TechniqueCategory.MOVEMENT, 1, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_MOVEMENT_XUAN =
            ITEMS.registerItem("manual_movement_xuan",
                    props -> new TechniqueManualItem(TechniqueCategory.MOVEMENT, 2, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_MOVEMENT_DI =
            ITEMS.registerItem("manual_movement_di",
                    props -> new TechniqueManualItem(TechniqueCategory.MOVEMENT, 3, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_MOVEMENT_TIAN =
            ITEMS.registerItem("manual_movement_tian",
                    props -> new TechniqueManualItem(TechniqueCategory.MOVEMENT, 4, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_BODY_HUANG =
            ITEMS.registerItem("manual_body_huang",
                    props -> new TechniqueManualItem(TechniqueCategory.BODY, 1, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_BODY_XUAN =
            ITEMS.registerItem("manual_body_xuan",
                    props -> new TechniqueManualItem(TechniqueCategory.BODY, 2, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_BODY_DI =
            ITEMS.registerItem("manual_body_di",
                    props -> new TechniqueManualItem(TechniqueCategory.BODY, 3, props));

    public static final DeferredItem<TechniqueManualItem> MANUAL_BODY_TIAN =
            ITEMS.registerItem("manual_body_tian",
                    props -> new TechniqueManualItem(TechniqueCategory.BODY, 4, props));

    /** The 16 manuals, ordered by category then grade; see {@link #manual}. */
    public static final List<DeferredItem<TechniqueManualItem>> MANUALS = List.of(
            MANUAL_CORE_HUANG,
            MANUAL_CORE_XUAN,
            MANUAL_CORE_DI,
            MANUAL_CORE_TIAN,
            MANUAL_ACTIVE_HUANG,
            MANUAL_ACTIVE_XUAN,
            MANUAL_ACTIVE_DI,
            MANUAL_ACTIVE_TIAN,
            MANUAL_MOVEMENT_HUANG,
            MANUAL_MOVEMENT_XUAN,
            MANUAL_MOVEMENT_DI,
            MANUAL_MOVEMENT_TIAN,
            MANUAL_BODY_HUANG,
            MANUAL_BODY_XUAN,
            MANUAL_BODY_DI,
            MANUAL_BODY_TIAN);

    /**
     * The {@code myvillage:main} creative tab. Icon + content is the rockery
     * item; the tab groups all hand-placeable myvillage blocks together.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.myvillage.main"))
                    .icon(() -> new ItemStack(ROCKERY_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(QUALITY_PICKER.get());
                        output.accept(ROCKERY_BLOCK_ITEM.get());
                        output.accept(TEST_ITEM_BLOCK_ITEM.get());
                        output.accept(SPIRIT_TESTING_STELE_ITEM.get());
                        output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get());
                        output.accept(SCRIPTURE_SHELF_ITEM.get());
                        output.accept(CULTIVATION_HANDBOOK.get());
                        output.accept(SIMPLE_FOX_SPAWN_EGG.get());
                        output.accept(DEMON_WOLF_SPAWN_EGG.get());
                        output.accept(THUNDER_TIGER_SPAWN_EGG.get());
                        output.accept(CULTIVATOR_SPAWN_EGG.get());
                        output.accept(RIDEABLE_FLYING_SWORD.get());
                        output.accept(QINGFENG_SWORD.get());
                        output.accept(XUANYUE_ZHENSHAN_SWORD.get());
                        output.accept(CHILIAN_LIHUO_SWORD.get());
                        output.accept(QINGXIAO_LIUYUN_SWORD.get());
                        output.accept(LINGXIAO_SPEAR.get());
                        output.accept(XUANTIE_GAUNTLET.get());
                        output.accept(LOW_GRADE_SPIRIT_STONE.get());
                        output.accept(SPIRIT_STONE_BAG.get());
                        output.accept(DEMON_LURE_PEARL.get());
                        output.accept(SPIRIT_SOIL.get());
                        output.accept(FORGING_TABLE.get());
                        output.accept(FORGING_ANVIL.get());
                        output.accept(QUENCHING_TROUGH.get());
                        output.accept(FORGED_PICKAXE.get());
                        output.accept(PATTERN_FORGED_PICKAXE.get());
                        output.accept(FORGED_AXE.get());
                        output.accept(PATTERN_FORGED_AXE.get());
                        output.accept(FORGED_SHOVEL.get());
                        output.accept(PATTERN_FORGED_SHOVEL.get());
                        output.accept(FORGED_HOE.get());
                        output.accept(PATTERN_FORGED_HOE.get());
                        WORKPIECES.values().forEach(entry -> {
                            var item = entry.get();
                            output.accept(com.example.myvillage.forging.WorkshopItems.blank(new com.example.myvillage.forging.Workpiece(
                                    item.recipe(),item.stage(),item.stage()==0?0:com.example.myvillage.forging.WorkshopRules.get().forming_strikes(),0)));
                        });
                        output.accept(XUANTIE_INGOT.get());
                        output.accept(CHITONG_INGOT.get());
                        output.accept(EDGE_TEMPERING_SAND.get());
                        output.accept(PATTERN_QINGFENG_SWORD.get());
                        output.accept(PATTERN_LINGXIAO_SPEAR.get());
                        output.accept(ALCHEMY_FURNACE.get());
                        output.accept(SPIRIT_LEAF.get());
                        output.accept(DEW_BLOSSOM.get());
                        output.accept(SUNROOT.get());
                        output.accept(BLOOD_BERRY.get());
                        output.accept(CLOUD_FUNGUS.get());
                        output.accept(CALM_LOTUS.get());
                        output.accept(FORMULA_HUILING.get());
                        output.accept(FORMULA_YANGYUAN.get());
                        output.accept(FORMULA_NINGSHEN.get());
                        output.accept(PILL_HUILING.get());
                        output.accept(PILL_YANGYUAN.get());
                        output.accept(PILL_NINGSHEN.get());

                        output.accept(SPIRIT_STONE_ORE_ITEM.get());
                        output.accept(XUANTIE_ORE_ITEM.get());
                        output.accept(EDGE_TEMPERING_SAND_DEPOSIT_ITEM.get());
                        output.accept(DEEPSLATE_SPIRIT_STONE_ORE_ITEM.get());
                        for (DeferredItem<TechniqueManualItem> manual : MANUALS) {
                            output.accept(manual.get());
                        }
                        // then, like enchanted books, one manual per registered technique of grade 1..4
                        TechniqueManualItem.creativeStacks(params.holders()).forEach(output::accept);
                    })
                    .build());

    private ModItems() {
    }

    /** The manual item of {@code category} and {@code grade} (1 黄 .. 4 天). */
    public static DeferredItem<TechniqueManualItem> manual(TechniqueCategory category, int grade) {
        if (grade < TechniqueManualItem.MIN_GRADE || grade > TechniqueManualItem.MAX_GRADE) {
            throw new IllegalArgumentException("Manual grade must be in 1..4, got " + grade);
        }
        return MANUALS.get(category.ordinal() * TechniqueManualItem.MAX_GRADE + grade - 1);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
