package com.example.myvillage.entity.npc;

import com.example.myvillage.MyVillageMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** Neutral sword cultivator; the same body is also the world ledger's managed projection. */
public final class CultivatorEntity extends NpcEntity {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivator");
    /** An unhurried walk: about 1.7 blocks per second. */
    static final double STROLL_SPEED_MODIFIER = 0.65;
    /** 旧外观和 npcgen 组合共享同一个目录，注册所有模型与渲染层。 */
    public static final List<String> LOOKS = CultivatorVariants.looks();

    public CultivatorEntity(EntityType<? extends CultivatorEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, NpcCombatRules.get().health())
                .add(Attributes.MOVEMENT_SPEED, NpcCombatRules.get().speed())
                .add(Attributes.ATTACK_DAMAGE, NpcCombatRules.get().attack())
                .add(Attributes.ATTACK_KNOCKBACK, 0)
                .add(Attributes.FOLLOW_RANGE, NpcCombatRules.get().followRange());
    }

    @Override
    protected List<String> looks() {
        return LOOKS;
    }

    @Override
    protected String spawnEggLook(RandomSource random) {
        // 保持原有三类外观的占比；只在选中类别内抽取组合。
        List<String> families = CultivatorVariants.legacyLooks();
        String family = families.get(random.nextInt(families.size()));
        if (CultivatorLooks.DEFAULT.equals(family)) {
            var variants = CultivatorVariants.males();
            return variants.get(random.nextInt(variants.size())).look();
        }
        if (!CultivatorLooks.FEMALE_NOVICE.equals(family)) return family;
        var variants = CultivatorVariants.novices();
        return variants.get(random.nextInt(variants.size())).look();
    }

    @Override
    protected double strollSpeedModifier() {
        return STROLL_SPEED_MODIFIER;
    }
}
