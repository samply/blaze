package blaze.coll;

import clojure.lang.AFn;
import clojure.lang.IDeref;
import clojure.lang.IFn;
import clojure.lang.RT;

import static java.util.Objects.requireNonNull;

/**
 * The reducing function of the {@code blaze.coll.core/partition-all}
 * transducer.
 * <p>
 * Collects inputs into an array of size {@code n} and emits it as
 * {@link Chunk} once full. Starts a new array for the next chunk instead of
 * reusing the old one, so emitted chunks never change and don't have to be
 * copied.
 */
public final class PartitionAll extends AFn {

    private final IFn rf;
    private final int n;
    private Object[] items;
    private int count;

    public PartitionAll(IFn rf, int n) {
        this.rf = requireNonNull(rf);
        this.n = n;
    }

    @Override
    public Object invoke() {
        return rf.invoke();
    }

    @Override
    public Object invoke(Object result) {
        if (items != null) {
            result = rf.invoke(result, emit());
            if (RT.isReduced(result)) {
                result = ((IDeref) result).deref();
            }
        }
        return rf.invoke(result);
    }

    @Override
    public Object invoke(Object result, Object input) {
        if (items == null) {
            items = new Object[n];
        }
        items[count++] = input;
        return count == n ? rf.invoke(result, emit()) : result;
    }

    private Chunk emit() {
        var chunk = new Chunk(items, count);
        items = null;
        count = 0;
        return chunk;
    }
}
