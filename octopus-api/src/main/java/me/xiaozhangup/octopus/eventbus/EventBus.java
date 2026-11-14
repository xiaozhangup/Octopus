package me.xiaozhangup.octopus.eventbus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Server-owned, in-process business notifications, available from
 * {@link org.bukkit.Bukkit#getEventBus()}. All listeners run on the actual server
 * main thread, outside parallel world ticking. Messages are not retained or replayed.
 * <p>
 * Topics match exactly and follow {@link BusEvent#validateTopic(String)}. Invalid
 * arguments fail immediately, including for post(). Registering or publishing
 * requires an enabled plugin and an open bus. Plugin disable removes subscriptions
 * and cancels its queued posts. Server shutdown closes the bus and cancels all posts.
 */
public interface EventBus {
    /** Counts successful and failed callbacks; zero listeners is a successful dispatch. */
    record DispatchResult(int delivered, int failed) {}

    /**
     * Registers an exact-topic listener from any thread. Listeners run in registration
     * order, with a snapshot taken at dispatch time. New registrations affect subsequent
     * dispatches, including nested publish() calls. Closed subscriptions are skipped.
     * Listener exceptions are logged and do not prevent subsequent listeners running.
     */
    @NotNull Subscription subscribe(@NotNull Plugin owner, @NotNull String topic,
                                   @NotNull Consumer<? super BusEvent> listener);

    /** Closes all subscriptions owned by a plugin, without cancelling its queued posts. */
    void unsubscribeAll(@NotNull Plugin owner);

    /**
     * Immediately dispatches on the server main thread. World tick workers and the
     * parallel-world phase are rejected with IllegalStateException; use post() there.
     * Nested publishes are dispatched immediately before the outer listener returns.
     */
    @NotNull DispatchResult publish(@NotNull Plugin source, @NotNull String topic,
                                    @Nullable UUID playerId, @NotNull Map<String, ?> data);

    /**
     * Snapshots data and enqueues from any thread. Dispatch occurs before parallel
     * world ticking, in enqueue order. Posts made during a drain wait for the next
     * drain. The future completes after all listeners, with failures counted in the
     * result, or is cancelled when the source plugin or bus stops. Cancelling the
     * future before dispatch skips delivery; it does not interrupt a running callback.
     * <p>
     * Do not wait for this future on a server/world tick thread: delivery needs the
     * main tick to continue. Future continuations may run on the completing thread;
     * cancellation can also complete it on a different thread.
     */
    @NotNull CompletableFuture<DispatchResult> post(@NotNull Plugin source, @NotNull String topic,
                                                   @Nullable UUID playerId, @NotNull Map<String, ?> data);

    default @NotNull DispatchResult publish(@NotNull Plugin source, @NotNull String topic) {
        return publish(source, topic, null, Map.of());
    }

    default @NotNull DispatchResult publish(@NotNull Plugin source, @NotNull String topic, @Nullable UUID playerId) {
        return publish(source, topic, playerId, Map.of());
    }

    default @NotNull DispatchResult publish(@NotNull Plugin source, @NotNull String topic, @NotNull Map<String, ?> data) {
        return publish(source, topic, null, data);
    }

    default @NotNull CompletableFuture<DispatchResult> post(@NotNull Plugin source, @NotNull String topic) {
        return post(source, topic, null, Map.of());
    }

    default @NotNull CompletableFuture<DispatchResult> post(@NotNull Plugin source, @NotNull String topic, @Nullable UUID playerId) {
        return post(source, topic, playerId, Map.of());
    }

    default @NotNull CompletableFuture<DispatchResult> post(@NotNull Plugin source, @NotNull String topic, @NotNull Map<String, ?> data) {
        return post(source, topic, null, data);
    }
}
