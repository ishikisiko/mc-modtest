package com.example.myvillage.qianji;

import com.google.gson.JsonParser;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralBookcaseOutlineTest {
    @Test void serverSelectionMatchesTheGeneratedBooksInAllFourDirections(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var outline=new ProceduralBookcaseOutline(JsonParser.parseString("{\"boxes\":[[0,0,0,16,14,7,\"procedural_books\",0,0]]}").getAsJsonObject());
        var region=new ProceduralBooks.Region(0,0,0,16,14,7,0,0);var rules=ProceduralBooks.rules();
        Direction[] directions={Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST};
        for(int turn=0;turn<4;turn++)for(int x=-2;x<3;x++){
            var pos=new BlockPos(x,24,7);var actual=outline.shape(directions[turn],pos);
            int seed=ProceduralBooks.pattern(turn%2==0?pos.getX():pos.getZ(),pos.getY(),0,rules);
            VoxelShape expected=Shapes.empty();
            for(var b:ProceduralBooks.generate(region,seed,rules)){
                VoxelShape local=Block.box(b.x0(),b.y0(),b.z0(),b.x1(),b.y1(),b.z1());
                for(int i=0;i<turn;i++){
                    VoxelShape rotated=Shapes.empty();
                    for(var a:local.toAabbs())rotated=Shapes.or(rotated,Shapes.box(1-a.maxZ,a.minY,a.minX,1-a.minZ,a.maxY,a.maxX));
                    local=rotated;
                }
                expected=Shapes.or(expected,local);
            }
            assertFalse(Shapes.joinIsNotEmpty(actual,expected,BooleanOp.NOT_SAME));
            assertFalse(Shapes.joinIsNotEmpty(outline.shape(directions[turn],pos),expected,BooleanOp.NOT_SAME));
        }
    }
}
