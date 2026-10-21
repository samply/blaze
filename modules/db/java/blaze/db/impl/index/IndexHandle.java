package blaze.db.impl.index;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

/**
 * An id together with a sorted set of hash prefixes.
 * <p>
 * Index handles with exactly one hash prefix, which are by far the most
 * common, are represented by {@link Single}, which needs no separate array.
 * All other index handles, including empty ones, are represented by
 * {@link Multi}. The factory methods ensure that representation, so equal
 * index handles always have the same class.
 */
public sealed abstract class IndexHandle permits IndexHandle.Single, IndexHandle.Multi {

    private static final int[] EMPTY = new int[0];

    private final String id;

    private IndexHandle(String id) {
        this.id = requireNonNull(id);
    }

    private static IndexHandle empty(String id) {
        return new Multi(id, EMPTY);
    }

    static IndexHandle of(String id, int hashPrefix) {
        return new Single(id, hashPrefix);
    }

    /**
     * Creates an index handle with the first {@code length} hash prefixes of
     * {@code hashPrefixes} which have to be sorted.
     */
    private static IndexHandle ofSorted(String id, int[] hashPrefixes, int length) {
        return switch (length) {
            case 0 -> empty(id);
            case 1 -> new Single(id, hashPrefixes[0]);
            default -> new Multi(id, length == hashPrefixes.length ? hashPrefixes : Arrays.copyOf(hashPrefixes, length));
        };
    }

    public static IndexHandle fromResourceHandle(ResourceHandle resourceHandle) {
        return new Single(resourceHandle.id(), resourceHandle.hashPrefix());
    }

    public static IndexHandle fromSingleVersionId(SingleVersionId singleVersionId) {
        return new Single(singleVersionId.id(), singleVersionId.hashPrefix());
    }

    public final String id() {
        return id;
    }

    public final List<Integer> hashPrefixes() {
        return hashPrefixStream().boxed().toList();
    }

    public abstract List<SingleVersionId> toSingleVersionIds();

    public final boolean matchesHashPrefix(ResourceHandle resourceHandle) {
        return contains(resourceHandle.hashPrefix());
    }

    public final IndexHandle conj(SingleVersionId singleVersionId) {
        return conj(singleVersionId.hashPrefix());
    }

    abstract IndexHandle conj(int hashPrefix);

    abstract boolean contains(int hashPrefix);

    abstract IntStream hashPrefixStream();

    public final IndexHandle intersection(IndexHandle other) {
        checkIds(other);
        if (this instanceof Single single) {
            return other.contains(single.hashPrefix) ? this : empty(id);
        }
        if (other instanceof Single single) {
            return contains(single.hashPrefix) ? other : empty(id);
        }
        return ((Multi) this).intersection((Multi) other);
    }

    public final IndexHandle union(IndexHandle other) {
        checkIds(other);
        if (other instanceof Single single) {
            return conj(single.hashPrefix);
        }
        if (this instanceof Single single) {
            return other.conj(single.hashPrefix);
        }
        return ((Multi) this).union((Multi) other);
    }

    private void checkIds(IndexHandle other) {
        if (!id.equals(other.id)) {
            throw new IllegalArgumentException("ids " + id + " and " + other.id + " differ");
        }
    }

    @Override
    public final String toString() {
        return "IndexHandle{" +
                "id=" + id +
                ", hashPrefixes=[" + hashPrefixStream().mapToObj(h -> "0x" + Integer.toHexString(h)).collect(Collectors.joining(",")) +
                "]}";
    }

    private static final class Single extends IndexHandle {

        private final int hashPrefix;

        private Single(String id, int hashPrefix) {
            super(id);
            this.hashPrefix = hashPrefix;
        }

        @Override
        IndexHandle conj(int hashPrefix) {
            if (hashPrefix == this.hashPrefix) {
                return this;
            }
            var hashPrefixes = hashPrefix < this.hashPrefix
                    ? new int[]{hashPrefix, this.hashPrefix}
                    : new int[]{this.hashPrefix, hashPrefix};
            return new Multi(id(), hashPrefixes);
        }

        @Override
        boolean contains(int hashPrefix) {
            return hashPrefix == this.hashPrefix;
        }

        @Override
        IntStream hashPrefixStream() {
            return IntStream.of(hashPrefix);
        }

        @Override
        public List<SingleVersionId> toSingleVersionIds() {
            return List.of(new SingleVersionId(id(), hashPrefix));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Single that && id().equals(that.id()) && hashPrefix == that.hashPrefix;
        }

        @Override
        public int hashCode() {
            return 31 * id().hashCode() + hashPrefix;
        }
    }

    /**
     * Holds no or at least two hash prefixes.
     */
    private static final class Multi extends IndexHandle {

        private final int[] hashPrefixes;

        private Multi(String id, int[] hashPrefixes) {
            super(id);
            this.hashPrefixes = hashPrefixes;
        }

        @Override
        IndexHandle conj(int hashPrefix) {
            int index = Arrays.binarySearch(hashPrefixes, hashPrefix);
            if (index >= 0) {
                return this;
            } else {
                index = -(index + 1);
                var newHashPrefixes = new int[hashPrefixes.length + 1];
                System.arraycopy(hashPrefixes, 0, newHashPrefixes, 0, index);
                System.arraycopy(hashPrefixes, index, newHashPrefixes, index + 1, hashPrefixes.length - index);
                newHashPrefixes[index] = hashPrefix;
                return ofSorted(id(), newHashPrefixes, newHashPrefixes.length);
            }
        }

        @Override
        boolean contains(int hashPrefix) {
            return Arrays.binarySearch(hashPrefixes, hashPrefix) >= 0;
        }

        @Override
        IntStream hashPrefixStream() {
            return IntStream.of(hashPrefixes);
        }

        @Override
        public List<SingleVersionId> toSingleVersionIds() {
            return hashPrefixStream().mapToObj(p -> new SingleVersionId(id(), p)).toList();
        }

        private IndexHandle intersection(Multi other) {
            int i = 0, j = 0, r = 0;
            var res = new int[Math.min(hashPrefixes.length, other.hashPrefixes.length)];

            while (i < hashPrefixes.length && j < other.hashPrefixes.length) {
                if (hashPrefixes[i] < other.hashPrefixes[j]) {
                    i++;
                } else if (hashPrefixes[i] > other.hashPrefixes[j]) {
                    j++;
                } else {
                    res[r++] = hashPrefixes[i++];
                    j++;
                }
            }

            return ofSorted(id(), res, r);
        }

        private IndexHandle union(Multi other) {
            int i = 0, j = 0, r = 0;
            var res = new int[hashPrefixes.length + other.hashPrefixes.length];

            while (i < hashPrefixes.length && j < other.hashPrefixes.length) {
                if (hashPrefixes[i] < other.hashPrefixes[j]) {
                    res[r++] = hashPrefixes[i++];
                } else if (hashPrefixes[i] > other.hashPrefixes[j]) {
                    res[r++] = other.hashPrefixes[j++];
                } else {
                    res[r++] = hashPrefixes[i++];
                    j++;
                }
            }

            while (i < hashPrefixes.length) {
                res[r++] = hashPrefixes[i++];
            }

            while (j < other.hashPrefixes.length) {
                res[r++] = other.hashPrefixes[j++];
            }

            return ofSorted(id(), res, r);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            return o instanceof Multi that && id().equals(that.id()) && Arrays.equals(hashPrefixes, that.hashPrefixes);
        }

        @Override
        public int hashCode() {
            return 31 * id().hashCode() + Arrays.hashCode(hashPrefixes);
        }
    }
}
