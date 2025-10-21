package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Integers;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

public final class Integer extends PrimitiveElement {

    /**
     * Memory size.
     * <p>
     * 8 byte - object header
     * 4 byte - extension data reference
     * 4 byte - int value
     * 1 byte - boolean value
     * 7 byte - padding
     */
    private static final int MEM_SIZE_OBJECT = MEM_SIZE_OBJECT_HEADER + 16;

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "integer");

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

    public static Integer create(Number value) {
        return value == null ? EMPTY : new Integer(ExtensionData.EMPTY, value.intValue(), false);
    }

    public static Integer create(IPersistentMap m) {
        Number value = (Number) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? 0 : value.intValue(), value == null);
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public Integer empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Integer assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, val == null ? 0 : ((Number) val).intValue(), val == null);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value, isValueNull);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value, isValueNull);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Integer.");
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
