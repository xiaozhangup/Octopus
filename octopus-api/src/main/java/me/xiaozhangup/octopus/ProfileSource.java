package me.xiaozhangup.octopus;

import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.Optional;

public interface ProfileSource {
    boolean save(Player player, byte[] data) throws IOException;
    Optional<byte[]> load(Player player) throws IOException;
    Optional<byte[]> load(String playerName, String uuid) throws IOException;
}
