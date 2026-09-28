package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Integers;
import clojure.lang.ILookupThunk;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;

public final class Integer extends PrimitiveElement {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 8 byte - long value
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + MEM_SIZE_REFERENCE + 8 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "integer");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Integer ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInteger");

    private static final byte HASH_MARKER = 1;

    // sentinel value for the value being absent.
    // one past the int range; (int) ABSENT == java.lang.Integer.MIN_VALUE
    private static final long ABSENT = java.lang.Integer.MAX_VALUE + 1L;

    private static final Interner<ExtensionData, Integer> INTERNER = Interners.weakInterner(k -> new Integer(k, ABSENT));
    private static final Integer EMPTY = new Integer(ExtensionData.EMPTY, ABSENT);

    private final long value;

    private Integer(ExtensionData extensionData, long value) {
        super(extensionData);
        this.value = value;
    }

    private static Integer maybeIntern(ExtensionData extensionData, long value) {
        return extensionData.isInterned() && value == ABSENT
                ? INTERNER.intern(extensionData)
                : new Integer(extensionData, value);
    }

    private static long checkIntRange(Number value) {
        long val = value.longValue();
        if ((int) val != val) {
            throw new IllegalArgumentException("Invalid integer value `%d`.".formatted(val));
        }
        return val;
    }

    public static Integer create(Number value) {
        return value == null ? EMPTY : new Integer(ExtensionData.EMPTY, checkIntRange(value));
    }

    public static Integer create(IPersistentMap m) {
        var value = (Number) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? ABSENT : checkIntRange(value));
    }

    @Override
    public boolean hasValue() {
        return value != ABSENT;
    }

    @Override
    public java.lang.Integer value() {
        return value == ABSENT ? null : (int) value;
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
    public Integer empty() {
        return EMPTY;
    }

    @Override
    public Integer assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, val == null ? ABSENT : checkIntRange((Number) val));
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public Integer withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value);
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeNumber(value);
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
            Integers.hashInto((int) value, sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Integer that &&
                extensionData.equals(that.extensionData) &&
                value == that.value;
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + (int) value;
    }

    @Override
    public String toString() {
        return "Integer{" + extensionData + (value == ABSENT ? "" : ", value=" + value) + '}';
    }
}
