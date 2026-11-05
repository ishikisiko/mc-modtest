package com.example.myvillage.client.entity.beast;

import com.example.myvillage.legacy.LegacyBeastEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class LegacyBeastRenderer extends BeastRenderer<LegacyBeastEntity> {
    public LegacyBeastRenderer(EntityRendererProvider.Context context,ResourceLocation id){
        super(context,id);
        addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<LegacyBeastEntity,BeastModel<LegacyBeastEntity>>(this){
            @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,LegacyBeastEntity beast,
                    float limbSwing,float limbAmount,float partial,float age,float yaw,float pitch){
                if(!beast.toad()||beast.heat()==0||beast.isInvisible())return;
                int v=Math.clamp(beast.heat()*255/com.example.myvillage.legacy.LegacyRules.get().ticks("toad_max_heat"),0,255);
                int tint=0xff000000|(v<<16)|(v<<8)|v;
                getParentModel().renderToBuffer(pose,buffers.getBuffer(net.minecraft.client.renderer.RenderType.eyes(BeastRenderer.eyesTexture(id))),15728640,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,tint);
            }
        });
    }
    @Override public void render(LegacyBeastEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();if(entity.sword()&&!entity.dormant())pose.translate(0,.35,0);
        super.render(entity,yaw,partial,pose,buffers,light);pose.popPose();
    }
}
