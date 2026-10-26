package blaze.db.impl.index;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Records the keys of resource handles that missed in the resource handle cache
 * within a window of distinct keys.
 * <p>
 * The doorkeeper consists of a single Bloom filter with a tumbling window: once
 * the window is full, it replaces its Bloom filter by an empty one and forgets
 * all keys recorded before. So the window doesn't slide, and how long a key is
 * remembered depends on when its first miss falls in the window. On average, a
 * key is remembered for half a window.
 * <p>
 * The Bloom filter is a blocked one, so that recording or looking up a key
 * costs a single cache miss. It counts the distinct keys in a striped counter,
 * so that concurrent readers don't contend on a single counter. Because summing
 * that counter is expensive, only a random sample of the new keys checks the
 * count, so a window can exceed its size by a small number of keys.
 * <p>
 * All methods are thread-safe. A race between a reset and the recording of a
 * key only affects whether that key is seen on its next miss.
 */
public final class Doorkeeper {

    /**
     * The 64-bit offset basis of the Fowler-Noll-Vo hash function, the start
     * value of the hash.
     * <p>
     * Its value 14695981039346656037 is defined by the specification. It's the
     * FNV-0 hash, which starts with zero, of the signature of one of the
     * authors, so that the start value isn't zero.
     *
     * @see <a href="https://datatracker.ietf.org/doc/draft-eastlake-fnv/">The
     * FNV Non-Cryptographic Hash Algorithm</a>
     */
    private static final long FNV_OFFSET_BASIS = 0xCBF29CE484222325L;

    /**
     * The 64-bit prime of the Fowler-Noll-Vo hash function, the factor the hash
     * is multiplied with after each input value.
     * <p>
     * Its value 2^40 + 2^8 + 0xB3 = 1099511628211 is defined by the
     * specification. It has few set bits, so that the multiplication is cheap
     * on hardware without fast multipliers, and spreads every input value into
     * the upper bits of the hash.
     *
     * @see <a href="https://datatracker.ietf.org/doc/draft-eastlake-fnv/">The
     * FNV Non-Cryptographic Hash Algorithm</a>
     */
    private static final long FNV_PRIME = 0x100000001B3L;

    /**
     * The maximum number of new keys per check of the count.
     */
    private static final long MAX_CHECK_INTERVAL = 256;

    /**
     * The minimum number of keys in a window per new key checking the count. A
     * window of at most this size checks the count on every new key.
     */
    private static final long MIN_WINDOW_PER_CHECK_INTERVAL = 4096;

    private final AtomicReference<BlockedBloomFilter> filter;
    private final long window;
    private final int checkMask;

    /**
     * Creates a doorkeeper with a {@code window} of distinct keys.
     */
    public Doorkeeper(long window) {
        this.window = window;
        checkMask = checkMask(window);
        filter = new AtomicReference<>(new BlockedBloomFilter(window));
    }

    /**
     * Returns the mask selecting the new keys checking the count.
     * <p>
     * The check interval is a power of two of at most 256 keys and at most a
     * 4096th of the window, so that the window exceeds its size by at most a
     * small fraction.
     */
    private static int checkMask(long window) {
        long interval = Long.highestOneBit(Math.max(1, window / MIN_WINDOW_PER_CHECK_INTERVAL));
        return (int) Math.min(MAX_CHECK_INTERVAL, interval) - 1;
    }

    /**
     * Returns a 64-bit hash of the type ID and the logical FHIR ID of
     * {@code handle}, so that all versions of a resource have the same hash.
     * <p>
     * Uses FNV-1a over the type ID and the chars of the ID, followed by the
     * finalizer of MurmurHash3, which mixes all bits, because the Bloom filter
     * chooses the block by the high bits.
     */
    static long hash(ResourceHandle handle) {
        long hash = (FNV_OFFSET_BASIS ^ handle.tid()) * FNV_PRIME;
        String id = handle.id();
        for (int i = 0; i < id.length(); i++) {
            hash = (hash ^ id.charAt(i)) * FNV_PRIME;
        }
        return mix(hash);
    }

    private static long mix(long hash) {
        hash ^= hash >>> 33;
        hash *= 0xFF51AFD7ED558CCDL;
        hash ^= hash >>> 33;
        hash *= 0xC4CEB9FE1A85EC53L;
        return hash ^ (hash >>> 33);
    }

    /**
     * Returns true if the key of {@code handle} missed before in the current
     * window. Records the miss otherwise.
     * <p>
     * Only new keys count towards the window, so that the Bloom filter is reset
     * after its window of distinct keys is full.
     */
    public boolean seen(ResourceHandle handle) {
        BlockedBloomFilter filter = this.filter.get();
        if (filter.put(hash(handle))) {
            if (isCheckSample() && window <= filter.count()) {
                reset(filter);
            }
            return false;
        }
        return true;
    }

    private boolean isCheckSample() {
        return (ThreadLocalRandom.current().nextInt() & checkMask) == 0;
    }

    /**
     * Replaces the full {@code filter} by an empty one, unless another reader
     * did that already.
     */
    private void reset(BlockedBloomFilter filter) {
        this.filter.compareAndSet(filter, new BlockedBloomFilter(window));
    }
}
