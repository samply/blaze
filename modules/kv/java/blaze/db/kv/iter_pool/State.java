package blaze.db.kv.iter_pool;

import clojure.lang.Keyword;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

/**
 * A state holding iterators that can be either borrowed or returned.
 * <p> 
 * Instances are mutable and are meant to be used inside a {@link ConcurrentHashMap#compute(Object, BiFunction)}
 * which ensures atomic updates. Users should only use the static methods {@link #borrowIterator},
 * {@link #returnIterator} and {@link #addIterator}.
 */
public final class State {

    private final List<AutoCloseable> allIterators = new ArrayList<>();
    private final Deque<AutoCloseable> returnedIterators = new ArrayDeque<>();

    private State() {
    }

    /**
     * Tries to borrow an iterator from {@code pool} for {@code columnFamily column family}.
     * <p>
     * Changes the state of {@code pool}. If no iterator is available, a new iterator has to be created outside the
     * state transition and added via {@link #addIterator(ConcurrentHashMap, Keyword, AutoCloseable) add iterator}.
     *
     * @param pool         the pool to borrow the iterator from
     * @param columnFamily the column family for which the iterator should be borrowed
     * @return an iterator or {@code null} if no iterator is available
     */
    public static AutoCloseable borrowIterator(ConcurrentHashMap<Keyword, State> pool, Keyword columnFamily) {
        var output = new AtomicReference<AutoCloseable>();
        pool.compute(columnFamily, (k, state) -> {
            if (state == null) return new State();
            var iterator = state.borrowIter();
            if (iterator != null) output.set(iterator);
            return state;
        });
        return output.get();
    }

    /**
     * Tries to return {@code iterator} to {@code pool} for {@code columnFamily column family}.
     * <p>
     * Changes the state of {@code pool}. Assumes that the iterator was borrowed before.
     *
     * @param pool         the pool to return the iterator to
     * @param columnFamily the column family for which the iterator should be returned
     * @param iterator     the iterator to return
     */
    public static void returnIterator(ConcurrentHashMap<Keyword, State> pool, Keyword columnFamily, AutoCloseable iterator) {
        pool.computeIfPresent(columnFamily, (k, state) -> {
            state.returnIter(iterator);
            return state;
        });
    }

    /**
     * Adds {@code iterator} to {@code pool} for {@code columnFamily column family}.
     * <p>
     * Changes the state of {@code pool}.
     *
     * @param pool         the pool to add the iterator to
     * @param columnFamily the column family for which the iterator should be added
     * @param iterator     the iterator to add
     */
    public static void addIterator(ConcurrentHashMap<Keyword, State> pool, Keyword columnFamily, AutoCloseable iterator) {
        pool.computeIfPresent(columnFamily, (k, state) -> {
            state.addIter(iterator);
            return state;
        });
    }

    public static void closeAllIterators(ConcurrentHashMap<Keyword, State> pool) throws Exception {
        for (State state : pool.values()) {
            state.closeAllIterators();
        }
    }

    public List<AutoCloseable> borrowed() {
        return allIterators.stream().filter(i -> !returnedIterators.contains(i)).toList();
    }

    public List<AutoCloseable> returned() {
        return List.copyOf(returnedIterators);
    }

    /**
     * Tries to borrow an iterator.
     *
     * @return the first available iterator or {@code null} if none is available.
     */
    private AutoCloseable borrowIter() {
        return returnedIterators.pollFirst();
    }

    /**
     * Returns {@code iterator}.
     *
     * @param iterator the iterator to return
     */
    private void returnIter(AutoCloseable iterator) {
        returnedIterators.addLast(iterator);
    }

    /**
     * Adds {@code iterator}.
     *
     * @param iterator the iterator to add
     */
    private void addIter(final AutoCloseable iterator) {
        allIterators.add(iterator);
    }

    private void closeAllIterators() throws Exception {
        for (var iterator : allIterators) {
            iterator.close();
        }
    }
}
