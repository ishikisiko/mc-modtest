package com.example.myvillage.worldplan.runtime;

import com.example.myvillage.worldplan.WorldPlan;
import com.example.myvillage.worldplan.atlas.WorldAtlas;
import com.example.myvillage.worldplan.atlas.WorldAtlasSampler;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.LoggerFactory;

/** One immutable overworld plan per running server; all access is on the server thread. */
public final class WorldAtlasCache {
    private static WorldPlan owner;
    private static CompletableFuture<WorldAtlas> future;
    private static ExecutorService executor;
    private WorldAtlasCache() {}
    public static WorldAtlas read(WorldPlan plan) {
        if (owner!=plan) {
            clear(); owner=plan;
            executor=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"myvillage-atlas-sampler");t.setDaemon(true);return t;});
            future=CompletableFuture.supplyAsync(()->WorldAtlasSampler.capture(plan),executor).exceptionally(error->{
                LoggerFactory.getLogger(WorldAtlasCache.class).error("World atlas sampling failed",error);
                return WorldAtlas.state(WorldAtlas.Status.FAILED);
            });
        }
        return future.getNow(WorldAtlas.state(WorldAtlas.Status.LOADING));
    }
    public static void clear() {
        if (future!=null) future.cancel(true);
        if (executor!=null) executor.shutdownNow();
        future=null;executor=null;owner=null;
    }
}
