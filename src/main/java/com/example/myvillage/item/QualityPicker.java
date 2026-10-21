package com.example.myvillage.item;

import com.example.myvillage.alchemy.PillQuality;
import com.example.myvillage.forging.ForgeMark;
import com.example.myvillage.forging.ForgedEquipment;
import com.example.myvillage.forging.ForgingRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** The picker creates normal production stacks, including actual forged equipment effects. */
public final class QualityPicker {
    private QualityPicker() {}

    public static boolean held(Player player) {
        return player.getMainHandItem().is(ModItems.QUALITY_PICKER.get())
                || player.getOffhandItem().is(ModItems.QUALITY_PICKER.get());
    }

    public static List<Item> catalogue() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals("myvillage"))
                .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString())).toList();
    }

    public static List<String> traits(Item item) {
        return ForgingRules.get().forOutput(BuiltInRegistries.ITEM.getKey(item).toString())
                .map(ForgingRules.Recipe::traits).orElse(List.of());
    }

    public static int defaultQuality(Item item) {
        for(int q=0;q<=4;q++)if(allows(item,q))return q;
        return 0;
    }

    public static boolean allows(Item item, int quality) {
        if(item instanceof FoundationPillItem pill)return pill.allows(quality);
        if (item instanceof AlchemyPillItem) return quality >= 1 && quality <= 4;
        return quality == 0 || (!traits(item).isEmpty() && quality >= 1 && quality <= 4);
    }

    public static ItemStack create(Item item, int quality, int trait, boolean fullStack) {
        if (!allows(item, quality) || trait < 0 || trait >= ForgeMark.TRAITS.size()) return ItemStack.EMPTY;
        ItemStack result = new ItemStack(item);
        if (item instanceof AlchemyPillItem || item instanceof FoundationPillItem) {
            if (trait != 0) return ItemStack.EMPTY;
            PillQuality.apply(result, quality);
        } else if (quality > 0) {
            String selected = ForgeMark.TRAITS.get(trait);
            if (!traits(item).contains(selected)) return ItemStack.EMPTY;
            ForgedEquipment.apply(result, new ForgeMark(selected, quality));
        } else if (trait != 0) return ItemStack.EMPTY;
        if (fullStack) result.setCount(result.getMaxStackSize());
        return result;
    }

    public static void give(ServerPlayer player, ResourceLocation id, int quality, int trait, boolean fullStack) {
        if (!player.isAlive() || player.isRemoved() || !held(player) || !id.getNamespace().equals("myvillage")) return;
        var item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty()) return;
        ItemStack result = create(item.get(), quality, trait, fullStack);
        if (result.isEmpty()) return;
        // Plan on copies so a full creative inventory cannot silently delete the requested stack.
        var inventory = player.getInventory();
        var copies = new ArrayList<ItemStack>();
        for (var stack : inventory.items) copies.add(stack.copy());
        ItemStack remaining = result.copy();
        for (var stack : copies) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, remaining)) {
                int amount = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(amount); remaining.shrink(amount);
                if (remaining.isEmpty()) break;
            }
        }
        for (int i = 0; i < copies.size() && !remaining.isEmpty(); i++) {
            if (copies.get(i).isEmpty()) { copies.set(i, remaining.copy()); remaining = ItemStack.EMPTY; }
        }
        if (!remaining.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.myvillage.quality_picker.full"), false);
            return;
        }
        for (int i = 0; i < copies.size(); i++) inventory.items.set(i, copies.get(i));
        inventory.setChanged(); player.inventoryMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("message.myvillage.quality_picker.given", result.getHoverName(), result.getCount()), false);
    }
}
