package com.example.myvillage.wilderness.habitat;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** 每月有限名单。卸载不计死亡，月份回拨不补货，跨过多月只创建一轮。 */
public final class HabitatState {
    public static final int VERSION=1;
    public static final class Member {
        public UUID id;public String entity,role;public int slot;public boolean spawned,dead;
        public String activity="rest";
    }
    public static final class Round {
        public long generation=-1,month=-1;public String site;
        public List<Member> members=new ArrayList<>();public Set<Long> construction=new HashSet<>();
        public long kills,alarms;public boolean occupied(){return !construction.isEmpty();}
        public long living(){return members.stream().filter(m->!m.dead).count();}
    }
    public long highestMonth;
    public final Map<String,Round> rounds=new TreeMap<>();
    public Round site(String id){return rounds.computeIfAbsent(id,k->{var r=new Round();r.site=k;return r;});}
    public boolean due(Round round,long month,int months){highestMonth=Math.max(highestMonth,month);return round.generation<0||highestMonth-round.month>=months;}
    public boolean refresh(Round round,long month,int months,boolean safe,HabitatRules.Kind kind,long seed){
        if(!due(round,month,months)||!safe||round.occupied())return false;
        round.generation++;round.month=highestMonth;round.members.clear();
        for(int i=0;i<kind.members().size();i++){
            var m=new Member();m.slot=i;m.entity=kind.members().get(i);m.role=i==0?"leader":i<kind.guards()?"guard":"patrol";
            m.id=UUID.nameUUIDFromBytes((seed+":"+round.site+":"+round.generation+":"+i).getBytes(StandardCharsets.UTF_8));round.members.add(m);
        }return true;
    }
    public boolean current(String site,long generation,UUID id){var r=rounds.get(site);return r!=null&&r.generation==generation&&r.members.stream().anyMatch(m->m.id.equals(id)&&!m.dead);}
    public boolean death(String site,long generation,UUID id){if(!current(site,generation,id))return false;var r=rounds.get(site);r.members.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow().dead=true;r.kills++;return true;}
    public String encode(){var root=new JsonObject();root.addProperty("version",VERSION);root.addProperty("highest_month",highestMonth);root.add("rounds",new Gson().toJsonTree(rounds));return root.toString();}
    public static HabitatState decode(String text){
        var root=JsonParser.parseString(text).getAsJsonObject();if(root.get("version").getAsInt()!=VERSION)throw new IllegalArgumentException("生态存档格式错误");
        var state=new HabitatState();state.highestMonth=root.get("highest_month").getAsLong();if(state.highestMonth<0)throw new IllegalArgumentException("生态月份错误");
        for(var entry:root.getAsJsonObject("rounds").entrySet()){
            Round round=new Gson().fromJson(entry.getValue(),Round.class);
            if(!entry.getKey().equals(round.site)||round.generation< -1||round.month>state.highestMonth||round.members.size()>12||round.construction.size()>4096)throw new IllegalArgumentException("生态据点存档错误");
            Set<UUID> ids=new HashSet<>();for(var member:round.members)if(member.id==null||!ids.add(member.id))throw new IllegalArgumentException("生态成员身份重复");
            state.rounds.put(entry.getKey(),round);
        }return state;
    }
}
