package com.example.myvillage.client.equipment;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.equipment.EquipmentArmorItem;
import com.example.myvillage.equipment.EquipmentContent;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

@Mod(value = MyVillageMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientEquipmentBootstrap {
    public ClientEquipmentBootstrap(IEventBus bus) {
        bus.addListener(ClientEquipmentBootstrap::reload);
        bus.addListener(ClientEquipmentBootstrap::extensions);
    }

    private static void reload(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(EquipmentModels.INSTANCE);
    }

    private static void extensions(RegisterClientExtensionsEvent event) {
        event.registerItem(new IClientItemExtensions() {
            @Override public HumanoidModel<?> getHumanoidArmorModel(LivingEntity entity, ItemStack stack,
                    EquipmentSlot slot, HumanoidModel<?> original) {
                var item = (EquipmentArmorItem) stack.getItem();
                boolean slim = entity instanceof AbstractClientPlayer player && player.getSkin().model() == PlayerSkin.Model.SLIM;
                // NeoForge copies the original pose and slot visibility after this return.
                return EquipmentModels.INSTANCE.get(item.definition().id(), slim);
            }
        }, EquipmentContent.items());
    }
}
