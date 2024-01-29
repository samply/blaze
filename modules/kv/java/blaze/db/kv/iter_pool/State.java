package blaze.db.kv.iter_pool;

import clojure.lang.Keyword;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A state holding iterators that can be either borrowed or returned.
 * <p> 
 * Instances are immutable and state transition methods return new instances.
 */
public final class State {

    public static final State EMPTY = new State(-1, new AutoCloseable[0], new boolean[0]);

    private final int outputIdx;
    private final AutoCloseable[] iterators;
    // true = returned, false = borrowed
    private final boolean[] iterState;

    private State(int outputIdx, AutoCloseable[] iterators, boolean[] iterState) {
        this.outputIdx = outputIdx;
        this.iterators = iterators;
        this.iterState = iterState;
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
        return pool.compute(columnFamily, (k, state) -> state == null ? EMPTY : state.borrowIter()).output();
    }

    /**
     * Tries to return {@code iterator} to {@code pool} for {@code columnFamily column family}.
     * <p>
     * Changes the state of {@code pool} except if the iterator was not found.
     *
     * @param pool         the pool to return the iterator to
     * @param columnFamily the column family for which the iterator should be returned
     * @param iterator     the iterator to return
     * @throws IllegalArgumentException if the iterator was not found
     */
    public static void returnIterator(ConcurrentHashMap<Keyword, State> pool, Keyword columnFamily, AutoCloseable iterator) {
        pool.computeIfPresent(columnFamily, (k, state) -> state.returnIter(iterator));
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
        pool.computeIfPresent(columnFamily, (k, state) -> state.addIter(iterator));
    }

    public static void closeAllIterators(ConcurrentHashMap<Keyword, State> pool) throws Exception {
        for (State state : pool.values()) {
            state.closeAllIterators();
        }
    }

    /**
     * Returns the iterator marked as output or {@code null} of none is marked.
     *
     * @return the iterator marked as output or {@code null} of none is marked
     */
    public AutoCloseable output() {
        return outputIdx == -1 ? null : iterators[outputIdx];
    }

    public List<AutoCloseable> borrowed() {
        List<AutoCloseable> list = new ArrayList<>(iterators.length);
        for (int i = 0; i < iterState.length; i++) {
            if (!iterState[i]) {
                list.add(iterators[i]);
            }
        }
        return list;
    }

    public List<AutoCloseable> returned() {
        List<AutoCloseable> list = new ArrayList<>(iterators.length);
        for (int i = 0; i < iterState.length; i++) {
            if (iterState[i]) {
                list.add(iterators[i]);
            }
        }
        return list;
    }

    /**
     * Tries to borrow an iterator.
     * <p>
     * Searches through all {@link #iterState iterator states} until one {@code true} (returned) state is found. If one
     * is found, copies the iterator states, sets the one to {@code false} (borrowed) and returns a new state with the
     * {@link #outputIdx output index} of that iterator. Otherwise returns a new state with the output index set to
     * {@code -1}.
     *
     * @return a new state with either {@link #outputIdx output index} set to the index of the found iterator or
     * {@code -1}
     */
    private State borrowIter() {
        for (int i = 0; i < iterState.length; i++) {
            if (iterState[i]) {
                var newIterState = iterState.clone();
                newIterState[i] = false;
                return new State(i, iterators, newIterState);
            }
        }
        return new State(-1, iterators, iterState);
    }

    /**
     * Tries to return {@code iterator}.
     * <p>
     * Searches through all {@link #iterators} until {@code iterator} is found. If it is found, copies the iterator
     * states, sets the one to {@code true} (returned) and returns that new state. Otherwise throws an
     * {@link IllegalArgumentException} because the iterator was never borrowed before.
     *
     * @param iterator the iterator to return
     * @return a new state were the state of the iterator is set to {@code true} (returned)
     * @throws IllegalArgumentException if the iterator was not found
     */
    private State returnIter(AutoCloseable iterator) {
        for (int i = 0; i < iterators.length; i++) {
            if (iterators[i] == iterator) {
                var newIterState = iterState.clone();
                newIterState[i] = true;
                return new State(-1, iterators, newIterState);
            }
        }
        throw new IllegalArgumentException("Iterator " + iterator + " not found.");
    }

    /**
     * Adds {@code iterator}.
     *
     * @param iterator the iterator to add
     * @return a new state with {@code iterator} added marked as {@code false} (borrowed) and with the
     * {@link #outputIdx output index} set to it
     */
    private State addIter(final AutoCloseable iterator) {
        int l = iterators.length;
        var newIterators = Arrays.copyOf(iterators, l + 1);
        var newIterState = Arrays.copyOf(iterState, l + 1);
        newIterators[l] = iterator;
        newIterState[l] = false;
        return new State(l, newIterators, newIterState);
    }

    private void closeAllIterators() throws Exception {
        for (var iterator : iterators) {
            iterator.close();
        }
    }
}
