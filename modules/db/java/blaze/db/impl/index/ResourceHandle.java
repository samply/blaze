package blaze.db.impl.index;

import blaze.fhir.Hash;
import clojure.lang.*;

import java.util.Comparator;

import static java.util.Objects.requireNonNull;

public final class ResourceHandle implements ILookup, IKeywordLookup, IObj {

    public static final Comparator<ResourceHandle> ID_CMP = Comparator.comparing(rh -> rh.id);

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "type");
    private static final Keyword TID = RT.keyword(null, "tid");
    private static final Keyword ID = RT.keyword(null, "id");
    private static final Keyword T = RT.keyword(null, "t");
    private static final Keyword HASH = RT.keyword(null, "hash");
    private static final Keyword NUM_CHANGES = RT.keyword(null, "num-changes");
    private static final Keyword OP = RT.keyword(null, "op");
    private static final Keyword OP_CREATE = RT.keyword(null, "create");
    private static final Keyword OP_PUT = RT.keyword(null, "put");
    private static final Keyword OP_DELETE = RT.keyword(null, "delete");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? rh.fhirType : this;
        }
    };

    private static final ILookupThunk TID_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? rh.tid : this;
        }
    };

    private static final ILookupThunk ID_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? rh.id : this;
        }
    };

    private static final ILookupThunk T_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? rh.t : this;
        }
    };

    private static final ILookupThunk HASH_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? rh.hash : this;
        }
    };

    private static final ILookupThunk NUM_CHANGES_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? numChanges(rh.state) : this;
        }
    };

    private static final ILookupThunk OP_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? op(rh.state) : this;
        }
    };

    public static long numChanges(long state) {
        return state >> 8;
    }

    public static Keyword op(long state) {
        return (state & 2) != 0 ? OP_CREATE : (state & 1) != 0 ? OP_DELETE : OP_PUT;
    }

    private final Keyword fhirType;
    private final int tid;
    private final String id;
    private final long t;
    private final Hash hash;
    private final long state;
    private final IPersistentMap meta;

    public ResourceHandle(Keyword fhirType, int tid, String id, long t, Hash hash, long state, IPersistentMap meta) {
        this.fhirType = requireNonNull(fhirType);
        this.tid = tid;
        this.id = requireNonNull(id);
        this.t = t;
        this.hash = requireNonNull(hash);
        this.state = state;
        this.meta = meta;
    }

    @Override
    public Object valAt(Object key) {
        return valAt(key, null);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        if (key == FHIR_TYPE) return fhirType;
        if (key == TID) return tid;
        if (key == ID) return id;
        if (key == T) return t;
        if (key == HASH) return hash;
        if (key == NUM_CHANGES) return numChanges(state);
        if (key == OP) return op(state);
        return notFound;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        if (key == FHIR_TYPE) return FHIR_TYPE_LOOKUP_THUNK;
        if (key == TID) return TID_LOOKUP_THUNK;
        if (key == ID) return ID_LOOKUP_THUNK;
        if (key == T) return T_LOOKUP_THUNK;
        if (key == HASH) return HASH_LOOKUP_THUNK;
        if (key == NUM_CHANGES) return NUM_CHANGES_LOOKUP_THUNK;
        if (key == OP) return OP_LOOKUP_THUNK;
        return null;
    }

    @Override
    public IObj withMeta(IPersistentMap meta) {
        return new ResourceHandle(fhirType, tid, id, t, hash, state, meta);
    }

    @Override
    public IPersistentMap meta() {
        return meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof ResourceHandle that && tid == that.tid && id.equals(that.id) && t == that.t;
    }

    @Override
    public int hashCode() {
        int result = tid;
        result = 31 * result + id.hashCode();
        result = 31 * result + Long.hashCode(t);
        return result;
    }

    @Override
    public String toString() {
        return fhirType.getName() + "[id = " + id + ", t = " + t + ']';
    }
}
