package com.example.myvillage.client.forging;

import com.example.myvillage.block.QuenchingTroughBlock;
import com.example.myvillage.block.entity.QuenchingTroughBlockEntity;
import com.example.myvillage.forging.WorkshopRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;

public final class QuenchingTroughRenderer implements BlockEntityRenderer<QuenchingTroughBlockEntity> {
    public QuenchingTroughRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(QuenchingTroughBlockEntity block,float partialTick,PoseStack pose,MultiBufferSource buffers,int light,int overlay) {
        if(block.getLevel()==null||block.started()<0||!WorkshopRules.KINDS.contains(block.kind())) return;
        float progress=(block.getLevel().getGameTime()-block.started()+partialTick)/WorkshopRules.get().operation_ticks().get("quench");
        if(progress<0||progress>=1) return;
        pose.pushPose();pose.translate(.5,0,.5);
        pose.mulPose(Axis.YP.rotationDegrees(180-block.getBlockState().getValue(QuenchingTroughBlock.FACING).toYRot()));
        pose.translate(-.5,0,-.5);
        // 器坯先压入水面，再提起；冷色在入水后替换红热金属。
        float immersion=Math.min(1,Math.min(progress/.25f,(1-progress)/.2f));
        pose.translate(0,.85-.38*immersion,0);
        ForgingAnvilRenderer.draw(block.kind()+(progress<.25f?"_1_hot":"_2"),pose,buffers,
                progress<.25f?0x00F000F0:light,overlay,1,1,1);
        pose.popPose();
    }
}
