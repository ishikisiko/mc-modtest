package com.example.myvillage.npc;

import com.example.myvillage.entity.npc.NpcCombatRuntime;
import com.example.myvillage.entity.npc.NpcEntity;
import com.example.myvillage.portrait.PortraitAssign;
import com.example.myvillage.sim.PersonView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.commerce.CommerceRules;
import com.example.myvillage.sim.life.LifeResidentView;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimSavedData;
import com.example.myvillage.sim.runtime.WorldSimText;
import com.example.myvillage.sim.runtime.avatar.WorldSimAvatars;
import com.example.myvillage.sim.runtime.commerce.NpcCommerce;
import com.example.myvillage.sim.runtime.life.NpcLifeInteraction;
import com.example.myvillage.sim.runtime.player.SectDialogue;
import com.example.myvillage.story.StoryRuntime;
import com.example.myvillage.story.StorySessions;
import com.example.myvillage.story.net.StoryDialoguePayload;
import com.example.myvillage.story.net.StoryDialoguePayload.Option;
import java.util.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shared server entry. Existing services keep their own final authorization and mutation rules. */
public final class NpcDialogue {
    private static final StorySessions SESSIONS = new StorySessions();
    private static final Map<UUID, Long> LAST = new LinkedHashMap<>();
    private NpcDialogue() { }

    public static boolean valid(ServerPlayer p, NpcEntity npc) {
        if (npc == null || npc instanceof net.minecraft.world.entity.monster.Enemy
                || p.isRemoved() || !p.isAlive() || p.isSpectator() || p.getServer() == null
                || p.getServer().getPlayerList().getPlayer(p.getUUID()) != p
                || npc.isRemoved() || !npc.isAlive() || npc.combatEngaged() || npc.getTarget() != null
                || npc.level() != p.level() || p.serverLevel().getEntity(npc.getUUID()) != npc
                || !NpcCombatRuntime.managed(npc) || !NpcCombatRuntime.canTick(npc)
                || p.distanceToSqr(npc) > Math.pow(NpcInteractionRules.get().range(), 2)
                || !p.hasLineOfSight(npc)) return false;
        if (!npc.isLedgerAvatar()) return true;
        var sim = WorldSimRuntime.sim().orElse(null);
        if (sim == null || sim.person(npc.ledgerPersonId()).filter(PersonView::alive).isEmpty()) return false;
        var life = sim.life(npc.ledgerPersonId()).orElse(null);
        return life == null || life.embodied() && WorldSimAvatars.isManagedLife(npc, life.epoch());
    }

    private static StorySessions.Identity identity(ServerPlayer p, NpcEntity npc) {
        var sim = WorldSimRuntime.sim().orElse(null);
        var person = sim == null ? null : sim.person(npc.ledgerPersonId()).orElse(null);
        var life = sim == null ? null : sim.life(npc.ledgerPersonId()).orElse(null);
        String role = person == null ? "rogue" : person.sectId() + ":" + person.rank() + ":" + person.status();
        String site = life == null ? "" : life.siteId() + ":" + life.role() + ":" + life.epoch();
        return new StorySessions.Identity(npc.getUUID(), npc.getId(), npc.ledgerPersonId(), npc.combatEpoch(),
                role, p.level().dimension().location().toString(), 0, site);
    }

    private static boolean allow(ServerPlayer p) {
        long now = p.getServer().getTickCount();
        Long old = LAST.get(p.getUUID());
        if (old != null && now >= old && now - old < NpcInteractionRules.get().intentCooldownTicks()) return false;
        LAST.put(p.getUUID(), now);
        while (LAST.size() > NpcInteractionRules.get().maxSessions()) {
            UUID first = LAST.keySet().iterator().next(); LAST.remove(first); SESSIONS.forget(first);
        }
        return true;
    }

    public static void open(ServerPlayer p, NpcEntity npc) {
        if (!valid(p, npc) || !allow(p)) return;
        var sim = npc.isLedgerAvatar() ? WorldSimRuntime.sim().orElse(null) : null;
        String greeting = "道友，有事不妨直说。我只是路过此地的一介散修。";
        if (sim != null) {
            sim.registerPlayerActor(p.getUUID().toString(), p.getGameProfile().getName());
            var context = sim.conversationContext(npc.ledgerPersonId(), p.getUUID().toString());
            greeting = context.metBefore() ? "又见面了，道友。今日想聊些什么？" : "初次见面，在下" + context.npcName() + "。道友有何见教？";
            sim.rememberConversation(npc.ledgerPersonId(), p.getUUID().toString());
            WorldSimSavedData.get(p.getServer().overworld()).setDirty();
        }
        send(p, npc, List.of(greeting), NpcDialogueOptions.home());
    }

    public static NpcDialogueOptions.Capabilities capabilities(ServerPlayer p, NpcEntity npc) {
        var sim = npc.isLedgerAvatar() ? WorldSimRuntime.sim().orElse(null) : null;
        var person = sim == null ? null : sim.person(npc.ledgerPersonId()).orElse(null);
        var life = sim == null ? null : sim.life(npc.ledgerPersonId()).orElse(null);
        boolean sect = person != null && person.sectId() >= 0
                && sim.sect(person.sectId()).filter(s -> s.state().equals("active")).isPresent();
        return new NpcDialogueOptions.Capabilities(person != null, sect,
                sect && SectDialogue.hasOffice(sim, person),
                life != null && CommerceRules.ROLES.contains(life.role()) && sim.commerce(life.siteId()).isPresent(),
                StoryRuntime.canDiscuss(p, npc), life != null && life.role().equals("guide"));
    }

    public static void choose(ServerPlayer p, NpcDialogueIntent intent) {
        var c = intent.choice();
        var old = SESSIONS.current(p.getUUID());
        if (old == null || old.nonce() != c.nonce() || old.identity().entityId() != c.entityId()) return;
        var entity = p.serverLevel().getEntity(c.entityId());
        var npc = entity instanceof NpcEntity n ? n : null;
        if (!valid(p, npc)) { SESSIONS.forget(p.getUUID()); return; }
        // Escape always releases a matching capability, even immediately after opening.
        if (!c.optionId().equals("close") && !allow(p)) {
            send(p, npc, List.of("道友，慢慢说。"), NpcDialogueOptions.home()); return;
        }
        var consumed = SESSIONS.consume(p.getUUID(), c.nonce(), c.optionId(), identity(p, npc),
                p.serverLevel().getGameTime(), true);
        if (consumed == null) {
            if (npc.getUUID().equals(old.identity().entity()))
                send(p, npc, List.of("眼下情形有些变化，道友想问什么？"), NpcDialogueOptions.home());
            return;
        }
        String option = c.optionId();
        if (option.equals("close")) return;
        if (option.equals("home")) { send(p, npc, List.of("道友还有何事？"), NpcDialogueOptions.home()); return; }
        var caps = capabilities(p, npc);
        if (option.startsWith("services_")) {
            int index = Integer.parseInt(option.substring("services_".length())); // only server-offered IDs reach here
            send(p, npc, List.of(NpcDialogueOptions.services(caps).isEmpty()
                    ? "我在此闲逛歇脚，暂时没有可托付的事务。" : "道友想谈哪件事？"), NpcDialogueOptions.servicesPage(caps, index));
            return;
        }
        if (option.equals("chat") || option.equals("status")) {
            send(p, npc, lines(p, npc, option), List.of(new Option("home", "返回交谈"), new Option("close", "告辞")));
            return;
        }
        if (NpcDialogueOptions.services(caps).stream().noneMatch(o -> o.id().equals(option))) {
            send(p, npc, List.of("此事眼下不便办理。"), NpcDialogueOptions.home()); return;
        }
        // The client has dismissed the shared page before requesting a different menu.
        if (option.equals("trade")) {
            if (NpcCommerce.valid(p, npc, WorldSimRuntime.sim().orElse(null)) && NpcCommerce.openDialogue(p, npc)) return;
            send(p, npc, List.of("我眼下没有在岗，暂时无法交易。等我回到店中当值时再来吧。"), NpcDialogueOptions.home()); return;
        }
        if (option.equals("story") && StoryRuntime.interact(p, npc)) return;
        if (option.equals("sect_office") && SectDialogue.open(p, npc)) return;
        if (option.equals("guide")) {
            com.example.myvillage.exploration.ExplorationRuntime.guide(p, Component.literal(npc.getName().getString()), false);
            send(p, npc, List.of("行路修行的建议已记下，道友可慢慢参详。"), NpcDialogueOptions.home()); return;
        }
        send(p, npc, option.equals("sect_topic") ? sectLines(npc) : List.of("此事眼下不便办理，请稍后再来。"),
                List.of(new Option("home", "返回交谈"), new Option("close", "告辞")));
    }

    private static List<String> lines(ServerPlayer p, NpcEntity npc, String topic) {
        WorldSim sim = npc.isLedgerAvatar() ? WorldSimRuntime.sim().orElse(null) : null;
        PersonView person = sim == null ? null : sim.person(npc.ledgerPersonId()).orElse(null);
        LifeResidentView life = sim == null ? null : sim.life(npc.ledgerPersonId()).orElse(null);
        if (person == null) return List.of(topic.equals("status")
                ? "近日只在这一带走走，累了便歇脚，暂时没有远行的打算。"
                : "我独来独往，不依附宗门。路上能遇到同道，说上几句，也是缘分。");
        if (topic.equals("status")) {
            if (life != null) return List.of("我眼下的安排是" + NpcLifeInteraction.activityText(life.activity()).getString()
                    + "，目前" + Component.translatable("message.myvillage.life.phase." + life.phase()).getString() + "。",
                    life.reason().equals("none") || life.reason().isEmpty() ? "道友有话请讲。" : NpcLifeInteraction.reasonText(life.reason()).getString());
            return List.of("我眼下" + WorldSimText.status(person.status()).getString() + "。",
                    person.injury() > 0 ? "身上有伤，还需静养。" : "近日无恙，多谢道友挂念。");
        }
        var context = sim.conversationContext(person.id(), p.getUUID().toString());
        String familiar = context.familiarity() > 1 ? "与道友也算渐渐熟悉了。" : "同道相逢，且聊几句。";
        String disposition = person.traits().size() < 5 ? "修行各有机缘，循序而进便好。"
                : switch (person.traits().indexOf(Collections.max(person.traits()))) {
                    case 0 -> "路还长，我总想再向上走一层。";
                    case 1 -> "遇事总须有些担当，不过动手之前也该想清后果。";
                    case 2 -> "出门在外，先看清地势与人情，再做打算。";
                    case 3 -> "比起久坐一处，我更愿沿途看看不同的山水。";
                    default -> "修行路上，旧友与承诺都该放在心上。";
                };
        return List.of(familiar, "如今修至" + WorldSimText.realm(person.realmId()).getString() + "，仍有许多功课要做。", disposition);
    }

    private static List<String> sectLines(NpcEntity npc) {
        var sim = WorldSimRuntime.sim().orElseThrow();
        var person = sim.person(npc.ledgerPersonId()).orElseThrow();
        var sect = sim.sect(person.sectId()).orElseThrow();
        return List.of("我出自" + sect.name() + "，在门中是" + WorldSimText.rank(person.rank()).getString() + "。",
                "本门现有" + sect.memberCount() + "位修士，宗主是" + sect.masterName() + "。",
                SectDialogue.hasOffice(sim, person) ? "若要办理门中事务，道友可另行问我。" : "入门与门中差事，要请守山执事作主；拜师还须长老亲自允诺。");
    }

    private static void send(ServerPlayer p, NpcEntity npc, List<String> lines, List<Option> options) {
        var rules = NpcInteractionRules.get();
        npc.pauseConversation(rules.pauseTicks());
        var sim = npc.isLedgerAvatar() ? WorldSimRuntime.sim().orElse(null) : null;
        var person = sim == null ? null : sim.person(npc.ledgerPersonId()).orElse(null);
        if (sim != null && sim.life(npc.ledgerPersonId()).isPresent()) {
            sim.pauseLife(npc.ledgerPersonId(), rules.pauseTicks());
            WorldSimSavedData.get(p.getServer().overworld()).setDirty();
        }
        npc.getNavigation().stop(); npc.getLookControl().setLookAt(p, 30, 30);
        var ids = new HashSet<String>(); options.forEach(o -> ids.add(o.id())); ids.add("close");
        var session = SESSIONS.open(p.getUUID(), identity(p, npc), p.serverLevel().getGameTime(), rules.sessionTicks(), ids, false);
        var portrait = person == null ? PortraitAssign.of(npc.getUUID().hashCode() & Integer.MAX_VALUE,
                npc.look().startsWith("f_"), "qi_refining", "rogue", -1, 20,
                new int[]{2000,2000,2000,2000,2000}, new int[]{50,50,50,50,50}, 0, true)
                : PortraitAssign.of(person, sim.day(), WorldSimRuntime.daysPerYear());
        String name = person == null ? npc.hasCustomName() ? npc.getCustomName().getString() : "无名散修" : person.name();
        String role = person == null || person.sectId() < 0 ? "散修" : person.sectName() + " · " + WorldSimText.rank(person.rank()).getString();
        PacketDistributor.sendToPlayer(p, new NpcDialoguePayload(new StoryDialoguePayload(npc.getId(), session.nonce(),
                clip(role, 64), clip(name, 64), portrait, "与" + clip(name, 60) + "交谈", lines, options)));
    }
    private static String clip(String s, int max) { return s.length() <= max ? s : s.substring(0, max); }
    public static void forget(UUID player) { SESSIONS.forget(player); LAST.remove(player); }
    public static void clear() { SESSIONS.clear(); LAST.clear(); }
}
