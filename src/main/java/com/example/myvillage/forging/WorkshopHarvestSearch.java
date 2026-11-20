package com.example.myvillage.forging;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;

final class WorkshopHarvestSearch {
    private static final int MAX_TREE_NODES = 2048;
    private static final int MAX_TREE_SPAN = 48;
    private static final Comparator<BlockPos> BY_POSITION = Comparator.<BlockPos>comparingInt(BlockPos::getY)
            .thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ);
    private static final List<BlockPos> NEIGHBORS = neighbors();

    private WorkshopHarvestSearch() {}

    private static List<BlockPos> neighbors() {
        var offsets = new ArrayList<BlockPos>();
        for (BlockPos offset : BlockPos.betweenClosed(-1,-1,-1,1,1,1))
            if (!offset.equals(BlockPos.ZERO)) offsets.add(offset.immutable());
        offsets.sort(Comparator.<BlockPos>comparingDouble(pos -> pos.distSqr(BlockPos.ZERO)).thenComparing(BY_POSITION));
        return List.copyOf(offsets);
    }

    /** Each successful break extends the search. A protected or changed block cannot bridge it. */
    static int mine(BlockPos origin, WorkshopChainConfig.PickaxeLimits limits, Predicate<BlockPos> harvest) {
        var seen = new HashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        seen.add(origin);
        queue.add(origin);
        int count = 1;
        int radiusSquared = limits.radius()*limits.radius();
        while (!queue.isEmpty() && count < limits.count()) {
            BlockPos pos = queue.removeFirst();
            for (BlockPos offset : NEIGHBORS) {
                BlockPos next = pos.offset(offset);
                if (next.distSqr(origin)>radiusSquared || !seen.add(next) || !harvest.test(next)) continue;
                if (++count == limits.count()) return count;
                queue.addLast(next);
            }
        }
        return count;
    }

    record TreePlan(List<BlockPos> roots, List<BlockPos> logs) {
        private static final TreePlan EMPTY = new TreePlan(List.of(), List.of());
    }

    /** Separate touching crowns by distance to naturally rooted trunks, including 2x2 trunks. */
    static TreePlan tree(BlockPos origin, Predicate<BlockPos> wood, Predicate<BlockPos> rooted,
                               Predicate<BlockPos> crown, boolean thickTrunks) {
        Set<BlockPos> logs = connectedWood(origin,wood);
        if (logs.isEmpty()) return TreePlan.EMPTY;
        Map<BlockPos,Ownership> owners = assignOwners(logs,rooted,thickTrunks);
        var owner = owners.get(origin);
        if (owner == null || owner.root()<0) return TreePlan.EMPTY;
        var tree = logs.stream().filter(pos -> owners.get(pos).root()==owner.root()).sorted(BY_POSITION).toList();
        if (tree.stream().noneMatch(crown)) return TreePlan.EMPTY;
        List<BlockPos> roots = tree.stream().filter(pos -> owners.get(pos).distance()==0).toList();
        return new TreePlan(roots,tree.stream().filter(pos -> !pos.equals(origin)).toList());
    }

    private static Set<BlockPos> connectedWood(BlockPos origin, Predicate<BlockPos> wood) {
        var logs = new LinkedHashSet<BlockPos>();
        var queue = new ArrayDeque<BlockPos>();
        logs.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            for (BlockPos offset : NEIGHBORS) {
                BlockPos next = pos.offset(offset);
                if (logs.contains(next) || !wood.test(next)) continue;
                if (logs.size() == MAX_TREE_NODES || Math.abs(next.getX()-origin.getX())>MAX_TREE_SPAN
                        || Math.abs(next.getY()-origin.getY())>MAX_TREE_SPAN
                        || Math.abs(next.getZ()-origin.getZ())>MAX_TREE_SPAN) return Set.of();
                logs.add(next);
                queue.addLast(next);
            }
        }
        return logs;
    }

    private record Ownership(int root, int distance) {}

    private static Map<BlockPos,Ownership> assignOwners(Set<BlockPos> logs, Predicate<BlockPos> rooted,
                                                         boolean thickTrunks) {
        List<BlockPos> roots = logs.stream().filter(rooted).sorted(BY_POSITION).toList();
        var rootPositions = new HashSet<>(roots);
        var owners = new HashMap<BlockPos,Ownership>();
        var queue = new ArrayDeque<BlockPos>();
        int root = 0;
        for (BlockPos pos : roots) {
            if (owners.containsKey(pos)) continue;
            int owner = root++;
            List<BlockPos> thickTrunk = List.of(pos,pos.east(),pos.south(),pos.east().south());
            if (thickTrunks && rootPositions.containsAll(thickTrunk) && thickTrunk.stream().noneMatch(owners::containsKey)) {
                for (BlockPos corner : thickTrunk) {
                    owners.put(corner,new Ownership(owner,0));
                    queue.addLast(corner);
                }
            } else {
                owners.put(pos,new Ownership(owner,0));
                queue.addLast(pos);
            }
        }
        while (!queue.isEmpty()) {
            BlockPos pos = queue.removeFirst();
            var owner = owners.get(pos);
            int distance = owner.distance()+1;
            for (BlockPos offset : NEIGHBORS) {
                BlockPos next = pos.offset(offset);
                if (!logs.contains(next)) continue;
                var other = owners.get(next);
                if (other == null) {
                    owners.put(next,new Ownership(owner.root(),distance));
                    queue.addLast(next);
                } else if (other.distance()==distance && other.root()!=owner.root()) {
                    owners.put(next,new Ownership(-1,distance));
                }
            }
        }
        return owners;
    }
}
