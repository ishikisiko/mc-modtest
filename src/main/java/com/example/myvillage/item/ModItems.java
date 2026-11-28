package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.entity.ModEntities;
import com.example.myvillage.streetfurniture.StreetFurnitureItem;
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
 * Items registry for the yuanxian mod. Exposes hand-placeable decor and the
 * simple fox spawn egg through the dedicated {@code yuanxian:main} creative
 * tab.
 *
 * <p>The rockery has 56 sub-block variants but only ONE item id
 * ({@code yuanxian:rockery_block}); the variant to place is rolled at placement
 * time in {@link com.example.myvillage.block.RockeryBlock#getStateForPlacement},
 * so the inventory stays a single slot (mirrors vanilla behaviour for blocks
 * whose appearance varies per-placement, e.g. flower pots / mycelium).
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MyVillageMod.MOD_ID);

    public static final DeferredItem<QualityPickerItem> QUALITY_PICKER =
            ITEMS.registerItem("quality_picker", QualityPickerItem::new);

    /** DeferredRegister for the {@code yuanxian:main} creative tab. */
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
    public static final DeferredItem<BlockItem> LINLANG_COUNTER_ITEM = ITEMS.registerItem("linlang_counter", props -> new BlockItem(ModBlocks.LINLANG_COUNTER.get(), props));
    public static final DeferredItem<BlockItem> JIANGHU_BOARD_ITEM = ITEMS.registerItem("jianghu_board", props -> new BlockItem(ModBlocks.JIANGHU_BOARD.get(), props));

    public static final DeferredItem<StreetFurnitureItem> RUNE_LAMP_ITEM =
            ITEMS.registerItem("rune_lamp", props -> new StreetFurnitureItem(ModBlocks.RUNE_LAMP.get(), props));
    public static final DeferredItem<StreetFurnitureItem> TALL_STREET_LAMP_ITEM =
            ITEMS.registerItem("tall_street_lamp", props -> new StreetFurnitureItem(ModBlocks.TALL_STREET_LAMP.get(), props));
    public static final DeferredItem<StreetFurnitureItem> LOW_STREET_LAMP_ITEM =
            ITEMS.registerItem("low_street_lamp", props -> new StreetFurnitureItem(ModBlocks.LOW_STREET_LAMP.get(), props));
    public static final DeferredItem<StreetFurnitureItem> DRYING_RACK_ITEM =
            ITEMS.registerItem("drying_rack", props -> new StreetFurnitureItem(ModBlocks.DRYING_RACK.get(), props));

    public static final DeferredItem<CultivationHandbookItem> CULTIVATION_HANDBOOK =
            ITEMS.registerItem("cultivation_handbook",
                    props -> new CultivationHandbookItem(props.stacksTo(1)));

    public static final java.util.List<DeferredItem<DeferredSpawnEggItem>> UNDEAD_SPAWN_EGGS =
            com.example.myvillage.entity.undead.UndeadRules.get().variants().stream().map(v ->
                    ITEMS.registerItem(v.id() + "_spawn_egg", props -> new DeferredSpawnEggItem(
                            com.example.myvillage.entity.undead.UndeadEntities.TYPES.get(v.id()), v.color("body"), v.color("cloth"), props))).toList();

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

    public static final DeferredItem<DeferredSpawnEggItem> LOTUS_GUARD_SPAWN_EGG =
            ITEMS.registerItem("lotus_guard_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.LOTUS_GUARD,
                            0x52636D, 0xAB7183, props));

    public static final DeferredItem<DeferredSpawnEggItem> LANTERN_CARRIER_SPAWN_EGG =
            ITEMS.registerItem("lantern_carrier_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.LANTERN_CARRIER, 0x66543D, 0xBBC16F, props));
    public static final DeferredItem<DeferredSpawnEggItem> CLIFF_RAY_SPAWN_EGG =
            ITEMS.registerItem("cliff_ray_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.CLIFF_RAY, 0x52666A, 0xAD9164, props));
    public static final DeferredItem<Item> MOSS_LANTERN_WICK = ITEMS.registerItem("moss_lantern_wick", Item::new);
    public static final DeferredItem<Item> LAYERED_ROCK_FIN = ITEMS.registerItem("layered_rock_fin", Item::new);

    public static final DeferredItem<Item> TIDE_CRAB_CALCIUM =
            ITEMS.registerItem("tide_crab_calcium", Item::new);
    public static final DeferredItem<Item> TIDE_SALT_SAC =
            ITEMS.registerItem("tide_salt_sac", Item::new);
    public static final DeferredItem<Item> HOOK_JAW_SHELL =
            ITEMS.registerItem("hook_jaw_shell", Item::new);
    public static final DeferredItem<Item> PACKED_SAND_STINGER =
            ITEMS.registerItem("packed_sand_stinger", Item::new);
    public static final DeferredItem<Item> TIDE_DRAGON_SCALE =
            ITEMS.registerItem("tide_dragon_scale", Item::new);
    public static final DeferredItem<Item> TIDAL_GORGE_SEAL =
            ITEMS.registerItem("tidal_gorge_seal", Item::new);
    public static final DeferredItem<Item> STELE_APE_MARROW =
            ITEMS.registerItem("stele_ape_marrow", Item::new);
    public static final DeferredItem<Item> OLD_PASS_SEAL =
            ITEMS.registerItem("old_pass_seal", Item::new);
    public static final DeferredItem<DeferredSpawnEggItem> TIDE_MUD_CRAB_SPAWN_EGG =
            ITEMS.registerItem("tide_mud_crab_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.TIDE_MUD_CRAB, 0x684E3E, 0xC79D78, props));
    public static final DeferredItem<DeferredSpawnEggItem> HOOK_JAW_SCORPION_SPAWN_EGG =
            ITEMS.registerItem("hook_jaw_scorpion_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.HOOK_JAW_SCORPION, 0x9A4F2B, 0x502F24, props));
    public static final DeferredItem<DeferredSpawnEggItem> TIDE_DRAGON_SPAWN_EGG =
            ITEMS.registerItem("tide_dragon_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.TIDE_DRAGON, 0x366869, 0xABC38D, props));
    public static final DeferredItem<DeferredSpawnEggItem> STELE_APE_SPAWN_EGG =
            ITEMS.registerItem("stele_ape_spawn_egg",
                    props -> new DeferredSpawnEggItem(ModEntities.STELE_APE, 0x8C664B, 0x41372D, props));
    private static final java.util.List<DeferredItem<? extends Item>> PROVINCIAL_ENCOUNTER_ITEMS = provincialEncounterItems();

    private static java.util.List<DeferredItem<? extends Item>> provincialEncounterItems() {
        var items = new java.util.ArrayList<DeferredItem<? extends Item>>();
        for (var rule : com.example.myvillage.entity.beast.ProvincialFauna.rules()) {
            items.add(ITEMS.registerItem(rule.id() + "_spawn_egg", props -> new DeferredSpawnEggItem(
                    ModEntities.PROVINCIAL_FAUNA.get(rule.id()), rule.color(), 0x54515A, props)));
            for (String material : rule.materials()) items.add(ITEMS.registerItem(material, Item::new));
        }
        for (var rule : com.example.myvillage.entity.beast.ProvincialHostiles.rules()) {
            items.add(ITEMS.registerItem(rule.id() + "_spawn_egg", props -> new DeferredSpawnEggItem(
                    ModEntities.PROVINCIAL_ENEMIES.get(rule.id()), rule.color(), 0x54515A, props)));
            for (String material : rule.materials()) items.add(ITEMS.registerItem(material, Item::new));
        }
        return java.util.List.copyOf(items);
    }

    public static final DeferredItem<RideableFlyingSwordItem> AZURE_CLOUD_FLYING_SWORD =
            ITEMS.registerItem("azure_cloud_flying_sword", props -> new RideableFlyingSwordItem(props.stacksTo(1), true));
    public static final DeferredItem<RideableFlyingSwordItem> RIDEABLE_FLYING_SWORD =
            ITEMS.registerItem("rideable_flying_sword",
                    props -> new RideableFlyingSwordItem(props.stacksTo(1)));

    public static final DeferredItem<CompanionSwordItem> PRIMORDIAL_COMPANION_SWORD =
            ITEMS.registerItem("primordial_companion_sword", props -> new CompanionSwordItem(props
                    .attributes(SwordItem.createAttributes(Tiers.IRON, 3, -2.4F))
                    .component(net.minecraft.core.component.DataComponents.UNBREAKABLE,
                            new net.minecraft.world.item.component.Unbreakable(false))));

    public static final DeferredItem<SwordItem> QINGFENG_SWORD =
            ITEMS.registerItem("qingfeng_sword",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> XUANYUE_ZHENSHAN_SWORD =
            ITEMS.registerItem("xuanyue_zhenshan_sword",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> CHILIAN_LIHUO_SWORD =
            ITEMS.registerItem("chilian_lihuo_sword",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 3, -2.4F))));

    public static final DeferredItem<SwordItem> QINGXIAO_LIUYUN_SWORD =
            ITEMS.registerItem("qingxiao_liuyun_sword",
                    props -> new CombatWeaponItem(
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

    public static final DeferredItem<SwordItem> ZHUJI_BAIQING_LINGYUN_GAUNTLET =
            ITEMS.registerItem("zhuji_baiqing_lingyun_gauntlet",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 1, -1.8F))));

    public static final DeferredItem<SwordItem> JINDAN_JINWU_GAUNTLET =
            ITEMS.registerItem("jindan_jinwu_gauntlet",
                    props -> new CombatWeaponItem(
                            Tiers.DIAMOND,
                            props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, 1, -1.8F))));

    public static final DeferredItem<BlockItem> FOLDED_RIDGE_STONE = ITEMS.registerSimpleBlockItem(ModBlocks.FOLDED_RIDGE_STONE);
    public static final DeferredItem<BlockItem> RIDGE_LAMP_MOSS = ITEMS.registerSimpleBlockItem(ModBlocks.RIDGE_LAMP_MOSS);
    public static final DeferredItem<BlockItem> RIDGE_SCALE_FERN = ITEMS.registerSimpleBlockItem(ModBlocks.RIDGE_SCALE_FERN);

    public static final DeferredItem<BlockItem> SPIRIT_SOIL = ITEMS.registerItem("spirit_soil",
            props -> new BlockItem(ModBlocks.SPIRIT_SOIL.get(), props));
    public static final DeferredItem<BlockItem> CINDER_SOIL = ITEMS.registerItem("cinder_soil",
            props -> new BlockItem(ModBlocks.CINDER_SOIL.get(), props));
    public static final DeferredItem<BlockItem> END_SAND_SOIL = ITEMS.registerItem("end_sand_soil",
            props -> new BlockItem(ModBlocks.END_SAND_SOIL.get(), props));
    public static final DeferredItem<Item> RAW_XUANTIE = ITEMS.registerItem("raw_xuantie",
            props -> new Item(props));
    public static final DeferredItem<ForgingMaterialItem> XUANTIE_INGOT = ITEMS.registerItem("xuantie_ingot",
            props -> new ForgingMaterialItem(props,"xuantie_ingot"));
    public static final DeferredItem<ForgingMaterialItem> CHITONG_INGOT = ITEMS.registerItem("chitong_ingot",
            props -> new ForgingMaterialItem(props,"chitong_ingot"));
    /** Trial item only: no acquisition, forging hookup or recipe yet. */
    public static final DeferredItem<Item> BLOOD_SPIRIT_INGOT = ITEMS.registerItem("blood_spirit_ingot",
            props -> new Item(props));
    public static final DeferredItem<ForgingMaterialItem> EDGE_TEMPERING_SAND = ITEMS.registerItem("edge_tempering_sand",
            props -> new ForgingMaterialItem(props,"edge_tempering_sand"));
    public static final DeferredItem<BlockItem> FORGING_TABLE = ITEMS.registerItem("forging_table",
            props -> new BlockItem(ModBlocks.FORGING_TABLE.get(), props));
    public static final DeferredItem<ForgePatternItem> PATTERN_XUANTIE_FLYING_SWORD = ITEMS.registerItem("pattern_xuantie_flying_sword",
            props -> new ForgePatternItem(props, "xuantie_flying_sword"));
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
    public static final java.util.Map<String,DeferredItem<? extends Item>> FOUNDRY_ITEMS = com.example.myvillage.forging.FoundryItems.register(ITEMS);
    public static final DeferredItem<BlockItem> HEATING_KILN = ITEMS.registerItem("heating_kiln",p->new BlockItem(ModBlocks.HEATING_KILN.get(),p));
    public static final DeferredItem<BlockItem> ASSEMBLY_TABLE = ITEMS.registerItem("assembly_table",p->new BlockItem(ModBlocks.ASSEMBLY_TABLE.get(),p));
    public static final DeferredItem<BlockItem> EARTH_FIRE_VENT = ITEMS.registerItem("earth_fire_vent",p->new BlockItem(ModBlocks.EARTH_FIRE_VENT.get(),p));
    public static final DeferredItem<BlockItem> COLD_FIRE_VENT = ITEMS.registerItem("cold_fire_vent",p->new BlockItem(ModBlocks.COLD_FIRE_VENT.get(),p));
    public static final DeferredItem<ForgingHammerItem> FORGING_HAMMER = ITEMS.registerItem("forging_hammer", ForgingHammerItem::new);
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
    public static final java.util.List<DeferredItem<Item>> FORGED_TOOL_SETS =
            com.example.myvillage.forging.WorkshopToolSets.register(ITEMS);
    public static final java.util.List<DeferredItem<CombatWeaponItem>> FORGED_ARMS = ForgedArmsContent.register(ITEMS);
    public static final java.util.Map<String,DeferredItem<ForgePatternItem>> ADDITIONAL_PATTERNS = com.example.myvillage.forging.WorkshopItems.registerAdditionalPatterns(ITEMS);
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
    public static final DeferredItem<OtherworldHerbItem> RED_FURNACE_VINE = ITEMS.registerItem("red_furnace_vine",
            props -> new OtherworldHerbItem(ModBlocks.RED_FURNACE_VINE_CROP.get(), props));
    public static final DeferredItem<OtherworldHerbItem> STAR_DEW_GRASS = ITEMS.registerItem("star_dew_grass",
            props -> new OtherworldHerbItem(ModBlocks.STAR_DEW_GRASS_CROP.get(), props));
    public static final DeferredItem<FormulaItem> FORMULA_HUILING = ITEMS.registerItem("formula_huiling", props -> new FormulaItem(props, "huiling"));
    public static final DeferredItem<ExpeditionMaterialItem> REAVER_GRIT = ITEMS.registerItem("reaver_grit", props -> new ExpeditionMaterialItem(props, "reaver_grit"));
    public static final DeferredItem<FormulaItem> FORMULA_POZHANG = ITEMS.registerItem("formula_pozhang", props -> new FormulaItem(props, "pozhang"));
    public static final DeferredItem<AlchemyPillItem> PILL_POZHANG = ITEMS.registerItem("pill_pozhang", props -> new AlchemyPillItem(props, "pozhang"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> EVIL_CULTIVATOR_SPAWN_EGG =
            ITEMS.registerItem("evil_cultivator_spawn_egg", props ->
                    new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.EVIL_CULTIVATOR, 0x29262D, 0x762434, props));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> RIDGE_REAVER_SPAWN_EGG = ITEMS.registerItem("ridge_reaver_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.RIDGE_REAVER, 0x26354B, 0xA34F37, props));
    public static final DeferredItem<ExpeditionMaterialItem> TALISMAN_ASH = ITEMS.registerItem("talisman_ash", props -> new ExpeditionMaterialItem(props, "talisman_ash"));
    public static final DeferredItem<FormulaItem> FORMULA_DINGSHEN = ITEMS.registerItem("formula_dingshen", props -> new FormulaItem(props, "dingshen"));
    public static final DeferredItem<AlchemyPillItem> PILL_DINGSHEN = ITEMS.registerItem("pill_dingshen", props -> new AlchemyPillItem(props, "dingshen"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> ASH_TALISMANIST_SPAWN_EGG = ITEMS.registerItem("ash_talismanist_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.ASH_TALISMANIST, 0xC5B9A1, 0x373636, props));
    public static final DeferredItem<ExpeditionMaterialItem> THORN_BONE = ITEMS.registerItem("thorn_bone", props -> new ExpeditionMaterialItem(props, "thorn_bone"));
    public static final DeferredItem<FormulaItem> FORMULA_HUMAI = ITEMS.registerItem("formula_humai", props -> new FormulaItem(props, "humai"));
    public static final DeferredItem<AlchemyPillItem> PILL_HUMAI = ITEMS.registerItem("pill_humai", props -> new AlchemyPillItem(props, "humai"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> THORNBACK_BADGER_SPAWN_EGG = ITEMS.registerItem("thornback_badger_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.THORNBACK_BADGER, 0x514232, 0x8A9260, props));
    public static final DeferredItem<ExpeditionMaterialItem> REDPLATE_SCALE = ITEMS.registerItem("redplate_scale", props -> new ExpeditionMaterialItem(props, "redplate_scale"));
    public static final DeferredItem<FormulaItem> FORMULA_YUYAN = ITEMS.registerItem("formula_yuyan", props -> new FormulaItem(props, "yuyan"));
    public static final DeferredItem<AlchemyPillItem> PILL_YUYAN = ITEMS.registerItem("pill_yuyan", props -> new AlchemyPillItem(props, "yuyan"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> REDPLATE_LIZARD_SPAWN_EGG = ITEMS.registerItem("redplate_lizard_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.REDPLATE_LIZARD, 0xAC583B, 0x414449, props));
    public static final DeferredItem<ExpeditionMaterialItem> FROST_PLUME = ITEMS.registerItem("frost_plume", props -> new ExpeditionMaterialItem(props, "frost_plume"));
    public static final DeferredItem<FormulaItem> FORMULA_QINGSHEN = ITEMS.registerItem("formula_qingshen", props -> new FormulaItem(props, "qingshen"));
    public static final DeferredItem<AlchemyPillItem> PILL_QINGSHEN = ITEMS.registerItem("pill_qingshen", props -> new AlchemyPillItem(props, "qingshen"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> FROSTPLUME_PHEASANT_SPAWN_EGG = ITEMS.registerItem("frostplume_pheasant_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.FROSTPLUME_PHEASANT, 0x364C76, 0x829BB2, props));
    public static final DeferredItem<ExpeditionMaterialItem> LANTERN_SPORE = ITEMS.registerItem("lantern_spore", props -> new ExpeditionMaterialItem(props, "lantern_spore"));
    public static final DeferredItem<FormulaItem> FORMULA_QINGZHANG = ITEMS.registerItem("formula_qingzhang", props -> new FormulaItem(props, "qingzhang"));
    public static final DeferredItem<AlchemyPillItem> PILL_QINGZHANG = ITEMS.registerItem("pill_qingzhang", props -> new AlchemyPillItem(props, "qingzhang"));
    public static final DeferredItem<net.neoforged.neoforge.common.DeferredSpawnEggItem> LANTERN_SPORELING_SPAWN_EGG = ITEMS.registerItem("lantern_sporeling_spawn_egg", props -> new net.neoforged.neoforge.common.DeferredSpawnEggItem(ModEntities.LANTERN_SPORELING, 0x275650, 0x9A7738, props));
    public static final DeferredItem<AlchemyPillItem> PILL_HUILING = ITEMS.registerItem("pill_huiling", props -> new AlchemyPillItem(props.stacksTo(16), "huiling"));
    public static final DeferredItem<FormulaItem> FORMULA_YANGYUAN = ITEMS.registerItem("formula_yangyuan", props -> new FormulaItem(props, "yangyuan"));
    public static final DeferredItem<AlchemyPillItem> PILL_YANGYUAN = ITEMS.registerItem("pill_yangyuan", props -> new AlchemyPillItem(props.stacksTo(16), "yangyuan"));
    public static final DeferredItem<FormulaItem> FORMULA_NINGSHEN = ITEMS.registerItem("formula_ningshen", props -> new FormulaItem(props, "ningshen"));
    public static final DeferredItem<AlchemyPillItem> PILL_NINGSHEN = ITEMS.registerItem("pill_ningshen", props -> new AlchemyPillItem(props.stacksTo(16), "ningshen"));
    public static final DeferredItem<FormulaItem> FORMULA_HUILING_2 = ITEMS.registerItem("formula_huiling_2", props -> new FormulaItem(props, "huiling_2"));
    public static final DeferredItem<AlchemyPillItem> PILL_HUILING_2 = ITEMS.registerItem("pill_huiling_2", props -> new AlchemyPillItem(props, "huiling_2"));
    public static final DeferredItem<FormulaItem> FORMULA_YANGYUAN_2 = ITEMS.registerItem("formula_yangyuan_2", props -> new FormulaItem(props, "yangyuan_2"));
    public static final DeferredItem<AlchemyPillItem> PILL_YANGYUAN_2 = ITEMS.registerItem("pill_yangyuan_2", props -> new AlchemyPillItem(props, "yangyuan_2"));
    public static final DeferredItem<FormulaItem> FORMULA_NINGSHEN_2 = ITEMS.registerItem("formula_ningshen_2", props -> new FormulaItem(props, "ningshen_2"));
    public static final DeferredItem<AlchemyPillItem> PILL_NINGSHEN_2 = ITEMS.registerItem("pill_ningshen_2", props -> new AlchemyPillItem(props, "ningshen_2"));
    public static final DeferredItem<FormulaItem> FORMULA_HUILING_3 = ITEMS.registerItem("formula_huiling_3", props -> new FormulaItem(props, "huiling_3"));
    public static final DeferredItem<AlchemyPillItem> PILL_HUILING_3 = ITEMS.registerItem("pill_huiling_3", props -> new AlchemyPillItem(props, "huiling_3"));
    public static final DeferredItem<FormulaItem> FORMULA_YANGYUAN_3 = ITEMS.registerItem("formula_yangyuan_3", props -> new FormulaItem(props, "yangyuan_3"));
    public static final DeferredItem<AlchemyPillItem> PILL_YANGYUAN_3 = ITEMS.registerItem("pill_yangyuan_3", props -> new AlchemyPillItem(props, "yangyuan_3"));
    public static final DeferredItem<FormulaItem> FORMULA_NINGSHEN_3 = ITEMS.registerItem("formula_ningshen_3", props -> new FormulaItem(props, "ningshen_3"));
    public static final DeferredItem<AlchemyPillItem> PILL_NINGSHEN_3 = ITEMS.registerItem("pill_ningshen_3", props -> new AlchemyPillItem(props, "ningshen_3"));

    public static final DeferredItem<FormulaItem> FORMULA_ZHUJI_LOW = ITEMS.registerItem("formula_zhuji_low", props -> new FormulaItem(props, "zhuji_low"));
    public static final DeferredItem<FormulaItem> FORMULA_ZHUJI_MIDDLE = ITEMS.registerItem("formula_zhuji_middle", props -> new FormulaItem(props, "zhuji_middle"));
    public static final DeferredItem<FormulaItem> FORMULA_ZHUJI_HIGH = ITEMS.registerItem("formula_zhuji_high", props -> new FormulaItem(props, "zhuji_high"));
    public static final DeferredItem<FormulaItem> FORMULA_BUJI_MIDDLE = ITEMS.registerItem("formula_buji_middle", props -> new FormulaItem(props, "buji_middle"));
    public static final DeferredItem<FormulaItem> FORMULA_BUJI_HIGH = ITEMS.registerItem("formula_buji_high", props -> new FormulaItem(props, "buji_high"));
    public static final DeferredItem<FoundationPillItem> PILL_ZHUJI = ITEMS.registerItem("pill_zhuji", props -> new FoundationPillItem(props, false));
    public static final DeferredItem<FoundationPillItem> PILL_BUJI = ITEMS.registerItem("pill_buji", props -> new FoundationPillItem(props, true));
    public static final DeferredItem<FormulaItem> FORMULA_NINGYING = ITEMS.registerItem("formula_ningying", props -> new FormulaItem(props, "ningying"));
    public static final DeferredItem<FormulaItem> FORMULA_XUYING = ITEMS.registerItem("formula_xuying", props -> new FormulaItem(props, "xuying"));
    public static final DeferredItem<Item> FIVE_COLOR_GOLDEN_LOTUS = ITEMS.registerItem("five_color_golden_lotus",
            properties -> new Item(properties.rarity(net.minecraft.world.item.Rarity.EPIC).stacksTo(16)));
    public static final DeferredItem<NascentPillItem> PILL_NINGYING = ITEMS.registerItem("pill_ningying", NascentPillItem::new);
    public static final DeferredItem<AlchemyPillItem> PILL_XUYING = ITEMS.registerItem("pill_xuying", props -> new AlchemyPillItem(props, "xuying"));
    public static final DeferredItem<FormulaItem> FORMULA_NINGJIN = ITEMS.registerItem("formula_ningjin", props -> new FormulaItem(props, "ningjin"));
    public static final DeferredItem<FormulaItem> FORMULA_HUASHEN = ITEMS.registerItem("formula_huashen", props -> new FormulaItem(props, "huashen"));
    public static final DeferredItem<TribulationPillItem> PILL_NINGJIN = ITEMS.registerItem("pill_ningjin", props -> new TribulationPillItem(props, "ningjin"));
    public static final DeferredItem<TribulationPillItem> PILL_HUASHEN = ITEMS.registerItem("pill_huashen", props -> new TribulationPillItem(props, "huashen"));
    public static final DeferredItem<NingyuanFruitItem> NINGYUAN_FRUIT_LOW = ITEMS.registerItem("ningyuan_fruit_low", props -> new NingyuanFruitItem(props, 1));
    public static final DeferredItem<NingyuanFruitItem> NINGYUAN_FRUIT_HIGH = ITEMS.registerItem("ningyuan_fruit_high", props -> new NingyuanFruitItem(props, 3));

    public static final DeferredItem<DemonLurePearlItem> DEMON_LURE_PEARL =
            ITEMS.registerItem("demon_lure_pearl", DemonLurePearlItem::new);

    public static final DeferredItem<SpiritStoneBagItem> SPIRIT_STONE_BAG =
            ITEMS.registerItem("spirit_stone_bag", SpiritStoneBagItem::new);

    public static final DeferredItem<Item> LOW_GRADE_SPIRIT_STONE =
            ITEMS.registerSimpleItem("low_grade_spirit_stone");
    public static final DeferredItem<Item> MEDIUM_GRADE_SPIRIT_STONE =
            ITEMS.registerSimpleItem("medium_grade_spirit_stone");
    public static final DeferredItem<Item> HIGH_GRADE_SPIRIT_STONE =
            ITEMS.registerSimpleItem("high_grade_spirit_stone");

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

    public static final DeferredItem<BasicBreathingManualItem> BASIC_BREATHING_MANUAL =
            ITEMS.registerItem("basic_breathing_manual", BasicBreathingManualItem::new);

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
     * The {@code yuanxian:main} creative tab. Icon + content is the rockery
     * item; the tab groups all hand-placeable yuanxian blocks together.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.yuanxian.main"))
                    .icon(() -> new ItemStack(ROCKERY_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(QUALITY_PICKER.get());
                        output.accept(ROCKERY_BLOCK_ITEM.get());
                        output.accept(TEST_ITEM_BLOCK_ITEM.get());
                        output.accept(SPIRIT_TESTING_STELE_ITEM.get());
                        output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get());
                        output.accept(SCRIPTURE_SHELF_ITEM.get());
                        output.accept(LINLANG_COUNTER_ITEM.get());
                        output.accept(JIANGHU_BOARD_ITEM.get());
                        output.accept(RUNE_LAMP_ITEM.get());
                        output.accept(TALL_STREET_LAMP_ITEM.get());
                        output.accept(LOW_STREET_LAMP_ITEM.get());
                        output.accept(com.example.myvillage.decoration.SwordQiDecorationContent.PEDESTAL.get());
                        output.accept(com.example.myvillage.decoration.DemonPactPillarContent.PILLAR.get());
                        output.accept(DRYING_RACK_ITEM.get());
                        output.accept(CULTIVATION_HANDBOOK.get());
                        var aptitudeRules = com.example.myvillage.aptitude.AptitudeRules.get();
                        for (String topic : aptitudeRules.ids()) output.accept(com.example.myvillage.aptitude.AptitudeContent.create(
                                aptitudeRules.elements().contains(topic) ? "fruit" : "book", topic, 1));
                        for (String topic : aptitudeRules.weapons()) output.accept(com.example.myvillage.aptitude.AptitudeContent.create("memory", topic, 1));
                        for (String topic : aptitudeRules.crafts()) output.accept(com.example.myvillage.aptitude.AptitudeContent.create("research", topic, 1));
                        UNDEAD_SPAWN_EGGS.forEach(egg -> output.accept(egg.get()));
                        com.example.myvillage.legacy.LegacyContent.creative(output);
                        BeastMaterialContent.creative(output);
                        com.example.myvillage.storagebag.StorageBagContent.creative(output);
                        output.accept(SIMPLE_FOX_SPAWN_EGG.get());
                        output.accept(DEMON_WOLF_SPAWN_EGG.get());
                        output.accept(THUNDER_TIGER_SPAWN_EGG.get());
                        output.accept(LOTUS_GUARD_SPAWN_EGG.get());
                        output.accept(LANTERN_CARRIER_SPAWN_EGG.get());
                        output.accept(CLIFF_RAY_SPAWN_EGG.get());
                        output.accept(TIDE_MUD_CRAB_SPAWN_EGG.get());
                        output.accept(HOOK_JAW_SCORPION_SPAWN_EGG.get());
                        output.accept(TIDE_DRAGON_SPAWN_EGG.get());
                        output.accept(STELE_APE_SPAWN_EGG.get());
                        output.accept(MOSS_LANTERN_WICK.get());
                        output.accept(LAYERED_ROCK_FIN.get());
                        output.accept(TIDE_CRAB_CALCIUM.get());
                        output.accept(TIDE_SALT_SAC.get());
                        output.accept(HOOK_JAW_SHELL.get());
                        output.accept(PACKED_SAND_STINGER.get());
                        output.accept(TIDE_DRAGON_SCALE.get());
                        output.accept(TIDAL_GORGE_SEAL.get());
                        output.accept(STELE_APE_MARROW.get());
                        output.accept(OLD_PASS_SEAL.get());
                        for (var item : PROVINCIAL_ENCOUNTER_ITEMS) output.accept(item.get());
                        output.accept(CULTIVATOR_SPAWN_EGG.get());
                        output.accept(RIDEABLE_FLYING_SWORD.get());
                        output.accept(AZURE_CLOUD_FLYING_SWORD.get());
                        output.accept(QINGFENG_SWORD.get());
                        output.accept(XUANYUE_ZHENSHAN_SWORD.get());
                        output.accept(CHILIAN_LIHUO_SWORD.get());
                        output.accept(QINGXIAO_LIUYUN_SWORD.get());
                        output.accept(LINGXIAO_SPEAR.get());
                        FORGED_ARMS.forEach(entry -> output.accept(entry.get()));
                        output.accept(XUANTIE_GAUNTLET.get());
                        output.accept(ZHUJI_BAIQING_LINGYUN_GAUNTLET.get());
                        output.accept(JINDAN_JINWU_GAUNTLET.get());
                        output.accept(LOW_GRADE_SPIRIT_STONE.get());
                        output.accept(MEDIUM_GRADE_SPIRIT_STONE.get());
                        output.accept(HIGH_GRADE_SPIRIT_STONE.get());
                        output.accept(SPIRIT_STONE_BAG.get());
                        output.accept(DEMON_LURE_PEARL.get());
                        output.accept(FOLDED_RIDGE_STONE.get());
                        output.accept(RIDGE_LAMP_MOSS.get());
                        output.accept(RIDGE_SCALE_FERN.get());
                        output.accept(SPIRIT_SOIL.get());
                        output.accept(CINDER_SOIL.get());
                        output.accept(END_SAND_SOIL.get());
                        output.accept(FORGING_TABLE.get());
                        output.accept(FORGING_ANVIL.get());
                        output.accept(FORGING_HAMMER.get());
                        output.accept(HEATING_KILN.get());output.accept(ASSEMBLY_TABLE.get());
                        FOUNDRY_ITEMS.values().forEach(item->output.accept(item.get()));
                        output.accept(EARTH_FIRE_VENT.get());output.accept(COLD_FIRE_VENT.get());
                        output.accept(QUENCHING_TROUGH.get());
                        output.accept(FORGED_PICKAXE.get());
                        output.accept(PATTERN_FORGED_PICKAXE.get());
                        output.accept(FORGED_AXE.get());
                        output.accept(PATTERN_FORGED_AXE.get());
                        output.accept(FORGED_SHOVEL.get());
                        output.accept(PATTERN_FORGED_SHOVEL.get());
                        output.accept(FORGED_HOE.get());
                        output.accept(PATTERN_FORGED_HOE.get());
                        FORGED_TOOL_SETS.forEach(entry -> output.accept(entry.get()));
                        ADDITIONAL_PATTERNS.values().forEach(entry -> output.accept(entry.get()));
                        WORKPIECES.values().forEach(entry -> {
                            var item = entry.get();
                            output.accept(com.example.myvillage.forging.WorkshopItems.blank(new com.example.myvillage.forging.Workpiece(
                                    item.recipe(),item.stage(),item.stage()==0?0:com.example.myvillage.forging.WorkshopRules.get().formingPoints(item.recipe()),0)));
                        });
                        output.accept(RAW_XUANTIE.get());
                        output.accept(XUANTIE_INGOT.get());
                        output.accept(CHITONG_INGOT.get());
                        output.accept(BLOOD_SPIRIT_INGOT.get());
                        output.accept(EDGE_TEMPERING_SAND.get());
                        output.accept(PATTERN_XUANTIE_FLYING_SWORD.get());
                        output.accept(PATTERN_QINGFENG_SWORD.get());
                        output.accept(PATTERN_LINGXIAO_SPEAR.get());
                        output.accept(ALCHEMY_FURNACE.get());
                        com.example.myvillage.talisman.TalismanContent.creative(output);
                        com.example.myvillage.equipment.EquipmentContent.creative(output);
                        com.example.myvillage.equipment.immortal.ImmortalEquipmentContent.creative(output);
                        com.example.myvillage.block.IngotBlockContent.creative(output);
                        output.accept(SPIRIT_LEAF.get());
                        output.accept(DEW_BLOSSOM.get());
                        output.accept(SUNROOT.get());
                        output.accept(BLOOD_BERRY.get());
                        output.accept(CLOUD_FUNGUS.get());
                        output.accept(CALM_LOTUS.get());
                        output.accept(RED_FURNACE_VINE.get());
                        output.accept(STAR_DEW_GRASS.get());
                        output.accept(FORMULA_HUILING.get());
                        output.accept(FORMULA_YANGYUAN.get());
                        output.accept(FORMULA_NINGSHEN.get());
                        output.accept(FORMULA_HUILING_2.get());
                        output.accept(FORMULA_YANGYUAN_2.get());
                        output.accept(FORMULA_NINGSHEN_2.get());
                        output.accept(FORMULA_HUILING_3.get());
                        output.accept(FORMULA_YANGYUAN_3.get());
                        output.accept(FORMULA_NINGSHEN_3.get());
                        output.accept(REAVER_GRIT.get());
                        output.accept(FORMULA_POZHANG.get());
                        output.accept(PILL_POZHANG.get());
                        output.accept(EVIL_CULTIVATOR_SPAWN_EGG.get());
                        output.accept(RIDGE_REAVER_SPAWN_EGG.get());
                        output.accept(TALISMAN_ASH.get());
                        output.accept(FORMULA_DINGSHEN.get());
                        output.accept(PILL_DINGSHEN.get());
                        output.accept(ASH_TALISMANIST_SPAWN_EGG.get());
                        output.accept(THORN_BONE.get());
                        output.accept(FORMULA_HUMAI.get());
                        output.accept(PILL_HUMAI.get());
                        output.accept(THORNBACK_BADGER_SPAWN_EGG.get());
                        output.accept(REDPLATE_SCALE.get());
                        output.accept(FORMULA_YUYAN.get());
                        output.accept(PILL_YUYAN.get());
                        output.accept(REDPLATE_LIZARD_SPAWN_EGG.get());
                        output.accept(FROST_PLUME.get());
                        output.accept(FORMULA_QINGSHEN.get());
                        output.accept(PILL_QINGSHEN.get());
                        output.accept(FROSTPLUME_PHEASANT_SPAWN_EGG.get());
                        output.accept(LANTERN_SPORE.get());
                        output.accept(FORMULA_QINGZHANG.get());
                        output.accept(PILL_QINGZHANG.get());
                        output.accept(LANTERN_SPORELING_SPAWN_EGG.get());
                        output.accept(PILL_HUILING.get());
                        output.accept(PILL_YANGYUAN.get());
                        output.accept(PILL_NINGSHEN.get());
                        output.accept(PILL_HUILING_2.get());
                        output.accept(PILL_YANGYUAN_2.get());
                        output.accept(PILL_NINGSHEN_2.get());
                        output.accept(PILL_HUILING_3.get());
                        output.accept(PILL_YANGYUAN_3.get());
                        output.accept(PILL_NINGSHEN_3.get());
                        output.accept(FORMULA_ZHUJI_LOW.get());
                        output.accept(FORMULA_ZHUJI_MIDDLE.get());
                        output.accept(FORMULA_ZHUJI_HIGH.get());
                        output.accept(FORMULA_BUJI_MIDDLE.get());
                        output.accept(FORMULA_BUJI_HIGH.get());
                        output.accept(FORMULA_NINGYING.get());
                        output.accept(FORMULA_XUYING.get());
                        output.accept(FORMULA_NINGJIN.get());
                        output.accept(FORMULA_HUASHEN.get());
                        output.accept(NINGYUAN_FRUIT_LOW.get());output.accept(NINGYUAN_FRUIT_HIGH.get());
                        for(int quality=1;quality<=4;quality++) {
                            var pill=new net.minecraft.world.item.ItemStack(PILL_ZHUJI.get());com.example.myvillage.alchemy.PillQuality.apply(pill,quality);output.accept(pill);
                            var repair=new net.minecraft.world.item.ItemStack(PILL_BUJI.get());com.example.myvillage.alchemy.PillQuality.apply(repair,quality);output.accept(repair);
                            var nascent = new net.minecraft.world.item.ItemStack(PILL_NINGYING.get());
                            com.example.myvillage.alchemy.PillQuality.apply(nascent, quality);
                            output.accept(nascent);
                            var reformation = new net.minecraft.world.item.ItemStack(PILL_XUYING.get());
                            com.example.myvillage.alchemy.PillQuality.apply(reformation, quality);
                            output.accept(reformation);
                            output.accept(QualityPicker.create(PILL_NINGJIN.get(), quality, 0, false));
                            output.accept(QualityPicker.create(PILL_HUASHEN.get(), quality, 0, false));
                        }


                        output.accept(SPIRIT_STONE_ORE_ITEM.get());
                        output.accept(XUANTIE_ORE_ITEM.get());
                        output.accept(EDGE_TEMPERING_SAND_DEPOSIT_ITEM.get());
                        output.accept(DEEPSLATE_SPIRIT_STONE_ORE_ITEM.get());
                        output.accept(BASIC_BREATHING_MANUAL.get());
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
