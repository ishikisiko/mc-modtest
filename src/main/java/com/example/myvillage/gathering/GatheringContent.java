package com.example.myvillage.gathering;

import com.example.myvillage.MyVillageMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class GatheringContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MyVillageMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MyVillageMod.MOD_ID);
    private static BlockBehaviour.Properties properties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3.5F, 8)
                .sound(SoundType.STONE).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK);
    }
    public static final DeferredBlock<GatheringBlock> CORE = BLOCKS.register("spirit_gathering_core", () -> new GatheringBlock(true, properties()));
    public static final DeferredBlock<GatheringBlock> NODE = BLOCKS.register("spirit_gathering_node", () -> new GatheringBlock(false, properties().noOcclusion()));
    public static final Supplier<BlockEntityType<GatheringBlockEntity>> ENTITY = ENTITIES.register("spirit_gathering_core",
            () -> BlockEntityType.Builder.of(GatheringBlockEntity::new, CORE.get()).build(null));
    public static final Supplier<MenuType<GatheringMenu>> MENU = MENUS.register("spirit_gathering",
            () -> new MenuType<>(GatheringMenu::new, FeatureFlags.DEFAULT_FLAGS));
    static {
        ITEMS.registerSimpleBlockItem(CORE); ITEMS.registerSimpleBlockItem(NODE);
        TABS.register("spirit_gathering", () -> CreativeModeTab.builder().title(Component.literal("聚灵阵"))
                .icon(() -> new ItemStack(CORE.get())).displayItems((parameters, output) -> {
                    output.accept(CORE.get()); output.accept(NODE.get());
                }).build());
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); TABS.register(bus);
    }
    private GatheringContent() { }
}
