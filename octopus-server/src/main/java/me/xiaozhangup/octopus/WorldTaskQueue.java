package me.xiaozhangup.octopus;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public class WorldTaskQueue implements Executor {
    private final MultiThreadedQueue<Runnable> internalTaskQueue = new MultiThreadedQueue<>();

    private final ConcurrentMap<String, Runnable> cyclicalTasks = new ConcurrentHashMap<>();
    private final ServerLevel world;

    private Thread currentOwner;
    private static final VarHandle OWNER_HANDLE = ConcurrentUtil.getVarHandle(WorldTaskQueue.class, "currentOwner", Thread.class);

    public WorldTaskQueue(ServerLevel world) {
        this.world = world;
    }

    public void acquirePoller() {
        if (!OWNER_HANDLE.compareAndSet(this, null, Thread.currentThread())) {
            final Thread owner = (Thread) OWNER_HANDLE.getVolatile(this);

            throw new IllegalStateException("Task queue already owned by " + owner);
        }
    }

    public void submitCyclicalTask(String id, Runnable task) {
        this.cyclicalTasks.put(id, task);
    }

    public Runnable removeCyclicalTask(String id) {
        return this.cyclicalTasks.remove(id);
    }

    public void submitTask(Runnable runnable) {
        this.internalTaskQueue.offer(runnable);
        this.tryNotifyOwner();
    }

    public int taskQueueLength() {
        return this.internalTaskQueue.size();
    }

    public boolean pollTasks() {
        if (!this.isOwnedByCurrentThread()) {
            return false;
        }

        Runnable runnable = this.internalTaskQueue.poll();

        if (runnable != null) {

            // TODO: try-catch?
            runnable.run();

            return true;
        }

        if (this.world.getChunkSource().runDistanceManagerUpdates()) {
            return true;
        }

        return this.world.moonrise$getChunkTaskScheduler().executeMainThreadTask();
    }

    public void spinWait(@NotNull BooleanSupplier stopCondition) {
        while (!stopCondition.getAsBoolean()) {
            if (!this.pollTasks()) {
                Thread.yield();
                LockSupport.parkNanos(1_000);
            }
        }
    }

    public void tryNotifyOwner() {
        Thread currentHeld = (Thread) OWNER_HANDLE.getVolatile(this);

        if (currentHeld != null) {
            LockSupport.unpark(currentHeld);
        }
    }

    public void releasePoller() {
        final Thread currentThread = Thread.currentThread();

        if (!OWNER_HANDLE.compareAndSet(this, currentThread, null)) {
            throw new IllegalStateException("Thread " + OWNER_HANDLE.getVolatile(this) + " has already owned this queue !");
        }
    }

    public boolean isOwnedByCurrentThread() {
        return OWNER_HANDLE.getVolatile(this) == Thread.currentThread();
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (this.internalTaskQueue.offer(command)) {
            this.tryNotifyOwner();
        }
    }
}