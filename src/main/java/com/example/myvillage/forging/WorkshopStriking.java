package com.example.myvillage.forging;

import com.example.myvillage.item.ModDataComponents;
import net.minecraft.world.item.ItemStack;
import java.util.Map;

/** Existing forming/training rules shared by the physical contact and its readback. */
public final class WorkshopStriking {
    private WorkshopStriking() {}
    public static boolean direction(ItemStack input, int direction) {
        var craft = WorkshopEquipment.craft(input);
        if (craft.isEmpty() || !craft.get().training(direction)) return false;
        var recipe = ForgingRules.get().recipe(craft.get().recipe()).orElseThrow();
        return direction != 3 || recipe.traits().contains("channeling") && input.has(ModDataComponents.FORGE_MARK.get());
    }
    public static int reason(ItemStack input, ItemStack output, int heat, int direction) {
        if (!output.isEmpty()) return 1;
        if (input.getCount() != 1) return 9;
        var rules = WorkshopRules.get();
        if (WorkshopItems.valid(input)) {
            var piece = input.get(ModDataComponents.WORKPIECE.get());
            if (piece.stage() > 1 || piece.blows() >= rules.forming_strikes()) return 2;
        } else {
            var craft = WorkshopEquipment.craft(input);
            if (craft.isEmpty()) return 9;
            if (!direction(input, direction)) return 11;
            var record = WorkshopEquipment.record(input);
            if (record.strikes() >= craft.get().max_strikes()) return 6;
            if (rules.available(record, craft.get()) == 0) return 5;
        }
        return heat > 0 ? 0 : 3;
    }
    public static Map<String, Integer> costs(ItemStack input) {
        return WorkshopItems.valid(input) ? Map.of() : WorkshopRules.get().training_cost();
    }
    public static ItemStack result(ItemStack input, int direction) {
        if (WorkshopItems.valid(input)) {
            var piece = input.get(ModDataComponents.WORKPIECE.get()).strike(WorkshopRules.get());
            var result = WorkshopItems.blank(piece);
            result.applyComponents(input.getComponentsPatch());
            result.set(ModDataComponents.WORKPIECE.get(), piece);
            return result;
        }
        var craft = WorkshopEquipment.craft(input).orElseThrow();
        if (!direction(input, direction)) throw new IllegalArgumentException("Invalid training direction");
        var result = input.copy();
        result.set(ModDataComponents.TEMPER.get(), WorkshopEquipment.record(input).strike(direction, craft, WorkshopRules.get()));
        WorkshopEquipment.refresh(result);
        return result;
    }
}
