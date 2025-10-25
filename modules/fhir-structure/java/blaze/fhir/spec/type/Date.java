package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

public final class Date extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "date");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueDate");

    private static final byte HASH_MARKER = 10;

    private static final Interner<ExtensionData, Date> INTERNER = Interners.weakInterner(k -> new Date(k, null));
    private static final Date EMPTY = new Date(ExtensionData.EMPTY, null);

    private final blaze.fhir.spec.type.system.Date value;

    private Date(ExtensionData extensionData, blaze.fhir.spec.type.system.Date value) {
        super(extensionData);
        this.value = value;
    }

    private static Date maybeIntern(ExtensionData extensionData, blaze.fhir.spec.type.system.Date value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Date(extensionData, value);
    }

    public static Date create(blaze.fhir.spec.type.system.Date value) {
        return value == null ? EMPTY : new Date(ExtensionData.EMPTY, value);
    }

    public static Date create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (blaze.fhir.spec.type.system.Date) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public blaze.fhir.spec.type.system.Date value() {
        return value;
    }

    @Override
    public Date empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Date assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (blaze.fhir.spec.type.system.Date) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Date.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            // TODO: improve performance with AsciiByteArrayAppendable
            generator.writeString(value.toString());
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
            value.hashInto(sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + (value == null ? 0 : value.memSize());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Date that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Date{" + extensionData + ", value=" + value + '}';
    }
}
