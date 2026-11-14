package com.example.myvillage.client.qianji;

import com.example.myvillage.qianji.ProceduralBooks;
import com.google.gson.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.*;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/** 仅在区块建模时生成书册四边形；保留原静态木框和玻璃层，不使用逐帧渲染器。 */
public final class ProceduralBooksModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<BlockPos> POSITION=new ModelProperty<>();
    private final List<ProceduralBooks.Region> regions;
    private final ProceduralBooks.Rules rules;
    private final Map<String,TextureAtlasSprite> sprites;
    private final ModelState transform;
    private final Map<List<Integer>,List<BakedQuad>> cache=new LinkedHashMap<>(4,.75f,true);

    private ProceduralBooksModel(BakedModel base,List<ProceduralBooks.Region> regions,ProceduralBooks.Rules rules,
                                 Map<String,TextureAtlasSprite> sprites,ModelState transform){
        super(base);this.regions=List.copyOf(regions);this.rules=rules;this.sprites=Map.copyOf(sprites);this.transform=transform;
    }
    @Override public ModelData getModelData(BlockAndTintGetter view,BlockPos pos,BlockState state,ModelData data){
        return originalModel.getModelData(view,pos,state,data).derive().with(POSITION,pos.immutable()).build();
    }
    private synchronized List<BakedQuad> books(ModelData data){
        BlockPos pos=data.get(POSITION);if(pos==null)pos=BlockPos.ZERO;
        // 模型局部 Y 轴不随水平旋转变化，同一排跨 Y 格的部分具有相同种子。
        List<Integer> key=new ArrayList<>(regions.size());
        for(var r:regions){
            Direction horizontal=transform.getRotation().rotateTransform(r.facing()==0||r.facing()==2?Direction.EAST:Direction.SOUTH);
            int column=horizontal.getAxis()==Direction.Axis.X?pos.getX():pos.getZ();
            key.add(ProceduralBooks.pattern(column,pos.getY(),r.rowY(),rules));
        }
        List<BakedQuad> found=cache.get(key);if(found!=null)return found;
        List<BakedQuad> quads=new ArrayList<>();
        for(int i=0;i<regions.size();i++)for(var b:ProceduralBooks.generate(regions.get(i),key.get(i),rules)){
            Map<Direction,BlockElementFace> faces=new EnumMap<>(Direction.class);
            for(Direction face:Direction.values())faces.put(face,new BlockElementFace(null,-1,"#"+b.role(),new BlockFaceUV(null,0)));
            var element=new BlockElement(new Vector3f(b.x0(),b.y0(),b.z0()),new Vector3f(b.x1(),b.y1(),b.z1()),faces,null,true);
            for(Direction face:Direction.values())quads.add(BlockModel.bakeFace(element,faces.get(face),sprites.get(b.role()),face,transform));
        }
        found=List.copyOf(quads);cache.put(List.copyOf(key),found);
        while(cache.size()>rules.cachedVariants())cache.remove(cache.keySet().iterator().next());
        return found;
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random,ModelData data,@Nullable RenderType layer){
        List<BakedQuad> base=originalModel.getQuads(state,side,random,data,layer);
        if(side!=null||(layer!=null&&layer!=RenderType.solid()))return base;
        var result=new ArrayList<>(base);result.addAll(books(data));return result;
    }
    @Override public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random){
        return getQuads(state,side,random,ModelData.EMPTY,null);
    }
    @Override public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data){
        return ChunkRenderTypeSet.union(originalModel.getRenderTypes(state,random,data),ChunkRenderTypeSet.of(RenderType.solid()));
    }
    @Override public TriState useAmbientOcclusion(BlockState state,ModelData data,RenderType layer){
        return layer==RenderType.solid()?TriState.TRUE:originalModel.useAmbientOcclusion(state,data,layer);
    }
    @Override public List<BakedModel> getRenderPasses(ItemStack stack,boolean fabulous){
        List<BakedModel> result=new ArrayList<>();
        result.add(new BakedModelWrapper<BakedModel>(this){
            @Override public List<RenderType> getRenderTypes(ItemStack s,boolean f){return List.of(RenderType.entitySolid(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));}
            @Override public List<BakedQuad> getQuads(BlockState s,Direction face,RandomSource r){return face==null?books(ModelData.EMPTY):List.of();}
            @Override public List<BakedQuad> getQuads(BlockState s,Direction face,RandomSource r,ModelData data,RenderType layer){return face==null?books(ModelData.EMPTY):List.of();}
        });
        result.addAll(originalModel.getRenderPasses(stack,fabulous));return List.copyOf(result);
    }
    public record Geometry(BlockModel base,List<ProceduralBooks.Region> regions) implements IUnbakedGeometry<Geometry> {
        @Override public void resolveParents(Function<ResourceLocation,UnbakedModel> models,IGeometryBakingContext context){base.resolveParents(models);}
        @Override public BakedModel bake(IGeometryBakingContext context,ModelBaker baker,Function<Material,TextureAtlasSprite> getter,ModelState state,ItemOverrides overrides){
            if(!context.getRootTransform().isIdentity())state=UnbakedGeometryHelper.composeRootTransformIntoModelState(state,context.getRootTransform());
            var rules=ProceduralBooks.rules();Map<String,TextureAtlasSprite> sprites=new HashMap<>();
            for(String role:rules.roles())sprites.put(role,getter.apply(context.getMaterial(role)));
            return new ProceduralBooksModel(base.bake(baker,getter,state),regions,rules,sprites,state);
        }
    }
    public static final class Loader implements IGeometryLoader<Geometry> {
        public static final Loader INSTANCE=new Loader();
        @Override public Geometry read(JsonObject json,JsonDeserializationContext context){
            List<ProceduralBooks.Region> regions=new ArrayList<>();
            for(var item:json.getAsJsonArray("book_regions"))regions.add(ProceduralBooks.Region.fromJson(item.getAsJsonArray()));
            if(regions.isEmpty()||regions.size()>32)throw new JsonParseException("书册排列区域数量越界");
            return new Geometry(context.deserialize(json.get("base_model"),BlockModel.class),List.copyOf(regions));
        }
        private Loader(){}
    }
}
