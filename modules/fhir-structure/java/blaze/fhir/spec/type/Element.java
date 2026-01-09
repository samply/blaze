package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.Keyword;

import java.lang.String;
import java.util.List;

public interface Element extends Base {

    String id();

    List<Extension> extension();

    @Override
    default ILookupThunk getLookupThunk(Keyword key) {
        if (key == EXTENSION) return new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Element p ? p.extension() : this;
            }
        };
        if (key == ID) return new ILookupThunk() {
            @Override
            public Object get(Object target) {
                return target instanceof Element p ? p.id() : this;
            }
        };
        return Base.super.getLookupThunk(key);
    }
}
