package com.example.myvillage.client.townmap;

import com.example.myvillage.worldplan.townmap.TownMapLocation;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.LoggerFactory;

public final class ClientTownMap {
    public record View(TownMapLocation location, TownMapData data) {
        public BlockPos worldEntry(TownMapData.Place place) {
            return location.origin().offset(place.entry());
        }
    }

    public record Target(TownMapLocation location, TownMapData.Place place, Vec3 position) { }

    private static TownMapLocation location = TownMapLocation.EMPTY;
    private static TownMapLocation loaded = TownMapLocation.EMPTY;
    private static View view;
    private static Target target;

    private ClientTownMap() { }

    public static void install() {
        TownMapLocation.installReceiver(next -> location = next);
    }

    public static boolean nearby() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(Level.OVERWORLD) && location.available();
    }

    public static View view() {
        if (!nearby()) return null;
        if (!location.equals(loaded)) {
            loaded = location;
            view = null;
            try {
                view = new View(location, TownMapData.load(location));
            } catch (RuntimeException e) {
                LoggerFactory.getLogger(ClientTownMap.class).warn("无法加载城镇地图 {}", location.city(), e);
            }
        }
        return view;
    }

    public static boolean follow(View shown, TownMapData.Place place) {
        if (shown == null || shown != view() || !shown.data().places().contains(place)) return false;
        target = new Target(shown.location(), place, Vec3.atBottomCenterOf(shown.worldEntry(place)));
        return true;
    }

    public static Target target() { return target; }

    public static Target targetInWorld() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(Level.OVERWORLD) ? target : null;
    }

    public static void stop() { target = null; }

    public static void logout(ClientPlayerNetworkEvent.LoggingOut ignored) {
        location = loaded = TownMapLocation.EMPTY;
        view = null;
        target = null;
    }
}
