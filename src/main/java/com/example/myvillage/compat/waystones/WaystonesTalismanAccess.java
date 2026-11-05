package com.example.myvillage.compat.waystones;

import com.example.myvillage.talisman.TalismanTravel;
import com.example.myvillage.talisman.TravelPoint;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.blay09.mods.waystones.api.Waystone;
import net.blay09.mods.waystones.api.WaystoneTypes;
import net.blay09.mods.waystones.api.WaystonesAPI;
import net.minecraft.server.level.ServerPlayer;

/** 仅由可选模组 bootstrap 加载；不复制玩家石碑解锁账簿。 */
public final class WaystonesTalismanAccess implements TalismanTravel.Waystones {
    private static boolean eligible(ServerPlayer p, Waystone stone) {
        return stone.isValid() && stone.getWaystoneType().equals(WaystoneTypes.WAYSTONE)
                && WaystonesAPI.isWaystoneActivated(p, stone);
    }
    @Override public List<TalismanTravel.WaystoneEntry> activated(ServerPlayer p) {
        return WaystonesAPI.getActivatedWaystones(p).stream().filter(s -> eligible(p, s))
                .map(s -> new TalismanTravel.WaystoneEntry(s.getWaystoneUid(), name(s), s.getDimension().location(), s.getPos()))
                .sorted(Comparator.comparing(TalismanTravel.WaystoneEntry::name).thenComparing(TalismanTravel.WaystoneEntry::id)).toList();
    }
    private static String name(Waystone s) {
        String name = s.getEffectiveName().getString();
        return name.length() > 64 ? name.substring(0, 64) : name;
    }
    @Override public Optional<TalismanTravel.Transfer> prepare(ServerPlayer p, UUID id) {
        var stone = WaystonesAPI.getWaystone(p.getServer(), id).filter(s -> eligible(p, s));
        if (stone.isEmpty()) return Optional.empty();
        var target = stone.get();
        var level = p.getServer().getLevel(target.getDimension());
        if (level == null || !target.isValidInLevel(level)) return Optional.empty();
        var destination = target.resolveDestination(level);
        if (destination.isEmpty()) return Optional.empty();
        var at = destination.get();
        var pos = at.location();
        var point = new TravelPoint(level.dimension().location(), pos.x, pos.y, pos.z, at.direction().toYRot(), 0);
        if (!TalismanTravel.safe(p, level, point)) return Optional.empty();
        // The consumed talisman and qi are the price of this custom route; native stone-to-stone pricing is unchanged.
        var context = WaystonesAPI.createCustomTeleportContext(p, target).left();
        if (context.isEmpty()) return Optional.empty();
        return Optional.of(new TalismanTravel.Transfer(point, () -> WaystonesAPI.tryTeleport(context.get()).left()
                .filter(entities -> entities.contains(p)).isPresent()));
    }
}
