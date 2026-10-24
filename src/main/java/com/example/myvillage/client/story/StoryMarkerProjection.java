package com.example.myvillage.client.story;

/** Pure clip-space to GUI-space geometry. Uses the actual rendered camera/FOV, never a guessed yaw. */
public final class StoryMarkerProjection {
    private StoryMarkerProjection() { }
    public record Point(double x, double y, boolean edge, double directionX, double directionY) { }

    /** Returns null for invalid matrices/viewport; rear-centre deterministically points down. */
    public static Point project(double clipX, double clipY, double clipW, int width, int height) {
        if (!Double.isFinite(clipX) || !Double.isFinite(clipY) || !Double.isFinite(clipW)
                || width < 160 || height < 160) return null;
        double cx = width / 2.0, cy = height / 2.0;
        double divisor = Math.max(Math.abs(clipW), 1e-6);
        double dx = clipX / divisor * cx, dy = -clipY / divisor * cy;
        // A centred target behind the camera has no horizontal bearing; keep one stable lower cue.
        if (clipW <= 0 && Math.hypot(dx, dy) < 1) { dx = 0; dy = height; }
        double x = cx + dx, y = cy + dy;
        double left = 24, right = width - 24, top = 24, bottom = height - 64;
        if (clipW > 0 && x >= left && x <= right && y >= top && y <= bottom)
            return new Point(x, y, false, 0, 1);
        // Intersect the bearing ray with a bounded ellipse inside the HUD-safe rectangle.
        double rx = (right - left) / 2, ry = (bottom - top) / 2;
        double edgeCy = (top + bottom) / 2;
        double norm = Math.hypot(dx / rx, dy / ry);
        if (!Double.isFinite(norm) || norm < 1e-9) return null;
        double length = Math.hypot(dx, dy);
        return new Point(cx + dx / norm, edgeCy + dy / norm, true, dx / length, dy / length);
    }
}
