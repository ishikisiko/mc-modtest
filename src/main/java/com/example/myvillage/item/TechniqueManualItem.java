package com.example.myvillage.item;

import com.example.myvillage.cultivation.data.HeritageDefinition;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.RealmDefinition;
import com.example.myvillage.cultivation.data.RealmStageDefinition;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.SpiritualElementDefinition;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.data.TechniqueRequirements;
import com.example.myvillage.cultivation.study.ManualStudy;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * A technique manual (秘籍): one item per category and grade, the technique itself is the
 * {@link ModDataComponents#TECHNIQUE} component, like an enchanted book carries its enchantment
 * (docs/technique-manual-brief.md section 1). A manual is valid when the component names a
 * registered technique of the item's own category and grade; a manual without the component is
 * blank, any other mismatch makes it damaged (残损秘籍), which cannot be read.
 *
 * <p>Using it hands over to {@link ManualStudy#use} on the server; the client predicts CONSUME for a
 * valid manual (no hand swing, which would interrupt the study) and PASS for a blank or damaged one.
 */
public class TechniqueManualItem extends Item {
    public static final int MIN_GRADE = 1;
    public static final int MAX_GRADE = 4;

    private static final List<String> GRADE_IDS = List.of("huang", "xuan", "di", "tian");
    private static final List<Rarity> GRADE_RARITIES = List.of(Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC);

    private static final String GRADE_NAME_KEY = "screen.myvillage.cultivation.grade_name.";
    private static final String CATEGORY_KEY = "screen.myvillage.cultivation.category.";
    private static final String TOOLTIP_KEY = "tooltip.myvillage.manual.";

    /** Why a stack is not a readable manual. */
    public enum Mismatch {
        /** The stack is not a technique manual at all. */
        NOT_A_MANUAL,
        /** No technique component: a blank manual. */
        BLANK,
        /** The component names a technique that is not registered. */
        UNKNOWN_TECHNIQUE,
        /** The technique's category is not the item's. */
        WRONG_CATEGORY,
        /** The technique's grade is not the item's. */
        WRONG_GRADE;

        /** Every mismatch but a blank book (and a non-manual) reads as a damaged manual. */
        public boolean damaged() {
            return this != NOT_A_MANUAL && this != BLANK;
        }
    }

    private final TechniqueCategory category;
    private final int grade;

    public TechniqueManualItem(TechniqueCategory category, int grade, Properties properties) {
        super(properties.stacksTo(1).rarity(rarity(checkGrade(grade))));
        this.category = Objects.requireNonNull(category, "category");
        this.grade = grade;
    }

    public TechniqueCategory category() {
        return category;
    }

    public int grade() {
        return grade;
    }

    // ---- pure rules ----------------------------------------------------------------------------

    /** {@code manual_<category>_<huang|xuan|di|tian>}. */
    public static String itemId(TechniqueCategory category, int grade) {
        return "manual_" + category.serializedName() + "_" + gradeId(grade);
    }

    /** huang, xuan, di, tian for grades 1..4. */
    public static String gradeId(int grade) {
        return GRADE_IDS.get(checkGrade(grade) - 1);
    }

    /** 黄 COMMON, 玄 UNCOMMON, 地 RARE, 天 EPIC. */
    public static Rarity rarity(int grade) {
        return GRADE_RARITIES.get(checkGrade(grade) - 1);
    }

    /**
     * Whether a manual of {@code category} and {@code grade} holding {@code techniqueId} can be read:
     * empty when it can, otherwise the first reason it cannot.
     */
    public static Optional<Mismatch> check(
            TechniqueCategory category,
            int grade,
            @Nullable ResourceLocation techniqueId,
            Function<ResourceLocation, Optional<TechniqueDefinition>> techniques) {
        if (techniqueId == null) {
            return Optional.of(Mismatch.BLANK);
        }
        Optional<TechniqueDefinition> definition = techniques.apply(techniqueId);
        if (definition.isEmpty()) {
            return Optional.of(Mismatch.UNKNOWN_TECHNIQUE);
        }
        if (definition.get().category() != category) {
            return Optional.of(Mismatch.WRONG_CATEGORY);
        }
        if (definition.get().grade() != grade) {
            return Optional.of(Mismatch.WRONG_GRADE);
        }
        return Optional.empty();
    }

    /** {@link #check(TechniqueCategory, int, ResourceLocation, Function)} for a stack, against {@code registries}. */
    public static Optional<Mismatch> check(ItemStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(registries, "registries");
        return check(stack, Catalog.of(registries));
    }

    private static Optional<Mismatch> check(ItemStack stack, Catalog catalog) {
        if (stack == null || !(stack.getItem() instanceof TechniqueManualItem manual)) {
            return Optional.of(Mismatch.NOT_A_MANUAL);
        }
        return check(manual.category, manual.grade, stack.get(ModDataComponents.TECHNIQUE.get()), catalog::technique);
    }

    /** The technique a stack names, when it is a valid manual. */
    public static Optional<ResourceLocation> technique(ItemStack stack, HolderLookup.Provider registries) {
        return check(stack, registries).isPresent()
                ? Optional.empty()
                : Optional.ofNullable(stack.get(ModDataComponents.TECHNIQUE.get()));
    }

    /**
     * The display name: {@code 《name》} for a valid manual, {@code 空白秘籍 · category · grade} for a
     * blank one, red {@code 残损秘籍} otherwise. {@code techniques} may be null when no registry can be
     * reached; a named manual then shows its technique's conventional name unchecked.
     */
    public static Component name(
            TechniqueCategory category,
            int grade,
            @Nullable ResourceLocation techniqueId,
            @Nullable Function<ResourceLocation, Optional<TechniqueDefinition>> techniques) {
        if (techniqueId == null) {
            return Component.translatable("item.myvillage.manual.blank", categoryName(category), gradeName(grade));
        }
        if (techniques == null) {
            return Component.translatable("item.myvillage.manual.named",
                    Component.translatable(conventionalTranslationKey(techniqueId)));
        }
        Optional<Mismatch> mismatch = check(category, grade, techniqueId, techniques);
        if (mismatch.isPresent()) {
            return Component.translatable("item.myvillage.manual.damaged").withStyle(ChatFormatting.RED);
        }
        return Component.translatable("item.myvillage.manual.named",
                Component.translatable(techniques.apply(techniqueId).orElseThrow().translationKey()));
    }

    /** Whole percent of {@code points} already comprehended, 0..100, rounded down. */
    public static int comprehensionPercent(int comprehension, int points) {
        if (comprehension <= 0 || points <= 0) {
            return 0;
        }
        return (int) Math.min(100L, (long) comprehension * 100L / points);
    }

    /**
     * The techniques that get a creative-tab manual: every one of grade ≥ 1, by category
     * (心法, 绝技, 身法, 炼体), then grade, then id.
     */
    public static List<ResourceLocation> creativeVariants(Map<ResourceLocation, TechniqueDefinition> techniques) {
        return techniques.entrySet().stream()
                .filter(entry -> entry.getValue().grade() >= MIN_GRADE && entry.getValue().grade() <= MAX_GRADE)
                .sorted(Comparator
                        .<Map.Entry<ResourceLocation, TechniqueDefinition>>comparingInt(
                                entry -> entry.getValue().category().ordinal())
                        .thenComparingInt(entry -> entry.getValue().grade())
                        .thenComparing(entry -> entry.getKey().toString()))
                .map(Map.Entry::getKey)
                .toList();
    }

    /** One manual stack per {@link #creativeVariants} entry, read from {@code registries}. */
    public static List<ItemStack> creativeStacks(HolderLookup.Provider registries) {
        Catalog catalog = Catalog.of(registries);
        return creativeVariants(catalog.techniques()).stream()
                .map(id -> manualFor(catalog, id))
                .filter(stack -> !stack.isEmpty())
                .toList();
    }

    /**
     * A manual teaching {@code techniqueId}: the item of its category and grade with the technique
     * component. Empty when the technique is not registered or has grade 0 (凡阶 has no manual).
     */
    public static ItemStack manualFor(HolderLookup.Provider registries, ResourceLocation techniqueId) {
        return manualFor(Catalog.of(Objects.requireNonNull(registries, "registries")), techniqueId);
    }

    private static ItemStack manualFor(Catalog catalog, ResourceLocation techniqueId) {
        Optional<TechniqueDefinition> definition = catalog.technique(techniqueId);
        if (definition.isEmpty() || definition.get().grade() < MIN_GRADE || definition.get().grade() > MAX_GRADE) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(ModItems.manual(definition.get().category(), definition.get().grade()).get());
        stack.set(ModDataComponents.TECHNIQUE.get(), techniqueId);
        return stack;
    }

    // ---- item behaviour ------------------------------------------------------------------------

    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation techniqueId = stack.get(ModDataComponents.TECHNIQUE.get());
        Catalog catalog = techniqueId == null ? null : Catalog.of(null);
        return name(category, grade, techniqueId, catalog != null && catalog.available() ? catalog::technique : null);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return grade == MAX_GRADE || super.isFoil(stack);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer) {
            return ManualStudy.use(serverPlayer, hand);
        }
        return clientUse(stack, check(stack, level.registryAccess()).isEmpty());
    }

    /**
     * The client's prediction: CONSUME for a valid manual, PASS otherwise. Never SUCCESS, which swings
     * the hand; the swing reaches the server and stops the study session that just started.
     */
    static InteractionResultHolder<ItemStack> clientUse(ItemStack stack, boolean valid) {
        return valid ? InteractionResultHolder.consume(stack) : InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        Catalog catalog = Catalog.of(context == null ? null : context.registries());
        tooltip.add(Component.translatable(TOOLTIP_KEY + "kind", categoryName(category), gradeName(grade))
                .withStyle(ChatFormatting.GOLD));
        ResourceLocation techniqueId = stack.get(ModDataComponents.TECHNIQUE.get());
        if (techniqueId == null) {
            tooltip.add(Component.translatable(TOOLTIP_KEY + "blank").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (!catalog.available()) {
            tooltip.add(Component.translatable(TOOLTIP_KEY + "technique_id", techniqueId.toString())
                    .withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        Optional<Mismatch> mismatch = check(category, grade, techniqueId, catalog::technique);
        if (mismatch.isPresent()) {
            tooltip.add(damageReason(mismatch.get(), techniqueId, catalog).withStyle(ChatFormatting.RED));
            tooltip.add(Component.translatable(TOOLTIP_KEY + "damaged.unreadable").withStyle(ChatFormatting.DARK_RED));
            return;
        }
        TechniqueDefinition definition = catalog.technique(techniqueId).orElseThrow();
        definition.school().ifPresent(schoolId -> tooltip.add(Component.translatable(TOOLTIP_KEY + "school",
                catalog.school(schoolId)
                        .<Component>map(school -> Component.translatable(school.translationKey()))
                        .orElseGet(() -> Component.literal(schoolId.toString())))
                .withStyle(ChatFormatting.GRAY)));
        tooltip.add(Component.translatable(TOOLTIP_KEY + "elements", elementList(definition.elements(), catalog))
                .withStyle(ChatFormatting.GRAY));
        for (Map.Entry<ResourceLocation, HeritageDefinition> heritage : catalog.heritagesContaining(techniqueId)) {
            HeritageDefinition chain = heritage.getValue();
            tooltip.add(Component.translatable(TOOLTIP_KEY + "heritage", Component.translatable(
                    "screen.myvillage.cultivation.heritage_position",
                    Component.translatable(chain.translationKey()),
                    (chain.indexOf(techniqueId) + 1) + "/" + chain.techniques().size()))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable(TOOLTIP_KEY + "requirement", requirementText(definition.requirements(), catalog))
                .withStyle(ChatFormatting.GRAY));
        Integer comprehension = stack.get(ModDataComponents.COMPREHENSION.get());
        if (comprehension != null && comprehension > 0) {
            tooltip.add(Component.translatable(TOOLTIP_KEY + "comprehension",
                    comprehensionPercent(comprehension, definition.study().points()))
                    .withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(TOOLTIP_KEY + "use").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- text helpers --------------------------------------------------------------------------

    static Component categoryName(TechniqueCategory category) {
        return Component.translatable(CATEGORY_KEY + category.serializedName());
    }

    static Component gradeName(int grade) {
        return Component.translatable(GRADE_NAME_KEY + grade);
    }

    /** The key the technique catalogue gives every technique: {@code cultivation.technique.<ns>.<path>}. */
    static String conventionalTranslationKey(ResourceLocation techniqueId) {
        return "cultivation.technique." + techniqueId.getNamespace() + "." + techniqueId.getPath();
    }

    private static MutableComponent damageReason(Mismatch mismatch, ResourceLocation techniqueId, Catalog catalog) {
        Optional<TechniqueDefinition> definition = catalog.technique(techniqueId);
        return switch (mismatch) {
            case WRONG_CATEGORY -> Component.translatable(TOOLTIP_KEY + "damaged.wrong_category",
                    Component.translatable(definition.orElseThrow().translationKey()),
                    categoryName(definition.orElseThrow().category()));
            case WRONG_GRADE -> Component.translatable(TOOLTIP_KEY + "damaged.wrong_grade",
                    Component.translatable(definition.orElseThrow().translationKey()),
                    gradeName(definition.orElseThrow().grade()));
            default -> Component.translatable(TOOLTIP_KEY + "damaged.unknown_technique", techniqueId.toString());
        };
    }

    private static Component elementList(List<ResourceLocation> elements, Catalog catalog) {
        if (elements.isEmpty()) {
            return Component.translatable("screen.myvillage.cultivation.technique_no_element");
        }
        MutableComponent list = Component.empty();
        for (int index = 0; index < elements.size(); index++) {
            if (index > 0) {
                list.append(" · ");
            }
            list.append(elementName(elements.get(index), catalog));
        }
        return list;
    }

    private static MutableComponent elementName(ResourceLocation elementId, Catalog catalog) {
        Optional<SpiritualElementDefinition> element = catalog.element(elementId);
        if (element.isEmpty()) {
            return Component.literal(elementId.toString());
        }
        MutableComponent name = Component.translatable(element.get().translationKey());
        element.get().displayColor().ifPresent(color -> name.withStyle(style -> style.withColor(color)));
        return name;
    }

    /** As the 功法 page states it: realm · stage, then each element affinity as {@code 金 ≥ 15.0%}. */
    private static Component requirementText(TechniqueRequirements requirements, Catalog catalog) {
        List<Component> parts = new ArrayList<>();
        requirements.minimumRealm().ifPresent(realmId -> {
            Optional<RealmDefinition> realm = catalog.realm(realmId);
            MutableComponent text = realm.<MutableComponent>map(definition -> Component.translatable(definition.translationKey()))
                    .orElseGet(() -> Component.literal(realmId.toString()));
            requirements.minimumStage().ifPresent(stageId -> text.append(" · ").append(realm
                    .flatMap(definition -> definition.stages().stream()
                            .filter(stage -> stage.id().equals(stageId))
                            .findFirst())
                    .map(RealmStageDefinition::translationKey)
                    .<Component>map(Component::translatable)
                    .orElseGet(() -> Component.literal(stageId.toString()))));
            parts.add(text);
        });
        requirements.minimumElementAffinities().forEach((elementId, basisPoints) -> parts.add(
                elementName(elementId, catalog)
                        .append(String.format(Locale.ROOT, " ≥ %.1f%%", basisPoints / 100.0D))));
        if (parts.isEmpty()) {
            return Component.translatable("screen.myvillage.cultivation.none");
        }
        MutableComponent text = Component.empty();
        for (int index = 0; index < parts.size(); index++) {
            if (index > 0) {
                text.append("  ");
            }
            text.append(parts.get(index));
        }
        return text;
    }

    private static int checkGrade(int grade) {
        if (grade < MIN_GRADE || grade > MAX_GRADE) {
            throw new IllegalArgumentException("Manual grade must be in " + MIN_GRADE + ".." + MAX_GRADE + ", got " + grade);
        }
        return grade;
    }

    /**
     * The cultivation registries as seen from a {@link HolderLookup.Provider}, or, without one, from
     * whatever NeoForge can resolve on this side (the running server, else the client level).
     */
    private record Catalog(@Nullable HolderLookup.Provider provider) {
        static Catalog of(@Nullable HolderLookup.Provider provider) {
            return new Catalog(provider);
        }

        boolean available() {
            return lookup(ModCultivationRegistries.TECHNIQUES).isPresent();
        }

        Optional<TechniqueDefinition> technique(ResourceLocation id) {
            return get(ModCultivationRegistries.TECHNIQUES, id);
        }

        Optional<SchoolDefinition> school(ResourceLocation id) {
            return get(ModCultivationRegistries.SCHOOLS, id);
        }

        Optional<RealmDefinition> realm(ResourceLocation id) {
            return get(ModCultivationRegistries.REALMS, id);
        }

        Optional<SpiritualElementDefinition> element(ResourceLocation id) {
            return get(ModCultivationRegistries.SPIRITUAL_ELEMENTS, id);
        }

        Map<ResourceLocation, TechniqueDefinition> techniques() {
            return lookup(ModCultivationRegistries.TECHNIQUES)
                    .map(registry -> registry.listElements().collect(java.util.stream.Collectors.toMap(
                            holder -> holder.key().location(), Holder.Reference::value,
                            (first, second) -> first, java.util.TreeMap::new)))
                    .<Map<ResourceLocation, TechniqueDefinition>>map(map -> map)
                    .orElse(Map.of());
        }

        /** As {@link ModCultivationRegistries#heritagesContaining}, by heritage id. */
        List<Map.Entry<ResourceLocation, HeritageDefinition>> heritagesContaining(ResourceLocation techniqueId) {
            return lookup(ModCultivationRegistries.HERITAGES)
                    .map(registry -> registry.listElements()
                            .filter(holder -> holder.value().contains(techniqueId))
                            .sorted(Comparator.comparing(holder -> holder.key().location().toString()))
                            .<Map.Entry<ResourceLocation, HeritageDefinition>>map(holder ->
                                    Map.entry(holder.key().location(), holder.value()))
                            .toList())
                    .orElse(List.of());
        }

        private <T> Optional<T> get(ResourceKey<Registry<T>> key, ResourceLocation id) {
            if (id == null) {
                return Optional.empty();
            }
            return lookup(key).flatMap(registry -> registry.get(ResourceKey.create(key, id))).map(Holder::value);
        }

        private <T> Optional<HolderLookup.RegistryLookup<T>> lookup(ResourceKey<Registry<T>> key) {
            if (provider != null) {
                return provider.lookup(key);
            }
            return Optional.ofNullable(CommonHooks.resolveLookup(key));
        }
    }
}
