package com.example.myvillage.ruins;

import com.example.myvillage.block.*;
import com.example.myvillage.item.TechniqueManualItem;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Only loaded, unchanged destination cells may receive a reward; no arbitrary block replacement. */
public final class RuinCoreBlockEntity extends BlockEntity {
    private static final Logger LOG = LoggerFactory.getLogger(RuinCoreBlockEntity.class);
    private RuinChoice choice = new RuinChoice(null);
    public RuinCoreBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.RUIN_CORE.get(),pos,state); }
    private BlockPos target(RuinRules.Pos canonical) {
        int turns = switch (getBlockState().getValue(RuinCoreBlock.FACING)) {
            case EAST -> 1; case SOUTH -> 2; case WEST -> 3; default -> 0;
        };
        var d = canonical.subtract(RuinRules.get().core()).rotate(turns);
        return worldPosition.offset(d.x(),d.y(),d.z());
    }
    private void message(ServerPlayer player, String key, Object... arguments) {
        player.displayClientMessage(Component.translatable("message.myvillage.ruin."+key,arguments), false);
    }
    public void use(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || player.level() != level || player.isSpectator()
                || !player.mayBuild() || !player.getMainHandItem().isEmpty()) return;
        RuinRules rules = RuinRules.get();
        if (player.distanceToSqr(worldPosition.getCenter()) > rules.interactionDistance()*rules.interactionDistance()) return;
        if (!choice.charged()) { message(player,"spent"); return; }
        var request = player.isShiftKeyDown() ? RuinChoice.Choice.VAULT : RuinChoice.Choice.GARDEN;
        if (!choice.confirm(player.getUUID(), request, server.getGameTime(), rules.confirmationTicks())) {
            message(player,request == RuinChoice.Choice.GARDEN ? "confirm_garden" : "confirm_vault",rules.garden().size()); return;
        }
        BlockPos vault = target(rules.vault());
        BarrelBlockEntity chest = null;
        ItemStack manual = ItemStack.EMPTY;
        if (request == RuinChoice.Choice.GARDEN) {
            for (var plant : rules.garden()) {
                BlockPos p = target(plant.pos());
                if (!server.hasChunkAt(p) || !server.hasChunkAt(p.below())) { message(player,"unloaded"); return; }
                if (!server.getBlockState(p.below()).is(Blocks.COARSE_DIRT)
                        || !(server.getBlockState(p).isAir() || server.getBlockState(p).is(Blocks.DEAD_BUSH))) {
                    message(player,"garden_changed"); return;
                }
                if (!(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(plant.crop())) instanceof SpiritCropBlock)) {
                    message(player,"invalid"); return;
                }
            }
        } else {
            if (!server.hasChunkAt(vault)) { message(player,"unloaded"); return; }
            if (!(server.getBlockEntity(vault) instanceof BarrelBlockEntity barrel) || !barrel.isEmpty()
                    || barrel.getLootTable() != null) { message(player,"vault_changed"); return; }
            chest = barrel;
            manual = TechniqueManualItem.manualFor(server.registryAccess(), ResourceLocation.parse(rules.manual()));
            if (manual.isEmpty()) { message(player,"invalid"); return; }
            for (var reward : rules.vaultItems()) {
                if (!BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(reward.id()))) { message(player,"invalid"); return; }
            }
        }
        // The server thread serializes competing players. Commit before any external reward writes.
        if (!choice.commit(request)) return;
        setChanged();
        server.setBlock(worldPosition,getBlockState().setValue(RuinCoreBlock.LIT,false),Block.UPDATE_ALL);
        if (request == RuinChoice.Choice.GARDEN) {
            for (var plant : rules.garden()) {
                BlockPos p = target(plant.pos());
                server.setBlock(p.below(),ModBlocks.SPIRIT_SOIL.get().defaultBlockState(),Block.UPDATE_ALL);
                var crop = (SpiritCropBlock) BuiltInRegistries.BLOCK.get(ResourceLocation.parse(plant.crop()));
                server.setBlock(p,crop.getStateForAge(crop.getMaxAge()),Block.UPDATE_ALL);
            }
        } else {
            chest.setItem(0,manual);
            int slot = 1;
            for (var reward : rules.vaultItems())
                chest.setItem(slot++,new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(reward.id())),reward.count()));
            chest.setChanged();
        }
        message(player,request == RuinChoice.Choice.GARDEN ? "garden" : "vault");
        LOG.info("RUIN_CHOICE pos={} dimension={} player={} choice={}",worldPosition,server.dimension().location(),player.getUUID(),request);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag,registries); tag.putString("Choice",choice.choice().name());
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag,registries); choice = new RuinChoice(tag.getString("Choice"));
    }
}
