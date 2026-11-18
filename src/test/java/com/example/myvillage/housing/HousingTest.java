package com.example.myvillage.housing;

import com.example.myvillage.courtyard.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class HousingTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();var registry=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;
        synchronized(registry){registry.unfreeze();try{
            for(var f:CourtyardCatalog.all()){
                var id=ResourceLocation.fromNamespaceAndPath("yuanxian",f.id());
                if(!registry.containsKey(id))Registry.register(registry,id,CourtyardBlocks.create(f.id(),BlockBehaviour.Properties.of().noOcclusion()));
            }
            for(int i=0;i<HousingDetails.groups().size();i++){
                var id=ResourceLocation.fromNamespaceAndPath("yuanxian",HousingDetails.groups().get(i).id());
                if(!registry.containsKey(id))Registry.register(registry,id,HousingBlocks.create(i,BlockBehaviour.Properties.of().noOcclusion().dynamicShape()));
            }
        }finally{registry.freeze();}}
    }
    @Test void approvedTwelveTemplatesParseEveryActualRegistryState(){
        assertEquals(12,HousingTemplates.all().size());assertEquals(6,HousingTemplates.players().size());
        for(var info:HousingTemplates.all()){
            var template=HousingTemplates.get(info.id());assertTrue(template.blocks().size()>5000,info.id());
            Set<BlockPos> positions=new HashSet<>();
            for(var unit:template.units())for(var cell:unit)assertTrue(positions.add(cell.pos()),info.id()+cell.pos());
            assertTrue(positions.containsAll(template.blocks().keySet()));
            assertTrue(template.blocks().values().stream().anyMatch(state->state.getBlock() instanceof net.minecraft.world.level.block.BedBlock),info.id());
        }
    }
    @Test void allPlayerHousesFitBothParcelOrientationsAtFourRotations(){
        for(var h:HousingTemplates.players())for(int turn=0;turn<4;turn++){
            assertTrue(HousingRuntime.fits(h,turn,0,0,64,72),h.id());assertTrue(HousingRuntime.fits(h,turn,0,0,72,64),h.id());
            assertFalse(HousingRuntime.fits(h,turn,72,0,64,72));assertFalse(HousingRuntime.fits(h,turn,-1,0,64,72));
            assertFalse(HousingRuntime.fits(h,turn,Integer.MAX_VALUE,0,64,72));
            assertFalse(HousingRuntime.fits(h,turn,0,Integer.MAX_VALUE,64,72));
        }
        assertFalse(HousingRuntime.fits(HousingTemplates.players().getFirst(),4,0,0,64,72));
    }
    @Test void coordinateRotationPreservesEachWholeTemplateAndFourTurns(){
        for(var info:HousingTemplates.all()){
            var positions=HousingTemplates.get(info.id()).blocks().keySet();
            for(int turn=0;turn<4;turn++){
                var rotated=new HashSet<BlockPos>();for(var p:positions)rotated.add(HousingTemplates.rotate(p,info.width(),info.depth(),turn));
                assertEquals(positions.size(),rotated.size(),info.id());
                int w=turn%2==0?info.width():info.depth(),d=turn%2==0?info.depth():info.width();
                for(var p:rotated)assertTrue(p.getX()>=0&&p.getX()<w&&p.getZ()>=0&&p.getZ()<d);
            }
        }
    }
    @Test void unfinishedTitlePersistsOwnerTemplateAndCursorTogether(){
        var data=new HousingSavedData();var owner=UUID.randomUUID();var t=new HousingSavedData.Title(owner,"测试玩家");
        t.house="shanlang";t.turn=3;t.offsetX=6;t.offsetZ=11;t.cursor=704;t.status="building";data.titles.put("capital_lingyue/plot",t);
        long edited=new BlockPos(12,30,45).asLong();data.edits.put("capital_lingyue/plot",new HashSet<>(Set.of(edited)));
        var raw=data.save(new CompoundTag(),null);var result=HousingSavedData.load(raw,null).titles.get("capital_lingyue/plot");
        assertEquals(owner,result.owner);assertEquals("shanlang",result.house);assertEquals(704,result.cursor);assertEquals(3,result.turn);
        assertEquals(6,result.offsetX);assertEquals(11,result.offsetZ);assertEquals("building",result.status);
        assertTrue(HousingSavedData.load(raw,null).edits.get("capital_lingyue/plot").contains(edited));
    }
    @Test void innerDoorOpenShapeKeepsTwoAndThreeBlockPassagesClear(){
        var family=CourtyardCatalog.get("courtyard_double_door");
        for(int vi=1;vi<3;vi++){
            var v=family.variant(vi);double w=vi==1?2:3;var portal=new net.minecraft.world.phys.AABB(1,.063,0,w+1,3,3);
            boolean closed=false;
            for(int p=0;p<v.partCount();p++){
                var offset=v.offset(p,Direction.NORTH);
                for(var box:v.part(p,true).shape(Direction.NORTH).toAabbs())assertFalse(portal.intersects(box.move(offset)),"内门全开侵入门洞");
                for(var box:v.part(p,false).shape(Direction.NORTH).toAabbs())closed|=portal.intersects(box.move(offset));
            }
            assertTrue(closed);
        }
    }
}
