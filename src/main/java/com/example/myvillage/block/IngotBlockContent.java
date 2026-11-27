package com.example.myvillage.block;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.equipment.immortal.ImmortalEquipmentCatalogue;
import com.example.myvillage.item.ModItems;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Shares the ingot registrations with the equipment catalogue and the main item register. */
public final class IngotBlockContent {
    private static final String INGOT_SUFFIX = "_ingot";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final List<DeferredItem<BlockItem>> BLOCK_ITEMS = Stream.concat(
                    ModItems.ITEMS.getEntries().stream().map(DeferredHolder::getId)
                            .filter(id -> id.getPath().endsWith(INGOT_SUFFIX)),
                    ImmortalEquipmentCatalogue.ingots().stream().map(ImmortalEquipmentCatalogue.IngotDefinition::id))
            .map(IngotBlockContent::registerBlock)
            .toList();

    private IngotBlockContent() { }

    private static DeferredItem<BlockItem> registerBlock(ResourceLocation ingot) {
        String path = ingot.getPath();
        if (!path.endsWith(INGOT_SUFFIX)) {
            throw new IllegalArgumentException("缺少锭的注册后缀：" + ingot);
        }
        String name = path.substring(0, path.length() - INGOT_SUFFIX.length()) + "_block";
        var block = BLOCKS.registerBlock(name, Block::new, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .requiresCorrectToolForDrops()
                .sound(SoundType.METAL));
        return ITEMS.registerSimpleBlockItem(block);
    }

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    public static void creative(CreativeModeTab.Output output) {
        BLOCK_ITEMS.forEach(item -> output.accept(item.get()));
    }
}
