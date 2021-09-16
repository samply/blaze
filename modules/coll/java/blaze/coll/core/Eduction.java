package blaze.coll.core;

import clojure.core$conj_BANG_;
import clojure.core$transduce;
import clojure.core$completing;
import clojure.lang.*;

public final class Eduction implements Sequential, Seqable, IReduceInit, Counted {

    private static final core$conj_BANG_ CONJ = new core$conj_BANG_();
    private static final AFn INC = new AFn() {
        @Override
        public Object invoke(Object sum, Object x) {
            return ((Number) sum).intValue() + 1;
        }
    };

    private final IFn xform;
    private final Object coll;

    public Eduction(IFn xform, Object coll) {
        this.xform = xform;
        this.coll = coll;
    }

    public int count() {
        return ((Number) reduce(INC, 0)).intValue();
    }

    public ISeq seq() {
        Object init = PersistentVector.EMPTY.asTransient();
        return ((ITransientCollection) reduce(CONJ, init)).persistent().seq();
    }

    public Object reduce(IFn f, Object init) {
        return core$transduce.invokeStatic(xform, core$completing.invokeStatic(f), init, coll);
    }
}
