package com.example.myvillage.forging;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.cultivation.crafting.CraftingRules;
import com.example.myvillage.item.ModDataComponents;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Plans one finished item's infusion without changing the input or paying its costs. */
public final class EquipmentInfusion {
    private EquipmentInfusion() { }

    public record Preparation(int reason, ItemStack result, Map<String,Integer> costs) {
        public Preparation { costs=Map.copyOf(costs); }
        public boolean valid() { return reason==0; }
    }

    public static int qualityCap(ServerPlayer player) {
        var aptitude=player.getData(CultivationAttachments.CRAFTING.get());
        return aptitude.initialized()&&CultivationService.getProfile(player).awakened()
                ?aptitude.qualityCap(CraftingAptitudes.Profession.FORGING,CraftingRules.get()):0;
    }

    public static Preparation prepare(ItemStack input, String trait, int cap) {
        var craft=WorkshopEquipment.craft(input);
        if(input.getCount()!=1||craft.isEmpty()||input.has(ModDataComponents.FORGE_MARK.get()))return rejected(2);
        if(cap<1||cap>4)return rejected(7);
        var recipe=ForgingRules.get().recipe(craft.get().recipe()).orElseThrow();
        if(trait==null||!recipe.traits().contains(trait))return rejected(11);

        var result=input.copyWithCount(1);
        ForgedEquipment.apply(result,new ForgeMark(trait,cap));
        if(result.has(ModDataComponents.TEMPER.get()))WorkshopEquipment.refresh(result);
        return new Preparation(0,result,WorkshopRules.get().infusionCosts(recipe,trait));
    }

    private static Preparation rejected(int reason) {
        return new Preparation(reason,ItemStack.EMPTY,Map.of());
    }
}
