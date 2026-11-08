package com.example.myvillage.cultivation.fate;

import com.example.myvillage.alchemy.AlchemyRules;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrowingPlantBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/** 玩家有效破坏植物后，在原有掉落中追加一次药材抽取。 */
public final class HerbalLoreRuntime {
    public static final TagKey<Block> PLANTS = TagKey.create(Registries.BLOCK,
            ResourceLocation.parse("myvillage:herbal_lore_plants"));
    private HerbalLoreRuntime() { }
    public static void register() { NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, HerbalLoreRuntime::drops); }
    private static final class Materials {
        private static final List<String> ALL = AlchemyRules.get().recipes().stream()
                .flatMap(recipe -> recipe.ingredients().stream()).distinct().sorted().toList();
    }
    public static List<String> materials() { return Materials.ALL; }
    public static boolean plant(BlockState state) {
        return state.getBlock() instanceof BushBlock || state.getBlock() instanceof GrowingPlantBlock
                || state.getBlock() instanceof FlowerPotBlock pot && pot.getPotted() != net.minecraft.world.level.block.Blocks.AIR
                || state.is(PLANTS);
    }
    static Optional<String> roll(BlockState state, double chance, RandomSource random) {
        if (chance <= 0 || !plant(state) || random.nextDouble() >= chance) return Optional.empty();
        var materials = materials();
        return materials.isEmpty() ? Optional.empty() : Optional.of(materials.get(random.nextInt(materials.size())));
    }
    private static void drops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof ServerPlayer player) || !player.isAlive()
                || player.isCreative() || player.isSpectator()) return;
        roll(event.getState(), FateService.effect(player, "herbal_find_chance"), event.getLevel().random).ifPresent(id -> {
            var item = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
            var pos = event.getPos();
            event.getDrops().add(new ItemEntity(event.getLevel(), pos.getX()+.5, pos.getY()+.5, pos.getZ()+.5, item));
        });
    }
}
