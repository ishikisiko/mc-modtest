package com.example.myvillage.forging;

/** 只读的台面表现，不携带物品，也不参与扣料或判定。 */
public record WorkshopDisplay(String kind, int stage, boolean hot, long strikeStart) {
    public static final WorkshopDisplay EMPTY = new WorkshopDisplay("", 0, false, -1);
    public static WorkshopDisplay from(WorkshopMenu menu, long now) {
        var stack = menu.input().isEmpty() ? menu.output() : menu.input();
        if (stack.isEmpty()) return EMPTY;
        var piece = menu.piece();
        return new WorkshopDisplay(menu.craft().kind(), piece == null ? 5 : piece.stage(), menu.heat() > 0,
                menu.action() == WorkshopMenu.STRIKE ? now - (menu.duration(WorkshopMenu.STRIKE) - menu.remaining()) : -1);
    }
    /** 从抬锤到触砧，再收锤；接触帧与服务端同用 contact_tick。 */
    public static float hammerAngle(float elapsed, int contact, int duration) {
        if (elapsed < 0 || elapsed >= duration) return 90;
        if (elapsed <= contact) {
            float lift = contact * .4f;
            if (elapsed <= lift) return 90 - 78 * elapsed / lift;
            float t = (elapsed - lift) / (contact - lift);
            return 12 + 78 * t * t;
        }
        float t = (elapsed - contact) / (duration - contact);
        return 90 - 10 * (float)Math.sin(t * Math.PI);
    }
}
