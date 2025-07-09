package blaze.coll;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BinaryOperator;

import static java.util.Objects.requireNonNull;

public final class UnionIterator<E extends Comparable<E>> implements Iterator<E>, AutoCloseable {

    private final BinaryOperator<E> merge;
    private final Iterator<E> iter1;
    private final Iterator<E> iter2;
    private E r;
    private E e1;
    private E e2;

    public UnionIterator(BinaryOperator<E> merge, Iterator<E> iter1, Iterator<E> iter2) {
        this.merge = requireNonNull(merge);
        this.iter1 = requireNonNull(iter1);
        this.iter2 = requireNonNull(iter2);
    }

    @Override
    public boolean hasNext() {
        if (r == null) {
            if ((e1 != null || iter1.hasNext()) && (e2 != null || iter2.hasNext())) {
                var x1 = e1 == null ? iter1.next() : e1;
                var x2 = e2 == null ? iter2.next() : e2;
                var c = x1.compareTo(x2);

                if (c < 0) {
                    r = x1;
                    e1 = null;
                    e2 = x2;
                } else if (c > 0) {
                    r = x2;
                    e1 = x1;
                    e2 = null;
                } else {
                    r = merge.apply(x1, x2);
                    e1 = null;
                    e2 = null;
                    return true;
                }
            } else if (e1 != null) {
                r = e1;
                e1 = null;
            } else if (e2 != null) {
                r = e2;
                e2 = null;
            } else if (iter1.hasNext()) {
                r = iter1.next();
            } else if (iter2.hasNext()) {
                r = iter2.next();
            } else {
                return false;
            }
        }
        return true;
    }

    @Override
    public E next() {
        if (hasNext()) {
            var x = r;
            r = null;
            return x;
        } else {
            throw new NoSuchElementException();
        }
    }

    @Override
    public void close() throws Exception {
        if (iter1 instanceof AutoCloseable c1) {
            c1.close();
        }
        if (iter2 instanceof AutoCloseable c2) {
            c2.close();
        }
    }
}
