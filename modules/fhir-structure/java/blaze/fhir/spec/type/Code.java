package blaze.fhir.spec.type;

import blaze.Interner;
import blaze.Interners;
import blaze.fhir.spec.type.system.Strings;
import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.PersistentVector;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;
import com.google.common.hash.PrimitiveSink;

import java.io.IOException;
import java.lang.String;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

public final class Code extends PrimitiveElement {

    private static final Keyword FHIR_TYPE = Keyword.intern("fhir", "code");

    private static final FieldName FIELD_NAME_EXTENSION_VALUE = FieldName.of("valueCode");

    private static final byte HASH_MARKER = 13;

    private static final Interner<InternerKey, Code> INTERNER = Interners.weakInterner(k -> create(k.extensionData, k.value));
    private static final Code EMPTY = new Code(ExtensionData.EMPTY, null);

    private final SerializedString value;

    private Code(ExtensionData extensionData, SerializedString value) {
        super(extensionData);
        this.value = value;
    }

    private static Code create(ExtensionData extensionData, String value) {
        return new Code(extensionData, value == null ? null : new SerializedString(value));
    }

    private static Code intern(ExtensionData extensionData, String value) {
        return INTERNER.intern(new InternerKey(extensionData, value));
    }

    private static Code maybeIntern(ExtensionData extensionData, String value) {
        return extensionData.isInterned() ? intern(extensionData, value) : create(extensionData, value);
    }

    public static Code create(String value) {
        return intern(ExtensionData.EMPTY, requireNonNull(value));
    }

    public static Code create(IPersistentMap m) {
        return maybeIntern(ExtensionData.fromMap(m), (String) m.valAt(VALUE));
    }

    @Override
    public Keyword fhirType() {
        return FHIR_TYPE;
    }

    @Override
    public boolean isInterned() {
        return extensionData.isInterned();
    }

    @Override
    public String value() {
        return value == null ? null : value.getValue();
    }

    @Override
    public Object valAt(Object key, Object notFound) {
        return key == FHIR_TYPE_KEY ? FHIR_TYPE : super.valAt(key, notFound);
    }

    @Override
    public Code empty() {
        return EMPTY;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Code assoc(Object key, Object val) {
        if (key == VALUE) return maybeIntern(extensionData, (String) val);
        if (key == EXTENSION)
            return maybeIntern(extensionData.withExtension((List<Extension>) (val == null ? PersistentVector.EMPTY : val)), value());
        if (key == ID) return maybeIntern(extensionData.withId((String) val), value());
        throw new UnsupportedOperationException("The key `" + key + "` isn't supported on FHIR.Code.");
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
        if (hasValue()) {
            sink.putByte((byte) 2);
            Strings.hashInto(value.getValue(), sink);
        }
    }

    @Override
    public int memSize() {
        return isInterned() ? 0 : MEM_SIZE_OBJECT + extensionData.memSize() + Strings.memSize(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Code that &&
                extensionData.equals(that.extensionData) &&
                Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return 31 * extensionData.hashCode() + Objects.hashCode(value);
    }

    @Override
    public String toString() {
        return "Code{" +
                extensionData +
                ", value=" + (value == null ? null : '\'' + value() + '\'') +
                '}';
    }

    private record InternerKey(ExtensionData extensionData, String value) {
        private InternerKey {
            requireNonNull(extensionData);
        }
    }
}
