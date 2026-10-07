package com.example.myvillage.portrait;

import com.google.gson.JsonObject;
import java.util.Locale;

/** Shared loading for the portrait tests: the exported maps once, and specs from the JSON fixtures. */
final class PortraitTestData {
    private static PortraitMaps maps;

    private PortraitTestData() {
    }

    /** The maps through the class loader, the way the client reads them through its resource manager. */
    static synchronized PortraitMaps maps() {
        if (maps == null) {
            maps = PortraitMaps.load(path -> PortraitMaps.class.getResourceAsStream("/assets/myvillage/portrait/" + path));
        }
        return maps;
    }

    /** A fixture spec: the Python generator's field names and lower-case variant keys. */
    static PortraitSpec spec(JsonObject o) {
        return new PortraitSpec(
                o.get("female").getAsBoolean(),
                e(PortraitSpec.Face.class, o, "face"),
                e(PortraitSpec.Skin.class, o, "skin"),
                e(PortraitSpec.HairColour.class, o, "hairColour"),
                e(PortraitSpec.Front.class, o, "front"),
                e(PortraitSpec.Back.class, o, "back"),
                e(PortraitSpec.EyeShape.class, o, "eyeShape"),
                e(PortraitSpec.EyeColour.class, o, "eyeColour"),
                e(PortraitSpec.Brow.class, o, "brow"),
                e(PortraitSpec.Mouth.class, o, "mouth"),
                o.get("nose").getAsBoolean(),
                o.get("blush").getAsBoolean(),
                e(PortraitSpec.Robe.class, o, "robe"),
                o.get("accent").getAsInt(),
                e(PortraitSpec.Headwear.class, o, "headwear"),
                o.get("bandage").getAsBoolean(),
                o.get("scar").getAsBoolean(),
                o.get("old").getAsBoolean(),
                o.get("dead").getAsBoolean());
    }

    private static <E extends Enum<E>> E e(Class<E> type, JsonObject o, String field) {
        return Enum.valueOf(type, o.get(field).getAsString().toUpperCase(Locale.ROOT));
    }
}
