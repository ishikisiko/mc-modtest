package com.example.myvillage.entity.npc;

import com.example.myvillage.resources.BuiltinResources;
import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.stats.StatsRules;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** All encounter and reward tuning, apart from the shared NPC breakthrough rules. */
public record EvilCultivatorRules(Attributes attributes,
        @SerializedName("manual_preference") double manualPreference,
        @SerializedName("fallback_realm") String fallbackRealm,
        Tier ordinary, Tier prodigy) {

    public record Attributes(double health, double attack, double armor, double speed,
            @SerializedName("follow_range") double followRange,
            @SerializedName("stroll_speed") double strollSpeed, int experience) {
        public Attributes {
            if (!(health > 0 && attack > 0 && armor >= 0 && speed > 0 && followRange > 0
                    && strollSpeed > 0 && experience >= 0)) throw new IllegalArgumentException("Invalid evil cultivator attributes");
        }
    }

    public record Tier(@SerializedName("spirit_stones") List<Integer> spiritStones,
            @SerializedName("manual_chance") double manualChance,
            @SerializedName("grade_weights") List<Integer> gradeWeights,
            @SerializedName("learned_max_grade") int learnedMaxGrade,
            @SerializedName("starting_mastery") long startingMastery) {
        public Tier {
            spiritStones = List.copyOf(spiritStones);
            gradeWeights = List.copyOf(gradeWeights);
            if (spiritStones.size() != 2 || spiritStones.getFirst() < 1
                    || spiritStones.getLast() < spiritStones.getFirst() || spiritStones.getLast() > 64
                    || !probability(manualChance) || gradeWeights.size() != 4
                    || gradeWeights.stream().anyMatch(w -> w < 0 || w > 1000)
                    || gradeWeights.stream().allMatch(w -> w == 0)
                    || learnedMaxGrade < 1 || learnedMaxGrade > 4 || startingMastery < 0) {
                throw new IllegalArgumentException("Invalid evil cultivator tier");
            }
        }

        int gradeWeight(int grade) {
            return grade >= 1 && grade <= gradeWeights.size() ? gradeWeights.get(grade - 1) : 0;
        }
    }

    public EvilCultivatorRules {
        if (attributes == null || ordinary == null || prodigy == null || !probability(manualPreference)
                || ordinary.gradeWeight(4) != 0 || prodigy.gradeWeight(1) != 0) {
            throw new IllegalArgumentException("Invalid evil cultivator rules");
        }
        StatsRules.bundled().realmKey(fallbackRealm, 0);
    }

    private static boolean probability(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }

    RealmKey fallback() { return StatsRules.bundled().realmKey(fallbackRealm, 0); }

    Tier tier(boolean gifted) { return gifted ? prodigy : ordinary; }

    public static EvilCultivatorRules get() { return Holder.RULES; }

    private static final class Holder {
        private static final EvilCultivatorRules RULES = load();

        private static EvilCultivatorRules load() {
            var path = "data/yuanxian/wilderness/evil_cultivator.json";
            try (var in = BuiltinResources.open(path)) {
                if (in == null) throw new IllegalStateException("Missing " + path);
                return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), EvilCultivatorRules.class);
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read " + path, e);
            }
        }
    }
}
