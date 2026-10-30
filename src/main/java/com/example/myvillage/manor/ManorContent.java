package com.example.myvillage.manor;

import com.example.myvillage.MyVillageMod;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class ManorContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    public static final Map<String,DeferredBlock<?>> MATERIALS=new LinkedHashMap<>();
    static {
        var catalog=ManorCatalog.get();
        for(var m:catalog.materials) {
            var block=BLOCKS.register(m.id(),()->new Block(materialProperties(m)));
            MATERIALS.put(m.id(),block);ITEMS.registerSimpleBlockItem(block);
        }
        for(var bank:catalog.banks) {
            var block=BLOCKS.register(bank.id(),()->new ManorComponentBlock(bank,baseProperties()));
            ITEMS.registerItem(bank.id(),p->new ManorComponentItem(block.get(),p
                    .component(DataComponents.BLOCK_STATE,new BlockItemStateProperties(Map.of("shape","0")))
                    .component(DataComponents.CUSTOM_MODEL_DATA,new CustomModelData(1))));
        }
    }
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("lord_manors",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.myvillage.lord_manors"))
            .icon(()->new ItemStack(MATERIALS.get("manor_beiyuan_roof").get()))
            .displayItems((parameters,output)->MATERIALS.values().forEach(block->output.accept(block.get())))
            .build());
    private static BlockBehaviour.Properties baseProperties(){var c=ManorCatalog.get();return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(c.strength,c.resistance).sound(SoundType.STONE);}
    private static BlockBehaviour.Properties materialProperties(ManorCatalog.Material m) {
        var p=baseProperties().sound(switch(m.sound()){case "wood"->SoundType.WOOD;case "glass"->SoundType.GLASS;case "grass"->SoundType.GRASS;default->SoundType.STONE;});
        if(!m.opaque())p.noOcclusion();if(!m.collides())p.noCollission();return p;
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
    private ManorContent(){ }
}
