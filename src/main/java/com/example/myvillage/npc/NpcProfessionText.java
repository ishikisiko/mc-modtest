package com.example.myvillage.npc;

import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.profession.ProfessionSupplyView;
import com.example.myvillage.sim.profession.ProfessionView;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;

/** Chinese, read-only presentation shared by administrator inspection and real conversations. */
public final class NpcProfessionText {
    private static final String KEY = "message.myvillage.life.profession.";
    private NpcProfessionText() { }

    private static String text(String suffix, Object... args) {
        return NpcDialogueText.of(Component.translatable(KEY + suffix, args));
    }

    public static String role(String role) {
        return NpcDialogueText.of(Component.translatable("message.myvillage.life.role."
                + (role.isEmpty() ? "none" : role)));
    }

    public static String item(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "myvillage" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return NpcDialogueText.of(Component.translatable("item." + namespace + "." + path));
    }

    public static String inventory(Map<String, Integer> amounts) {
        List<String> entries = amounts.entrySet().stream().filter(e -> e.getValue() > 0)
                .sorted(Map.Entry.comparingByKey()).map(e -> item(e.getKey()) + "×" + e.getValue()).toList();
        return entries.isEmpty() ? text("empty") : String.join("、", entries);
    }

    public static List<String> resident(ProfessionView view) {
        var lines = new ArrayList<>(List.of(text("occupation", role(view.role())),
                text("skills", view.gatheringLevel(), view.gatheringExperience(),
                        view.plantingLevel(), view.plantingExperience(), view.alchemyLevel(), view.alchemyExperience()),
                text("carried", inventory(view.herbInventory()), view.legacyHerbs())));
        if (List.of("herb_child", "spirit_grower", "alchemist", "herbalist").contains(view.role()))
            lines.add(text("scope." + view.role()));
        return List.copyOf(lines);
    }

    public static List<String> supply(WorldSim sim, String siteId) {
        var supply = sim.professionSupply(siteId);
        if (supply.isEmpty()) return List.of();
        var lines = new ArrayList<>(supply(supply.get(), sim.lifeClockTicks()));
        var commerce = sim.commerce(siteId);
        var products = sim.professionRules().recipes().stream().map(r -> r.output()).collect(java.util.stream.Collectors.toSet());
        lines.add(commerce.map(c -> text("output", inventory(c.items().entrySet().stream()
                .filter(e -> products.contains(e.getKey()))
                .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)))))
                .orElseGet(() -> text("commerce_required")));
        return List.copyOf(lines);
    }

    public static List<String> supply(ProfessionSupplyView view, long now) {
        var lines = new ArrayList<String>();
        lines.add(text("stock", inventory(view.herbs()), view.legacyStock()));
        if (view.gardens().isEmpty()) lines.add(text("no_garden"));
        for (var garden : view.gardens()) {
            if (garden.physical()) {
                String state = text(switch (garden.observedAge()) {
                    case -1 -> "physical_empty";
                    case 0 -> "physical_seedling";
                    case 1 -> "physical_growing";
                    case 2 -> "physical_mature";
                    default -> "physical_unknown";
                });
                lines.add(text("physical_garden", garden.placeId(), item(garden.herb()),
                        garden.cropX(), garden.cropY(), garden.cropZ(), state));
                continue;
            }
            String state = garden.plantedAt() < 0 ? text("garden_empty")
                    : garden.matureAt() <= now ? text("garden_mature")
                    : text("garden_growing", garden.matureAt() - now);
            lines.add(text("garden", garden.placeId(), item(garden.herb()), state,
                    text(garden.tended() ? "garden_tended" : "garden_untended")));
        }
        return List.copyOf(lines);
    }
}
