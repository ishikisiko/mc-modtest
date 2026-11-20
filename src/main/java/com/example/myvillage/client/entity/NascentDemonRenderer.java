package com.example.myvillage.client.entity;

import com.example.myvillage.entity.NascentDemon;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class NascentDemonRenderer extends MobRenderer<NascentDemon, PlayerModel<NascentDemon>> {
    private final PlayerModel<NascentDemon> wide;
    private final PlayerModel<NascentDemon> slim;

    public NascentDemonRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), .5F);
        wide = model;
        slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
    }

    private PlayerSkin skin(NascentDemon demon) {
        return demon.owner().map(demon.level()::getPlayerByUUID).filter(AbstractClientPlayer.class::isInstance)
                .map(AbstractClientPlayer.class::cast).map(AbstractClientPlayer::getSkin)
                .orElseGet(() -> DefaultPlayerSkin.get(demon.owner().orElse(demon.getUUID())));
    }

    @Override
    public void render(NascentDemon demon, float yaw, float partial, PoseStack pose,
            MultiBufferSource buffers, int light) {
        model = skin(demon).model() == PlayerSkin.Model.SLIM ? slim : wide;
        super.render(demon, yaw, partial, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(NascentDemon demon) {
        return skin(demon).texture();
    }
}
