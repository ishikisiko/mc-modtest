package com.example.myvillage.worldplan.travel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Bounded candidate ordering, independent of levels, chunks and block mutation. Y is first standing air. */
public final class AtlasCityLanding {
    public record Point(int x, int y, int z) { }
    public record Chunk(int x, int z) { }
    private AtlasCityLanding() { }

    public static List<Point> candidates(Point entry, AtlasCityTravelRules rules) {
        List<Point> columns = new ArrayList<>();
        int radius = rules.searchRadius();
        for (int z = -radius; z <= radius; z++) for (int x = -radius; x <= radius; x++)
            columns.add(new Point(x, 0, z));
        columns.sort(Comparator.comparingInt((Point p) -> p.x()*p.x()+p.z()*p.z())
                .thenComparingInt(Point::z).thenComparingInt(Point::x));
        List<Point> result = new ArrayList<>();
        for (Point column : columns) {
            result.add(new Point(entry.x()+column.x(), entry.y(), entry.z()+column.z()));
            for (int dy = 1; dy <= rules.verticalRadius(); dy++) {
                result.add(new Point(entry.x()+column.x(), entry.y()+dy, entry.z()+column.z()));
                result.add(new Point(entry.x()+column.x(), entry.y()-dy, entry.z()+column.z()));
            }
        }
        return List.copyOf(result);
    }

    public static List<Chunk> chunks(Point entry, AtlasCityTravelRules rules) {
        // One additional block protects collision-shape lookups at the edge of the hazard scan.
        int margin = rules.searchRadius()+rules.hazardPadding()+1;
        List<Chunk> chunks = new ArrayList<>();
        for (int z = Math.floorDiv(entry.z()-margin,16); z <= Math.floorDiv(entry.z()+margin,16); z++)
            for (int x = Math.floorDiv(entry.x()-margin,16); x <= Math.floorDiv(entry.x()+margin,16); x++)
                chunks.add(new Chunk(x,z));
        if (chunks.size() > rules.maxChunksPerRequest()) throw new IllegalArgumentException("入口加载范围超过预算");
        return List.copyOf(chunks);
    }
}
