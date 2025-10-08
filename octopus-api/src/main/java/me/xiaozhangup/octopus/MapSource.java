package me.xiaozhangup.octopus;

import java.util.Optional;

public interface MapSource {
    int getNextMapId();
    Optional<byte[]> getMapData(int mapId);
    boolean saveMapData(int mapId, byte[] data);
}
