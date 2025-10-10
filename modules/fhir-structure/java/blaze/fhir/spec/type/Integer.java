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
import java.util.Objects;

public final class Integer extends PrimitiveElement {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 byte - int value
     * 1 byte - boolean value
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + MEM_SIZE_REFERENCE + 4 + 1 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "integer");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof Integer ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueInteger");

    private static final byte HASH_MARKER = 1;

    private static final Interner<ExtensionData, Integer> INTERNER = Interners.weakInterner(k -> new Integer(k, 0, true));
    private static final Integer EMPTY = new Integer(ExtensionData.EMPTY, 0, true);

    private final int value;
    private final boolean isValueNull;

    private Integer(ExtensionData extensionData, int value, boolean isValueNull) {
        super(extensionData);
        this.value = value;
        this.isValueNull = isValueNull;
    }

    private static Integer maybeIntern(ExtensionData extensionData, int value, boolean isValueNull) {
        return extensionData.isInterned() && isValueNull
                ? INTERNER.intern(extensionData)
                : new Integer(extensionData, value, isValueNull);
    }

    private static int toInt(Number value) {
        long val = value.longValue();
        if ((int) val != val) {
            throw new IllegalArgumentException("Invalid integer value `%d`.".formatted(val));
        }
        return (int) val;
    }

    public static Integer create(Number value) {
        return value == null ? EMPTY : new Integer(ExtensionData.EMPTY, toInt(value), false);
    }

    public static Integer create(IPersistentMap m) {
        var value = (Number) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? 0 : toInt(value), value == null);
    }

    @Override
    public boolean hasValue() {
        return !isValueNull;
    }

    @Override
    public java.lang.Integer value() {
        return isValueNull ? null : value;
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
        if (key == VALUE) return maybeIntern(extensionData, val == null ? 0 : toInt((Number) val), val == null);
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value, isValueNull);
        if (key == ID) return maybeIntern(extensionData.withId(val), value, isValueNull);
        return this;
    }

    @Override
    public Integer withMeta(IPersistentMap meta) {
        return maybeIntern(extensionData.withMeta(meta), value, isValueNull);
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
            Integers.hashInto(value, sink);
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
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Integer{" + extensionData + ", value=" + value + '}';
    }
}
