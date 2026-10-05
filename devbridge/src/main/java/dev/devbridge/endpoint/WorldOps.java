package dev.devbridge.endpoint;

import com.google.gson.JsonObject;
import dev.devbridge.http.BridgeException;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * World operations (open, leave, create, save, snapshot, restore, reset, delete) run one at a
 * time and outlast an HTTP request: a route starts one and returns its id at once, the caller
 * polls {@code /client/world} or {@code /client/world/operation?id=} until it has finished.
 * No game classes, so {@code WorldFilesCheck} runs it on its own.
 */
final class WorldOps {
    private WorldOps() {}

    private static final int KEEP = 20;

    static final class Op {
        final long id;
        final String name;
        final String folder;
        final long started = System.currentTimeMillis();
        /** Filled by the steps; reported with the finished operation. */
        final JsonObject result = new JsonObject();
        private volatile String phase = "queued";

        private Op(long id, String name, String folder) {
            this.id = id;
            this.name = name;
            this.folder = folder;
        }

        void phase(String p) {
            phase = p;
        }

        JsonObject ref() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("name", name);
            o.addProperty("folder", folder);
            return o;
        }
    }

    private static Op running;
    private static long nextId = 1;
    private static final Deque<JsonObject> FINISHED = new ArrayDeque<>();

    /** Starts an operation, or 409 while another one runs. */
    static synchronized Op begin(String name, String folder) {
        if (running != null) {
            throw new BridgeException(409, "busy: " + running.name + (running.folder == null ? "" : " " + running.folder)
                    + " (operation " + running.id + ", " + running.phase + "); wait for it (GET /client/world)");
        }
        running = new Op(nextId++, name, folder);
        return running;
    }

    static synchronized boolean busy() {
        return running != null;
    }

    /** Records the outcome; error null means success. */
    static synchronized void finish(Op op, String error) {
        JsonObject o = op.ref();
        o.addProperty("state", error == null ? "done" : "failed");
        o.addProperty("ok", error == null);
        o.addProperty("error", error);
        o.addProperty("startedAt", op.started);
        o.addProperty("finishedAt", System.currentTimeMillis());
        o.addProperty("millis", System.currentTimeMillis() - op.started);
        o.add("result", op.result.deepCopy());
        FINISHED.addFirst(o);
        while (FINISHED.size() > KEEP) FINISHED.removeLast();
        if (running == op) running = null;
    }

    /** busy (operation name or null), current {id, name, folder, phase, seconds}, lastOperation, lastError (of lastOperation). */
    static synchronized void status(JsonObject into) {
        into.addProperty("busy", running == null ? null : running.name);
        if (running != null) {
            JsonObject c = running.ref();
            c.addProperty("state", "running");
            c.addProperty("phase", running.phase);
            c.addProperty("seconds", (System.currentTimeMillis() - running.started) / 1000);
            into.add("current", c);
        } else {
            into.add("current", null);
        }
        JsonObject last = FINISHED.peekFirst();
        into.add("lastOperation", last == null ? null : last.deepCopy());
        into.addProperty("lastError", last == null || last.get("error").isJsonNull() ? null : last.get("error").getAsString());
    }

    /** One operation by id: running, one of the last 20 finished, or state "unknown". */
    static synchronized JsonObject get(long id) {
        if (running != null && running.id == id) {
            JsonObject c = running.ref();
            c.addProperty("state", "running");
            c.addProperty("phase", running.phase);
            c.addProperty("seconds", (System.currentTimeMillis() - running.started) / 1000);
            return c;
        }
        for (JsonObject o : FINISHED) if (o.get("id").getAsLong() == id) return o.deepCopy();
        JsonObject o = new JsonObject();
        o.addProperty("id", id);
        o.addProperty("state", id < nextId ? "unknown" : "not started");
        return o;
    }
}
