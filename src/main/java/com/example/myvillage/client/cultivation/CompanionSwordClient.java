package com.example.myvillage.client.cultivation;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.CompanionSwordItem;
import com.example.myvillage.item.ModItems;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

@EventBusSubscriber(modid = MyVillageMod.MOD_ID, value = Dist.CLIENT)
public final class CompanionSwordClient {
    private CompanionSwordClient() { }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ModItems.PRIMORDIAL_COMPANION_SWORD.get(),
                ResourceLocation.parse("yuanxian:companion_form"), (stack, level, entity, seed) -> {
                    var mark = CompanionSwordItem.mark(stack);
                    return mark == null ? 0 : mark.form() / 4.0F;
                }));
    }
}
