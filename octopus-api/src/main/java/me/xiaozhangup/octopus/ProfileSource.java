package me.xiaozhangup.octopus;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Optional;

public interface ProfileSource {
    boolean save(@NotNull Player player, byte @NotNull [] data) throws IOException;
    @NotNull Optional<byte[]> load(@NotNull String playerName, @NotNull String uuid) throws IOException;
}
