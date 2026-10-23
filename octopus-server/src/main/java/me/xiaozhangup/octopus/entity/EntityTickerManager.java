package me.xiaozhangup.octopus.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.bukkit.craftbukkit.entity.CraftEntityType;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.plugin.Plugin;

public final class EntityTickerManager {
    private volatile Map<EntityType<?>, List<Registration>> registrations = Map.of();
    private volatile long currentTick;

    public synchronized EntityTicker register(Plugin plugin, org.bukkit.entity.EntityType type,
                                             long initialDelay, long period,
                                             Consumer<? super org.bukkit.entity.Entity> callback) {
        Objects.requireNonNull(plugin, "Plugin may not be null");
        Objects.requireNonNull(type, "Entity type may not be null");
        Objects.requireNonNull(callback, "Callback may not be null");
        if (initialDelay < 1 || period < 1) {
            throw new IllegalArgumentException("Initial delay and period must be positive");
        }
        if (!plugin.isEnabled()) {
            throw new IllegalPluginAccessException("Plugin attempted to register an entity ticker while disabled");
        }

        EntityType<?> entityType = CraftEntityType.bukkitToMinecraft(type);
        Registration registration = new Registration(plugin, entityType, this.currentTick, initialDelay, period, callback);
        Map<EntityType<?>, List<Registration>> updated = new HashMap<>(this.registrations);
        List<Registration> callbacks = new ArrayList<>(updated.getOrDefault(entityType, List.of()));
        callbacks.add(registration);
        updated.put(entityType, List.copyOf(callbacks));
        this.registrations = Map.copyOf(updated);
        return registration;
    }

    /** Advances the shared clock before the server schedules world ticks. */
    public void beginTick() {
        this.currentTick++;
    }

    public void tick(Entity entity) {
        List<Registration> callbacks = this.registrations.get(entity.getType());
        if (callbacks == null) return;

        long tick = this.currentTick;
        for (Registration registration : callbacks) {
            // Earlier callbacks, or the entity's own tick, may have removed it.
            if (entity.isRemoved()) break;
            long elapsed = tick - registration.createdAt;
            if (!registration.registered || !registration.plugin.isEnabled()
                || elapsed < registration.initialDelay
                || (elapsed - registration.initialDelay) % registration.period != 0) continue;

            try {
                registration.callback.accept(entity.getBukkitEntity());
            } catch (Throwable throwable) {
                registration.plugin.getLogger().log(Level.SEVERE,
                    "Entity ticker for " + entity.getType().toShortString() + " generated an exception", throwable);
            }
        }
    }

    public synchronized void unregisterAll(Plugin plugin) {
        for (List<Registration> callbacks : this.registrations.values()) {
            for (Registration registration : callbacks) {
                if (registration.plugin == plugin) registration.unregister();
            }
        }
    }

    private synchronized void unregister(Registration registration) {
        if (!registration.registered) return;
        registration.registered = false;
        Map<EntityType<?>, List<Registration>> updated = new HashMap<>(this.registrations);
        List<Registration> callbacks = new ArrayList<>(updated.get(registration.type));
        callbacks.remove(registration);
        if (callbacks.isEmpty()) updated.remove(registration.type);
        else updated.put(registration.type, List.copyOf(callbacks));
        this.registrations = Map.copyOf(updated);
    }

    private final class Registration implements EntityTicker {
        private final Plugin plugin;
        private final EntityType<?> type;
        private final long createdAt;
        private final long initialDelay;
        private final long period;
        private final Consumer<? super org.bukkit.entity.Entity> callback;
        private volatile boolean registered = true;

        private Registration(Plugin plugin, EntityType<?> type, long createdAt, long initialDelay, long period,
                             Consumer<? super org.bukkit.entity.Entity> callback) {
            this.plugin = plugin;
            this.type = type;
            this.createdAt = createdAt;
            this.initialDelay = initialDelay;
            this.period = period;
            this.callback = callback;
        }

        @Override
        public void unregister() {
            EntityTickerManager.this.unregister(this);
        }
    }
}
