package com.example.myvillage.story;

import java.util.ArrayList;
import java.util.Set;
import java.util.function.Predicate;

/** Pure, bounded receipts for the apprentice opening. Inventory is never evidence of work. */
public final class StoryOpening {
    public static final String BREATH="opening_breath_settled";
    public static final String EXPERIENCED="opening_experienced";
    public static final String SUPPLIES="opening_supplies";
    private static final String PLANTED="opening_sown_";
    public static final Set<String> FACT_EFFECTS=Set.of("verify_root","verify_inheritance","verify_breath","verify_work");
    /** Snapshot of server-owned facts. Reading them never grants cultivation, supplies or pay. */
    public record Facts(boolean root,boolean fate,boolean learned) {}
    /** Tick-local witness: a matching final block is insufficient after another successful mutation. */
    public static final class PlantWitness<E> {
        private final E placement;
        private final ArrayList<E> laterChanges=new ArrayList<>();
        public PlantWitness(E placement){this.placement=placement;}
        public void changedAfter(E event){laterChanges.add(event);}
        // Cancellation is read after every event listener and vanilla rollback have finished.
        public boolean intact(Predicate<E> canceled) {
            return !canceled.test(placement)&&laterChanges.stream().allMatch(canceled);
        }
    }
    private StoryOpening() {}

    /** Accepted v1 records retain their exact definition, evidence and pending reward. */
    public static StoryDefinition definition(StoryProgress p,StoryDefinition legacy,StoryDefinition opening) {
        return !p.accepted()||p.definitionVersion()==opening.version()?opening:legacy;
    }
    public static boolean active(StoryProgress p) {return p.chapter().equals(StoryProgress.CHAPTER)&&p.accepted()&&p.definitionVersion()==2&&!p.completed();}
    public static long planted(StoryProgress p) {return p.receipts().stream().filter(s->s.startsWith(PLANTED)).count();}
    public static String plantingReceipt(long packedPosition) {return PLANTED+Long.toUnsignedString(packedPosition,36);}
    public static boolean breathingDone(StoryProgress p) {return p.receipts().contains(BREATH)||p.receipts().contains(EXPERIENCED);}
    /** Stability alone can be initialized/adjusted without a real cultivation settlement. */
    public static boolean experienced(boolean root,boolean nonMortal,long cultivation,long breathingMastery) {
        return root&&(nonMortal||cultivation>0||breathingMastery>0);
    }
    /**
     * Server-only fact edges, at most four per call. No action reducer, inventory effect or reward path.
     * Definition validation fixes their sources/destinations and forbids cycles/effects beyond these facts.
     */
    public static StoryProgress reconcile(StoryDefinition d,StoryProgress p,Facts facts) {
        if(!active(p)||d.version()!=p.definitionVersion()||!d.work().enabled())return p;
        for(int step=0;step<4;step++) {
            final var before=p;
            var edge=d.actions().values().stream().filter(a->a.kind().equals("fact")&&a.stages().contains(before.stage())).findFirst().orElse(null);
            if(edge==null||!FACT_EFFECTS.contains(edge.effect())
                    ||!allowed(edge.effect(),p,facts.root(),facts.fate(),facts.learned(),d.work().count()))break;
            var journal=new ArrayList<>(p.journal());
            if(!edge.journal().isEmpty())journal.add(edge.journal());
            if(journal.size()>32)journal.remove(0);
            p=new StoryProgress(p.schema(),p.chapter(),p.definitionVersion(),p.site(),p.dimension(),edge.next(),
                    Math.addExact(p.revision(),1),p.evidence(),journal,p.receipts());
        }
        return p;
    }
    public static boolean allowed(String effect,StoryProgress p,boolean root,boolean fate,boolean learned,int requiredPlantings) {
        return switch(effect) {
            case "verify_root" -> root&&fate;
            case "verify_inheritance" -> root&&learned;
            case "verify_breath" -> breathingDone(p);
            case "supply_work" -> !p.receipts().contains(SUPPLIES);
            case "verify_work" -> p.receipts().contains(SUPPLIES)&&requiredPlantings>0&&planted(p)>=requiredPlantings;
            default -> true;
        };
    }
    /** Called only after a verified server event, never from a client intent or a tick estimate. */
    public static StoryProgress recordBreath(StoryProgress p) {
        return active(p)&&p.stage().equals("first_breath")?receipt(p,BREATH):p;
    }
    public static StoryProgress recordPlant(StoryProgress p,long position,int required) {
        if(!active(p)||!p.stage().equals("planting")||!p.receipts().contains(SUPPLIES)||planted(p)>=required)return p;
        return receipt(p,plantingReceipt(position));
    }
    public static StoryProgress receipt(StoryProgress p,String id) {
        if(p.receipts().contains(id))return p;
        if(!StoryProgress.id(id)||p.receipts().size()>=32)throw new IllegalArgumentException("opening receipt bound");
        var receipts=new ArrayList<>(p.receipts());receipts.add(id);
        return new StoryProgress(p.schema(),p.chapter(),p.definitionVersion(),p.site(),p.dimension(),p.stage(),
                Math.addExact(p.revision(),1),p.evidence(),p.journal(),receipts);
    }
}
