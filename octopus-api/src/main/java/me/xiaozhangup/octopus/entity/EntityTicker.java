package me.xiaozhangup.octopus.entity;

/**
 * A registration for periodic callbacks on an exact entity type.
 *
 * @see org.bukkit.Server#registerEntityTicker
 */
public interface EntityTicker extends AutoCloseable {

    /**
     * Unregisters this callback. Repeated calls have no effect.
     * This method may be called from any thread. A callback already executing
     * on another world thread may finish after this method returns.
     */
    void unregister();

    @Override
    default void close() {
        this.unregister();
    }
}
