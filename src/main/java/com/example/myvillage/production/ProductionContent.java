package com.example.myvillage.production;

import com.example.myvillage.MyVillageMod;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ProductionContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyVillageMod.MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(Registries.FEATURE, MyVillageMod.MOD_ID);
    private static final Map<ProductionPart, DeferredBlock<ProductionBlock>> PARTS = new EnumMap<>(ProductionPart.class);
    private static final Supplier<Item> CORE = ITEMS.registerSimpleItem("condensation_core");
    private static final Supplier<Item> JADE_CORE = ITEMS.registerSimpleItem("jade_condensation_core");

    static {
        for (var part : ProductionPart.values()) {
            var block = BLOCKS.register(part.id, () -> part == ProductionPart.CRYSTAL_CHANNEL
                    ? new CrystalChannelBlock(properties(part)) : new ProductionBlock(part, properties(part)));
            PARTS.put(part, block);
            if (!part.eye()) ITEMS.registerSimpleBlockItem(part.id, block);
        }
        FEATURES.register("natural_mine_eye", MineEyeFeature::new);
        TABS.register("passive_production", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.yuanxian.passive_production"))
                .icon(() -> new ItemStack(block(ProductionPart.MINE_STATION)))
                .displayItems((params, output) -> {
                    output.accept(CORE.get());
                    output.accept(JADE_CORE.get());
                    PARTS.forEach((part, block) -> { if (!part.eye()) output.accept(block.get()); });
                }).build());
    }

    static final Supplier<BlockEntityType<ProductionBlockEntity>> CONTROLLER = ENTITIES.register("production_controller",
            () -> BlockEntityType.Builder.of(ProductionBlockEntity::new,
                    Arrays.stream(ProductionKind.values()).map(kind -> block(kind.controller)).toArray(Block[]::new)).build(null));
    public static final Supplier<MenuType<ProductionMenu>> MENU = MENUS.register("production",
            () -> new MenuType<>(ProductionMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private static BlockBehaviour.Properties properties(ProductionPart part) {
        var props = BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).sound(SoundType.STONE).pushReaction(PushReaction.BLOCK);
        if (part.eye()) return props.strength(-1, 3600000).noLootTable();
        return props.strength(3.5F, 8).requiresCorrectToolForDrops().noOcclusion();
    }

    static ProductionBlock block(ProductionPart part) { return PARTS.get(part).get(); }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        FEATURES.register(bus);
        bus.addListener(ProductionContent::capabilities);
    }

    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(Capabilities.ItemHandler.BLOCK, (level, pos, state, unused, side) -> {
            if (level.isClientSide() || !level.hasChunkAt(pos) || side == null
                    || side != Direction.DOWN && state.getValue(ProductionBlock.FACING) != side) return null;
            var controller = ProductionPattern.resolveOutlet(level, pos, state);
            if (controller == null) return null;
            return new IItemHandler() {
                private ProductionBlockEntity current() {
                    if (!level.hasChunkAt(pos) || controller.isRemoved()) return null;
                    var actual = level.getBlockState(pos);
                    if (actual != state || ProductionPattern.resolveOutlet(level, pos, actual) != controller) return null;
                    return controller;
                }

                public int getSlots() { return controller.kind().gradeCount(); }
                private boolean valid(int slot) { return slot >= 0 && slot < getSlots(); }
                public ItemStack getStackInSlot(int slot) {
                    var root = current();
                    return root != null && valid(slot) ? root.getItem(slot + 1).copy() : ItemStack.EMPTY;
                }
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    var root = current();
                    if (root == null || !valid(slot) || amount <= 0) return ItemStack.EMPTY;
                    var item = root.getItem(slot + 1);
                    return simulate ? item.copyWithCount(Math.min(amount, item.getCount())) : root.removeItem(slot + 1, amount);
                }
                public int getSlotLimit(int slot) { return valid(slot) ? ProductionRules.STACK_TARGET : 0; }
                public boolean isItemValid(int slot, ItemStack stack) { return false; }
            };
        }, block(ProductionPart.MINE_OUTLET), block(ProductionPart.CRYSTAL_OUTLET));
    }

    private ProductionContent() { }
}
