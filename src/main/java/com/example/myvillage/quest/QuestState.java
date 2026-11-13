package com.example.myvillage.quest;

import com.example.myvillage.stats.RealmKey;
import com.example.myvillage.wilderness.WildernessRules;
import com.google.gson.Gson;
import java.util.*;

/** Saved state owns admission, frozen assignments and reward receipts; never player cultivation. */
public final class QuestState {
    private static final Gson JSON=new Gson();
    public int schema=1;
    public Map<UUID,Instance> instances=new LinkedHashMap<>();
    public Map<UUID,Member> members=new LinkedHashMap<>();
    public static final class Place {
        public String dimension;public double x,y,z;public float yaw,pitch;
        public Place(String d,double x,double y,double z,float yaw,float pitch){this.dimension=d;this.x=x;this.y=y;this.z=z;this.yaw=yaw;this.pitch=pitch;}
        public boolean valid(){return dimension!=null&&dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")&&Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)&&Math.abs(x)<30_000_000&&Math.abs(z)<30_000_000&&y>=-2048&&y<=2048&&Float.isFinite(yaw)&&Float.isFinite(pitch);}
    }
    public static final class Instance {
        public UUID id,leader;public String map,site,sourceName,region,hash,state="FORMING";
        public long seed,expiresAt,lightingAt;
        public int buildRun,buildOffset,lightMinimum=-1;
        public List<UUID> party=new ArrayList<>();public List<Slot> slots=new ArrayList<>();
        public Map<String,Encounter> encounters=new LinkedHashMap<>();
    }
    public static final class Encounter {public String state="WAITING";public int notice;}
    public static final class Slot {
        public UUID id;public String group,realm;public int stage,index;public boolean spawned,dead;
        public Slot(UUID id,String group,int index,RealmKey realm){this.id=id;this.group=group;this.index=index;this.realm=realm.realmId();this.stage=realm.stage();}
        public RealmKey key(){return new RealmKey(realm,stage);}
    }
    public static final class Member {
        public UUID instance;public Place outside,inside;public String result="ACTIVE";
        public int rescues,stones,reputation;public boolean participated,pendingReward,claiming,claimed;
    }
    public static String dimension(UUID id){return "myvillage:expedition/"+id;}
    public static Optional<UUID> identity(String key){
        if(!key.startsWith("myvillage:expedition/"))return Optional.empty();
        try{return Optional.of(UUID.fromString(key.substring("myvillage:expedition/".length())));}catch(IllegalArgumentException ignored){return Optional.empty();}
    }
    public static boolean reserved(String key){return key.equals("myvillage:expedition_template")||key.startsWith("myvillage:expedition/");}
    public Instance issue(UUID leader,String map,String site,String sourceName,String region,long seed,WildernessRules.RealmRange range,QuestMap definition,String hash){
        var existing=members.get(leader);
        if(existing!=null&&(existing.pendingReward||existing.claiming||instances.containsKey(existing.instance)&&existing.result.equals("ACTIVE")))throw new IllegalArgumentException("你还有未结束的远征或待领报酬。");
        var run=new Instance();run.id=UUID.randomUUID();run.leader=leader;run.map=map;run.site=site;run.sourceName=sourceName;run.region=region;run.seed=seed;run.hash=hash;run.party.add(leader);
        for(var group:definition.groups()){
            run.encounters.put(group.id(),new Encounter());
            for(int i=0;i<group.actors().size();i++){
                var actor=group.actors().get(i);UUID uuid=UUID.randomUUID();
                RealmKey key=actor.boss()?range.max():range.sample(seed,region,uuid.getLeastSignificantBits());
                run.slots.add(new Slot(uuid,group.id(),i,key));
            }
        }
        instances.put(run.id,run);var member=new Member();member.instance=run.id;members.put(leader,member);return run;
    }
    public boolean killed(UUID instance,UUID guard){
        var run=instances.get(instance);if(run==null)return false;
        for(var slot:run.slots)if(slot.id.equals(guard)&&slot.spawned&&!slot.dead){slot.dead=true;return true;}return false;
    }
    public boolean goal(Instance run,QuestMap map){return map.groups().stream().filter(QuestMap.Group::required).allMatch(g->run.slots.stream().filter(s->s.group.equals(g.id())).allMatch(s->s.dead));}
    public String encode(){return JSON.toJson(this);}
    public static QuestState decode(String value){var result=JSON.fromJson(value,QuestState.class);result.validate();return result;}
    public void validate(){
        if(schema!=1||instances==null||members==null)throw new IllegalStateException("远征存档版本无效");
        var guards=new HashSet<UUID>();
        for(var entry:instances.entrySet()){
            var r=entry.getValue();
            if(r==null||!entry.getKey().equals(r.id)||r.leader==null||r.map==null||r.region==null||r.site==null||r.sourceName==null||r.hash==null||!r.hash.matches("[a-f0-9]{64}")||r.party==null||r.party.isEmpty()||r.party.size()>4||!r.party.contains(r.leader)||new HashSet<>(r.party).size()!=r.party.size()||r.slots==null||r.encounters==null||r.buildRun<0||r.buildOffset<0||r.expiresAt<0)
                throw new IllegalStateException("远征身份或成员无效");
            if(!Set.of("FORMING","BUILDING","LIGHTING","READY","RUNNING","SUSPENDED","GOAL","FAILED","RECLAIMING").contains(r.state))throw new IllegalStateException("远征状态无效");
            var map=QuestMap.get(r.map);var region=WildernessRules.bundled().region(r.region).orElseThrow(()->new IllegalStateException("远征签发地区无效"));
            var range=map.realmPool().equals("rogue")?region.rogue():region.beast();
            if(r.slots.size()!=map.groups().stream().mapToInt(g->g.actors().size()).sum()||r.encounters.size()!=map.groups().size())throw new IllegalStateException("远征守敌名单不完整");
            var positions=new HashSet<String>();
            for(var slot:r.slots){
                if(slot==null||slot.id==null||slot.group==null||slot.index<0||!guards.add(slot.id)||!r.encounters.containsKey(slot.group)||slot.dead&&!slot.spawned)throw new IllegalStateException("远征守敌身份无效");
                var group=map.groups().stream().filter(g->g.id().equals(slot.group)).findFirst().orElseThrow(()->new IllegalStateException("远征守敌分组无效"));
                if(slot.index>=group.actors().size()||!positions.add(slot.group+":"+slot.index)||!range.contains(slot.key())||group.actors().get(slot.index).boss()&&!slot.key().equals(range.max()))throw new IllegalStateException("远征守敌位置或境界无效");
            }
            for(var e:r.encounters.values())if(e==null||!Set.of("WAITING","NOTICE","ACTIVE","DONE").contains(e.state))throw new IllegalStateException("远征遭遇状态无效");
        }
        for(var e:members.entrySet()){
            var m=e.getValue();if(e.getKey()==null||m==null||m.instance==null||m.rescues<0||m.stones<0||m.reputation<0||m.outside!=null&&(!m.outside.valid()||reserved(m.outside.dimension))||m.inside!=null&&!m.inside.valid())throw new IllegalStateException("远征玩家记录无效");
            if(!Set.of("ACTIVE","SUCCESS","FAILED","ABANDONED").contains(m.result)||m.claimed&&(m.pendingReward||m.claiming)||m.claiming&&(!m.pendingReward||!m.result.equals("SUCCESS")))throw new IllegalStateException("远征回执无效");
        }
    }
}
