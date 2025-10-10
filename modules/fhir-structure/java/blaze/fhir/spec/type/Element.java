package blaze.fhir.spec.type;

import clojure.lang.ILookupThunk;
import clojure.lang.Keyword;

import java.lang.String;
import java.util.List;

public interface Element extends Base {

    ILookupThunk ID_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Element p ? p.id() : this;
        }
    };

    ILookupThunk EXTENSION_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Element p ? p.extension() : this;
        }
    };

    String id();

    List<Extension> extension();

    @Override
    default ILookupThunk getLookupThunk(Keyword key) {
        if (key == EXTENSION) return EXTENSION_LOOKUP_THUNK;
        if (key == ID) return ID_LOOKUP_THUNK;
        return Base.super.getLookupThunk(key);
    }
}
