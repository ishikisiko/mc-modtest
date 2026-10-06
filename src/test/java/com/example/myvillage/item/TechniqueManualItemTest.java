package com.example.myvillage.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.example.myvillage.item.TechniqueManualItem.Mismatch;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Rarity;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.registries.DeferredItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The manual's pure rules: ids, rarity, validity, names, comprehension and the creative-tab variants. */
final class TechniqueManualItemTest {
    private static final ResourceLocation SWORD_ART = id("gengjin_jianjue");
    private static final Map<ResourceLocation, TechniqueDefinition> TECHNIQUES = Map.of(
            SWORD_ART, technique("gengjin_jianjue", TechniqueCategory.ACTIVE, 2),
            id("basic_breathing"), technique("basic_breathing", TechniqueCategory.CORE, 0));
    private static final Function<ResourceLocation, Optional<TechniqueDefinition>> LOOKUP =
            techniqueId -> Optional.ofNullable(TECHNIQUES.get(techniqueId));

    @BeforeAll
    static void bootstrap() {
        // Item.Properties needs FeatureFlags, whose NeoForge loader reads the (here absent) mod list.
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void sixteenItemIdsOnePerCategoryAndGrade() {
        Set<String> ids = new HashSet<>();
        for (TechniqueCategory category : TechniqueCategory.values()) {
            for (int grade = 1; grade <= 4; grade++) {
                ids.add(TechniqueManualItem.itemId(category, grade));
            }
        }
        assertEquals(16, ids.size());
        assertEquals("manual_core_huang", TechniqueManualItem.itemId(TechniqueCategory.CORE, 1));
        assertEquals("manual_active_xuan", TechniqueManualItem.itemId(TechniqueCategory.ACTIVE, 2));
        assertEquals("manual_movement_di", TechniqueManualItem.itemId(TechniqueCategory.MOVEMENT, 3));
        assertEquals("manual_body_tian", TechniqueManualItem.itemId(TechniqueCategory.BODY, 4));
        assertThrows(IllegalArgumentException.class, () -> TechniqueManualItem.itemId(TechniqueCategory.CORE, 0));
        assertThrows(IllegalArgumentException.class, () -> TechniqueManualItem.itemId(TechniqueCategory.CORE, 5));
    }

    @Test
    void registeredManualsMatchTheirIds() {
        assertEquals(16, ModItems.MANUALS.size());
        for (TechniqueCategory category : TechniqueCategory.values()) {
            for (int grade = 1; grade <= 4; grade++) {
                DeferredItem<TechniqueManualItem> manual = ModItems.manual(category, grade);
                assertEquals(ResourceLocation.fromNamespaceAndPath("myvillage", TechniqueManualItem.itemId(category, grade)),
                        manual.getId());
            }
        }
        assertThrows(IllegalArgumentException.class, () -> ModItems.manual(TechniqueCategory.BODY, 0));
    }

    @Test
    void rarityFollowsTheGrade() {
        assertEquals(Rarity.COMMON, TechniqueManualItem.rarity(1));
        assertEquals(Rarity.UNCOMMON, TechniqueManualItem.rarity(2));
        assertEquals(Rarity.RARE, TechniqueManualItem.rarity(3));
        assertEquals(Rarity.EPIC, TechniqueManualItem.rarity(4));
    }

    @Test
    void validityNeedsARegisteredTechniqueOfTheItemsCategoryAndGrade() {
        assertEquals(Optional.empty(), TechniqueManualItem.check(TechniqueCategory.ACTIVE, 2, SWORD_ART, LOOKUP));
        assertEquals(Optional.of(Mismatch.BLANK), TechniqueManualItem.check(TechniqueCategory.ACTIVE, 2, null, LOOKUP));
        assertEquals(Optional.of(Mismatch.UNKNOWN_TECHNIQUE),
                TechniqueManualItem.check(TechniqueCategory.ACTIVE, 2, id("lost_art"), LOOKUP));
        assertEquals(Optional.of(Mismatch.WRONG_CATEGORY),
                TechniqueManualItem.check(TechniqueCategory.CORE, 2, SWORD_ART, LOOKUP));
        assertEquals(Optional.of(Mismatch.WRONG_GRADE),
                TechniqueManualItem.check(TechniqueCategory.ACTIVE, 3, SWORD_ART, LOOKUP));
        assertEquals(Optional.of(Mismatch.WRONG_GRADE),
                TechniqueManualItem.check(TechniqueCategory.CORE, 1, id("basic_breathing"), LOOKUP));

        assertFalse(Mismatch.BLANK.damaged());
        assertFalse(Mismatch.NOT_A_MANUAL.damaged());
        assertTrue(Mismatch.UNKNOWN_TECHNIQUE.damaged());
        assertTrue(Mismatch.WRONG_CATEGORY.damaged());
        assertTrue(Mismatch.WRONG_GRADE.damaged());
    }

    @Test
    void namesForValidBlankDamagedAndUnresolvedManuals() {
        Component named = TechniqueManualItem.name(TechniqueCategory.ACTIVE, 2, SWORD_ART, LOOKUP);
        assertEquals("item.myvillage.manual.named", key(named));
        assertEquals("cultivation.technique.myvillage.gengjin_jianjue", key((Component) args(named)[0]));
        assertNull(named.getStyle().getColor());

        Component blank = TechniqueManualItem.name(TechniqueCategory.ACTIVE, 2, null, LOOKUP);
        assertEquals("item.myvillage.manual.blank", key(blank));
        assertEquals("screen.myvillage.cultivation.category.active", key((Component) args(blank)[0]));
        assertEquals("screen.myvillage.cultivation.grade_name.2", key((Component) args(blank)[1]));

        for (Component damaged : List.of(
                TechniqueManualItem.name(TechniqueCategory.ACTIVE, 2, id("lost_art"), LOOKUP),
                TechniqueManualItem.name(TechniqueCategory.BODY, 2, SWORD_ART, LOOKUP),
                TechniqueManualItem.name(TechniqueCategory.ACTIVE, 4, SWORD_ART, LOOKUP))) {
            assertEquals("item.myvillage.manual.damaged", key(damaged));
            assertEquals(TextColor.fromLegacyFormat(ChatFormatting.RED), damaged.getStyle().getColor());
        }

        Component unresolved = TechniqueManualItem.name(TechniqueCategory.ACTIVE, 2, SWORD_ART, null);
        assertEquals("item.myvillage.manual.named", key(unresolved));
        assertEquals("cultivation.technique.myvillage.gengjin_jianjue", key((Component) args(unresolved)[0]));
    }

    @Test
    void comprehensionIsAWholePercentOfTheStudyPoints() {
        assertEquals(0, TechniqueManualItem.comprehensionPercent(0, 4000));
        assertEquals(0, TechniqueManualItem.comprehensionPercent(39, 4000));
        assertEquals(37, TechniqueManualItem.comprehensionPercent(1480, 4000));
        assertEquals(99, TechniqueManualItem.comprehensionPercent(95999, 96000));
        assertEquals(100, TechniqueManualItem.comprehensionPercent(96000, 96000));
        assertEquals(100, TechniqueManualItem.comprehensionPercent(Integer.MAX_VALUE, 4000));
        assertEquals(0, TechniqueManualItem.comprehensionPercent(10, 0));
    }

    @Test
    void creativeTabListsEveryShippedTechniqueAboveGradeZeroInOrder() throws Exception {
        Map<ResourceLocation, TechniqueDefinition> shipped = new TreeMap<>();
        Path directory = Path.of("src/main/resources/data/myvillage/myvillage/technique");
        try (var files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                shipped.put(id(name.substring(0, name.length() - ".json".length())),
                        TechniqueDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                                .getOrThrow());
            }
        }
        List<ResourceLocation> variants = TechniqueManualItem.creativeVariants(shipped);

        long manualTechniques = shipped.values().stream().filter(technique -> technique.grade() >= 1).count();
        assertEquals(manualTechniques, variants.size());
        assertEquals(shipped.size() - 1, variants.size());
        assertFalse(variants.contains(id("basic_breathing")));
        assertEquals(new HashSet<>(variants).size(), variants.size());

        List<String> sortKeys = new ArrayList<>();
        for (ResourceLocation variant : variants) {
            TechniqueDefinition technique = shipped.get(variant);
            sortKeys.add(technique.category().ordinal() + "/" + technique.grade() + "/" + variant);
        }
        List<String> sorted = new ArrayList<>(sortKeys);
        sorted.sort(null);
        assertEquals(sorted, sortKeys);
        assertEquals(TechniqueCategory.CORE, shipped.get(variants.get(0)).category());
        assertEquals(TechniqueCategory.BODY, shipped.get(variants.get(variants.size() - 1)).category());
    }

    private static String key(Component component) {
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static Object[] args(Component component) {
        return ((TranslatableContents) component.getContents()).getArgs();
    }

    private static TechniqueDefinition technique(String path, TechniqueCategory category, int grade) {
        return new TechniqueDefinition("cultivation.technique.myvillage." + path, category, grade, List.of(),
                TechniqueRequirements.none());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("myvillage", path);
    }
}
