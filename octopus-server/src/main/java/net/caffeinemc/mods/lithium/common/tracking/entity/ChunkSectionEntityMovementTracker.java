package net.caffeinemc.mods.lithium.common.tracking.entity;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public abstract class ChunkSectionEntityMovementTracker {
    protected long lastChangeTime = 0;
    protected final ReferenceOpenHashSet<HopperBlockEntity> listeners = new ReferenceOpenHashSet<>();
    protected final ChunkSectionIdentifier identifier;
    protected int userCount = 0;

    public ChunkSectionEntityMovementTracker(long sectionKey, UUID levelId) {
        identifier = new ChunkSectionIdentifier(sectionKey, levelId);
    }

    public void register() {
        this.userCount++;
    }

    public abstract void unregister();

    public static void unregister(ChunkSectionEntityMovementTracker @NotNull [] trackers) { // Leaf - Replace Lithium tracker list with array
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            tracker.unregister();
        }
    }

    public boolean isUnchangedSince(long lastCheckedTime) {
        return this.lastChangeTime <= lastCheckedTime;
    }

    public static boolean isUnchangedSince(long lastCheckedTime, ChunkSectionEntityMovementTracker @NotNull [] trackers) { // Leaf - Replace Lithium tracker list with array
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            if (!tracker.isUnchangedSince(lastCheckedTime)) {
                return false;
            }
        }
        return true;
    }

    public void listenToEntityMovementOnce(HopperBlockEntity listener) {
        this.listeners.add(listener);
    }

    public static void listenToEntityMovementOnce(HopperBlockEntity listener, ChunkSectionEntityMovementTracker @NotNull [] trackers) { // Leaf - Replace Lithium tracker list with array
        for (ChunkSectionEntityMovementTracker tracker : trackers) {
            tracker.listenToEntityMovementOnce(listener);
        }
    }

    private void setChanged(long atTime) {
        if (atTime > this.lastChangeTime) {
            this.lastChangeTime = atTime;
        }
    }

    public void notifyAllListeners(long time) {
        if (!listeners.isEmpty()) {
            for (HopperBlockEntity listener : listeners) {
                listener.wakeUpNow();
            }
            listeners.clear();
        }
        setChanged(time);
    }

    public record ChunkSectionIdentifier(long sectionKey, UUID levelId) {
    }
}
