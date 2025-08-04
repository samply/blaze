package blaze.coll;

import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.BinaryOperator;

import static java.util.Objects.requireNonNull;

public final class IntersectionIterator<E> implements Iterator<E>, AutoCloseable {

    private final Comparator<E> comparator;
    private final BinaryOperator<E> merge;
    private final Iterator<E> iter1;
    private final Iterator<E> iter2;
    private E r;
    private E e1;
    private E e2;

    public IntersectionIterator(Comparator<E> comparator, BinaryOperator<E> merge, Iterator<E> iter1, Iterator<E> iter2) {
        this.comparator = requireNonNull(comparator);
        this.merge = requireNonNull(merge);
        this.iter1 = requireNonNull(iter1);
        this.iter2 = requireNonNull(iter2);
    }

    @Override
    public boolean hasNext() {
        if (r != null) {
            return true;
        }

        while (r == null && (e1 != null || iter1.hasNext()) && (e2 != null || iter2.hasNext())) {
            if (e1 == null) {
                e1 = iter1.next();
            }
            if (e2 == null) {
                e2 = iter2.next();
            }

            int c = comparator.compare(e1, e2);

            if (c < 0) {
                e1 = null;
            } else if (c > 0) {
                e2 = null;
            } else {
                r = merge.apply(e1, e2);
                e1 = null;
                e2 = null;
            }
        }

        return r != null;
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
    public void close() {
        try {
            if (iter1 instanceof AutoCloseable c1) {
                c1.close();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (iter2 instanceof AutoCloseable c2) {
                    c2.close();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
