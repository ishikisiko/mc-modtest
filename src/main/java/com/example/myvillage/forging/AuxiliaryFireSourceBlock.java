package com.example.myvillage.forging;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.*;

/** A natural source is captured once; a spent earth vent still anchors its captured fire. */
public final class AuxiliaryFireSourceBlock extends Block {
    public static final BooleanProperty LIT=BlockStateProperties.LIT;
    private static final MapCodec<AuxiliaryFireSourceBlock> EARTH=simpleCodec(p->new AuxiliaryFireSourceBlock(p,"earth"));
    private static final MapCodec<AuxiliaryFireSourceBlock> COLD=simpleCodec(p->new AuxiliaryFireSourceBlock(p,"cold"));
    private final String kind;
    public AuxiliaryFireSourceBlock(Properties properties,String kind){super(properties);this.kind=kind;registerDefaultState(stateDefinition.any().setValue(LIT,true));}
    public String kind(){return kind;}
    @Override protected MapCodec<? extends Block> codec(){return kind.equals("earth")?EARTH:COLD;}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(LIT);}
}
