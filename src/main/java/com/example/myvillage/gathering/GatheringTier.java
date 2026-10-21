package com.example.myvillage.gathering;

/** Explicit registry identities; the original array keeps its existing IDs. */
public enum GatheringTier {
    LOW("low", "四方聚灵阵", "spirit_gathering_core", "spirit_gathering_node"),
    MEDIUM("medium", "中品聚灵阵", "medium_grade_gathering_core", "medium_grade_gathering_node"),
    HIGH("high", "上品聚灵阵", "high_grade_gathering_core", "high_grade_gathering_node");
    public final String id, label, coreId, nodeId;
    GatheringTier(String id, String label, String coreId, String nodeId) {
        this.id = id; this.label = label; this.coreId = coreId; this.nodeId = nodeId;
    }
    public static GatheringTier fromWire(int value) {
        return value >= 0 && value < values().length ? values()[value] : LOW;
    }
}
