package com.example.myvillage.forging;

import net.minecraft.world.item.ItemStack;
import java.util.*;

/** A shared item pool is allocated across requirements; overlapping tags never double-spend. */
public final class ForgingCosts {
    private ForgingCosts(){}
    public record Reservation(List<ItemStack> remaining,List<ItemStack> consumed) {
        public Reservation { remaining=List.copyOf(remaining);consumed=List.copyOf(consumed); }
    }
    public static Optional<List<ItemStack>> plan(List<ItemStack> inventory,Map<String,Integer> costs){
        return reserve(inventory,costs).map(Reservation::remaining);
    }

    /** Exact withdrawn identities are escrowed by the caller until the operation commits. */
    public static Optional<Reservation> reserve(List<ItemStack> inventory,Map<String,Integer> costs){
        var expanded=new com.example.myvillage.spiritstone.SpiritStoneStorage.Expanded(inventory, Math.toIntExact(costs.values().stream().mapToLong(Integer::longValue).sum()));
        var entries=expanded.entries();
        var requirements=costs.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        int[] counts=entries.stream().mapToInt(ItemStack::getCount).toArray();
        int[] needs=requirements.stream().mapToInt(Map.Entry::getValue).toArray();
        boolean[][] accepts=new boolean[counts.length][needs.length];
        for(int j=0;j<needs.length;j++){
            String reference=ForgingIngredients.validate(requirements.get(j).getKey());
            for(int i=0;i<counts.length;i++)accepts[i][j]=ForgingIngredients.matches(reference,entries.get(i));
        }
        var remaining=allocate(counts,needs,accepts);if(remaining.isEmpty())return Optional.empty();
        var consumed=new ArrayList<ItemStack>();
        int[] left=remaining.get();
        for(int i=0;i<counts.length;i++){
            var entry=entries.get(i);
            for(int spent=counts[i]-left[i];spent>0;){
                int take=Math.min(spent,entry.getMaxStackSize());
                consumed.add(entry.copyWithCount(take));spent-=take;
            }
        }
        return Optional.of(new Reservation(expanded.remaining(left),consumed));
    }

    /** Tiny integral flow graph: stacks -> matching requirements. Residual paths undo greedy choices. */
    static Optional<int[]> allocate(int[] counts,int[] needs,boolean[][] accepts){
        int source=counts.length+needs.length,sink=source+1,size=sink+1;
        int[][] residual=new int[size][size];
        for(int i=0;i<counts.length;i++){
            if(counts[i]<0)throw new IllegalArgumentException("Negative inventory count");
            residual[source][i]=counts[i];
            for(int j=0;j<needs.length;j++)if(accepts[i][j])residual[i][counts.length+j]=counts[i];
        }
        for(int j=0;j<needs.length;j++){
            if(needs[j]<=0)throw new IllegalArgumentException("Nonpositive cost");
            residual[counts.length+j][sink]=needs[j];
        }
        while(true){
            int[] parent=new int[size];Arrays.fill(parent,-1);parent[source]=source;
            var queue=new ArrayDeque<Integer>();queue.add(source);
            while(!queue.isEmpty()&&parent[sink]<0){
                int from=queue.removeFirst();
                for(int to=0;to<size;to++)if(parent[to]<0&&residual[from][to]>0){parent[to]=from;queue.addLast(to);}
            }
            if(parent[sink]<0)break;
            int moved=Integer.MAX_VALUE;
            for(int to=sink;to!=source;to=parent[to])moved=Math.min(moved,residual[parent[to]][to]);
            for(int to=sink;to!=source;to=parent[to]){residual[parent[to]][to]-=moved;residual[to][parent[to]]+=moved;}
        }
        for(int j=0;j<needs.length;j++)if(residual[counts.length+j][sink]>0)return Optional.empty();
        int[] remaining=new int[counts.length];
        for(int i=0;i<counts.length;i++)remaining[i]=residual[source][i];
        return Optional.of(remaining);
    }
}
