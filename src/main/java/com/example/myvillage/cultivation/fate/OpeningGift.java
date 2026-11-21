package com.example.myvillage.cultivation.fate;

import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.forging.Workpiece;
import com.example.myvillage.forging.WorkshopEquipment;
import com.example.myvillage.forging.WorkshopRules;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.spiritstone.SpiritStoneSources;
import com.example.myvillage.spiritstone.SpiritStoneStorage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class OpeningGift {
    private OpeningGift() { }

    static void validate(FateState selection, int weapon) {
        if (selection.selected().contains(FateReworkRules.GIFT_ID) ? !FateReworkRules.get().weaponChoice(weapon) : weapon != -1)
            throw new IllegalArgumentException("weapon");
    }

    public static void claim(ServerPlayer player) {
        var state = FateExtras.of(player);
        var rules = FateReworkRules.get();
        if (state.received() || !FateService.has(player,FateReworkRules.GIFT_ID) || !rules.weaponChoice(state.weapon())) return;
        List<ItemStack> bundle = new ArrayList<>();
        for (ResourceLocation id : rules.armor()) bundle.add(gear(id, rules.quality()));
        bundle.add(gear(rules.weapons().get(state.weapon()),rules.quality()));
        bundle.add(new ItemStack(ModItems.PILL_YANGYUAN.get(),rules.pills()));
        bundle.add(new ItemStack(ModItems.LOW_GRADE_SPIRIT_STONE.get(),rules.stones()));
        if (!deliver(player,bundle)) {
            player.displayClientMessage(Component.translatable("fate.yuanxian.gift_pending"),false);
            return;
        }
        CultivationService.replaceFateExtras(player,state.redeemed());
        player.displayClientMessage(Component.translatable("fate.yuanxian.gift_received"),false);
    }

    static boolean deliver(ServerPlayer player, List<ItemStack> bundle) {
        var access = SpiritStoneSources.open(player);
        var copies = access.copies();
        for (ItemStack stack : bundle) {
            if (SpiritStoneStorage.insertLoose(copies,stack,stack.getCount(),player.getInventory().items.size()) != 0) return false;
        }
        return access.commit(copies);
    }

    private static ItemStack gear(ResourceLocation item,int quality) {
        var work = WorkshopRules.get().forOutput(item.toString()).orElseThrow();
        return WorkshopEquipment.complete(new Workpiece(work.recipe(),4,work.forming_points(),0),quality);
    }
}
