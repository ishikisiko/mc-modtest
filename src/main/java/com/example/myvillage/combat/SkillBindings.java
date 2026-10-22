package com.example.myvillage.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Saved choices, not physical keys. Unknown/unlearned IDs stay inert until valid again. */
public record SkillBindings(List<ResourceLocation> slots) {
    public static final int SIZE=6;
    public static final ResourceLocation AUTO=ResourceLocation.parse("myvillage:auto_weapon_art");
    public static final ResourceLocation EMPTY=ResourceLocation.parse("myvillage:unassigned");
    public static final Codec<SkillBindings> CODEC=ResourceLocation.CODEC.listOf().validate(ids -> {
        try { new SkillBindings(ids);return DataResult.success(ids); }
        catch(IllegalArgumentException e){return DataResult.error(e::getMessage);}
    }).xmap(SkillBindings::new,SkillBindings::slots);
    public SkillBindings {
        slots=List.copyOf(slots);
        if(slots.size()!=SIZE)throw new IllegalArgumentException("Exactly six skill bindings");
        Set<ResourceLocation> used=new HashSet<>();
        for(int i=0;i<SIZE;i++) {
            var id=slots.get(i);
            if(id.toString().length()>128 || (id.equals(AUTO) && i>1))throw new IllegalArgumentException("Invalid skill binding");
            if(!id.equals(AUTO) && !id.equals(EMPTY) && !used.add(id))throw new IllegalArgumentException("Duplicate skill binding");
        }
    }
    public static SkillBindings defaults(){return new SkillBindings(List.of(AUTO,AUTO,EMPTY,EMPTY,EMPTY,EMPTY));}
    public SkillBindings with(int slot,ResourceLocation id) {
        if(slot<0 || slot>=SIZE)throw new IllegalArgumentException("Invalid skill slot");
        var next=new ArrayList<>(slots);
        if(!id.equals(AUTO) && !id.equals(EMPTY))for(int i=0;i<SIZE;i++)if(next.get(i).equals(id))next.set(i,EMPTY);
        next.set(slot,id);return new SkillBindings(next);
    }
    public static int hudIndex(int slot){if(slot<0 || slot>=SIZE)throw new IllegalArgumentException("Invalid skill slot");return slot<2?slot:slot+2;}
}
