package me.xiaozhangup.octopus.eventbus;

/** A plugin-owned subscription, automatically closed when its plugin is disabled. */
public interface Subscription extends AutoCloseable {
    boolean isActive();

    /**
     * Unsubscribes from any thread. Repeated calls have no effect. An already
     * executing callback may finish after this method returns.
     */
    @Override
    void close();
}
