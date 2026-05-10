package me.xiaozhangup.octopus;

import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.Bukkit;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WorldTickFutureTask extends FutureTask<Pair<ServerLevel, Throwable>> {
    private final ServerLevel level;
    private volatile Thread runningThread;

    public WorldTickFutureTask(ServerLevel level, Callable<Pair<ServerLevel, Throwable>> callable) {
        super(callable);
        this.level = level;
    }

    public String getLevelName() {
        return level.dimension().identifier().toDebugFileName();
    }

    public void createTimeoutReport() {
        Logger logger = Bukkit.getServer().getLogger();
        logger.log(Level.WARNING,"World tick task has not completed after 2 seconds: " + this.getLevelName());

        final Thread runningThread = this.runningThread;
        if (runningThread == null) {
            return;
        }

        logger.log(Level.WARNING, "------------------------------");
        logger.log(Level.WARNING, "World thread dump (" + this.getLevelName() + ")");
        logger.log(Level.WARNING, "------------------------------");
        logger.log(Level.WARNING, "Thread: " + runningThread.getName());
        logger.log(Level.WARNING, "\tPID: " + runningThread.threadId()
                + " | Priority: " + runningThread.getPriority()
                + " | State: " + runningThread.getState());
        logger.log(Level.WARNING, "\tStack:");
        for (final StackTraceElement element : runningThread.getStackTrace()) {
            logger.log(Level.WARNING, "\t\tat " + element);
        }
        logger.log(Level.WARNING, "------------------------------");
    }

    @Override
    public void run() {
        this.runningThread = Thread.currentThread();
        try {
            super.run();
        } finally {
            this.runningThread = null;
        }
    }

    @Override
    public String toString() {
        return "WorldTickFutureTask{" + this.getLevelName() + "}";
    }
}