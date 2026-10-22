package com.example.myvillage.client.combat;

import com.example.myvillage.client.cultivation.ClientCultivationState;
import com.example.myvillage.combat.CombatMode;
import com.example.myvillage.combat.art.ArtDefinition;
import com.example.myvillage.combat.art.ArtMath;
import com.example.myvillage.combat.definition.CombatStyles;
import com.example.myvillage.combat.definition.WeaponDefinition;
import com.example.myvillage.combat.network.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import java.util.*;

/** Presentation only. Accepted server cues create meshes; local key presses create no hits or motion. */
public final class ClientAdvancedArts {
    private record Stamp(net.minecraft.world.entity.Entity entity,long revision,long tick,int kind,ArtDefinition art,int phase) {}
    private record Glow(int entity,ArtDefinition art,Vec3 origin,float yaw,double born,double lifetime,boolean ward,
                        List<ArtRibbonShape.Quad> mesh) {}
    private static final Map<ResourceLocation,Long> COOLDOWNS=new HashMap<>();
    private static final Map<Integer,Stamp> STAMPS=new HashMap<>();
    private static final List<Glow> GLOWS=new ArrayList<>();
    private static ClientLevel world;
    private static long activeUntil=Long.MIN_VALUE,pendingUntil=Long.MIN_VALUE,localRevision;
    private static long particleTick=Long.MIN_VALUE;
    private static int particleBudget;
    private ClientAdvancedArts() {}
    static void install(IEventBus bus) {
        CombatArtReceiver.install(ClientAdvancedArts::receive);
        bus.addListener(ClientAdvancedArts::registerHud);
        NeoForge.EVENT_BUS.addListener(ClientAdvancedArts::tick);
        NeoForge.EVENT_BUS.addListener(ClientAdvancedArts::renderWorld);
        NeoForge.EVENT_BUS.addListener(ClientAdvancedArts::logout);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST,false,InputEvent.InteractionKeyMappingTriggered.class,
                ClientAdvancedArts::suppressConflictingInput);
    }
    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR,ResourceLocation.parse("myvillage:advanced_arts"),ClientAdvancedArts::hud);
    }
    private static void ensureWorld() {
        ClientLevel current=Minecraft.getInstance().level;
        if(current!=world) {
            world=current;GLOWS.clear();STAMPS.clear();activeUntil=Long.MIN_VALUE;pendingUntil=Long.MIN_VALUE;
        }
    }
    private static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        COOLDOWNS.clear();GLOWS.clear();STAMPS.clear();world=null;
        activeUntil=pendingUntil=Long.MIN_VALUE;localRevision=0;
    }
    private static void tick(ClientTickEvent.Post event) {
        ensureWorld();
        while(ClientCombatKeyMappings.ART_PRIMARY.consumeClick()) intent(0);
        while(ClientCombatKeyMappings.ART_SECONDARY.consumeClick()) intent(1);
        if(world!=null) STAMPS.entrySet().removeIf(e -> world.getEntity(e.getKey())!=e.getValue().entity());
        var player=Minecraft.getInstance().player;
        if(player==null || !player.isAlive())activeUntil=pendingUntil=Long.MIN_VALUE;
    }
    private static boolean busy() {
        long now=ClientCombatClock.ticks();return now<activeUntil || now<pendingUntil;
    }
    private static void intent(int slot) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.screen!=null || mc.isPaused() || !mc.isWindowActive()
                || !mc.player.isAlive() || mc.player.isSpectator() || ClientCombatState.mode()!=CombatMode.CULTIVATION
                || busy() || ClientCultivationState.meditation().map(s -> s.state().active()).orElse(false)) return;
        pendingUntil=ArtMath.deadline(ClientCombatClock.ticks(),8);
        PacketDistributor.sendToServer(new CombatArtIntentPayload(slot));
    }
    private static void suppressConflictingInput(InputEvent.InteractionKeyMappingTriggered event) {
        if(busy() && (event.isAttack() || event.isUseItem())) {
            event.setCanceled(true);event.setSwingHand(false);
        }
    }
    /** The preceding art START marks the generic animation action as external, never a basic combo. */
    static Optional<com.example.myvillage.combat.definition.AttackMoveDefinition> startedMove(int entityId,long revision) {
        Stamp stamp=STAMPS.get(entityId);
        if(stamp==null || stamp.revision()!=revision || stamp.kind()==CombatArtStatePayload.STOP
                || world==null || world.getEntity(entityId)!=stamp.entity()) return Optional.empty();
        return Optional.of(stamp.art().phases().get(stamp.phase()).move(CombatStyles.bundled(),1));
    }
    private static void receive(CombatArtStatePayload p) {
        ensureWorld();Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null) return;
        var art=ArtDefinition.find(p.artId()).orElse(null);
        if(art==null || p.phase()>=art.phases().size()) return;
        boolean self=p.entityId()==mc.player.getId();
        long now=ClientCombatClock.ticks();
        if(p.kind()==CombatArtStatePayload.COOLDOWN) {
            if(self) COOLDOWNS.put(art.id(),ArtMath.deadline(now,p.cooldown()));
            return;
        }
        var caster=mc.level.getEntity(p.entityId());if(caster==null)return;
        var old=STAMPS.get(p.entityId());
        if(old!=null && old.entity()==caster && (p.revision()<old.revision() || (p.revision()==old.revision()
                && (p.serverTick()<old.tick() || (p.serverTick()==old.tick() && p.kind()==old.kind()))))) return;
        STAMPS.put(p.entityId(),new Stamp(caster,p.revision(),p.serverTick(),p.kind(),art,p.phase()));
        var phase=art.phases().get(p.phase());
        if(self) {
            COOLDOWNS.put(art.id(),ArtMath.deadline(now,p.cooldown()));
            if(p.kind()==CombatArtStatePayload.START) {
                localRevision=p.revision();pendingUntil=Long.MIN_VALUE;
                activeUntil=ArtMath.deadline(now,phase.move(CombatStyles.bundled(),1).totalTicks()+4);
            } else if(p.kind()==CombatArtStatePayload.STOP && p.revision()>=localRevision) {
                activeUntil=pendingUntil=Long.MIN_VALUE;
            }
        }
        if(p.kind()==CombatArtStatePayload.STOP) {
            GLOWS.removeIf(g -> g.entity()==p.entityId() && g.ward());return;
        }
        if(p.kind()==CombatArtStatePayload.START) {
            if(p.phase()==0 && p.origin().distanceToSqr(mc.player.position())<32*32)
                mc.level.playLocalSound(p.origin().x,p.origin().y,p.origin().z,SoundEvents.AMETHYST_BLOCK_CHIME,
                        SoundSource.PLAYERS,.5F,art.castPitch(),false);
            if(p.phase()!=0 || art.guardReduction()<=0) return;
        }
        if(mc.level.getEntity(p.entityId())==null || p.origin().distanceToSqr(mc.player.position())>64*64) return;
        boolean ward=p.kind()==CombatArtStatePayload.WARD || p.kind()==CombatArtStatePayload.START;
        double life=ward?Math.max(0,phase.move(CombatStyles.bundled(),1).activeStartTick()-p.age()
                -ClientCombatClock.elapsedSinceServer(p.serverTick())):art.fxLifetimeTicks();
        if(life<=0)return;
        if(GLOWS.size()>=64) GLOWS.removeFirst();
        GLOWS.add(new Glow(p.entityId(),art,p.origin(),p.yaw(),now,life,ward,
                ArtRibbonShape.mesh(phase.fx(),phase.reach(),phase.shape().equals("ring")?360:phase.arc(),
                        phase.height(),art.particleCount(),ward)));
        if(particleTick!=now) { particleTick=now;particleBudget=128; }
        int count=Math.min(particleBudget,ward?4:art.particleCount()/3);
        particleBudget-=count;
        var dust=new DustParticleOptions(new Vector3f(art.red(),art.green(),art.blue()),ward?.7F:1.0F);
        for(int i=0;i<count;i++) {
            double angle=2*Math.PI*i/Math.max(1,count),r=ward?.7:Math.min(1.2,phase.reach()/3);
            mc.level.addParticle(dust,p.origin().x+Math.sin(angle)*r,p.origin().y+phase.height(),
                    p.origin().z+Math.cos(angle)*r,0,.02,0);
        }
    }
    private static void renderWorld(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || GLOWS.isEmpty())return;
        ensureWorld();Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        double now=ClientCombatClock.now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        Vec3 camera=event.getCamera().getPosition();
        var buffers=mc.renderBuffers().bufferSource();
        VertexConsumer vertices=buffers.getBuffer(CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT);
        GLOWS.removeIf(g -> now-g.born()>=g.lifetime() || mc.level.getEntity(g.entity())==null);
        for(var glow:GLOWS) {
            float alpha=(float)Math.clamp(1-(now-glow.born())/glow.lifetime(),0,1);
            Vec3 origin=glow.origin().subtract(camera);
            for(var q:glow.mesh()) {
                vertex(vertices,q.a(),origin,glow,alpha*.75F,false);
                vertex(vertices,q.b(),origin,glow,alpha*.75F,false);
                vertex(vertices,q.c(),origin,glow,alpha,true);
                vertex(vertices,q.d(),origin,glow,alpha,true);
            }
        }
        buffers.endBatch(CombatRenderTypes.WEAPON_TRAIL_TRANSLUCENT);
    }
    private static void vertex(VertexConsumer out,ArtRibbonShape.Point v,Vec3 origin,Glow glow,float alpha,boolean core) {
        double yaw=Math.toRadians(glow.yaw()),x=v.x()*Math.cos(yaw)-v.z()*Math.sin(yaw),
                z=v.x()*Math.sin(yaw)+v.z()*Math.cos(yaw);
        float mix=core?.8F:0;
        out.addVertex((float)(origin.x+x),(float)(origin.y+v.y()),(float)(origin.z+z))
                .setColor(Math.round(255*(glow.art().red()*(1-mix)+mix)),
                        Math.round(255*(glow.art().green()*(1-mix)+mix)),
                        Math.round(255*(glow.art().blue()*(1-mix)+mix)),Math.round(255*alpha));
    }
    private static void hud(GuiGraphics g,DeltaTracker delta) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.options.hideGui || mc.player.isSpectator()
                || ClientCombatState.mode()!=CombatMode.CULTIVATION)return;
        var family=CombatStyles.bundled().weapon(mc.player.getMainHandItem()).flatMap(WeaponDefinition::family);
        var profile=ClientCultivationState.latest().orElse(null);
        if(family.isEmpty() || profile==null)return;
        if(!ArtHotbarLayout.fits(g.guiWidth(),g.guiHeight()))return;
        for(int slot=0;slot<2;slot++) {
            var art=ArtDefinition.forSlot(family.get(),slot).orElse(null);if(art==null)continue;
            var box=ArtHotbarLayout.slot(g.guiWidth(),g.guiHeight(),slot);
            int x=box.x(),y=box.y(),size=box.size();
            int remaining=ArtMath.remaining(ClientCombatClock.ticks(),COOLDOWNS.getOrDefault(art.id(),ClientCombatClock.ticks()));
            boolean learned=profile.learnedTechniques().containsKey(art.id());
            boolean qualified=com.example.myvillage.cultivation.data.ModCultivationRegistries.technique(mc.level.registryAccess(),art.id())
                    .map(def -> com.example.myvillage.cultivation.technique.TechniqueRequirementEvaluator
                            .evaluate(mc.level.registryAccess(),profile,def).satisfied()).orElse(false);
            long cost=com.example.myvillage.forging.ForgedEquipment.qiCost(mc.player.getMainHandItem(),art.qiCost());
            boolean power=profile.currentSpiritualPower()>=cost;
            boolean usable=learned && qualified;
            int edge=!usable?0xFF5E645D:!power?0xFF995C50:remaining>0?0xFF777064:0xFFBBA77A;
            g.fill(x-1,y-1,x+size+1,y+size+1,0xB5000000);
            g.fill(x,y,x+size,y+size,edge);
            g.fill(x+1,y+1,x+size-1,y+size-1,0xFF15221F);
            var texture=ResourceLocation.fromNamespaceAndPath(art.id().getNamespace(),
                    "textures/gui/advanced_arts/"+art.id().getPath()+".png");
            g.pose().pushPose();g.pose().translate(x+2,y+2,0);
            g.pose().scale((size-4)/32F,(size-4)/32F,1);
            g.blit(texture,0,0,0,0,32,32,32,32);g.pose().popPose();
            if(!usable) {
                g.fill(x+1,y+1,x+size-1,y+size-1,0xB8181C1B);
                // Small lock, leaving the distinct skill silhouette readable underneath.
                g.fill(x+9,y+10,x+15,y+16,0xFFABA898);
                g.fill(x+10,y+7,x+14,y+11,0xFFABA898);
                g.fill(x+11,y+8,x+13,y+11,0xFF202824);
            } else {
                if(!power)g.fill(x+1,y+1,x+size-1,y+size-1,0x80551F1B);
                if(remaining>0) {
                    int cover=ArtHotbarLayout.coverHeight(remaining,art.cooldownTicks(),size-2);
                    g.fill(x+1,y+1,x+size-1,y+1+cover,0xBA08100F);
                    String time=ArtHotbarLayout.cooldownText(remaining);
                    g.drawString(mc.font,time,x+(size-mc.font.width(time))/2,y+8,0xFFFFF1D1,true);
                } else if(!power) {
                    g.drawString(mc.font,"!",x+size-7,y+size-11,0xFFFFA18A,true);
                }
            }
            // The live rebound key is a small corner badge, never a permanent skill-name panel.
            var key=slot==0?ClientCombatKeyMappings.ART_PRIMARY:ClientCombatKeyMappings.ART_SECONDARY;
            String label=mc.font.plainSubstrByWidth(key.getTranslatedKeyMessage().getString(),25);
            float scale=.75F;
            int keyWidth=(int)Math.ceil(mc.font.width(label)*scale);
            g.fill(x+1,y+1,x+keyWidth+4,y+8,0xE0101816);
            g.pose().pushPose();g.pose().translate(x+2,y+1,0);g.pose().scale(scale,scale,1);
            g.drawString(mc.font,label,0,0,0xFFF2E6CB,false);g.pose().popPose();
        }
    }
}
