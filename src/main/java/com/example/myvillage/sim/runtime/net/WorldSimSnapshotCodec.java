package com.example.myvillage.sim.runtime.net;

import net.minecraft.network.FriendlyByteBuf;

/**
 * The wire layout of a {@link WorldSimSnapshot}: the query (as in {@link WorldSimQueryPayload}),
 * the header, then each section in record order. A nullable section is prefixed with a boolean,
 * a list with its varint size. Needs no registry access, so it runs on a plain
 * {@link FriendlyByteBuf}.
 */
public final class WorldSimSnapshotCodec {
    private WorldSimSnapshotCodec() {
    }

    public static void write(FriendlyByteBuf buf, WorldSimSnapshot s) {
        WorldSimQueryPayload.STREAM_CODEC.encode(buf, new WorldSimQueryPayload(s.query()));
        buf.writeBoolean(s.active());
        buf.writeUtf(s.inactiveReason());
        buf.writeVarLong(s.day());
        buf.writeVarLong(s.prehistoryDays());
        buf.writeVarInt(s.daysPerYear());
        writeNullable(buf, s.overview(), WorldSimSnapshotCodec::writeOverview);
        buf.writeCollection(s.sects(), WorldSimSnapshotCodec::writeSectSummary);
        writeNullable(buf, s.sect(), WorldSimSnapshotCodec::writeSectDetail);
        buf.writeCollection(s.persons(), WorldSimSnapshotCodec::writePersonSummary);
        writeNullable(buf, s.person(), WorldSimSnapshotCodec::writePersonDetail);
        buf.writeCollection(s.events(), WorldSimSnapshotCodec::writeEvent);
        buf.writeCollection(s.causes(), WorldSimSnapshotCodec::writeEvent);
        writeNullable(buf, s.region(), WorldSimSnapshotCodec::writeRegion);
    }

    public static WorldSimSnapshot read(FriendlyByteBuf buf) {
        WorldSimQuery query = WorldSimQueryPayload.STREAM_CODEC.decode(buf).query();
        boolean active = buf.readBoolean();
        String inactiveReason = buf.readUtf();
        long day = buf.readVarLong();
        long prehistoryDays = buf.readVarLong();
        int daysPerYear = buf.readVarInt();
        WorldSimSnapshot.Overview overview = buf.readBoolean() ? readOverview(buf) : null;
        var sects = buf.readList(WorldSimSnapshotCodec::readSectSummary);
        WorldSimSnapshot.SectDetail sect = buf.readBoolean() ? readSectDetail(buf) : null;
        var persons = buf.readList(WorldSimSnapshotCodec::readPersonSummary);
        WorldSimSnapshot.PersonDetail person = buf.readBoolean() ? readPersonDetail(buf) : null;
        var events = buf.readList(WorldSimSnapshotCodec::readEvent);
        var causes = buf.readList(WorldSimSnapshotCodec::readEvent);
        WorldSimSnapshot.Region region = buf.readBoolean() ? readRegion(buf) : null;
        return new WorldSimSnapshot(query, active, inactiveReason, day, prehistoryDays, daysPerYear, overview, sects,
                sect, persons, person, events, causes, region);
    }

    private interface Writer<T> {
        void write(FriendlyByteBuf buf, T value);
    }

    private static <T> void writeNullable(FriendlyByteBuf buf, T value, Writer<T> writer) {
        buf.writeBoolean(value != null);
        if (value != null) {
            writer.write(buf, value);
        }
    }

    // ------------------------------------------------------------------ overview

    private static void writeOverview(FriendlyByteBuf buf, WorldSimSnapshot.Overview o) {
        buf.writeUtf(o.tierId());
        buf.writeVarInt(o.population());
        buf.writeVarInt(o.targetPopulation());
        buf.writeCollection(o.livingByRealm(), (b, r) -> {
            b.writeUtf(r.realmId());
            b.writeVarInt(r.count());
        });
        buf.writeVarInt(o.activeSects());
        buf.writeVarInt(o.destroyedSects());
        buf.writeVarInt(o.deadCount());
        buf.writeVarLong(o.eventCount());
        buf.writeBoolean(o.paused());
        buf.writeVarInt(o.pendingDays());
        buf.writeVarLong(o.calendarDay());
    }

    private static WorldSimSnapshot.Overview readOverview(FriendlyByteBuf buf) {
        String tierId = buf.readUtf();
        int population = buf.readVarInt();
        int target = buf.readVarInt();
        var realms = buf.readList(b -> new WorldSimSnapshot.RealmCount(b.readUtf(), b.readVarInt()));
        int active = buf.readVarInt();
        int destroyed = buf.readVarInt();
        int dead = buf.readVarInt();
        long eventCount = buf.readVarLong();
        boolean paused = buf.readBoolean();
        int pending = buf.readVarInt();
        long calendarDay = buf.readVarLong();
        return new WorldSimSnapshot.Overview(tierId, population, target, realms, active, destroyed, dead, eventCount,
                paused, pending, calendarDay);
    }

    // ------------------------------------------------------------------ sects

    private static void writeSectSummary(FriendlyByteBuf buf, WorldSimSnapshot.SectSummary s) {
        buf.writeVarInt(s.id());
        buf.writeUtf(s.name());
        buf.writeUtf(s.regionName());
        buf.writeUtf(s.masterName());
        buf.writeVarInt(s.memberCount());
        buf.writeUtf(s.topRealmId());
        buf.writeVarInt(s.prestige());
        buf.writeVarInt(s.gateX());
        buf.writeVarInt(s.gateZ());
        buf.writeBoolean(s.gateRealized());
        buf.writeBoolean(s.active());
        buf.writeVarInt(s.distance());
    }

    private static WorldSimSnapshot.SectSummary readSectSummary(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf();
        String regionName = buf.readUtf();
        String masterName = buf.readUtf();
        int members = buf.readVarInt();
        String topRealm = buf.readUtf();
        int prestige = buf.readVarInt();
        int gateX = buf.readVarInt();
        int gateZ = buf.readVarInt();
        boolean realized = buf.readBoolean();
        boolean active = buf.readBoolean();
        int distance = buf.readVarInt();
        return new WorldSimSnapshot.SectSummary(id, name, regionName, masterName, members, topRealm, prestige, gateX,
                gateZ, realized, active, distance);
    }

    private static void writeSectDetail(FriendlyByteBuf buf, WorldSimSnapshot.SectDetail d) {
        writeSectSummary(buf, d.summary());
        buf.writeVarLong(d.foundedDay());
        buf.writeVarInt(d.founderId());
        buf.writeUtf(d.founderName());
        buf.writeVarInt(d.masterId());
        buf.writeVarInt(d.parentSectId());
        buf.writeUtf(d.parentSectName());
        buf.writeVarLong(d.destroyedDay());
        buf.writeVarInt(d.resources());
        buf.writeUtf(d.signatureTechniqueName());
        buf.writeCollection(d.relations(), (b, r) -> {
            b.writeVarInt(r.otherSectId());
            b.writeUtf(r.otherSectName());
            b.writeVarInt(r.value());
            b.writeUtf(r.state());
        });
    }

    private static WorldSimSnapshot.SectDetail readSectDetail(FriendlyByteBuf buf) {
        WorldSimSnapshot.SectSummary summary = readSectSummary(buf);
        long foundedDay = buf.readVarLong();
        int founderId = buf.readVarInt();
        String founderName = buf.readUtf();
        int masterId = buf.readVarInt();
        int parentId = buf.readVarInt();
        String parentName = buf.readUtf();
        long destroyedDay = buf.readVarLong();
        int resources = buf.readVarInt();
        String technique = buf.readUtf();
        var relations = buf.readList(b -> new WorldSimSnapshot.SectRelation(b.readVarInt(), b.readUtf(),
                b.readVarInt(), b.readUtf()));
        return new WorldSimSnapshot.SectDetail(summary, foundedDay, founderId, founderName, masterId, parentId,
                parentName, destroyedDay, resources, technique, relations);
    }

    // ------------------------------------------------------------------ persons

    private static void writePersonSummary(FriendlyByteBuf buf, WorldSimSnapshot.PersonSummary p) {
        buf.writeVarInt(p.id());
        buf.writeUtf(p.name());
        buf.writeUtf(p.title());
        buf.writeBoolean(p.alive());
        buf.writeUtf(p.realmId());
        buf.writeVarInt(p.stage());
        buf.writeVarInt(p.sectId());
        buf.writeUtf(p.sectName());
        buf.writeUtf(p.rank());
    }

    private static WorldSimSnapshot.PersonSummary readPersonSummary(FriendlyByteBuf buf) {
        int id = buf.readVarInt();
        String name = buf.readUtf();
        String title = buf.readUtf();
        boolean alive = buf.readBoolean();
        String realmId = buf.readUtf();
        int stage = buf.readVarInt();
        int sectId = buf.readVarInt();
        String sectName = buf.readUtf();
        String rank = buf.readUtf();
        return new WorldSimSnapshot.PersonSummary(id, name, title, alive, realmId, stage, sectId, sectName, rank);
    }

    private static void writePersonDetail(FriendlyByteBuf buf, WorldSimSnapshot.PersonDetail d) {
        writePersonSummary(buf, d.summary());
        buf.writeUtf(d.gender());
        buf.writeVarLong(d.birthDay());
        buf.writeVarLong(d.deathDay());
        buf.writeUtf(d.deathCause());
        buf.writeVarInt(d.killerId());
        buf.writeUtf(d.killerName());
        buf.writeUtf(d.rootGrade());
        buf.writeCollection(d.root(), FriendlyByteBuf::writeVarInt);
        buf.writeDouble(d.progress());
        buf.writeVarInt(d.masterId());
        buf.writeUtf(d.masterName());
        buf.writeUtf(d.regionName());
        buf.writeUtf(d.status());
        buf.writeUtf(d.techniqueName());
        buf.writeVarInt(d.injury());
        buf.writeCollection(d.relations(), (b, r) -> {
            b.writeVarInt(r.otherId());
            b.writeUtf(r.otherName());
            b.writeUtf(r.kind());
            b.writeVarInt(r.strength());
        });
    }

    private static WorldSimSnapshot.PersonDetail readPersonDetail(FriendlyByteBuf buf) {
        WorldSimSnapshot.PersonSummary summary = readPersonSummary(buf);
        String gender = buf.readUtf();
        long birthDay = buf.readVarLong();
        long deathDay = buf.readVarLong();
        String deathCause = buf.readUtf();
        int killerId = buf.readVarInt();
        String killerName = buf.readUtf();
        String rootGrade = buf.readUtf();
        var root = buf.readList(FriendlyByteBuf::readVarInt);
        double progress = buf.readDouble();
        int masterId = buf.readVarInt();
        String masterName = buf.readUtf();
        String regionName = buf.readUtf();
        String status = buf.readUtf();
        String technique = buf.readUtf();
        int injury = buf.readVarInt();
        var relations = buf.readList(b -> new WorldSimSnapshot.PersonRelation(b.readVarInt(), b.readUtf(),
                b.readUtf(), b.readVarInt()));
        return new WorldSimSnapshot.PersonDetail(summary, gender, birthDay, deathDay, deathCause, killerId, killerName,
                rootGrade, root, progress, masterId, masterName, regionName, status, technique, injury, relations);
    }

    // ------------------------------------------------------------------ events and region

    private static void writeEvent(FriendlyByteBuf buf, WorldSimSnapshot.EventLine e) {
        buf.writeVarLong(e.id());
        buf.writeVarLong(e.day());
        buf.writeVarInt(e.importance());
        buf.writeVarInt(e.subjectId());
        buf.writeUtf(e.textKey());
        buf.writeCollection(e.params(), FriendlyByteBuf::writeUtf);
        buf.writeVarLong(e.causeId());
    }

    private static WorldSimSnapshot.EventLine readEvent(FriendlyByteBuf buf) {
        long id = buf.readVarLong();
        long day = buf.readVarLong();
        int importance = buf.readVarInt();
        int subjectId = buf.readVarInt();
        String textKey = buf.readUtf();
        var params = buf.readList(FriendlyByteBuf::readUtf);
        long causeId = buf.readVarLong();
        return new WorldSimSnapshot.EventLine(id, day, importance, subjectId, textKey, params, causeId);
    }

    private static void writeRegion(FriendlyByteBuf buf, WorldSimSnapshot.Region r) {
        buf.writeUtf(r.id());
        buf.writeUtf(r.displayName());
        buf.writeVarInt(r.tier());
        buf.writeVarInt(r.qiLo());
        buf.writeVarInt(r.qiHi());
        buf.writeVarInt(r.dangerLo());
        buf.writeVarInt(r.dangerHi());
        buf.writeBoolean(r.admitsSects());
        buf.writeVarInt(r.livingCount());
    }

    private static WorldSimSnapshot.Region readRegion(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String displayName = buf.readUtf();
        int tier = buf.readVarInt();
        int qiLo = buf.readVarInt();
        int qiHi = buf.readVarInt();
        int dangerLo = buf.readVarInt();
        int dangerHi = buf.readVarInt();
        boolean admits = buf.readBoolean();
        int living = buf.readVarInt();
        return new WorldSimSnapshot.Region(id, displayName, tier, qiLo, qiHi, dangerLo, dangerHi, admits, living);
    }
}
