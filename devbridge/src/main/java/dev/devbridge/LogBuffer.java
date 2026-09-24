package dev.devbridge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * In-memory ring buffer of log events, attached to the Log4j root logger so it
 * sees output from every mod, Minecraft itself and NeoForge.
 *
 * Each entry gets a monotonically increasing sequence number so a client can poll
 * with {@code since=<seq>} and only receive new lines.
 */
public final class LogBuffer extends AbstractAppender {

    public record Entry(long seq, long time, String level, String logger, String thread,
                        String message, String throwable) {}

    private static final LogBuffer INSTANCE = new LogBuffer();

    private final ArrayDeque<Entry> ring = new ArrayDeque<>();
    private int capacity = 5000;
    private long nextSeq = 1;

    private LogBuffer() {
        super("DevBridgeLogBuffer", null, null, true, Property.EMPTY_ARRAY);
    }

    public static void install() {
        if (!INSTANCE.isStarted()) {
            INSTANCE.start();
            Logger root = (Logger) LogManager.getRootLogger();
            root.addAppender(INSTANCE);
        }
    }

    public static LogBuffer get() {
        return INSTANCE;
    }

    public synchronized void setCapacity(int capacity) {
        this.capacity = Math.max(100, capacity);
        while (ring.size() > this.capacity) ring.pollFirst();
    }

    @Override
    public void append(LogEvent event) {
        String thrown = null;
        if (event.getThrown() != null) {
            StringWriter sw = new StringWriter();
            event.getThrown().printStackTrace(new PrintWriter(sw));
            thrown = sw.toString();
        }
        Entry entry;
        synchronized (this) {
            entry = new Entry(nextSeq++, event.getTimeMillis(), event.getLevel().name(),
                    event.getLoggerName(), event.getThreadName(),
                    event.getMessage().getFormattedMessage(), thrown);
            ring.addLast(entry);
            if (ring.size() > capacity) ring.pollFirst();
        }
    }

    public synchronized long latestSeq() {
        return nextSeq - 1;
    }

    /** Returns up to {@code limit} newest entries matching the filter, in chronological order. */
    public synchronized List<Entry> query(Predicate<Entry> filter, int limit) {
        List<Entry> out = new ArrayList<>();
        var it = ring.descendingIterator();
        while (it.hasNext() && out.size() < limit) {
            Entry e = it.next();
            if (filter.test(e)) out.add(e);
        }
        java.util.Collections.reverse(out);
        return out;
    }

    public synchronized void clear() {
        ring.clear();
    }
}
