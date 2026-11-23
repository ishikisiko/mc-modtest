package com.example.myvillage.kitchen;

import com.example.myvillage.MyVillageMod;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemNameBlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class KitchenContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<MobEffect> EFFECT_REGISTRY = DeferredRegister.create(Registries.MOB_EFFECT, MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyVillageMod.MOD_ID);
    private static final Map<String, DeferredItem<? extends Item>> INGREDIENTS = new LinkedHashMap<>();
    private static final Map<String, DeferredHolder<MobEffect, MobEffect>> EFFECTS = new LinkedHashMap<>();

    public static final DeferredBlock<KitchenStationBlock> MILL = BLOCKS.register("kitchen_mill",
            () -> new KitchenStationBlock(KitchenRules.Station.MILL, station(MapColor.STONE, SoundType.STONE, 2.5F)));
    public static final DeferredBlock<KitchenStationBlock> BOARD = BLOCKS.register("kitchen_board",
            () -> new KitchenStationBlock(KitchenRules.Station.BOARD, station(MapColor.WOOD, SoundType.WOOD, 1.2F)));
    public static final DeferredBlock<KitchenStationBlock> STOVE = BLOCKS.register("kitchen_stove",
            () -> new KitchenStationBlock(KitchenRules.Station.STOVE, station(MapColor.TERRACOTTA_RED, SoundType.STONE, 3)
                    .lightLevel(state -> state.getValue(KitchenStationBlock.LIT) ? 12 : 0)));
    public static final DeferredBlock<GingerCrop> GINGER_CROP = BLOCKS.register("kitchen_ginger_crop",
            () -> new GingerCrop(BlockBehaviour.Properties.ofFullCopy(Blocks.CARROTS)));
    public static final DeferredItem<Item> KNIFE = ITEMS.registerSimpleItem("kitchen_knife",
            new Item.Properties().durability(KitchenRules.get().knifeDurability));
    public static final Supplier<BlockEntityType<KitchenPrepBlockEntity>> PREP_ENTITY = ENTITIES.register("kitchen_prep",
            () -> BlockEntityType.Builder.of(KitchenPrepBlockEntity::new, MILL.get(), BOARD.get()).build(null));
    public static final Supplier<BlockEntityType<KitchenStoveBlockEntity>> STOVE_ENTITY = ENTITIES.register("kitchen_stove",
            () -> BlockEntityType.Builder.of(KitchenStoveBlockEntity::new, STOVE.get()).build(null));
    public static final Supplier<MenuType<KitchenMenu>> MENU = MENUS.register("kitchen_stove",
            () -> new MenuType<>(KitchenMenu::new, FeatureFlags.DEFAULT_FLAGS));

    static {
        var rules = KitchenRules.get();
        ITEMS.registerSimpleBlockItem(MILL);
        ITEMS.registerSimpleBlockItem(BOARD);
        ITEMS.registerSimpleBlockItem(STOVE);
        INGREDIENTS.put(rules.ginger, ITEMS.registerItem(rules.ginger, properties -> new ItemNameBlockItem(GINGER_CROP.get(), properties)));
        for (var prep : rules.preparations) INGREDIENTS.put(prep.id(), ITEMS.registerSimpleItem(prep.id()));
        for (var meal : rules.meals) {
            EFFECTS.put(meal.id(), EFFECT_REGISTRY.register(meal.id(), () -> new MealEffect(meal)));
            INGREDIENTS.put(meal.id(), ITEMS.registerItem(meal.id(), properties -> new KitchenFoodItem(meal, properties)));
        }
        TABS.register("kitchen", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.yuanxian.kitchen"))
                .icon(() -> new ItemStack(STOVE.get()))
                .displayItems((parameters, output) -> {
                    output.accept(MILL.get());
                    output.accept(BOARD.get());
                    output.accept(KNIFE.get());
                    output.accept(STOVE.get());
                    INGREDIENTS.values().forEach(item -> output.accept(item.get()));
                }).build());
    }

    private KitchenContent() { }

    private static BlockBehaviour.Properties station(MapColor color, SoundType sound, float hardness) {
        return BlockBehaviour.Properties.of().mapColor(color).sound(sound).strength(hardness)
                .noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    public static Item item(String id) {
        var entry = INGREDIENTS.get(id);
        if (entry == null) throw new IllegalArgumentException("未知厨房产物：" + id);
        return entry.get();
    }

    public static Holder<MobEffect> effect(String id) { return EFFECTS.get(id); }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        EFFECT_REGISTRY.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        bus.addListener(KitchenContent::capabilities);
        KitchenRuntime.register();
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, STOVE_ENTITY.get(), KitchenStoveBlockEntity::sidedHandler);
    }

    private static final class MealEffect extends MobEffect {
        private MealEffect(KitchenRules.Meal meal) {
            super(MobEffectCategory.BENEFICIAL, 0xBA934E);
            if (meal.id().equals("kitchen_pumpkin_pancake")) {
                addAttributeModifier(Attributes.SNEAKING_SPEED, KitchenRules.id("kitchen/sneak"),
                        meal.percent() / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            }
        }
    }
}
