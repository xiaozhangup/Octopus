package me.xiaozhangup.octopus;

import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public interface MapSource {
    int getNextMapId();
    @NotNull Optional<byte[]> getMapData(int mapId);
    void saveMapData(int mapId, byte @NotNull [] data);
}
