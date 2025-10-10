package blaze;

import clojure.lang.Util;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

public final class Interners {

    private Interners() {
    }

    public static <K, V> Interner<K, V> strongInterner(Function<K, V> creator) {
        return new StrongInterner<>(creator);
    }

    public static <K, V> Interner<K, V> weakInterner(Function<K, V> creator) {
        return new WeakInterner<>(creator);
    }

    private static final class StrongInterner<K, V> implements Interner<K, V> {

        private final ConcurrentHashMap<K, V> table = new ConcurrentHashMap<>();
        private final Function<K, V> creator;

        private StrongInterner(Function<K, V> creator) {
            this.creator = requireNonNull(creator);
        }

        @Override
        public V intern(K key) {
            V existingVal = table.get(key);
            if (existingVal != null) return existingVal;
            V newVal = creator.apply(key);
            existingVal = table.putIfAbsent(key, newVal);
            return existingVal == null ? newVal : existingVal;
        }
    }

    private static final class WeakInterner<K, V> implements Interner<K, V> {

        private final ConcurrentHashMap<K, Reference<V>> table = new ConcurrentHashMap<>();
        private final ReferenceQueue<V> rq = new ReferenceQueue<>();
        private final Function<K, V> creator;

        private WeakInterner(Function<K, V> creator) {
            this.creator = requireNonNull(creator);
        }

        @Override
        public V intern(K key) {
            while (true) {
                Reference<V> existingRef = table.get(key);
                if (existingRef == null) {
                    Util.clearCache(rq, table);
                    V newVal = creator.apply(key);
                    existingRef = table.putIfAbsent(key, new WeakReference<>(newVal, rq));
                    if (existingRef == null) {
                        return newVal;
                    }
                }
                V existingVal = existingRef.get();
                if (existingVal != null) {
                    return existingVal;
                }
                //entry died in the interim, do over
                table.remove(key, existingRef);
            }
        }
    }
}
