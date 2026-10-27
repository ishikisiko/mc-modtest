package com.example.myvillage.client.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.block.ForgingAnvilBlock;
import com.example.myvillage.block.entity.ForgingAnvilBlockEntity;
import com.example.myvillage.forging.WorkshopDisplay;
import com.example.myvillage.forging.WorkshopRules;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** 台面器坯与落锤。朝向、工序和时间均读取服务器同步的只读表现。 */
public final class ForgingAnvilRenderer implements BlockEntityRenderer<ForgingAnvilBlockEntity> {
    private static final java.util.List<String> KINDS = WorkshopRules.KINDS;
    public ForgingAnvilRenderer(BlockEntityRendererProvider.Context context) {}
    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "block/workshop/"+name));
    }
    public static void registerModels(ModelEvent.RegisterAdditional event) {
        event.register(model("hammer"));
        for (String kind : KINDS) for (int stage=0; stage<6; stage++) {
            event.register(model(kind+"_"+stage));
            if (stage==0||stage==1||stage==5) event.register(model(kind+"_"+stage+"_hot"));
        }
    }
    static void draw(String name, PoseStack pose, MultiBufferSource buffers, int light, int overlay,
                             float red, float green, float blue) {
        var mc=Minecraft.getInstance();
        mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(), buffers.getBuffer(RenderType.solid()), null,
                mc.getModelManager().getModel(model(name)), red, green, blue, light, overlay);
    }
    @Override public void render(ForgingAnvilBlockEntity block, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        var display=block.display();
        pose.pushPose();
        pose.translate(.5,0,.5);
        // JSON 北向为零；Minecraft 的方位角以南向为零。
        pose.mulPose(Axis.YP.rotationDegrees(180-block.getBlockState().getValue(ForgingAnvilBlock.FACING).toYRot()));
        pose.translate(-.5,0,-.5);
        if (KINDS.contains(display.kind())) {
            pose.pushPose();pose.translate(0,1.005,0);
            boolean hot=display.hot()&&(display.stage()==0||display.stage()==1||display.stage()==5);
            draw(display.kind()+"_"+display.stage()+(hot?"_hot":""),pose,buffers,hot?0x00F000F0:light,overlay,1,1,1);
            pose.popPose();
        }
        float elapsed=block.getLevel()==null?-1:(float)(block.getLevel().getGameTime()-display.strikeStart())+partialTick;
        var rules=WorkshopRules.get();
        boolean swinging=display.strikeStart()>=0&&elapsed>=0&&elapsed<rules.strike_ticks();
        pose.pushPose();
        pose.translate(.97,1.28,.5);
        pose.mulPose(Axis.ZP.rotationDegrees(swinging?WorkshopDisplay.hammerAngle(elapsed,rules.contact_tick(),rules.strike_ticks()):90));
        pose.translate(-.5,0,-.5);
        draw("hammer",pose,buffers,light,overlay,1,1,1);
        pose.popPose();pose.popPose();
    }
}
