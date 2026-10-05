package com.example.myvillage.entity.beast;

/** An inclusive {@code [first, last]} range of move ticks. */
public record BeastTickRange(int first, int last) {
    public BeastTickRange {
        if (first < 0 || last < first) {
            throw new IllegalArgumentException("Tick range must satisfy 0 <= first <= last, got [" + first + ", " + last + "]");
        }
    }

    public boolean contains(int tick) {
        return tick >= first && tick <= last;
    }
}
