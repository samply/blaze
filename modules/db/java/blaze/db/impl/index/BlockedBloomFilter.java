package blaze.db.impl.index;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.atomic.LongAdder;

/**
 * A Bloom filter of 64-bit hashes that keeps all bits of a hash in a single
 * block of 512 bits, the size of a cache line.
 * <p>
 * A lookup costs a single cache miss instead of one per bit like in a classic
 * Bloom filter. Because the hashes don't spread evenly over the blocks, a
 * blocked Bloom filter needs more bits per key for the same false positive
 * probability. With 10 bits per key and 6 bits per hash, the false positive
 * probability is about 1 % at the expected number of keys.
 * <p>
 * The hashes have to be well mixed, because the high 32 bits choose the block
 * and the low 18 bits choose the bits inside the block.
 * <p>
 * All methods are thread-safe and lock-free. Concurrent puts don't lose bits.
 *
 * @see <a href="https://doi.org/10.1145/1498698.1594230">Putze, Sanders and
 * Singler: Cache-, Hash- and Space-Efficient Bloom Filters</a>
 */
public final class BlockedBloomFilter {

    private static final VarHandle BITS = MethodHandles.arrayElementVarHandle(long[].class);

    private static final int BITS_PER_KEY = 10;
    private static final int BITS_PER_HASH = 6;
    private static final int BLOCK_BITS = 512;
    private static final int BLOCK_MASK = BLOCK_BITS - 1;
    private static final int LONGS_PER_BLOCK = BLOCK_BITS / Long.SIZE;

    private final long[] bits;
    private final long numBlocks;
    private final LongAdder count = new LongAdder();

    /**
     * Creates a Bloom filter with a false positive probability of about 1 % at
     * {@code expectedKeys}.
     */
    public BlockedBloomFilter(long expectedKeys) {
        numBlocks = Math.max(1, Math.ceilDiv(expectedKeys * BITS_PER_KEY, BLOCK_BITS));
        bits = new long[Math.toIntExact(numBlocks * LONGS_PER_BLOCK)];
    }

    /**
     * Maps the high 32 bits of {@code hash} onto the blocks without a modulo.
     */
    private int blockOffset(long hash) {
        return (int) (((hash >>> 32) * numBlocks) >>> 32) * LONGS_PER_BLOCK;
    }

    /**
     * Returns the position of the bit {@code i} inside the block by double
     * hashing. The step is odd, so that the positions differ.
     */
    private static int bitPosition(long hash, int i) {
        int start = (int) hash & BLOCK_MASK;
        int step = ((int) (hash >>> 9) & BLOCK_MASK) | 1;
        return (start + i * step) & BLOCK_MASK;
    }

    /**
     * Puts {@code hash} into this filter.
     * <p>
     * Returns true if the bits of this filter changed, so that {@code hash} was
     * definitely not put before. Returns false if {@code hash} might have been
     * put before.
     */
    public boolean put(long hash) {
        int offset = blockOffset(hash);
        boolean changed = false;
        for (int i = 0; i < BITS_PER_HASH; i++) {
            int position = bitPosition(hash, i);
            int index = offset + (position >>> 6);
            long mask = 1L << position;
            // reads first, so that puts of hashes already present don't write
            if (((long) BITS.getOpaque(bits, index) & mask) == 0 &&
                    ((long) BITS.getAndBitwiseOr(bits, index, mask) & mask) == 0) {
                changed = true;
            }
        }
        if (changed) {
            count.increment();
        }
        return changed;
    }

    /**
     * Returns true if {@code hash} might have been put into this filter and
     * false if this is definitely not the case.
     */
    public boolean mightContain(long hash) {
        int offset = blockOffset(hash);
        for (int i = 0; i < BITS_PER_HASH; i++) {
            int position = bitPosition(hash, i);
            int index = offset + (position >>> 6);
            if (((long) BITS.getOpaque(bits, index) & (1L << position)) == 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the number of puts that changed this filter, the number of
     * distinct hashes put, apart from false positives.
     * <p>
     * Sums a striped counter, which is more expensive than a put.
     */
    public long count() {
        return count.sum();
    }
}
