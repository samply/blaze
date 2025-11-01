package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Booleans;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class Boolean extends PrimitiveElement {

    public static final Boolean TRUE = new Boolean(ExtensionData.EMPTY, java.lang.Boolean.TRUE);
    public static final Boolean FALSE = new Boolean(ExtensionData.EMPTY, java.lang.Boolean.FALSE);

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "boolean");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBoolean");

    private static final byte HASH_MARKER = 0;

    private static final Interner<InternerKey, Boolean> INTERNER = Interners.weakInterner(k -> new Boolean(k.extensionData, k.value));
    private static final Boolean EMPTY = new Boolean(ExtensionData.EMPTY, null);

    private final java.lang.Boolean value;

    private Boolean(ExtensionData extensionData, java.lang.Boolean value) {
        super(extensionData);
        this.value = value;
    }

    private static Boolean intern(ExtensionData extensionData, java.lang.Boolean value) {
        return INTERNER.intern(new InternerKey(extensionData, value));
    }

    private static Boolean maybeIntern(ExtensionData extensionData, java.lang.Boolean value) {
        return extensionData.isInterned() ? intern(extensionData, value) : new Boolean(extensionData, value);
    }

    public static Boolean create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (java.lang.Boolean) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned();
    }

    public java.lang.Boolean value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Boolean empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Boolean assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (java.lang.Boolean) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Boolean.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeBoolean(value);
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
            Booleans.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Boolean that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Boolean{" +
                extensionData +
                ", value=" + value +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, java.lang.Boolean value) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
