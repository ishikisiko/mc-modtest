package com.example.myvillage.sim.commerce;

import com.example.myvillage.sim.SimDataException;
import com.google.gson.JsonObject;
import java.util.*;

/** Authored commercial offers. Money is the existing site's treasury, not another wallet. */
public record CommerceRules(String currency, int stockLimit, int interactionRange, int stationRange,
        int restCooldownTicks, int restSafeTicks, int actionIntervalTicks, double restHealthFraction, double restQiFraction,
        List<Offer> offers, List<Staff> staff) {
    public static final int MAX_OFFERS = 32;
    public static final Set<String> ROLES = Set.of("herbalist", "smith", "innkeeper");
    public record Offer(String id, String role, String kind, String item, int quantity, int price,
                        String pool, int units, int initialStock, int capacity, int medicineCost) { }
    public record Staff(String role, String home, String work) { }
    public CommerceRules { offers=List.copyOf(offers); staff=List.copyOf(staff); }
    /** Old pure simulation fixtures deliberately have no new economy. */
    public static CommerceRules disabled() { return new CommerceRules("myvillage:low_grade_spirit_stone",1,4,3,1200,100,4,.25,.25,List.of(),List.of()); }
    public List<Offer> offers(String role) { return offers.stream().filter(o->o.role().equals(role)).toList(); }
    public Offer offer(String id) { return offers.stream().filter(o->o.id().equals(id)).findFirst().orElse(null); }
    /** Herb offers share one warehouse; absent offers keep the legacy life limit. */
    public int herbCapacity(int fallback) {
        for (Offer offer : offers) if (offer.pool().equals("herbs")) return offer.capacity();
        return fallback;
    }
    public static CommerceRules parse(String file, JsonObject root) {
        try {
            fields(root,"schema","currency","stock_limit","interaction_range","station_range","rest_cooldown_ticks",
                    "rest_safe_ticks","action_interval_ticks","rest_health_fraction","rest_qi_fraction","offers","staff");
            integer(root,"schema",1,1);
            int limit=integer(root,"stock_limit",1,20_000);
            List<Offer> offers=new ArrayList<>(); Set<String> ids=new HashSet<>();
            for(var value:root.getAsJsonArray("offers")) {
                JsonObject o=value.getAsJsonObject();
                fields(o,"id","role","kind","item","quantity","price","pool","units","initial_stock","capacity","medicine_cost");
                String id=identifier(o,"id"),role=text(o,"role"),kind=text(o,"kind"),pool=text(o,"pool");
                if(!ids.add(id)||!ROLES.contains(role)||!Set.of("buy","sell","rest").contains(kind)
                        ||!Set.of("items","herbs","service").contains(pool)) throw new IllegalArgumentException("Invalid offer "+id);
                int quantity=integer(o,"quantity",0,64),units=integer(o,"units",0,64),
                        capacity=integer(o,"capacity",1,limit),initial=integer(o,"initial_stock",0,capacity),medicine=integer(o,"medicine_cost",0,64);
                String item=resource(o,"item");
                if(kind.equals("rest")) {
                    if(!pool.equals("service")||!role.equals("innkeeper")||quantity!=0||units!=0||initial!=0||medicine!=0||!item.equals("minecraft:air"))
                        throw new IllegalArgumentException("Invalid rest service");
                } else if(quantity<1||units!=quantity||pool.equals("service")||item.equals("minecraft:air")
                        ||pool.equals("herbs")&&(!kind.equals("sell")||!role.equals("herbalist")||initial!=0||medicine!=0)
                        ||medicine>0&&(!kind.equals("buy")||!role.equals("herbalist"))) throw new IllegalArgumentException("Invalid commodity");
                offers.add(new Offer(id,role,kind,item,quantity,integer(o,"price",1,10_000),pool,units,initial,capacity,medicine));
            }
            if(offers.isEmpty()||offers.size()>MAX_OFFERS) throw new IllegalArgumentException("Offer limit");
            // One warehouse entry per item; buy/sell definitions must agree about its bounds and startup stock.
            Map<String,Offer> stocks=new HashMap<>();
            Integer herbCapacity=null;
            for(Offer o:offers) if(o.pool().equals("items")) {
                Offer old=stocks.putIfAbsent(o.item(),o);
                if(old!=null&&(old.initialStock()!=o.initialStock()||old.capacity()!=o.capacity()))
                    throw new IllegalArgumentException("Inconsistent stock "+o.item());
            } else if(o.pool().equals("herbs")) {
                if(herbCapacity!=null&&herbCapacity!=o.capacity())
                    throw new IllegalArgumentException("Inconsistent herb capacity "+o.item());
                herbCapacity=o.capacity();
            }
            List<Staff> staff=new ArrayList<>(); Set<String> roles=new HashSet<>();
            for(var value:root.getAsJsonArray("staff")) {
                var o=value.getAsJsonObject();fields(o,"role","home","work");
                String role=text(o,"role");
                if(!ROLES.contains(role)||!roles.add(role)) throw new IllegalArgumentException("Invalid staff role");
                staff.add(new Staff(role,identifier(o,"home"),identifier(o,"work")));
            }
            String currency=resource(root,"currency");
            if(currency.equals("minecraft:air"))throw new IllegalArgumentException("Invalid currency");
            return new CommerceRules(currency,limit,integer(root,"interaction_range",2,8),
                    integer(root,"station_range",1,8),integer(root,"rest_cooldown_ticks",200,240_000),
                    integer(root,"rest_safe_ticks",0,2400),integer(root,"action_interval_ticks",1,40),decimal(root,"rest_health_fraction",0,1),decimal(root,"rest_qi_fraction",0,1),offers,staff);
        } catch(RuntimeException e) { throw new SimDataException(file,"commerce",e.getMessage()); }
    }
    private static void fields(JsonObject o,String... keys) {
        if(!o.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Missing/unknown fields: "+o.keySet());
    }
    private static String text(JsonObject o,String k) {
        var v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException(k);
        return v.getAsString();
    }
    private static String identifier(JsonObject o,String k) {
        String s=text(o,k);if(!s.matches("[a-z0-9_]{1,96}"))throw new IllegalArgumentException(k);return s;
    }
    private static String resource(JsonObject o,String k) {
        String s=text(o,k);if(s.length()>160||!s.matches("[a-z0-9_.-]+:[a-z0-9_./-]{1,128}"))throw new IllegalArgumentException(k);return s;
    }
    private static int integer(JsonObject o,String k,int lo,int hi) {
        var v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(k);
        int n=v.getAsBigDecimal().intValueExact();if(n<lo||n>hi)throw new IllegalArgumentException(k);return n;
    }
    private static double decimal(JsonObject o,String k,double lo,double hi) {
        var v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException(k);
        double n=v.getAsDouble();if(!Double.isFinite(n)||n<lo||n>hi)throw new IllegalArgumentException(k);return n;
    }
}
