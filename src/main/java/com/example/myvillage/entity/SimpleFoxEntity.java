package com.example.myvillage.entity;

import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.level.Level;

public final class SimpleFoxEntity extends Fox {
    public SimpleFoxEntity(EntityType<? extends Fox> entityType, Level level) {
        super(entityType, level);
    }

    @Nullable
    @Override
    public SimpleFoxEntity getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
        SimpleFoxEntity child = ModEntities.SIMPLE_FOX.get().create(level);
        if (child != null) {
            Fox.Type otherVariant = otherParent instanceof Fox fox
                    ? fox.getVariant()
                    : this.getVariant();
            child.setVariant(this.random.nextBoolean() ? this.getVariant() : otherVariant);
        }
        return child;
    }
}
