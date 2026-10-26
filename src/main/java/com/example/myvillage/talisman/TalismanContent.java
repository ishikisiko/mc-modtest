package com.example.myvillage.talisman;

import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.common.NeoForge;

/** 独立注册族，减少三个并行 demo 的共享文件冲突。 */
public final class TalismanContent {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("myvillage");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("myvillage");
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "myvillage");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "myvillage");
    private static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, "myvillage");
    public static final Supplier<TalismanDeskBlock> DESK = BLOCKS.registerBlock("talisman_desk", TalismanDeskBlock::new,
            BlockBehaviour.Properties.of().strength(2.5F).noOcclusion().sound(SoundType.WOOD).pushReaction(PushReaction.BLOCK));
    public static final Supplier<BlockItem> DESK_ITEM = ITEMS.registerItem("talisman_desk", p -> new BlockItem(DESK.get(), p.stacksTo(1)));
    public static final Supplier<Item> PAPER = ITEMS.registerSimpleItem("talisman_paper");
    public static final Supplier<Item> INK = ITEMS.registerSimpleItem("spiritual_ink");
    public static final Supplier<TalismanItem> LIGHTNING = ITEMS.registerItem("talisman_lightning", p -> new TalismanItem(p, 0));
    public static final Supplier<TalismanItem> SNARE = ITEMS.registerItem("talisman_snare", p -> new TalismanItem(p, 1));
    public static final Supplier<TalismanItem> WARD = ITEMS.registerItem("talisman_ward", p -> new TalismanItem(p, 2));
    public static final Supplier<DataComponentType<Integer>> QUALITY = COMPONENTS.registerComponentType("talisman_quality",
            b -> b.persistent(Codec.intRange(1, 4)).networkSynchronized(ByteBufCodecs.VAR_INT.map(TalismanQuality::require, TalismanQuality::require)));
    public static final Supplier<BlockEntityType<TalismanDeskBlockEntity>> DESK_ENTITY = ENTITIES.register("talisman_desk",
            () -> BlockEntityType.Builder.of(TalismanDeskBlockEntity::new, DESK.get()).build(null));
    public static final Supplier<MenuType<TalismanMenu>> MENU = MENUS.register("talisman_desk",
            () -> new MenuType<>(TalismanMenu::new, FeatureFlags.DEFAULT_FLAGS));
    private TalismanContent() { }
    public static ItemStack result(int recipe, int quality) {
        ItemStack stack = new ItemStack(switch (recipe) { case 0 -> LIGHTNING.get(); case 1 -> SNARE.get(); case 2 -> WARD.get(); default -> throw new IllegalArgumentException("符式无效"); });
        TalismanQuality.apply(stack, quality); return stack;
    }
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); COMPONENTS.register(bus);
        TalismanRuntime.register();
        NeoForge.EVENT_BUS.addListener(TalismanCommands::register);
    }
    public static void creative(net.minecraft.world.item.CreativeModeTab.Output output) {
        output.accept(DESK_ITEM.get()); output.accept(PAPER.get()); output.accept(INK.get());
        output.accept(LIGHTNING.get()); output.accept(SNARE.get()); output.accept(WARD.get());
    }
}
