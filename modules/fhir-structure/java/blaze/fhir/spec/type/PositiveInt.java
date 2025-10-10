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
import java.lang.Integer;
import java.lang.String;
import java.util.Objects;

public final class PositiveInt extends PrimitiveElement {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 or 8 byte - extension data reference
     * 4 byte - int value
     */
    private static final int MEM_SIZE_OBJECT = (MEM_SIZE_OBJECT_HEADER + MEM_SIZE_REFERENCE + 4 + 7) & ~7;

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "positiveInt");

    private static final ILookupThunk FHIR_TYPE_LOOKUP_THUNK = new ILookupThunk() {
        @Override
        public Object get(Object target) {
            return target instanceof PositiveInt ? FHIR_TYPE : this;
        }
    };

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valuePositiveInt");

    private static final byte HASH_MARKER = 18;

    private static final Interner<ExtensionData, PositiveInt> INTERNER = Interners.weakInterner(k -> new PositiveInt(k, 0));
    private static final PositiveInt EMPTY = new PositiveInt(ExtensionData.EMPTY, 0);

    private final int value;

    private PositiveInt(ExtensionData extensionData, int value) {
        super(extensionData);
        this.value = value;
    }

    private static PositiveInt maybeIntern(ExtensionData extensionData, int value) {
        return extensionData.isInterned() && value == 0
                ? INTERNER.intern(extensionData)
                : new PositiveInt(extensionData, value);
    }

    private static int toPosInt(Number value) {
        long val = value.longValue();
        if (val <= 0 || (int) val != val) {
            throw new IllegalArgumentException("Invalid positiveInt value `%d`.".formatted(val));
        }
        return (int) val;
    }

    public static PositiveInt create(Number value) {
        return value == null ? EMPTY : new PositiveInt(ExtensionData.EMPTY, toPosInt(value));
    }

    public static PositiveInt create(IPersistentMap m) {
        var value = (Number) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? 0 : toPosInt(value));
    }

    @Override
    public boolean hasValue() {
        return value > 0;
    }

    @Override
    public Integer value() {
        return hasValue() ? value : null;
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
    public PositiveInt empty() {
        return EMPTY;
    }

    @Override
    public PositiveInt assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, val == null ? 0 : toPosInt((Number) val));
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public PositiveInt withMeta(IPersistentMap meta) {
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
        return o instanceof PositiveInt that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "PositiveInt{" + extensionData + ", value=" + value + '}';
    }
}
