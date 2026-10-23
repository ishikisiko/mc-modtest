package com.example.myvillage.entity.beast;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
public final class FrostplumePheasantEntity extends RosterBeastEntity {
    public static final ResourceLocation ID=ResourceLocation.fromNamespaceAndPath("myvillage", "frostplume_pheasant");
    public FrostplumePheasantEntity(EntityType<? extends FrostplumePheasantEntity> type,Level level){super(type,level);}
    @Override public ResourceLocation beastId(){return ID;}
}
