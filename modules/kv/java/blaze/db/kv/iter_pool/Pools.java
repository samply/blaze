package blaze.db.kv.iter_pool;

import blaze.db.kv.KvCloseable;
import clojure.lang.IFn;
import clojure.lang.Keyword;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Objects.requireNonNull;

/**
 * The per-thread {@link Pool iterator pools} of one snapshot.
 * <p>
 * Creating a pool and setting the closed flag happen under the lock of this object. So {@link #close() close} sees
 * every pool created before it, and no pool is created after it.
 * <p>
 * Closing closes all pools, which close all their iterators, borrowed and returned, and then the underlying snapshot.
 * It doesn't wait for borrowed iterators, so the snapshot must only be closed after all work using it is done.
 */
public final class Pools {

    private final KvCloseable snapshot;
    private final IFn newIterator;
    private final ConcurrentHashMap<Thread, Pool> pools = new ConcurrentHashMap<>();
    private boolean closed;

    /**
     * Creates pools for {@code snapshot}.
     *
     * @param snapshot    the underlying snapshot
     * @param newIterator a function taking the underlying snapshot and a column family, returning a new iterator
     */
    public Pools(KvCloseable snapshot, IFn newIterator) {
        this.snapshot = requireNonNull(snapshot);
        this.newIterator = requireNonNull(newIterator);
    }

    /**
     * Returns the pool of the current thread, creating it on first use.
     *
     * @return the pool or {@code null} if this snapshot is closed before the pool was created
     */
    public Pool pool() {
        var thread = Thread.currentThread();
        var pool = pools.get(thread);
        return pool == null ? newPool(thread) : pool;
    }

    private synchronized Pool newPool(Thread thread) {
        if (closed) return null;
        var pool = new Pool(this);
        pools.put(thread, pool);
        return pool;
    }

    KvCloseable createIterator(Keyword columnFamily) {
        return (KvCloseable) newIterator.invoke(snapshot, columnFamily);
    }

    /**
     * Closes all pools and then the underlying snapshot.
     * <p>
     * Calling close more than once has no effect.
     */
    public void close() {
        synchronized (this) {
            if (closed) return;
            closed = true;
        }
        for (var pool : pools.values()) {
            pool.close();
        }
        snapshot.close();
    }

    /**
     * Returns the number of iterators currently borrowed from all pools.
     * <p>
     * Only for tests.
     */
    public int numBorrowed() {
        int sum = 0;
        for (var pool : pools.values()) {
            sum += pool.numBorrowed();
        }
        return sum;
    }

    /**
     * Returns a copy of the map of thread to pool.
     * <p>
     * Only for tests. Borrowing iterators from these pools or giving them back directly bypasses the checks of the
     * pooled iterators.
     */
    public Map<Thread, Pool> pools() {
        return Map.copyOf(pools);
    }
}
