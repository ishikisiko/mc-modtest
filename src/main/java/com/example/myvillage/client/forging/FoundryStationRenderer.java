package com.example.myvillage.client.forging;

import com.example.myvillage.forging.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.world.item.ItemDisplayContext;

/** Read-only rendering of the workstation's actual item. */
public final class FoundryStationRenderer<T extends FoundryBlockEntity> implements BlockEntityRenderer<T> {
    public FoundryStationRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(T block,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var stack=block.displayItem();if(stack.isEmpty()||block.getLevel()==null)return;
        pose.pushPose();
        if(block.kind()==FoundryMenu.Kind.KILN){
            var facing=block.getBlockState().getValue(FoundryStationBlock.FACING);pose.translate(.5,.51,.5);pose.mulPose(Axis.YP.rotationDegrees(180-facing.toYRot()));
            pose.scale(.65f,.65f,.65f);pose.translate(-.5,0,-.5);
            var piece=WorkshopItems.valid(stack)?stack.get(com.example.myvillage.item.ModDataComponents.WORKPIECE.get()):null;
            var craft=piece!=null?WorkshopRules.get().craft(piece.recipe()):WorkshopEquipment.craft(stack).orElse(null);
            if(craft!=null){
                int stage=piece==null?5:piece.stage();boolean hot=ForgeHeat.get(stack,block.getLevel().getGameTime())>=FoundryRules.get().heat().minimum();
                boolean hotModel=hot&&(stage==0||stage==1||stage==5);
                ForgingAnvilRenderer.draw(craft.kind()+"_"+stage+(hotModel?"_hot":""),pose,buffers,hot?0x00F000F0:light,overlay,1,hot&&!hotModel?.55f:1,hot&&!hotModel?.35f:1);
            }
            pose.popPose();return;
        }else if(block.kind()==FoundryMenu.Kind.PREPARATION){
            pose.translate(.5,1.20,.5);pose.mulPose(Axis.YP.rotationDegrees((block.getLevel().getGameTime()+partial)*1.5f));pose.scale(.65f,.65f,.65f);
        }else{pose.translate(.5,.9,.5);pose.mulPose(Axis.XP.rotationDegrees(90));pose.scale(.65f,.65f,.65f);}
        Minecraft.getInstance().getItemRenderer().renderStatic(stack,ItemDisplayContext.FIXED,light,overlay,pose,buffers,block.getLevel(),0);pose.popPose();
    }
}
