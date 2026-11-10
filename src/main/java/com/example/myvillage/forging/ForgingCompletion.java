package com.example.myvillage.forging;

import com.example.myvillage.MyVillageMod;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.crafting.CraftingQualityRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Production samples only at a server commit; displays and ingredient previews never draw an outcome. */
@EventBusSubscriber(modid=MyVillageMod.MOD_ID)
public final class ForgingCompletion {
    private ForgingCompletion(){}
    public static int aptitude(ServerPlayer player){
        var value=player.getData(CultivationAttachments.CRAFTING.get());
        return value.initialized()?value.forging():0;
    }
    public static int roll(ServerPlayer player,String recipe){
        var definition=ForgingRules.get().recipe(recipe).orElseThrow();
        return CraftingQualityRules.get().roll(aptitude(player),definition.qualityTier(),0,
                com.example.myvillage.cultivation.crafting.CraftingTalents.modifier(player),player.getRandom()::nextGaussian).quality();
    }
    public static void stamp(ItemStack result,int quality){
        if(quality<0||quality>4)throw new IllegalArgumentException("Invalid forging outcome");
        if(WorkshopEquipment.craft(result).isEmpty()||result.has(ModDataComponents.FORGE_MARK.get()))return;
        if(quality==0){result.setCount(0);return;}
        result.set(ModDataComponents.FORGE_MARK.get(),ForgeMark.uninfused(quality));
    }
    public static void failed(ServerPlayer player){
        player.displayClientMessage(Component.literal("炼器失败，材料已消耗。"),false);
    }
    @SubscribeEvent
    public static void crafted(PlayerEvent.ItemCraftedEvent event){
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var result=event.getCrafting();var craft=WorkshopEquipment.craft(result);
        if(craft.isPresent()&&!result.has(ModDataComponents.FORGE_MARK.get())){
            int quality=roll(player,craft.orElseThrow().recipe());stamp(result,quality);
            if(quality==0){failed(player);return;}
        }
        if(!result.isEmpty())com.example.myvillage.aptitude.AptitudeRuntime.equipmentCompleted(player,result,false);
    }
}
