package blaze.db.impl.index;

import blaze.ByteString;
import blaze.fhir.Hash;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public final class MultiVersionId {

    private final ByteString id;
    private final int[] hashPrefixes;

    private MultiVersionId(ByteString id, int[] hashPrefixes) {
        this.id = id;
        this.hashPrefixes = hashPrefixes;
    }

    public static MultiVersionId fromSingleVersionId(SingleVersionId singleVersionId) {
        return new MultiVersionId(singleVersionId.id(), new int[]{singleVersionId.hashPrefix()});
    }

    public ByteString id() {
        return id;
    }

    public boolean matchesHash(Hash hash) {
        return Arrays.binarySearch(hashPrefixes, hash.prefix()) >= 0;
    }

    public MultiVersionId conj(SingleVersionId singleVersionId) {
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
            return new MultiVersionId(id, newHashPrefixes);
        }
    }

    @Override
    public String toString() {
        return "MultiVersionId{" +
                "id=" + id +
                ", hashPrefixes=[" + IntStream.of(hashPrefixes).mapToObj(h -> "0x" + Integer.toHexString(h)).collect(Collectors.joining(",")) +
                "]}";
    }
}
