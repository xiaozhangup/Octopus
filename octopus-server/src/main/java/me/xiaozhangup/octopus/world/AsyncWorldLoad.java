package me.xiaozhangup.octopus.world;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.level.ServerLevel;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.Nullable;

public final class AsyncWorldLoad {
    public final WorldCreator creator;
    public final CompletableFuture<@Nullable World> future = new CompletableFuture<>();
    public final AtomicBoolean finished = new AtomicBoolean();
    public boolean reserved;
    public volatile @Nullable PreparedWorld prepared;
    public volatile @Nullable ServerLevel level;

    public AsyncWorldLoad(WorldCreator creator) {
        this.creator = creator;
    }
}
