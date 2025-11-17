package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Integers;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.Integer;
import java.lang.String;
import java.util.Objects;

public final class UnsignedInt extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = RT.keyword("fhir", "unsignedInt");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUnsignedInt");

    private static final byte HASH_MARKER = 17;

    private static final Interner<ExtensionData, UnsignedInt> INTERNER = Interners.weakInterner(k -> new UnsignedInt(k, -1));
    private static final UnsignedInt EMPTY = new UnsignedInt(ExtensionData.EMPTY, -1);

    private final int value;

    private UnsignedInt(ExtensionData extensionData, int value) {
        super(extensionData);
        this.value = value;
    }

    private static UnsignedInt maybeIntern(ExtensionData extensionData, int value) {
        return extensionData.isInterned() && value < 0
                ? INTERNER.intern(extensionData)
                : new UnsignedInt(extensionData, value);
    }

    private static int toUnsignedInt(Number value) {
        long val = value.longValue();
        if (val < 0 || (int) val != val) {
            throw new IllegalArgumentException("Invalid unsignedInt value `%d`.".formatted(val));
        }
        return (int) val;
    }

    public static UnsignedInt create(Number value) {
        return value == null ? EMPTY : new UnsignedInt(ExtensionData.EMPTY, toUnsignedInt(value));
    }

    public static UnsignedInt create(IPersistentMap m) {
        var value = (Number) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? -1 : toUnsignedInt(value));
    }

    @Override
    public boolean hasValue() {
        return value >= 0;
    }

    @Override
    public Integer value() {
        return hasValue() ? value : null;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public UnsignedInt empty() {
        return EMPTY;
    }

    @Override
    public UnsignedInt assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, val == null ? -1 : toUnsignedInt((Number) val));
        if (key == EXTENSION) return maybeIntern(extensionData.withExtension(val), value);
        if (key == ID) return maybeIntern(extensionData.withId(val), value);
        return this;
    }

    @Override
    public UnsignedInt withMeta(IPersistentMap meta) {
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
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof UnsignedInt that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "UnsignedInt{" + extensionData + ", value=" + value + '}';
    }
}
