package com.example.myvillage.exploration;

import com.example.myvillage.item.ModDataComponents;
import com.example.myvillage.item.TechniqueManualItem;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Append to explicitly listed finite exploration tables; no entity drops or repeatable spawner payouts. */
public final class ExplorationLoot extends LootModifier {
    public static final MapCodec<ExplorationLoot> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i).apply(i, ExplorationLoot::new));
    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, "myvillage");
    static { SERIALIZERS.register("exploration", () -> CODEC); }
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
    public ExplorationLoot(LootItemCondition[] conditions) { super(conditions); }
    @Override public MapCodec<? extends IGlobalLootModifier> codec() { return CODEC; }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var rules = ExplorationRules.get(); String pool = rules.lootTables().get(context.getQueriedLootTableId().toString());
        if (pool == null) return loot;
        var origin = context.getParamOrNull(LootContextParams.ORIGIN);
        if (origin != null) {
            var location = ExplorationRuntime.at(context.getLevel(), BlockPos.containing(origin), null);
            if (location.isPresent()) pool = rules.rewardPool(pool, location.get().recommended().max());
        }
        var rewards = rules.pools().get(pool);
        int totalWeight = rewards.entries().stream().mapToInt(ExplorationRules.Reward::weight).sum();
        for (int i = 0; i < rewards.rolls(); i++) {
            int draw = context.getRandom().nextInt(totalWeight);
            for (var r : rewards.entries()) {
                draw -= r.weight(); if (draw >= 0) continue;
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(r.item())),
                        r.min() + context.getRandom().nextInt(r.max() - r.min() + 1));
                if (!r.technique().isEmpty()) {
                    var manual = TechniqueManualItem.manualFor(context.getLevel().registryAccess(), ResourceLocation.parse(r.technique()));
                    // Startup validates this pair. Do not hand out an unreadable blank manual after a registry change.
                    if (manual.isEmpty() || !manual.is(stack.getItem())) break;
                    stack.set(ModDataComponents.TECHNIQUE.get(), ResourceLocation.parse(r.technique()));
                }
                loot.add(stack); break;
            }
        }
        return loot;
    }
}
