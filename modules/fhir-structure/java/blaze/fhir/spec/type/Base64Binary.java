package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

public final class Base64Binary extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "base64Binary");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueBase64Binary");

    private static final byte HASH_MARKER = 8;

    private static final Interner<ExtensionData, Base64Binary> INTERNER = Interners.weakInterner(k -> new Base64Binary(k, null));
    private static final Base64Binary EMPTY = new Base64Binary(ExtensionData.EMPTY, null);

    private final String value;

    private Base64Binary(ExtensionData extensionData, String value) {
        super(extensionData);
        this.value = value;
    }

    private static Base64Binary maybeIntern(ExtensionData extensionData, String value) {
        return extensionData.isInterned() && value == null ? INTERNER.intern(extensionData) : new Base64Binary(extensionData, value);
    }

    public static Base64Binary create(String value) {
        return value == null ? EMPTY : new Base64Binary(ExtensionData.EMPTY, value);
    }

    public static Base64Binary create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    public String value() {
        return value;
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Base64Binary empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Base64Binary assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (String) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value);
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value);
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Base64Binary.");
    }

    @Override
    public FieldName fieldNameExtensionValue() {
        return FIELD_NAME_EXTENSION_VALUE;
    }

    @Override
    public void serializeJsonPrimitiveValue(JsonGenerator generator) throws IOException {
        if (hasValue()) {
            generator.writeString(value);
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
            Strings.hashInto(value, sink);
        }
    }

    @Override
    public int memSize() {
        return super.memSize() + Strings.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Base64Binary that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Base64Binary{" + extensionData + ", value=" + (value == null ? null : '\'' + value + '\'') + '}';
    }
}
