package com.example.myvillage.client.entity.beast;

import com.example.myvillage.legacy.LegacyProjectionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;

/** Uses actual item meshes and the caster's skin for the temporary shadow. */
public final class LegacyProjectionRenderer extends EntityRenderer<LegacyProjectionEntity> {
    private final ItemRenderer items;
    private final net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity> wide;
    private final net.minecraft.client.model.PlayerModel<net.minecraft.world.entity.LivingEntity> slim;
    public LegacyProjectionRenderer(EntityRendererProvider.Context context){
        super(context);items=context.getItemRenderer();
        wide=new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER),false);
        slim=new net.minecraft.client.model.PlayerModel<>(context.bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER_SLIM),true);
    }
    @Override public void render(LegacyProjectionEntity e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        if(e.kind()==LegacyProjectionEntity.SHADOW){
            poses.scale(-1,-1,1);poses.translate(0,-1.5,0);
            var owner=e.level().getEntity(e.ownerId());
            var skin=owner instanceof net.minecraft.client.player.AbstractClientPlayer player?player.getSkin():net.minecraft.client.resources.DefaultPlayerSkin.get(e.getUUID());
            var model=skin.model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM?slim:wide;
            model.setAllVisible(true);model.crouching=false;model.riding=false;model.young=false;model.attackTime=0;
            if(owner instanceof net.minecraft.world.entity.LivingEntity living)model.setupAnim(living,0,0,0,0,0);
            model.renderToBuffer(poses,buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),15728640,OverlayTexture.NO_OVERLAY,0x6699ccff);
        }else{
            if(e.kind()==LegacyProjectionEntity.SWORD)poses.mulPose(Axis.XP.rotationDegrees(90));
            else if(e.kind()==LegacyProjectionEntity.RULER)poses.translate(0,.5,0);
            else{poses.translate(0,.15,0);poses.scale(.35F,.35F,.35F);poses.mulPose(Axis.YP.rotationDegrees((e.tickCount+partial)*3));}
            items.renderStatic(e.visualStack(),ItemDisplayContext.NONE,e.kind()==LegacyProjectionEntity.EMBER?15728640:light,OverlayTexture.NO_OVERLAY,poses,buffers,e.level(),e.getId());
        }
        poses.popPose();super.render(e,yaw,partial,poses,buffers,light);
    }
    @Override public ResourceLocation getTextureLocation(LegacyProjectionEntity e){return InventoryMenu.BLOCK_ATLAS;}
}
