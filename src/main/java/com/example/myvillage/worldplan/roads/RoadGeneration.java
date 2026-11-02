package com.example.myvillage.worldplan.roads;

import com.example.myvillage.worldplan.WorldPlan;
import java.util.*;
import java.util.concurrent.*;

/** A generator-owned planning job; no world, chunk or entity access on the planning thread. */
public final class RoadGeneration implements AutoCloseable {
    private final RoadRules rules=RoadRules.load();
    private final RoadLayout cached;
    private volatile RoadLayout layout;
    private volatile RoadNetworkPlanner.Progress progress=new RoadNetworkPlanner.Progress("idle","","",0,0);
    private volatile String failure="";
    private volatile long started,finished;
    private WorldPlan world;
    private ExecutorService worker;
    private CompletableFuture<RoadLayout> ready;
    private volatile boolean closed;
    public RoadGeneration(Optional<RoadLayout> cached){this.cached=cached.orElse(null);}
    public synchronized void bind(WorldPlan world){
        if(this.world!=null){if(!this.world.fingerprint().equals(world.fingerprint()))throw new IllegalStateException("不能跨世界复用道路生成器");return;}
        this.world=world;started=System.nanoTime();
        if(cached!=null){cached.requireIdentity(world,rules);layout=cached;finished=started;progress=new RoadNetworkPlanner.Progress("cached","","",cached.routes().size(),cached.routes().size());ready=CompletableFuture.completedFuture(cached);return;}
        if(!rules.automatic){layout=RoadLayout.empty(world,rules);finished=started;progress=new RoadNetworkPlanner.Progress("disabled","","",0,0);ready=CompletableFuture.completedFuture(layout);return;}
        progress=new RoadNetworkPlanner.Progress("idle","","",0,world.cities().size()-1);ready=new CompletableFuture<>();
    }
    private synchronized void start(){
        if(closed)throw new CancellationException("服务器已停止");
        if(worker!=null||layout!=null||!failure.isEmpty())return;
        if(world==null)throw new IllegalStateException("道路生成器尚未绑定种子");
        started=System.nanoTime();progress=new RoadNetworkPlanner.Progress("searching","","",0,world.cities().size()-1);
        worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"MyVillage-RoadGeneration");t.setDaemon(true);return t;});
        worker.submit(()->{
            try{var network=RoadNetworkPlanner.plan(world,rules,List.of(),List.of(),p->progress=p);RoadLayout result=RoadLayout.from(world,rules,network);result.requireIdentity(world,rules);if(closed)throw new CancellationException("服务器已停止");layout=result;finished=System.nanoTime();ready.complete(result);org.slf4j.LoggerFactory.getLogger(RoadGeneration.class).info("官道生成布局就绪：{} 城、{} 段、{} 区块，{} 秒",world.cities().size(),result.routes().size(),result.slices().size(),seconds());}
            catch(Throwable e){failure=e.getMessage()==null?e.toString():e.getMessage();finished=System.nanoTime();progress=new RoadNetworkPlanner.Progress("failed","","",progress.plannedRoutes(),progress.requiredRoutes());ready.completeExceptionally(e);org.slf4j.LoggerFactory.getLogger(RoadGeneration.class).error("官道生成布局失败：{}",failure,e);}
            finally{worker.shutdown();}
        });
    }
    public RoadLayout await(){
        RoadLayout known=layout;if(known!=null)return known;
        start();
        CompletableFuture<RoadLayout> f=ready;if(f==null)throw new IllegalStateException("道路生成器尚未绑定种子");
        try{return f.get();}catch(InterruptedException e){Thread.currentThread().interrupt();throw new CancellationException("道路生成已取消");}catch(ExecutionException e){throw new IllegalStateException("道路布局失败："+failure,e.getCause());}
    }
    public Optional<RoadLayout> snapshot(){return Optional.ofNullable(layout);}
    public RoadRules rules(){return rules;}
    public RoadNetworkPlanner.Progress progress(){return progress;}
    public String failure(){return failure;}
    public double seconds(){return started==0?0:((finished==0?System.nanoTime():finished)-started)/1e9;}
    @Override public synchronized void close(){closed=true;if(worker!=null)worker.shutdownNow();if(ready!=null&&!ready.isDone())ready.completeExceptionally(new CancellationException("服务器已停止"));}
}
