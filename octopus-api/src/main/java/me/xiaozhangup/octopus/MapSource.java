package me.xiaozhangup.octopus;

import java.util.Optional;

public interface MapSource {
    int getNextMapId();
    Optional<byte[]> getMapData(int mapId);
    void saveMapData(int mapId, byte[] data);
    void setDirty(int mapId);
}
