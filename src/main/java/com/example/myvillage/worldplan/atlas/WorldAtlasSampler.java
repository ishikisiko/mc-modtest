package com.example.myvillage.worldplan.atlas;

import com.example.myvillage.worldplan.WorldPlan;
import java.util.Base64;
import java.util.ArrayList;

/** Pure sampling of the saved plan, deliberately skipping ecology and chunk access. */
public final class WorldAtlasSampler {
    private WorldAtlasSampler() {}
    public static WorldAtlas capture(WorldPlan plan) {
        int n=WorldAtlas.RESOLUTION, radius=plan.rules().coreRadius+2*plan.rules().coreBlend;
        var regions=new ArrayList<WorldAtlas.Region>();
        for (var r:plan.regions()) {
            double[] p=plan.geometry().unwarp(r.x(),r.z());
            regions.add(new WorldAtlas.Region(r.id(),r.profile().name(),p[0],p[1]));
        }
        byte[] cells=new byte[n*n];
        for (int z=0;z<n;z++) {
            if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
            int wz=(int)Math.round(-radius+z*2.0*radius/(n-1));
            for (int x=0;x<n;x++) {
                int wx=(int)Math.round(-radius+x*2.0*radius/(n-1)), index=0;
                if (StrictMath.hypot(wx,wz)<=plan.rules().coreRadius) {
                    double[] p=plan.geometry().warp(wx,wz); double best=Double.POSITIVE_INFINITY;
                    for (int i=0;i<plan.regions().size();i++) {
                        var r=plan.regions().get(i); double distance=StrictMath.hypot(p[0]-r.x(),p[1]-r.z());
                        if (distance<best) { best=distance; index=i+1; }
                    }
                }
                cells[z*n+x]=(byte)(index | (plan.surfaceY(wx,wz)<plan.rules().seaLevel?128:0));
            }
        }
        var cities=new ArrayList<WorldAtlas.City>();
        for(var city:plan.cities()) {
            var entry=city.entrance();
            cities.add(new WorldAtlas.City(city.cityId(),city.bundle().displayName,city.regionId(),entry.x(),entry.y(),entry.z()));
        }
        return new WorldAtlas(WorldAtlas.Status.READY,plan.fingerprint(),n,radius,plan.rules().coreRadius,
                regions,cities,Base64.getEncoder().encodeToString(cells));
    }
}
