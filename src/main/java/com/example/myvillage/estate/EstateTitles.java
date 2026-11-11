package com.example.myvillage.estate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Server-thread transaction: a sold title never calls the payment callback again. */
public final class EstateTitles {
    public record Title(UUID owner, String name) { }
    public enum Result { PURCHASED, ALREADY_OWNER, SOLD, PAYMENT_FAILED }
    private final Map<String, Title> titles = new HashMap<>();
    public Title get(String id) { return titles.get(id); }
    public Map<String, Title> snapshot() { return Map.copyOf(titles); }
    void restore(String id, Title title) {
        if (!id.matches("capital_[a-z0-9_]+/[a-z0-9_]{1,64}") || title.name().length() > 64
                || titles.putIfAbsent(id, title) != null) throw new IllegalArgumentException("重复或无效房契");
    }
    public Result purchase(String id, UUID player, String name, BooleanSupplier pay) {
        Title old = get(id);
        if (old != null) return old.owner().equals(player) ? Result.ALREADY_OWNER : Result.SOLD;
        if (!pay.getAsBoolean()) return Result.PAYMENT_FAILED;
        titles.put(id, new Title(player, name));
        return Result.PURCHASED;
    }
}
