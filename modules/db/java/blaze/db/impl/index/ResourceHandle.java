package blaze.db.impl.index;

import blaze.ByteString;
import blaze.fhir.Hash;
import clojure.lang.*;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;

import static java.util.Objects.requireNonNull;

public final class ResourceHandle implements ILookup, IKeywordLookup {

    public static final Comparator<ResourceHandle> ID_CMP = Comparator.comparing(rh -> rh.id);

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "type");
    private static final Keyword TID = RT.keyword(null, "tid");
    private static final Keyword ID = RT.keyword(null, "id");
    private static final Keyword T = RT.keyword(null, "t");
    private static final Keyword HASH = RT.keyword(null, "hash");
    private static final Keyword HASH_PREFIX = RT.keyword(null, "hash-prefix");
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
            return target instanceof ResourceHandle rh ? rh.hash() : this;
        }
    };

    private static final ILookupThunk HASH_PREFIX_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof ResourceHandle rh ? Integer.toUnsignedLong(rh.hashPrefix()) : this;
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
    private final long h0;
    private final long h1;
    private final long h2;
    private final long h3;
    private final long state;

    /**
     * Creates a resource handle with the hash given by its four longs
     * {@code h0} to {@code h3}.
     */
    public ResourceHandle(Keyword fhirType, int tid, String id, long t, long h0, long h1, long h2, long h3,
                          long state) {
        this.fhirType = requireNonNull(fhirType);
        this.tid = tid;
        this.id = requireNonNull(id);
        this.t = t;
        this.h0 = h0;
        this.h1 = h1;
        this.h2 = h2;
        this.h3 = h3;
        this.state = state;
    }

    /**
     * Returns a new {@link Hash} instance on each call.
     */
    public Hash hash() {
        return Hash.fromLongs(h0, h1, h2, h3);
    }

    ByteString idByteString() {
        return ByteString.copyFrom(id, StandardCharsets.ISO_8859_1);
    }

    int hashPrefix() {
        return Hash.prefix(h0);
    }

    public void copyTidTo(ByteBuffer target) {
        target.putInt(tid);
    }

    /**
     * Copies the ISO-8859-1 encoded bytes of the id into {@code target}
     * without creating an intermediate byte array.
     */
    public void copyIdTo(ByteBuffer target) {
        for (int i = 0; i < id.length(); i++) {
            target.put((byte) id.charAt(i));
        }
    }

    public void copyHashPrefixTo(ByteBuffer target) {
        target.putInt(hashPrefix());
    }

    public void copyHashTo(ByteString.Builder target) {
        target.putLong(h0);
        target.putLong(h1);
        target.putLong(h2);
        target.putLong(h3);
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
        if (key == HASH) return hash();
        if (key == HASH_PREFIX) return Integer.toUnsignedLong(hashPrefix());
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
        if (key == HASH_PREFIX) return HASH_PREFIX_LOOKUP_THUNK;
        if (key == NUM_CHANGES) return NUM_CHANGES_LOOKUP_THUNK;
        if (key == OP) return OP_LOOKUP_THUNK;
        return null;
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
