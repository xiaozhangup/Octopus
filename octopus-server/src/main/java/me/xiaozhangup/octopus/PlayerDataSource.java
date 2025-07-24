package me.xiaozhangup.octopus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.io.IOException;
import java.util.Optional;

public interface PlayerDataSource {
    boolean save(Player player, CompoundTag data) throws IOException;
    Optional<CompoundTag> load(Player player) throws IOException;
    Optional<CompoundTag> load(String playerName, String uuid) throws IOException;
}
