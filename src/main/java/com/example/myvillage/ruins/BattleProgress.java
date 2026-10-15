package com.example.myvillage.ruins;

import java.util.*;

/** Absence is never death. Only a receipt for a bound UUID can finish a guard slot. */
public final class BattleProgress {
    public enum State { DORMANT, READY, ACTIVE, CLEARED, REWARDED }
    private State state;
    private int wave;
    private final List<Integer> sizes;
    private UUID[] guards;
    private boolean[] dead;
    public BattleProgress(List<Integer> sizes, String state, int wave, List<UUID> ids, List<Boolean> deaths) {
        this.sizes=List.copyOf(sizes);
        try {
            this.state=State.valueOf(state); this.wave=wave;
            if (sizes.isEmpty() || sizes.stream().anyMatch(n->n<1 || n>8) || wave<0 || wave>sizes.size()
                    || ((this.state==State.CLEARED || this.state==State.REWARDED) != (wave==sizes.size()))
                    || (this.state==State.READY && wave!=0)) throw new IllegalArgumentException();
            int count=wave<sizes.size()?sizes.get(wave):0;
            guards=new UUID[count]; dead=new boolean[count];
            if (!ids.isEmpty() || !deaths.isEmpty()) {
                if (ids.size()!=count || deaths.size()!=count) throw new IllegalArgumentException();
                var unique=new HashSet<UUID>();
                for(int i=0;i<count;i++) {
                    guards[i]=ids.get(i); dead[i]=deaths.get(i);
                    if (guards[i]!=null && !unique.add(guards[i]) || dead[i] && guards[i]==null) throw new IllegalArgumentException();
                }
            }
            if (this.state==State.READY && Arrays.stream(guards).anyMatch(Objects::nonNull)) throw new IllegalArgumentException();
        } catch (RuntimeException e) { this.state=State.DORMANT; this.wave=0; guards=new UUID[0]; dead=new boolean[0]; }
    }
    public State state(){return state;}
    public int wave(){return wave;}
    public int count(){return guards.length;}
    public UUID guard(int i){return guards[i];}
    public boolean dead(int i){return dead[i];}
    public boolean start(){if(state!=State.READY)return false;state=State.ACTIVE;return true;}
    public boolean bind(int i,UUID id){if(state!=State.ACTIVE || guards[i]!=null || id==null)return false;guards[i]=id;return true;}
    public boolean died(UUID id){if(state!=State.ACTIVE)return false;for(int i=0;i<count();i++)if(id.equals(guards[i]) && !dead[i]){dead[i]=true;return true;}return false;}
    public void retry(int i){if(state==State.ACTIVE && !dead[i])guards[i]=null;}
    public boolean advance(){
        if(state!=State.ACTIVE)return false;
        for(boolean d:dead)if(!d)return false;
        wave++;
        if(wave==sizes.size()){state=State.CLEARED;guards=new UUID[0];dead=new boolean[0];}
        else {guards=new UUID[sizes.get(wave)];dead=new boolean[guards.length];}
        return true;
    }
    public boolean reward(){if(state!=State.CLEARED)return false;state=State.REWARDED;return true;}
}
