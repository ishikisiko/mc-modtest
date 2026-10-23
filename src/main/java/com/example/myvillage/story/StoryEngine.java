package com.example.myvillage.story;

import java.util.*;

/** Pure transition planner. Caller supplies a verified action and commits its effects before state. */
public final class StoryEngine {
    public static final String RECEIPT="qianzhen_chapter_1_reward";
    public record Plan(boolean changed,StoryProgress progress,String effect,boolean reward) {}
    private StoryEngine() {}
    public static boolean offered(StoryDefinition d,StoryProgress p,StoryDefinition.Action a) {
        return p.chapter().equals(d.id()) && (!p.accepted() || p.definitionVersion()==d.version()) && d.stages().containsKey(p.stage())
                && a.stages().contains(p.stage()) && p.evidence().containsAll(a.requires())
                && (a.evidence().isEmpty()||!p.evidence().contains(a.evidence()))
                && (!a.kind().equals("claim")||!p.receipts().contains(RECEIPT));
    }
    public static Plan reduce(StoryDefinition d,StoryProgress p,String actionId,String kind,String binding,String site,String dimension) {
        var a=d.actions().get(actionId);
        if(a==null||!offered(d,p,a)||!a.kind().equals(kind)
                ||!binding.equals(kind.equals("investigate")?a.target():a.role())
                ||site.isEmpty()||dimension.isEmpty()||p.accepted()&&(!p.site().equals(site)||!p.dimension().equals(dimension)))
            return new Plan(false,p,"",false);
        var evidence=new ArrayList<>(p.evidence());if(!a.evidence().isEmpty())evidence.add(a.evidence());
        var journal=new ArrayList<>(p.journal());if(!a.journal().isEmpty())journal.add(a.journal());
        if(journal.size()>32)journal.remove(0);
        var receipts=new ArrayList<>(p.receipts());boolean reward=a.kind().equals("claim");if(reward)receipts.add(RECEIPT);
        String next=a.next().isEmpty()?p.stage():a.next();var stage=d.stages().get(next);
        while(!stage.autoNext().isEmpty()&&evidence.containsAll(stage.autoRequires())) {next=stage.autoNext();stage=d.stages().get(next);}
        var result=new StoryProgress(1,p.chapter(),d.version(),site,dimension,next,Math.addExact(p.revision(),1),evidence,journal,receipts);
        return new Plan(true,result,a.effect(),reward);
    }
}
