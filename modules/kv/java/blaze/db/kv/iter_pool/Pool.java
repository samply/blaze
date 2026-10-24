package blaze.db.kv.iter_pool;

import blaze.db.kv.KvCloseable;
import clojure.lang.Keyword;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * The iterator pool of one thread of a {@link Pools snapshot}.
 * <p>
 * Holds, per column family, a deque of returned iterators, all iterators it created and a closed flag. All state is
 * guarded by the lock of this object. Because a pool is almost always used only by its own thread, the lock is
 * uncontended. Only returning an iterator from another thread and closing the snapshot touch the pool from other
 * threads.
 * <p>
 * State transitions:
 * <ul>
 *     <li>{@link #borrow(Keyword) borrow}: fails if the pool is closed. Otherwise, takes a returned iterator or creates
 *     a new one.</li>
 *     <li>{@link #giveBack(Keyword, KvCloseable) give back}: adds the iterator to the returned ones if the pool is open.
 *     Otherwise, has no effect, because the iterator is closed already.</li>
 *     <li>{@link #close() close}: sets the closed flag and closes all iterators, borrowed and returned.</li>
 * </ul>
 * Closing a borrowed iterator frees its native handle, while the code holding it may still use it. So the code using
 * an iterator has to check the {@link #isClosed() closed flag} before each use. That check can't prevent a race: a
 * thread that passed it while the pool is closed uses a freed native handle. So the snapshot must only be closed after
 * all work using it is done.
 */
public final class Pool {

    private final Pools pools;
    private final Map<Keyword, ArrayDeque<KvCloseable>> returned = new HashMap<>();
    private final List<KvCloseable> iterators = new ArrayList<>();
    private volatile boolean closed;

    Pool(Pools pools) {
        this.pools = pools;
    }

    /**
     * Borrows an iterator over {@code columnFamily}.
     * <p>
     * Takes the first returned iterator or creates a new one if none is available. Exceptions thrown while creating a
     * new iterator propagate and the iterator isn't added to this pool. Because the lock of this pool is held while
     * creating a new iterator, and {@link Pools#close() closing the snapshot} closes this pool before the underlying
     * snapshot, no iterator is created on a closed underlying snapshot.
     *
     * @param columnFamily the column family of the iterator
     * @return the iterator or {@code null} if this pool is closed
     */
    public synchronized KvCloseable borrow(Keyword columnFamily) {
        requireNonNull(columnFamily);
        if (closed) return null;
        var deque = returned.get(columnFamily);
        var iterator = deque == null ? null : deque.pollFirst();
        if (iterator == null) {
            iterator = pools.createIterator(columnFamily);
            iterators.add(iterator);
        }
        return iterator;
    }

    /**
     * Gives back {@code iterator} over {@code columnFamily} that was borrowed from this pool.
     * <p>
     * Can be called from any thread. Has no effect if this pool is closed, because the iterator is closed already. The
     * caller has to ensure that an iterator is given back only once per borrow.
     *
     * @param columnFamily the column family of the iterator
     * @param iterator     the iterator to give back
     */
    public synchronized void giveBack(Keyword columnFamily, KvCloseable iterator) {
        requireNonNull(columnFamily);
        requireNonNull(iterator);
        if (closed) return;
        returned.computeIfAbsent(columnFamily, k -> new ArrayDeque<>()).addLast(iterator);
    }

    /**
     * Closes this pool and all its iterators, borrowed and returned.
     */
    synchronized void close() {
        closed = true;
        for (var iterator : iterators) {
            iterator.close();
        }
        iterators.clear();
        returned.clear();
    }

    /**
     * Returns {@code true} if this pool is closed.
     * <p>
     * Can be called from any thread without taking the lock of this pool.
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * Returns the number of iterators currently borrowed from this pool.
     * <p>
     * Only for tests.
     */
    public synchronized int numBorrowed() {
        int numReturned = 0;
        for (var deque : returned.values()) {
            numReturned += deque.size();
        }
        return iterators.size() - numReturned;
    }

    /**
     * Returns copies of the deques of returned iterators by column family.
     * <p>
     * Only for tests. The iterators still belong to this pool, so they must neither be used nor closed.
     */
    public synchronized Map<Keyword, List<KvCloseable>> returned() {
        var result = new HashMap<Keyword, List<KvCloseable>>();
        returned.forEach((columnFamily, deque) -> result.put(columnFamily, List.copyOf(deque)));
        return result;
    }
}
