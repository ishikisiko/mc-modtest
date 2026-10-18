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
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

/** Registry IDs are frozen content hashes, separate for plaster/timber/tile/stone/etc. */
public final class CourtyardContent {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,MyVillageMod.MOD_ID);
    public static final Map<String,DeferredBlock<CourtyardPieceBlock>> PIECES=new LinkedHashMap<>();
    public static final Map<String,DeferredItem<BlockItem>> PIECE_ITEMS=new LinkedHashMap<>();
    static {
        for(var piece:CourtyardCatalog.all()) {
            var block=BLOCKS.register(piece.id(),()->new CourtyardPieceBlock(piece.id(),properties(piece)));
            var item=ITEMS.registerItem(piece.id(),p->new BlockItem(block.get(),p));
            PIECES.put(piece.id(),block);PIECE_ITEMS.put(piece.id(),item);
        }
    }
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("courtyard",()->CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.myvillage.courtyard"))
            .icon(()->new ItemStack(PIECE_ITEMS.get(CourtyardCatalog.all().stream().filter(p->p.full()&&p.family().equals("plaster")).findFirst().orElseThrow().id()).get()))
            .displayItems((parameters,output)->PIECE_ITEMS.values().forEach(item->output.accept(item.get())))
            .build());
    private static BlockBehaviour.Properties properties(CourtyardCatalog.Piece p) {
        SoundType sound=switch(p.sound()){case "wood"->SoundType.WOOD;case "metal"->SoundType.METAL;default->SoundType.STONE;};
        var result=BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(p.strength()).sound(sound).lightLevel(state->p.light());
        if(!p.full())result.noOcclusion();
        return result;
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TABS.register(bus);}
    private CourtyardContent(){}
}
