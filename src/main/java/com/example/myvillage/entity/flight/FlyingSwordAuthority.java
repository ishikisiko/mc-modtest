package com.example.myvillage.entity.flight;

import java.util.UUID;

/** Side-free authorization policy. No client supplied identities enter the runtime call sites. */
public final class FlyingSwordAuthority {
    private FlyingSwordAuthority() { }
    public static boolean controls(UUID owner, UUID sender, boolean alive, boolean passenger, boolean sameLevel) {
        return owner != null && owner.equals(sender) && alive && passenger && sameLevel;
    }
    public static boolean mayLaunch(boolean alive, boolean spectator, boolean passenger, boolean sleeping,
            boolean grounded, boolean wet, boolean tribulation, boolean qualified) {
        return alive && !spectator && !passenger && !sleeping && grounded && !wet && !tribulation && qualified;
    }
    public static boolean currentBinding(UUID binding, UUID removed) {
        return binding != null && binding.equals(removed);
    }
}
