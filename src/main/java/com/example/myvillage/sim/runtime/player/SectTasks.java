package com.example.myvillage.sim.runtime.player;

import com.example.myvillage.entity.beast.BeastEntity;
import com.example.myvillage.item.ModItems;
import com.example.myvillage.region.runtime.RegionRuntimeService;
import com.example.myvillage.sect.SectCourtyard;
import com.example.myvillage.sim.Admission;
import com.example.myvillage.sim.PlayerMemberView;
import com.example.myvillage.sim.SectView;
import com.example.myvillage.sim.SimEvent;
import com.example.myvillage.sim.TaskView;
import com.example.myvillage.sim.WorldSim;
import com.example.myvillage.sim.data.ContentTables;
import com.example.myvillage.sim.engine.TextKeys;
import com.example.myvillage.sim.runtime.WorldSimRuntime;
import com.example.myvillage.sim.runtime.WorldSimText;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The runtime side of sect tasks (宗门事务) and apprenticeship (拜师), sect entry slice 3. Progress
 * comes from the world: a patrol counts beasts the player kills in their sect's region
 * ({@link #onLivingDeath}); a courier is done once the player stands on the destination sect's
 * compound site ({@link #tick}, every {@link #COURIER_INTERVAL_TICKS} ticks); a tribute keeps no
 * progress, the low-grade spirit stones are counted and taken at turn-in. Each progress step logs
 * {@code SECT_TASK player=<name> kind=<kind> progress=<p>/<n>}; taking, turning in and
 * apprenticing go through the {@code WorldSim} facade and log
 * {@code SECT_ENTRY player=<name> intent=TASK_ACCEPT|TASK_TURN_IN|APPRENTICE sect=<id> result=ok|<reason>}.
 */
public final class SectTasks {
    private static final Logger LOGGER = LoggerFactory.getLogger(SectTasks.class);
    static final int COURIER_INTERVAL_TICKS = 20;
    static final String PROGRESS_KEY = "message.myvillage.world.sect.task_progress";
    static final String ACCEPTED_KEY = "message.myvillage.world.sect.task_accepted";
    static final String DONE_KEY = "message.myvillage.world.sect.task_done";
    static final String APPRENTICED_KEY = "message.myvillage.world.sect.apprenticed";
    static final String TASK_ACCEPT = "TASK_ACCEPT";
    static final String TASK_TURN_IN = "TASK_TURN_IN";
    static final String APPRENTICE = "APPRENTICE";

    private SectTasks() {
    }

    // ------------------------------------------------------------------ progress hooks

    /**
     * A beast died: one step of the killer's patrol when the killer stands in their sect's region
     * in the overworld (the ledger's regions are overworld regions).
     */
    static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof BeastEntity)
                || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.level().dimension() != Level.OVERWORLD) {
            return;
        }
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return;
        }
        WorldSim sim = active.get();
        String id = player.getUUID().toString();
        try {
            Optional<TaskView> task = sim.task(id);
            Optional<PlayerMemberView> me = sim.playerMember(id);
            if (task.isEmpty() || me.isEmpty() || !me.get().inSect()) {
                return;
            }
            String sectRegion = sim.sect(me.get().sectId()).map(SectView::regionId).orElse("");
            if (!patrolCounts(task.get(), RegionRuntimeService.currentRegion(player), sectRegion)) {
                return;
            }
            if (sim.advanceTask(id, ContentTables.TASK_PATROL, 1)) {
                progressed(sim, player, ContentTables.TASK_PATROL);
            }
        } catch (RuntimeException ex) {
            LOGGER.warn("SectTasks: patrol step of {} failed", name(player), ex);
        }
    }

    /** Every {@link #COURIER_INTERVAL_TICKS} ticks: delivers the letters of players at their destination's gate. */
    static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % COURIER_INTERVAL_TICKS == 0) {
            tick(event.getServer());
        }
    }

    /** One courier check over the online players; a letter to a sect that is no longer active waits. */
    static void tick(MinecraftServer server) {
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return;
        }
        WorldSim sim = active.get();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() != server.overworld()) {
                continue;
            }
            String id = player.getUUID().toString();
            try {
                Optional<TaskView> task = sim.task(id);
                if (task.isEmpty() || !courierPending(task.get())) {
                    continue;
                }
                Optional<SectView> target = sim.sect(task.get().targetSectId());
                if (target.isEmpty() || !target.get().state().equals("active")) {
                    continue;
                }
                SectCourtyard.Footprint site = SectCourtyard.footprint(
                        new BlockPos(target.get().gateX(), 0, target.get().gateZ()));
                if (site.distanceTo(player.getX(), player.getZ()) == 0
                        && sim.advanceTask(id, ContentTables.TASK_COURIER, task.get().count())) {
                    progressed(sim, player, ContentTables.TASK_COURIER);
                }
            } catch (RuntimeException ex) {
                LOGGER.warn("SectTasks: courier check of {} failed", name(player), ex);
            }
        }
    }

    private static void progressed(WorldSim sim, ServerPlayer player, String kind) {
        WorldSimPlayers.markDirty(player.getServer());
        Optional<TaskView> after = sim.task(player.getUUID().toString());
        int progress = after.map(TaskView::progress).orElse(0);
        int count = after.map(TaskView::count).orElse(0);
        after.ifPresent(t -> player.sendSystemMessage(Component.translatable(PROGRESS_KEY, name(t),
                String.valueOf(progress), String.valueOf(count))));
        LOGGER.info("SECT_TASK player={} kind={} progress={}/{}", name(player), kind, progress, count);
    }

    // ------------------------------------------------------------------ dialogue actions

    /** The steward hands the player this year's task ({@code WorldSim.acceptTask}). */
    static WorldSimPlayers.Result accept(ServerPlayer player, int sectId) {
        String name = name(player);
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return WorldSimPlayers.logged(name, TASK_ACCEPT, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        SimEvent e;
        try {
            e = active.get().acceptTask(player.getUUID().toString(), name);
        } catch (IllegalArgumentException ex) {
            return WorldSimPlayers.logged(name, TASK_ACCEPT, sectId, fail(WorldSimPlayers.reasonOf(ex)));
        } catch (RuntimeException ex) {
            LOGGER.error("SectTasks: TASK_ACCEPT of {} at sect {} failed", name, sectId, ex);
            return WorldSimPlayers.logged(name, TASK_ACCEPT, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        WorldSimPlayers.markDirty(player.getServer());
        player.sendSystemMessage(Component.translatable(ACCEPTED_KEY, WorldSimText.event(e)));
        return WorldSimPlayers.logged(name, TASK_ACCEPT, sectId, new WorldSimPlayers.Result(true, Admission.OK));
    }

    /**
     * The player turns in their task ({@code WorldSim.completeTask}). A tribute needs the stones in
     * the inventory ({@link SectDialogueKeys#TRIBUTE_SHORT} otherwise); they are counted before the
     * ledger is asked and taken right after it agrees, on the same server tick.
     */
    static WorldSimPlayers.Result turnIn(ServerPlayer player, int sectId) {
        String name = name(player);
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        WorldSim sim = active.get();
        String id = player.getUUID().toString();
        Optional<TaskView> task;
        try {
            task = sim.task(id);
        } catch (RuntimeException ex) {
            LOGGER.error("SectTasks: task of {} unreadable", name, ex);
            return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        boolean tribute = task.isPresent() && ContentTables.TASK_TRIBUTE.equals(task.get().kind());
        if (tribute && !tributeReady(player, task.get())) {
            return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, fail(SectDialogueKeys.TRIBUTE_SHORT));
        }
        SimEvent e;
        try {
            e = sim.completeTask(id, name);
        } catch (IllegalArgumentException ex) {
            return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, fail(WorldSimPlayers.reasonOf(ex)));
        } catch (RuntimeException ex) {
            LOGGER.error("SectTasks: TASK_TURN_IN of {} at sect {} failed", name, sectId, ex);
            return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        if (tribute && !takeTribute(player, task.get().count())) {
            LOGGER.error("SectTasks: {} turned in a tribute but the stones were gone when taken", name);
        }
        WorldSimPlayers.markDirty(player.getServer());
        int contribution = task.map(TaskView::contribution).orElse(0);
        player.sendSystemMessage(Component.translatable(DONE_KEY, WorldSimText.event(e), String.valueOf(contribution)));
        return WorldSimPlayers.logged(name, TASK_TURN_IN, sectId, new WorldSimPlayers.Result(true, Admission.OK));
    }

    /** The player becomes the disciple of ledger person {@code masterId} ({@code WorldSim.apprentice}). */
    static WorldSimPlayers.Result apprentice(ServerPlayer player, int sectId, int masterId) {
        String name = name(player);
        Optional<WorldSim> active = WorldSimRuntime.sim();
        if (active.isEmpty()) {
            return WorldSimPlayers.logged(name, APPRENTICE, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        SimEvent e;
        try {
            e = active.get().apprentice(player.getUUID().toString(), name, masterId);
        } catch (IllegalArgumentException ex) {
            return WorldSimPlayers.logged(name, APPRENTICE, sectId, fail(WorldSimPlayers.reasonOf(ex)));
        } catch (RuntimeException ex) {
            LOGGER.error("SectTasks: APPRENTICE of {} at sect {} failed", name, sectId, ex);
            return WorldSimPlayers.logged(name, APPRENTICE, sectId, fail(SectDialogueKeys.INACTIVE));
        }
        WorldSimPlayers.markDirty(player.getServer());
        player.sendSystemMessage(Component.translatable(APPRENTICED_KEY, WorldSimText.event(e)));
        return WorldSimPlayers.logged(name, APPRENTICE, sectId, new WorldSimPlayers.Result(true, Admission.OK));
    }

    // ------------------------------------------------------------------ tribute stones

    /** Whether the inventory holds the low-grade spirit stones the tribute task asks for. */
    public static boolean tributeReady(ServerPlayer player, TaskView task) {
        return countStones(stoneCounts(player.getInventory())) >= task.count();
    }

    /** Takes {@code count} low-grade spirit stones, slot by slot; takes nothing and returns false when short. */
    public static boolean takeTribute(ServerPlayer player, int count) {
        Inventory inventory = player.getInventory();
        int[] take = takePlan(stoneCounts(inventory), count);
        if (take == null) {
            return false;
        }
        for (int slot = 0; slot < take.length; slot++) {
            if (take[slot] > 0) {
                inventory.getItem(slot).shrink(take[slot]);
            }
        }
        inventory.setChanged();
        return true;
    }

    /** Per slot, the number of low-grade spirit stones in it (0 for any other stack). */
    private static int[] stoneCounts(Inventory inventory) {
        int[] counts = new int[inventory.getContainerSize()];
        for (int slot = 0; slot < counts.length; slot++) {
            ItemStack stack = inventory.getItem(slot);
            counts[slot] = !stack.isEmpty() && stack.is(ModItems.LOW_GRADE_SPIRIT_STONE.get()) ? stack.getCount() : 0;
        }
        return counts;
    }

    // ------------------------------------------------------------------ pure decisions

    /** The stones over all slots ({@code counts} per slot, 0 for slots without stones). */
    static int countStones(int[] counts) {
        long total = 0;
        for (int c : counts) {
            total += Math.max(0, c);
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * How many stones to take from each slot, first slots first, to take {@code amount}; null when
     * the slots hold fewer (nothing is to be taken then). A non-positive amount takes nothing.
     */
    static int[] takePlan(int[] counts, int amount) {
        int[] take = new int[counts.length];
        if (amount <= 0) {
            return take;
        }
        if (countStones(counts) < amount) {
            return null;
        }
        int left = amount;
        for (int slot = 0; slot < counts.length && left > 0; slot++) {
            int n = Math.min(Math.max(0, counts[slot]), left);
            take[slot] = n;
            left -= n;
        }
        return take;
    }

    /** Whether a beast kill steps this task: a patrol, not yet done, killed inside the player's sect region. */
    static boolean patrolCounts(TaskView task, Optional<String> playerRegion, String sectRegion) {
        return ContentTables.TASK_PATROL.equals(task.kind()) && task.progress() < task.count()
                && sectRegion != null && !sectRegion.isEmpty() && playerRegion.filter(sectRegion::equals).isPresent();
    }

    /** Whether a courier task still waits for its letter to arrive. */
    static boolean courierPending(TaskView task) {
        return ContentTables.TASK_COURIER.equals(task.kind()) && task.progress() < task.count()
                && task.targetSectId() >= 0;
    }

    /**
     * The meditation factor of a master, in basis points: {@code round((1 + guidance) × 10000)}
     * when the player is in a sect, has a master, and the master is alive and of the same sect;
     * 10000 otherwise. Clamped to {@code 0..}{@value #GUIDANCE_BP_MAX} (the rules already cap
     * {@code master_guidance} at 10).
     */
    static int guidanceBasisPoints(int memberSectId, int masterId, boolean masterAlive, int masterSectId,
                                   double guidance) {
        if (memberSectId < 0 || masterId < 0 || !masterAlive || masterSectId != memberSectId
                || !Double.isFinite(guidance) || guidance <= -1.0) {
            return 10_000;
        }
        return (int) Math.max(0L, Math.min(GUIDANCE_BP_MAX, Math.round((1.0 + guidance) * 10_000)));
    }

    /** Largest guidance factor in basis points (×100). */
    static final long GUIDANCE_BP_MAX = 1_000_000L;

    // ------------------------------------------------------------------ text

    /** The task's name as a component. */
    static Component name(TaskView task) {
        return Component.translatable(TextKeys.taskName(task.id()).substring(1));
    }

    /** The task's brief line: its count, or the courier's destination sect. */
    static Component brief(TaskView task) {
        String param = ContentTables.TASK_COURIER.equals(task.kind()) ? task.targetSectName()
                : String.valueOf(task.count());
        return Component.translatable(TextKeys.taskBrief(task.id()), param);
    }

    private static WorldSimPlayers.Result fail(String reason) {
        return new WorldSimPlayers.Result(false, reason);
    }

    private static String name(ServerPlayer player) {
        return player.getGameProfile().getName();
    }
}
