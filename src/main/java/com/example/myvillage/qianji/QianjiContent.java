package com.example.myvillage.qianji;

import com.example.myvillage.MyVillageMod;
import java.util.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class QianjiContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MyVillageMod.MOD_ID);
    public static final Map<String,DeferredBlock<?>> MATERIALS=new LinkedHashMap<>();
    static {
        var c=QianjiCatalog.get();
        for(var m:c.materials){var b=BLOCKS.register(m.id(),()->new Block(material(m)));MATERIALS.put(m.id(),b);ITEMS.registerSimpleBlockItem(b);}
        for(var bank:c.banks){var b=BLOCKS.register(bank.id(),()->new QianjiComponentBlock(bank,properties().lightLevel(s->bank.lights().get(s.getValue(QianjiComponentBlock.SHAPE)))));ITEMS.registerSimpleBlockItem(b);}
    }
    public static final DeferredBlock<QianjiAtmosphereBlock> ANCHOR=BLOCKS.register("qianji_atmosphere_anchor",()->new QianjiAtmosphereBlock(properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<QianjiAtmosphereEntity>> ENTITY=ENTITIES.register("qianji_atmosphere_anchor",()->BlockEntityType.Builder.of(QianjiAtmosphereEntity::new,ANCHOR.get()).build(null));
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("qianji",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.myvillage.qianji")).icon(()->new ItemStack(MATERIALS.get("qianji_tile").get())).displayItems((p,out)->MATERIALS.values().forEach(b->out.accept(b.get()))).build());
    private static BlockBehaviour.Properties properties(){var c=QianjiCatalog.get();return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(c.strength,c.resistance).sound(SoundType.STONE);}
    private static BlockBehaviour.Properties material(QianjiCatalog.Material m){var p=properties().sound(switch(m.sound()){case "wood"->SoundType.WOOD;case "glass"->SoundType.GLASS;case "grass"->SoundType.GRASS;default->SoundType.STONE;}).lightLevel(s->m.light());if(!m.opaque())p.noOcclusion();if(!m.collides())p.noCollission();return p;}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);ENTITIES.register(bus);}
    private QianjiContent(){}
}
