package com.example.myvillage.entity.npc;

import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.elemental.ElementalRules;
import com.example.myvillage.combat.runtime.CombatCaster;
import com.example.myvillage.combat.runtime.CombatDamageService;
import com.example.myvillage.cultivation.data.HeartMethod.Slot;
import com.example.myvillage.cultivation.data.ModCultivationRegistries;
import com.example.myvillage.cultivation.data.SchoolDefinition;
import com.example.myvillage.cultivation.data.SchoolRuntime;
import com.example.myvillage.cultivation.data.TechniqueCategory;
import com.example.myvillage.cultivation.data.TechniqueDefinition;
import com.example.myvillage.cultivation.technique.HeartTechniques;
import com.example.myvillage.item.TechniqueManualItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/** Birth loadouts and manual rewards use the same current, public technique catalogue. */
final class EvilCultivatorTechniques {
    record Entry(ResourceLocation id, TechniqueDefinition definition, SchoolDefinition school) {
        int grade() { return definition.grade(); }
        boolean commonHeart() {
            return definition.isCore() && definition.school().isEmpty() && definition.elements().isEmpty();
        }
        boolean matches(AttackStyle style) {
            if (commonHeart()) return true;
            if (school == null) return false;
            if (style.element() != null) return school.runtime().orElse(null) == SchoolRuntime.SPELL
                    && school.elementLean().contains(style.element());
            return school.weaponFamily().filter(style.family()::equals).isPresent();
        }
    }

    record AttackStyle(String family, ResourceLocation element) {
        static final AttackStyle SWORD = new AttackStyle("sword", null);
        boolean known() { return !family.isEmpty() || element != null; }
    }

    private EvilCultivatorTechniques() { }

    private static List<Entry> publicTechniques(RegistryAccess access) {
        var exclusive = access.registryOrThrow(ModCultivationRegistries.HERITAGES).stream()
                .filter(heritage -> heritage.exclusive()).flatMap(heritage -> heritage.techniques().stream())
                .collect(Collectors.toUnmodifiableSet());
        var techniques = access.registryOrThrow(ModCultivationRegistries.TECHNIQUES);
        return techniques.entrySet().stream()
                .filter(entry -> entry.getValue().grade() > 0 && !exclusive.contains(entry.getKey().location()))
                .map(entry -> new Entry(entry.getKey().location(), entry.getValue(),
                        entry.getValue().school().flatMap(id -> ModCultivationRegistries.school(access, id)).orElse(null)))
                .sorted(Comparator.comparing(Entry::id)).toList();
    }

    static NpcSkillLoadout birthLoadout(EvilCultivatorEntity npc) {
        var tier = EvilCultivatorRules.get().tier(npc.prodigy());
        var eligible = publicTechniques(npc.registryAccess()).stream()
                .filter(entry -> entry.grade() <= tier.learnedMaxGrade() && entry.matches(AttackStyle.SWORD))
                .filter(entry -> CombatCaster.eligible(npc, entry.definition()))
                .toList();
        var core = best(eligible.stream().filter(entry -> entry.definition().isCore()
                && HeartTechniques.slot(entry.definition()) == Slot.SPIRIT).toList(), npc.getRandom());
        var swordArt = best(eligible.stream().filter(entry -> entry.definition().category() == TechniqueCategory.ACTIVE
                && NpcSkillRuntime.executable(entry.id(), entry.definition())).toList(), npc.getRandom());
        var learned = new ArrayList<NpcSkillLoadout.Learned>();
        core.ifPresent(entry -> learned.add(new NpcSkillLoadout.Learned(entry.id().toString(), tier.startingMastery(), false, 1)));
        swordArt.ifPresent(entry -> learned.add(new NpcSkillLoadout.Learned(entry.id().toString(), tier.startingMastery(), true, 0)));
        return new NpcSkillLoadout("", core.map(entry -> entry.id().toString()).orElse(""), learned);
    }

    private static Optional<Entry> best(List<Entry> entries, RandomSource random) {
        if (entries.isEmpty()) return Optional.empty();
        int highest = entries.stream().mapToInt(Entry::grade).max().orElseThrow();
        var pool = entries.stream().filter(entry -> entry.grade() == highest).toList();
        return Optional.of(pool.get(random.nextInt(pool.size())));
    }

    static ItemStack manual(EvilCultivatorEntity npc, DamageSource source) {
        var rules = EvilCultivatorRules.get();
        var tier = rules.tier(npc.prodigy());
        var random = npc.getRandom();
        if (random.nextDouble() >= tier.manualChance()) return ItemStack.EMPTY;
        return pick(publicTechniques(npc.registryAccess()), tier, attackStyle(npc, source), rules.manualPreference(), random)
                .map(entry -> TechniqueManualItem.manualFor(npc.registryAccess(), entry.id()))
                .orElse(ItemStack.EMPTY);
    }

    /** Draw the preference bucket before the grade so the 50% is conditional on a manual drop. */
    static Optional<Entry> pick(List<Entry> entries, EvilCultivatorRules.Tier tier, AttackStyle style,
            double preference, RandomSource random) {
        var allowed = entries.stream().filter(entry -> tier.gradeWeight(entry.grade()) > 0).toList();
        if (allowed.isEmpty()) return Optional.empty();
        var pool = allowed;
        if (style.known()) {
            boolean prefer = random.nextDouble() < preference;
            var bucket = allowed.stream().filter(entry -> entry.matches(style) == prefer).toList();
            if (!bucket.isEmpty()) pool = bucket;
        }
        int weight = pool.stream().map(Entry::grade).distinct().mapToInt(tier::gradeWeight).sum();
        int ticket = random.nextInt(weight);
        for (int grade = 1; grade <= TechniqueManualItem.MAX_GRADE; grade++) {
            int selectedGrade = grade;
            var atGrade = pool.stream().filter(entry -> entry.grade() == selectedGrade).toList();
            if (atGrade.isEmpty()) continue;
            ticket -= tier.gradeWeight(grade);
            if (ticket < 0) return Optional.of(atGrade.get(random.nextInt(atGrade.size())));
        }
        throw new IllegalStateException("Manual grade draw did not select an available grade");
    }

    private static AttackStyle attackStyle(LivingEntity victim, DamageSource source) {
        var element = ElementalRules.type(source);
        if (element.isPresent()) {
            return new AttackStyle("", ResourceLocation.fromNamespaceAndPath("yuanxian", element.get().id()));
        }
        String weaponFamily = CombatDamageService.weaponFamilyForHit(victim, source).orElseGet(() -> {
            if (!(source.getEntity() instanceof LivingEntity attacker) || source.getDirectEntity() != attacker
                    || !source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypes.MOB_ATTACK)) return "";
            var weapon = attacker.getMainHandItem();
            return CombatStyles.bundled().weapon(weapon).flatMap(weaponDefinition -> weaponDefinition.family())
                    .orElseGet(() -> weapon.is(ItemTags.SWORDS) ? "sword" : weapon.isEmpty() ? "fist" : "");
        });
        return new AttackStyle(weaponFamily, null);
    }
}
