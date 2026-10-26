package com.example.myvillage.client.cultivation.panel;

import com.example.myvillage.cultivation.crafting.CraftingAptitudes;
import java.util.List;
import java.util.Objects;

/** Presentation of server-supplied ceilings; deliberately contains no aptitude-to-quality rules. */
public final class CraftingReadouts {
    private CraftingReadouts() { }

    public record Readout(String professionKey, boolean initialized, int score, int qualityCap) {
        public String qualityKey() { return CraftingReadouts.qualityKey(qualityCap); }
    }

    public static List<Readout> of(CraftingAptitudes aptitudes, int alchemyQualityCap, int forgingQualityCap) {
        Objects.requireNonNull(aptitudes, "aptitudes");
        return List.of(
                readout("alchemy", aptitudes.initialized(), aptitudes.alchemy(), alchemyQualityCap),
                readout("forging", aptitudes.initialized(), aptitudes.forging(), forgingQualityCap));
    }

    public static List<Readout> of(CraftingAptitudes a, int alchemy, int forging, int talisman) {
        var rows = new java.util.ArrayList<>(of(a, alchemy, forging));
        rows.add(readout("talisman", a.talismanInitialized(), a.score(CraftingAptitudes.Profession.TALISMAN), talisman));
        return List.copyOf(rows);
    }

    private static Readout readout(String profession, boolean initialized, int score, int cap) {
        if (initialized && (cap < 1 || cap > 4)) throw new IllegalArgumentException("Invalid server quality ceiling");
        return new Readout("screen.myvillage.crafting." + profession, initialized, initialized ? score : 0, initialized ? cap : 0);
    }

    public static String qualityKey(int cap) {
        return "crafting.myvillage.quality." + switch (cap) {
            case 0 -> "pending";
            case 1 -> "lower";
            case 2 -> "middle";
            case 3 -> "upper";
            case 4 -> "perfect";
            default -> throw new IllegalArgumentException("Invalid server quality ceiling: " + cap);
        };
    }
}
