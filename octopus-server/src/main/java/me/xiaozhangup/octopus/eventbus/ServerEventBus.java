package me.xiaozhangup.octopus.eventbus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import me.xiaozhangup.octopus.hamster.HamsterContext;
import net.minecraft.server.MinecraftServer;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

/** The lock protects registration and queue state, never user callbacks or futures. */
public final class ServerEventBus implements EventBus {
    private final MinecraftServer server;
    private final Object lock = new Object();
    private final Map<String, List<Registration>> subscriptions = new HashMap<>();
    private final Set<Pending> pending = new LinkedHashSet<>();
    private boolean closed;

    public ServerEventBus(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public Subscription subscribe(Plugin owner, String topic, Consumer<? super BusEvent> listener) {
        Objects.requireNonNull(owner, "owner");
        BusEvent.validateTopic(topic);
        Objects.requireNonNull(listener, "listener");
        synchronized (lock) {
            requireOpen(owner);
            Registration registration = new Registration(owner, topic, listener);
            List<Registration> updated = new ArrayList<>(subscriptions.getOrDefault(topic, List.of()));
            updated.add(registration);
            subscriptions.put(topic, List.copyOf(updated));
            return registration;
        }
    }

    @Override
    public void unsubscribeAll(Plugin owner) {
        Objects.requireNonNull(owner, "owner");
        synchronized (lock) {
            removeSubscriptions(owner);
        }
    }

    @Override
    public DispatchResult publish(Plugin source, String topic, UUID playerId, Map<String, ?> data) {
        requireDispatchThread();
        Objects.requireNonNull(source, "source");
        BusEvent event = new BusEvent(topic, source.getName(), playerId, data);
        synchronized (lock) {
            requireOpen(source);
        }
        return dispatch(event);
    }

    @Override
    public CompletableFuture<DispatchResult> post(Plugin source, String topic, UUID playerId, Map<String, ?> data) {
        Objects.requireNonNull(source, "source");
        BusEvent event = new BusEvent(topic, source.getName(), playerId, data);
        CompletableFuture<DispatchResult> result = new CompletableFuture<>();
        synchronized (lock) {
            requireOpen(source);
            pending.add(new Pending(source, event, result));
        }
        return result;
    }

    /** Called once per tick before any parallel world tasks are submitted. */
    public void drain() {
        requireDispatchThread();
        List<Pending> batch;
        synchronized (lock) {
            batch = List.copyOf(pending);
        }
        for (Pending message : batch) {
            synchronized (lock) {
                // Plugin disable/shutdown can remove later messages while a callback runs.
                if (!pending.remove(message)) continue;
            }
            if (message.result.isCancelled()) continue;
            if (!message.source.isEnabled()) {
                message.result.cancel(false);
                continue;
            }
            message.result.complete(dispatch(message.event));
        }
    }

    /** Called from the plugin manager after disabling the plugin. */
    public void disablePlugin(Plugin owner) {
        List<Pending> removed = new ArrayList<>();
        synchronized (lock) {
            removeSubscriptions(owner);
            pending.removeIf(message -> {
                if (message.source != owner) return false;
                removed.add(message);
                return true;
            });
        }
        removed.forEach(message -> message.result.cancel(false));
    }

    public void shutdown() {
        List<Pending> removed;
        synchronized (lock) {
            closed = true;
            subscriptions.values().forEach(list -> list.forEach(registration -> registration.active = false));
            subscriptions.clear();
            removed = List.copyOf(pending);
            pending.clear();
        }
        removed.forEach(message -> message.result.cancel(false));
    }

    private DispatchResult dispatch(BusEvent event) {
        List<Registration> listeners;
        synchronized (lock) {
            listeners = subscriptions.getOrDefault(event.topic(), List.of());
        }
        int delivered = 0;
        int failed = 0;
        for (Registration registration : listeners) {
            if (!registration.active || !registration.owner.isEnabled()) continue;
            try (var context = HamsterContext.plugin(registration.owner, "eventbus")) {
                registration.listener.accept(event);
                delivered++;
            } catch (Throwable throwable) {
                failed++;
                registration.owner.getLogger().log(Level.SEVERE,
                    "EventBus listener failed for " + event.topic() + " from " + event.source(), throwable);
            }
        }
        return new DispatchResult(delivered, failed);
    }

    private void requireDispatchThread() {
        // Bukkit.isPrimaryThread() also accepts Octopus world tick workers.
        if (Thread.currentThread() != server.getRunningThread() || server.isIteratingOverLevels) {
            throw new IllegalStateException("EventBus.publish requires the server main thread outside parallel world ticking; use post");
        }
    }

    private void requireOpen(Plugin owner) {
        if (closed) throw new IllegalStateException("EventBus is shut down");
        if (!owner.isEnabled()) {
            throw new IllegalPluginAccessException("Disabled plugin cannot use EventBus: " + owner.getName());
        }
    }

    private void removeSubscriptions(Plugin owner) {
        var iterator = subscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            List<Registration> remaining = new ArrayList<>();
            for (Registration registration : entry.getValue()) {
                if (registration.owner == owner) registration.active = false;
                else remaining.add(registration);
            }
            if (remaining.isEmpty()) iterator.remove();
            else entry.setValue(List.copyOf(remaining));
        }
    }

    private final class Registration implements Subscription {
        private final Plugin owner;
        private final String topic;
        private final Consumer<? super BusEvent> listener;
        private volatile boolean active = true;

        private Registration(Plugin owner, String topic, Consumer<? super BusEvent> listener) {
            this.owner = owner;
            this.topic = topic;
            this.listener = listener;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public void close() {
            synchronized (lock) {
                if (!active) return;
                active = false;
                List<Registration> remaining = new ArrayList<>(subscriptions.get(topic));
                remaining.remove(this);
                if (remaining.isEmpty()) subscriptions.remove(topic);
                else subscriptions.put(topic, List.copyOf(remaining));
            }
        }
    }

    private record Pending(Plugin source, BusEvent event, CompletableFuture<DispatchResult> result) {}
}
