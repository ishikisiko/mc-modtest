package com.example.myvillage.courtyard;

import com.example.myvillage.MyVillageMod;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Exactly one registered item and one creative/JEI entry per building family. */
public final class CourtyardContent {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    public static final Map<String,DeferredBlock<CourtyardAssemblyBlock>> BLOCK_ITEMS=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<CourtyardAssemblyItem>> ITEMS_BY_FAMILY=new LinkedHashMap<>();
    static {
        for(var family:CourtyardCatalog.all()){
            var block=BLOCKS.register(family.id(),()->CourtyardBlocks.create(family.id(),properties(family)));
            var item=ITEMS.registerItem(family.id(),p->new CourtyardAssemblyItem(block.get(),p));
            BLOCK_ITEMS.put(family.id(),block);ITEMS_BY_FAMILY.put(family.id(),item);
        }
    }
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("courtyard",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.myvillage.courtyard"))
            .icon(()->new ItemStack(ITEMS_BY_FAMILY.get("courtyard_window").get()))
            .displayItems((parameters,output)->ITEMS_BY_FAMILY.values().forEach(item->output.accept(item.get())))
            .build());
    private static BlockBehaviour.Properties properties(CourtyardCatalog.Family f){
        SoundType sound=switch(f.sound()){case "wood"->SoundType.WOOD;case "metal"->SoundType.METAL;default->SoundType.STONE;};
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(f.strength()).sound(sound).noOcclusion()
                .pushReaction(PushReaction.BLOCK).lightLevel(state->f.light());
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
    private CourtyardContent(){}
}
