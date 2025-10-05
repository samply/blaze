package blaze;

import clojure.lang.IDeref;
import clojure.lang.IFn;
import clojure.lang.IReduceInit;
import clojure.lang.RT;

import java.util.List;

public final class ReducibleArray implements IReduceInit {

    private final Object[] items;

    public ReducibleArray(List<?> list) {
        this.items = list.toArray();
    }

    public Object reduce(IFn f, Object init) {
        for (Object item : items) {
            init = f.invoke(init, item);
            if (RT.isReduced(init)) {
                return ((IDeref) init).deref();
            }
        }
        return init;
    }
}
