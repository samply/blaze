package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Decimals;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public final class Decimal extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "decimal");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDecimal");

    private static final byte HASH_MARKER = 4;

    private static final Interner<ExtensionData, Decimal> INTERNER = Interners.weakInterner(k -> new Decimal(k, null));
    private static final Decimal EMPTY = new Decimal(ExtensionData.EMPTY, null);

    private final BigDecimal value;

    private Decimal(ExtensionData extensionData, BigDecimal value) {
        super(extensionData);
        this.value = value;
    }

    private static Decimal maybeIntern(ExtensionData extensionData, BigDecimal value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Decimal(extensionData, value);
    }

    public static Decimal create(BigDecimal value) {
        return value == null ? EMPTY : new Decimal(ExtensionData.EMPTY, value);
    }

    public static Decimal create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (BigDecimal) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public BigDecimal value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Decimal empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Decimal assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (BigDecimal) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Decimal.");
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
        if (value != null) {
            sink.putByte((byte) 2);
            Decimals.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + Decimals.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Decimal d &&
                extensionData.equals(d.extensionData) &&
                Objects.equals(value, d.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Decimal{" + extensionData + ", value=" + value + '}';
    }
}
