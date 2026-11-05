package com.example.myvillage.client.entity.beast;

import com.example.myvillage.legacy.LegacyBeastEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class LegacyBeastRenderer extends BeastRenderer<LegacyBeastEntity> {
    public LegacyBeastRenderer(EntityRendererProvider.Context context,ResourceLocation id){super(context,id);}
    @Override public void render(LegacyBeastEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();if(entity.sword()&&!entity.dormant())pose.translate(0,.35,0);
        super.render(entity,yaw,partial,pose,buffers,light);pose.popPose();
    }
}
