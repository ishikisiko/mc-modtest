package com.example.myvillage.aptitude;

import com.example.myvillage.cultivation.CultivationAttachments;
import com.example.myvillage.cultivation.CultivationService;
import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** 共用读取口；玩家替换经 CultivationService，命簿人物替换经 WorldSim。 */
public final class AptitudeRuntime {
    private AptitudeRuntime() { }
    public static String name(String id) {
        return switch (id) {
            case "metal" -> "金灵根"; case "wood" -> "木灵根"; case "water" -> "水灵根";
            case "fire" -> "火灵根"; case "earth" -> "土灵根"; case "sword" -> "剑术";
            case "spear" -> "枪术"; case "fist" -> "拳掌"; case "alchemy" -> "炼丹";
            case "forging" -> "炼器"; case "talisman" -> "制符"; default -> id;
        };
    }
    public static int score(LivingEntity entity, String id) {
        if (!AptitudeRules.get().valid(id)) return 0;
        if (entity instanceof ServerPlayer player) {
            if (AptitudeRules.get().elements().contains(id)) return CultivationService.getProfile(player).spiritualRoot()
                    .map(root -> root.points().getOrDefault(ResourceLocation.fromNamespaceAndPath("myvillage", id), 0)).orElse(0);
            if (AptitudeRules.get().crafts().contains(id)) return CultivationService.craftingAptitudes(player).score(profession(id));
            return CultivationService.aptitudes(player).scores().get(id);
        }
        if (entity instanceof NpcEntity npc) {
            if (npc.isLedgerAvatar()) return WorldSimRuntime.sim().map(sim -> sim.aptitudes(npc.ledgerPersonId()).get(id)).orElse(0);
            var state = npc.getData(CultivationAttachments.APTITUDES.get());
            if (!state.initialized() && !npc.level().isClientSide()) {
                state = new AptitudeState(true, AptitudeValues.generate(npc.level().getServer().overworld().getSeed(),
                        npc.getUUID(), 0, true), Map.of(), Map.of(), java.util.Optional.empty());
                npc.setData(CultivationAttachments.APTITUDES.get(), state);
            }
            return state.scores().get(id);
        }
        return 0;
    }
    public static CraftingAptitudes.Profession profession(String id) {
        return switch (id) {
            case "alchemy" -> CraftingAptitudes.Profession.ALCHEMY;
            case "forging" -> CraftingAptitudes.Profession.FORGING;
            case "talisman" -> CraftingAptitudes.Profession.TALISMAN;
            default -> throw new IllegalArgumentException("Unknown crafting aptitude " + id);
        };
    }
    public static boolean set(ServerPlayer player, String id, int value) {
        if (value < 0 || !AptitudeRules.get().valid(id) || !CultivationService.getProfile(player).awakened()) return false;
        CultivationService.initializeAptitudes(player);
        if (AptitudeRules.get().elements().contains(id)) {
            var profile = CultivationService.getProfile(player);
            return CultivationService.replaceProfile(player, profile.withSpiritualRoot(profile.spiritualRoot().orElseThrow()
                    .with(ResourceLocation.fromNamespaceAndPath("myvillage", id), value))).success();
        }
        if (AptitudeRules.get().crafts().contains(id)) return CultivationService.setCraftingAptitude(player, profession(id), value).success();
        var current = CultivationService.aptitudes(player);
        CultivationService.replaceAptitudes(player, current.withScores(current.scores().with(id, value)), true);
        return true;
    }
    public static boolean add(ServerPlayer player, String id, int amount, String source) {
        int old = score(player, id), next = AptitudeRules.add(old, amount);
        if (next == old || !set(player, id, next)) return false;
        player.displayClientMessage(Component.literal(name(id) + "资质 +" + (next - old) + "（" + source + "），当前 " + next), false);
        return true;
    }
    public static double weaponMultiplier(LivingEntity entity, String family) {
        return AptitudeRules.get().weapons().contains(family) ? AptitudeRules.get().multiplier(score(entity, family)) : 1;
    }
    public static long clock(ServerPlayer player) { return player.serverLevel().getServer().overworld().getGameTime(); }
    public static void practice(ServerPlayer player, String id, int amount) {
        if (!player.isAlive() || player.isSpectator() || player.isCreative() || !CultivationService.getProfile(player).awakened()
                || !AptitudeRules.get().valid(id) || amount <= 0) return;
        CultivationService.initializeAptitudes(player);
        var state = CultivationService.aptitudes(player); long now = clock(player);
        if (state.receipts().getOrDefault("practice/" + id, 0L) > now) return;
        long progress = state.progress().getOrDefault(id, 0L) + amount;
        long required = AptitudeRules.get().practiceRequired(score(player, id));
        state = state.counter(false, id, progress >= required ? progress - required : progress)
                .counter(true, "practice/" + id, now + AptitudeRules.get().growth("practice_cooldown_ticks"));
        CultivationService.replaceAptitudes(player, state, false);
        if (progress >= required) add(player, id, 1, "实践领悟");
    }
    public static boolean nearStation(ServerPlayer player, net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return player != null && player.level() == level && player.isAlive()
                && player.position().distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(pos))
                    <= Math.pow(AptitudeRules.get().growth("teaching_range"), 2);
    }
    public static void equipmentCompleted(ServerPlayer player, net.minecraft.world.item.ItemStack result, boolean repair) {
        var craft = com.example.myvillage.forging.WorkshopEquipment.craft(result);
        if (craft.isEmpty()) return;
        var mark = result.get(com.example.myvillage.item.ModDataComponents.FORGE_MARK.get());
        crafted(player, "forging", repair ? "repair" : craft.orElseThrow().recipe(), mark == null ? 1 : mark.quality(), result);
    }
    public static void crafted(ServerPlayer player, String profession, String recipe, int quality, net.minecraft.world.item.ItemStack result) {
        if (player == null) return;
        AptitudeCultivation.crafted(player, profession, recipe, quality, result);
        practice(player, profession, AptitudeRules.get().growth("practice_craft"));
    }
}
