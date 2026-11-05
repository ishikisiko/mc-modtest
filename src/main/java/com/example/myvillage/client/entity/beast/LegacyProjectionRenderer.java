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

/** Uses real item meshes and a generated continuous robe for the temporary shadow. */
public final class LegacyProjectionRenderer extends EntityRenderer<LegacyProjectionEntity> {
    private final ItemRenderer items;
    private final ModelPart shadow;
    private static final ResourceLocation WRAITH=ResourceLocation.parse("myvillage:mirror_wraith");
    public LegacyProjectionRenderer(EntityRendererProvider.Context context){
        super(context);items=context.getItemRenderer();shadow=context.bakeLayer(BeastRenderer.layer(WRAITH));
    }
    @Override public void render(LegacyProjectionEntity e,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        if(e.kind()==LegacyProjectionEntity.SHADOW){
            poses.scale(-1,-1,1);poses.translate(0,-1.5,0);
            shadow.render(poses,buffers.getBuffer(RenderType.entityTranslucent(BeastRenderer.texture(WRAITH))),15728640,OverlayTexture.NO_OVERLAY,0x6699ccff);
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
