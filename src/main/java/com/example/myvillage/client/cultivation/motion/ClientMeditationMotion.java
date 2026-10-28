package com.example.myvillage.client.cultivation.motion;

import com.example.myvillage.cultivation.network.MeditationPosePayload;
import com.example.myvillage.cultivation.network.MeditationPoseReceiver;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.slf4j.LoggerFactory;

/** Client presentation cache keyed by both UUID and entity identity, discarded on world changes. */
public final class ClientMeditationMotion {
    private static ClientLevel level;
    private static final Map<UUID,Entry> ENTRIES=new HashMap<>();
    private static MeditationMotion motion=MeditationMotion.bundled();
    private static final class Entry {
        final int entityId;
        final MeditationTimeline timeline=new MeditationTimeline();
        Vec3 anchor;
        double received;
        double cachedTime=Double.NaN;
        MeditationPose cachedPose;
        boolean observed;
        Entry(int entityId){this.entityId=entityId;}
    }
    public static void initialize(){MeditationPoseReceiver.install(ClientMeditationMotion::receive);}
    public static void clear(){ENTRIES.clear();level=null;}
    public static MeditationMotion definition(){return motion;}
    public static void reload(net.minecraft.server.packs.resources.ResourceManager manager) {
        try(var stream=manager.getResourceOrThrow(ResourceLocation.parse("myvillage:cultivation/meditation_motion.json")).open();
                var reader=new java.io.InputStreamReader(stream,java.nio.charset.StandardCharsets.UTF_8)) {
            motion=MeditationMotion.parse(reader);MeditationSurface.clear();
        } catch(java.io.IOException e){throw new IllegalStateException("修炼动作加载失败",e);}
    }
    private static void world() {
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=level){ENTRIES.clear();level=current;}
    }
    private static void receive(MeditationPosePayload p) {
        world();
        if(level==null||!p.dimension().equals(level.dimension().location()))return;
        Entry e=ENTRIES.get(p.playerId());
        if(e==null||e.entityId!=p.entityId()){e=new Entry(p.entityId());ENTRIES.put(p.playerId(),e);}
        if(!e.timeline.accept(p.revision(),p.active(),p.startedAt(),p.enterTicks(),p.graceful(),motion.exitTicks()))return;
        e.received=level.getGameTime();e.cachedTime=Double.NaN;e.observed=false;
        var entity=level.getEntity(p.entityId());
        e.anchor=entity!=null&&entity.getUUID().equals(p.playerId())?entity.position():null;
        LoggerFactory.getLogger(ClientMeditationMotion.class).info("MEDITATION_POSE player={} active={} graceful={} revision={} age={}",
                p.playerId(),p.active(),p.graceful(),p.revision(),Math.max(0,level.getGameTime()-p.startedAt()));
    }
    public static void tick() {
        world();if(level==null)return;
        ENTRIES.entrySet().removeIf(entry->{
            Entry e=entry.getValue();var entity=level.getEntity(e.entityId);
            return level.getGameTime()-e.received>100&&(entity==null||!entity.getUUID().equals(entry.getKey()));
        });
    }
    public static MeditationPose pose(AbstractClientPlayer player,float partialTick) {
        world();if(level==null)return null;
        Entry e=ENTRIES.get(player.getUUID());
        if(e==null||e.entityId!=player.getId())return null;
        double now=level.getGameTime()+partialTick;
        if(e.anchor==null)e.anchor=player.position();
        // Stop rendering immediately on visible interruption; never modifies authoritative gains/state.
        if(!player.isAlive()||player.isSpectator()||player.isPassenger()||player.isSleeping()
                ||player.isFallFlying()||player.isSwimming()||player.getAbilities().flying
                ||!player.onGround()||player.swinging||player.isUsingItem()||player.hurtTime>0
                ||player.position().distanceToSqr(e.anchor)>.0004) {
            if(!e.timeline.active())e.timeline.abort(now);return null;
        }
        float weight=e.timeline.weight(now);
        if(weight<=.00001f)return null;
        if(e.cachedTime!=now) {
            e.cachedPose=MeditationPose.sample(motion,weight,e.timeline.age(now),player.getSkin().model()==PlayerSkin.Model.SLIM);
            e.cachedTime=now;
        }
        return e.cachedPose;
    }
    public static void observed(AbstractClientPlayer player,MeditationPose pose) {
        Entry e=ENTRIES.get(player.getUUID());
        if(e!=null && !e.observed && pose.weight()>.999f) {
            e.observed=true;
            LoggerFactory.getLogger(ClientMeditationMotion.class).info("MEDITATION_POSE_RENDERED player={} revision={} drop_pixels={} skin={}",
                    player.getUUID(),e.timeline.revision(),pose.drop(),player.getSkin().model());
        }
    }
    public static boolean displaying(AbstractClientPlayer player) {
        return pose(player,Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true))!=null;
    }
    private ClientMeditationMotion() {}
}
