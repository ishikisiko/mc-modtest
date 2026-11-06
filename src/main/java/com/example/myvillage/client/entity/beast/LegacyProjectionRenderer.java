package com.example.myvillage.client.entity.beast;

import com.example.myvillage.legacy.LegacyProjectionEntity;
import com.example.myvillage.legacy.LegacyRules;
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
        if(e.kind()>=LegacyProjectionEntity.FIRE_BURST&&e.kind()!=LegacyProjectionEntity.SHADOW_PATH)return;
        poses.pushPose();
        if(e.kind()==LegacyProjectionEntity.SHADOW||e.kind()==LegacyProjectionEntity.SHADOW_PATH){
            var owner=e.level().getEntity(e.ownerId());
            if(owner==null){poses.popPose();return;}
            int copies=e.kind()==LegacyProjectionEntity.SHADOW?1:(int)LegacyRules.get().visual("shadow_copies");
            float fade=1-e.age(partial)/e.duration();
            for(int i=0;i<copies;i++) {
                poses.pushPose();
                double progress=copies==1?0:i/(double)(copies-1);var offset=e.vector().scale(progress);
                poses.translate(offset.x,offset.y,offset.z);poses.mulPose(Axis.YP.rotationDegrees(-yaw));
                poses.scale(-1,-1,1);poses.translate(0,-1.5,0);
                var skin=owner instanceof net.minecraft.client.player.AbstractClientPlayer player?player.getSkin():net.minecraft.client.resources.DefaultPlayerSkin.get(e.getUUID());
                var model=skin.model()==net.minecraft.client.resources.PlayerSkin.Model.SLIM?slim:wide;
                model.setAllVisible(true);model.crouching=false;model.riding=false;model.young=false;model.attackTime=0;
                if(owner instanceof net.minecraft.world.entity.LivingEntity living)model.setupAnim(living,0,0,0,0,0);
                double alpha=LegacyRules.get().visual("shadow_alpha")*(copies==1?.65+.35*fade:fade*(1-progress*.55));
                int tint=((int)Math.clamp(Math.round(alpha*255),0,255)<<24)|LegacyRules.get().color("shadow");
                model.renderToBuffer(poses,buffers.getBuffer(RenderType.entityTranslucent(skin.texture())),15728640,OverlayTexture.NO_OVERLAY,tint);
                poses.popPose();
            }
        }else{
            poses.mulPose(Axis.YP.rotationDegrees(-yaw));
            if(e.kind()==LegacyProjectionEntity.SWORD) {
                float scale=(float)(LegacyRules.get().visual("sword_body_scale")*Math.min(1,(e.age(partial)+1)/LegacyRules.get().visual("sword_summon_ticks")));
                poses.mulPose(Axis.XP.rotationDegrees(90+e.getXRot()));poses.scale(scale,scale,scale);
                // The server's swept point is the visible blade tip; the body trails behind it.
                poses.translate(0,.5-LegacyRules.get().shape("guiyan_sword","tip_y")/16,0);
            }
            else if(e.kind()==LegacyProjectionEntity.RULER) {
                float scale=(float)LegacyRules.get().visual("ruler_model_scale");poses.scale(scale,scale,scale);poses.translate(0,.5,0);
            }
            else{poses.translate(0,.15,0);poses.scale(.35F,.35F,.35F);poses.mulPose(Axis.YP.rotationDegrees((e.tickCount+partial)*3));}
            items.renderStatic(e.visualStack(),ItemDisplayContext.NONE,e.kind()==LegacyProjectionEntity.EMBER?15728640:light,OverlayTexture.NO_OVERLAY,poses,buffers,e.level(),e.getId());
        }
        poses.popPose();super.render(e,yaw,partial,poses,buffers,light);
    }
    @Override public boolean shouldRender(LegacyProjectionEntity e,net.minecraft.client.renderer.culling.Frustum frustum,double x,double y,double z) {
        double extent=Math.max(e.radius(),e.kind()==LegacyProjectionEntity.RULER?LegacyRules.get().deployedRulerHeight():3);
        return e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z))&&frustum.isVisible(e.getBoundingBox().expandTowards(e.vector()).inflate(extent));
    }
    @Override public ResourceLocation getTextureLocation(LegacyProjectionEntity e){return InventoryMenu.BLOCK_ATLAS;}
}
