package com.example.myvillage.wilderness.habitat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import java.util.EnumSet;

/** 巡猎、留守与有实际去向的撤退；战斗招式仍由原实体执行。 */
final class HabitatGoal extends Goal {
    private final Mob mob;
    private long nextMove;
    HabitatGoal(Mob mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){
        var context=HabitatRuntime.member(mob);if(context==null)return false;
        double distance=Math.hypot(mob.getX()-context.site().x(),mob.getZ()-context.site().z());
        double fraction=context.member().role.equals("leader")?context.rules().number("leader_retreat_fraction"):context.rules().number("retreat_health_fraction");
        return !com.example.myvillage.wilderness.runtime.WildernessRuntime.regionAt(context.level(),mob.blockPosition()).filter(context.rules().region::equals).isPresent()||mob.getTarget()==null||mob.getHealth()<mob.getMaxHealth()*fraction||distance>context.rules().integer("pursuit_radius");
    }
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public void tick(){
        var c=HabitatRuntime.member(mob);if(c==null)return;
        long now=mob.level().getGameTime();
        double distance=Math.hypot(mob.getX()-c.site().x(),mob.getZ()-c.site().z());
        double fraction=c.member().role.equals("leader")?c.rules().number("leader_retreat_fraction"):c.rules().number("retreat_health_fraction");
        boolean retreat=!com.example.myvillage.wilderness.runtime.WildernessRuntime.regionAt(c.level(),mob.blockPosition()).filter(c.rules().region::equals).isPresent()||mob.getHealth()<mob.getMaxHealth()*fraction||distance>c.rules().integer("pursuit_radius");
        if(retreat){mob.setTarget(null);c.member().activity="retreat";}
        if(now<nextMove)return;nextMove=now+c.rules().integer("navigation_interval_ticks");
        boolean night=mob.level().isNight();boolean guard=!c.member().role.equals("patrol")||(c.kind().nightHunt()&&!night);
        int radius=retreat||guard?c.rules().integer("guard_radius"):c.rules().integer("patrol_radius");
        double angle=(now/(double)c.rules().integer("route_step_ticks")+c.member().slot)*Math.PI/2;
        int x=c.site().x()+(int)Math.round(Math.cos(angle)*radius),z=c.site().z()+(int)Math.round(Math.sin(angle)*radius);
        if(HabitatRuntime.protectedConstruction(c.round(),c.rules(),x,z))return;
        var destination=HabitatRuntime.ground((ServerLevel)mob.level(),x,z,mob);
        if(destination!=null)mob.getNavigation().moveTo(destination.getX()+.5,destination.getY(),destination.getZ()+.5,c.rules().number("navigation_speed"));
        if(!retreat)c.member().activity=guard?"guard":c.kind().faction().equals("bandit")?"patrol":"hunt";
    }
    @Override public void stop(){mob.getNavigation().stop();}
}
