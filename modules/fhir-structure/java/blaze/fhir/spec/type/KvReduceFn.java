package blaze.fhir.spec.type;

import clojure.lang.AFn;
import clojure.lang.IFn;
import clojure.lang.ILookup;

final class KvReduceFn extends AFn {

    private final IFn f;
    private final ILookup obj;

    KvReduceFn(IFn f, ILookup obj) {
        this.f = f;
        this.obj = obj;
    }

    @Override
    public Object invoke(Object res, Object key) {
        Object val = obj.valAt(key);
        return val == null ? res : f.invoke(res, key, val);
    }
}
