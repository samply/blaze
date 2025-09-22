package blaze.fhir.spec.type;

import clojure.lang.ILookup;
import clojure.lang.Keyword;
import clojure.lang.MapEntry;

import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

import static java.util.Objects.requireNonNull;

public class BaseIterator implements Iterator<Map.Entry<Object, Object>> {

    private final ILookup lookup;
    private final Keyword[] fields;
    private int i = 0;

    public BaseIterator(ILookup lookup, Keyword[] fields) {
        this.lookup = requireNonNull(lookup);
        this.fields = requireNonNull(fields);
    }

    public boolean hasNext() {
        return i < fields.length;
    }

    public Map.Entry<Object, Object> next() {
        if (hasNext()) {
            Object k = fields[i];
            i++;
            return MapEntry.create(k, lookup.valAt(k));
        }
        throw new NoSuchElementException();
    }
}
