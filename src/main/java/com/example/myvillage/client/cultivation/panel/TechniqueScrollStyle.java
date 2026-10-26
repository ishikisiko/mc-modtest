package com.example.myvillage.client.cultivation.panel;

/** Exact cosmetic identities only; progression and effect values remain in the growth rules. */
public enum TechniqueScrollStyle {
    BREATHING("basic", "引气归元", "基础心法"),
    QINGMU("qingmu", "木脉长春", "青帝木脉·前篇"),
    YIMU("yimu", "承脉生华", "青帝木脉·承自青木"),
    SWORD("sword", "凝气成锋", "剑诀"),
    UNKNOWN("", "凝神修习", "功法");

    private final String texture, subtitle, lineage;
    TechniqueScrollStyle(String texture, String subtitle, String lineage) {
        this.texture = texture; this.subtitle = subtitle; this.lineage = lineage;
    }
    public String texture() { return texture; }
    public String subtitle() { return subtitle; }
    public String lineage() { return lineage; }
    public static TechniqueScrollStyle forTechnique(String id) {
        if (id == null) return UNKNOWN;
        return switch (id) {
            case "myvillage:basic_breathing" -> BREATHING;
            case "myvillage:qingmu_changchun_gong" -> QINGMU;
            case "myvillage:yimu_shenghua_jue" -> YIMU;
            case "myvillage:jianqi_jue" -> SWORD;
            default -> UNKNOWN;
        };
    }
    public String phase(int phase) {
        int i = Math.floorMod(phase, 3);
        return switch (this) {
            case QINGMU -> new String[] {"纳元", "行脉", "养枝"}[i];
            case YIMU -> new String[] {"承脉", "生华", "养蕊"}[i];
            case SWORD -> new String[] {"聚气", "凝锋", "敛意"}[i];
            default -> new String[] {"引气", "周流", "归元"}[i];
        };
    }
}
