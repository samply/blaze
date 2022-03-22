package blaze.fhir.spec.type;

import clojure.lang.Counted;
import clojure.lang.ILookup;
import clojure.lang.Indexed;
import clojure.lang.MapEntry;

import java.util.Iterator;
import java.util.NoSuchElementException;

class TypeIterator<T extends ILookup & Counted> implements Iterator<MapEntry> {

    private final Indexed fields;
    private final int nonNullFieldCount;
    private final T obj;
    private int i, j = 0;

    TypeIterator(Indexed fields, T obj) {
        this.fields = fields;
        this.nonNullFieldCount = obj.count();
        this.obj = obj;
    }

    @Override
    public boolean hasNext() {
        return j < nonNullFieldCount;
    }

    @Override
    public MapEntry next() {
        if (j < nonNullFieldCount) {
            Object k = fields.nth(i);
            i++;
            Object val = obj.valAt(k);
            if (val == null) {
                return next();
            } else {
                j++;
                return MapEntry.create(k, val);
            }
        } else {
            throw new NoSuchElementException();
        }
    }
}
