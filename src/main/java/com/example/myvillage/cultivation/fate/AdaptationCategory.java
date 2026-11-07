package com.example.myvillage.cultivation.fate;

/** 仅供金刚适应使用，不改变公共伤害/抗性通道。 */
public enum AdaptationCategory {
    PHYSICAL("physical", "一般物理"), SLASH("slash", "斩击"), PIERCE("pierce", "穿刺"), IMPACT("impact", "冲击"),
    METAL("metal", "金法"), WOOD("wood", "木法"), WATER("water", "水法"), FIRE("fire", "火法"), EARTH("earth", "土法"), THUNDER("thunder", "雷击");
    private final String id, label;
    AdaptationCategory(String id, String label) { this.id=id; this.label=label; }
    public String id() { return id; }
    public String label() { return label; }
    public static AdaptationCategory fromId(String id) {
        for (var c : values()) if (c.id.equals(id)) return c;
        throw new IllegalArgumentException("未知适应类别：" + id);
    }
}
