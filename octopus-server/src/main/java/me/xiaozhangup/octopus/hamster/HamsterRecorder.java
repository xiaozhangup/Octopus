package me.xiaozhangup.octopus.hamster;

import com.google.common.hash.Hashing;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.status.StatusLogger;

/** Hamster performs no disk or Bukkit access on the logging path. */
public final class HamsterRecorder implements AutoCloseable {
    static final int SCHEMA_VERSION = 1;
    private static volatile HamsterRecorder instance;
    final String sessionId = UUID.randomUUID().toString();
    final String serverVersion;
    final ArrayBlockingQueue<ErrorRecord> queue = new ArrayBlockingQueue<>(1024);
    final AtomicLong dropped = new AtomicLong();
    private final ArrayDeque<Breadcrumb> breadcrumbs = new ArrayDeque<>(100);
    private final HamsterAppender appender;
    private final Thread writer;
    private final Logger root;
    private long sequence;
    volatile boolean accepting = true;

    private HamsterRecorder(Path directory, int retentionDays, long maxDiskBytes, String serverVersion) throws IOException {
        this.serverVersion = serverVersion;
        this.root = (Logger) LogManager.getRootLogger();
        this.appender = new HamsterAppender(this);
        HamsterStore store = new HamsterStore(this, directory, retentionDays, maxDiskBytes);
        this.writer = new Thread(store, "Hamster writer");
        this.writer.setDaemon(true);
        this.writer.start();
        this.appender.start();
        this.root.addAppender(this.appender);
    }

    public static synchronized void start(boolean enabled, String directory, int retentionDays, int maxDiskMiB, String serverVersion) {
        if (!enabled) return;
        try {
            if (retentionDays < 1 || maxDiskMiB < 16) {
                throw new IllegalArgumentException("Hamster retention-days must be positive and max-disk-mib must be at least 16");
            }
            instance = new HamsterRecorder(Path.of(directory), retentionDays, maxDiskMiB * 1024L * 1024L, serverVersion);
        } catch (IOException | IllegalArgumentException e) {
            StatusLogger.getLogger().error("Cannot start Hamster", e);
        }
    }

    public static boolean isActive() {
        HamsterRecorder current = instance;
        return current != null && current.accepting;
    }

    public static synchronized void stop() {
        if (instance == null) return;
        instance.close();
        instance = null;
    }

    synchronized void accept(LogEvent event) {
        if (!this.accepting) return;
        String message = clip(event.getMessage().getFormattedMessage(), 2048);
        if (event.getThrown() == null || !event.getLevel().isMoreSpecificThan(Level.WARN)) {
            if (this.breadcrumbs.size() == 100) this.breadcrumbs.removeFirst();
            this.breadcrumbs.addLast(new Breadcrumb(event.getTimeMillis(), clip(event.getThreadName(), 128),
                event.getLevel().name(), clip(event.getLoggerName(), 256), clip(message, 256)));
            return;
        }
        Map<String, String> context = new LinkedHashMap<>();
        event.getContextData().forEach((key, value) -> {
            if (key.startsWith(HamsterContext.PREFIX) && context.size() < 16) {
                context.put(key.substring(HamsterContext.PREFIX.length()), clip(String.valueOf(value), 256));
            }
        });
        if (event.getThrown() instanceof HamsterWorldException failure) {
            context.put("source", "world.tick");
            context.put("world", clip(failure.world, 256));
            context.put("dimension", clip(failure.dimension, 256));
            context.put("world.thread", clip(failure.worldThread, 128));
        }
        StringBuilder stack = new StringBuilder();
        StringBuilder signature = new StringBuilder();
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        appendThrowable(event.getThrown(), "", stack, signature, visited, 0);
        String group = this.serverVersion + '\n' + context.getOrDefault("plugin.name", "") + '\n'
            + context.getOrDefault("plugin.version", "") + '\n' + context.getOrDefault("source", "log") + '\n'
            + context.getOrDefault("event", "") + '\n' + signature;
        String fingerprint = Hashing.sha256().hashString(group, StandardCharsets.UTF_8).toString();
        // Keep the preceding global log window, with its original timestamps and threads.
        List<Breadcrumb> recent = new ArrayList<>();
        int remaining = 6000;
        for (var iterator = this.breadcrumbs.descendingIterator(); iterator.hasNext() && remaining > 0;) {
            Breadcrumb entry = iterator.next();
            String text = clip(entry.message(), Math.min(256, remaining));
            recent.add(new Breadcrumb(entry.time(), entry.thread(), entry.level(), entry.logger(), text));
            remaining -= text.length() + entry.thread().length() + entry.logger().length() + 64;
        }
        Collections.reverse(recent);
        ErrorRecord record = new ErrorRecord(++this.sequence, event.getTimeMillis(), event.getLevel().name(),
            clip(event.getLoggerName(), 256), clip(event.getThreadName(), 128), message,
            clip(event.getThrown().getClass().getName(), 256), fingerprint,
            Map.copyOf(context), clip(stack.toString(), 12000), stack.length() > 12000 || stack.indexOf("[Hamster:") >= 0,
            List.copyOf(recent));
        if (!this.queue.offer(record)) this.dropped.incrementAndGet();
    }

    private static void appendThrowable(Throwable throwable, String prefix, StringBuilder stack, StringBuilder signature,
                                        Set<Throwable> visited, int depth) {
        if (depth >= 16 || visited.size() >= 32 || stack.length() > 12000) {
            stack.append("\n[Hamster: stack truncated]\n");
            return;
        }
        if (!visited.add(throwable)) {
            stack.append(prefix).append("[circular exception reference]\n");
            return;
        }
        String type = throwable.getClass().getName();
        stack.append(prefix).append(type);
        if (throwable.getMessage() != null) stack.append(": ").append(clip(throwable.getMessage(), 1024));
        stack.append('\n');
        signature.append(type).append('\n');
        int count = 0;
        for (StackTraceElement frame : throwable.getStackTrace()) {
            if (++count > 128 || stack.length() > 12000) {
                stack.append("\t[Hamster: frames truncated]\n");
                break;
            }
            stack.append("\tat ").append(frame).append('\n');
            signature.append(frame.getClassName()).append('#').append(frame.getMethodName()).append('\n');
        }
        for (Throwable suppressed : throwable.getSuppressed()) {
            if (stack.length() > 12000) break;
            appendThrowable(suppressed, "Suppressed: ", stack, signature, visited, depth + 1);
        }
        if (throwable.getCause() != null) appendThrowable(throwable.getCause(), "Caused by: ", stack, signature, visited, depth + 1);
    }

    static String clip(String value, int length) {
        return value.length() <= length ? value : value.substring(0, Math.max(0, length - 1)) + "…";
    }

    @Override
    public void close() {
        this.root.removeAppender(this.appender);
        this.appender.stop();
        synchronized (this) {
            this.accepting = false;
        }
        try {
            this.writer.join(2000);
            if (this.writer.isAlive()) StatusLogger.getLogger().warn("Hamster shutdown deadline reached; queued records may be lost");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    record Breadcrumb(long time, String thread, String level, String logger, String message) {}
    record ErrorRecord(long sequence, long time, String level, String logger, String thread, String message,
                       String exception, String fingerprint, Map<String, String> context, String stack,
                       boolean truncated, List<Breadcrumb> breadcrumbs) {}
}
