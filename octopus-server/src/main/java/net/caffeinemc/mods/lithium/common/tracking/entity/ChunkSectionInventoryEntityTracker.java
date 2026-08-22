package net.caffeinemc.mods.lithium.common.tracking.entity;

import ca.spottedleaf.moonrise.common.util.CoordinateUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import net.caffeinemc.mods.lithium.common.util.tuples.WorldSectionBox;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkSectionInventoryEntityTracker extends ChunkSectionEntityMovementTracker {
    public static final Map<ChunkSectionIdentifier, ChunkSectionInventoryEntityTracker> containerEntityMovementTrackerMap = new ConcurrentHashMap<>();

    public ChunkSectionInventoryEntityTracker(long sectionKey, UUID levelId) {
        super(sectionKey, levelId);
    }

    @Override
    public void unregister() {
        this.userCount--;
        if (this.userCount <= 0) {
            containerEntityMovementTrackerMap.remove(identifier);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static @NotNull List<Container> getEntities(@NotNull Level level, AABB boundingBox) {
        return level.getEntitiesOfClass((Class) Container.class, boundingBox, EntitySelector.CONTAINER_ENTITY_SELECTOR);
    }

    // Leaf start - Replace Lithium tracker list with array
    public static ChunkSectionInventoryEntityTracker[] registerAt(ServerLevel world, AABB interactionArea) {
        WorldSectionBox worldSectionBox = WorldSectionBox.entityAccessBox(interactionArea);
        UUID levelId = world.uuid;

        int count = 0;
        int sizeX = worldSectionBox.chunkX2() - worldSectionBox.chunkX1();
        int sizeY = worldSectionBox.chunkY2() - worldSectionBox.chunkY1();
        int sizeZ = worldSectionBox.chunkZ2() - worldSectionBox.chunkZ1();
        ChunkSectionInventoryEntityTracker[] trackers = new ChunkSectionInventoryEntityTracker[sizeX * sizeY * sizeZ];

        for (int x = worldSectionBox.chunkX1(); x < worldSectionBox.chunkX2(); x++) {
            for (int y = worldSectionBox.chunkY1(); y < worldSectionBox.chunkY2(); y++) {
                for (int z = worldSectionBox.chunkZ1(); z < worldSectionBox.chunkZ2(); z++) {
                    trackers[count++] = registerAt(CoordinateUtils.getChunkSectionKey(x, y, z), levelId);
                }
            }
        }
        assert count == trackers.length;

        return trackers;
        // Leaf end - Replace Lithium tracker list with array
    }

    private static @NotNull ChunkSectionInventoryEntityTracker registerAt(long key, UUID levelId) {
        ChunkSectionInventoryEntityTracker tracker = containerEntityMovementTrackerMap.computeIfAbsent(
            new ChunkSectionIdentifier(key, levelId),
            k -> new ChunkSectionInventoryEntityTracker(key, levelId)
        );
        tracker.register();
        return tracker;
    }
}
