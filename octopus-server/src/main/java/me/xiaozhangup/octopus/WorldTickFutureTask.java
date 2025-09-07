package me.xiaozhangup.octopus;

import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

public class WorldTickFutureTask extends FutureTask<Pair<ServerLevel, Throwable>> {
    private final ServerLevel level;

    public WorldTickFutureTask(ServerLevel level, Callable<Pair<ServerLevel, Throwable>> callable) {
        super(callable);
        this.level = level;
    }

    public String getLevelName() {
        return level.dimension().location().toString();
    }

    @Override
    public String toString() {
        return "WorldTickFutureTask{" + level.dimension().location() + "}";
    }
}