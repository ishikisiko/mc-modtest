package com.example.myvillage.sim.runtime.life;

import com.example.myvillage.courtyard.CourtyardDoorBlock;
import com.example.myvillage.entity.npc.NpcEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 开启当前路线会穿过的整门；门框碰撞继续参与寻路和关门检查。 */
public final class NpcCourtyardDoorGoal extends Goal {
    private final NpcEntity npc;private BlockPos target;private int age;
    public NpcCourtyardDoorGoal(NpcEntity npc){this.npc=npc;}
    @Override public boolean canUse(){
        var path=npc.getNavigation().getPath();if(path==null||path.isDone())return false;
        int end=Math.min(path.getNodeCount()-1,path.getNextNodeIndex()+2);var node=path.getNode(end);
        AABB sweep=npc.getBoundingBox().minmax(npc.getBoundingBox().move(node.x+.5-npc.getX(),node.y-npc.getY(),node.z+.5-npc.getZ()));
        for(var p:BlockPos.betweenClosed(npc.blockPosition().offset(-2,-1,-2),npc.blockPosition().offset(2,2,2))){
            var state=npc.level().getBlockState(p);
            if(!(state.getBlock() instanceof CourtyardDoorBlock)||state.getValue(CourtyardDoorBlock.OPEN))continue;
            if(state.getCollisionShape(npc.level(),p).toAabbs().stream().anyMatch(b->sweep.intersects(b.move(p)))){target=p.immutable();return true;}
        }
        return false;
    }
    @Override public void start(){age=0;var state=npc.level().getBlockState(target);if(state.getBlock() instanceof CourtyardDoorBlock door)door.setOpenByNpc(npc.level(),target,state,npc,true);}
    @Override public boolean canContinueToUse(){return age<200&&target!=null&&npc.level().getBlockState(target).getBlock() instanceof CourtyardDoorBlock;}
    @Override public void tick(){
        age++;if(age<40)return;var state=npc.level().getBlockState(target);
        if(state.getBlock() instanceof CourtyardDoorBlock door&&door.setOpenByNpc(npc.level(),target,state,npc,false))age=200;
    }
    @Override public void stop(){target=null;}
    @Override public boolean requiresUpdateEveryTick(){return true;}
}
