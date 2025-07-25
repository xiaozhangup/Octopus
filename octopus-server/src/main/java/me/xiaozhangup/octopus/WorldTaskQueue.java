package me.xiaozhangup.octopus;

import ca.spottedleaf.concurrentutil.collection.MultiThreadedQueue;
import ca.spottedleaf.concurrentutil.util.ConcurrentUtil;
import com.mojang.logging.LogUtils;
import net.minecraft.DefaultUncaughtExceptionHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.lang.invoke.VarHandle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

public class WorldTaskQueue implements Executor {
    private final Thread.UncaughtExceptionHandler exceptionHandler = new DefaultUncaughtExceptionHandler(LogUtils.getLogger());
    private final MultiThreadedQueue<Runnable> internalTaskQueue = new MultiThreadedQueue<>();

    private final MultiThreadedQueue<Runnable> scopedTasks = new MultiThreadedQueue<>();
    private final MultiThreadedQueue<Runnable> callbackTasks = new MultiThreadedQueue<>();
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

    public void submitScopedTask(Runnable runnable) {
        this.scopedTasks.offer(runnable);
        this.tryNotifyOwner();
    }

    public void submitCallbackTask(Runnable task) {
        this.callbackTasks.offer(task);
    }

    public void submitCyclicalTask(String id, Runnable task) {
        this.cyclicalTasks.put(id, task);
    }

    public Runnable removeCyclicalTask(String id) {
        return this.cyclicalTasks.remove(id);
    }

    public void finalizeCallbackTasks() {
        if (anyThreadHolding())
            throw new IllegalStateException("Queue doesn't get out of using state!");

        Runnable task;
        while ((task = this.callbackTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable e){
                this.exceptionHandler.uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    public void finalizeScopedTasks() {
        if (!this.isFullyHeldByCurrentThread()) {
            throw new IllegalStateException("Task queue not owned by current thread!");
        }

        Runnable task;
        while ((task = this.scopedTasks.poll()) != null) {
            try {
                task.run();
            } catch (Throwable e){
                this.exceptionHandler.uncaughtException(Thread.currentThread(), e);
            }
        }
    }

    public void finalizeCyclicalTasks() {
        cyclicalTasks.forEach((id, task) -> {
            try {
                task.run();
            } catch (Throwable e){
                MinecraftServer.LOGGER.error("Cyclical task {} failed in {}", id, this.world.getWorld().getName());
                this.exceptionHandler.uncaughtException(Thread.currentThread(), e);
            }
        });
    }

    public void submitTask(Runnable runnable) {
        this.internalTaskQueue.offer(runnable);
        this.tryNotifyOwner();
    }

    public int taskQueueLength() {
        return this.internalTaskQueue.size();
    }

    public boolean pollTasks() {
        if (this.anyThreadHolding() && !this.isOwnedByCurrentThread()) {
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

    public boolean anyThreadHolding() {
        return OWNER_HANDLE.getVolatile(this) != null;
    }

    public boolean isOwnedByCurrentThread() {
        return OWNER_HANDLE.getVolatile(this) == Thread.currentThread();
    }

    public boolean isFullyHeldByCurrentThread() {
        if (OWNER_HANDLE.getVolatile(this) == null)
            return false;

        return isOwnedByCurrentThread();
    }

    @Override
    public void execute(@NotNull Runnable command) {
        if (this.internalTaskQueue.offer(command)) {
            this.tryNotifyOwner();
        }
    }
}