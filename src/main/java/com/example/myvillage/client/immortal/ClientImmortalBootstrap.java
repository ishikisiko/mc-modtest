package com.example.myvillage.client.immortal;

import com.example.myvillage.equipment.immortal.ImmortalEquipmentContent;
import com.example.myvillage.client.combat.ImmortalArmPoseBridge;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Separate CLIENT entrypoint: no central registration edits and no changes to the old armor renderer. */
@Mod(value="myvillage",dist=Dist.CLIENT)
public final class ClientImmortalBootstrap {
    private static final Model EMPTY=new Model(RenderType::entitySolid) {
        @Override public void renderToBuffer(PoseStack poses,VertexConsumer out,int light,int overlay,int color) { }
    };
    private static final HumanoidModel<LivingEntity> FIRST_PERSON=skeleton();
    public ClientImmortalBootstrap(IEventBus bus,ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT,ImmortalClientConfig.SPEC,"myvillage-immortal-client.toml");
        bus.addListener(ClientImmortalBootstrap::reload);
        bus.addListener(ClientImmortalBootstrap::extensions);
        bus.addListener(ClientImmortalBootstrap::layers);
        NeoForge.EVENT_BUS.addListener(ClientImmortalBootstrap::arm);
    }
    private static boolean supported(LivingEntity entity) {
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer instanceof LivingEntityRenderer<?,?> living&&living.getModel() instanceof HumanoidModel<?>;
    }
    private static void reload(RegisterClientReloadListenersEvent event) {event.registerReloadListener(ImmortalMeshResources.INSTANCE);}
    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public Model getGenericArmorModel(LivingEntity entity,ItemStack stack,EquipmentSlot slot,HumanoidModel<?> original) {
                // Drawing is owned by the layer whenever the entity exposes the HumanoidModel bone contract.
                return supported(entity)?EMPTY:original;
            }
        },ImmortalEquipmentContent.armorItems());
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void add(LivingEntityRenderer renderer) {
        if(renderer.getModel() instanceof HumanoidModel)renderer.addLayer(new ImmortalArmorLayer(renderer));
    }
    private static void layers(EntityRenderersEvent.AddLayers event) {
        java.util.Set<LivingEntityRenderer<?,?>> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(var skin:event.getSkins()) {
            var renderer=event.getSkin(skin);
            if(renderer instanceof LivingEntityRenderer<?,?> living&&seen.add(living))add(living);
        }
        for(var type:event.getEntityTypes()) {
            var renderer=event.getRenderer(type);
            if(renderer instanceof LivingEntityRenderer<?,?> living&&seen.add(living))add(living);
        }
    }
    private static void arm(RenderArmEvent event) {
        var player=event.getPlayer();
        float partialTick=Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
        if(ImmortalArmPoseBridge.ownsArm(player,event.getArm(),partialTick))return;
        if(player.isInvisible()||player.isSpectator())return;
        var renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player);
        if(!(renderer instanceof PlayerRenderer vanilla))return;
        // RenderArmEvent precedes PlayerRenderer.renderHand's reset. Clone its live model and perform
        // that same reset on our private skeleton, without changing the renderer or hiding the skin.
        copy(vanilla.getModel(),FIRST_PERSON);
        FIRST_PERSON.attackTime=0;FIRST_PERSON.crouching=false;FIRST_PERSON.swimAmount=0;
        FIRST_PERSON.setupAnim(player,0,0,0,0,0);
        var bone=event.getArm()==HumanoidArm.RIGHT?FIRST_PERSON.rightArm:FIRST_PERSON.leftArm;
        bone.xRot=0;
        renderFirstPersonArm(player,event.getArm(),FIRST_PERSON,event.getPoseStack(),event.getMultiBufferSource(),
                event.getPackedLight(),Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true));
    }
    /** Integration hook for a renderer that already supplies its own arm bone pose (not a weapon IK solver). */
    public static void renderFirstPersonArm(AbstractClientPlayer player,HumanoidArm arm,HumanoidModel<?> posedModel,
            PoseStack poses,MultiBufferSource buffers,int light,float partialTick) {
        if(player.isInvisible()||player.isSpectator())return;
        ImmortalMeshRenderer.draw(ImmortalArmorLayer.wearing(player),posedModel,poses,buffers,light,
                (player.tickCount+partialTick)/20.0,ImmortalArmorLayer.effects(player),ImmortalClientConfig.elements(),
                arm==HumanoidArm.RIGHT?"right_arm":"left_arm");
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    private static void copy(HumanoidModel from,HumanoidModel to) {from.copyPropertiesTo(to);}
    private static HumanoidModel<LivingEntity> skeleton() {
        var mesh=new MeshDefinition();
        for(String bone:java.util.List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))
            mesh.getRoot().addOrReplaceChild(bone,CubeListBuilder.create(),PartPose.ZERO);
        return new HumanoidModel<>(LayerDefinition.create(mesh,16,16).bakeRoot());
    }
}
