package com.example.myvillage.client.manor;

import com.example.myvillage.manor.*;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.*;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManorGlassModelTest {
    @BeforeAll static void bootstrap(){
        if(LoadingModList.get()==null)LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        SharedConstants.tryDetectVersion();Bootstrap.bootStrap();
    }
    private static ManorCatalog.Profile window(){
        var bank=ManorCatalog.get().banks.stream().filter(b->b.id().equals("manor_lingyue_carved_7")).findFirst().orElseThrow();
        return bank.profile(21);
    }
    private static List<ManorQuadClipper.Rect> mask(ManorCatalog.Profile profile,Direction face,String material){
        Direction inward=face.getOpposite();
        return ManorGlassModel.faceMask(profile.occlusionShape(Direction.NORTH).getFaceShape(inward),
                profile.translucentShape(material,Direction.NORTH).getFaceShape(inward),face);
    }
    private static BakedQuad quad(Direction face,double plane,double u0,double v0,double u1,double v1){
        int axis=ManorQuadClipper.axis(face),u=ManorQuadClipper.u(axis),v=ManorQuadClipper.v(axis);int[] data=new int[32];
        double[][] corners={{u0,v0},{u1,v0},{u1,v1},{u0,v1}};
        for(int i=0;i<4;i++){
            data[i*8+axis]=Float.floatToRawIntBits((float)plane);
            data[i*8+u]=Float.floatToRawIntBits((float)corners[i][0]);data[i*8+v]=Float.floatToRawIntBits((float)corners[i][1]);
            data[i*8+3]=-1;data[i*8+4]=Float.floatToRawIntBits((float)corners[i][0]);data[i*8+5]=Float.floatToRawIntBits((float)corners[i][1]);
        }
        return new BakedQuad(data,3,face,null,true,false);
    }
    @Test void realLingyueFrameWouldCullTheWholeGlassFaceUnderVanillaOcclusion(){
        var profile=window();var border=Block.box(0,0,0,16,16,1).getFaceShape(Direction.EAST);
        // Block.shouldRenderFace 对 canOcclude 邻块使用的原版 ONLY_FIRST 运算。
        assertFalse(Shapes.joinIsNotEmpty(profile.occlusionShape(Direction.NORTH).getFaceShape(Direction.WEST),border,BooleanOp.ONLY_FIRST),"旧 cullface 会让木框遮挡范围决定整块玻璃显隐");
        var glass=quad(Direction.WEST,0,1.0/16,0,1,1);
        assertSame(glass,ManorQuadClipper.clip(glass,ManorGlassModel.faceMask(border,Shapes.empty(),Direction.WEST)).getFirst(),"实际外露玻璃必须保留");
    }
    @Test void adjacentRealSculptedGlassDropsTheSharedPlane(){
        var glass=quad(Direction.UP,1,0,1.0/16,10.0/16,1);
        assertTrue(ManorQuadClipper.clip(glass,mask(window(),Direction.UP,"manor_lingyue_window")).isEmpty());
    }
    @Test void differentGlassMaterialDoesNotRemoveTheExposedSurface(){
        var glass=quad(Direction.UP,1,0,1.0/16,10.0/16,1);
        assertSame(glass,ManorQuadClipper.clip(glass,mask(window(),Direction.UP,"manor_donghai_window")).getFirst());
    }
    @Test void partialOcclusionPreservesUvsAndVertexAttributesInEveryDirection(){
        for(Direction face:Direction.values()){
            double plane=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?1:0;
            var result=ManorQuadClipper.clip(quad(face,plane,0,0,1,1),List.of(new ManorQuadClipper.Rect(0,0,.5,1)));
            assertEquals(1,result.size());var clipped=result.getFirst();int[] data=clipped.getVertices();
            assertEquals(3,clipped.getTintIndex());assertFalse(clipped.hasAmbientOcclusion());assertTrue(clipped.isShade());
            int u=ManorQuadClipper.u(ManorQuadClipper.axis(face)),v=ManorQuadClipper.v(ManorQuadClipper.axis(face));
            for(int i=0;i<4;i++){
                assertTrue(Float.intBitsToFloat(data[i*8+u])>=.5);
                assertEquals(Float.intBitsToFloat(data[i*8+u]),Float.intBitsToFloat(data[i*8+4]));
                assertEquals(Float.intBitsToFloat(data[i*8+v]),Float.intBitsToFloat(data[i*8+5]));
                assertEquals(-1,data[i*8+3]);
            }
        }
    }
    @Test void insetGlassFaceIsNotClippedByTheNextBlocksBoundary(){
        var glass=quad(Direction.EAST,.625,0,0,1,1);
        assertSame(glass,ManorQuadClipper.clip(glass,List.of(new ManorQuadClipper.Rect(0,0,1,1))).getFirst());
    }
    @Test void rotatedMaterialShapesFollowAllFourBuildingFacings(){
        var profile=window();
        for(Direction facing:List.of(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)){
            var shape=profile.translucentShape("manor_lingyue_window",facing);
            assertFalse(shape.isEmpty());assertTrue(profile.translucentShape("manor_donghai_window",facing).isEmpty());
            var box=shape.bounds();
            if(facing==Direction.NORTH||facing==Direction.SOUTH)assertEquals(.625,box.maxX-box.minX);
            else assertEquals(.625,box.maxZ-box.minZ);
        }
    }
    @Test void actualModelBakeHookWrapsWorldWindowsAndPreservesInventoryGeometry(){
        List<BakedQuad> quads=List.of(quad(Direction.UP,1,0,0,1,1));
        BakedModel original=new BakedModel(){
            @Override public List<BakedQuad> getQuads(BlockState state,Direction side,RandomSource random){return quads;}
            @Override public boolean useAmbientOcclusion(){return true;}
            @Override public boolean isGui3d(){return true;}
            @Override public boolean usesBlockLight(){return true;}
            @Override public boolean isCustomRenderer(){return false;}
            @Override public TextureAtlasSprite getParticleIcon(){return null;}
            @Override public ItemOverrides getOverrides(){return ItemOverrides.EMPTY;}
        };
        var id=ResourceLocation.fromNamespaceAndPath("myvillage","manor_lingyue_carved_7");
        var world=new ModelResourceLocation(id,"facing=north,shape=21");var item=new ModelResourceLocation(id,"inventory");
        var other=new ModelResourceLocation(ResourceLocation.withDefaultNamespace("stone"),"");
        Map<ModelResourceLocation,BakedModel> models=new HashMap<>(Map.of(world,original,item,original,other,original));
        assertEquals(EventBusSubscriber.Bus.MOD,ManorModelEvents.class.getAnnotation(EventBusSubscriber.class).bus());
        ManorModelEvents.models(new ModelEvent.ModifyBakingResult(models,material->null,null));
        assertInstanceOf(ManorGlassModel.class,models.get(world));assertSame(original,models.get(item));assertSame(original,models.get(other));
        assertSame(quads,models.get(world).getQuads(null,null,RandomSource.create(1),ModelData.EMPTY,null));
    }
}
