package blaze.coll;

import clojure.lang.Counted;
import clojure.lang.IDeref;
import clojure.lang.IFn;
import clojure.lang.IReduceInit;
import clojure.lang.RT;
import clojure.lang.Sequential;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * An immutable chunk of items emitted by {@link PartitionAll}.
 * <p>
 * The chunk owns its array. It is only created by {@link PartitionAll}, which
 * never touches the array again after creating the chunk.
 */
public final class Chunk implements IReduceInit, Counted, Sequential, Iterable<Object> {

    private final Object[] items;
    private final int count;

    Chunk(Object[] items, int count) {
        this.items = items;
        this.count = count;
    }

    @Override
    public Object reduce(IFn f, Object start) {
        var ret = start;
        for (int i = 0; i < count; i++) {
            ret = f.invoke(ret, items[i]);
            if (RT.isReduced(ret)) {
                return ((IDeref) ret).deref();
            }
        }
        return ret;
    }

    @Override
    public int count() {
        return count;
    }

    @Override
    public Iterator<Object> iterator() {
        return new Iterator<>() {
            private int i;

            @Override
            public boolean hasNext() {
                return i < count;
            }

            @Override
            public Object next() {
                if (i < count) {
                    return items[i++];
                } else {
                    throw new NoSuchElementException();
                }
            }
        };
    }
}
