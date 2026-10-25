package com.example.myvillage.entity.npc;

import com.example.myvillage.MyVillageMod;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
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
    private boolean appearanceChosen;

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
        return appearanceChosen ? look() : CultivatorVariants.randomLook(random::nextInt);
    }

    @Override
    public void setLook(String look) {
        super.setLook(look);
        appearanceChosen = true;
    }

    /** 入世时兜住未调用 finalizeSpawn 的命令／直接创建；旧存档和显式 Look 不重抽。 */
    public void prepareAppearance(boolean loadedFromDisk) {
        if (!loadedFromDisk && !appearanceChosen) setLook(spawnEggLook(getRandom()));
        appearanceChosen = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        // 旧 default 也明确写入，确保跨维度复制／再次加载不会被误认成未选款的新实体。
        tag.putString(LOOK_TAG, look());
    }

    @Override
    protected double strollSpeedModifier() {
        return STROLL_SPEED_MODIFIER;
    }
}
