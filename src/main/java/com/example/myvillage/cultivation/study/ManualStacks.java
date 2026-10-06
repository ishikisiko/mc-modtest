package com.example.myvillage.cultivation.study;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.TechniqueManualItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Reads technique manuals (秘籍) from item stacks: which technique they teach and how far they are read. */
public final class ManualStacks {
    private ManualStacks() {
    }

    /**
     * The technique a stack teaches when it is a readable manual (brief §1): a {@link TechniqueManualItem}
     * whose {@code myvillage:technique} names a registered technique of the item's category and grade.
     */
    public static Optional<ResourceLocation> technique(
            ItemStack stack, Function<ResourceLocation, TechniqueDefinition> techniques) {
        Objects.requireNonNull(techniques, "techniques");
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof TechniqueManualItem manual)) {
            return Optional.empty();
        }
        ResourceLocation id = stack.get(ModDataComponents.TECHNIQUE.get());
        return technique(manual.category(), manual.grade(), id, techniques);
    }

    /** The same rule for an item's category and grade and a component value, without a stack. */
    public static Optional<ResourceLocation> technique(
            TechniqueCategory category,
            int grade,
            ResourceLocation id,
            Function<ResourceLocation, TechniqueDefinition> techniques) {
        Objects.requireNonNull(techniques, "techniques");
        if (id == null) {
            return Optional.empty();
        }
        boolean readable = TechniqueManualItem.check(
                category, grade, id, techniqueId -> Optional.ofNullable(techniques.apply(techniqueId))).isEmpty();
        return readable ? Optional.of(id) : Optional.empty();
    }

    /** The comprehension points on a stack; absent reads as 0. */
    public static int comprehension(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0;
        }
        return Math.max(0, stack.getOrDefault(ModDataComponents.COMPREHENSION.get(), 0));
    }

    /** The manual in {@code slot} of a player's inventory, read live on every call. */
    public static ManualSlot slot(
            Inventory inventory, int slot, Function<ResourceLocation, TechniqueDefinition> techniques) {
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(techniques, "techniques");
        return new ManualSlot() {
            private ItemStack stack() {
                return slot >= 0 && slot < inventory.getContainerSize() ? inventory.getItem(slot) : ItemStack.EMPTY;
            }

            @Override
            public Optional<ResourceLocation> technique() {
                return ManualStacks.technique(stack(), techniques);
            }

            @Override
            public int comprehension() {
                return ManualStacks.comprehension(stack());
            }

            @Override
            public void writeComprehension(int points) {
                if (points < 0) {
                    throw new IllegalArgumentException("Comprehension must be non-negative, got " + points);
                }
                ItemStack stack = stack();
                if (!stack.isEmpty()) {
                    stack.set(ModDataComponents.COMPREHENSION.get(), points);
                    inventory.setChanged();
                }
            }

            @Override
            public void consumeOne() {
                ItemStack stack = stack();
                if (!stack.isEmpty()) {
                    stack.shrink(1);
                    inventory.setChanged();
                }
            }
        };
    }
}
