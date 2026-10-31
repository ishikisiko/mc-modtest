package com.example.myvillage.compat.jade;

import com.example.myvillage.manor.ManorLocation;
import com.example.myvillage.manor.ManorPresentation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.*;

/** 已有 Jade 请求仅同步一条位置说明，不新增逐格实体或专用网络协议。 */
public final class ManorJadeDataProvider implements IServerDataProvider<BlockAccessor> {
    static final String DATA="myvillage_manor_location";
    @Override public ResourceLocation getUid() { return ManorJadePlugin.NAMES; }
    @Override public boolean shouldRequestData(BlockAccessor accessor) {
        return ManorPresentation.get().describe(accessor.getBlockState()).isPresent();
    }
    @Override public void appendServerData(CompoundTag data,BlockAccessor accessor) {
        data.remove(DATA);
        if(!(accessor.getLevel() instanceof ServerLevel level))return;
        ManorPresentation.get().describe(accessor.getBlockState()).flatMap(info -> ManorLocation.at(level,accessor.getPosition(),info))
                .ifPresent(name -> data.put(DATA,encode(level.dimension().location(),accessor.getPosition(),accessor.getBlockState(),name)));
    }
    public static CompoundTag encode(ResourceLocation dimension,BlockPos pos,BlockState state,ManorLocation.Name name) {
        var data=new CompoundTag();
        data.putString("dimension",dimension.toString());data.putLong("pos",pos.asLong());
        data.put("state",NbtUtils.writeBlockState(state));data.putString("name",name.title());data.putBoolean("gallery",name.gallery());
        return data;
    }
    public static Optional<ManorLocation.Name> read(CompoundTag serverData,ResourceLocation dimension,BlockPos pos,BlockState state) {
        var data=serverData.getCompound(DATA);
        if(!data.contains("pos",Tag.TAG_LONG)||!data.getString("dimension").equals(dimension.toString())||data.getLong("pos")!=pos.asLong()
                ||!data.getCompound("state").equals(NbtUtils.writeBlockState(state))||data.getString("name").isBlank())return Optional.empty();
        return Optional.of(new ManorLocation.Name(data.getString("name"),data.getBoolean("gallery")));
    }
}
