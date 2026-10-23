package com.example.myvillage.story;

import java.security.SecureRandom;
import java.util.*;

/** Server-thread-only one-use capabilities. One player's delayed packet cannot close a newer page. */
public final class StorySessions {
    public record Identity(UUID entity,int entityId,int person,long epoch,String role,String dimension,long revision,String site) {}
    public record Session(long nonce,Identity identity,long expires,Set<String> options,boolean informed) {}
    private final Map<UUID,Session> sessions=new HashMap<>();
    private long sequence=new SecureRandom().nextLong() >>> 2;
    public Session open(UUID player,Identity identity,long now,int ttl,Set<String> options,boolean informed) {
        long nonce=Math.incrementExact(sequence);sequence=nonce;
        var session=new Session(nonce,identity,now+ttl,Set.copyOf(options),informed);sessions.put(player,session);return session;
    }
    public Session current(UUID player){return sessions.get(player);}
    public Session consume(UUID player,long nonce,String option,Identity identity,long now,boolean validEnvironment) {
        var s=sessions.get(player);
        if(s==null||s.nonce()!=nonce)return null;
        if(!validEnvironment||now>=s.expires()||!s.identity().equals(identity)){sessions.remove(player);return null;}
        if(!s.options().contains(option))return null;
        sessions.remove(player);return s;
    }
    public void forget(UUID player){sessions.remove(player);}
    public void clear(){sessions.clear();}
}
