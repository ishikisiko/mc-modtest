package com.example.myvillage.mixin;

import com.example.myvillage.client.cultivation.motion.MeditationPart;
import com.example.myvillage.client.cultivation.motion.MeditationSurface;
import com.example.myvillage.client.cultivation.motion.MeditationWarp;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only tagged player/armor limbs take the alternate surface path. All other ModelParts are untouched. */
@Mixin(ModelPart.class)
public abstract class MeditationModelPartMixin implements MeditationPart {
    @Shadow @Final private List<ModelPart.Cube> cubes;
    @Unique private MeditationWarp yuanxian$warp;
    @Override public MeditationWarp yuanxian$meditationWarp(){return yuanxian$warp;}
    @Override public void yuanxian$meditationWarp(MeditationWarp value){yuanxian$warp=value;}
    @Inject(method="copyFrom",at=@At("TAIL"))
    private void yuanxian$copy(ModelPart other,CallbackInfo ci) {
        yuanxian$warp=((MeditationPart)(Object)other).yuanxian$meditationWarp();
    }
    @Inject(method="resetPose",at=@At("HEAD"))
    private void yuanxian$reset(CallbackInfo ci){yuanxian$warp=null;}
    @Inject(method="compile",at=@At("HEAD"),cancellable=true)
    private void yuanxian$compile(PoseStack.Pose pose,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci) {
        if(yuanxian$warp==null)return;
        MeditationSurface.cubes(cubes,pose,out,yuanxian$warp,light,overlay,color);ci.cancel();
    }
}
