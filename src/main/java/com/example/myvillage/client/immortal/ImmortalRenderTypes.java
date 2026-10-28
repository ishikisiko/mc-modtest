package com.example.myvillage.client.immortal;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/** Per-reload instances live in the atomic model cache, never in an unbounded texture memoizer. */
final class ImmortalRenderTypes extends RenderType {
    private ImmortalRenderTypes() { super("immortal_unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    static RenderType solid(ResourceLocation texture, boolean doubleSided) {
        return create("immortal_solid",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,true,false,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false))
                        .setCullState(doubleSided?NO_CULL:CULL).setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY).createCompositeState(true));
    }
    static RenderType glass(ResourceLocation texture) {
        return create("immortal_glass",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,true,true,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_CULL_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                        .setWriteMaskState(COLOR_WRITE).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).createCompositeState(true));
    }
    static RenderType glow(ResourceLocation texture) {
        return create("immortal_local_glow",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1536,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture,false,false))
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(CULL)
                        .setWriteMaskState(COLOR_WRITE).setOverlayState(OVERLAY).createCompositeState(false));
    }
    static final RenderType EFFECTS = effectType("immortal_effects",false);
    static final RenderType TRAILS = effectType("immortal_trails",true);
    private static RenderType effectType(String name,boolean additive) {
        return create(name,DefaultVertexFormat.POSITION_COLOR,VertexFormat.Mode.QUADS,1536,false,true,
                CompositeState.builder().setShaderState(RENDERTYPE_LIGHTNING_SHADER)
                        .setTransparencyState(additive?LIGHTNING_TRANSPARENCY:TRANSLUCENT_TRANSPARENCY)
                        .setWriteMaskState(COLOR_WRITE).setCullState(NO_CULL).createCompositeState(false));
    }
    // Vanilla armor glint shader/texture/scroll, with back-face culling for glass and closed meshes.
    static final RenderType GLINT = glint(false);
    static final RenderType GLASS_GLINT = glint(true);
    private static RenderType glint(boolean glass) {
        return create(glass?"immortal_glass_glint":"immortal_armor_glint",DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,1536,false,false,CompositeState.builder()
                        .setShaderState(RENDERTYPE_ARMOR_ENTITY_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(net.minecraft.client.renderer.entity.ItemRenderer.ENCHANTED_GLINT_ENTITY,true,false))
                        .setWriteMaskState(COLOR_WRITE).setCullState(CULL).setDepthTestState(glass?LEQUAL_DEPTH_TEST:EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY).setTexturingState(ENTITY_GLINT_TEXTURING)
                        .setLayeringState(VIEW_OFFSET_Z_LAYERING).createCompositeState(false));
    }
}
