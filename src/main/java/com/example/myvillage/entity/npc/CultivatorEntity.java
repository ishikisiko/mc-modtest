package com.example.myvillage.entity.npc;

import com.example.myvillage.MyVillageMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/**
 * 修仙者: a sect disciple in a layered robe. Its disposition is not decided yet, so it only stands,
 * strolls and looks at players; the attributes below are a plain body's until a friend or foe
 * design replaces them. The world simulation also uses this type for the avatars of its ledger
 * persons (see {@link NpcEntity#becomeLedgerAvatar}); the summoned cultivator is unchanged by that.
 */
public final class CultivatorEntity extends NpcEntity {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(MyVillageMod.MOD_ID, "cultivator");
    static final double MAX_HEALTH = 20.0;
    static final double MOVEMENT_SPEED = 0.3;
    /** An unhurried walk: about 1.7 blocks per second. */
    static final double STROLL_SPEED_MODIFIER = 0.65;
    /** The cultivator's looks: the male disciple, the female novice and the female adept. */
    public static final List<String> LOOKS = List.of("default", "f_novice", "f_adept");

    public CultivatorEntity(EntityType<? extends CultivatorEntity> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED);
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
