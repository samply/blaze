package blaze.db.impl.index;

import blaze.ByteString;
import blaze.fhir.Hash;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Objects.requireNonNull;

public final class IndexHandle {

    private final ByteString id;
    private final int[] hashPrefixes;

    private IndexHandle(ByteString id, int[] hashPrefixes) {
        this.id = requireNonNull(id);
        this.hashPrefixes = requireNonNull(hashPrefixes);
    }

    public static IndexHandle fromIdAndHash(ByteString id, Hash hash) {
        return new IndexHandle(id, new int[]{hash.prefix()});
    }

    public static IndexHandle fromSingleVersionId(SingleVersionId singleVersionId) {
        return new IndexHandle(singleVersionId.id(), new int[]{singleVersionId.hashPrefix()});
    }

    public ByteString id() {
        return id;
    }

    public List<Integer> hashPrefixes() {
        return Arrays.stream(hashPrefixes).boxed().toList();
    }

    public List<SingleVersionId> toSingleVersionIds() {
        return Arrays.stream(hashPrefixes).mapToObj(p -> new SingleVersionId(id, p)).toList();
    }

    public boolean matchesHash(Hash hash) {
        return Arrays.binarySearch(hashPrefixes, hash.prefix()) >= 0;
    }

    public IndexHandle conj(SingleVersionId singleVersionId) {
        int hashPrefix = singleVersionId.hashPrefix();
        int index = Arrays.binarySearch(hashPrefixes, hashPrefix);
        if (index >= 0) {
            return this;
        } else {
            index = -(index + 1);
            var newHashPrefixes = new int[hashPrefixes.length + 1];
            System.arraycopy(hashPrefixes, 0, newHashPrefixes, 0, hashPrefixes.length);
            System.arraycopy(hashPrefixes, index, newHashPrefixes, index + 1, hashPrefixes.length - index);
            newHashPrefixes[index] = hashPrefix;
            return new IndexHandle(id, newHashPrefixes);
        }
    }

    public IndexHandle intersection(IndexHandle other) {
        checkIds(other);

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

        return new IndexHandle(id, Arrays.copyOf(res, r));
    }

    public IndexHandle union(IndexHandle other) {
        checkIds(other);

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

        return new IndexHandle(id, Arrays.copyOf(res, r));
    }

    private void checkIds(IndexHandle other) {
        if (!id.equals(other.id)) {
            throw new IllegalArgumentException("ids " + id + " and " + other.id + " differ");
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof IndexHandle that && id.equals(that.id) && Arrays.equals(hashPrefixes, that.hashPrefixes);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + Arrays.hashCode(hashPrefixes);
    }

    @Override
    public String toString() {
        return "IndexHandle{" +
                "id=" + id +
                ", hashPrefixes=[" + IntStream.of(hashPrefixes).mapToObj(h -> "0x" + Integer.toHexString(h)).collect(Collectors.joining(",")) +
                "]}";
    }
}
