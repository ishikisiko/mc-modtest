package com.example.myvillage.client.cultivation.panel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A channel route smoothed into a polyline and measured by arc length, in the chart's
 * normalised square. Drawing samples it for the channel stroke; flowing qi asks for the point
 * at a fraction of its length.
 */
public final class MeridianPath {
    /** Samples per route segment; enough for smooth curves at the panel's size. */
    static final int SAMPLES_PER_SEGMENT = 8;

    private final float[] xs;
    private final float[] ys;
    private final float[] distances;
    private final float[] stopFractions;
    private final List<Optional<String>> stopIds;

    private MeridianPath(float[] xs, float[] ys, float[] stopFractions, List<Optional<String>> stopIds) {
        this.stopIds = List.copyOf(stopIds);
        this.xs = xs;
        this.ys = ys;
        this.distances = new float[xs.length];
        for (int index = 1; index < xs.length; index++) {
            distances[index] = distances[index - 1]
                    + (float) Math.hypot(xs[index] - xs[index - 1], ys[index] - ys[index - 1]);
        }
        float length = distances[distances.length - 1];
        this.stopFractions = new float[stopFractions.length];
        for (int index = 0; index < stopFractions.length; index++) {
            int sample = (int) stopFractions[index];
            this.stopFractions[index] = length <= 0.0F ? 0.0F : distances[sample] / length;
        }
    }

    /** Smooths one channel's stops with a Catmull-Rom spline that passes through every stop. */
    public static MeridianPath of(MeridianChart.Channel channel) {
        return of(List.of(channel));
    }

    /**
     * Joins channels end to start into one path, smoothing across the joins; when the last
     * channel ends where the first starts, the path is treated as a closed loop.
     */
    public static MeridianPath of(List<MeridianChart.Channel> channels) {
        List<float[]> stops = new ArrayList<>();
        List<Optional<String>> ids = new ArrayList<>();
        for (MeridianChart.Channel channel : channels) {
            List<MeridianChart.Stop> route = channel.stops();
            for (int index = 0; index < route.size(); index++) {
                if (!stops.isEmpty() && index == 0) {
                    continue; // the previous channel ended on this stop
                }
                stops.add(new float[] {route.get(index).x(), route.get(index).y()});
                ids.add(route.get(index).acupoint());
            }
        }
        float[] first = stops.get(0);
        float[] last = stops.get(stops.size() - 1);
        boolean closed = stops.size() > 2 && first[0] == last[0] && first[1] == last[1];
        return smooth(stops, ids, closed);
    }

    private static MeridianPath smooth(List<float[]> stops, List<Optional<String>> ids, boolean closed) {
        int count = stops.size();
        int segments = count - 1;
        float[] xs = new float[segments * SAMPLES_PER_SEGMENT + 1];
        float[] ys = new float[xs.length];
        float[] stopSamples = new float[count];
        for (int segment = 0; segment < segments; segment++) {
            float[] p0 = stop(stops, segment - 1, closed);
            float[] p1 = stops.get(segment);
            float[] p2 = stops.get(segment + 1);
            float[] p3 = stop(stops, segment + 2, closed);
            stopSamples[segment] = segment * SAMPLES_PER_SEGMENT;
            for (int step = 0; step < SAMPLES_PER_SEGMENT; step++) {
                float t = step / (float) SAMPLES_PER_SEGMENT;
                int sample = segment * SAMPLES_PER_SEGMENT + step;
                xs[sample] = catmullRom(p0[0], p1[0], p2[0], p3[0], t);
                ys[sample] = catmullRom(p0[1], p1[1], p2[1], p3[1], t);
            }
        }
        xs[xs.length - 1] = stops.get(count - 1)[0];
        ys[ys.length - 1] = stops.get(count - 1)[1];
        stopSamples[count - 1] = xs.length - 1;
        return new MeridianPath(xs, ys, stopSamples, ids);
    }

    /** The stop at {@code index}, wrapping on a closed loop and repeating the end on an open one. */
    private static float[] stop(List<float[]> stops, int index, boolean closed) {
        int count = stops.size();
        if (closed) {
            // the last stop repeats the first, so the loop has count - 1 distinct stops
            int distinct = count - 1;
            return stops.get(Math.floorMod(index, distinct));
        }
        return stops.get(Math.max(0, Math.min(count - 1, index)));
    }

    private static float catmullRom(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (2.0F * p1
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3);
    }

    public int size() {
        return xs.length;
    }

    public float x(int sample) {
        return xs[sample];
    }

    public float y(int sample) {
        return ys[sample];
    }

    public float length() {
        return distances[distances.length - 1];
    }

    /** Arc-length fraction (0..1) of the path at which its {@code index}-th route stop lies. */
    public float stopFraction(int index) {
        return stopFractions[index];
    }

    /** Arc-length fraction of the first stop on {@code acupoint}, if the path passes it. */
    public Optional<Float> fractionOf(String acupoint) {
        for (int index = 0; index < stopIds.size(); index++) {
            if (stopIds.get(index).filter(acupoint::equals).isPresent()) {
                return Optional.of(stopFractions[index]);
            }
        }
        return Optional.empty();
    }

    /** The sample index at or just before {@code fraction} of the length. */
    public int sampleAt(double fraction) {
        double target = clamp(fraction) * length();
        int low = 0;
        int high = distances.length - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (distances[middle] <= target) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    /** The point at {@code fraction} (clamped to 0..1) of the path's length, as {x, y}. */
    public float[] pointAt(double fraction) {
        double target = clamp(fraction) * length();
        int sample = sampleAt(fraction);
        if (sample >= distances.length - 1) {
            return new float[] {xs[xs.length - 1], ys[ys.length - 1]};
        }
        float span = distances[sample + 1] - distances[sample];
        float t = span <= 0.0F ? 0.0F : (float) ((target - distances[sample]) / span);
        return new float[] {
            xs[sample] + (xs[sample + 1] - xs[sample]) * t,
            ys[sample] + (ys[sample + 1] - ys[sample]) * t
        };
    }

    private static double clamp(double fraction) {
        return Math.max(0.0D, Math.min(1.0D, fraction));
    }
}
