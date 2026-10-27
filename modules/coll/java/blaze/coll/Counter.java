package blaze.coll;

import clojure.lang.AFn;
import clojure.lang.IReduceInit;

/**
 * A reducing function that counts the inputs in a primitive {@code long}.
 * <p>
 * Ignores the accumulated result, so it doesn't allocate a boxed
 * {@link Long} per input like {@code (fn [sum _] (inc sum))} would.
 */
public final class Counter extends AFn {

    private long count;

    private Counter() {
    }

    /**
     * Returns the number of items of the reducible {@code coll}.
     */
    public static long count(IReduceInit coll) {
        var counter = new Counter();
        coll.reduce(counter, null);
        return counter.count;
    }

    @Override
    public Object invoke(Object result, Object input) {
        count++;
        return null;
    }
}
