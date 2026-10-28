package com.example.myvillage.client.immortal;

import com.example.myvillage.equipment.immortal.ImmortalArmorItem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Uses the renderer's live humanoid bone contract. Vanilla player/armor-stand shapes are the
 * fitting baseline; other HumanoidModel renderers retain their own pose and entity scale, but
 * nonstandard body proportions and custom age transforms require their renderer's adapter. */
final class ImmortalArmorLayer<T extends LivingEntity,M extends HumanoidModel<T>> extends RenderLayer<T,M> {
    ImmortalArmorLayer(RenderLayerParent<T,M> parent) {super(parent);}
    static List<ImmortalMeshRenderer.Wearing> wearing(LivingEntity entity) {
        boolean slim=entity instanceof AbstractClientPlayer player&&player.getSkin().model()==PlayerSkin.Model.SLIM;
        List<ImmortalMeshRenderer.Wearing> result=new ArrayList<>(4);
        for(EquipmentSlot slot:List.of(EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.HEAD)) {
            var stack=entity.getItemBySlot(slot);
            if(!(stack.getItem() instanceof ImmortalArmorItem item)||item.getEquipmentSlot()!=slot)continue;
            var mesh=ImmortalMeshResources.INSTANCE.get(item.definition().model());
            if(mesh!=null)result.add(new ImmortalMeshRenderer.Wearing(mesh,mesh.data().profiles().get(slim?"slim":"standard"),slot.getName(),stack.hasFoil()));
        }
        return result;
    }
    static boolean effects(LivingEntity entity) {
        if(!ImmortalClientConfig.enabled())return false;
        var camera=Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d=ImmortalClientConfig.distance();
        return entity.position().distanceToSqr(camera)<=d*d;
    }
    @Override public void render(PoseStack poses,MultiBufferSource buffers,int light,T entity,float limbSwing,
            float limbAmount,float partialTick,float age,float yaw,float pitch) {
        var pieces=wearing(entity);
        if(pieces.isEmpty() || entity instanceof net.minecraft.world.entity.player.Player player && player.isSpectator())return;
        ImmortalMeshRenderer.draw(pieces,getParentModel(),poses,buffers,light,(entity.tickCount+partialTick)/20.0,
                effects(entity),ImmortalClientConfig.elements(),null);
    }
}
