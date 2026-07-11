package com.example.myvillage.client.entity;

import com.example.myvillage.MyVillageMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.FoxRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Fox;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class SimpleFoxRenderer extends FoxRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            MyVillageMod.MOD_ID,
            "textures/entity/simple_fox/simple_fox.png");

    public SimpleFoxRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(Fox fox) {
        return TEXTURE;
    }
}
