package blaze.db.impl.index;

import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.Reduced;

import java.nio.ByteBuffer;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.util.Objects.requireNonNull;

/**
 * A reducing function which partitions consecutive single version ids by id,
 * emitting one {@link IndexHandle} per partition.
 * <p>
 * Instances are stateful. A reducing function is only used by one reduction
 * on one thread, so the state is held in a plain field without the memory
 * barriers a volatile would need.
 */
public abstract class ByIdGrouper extends AFn {

    private final IFn rf;
    private IndexHandle indexHandle;

    private ByIdGrouper(IFn rf) {
        this.rf = requireNonNull(rf);
    }

    /**
     * Returns a reducing function which receives {@link SingleVersionId}s and
     * calls {@code rf} with {@link IndexHandle}s.
     */
    public static ByIdGrouper ofSingleVersionIds(IFn rf) {
        return new SingleVersionIds(rf);
    }

    /**
     * Returns a reducing function which receives byte buffers of
     * SearchParamValueResource keys and calls {@code rf} with
     * {@link IndexHandle}s.
     * <p>
     * The id and hash prefix are read from the end of the keys, so keys of
     * other indices ending the same way can be used too. The byte buffers
     * have to have an accessible backing array and can be reused after each
     * call.
     */
    public static ByIdGrouper ofSearchParamValueResourceKeys(IFn rf) {
        return new SearchParamValueResourceKeys(rf);
    }

    @Override
    public final Object invoke(Object result) {
        var current = indexHandle;
        if (current != null) {
            indexHandle = null;
            result = unreduced(rf.invoke(result, current));
        }
        return rf.invoke(result);
    }

    /**
     * Returns the index handle of the current partition or null if there is
     * none.
     */
    final IndexHandle current() {
        return indexHandle;
    }

    /**
     * Replaces the index handle of the current partition with
     * {@code indexHandle} having the same id.
     */
    final void replaceCurrent(IndexHandle indexHandle) {
        this.indexHandle = indexHandle;
    }

    /**
     * Emits the index handle of the current partition, if there is one, and
     * starts a new partition with {@code next}.
     */
    final Object startNext(Object result, IndexHandle next) {
        var current = indexHandle;
        if (current == null) {
            indexHandle = next;
            return result;
        }
        var ret = rf.invoke(result, current);
        indexHandle = ret instanceof Reduced ? null : next;
        return ret;
    }

    private static Object unreduced(Object x) {
        return x instanceof Reduced reduced ? reduced.deref() : x;
    }

    private static final class SingleVersionIds extends ByIdGrouper {

        private SingleVersionIds(IFn rf) {
            super(rf);
        }

        @Override
        public Object invoke(Object result, Object input) {
            var singleVersionId = (SingleVersionId) input;
            var current = current();
            if (current != null && current.id().equals(singleVersionId.id())) {
                replaceCurrent(current.conj(singleVersionId.hashPrefix()));
                return result;
            }
            return startNext(result, IndexHandle.fromSingleVersionId(singleVersionId));
        }
    }

    private static final class SearchParamValueResourceKeys extends ByIdGrouper {

        private SearchParamValueResourceKeys(IFn rf) {
            super(rf);
        }

        @Override
        public Object invoke(Object result, Object input) {
            var buffer = (ByteBuffer) input;
            int hashPrefixStart = buffer.limit() - SearchParamValueResource.HASH_PREFIX_SIZE;
            int hashPrefix = buffer.getInt(hashPrefixStart);
            int idSize = SearchParamValueResource.idSize(buffer);
            int idStart = buffer.arrayOffset() + hashPrefixStart - 1 - idSize;
            var current = current();
            if (current != null && idEquals(current.id(), buffer.array(), idStart, idSize)) {
                replaceCurrent(current.conj(hashPrefix));
                return result;
            }
            var id = new String(buffer.array(), idStart, idSize, ISO_8859_1);
            return startNext(result, IndexHandle.of(id, hashPrefix));
        }

        /**
         * Compares from the end, because consecutive ids in an index are
         * sorted and so tend to share a prefix.
         */
        private static boolean idEquals(String id, byte[] array, int offset, int length) {
            if (id.length() != length) {
                return false;
            }
            for (int i = length - 1; i >= 0; i--) {
                if (id.charAt(i) != (char) (array[offset + i] & 0xFF)) {
                    return false;
                }
            }
            return true;
        }
    }
}
