package me.xiaozhangup.octopus.eventbus;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An immutable business notification. Topics are case-sensitive, nonempty, and
 * contain only ASCII letters, digits, underscores and dots ({@code [A-Za-z0-9_.]+}).
 * The bus supplies the source plugin's name; playerId is optional.
 * <p>
 * Data supports String, Boolean, Byte, Short, Integer, Long, Float, Double,
 * BigInteger, BigDecimal, UUID, lists and string-keyed maps. Collections are
 * recursively copied. Nulls, cycles and other object types are rejected.
 * String values and data keys are not subject to the topic character restriction.
 */
public record BusEvent(
    @NotNull String topic,
    @NotNull String source,
    @Nullable UUID playerId,
    @NotNull Map<String, ?> data
) {
    private static final Pattern TOPIC = Pattern.compile("[A-Za-z0-9_.]+");

    public BusEvent {
        validateTopic(topic);
        Objects.requireNonNull(source, "source");
        data = copyMap(Objects.requireNonNull(data, "data"), new IdentityHashMap<>());
    }

    /** Validates a topic without registering or publishing it. */
    public static void validateTopic(@NotNull String topic) {
        Objects.requireNonNull(topic, "topic");
        if (!TOPIC.matcher(topic).matches()) {
            throw new IllegalArgumentException("EventBus topic must match [A-Za-z0-9_.]+: " + topic);
        }
    }

    private static Map<String, Object> copyMap(Map<?, ?> source, IdentityHashMap<Object, Boolean> path) {
        enter(source, path);
        Map<String, Object> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!(key instanceof String name)) {
                throw new IllegalArgumentException("EventBus data keys must be strings");
            }
            copy.put(name, copyValue(value, path));
        });
        path.remove(source);
        return Collections.unmodifiableMap(copy);
    }

    private static Object copyValue(Object value, IdentityHashMap<Object, Boolean> path) {
        if (value == null) {
            throw new IllegalArgumentException("EventBus data values may not be null");
        }
        Class<?> type = value.getClass();
        if (type == String.class || type == Boolean.class || type == Byte.class || type == Short.class
            || type == Integer.class || type == Long.class || type == Float.class || type == Double.class
            || type == BigInteger.class || type == BigDecimal.class || type == UUID.class) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            return copyMap(map, path);
        }
        if (value instanceof List<?> list) {
            enter(list, path);
            List<Object> copy = new ArrayList<>(list.size());
            for (Object element : list) copy.add(copyValue(element, path));
            path.remove(list);
            return Collections.unmodifiableList(copy);
        }
        throw new IllegalArgumentException("Unsupported EventBus data type: " + type.getName());
    }

    private static void enter(Object value, IdentityHashMap<Object, Boolean> path) {
        if (path.put(value, Boolean.TRUE) != null) {
            throw new IllegalArgumentException("EventBus data may not contain cycles");
        }
    }
}
