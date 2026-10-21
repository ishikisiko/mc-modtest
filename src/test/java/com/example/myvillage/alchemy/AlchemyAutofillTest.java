package com.example.myvillage.alchemy;

import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlchemyAutofillTest {
    private static final List<String> WANTED = List.of("minecraft:carrot", "minecraft:brown_mushroom", "minecraft:coal");
    private static final List<Integer> COUNTS = List.of(1, 1, 2);
    @BeforeAll static void bootstrap() {
        if (LoadingModList.get() == null) LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
    }
    private static List<ItemStack> emptyInputs() { return List.of(ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY); }
    @Test void fillsOnlyOneBatchWithoutMutatingTheSourceAndRepeatDoesNotDrainMore() {
        var bag = List.of(new ItemStack(Items.CARROT, 8), new ItemStack(Items.BROWN_MUSHROOM, 8), new ItemStack(Items.COAL, 8));
        var first = AlchemyAutofill.plan(bag, emptyInputs(), WANTED, COUNTS).orElseThrow();
        assertEquals(List.of(1, 1, 2), first.inputs().stream().map(ItemStack::getCount).toList());
        assertEquals(List.of(7, 7, 6), first.inventory().stream().map(ItemStack::getCount).toList());
        assertTrue(bag.stream().allMatch(stack -> stack.getCount() == 8));
        var next = AlchemyAutofill.plan(first.inventory(), first.inputs(), WANTED, COUNTS).orElseThrow();
        for (int i = 0; i < bag.size(); i++) assertTrue(ItemStack.matches(first.inventory().get(i), next.inventory().get(i)));
    }
    @Test void incompatibleInputsReturnToInventoryWithoutLossAndMissingIngredientsStayMissing() {
        var bag = List.of(new ItemStack(Items.CARROT, 4), ItemStack.EMPTY, new ItemStack(Items.COAL, 1));
        var old = List.of(new ItemStack(Items.POTATO, 3), ItemStack.EMPTY, ItemStack.EMPTY);
        var result = AlchemyAutofill.plan(bag, old, WANTED, COUNTS).orElseThrow();
        assertTrue(result.inventory().get(1).is(Items.POTATO)); assertEquals(3, result.inventory().get(1).getCount());
        assertTrue(result.inputs().get(1).isEmpty()); assertEquals(1, result.inputs().get(2).getCount());
        assertEquals(3, old.get(0).getCount()); assertEquals(4, bag.get(0).getCount());
    }
    @Test void fullInventoryRejectsTheWholePlanWithoutAnyChanges() {
        var bag = List.of(new ItemStack(Items.CARROT, 64), new ItemStack(Items.BROWN_MUSHROOM, 64), new ItemStack(Items.COAL, 64));
        var old = List.of(new ItemStack(Items.POTATO, 3), ItemStack.EMPTY, ItemStack.EMPTY);
        assertTrue(AlchemyAutofill.plan(bag, old, WANTED, COUNTS).isEmpty());
        assertTrue(bag.stream().allMatch(stack -> stack.getCount() == 64)); assertEquals(3, old.get(0).getCount());
    }
    @Test void switchingRecipesConservesEveryStackAcrossTheTwoInventories() {
        var bag = List.of(new ItemStack(Items.CARROT, 5), new ItemStack(Items.BROWN_MUSHROOM, 5),
                new ItemStack(Items.COAL, 5), ItemStack.EMPTY, ItemStack.EMPTY);
        var old = List.of(new ItemStack(Items.POTATO, 32), new ItemStack(Items.BEETROOT, 32), new ItemStack(Items.COAL, 4));
        var result = AlchemyAutofill.plan(bag, old, WANTED, COUNTS).orElseThrow();
        Map<String, Integer> before = count(bag, old), after = count(result.inventory(), result.inputs());
        assertEquals(before, after); assertEquals(4, result.inputs().get(2).getCount());
    }
    @Test void thirdIngredientConservesComponentsAndReturnsWhenSwitchingToTwoHerbs() {
        var bag=List.of(new ItemStack(Items.CARROT,8),new ItemStack(Items.BROWN_MUSHROOM,8),new ItemStack(Items.COAL,8),new ItemStack(Items.APPLE,8),ItemStack.EMPTY);
        var empty=List.of(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY);
        var three=AlchemyAutofill.plan(bag,empty,List.of("minecraft:carrot","minecraft:brown_mushroom","minecraft:coal","minecraft:apple"),List.of(4,4,6,4)).orElseThrow();
        assertEquals(List.of(4,4,6,4),three.inputs().stream().map(ItemStack::getCount).toList());
        assertEquals(count(bag,empty),count(three.inventory(),three.inputs()));
        var two=AlchemyAutofill.plan(three.inventory(),three.inputs(),List.of("minecraft:carrot","minecraft:brown_mushroom","minecraft:coal",""),List.of(1,1,2,0)).orElseThrow();
        assertTrue(two.inputs().get(3).isEmpty());assertEquals(count(bag,empty),count(two.inventory(),two.inputs()));
    }
    private static Map<String, Integer> count(List<ItemStack> bag, List<ItemStack> slots) {
        Map<String, Integer> counts = new TreeMap<>();
        java.util.stream.Stream.concat(bag.stream(), slots.stream()).filter(s -> !s.isEmpty())
                .forEach(s -> counts.merge(s.getItem().toString(), s.getCount(), Integer::sum));
        return counts;
    }
}
