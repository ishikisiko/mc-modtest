package com.example.myvillage.housing;

import com.example.myvillage.MyVillageMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;

public final class HousingContent {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks(MyVillageMod.MOD_ID);
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems(MyVillageMod.MOD_ID);
    public static final List<DeferredBlock<HousingDetailBlock>> DETAILS=new ArrayList<>();
    static {
        for(int i=0;i<HousingDetails.groups().size();i++){
            final int group=i;
            var block=BLOCKS.register(HousingDetails.groups().get(i).id(),()->HousingBlocks.create(group,
                BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(1.5F).sound(SoundType.STONE)
                    .noOcclusion().dynamicShape().pushReaction(PushReaction.BLOCK)));
            DETAILS.add(block);ITEMS.registerItem(HousingDetails.groups().get(i).id(),p->new BlockItem(block.get(),p));
        }
    }
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);}
    private HousingContent(){}
}
