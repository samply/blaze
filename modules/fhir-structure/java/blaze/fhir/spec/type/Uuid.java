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
import java.util.UUID;

public final class Uuid extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "uuid");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueUuid");

    private static final byte HASH_MARKER = 19;

    private static final Interner<ExtensionData, Uuid> INTERNER = Interners.weakInterner(k -> new Uuid(k, null));
    private static final Uuid EMPTY = new Uuid(ExtensionData.EMPTY, null);
    private static final int MEM_SIZE_UUID = 24;

    private final UUID value;

    private Uuid(ExtensionData extensionData, UUID value) {
        super(extensionData);
        this.value = value;
    }

    private static Uuid maybeIntern(ExtensionData extensionData, UUID value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Uuid(extensionData, value);
    }

    public static Uuid create(String value) {
        return value == null ? EMPTY : new Uuid(ExtensionData.EMPTY, UUID.fromString(value.substring(9)));
    }

    public static Uuid create(IPersistentMap m) {
        String value = (String) m.valAt(VALUE);
        return maybeIntern(ExtensionData.fromMap(m), value == null ? null : UUID.fromString(value.substring(9)));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public String value() {
        return value == null ? null : "urn:uuid:" + value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Uuid empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Uuid assoc(Object key, Object val) {
        if (key == VALUE)
            return maybeIntern(extensionData, val == null ? null : UUID.fromString(((String) val).substring(9)));
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Uuid.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value());
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
            sink.putLong(value.getMostSignificantBits());
            sink.putLong(value.getLeastSignificantBits());
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + (value == null ? 0 : MEM_SIZE_UUID);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Uuid that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Uuid{" +
                extensionData +
                ", value=" + (value == null ? null : "'" + value() + '\'') +
                '}';
    }
}
