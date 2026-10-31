package com.example.myvillage.manor;

import com.example.myvillage.worldplan.capital.CapitalCity;
import com.example.myvillage.worldplan.runtime.WorldPlanRuntime;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** 仅按当前世界的真实位置标注归属，不把材质流派当作建筑所有权。 */
public final class ManorLocation {
    public record Name(String title,boolean gallery) {
        public Component component() {
            return gallery?Component.translatable("tooltip.myvillage.manor_gallery",title):Component.literal(title);
        }
    }
    public static Optional<Name> at(ServerLevel level,BlockPos pos,ManorPresentation.Info info) {
        var catalog=ManorCatalog.get();
        if(level.dimension().location().toString().equals(catalog.gallery.dimension())) {
            for(int i=0;i<catalog.manors.size();i++) {
                var manor=catalog.manors.get(i);var gallery=catalog.gallery;
                int x=i%gallery.columns()*gallery.spacing(),z=i/gallery.columns()*gallery.spacing();
                if(manor.city().equals(info.city())&&pos.getX()>=x&&pos.getX()<x+manor.width()
                        &&pos.getY()>=gallery.floorY()&&pos.getY()<gallery.floorY()+manor.height()
                        &&pos.getZ()>=z&&pos.getZ()<z+manor.depth())
                    return Optional.of(new Name(manor.name(),true));
            }
            return Optional.empty();
        }
        return WorldPlanRuntime.generator(level).flatMap(generator -> generator.plan().capitalAt(pos.getX(),pos.getZ()))
                .filter(city -> contains(city,pos,info.city()))
                .map(city -> new Name(catalog.manor(city.regionId()).name(),false));
    }
    public static boolean contains(CapitalCity city,BlockPos pos,String componentCity) {
        return city.regionId().equals(componentCity)&&city.bundle().manorSite
                .filter(site -> site.contains(pos.getX()-city.x(),pos.getY()-city.originY(),pos.getZ()-city.z())).isPresent();
    }
    private ManorLocation() { }
}
