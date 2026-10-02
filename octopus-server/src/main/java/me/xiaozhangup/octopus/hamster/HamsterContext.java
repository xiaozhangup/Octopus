package me.xiaozhangup.octopus.hamster;

import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.logging.log4j.ThreadContext;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.world.WorldEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitTask;

/** Context belongs to the originating thread and is copied before leaving it. */
public final class HamsterContext implements AutoCloseable {
    static final String PREFIX = "hamster.";
    private static final HamsterContext EMPTY = new HamsterContext(Map.of());
    private final Map<String, String> previous;

    private HamsterContext(Map<String, String> values) {
        this.previous = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            String name = PREFIX + key;
            this.previous.put(name, ThreadContext.get(name));
            ThreadContext.put(name, value);
        });
    }

    public static HamsterContext plugin(Plugin plugin, String source) {
        if (!HamsterRecorder.isActive()) return EMPTY;
        return new HamsterContext(pluginValues(plugin, source));
    }

    public static HamsterContext event(Event event, RegisteredListener listener) {
        if (!HamsterRecorder.isActive()) return EMPTY;
        Map<String, String> values = pluginValues(listener.getPlugin(), "event");
        values.put("event", event.getClass().getName());
        values.put("listener", listener.getListener().getClass().getName());
        values.put("async", Boolean.toString(event.isAsynchronous()));
        if (event instanceof PlayerEvent playerEvent) {
            Player player = playerEvent.getPlayer();
            values.put("player.uuid", player.getUniqueId().toString());
            values.put("player.name", player.getName());
        } else if (event instanceof BlockEvent blockEvent) {
            var block = blockEvent.getBlock();
            values.put("world", block.getWorld().getName());
            values.put("block", block.getX() + ", " + block.getY() + ", " + block.getZ());
        } else if (event instanceof EntityEvent entityEvent) {
            var entity = entityEvent.getEntity();
            values.put("entity.uuid", entity.getUniqueId().toString());
            values.put("entity.type", entity.getType().name());
        } else if (event instanceof WorldEvent worldEvent) {
            values.put("world", worldEvent.getWorld().getName());
        }
        return new HamsterContext(values);
    }

    public static HamsterContext task(BukkitTask task) {
        if (!HamsterRecorder.isActive()) return EMPTY;
        Map<String, String> values = pluginValues(task.getOwner(), "task");
        values.put("task.id", Integer.toString(task.getTaskId()));
        values.put("async", Boolean.toString(!task.isSync()));
        return new HamsterContext(values);
    }

    public static HamsterContext world(String world, String dimension) {
        if (!HamsterRecorder.isActive()) return EMPTY;
        return new HamsterContext(Map.of("world", world, "dimension", dimension));
    }

    private static Map<String, String> pluginValues(Plugin plugin, String source) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("source", source);
        values.put("plugin.name", plugin.getPluginMeta().getName());
        values.put("plugin.version", plugin.getPluginMeta().getVersion());
        return values;
    }

    @Override
    public void close() {
        this.previous.forEach((key, value) -> {
            if (value == null) ThreadContext.remove(key);
            else ThreadContext.put(key, value);
        });
    }
}
