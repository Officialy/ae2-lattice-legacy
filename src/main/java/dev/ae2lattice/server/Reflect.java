package dev.ae2lattice.server;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

/** Tiny cached reflection helper for optional mod integrations (GregTech). Never throws. */
final class Reflect {
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    @Nullable
    static Object invoke(@Nullable Object target, String name) {
        if (target == null) return null;
        var cls = target.getClass();
        var m = METHODS.computeIfAbsent(cls.getName() + "#" + name, k -> {
            try {
                return Optional.of(cls.getMethod(name));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
        if (m.isEmpty()) return null;
        try {
            return m.get().invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static final Map<String, Optional<java.lang.reflect.Field>> FIELDS = new ConcurrentHashMap<>();

    @Nullable
    static Object field(@Nullable Object target, String name) {
        if (target == null) return null;
        var cls = target.getClass();
        var f = FIELDS.computeIfAbsent(cls.getName() + "." + name, k -> {
            try {
                return Optional.of(cls.getField(name));
            } catch (Throwable t) {
                return Optional.empty();
            }
        });
        if (f.isEmpty()) return null;
        try {
            return f.get().get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private Reflect() {}
}
