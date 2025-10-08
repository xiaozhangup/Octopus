package me.xiaozhangup.octopus;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;

public interface WorldMapDataSource {
    int getNextMapId();
    Optional<CompoundTag> getMapData(int mapId);
    boolean saveMapData(int mapId, CompoundTag data);
}
