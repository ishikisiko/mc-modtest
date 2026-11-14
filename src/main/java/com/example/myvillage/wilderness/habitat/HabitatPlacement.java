package com.example.myvillage.wilderness.habitat;

import com.mojang.brigadier.StringReader;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** 仅在 FEATURES 阶段施工本区块；刷新实体从不重放这些方块。 */
public final class HabitatPlacement {
    private HabitatPlacement(){}
    public static void place(HabitatLayout layout,WorldGenLevel level,ChunkAccess chunk){
        Map<String,BlockState> states=new HashMap<>();var registry=level.registryAccess().lookupOrThrow(Registries.BLOCK);
        var at=new BlockPos.MutableBlockPos();
        for(var site:layout.sites())for(var block:layout.rules().templates.get(site.kind())){
            int x=site.x()+block.x(),y=site.y()+block.y(),z=site.z()+block.z();if((x>>4)!=chunk.getPos().x||(z>>4)!=chunk.getPos().z)continue;
            var state=states.computeIfAbsent(block.block(),key->{try{return BlockStateParser.parseForBlock(registry,new StringReader(key),false).blockState();}catch(Exception error){throw new IllegalArgumentException("生态模板方块错误："+key,error);}});
            at.set(x,y,z);chunk.setBlockState(at,state,false);
            if(state.hasBlockEntity()&&state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock blockEntity){
                var entity=blockEntity.newBlockEntity(at.immutable(),state);if(entity!=null)chunk.setBlockEntity(entity);
            }
            if(block.y()== -1)for(int depth=1;depth<=layout.rules().integer("maximum_slope")+1;depth++){
                at.set(x,y-depth,z);if(!chunk.getBlockState(at).isAir()&&chunk.getBlockState(at).getFluidState().isEmpty())break;chunk.setBlockState(at,Blocks.DIRT.defaultBlockState(),false);
            }
        }
    }
}
