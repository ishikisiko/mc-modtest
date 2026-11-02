package com.example.myvillage.client.entity;
import com.example.myvillage.entity.undead.UndeadArrowEntity;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
public final class UndeadArrowRenderer extends ArrowRenderer<UndeadArrowEntity> {
    private static final ResourceLocation TEXTURE=ResourceLocation.withDefaultNamespace("textures/entity/projectiles/arrow.png");
    public UndeadArrowRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(UndeadArrowEntity entity){return TEXTURE;}
}
