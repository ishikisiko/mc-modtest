package com.example.myvillage.item;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ModBlocks;
import com.example.myvillage.entity.ModEntities;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tiers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredItem<RideableFlyingSwordItem> RIDEABLE_FLYING_SWORD =
            ITEMS.registerItem("rideable_flying_sword",
                    props -> new RideableFlyingSwordItem(props.stacksTo(1)));

    public static final DeferredItem<SwordItem> QINGFENG_SWORD =
            ITEMS.registerItem("qingfeng_sword",
                    props -> new SwordItem(
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

    public static final DeferredItem<Item> LOW_GRADE_SPIRIT_STONE =
            ITEMS.registerSimpleItem("low_grade_spirit_stone");

    public static final DeferredItem<BlockItem> SPIRIT_STONE_ORE_ITEM =
            ITEMS.registerItem("spirit_stone_ore",
                    props -> new BlockItem(ModBlocks.SPIRIT_STONE_ORE.get(), props));

    public static final DeferredItem<BlockItem> DEEPSLATE_SPIRIT_STONE_ORE_ITEM =
            ITEMS.registerItem("deepslate_spirit_stone_ore",
                    props -> new BlockItem(ModBlocks.DEEPSLATE_SPIRIT_STONE_ORE.get(), props));

    /**
     * The {@code myvillage:main} creative tab. Icon + content is the rockery
     * item; the tab groups all hand-placeable myvillage blocks together.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN_TAB =
            CREATIVE_TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.myvillage.main"))
                    .icon(() -> new ItemStack(ROCKERY_BLOCK_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(ROCKERY_BLOCK_ITEM.get());
                        output.accept(TEST_ITEM_BLOCK_ITEM.get());
                        output.accept(SPIRIT_TESTING_STELE_ITEM.get());
                        output.accept(TECHNIQUE_INHERITANCE_STELE_ITEM.get());
                        output.accept(CULTIVATION_HANDBOOK.get());
                        output.accept(SIMPLE_FOX_SPAWN_EGG.get());
                        output.accept(RIDEABLE_FLYING_SWORD.get());
                        output.accept(QINGFENG_SWORD.get());
                        output.accept(XUANYUE_ZHENSHAN_SWORD.get());
                        output.accept(CHILIAN_LIHUO_SWORD.get());
                        output.accept(QINGXIAO_LIUYUN_SWORD.get());
                        output.accept(LOW_GRADE_SPIRIT_STONE.get());
                        output.accept(SPIRIT_STONE_ORE_ITEM.get());
                        output.accept(DEEPSLATE_SPIRIT_STONE_ORE_ITEM.get());
                    })
                    .build());

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        CREATIVE_TABS.register(modEventBus);
    }
}
