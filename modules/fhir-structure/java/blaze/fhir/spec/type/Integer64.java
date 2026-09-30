package blaze.fhir.spec.type;

import blaze.fhir.spec.type.system.Longs;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;

public final class Integer64 extends PrimitiveElement {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 8 byte - long value
     * 1 byte - has value flag
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + MEM_SIZE_REFERENCE + 8 + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "integer64");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Integer64 ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInteger64");

    private static final byte HASH_MARKER = 2;

    private static final Integer64 EMPTY = new Integer64(ExtensionData.EMPTY, 0, false);
    private static final ValuelessInterner<Integer64> INTERNER = new ValuelessInterner<>(EMPTY, k -> new Integer64(k, 0, false));

    // the whole long range is valid, so absence can't be encoded as a sentinel value
    private final long value;
    private final boolean hasValue;

    private Integer64(ExtensionData extensionData, long value, boolean hasValue) {
        super(extensionData);
        this.value = value;
        this.hasValue = hasValue;
    }

    private static Integer64 maybeIntern(ExtensionData extensionData, long value, boolean hasValue) {
        return extensionData.isInterned() && !hasValue
                ? INTERNER.intern(extensionData)
                : new Integer64(extensionData, value, hasValue);
    }

    private static long checkLong(Object value) {
        if (value instanceof Long l) return l;
        throw new IllegalArgumentException("Invalid integer64 value `%s`.".formatted(value));
    }

    public static Integer64 create(Long value) {
        return value == null ? EMPTY : new Integer64(ExtensionData.EMPTY, value, true);
    }

    public static Integer64 create(IPersistentMap m) {
        var value = m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? 0 : checkLong(value), value != null);
    }

    @Override
    public boolean hasValue() {
        return hasValue;
    }

    @Override
    public Long value() {
        return hasValue ? value : null;
    }

    @Override
    public ILookupThunk getLookupThunk(Keyword key) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE_LOOKUP_THUNK : super.getLookupThunk(key);
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Integer64 empty() {
        return EMPTY;
    }

    @Override
    public Integer64 assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, val == null ? 0 : checkLong(val), val != null);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value, hasValue);
        if (key == ID) return maybeIntern(extensionData.withId(val), value, hasValue);
        return this;
    }

    @Override
    public Integer64 withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value, hasValue);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(Long.toString(value));
        } else {
            generator.writeNull();
        }
    }

    @Override
    @SuppressWarnings("UnstableApiUsage")
    public void hashInto(PrimitiveSink sink) {
        sink.putByte(HASH_MARKER);
        extensionData.hashInto(sink);
        if (hasValue()) {
            sink.putByte((byte) 2);
            Longs.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Integer64 that &&
                extensionData.equals(that.extensionData) &&
                hasValue == that.hasValue &&
                value == that.value;
    }

    @Override
    public int hashCode() {
        return 31 * (31 * extensionData.hashCode() + java.lang.Boolean.hashCode(hasValue)) + Long.hashCode(value);
    }

    @Override
    public String toString() {
        return "Integer64{" + extensionData + (hasValue ? ", value=" + value : "") + '}';
    }
}
