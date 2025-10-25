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
import java.lang.Integer;
import java.lang.String;
import java.util.List;
import java.util.Objects;

public final class PositiveInt extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "positiveInt");

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

    public static PositiveInt create(Number value) {
        if (value == null) {
            return EMPTY;
        }
        int val = value.intValue();
        if (val > 0) {
            return new PositiveInt(ExtensionData.EMPTY, val);
        }
        throw invalidValueException(val);
    }

    public static PositiveInt create(IPersistentMap m) {
        Number value = (Number) m.valAt(VALUE);
        if (value == null) {
            return maybeIntern(ExtensionData.fromMap(m), 0);
        }
        int val = value.intValue();
        if (val > 0) {
            return maybeIntern(ExtensionData.fromMap(m), val);
        }
        throw invalidValueException(val);
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
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
    public PositiveInt empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PositiveInt assoc(Object key, Object val) {
        if (key == VALUE) {
            if (val == null) {
                return maybeIntern(extensionData, -1);
            }
            int v = ((Number) val).intValue();
            if (v > 0) {
                return maybeIntern(extensionData, v);
            }
            throw invalidValueException(v);
        }
        if (key == EXTENSION) {
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        }
        if (key == ID) {
            return maybeIntern(extensionData.withId((String) val), value);
        }
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.positiveInt.");
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

    private static IllegalArgumentException invalidValueException(int val) {
        return new IllegalArgumentException("Invalid positiveInt value `%s`.".formatted(val));
    }
}
