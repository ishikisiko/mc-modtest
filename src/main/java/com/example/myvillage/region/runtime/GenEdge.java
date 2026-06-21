package com.example.myvillage.region.runtime;

/**
 * A typed edge in the generated region graph. Java mirror of {@code GenEdge}
 * in {@code tools/buildgen/region_topology.py}.
 *
 * @param a          one endpoint id ({@code a <= b} by construction)
 * @param b          the other endpoint id
 * @param type       {@link RegionContract#EDGE_LIAN} (连) or {@link RegionContract#EDGE_GE} (隔)
 * @param separator  palette separator, set iff {@code type == 隔}; otherwise {@code null}
 * @param pass       pass label for a walled region's retained 连 edge (关隘), else {@code null}
 */
public record GenEdge(String a, String b, String type, String separator, String pass) {

    /** True iff this edge is a walled region's 关隘. */
    public boolean isPass() {
        return pass != null;
    }
}
