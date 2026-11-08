package com.example.myvillage.sim.sect;

import java.util.UUID;

/** 正式长老考核绑定唯一实体和尝试；死亡回执由服务器交回命簿。 */
public final class ElderAssessment {
    private ElderAssessment() { }
    public record Trial(String attempt, String target, String dimension, int x, int y, int z, long deadline) {
        public Trial {
            UUID.fromString(attempt); UUID.fromString(target);
            if (dimension == null || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || deadline < 0)
                throw new IllegalArgumentException("长老考核记录无效");
        }
        public boolean identity(String attempt, String target) { return this.attempt.equals(attempt) && this.target.equals(target); }
        public boolean matches(String attempt, String target, long now) {
            return identity(attempt, target) && now >= 0 && now < deadline;
        }
        public boolean within(String dimension, double x, double y, double z, int radius) {
            double dx = x - this.x, dy = y - this.y, dz = z - this.z;
            return this.dimension.equals(dimension) && dx*dx + dy*dy + dz*dz <= (double) radius*radius;
        }
    }
}
