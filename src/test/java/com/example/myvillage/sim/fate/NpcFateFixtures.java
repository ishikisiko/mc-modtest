package com.example.myvillage.sim.fate;

import com.example.myvillage.sim.SimRng;
import com.example.myvillage.sim.engine.Purpose;
import com.example.myvillage.sim.model.Person;
import com.example.myvillage.sim.model.Tombstone;

/** 手写命簿 fixture 同样提供当前格式要求的出生天赋。 */
public final class NpcFateFixtures {
    private NpcFateFixtures() { }
    public static void assign(Person person) {
        person.fates = NpcFatePool.bundled().roll(person.root, person.prodigy,
                SimRng.at(21, 0, person.id, Purpose.NPC_FATES));
    }
    public static void assign(Tombstone dead) {
        dead.fates = NpcFatePool.bundled().roll(new int[5], dead.prodigy,
                SimRng.at(21, 0, dead.id, Purpose.NPC_FATES));
    }
}
