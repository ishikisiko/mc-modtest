package com.example.myvillage.entity.npc;

import com.example.myvillage.MyVillageMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
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
    /** The cultivator's looks: the male disciple, the female novice and the female adept. */
    public static final List<String> LOOKS = List.of("default", "f_novice", "f_adept");

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
    protected double strollSpeedModifier() {
        return STROLL_SPEED_MODIFIER;
    }
}
