package com.example.myvillage.client.manor;

import com.example.myvillage.manor.ManorCatalog;
import com.example.myvillage.manor.ManorComponentBlock;
import java.util.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.*;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

/** 透明面独立计算相邻遮挡；纯区块模型数据，没有方块实体或逐帧世界修改。 */
final class ManorGlassModel extends BakedModelWrapper<BakedModel> {
    private static final Map<ResourceLocation,String> MATERIALS=new HashMap<>(),TEXTURES=new HashMap<>();
    private static final ModelProperty<Map<Direction,Map<String,List<ManorQuadClipper.Rect>>>> MASKS=new ModelProperty<>();
    static {
        for(var m:ManorCatalog.get().materials)if(!m.opaque()){
            MATERIALS.put(ResourceLocation.fromNamespaceAndPath("myvillage",m.id()),m.id());
            TEXTURES.put(ResourceLocation.fromNamespaceAndPath("myvillage","block/lord_manors/"+m.id()),m.id());
        }
    }
    ManorGlassModel(BakedModel original){super(original);}
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){
        return layer==RenderType.translucent()?TriState.FALSE:originalModel.useAmbientOcclusion(state,data,layer);
    }

    static List<ManorQuadClipper.Rect> mask(BlockAndTintGetter view,BlockPos pos,Direction face,String material) {
        BlockState neighbor=view.getBlockState(pos);Direction inward=face.getOpposite();
        VoxelShape cover=neighbor.canOcclude()?neighbor.getFaceOcclusionShape(view,pos,inward):Shapes.empty();
        if(neighbor.getBlock() instanceof ManorComponentBlock component){
            var profile=component.profile(neighbor);
            cover=Shapes.or(cover,profile.translucentShape(material,neighbor.getValue(ManorComponentBlock.FACING)).getFaceShape(inward));
        }else if(material.equals(MATERIALS.get(BuiltInRegistries.BLOCK.getKey(neighbor.getBlock()))))cover=Shapes.block();
        int axis=ManorQuadClipper.axis(face),u=ManorQuadClipper.u(axis),v=ManorQuadClipper.v(axis);
        List<ManorQuadClipper.Rect> result=new ArrayList<>();
        for(var box:cover.toAabbs()){
            double[] low={box.minX,box.minY,box.minZ},high={box.maxX,box.maxY,box.maxZ};
            result.add(new ManorQuadClipper.Rect(low[u],low[v],high[u],high[v]));
        }
        return List.copyOf(result);
    }
    @Override public ModelData getModelData(BlockAndTintGetter view,BlockPos pos,BlockState state,ModelData data) {
        ModelData original=originalModel.getModelData(view,pos,state,data);Set<String> materials;
        if(state.getBlock() instanceof ManorComponentBlock component)materials=component.profile(state).translucentMaterials();
        else {String material=MATERIALS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));materials=material==null?Set.of():Set.of(material);}
        if(materials.isEmpty())return original;
        Map<Direction,Map<String,List<ManorQuadClipper.Rect>>> masks=new EnumMap<>(Direction.class);
        for(Direction face:Direction.values()){
            Map<String,List<ManorQuadClipper.Rect>> perMaterial=new HashMap<>();
            for(String material:materials)perMaterial.put(material,mask(view,pos.relative(face),face,material));
            masks.put(face,Map.copyOf(perMaterial));
        }
        return original.derive().with(MASKS,Map.copyOf(masks)).build();
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random,ModelData data,@Nullable RenderType layer) {
        List<BakedQuad> source=originalModel.getQuads(state,side,random,data,layer);var masks=data.get(MASKS);
        if(masks==null||side!=null)return source;
        List<BakedQuad> result=new ArrayList<>(source.size());
        for(BakedQuad quad:source){
            String material=TEXTURES.get(quad.getSprite().contents().name());
            var cover=material==null?List.<ManorQuadClipper.Rect>of():masks.get(quad.getDirection()).getOrDefault(material,List.of());
            result.addAll(ManorQuadClipper.clip(quad,cover));
        }
        return result;
    }
}
