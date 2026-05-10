package me.xiaozhangup.octopus;

import net.minecraft.server.level.ServerLevel;
import org.apache.commons.lang3.tuple.Pair;

import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;

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

    public String createTimeoutReport() {
        final Thread runningThread = this.runningThread;
        if (runningThread == null) {
            return "World tick task has not completed after 2 seconds: " + this.getLevelName();
        }

        final StringBuilder stack = new StringBuilder();
        stack.append("World tick task has not completed after 2 seconds: ")
            .append(this.getLevelName())
            .append(System.lineSeparator())
            .append("Thread: ")
            .append(runningThread.getName())
            .append(" | State: ")
            .append(runningThread.getState());
        for (final StackTraceElement element : runningThread.getStackTrace()) {
            stack.append(System.lineSeparator()).append("\tat ").append(element);
        }
        return stack.toString();
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