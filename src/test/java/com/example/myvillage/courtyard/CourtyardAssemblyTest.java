package com.example.myvillage.courtyard;

import com.example.myvillage.network.CourtyardVariantPayload;
import io.netty.buffer.Unpooled;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class CourtyardAssemblyTest {
    static final Map<String,CourtyardAssemblyBlock> blocks=new LinkedHashMap<>();
    static final Map<String,CourtyardAssemblyItem> items=new LinkedHashMap<>();
    @BeforeAll @SuppressWarnings("deprecation") static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
        var br=(MappedRegistry<Block>)BuiltInRegistries.BLOCK;var ir=(MappedRegistry<Item>)BuiltInRegistries.ITEM;
        synchronized(br){synchronized(ir){br.unfreeze();ir.unfreeze();try{
            for(var f:CourtyardCatalog.all()){
                var key=ResourceLocation.fromNamespaceAndPath("myvillage_test",f.id());
                var b=Registry.register(br,key,CourtyardBlocks.create(f.id(),BlockBehaviour.Properties.of().noOcclusion()));blocks.put(f.id(),b);
                var item=Registry.register(ir,key,new CourtyardAssemblyItem(b,new Item.Properties()));items.put(f.id(),item);
            }
        }finally{ir.freeze();br.freeze();}}}
    }
    static boolean inside(VoxelShape shape,double x,double y,double z){return shape.toAabbs().stream().anyMatch(b->b.contains(x,y,z));}

    @Test void wholeWindowHasNinePartsAndStableRootInEveryFacing(){
        var b=blocks.get("courtyard_window");var v=b.family().variant(0);var root=new BlockPos(15,70,15);
        assertEquals(9,v.partCount());
        for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            Set<BlockPos> footprint=new HashSet<>();
            for(int p=0;p<v.partCount();p++){var pos=root.offset(v.offset(p,facing));assertTrue(footprint.add(pos));assertEquals(root,b.root(pos,b.state(0,p,facing,false)));}
            assertEquals(9,footprint.size());
        }
    }
    @Test void largestCurvedRoofHasBoundedPartsAndLegacyStatesDefaultToRoot(){
        var b=blocks.get("courtyard_roof");var v=b.family().variant(12);var root=new BlockPos(10,80,10);
        assertEquals(25,v.width());assertEquals(100,v.partCount());
        for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            Set<BlockPos> footprint=new HashSet<>();
            for(int part=0;part<v.partCount();part++){
                var state=b.state(12,part,facing,false);var pos=root.offset(v.offset(part,facing));
                assertTrue(footprint.add(pos));assertEquals(root,b.root(pos,state));
            }
        }
        var legacy=NbtUtils.writeBlockState(b.state(0,0,Direction.NORTH,false));
        legacy.getCompound("Properties").remove("part");
        assertEquals(b.state(0,0,Direction.NORTH,false),NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),legacy));
        var item=items.get("courtyard_roof");var stack=new ItemStack(item,2);var id=BuiltInRegistries.ITEM.getKey(item);
        assertTrue(CourtyardAssemblyItem.accepts(stack,id,15));assertFalse(CourtyardAssemblyItem.accepts(stack,id,16));
    }
    @Test void shapesRotateWithoutFullCubeCollisionsInOpenParts(){
        for(var f:CourtyardCatalog.all())for(var v:f.variants())for(int part=0;part<v.partCount();part++){
            var p=v.part(part);
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                double px=(x+.5)/16,pz=(z+.5)/16,y=.53125;
                boolean expected=inside(p.shape(Direction.NORTH),px,y,pz);
                for(int t=0;t<4;t++){assertEquals(expected,inside(p.shape(CourtyardCatalog.facing(t)),px,y,pz),f.id());double next=1-pz;pz=px;px=next;}
            }
        }
        var gate=CourtyardCatalog.get("courtyard_gate").variant(0);
        assertTrue(gate.part(7).shape(Direction.NORTH).isEmpty());
    }
    @Test void rotationsAndMirrorsPreserveWholeOwnershipAndSavedStates(){
        for(var b:blocks.values())for(int vi=0;vi<b.family().variants().size();vi++){
            var v=b.family().variant(vi);
            for(int p=0;p<v.partCount();p++){
                var state=b.state(vi,p,Direction.NORTH,true);
                assertEquals(state,state.rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90).rotate(Rotation.CLOCKWISE_90));
                assertEquals(state,state.mirror(Mirror.FRONT_BACK).mirror(Mirror.FRONT_BACK));
                assertEquals(state,state.mirror(Mirror.LEFT_RIGHT).mirror(Mirror.LEFT_RIGHT));
                assertEquals(state,NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(),NbtUtils.writeBlockState(state)));
            }
        }
    }
    @Test void variantSelectionIsBoundedToTheHeldItemAndPreservesCount(){
        var item=items.get("courtyard_window");var stack=new ItemStack(item,7);var id=BuiltInRegistries.ITEM.getKey(item);
        assertTrue(CourtyardAssemblyItem.accepts(stack,id,2));
        assertFalse(CourtyardAssemblyItem.accepts(stack,id,-1));assertFalse(CourtyardAssemblyItem.accepts(stack,id,3));
        assertFalse(CourtyardAssemblyItem.accepts(stack,ResourceLocation.parse("minecraft:stone"),1));
        CourtyardAssemblyItem.setVariant(stack,1);assertEquals(7,stack.getCount());assertEquals(1,item.variant(stack));
        assertEquals(2,stack.get(DataComponents.CUSTOM_MODEL_DATA).value());
        assertThrows(IllegalArgumentException.class,()->CourtyardAssemblyItem.setVariant(stack,100000));
    }
    @Test void selectionPayloadContainsOnlyIdentityHandAndIndex(){
        assertEquals(3,CourtyardVariantPayload.class.getRecordComponents().length);
        var packet=new CourtyardVariantPayload(ResourceLocation.parse("myvillage:courtyard_window"),true,2);
        var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY,ConnectionType.NEOFORGE);
        try{CourtyardVariantPayload.STREAM_CODEC.encode(buffer,packet);assertEquals(packet,CourtyardVariantPayload.STREAM_CODEC.decode(buffer));assertEquals(0,buffer.readableBytes());}
        finally{buffer.release();}
    }
}
